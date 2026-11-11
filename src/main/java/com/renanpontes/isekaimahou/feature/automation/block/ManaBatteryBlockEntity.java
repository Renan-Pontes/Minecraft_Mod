package com.renanpontes.isekaimahou.feature.automation.block;

import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bateria de Mana: guarda até {@link #CAPACITY} de mana. Pedestais gastam daqui; condensadores e jogadores enchem.
 * O nível (0–4) vira estado do bloco (textura/luz) e o comparador lê 0–15.
 */
public class ManaBatteryBlockEntity extends BlockEntity {
    /** v1.0: 60 000 (×{@link ResourceTraining#MANA_SCALE} da v0.8: guarda o mesmo número de conjurações). */
    public static final int CAPACITY = Math.round(20_000 * ResourceTraining.MANA_SCALE);

    private int mana;

    public ManaBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(AutoRegistries.MANA_BATTERY_BE.get(), pos, state);
    }

    public int mana() { return mana; }
    public int space() { return CAPACITY - mana; }

    /** Coloca mana; devolve quanto coube. */
    public int insert(int amount) {
        int add = Mth.clamp(amount, 0, space());
        if (add > 0) setMana(mana + add);
        return add;
    }

    /** Tira mana; devolve quanto saiu. */
    public int extract(int amount) {
        int take = Mth.clamp(amount, 0, mana);
        if (take > 0) setMana(mana - take);
        return take;
    }

    private void setMana(int value) {
        int oldSignal = comparatorSignal(mana);
        mana = Mth.clamp(value, 0, CAPACITY);
        setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        int charge = chargeLevel(mana);
        if (state.hasProperty(ManaBatteryBlock.CHARGE) && state.getValue(ManaBatteryBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, state.setValue(ManaBatteryBlock.CHARGE, charge), Block.UPDATE_ALL);
        }
        if (comparatorSignal(mana) != oldSignal) level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }

    /** Nível visual 0–4 (0 só quando vazia, 4 só quando cheia). */
    public static int chargeLevel(int mana) {
        if (mana <= 0) return 0;
        if (mana >= CAPACITY) return 4;
        return Mth.clamp(1 + mana * 3 / CAPACITY, 1, 3);
    }

    public static int comparatorSignal(int mana) {
        return mana <= 0 ? 0 : Math.min(15, 1 + (int) ((long) mana * 14 / CAPACITY));
    }

    // ---------------------------------------------------------------- NBT e componentes do item
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mana", mana);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mana = Mth.clamp(tag.getInt("Mana"), 0, CAPACITY);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        mana = Mth.clamp(input.getOrDefault(AutoRegistries.BATTERY_MANA.get(), 0), 0, CAPACITY);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (mana > 0) components.set(AutoRegistries.BATTERY_MANA.get(), mana);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Mana");
    }
}
