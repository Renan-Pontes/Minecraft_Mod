package com.renanpontes.isekaimahou.feature.auto_glyphs;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.automation.glyph.AutoArea;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Estimativa BARATA da área de uma magia de fazenda, para os gatilhos de pedestal: o centro é o bloco de onde a magia
 * nasce menos um (o próprio pedestal, ou o chão sob o jogador) e o raio é o maior raio dos efeitos da receita
 * (com Expandir/Concentrar). Também sabe dizer se há plantas maduras, terra vazia, itens e "trabalho".
 * Só lê chunks carregados; para na primeira coisa achada.
 */
public final class AreaProbe {
    /** Camadas olhadas abaixo/acima do centro. */
    public static final int DOWN = 2, UP = 2;

    public static BlockPos center(Vec3 origin) {
        return BlockPos.containing(origin).below();
    }

    /** Raio base de cada efeito de fazenda conhecido (o mesmo que o efeito usa). */
    public static float baseRadius(EffectGlyph e) {
        String path = e.id().getPath();
        switch (path) {
            case "till": return 3F;
            case "harvest_replant", "sow", "smelt_items": return 4F;
            case "shear", "breed": return 5F;
            case "collect_items": return 6F;
            default:
                float h = e.hintRadius();
                return h > 0 ? h : 4F;
        }
    }

    /** Maior raio (já com aumentos) entre os efeitos do primeiro estágio. */
    public static int radius(ParsedSpell spell) {
        int r = 0;
        for (ParsedSpell.EffectEntry en : spell.effects()) r = Math.max(r, AutoArea.radius(en.stats(), baseRadius(en.effect())));
        return r > 0 ? r : 4;
    }

    // =====================================================================================
    //  Lavoura
    // =====================================================================================

    /** Pronta para a Colheita Fértil (mesmas regras dela). */
    public static boolean ripe(ServerLevel level, BlockPos p, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(s);
        if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (b instanceof CocoaBlock) return s.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        if (b instanceof SweetBerryBushBlock) return s.getValue(SweetBerryBushBlock.AGE) >= 2;
        if (s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(p.relative(d)).getBlock() instanceof AttachedStemBlock) return true;
            }
            return false;
        }
        if (s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO)) {
            return level.getBlockState(p.below()).is(b) && !level.getBlockState(p.below(2)).is(b);
        }
        return false;
    }

    public static boolean anyRipe(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : AutoArea.disk(center, r, DOWN, UP)) {
            if (level.isLoaded(p) && ripe(level, p, level.getBlockState(p))) return true;
        }
        return false;
    }

    /** Terra arada (ou areia das almas) com o espaço de cima vazio. */
    public static boolean anyEmptySoil(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : AutoArea.disk(center, r, DOWN + 1, UP)) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if ((s.is(Blocks.FARMLAND) || s.is(Blocks.SOUL_SAND)) && level.isEmptyBlock(p.above())) return true;
        }
        return false;
    }

    /** Algo para Arar: grama/terra com ar em cima, ou terra arada seca. */
    public static boolean anyTillable(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : AutoArea.disk(center, r, DOWN, 1)) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.FARMLAND)) {
                if (s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) return true;
            } else if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)) && level.getBlockState(p.above()).canBeReplaced()
                    && !level.getBlockState(p.above()).is(BlockTags.CROPS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyItems(ServerLevel level, BlockPos center, int r) {
        return !level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r, r, r), e -> e.isAlive() && !e.getItem().isEmpty())
                .isEmpty();
    }

    // =====================================================================================
    //  "Há trabalho?"
    // =====================================================================================

    /** Algum efeito da receita tem trabalho? Efeitos desconhecidos contam como "sim". */
    public static boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, ParsedSpell spell) {
        if (spell.effects().isEmpty()) return true;
        for (ParsedSpell.EffectEntry en : spell.effects()) {
            if (effectHasWork(level, center, pedestal, en.effect(), en.stats())) return true;
        }
        return false;
    }

    private static boolean effectHasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, EffectGlyph e, SpellStats stats) {
        if (e instanceof WorkProbe probe) return probe.hasWork(level, center, pedestal, stats);
        int r = AutoArea.radius(stats, baseRadius(e));
        return switch (e.id().getPath()) {
            case "harvest_replant" -> anyRipe(level, center, r);
            case "sow" -> anyEmptySoil(level, center, r);
            case "till" -> anyTillable(level, center, r);
            case "collect_items", "smelt_items" -> anyItems(level, center, r);
            default -> true;
        };
    }

    private AreaProbe() {}
}
