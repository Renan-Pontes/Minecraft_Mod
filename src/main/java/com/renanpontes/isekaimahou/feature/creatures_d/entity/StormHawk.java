package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.SwoopAttackGoal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Falcão da Tempestade: predador dos picos (mais comum em tempestades). Circula alto, mergulha com as
 * garras e invoca raios: um círculo de faíscas marca o chão e, segundos depois, o raio cai ali.
 * Alvos Encharcados levam o dobro e ficam atordoados. Raios o curam; em tempestades fica mais forte.
 */
public class StormHawk extends FlyingElemental implements Enemy {
    private Vec3 strikePos = Vec3.ZERO;

    public StormHawk(EntityType<? extends StormHawk> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.FLYING_SPEED, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public Element element() {
        return Element.WIND;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "cast", 4, 28, 25, 100, 160, false, t -> strike())
                .when(t -> level().canSeeSky(t.blockPosition()))
                .onStart(t -> strikePos = t.position())
                .cooldownScale(() -> level().isThundering() ? 0.55 : 1.0)
                .windup((t, left) -> telegraph(left)));
        goalSelector.addGoal(2, new SwoopAttackGoal(this, 1.6, 30));
        goalSelector.addGoal(5, new FlyWanderGoal(this, 1.0, 8, 18, 16));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                e -> e instanceof Rabbit || e instanceof Chicken));
    }

    /** Aviso do raio: faíscas girando no chão onde ele vai cair. */
    private void telegraph(int left) {
        if (!(level() instanceof ServerLevel sl) || left % 3 != 0) return;
        double r = 1.2 + left * 0.04;
        MobMagic.ring(sl, strikePos, ParticleTypes.ELECTRIC_SPARK, r, 14, 0.02);
        sl.sendParticles(ElementFx.dust(Element.LIGHTNING, 1.2F), strikePos.x, strikePos.y + 0.1, strikePos.z, 4, r * 0.5, 0.05, r * 0.5, 0);
        if (left == 24) playSoundHere(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
    }

    private void strike() {
        if (!(level() instanceof ServerLevel sl)) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(strikePos.x, strikePos.y, strikePos.z);
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        // Dano mágico ignora armadura: valores contidos, pois Encharcado dobra (Eletrocutar)
        float dmg = level().isThundering() ? 7.0F : 5.0F;
        for (LivingEntity e : MobMagic.victimsAround(this, strikePos.add(0, 0.5, 0), 1.8)) {
            boolean soaked = e.hasEffect(CoreRegistries.SOAKED);
            if (e.hurt(damageSources().indirectMagic(this, this), soaked ? dmg * 2 : dmg)) {
                e.igniteForSeconds(2.0F);
                if (soaked) {
                    // Eletrocutar: água + raio
                    e.removeEffect(CoreRegistries.SOAKED);
                    e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 40, 0), this);
                }
            }
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, strikePos.x, strikePos.y + 0.5, strikePos.z, 40, 0.8, 0.8, 0.8, 0.3);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
        }
        if (random.nextInt(4) == 0 || target.hasEffect(CoreRegistries.SOAKED)) {
            target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20, 0), this);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && level().isThundering() && tickCount % 40 == 0) {
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, false));
        }
    }

    /** O raio carrega o falcão em vez de feri-lo. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        heal(6.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 20, 0.5, 0.5, 0.5, 0.2);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.LIGHTNING_BOLT) || super.isInvulnerableTo(source);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        int chance = level().isThundering() ? 2 : 8;
        if (random.nextInt(chance) == 0) {
            Vec3 w = localPoint(-0.1, 0.5, (random.nextBoolean() ? 1 : -1) * (0.4 + random.nextDouble() * 0.8));
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 0, -0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.5F;
    }
}
