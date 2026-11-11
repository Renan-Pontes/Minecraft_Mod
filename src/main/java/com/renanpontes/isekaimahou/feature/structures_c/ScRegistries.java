package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo structures_c (as estruturas em si são dados de worldgen). */
public final class ScRegistries {
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    /** "isekai_mahou:rpg_glyph_scroll" — sorteia um glifo existente para o Pergaminho de Glifo dos baús. */
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RpgGlyphScrollFunction>> RPG_GLYPH_SCROLL =
            LOOT_FUNCTIONS.register("rpg_glyph_scroll", () -> new LootItemFunctionType<>(RpgGlyphScrollFunction.CODEC));

    /** Instâncias de estrutura já exploradas pelo jogador ("dimensão|estrutura|chunk do início"). */
    private static final Codec<HashSet<String>> EXPLORED_CODEC = Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new);
    public static final Supplier<AttachmentType<HashSet<String>>> EXPLORED = ATTACHMENTS.register("structures_c_explored",
            () -> AttachmentType.builder(() -> new HashSet<String>()).serialize(EXPLORED_CODEC).copyOnDeath().build());

    /** Tipo "isekai_mahou:troll_bridge": ponte girada para atravessar o rio (o jigsaw giraria ao acaso). */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, IsekaiMahou.MODID);
    public static final DeferredHolder<StructureType<?>, StructureType<TrollBridgeStructure>> TROLL_BRIDGE_TYPE =
            STRUCTURE_TYPES.register("troll_bridge", () -> () -> TrollBridgeStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> TROLL_BRIDGE_PIECE =
            STRUCTURE_PIECES.register("troll_bridge", () -> (StructurePieceType.StructureTemplateType) TrollBridgePiece::new);

    public static void register(IEventBus modBus) {
        LOOT_FUNCTIONS.register(modBus);
        ATTACHMENTS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
    }

    private ScRegistries() {}
}
