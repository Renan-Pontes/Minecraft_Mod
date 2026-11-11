package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Poça de lava temporária do Ignis. Não é um bloco (não escorre nem queima construções): é uma entidade
 * desenhada como lava no chão que queima quem pisa nela e some sozinha. Água por perto apaga a poça.
 */
public class IgnisLavaPool extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(IgnisLavaPool.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(IgnisLavaPool.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BLUE = SynchedEntityData.defineId(IgnisLavaPool.class, EntityDataSerializers.BOOLEAN);

    @Nullable private UUID ownerId;

    public IgnisLavaPool(EntityType<? extends IgnisLavaPool> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, @Nullable LivingEntity owner, Vec3 pos, float radius, int life, boolean blue) {
        BlockPos bp = BlockPos.containing(pos.x, pos.y + 0.2, pos.z);
        if (level.getFluidState(bp).is(FluidTags.WATER)) {
            // Lava na água vira vapor
            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 20, radius * 0.4, 0.3, radius * 0.4, 0.02);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.8F);
            return;
        }
        IgnisLavaPool p = new IgnisLavaPool(BossesARegistry.LAVA_POOL.get(), level);
        p.setPos(pos.x, pos.y + 0.02, pos.z);
        p.ownerId = owner != null ? owner.getUUID() : null;
        p.entityData.set(DATA_RADIUS, radius);
        p.entityData.set(DATA_LIFE, life);
        p.entityData.set(DATA_BLUE, blue);
        level.addFreshEntity(p);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    public int life() { return entityData.get(DATA_LIFE); }

    public boolean isBlue() { return entityData.get(DATA_BLUE); }

    /** Raio visível no momento (cresce no começo, encolhe no fim). */
    public float visibleRadius(float partialTick) {
        float age = tickCount + partialTick;
        float grow = Math.min(1F, age / 8F);
        float shrink = Math.min(1F, Math.max(0F, (life() - age) / 20F));
        return radius() * grow * shrink;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 2.0F);
        builder.define(DATA_LIFE, 160);
        builder.define(DATA_BLUE, false);
    }

    @Override
    public void tick() {
        super.tick();
        float r = visibleRadius(0);
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMOKE,
                        getX() + Math.cos(a) * d, getY() + 0.1, getZ() + Math.sin(a) * d, 0, 0.03, 0);
            }
            if (isBlue() && random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * d, getY() + 0.1,
                        getZ() + Math.sin(a) * d, 0, 0.04, 0);
            }
            return;
        }
        if (tickCount >= life()) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount % 20 == 0 && sl.isRainingAt(blockPosition().above())) {
            // Chuva esfria a poça mais rápido
            entityData.set(DATA_LIFE, Math.max(tickCount + 21, life() - 40));
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.4, getZ(), 6, r * 0.4, 0.1, r * 0.4, 0.01);
        }
        if (tickCount % 30 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 0.6F, 0.9F);
        if (tickCount % 10 != 0) return;
        Entity owner = ownerId != null ? sl.getEntity(ownerId) : null;
        LivingEntity caster = owner instanceof LivingEntity le ? le : null;
        AABB box = new AABB(getX() - r, getY() - 0.5, getZ() - r, getX() + r, getY() + 1.4, getZ() + r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> caster != null ? BossSpells.isFoe(caster, e) : !(e instanceof ElementalGuardian) && !e.fireImmune())) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > (r + e.getBbWidth() * 0.4) * (r + e.getBbWidth() * 0.4)) continue;
            if (caster != null) BossSpells.hit(caster, e, Element.FIRE, isBlue() ? 4F : 3F);
            else e.hurt(damageSources().hotFloor(), 3F);
            e.igniteForSeconds(4);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }
}
