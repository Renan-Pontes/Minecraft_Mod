package com.renanpontes.isekaimahou.feature.ascension.block;

import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Pedestal: guarda uma pilha (um tipo de item) e sincroniza com o cliente para o item flutuante. */
public class AscensionPedestalBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;

    public AscensionPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(AscensionRegistry.PEDESTAL_BE.get(), pos, state);
    }

    public ItemStack getItem() {
        return item;
    }

    /** Coloca até {@code amount} itens de {@code from} (não altera {@code from}). Retorna quantos entraram. */
    public int insert(ItemStack from, int amount) {
        if (from.isEmpty() || amount <= 0) return 0;
        if (item.isEmpty()) {
            int n = Math.min(amount, from.getMaxStackSize());
            item = from.copyWithCount(n);
            changed();
            return n;
        }
        if (!ItemStack.isSameItemSameComponents(item, from)) return 0;
        int n = Math.min(amount, item.getMaxStackSize() - item.getCount());
        if (n <= 0) return 0;
        item.grow(n);
        changed();
        return n;
    }

    /** Retira até {@code count} itens do tipo informado (usado ao consumir o tributo). */
    public ItemStack take(Item type, int count) {
        if (item.isEmpty() || !item.is(type) || count <= 0) return ItemStack.EMPTY;
        ItemStack out = item.split(count);
        changed();
        return out;
    }

    public ItemStack removeAll() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, s.getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) tag.put("item", item.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        item = tag.contains("item") ? ItemStack.parseOptional(registries, tag.getCompound("item")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // Nunca vazio: o cliente ignora pacotes com tag vazia (e não limparia o item retirado).
        tag.putBoolean("synced", true);
        if (!item.isEmpty()) tag.put("item", item.save(registries));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
