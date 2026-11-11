package com.renanpontes.isekaimahou.feature.ascension.block;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ascension.AscensionAltarGuard;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pedra Rúnica: decorativa e obrigatória no padrão. Quando há um Núcleo de Ascensão por perto,
 * as runas soltam letras encantadas que voam até ele.
 */
public class RuneStoneBlock extends Block {
    public RuneStoneBlock(Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        BlockPos core = findCore(level, pos);
        double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
        if (core != null) {
            // A partícula ENCHANT nasce em (destino + velocidade) e voa até o destino: sai da pedra e entra no Núcleo.
            level.addParticle(ParticleTypes.ENCHANT, core.getX() + 0.5, core.getY() + 1.1, core.getZ() + 0.5,
                    pos.getX() - core.getX() + random.nextDouble() - 0.5, pos.getY() - core.getY() + 0.2,
                    pos.getZ() - core.getZ() + random.nextDouble() - 0.5);
        } else {
            level.addParticle(ParticleTypes.ENCHANT, x, y + 0.4, z, (random.nextDouble() - 0.5) * 0.8, -0.6, (random.nextDouble() - 0.5) * 0.8);
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Peça de altar público: não quebra fora do criativo.
        if (AscensionAltarGuard.lockedFor(player, level, pos)) return 0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return AscensionAltarGuard.isPublicPiece(level, pos) ? AscensionAltarGuard.BLAST_PROOF
                : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return AscensionAltarGuard.entityMayDestroy(entity, level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }

    private static BlockPos findCore(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 0, 4))) {
            if (level.getBlockState(p).is(AscensionRegistry.CORE.get())) return p.immutable();
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.rune_stone").withStyle(ChatFormatting.GRAY));
    }
}
