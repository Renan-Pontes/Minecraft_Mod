package com.renanpontes.isekaimahou.feature.legends_j;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Utilitários de servidor dos glifos de anime do legends_j. As regras de alvo são as do legends_f
 * ({@link LfUtil#isFoe}: monstros, chefes, jogadores NÃO aliados — PvP desligado = aliado — e quem te ataca), então
 * Modo Paz, grupos, duelos e guerras de cidade valem igual aqui.
 */
public final class LjUtil {

    public static boolean isFoe(SpellContext ctx, Entity e) {
        return LfUtil.isFoe(ctx, e);
    }

    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        return LfUtil.foesAround(ctx, c, radius, max);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo, nova em volta de si...)? */
    public static boolean self(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    /** Só na primeira chamada de {@code key} nesta conjuração neste tick (formas de área chamam o efeito por alvo). */
    public static boolean once(SpellContext ctx, String key) {
        return LfUtil.oncePerTick(ctx, "lj:" + key);
    }

    /** Criatura na mira de {@code looker} até {@code range} blocos (para antes de paredes). */
    @Nullable
    public static LivingEntity lookTarget(LivingEntity looker, double range, java.util.function.Predicate<LivingEntity> ok) {
        Vec3 eye = looker.getEyePosition();
        Vec3 look = looker.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = looker.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
        if (bh.getType() == HitResult.Type.BLOCK) end = bh.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : looker.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5),
                e -> e != looker && e.isAlive() && !e.isSpectator() && ok.test(e))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Ponto que o conjurador mira (bloco ou o fim do alcance). */
    public static Vec3 lookPoint(LivingEntity looker, double range) {
        Vec3 eye = looker.getEyePosition();
        Vec3 end = eye.add(looker.getLookAngle().scale(range));
        BlockHitResult bh = looker.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
        return bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
    }

    /** Criatura "comum" que um glifo de controle pode mexer: nada de chefes, NPCs, suportes de armadura. */
    public static boolean ordinaryMob(LivingEntity e) {
        return !(e instanceof Player) && !(e instanceof Npc) && !(e instanceof ArmorStand) && !Statuses.isBossLike(e);
    }

    /**
     * Pode trocar de lugar / mexer com este jogador? Só com quem é do MESMO GRUPO ou é inimigo de verdade (PvP ligado,
     * duelo, guerra). PvP desligado conta como "aliado" no motor, mas desconhecidos não são arrastados por aí.
     */
    public static boolean playerConsents(SpellContext ctx, Player other) {
        LivingEntity c = ctx.caster();
        if (other.isCreative() || other.isSpectator()) return false;
        if (c instanceof Player cp && Party.sameParty(cp, other)) return true;
        return isFoe(ctx, other);
    }

    /**
     * Área indestrutível (torre em escalada, masmorra): troca de lugar e blocos temporários não entram nem saem dela
     * (sem atalhos em quebra-cabeças). Os dois pontos dentro da mesma área = tudo bem.
     */
    public static boolean wardCrossing(ServerLevel l, Vec3 a, Vec3 b, @Nullable Entity caster) {
        boolean wa = Destruction.wardedFor(l, BlockPos.containing(a), caster);
        boolean wb = Destruction.wardedFor(l, BlockPos.containing(b), caster);
        return wa != wb;
    }

    /** Zona segura (cidade/vila): cidades são sagradas. */
    public static boolean safe(ServerLevel l, Vec3 p) {
        return Places.isSafe(l, BlockPos.containing(p));
    }

    /** Ataques automáticos (mob sob Geass) ficam quietos com o dono em Modo Paz ou em zona segura. */
    public static boolean calm(@Nullable LivingEntity owner) {
        if (owner == null) return true;
        if (owner instanceof Player p && PeaceMode.isOn(p)) return true;
        return owner.level() instanceof ServerLevel sl && safe(sl, owner.position());
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

    private LjUtil() {}
}
