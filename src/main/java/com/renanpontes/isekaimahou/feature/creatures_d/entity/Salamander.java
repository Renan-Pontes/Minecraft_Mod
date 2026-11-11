package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Salamandra: lagarto de fogo dos desertos, badlands e Nether. Morde incendiando e cospe bolas de
 * fogo à distância (com aviso de chamas na boca). Imune a fogo, sofre na água e na chuva.
 */
public class Salamander extends ElementalCreature implements Enemy {
    public Salamander(EntityType<? extends Salamander> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setPathfindingMalus(PathType.LAVA, 8.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        setPathfindingMalus(PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.FIRE;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new AbilityGoal(this, "cast", 3.5, 14, 12, 50, 90, true, this::spitFire)
                .windup((t, left) -> {
                    if (left % 3 == 0) serverParticles(ParticleTypes.FLAME, localPoint(0.85, 0.35, 0), 2, 0.08, 0.01);
                }));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Cusparada de fogo: uma bola grande e duas brasas menores em leque. */
    private void spitFire(LivingEntity target) {
        MobMagic.aim(this, target);
        ElementalBolt.shoot(this, target, Element.FIRE, 5.0F, 1.1F, 1.0F, Items.FIRE_CHARGE);
        ElementalBolt.shoot(this, target, Element.FIRE, 2.0F, 0.9F, 9.0F, Items.BLAZE_POWDER);
        ElementalBolt.shoot(this, target, Element.FIRE, 2.0F, 0.9F, 9.0F, Items.BLAZE_POWDER);
        serverParticles(ParticleTypes.LAVA, localPoint(0.9, 0.4, 0), 3, 0.1, 0.0);
        playSoundHere(SoundEvents.BLAZE_SHOOT, 1.0F, 1.3F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.igniteForSeconds(3.0F);
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.25), getY() + 0.5, getRandomZ(0.25), 0, 0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            Vec3 tail = localPoint(-1.4, 0.3, 0);
            level().addParticle(ParticleTypes.FLAME, tail.x, tail.y, tail.z, 0, 0.03, 0);
        }
        if (random.nextInt(12) == 0) bodyParticle(ParticleTypes.SMOKE, 0.8, 0.3, 0.02);
        if (isInWaterRainOrBubble() && random.nextInt(2) == 0) bodyParticle(ParticleTypes.CLOUD, 0.9, 0.4, 0.05);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STRIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.STRIDER_STEP, 0.4F, 1.3F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.25F;
    }
}
