package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerUtil;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Lobo do Ranger: o companheiro que LUTA. Morde, salta nos alvos marcados e, no estágio Alfa, cada mordida faz o
 * alvo sangrar (lentidão) e uiva dando força ao dono. Cresce com a classe.
 */
public class RangerWolfEntity extends RangerCompanion {
    public static final String ID = "ranger_wolf";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ranger_wolf.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ranger_wolf.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.ranger_wolf.run");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.ranger_wolf.sit");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.ranger_wolf.bite");
    private static final RawAnimation HOWL = RawAnimation.begin().thenPlay("animation.ranger_wolf.howl");

    // ---------------- Despertar da Deusa Loba (glifo ranger_wolf_god) ----------------
    private static final EntityDataAccessor<Boolean> DATA_GOD = SynchedEntityData.defineId(RangerWolfEntity.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation GOD_SCALE = IsekaiMahou.id("ranger_wolf_god_scale");
    private static final ResourceLocation GOD_HEALTH = IsekaiMahou.id("ranger_wolf_god_health");
    private static final ResourceLocation GOD_DAMAGE = IsekaiMahou.id("ranger_wolf_god_damage");
    private static final ResourceLocation GOD_ARMOR = IsekaiMahou.id("ranger_wolf_god_armor");
    private static final ResourceLocation GOD_SPEED = IsekaiMahou.id("ranger_wolf_god_speed");
    private static final ResourceLocation GOD_KNOCKBACK = IsekaiMahou.id("ranger_wolf_god_knockback");
    private static final ResourceLocation GOD_STEP = IsekaiMahou.id("ranger_wolf_god_step");
    /** Ticks que faltam da forma divina (so no servidor; nao e salvo: recarregar o mundo volta ao lobo normal). */
    private int godTicks;
    private boolean sweeping;

    public RangerWolfEntity(EntityType<? extends RangerWolfEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GOD, false);
    }

    /** Esta na forma de Deusa Loba? (o renderer troca a textura) */
    public boolean isGod() {
        return entityData.get(DATA_GOD);
    }

    /** Estagio para a textura: 0..2 = Filhote/Adulto/Alfa; 3 = Deusa Loba. */
    public int textureStage() {
        return isGod() ? 3 : stage();
    }

    /**
     * Desperta a Deusa Loba por {@code ticks}: tamanho x2,5, +80 de vida (cura tudo), mordida +14 x poder, armadura,
     * velocidade e firmeza. Despertar de novo renova o tempo.
     */
    public void awaken(int ticks, float power) {
        godTicks = Math.max(godTicks, ticks);
        boolean was = isGod();
        entityData.set(DATA_GOD, true);
        mod(Attributes.SCALE, GOD_SCALE, 1.5, AttributeModifier.Operation.ADD_VALUE);
        mod(Attributes.MAX_HEALTH, GOD_HEALTH, 80.0, AttributeModifier.Operation.ADD_VALUE);
        mod(Attributes.ATTACK_DAMAGE, GOD_DAMAGE, 14.0 * Math.max(0.5F, power), AttributeModifier.Operation.ADD_VALUE);
        mod(Attributes.ARMOR, GOD_ARMOR, 10.0, AttributeModifier.Operation.ADD_VALUE);
        mod(Attributes.MOVEMENT_SPEED, GOD_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(Attributes.KNOCKBACK_RESISTANCE, GOD_KNOCKBACK, 0.8, AttributeModifier.Operation.ADD_VALUE);
        mod(Attributes.STEP_HEIGHT, GOD_STEP, 1.0, AttributeModifier.Operation.ADD_VALUE);
        setHealth(getMaxHealth());
        if (level() instanceof ServerLevel sl) {
            for (int k = 0; k < 3; k++) {
                RangerFx.ring(sl, position().add(0, 0.2 + k * 0.8, 0), 1.5 + k * 0.6, 32, WeaponFx.dust(k == 1 ? 0xC8303C : 0xF4F4FF, 1.5F), 0);
            }
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 40, 1.0, 1.0, 1.0, 0.1);
            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 2, getZ(), 40, 2.0, 1.0, 2.0, 0.05);
            RangerFx.sound(sl, position(), SoundEvents.WOLF_HOWL, 2.0F, 0.5F);
            RangerFx.sound(sl, position(), SoundEvents.RAVAGER_ROAR, 1.0F, 0.7F);
            if (!was) RangerFx.sound(sl, position(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.0F, 0.8F);
        }
        triggerAnim("action", "howl");
    }

    private void mod(Holder<Attribute> attr, ResourceLocation id, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, v, op));
    }

    /** Volta a ser o lobo normal. */
    private void endGod(ServerLevel sl) {
        godTicks = 0;
        entityData.set(DATA_GOD, false);
        for (Holder<Attribute> attr : java.util.List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
                Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE, Attributes.STEP_HEIGHT)) {
            AttributeInstance inst = getAttribute(attr);
            if (inst == null) continue;
            for (ResourceLocation id : java.util.List.of(GOD_SCALE, GOD_HEALTH, GOD_DAMAGE, GOD_ARMOR, GOD_SPEED, GOD_KNOCKBACK, GOD_STEP)) {
                inst.removeModifier(id);
            }
        }
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.6, getZ(), 30, 0.8, 0.6, 0.8, 0.05);
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1, getZ(), 20, 1.0, 0.6, 1.0, 0.03);
        RangerFx.sound(sl, position(), SoundEvents.WOLF_WHINE, 1.0F, 0.8F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl) || !isGod()) return;
        if (godTicks <= 0 || --godTicks <= 0) {
            endGod(sl);
            return;
        }
        double h = getBbHeight();
        if (tickCount % 2 == 0) {
            sl.sendParticles(WeaponFx.dust(tickCount % 6 == 0 ? 0xC8303C : 0xF4F4FF, 1.2F), getX(), getY() + h * 0.7, getZ(), 2,
                    getBbWidth() * 0.4, h * 0.3, getBbWidth() * 0.4, 0);
        }
        if (tickCount % 8 == 0) sl.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + h, getZ(), 2, 1.0, 0.3, 1.0, 0.02);
        // Bencao da Floresta: o dono perto regenera
        if (tickCount % 20 == 0 && getOwner() instanceof LivingEntity o && o.distanceToSqr(this) < 16 * 16) {
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true));
        }
        // uivo de terror a cada 5 s
        if (tickCount % 100 == 0) {
            triggerAnim("action", "howl");
            RangerFx.sound(sl, position(), SoundEvents.WOLF_HOWL, 1.8F, 0.5F);
            LivingEntity owner = getOwner();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10),
                    x -> x.isAlive() && x != this && x != owner && x instanceof Enemy && !RangerUtil.isFriend(owner, x))) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), this);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 1, 0.1, 0.1, 0.1, 0);
            }
            RangerFx.ring(sl, position().add(0, 0.2, 0), 10, 48, WeaponFx.dust(0xF4F4FF, 1.2F), 0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected double healthPerLevel() { return 9.0; }

    @Override
    protected double damagePerLevel() { return 1.6; }

    @Override
    public String slotKey() { return RangerCompanions.WOLF; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2, 10.0F, 3.0F));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new MarkedTargetGoal());
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, this::huntable));
    }

    /** Ordem do dono: salta até o alvo e rosna. */
    @Override
    public void command(LivingEntity target) {
        super.command(target);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 d = target.position().subtract(position());
        double h = d.horizontalDistance();
        if (h > 1.5 && h < 14 && onGround()) {
            Vec3 v = d.multiply(1, 0, 1).normalize().scale(Math.min(1.4, 0.25 + h * 0.09));
            setDeltaMovement(v.x, 0.45 + Math.min(0.3, h * 0.02), v.z);
            hasImpulse = true;
        }
        triggerAnim("action", "howl");
        RangerFx.sound(sl, position(), SoundEvents.WOLF_GROWL, 1.0F, 0.8F);
    }

    /** Uivo (chamado/volta): no Alfa, o dono ganha Força por alguns segundos. */
    public void howl() {
        triggerAnim("action", "howl");
        if (!(level() instanceof ServerLevel sl)) return;
        RangerFx.sound(sl, position(), SoundEvents.WOLF_HOWL, 1.2F, stage() == 2 ? 0.7F : 0.9F);
        if (stage() == 2 && getOwner() instanceof LivingEntity o && o.distanceToSqr(this) < 16 * 16) {
            o.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0, true, true));
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && isGod() && !sweeping && level() instanceof ServerLevel sl) {
            // a mordida da Deusa pega tudo em volta (metade do dano)
            sweeping = true;
            try {
                float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F;
                LivingEntity owner = getOwner();
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
                        x -> x.isAlive() && x != target && x != this && x != owner && x instanceof Enemy && !RangerUtil.isFriend(owner, x))) {
                    e.hurt(damageSources().mobAttack(this), dmg);
                }
            } finally {
                sweeping = false;
            }
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3, 0.6, 0.2, 0.6, 0);
            RangerFx.sound(sl, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        }
        if (hit) {
            triggerAnim("action", "bite");
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
            }
            if (stage() >= 1 && target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + 20 * stage(), stage() - 1), this);
            }
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getTarget() != null ? SoundEvents.WOLF_GROWL : SoundEvents.WOLF_PANT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.95F - 0.08F * stage();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RangerWolfEntity>(this, "movement", 4, state -> {
            if (isInSittingPose()) return state.setAndContinue(SIT);
            if (!state.isMoving()) return state.setAndContinue(IDLE);
            return state.setAndContinue(isAggressive() || getDeltaMovement().horizontalDistanceSqr() > 0.03 ? RUN : WALK);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE).triggerableAnim("howl", HOWL));
    }
}
