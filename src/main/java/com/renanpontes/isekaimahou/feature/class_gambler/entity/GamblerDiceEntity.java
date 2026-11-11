package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Dado mágico da forma Dados: é jogado, quica e rola pelo chão; quando para, mostra a face (sincronizada) e avisa a
 * rodada ({@link DiceRound}), que soma os dados e estoura a área. Modelo GeckoLib; o giro enquanto rola e a face de
 * cima ao parar são feitos no modelo do cliente. Some sozinho pouco depois.
 */
public class GamblerDiceEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Byte> DATA_FACE = SynchedEntityData.defineId(GamblerDiceEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(GamblerDiceEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation GLOW = RawAnimation.begin().thenLoop("animation.gambler_dice.glow");
    /** Tempo máximo rolando antes de parar à força. */
    private static final int MAX_ROLL = 45;
    /** Quanto tempo o dado fica mostrando a face depois de parar. */
    private static final int LINGER = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private DiceRound round;
    private int still;
    private int settledAt = -1;
    /** Face escolhida antes (1..6) para dados só de mostrar (Dados do Destino, Aposta Total); 0 = sorteia. */
    private int forcedFace;
    /** Tempo máximo rolando (dados de mostrar param antes, para o resultado sair na hora certa). */
    private int rollLimit = MAX_ROLL;

    public GamblerDiceEntity(EntityType<? extends GamblerDiceEntity> type, Level level) {
        super(type, level);
    }

    public static GamblerDiceEntity create(ServerLevel level, Vec3 pos, Vec3 velocity, int color, @Nullable DiceRound round) {
        GamblerDiceEntity e = new GamblerDiceEntity(GbRegistries.DICE.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(velocity);
        e.setYRot(level.random.nextFloat() * 360F);
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        e.round = round;
        return e;
    }

    /**
     * Dado só de MOSTRAR um resultado já sorteado no servidor: cai/rola e para na face {@code face} em até
     * {@code rollTicks} ticks, sem rodada (nenhum efeito sai dele).
     */
    public static GamblerDiceEntity show(ServerLevel level, Vec3 pos, Vec3 velocity, int color, int face, int rollTicks) {
        GamblerDiceEntity e = create(level, pos, velocity, color, null);
        e.forcedFace = Math.max(1, Math.min(6, face));
        e.rollLimit = Math.max(6, Math.min(MAX_ROLL, rollTicks));
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FACE, (byte) 0);
        builder.define(DATA_COLOR, 0xFFC94A);
    }

    /** Face de cima (1..6), ou 0 enquanto rola. */
    public int face() {
        return entityData.get(DATA_FACE);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        if (face() > 0) {
            if (tickCount - settledAt > LINGER) {
                sl.sendParticles(GbFx.dust(color(), 1.0F), getX(), getY() + 0.25, getZ(), 6, 0.15, 0.15, 0.15, 0);
                discard();
            }
            return;
        }
        // física simples: gravidade, quique amortecido e atrito no chão
        Vec3 v = getDeltaMovement().add(0, -0.06, 0);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        if (horizontalCollision) after = new Vec3(-v.x * 0.5, after.y, -v.z * 0.5);
        if (onGround()) {
            double bounce = v.y < -0.12 ? -v.y * 0.45 : 0;
            if (bounce > 0) GbFx.sound(sl, position(), SoundEvents.BONE_BLOCK_HIT, 0.6F, 1.6F + random.nextFloat() * 0.3F);
            after = new Vec3(after.x * 0.78, bounce, after.z * 0.78);
        } else {
            after = after.scale(0.98);
        }
        setDeltaMovement(after);
        if (tickCount % 3 == 0) sl.sendParticles(GbFx.dust(color(), 0.6F), getX(), getY() + 0.2, getZ(), 1, 0.05, 0.05, 0.05, 0);
        still = onGround() && after.horizontalDistanceSqr() < 0.0009 ? still + 1 : 0;
        if (still >= 4 || tickCount >= rollLimit) settle(sl);
    }

    private void settle(ServerLevel sl) {
        int f = forcedFace > 0 ? forcedFace : round != null ? round.faceFor(this) : 1 + random.nextInt(6);
        entityData.set(DATA_FACE, (byte) f);
        setDeltaMovement(Vec3.ZERO);
        settledAt = tickCount;
        GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F, 1.4F);
        sl.sendParticles(GbFx.dust(color(), 1.0F), getX(), getY() + 0.5, getZ(), 8, 0.2, 0.15, 0.2, 0);
        if (round != null) round.settled(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && face() == 0 && round != null) round.lost(this);
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "glow", 0, state -> state.setAndContinue(GLOW)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Ainda rolando (o modelo do cliente gira o dado). */
    public boolean rolling() {
        return face() == 0;
    }
}
