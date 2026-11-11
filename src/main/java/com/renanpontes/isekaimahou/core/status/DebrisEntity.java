package com.renanpontes.isekaimahou.core.status;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Escombro de destruição: um bloco que voa girando para fora da cratera e some em 2–3 s. É SÓ visual: nunca coloca
 * bloco, nunca vira item, não é salvo no mundo, não pode ser atingido.
 */
public class DebrisEntity extends Entity {
    public static final int LIFETIME = 50;
    /** Máximo de escombros vivos no servidor (todos os mundos). */
    public static final int MAX_ALIVE = 160;
    private static final EntityDataAccessor<BlockState> DATA_STATE = SynchedEntityData.defineId(DebrisEntity.class,
            EntityDataSerializers.BLOCK_STATE);

    private static int alive;
    private boolean counted;
    /** Cliente: giro aleatório (graus por tick). */
    public float spinX, spinZ;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
        this.spinX = (level.random.nextFloat() - 0.5F) * 40F;
        this.spinZ = (level.random.nextFloat() - 0.5F) * 40F;
    }

    /** Lança um escombro (respeita o limite global). */
    public static boolean spawn(ServerLevel level, Vec3 at, BlockState state, Vec3 velocity) {
        if (alive >= MAX_ALIVE || state.isAir() || !state.getFluidState().isEmpty()) return false;
        DebrisEntity e = new DebrisEntity(StatusRegistries.DEBRIS.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.setDeltaMovement(velocity);
        e.entityData.set(DATA_STATE, state);
        if (!level.addFreshEntity(e)) return false;
        e.counted = true;
        alive++;
        return true;
    }

    static void resetCount() {
        alive = 0;
    }

    public BlockState blockState() {
        return entityData.get(DATA_STATE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, Blocks.STONE.defaultBlockState());
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public void tick() {
        super.tick();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 v = getDeltaMovement();
        if (onGround()) {
            // Quica uma vez e para (nunca vira bloco)
            v = new Vec3(v.x * 0.55, v.y < -0.12 ? -v.y * 0.35 : 0, v.z * 0.55);
            spinX *= 0.6F;
            spinZ *= 0.6F;
        } else {
            v = v.scale(0.98);
        }
        setDeltaMovement(v);
        if (!level().isClientSide && (tickCount >= LIFETIME || isInLava())) discard();
    }

    /**
     * Saiu do mundo por QUALQUER caminho (sumiu, chunk descarregado, nível fechado): libera a vaga no limite global.
     * ({@code remove()} não é chamado quando o chunk descarrega — só {@code setRemoved}.)
     */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (counted && !level().isClientSide) {
            counted = false;
            alive = Math.max(0, alive - 1);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, Block.getId(blockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        entityData.set(DATA_STATE, Block.stateById(packet.getData()));
        setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
