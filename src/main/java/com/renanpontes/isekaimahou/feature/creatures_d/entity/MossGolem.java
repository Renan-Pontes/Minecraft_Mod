package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Golem de Musgo: guardião lento das cavernas exuberantes e selvas. Neutro, mas quando provocado
 * soca arremessando para cima e bate no chão (aviso: folhas caindo), enraizando quem estiver perto.
 * Faz fotossíntese: regenera na luz, na chuva ou sobre musgo.
 */
public class MossGolem extends ElementalCreature {
    public MossGolem(EntityType<? extends MossGolem> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public Element element() {
        return Element.EARTH;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "slam"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new AbilityGoal(this, "slam", 0, 4.5, 18, 100, 160, true, t -> slam())
                .windup((t, left) -> {
                    if (left % 2 == 0) serverParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, position().add(0, 2.4, 0), 3, 0.8, 0);
                }));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    /** Pancada no chão: dano em área, enraíza e arremessa para cima. */
    private void slam() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 c = position();
        BlockParticleOption moss = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState());
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        for (double r = 1.0; r <= 4.0; r += 1.0) {
            MobMagic.ring(sl, c, moss, r, (int) (r * 10), 0.15);
            MobMagic.ring(sl, c, dirt, r + 0.5, (int) (r * 6), 0.1);
        }
        for (LivingEntity e : MobMagic.victimsAround(this, c, 4.5)) {
            if (e.hurt(damageSources().mobAttack(this), 7.0F)) {
                e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 50, 0), this);
                MobMagic.knockAway(e, c, 0.4, 0.5);
            }
        }
        playSoundHere(SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
        playSoundHere(SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.6F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
        target.hurtMarked = true;
        playSoundHere(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 40 != 0 || getHealth() >= getMaxHealth()) return;
        // Fotossíntese
        BlockState below = level().getBlockState(blockPosition().below());
        boolean fed = isInWaterRainOrBubble() || below.is(Blocks.MOSS_BLOCK) || level().getMaxLocalRawBrightness(blockPosition()) > 11;
        if (fed) {
            heal(1.0F);
            serverParticles(ParticleTypes.HAPPY_VILLAGER, position().add(0, 1.6, 0), 3, 0.5, 0);
        }
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(14) == 0) bodyParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, 0.9, 0.6, 0);
        if (random.nextInt(40) == 0) bodyParticle(ParticleTypes.SPORE_BLOSSOM_AIR, 0.6, 0.8, 0);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.8F, 0.8F);
        playSound(SoundEvents.MOSS_STEP, 0.8F, 1.0F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7F;
    }
}
