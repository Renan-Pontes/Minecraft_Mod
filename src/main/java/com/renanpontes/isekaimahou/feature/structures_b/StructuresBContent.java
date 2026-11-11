package com.renanpontes.isekaimahou.feature.structures_b;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.structures_b.block.CryptSarcophagusBlock;
import com.renanpontes.isekaimahou.feature.structures_b.block.FairyGlowbloomBlock;
import com.renanpontes.isekaimahou.feature.structures_b.loot.ItemIfPresentFunction;
import com.renanpontes.isekaimahou.feature.structures_b.loot.RuinGlyphScrollFunction;
import com.renanpontes.isekaimahou.feature.structures_b.world.ExploredStructures;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo de estruturas B: blocos decorativos das estruturas, partícula de vaga-lume, funções de loot e dados de exploração. */
public final class StructuresBContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    // ---------------- Blocos ----------------
    /** Flor-Vaga-lume: brilha e solta vaga-lumes à noite (Lago das Fadas). */
    public static final DeferredBlock<FairyGlowbloomBlock> FAIRY_GLOWBLOOM = BLOCKS.registerBlock("fairy_glowbloom",
            FairyGlowbloomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
                    .sound(SoundType.AZALEA).lightLevel(s -> 9).offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.DESTROY));

    /** Sarcófago Antigo (Cripta Esquecida): duas partes, tampa que abre; lacrado = guarda tesouro e às vezes um morto. */
    public static final DeferredBlock<CryptSarcophagusBlock> CRYPT_SARCOPHAGUS = BLOCKS.registerBlock("crypt_sarcophagus",
            CryptSarcophagusBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion().pushReaction(PushReaction.BLOCK));

    // ---------------- Itens ----------------
    public static final DeferredItem<BlockItem> FAIRY_GLOWBLOOM_ITEM = ITEMS.registerSimpleBlockItem("fairy_glowbloom",
            FAIRY_GLOWBLOOM, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> CRYPT_SARCOPHAGUS_ITEM = ITEMS.registerSimpleBlockItem("crypt_sarcophagus",
            CRYPT_SARCOPHAGUS, new Item.Properties().stacksTo(16));

    // ---------------- Partículas ----------------
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FAIRY_FIREFLY = PARTICLES.register("fairy_firefly",
            () -> new SimpleParticleType(false));

    // ---------------- Funções de loot ----------------
    /** Transforma um Pergaminho de Glifo em um glifo aleatório (filtros por lista, elemento e tier). */
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RuinGlyphScrollFunction>> RUIN_GLYPH_SCROLL =
            LOOT_FUNCTIONS.register("ruin_glyph_scroll", () -> new LootItemFunctionType<>(RuinGlyphScrollFunction.CODEC));
    /** Troca o item por outro (de outro módulo) se ele existir; senão mantém o item base. */
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<ItemIfPresentFunction>> ITEM_IF_PRESENT =
            LOOT_FUNCTIONS.register("item_if_present", () -> new LootItemFunctionType<>(ItemIfPresentFunction.CODEC));

    // ---------------- Dados do jogador ----------------
    /** Quais estruturas deste módulo o jogador já visitou (para mensagens de descoberta e contadores). */
    public static final Supplier<AttachmentType<ExploredStructures>> EXPLORED = ATTACHMENTS.register("structures_b_explored",
            () -> AttachmentType.builder(() -> new ExploredStructures()).serialize(ExploredStructures.CODEC).copyOnDeath().build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        PARTICLES.register(modBus);
        LOOT_FUNCTIONS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private StructuresBContent() {}
}
