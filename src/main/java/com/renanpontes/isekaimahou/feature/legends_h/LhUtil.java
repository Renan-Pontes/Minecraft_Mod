package com.renanpontes.isekaimahou.feature.legends_h;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Utilitários de servidor dos glifos de anime do legends_h. As regras de alvo vêm do legends_f
 * ({@link LfUtil#isFoe}: monstros, chefes, jogadores NÃO aliados — PvP desligado = aliado — e quem ataca você), então
 * Modo Paz, grupos, duelos e guerras de cidade valem igual aqui.
 */
public final class LhUtil {

    /** Poder efetivo (aumentos × conjuração × atributo de poder mágico × maestria). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return LfUtil.power(ctx, stats);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo)? */
    public static boolean self(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    /** Direção do golpe: para onde o conjurador olha (Si Mesmo/aliado) ou dos olhos até o alvo. */
    public static Vec3 aim(SpellContext ctx, @Nullable Entity target) {
        LivingEntity c = ctx.caster();
        if (c == null) return ctx.look();
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

    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        return LfUtil.foesAround(ctx, c, radius, max);
    }

    /** Primeiro inimigo na mira (cone estreito), até {@code range} blocos, com linha de visão. */
    @Nullable
    public static LivingEntity firstFoeInSight(SpellContext ctx, LivingEntity c, double range, double minDot) {
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : foesAround(ctx, eye.add(look.scale(range * 0.5)), range * 0.5 + 2, 24)) {
            Vec3 to = center(e).subtract(eye);
            double d = to.length();
            if (d > range || d < 1.0E-3 || to.scale(1 / d).dot(look) < minDot || !c.hasLineOfSight(e)) continue;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Chefes resistem a empurrões/controle. */
    public static boolean boss(LivingEntity e) {
        return Statuses.isBossLike(e);
    }

    /**
     * Ataques automáticos (o Sheer Heart Attack) ficam quietos com o dono em Modo Paz ou dentro de uma zona segura
     * (cidades são sagradas).
     */
    public static boolean calm(@Nullable LivingEntity owner) {
        if (owner == null) return true;
        if (owner instanceof Player p && PeaceMode.isOn(p)) return true;
        return owner.level() instanceof ServerLevel sl && Places.isSafe(sl, BlockPos.containing(owner.position()));
    }

    /** Zona segura (cidade) no ponto. */
    public static boolean safeAt(ServerLevel level, Vec3 p) {
        return Places.isSafe(level, BlockPos.containing(p));
    }

    /**
     * Dano em si mesmo que NUNCA mata (o preço das técnicas: dedo quebrado do Deku, garganta do Inumaki): deixa pelo
     * menos meio coração. Criativo não sofre.
     */
    public static void strain(LivingEntity e, float amount) {
        if (amount <= 0 || !e.isAlive()) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        float hp = e.getHealth();
        float dmg = Math.min(amount, hp - 1F);
        if (dmg <= 0) return;
        e.setHealth(hp - dmg);
        e.hurtTime = 6;
        e.hurtDuration = 6;
        if (e.level() instanceof ServerLevel sl) sl.broadcastEntityEvent(e, (byte) 2);
    }

    /** Círculo de quem conjura (mobs contam como 6). */
    public static int circle(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer sp ? MagicApi.get(sp).circle() : 6;
    }

    // =====================================================================================
    //  Mensagens, sons e partículas
    // =====================================================================================

    public static void actionbar(@Nullable Entity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Letreiro com o nome da técnica (desenhado pelo módulo cinematic). Silencioso não anuncia. */
    public static void banner(LivingEntity source, SpellStats stats, String key, int rgb, boolean big) {
        if (stats != null && stats.silent) return;
        LfUtil.banner(source, "announce.isekai_mahou.legends_h." + key, rgb, big);
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Mth.clamp(size, 0.1F, 4F));
    }

    /** Linha de poeira colorida de {@code a} até {@code b} (no máximo {@code max} pontos) para todos por perto. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, int rgb, float size, int max) {
        Vec3 d = b.subtract(a);
        int n = Mth.clamp((int) (d.length() * 2.5), 2, max);
        DustParticleOptions dust = dust(rgb, size);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** O mesmo, mas só um jogador vê (Epitáfio, bombas da Killer Queen). */
    public static void privateLine(ServerPlayer sp, Vec3 a, Vec3 b, int rgb, float size, int max) {
        Vec3 d = b.subtract(a);
        int n = Mth.clamp((int) (d.length() * 2.5), 2, max);
        DustParticleOptions dust = dust(rgb, size);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            sp.serverLevel().sendParticles(sp, dust, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // =====================================================================================
    //  Rede
    // =====================================================================================

    /** Estado de tela para a própria entidade e quem a acompanha. */
    public static void screenTracking(Entity e, byte kind, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new LhNet.Screen(kind, e.getId(), ticks));
    }

    public static void screen(ServerPlayer sp, byte kind, int entity, int ticks) {
        PacketDistributor.sendToPlayer(sp, new LhNet.Screen(kind, entity, ticks));
    }

    /** Só na primeira chamada de {@code key} nesta conjuração neste tick (formas de área chamam o efeito por alvo). */
    public static boolean once(SpellContext ctx, String key) {
        return LfUtil.oncePerTick(ctx, "lh:" + key);
    }

    private LhUtil() {}
}
