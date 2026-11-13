package com.renanpontes.isekaimahou.feature.towers.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.towers.TowerRecords;
import com.renanpontes.isekaimahou.feature.towers.TwRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Núcleo do Andar: pedestal no centro de cada andar da Torre de Desafio. O {@link FloorCoreBlockEntity} roda o desafio
 * (ondas, runas ou guardião) e, quando o andar é vencido, some com os Selos e o topo fica verde ({@link #CLEARED}).
 */
public class FloorCoreBlock extends Block implements EntityBlock {
    public static final BooleanProperty CLEARED = BooleanProperty.create("cleared");

    public FloorCoreBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(CLEARED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLEARED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloorCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != TwRegistries.FLOOR_CORE_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<FloorCoreBlockEntity>) FloorCoreBlockEntity::serverTick;
    }

    /**
     * Clique: mostra o andar, o estado do desafio e (v1.0) o recorde de tempo deste andar nesta variante de torre — o do
     * mundo e o de quem clicou. Uma linha só, em cima da barra de itens.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FloorCoreBlockEntity be && level.getServer() != null) {
            Component line = Component.translatable("tower.isekai_mahou.core.status", be.floorLabel(),
                    Component.translatable("tower.isekai_mahou.core." + be.stateName()));
            TowerRecords.Entry best = TowerRecords.get(level.getServer()).bestEntry(TowerRecords.key(be.variant(), be.floor()));
            if (best != null) {
                int mine = TowerRecords.personalBest(level.getServer(), player.getUUID(), be.variant(), be.floor());
                String time = TowerRecords.format(best.ticks());
                line = mine >= 0 && !best.player().equals(player.getUUID())
                        ? Component.translatable("tower.isekai_mahou.core.record_mine", line, time, best.name(), TowerRecords.format(mine))
                        : Component.translatable("tower.isekai_mahou.core.record", line, time, best.name());
            }
            player.displayClientMessage(line, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
