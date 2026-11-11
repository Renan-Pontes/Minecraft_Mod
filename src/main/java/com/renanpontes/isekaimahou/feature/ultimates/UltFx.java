package com.renanpontes.isekaimahou.feature.ultimates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Ajudantes de servidor do módulo: partículas vistas de longe, sons, mira no chão, "foto" da Carga de Ultimate
 * (para as ultimates não recarregarem a si mesmas) e o filtro de "inimigo de verdade" usado pelo Colosso e pela
 * Transcendência (não sai batendo em vaca e aldeão).
 */
public final class UltFx {
    /** Alcance das partículas forçadas (meteoros caem longe de quem conjura). */
    public static final double FAR = 160.0;

    // =====================================================================================
    //  Partículas e sons
    // =====================================================================================
    public static void far(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                           double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= FAR * FAR) {
                level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void far(ServerLevel level, ParticleOptions type, Vec3 p, int count, double spread, double speed) {
        far(level, type, p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    public static int rgb(SpellContext ctx) {
        return ctx.color() & 0xFFFFFF;
    }

    /** Cor média entre duas cores RGB. */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Elementos da magia (o primário primeiro), para o visual variar entre eles. */
    public static List<Element> elements(SpellContext ctx) {
        List<Element> out = new ArrayList<>();
        out.add(ctx.primaryElement());
        for (Element e : ctx.rootSpell().elements()) if (!out.contains(e)) out.add(e);
        return out;
    }

    // =====================================================================================
    //  Carga de Ultimate
    // =====================================================================================
    /** Cargas atuais do jogador (null para mobs). */
    @Nullable
    public static float[] chargeSnapshot(@Nullable LivingEntity owner) {
        if (!(owner instanceof ServerPlayer sp)) return null;
        MagicData d = MagicApi.get(sp);
        Element[] all = Element.values();
        float[] out = new float[all.length];
        for (int i = 0; i < all.length; i++) out[i] = d.charge(all[i]);
        return out;
    }

    /** Desfaz a carga ganha desde a foto (nunca tira carga que já existia). */
    public static void chargeRestore(@Nullable LivingEntity owner, @Nullable float[] snap) {
        if (snap == null || !(owner instanceof ServerPlayer sp)) return;
        MagicData d = MagicApi.get(sp);
        Element[] all = Element.values();
        for (int i = 0; i < all.length; i++) {
            if (d.charge(all[i]) > snap[i]) d.setCharge(all[i], snap[i]);
        }
    }

    // =====================================================================================
    //  Conjurador, alvos e mira
    // =====================================================================================
    /** O conjurador ainda está no mundo da magia. */
    public static boolean casterReady(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        return c != null && !c.isRemoved() && c.isAlive() && c.level() == ctx.level();
    }

    public static boolean isFake(@Nullable Entity e) {
        return e instanceof FakePlayer;
    }

    /**
     * Inimigo "de verdade" de quem conjurou: monstros, quem está mirando o dono (ou um aliado dele), quem o feriu
     * agora há pouco e quem ele acabou de atacar. Mob conjurador: o alvo dele e quem o feriu.
     */
    public static boolean isFoe(SpellContext ctx, @Nullable LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || ctx.isAlly(e)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (owner == null) return e instanceof Enemy;
        if (owner instanceof Mob mob) {
            return e == mob.getTarget() || e == owner.getLastHurtByMob();
        }
        if (e == owner.getLastHurtByMob()) return true;
        if (e == owner.getLastHurtMob() && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) return true;
        if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == owner || ctx.isAlly(m.getTarget()))) return true;
        return e instanceof Enemy && !(e instanceof Player);
    }

    public static List<LivingEntity> foesAround(SpellContext ctx, @Nullable LivingEntity owner, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.distanceToSqr(center) <= radius * radius)) {
            if (isFoe(ctx, owner, e)) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return out;
    }

    /** Algum efeito da magia vale para este alvo? */
    public static boolean affects(SpellContext ctx, Entity target) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (ctx.isValidTarget(e.effect(), e.stats(), target)) return true;
        }
        return false;
    }

    public static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, ClipContext.Fluid fluid, @Nullable Entity source) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid,
                source == null ? CollisionContext.empty() : CollisionContext.of(source)));
    }

    /** Desce de {@code p} até o primeiro bloco sólido ou superfície de líquido (até {@code depth} blocos). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p, double depth) {
        BlockHitResult r = clip(level, p, p.subtract(0, depth, 0), ClipContext.Fluid.ANY, null);
        return r.getType() == HitResult.Type.MISS ? p : r.getLocation();
    }

    /**
     * Ponto mirado no chão até {@code range} blocos: âncora do estágio, o alvo do mob, a criatura na mira ou o chão onde
     * o olhar bate (mirando o céu: o chão embaixo do fim do alcance).
     */
    public static Vec3 aimGround(SpellContext ctx, double range) {
        ServerLevel level = ctx.level();
        if (ctx.anchor() != null) return groundBelow(level, ctx.anchor().add(0, 0.5, 0), 24);
        LivingEntity c = ctx.caster();
        if (!ctx.hasOrigin() && c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget().position();
        }
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(ctx.look().scale(range));
        BlockHitResult bh = clip(level, eye, end, ClipContext.Fluid.ANY, c);
        Vec3 limit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities(c, new AABB(eye, limit).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && affects(ctx, e))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.4 + e.getPickRadius()).clip(eye, limit);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        if (best != null) return best.position();
        if (bh.getType() == HitResult.Type.MISS) return groundBelow(level, end, 64);
        if (bh.getDirection() == Direction.UP) return bh.getLocation();
        Vec3 out = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
        return groundBelow(level, out, 24);
    }

    /** Círculo de quem conjura (mobs e pedestais contam como Círculo 4). */
    public static int circleOf(@Nullable LivingEntity caster) {
        if (caster instanceof ServerPlayer sp && !(caster instanceof FakePlayer)) return Math.max(1, MagicApi.get(sp).circle());
        return 4;
    }

    private UltFx() {}
}
