package com.renanpontes.isekaimahou.feature.renown;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Põe o <b>Quadro de Renome nas cidades</b> sem mexer nos moldes delas: na primeira vez que alguém usa um Quadro de
 * Missões/Facção numa cidade do mundo (lugar de NPC, não cidade de jogador), um Quadro de Renome aparece AO LADO dele —
 * só num bloco de ar com chão firme, mesma parede atrás, frente livre e longe de portas. Nada é quebrado nem trocado
 * ("cidades são sagradas"). Uma vez por cidade; se alguém tirar o quadro de lá, ele não volta.
 */
final class BoardPlacer {
    private static final ResourceLocation QUEST_BOARD = IsekaiMahou.id("quest_board"), FACTION_BOARD = IsekaiMahou.id("faction_board");

    private BoardPlacer() {}

    static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = e.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.equals(QUEST_BOARD) && !id.equals(FACTION_BOARD)) return;
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Places.Place place = Places.at(level, pos);
        // Só cidades do mundo (têm facção dona); cidade de jogador recebe o quadro pela receita, onde o dono quiser
        if (place == null || place.faction().isEmpty() || !("city".equals(place.type()) || "capital".equals(place.type()))) return;
        RenownData data = RenownData.get(level.getServer());
        if (data.boardPlaces.contains(place.id())) return;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos spot = pos.relative(side);
            if (!place.contains(spot) || !fits(level, pos, spot, facing)) continue;
            Block board = RenownContent.RENOWN_BOARD.get();
            level.setBlock(spot, board.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), Block.UPDATE_ALL);
            data.boardPlaces.add(place.id());
            data.setDirty();
            return;
        }
    }

    private static boolean fits(ServerLevel level, BlockPos questBoard, BlockPos spot, Direction facing) {
        if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.relative(facing)).isAir()) return false;
        BlockPos below = spot.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        // Mesma situação atrás (parede com parede, vão com vão): o quadro novo continua a linha do que já existe
        Direction back = facing.getOpposite();
        if (level.getBlockState(spot.relative(back)).isAir() != level.getBlockState(questBoard.relative(back)).isAir()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(spot.relative(d)).getBlock() instanceof DoorBlock) return false;
        }
        return true;
    }
}
