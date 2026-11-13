package com.renanpontes.isekaimahou.feature.depths.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Verme Cavador: larva das profundezas (e cria do Verme-Rei). Longe do alvo ele MERGULHA no chão (só um montinho de
 * terra andando, intocável) e irrompe embaixo da vítima com uma mordida que joga para cima e faz sangrar.
 * Fica 6 s sem poder cavar de novo — a hora de bater.
 */
public class DepthsBurrower extends DepthsMob {
    private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData.defineId(DepthsBurrower.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_UNDER = 80;

    private int burrowCd = 60;
    private int sinking;
    private int underTicks;

    public DepthsBurrower(EntityType<? extends DepthsBurrower> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected String modelId() {
        return "depths_burrower";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack", "burrow", "emerge");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROWED, false);
    }

    public boolean isBurrowed() {
        return entityData.get(DATA_BURROWED);
    }

    private void setBurrowed(boolean b) {
        entityData.set(DATA_BURROWED, b);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("body", "mound");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return "mound".equals(bone) == isBurrowed();
    }

    @Override
    protected RawAnimation baseAnimation(boolean moving) {
        if (isBurrowed()) return anim("burrowed", true);
        return super.baseAnimation(moving);
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !isBurrowed();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBurrowed() && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        // Enterrado ele só morde ao sair da terra (emerge).
        if (isBurrowed() || sinking > 0) return false;
        action("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && random.nextFloat() < 0.3F) {
            Statuses.apply(le, Statuses.Kind.BLEEDING, 1, 60, this, 1F);
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity target = getTarget();
        if (sinking > 0) {
            getNavigation().stop();
            if (--sinking == 0) {
                setBurrowed(true);
                underTicks = 0;
            }
            return;
        }
        if (isBurrowed()) {
            underTicks++;
            if (tickCount % 3 == 0) dirt(sl, 3);
            if (target != null && tickCount % 8 == 0) getNavigation().moveTo(target, 1.7);
            if (target == null || underTicks > MAX_UNDER || distanceToSqr(target) < 2.6 * 2.6) emerge(sl, target);
            return;
        }
        if (burrowCd > 0) burrowCd--;
        if (target != null && burrowCd <= 0 && onGround() && distanceToSqr(target) > 36 && distanceToSqr(target) < 400) {
            sinking = 8;
            action("burrow");
            playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.7F);
            dirt(sl, 10);
        }
    }

    private void emerge(ServerLevel sl, LivingEntity target) {
        setBurrowed(false);
        burrowCd = 120;
        action("emerge");
        dirt(sl, 16);
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.9F);
        if (target != null && distanceToSqr(target) < 3.0 * 3.0 && target.hurt(damageSources().mobAttack(this),
                (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.3F)) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.45, 0));
            target.hurtMarked = true;
            Statuses.apply(target, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
        }
    }

    private void dirt(ServerLevel sl, int count) {
        BlockPos under = blockPosition().below();
        BlockState state = sl.getBlockState(under);
        if (state.isAir()) state = Blocks.DIRT.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.1, getZ(), count, 0.3, 0.05, 0.3, 0.1);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return isBurrowed() ? null : SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBurrowed(tag.getBoolean("Burrowed"));
    }
}
