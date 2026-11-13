package com.renanpontes.isekaimahou.feature.legends_c.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fuga ("Abra"): três fases numa entidade só.
 * <ol>
 *   <li>DRAW — um arco de chamas se forma na mão do conjurador e a flecha é puxada ({@link #DRAW_TICKS}).</li>
 *   <li>FLY — a flecha flamejante voa reta e rápida, incendiando quem passa perto e QUEIMANDO o chão por onde passa
 *       (SCORCH ao longo do caminho).</li>
 *   <li>COLUMN — no impacto: explosão (dano alto + Em Chamas 5), cratera e uma COLUNA DE FOGO de ~22 blocos que
 *       queima quem está dentro enquanto dura.</li>
 * </ol>
 */
public class FugaArrowEntity extends LcEntity {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_PHASE_TICK = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_COL_RADIUS = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COL_TICKS = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.INT);

    public static final int DRAW = 0, FLY = 1, COLUMN = 2;
    public static final int DRAW_TICKS = 22;
    public static final double SPEED = 3.2;
    public static final float COLUMN_HEIGHT = 22F;
    public static final float IMPACT_DAMAGE = 26F;

    /** Uma Fuga por conjurador de cada vez. */
    private static final Map<UUID, FugaArrowEntity> ACTIVE = new HashMap<>();

    @Nullable private SpellStats stats;
    @Nullable private LivingEntity aimTarget;
    @Nullable private Vec3 aimPoint;
    private Vec3 velocity = Vec3.ZERO;
    private double travelled;
    private final Set<Integer> burned = new HashSet<>();
    /** Já bateu numa barreira de domínio no voo (a explosão não tira integridade de novo). */
    private boolean wallHit;

    public FugaArrowEntity(EntityType<? extends FugaArrowEntity> type, Level level) {
        super(type, level);
    }

    /** Começa a Fuga (nada se o conjurador já tem uma em andamento). */
    public static void spawn(SpellContext ctx, SpellStats stats, @Nullable LivingEntity target, @Nullable Vec3 point) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        FugaArrowEntity old = ACTIVE.get(caster.getUUID());
        if (old != null && !old.isRemoved() && old.phase() != COLUMN) return;
        ServerLevel l = ctx.level();
        FugaArrowEntity f = new FugaArrowEntity(LcRegistry.FUGA_ARROW.get(), l);
        f.ctx = ctx;
        f.stats = stats;
        f.aimTarget = target;
        f.aimPoint = point;
        f.bindOwner(caster);
        f.entityData.set(DATA_PHASE_TICK, l.getGameTime());
        f.entityData.set(DATA_COL_RADIUS, stats.radius(3F) * (LcUtil.colossal(stats) ? 2F : 1F));
        f.entityData.set(DATA_COL_TICKS, Mth.clamp(stats.duration(60), 30, 240));
        Vec3 hand = handPos(caster, 1F);
        f.setPos(hand.x, hand.y, hand.z);
        f.aim(caster.getLookAngle());
        l.addFreshEntity(f);
        ACTIVE.put(caster.getUUID(), f);
        Announce.send(caster, Component.translatable("announce.isekai_mahou.legends_c.fuga"), LcFx.FIRE, Announce.STYLE_ULTIMATE);
        LcFx.sound(l, hand, SoundEvents.FIRECHARGE_USE, 1.5F, 0.5F);
        LcFx.sound(l, hand, SoundEvents.BLAZE_AMBIENT, 1.2F, 0.5F);
    }

    /** Onde o arco fica: à frente e um pouco à direita dos olhos. */
    public static Vec3 handPos(LivingEntity o, float pt) {
        Vec3 look = o.getViewVector(pt);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return o.getEyePosition(pt).add(look.scale(0.9)).add(right.scale(0.3)).add(0, -0.25, 0);
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, DRAW);
        builder.define(DATA_PHASE_TICK, 0L);
        builder.define(DATA_COL_RADIUS, 3F);
        builder.define(DATA_COL_TICKS, 60);
    }

    public int phase() { return entityData.get(DATA_PHASE); }
    /** Ticks desde que a fase atual começou. */
    public int phaseAge() { return (int) (level().getGameTime() - entityData.get(DATA_PHASE_TICK)); }
    public float columnRadius() { return entityData.get(DATA_COL_RADIUS); }
    public int columnTicks() { return entityData.get(DATA_COL_TICKS); }

    /** Direção pela rotação da entidade (sincronizada). */
    public Vec3 dir() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    private void aim(Vec3 d) {
        Vec3 n = d.normalize();
        setYRot((float) (Mth.atan2(-n.x, n.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Math.asin(Mth.clamp(n.y, -1.0, 1.0)) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    private void setPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
        entityData.set(DATA_PHASE_TICK, level().getGameTime());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel l = (ServerLevel) level();
        if (ctx == null || stats == null || owner == null) {
            discard();
            return;
        }
        switch (phase()) {
            case DRAW -> draw(l);
            case FLY -> fly(l);
            default -> column(l);
        }
    }

    private void draw(ServerLevel l) {
        if (!owner.isAlive() || owner.level() != l) {
            discard();
            return;
        }
        Vec3 hand = handPos(owner, 1F);
        setPos(hand.x, hand.y, hand.z);
        Vec3 dir = owner.getLookAngle();
        if (aimTarget != null && aimTarget.isAlive()) dir = LcUtil.center(aimTarget).subtract(hand);
        else if (aimPoint != null) dir = aimPoint.subtract(hand);
        aim(dir);
        if (phaseAge() % 4 == 0) LcFx.sound(l, hand, SoundEvents.FIRE_AMBIENT, 1.2F, 0.6F + phaseAge() * 0.03F);
        if (phaseAge() >= DRAW_TICKS) {
            velocity = dir().scale(SPEED * stats.speedMult());
            setPhase(FLY);
            LcFx.sound(l, hand, SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
            LcFx.sound(l, hand, SoundEvents.CROSSBOW_SHOOT, 1.5F, 0.6F);
            LcFx.flash(l, hand, 1.8F, LcFx.FIRE, 6);
        }
    }

    private void fly(ServerLevel l) {
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        LcUtil.Wall wall = LcUtil.firstWall(l, from, to, owner);
        if (wall != null) {
            wallHit = true;
            LcUtil.strikeWall(l, wall, owner.position(), owner, IMPACT_DAMAGE * 1.5F * stats.power() * ctx.powerMult());
            impact(l, wall.point().subtract(velocity.normalize().scale(0.4)));   // explode do lado de cá da parede
            return;
        }
        BlockHitResult bh = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        LivingEntity hit = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.8),
                e -> e.isAlive() && !e.isSpectator() && e != owner && !ctx.isAlly(e))) {
            var clip = e.getBoundingBox().inflate(0.4).clip(from, end);
            if (clip.isPresent()) {
                double d = clip.get().distanceToSqr(from);
                if (d < best) { best = d; hit = e; }
            } else if (!LcUtil.behindWall(l, from, e, owner) && burned.add(e.getId())) {
                // Passou raspando: pega fogo
                ctx.damage(e, 6F, stats, this);
                Statuses.apply(e, Statuses.Kind.BURNING, 2, stats.duration(100), owner, ctx.powerMult());
            }
        }
        if (hit != null) {
            impact(l, LcUtil.center(hit));
            return;
        }
        if (bh.getType() == HitResult.Type.BLOCK) {
            impact(l, bh.getLocation());
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
        travelled += velocity.length();
        // O chão queima por onde a flecha passa
        if (tickCount % 2 == 0 && LcUtil.hasGround(l, to, 5)) {
            Destruction.surface(l, LcUtil.ground(l, to, 5), 1.8F, owner, Destruction.Mode.SCORCH);
        }
        LcFx.far(l, Vfx.flame(), to.x, to.y, to.z, 6, 0.2, 0.2, 0.2, 0.02);
        if (tickCount % 3 == 0) LcFx.far(l, ParticleTypes.LARGE_SMOKE, to.x, to.y, to.z, 2, 0.1, 0.1, 0.1, 0.01);
        if (travelled >= stats.radius(64F)) impact(l, to);
    }

    private void impact(ServerLevel l, Vec3 at) {
        boolean colossal = LcUtil.colossal(stats);
        Vec3 ground = LcUtil.hasGround(l, at, 6) ? LcUtil.ground(l, at, 6) : at;
        setPos(ground.x, ground.y, ground.z);
        setDeltaMovement(Vec3.ZERO);
        setPhase(COLUMN);
        float r = stats.radius(5F) * (colossal ? 2F : 1F);
        for (LivingEntity e : ctx.enemiesAround(at, r)) {
            if (LcUtil.behindWall(l, at, e, owner)) continue;   // a explosão não atravessa a barreira de outro domínio
            ctx.damage(e, IMPACT_DAMAGE, stats, this);
            Statuses.apply(e, Statuses.Kind.BURNING, 5, stats.duration(160), owner, ctx.powerMult());
            Vec3 n = LcUtil.center(e).subtract(at);
            if (n.lengthSqr() > 1.0E-4 && !BossKit.isBossLike(e)) e.push(n.normalize().x * 1.2, 0.7, n.normalize().z * 1.2);
            e.hurtMarked = true;
        }
        if (!wallHit && Domains.barrierAt(l, at) != null) {
            Domains.hitBarrier(l, at, owner.position(), owner, IMPACT_DAMAGE * 1.5F * stats.power() * ctx.powerMult());
        }
        Destruction.crater(l, at, Math.min(18F, (3.5F + 1.5F * Math.max(0, stats.aoe)) * (colossal ? 2F : 1F)), owner);
        Destruction.surface(l, ground, columnRadius() + 4F, owner, Destruction.Mode.SCORCH);
        LcFx.flash(l, at, 6F + r * 0.5F, LcFx.FIRE, 12);
        LcFx.wave(l, ground.add(0, 0.5, 0), new Vec3(0, 1, 0), r * 1.8F, LcFx.FIRE, 18);
        LcFx.far(l, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 1.0, 0.5, 1.0, 0);
        LcFx.far(l, ParticleTypes.LAVA, at.x, at.y, at.z, 40, r * 0.5, 1.0, r * 0.5, 0.2);
        LcFx.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 4F, 0.6F);
        LcFx.sound(l, at, SoundEvents.FIRECHARGE_USE, 3F, 0.4F);
        BossKit.shake(l, at, 64, colossal ? 2.5F : 1.6F, 20);
    }

    private void column(ServerLevel l) {
        int age = phaseAge();
        if (age >= columnTicks()) {
            ACTIVE.remove(owner.getUUID(), this);
            discard();
            return;
        }
        Vec3 base = position();
        float r = columnRadius();
        if (age % 10 == 0) {
            AABB box = new AABB(base.x - r, base.y - 1, base.z - r, base.x + r, base.y + COLUMN_HEIGHT, base.z + r);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner && !ctx.isAlly(e))) {
                double dx = e.getX() - base.x, dz = e.getZ() - base.z;
                if (dx * dx + dz * dz > (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5)) continue;
                if (LcUtil.behindWall(l, base.add(0, Math.min(COLUMN_HEIGHT, e.getY() - base.y + 1), 0), e, owner)) continue;
                ctx.damage(e, 5F, stats, this);
                Statuses.apply(e, Statuses.Kind.BURNING, 2, stats.duration(100), owner, ctx.powerMult());
                e.push(0, 0.25, 0);
                e.hurtMarked = true;
            }
        }
        if (age % 20 == 0) LcFx.sound(l, base, SoundEvents.BLAZE_BURN, 2.0F, 0.5F);
        if (age % 2 == 0) {
            LcFx.far(l, Vfx.flame(), base.x, base.y + COLUMN_HEIGHT * 0.5, base.z, 14, r * 0.5, COLUMN_HEIGHT * 0.4, r * 0.5, 0.06);
            LcFx.far(l, Vfx.largeSmoke(), base.x, base.y + COLUMN_HEIGHT, base.z, 4, r * 0.6, 1.0, r * 0.6, 0.05);
        }
    }

    private void clientFx() {
        if (phase() == DRAW) {
            LivingEntity o = ownerEntity();
            if (o == null) return;
            Vec3 h = handPos(o, 1F);
            level().addParticle(Vfx.flame(), h.x + (random.nextDouble() - 0.5) * 0.9, h.y + (random.nextDouble() - 0.5) * 0.9,
                    h.z + (random.nextDouble() - 0.5) * 0.9, 0, 0.01, 0);
        } else if (phase() == FLY) {
            Vec3 p = position();
            level().addParticle(Vfx.smallFlame(), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
