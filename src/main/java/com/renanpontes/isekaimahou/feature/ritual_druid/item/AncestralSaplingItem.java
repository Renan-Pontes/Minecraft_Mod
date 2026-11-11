package com.renanpontes.isekaimahou.feature.ritual_druid.item;

import com.renanpontes.isekaimahou.feature.ritual_druid.DruidGrove;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidProgress;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.AncestralSaplingBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Item da Muda Ancestral: só deixa plantar se o jogador ainda não tiver uma Árvore Coração viva e se não houver
 * outra Árvore Coração perto demais (as copas se sobreporiam e uma "curaria" os blocos da outra sem fim).
 */
public class AncestralSaplingItem extends BlockItem {
    /** Distância horizontal mínima entre duas Árvores Coração (a copa no estágio X chega a ~9 blocos do tronco). */
    public static final int MIN_DISTANCE = 20;

    public AncestralSaplingItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel level) {
            if (DruidProgress.hasLivingTree(sp)) {
                GlobalPos tree = DruidGrove.of(sp).tree();
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.only_one",
                        tree.pos().getX(), tree.pos().getY(), tree.pos().getZ()).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            if (otherTreeNear(level, ctx.getClickedPos())) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.too_close", MIN_DISTANCE)
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
        }
        return super.place(ctx);
    }

    /** Procura Núcleos/Mudas Ancestrais nos chunks carregados em volta (só block entities: barato). */
    private static boolean otherTreeNear(ServerLevel level, BlockPos pos) {
        int r2 = MIN_DISTANCE * MIN_DISTANCE;
        for (int cx = (pos.getX() - MIN_DISTANCE) >> 4; cx <= (pos.getX() + MIN_DISTANCE) >> 4; cx++) {
            for (int cz = (pos.getZ() - MIN_DISTANCE) >> 4; cz <= (pos.getZ() + MIN_DISTANCE) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof HeartTreeCoreBlockEntity) && !(be instanceof AncestralSaplingBlockEntity)) continue;
                    BlockPos o = be.getBlockPos();
                    double dx = o.getX() - pos.getX(), dz = o.getZ() - pos.getZ();
                    if (dx * dx + dz * dz < r2 && Math.abs(o.getY() - pos.getY()) < 32) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
