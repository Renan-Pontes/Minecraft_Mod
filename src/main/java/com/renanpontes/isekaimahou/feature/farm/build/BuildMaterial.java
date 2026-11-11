package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.wand.WandData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.items.IItemHandler;

/** Qual bloco a construção mágica usa: mão secundária → material da varinha → primeiro bloco da barra (ou do baú). */
public final class BuildMaterial {

    /** Blocos que não servem de material em massa (duplos, líquidos, ar). */
    public static boolean valid(Block b) {
        if (b == Blocks.AIR || b instanceof LiquidBlock) return false;
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || s.hasProperty(BlockStateProperties.BED_PART)) return false;
        return b.asItem() != net.minecraft.world.item.Items.AIR;
    }

    @Nullable
    public static Block blockOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && valid(bi.getBlock())) return bi.getBlock();
        return null;
    }

    /** Estado padrão virado para o jogador (escadas, fornalhas...) e laje de baixo. */
    public static BlockState orient(Block b, Direction facing) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite());
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) s = s.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        if (s.hasProperty(BlockStateProperties.WATERLOGGED)) s = s.setValue(BlockStateProperties.WATERLOGGED, false);
        return s;
    }

    /** Material do jogador (a varinha guarda um; a mão secundária vence). */
    @Nullable
    public static Block forPlayer(ServerPlayer player, @Nullable ResourceLocation stored) {
        Block off = blockOf(player.getOffhandItem());
        if (off != null) return off;
        if (stored != null) {
            Block b = BuiltInRegistries.BLOCK.get(stored);
            if (valid(b)) return b;
        }
        return null;
    }

    /** Para glifos: mão secundária → material de uma varinha → primeiro bloco da barra (fora a mão principal). */
    @Nullable
    public static Block forCaster(ServerPlayer player) {
        Block b = forPlayer(player, storedMaterial(player));
        if (b != null) return b;
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (i == inv.selected) continue;
            Block hb = blockOf(inv.getItem(i));
            if (hb != null) return hb;
        }
        return null;
    }

    @Nullable
    public static ResourceLocation storedMaterial(ServerPlayer player) {
        WandData main = player.getMainHandItem().get(FarmRegistries.WAND.get());
        if (main != null && main.material().isPresent()) return main.material().get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            WandData d = inv.getItem(i).get(FarmRegistries.WAND.get());
            if (d != null && d.material().isPresent()) return d.material().get();
        }
        return null;
    }

    /** Pedestais rúnicos (conjurador sem inventário): primeiro bloco nos baús em volta. */
    @Nullable
    public static Block fromContainers(ServerLevel level, List<BlockPos> containers) {
        for (BlockPos p : containers) {
            IItemHandler h = Supply.handler(level, p);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots(); i++) {
                Block b = blockOf(h.getStackInSlot(i));
                if (b != null) return b;
            }
        }
        return null;
    }

    private BuildMaterial() {}
}
