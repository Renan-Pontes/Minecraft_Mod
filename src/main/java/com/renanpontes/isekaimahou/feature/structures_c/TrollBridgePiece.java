package com.renanpontes.isekaimahou.feature.structures_c;

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
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** A peça única da Ponte do Troll (o .nbt inteiro), com a rotação escolhida por {@link TrollBridgeStructure}. */
public class TrollBridgePiece extends TemplateStructurePiece {
    /** Pedra envelhecida (musgo e rachaduras), igual ao processador das outras estruturas. */
    private static final RuleProcessor DECAY = new RuleProcessor(List.of(
            new ProcessorRule(new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.25F), AlwaysTrueTest.INSTANCE,
                    Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.1F), AlwaysTrueTest.INSTANCE,
                    Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.COBBLESTONE, 0.2F), AlwaysTrueTest.INSTANCE,
                    Blocks.MOSSY_COBBLESTONE.defaultBlockState())));

    public TrollBridgePiece(StructureTemplateManager manager, ResourceLocation template, BlockPos origin, Rotation rotation) {
        super(ScRegistries.TROLL_BRIDGE_PIECE.get(), 0, manager, template, template.toString(), settings(rotation), origin);
    }

    /** Recarregado do chunk salvo (peças ainda não colocadas no mundo). */
    public TrollBridgePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ScRegistries.TROLL_BRIDGE_PIECE.get(), tag, manager, loc -> settings(readRotation(tag)));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        // O ar do .nbt é colocado de propósito (limpa o vão do covil e o barranco sobre o tabuleiro).
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setFinalizeEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(DECAY);
    }

    private static Rotation readRotation(CompoundTag tag) {
        try {
            return Rotation.valueOf(tag.getString("Rot").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rotation.NONE;
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // O molde não usa blocos de dados: baú, troll e decoração já vêm dentro do .nbt.
    }
}
