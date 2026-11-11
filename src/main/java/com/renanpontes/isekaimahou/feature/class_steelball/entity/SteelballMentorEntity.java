package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbMentorService;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Mestre Rotari — o velho mestre itinerante da Rotação. Aparece perto do Steel Ball quando há lição pronta (ou chamado
 * pela Carta), ensina as lições (diálogo) e some depois de alguns minutos. Fora do desafio é intocável (desvia girando).
 * <p>Desafio do Mestre (lição IV): duelo de verdade — bolas de aço que voltam para a mão, rajadas de unhas, Contração
 * Muscular telegrafada no chão e, abaixo de 50%, a Rotação Medicinal e as bolas douradas. Ninguém morre: com 15% de
 * vida ele se RENDE ("Bom giro!") e a lição fica liberada.
 */
public class SteelballMentorEntity extends PathfinderMob implements GeoEntity {
    public static final int STATE_IDLE = 0, STATE_DUEL = 1, STATE_YIELD = 2;
    public static final int VISIT_TICKS = 6000;
    private static final int MAX_FIGHT = 4 * 60 * 20;
    private static final float YIELD_FRACTION = 0.15F;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SteelballMentorEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.steelball_mentor.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.steelball_mentor.walk");
    private static final RawAnimation FIGHT = RawAnimation.begin().thenLoop("animation.steelball_mentor.fight");
    private static final RawAnimation THROW = RawAnimation.begin().thenPlay("animation.steelball_mentor.throw");
    private static final RawAnimation NAILS = RawAnimation.begin().thenPlay("animation.steelball_mentor.nails");
    private static final RawAnimation TALK = RawAnimation.begin().thenPlay("animation.steelball_mentor.talk");
    private static final RawAnimation BOW = RawAnimation.begin().thenPlay("animation.steelball_mentor.bow");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID student;
    @Nullable
    private UUID challenger;
    @Nullable
    private ServerBossEvent bar;
    private int visitLeft = VISIT_TICKS;
    private int fightTicks;
    private int yieldTicks;
    private int ballCd = 40, nailCd = 80, lockCd = 140;
    private boolean healed;
    private int strafe = 1;

    public SteelballMentorEntity(EntityType<? extends SteelballMentorEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SbRegistry.STEEL_BALL.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return state() == STATE_IDLE && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, STATE_IDLE);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    private void setState(int s) {
        entityData.set(DATA_STATE, s);
    }

    public void setStudent(@Nullable UUID id) {
        this.student = id;
    }

    @Nullable
    public UUID student() {
        return student;
    }

    public void keepVisiting(int ticks) {
        visitLeft = Math.max(visitLeft, ticks);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SbRegistry.STEEL_BALL.get()));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // =====================================================================================
    //  Conversa
    // =====================================================================================

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (state() == STATE_DUEL) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            getNavigation().stop();
            getLookControl().setLookAt(sp, 30F, 30F);
            triggerAnim("action", "talk");
            SbMentorService.talk(sp, this);
            keepVisiting(1200);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void bow() {
        triggerAnim("action", "bow");
    }

    // =====================================================================================
    //  Desafio
    // =====================================================================================

    public boolean dueling() {
        return state() == STATE_DUEL;
    }

    /** Começa o desafio da lição IV com {@code sp}. */
    public void startDuel(ServerPlayer sp) {
        if (state() == STATE_DUEL) return;
        challenger = sp.getUUID();
        fightTicks = 0;
        healed = false;
        ballCd = 30;
        nailCd = 70;
        lockCd = 120;
        setHealth(getMaxHealth());
        setState(STATE_DUEL);
        bar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.addPlayer(sp);
        ServerLevel sl = (ServerLevel) level();
        SbFx.spiral(sl, position(), SbFx.STEEL, 1.2, 2.4, 40, 0F);
        SbFx.sound(sl, position(), SoundEvents.ANVIL_LAND, 0.8F, 1.6F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.duel.start").withStyle(ChatFormatting.GOLD), true);
    }

    @Nullable
    private ServerPlayer challenger() {
        if (challenger == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(challenger) instanceof ServerPlayer sp && sp.level() == level() ? sp : null;
    }

    private void endDuel(boolean lost) {
        ServerPlayer sp = challenger();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        challenger = null;
        setState(STATE_IDLE);
        setHealth(getMaxHealth());
        getNavigation().stop();
        if (sp != null) {
            sp.displayClientMessage(Component.translatable(lost ? "message.isekai_mahou.steelball.duel.lost"
                    : "message.isekai_mahou.steelball.duel.timeout").withStyle(ChatFormatting.GRAY), true);
        }
        keepVisiting(2400);
    }

    private void yieldTo(ServerPlayer sp) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        setState(STATE_YIELD);
        yieldTicks = 60;
        setHealth(Math.max(1F, getMaxHealth() * YIELD_FRACTION));
        getNavigation().stop();
        triggerAnim("action", "bow");
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        SbFx.sound(sl, position(), SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
        MagicApi.addCounter(sp, SbProgress.DUEL, 1);
        Dialogue.show(sp, "steelball.mentor.yield", getDisplayName(), SbProgress.RGB, List.of(
                Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.steelball.duel.yield1")),
                Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.steelball.duel.yield2"))));
        challenger = null;
        keepVisiting(2400);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() != STATE_DUEL) {
            // fora do desafio: desvia girando
            if (source.getEntity() instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
                SbFx.spinRing(sl, position().add(0, 1, 0), SbFx.STEEL, 0.8, 10);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.mentor.dodge").withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        ServerPlayer sp = challenger();
        if (sp == null || source.getEntity() != sp) return false;
        if (getHealth() - amount <= getMaxHealth() * YIELD_FRACTION) {
            yieldTo(sp);
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return state() == STATE_DUEL;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        switch (state()) {
            case STATE_DUEL -> duelTick(sl);
            case STATE_YIELD -> {
                if (--yieldTicks <= 0) {
                    setState(STATE_IDLE);
                    setHealth(getMaxHealth());
                }
            }
            default -> {
                if (--visitLeft <= 0 && level().getNearestPlayer(this, 6) == null) leave(sl);
            }
        }
        if (bar != null) bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
    }

    private void duelTick(ServerLevel sl) {
        ServerPlayer sp = challenger();
        if (sp == null || !sp.isAlive() || sp.distanceToSqr(this) > 40 * 40) {
            endDuel(true);
            return;
        }
        if (++fightTicks > MAX_FIGHT) {
            endDuel(false);
            return;
        }
        boolean phase2 = getHealth() < getMaxHealth() * 0.5F;
        if (phase2 && !healed) {
            healed = true;
            heal(getMaxHealth() * 0.25F);
            SbFx.spiral(sl, position(), SbFx.HEAL, 1.0, 2.2, 36, 0F);
            SbFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.duel.phase2").withStyle(ChatFormatting.GOLD), true);
        }
        getLookControl().setLookAt(sp, 40F, 40F);
        // mantém distância de 6–10 blocos, rodeando
        double d = Math.sqrt(distanceToSqr(sp));
        if (fightTicks % 60 == 0) strafe = random.nextBoolean() ? 1 : -1;
        if (fightTicks % 10 == 0) {
            Vec3 away = position().subtract(sp.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-3) away = new Vec3(1, 0, 0);
            away = away.normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafe * 3.0);
            Vec3 dest = d < 6 ? position().add(away.scale(4)).add(side) : d > 10 ? sp.position().add(away.scale(8)) : position().add(side);
            getNavigation().moveTo(dest.x, dest.y, dest.z, 1.1);
        }
        if (--ballCd <= 0) {
            ballCd = phase2 ? 24 : 32;
            triggerAnim("action", "throw");
            sl.addFreshEntity(SteelBallEntity.mob(sl, this, sp, phase2 ? 7F : 5F, phase2));
            SbFx.sound(sl, position(), SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.5F);
        }
        if (--nailCd <= 0) {
            nailCd = phase2 ? 60 : 80;
            triggerAnim("action", "nails");
            for (int i = 0; i < 5; i++) {
                final int k = i;
                com.renanpontes.isekaimahou.core.boss.BossKit.schedule(sl, 2 * i, () -> {
                    ServerPlayer t = challenger();
                    if (t == null || !isAlive() || state() != STATE_DUEL) return;
                    Vec3 dir = t.getEyePosition().subtract(getEyePosition()).normalize();
                    sl.addFreshEntity(SpinNailEntity.mob(sl, this, dir, 2.5F));
                    SbFx.sound(sl, position(), SoundEvents.ARROW_SHOOT, 0.5F, 1.8F + k * 0.05F);
                });
            }
        }
        if (--lockCd <= 0) {
            lockCd = phase2 ? 110 : 150;
            Vec3 at = sp.position();
            Telegraph.circle(sl, at, 2.5F, 25, Telegraph.WARNING);
            com.renanpontes.isekaimahou.core.boss.BossKit.schedule(sl, 25, () -> {
                ServerPlayer t = challenger();
                if (t == null || !isAlive() || state() != STATE_DUEL) return;
                SbFx.spinRing(sl, at.add(0, 0.2, 0), SbFx.STEEL, 2.5, 24);
                SbFx.sound(sl, at, SoundEvents.CHAIN_PLACE, 1.0F, 0.7F);
                if (t.position().distanceToSqr(at) <= 2.8 * 2.8) {
                    t.hurt(damageSources().mobAttack(this), 5F);
                    Statuses.apply(t, Statuses.Kind.ROOTED, 1, 40, this, 1F);
                    SbSpin.apply(t, this, 0, 40);
                }
            });
        }
    }

    /** Some numa nuvem (fim da visita). */
    public void leave(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.03);
        SbFx.sound(sl, position(), SoundEvents.HORSE_GALLOP, 0.8F, 1.0F);
        if (bar != null) bar.removeAllPlayers();
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        super.remove(reason);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (state() == STATE_DUEL) return state.setAndContinue(state.isMoving() ? WALK : FIGHT);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("throw", THROW)
                .triggerableAnim("nails", NAILS)
                .triggerableAnim("talk", TALK)
                .triggerableAnim("bow", BOW));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (student != null) tag.putUUID("Student", student);
        tag.putInt("VisitLeft", visitLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        student = tag.hasUUID("Student") ? tag.getUUID("Student") : null;
        visitLeft = tag.contains("VisitLeft") ? tag.getInt("VisitLeft") : VISIT_TICKS;
        // o desafio não sobrevive a recarregar o mundo
        setState(STATE_IDLE);
    }
}
