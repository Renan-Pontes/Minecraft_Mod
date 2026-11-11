package com.renanpontes.isekaimahou.feature.class_shinigami.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.Signatures;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.SpiritTrials;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
 * O espírito da zanpakutō (GeckoLib): vive no Mundo Interior do dono, na praça do centro. Seis aparências (sem nome +
 * os 5 arquétipos). Modos:
 * <ul>
 *   <li><b>calmo</b> — invulnerável; medita sentado e se levanta quando o dono chega perto; conversar (clicar) segue o
 *   caminho dos ranks ({@link SpiritTrials#talk}); agachado + clicar = treino;</li>
 *   <li><b>prova</b> (Shikai / Bankai / treino) — luta de verdade contra o dono: cortes, Shunpo para as costas, a
 *   técnica do arquétipo e, na prova da Bankai, a Presa Lunar e o campo da Bankai nas fases 2 e 3. Abaixo de 10% da
 *   vida ele se RENDE (nunca morre): a prova foi vencida;</li>
 *   <li><b>rendido</b> — ajoelhado por alguns segundos, depois volta ao calmo.</li>
 * </ul>
 * O <b>Vazio Interior</b> (técnicas avançadas): na prova {@link SpiritTrials#HOLLOW} o espírito é tomado pelo Vazio —
 * aparência invertida (pele e roupa brancas, olhos negros e dourados), muito mais forte, com Cero telegrafado, Getsuga
 * negro, Sonído para as costas e regeneração rápida. Ao se render, volta à aparência do arquétipo.
 */
public class ZanpakutoSpiritEntity extends PathfinderMob implements GeoEntity {
    public static final int CALM = 0;
    public static final int TRIAL = 1;
    public static final int YIELDED = 2;
    /** Aparência do Vazio Interior (índice 6 da textura). */
    public static final int HOLLOW_LOOK = 6;

    private static final EntityDataAccessor<Integer> DATA_ARCH = SynchedEntityData.defineId(ZanpakutoSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(ZanpakutoSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(ZanpakutoSpiritEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TALK = SynchedEntityData.defineId(ZanpakutoSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(ZanpakutoSpiritEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.idle");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.sit");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.run");
    private static final RawAnimation GUARD = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.guard");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("animation.zanpakuto_spirit.talk");
    private static final RawAnimation YIELD = RawAnimation.begin().thenPlayAndHold("animation.zanpakuto_spirit.yield");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.zanpakuto_spirit.slash");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.zanpakuto_spirit.cast");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private Vec3 home;
    /** 1 = prova da Shikai, 2 = prova da Bankai, 3 = treino. */
    private int trialKind;
    private int skillCd = 60, stepCd = 80, waveCd = 120, fieldCd = 20;
    private int yieldTicks;
    /** Aparência para voltar quando o Vazio sair (-1 = nenhuma guardada). */
    private int restoreArch = -1;
    private int ceroCd = 90;

    public ZanpakutoSpiritEntity(EntityType<? extends ZanpakutoSpiritEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARCH, 0);
        builder.define(DATA_MODE, CALM);
        builder.define(DATA_SITTING, true);
        builder.define(DATA_TALK, 0);
        builder.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return mode() == TRIAL && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return mode() == TRIAL && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    public int archetypeIndex() {
        return entityData.get(DATA_ARCH);
    }

    @Nullable
    public Archetype archetype() {
        return Archetype.byIndex(archetypeIndex() - 1);
    }

    /** Aparência: 0 = sem nome, 1..5 = arquétipo, 6 = o Vazio Interior. */
    public void setArchetype(int v) {
        if (v != HOLLOW_LOOK && archetypeIndex() == HOLLOW_LOOK && mode() == TRIAL) {
            restoreArch = Mth.clamp(v, 0, 5);
            return;
        }
        entityData.set(DATA_ARCH, Mth.clamp(v, 0, HOLLOW_LOOK));
    }

    public boolean isHollow() {
        return archetypeIndex() == HOLLOW_LOOK;
    }

    /** O Vazio Interior toma o espírito (aparência invertida, até a prova acabar). */
    public void becomeHollow() {
        if (!isHollow()) restoreArch = archetypeIndex();
        entityData.set(DATA_ARCH, HOLLOW_LOOK);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ShinigamiFx.dust(0xF2EEE6, 1.6F), getX(), getY() + 1, getZ(), 50, 0.5, 0.9, 0.5, 0.05);
            sl.sendParticles(ShinigamiFx.BLACK, getX(), getY() + 1, getZ(), 40, 0.6, 0.9, 0.6, 0.05);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_AMBIENT, getSoundSource(), 1.2F, 0.4F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, getSoundSource(), 0.8F, 1.3F);
        }
    }

    public int mode() {
        return entityData.get(DATA_MODE);
    }

    public boolean sitting() {
        return entityData.get(DATA_SITTING);
    }

    public boolean talking() {
        return entityData.get(DATA_TALK) > 0;
    }

    public void talkFor(int ticks) {
        entityData.set(DATA_TALK, ticks);
        entityData.set(DATA_SITTING, false);
    }

    @Nullable
    public UUID owner() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(UUID id) {
        entityData.set(DATA_OWNER, Optional.of(id));
    }

    public boolean isOwner(Entity e) {
        return e != null && e.getUUID().equals(owner());
    }

    public void setHome(Vec3 pos) {
        this.home = pos;
    }

    public int trialKind() {
        return trialKind;
    }

    private static final net.minecraft.resources.ResourceLocation TRIAL_MOD = com.renanpontes.isekaimahou.IsekaiMahou.id("zanpakuto_spirit_trial");

    /**
     * Força da prova: treino e Shikai normais; Bankai (rank V, Círculo 8) com +300% de vida e +75% de dano; o Vazio
     * Interior (rank IV, Círculo 6) com +200% de vida e +60% de dano.
     */
    private void trialStrength(int kind) {
        for (var attr : java.util.List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
            var inst = getAttribute(attr);
            if (inst == null) continue;
            inst.removeModifier(TRIAL_MOD);
            if (kind == 2 || kind == SpiritTrials.HOLLOW) {
                double v = kind == 2 ? (attr == Attributes.MAX_HEALTH ? 3.0 : 0.75) : (attr == Attributes.MAX_HEALTH ? 2.0 : 0.6);
                inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TRIAL_MOD, v,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    /** Volta ao modo calmo (cura, sem alvo, de volta para a praça). */
    public void calm() {
        entityData.set(DATA_MODE, CALM);
        if (isHollow() && restoreArch >= 0) entityData.set(DATA_ARCH, restoreArch);
        restoreArch = -1;
        trialKind = 0;
        trialStrength(0);
        setTarget(null);
        getNavigation().stop();
        setHealth(getMaxHealth());
        removeAllEffects();
    }

    /** Começa uma luta contra o dono. */
    public void startTrial(ServerPlayer owner, int kind) {
        this.trialKind = kind;
        trialStrength(kind);
        entityData.set(DATA_MODE, TRIAL);
        entityData.set(DATA_SITTING, false);
        entityData.set(DATA_TALK, 0);
        setHealth(getMaxHealth());
        setTarget(owner);
        skillCd = 60;
        stepCd = 70;
        waveCd = 140;
        fieldCd = 40;
        ceroCd = 80;
        triggerAnim("action", "cast");
    }

    /** Fase da prova da Bankai (1..3), pela vida. */
    public int phase() {
        float f = getHealth() / Math.max(1F, getMaxHealth());
        return f > 0.66F ? 1 : f > 0.33F ? 2 : 3;
    }

    // =====================================================================================
    //  Interação e dano
    // =====================================================================================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!isOwner(sp)) {
            sp.displayClientMessage(com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami.msg("spirit_not_yours"), true);
            return InteractionResult.CONSUME;
        }
        if (mode() != CALM) return InteractionResult.CONSUME;
        getLookControl().setLookAt(sp, 30F, 30F);
        SpiritTrials.talk(sp, this, sp.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (mode() != TRIAL) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && !isOwner(attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void setHealth(float health) {
        // nunca morre numa prova: abaixo de 10% se rende
        if (!level().isClientSide && mode() == TRIAL && health <= getMaxHealth() * 0.1F) {
            super.setHealth(Math.max(1F, getMaxHealth() * 0.1F));
            yieldNow();
            return;
        }
        super.setHealth(health);
    }

    private void yieldNow() {
        if (mode() != TRIAL) return;
        entityData.set(DATA_MODE, YIELDED);
        yieldTicks = 120;
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, getSoundSource(), 1.0F, 1.2F);
            SpiritTrials.onYield(this);
        }
    }

    @Override
    public boolean isPushable() {
        return mode() == TRIAL;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le && archetype() != null) {
            Signatures.apply(this, le, archetype(), trialKind == 2 ? 1.2F : 0.6F, 60);
        }
        if (ok && isHollow()) heal(2.0F);
        triggerAnim("action", "slash");
        if (level() instanceof ServerLevel sl) {
            Vec3 look = getLookAngle();
            ShinigamiFx.slashArc(sl, position().add(0, 1.0, 0), look, 1.8, 60, 0.5F, ShinigamiFx.dust(color(), 1.1F));
        }
        return ok;
    }

    public int color() {
        if (isHollow()) return 0xE01030;
        Archetype a = archetype();
        return a == null ? 0xB8B8C8 : a.rgb;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 6 == 0) {
                level().addParticle(ShinigamiFx.dust(color(), 0.8F), getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(),
                        getRandomZ(0.6), 0, 0.02, 0);
            }
            return;
        }
        int talk = entityData.get(DATA_TALK);
        if (talk > 0) entityData.set(DATA_TALK, talk - 1);
        switch (mode()) {
            case CALM -> calmTick();
            case TRIAL -> trialTick();
            case YIELDED -> {
                if (--yieldTicks <= 0) calm();
            }
            default -> {
            }
        }
    }

    private void calmTick() {
        if (tickCount % 20 != 0) return;
        Player near = level().getNearestPlayer(this, 7.0);
        boolean ownerNear = near != null && isOwner(near);
        if (sitting() == ownerNear) entityData.set(DATA_SITTING, !ownerNear);
        if (home != null && position().distanceToSqr(home) > 4) getNavigation().moveTo(home.x, home.y, home.z, 0.8);
    }

    private void trialTick() {
        LivingEntity target = getTarget();
        if (!(target instanceof ServerPlayer sp) || !sp.isAlive() || sp.level() != level() || !isOwner(sp)) {
            SpiritTrials.spiritLost(this);
            calm();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (trialKind == SpiritTrials.HOLLOW) {
            hollowTick(sl, sp);
            return;
        }
        Archetype a = archetype();
        int phase = trialKind == 2 ? phase() : 1;
        // Shunpo para as costas
        if (--stepCd <= 0 && distanceToSqr(sp) > 9) {
            stepCd = phase >= 2 ? 60 : 90;
            Vec3 back = sp.position().subtract(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
            if (level().noCollision(this, getBoundingBox().move(back.subtract(position())))) {
                ShinigamiFx.afterimage(sl, this, position(), back);
                teleportTo(back.x, back.y, back.z);
                sl.playSound(null, back.x, back.y, back.z, SoundEvents.ENDER_DRAGON_FLAP, getSoundSource(), 0.4F, 2.0F);
            }
        }
        // técnica do arquétipo
        if (--skillCd <= 0 && a != null) {
            skillCd = trialKind == 2 ? (phase == 3 ? 70 : 100) : 130;
            archetypeSkill(sl, sp, a);
        }
        // Presa Lunar do espírito (prova da Bankai, fase 2+)
        if (trialKind == 2 && phase >= 2 && --waveCd <= 0) {
            waveCd = 110;
            moonFang(sl, sp);
        }
        // campo da Bankai do espírito (fase 3)
        if (trialKind == 2 && phase == 3 && --fieldCd <= 0) {
            fieldCd = 20;
            if (distanceToSqr(sp) < 8 * 8) {
                sp.hurt(damageSources().mobAttack(this), 2.0F);
                if (a != null) Signatures.apply(this, sp, a, 0.8F, 40);
            }
            ShinigamiFx.ring(sl, position().add(0, 0.1, 0), 8, 40, tickCount * 0.05F, ShinigamiFx.dust(color(), 1.3F));
        }
    }

    /**
     * A luta contra o Vazio Interior: Sonído para as costas (mais rápido a cada fase), Cero telegrafado (uma linha
     * vermelha avisa 0,8 s antes), Getsuga negro na fase 2+ e regeneração rápida na fase 3.
     */
    private void hollowTick(ServerLevel sl, ServerPlayer sp) {
        int phase = phase();
        if (tickCount % 4 == 0) {
            sl.sendParticles(ShinigamiFx.dust(0xE01030, 1.0F), getX(), getY() + 1.0, getZ(), 2, 0.3, 0.6, 0.3, 0.01);
            sl.sendParticles(ShinigamiFx.BLACK, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.6, 0.3, 0.01);
        }
        if (--stepCd <= 0 && distanceToSqr(sp) > 6) {
            stepCd = phase == 1 ? 60 : phase == 2 ? 45 : 32;
            Vec3 back = sp.position().subtract(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
            if (level().noCollision(this, getBoundingBox().move(back.subtract(position())))) {
                ShinigamiFx.afterimage(sl, this, position(), back);
                teleportTo(back.x, back.y, back.z);
                sl.playSound(null, back.x, back.y, back.z, SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 0.5F, 0.6F);
            }
        }
        if (--ceroCd <= 0) {
            ceroCd = phase == 3 ? 70 : 100;
            cero(sl, sp, phase == 3 ? 10.0F : 7.0F);
        }
        if (phase >= 2 && --waveCd <= 0) {
            waveCd = 120;
            moonFang(sl, sp);
        }
        if (phase == 3 && tickCount % 20 == 0) heal(getMaxHealth() * 0.012F);
    }

    /** O Cero do Vazio: linha vermelha de aviso, depois o feixe (dano em quem ficar na linha). */
    private void cero(ServerLevel sl, ServerPlayer sp, float dmg) {
        triggerAnim("action", "cast");
        Vec3 from = position().add(0, 1.4, 0);
        Vec3 dir = sp.position().add(0, 1.0, 0).subtract(from).normalize();
        ShinigamiFx.line(sl, from, from.add(dir.scale(18)), ShinigamiFx.dust(0xE01030, 0.6F), 0.6);
        sl.sendParticles(ShinigamiFx.dust(0xE01030, 1.5F), from.x + dir.x, from.y + dir.y, from.z + dir.z, 12, 0.15, 0.15, 0.15, 0);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, getSoundSource(), 0.8F, 0.9F);
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTasks.later(16, () -> {
            if (!isAlive() || mode() != TRIAL) return;
            Vec3 f = position().add(0, 1.4, 0);
            for (int i = 1; i <= 18; i++) {
                Vec3 p = f.add(dir.scale(i));
                sl.sendParticles(ShinigamiFx.dust(0xE01030, 1.8F), p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0);
                sl.sendParticles(ShinigamiFx.BLACK, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
            if (sp.isAlive() && sp.level() == level()) {
                Vec3 rel = sp.position().add(0, 1.0, 0).subtract(f);
                double along = rel.dot(dir);
                if (along > 0 && along < 18 && rel.subtract(dir.scale(along)).length() < 1.3) {
                    sp.hurt(damageSources().mobAttack(this), dmg);
                }
            }
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, getSoundSource(), 0.9F, 0.7F);
        });
    }

    /** A técnica de cada arquétipo, telegrafada (as partículas avisam meio segundo antes). */
    private void archetypeSkill(ServerLevel sl, ServerPlayer sp, Archetype a) {
        triggerAnim("action", "cast");
        Vec3 at = sp.position();
        ShinigamiFx.ring(sl, at.add(0, 0.1, 0), 2.5, 24, 0F, ShinigamiFx.dust(a.rgb, 1.2F));
        sl.playSound(null, getX(), getY(), getZ(), skillSound(a), getSoundSource(), 1.0F, 0.9F);
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTasks.later(12, () -> {
            if (!isAlive() || mode() != TRIAL || !sp.isAlive() || sp.level() != level()) return;
            float dmg = trialKind == 2 ? 5.0F : 3.5F;
            if (sp.position().distanceToSqr(at) <= 3.0 * 3.0) {
                sp.hurt(damageSources().mobAttack(this), dmg);
                Signatures.apply(this, sp, a, trialKind == 2 ? 1.2F : 0.8F, 60);
            }
            ShinigamiFx.signature(sl, a, at.add(0, 0.8, 0), 40, 1.6);
        });
    }

    private void moonFang(ServerLevel sl, ServerPlayer sp) {
        triggerAnim("action", "slash");
        Vec3 from = position().add(0, 1.0, 0);
        Vec3 dir = sp.position().add(0, 1.0, 0).subtract(from).normalize();
        ShinigamiFx.line(sl, from, from.add(dir.scale(14)), ShinigamiFx.dust(color(), 0.6F), 0.7);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, getSoundSource(), 0.5F, 1.6F);
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTasks.later(14, () -> {
            if (!isAlive() || mode() != TRIAL) return;
            Vec3 f = position().add(0, 1.0, 0);
            for (int i = 1; i <= 16; i++) {
                Vec3 p = f.add(dir.scale(i));
                sl.sendParticles(ShinigamiFx.dust(color(), 1.5F), p.x, p.y, p.z, 4, 0.6, 0.4, 0.6, 0);
                if (sp.isAlive() && sp.getBoundingBox().inflate(1.2).contains(p)) {
                    sp.hurt(damageSources().mobAttack(this), 7.0F);
                    break;
                }
            }
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 1.2F, 0.5F);
        });
    }

    private static SoundEvent skillSound(Archetype a) {
        return switch (a) {
            case WEIGHT -> SoundEvents.ANVIL_PLACE;
            case PETALS -> SoundEvents.CHERRY_LEAVES_PLACE;
            case FROST -> SoundEvents.POWDER_SNOW_STEP;
            case FLAME -> SoundEvents.FIRECHARGE_USE;
            case BRUSH -> SoundEvents.BOOK_PAGE_TURN;
        };
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Arch", isHollow() && restoreArch >= 0 ? restoreArch : archetypeIndex());
        UUID o = owner();
        if (o != null) tag.putUUID("Owner", o);
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setArchetype(tag.getInt("Arch"));
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        // provas não sobrevivem a um salvamento
        entityData.set(DATA_MODE, CALM);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ZanpakutoSpiritEntity>(this, "main", 5, state -> {
            if (mode() == YIELDED) return state.setAndContinue(YIELD);
            if (mode() == TRIAL) return state.setAndContinue(state.isMoving() ? RUN : GUARD);
            if (state.isMoving()) return state.setAndContinue(WALK);
            if (talking()) return state.setAndContinue(TALK);
            if (sitting()) return state.setAndContinue(SIT);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<ZanpakutoSpiritEntity>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("slash", SLASH)
                .triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
