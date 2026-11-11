package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.bosses_b.BbRegistries;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Projéteis mágicos dos chefes. A mesma classe serve três tipos:
 * <ul>
 *   <li>{@code abyssal_orb}: orbe de sombra teleguiado, corrompe; pode ser rebatido com um golpe;</li>
 *   <li>{@code celestial_lance}: lança de luz rápida e reta, expõe o alvo;</li>
 *   <li>{@code twilight_orb}: orbe do Arauto, explode em luz e sombra; também pode ser rebatido.</li>
 * </ul>
 * Além do tipo, o {@link #kind()} muda o comportamento: {@link #KIND_CHAIN} (corrente: prende e o chefe puxa depois,
 * com elos ligando o orbe ao chefe) e {@link #KIND_VERDICT} (avisa o chefe onde estourou, que solta o 2º estágio ali).
 */
public class BossBolt extends AbstractHurtingProjectile implements GeoEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.boss_bolt.spin");
    public static final int KIND_PLAIN = 0;
    public static final int KIND_CHAIN = 1;
    public static final int KIND_VERDICT = 2;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(BossBolt.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float damage = 8.0F;
    private float homing;
    private int homingTargetId = -1;
    private int life;

    public BossBolt(EntityType<? extends BossBolt> type, Level level) {
        super(type, level);
        this.accelerationPower = 0.0;
    }

    /** Dispara um projétil de {@code from} na direção {@code dir}. */
    public static BossBolt shoot(LivingEntity owner, EntityType<BossBolt> type, Vec3 from, Vec3 dir, double speed, float damage,
                                 @Nullable LivingEntity homingTarget, float homing) {
        return shoot(owner, type, from, dir, speed, damage, homingTarget, homing, KIND_PLAIN);
    }

    /** Igual, com um comportamento especial ({@link #KIND_CHAIN}, {@link #KIND_VERDICT}). */
    public static BossBolt shoot(LivingEntity owner, EntityType<BossBolt> type, Vec3 from, Vec3 dir, double speed, float damage,
                                 @Nullable LivingEntity homingTarget, float homing, int kind) {
        BossBolt b = new BossBolt(type, owner.level());
        b.entityData.set(DATA_KIND, kind);
        b.setOwner(owner);
        b.moveTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        b.setDeltaMovement(dir.normalize().scale(speed));
        b.hasImpulse = true;
        b.damage = damage;
        b.homing = homing;
        b.homingTargetId = homingTarget != null ? homingTarget.getId() : -1;
        owner.level().addFreshEntity(b);
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, KIND_PLAIN);
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    private boolean isLance() {
        return getType() == BbRegistries.CELESTIAL_LANCE.get();
    }

    private boolean isTwilight() {
        return getType() == BbRegistries.TWILIGHT_ORB.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trailFx();
            return;
        }
        life++;
        // A corrente tem comprimento (~30 blocos); os orbes e lanças somem depois de voar um bom tempo.
        int maxLife = kind() == KIND_CHAIN ? 26 : isLance() ? 70 : 140;
        if (life > maxLife) {
            fizzle();
            return;
        }
        if (homing > 0 && homingTargetId >= 0 && life > 4) {
            Entity t = level().getEntity(homingTargetId);
            if (t != null && t.isAlive()) {
                Vec3 v = getDeltaMovement();
                double speed = v.length();
                Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position()).normalize().scale(speed);
                setDeltaMovement(v.add(want.subtract(v).scale(homing)).normalize().scale(speed));
            }
        }
    }

    private void trailFx() {
        Vec3 p = position().add(0, getBbHeight() / 2, 0);
        Vec3 v = getDeltaMovement();
        if (kind() == KIND_CHAIN && tickCount % 2 == 0 && getOwner() instanceof BbBoss boss) {
            // Elos da corrente: do chefe até o orbe (lê como uma corrente sendo arremessada).
            Vec3 hand = boss.castHand();
            Vec3 d = p.subtract(hand);
            double len = d.length();
            double step = BbBoss.reducedParticles() ? 1.2 : 0.55;
            for (double s = 0; s < len; s += step) {
                Vec3 q = hand.add(d.scale(s / len));
                level().addParticle(BbFx.dust((int) (s / step) % 2 == 0 ? BbFx.SHADOW_DEEP : 0x6A6A78, 0.9F), q.x, q.y, q.z, 0, 0, 0);
            }
        }
        if (isLance()) {
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                level().addParticle(BbFx.dust(BbFx.LIGHT, 1.1F), p.x - v.x * f, p.y - v.y * f, p.z - v.z * f, 0, 0, 0);
            }
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0, 0);
        } else if (isTwilight()) {
            level().addParticle(BbFx.dust(BbFx.LIGHT, 1.2F), p.x + (random.nextDouble() - 0.5) * 0.4, p.y, p.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            level().addParticle(BbFx.dust(BbFx.SHADOW, 1.2F), p.x + (random.nextDouble() - 0.5) * 0.4, p.y, p.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.WITCH, p.x, p.y, p.z, 0, 0, 0);
        } else {
            level().addParticle(BbFx.dust(BbFx.SHADOW, 1.3F), p.x, p.y, p.z, 0, 0, 0);
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SQUID_INK, p.x, p.y, p.z, -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity owner = getOwner();
        if (owner != null && (target == owner || owner.isAlliedTo(target))) return false;
        return !(target instanceof BossBolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Entity t = result.getEntity();
        if (t.hurt(source(sl), damage) && t instanceof LivingEntity le) {
            applyOnHit(le);
            if (getOwner() instanceof BbBoss boss && boss.isAlive()) boss.onBoltHit(this, le);
        }
        impact(sl);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel sl) impact(sl);
    }

    private void applyOnHit(LivingEntity le) {
        Entity owner = getOwner();
        if (kind() == KIND_CHAIN) {
            // A corrente em si só corrompe: prender e puxar é com o chefe (BbBoss#onBoltHit).
            le.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), owner);
            return;
        }
        if (isLance()) {
            le.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), owner);
        } else if (isTwilight()) {
            le.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), owner);
            le.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), owner);
        } else {
            le.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), owner);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
            // O orbe de sombra cura quem o lançou (ou quem o rebateu de volta).
            if (owner instanceof LivingEntity lo && owner.isAlive()) lo.heal(damage * 0.3F);
        }
    }

    private DamageSource source(ServerLevel sl) {
        Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        Entity owner = getOwner();
        return new DamageSource(type, this, owner != null ? owner : this);
    }

    private void impact(ServerLevel sl) {
        Vec3 p = position().add(0, getBbHeight() / 2, 0);
        // A Sentença do Arauto (reserva sem glifos): o chefe solta o 2º estágio onde o orbe estourou.
        if (kind() == KIND_VERDICT && getOwner() instanceof BbBoss boss && boss.isAlive()) boss.onBoltImpact(this, p);
        if (kind() == KIND_CHAIN) {
            sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 1.4F), p.x, p.y, p.z, 14, 0.3, 0.3, 0.3, 0);
            BbFx.sound(sl, p, SoundEvents.CHAIN_HIT, 1.2F, 0.6F);
            discard();
            return;
        }
        if (isLance()) {
            sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 14, 0.15, 0.15, 0.15, 0.12);
            sl.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            BbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
        } else if (isTwilight()) {
            sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.6F), p.x, p.y, p.z, 20, 0.5, 0.5, 0.5, 0);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.6F), p.x, p.y, p.z, 20, 0.5, 0.5, 0.5, 0);
            sl.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 10, 0.4, 0.4, 0.4, 0.1);
            BbFx.sound(sl, p, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
            // Pequena explosão: atinge quem estiver perto do impacto.
            Entity owner = getOwner();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0), e -> e.isAlive()
                    && e != owner && (owner == null || !owner.isAlliedTo(e)) && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                e.hurt(source(sl), damage * 0.4F);
            }
        } else {
            sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 12, 0.3, 0.3, 0.3, 0.05);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.5F), p.x, p.y, p.z, 16, 0.4, 0.4, 0.4, 0);
            BbFx.sound(sl, p, SoundEvents.ENDER_EYE_DEATH, 0.9F, 0.6F);
        }
        discard();
    }

    private void fizzle() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() / 2, getZ(), 6, 0.1, 0.1, 0.1, 0.01);
        }
        discard();
    }

    /**
     * Rebatido por um jogador: perde o teleguiado, o tempo de vida recomeça e segue reto em velocidade fixa
     * (o vanilla daria aceleração contínua, e com inércia 1,0 o orbe nunca pararia de acelerar).
     */
    @Override
    protected void onDeflection(@Nullable Entity entity, boolean byPlayer) {
        super.onDeflection(entity, byPlayer);
        this.accelerationPower = 0.0;
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() > 1.0E-6) {
            setDeltaMovement(v.normalize().scale(1.3));
            hasImpulse = true;
        }
        homing = 0F;
        homingTargetId = -1;
        life = 0;
        if (level() instanceof ServerLevel sl) {
            BbFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 0.6F);
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() / 2, getZ(), 10, 0.2, 0.2, 0.2, 0.3);
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return 0.9F;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BoltDamage", damage);
        tag.putFloat("BoltHoming", homing);
        tag.putInt("BoltLife", life);
        tag.putInt("BoltKind", kind());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("BoltDamage");
        homing = tag.getFloat("BoltHoming");
        life = tag.getInt("BoltLife");
        entityData.set(DATA_KIND, tag.getInt("BoltKind"));
    }

    // ---------------- GeckoLib ----------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, s -> s.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
