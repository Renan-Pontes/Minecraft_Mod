package com.renanpontes.isekaimahou.feature.legends_g;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Utilitários de servidor das lendas do legends_g. Reaproveita as regras de alvo do legends_f
 * ({@link LfUtil#isFoe}: monstros, chefes, jogadores NÃO aliados — PvP desligado = aliado — e quem te ataca), assim
 * Modo Paz, grupos, duelos e guerras de cidade valem igual em todas as lendas.
 */
public final class LgUtil {

    /** Poder efetivo (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return LfUtil.power(ctx, stats);
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo)? */
    public static boolean self(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    /** O bloco atingido é o chão sob os pés de quem conjurou (a forma Si Mesmo também aplica no bloco de baixo). */
    public static boolean selfBlock(SpellContext ctx, BlockHitResult hit) {
        LivingEntity c = ctx.caster();
        return c != null && hit.getLocation().distanceToSqr(c.position()) < 2.25;
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Direção do golpe: para onde o conjurador olha (Si Mesmo/aliado) ou dos olhos até o alvo. */
    public static Vec3 aim(SpellContext ctx, @Nullable Entity target) {
        LivingEntity c = ctx.caster();
        if (c == null) return new Vec3(0, 0, 1);
        if (target == null || self(ctx, target) || ctx.isAlly(target)) return c.getLookAngle();
        Vec3 d = center(target).subtract(c.getEyePosition());
        return d.lengthSqr() < 1.0E-4 ? c.getLookAngle() : d.normalize();
    }

    /** Ponto onde um raio de {@code from} na direção {@code dir} para (primeiro bloco sólido ou {@code max} blocos). */
    public static Vec3 rayEnd(ServerLevel level, Vec3 from, Vec3 dir, double max, Entity source) {
        Vec3 to = from.add(dir.normalize().scale(max));
        BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        return r.getType() == HitResult.Type.MISS ? to : r.getLocation();
    }

    public static boolean isFoe(SpellContext ctx, Entity e) {
        return LfUtil.isFoe(ctx, e);
    }

    /** Inimigos numa esfera, mais perto primeiro. */
    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        return LfUtil.foesAround(ctx, c, radius, max);
    }

    /** Inimigos cuja caixa encosta no segmento {@code from→to} (com folga {@code width}), do mais perto ao mais longe. */
    public static List<LivingEntity> foesOnLine(SpellContext ctx, Vec3 from, Vec3 to, double width, int max) {
        AABB box = new AABB(from, to).inflate(width + 1.0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(ctx, e))) {
            AABB hb = e.getBoundingBox().inflate(width);
            if (hb.contains(from) || hb.clip(from, to).isPresent()) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /**
     * Criatura "mágica" (a anti-magia corta mais fundo): chefes, criaturas do mod (slimes, elementais, demônios...),
     * quem está com alguma bênção ativa e jogadores (todo jogador do isekai usa mana).
     */
    public static boolean magical(LivingEntity e) {
        if (Statuses.isBossLike(e) || e instanceof Player) return true;
        if (IsekaiMahou.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) return true;
        for (MobEffectInstance inst : e.getActiveEffects()) if (inst.getEffect().value().isBeneficial()) return true;
        return false;
    }

    /** Remove até {@code max} efeitos benéficos (bênçãos, escudos mágicos...). Devolve quantos saíram. */
    public static int stripBuffs(LivingEntity e, int max) {
        List<Holder<MobEffect>> good = new ArrayList<>();
        for (MobEffectInstance inst : e.getActiveEffects()) if (inst.getEffect().value().isBeneficial()) good.add(inst.getEffect());
        int n = 0;
        for (Holder<MobEffect> h : good) {
            if (n >= max) break;
            if (e.removeEffect(h)) n++;
        }
        return n;
    }

    /**
     * Ataques automáticos (o Grim, a onda da Forma Negra...) ficam quietos com o dono em Modo Paz ou dentro de uma
     * zona segura (cidades são sagradas).
     */
    public static boolean calm(@Nullable LivingEntity owner) {
        if (owner == null) return true;
        if (owner instanceof Player p && PeaceMode.isOn(p)) return true;
        return owner.level() instanceof ServerLevel sl && Places.isSafe(sl, BlockPos.containing(owner.position()));
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void actionbar(@Nullable Entity e, net.minecraft.network.chat.Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    /** Só na primeira chamada de {@code key} nesta conjuração neste tick (formas de área chamam o efeito por alvo). */
    public static boolean once(SpellContext ctx, String key) {
        return LfUtil.oncePerTick(ctx, "lg:" + key);
    }

    private LgUtil() {}
}
