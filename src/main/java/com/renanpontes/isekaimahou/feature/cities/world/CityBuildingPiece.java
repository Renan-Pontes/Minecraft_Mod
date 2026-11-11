package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * Um prédio/lote, a praça, o castelo, uma torre ou um portão: um .nbt de structure/city/&lt;paleta&gt;/ girado (e às
 * vezes espelhado) para a frente dar para a rua. O terreno embaixo já foi preparado pela {@link CityGroundPiece}.
 */
public class CityBuildingPiece extends TemplateStructurePiece implements PieceBeardifierModifier {
    /** Pedra envelhecida nas paletas de pedra (musgo e rachaduras), como nas outras estruturas do mod. */
    private static final RuleProcessor AGING = new RuleProcessor(List.of(
            new ProcessorRule(new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.05F), AlwaysTrueTest.INSTANCE,
                    Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.04F), AlwaysTrueTest.INSTANCE,
                    Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.COBBLESTONE, 0.08F), AlwaysTrueTest.INSTANCE,
                    Blocks.MOSSY_COBBLESTONE.defaultBlockState())));

    private final CityPalette palette;

    public CityBuildingPiece(StructureTemplateManager manager, ResourceLocation template, BlockPos origin, Rotation rotation,
                             Mirror mirror, CityPalette palette) {
        super(CityWorldgen.BUILDING_PIECE.get(), 0, manager, template, template.toString(),
                settings(rotation, mirror, palette), origin);
        this.palette = palette;
    }

    /** Recarregada do chunk de início salvo. */
    public CityBuildingPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(CityWorldgen.BUILDING_PIECE.get(), tag, manager,
                loc -> settings(readRotation(tag), readMirror(tag), CityPalette.byId(tag.getString("Palette"))));
        this.palette = CityPalette.byId(tag.getString("Palette"));
    }

    private static StructurePlaceSettings settings(Rotation rotation, Mirror mirror, CityPalette palette) {
        StructurePlaceSettings s = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setFinalizeEntities(true)
                // A água do chafariz/poço não pode "molhar" escadas e lajes vizinhas.
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (palette != CityPalette.DESERT) s.addProcessor(AGING);
        return s;
    }

    private static Rotation readRotation(CompoundTag tag) {
        try {
            return Rotation.valueOf(tag.getString("Rot").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rotation.NONE;
        }
    }

    private static Mirror readMirror(CompoundTag tag) {
        try {
            return Mirror.valueOf(tag.getString("Mirror").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mirror.NONE;
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putString("Mirror", placeSettings.getMirror().name());
        tag.putString("Palette", palette.id);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // Os templates da cidade não usam blocos de dados: NPCs, baús e enfeites já vêm dentro do .nbt.
    }

    // O aplainamento do terreno é feito uma vez só, pela peça do chão; os prédios não pedem outro.
    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }
}
