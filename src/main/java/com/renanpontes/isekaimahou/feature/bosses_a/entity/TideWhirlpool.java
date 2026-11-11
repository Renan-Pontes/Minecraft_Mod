package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Redemoinho da Maré: um funil de água parado que puxa e gira quem estiver perto; no centro afoga
 * (dano de água, Encharcado e lentidão). Modelo GeckoLib translúcido + bolhas e respingos.
 */
public class TideWhirlpool extends Entity implements GeoEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.tide_whirlpool.spin");
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(TideWhirlpool.class, EntityDataSerializers.INT);
    public static final double RADIUS = 6.0;
    /** Raio do centro que afoga (a Maré o marca em vermelho antes de o funil abrir). */
    public static final double CORE = 1.8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ownerId;
    private float damage = 3F;

    public TideWhirlpool(EntityType<? extends TideWhirlpool> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static TideWhirlpool spawn(LivingEntity owner, Vec3 pos, int life, float damage) {
        TideWhirlpool w = new TideWhirlpool(BossesARegistry.WHIRLPOOL.get(), owner.level());
        w.ownerId = owner.getUUID();
        w.damage = damage;
        w.entityData.set(DATA_LIFE, life);
        w.setPos(pos.x, pos.y + 0.05, pos.z);
        owner.level().addFreshEntity(w);
        return w;
    }

    public int life() { return entityData.get(DATA_LIFE); }

    /** Escala do funil (abre no começo e fecha no fim). */
    public float openness(float partialTick) {
        float age = tickCount + partialTick;
        return Math.min(1F, age / 12F) * Math.min(1F, Math.max(0F, (life() - age) / 15F));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE, 160);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity ownerEntity = ownerId != null ? sl.getEntity(ownerId) : null;
        // Some com a Maré (inclusive durante a animação de morte)
        if (tickCount >= life() || !(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) {
            sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 40, 2.0, 0.2, 2.0, 0.2);
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.7F);
        }
        float open = openness(0);
        double r = RADIUS * open;
        Vec3 c = position();
        AABB box = new AABB(c.x - r, c.y - 2.5, c.z - r, c.x + r, c.y + 3.0, c.z + r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> BossSpells.isFoe(owner, e))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) continue;
            double pull = 0.035 + 0.045 * (1.0 - d / RADIUS);
            Vec3 inward = d < 0.2 ? Vec3.ZERO : new Vec3(-dx / d, 0, -dz / d);
            Vec3 tangent = d < 0.2 ? Vec3.ZERO : new Vec3(-dz / d, 0, dx / d);
            Vec3 v = e.getDeltaMovement().add(inward.scale(pull)).add(tangent.scale(0.05));
            double max = 0.55;
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            if (h > max) v = new Vec3(v.x / h * max, v.y, v.z / h * max);
            e.setDeltaMovement(v.x, Math.min(v.y, e.isInWater() ? -0.03 : v.y), v.z);
            e.hurtMarked = true;
            if (d <= CORE && tickCount % 10 == 0) {
                BossSpells.hit(owner, e, Element.WATER, damage);
                e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 120, 0), owner);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), owner);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private void clientFx() {
        float open = openness(0);
        double t = tickCount * 0.35;
        for (int i = 0; i < 4; i++) {
            double r = 0.5 + random.nextDouble() * RADIUS * open;
            double a = t + random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            double sp = 0.12 + 0.6 / (r + 0.5);
            level().addParticle(i == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_POP, x, getY() + 0.15, z,
                    -Math.sin(a) * sp, 0.02, Math.cos(a) * sp);
            if (random.nextInt(2) == 0) {
                level().addParticle(ElementFx.dust(Element.WATER, 1.3F), x, getY() + 0.2, z, 0, 0, 0);
            }
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.BUBBLE_COLUMN_UP, getX() + (random.nextDouble() - 0.5), getY() - 0.3,
                    getZ() + (random.nextDouble() - 0.5), 0, 0.1, 0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
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
}
