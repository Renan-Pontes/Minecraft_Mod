package com.renanpontes.isekaimahou.feature.ritual_warlock.block;

import java.util.UUID;

import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Altar de Oferendas: guarda a oferenda (1 item, mostrada flutuando) e o Patrono preso no círculo.
 * O cliente recebe a oferenda e se há ritual ativo (para o renderer e as partículas).
 */
public class OfferingAltarBlockEntity extends BlockEntity {
    private ItemStack offering = ItemStack.EMPTY;
    private UUID patron;
    /** Cópia no cliente de "tem Patrono preso aqui". */
    private boolean active;

    public OfferingAltarBlockEntity(BlockPos pos, BlockState state) {
        super(WarlockContent.OFFERING_ALTAR_BE.get(), pos, state);
    }

    public ItemStack getOffering() {
        return offering;
    }

    public void setOffering(ItemStack stack) {
        offering = stack.copy();
        sync();
    }

    /** Tira a oferenda do altar (devolvida ao jogador ou consumida pelo ritual). */
    public ItemStack takeOffering() {
        ItemStack s = offering;
        offering = ItemStack.EMPTY;
        sync();
        return s;
    }

    public boolean isActiveClient() {
        return active;
    }

    public void setPatron(UUID id) {
        patron = id;
        active = id != null;
        sync();
    }

    /** Patrono preso neste círculo (carregado e ainda preso), ou null — limpa referências velhas. */
    public PatronEntity boundPatron(ServerLevel level) {
        if (patron == null) return null;
        Entity e = level.getEntity(patron);
        if (e instanceof PatronEntity p && p.isAlive() && p.isBound()) return p;
        // As entidades de um chunk carregam DEPOIS dos blocos: só esquece o Patrono quando elas já estão no
        // mundo (senão um clique logo após o chunk carregar apagaria um ritual em andamento).
        if (level.areEntitiesLoaded(ChunkPos.asLong(worldPosition))) setPatron(null);
        return null;
    }

    /** Há um Patrono ligado a este altar (mesmo que a entidade dele ainda não tenha carregado). */
    public boolean isOccupied(ServerLevel level) {
        return boundPatron(level) != null || patron != null;
    }

    public boolean isPatron(UUID id) {
        return patron != null && patron.equals(id);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!offering.isEmpty()) tag.put("Offering", offering.save(registries));
        if (patron != null) tag.putUUID("Patron", patron);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        offering = tag.contains("Offering") ? ItemStack.parseOptional(registries, tag.getCompound("Offering")) : ItemStack.EMPTY;
        patron = tag.hasUUID("Patron") ? tag.getUUID("Patron") : null;
        active = patron != null || tag.getBoolean("Active");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!offering.isEmpty()) tag.put("Offering", offering.save(registries));
        tag.putBoolean("Active", patron != null);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
