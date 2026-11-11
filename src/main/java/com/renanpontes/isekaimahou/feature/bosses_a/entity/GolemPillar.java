package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Pilar de pedra dos Pilares Ascendentes (Golem): sobe do chão de repente, fica um instante e afunda de volta.
 * Só visual (o dano vem do círculo telegrafado); não colide, não quebra blocos e não é salvo.
 */
public class GolemPillar extends Entity {
    public static final int LIFE = 34;
    private static final int RISE_TICKS = 3;
    private static final int SINK_TICKS = 10;

    public GolemPillar(EntityType<? extends GolemPillar> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 ground) {
        GolemPillar p = new GolemPillar(BossesARegistry.GOLEM_PILLAR.get(), level);
        p.setPos(ground.x, ground.y, ground.z);
        p.setYRot(level.random.nextFloat() * 360F);
        level.addFreshEntity(p);
    }

    /** Quanto do pilar está para fora do chão (0..1). */
    public float rise(float partialTick) {
        float age = tickCount + partialTick;
        if (age < RISE_TICKS) return age / RISE_TICKS;
        float left = LIFE - age;
        return left < SINK_TICKS ? Math.max(0F, left / SINK_TICKS) : 1F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount < RISE_TICKS + 2) {
                for (int i = 0; i < 6; i++) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
                            getX() + (random.nextDouble() - 0.5) * 1.6, getY() + random.nextDouble() * 2.5,
                            getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.2, 0);
                }
            }
            return;
        }
        if (tickCount >= LIFE) discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

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
}
