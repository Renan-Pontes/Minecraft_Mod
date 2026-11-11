package com.renanpontes.isekaimahou.feature.automation.block;

import java.util.List;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Coletor Arcano: puxa os itens soltos num raio de {@link #RADIUS} blocos para 27 espaços.
 * Funis (e canos de outros mods) só TIRAM itens. Sinal de redstone pausa a coleta.
 * Pedestais com o glifo Recolher mandam os itens direto para o coletor mais próximo.
 */
public class ArcaneCollectorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SIZE = 27;
    public static final int RADIUS = 6;
    private static final int[] ALL_SLOTS = IntStream.range(0, SIZE).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public ArcaneCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(AutoRegistries.ARCANE_COLLECTOR_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArcaneCollectorBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0 || !(level instanceof ServerLevel server)) return;
        if (level.hasNeighborSignal(pos)) return;
        be.pull(server);
    }

    /** Puxa os itens do chão em volta. */
    private void pull(ServerLevel level) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition).inflate(RADIUS),
                e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty());
        int moved = 0;
        for (ItemEntity ie : found) {
            ItemStack stack = ie.getItem();
            ItemStack rest = insert(stack.copy());
            if (rest.getCount() == stack.getCount()) continue;
            Vec3 at = ie.position().add(0, 0.2, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
            if (moved < 4) AutoFx.line(level, at, center.add(0, 0.4, 0), AutoFx.dust(AutoFx.MANA_RGB, 0.6F), 0.45);
            if (rest.isEmpty()) ie.discard();
            else ie.setItem(rest);
            moved++;
        }
        if (moved > 0) {
            level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.4, center.z, 12, 0.3, 0.2, 0.3, 0.3);
            AutoFx.sound(level, center, SoundEvents.ITEM_PICKUP, 0.25F, 0.6F + level.random.nextFloat() * 0.3F);
        }
    }

    /** Guarda um item (junta com pilhas iguais, depois espaços vazios). Devolve o que não coube. */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack rest = stack.copy();
        boolean changed = false;
        for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, rest)) continue;
            int room = Math.min(slot.getMaxStackSize(), getMaxStackSize()) - slot.getCount();
            if (room <= 0) continue;
            int n = Math.min(room, rest.getCount());
            slot.grow(n);
            rest.shrink(n);
            changed = true;
        }
        for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
            if (!items.get(i).isEmpty()) continue;
            int n = Math.min(rest.getCount(), rest.getMaxStackSize());
            items.set(i, rest.split(n));
            changed = true;
        }
        if (changed) setChanged();
        return rest;
    }

    // ---------------------------------------------------------------- Container
    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.isekai_mahou.arcane_collector");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    /** Funis não colocam itens: o coletor só recebe o que ele mesmo puxa (ou o que o jogador põe na mão). */
    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
