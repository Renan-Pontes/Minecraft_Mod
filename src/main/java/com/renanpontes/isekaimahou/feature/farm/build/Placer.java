package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.BossArena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Coloca/quebra blocos em nome de alguém respeitando as proteções: altura do mundo, chunk carregado, arenas de chefe
 * ({@link BossArena}), {@code mayInteract} (spawn) e os eventos de colocar/quebrar do NeoForge (mods de proteção,
 * Círculo de Ascensão, guardas da cidade...).
 */
public final class Placer {
    /** Dureza máxima que a construção mágica quebra (obsidiana = 50 fica de fora). */
    public static final float MAX_HARDNESS = 30F;

    public static boolean canEdit(ServerLevel level, @Nullable Player player, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (BossArena.isProtected(level, pos)) return false;
        return player == null || level.mayInteract(player, pos);
    }

    /** O lugar está livre para um bloco novo (ar, água, grama alta, neve fina...)? */
    public static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced() || (s.getFluidState().is(Fluids.WATER) && s.getBlock() == Blocks.WATER);
    }

    /** Coloca o estado; volta atrás se algum evento de proteção cancelar. */
    public static boolean place(ServerLevel level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (!canEdit(level, player, pos)) return false;
        BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        if (player != null && EventHooks.onBlockPlace(player, snap, Direction.UP)) {
            snap.restore(Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    /**
     * Parte "de cima" de blocos duplos (metade de cima de porta/planta alta, cabeceira da cama): nunca é copiada nem
     * paga à parte — {@link #placePartner} a põe junto com a parte principal (senão a colagem gastaria 2 portas).
     */
    public static boolean isSecondaryPart(BlockState s) {
        return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    /** Depois de pôr a parte principal de um bloco duplo, completa a outra metade (se o lugar estiver livre). */
    public static void placePartner(ServerLevel level, @Nullable Player player, BlockPos pos, BlockState placed) {
        BlockPos other;
        BlockState partner;
        if (placed.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && placed.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            other = pos.above();
            partner = placed.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        } else if (placed.hasProperty(BlockStateProperties.BED_PART) && placed.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && placed.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            other = pos.relative(placed.getValue(BlockStateProperties.HORIZONTAL_FACING));
            partner = placed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        } else {
            return;
        }
        if (partner.hasProperty(BlockStateProperties.WATERLOGGED)) partner = partner.setValue(BlockStateProperties.WATERLOGGED, false);
        if (!replaceable(level, other) || !canEdit(level, player, other)) return;
        level.setBlock(other, partner, Block.UPDATE_ALL);
    }

    /** Toca o som de colocar do bloco (com variação de tom). */
    public static void placeSound(ServerLevel level, BlockPos pos, BlockState state, float volume) {
        SoundType st = state.getSoundType(level, pos, null);
        level.playSound(null, pos, st.getPlaceSound(), SoundSource.BLOCKS, (st.getVolume() + 1F) / 2F * volume,
                st.getPitch() * (0.85F + level.random.nextFloat() * 0.3F));
    }

    /** Este bloco pode ser tirado pela construção mágica (sem inventário, não inquebrável, não duro demais)? */
    public static boolean removable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > MAX_HARDNESS) return false;
        return level.getBlockEntity(pos) == null;
    }

    /**
     * Tira o bloco (troca/limpeza do terreno). {@code asItem}: devolve o próprio bloco como item (troca bloco a bloco);
     * senão, os drops normais com uma picareta de ferro virtual. Os itens vão para {@code drops}.
     */
    public static boolean remove(ServerLevel level, @Nullable Player player, BlockPos pos, boolean asItem, Consumer<ItemStack> drops) {
        return remove(level, player, pos, asItem, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), drops);
    }

    /** Igual, com a ferramenta virtual dada (Fortuna...). */
    public static boolean remove(ServerLevel level, @Nullable Player player, BlockPos pos, boolean asItem, ItemStack tool,
                                 Consumer<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (isSecondaryPart(state)) {
            // Metade de cima/cabeceira: tira pela parte principal (é ela que dá o item); a outra some junto
            BlockPos main = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ? pos.below()
                    : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()) : null;
            if (main != null && level.getBlockState(main).is(state.getBlock())) return remove(level, player, main, asItem, tool, drops);
        }
        if (!canEdit(level, player, pos) || !removable(level, pos, state)) return false;
        if (player != null && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) return false;
        if (state.getFluidState().isSource() && state.getBlock() == Blocks.WATER) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        if (asItem) {
            ItemStack item = new ItemStack(state.getBlock().asItem());
            if (!item.isEmpty()) drops.accept(item);
        } else {
            BlockEntity be = level.getBlockEntity(pos);
            List<ItemStack> list = Block.getDrops(state, level, pos, be, player, tool);
            list.forEach(drops);
        }
        // Metade de cima de portas/plantas altas sai junto pelo próprio bloco
        level.levelEvent(2001, pos, Block.getId(state));
        BlockState air = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        level.setBlock(pos, air, Block.UPDATE_ALL);
        return true;
    }

    private Placer() {}
}
