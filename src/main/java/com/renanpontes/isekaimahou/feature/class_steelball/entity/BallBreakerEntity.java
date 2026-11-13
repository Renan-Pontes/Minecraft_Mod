package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

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
 * Esfera Quebrantadora (homenagem ao Ball Breaker): o espectro da bola dourada paira sobre o alvo e o DESGASTA — a
 * cada segundo a rotação envelhece o corpo: dano crescente ({@link #pulseDamage}), Lentidão e Fraqueza cada vez mais
 * fortes e a defesa quebrada (Exposto). Chefes envelhecem pela metade (Lentidão menor). Some no fim ou se o alvo cair.
 * Não é salva: ao carregar o mundo some.
 */
public class BallBreakerEntity extends Entity implements GeoEntity {
    public static final int PULSE = 20;
    public static final int BASE_TICKS = 160;
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(BallBreakerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(BallBreakerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(BallBreakerEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_ball_breaker.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    @Nullable
    private SpellStats stats;
    @Nullable
    private UUID targetId;
    private int pulses;

    public BallBreakerEntity(EntityType<? extends BallBreakerEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Dano do pulso {@code k} (0, 1, 2...): 1 + 0,5·k — cresce enquanto o desgaste avança. */
    public static float pulseDamage(int k) {
        return 1.0F + 0.5F * k;
    }

    /** Soma dos pulsos (para o detalhamento do Grimório). */
    public static float totalDamage(int ticks) {
        float sum = 0F;
        for (int k = 0; k < ticks / PULSE; k++) sum += pulseDamage(k);
        return sum;
    }

    /** Põe (ou renova) a esfera sobre o alvo. */
    public static void attach(SpellContext ctx, SpellStats stats, LivingEntity target, int ticks) {
        ServerLevel sl = ctx.level();
        for (BallBreakerEntity old : sl.getEntitiesOfClass(BallBreakerEntity.class, target.getBoundingBox().inflate(3),
                e -> target.getUUID().equals(e.targetId))) {
            old.discard();
        }
        BallBreakerEntity e = new BallBreakerEntity(SbRegistry.BALL_BREAKER.get(), sl);
        e.ctx = ctx;
        e.stats = stats;
        e.targetId = target.getUUID();
        e.entityData.set(DATA_TARGET, target.getId());
        e.entityData.set(DATA_LIFE, Math.max(PULSE * 2, ticks));
        e.entityData.set(DATA_SIZE, Math.max(0.9F, Math.min(3.0F, Math.max(target.getBbWidth() * 1.2F, target.getBbHeight() * 0.6F))));
        e.follow(target);
        sl.addFreshEntity(e);
        SbFx.sound(sl, target.position(), SoundEvents.WITHER_AMBIENT, 0.4F, 1.8F);
        // a esfera espectral se forma sobre o alvo e cai (a coreografia é do cliente)
        float r = e.size();
        SbFxNet.at(sl, SbFxNet.BREAKER_CHARGE, target.position(), Vec3.ZERO, r, 0xFFB43A, target.getId());
        com.renanpontes.isekaimahou.glyph.SpellScheduler.schedule(16, () -> {
            if (target.isAlive() && target.level() == sl) {
                SbFxNet.at(sl, SbFxNet.BREAKER_HIT, target.position().add(0, target.getBbHeight() * 0.5, 0), Vec3.ZERO, r, 0xFFB43A, target.getId());
            }
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_LIFE, BASE_TICKS);
        builder.define(DATA_SIZE, 1.0F);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public float openness(float partial) {
        float t = tickCount + partial;
        return Math.min(Math.min(1F, t / 10F), Math.max(0F, Math.min(1F, (life() - t) / 10F)));
    }

    @Nullable
    public Entity target() {
        int id = entityData.get(DATA_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    private void follow(Entity t) {
        setPos(t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        Entity t = target();
        if (t != null) follow(t);
        if (!(level() instanceof ServerLevel sl)) return;
        if (ctx == null || stats == null || !(t instanceof LivingEntity target) || !target.isAlive() || tickCount >= life()) {
            fade(sl);
            return;
        }
        if ((tickCount - 1) % PULSE == 0) wear(sl, target);
        if (tickCount % 4 == 0) {
            // cinzas e fios brancos: o corpo envelhece
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            sl.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y, c.z, 3, target.getBbWidth() * 0.4, target.getBbHeight() * 0.4,
                    target.getBbWidth() * 0.4, 0.01);
            sl.sendParticles(SbFx.dust(0xC8B89A, 0.7F), c.x, c.y, c.z, 1, 0.3, 0.4, 0.3, 0);
        }
    }

    private void wear(ServerLevel sl, LivingEntity target) {
        int k = pulses++;
        boolean boss = BossKit.isBossLike(target);
        target.invulnerableTime = 0;
        ctx.damage(target, pulseDamage(k), stats, this);
        if (!target.isAlive()) return;
        int slow = Math.min(boss ? 1 : 3, k / 2);
        int weak = Math.min(2, k / 3);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PULSE + 10, slow, false, true), ctx.caster());
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PULSE + 10, weak, false, true), ctx.caster());
        if (k >= 2) target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, PULSE + 10, Math.min(2, k / 3), false, false), ctx.caster());
        Statuses.apply(target, Statuses.Kind.EXPOSED, 1, PULSE * 2, ctx.caster(), stats.power() * ctx.powerMult());
        if (k % 2 == 0) SbSpin.apply(target, ctx.caster(), 0, 30);
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        SbFx.spiral(sl, target.position(), 0xFFB43A, target.getBbWidth() * 0.8 + 0.3, target.getBbHeight() + 0.3, 16, k * 0.7F);
        sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4 + k, 0.25, 0.4, 0.25, 0.01);
        SbFx.sound(sl, c, SoundEvents.CHAIN_BREAK, 0.6F, 1.4F - 0.08F * Math.min(8, k));
        SbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.5F + 0.05F * k);
    }

    private void fade(ServerLevel sl) {
        Vec3 p = position();
        sl.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        sl.sendParticles(SbFx.dust(0xFFB43A, 1.2F), p.x, p.y, p.z, 12, 0.5, 0.5, 0.5, 0);
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 0.6F);
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
        entityData.set(DATA_LIFE, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Spent", true);
    }
}
