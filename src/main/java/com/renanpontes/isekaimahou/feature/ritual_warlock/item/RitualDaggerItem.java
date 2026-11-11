package com.renanpontes.isekaimahou.feature.ritual_warlock.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Adaga Ritual: arma leve e rápida. Matar com ela colhe a alma do alvo (contador warlock.souls) e dá
 * um pouco de XP mágico (ver WarlockEvents). No altar, paga o sangue que ativa o círculo.
 * Clique numa vela apagada: acende com uma faísca infernal.
 */
public class RitualDaggerItem extends SwordItem {
    public RitualDaggerItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!CandleBlock.canLight(state)) return super.useOn(ctx);
        if (level instanceof ServerLevel sl) {
            sl.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            sl.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.6F);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.1, 0.15, 0.1, 0.01);
            sl.gameEvent(ctx.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_dagger.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_dagger.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_dagger.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
