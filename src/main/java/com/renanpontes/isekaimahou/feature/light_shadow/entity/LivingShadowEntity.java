package com.renanpontes.isekaimahou.feature.light_shadow.entity;

import java.util.EnumSet;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Sombra Viva: clone sombrio de quem conjurou. Defende o dono, ataca o que ele ataca e caça monstros por perto.
 * Os golpes são dano de Sombra creditado ao dono (XP de abate e contadores vão para ele). Dissolve no fim do tempo.
 */
public class LivingShadowEntity extends PathfinderMob implements OwnableEntity, GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.living_shadow.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.living_shadow.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.living_shadow.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int lifetime = 600;
    private float power = 1.0F;

    public LivingShadowEntity(EntityType<? extends LivingShadowEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0);
    }

    public void setup(LivingEntity owner, int lifetime, float power) {
        this.owner = owner != null ? owner.getUUID() : null;
        this.lifetime = lifetime;
        this.power = power;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(3, new FollowOwnerGoal());
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerTargetGoal(true));
        targetSelector.addGoal(2, new OwnerTargetGoal(false));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper) && !isAlliedTo(e) && !isOwnersFriend(e)));
    }

    // ------------------------------------------------------------------ dono
    @Override
    public UUID getOwnerUUID() {
        return owner;
    }

    @Override
    public LivingEntity getOwner() {
        if (owner == null) return null;
        if (level() instanceof ServerLevel sl) return LsUtil.owner(sl, owner);
        return level().getPlayerByUUID(owner);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (owner != null && (owner.equals(e.getUUID()) || (e instanceof OwnableEntity oe && owner.equals(oe.getOwnerUUID()))
                || owner.equals(LsUtil.possessor(e)))) {
            return true;
        }
        return super.isAlliedTo(e);
    }

    private boolean isOwnersFriend(Entity e) {
        return owner != null && level() instanceof ServerLevel sl && LsUtil.isAlly(sl, owner, e);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isOwnersFriend(target) && super.canAttack(target);
    }

    // ------------------------------------------------------------------ comportamento
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        LivingEntity o = getOwner();
        // Só invocações (com dono) são temporárias; as de ovo ficam e caçam monstros por perto
        if (owner != null && (--lifetime <= 0 || (o == null && tickCount > 40))) {
            dissolve(sl);
            return;
        }
        // Forja de Invocações: guardando um posto (Guardar/Ficar), a sombra não volta sozinha para o dono
        if (o != null && getTarget() == null && distanceToSqr(o) > 24 * 24 && SummonUtil.forgePost(this) == null) {
            LsFx.shadowPuff(sl, position(), getBbHeight());
            teleportTo(o.getX(), o.getY(), o.getZ());
            LsFx.shadowPuff(sl, position(), getBbHeight());
        }
        if (getTarget() != null && isOwnersFriend(getTarget())) setTarget(null);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(level() instanceof ServerLevel sl)) return false;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * power;
        boolean ok = target instanceof LivingEntity le
                ? LsUtil.spellHurt(sl, le, dmg, this, getOwner(), Element.SHADOW)
                : target.hurt(LsUtil.spellSource(sl, this, getOwner()), dmg);
        if (ok) {
            triggerAnim("action", "attack");
            setLastHurtMob(target);
            sl.sendParticles(Vfx.ink(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.25, 0.3, 0.25, 0.05);
            sl.sendParticles(LsFx.dust(LsFx.VIOLET, 1.1F), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0);
            LsFx.sound(sl, position(), SoundEvents.PHANTOM_BITE, 0.8F, 0.6F);
        }
        return ok;
    }

    private void dissolve(ServerLevel sl) {
        LsFx.shadowPuff(sl, position(), getBbHeight());
        LsFx.sound(sl, position(), SoundEvents.VEX_DEATH, 0.7F, 0.5F);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            // Fumaça escorrendo do corpo
            level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.SMOKE,
                    getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * getBbHeight(),
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel sl && source.getEntity() != null && owner != null && LsUtil.isAlly(sl, owner, source.getEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
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
        return 0.5F + random.nextFloat() * 0.15F;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) LsFx.shadowPuff(sl, position(), getBbHeight());
        super.die(source);
    }

    // ------------------------------------------------------------------ salvar
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Power", power);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("Power")) power = tag.getFloat("Power");
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP).triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ IA
    /** Ataca quem feriu o dono ({@code defend}) ou quem o dono atacou. */
    private class OwnerTargetGoal extends TargetGoal {
        private final boolean defend;
        private int lastStamp;
        private LivingEntity candidate;

        OwnerTargetGoal(boolean defend) {
            super(LivingShadowEntity.this, false);
            this.defend = defend;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = getOwner();
            if (o == null) return false;
            int stamp;
            if (defend) {
                candidate = o.getLastHurtByMob();
                stamp = o.getLastHurtByMobTimestamp();
            } else {
                candidate = o.getLastHurtMob();
                stamp = o.getLastHurtMobTimestamp();
                if (o instanceof Mob m && m.getTarget() != null) {
                    candidate = m.getTarget();
                    stamp = lastStamp + 1;
                }
            }
            return stamp != lastStamp && candidate != null && !isOwnersFriend(candidate)
                    && canAttack(candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            LivingEntity o = getOwner();
            if (o != null) lastStamp = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            super.start();
        }
    }

    /** Sem alvo, acompanha o dono. */
    private class FollowOwnerGoal extends Goal {
        private LivingEntity o;
        private int recalc;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            o = getOwner();
            return o != null && getTarget() == null && distanceToSqr(o) > 25;
        }

        @Override
        public boolean canContinueToUse() {
            return o != null && o.isAlive() && getTarget() == null && distanceToSqr(o) > 6.25 && !getNavigation().isDone();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(o, 10F, getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                getNavigation().moveTo(o, 1.2);
            }
        }

        @Override
        public void stop() {
            o = null;
            getNavigation().stop();
        }
    }
}
