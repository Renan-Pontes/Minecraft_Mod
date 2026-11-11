package com.renanpontes.isekaimahou.feature.bosses_b;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_b.block.HeraldTrophyBlock;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.AbyssSovereign;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.BossBolt;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.Seraph;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.TwilightHerald;
import com.renanpontes.isekaimahou.feature.bosses_b.item.TwilightHeartItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo bosses_b: os três chefes, os projéteis deles, ovos, o Coração do Crepúsculo e o troféu. */
public final class BbRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);

    // ---------------- Chefes ----------------
    public static final Supplier<EntityType<AbyssSovereign>> ABYSS_SOVEREIGN = ENTITIES.register("abyss_sovereign",
            () -> EntityType.Builder.of(AbyssSovereign::new, MobCategory.MONSTER).sized(1.5F, 3.7F).eyeHeight(3.2F)
                    .fireImmune().clientTrackingRange(12).build("abyss_sovereign"));
    public static final Supplier<EntityType<Seraph>> SERAPH = ENTITIES.register("seraph",
            () -> EntityType.Builder.of(Seraph::new, MobCategory.MONSTER).sized(1.4F, 3.4F).eyeHeight(2.85F)
                    .fireImmune().clientTrackingRange(12).build("seraph"));
    public static final Supplier<EntityType<TwilightHerald>> TWILIGHT_HERALD = ENTITIES.register("twilight_herald",
            () -> EntityType.Builder.of(TwilightHerald::new, MobCategory.MONSTER).sized(1.5F, 3.8F).eyeHeight(3.25F)
                    .fireImmune().clientTrackingRange(14).build("twilight_herald"));

    // ---------------- Projéteis (mesma classe, visual por tipo) ----------------
    public static final Supplier<EntityType<BossBolt>> ABYSSAL_ORB = bolt("abyssal_orb", 0.7F);
    public static final Supplier<EntityType<BossBolt>> CELESTIAL_LANCE = bolt("celestial_lance", 0.6F);
    public static final Supplier<EntityType<BossBolt>> TWILIGHT_ORB = bolt("twilight_orb", 0.8F);

    private static Supplier<EntityType<BossBolt>> bolt(String id, float size) {
        return ENTITIES.register(id, () -> EntityType.Builder.<BossBolt>of(BossBolt::new, MobCategory.MISC)
                .sized(size, size).clientTrackingRange(8).updateInterval(2).fireImmune().build(id));
    }

    // ---------------- Blocos ----------------
    public static final DeferredBlock<HeraldTrophyBlock> HERALD_TROPHY = BLOCKS.register("twilight_herald_trophy",
            () -> new HeraldTrophyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 1200.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 10).noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    // ---------------- Itens ----------------
    public static final DeferredItem<DeferredSpawnEggItem> ABYSS_SOVEREIGN_EGG = ITEMS.register("abyss_sovereign_spawn_egg",
            () -> new DeferredSpawnEggItem(ABYSS_SOVEREIGN, 0x120A1E, 0x9B4DFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SERAPH_EGG = ITEMS.register("seraph_spawn_egg",
            () -> new DeferredSpawnEggItem(SERAPH, 0xF6F1DF, 0xFFC93C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> TWILIGHT_HERALD_EGG = ITEMS.register("twilight_herald_spawn_egg",
            () -> new DeferredSpawnEggItem(TWILIGHT_HERALD, 0x2A1838, 0xF0C060, new Item.Properties()));

    public static final DeferredItem<TwilightHeartItem> TWILIGHT_HEART = ITEMS.register("twilight_heart",
            () -> new TwilightHeartItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<BlockItem> HERALD_TROPHY_ITEM = ITEMS.register("twilight_herald_trophy",
            () -> new BlockItem(HERALD_TROPHY.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private BbRegistries() {}
}
