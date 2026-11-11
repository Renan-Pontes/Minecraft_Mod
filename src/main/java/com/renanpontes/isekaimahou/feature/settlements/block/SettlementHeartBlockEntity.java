package com.renanpontes.isekaimahou.feature.settlements.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coração da cidade do jogador: guarda só o id da cidade e o estilo (para o cristal do renderer). O estado da cidade
 * fica no {@code SettlementData}; o tick daqui roda a construção, os letreiros, os portões e a chegada de moradores
 * ({@link SettlementService#tickHeart}) enquanto o coração está carregado.
 */
public class SettlementHeartBlockEntity extends BlockEntity {
    private String cityId = "";
    private String style = SettlementStyle.KINGDOM.id;
    private int age;

    public SettlementHeartBlockEntity(BlockPos pos, BlockState state) {
        super(SettlementsContent.HEART_BE.get(), pos, state);
    }

    public String cityId() {
        return cityId;
    }

    public SettlementStyle style() {
        SettlementStyle s = SettlementStyle.byId(style);
        return s == null ? SettlementStyle.KINGDOM : s;
    }

    public void bind(String id, SettlementStyle s) {
        cityId = id;
        style = s.id;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int age() {
        return age;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SettlementHeartBlockEntity be) {
        be.age++;
        if (level instanceof ServerLevel sl && !be.cityId.isEmpty()) SettlementService.tickHeart(sl, be);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("City", cityId);
        tag.putString("Style", style);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cityId = tag.getString("City");
        if (tag.contains("Style")) style = tag.getString("Style");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("City", cityId);
        tag.putString("Style", style);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
