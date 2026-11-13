package com.renanpontes.isekaimahou.feature.end_library.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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
 * Escriba do Vazio: bibliotecário encapuzado que flutua com um livro aberto. Mantém distância e lê uma "Linha de
 * Gravidade" (faixa marcada no chão): quem fica nela leva dano arcano e Levitação curta. Ao levar dano às vezes some
 * num estalo e reaparece perto (como um Enderman). Vive nas ilhas externas do End e nos geradores da Biblioteca.
 */
public class VoidScribe extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.void_scribe.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.void_scribe.walk");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.void_scribe.cast");
    private static final int COLOR = 0x7FE8FF;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int castCd = 40;
    private int blinkCd;

    public VoidScribe(EntityType<? extends VoidScribe> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 44.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CastGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && blinkCd <= 0 && random.nextFloat() < 0.35F
                && source.getEntity() instanceof LivingEntity attacker) {
            blinkCd = 100;
            Vec3 from = position();
            if (BossKit.blinkNear(this, attacker, 7.0)) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.8, 0.3, 0.3);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.3F);
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (blinkCd > 0) blinkCd--;
        if (level().isClientSide && random.nextInt(BgFx.reduced() ? 8 : 3) == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.6), getY() + 1.2 + random.nextDouble(), getRandomZ(0.6), 0, 0.2, 0);
        }
    }

    /** Fica a 6–12 blocos do alvo e lê a Linha de Gravidade. */
    private final class CastGoal extends Goal {
        CastGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null || !(level() instanceof ServerLevel sl)) return;
            getLookControl().setLookAt(t, 30F, 30F);
            double d = distanceTo(t);
            if (d > 12 || !hasLineOfSight(t)) {
                if (tickCount % 10 == 0) getNavigation().moveTo(t, 1.0);
            } else if (d < 6) {
                Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : away.normalize();
                Vec3 to = position().add(away.scale(5));
                if (tickCount % 10 == 0) getNavigation().moveTo(to.x, to.y, to.z, 1.1);
            } else {
                getNavigation().stop();
            }
            if (--castCd <= 0 && d < 16 && hasLineOfSight(t)) {
                castCd = 70 + random.nextInt(30);
                cast(sl, t);
            }
        }
    }

    private void cast(ServerLevel sl, LivingEntity t) {
        triggerAnim("action", "cast");
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.4F);
        Vec3 from = position();
        Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-3) return;
        dir = dir.normalize();
        Vec3 to = from.add(dir.scale(Math.min(18, distanceTo(t) + 3)));
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 1.6F).warmup(18).color(COLOR).owner(this).noRunes()
                .castName(Component.translatable("attack.isekai_mahou.void_scribe.gravity_line")).element(Element.ARCANE), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (e == this || e instanceof VoidScribe || e instanceof StarWisp) continue;
                        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                        if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1), this);
                        }
                    }
                    BgFx.line(sl, from.add(0, 1.0, 0), to.add(0, 1.0, 0), BgFx.dust(COLOR, 1.1F), 2.0);
                    playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.2F);
                });
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BOOK_PAGE_TURN;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ GeckoLib

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
