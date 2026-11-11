package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ilusão do Arquimago: parece ele, mas para olhos atentos é translúcida, NÃO tem sombra, os olhos e o cristal do
 * cajado não brilham e não há runas aos pés. Atira mísseis fracos e se desfaz com um único golpe. Some sozinha
 * depois de ~15 s ou quando o Arquimago morre. Não é salva no mundo.
 */
public class ArchmageIllusion extends Monster implements GeoEntity, BoneToggles {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.renegade_archmage.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.renegade_archmage.walk");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.renegade_archmage.cast");
    private static final int COLOR = 0xB98CFF;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = 300;
    private int castCd = 40;
    private int strafeDir = 1;

    public ArchmageIllusion(EntityType<? extends ArchmageIllusion> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    public void setOwner(RenegadeArchmage owner) {
        this.ownerId = owner.getUUID();
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (--life <= 0 || ownerId == null || !(sl.getEntity(ownerId) instanceof RenegadeArchmage owner) || !owner.isAlive()) {
            vanish();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null) {
            target = owner.getTarget();
            if (target != null) setTarget(target);
            else return;
        }
        if (castCd > 0) castCd--;
        double d = distanceTo(target);
        if (castCd <= 0 && hasLineOfSight(target)) {
            Vec3 from = position().add(0, 1.6, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(0.6));
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize().add(0, 0.25, 0).normalize();
            ClassBossBolt.fire(this, ClassBossBolt.Kind.ILLUSION, from, dir, 0.5, 2.0F, 0.08, target, 0.8F, 80);
            triggerAnim("action", "cast");
            playSound(SoundEvents.EVOKER_CAST_SPELL, 0.8F, 1.6F);
            castCd = 50 + random.nextInt(30);
        }
        if (d > 12 || !hasLineOfSight(target)) {
            if (tickCount % 10 == 0) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
            if (tickCount % 40 == 0) strafeDir = -strafeDir;
            getMoveControl().strafe(d < 6 ? -0.4F : 0.0F, strafeDir * 0.45F);
        }
        lookAt(target, 30F, 30F);
        getLookControl().setLookAt(target, 30F, 30F);
    }

    /** Desfaz a ilusão em partículas. */
    public void vanish() {
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            sl.sendParticles(BossFx.dust(COLOR, 1.5F), getX(), getY() + 1.2, getZ(), 25, 0.3, 0.8, 0.3, 0);
            sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.2, getZ(), 30, 0.4, 0.8, 0.4, 0.6);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, getSoundSource(), 1.0F, 1.5F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, getSoundSource(), 0.6F, 1.6F);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) return false;
        // Qualquer golpe desfaz a ilusão (sem animação de morte).
        if (source.getEntity() != null && BossAllies.isAlly(source.getEntity(), this)) return false;
        vanish();
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** Nada de barco/carrinho (nem para ilusões). */
    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
        return false;
    }

    @Override
    public List<String> toggledBones() {
        return List.of("shield");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return !bone.equals("shield");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP).triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
