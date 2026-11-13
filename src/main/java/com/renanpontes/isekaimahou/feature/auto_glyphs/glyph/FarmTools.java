package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.cities.CityConfront;
import com.renanpontes.isekaimahou.feature.cities.CityPlaces;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ferramentas "virtuais" (diamante) e as regras de proteção comuns aos glifos que quebram blocos (Pedreira, Lenhador).
 * Fortuna: Fortuna III. Toque Suave: Toque Suave (ganha da Fortuna).
 */
public final class FarmTools {
    private final ItemStack pick, shovel, axe, hoe, shears;

    public FarmTools(ServerLevel level, SpellStats stats) {
        pick = enchant(level, new ItemStack(Items.DIAMOND_PICKAXE), stats);
        shovel = enchant(level, new ItemStack(Items.DIAMOND_SHOVEL), stats);
        axe = enchant(level, new ItemStack(Items.DIAMOND_AXE), stats);
        hoe = enchant(level, new ItemStack(Items.DIAMOND_HOE), stats);
        shears = new ItemStack(Items.SHEARS);
    }

    private static ItemStack enchant(ServerLevel level, ItemStack tool, SpellStats stats) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (stats.silkTouch) tool.enchant(reg.getOrThrow(Enchantments.SILK_TOUCH), 1);
        else if (stats.fortune) tool.enchant(reg.getOrThrow(Enchantments.FORTUNE), 3);
        return tool;
    }

    public ItemStack axe() {
        return axe;
    }

    /** A ferramenta que colhe este bloco com drops (null = nenhuma serve: o bloco fica, para não perder o item). */
    @Nullable
    public ItemStack forState(BlockState s) {
        if (pick.isCorrectToolForDrops(s)) return pick;
        if (shovel.isCorrectToolForDrops(s)) return shovel;
        if (axe.isCorrectToolForDrops(s)) return axe;
        if (hoe.isCorrectToolForDrops(s)) return hoe;
        if (!s.requiresCorrectToolForDrops()) return pick;
        if (shears.isCorrectToolForDrops(s)) return shears;
        return null;
    }

    /** Há uma cidade da Guilda aqui? (os prédios dela nunca são quebrados por fazendas) */
    public static boolean nearCity(ServerLevel level, BlockPos center) {
        try {
            return CityPlaces.cityAt(level, center) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Regras de quebra que NUNCA mudam: rocha-mãe/inquebráveis, imunes ao Wither/Dragão, blocos com inventário/dados
     * (baús, pedestais, fornalhas...), áreas protegidas por módulos (arena de chefe...), proteção do spawn, chunk
     * descarregado e prédios das cidades da Guilda. A permissão do jogador/mods de proteção ({@code AutoArea.mayBreak})
     * é conferida à parte, logo antes de quebrar.
     */
    public static boolean untouchable(ServerLevel level, BlockPos p, BlockState s, @Nullable Entity caster, boolean city) {
        if (s.hasBlockEntity()) return true;
        if (s.getDestroySpeed(level, p) < 0) return true;
        if (s.is(BlockTags.WITHER_IMMUNE) || s.is(BlockTags.DRAGON_IMMUNE)) return true;
        if (Destruction.protectedAt(level, p, caster)) return true;
        if (city) {
            try {
                if (CityConfront.isBuilding(level, p)) return true;
            } catch (RuntimeException e) {
                return true;
            }
        }
        return false;
    }
}
