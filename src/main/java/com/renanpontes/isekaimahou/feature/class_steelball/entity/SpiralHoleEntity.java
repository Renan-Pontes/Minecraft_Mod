package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Buraco Espiral (Tusk):
 * <ul>
 *   <li>{@link #CHASER} — Ato 2, a Rotação Dourada: o buraco deixado pela unha dourada desliza pelo chão (e por
 *   cima de tudo) atrás do inimigo atingido — se ele morrer, vai atrás do inimigo mais perto. Quando alcança, "morde"
 *   a cada 0,75 s: aplica os efeitos da magia (60% do poder), deixa Girado e prende o pé (Lentidão). Some quando o
 *   tempo acaba.</li>
 *   <li>{@link #PORTAL} — Ato 3: a boca do buraco de minhoca (entrada e saída), só visual, some em 1,5 s.</li>
 * </ul>
 * Não é salvo: ao carregar o mundo some no primeiro tick.
 */
public class SpiralHoleEntity extends Entity implements GeoEntity {
    public static final byte CHASER = 0;
    public static final byte PORTAL = 1;
    public static final int BITE = 15;
    public static final float BITE_POWER = 0.6F;
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(SpiralHoleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(SpiralHoleEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_spiral_hole.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID targetId;
    private int lastBite = -BITE;
    private boolean initialized;

    public SpiralHoleEntity(EntityType<? extends SpiralHoleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Ato 2: o buraco que persegue {@code target} (ou o inimigo mais perto). */
    public static void chaser(SpellContext ctx, Vec3 at, @Nullable LivingEntity target, int ticks) {
        ServerLevel sl = ctx.level();
        SpiralHoleEntity e = new SpiralHoleEntity(SbRegistry.SPIRAL_HOLE.get(), sl);
        e.entityData.set(DATA_MODE, CHASER);
        e.entityData.set(DATA_LIFE, Math.max(40, ticks));
        e.ctx = ctx;
        e.initialized = true;
        e.targetId = target == null ? null : target.getUUID();
        Vec3 p = target != null ? target.position() : at;
        e.setPos(p.x, p.y + 0.02, p.z);
        sl.addFreshEntity(e);
        SbFx.sound(sl, p, SoundEvents.BEACON_ACTIVATE, 0.7F, 1.9F);
    }

    /** Ato 3: boca do buraco de minhoca (entrada ou saída). */
    public static void portal(ServerLevel sl, Vec3 at) {
        SpiralHoleEntity e = new SpiralHoleEntity(SbRegistry.SPIRAL_HOLE.get(), sl);
        e.entityData.set(DATA_MODE, PORTAL);
        e.entityData.set(DATA_LIFE, 30);
        e.initialized = true;
        e.setPos(at.x, at.y + 0.02, at.z);
        sl.addFreshEntity(e);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODE, CHASER);
        builder.define(DATA_LIFE, 120);
    }

    public byte mode() {
        return entityData.get(DATA_MODE);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    /** 0..1: abre no começo e fecha no fim. */
    public float openness(float partial) {
        float t = tickCount + partial;
        return Math.min(Math.min(1F, t / 6F), Math.max(0F, Math.min(1F, (life() - t) / 8F)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (!initialized || tickCount >= life()) {
            close(sl);
            return;
        }
        if (mode() == PORTAL) {
            if (tickCount % 3 == 0) SbFx.spinRing(sl, position().add(0, 0.05, 0), 0xB45CFF, 0.9 - tickCount * 0.01, 10);
            return;
        }
        if (ctx == null) {
            close(sl);
            return;
        }
        LivingEntity target = resolveTarget(sl);
        if (target != null) crawl(target);
        if (tickCount % 4 == 0) {
            SbFx.spinRing(sl, position().add(0, 0.06, 0), SbFx.GOLD, 0.55 + 0.1 * Math.sin(tickCount * 0.4), 8);
        }
        if (target != null && tickCount - lastBite >= BITE && reaches(target)) bite(sl, target);
    }

    @Nullable
    private LivingEntity resolveTarget(ServerLevel sl) {
        if (targetId != null) {
            Entity e = sl.getEntity(targetId);
            if (e instanceof LivingEntity le && le.isAlive() && e.distanceToSqr(this) < 48 * 48) return le;
            targetId = null;
        }
        if (ctx == null) return null;
        LivingEntity best = null;
        double bestD = 16 * 16;
        for (LivingEntity e : ctx.enemiesAround(position(), 16)) {
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) targetId = best.getUUID();
        return best;
    }

    /** Desliza na direção do alvo (0,36 bloco/tick na horizontal; acompanha a altura dos pés dele). */
    private void crawl(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double dist = flat.length();
        double step = Math.min(dist, 0.36 * Math.max(0.5F, ctx.formStats().speedMult()));
        Vec3 move = dist > 1.0E-3 ? flat.scale(step / dist) : Vec3.ZERO;
        double dy = Math.max(-0.5, Math.min(0.5, to.y + 0.02));
        setPos(getX() + move.x, getY() + dy, getZ() + move.z);
        if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(SbFx.dust(SbFx.GOLD, 0.7F), getX(), getY() + 0.05, getZ(), 2, 0.15, 0.0, 0.15, 0);
        }
    }

    private boolean reaches(LivingEntity target) {
        double r = 0.9 + target.getBbWidth() * 0.5;
        Vec3 d = target.position().subtract(position());
        return d.x * d.x + d.z * d.z <= r * r && Math.abs(d.y) <= Math.max(1.2, target.getBbHeight());
    }

    private void bite(ServerLevel sl, LivingEntity target) {
        lastBite = tickCount;
        SpellContext use = ctx.copy(ctx.powerMult() * BITE_POWER);
        target.invulnerableTime = 0;
        use.resolveOnEntity(target, this);
        if (!target.isAlive()) return;
        SbSpin.apply(target, ctx.caster(), 0, 40);
        boolean boss = BossKit.isBossLike(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, boss ? 0 : 2, false, false), ctx.caster());
        SbFx.spiral(sl, target.position(), SbFx.GOLD, target.getBbWidth() * 0.7 + 0.3, target.getBbHeight(), 14, tickCount * 0.3F);
        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.3, target.getZ(), 6, 0.2, 0.1, 0.2, 0.2);
        SbFx.sound(sl, target.position(), SoundEvents.GRINDSTONE_USE, 0.6F, 1.8F);
    }

    private void close(ServerLevel sl) {
        if (mode() == CHASER && initialized) {
            SbFx.goldenSpiral(sl, position().add(0, 0.3, 0), new Vec3(0, 1, 0), 0.9);
            if (ctx != null) ctx.end(position(), new Vec3(0, 1, 0));
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        initialized = false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Spent", true);
    }
}
