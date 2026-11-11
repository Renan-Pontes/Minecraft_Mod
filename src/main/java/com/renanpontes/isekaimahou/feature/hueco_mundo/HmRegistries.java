package com.renanpontes.isekaimahou.feature.hueco_mundo;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.block.GateAltarBlock;
import com.renanpontes.isekaimahou.feature.hueco_mundo.block.GlowCrystalBlock;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.HollowKing;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.SandEmpress;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.ThornQueen;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.Arrancar;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.BrambleHound;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.FeyPixie;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmBolt;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HollowBrute;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HollowCrawler;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HollowWraith;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.MenosGiant;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.RealmGate;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.DecayAxeItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.GargantaKeyItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.QueenWingsItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.ReishiCrystalItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.SoulShardItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.item.TideBladeItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.world.BoneRemainsFeature;
import com.renanpontes.isekaimahou.feature.hueco_mundo.world.FeyMushroomFeature;
import com.renanpontes.isekaimahou.feature.hueco_mundo.world.HollowRuinFeature;
import com.renanpontes.isekaimahou.feature.hueco_mundo.world.QuartzTreeFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo: blocos dos dois reinos, criaturas, chefes, portais, projéteis, itens, ovos e features de mundo. */
public final class HmRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ Blocos do Mundo Oco
    public static final DeferredBlock<ColoredFallingBlock> HOLLOW_SAND = BLOCKS.register("hollow_sand",
            () -> new ColoredFallingBlock(new ColorRGBA(0xFFE9ECF2), BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND)));
    public static final DeferredBlock<Block> HOLLOW_SANDSTONE = BLOCKS.registerSimpleBlock("hollow_sandstone",
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(0.8F));
    public static final DeferredBlock<Block> HOLLOW_SANDSTONE_BRICKS = BLOCKS.registerSimpleBlock("hollow_sandstone_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final DeferredBlock<GlowCrystalBlock> HOLLOW_CRYSTAL = BLOCKS.register("hollow_crystal",
            () -> new GlowCrystalBlock(0x9FD8FF, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(1.2F)
                    .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 10).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<GateAltarBlock> GARGANTA_ALTAR = BLOCKS.register("garganta_altar",
            () -> new GateAltarBlock(HmRealm.HOLLOW, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F).lightLevel(s -> 6).sound(SoundType.BONE_BLOCK).noLootTable()));

    // ------------------------------------------------------------------ Blocos do Reino das Fadas
    public static final DeferredBlock<Block> FEY_GLOWCAP = BLOCKS.registerSimpleBlock("fey_glowcap",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.3F).sound(SoundType.WOOD)
                    .lightLevel(s -> 13));
    public static final DeferredBlock<GateAltarBlock> FEY_RING_STONE = BLOCKS.register("fey_ring_stone",
            () -> new GateAltarBlock(HmRealm.FEY, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(-1.0F, 3600000.0F).lightLevel(s -> 8).sound(SoundType.STONE).noLootTable()));
    public static final DeferredBlock<FlowerBlock> MOONPETAL = BLOCKS.register("moonpetal",
            () -> new FlowerBlock(MobEffects.NIGHT_VISION, 8.0F, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission().instabreak().sound(SoundType.GRASS).lightLevel(s -> 7)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)));

    // ------------------------------------------------------------------ Itens dos blocos
    public static final DeferredItem<BlockItem> HOLLOW_SAND_ITEM = ITEMS.registerSimpleBlockItem("hollow_sand", HOLLOW_SAND, new Item.Properties());
    public static final DeferredItem<BlockItem> HOLLOW_SANDSTONE_ITEM = ITEMS.registerSimpleBlockItem("hollow_sandstone", HOLLOW_SANDSTONE, new Item.Properties());
    public static final DeferredItem<BlockItem> HOLLOW_SANDSTONE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("hollow_sandstone_bricks",
            HOLLOW_SANDSTONE_BRICKS, new Item.Properties());
    public static final DeferredItem<BlockItem> HOLLOW_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem("hollow_crystal", HOLLOW_CRYSTAL, new Item.Properties());
    public static final DeferredItem<BlockItem> GARGANTA_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("garganta_altar", GARGANTA_ALTAR,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> FEY_GLOWCAP_ITEM = ITEMS.registerSimpleBlockItem("fey_glowcap", FEY_GLOWCAP, new Item.Properties());
    public static final DeferredItem<BlockItem> FEY_RING_STONE_ITEM = ITEMS.registerSimpleBlockItem("fey_ring_stone", FEY_RING_STONE,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> MOONPETAL_ITEM = ITEMS.registerSimpleBlockItem("moonpetal", MOONPETAL, new Item.Properties());

    // ------------------------------------------------------------------ Criaturas
    public static final Supplier<EntityType<HollowCrawler>> HOLLOW_CRAWLER = mob("hollow_crawler", HollowCrawler::new, 1.3F, 0.9F, 0.6F);
    public static final Supplier<EntityType<HollowBrute>> HOLLOW_BRUTE = mob("hollow_brute", HollowBrute::new, 1.5F, 2.7F, 2.3F);
    public static final Supplier<EntityType<HollowWraith>> HOLLOW_WRAITH = mob("hollow_wraith", HollowWraith::new, 1.4F, 0.9F, 0.5F);
    public static final Supplier<EntityType<MenosGiant>> MENOS_GIANT = mob("menos_giant", MenosGiant::new, 2.4F, 8.4F, 7.6F);
    public static final Supplier<EntityType<Arrancar>> ARRANCAR = mob("arrancar", Arrancar::new, 0.6F, 1.95F, 1.7F);
    public static final Supplier<EntityType<FeyPixie>> FEY_PIXIE = mob("fey_pixie", FeyPixie::new, 0.5F, 0.7F, 0.45F);
    public static final Supplier<EntityType<BrambleHound>> BRAMBLE_HOUND = mob("bramble_hound", BrambleHound::new, 0.9F, 0.95F, 0.75F);

    // ------------------------------------------------------------------ Chefes
    public static final Supplier<EntityType<HollowKing>> HOLLOW_KING = boss("hollow_king", HollowKing::new, 1.3F, 3.1F, 2.7F);
    public static final Supplier<EntityType<SandEmpress>> SAND_EMPRESS = boss("sand_empress", SandEmpress::new, 0.9F, 2.5F, 2.2F);
    public static final Supplier<EntityType<ThornQueen>> THORN_QUEEN = boss("thorn_queen", ThornQueen::new, 1.1F, 3.0F, 2.6F);

    // ------------------------------------------------------------------ Portais e projéteis
    public static final Supplier<EntityType<RealmGate>> REALM_GATE = ENTITIES.register("realm_gate",
            () -> EntityType.Builder.<RealmGate>of(RealmGate::new, MobCategory.MISC).sized(2.0F, 4.0F)
                    .clientTrackingRange(10).updateInterval(20).fireImmune().noSummon().build("realm_gate"));
    public static final Supplier<EntityType<HmBolt>> HM_BOLT = ENTITIES.register("hm_bolt",
            () -> EntityType.Builder.<HmBolt>of(HmBolt::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(6).updateInterval(2).fireImmune().build("hm_bolt"));

    // ------------------------------------------------------------------ Itens
    public static final DeferredItem<Item> HOLLOW_MASK_FRAGMENT = ITEMS.registerSimpleItem("hollow_mask_fragment", new Item.Properties());
    public static final DeferredItem<Item> MENOS_CORE = ITEMS.registerSimpleItem("menos_core", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<SoulShardItem> ARRANCAR_SOUL_SHARD = ITEMS.register("arrancar_soul_shard",
            () -> new SoulShardItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> FEY_DUST = ITEMS.registerSimpleItem("fey_dust", new Item.Properties());
    public static final DeferredItem<Item> BRAMBLE_HEART = ITEMS.registerSimpleItem("bramble_heart", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ReishiCrystalItem> REISHI_CRYSTAL = ITEMS.register("reishi_crystal",
            () -> new ReishiCrystalItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<GargantaKeyItem> GARGANTA_KEY = ITEMS.register("garganta_key",
            () -> new GargantaKeyItem(new Item.Properties().stacksTo(8).rarity(Rarity.RARE)));
    public static final DeferredItem<DecayAxeItem> DECAY_AXE = ITEMS.register("decay_axe",
            () -> new DecayAxeItem(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                    .attributes(DecayAxeItem.createAttributes(Tiers.NETHERITE, 7.0F, -3.0F))));
    public static final DeferredItem<TideBladeItem> TIDE_SAND_BLADE = ITEMS.register("tide_sand_blade",
            () -> new TideBladeItem(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                    .attributes(TideBladeItem.createAttributes(Tiers.NETHERITE, 4, -2.4F))));
    public static final DeferredItem<QueenWingsItem> QUEEN_WINGS = ITEMS.register("queen_wings",
            () -> new QueenWingsItem(new Item.Properties().durability(64).rarity(Rarity.EPIC)));

    // ------------------------------------------------------------------ Ovos
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_CRAWLER_EGG = egg("hollow_crawler", HOLLOW_CRAWLER, 0xE6E2D8, 0x1A1A22);
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_BRUTE_EGG = egg("hollow_brute", HOLLOW_BRUTE, 0x2A2730, 0xEDE8DC);
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_WRAITH_EGG = egg("hollow_wraith", HOLLOW_WRAITH, 0x1C1A26, 0xD8D4C8);
    public static final DeferredItem<DeferredSpawnEggItem> MENOS_GIANT_EGG = egg("menos_giant", MENOS_GIANT, 0x0E0E12, 0xF2F0EA);
    public static final DeferredItem<DeferredSpawnEggItem> ARRANCAR_EGG = egg("arrancar", ARRANCAR, 0xF4F4F0, 0x2B2B33);
    public static final DeferredItem<DeferredSpawnEggItem> FEY_PIXIE_EGG = egg("fey_pixie", FEY_PIXIE, 0xFFB8E8, 0x8FFFE0);
    public static final DeferredItem<DeferredSpawnEggItem> BRAMBLE_HOUND_EGG = egg("bramble_hound", BRAMBLE_HOUND, 0x3E5A2A, 0xC23A5A);
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_KING_EGG = egg("hollow_king", HOLLOW_KING, 0x15131A, 0xD9B44A);
    public static final DeferredItem<DeferredSpawnEggItem> SAND_EMPRESS_EGG = egg("sand_empress", SAND_EMPRESS, 0xF1E6CF, 0x3FA7C9);
    public static final DeferredItem<DeferredSpawnEggItem> THORN_QUEEN_EGG = egg("thorn_queen", THORN_QUEEN, 0x2F4A2A, 0xE85A9A);

    // ------------------------------------------------------------------ Features (geração do mundo)
    public static final Supplier<Feature<NoneFeatureConfiguration>> QUARTZ_TREE = FEATURES.register("hm_quartz_tree",
            () -> new QuartzTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<Feature<NoneFeatureConfiguration>> HOLLOW_RUIN = FEATURES.register("hm_hollow_ruin",
            () -> new HollowRuinFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<Feature<NoneFeatureConfiguration>> BONE_REMAINS = FEATURES.register("hm_bone_remains",
            () -> new BoneRemainsFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<Feature<NoneFeatureConfiguration>> FEY_MUSHROOM = FEATURES.register("hm_fey_mushroom",
            () -> new FeyMushroomFeature(NoneFeatureConfiguration.CODEC));

    // ------------------------------------------------------------------ Dados do jogador
    /** Ponto de volta por reino ({realm: {dim, x, y, z, yaw}}) e recargas dos atalhos. */
    public static final Supplier<AttachmentType<CompoundTag>> RETURN_POINTS = ATTACHMENTS.register("hueco_mundo_return",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    private static <T extends Mob> Supplier<EntityType<T>> mob(String id, EntityType.EntityFactory<T> factory, float w, float h, float eye) {
        return ENTITIES.register(id, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(w, h).eyeHeight(eye)
                .clientTrackingRange(10).build(id));
    }

    private static <T extends Mob> Supplier<EntityType<T>> boss(String id, EntityType.EntityFactory<T> factory, float w, float h, float eye) {
        return ENTITIES.register(id, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(w, h).eyeHeight(eye)
                .fireImmune().clientTrackingRange(12).build(id));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties()));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(HOLLOW_CRAWLER_EGG, HOLLOW_BRUTE_EGG, HOLLOW_WRAITH_EGG, MENOS_GIANT_EGG, ARRANCAR_EGG, FEY_PIXIE_EGG,
                BRAMBLE_HOUND_EGG, HOLLOW_KING_EGG, SAND_EMPRESS_EGG, THORN_QUEEN_EGG);
    }

    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(HOLLOW_SAND_ITEM, HOLLOW_SANDSTONE_ITEM, HOLLOW_SANDSTONE_BRICKS_ITEM, HOLLOW_CRYSTAL_ITEM, GARGANTA_ALTAR_ITEM,
                FEY_GLOWCAP_ITEM, MOONPETAL_ITEM, FEY_RING_STONE_ITEM);
    }

    public static List<DeferredItem<? extends Item>> magicItems() {
        return List.of(HOLLOW_MASK_FRAGMENT, MENOS_CORE, ARRANCAR_SOUL_SHARD, REISHI_CRYSTAL, GARGANTA_KEY, DECAY_AXE, TIDE_SAND_BLADE,
                FEY_DUST, BRAMBLE_HEART, QUEEN_WINGS);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        FEATURES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(HOLLOW_CRAWLER.get(), HollowCrawler.createAttributes().build());
            e.put(HOLLOW_BRUTE.get(), HollowBrute.createAttributes().build());
            e.put(HOLLOW_WRAITH.get(), HollowWraith.createAttributes().build());
            e.put(MENOS_GIANT.get(), MenosGiant.createAttributes().build());
            e.put(ARRANCAR.get(), Arrancar.createAttributes().build());
            e.put(FEY_PIXIE.get(), FeyPixie.createAttributes().build());
            e.put(BRAMBLE_HOUND.get(), BrambleHound.createAttributes().build());
            e.put(HOLLOW_KING.get(), HollowKing.createAttributes().build());
            e.put(SAND_EMPRESS.get(), SandEmpress.createAttributes().build());
            e.put(THORN_QUEEN.get(), ThornQueen.createAttributes().build());
        });
    }

    private HmRegistries() {}
}
