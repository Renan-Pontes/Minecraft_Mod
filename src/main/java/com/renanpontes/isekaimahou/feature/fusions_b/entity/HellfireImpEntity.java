package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Diabrete da Chama Infernal: pequeno demônio voador invocado pelo Portal Infernal. Investe contra os inimigos
 * do dono com garras em brasa (dano + fogo), segue o dono pelo ar e some em cinzas quando o tempo acaba.
 */
public class HellfireImpEntity extends FbSummon {
    public static final int MAX_PER_OWNER = 4;

    public HellfireImpEntity(EntityType<? extends HellfireImpEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // FLYING_SPEED vira aceleração direta no travel() (como o Allay, 0.1): 0.12 dá ~13 blocos/s na investida
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.FLYING_SPEED, 0.12)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0);
    }

    /** Faz um diabrete sair do portal em {@code at}. */
    @Nullable
    public static HellfireImpEntity emerge(ServerLevel level, LivingEntity owner, Vec3 at, int life, float power) {
        limit(level, FbRegistries.HELLFIRE_IMP.get(), owner.getUUID(), MAX_PER_OWNER - 1, e -> true);
        HellfireImpEntity imp = FbRegistries.HELLFIRE_IMP.get().create(level);
        if (imp == null) return null;
        Vec3 p = imp.findSpot(at.add(0, 0.6, 0), true);
        imp.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
        imp.setup(owner, life, power);
        imp.setDeltaMovement(0, 0.35, 0);
        level.addFreshEntity(imp);
        imp.spawnFx(level);
        if (owner instanceof Mob m && m.getTarget() != null) imp.setTarget(m.getTarget());
        return imp;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ChargeGoal(this));
        goalSelector.addGoal(2, new FlyToOwnerGoal(this));
        goalSelector.addGoal(3, new HoverNearOwnerGoal(this));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        addTargetGoals();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    /** Voo como o do Allay: sem gravidade, com atrito no ar. */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.02F, travelVector);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8F));
            } else {
                moveRelative(getSpeed(), travelVector);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91F));
            }
        }
        calculateEntityAnimation(false);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }

    @Override
    public boolean doHurtTarget(Entity target) {
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean ok = target.hurt(FbUtil.summonSpellSource(this, getOwner()), dmg);
        swing(InteractionHand.MAIN_HAND);
        if (ok) {
            target.igniteForSeconds(3F);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(Vfx.flame(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        8, 0.25, 0.25, 0.25, 0.04);
                sl.sendParticles(Vfx.soulFlame(), target.getX(), target.getY() + target.getBbHeight() * 0.6,
                        target.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
            }
            playSound(SoundEvents.BLAZE_HURT, 0.5F, 1.8F);
        }
        return ok;
    }

    // =====================================================================================
    //  Visual e som
    // =====================================================================================

    @Override
    protected void spawnFx(ServerLevel level) {
        level.sendParticles(Vfx.flame(), getX(), getY() + 0.4, getZ(), 16, 0.25, 0.3, 0.25, 0.06);
        level.sendParticles(Vfx.largeSmoke(), getX(), getY() + 0.4, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(FbFx.dust(FbFx.HELLFIRE, 1.2F), getX(), getY() + 0.4, getZ(), 10, 0.3, 0.3, 0.3, 0);
    }

    @Override
    protected void vanishFx(ServerLevel level) {
        level.sendParticles(Vfx.ash(), getX(), getY() + 0.4, getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(Vfx.smoke(), getX(), getY() + 0.4, getZ(), 10, 0.2, 0.3, 0.2, 0.02);
        FbFx.sound(level, position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
    }

    @Override
    protected void clientFx() {
        if (random.nextInt(3) == 0) {
            level().addParticle(Vfx.smallFlame(), getX() + (random.nextDouble() - 0.5) * 0.4,
                    getY() + 0.2 + random.nextDouble() * 0.5, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
        if (random.nextInt(8) == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.8, getZ(), 0, 0.02, 0);
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VEX_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BLAZE_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.BLAZE_DEATH; }

    @Override
    public float getVoicePitch() { return 1.6F + random.nextFloat() * 0.2F; }

    // =====================================================================================
    //  IA
    // =====================================================================================

    /** Investida: voa até o alvo e golpeia com as garras quando encosta. */
    static class ChargeGoal extends Goal {
        private final HellfireImpEntity imp;
        private int cooldown;

        ChargeGoal(HellfireImpEntity imp) {
            this.imp = imp;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = imp.getTarget();
            return t != null && t.isAlive() && !imp.isAlliedTo(t);
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = imp.getTarget();
            if (t == null) return;
            imp.getLookControl().setLookAt(t, 30F, 30F);
            Vec3 aim = t.getEyePosition().add(0, -0.3, 0);
            imp.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.8);
            if (cooldown > 0) cooldown--;
            double reach = imp.getBbWidth() * 2 + t.getBbWidth() + 0.6;
            if (cooldown <= 0 && imp.distanceToSqr(t) < reach * reach) {
                imp.doHurtTarget(t);
                cooldown = 20;
                // Recua depois do golpe, como um vex
                Vec3 back = imp.position().subtract(t.position()).normalize().scale(0.5);
                imp.setDeltaMovement(back.x, 0.25, back.z);
            }
        }
    }

    /** Volta para perto do dono quando se afasta demais (teleporta se ficou muito longe). */
    static class FlyToOwnerGoal extends Goal {
        private final HellfireImpEntity imp;

        FlyToOwnerGoal(HellfireImpEntity imp) {
            this.imp = imp;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = imp.getOwner();
            // Espectador = dono em Projeção Astral: o diabrete fica guardando o corpo, não segue o espírito
            return o != null && !o.isSpectator() && imp.getTarget() == null && imp.distanceToSqr(o) > 6 * 6;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = imp.getOwner();
            return o != null && !o.isSpectator() && imp.getTarget() == null && imp.distanceToSqr(o) > 3 * 3;
        }

        /** O controle de voo só empurra no tick em que recebe o destino: precisa ser renovado todo tick. */
        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity o = imp.getOwner();
            if (o == null) return;
            if (imp.distanceToSqr(o) > 24 * 24) {
                imp.teleportNear(o, true);
                return;
            }
            imp.getMoveControl().setWantedPosition(o.getX(), o.getY() + 2.0, o.getZ(), 1.4);
        }
    }

    /** Fica pairando em volta do dono, voando de um ponto a outro em volta dele de vez em quando. */
    static class HoverNearOwnerGoal extends Goal {
        private final HellfireImpEntity imp;
        private Vec3 spot = Vec3.ZERO;
        private int wait;
        private int time;

        HoverNearOwnerGoal(HellfireImpEntity imp) {
            this.imp = imp;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return imp.getTarget() == null && --wait <= 0;
        }

        @Override
        public boolean canContinueToUse() {
            return imp.getTarget() == null && time > 0 && imp.distanceToSqr(spot) > 0.6 * 0.6;
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            time = 50;
            LivingEntity o = imp.getOwner();
            Vec3 base = o != null && !o.isSpectator() ? o.position() : imp.position();
            double a = imp.getRandom().nextDouble() * Math.PI * 2;
            double r = 1.5 + imp.getRandom().nextDouble() * 2.5;
            spot = new Vec3(base.x + Math.cos(a) * r, base.y + 1.5 + imp.getRandom().nextDouble() * 1.5, base.z + Math.sin(a) * r);
        }

        @Override
        public void tick() {
            time--;
            imp.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.9);
            imp.getLookControl().setLookAt(spot.x, spot.y, spot.z);
        }

        @Override
        public void stop() {
            wait = 30 + imp.getRandom().nextInt(40);
        }
    }
}
