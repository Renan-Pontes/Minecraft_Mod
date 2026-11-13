package com.renanpontes.isekaimahou.feature.end_library.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Fagulha Estelar: estrelinha viva que voa em volta do alvo e dá investidas rápidas (avisa com um brilho antes).
 * Frágil; às vezes deixa cair um Fragmento Estelar. Nasce nas ilhas externas do End, nos geradores da Biblioteca e
 * é invocada pelo Devorador de Estrelas.
 */
public class StarWisp extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_wisp.idle");
    private static final RawAnimation DASH = RawAnimation.begin().thenPlay("animation.star_wisp.dash");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int dashCd = 40;
    private int windup;
    private int dashing;
    private double orbit = Math.random() * Math.PI * 2;

    public StarWisp(EntityType<? extends StarWisp> type, Level level) {
        super(type, level);
        this.xpReward = 6;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            t = null;
        }
        if (dashCd > 0) dashCd--;
        if (t == null) {
            // Vaga devagar, subindo e descendo.
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, Math.sin(tickCount * 0.1) * 0.01, 0));
            return;
        }
        if (dashing > 0) {
            dashing--;
            if (getBoundingBox().inflate(0.4).intersects(t.getBoundingBox())) {
                if (t.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                    t.setDeltaMovement(t.getDeltaMovement().add(getDeltaMovement().scale(0.4)));
                    t.hurtMarked = true;
                }
                dashing = 0;
                setDeltaMovement(getDeltaMovement().scale(-0.4));
            }
            return;
        }
        if (windup > 0) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 2, 0.15, 0.15, 0.15, 0.01);
            if (--windup == 0) {
                Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position());
                setDeltaMovement(to.normalize().scale(0.9));
                dashing = 14;
                triggerAnim("action", "dash");
                playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8F, 1.6F);
            }
            return;
        }
        // Circula em volta do alvo a ~4 blocos, 2 acima da cabeça.
        orbit += 0.07;
        Vec3 goal = t.position().add(Math.cos(orbit) * 4.0, t.getBbHeight() + 1.5, Math.sin(orbit) * 4.0);
        Vec3 d = goal.subtract(position());
        Vec3 v = d.lengthSqr() > 0.09 ? d.normalize().scale(0.3) : d;
        setDeltaMovement(getDeltaMovement().scale(0.7).add(v.scale(0.3)));
        getLookControl().setLookAt(t, 30F, 30F);
        if (dashCd <= 0 && hasLineOfSight(t) && distanceTo(t) < 10) {
            dashCd = 60 + random.nextInt(30);
            windup = 12;
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.8F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(BgFx.reduced() ? 6 : 2) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 0, -0.01, 0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            BgFx.sphere(sl, position().add(0, 0.3, 0), 0.8, 20, ParticleTypes.END_ROD);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP).triggerableAnim("dash", DASH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
