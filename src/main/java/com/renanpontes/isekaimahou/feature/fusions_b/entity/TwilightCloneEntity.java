package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Clone do Crepúsculo (Dualidade): uma cópia translúcida do conjurador. O clone de LUZ segue o dono e cura
 * o aliado mais ferido por perto; o clone de SOMBRA caça os inimigos e enfraquece quem golpeia.
 */
public class TwilightCloneEntity extends FbSummon {
    private static final EntityDataAccessor<Boolean> LIGHT = SynchedEntityData.defineId(TwilightCloneEntity.class, EntityDataSerializers.BOOLEAN);

    public TwilightCloneEntity(EntityType<? extends TwilightCloneEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 4.0);
    }

    /** Cria (ou renova) o clone de luz ou de sombra do dono. */
    @Nullable
    public static TwilightCloneEntity create(ServerLevel level, LivingEntity owner, boolean light, Vec3 at, int life, float power,
                                             @Nullable LivingEntity target) {
        for (TwilightCloneEntity c : level.getEntities(FbRegistries.TWILIGHT_CLONE.get(),
                c -> c.isAlive() && owner.getUUID().equals(c.getOwnerUUID()) && c.isLight() == light)) {
            c.renew(life);
            if (target != null && !light) c.setTarget(target);
            return c;
        }
        TwilightCloneEntity c = FbRegistries.TWILIGHT_CLONE.get().create(level);
        if (c == null) return null;
        c.entityData.set(LIGHT, light);
        c.rebuildGoals();
        Vec3 p = c.findSpot(at, false);
        c.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
        c.setYHeadRot(owner.getYRot());
        c.setup(owner, life, power);
        level.addFreshEntity(c);
        c.spawnFx(level);
        if (target != null && !light) c.setTarget(target);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LIGHT, true);
    }

    public boolean isLight() { return entityData.get(LIGHT); }

    @Override
    protected void registerGoals() {
        // Os objetivos dependem do tipo (luz/sombra), que só é conhecido depois: ver rebuildGoals()
    }

    private void rebuildGoals() {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        if (isLight()) {
            goalSelector.addGoal(1, new HealAlliesGoal(this));
        } else {
            goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
            addTargetGoals();
        }
        goalSelector.addGoal(2, new FollowOwnerGoal(this, isLight() ? 3.0F : 6.0F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        swing(InteractionHand.MAIN_HAND);
        boolean ok = target.hurt(FbUtil.summonSpellSource(this, getOwner()), dmg);
        if (ok && target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), getOwner());
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(Vfx.ink(), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.2, 0.2, 0.03);
                sl.sendParticles(FbFx.dust(FbFx.TWILIGHT_SHADOW, 1.2F), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(),
                        6, 0.25, 0.25, 0.25, 0);
            }
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.7F);
        }
        return ok;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }

    // =====================================================================================
    //  Visual
    // =====================================================================================

    @Override
    protected void spawnFx(ServerLevel level) {
        Vec3 c = position().add(0, 1, 0);
        if (isLight()) {
            level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 20, 0.3, 0.6, 0.3, 0.04);
            level.sendParticles(FbFx.dust(FbFx.TWILIGHT_GOLD, 1.3F), c.x, c.y, c.z, 16, 0.35, 0.7, 0.35, 0);
            FbFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        } else {
            level.sendParticles(Vfx.ink(), c.x, c.y, c.z, 20, 0.3, 0.6, 0.3, 0.03);
            level.sendParticles(FbFx.dust(FbFx.TWILIGHT_SHADOW, 1.3F), c.x, c.y, c.z, 16, 0.35, 0.7, 0.35, 0);
            FbFx.sound(level, c, SoundEvents.ENDERMAN_AMBIENT, 0.6F, 0.6F);
        }
    }

    @Override
    protected void vanishFx(ServerLevel level) {
        Vec3 c = position().add(0, 1, 0);
        level.sendParticles(isLight() ? Vfx.endRod() : Vfx.largeSmoke(), c.x, c.y, c.z, 16, 0.3, 0.6, 0.3, 0.03);
        level.sendParticles(FbFx.dust(isLight() ? FbFx.TWILIGHT_GOLD : FbFx.TWILIGHT_SHADOW, 1.2F), c.x, c.y, c.z, 12, 0.3, 0.6, 0.3, 0);
        FbFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, isLight() ? 1.4F : 0.7F);
    }

    @Override
    protected void clientFx() {
        if (random.nextInt(4) == 0) {
            level().addParticle(isLight() ? FbFx.dust(FbFx.TWILIGHT_GOLD, 0.8F) : FbFx.dust(FbFx.TWILIGHT_SHADOW, 0.8F),
                    getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8,
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CloneLight", isLight());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LIGHT, tag.getBoolean("CloneLight"));
        rebuildGoals();
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    /** Segue o dono a pé; teleporta se ficou muito longe. */
    static class FollowOwnerGoal extends Goal {
        private final TwilightCloneEntity clone;
        private final float startDist;
        private int recalc;

        FollowOwnerGoal(TwilightCloneEntity clone, float startDist) {
            this.clone = clone;
            this.startDist = startDist;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = clone.getOwner();
            return o != null && !o.isSpectator() && clone.getTarget() == null && clone.distanceToSqr(o) > startDist * startDist;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = clone.getOwner();
            return o != null && clone.getTarget() == null && !clone.getNavigation().isDone() && clone.distanceToSqr(o) > 2.0 * 2.0;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() { clone.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity o = clone.getOwner();
            if (o == null) return;
            clone.getLookControl().setLookAt(o, 10.0F, clone.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (clone.distanceToSqr(o) > 20 * 20) clone.teleportNear(o, false);
            else clone.getNavigation().moveTo(o, 1.15);
        }
    }

    /** Clone de luz: a cada 1,5s cura o aliado mais ferido (inclusive o dono) num raio de 8 blocos. */
    static class HealAlliesGoal extends Goal {
        private final TwilightCloneEntity clone;
        private int cooldown = 20;

        /*
         * Sem flags de propósito: este objetivo roda sempre (prioridade 1) e, se travasse LOOK, o FollowOwnerGoal
         * (MOVE+LOOK, prioridade 2) nunca poderia começar e o clone de luz não seguiria o dono.
         */
        HealAlliesGoal(TwilightCloneEntity clone) {
            this.clone = clone;
        }

        @Override
        public boolean canUse() { return true; }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (--cooldown > 0) return;
            cooldown = 30;
            if (!(clone.level() instanceof ServerLevel sl)) return;
            LivingEntity best = null;
            float worst = 0.999F;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, clone.getBoundingBox().inflate(8),
                    e -> e.isAlive() && e != clone && clone.isAlliedTo(e) && !e.isSpectator())) {
                float frac = e.getHealth() / e.getMaxHealth();
                if (frac < worst) { worst = frac; best = e; }
            }
            if (best == null) return;
            clone.getLookControl().setLookAt(best, 30F, 30F);
            clone.swing(InteractionHand.MAIN_HAND);
            best.heal(2.5F * clone.power());
            FbFx.line(sl, clone.position().add(0, 1.3, 0), FbUtil.center(best), FbFx.dust(FbFx.TWILIGHT_GOLD, 0.9F), 3.0);
            sl.sendParticles(Vfx.endRod(), best.getX(), best.getY() + best.getBbHeight() * 0.6, best.getZ(), 6,
                    0.3, 0.3, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.HEART, best.getX(), best.getY() + best.getBbHeight() + 0.3, best.getZ(), 1, 0.2, 0.1, 0.2, 0);
            FbFx.sound(sl, best.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.7F);
        }
    }
}
