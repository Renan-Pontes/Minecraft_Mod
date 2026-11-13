package com.renanpontes.isekaimahou.feature.light_shadow.entity;

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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
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
 * Fada Guardiã (Invocar Fada Guardiã): voa em volta do dono, cura o aliado mais ferido por perto a cada 1,5s
 * e lança faíscas de luz em mortos-vivos e demônios inimigos. Some quando o tempo acaba.
 */
public class GuardianFairyEntity extends PathfinderMob implements OwnableEntity, GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_guardian_fairy.idle");
    private static final RawAnimation HEAL = RawAnimation.begin().thenPlay("animation.summoned_guardian_fairy.heal");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int lifetime = 1200;
    private float power = 1.0F;
    private int healCooldown = 20;
    private int sparkCooldown = 30;
    private Vec3 home;

    public GuardianFairyEntity(EntityType<? extends GuardianFairyEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /** Configura a fada invocada. */
    public void setup(LivingEntity owner, int lifetime, float power) {
        this.owner = owner != null ? owner.getUUID() : null;
        this.lifetime = lifetime;
        this.power = power;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
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

    private boolean isFriend(ServerLevel sl, LivingEntity e) {
        return owner != null ? LsUtil.isAlly(sl, owner, e) : e instanceof Player;
    }

    // ------------------------------------------------------------------ comportamento
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        LivingEntity o = getOwner();
        // Só invocações (com dono) são temporárias; as de ovo ficam e cuidam dos jogadores por perto
        if (owner != null && (--lifetime <= 0 || (o == null && tickCount > 40))) {
            // Tempo acabou, ou o dono saiu do mundo/foi para outra dimensão
            vanish(sl);
            return;
        }
        Vec3 anchor;
        // Forja de Invocações: com a ordem Guardar/Ficar a fada paira no posto em vez de acompanhar o dono
        Vec3 post = SummonUtil.forgePost(this);
        if (post != null) {
            double a = tickCount * 0.06 + getId() * 1.7;
            anchor = post.add(Math.cos(a) * 0.8, 1.2 + Math.sin(tickCount * 0.15) * 0.15, Math.sin(a) * 0.8);
        } else if (o != null) {
            if (distanceToSqr(o) > 24 * 24) {
                teleportTo(o.getX(), o.getY() + o.getBbHeight() + 0.3, o.getZ());
                LsFx.holyBurst(sl, position(), 8, 0.3);
            }
            double a = tickCount * 0.06 + getId() * 1.7;
            anchor = o.position().add(Math.cos(a) * 1.3, o.getBbHeight() + 0.15 + Math.sin(tickCount * 0.15) * 0.15, Math.sin(a) * 1.3);
        } else {
            if (home == null) home = position();
            anchor = home.add(0, Math.sin(tickCount * 0.1) * 0.3, 0);
        }
        Vec3 to = anchor.subtract(position());
        double d = to.length();
        if (d > 0.05) setDeltaMovement(getDeltaMovement().add(to.scale(Math.min(0.09, d * 0.04) / d)));
        // Olha para o dono quando está perto, senão para onde voa
        Vec3 face = o != null && d < 2.0 ? o.position().subtract(position()) : getDeltaMovement();
        if (face.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.rotLerp(0.3F, getYRot(), yaw));
            yBodyRot = getYRot();
        }
        if (--healCooldown <= 0) {
            healCooldown = 30;
            healPulse(sl);
        }
        if (--sparkCooldown <= 0) {
            sparkCooldown = 40;
            smite(sl);
        }
    }

    /** Cura o aliado mais ferido num raio de 8 blocos. */
    private void healPulse(ServerLevel sl) {
        LivingEntity best = null;
        float worst = 0.999F;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8),
                e -> e.isAlive() && e != this && isFriend(sl, e))) {
            float ratio = e.getHealth() / e.getMaxHealth();
            if (ratio < worst) {
                worst = ratio;
                best = e;
            }
        }
        if (best == null) return;
        best.heal(3.0F * power);
        best.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
        LsFx.line(sl, position().add(0, 0.45, 0), LsFx.mid(best), LsFx.fade(LsFx.WHITE_GOLD, 0x9CFFB0, 0.8F), 5);
        sl.sendParticles(ParticleTypes.HEART, best.getX(), best.getY() + best.getBbHeight() + 0.3, best.getZ(), 2, 0.3, 0.1, 0.3, 0);
        sl.sendParticles(Vfx.happy(), best.getX(), best.getY() + best.getBbHeight() * 0.5, best.getZ(), 6, 0.3, 0.4, 0.3, 0);
        LsFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
        triggerAnim("action", "heal");
    }

    /** Faísca de luz no morto-vivo/demônio inimigo mais próximo. */
    private void smite(ServerLevel sl) {
        if (owner == null) return;
        for (LivingEntity e : LsUtil.enemiesOf(sl, owner, position(), 8, this)) {
            if (!LsUtil.isUnholy(e) || !hasLineOfSight(e)) continue;
            LsFx.line(sl, position().add(0, 0.45, 0), LsFx.mid(e), LsFx.dust(LsFx.GOLD, 0.9F), 4);
            sl.sendParticles(Vfx.endRod(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.04);
            LsUtil.spellHurt(sl, e, 3.0F * power, this, getOwner(), Element.LIGHT);
            LsFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.7F);
            triggerAnim("action", "heal");
            return;
        }
    }

    private void vanish(ServerLevel sl) {
        LsFx.holyBurst(sl, position().add(0, 0.4, 0), 16, 0.3);
        LsFx.sound(sl, position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.6F);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            // Pó dourado caindo das asas
            level().addParticle(LsFx.dust(random.nextBoolean() ? LsFx.GOLD : LsFx.WHITE_GOLD, 0.6F),
                    getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 0.4 + random.nextDouble() * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.5, 0, -0.02, 0);
        }
    }

    /** Voo livre (como FlyingMob), com o movimento vindo de customServerAiStep. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            moveRelative(0.02F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.86));
        }
        calculateEntityAnimation(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Fogo amigo do dono e dos aliados não machuca a fada
        if (level() instanceof ServerLevel sl && source.getEntity() != null && owner != null && LsUtil.isAlly(sl, owner, source.getEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
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
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
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
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) LsFx.holyBurst(sl, position().add(0, 0.4, 0), 20, 0.35);
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
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP).triggerableAnim("heal", HEAL));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
