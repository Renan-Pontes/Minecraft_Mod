package com.renanpontes.isekaimahou.feature.earth_wind;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.earth_wind.block.EarthenQuicksandBlock;
import com.renanpontes.isekaimahou.feature.earth_wind.effect.EwMobEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.effect.GlideEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.effect.SilentStepsEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.effect.SwiftWindEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.effect.WeightedEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.StalagmiteSpike;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.TempestStorm;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.WindTornado;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do modulo earth_wind (blocos temporarios, entidades, ovos das invocacoes e efeitos de status). */
public final class EwRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // ---------------- Blocos temporarios (sem item e sem drop: somem/voltam ao normal sozinhos) ----------------
    /** Pedra conjurada: pilares, muralhas, domo da Fortaleza. Brilha de leve pelas runas. */
    public static final DeferredBlock<Block> RAISED_STONE = BLOCKS.register("raised_earth_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(s -> 4)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));
    /** Areia movedica conjurada: quem pisa afunda devagar. */
    public static final DeferredBlock<EarthenQuicksandBlock> EARTHEN_QUICKSAND = BLOCKS.register("earthen_quicksand",
            () -> new EarthenQuicksandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(0.6F)
                    .sound(SoundType.SAND)
                    .noLootTable()
                    .dynamicShape()
                    .pushReaction(PushReaction.BLOCK)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> true)));

    // ---------------- Entidades ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<SummonedStoneGolem>> STONE_GOLEM = ENTITIES.register("summoned_stone_golem",
            () -> EntityType.Builder.<SummonedStoneGolem>of(SummonedStoneGolem::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F).clientTrackingRange(10).build("summoned_stone_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<SummonedSylph>> SYLPH = ENTITIES.register("summoned_sylph",
            () -> EntityType.Builder.<SummonedSylph>of(SummonedSylph::new, MobCategory.MISC)
                    .sized(0.6F, 1.4F).clientTrackingRange(10).build("summoned_sylph"));
    public static final DeferredHolder<EntityType<?>, EntityType<WindTornado>> WIND_TORNADO = ENTITIES.register("wind_tornado",
            () -> EntityType.Builder.<WindTornado>of(WindTornado::new, MobCategory.MISC)
                    .sized(2.0F, 4.5F).clientTrackingRange(10).updateInterval(2).fireImmune().build("wind_tornado"));
    public static final DeferredHolder<EntityType<?>, EntityType<TempestStorm>> TEMPEST_STORM = ENTITIES.register("tempest_storm",
            () -> EntityType.Builder.<TempestStorm>of(TempestStorm::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(10).fireImmune().build("tempest_storm"));
    public static final DeferredHolder<EntityType<?>, EntityType<StalagmiteSpike>> STALAGMITE_SPIKE = ENTITIES.register("stalagmite_spike",
            () -> EntityType.Builder.<StalagmiteSpike>of(StalagmiteSpike::new, MobCategory.MISC)
                    .sized(1.0F, 2.5F).clientTrackingRange(8).updateInterval(20).fireImmune().build("stalagmite_spike"));

    // ---------------- Ovos (criativo/testes: a invocacao sai sem dono e dura o tempo padrao) ----------------
    public static final DeferredItem<DeferredSpawnEggItem> STONE_GOLEM_EGG = ITEMS.register("summoned_stone_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(STONE_GOLEM, 0x7A7268, 0xE8A040, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SYLPH_EGG = ITEMS.register("summoned_sylph_spawn_egg",
            () -> new DeferredSpawnEggItem(SYLPH, 0xBFF5E6, 0x3FA88A, new Item.Properties()));

    // ---------------- Efeitos de status ----------------
    /** Pele de Pedra: armadura, robustez e resistencia a empurrao (um pouco mais lento). */
    public static final DeferredHolder<MobEffect, MobEffect> STONESKIN = EFFECTS.register("stoneskin",
            () -> new EwMobEffect(MobEffectCategory.BENEFICIAL, 0x8C7A62)
                    .addAttributeModifier(Attributes.ARMOR, mod("stoneskin"), 4.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, mod("stoneskin"), 2.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, mod("stoneskin"), 0.35, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, mod("stoneskin"), 0.35, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, mod("stoneskin"), -0.05, Operation.ADD_MULTIPLIED_TOTAL));
    /** Peso: lento, sem pulo, cai mais rapido e sofre mais dano de queda. Voadores sao puxados para o chao. */
    public static final DeferredHolder<MobEffect, MobEffect> WEIGHTED = EFFECTS.register("weighted",
            () -> new WeightedEffect(MobEffectCategory.HARMFUL, 0x4E4034)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, mod("weighted"), -0.25, Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, mod("weighted"), -1.0, Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.GRAVITY, mod("weighted"), 0.6, Operation.ADD_MULTIPLIED_BASE)
                    .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, mod("weighted"), -1.5, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, mod("weighted"), 0.75, Operation.ADD_VALUE));
    /** Pulo Duplo: amplificador + 1 pulos extras no ar (detectado no cliente). */
    public static final DeferredHolder<MobEffect, MobEffect> DOUBLE_JUMP = EFFECTS.register("double_jump",
            () -> new EwMobEffect(MobEffectCategory.BENEFICIAL, 0xBFF5E6));
    /** Planar: queda lenta controlada pela direcao do olhar (agachar cancela). */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_GLIDE = EFFECTS.register("wind_glide",
            () -> new GlideEffect(MobEffectCategory.BENEFICIAL, 0xDDF7FF));
    /** Velocidade do Vento: corre mais e sobe degraus. */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_SWIFTNESS = EFFECTS.register("wind_swiftness",
            () -> new SwiftWindEffect(MobEffectCategory.BENEFICIAL, 0x9FF0D0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, mod("wind_swiftness"), 0.3, Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.STEP_HEIGHT, mod("wind_swiftness"), 0.5, Operation.ADD_VALUE));
    /** Barreira de Vento: rebate projeteis. */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_BARRIER = EFFECTS.register("wind_barrier",
            () -> new EwMobEffect(MobEffectCategory.BENEFICIAL, 0xCFFFF0, ParticleTypes.SMALL_GUST));
    /** Passos Silenciosos: mobs longe nao te notam e vibracoes nao sao ouvidas. */
    public static final DeferredHolder<MobEffect, MobEffect> SILENT_STEPS = EFFECTS.register("silent_steps",
            () -> new SilentStepsEffect(MobEffectCategory.BENEFICIAL, 0x5E7A70));

    private static ResourceLocation mod(String name) {
        return IsekaiMahou.id("effect." + name);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
    }

    private EwRegistries() {}
}
