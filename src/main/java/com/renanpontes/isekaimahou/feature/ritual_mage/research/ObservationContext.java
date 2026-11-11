package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * O que a Lente Arcana está vendo: entidade/bloco/fluido mirados, se olha o céu aberto,
 * a posição do jogador (para bioma/estrutura/clima) e o ponto focado (para as partículas).
 */
public record ObservationContext(ServerPlayer player, ServerLevel level, @Nullable Entity entity, @Nullable BlockState block,
                                 @Nullable BlockState fluid, boolean openSky, BlockPos pos, Vec3 focus) {
}
