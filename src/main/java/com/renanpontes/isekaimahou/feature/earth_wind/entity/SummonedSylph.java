package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Silfide invocada (Invocar Silfide): espirito do vento que voa perto do dono, da Velocidade a ele e aos
 * aliados proximos e corta os inimigos com laminas de vento a distancia. Dura um tempo limitado.
 */
public class SummonedSylph extends PathfinderMob implements GeoEntity, OwnableEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_sylph.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.summoned_sylph.fly");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.summoned_sylph.attack");
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private int life = 900;
    private float power = 1.0F;
    private int ownerMissing;
    private CompoundTag spellTag;
    private SpellContext ctx;

    public SummonedSylph(EntityType<? extends SummonedSylph> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** Invoca uma silfide para {@code ctx.caster()}. Desfaz a anterior do mesmo dono. */
    public static SummonedSylph summon(SpellContext ctx, Vec3 pos, int lifeTicks, float power, LivingEntity target) {
        ServerLevel level = ctx.level();
        LivingEntity owner = ctx.caster();
        if (owner != null) {
            UUID old = ACTIVE.get(owner.getUUID());
            if (old != null && level.getEntity(old) instanceof SummonedSylph s) s.vanish();
        }
        SummonedSylph s = new SummonedSylph(EwRegistries.SYLPH.get(), level);
        s.ownerId = owner != null ? owner.getUUID() : null;
        s.life = lifeTicks;
        s.power = power;
        s.ctx = ctx;
        s.spellTag = ctx.save();
        s.moveTo(pos.x, pos.y, pos.z, owner != null ? owner.getYRot() : 0, 0);
        var hp = s.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(24.0 * power);
        s.setHealth(s.getMaxHealth());
        s.setPersistenceRequired();
        if (target != null && !SummonAi.isFriendly(s, target)) s.setTarget(target);
        level.addFreshEntity(s);
        if (owner != null) ACTIVE.put(owner.getUUID(), s.getUUID());
        EwFx.windSpiral(level, pos.add(0, -0.5, 0), 1.2, 2.2, 30, 0);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        EwFx.sound(level, pos, SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 1.2F);
        EwFx.sound(level, pos, SoundEvents.BREEZE_IDLE_AIR, 1.0F, 1.4F);
        return s;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new StrikeGoal(this));
        goalSelector.addGoal(2, new SummonAi.FollowOwnerGoal<>(this, 1.2, 4.0F, 2.0F, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new SummonAi.DefendOwnerGoal<>(this, false));
        targetSelector.addGoal(2, new SummonAi.DefendOwnerGoal<>(this, true));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && !SummonAi.isFriendly(this, e)));
    }

    /** Voo suave (igual ao do allay): sem gravidade, com atrito no ar. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.02F, input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8));
            } else {
                moveRelative(getSpeed(), input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91));
            }
        }
        calculateEntityAnimation(false);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    public UUID getOwnerUUID() {
        return ownerId;
    }

    @Override
    public LivingEntity getOwner() {
        return SummonAi.owner(this, ownerId);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return SummonAi.isFriendly(this, other) || super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !SummonAi.isFriendly(this, target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null && SummonAi.isFriendly(this, source.getEntity())) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // =====================================================================================
    //  Comportamento
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // rastro de vento cintilante
            if (random.nextInt(2) == 0) {
                level().addParticle(ElementFx.dust(Element.WIND, 0.7F), getRandomX(0.4), getY() + 0.3 + random.nextDouble() * 0.6,
                        getRandomZ(0.4), 0, -0.02, 0);
            }
            if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 0, -0.03, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (--life <= 0) {
            vanish();
            return;
        }
        // uma silfide mais nova do mesmo dono ja existe (esta estava num chunk descarregado): desfaz esta
        if (ownerId != null) {
            UUID current = ACTIVE.putIfAbsent(ownerId, getUUID());
            if (current != null && !current.equals(getUUID())) {
                vanish();
                return;
            }
        }
        LivingEntity owner = getOwner();
        if (ownerId != null && owner == null) {
            if (++ownerMissing > 200) {
                vanish();
                return;
            }
        } else {
            ownerMissing = 0;
        }
        if (owner != null && tickCount % 60 == 0) blessWind(level, owner);
    }

    /** Da Velocidade ao dono e aos aliados proximos (linha de vento ate cada um). */
    private void blessWind(ServerLevel level, LivingEntity owner) {
        int amp = power >= 1.4F ? 1 : 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8),
                e -> e.isAlive() && (e == owner || (e instanceof Player && SummonAi.isFriendly(this, e))))) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, amp, true, true), this);
            EwFx.windLine(level, position().add(0, 0.7, 0), e.position().add(0, e.getBbHeight() * 0.5, 0));
        }
        EwFx.sound(level, position(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.5F, 1.4F);
    }

    /** Corte de vento a distancia (usa o dano de magia de Vento do conjurador quando possivel). */
    void windSlash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        triggerAnim("main", "attack");
        Vec3 from = position().add(0, getBbHeight() * 0.55, 0);
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        EwFx.windLine(level, from, to);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * power;
        SpellContext c = context();
        if (c != null) c.damage(t, base, new SpellStats(), this);
        else t.hurt(damageSources().mobAttack(this), base);
        EwUtil.push(t, to.subtract(from).normalize().scale(0.35), 0.15);
        EwFx.sound(level, to, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.5F);
        EwFx.sound(level, from, SoundEvents.BREEZE_SHOOT, 0.6F, 1.5F);
    }

    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    /** Some num redemoinho (fim do tempo ou dono sumiu). */
    public void vanish() {
        if (level() instanceof ServerLevel level) {
            EwFx.windSpiral(level, position(), 0.8, 1.8, 24, tickCount * 0.3);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            EwFx.sound(level, position(), SoundEvents.ALLAY_DEATH, 0.6F, 1.3F);
        }
        discard();
    }

    /** Esquece os registros de "invocacao atual" (ao fechar o servidor/mundo). */
    public static void clearActive() {
        ACTIVE.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (ownerId != null && reason.shouldDestroy()) ACTIVE.remove(ownerId, getUUID());
        super.remove(reason);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BREEZE_IDLE_AIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("Life", life);
        tag.putFloat("Power", power);
        if (spellTag != null) tag.put("Spell", spellTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        life = tag.contains("Life") ? tag.getInt("Life") : 600;
        power = tag.contains("Power") ? tag.getFloat("Power") : 1.0F;
        spellTag = tag.contains("Spell") ? tag.getCompound("Spell") : null;
        if (ownerId != null) ACTIVE.putIfAbsent(ownerId, getUUID());
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(state.isMoving() ? FLY : IDLE))
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  IA de ataque: mantem distancia, circula o alvo e dispara cortes de vento
    // =====================================================================================

    private static class StrikeGoal extends Goal {
        private final SummonedSylph sylph;
        private int cooldown;
        private double angle;

        StrikeGoal(SummonedSylph sylph) {
            this.sylph = sylph;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = sylph.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            cooldown = 10;
            angle = sylph.random.nextDouble() * Math.PI * 2;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = sylph.getTarget();
            if (t == null) return;
            sylph.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = sylph.distanceToSqr(t);
            boolean sees = sylph.hasLineOfSight(t);
            if (d > 9 * 9 || !sees) {
                sylph.getNavigation().moveTo(t.getX(), t.getY() + 2.5, t.getZ(), 1.3);
            } else if (sylph.tickCount % 15 == 0) {
                angle += 0.9;
                sylph.getNavigation().moveTo(t.getX() + Math.cos(angle) * 5, t.getY() + 2.5, t.getZ() + Math.sin(angle) * 5, 1.1);
            }
            if (--cooldown <= 0 && d < 12 * 12 && sees) {
                sylph.windSlash(t);
                cooldown = 30;
            }
        }
    }
}
