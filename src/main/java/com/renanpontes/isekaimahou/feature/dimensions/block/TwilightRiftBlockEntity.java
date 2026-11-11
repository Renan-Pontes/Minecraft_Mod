package com.renanpontes.isekaimahou.feature.dimensions.block;

import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Fenda do Crepúsculo: usa o efeito estrelado do portal do End, mas nas seis faces (um cubo de "céu rasgado"). */
public class TwilightRiftBlockEntity extends TheEndPortalBlockEntity {
    public TwilightRiftBlockEntity(BlockPos pos, BlockState state) {
        super(DimRegistry.TWILIGHT_RIFT_BE.get(), pos, state);
    }

    @Override
    public boolean shouldRenderFace(Direction face) {
        return level == null || !level.getBlockState(worldPosition.relative(face)).is(getBlockState().getBlock());
    }
}
