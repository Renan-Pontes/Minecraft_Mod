package com.renanpontes.isekaimahou.feature.automation.block;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Condensador de Mana: queima cristais, essências, lápis, ametista e redstone e manda o mana para as
 * Baterias a até {@link ManaNet#BATTERY_RANGE} blocos. Funis colocam combustível; ninguém tira.
 * Se não há bateria com espaço, a reserva enche e a queima pausa no meio do item (não desperdiça nada).
 */
public class ManaCondenserBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SIZE = 9;
    /** v1.0: toda a mana do condensador é ×{@link ResourceTraining#MANA_SCALE} da v0.8 (custos dos pedestais também). */
    private static final float SCALE = ResourceTraining.MANA_SCALE;
    public static final int BUFFER_CAP = Math.round(2000 * SCALE);
    /** Mana enviado às baterias a cada 5 ticks (v1.0: 360 → 1440/s). */
    private static final int SEND_PER_PULSE = Math.round(120 * SCALE);
    private static final int[] ALL_SLOTS = IntStream.range(0, SIZE).toArray();
    private static final ResourceLocation CRYSTAL_BLOCK = IsekaiMahou.id("mana_crystal_block");

    // Índices do ContainerData (tela)
    public static final int DATA_BUFFER = 0, DATA_BURN_LEFT = 1, DATA_BURN_TOTAL = 2, DATA_BATTERIES = 3, DATA_STORED_LO = 4,
            DATA_STORED_HI = 5, DATA_COUNT = 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int buffer;
    private int burnMana;
    private int burnLeft;
    private int burnTotal;
    private int batteries;
    private int stored;
    private int litHold;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_BUFFER -> buffer;
                case DATA_BURN_LEFT -> burnLeft;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_BATTERIES -> batteries;
                case DATA_STORED_LO -> stored & 0xFFFF;
                case DATA_STORED_HI -> (stored >>> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case DATA_BUFFER -> buffer = value;
                case DATA_BURN_LEFT -> burnLeft = value;
                case DATA_BURN_TOTAL -> burnTotal = value;
                case DATA_BATTERIES -> batteries = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ManaCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(AutoRegistries.MANA_CONDENSER_BE.get(), pos, state);
    }

    public ContainerData data() { return data; }

    // =====================================================================================
    //  Combustíveis
    // =====================================================================================

    /** Mana que um item rende no condensador (0 = não é combustível). v1.0: valores da v0.8 × escala de mana. */
    public static int fuelValue(ItemStack stack) {
        int base = baseFuel(stack);
        return base <= 0 ? 0 : Math.round(base * SCALE);
    }

    /** Rendimento na escala da v0.8 (a tabela da tela mostra estes × {@link ResourceTraining#MANA_SCALE}). */
    private static int baseFuel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (item == CoreRegistries.MANA_CRYSTAL.get()) return 400;
        if (item == Items.LAPIS_LAZULI) return 40;
        if (item == Items.LAPIS_BLOCK) return 360;
        if (item == Items.AMETHYST_SHARD) return 60;
        if (item == Items.AMETHYST_BLOCK) return 240;
        if (item == Items.REDSTONE) return 20;
        if (item == Items.REDSTONE_BLOCK) return 180;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.equals(CRYSTAL_BLOCK)) return 3600;
        if (id.getNamespace().equals(IsekaiMahou.MODID) && id.getPath().startsWith("essence_")) return 250;
        return 0;
    }

    /** Tempo de queima: o mesmo da v0.8 — 1 tick a cada 5 de mana na escala antiga (15 na v1.0; mín. 0,5 s). */
    private static int burnTicks(int value) {
        return Math.max(10, Math.round(value / (5 * SCALE)));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaCondenserBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.tick(server, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        boolean changed = false;
        boolean burning = false;
        if (burnLeft > 0) {
            int give = Mth.ceil(burnMana / (float) burnLeft);
            // Reserva cheia: a queima PAUSA (o mana que falta do item fica guardado, nada se perde)
            if (buffer + give <= BUFFER_CAP) {
                buffer += give;
                burnMana -= give;
                burnLeft--;
                burning = true;
                changed = true;
            }
        }
        if (burnLeft <= 0) {
            burnMana = 0;
            if (startNextFuel()) {
                burning = true;
                changed = true;
            }
        }
        long now = level.getGameTime() + worldPosition.asLong();
        if (now % 5 == 0) changed |= sendToBatteries(level, now % 20 == 0);

        // Fica aceso mais 1 s depois de queimar (sem piscar quando a reserva pausa e volta)
        litHold = burning ? 20 : Math.max(0, litHold - 1);
        boolean lit = litHold > 0;
        if (state.getValue(ManaCondenserBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(ManaCondenserBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (changed) setChanged();
    }

    private boolean startNextFuel() {
        if (buffer >= BUFFER_CAP) return false; // espera a reserva esvaziar
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            int value = fuelValue(stack);
            if (value <= 0) continue;
            stack.shrink(1);
            burnMana = value;
            burnTotal = burnTicks(value);
            burnLeft = burnTotal;
            return true;
        }
        burnTotal = 0;
        return false;
    }

    /** Manda a reserva para as baterias (as mais vazias primeiro). */
    private boolean sendToBatteries(ServerLevel level, boolean showFlow) {
        List<ManaBatteryBlockEntity> list = ManaNet.batteries(level, worldPosition);
        batteries = list.size();
        stored = ManaNet.total(list);
        if (buffer <= 0 || list.isEmpty()) return false;
        list.sort(Comparator.comparingInt(ManaBatteryBlockEntity::mana));
        int budget = Math.min(buffer, SEND_PER_PULSE);
        ManaBatteryBlockEntity firstTarget = null;
        for (ManaBatteryBlockEntity b : list) {
            int put = b.insert(budget);
            if (put > 0 && firstTarget == null) firstTarget = b;
            budget -= put;
            buffer -= put;
            if (budget <= 0) break;
        }
        if (firstTarget == null) return false;
        stored = ManaNet.total(list);
        if (showFlow) {
            Vec3 from = Vec3.atCenterOf(worldPosition).add(0, 0.55, 0);
            Vec3 to = Vec3.atCenterOf(firstTarget.getBlockPos());
            AutoFx.line(level, from, to, AutoFx.dust(AutoFx.MANA_RGB, 0.8F), 0.35);
        }
        return true;
    }

    // =====================================================================================
    //  Container
    // =====================================================================================
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return fuelValue(stack) > 0;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.isekai_mahou.mana_condenser");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ManaCondenserMenu(id, inventory, this);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return fuelValue(stack) > 0;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Buffer", buffer);
        tag.putInt("BurnMana", burnMana);
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        buffer = Mth.clamp(tag.getInt("Buffer"), 0, BUFFER_CAP);
        burnMana = Math.max(0, tag.getInt("BurnMana"));
        burnLeft = Math.max(0, tag.getInt("BurnLeft"));
        burnTotal = Math.max(0, tag.getInt("BurnTotal"));
    }
}
