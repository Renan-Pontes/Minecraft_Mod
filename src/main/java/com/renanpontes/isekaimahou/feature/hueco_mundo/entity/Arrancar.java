package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmText;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Arrancar: Vazio que arrancou a própria máscara e ganhou forma humana — elite do Mundo Oco, com nome e estilo próprios
 * (cinco variações, todas originais): todos usam Sonído (passo instantâneo com pós-imagem) e Cero, e cada um tem um golpe
 * de assinatura telegrafado.
 * <ol start="0">
 *   <li><b>Grael, a Pantera Azul</b> — três garradas seguidas em leque;</li>
 *   <li><b>Ulvar, o Lamento Verde</b> — Lança Relâmpago: feixe longo e fino;</li>
 *   <li><b>Nelva, a Lança de Cervo</b> — investida em linha com a lança;</li>
 *   <li><b>Varg, o Lobo Solitário</b> — rajada de cinco Ceros em leque;</li>
 *   <li><b>Kessra, o Louva-a-Deus</b> — Crescente: meia-lua enorme que sangra.</li>
 * </ol>
 */
public class Arrancar extends HmMob {
    public static final int VARIANTS = 5;
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Arrancar.class, EntityDataSerializers.INT);
    private static final int[] COLORS = {0x4FB8FF, 0x55FF8A, 0xFF8FC8, 0xB8C4D8, 0xC9A23A};
    private int sonidoCd = 60, ceroCd = 80, signatureCd = 120, busy;
    private Vec3 lineFrom = Vec3.ZERO, lineTo = Vec3.ZERO;

    public Arrancar(EntityType<? extends Arrancar> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 110).add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    public String modelId() {
        return "arrancar";
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("slash", "cero", "signature", "sonido");
    }

    public int variant() {
        return Mth.clamp(entityData.get(DATA_VARIANT), 0, VARIANTS - 1);
    }

    public void setVariant(int v) {
        entityData.set(DATA_VARIANT, Mth.clamp(v, 0, VARIANTS - 1));
    }

    public int color() {
        return COLORS[variant()];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        setVariant(random.nextInt(VARIANTS));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Nome próprio da variação ("Grael, a Pantera Azul"). */
    @Override
    protected Component getTypeName() {
        return HmText.of("entity.isekai_mahou.arrancar.name", String.valueOf(variant()));
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || busy > 0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (busy > 0) {
            busy--;
            return;
        }
        LivingEntity t = liveTarget();
        if (t == null) return;
        if (sonidoCd > 0) sonidoCd--;
        if (ceroCd > 0) ceroCd--;
        if (signatureCd > 0) signatureCd--;
        double d = distanceTo(t);
        if (signatureCd == 0 && d < 16 && hasLineOfSight(t)) {
            signatureCd = 200 + random.nextInt(60);
            signature(sl, t);
            return;
        }
        if (sonidoCd == 0 && d > 5 && d < 24) {
            sonidoCd = 90 + random.nextInt(50);
            Vec3 from = position();
            if (BossKit.blinkNear(this, t, 2.5)) {
                action("sonido");
                HmFx.afterimage(sl, from, position(), color());
                playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.8F);
            }
            return;
        }
        if (ceroCd == 0 && d > 6 && hasLineOfSight(t)) {
            ceroCd = 110 + random.nextInt(40);
            action("cero");
            HmBolt.shoot(this, getEyePosition(), t.getEyePosition(), variant() == 1 ? HmBolt.CERO_GREEN : HmBolt.CERO, 9F, 1.1F);
            playSound(SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
        }
    }

    private void signature(ServerLevel sl, LivingEntity t) {
        action("signature");
        getNavigation().stop();
        float yaw = (float) Math.toDegrees(Math.atan2(t.getZ() - getZ(), t.getX() - getX())) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        int c = color();
        switch (variant()) {
            case 0 -> { // Garras da Pantera: três leques seguidos.
                busy = 34;
                for (int i = 0; i < 3; i++) {
                    Telegraph.delayed(sl, Telegraph.Shape.cone(position(), yaw + (i - 1) * 25F, 110F, 4.2F).warmup(10 + i * 10).color(c)
                            .owner(this).noRunes(), hits -> hitAll(sl, hits, 7F, null));
                }
            }
            case 1 -> { // Lança Relâmpago.
                busy = 26;
                Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
                lineFrom = position();
                lineTo = lineFrom.add(dir.scale(22));
                Telegraph.delayed(sl, Telegraph.Shape.line(lineFrom, lineTo, 1.6F).warmup(22).color(c).owner(this), hits -> {
                    hitAll(sl, hits, 16F, null);
                    HmFx.beam(sl, getEyePosition(), lineTo.add(0, 1, 0), c, 0.6F);
                    sl.sendParticles(ParticleTypes.EXPLOSION, lineTo.x, lineTo.y + 1, lineTo.z, 1, 0, 0, 0, 0);
                    playSound(SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 1.2F);
                });
            }
            case 2 -> { // Investida do Cervo.
                busy = 30;
                Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
                lineFrom = position();
                lineTo = lineFrom.add(dir.scale(14));
                Telegraph.delayed(sl, Telegraph.Shape.line(lineFrom, lineTo, 2.2F).warmup(16).color(c).owner(this), hits -> {
                    hitAll(sl, hits, 13F, null);
                    teleportTo(lineTo.x, lineTo.y, lineTo.z);
                    HmFx.afterimage(sl, lineFrom, lineTo, c);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
                });
            }
            case 3 -> { // Rajada de Ceros.
                busy = 24;
                for (int i = 0; i < 5; i++) {
                    final int k = i;
                    BossKit.schedule(sl, 4 + i * 4, () -> {
                        if (!isAlive() || t.isRemoved()) return;
                        Vec3 aim = t.getEyePosition().add((k - 2) * 1.5, 0, (k - 2) * 1.5);
                        HmBolt.shoot(this, getEyePosition(), aim, HmBolt.CERO, 7F, 1.3F);
                        playSound(SoundEvents.BLAZE_SHOOT, 0.7F, 1.3F);
                    });
                }
            }
            default -> { // Crescente do Louva-a-Deus.
                busy = 22;
                Telegraph.delayed(sl, Telegraph.Shape.cone(position(), yaw, 170F, 6.2F).warmup(18).color(c).owner(this), hits -> {
                    hitAll(sl, hits, 14F, new MobEffectInstance(CoreRegistries.BLEEDING, 100, 0));
                    HmFx.ring(sl, position().add(0, 1.0, 0), 4.5, 30, ParticleTypes.SWEEP_ATTACK, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.5F);
                });
            }
        }
    }

    private void hitAll(ServerLevel sl, List<LivingEntity> hits, float dmg, @Nullable MobEffectInstance effect) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg) && effect != null) e.addEffect(new MobEffectInstance(effect), this);
        }
        sl.sendParticles(HmFx.dust(color(), 1.4F), getX(), getY() + 1, getZ(), 10, 0.6, 0.4, 0.6, 0);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("slash");
        return super.doHurtTarget(target);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
    }
}
