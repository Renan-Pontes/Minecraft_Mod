package com.renanpontes.isekaimahou.feature.ritual_warlock.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.RitualChalkBlock;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Giz Ritual (64 usos). Clique no chão: desenha uma runa de giz. Clique numa runa: troca o desenho.
 * Agachado clicando no Altar de Oferendas: desenha sozinho o anel do próximo pacto.
 */
public class RitualChalkItem extends Item {
    public RitualChalkItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (state.is(WarlockContent.OFFERING_ALTAR.get()) && player != null && player.isShiftKeyDown()) {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                PactRitual.autoDraw(sp, sl, clicked, stack, ctx.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.is(WarlockContent.RITUAL_CHALK.get())) {
            if (!level.isClientSide) {
                level.setBlock(clicked, state.cycle(RitualChalkBlock.RUNE), 3);
                level.playSound(null, clicked, SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, 0.8F, 1.4F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos target = state.canBeReplaced() ? clicked : clicked.relative(ctx.getClickedFace());
        if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL;
        BlockState chalk = WarlockContent.RITUAL_CHALK.get().defaultBlockState().setValue(RitualChalkBlock.RUNE, RitualChalkBlock.runeFor(target));
        if (!chalk.canSurvive(level, target)) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            level.setBlock(target, chalk, 3);
            level.playSound(null, target, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 0.9F, 1.3F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
            if (player != null && !player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(ctx.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_chalk_item.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_chalk_item.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_chalk_item.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
