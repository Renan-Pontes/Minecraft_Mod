package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Homem-Lagarto: guerreiro do pântano com lança (alcance maior), anda em grupos, nada muito bem e respira
 * debaixo d'água (tag minecraft:can_breathe_under_water, no merge do módulo). Às vezes aparece de dia nos pântanos.
 */
public class LizardmanEntity extends CbMonster {

    public LizardmanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 7;
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0)
                .add(NeoForgeMod.SWIM_SPEED, 2.0);
    }

    /** Escuro como monstro comum, ou 1 em 4 com qualquer luz (são nativos do pântano). */
    public static boolean checkSpawn(EntityType<LizardmanEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)
                && (random.nextInt(4) == 0 || Monster.isDarkEnoughToSpawn(level, pos, random));
    }

    @Override
    public CbFaction faction() {
        return CbFaction.LIZARDFOLK;
    }

    @Override
    protected String animId() {
        return "lizardman";
    }

    @Override
    protected float voicePitch() {
        return 0.8F;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    protected String loopAnim(boolean moving) {
        if (isInWater() && moving) return "swim";
        return super.loopAnim(moving);
    }

    /** Lança: alcança um pouco mais longe que um soco. */
    @Override
    protected AABB getAttackBoundingBox() {
        return super.getAttackBoundingBox().inflate(0.9, 0.0, 0.9);
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        // Na água persegue o alvo também na vertical (sobe/desce em direção a ele).
        if (isControlledByLocalInstance() && isInWater() && getTarget() != null) {
            double dy = getTarget().getY() - getY();
            setDeltaMovement(getDeltaMovement().add(0, Math.signum(dy) * Math.min(0.04, Math.abs(dy) * 0.02), 0));
        }
        super.travel(input);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAT_HISS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playAttackSound() {
        playSound(SoundEvents.TRIDENT_HIT, 0.7F, 1.2F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }
}
