package com.renanpontes.isekaimahou.feature.dimensions.block;

import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Só existe para o cliente desenhar o feixe de luz do Farol Celestial (o estado "active" fica no bloco). */
public class CelestialBeaconBlockEntity extends BlockEntity {
    public CelestialBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(DimRegistry.CELESTIAL_BEACON_BE.get(), pos, state);
    }

    public boolean isActive() {
        BlockState s = getBlockState();
        return s.hasProperty(CelestialBeaconBlock.ACTIVE) && s.getValue(CelestialBeaconBlock.ACTIVE);
    }
}
