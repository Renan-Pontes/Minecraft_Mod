package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.SwoopAttackGoal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Diabrete de Brasa: diabinho voador das badlands e do Nether. Mergulha arranhando (queima),
 * foge rindo e atira rajadas de três brasas. Imune a fogo, sofre na água.
 */
public class EmberImp extends FlyingElemental implements Enemy {
    private int volley;
    private int volleyDelay;
    private LivingEntity volleyTarget;

    public EmberImp(EntityType<? extends EmberImp> type, Level level) {
        super(type, level);
        this.xpReward = 7;
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        setPathfindingMalus(PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
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
        goalSelector.addGoal(1, new AbilityGoal(this, "cast", 3, 16, 10, 60, 100, false, t -> {
            volley = 3;
            volleyDelay = 0;
            volleyTarget = t;
        }).windup((t, left) -> serverParticles(ParticleTypes.FLAME, localPoint(0.3, 1.0, 0), 2, 0.2, 0.01)));
        goalSelector.addGoal(2, new SwoopAttackGoal(this, 1.3, 40));
        goalSelector.addGoal(5, new FlyWanderGoal(this, 1.0, 2, 6, 10));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Rajada: as brasas saem uma a uma, mesmo enquanto ele voa
        if (volley > 0 && --volleyDelay <= 0) {
            if (volleyTarget != null && volleyTarget.isAlive()) {
                ElementalBolt.shoot(this, volleyTarget, Element.FIRE, 3.0F, 1.2F, 5.0F, Items.FIRE_CHARGE);
                playSoundHere(SoundEvents.BLAZE_SHOOT, 0.6F, 1.6F);
            }
            volley--;
            volleyDelay = 6;
        }
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.igniteForSeconds(2.0F);
        playSoundHere(SoundEvents.VEX_CHARGE, 0.8F, 1.3F);
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
        Vec3 tail = localPoint(-0.55, 0.3, 0);
        if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.FLAME, tail.x, tail.y, tail.z, 0, 0.01, 0);
        if (random.nextInt(8) == 0) bodyParticle(ParticleTypes.SMOKE, 0.6, 0.3, 0.02);
        if (random.nextInt(30) == 0) bodyParticle(ParticleTypes.LAVA, 0.5, 0.2, 0.0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.8F;
    }
}
