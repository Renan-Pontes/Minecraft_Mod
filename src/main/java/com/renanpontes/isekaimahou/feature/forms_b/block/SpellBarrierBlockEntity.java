package com.renanpontes.isekaimahou.feature.forms_b.block;

import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cronômetro da barreira: guarda o tick do mundo em que ela some e o bloco "leve" que ela ocupou
 * (grama, flores, neve fina), para devolvê-lo no fim. Salvo com o chunk, então a barreira some mesmo
 * depois de recarregar o mundo (se o tempo já passou, some na hora).
 */
public class SpellBarrierBlockEntity extends BlockEntity {
    /** Tick do mundo em que a barreira some (-1 = ainda não definido: usa a duração padrão). */
    private long expireAt = -1;
    /** Bloco substituível que estava aqui antes (null = era ar ou água). */
    private BlockState previous;

    public SpellBarrierBlockEntity(BlockPos pos, BlockState state) {
        super(FormsBRegistry.SPELL_BARRIER_BE.get(), pos, state);
    }

    public void setExpireAt(long gameTime) {
        this.expireAt = gameTime;
        setChanged();
    }

    /** Lembra o bloco substituível que a barreira ocupou (planta, neve fina...). */
    public void setPrevious(BlockState state) {
        this.previous = state == null || state.isAir() ? null : state;
        setChanged();
    }

    public BlockState previous() {
        return previous;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpellBarrierBlockEntity be) {
        long now = level.getGameTime();
        if (be.expireAt < 0) be.setExpireAt(now + SpellBarrierBlock.DEFAULT_TICKS);
        else if (now >= be.expireAt) SpellBarrierBlock.dissolve(level, pos);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        expireAt = tag.contains("ExpireAt") ? tag.getLong("ExpireAt") : -1;
        previous = null;
        if (tag.contains("Previous")) {
            BlockState st = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Previous"));
            if (!st.isAir()) previous = st;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("ExpireAt", expireAt);
        if (previous != null) tag.put("Previous", NbtUtils.writeBlockState(previous));
    }
}
