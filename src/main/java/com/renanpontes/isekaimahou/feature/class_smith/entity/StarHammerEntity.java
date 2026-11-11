package com.renanpontes.isekaimahou.feature.class_smith.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * O martelo colossal da Forja Estelar (GeckoLib): surge no céu, sobe e desce três vezes no ponto mirado. A animação
 * "slam" tem 1 s e o golpe acontece aos 0,85 s de cada ciclo — o servidor bate nos ticks 17, 37 e 57 e some no 72.
 * Não é salvo (o contexto da magia vive só na memória) e não mexe em blocos.
 */
public class StarHammerEntity extends Entity implements GeoEntity {
    public static final int SLAMS = 3;
    public static final int FIRST_SLAM = 17;
    public static final int INTERVAL = 20;
    public static final int LIFE = 72;
    private static final RawAnimation SLAM = RawAnimation.begin().thenLoop("animation.smith_star_hammer.slam");
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(StarHammerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(StarHammerEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private SpellContext ctx;

    public StarHammerEntity(EntityType<? extends StarHammerEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static StarHammerEntity create(ServerLevel level, SpellContext ctx, Vec3 ground, float radius, int color) {
        StarHammerEntity e = new StarHammerEntity(SmithRegistries.STAR_HAMMER.get(), level);
        e.ctx = ctx;
        e.setPos(ground.x, ground.y, ground.z);
        LivingEntity caster = ctx.caster();
        if (caster != null) e.setYRot(caster.getYRot());
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        e.entityData.set(DATA_RADIUS, radius);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, SmithFx.STAR);
        builder.define(DATA_RADIUS, 5.0F);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (ctx == null || tickCount > LIFE) {
            if (ctx != null) ctx.end(position(), new Vec3(0, -1, 0));
            discard();
            return;
        }
        int t = tickCount - FIRST_SLAM;
        if (t >= 0 && t % INTERVAL == 0 && t / INTERVAL < SLAMS) slam(t / INTERVAL);
    }

    /** Uma martelada: efeitos na área, empurrão para cima, brasas, onda de choque e tremor. */
    private void slam(int index) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = position();
        float r = radius();
        boolean last = index == SLAMS - 1;
        SpellContext use = last ? ctx.copy(ctx.powerMult() * 1.5F) : ctx;
        use.resolveArea(c.add(0, 0.5, 0), r, this, index == 0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r),
                e -> e.isAlive() && e != ctx.caster() && !ctx.isAlly(e) && e.distanceToSqr(c) <= r * r)) {
            Vec3 away = e.position().subtract(c);
            away = new Vec3(away.x, 0, away.z);
            Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.35);
            e.push(push.x, last ? 0.75 : 0.45, push.z);
            e.hurtMarked = true;
        }
        ctx.impact(c, new Vec3(0, -1, 0), null);
        int color = color();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.4, c.z, 20, r * 0.4, 0.2, r * 0.4, 0);
        sl.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 50, r * 0.5, 0.1, r * 0.5, 0.06);
        sl.sendParticles(SmithFx.spark(color), c.x, c.y + 1.0, c.z, 60, r * 0.4, 0.6, r * 0.4, 0.25);
        SmithFx.ring(sl, c.add(0, 0.15, 0), r, SmithFx.dust(color, 1.6F), 56);
        SmithFx.ring(sl, c.add(0, 0.15, 0), r * 0.6, SmithFx.dust(SmithFx.MOLTEN, 1.4F), 40);
        SmithFx.ring(sl, c.add(0, 0.15, 0), r + 1.0, ParticleTypes.CAMPFIRE_COSY_SMOKE, 30);
        SmithFx.groundBurst(sl, c, 50);
        SmithFx.sound(sl, c, SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        SmithFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.7F + 0.1F * index);
        if (last) SmithFx.sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 0.8F);
        BossKit.shake(sl, c, 24, last ? 1.0F : 0.6F, 12);
    }

    private void clientFx() {
        Level l = level();
        Vec3 c = position();
        float r = radius();
        // anel rúnico pulsando no chão enquanto o martelo existe
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            l.addParticle(SmithFx.spark(color()), c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.03, 0);
        }
        if (random.nextInt(2) == 0) {
            l.addParticle(ParticleTypes.END_ROD, c.x + (random.nextDouble() - 0.5) * 2, c.y + 3 + random.nextDouble() * 3,
                    c.z + (random.nextDouble() - 0.5) * 2, 0, -0.02, 0);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // não é salvo (ver shouldBeSaved)
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // não é salvo (ver shouldBeSaved)
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(SLAM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
