package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Vazio Rastejante: predador baixo de máscara de osso com um buraco no peito. Parado, ele se ENTERRA na areia branca
 * (só a crista da máscara aparece) e salta em quem passa perto. Em bando, cerca o alvo.
 */
public class HollowCrawler extends HmMob {
    private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData.defineId(HollowCrawler.class, EntityDataSerializers.BOOLEAN);
    private int calm;
    private int leapCd;

    public HollowCrawler(EntityType<? extends HollowCrawler> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 28).add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.31).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String modelId() {
        return "hollow_crawler";
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("bite", "emerge", "leap");
    }

    @Override
    protected String baseAnimation(boolean moving) {
        if (isBurrowed()) return "burrowed";
        return moving ? "walk" : "idle";
    }

    public boolean isBurrowed() {
        return entityData.get(DATA_BURROWED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROWED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isBurrowed();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (leapCd > 0) leapCd--;
        LivingEntity target = liveTarget();
        if (isBurrowed()) {
            if (tickCount % 5 == 0) {
                for (ServerPlayer p : sl.getPlayers(p -> !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) < 7 * 7)) {
                    emerge(sl, p);
                    break;
                }
            }
            return;
        }
        if (target == null) {
            if (++calm > 160 && onGround() && level().getBlockState(blockPosition().below()).is(HmRegistries.HOLLOW_SAND.get())) burrow(sl);
            return;
        }
        calm = 0;
        double d = distanceTo(target);
        if (leapCd == 0 && onGround() && d > 3.5 && d < 10 && hasLineOfSight(target)) {
            leapCd = 90;
            action("leap");
            BossKit.leapAt(this, target, 1.3);
            playSound(SoundEvents.SPIDER_AMBIENT, 1.0F, 0.6F);
        }
    }

    private void burrow(ServerLevel sl) {
        calm = 0;
        entityData.set(DATA_BURROWED, true);
        getNavigation().stop();
        sandBurst(sl, 14);
    }

    private void emerge(ServerLevel sl, LivingEntity target) {
        entityData.set(DATA_BURROWED, false);
        action("emerge");
        sandBurst(sl, 30);
        playSound(SoundEvents.SAND_BREAK, 1.2F, 0.6F);
        setTarget(target);
        leapCd = 10;
    }

    private void sandBurst(ServerLevel sl, int n) {
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                getX(), getY() + 0.3, getZ(), n, 0.6, 0.3, 0.6, 0.15);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("bite");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && level() instanceof ServerLevel sl) {
            sl.sendParticles(HmFx.dust(HmFx.CERO_RED, 1.0F), le.getX(), le.getY() + 1, le.getZ(), 6, 0.2, 0.3, 0.2, 0);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && isBurrowed() && level() instanceof ServerLevel sl && source.getEntity() instanceof LivingEntity le) emerge(sl, le);
        return r;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_BURROWED, tag.getBoolean("Burrowed"));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }
}
