package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil mágico dos chefes: mísseis arcanos teleguiados (Arquimago e ilusões), raios de alma (Lich) e
 * bolas de fogo negro que explodem (Patrono). Dano do tipo "magia" do núcleo; não acerta aliados do chefe.
 * Temporário: não é salvo no mundo.
 */
public class ClassBossBolt extends ThrowableProjectile {
    public enum Kind {
        ARCANE(0xB98CFF), ILLUSION(0xC9B8F0), SOUL(0x5FD8E8), HELLFIRE(0xC0182E);

        public final int color;

        Kind(int color) {
            this.color = color;
        }

        public String texture() {
            return "class_boss_bolt_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ClassBossBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ClassBossBolt.class, EntityDataSerializers.FLOAT);

    private float damage = 4.0F;
    private double homing;
    private int lifetime = 100;
    @Nullable
    private UUID targetId;

    public ClassBossBolt(EntityType<? extends ClassBossBolt> type, Level level) {
        super(type, level);
    }

    /** Cria e lança um projétil. {@code homing} = quanto curva por tick (0 = reto). */
    public static ClassBossBolt fire(LivingEntity owner, Kind kind, Vec3 from, Vec3 direction, double speed, float damage,
                                     double homing, @Nullable LivingEntity target, float size, int lifetime) {
        ClassBossBolt b = new ClassBossBolt(BossesCRegistry.BOLT.get(), owner.level());
        b.setOwner(owner);
        b.setPos(from.x, from.y, from.z);
        b.shoot(direction.x, direction.y, direction.z, (float) speed, 0.0F);
        b.entityData.set(KIND, kind.ordinal());
        b.entityData.set(SIZE, size);
        b.damage = damage;
        b.homing = homing;
        b.lifetime = lifetime;
        b.targetId = target == null ? null : target.getUUID();
        owner.level().addFreshEntity(b);
        return b;
    }

    public Kind kind() {
        Kind[] v = Kind.values();
        int i = entityData.get(KIND);
        return i >= 0 && i < v.length ? v[i] : Kind.ARCANE;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(SIZE, 1.0F);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (level().isClientSide) {
            trail();
            return;
        }
        if (tickCount > lifetime) {
            impact(position(), null);
            return;
        }
        if (homing > 0 && tickCount > 4) steer();
    }

    private void steer() {
        if (targetId == null || !(level() instanceof ServerLevel sl)) return;
        if (!(sl.getEntity(targetId) instanceof LivingEntity t) || !t.isAlive()) return;
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(position());
        if (to.lengthSqr() < 1.0E-4) return;
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-4) return;
        setDeltaMovement(v.normalize().lerp(to.normalize(), homing).normalize().scale(speed));
        hasImpulse = true;
    }

    private void trail() {
        Kind k = kind();
        Vec3 p = position().add(0, getBbHeight() / 2, 0);
        Vec3 m = getDeltaMovement();
        int n = BossFx.reduced() ? 1 : 3;
        for (int i = 0; i < n; i++) {
            double f = i / (double) n;
            double x = p.x - m.x * f, y = p.y - m.y * f, z = p.z - m.z * f;
            level().addParticle(BossFx.dust(k.color, 0.9F * size()), x, y, z, 0, 0, 0);
        }
        switch (k) {
            case ARCANE, ILLUSION -> {
                if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.ENCHANT, p.x, p.y, p.z, -m.x, -m.y + 0.2, -m.z);
                if (k == Kind.ARCANE && random.nextInt(4) == 0) level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0, 0);
            }
            case SOUL -> {
                if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL, p.x, p.y, p.z, 0, 0.02, 0);
            }
            case HELLFIRE -> {
                level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, 0.01, 0);
                if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !BossAllies.isAlly(getOwner(), target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        impact(result.getLocation(), result.getEntity());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) return;
        impact(result.getLocation(), null);
    }

    private DamageSource source() {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(CoreRegistries.SPELL_DAMAGE).map(h -> (Holder<DamageType>) h).orElse(null);
        Entity owner = getOwner();
        return type == null ? damageSources().indirectMagic(this, owner) : new DamageSource(type, this, owner);
    }

    /** Acerto (ou fim do alcance): aplica o efeito do tipo e some. */
    private void impact(Vec3 at, @Nullable Entity hit) {
        if (!(level() instanceof ServerLevel sl)) return;
        Kind k = kind();
        if (k == Kind.HELLFIRE) {
            // Fogo negro: explosão sem quebrar blocos, queima e corrompe.
            double r = 2.5 * size();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), e -> e.isAlive()
                    && !BossAllies.isAlly(getOwner(), e))) {
                double d = e.position().distanceTo(at);
                if (d > r + 0.5 && e != hit) continue;
                float dmg = e == hit ? damage : damage * 0.6F;
                if (e.hurt(source(), dmg)) {
                    e.igniteForSeconds(4);
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), getOwner());
                }
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 30, r * 0.4, 0.4, r * 0.4, 0.06);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, r * 0.3, 0.3, r * 0.3, 0.04);
            sl.sendParticles(BossFx.dust(0x1A0508, 2.0F), at.x, at.y, at.z, 25, r * 0.4, 0.4, r * 0.4, 0);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 0.6F);
        } else {
            if (hit != null && hit.hurt(source(), damage) && hit instanceof LivingEntity le) {
                if (k == Kind.SOUL) le.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), getOwner());
                if (k == Kind.ARCANE) le.knockback(0.3, -getDeltaMovement().x, -getDeltaMovement().z);
            }
            sl.sendParticles(BossFx.dust(k.color, 1.4F), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0);
            sl.sendParticles(k == Kind.SOUL ? ParticleTypes.SOUL : ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.05);
            sl.playSound(null, at.x, at.y, at.z, k == Kind.SOUL ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.HOSTILE, 0.8F, 1.2F + random.nextFloat() * 0.3F);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
    }
}
