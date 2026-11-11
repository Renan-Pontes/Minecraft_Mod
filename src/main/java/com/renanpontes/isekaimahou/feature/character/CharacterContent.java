package com.renanpontes.isekaimahou.feature.character;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.character.block.ReincarnationAltarBlock;
import com.renanpontes.isekaimahou.feature.character.item.RaceRelicItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do módulo: dados da raça, o Altar da Reencarnação e os efeitos (MobEffect) das skills raciais.
 * v1.0: os 6 atributos da v0.9 ({@code isekai_mahou:strength}...) SAÍRAM (o jogador desistiu dos status).
 */
public final class CharacterContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    public static final Supplier<AttachmentType<CharacterData>> DATA = ATTACHMENTS.register("character",
            () -> AttachmentType.serializable(CharacterData::new).copyOnDeath().build());

    public static final DeferredBlock<ReincarnationAltarBlock> ALTAR = BLOCKS.registerBlock("reincarnation_altar",
            ReincarnationAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(s -> 11).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem("reincarnation_altar", ALTAR,
            new Item.Properties().rarity(Rarity.EPIC));

    // =====================================================================================
    //  Relíquias Raciais (a única forma de outra raça aprender as skills raciais)
    // =====================================================================================
    private static final Map<Race, DeferredItem<RaceRelicItem>> RELICS = new EnumMap<>(Race.class);

    static {
        for (Race r : Race.ALL) {
            RELICS.put(r, ITEMS.register(RaceRelicItem.itemId(r), () -> new RaceRelicItem(r,
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))));
        }
    }

    /** Relíquia Racial da raça {@code r}. */
    public static DeferredItem<RaceRelicItem> relic(Race r) {
        return RELICS.get(r);
    }

    // =====================================================================================
    //  Efeitos das skills raciais (os modificadores escalam com o amplificador + 1)
    // =====================================================================================
    /** Pele de Rocha (Anão): armadura, firmeza e −15% de dano recebido; um pouco mais lento. */
    public static final DeferredHolder<MobEffect, MobEffect> STONE_SKIN = EFFECTS.register("race_stone_skin",
            () -> new RaceMobEffect(MobEffectCategory.BENEFICIAL, 0x9A8F80)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.race_stone_skin.armor"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.race_stone_skin.toughness"), 2.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.race_stone_skin.steady"), 0.3,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.race_stone_skin.slow"), -0.06,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Fúria Sangrenta (Meio-Orc): golpes mais fortes e rápidos, roubo de vida corpo a corpo. */
    public static final DeferredHolder<MobEffect, MobEffect> BLOOD_FURY = EFFECTS.register("race_blood_fury",
            () -> new RaceMobEffect(MobEffectCategory.BENEFICIAL, 0xB01E28)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.race_blood_fury.damage"), 0.25,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.race_blood_fury.speed"), 0.10,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.race_blood_fury.move"), 0.08,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Escamas Dracônicas (Dragonato): armadura, −25% de dano de magia e espinhos corpo a corpo. */
    public static final DeferredHolder<MobEffect, MobEffect> DRAGON_SCALES = EFFECTS.register("race_dragon_scales",
            () -> new RaceMobEffect(MobEffectCategory.BENEFICIAL, 0xE0A030)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.race_dragon_scales.armor"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.race_dragon_scales.toughness"), 3.0,
                            AttributeModifier.Operation.ADD_VALUE));
    /** Instinto Selvagem (Bestial): velocidade, ataque rápido e esquiva. */
    public static final DeferredHolder<MobEffect, MobEffect> WILD_INSTINCT = EFFECTS.register("race_wild_instinct",
            () -> new RaceMobEffect(MobEffectCategory.BENEFICIAL, 0xD08A3A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.race_wild_instinct.move"), 0.12,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.race_wild_instinct.speed"), 0.12,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /**
     * Asas Sombrias (Demônio): gravidade bem menor (planar), queda segura e velocidade. SEMPRE com amplificador 0 (a
     * gravidade escalada passaria de −100%). As asas aparecem no jogador pelo pacote {@link CharacterNetwork.RaceFx}.
     */
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_WINGS = EFFECTS.register("race_shadow_wings",
            () -> new RaceMobEffect(MobEffectCategory.BENEFICIAL, 0x4A2A5E)
                    .addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("effect.race_shadow_wings.glide"), -0.55,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id("effect.race_shadow_wings.fall"), 12.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.race_shadow_wings.move"), 0.10,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // =====================================================================================
    //  v1.0.1: Transformações raciais (nível de raça V, {@link RaceForms}). Um efeito por raça, SEMPRE amplificador 0;
    //  os atributos somem sozinhos quando o efeito acaba (fim do tempo, leite, morte). O comportamento extra (voo,
    //  auras, golpe da forma) está em RaceForms.
    // =====================================================================================
    private static final Map<Race, DeferredHolder<MobEffect, MobEffect>> FORMS = new EnumMap<>(Race.class);

    static {
        for (Race r : Race.ALL) FORMS.put(r, EFFECTS.register("race_form_" + r.id, () -> formEffect(r)));
    }

    /** Efeito da Transformação da raça {@code r}. */
    public static DeferredHolder<MobEffect, MobEffect> form(Race r) {
        return FORMS.get(r);
    }

    private static MobEffect formEffect(Race r) {
        RaceMobEffect e = new RaceMobEffect(MobEffectCategory.BENEFICIAL, r.rgb);
        String p = "effect.race_form_" + r.id + ".";
        AttributeModifier.Operation total = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        AttributeModifier.Operation base = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        AttributeModifier.Operation add = AttributeModifier.Operation.ADD_VALUE;
        switch (r) {
            case HUMAN -> {
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.25, total);
                e.addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id(p + "power"), 0.25, add);
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.10, total);
                e.addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id(p + "health"), 10.0, add);
            }
            case ELF -> {
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.15, total);
                e.addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id(p + "regen"), 0.5, base);
                e.addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id(p + "power"), 0.15, add);
            }
            case DWARF -> {
                e.addAttributeModifier(Attributes.SCALE, IsekaiMahou.id(p + "scale"), 0.45, base);
                e.addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id(p + "armor"), 10.0, add);
                e.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id(p + "toughness"), 6.0, add);
                e.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(p + "steady"), 1.0, add);
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.30, total);
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "slow"), -0.08, total);
                e.addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, IsekaiMahou.id(p + "mining"), 1.0, base);
            }
            case HALF_ORC -> {
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.50, total);
                e.addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(p + "speed"), 0.25, total);
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.15, total);
                e.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(p + "steady"), 0.5, add);
                e.addAttributeModifier(Attributes.SCALE, IsekaiMahou.id(p + "scale"), 0.12, base);
            }
            case BEASTFOLK -> {
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.35, total);
                e.addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(p + "speed"), 0.30, total);
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.20, total);
                e.addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(p + "jump"), 0.30, base);
                e.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(p + "fall"), 10.0, add);
            }
            case DEMON -> {
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.30, total);
                e.addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id(p + "power"), 0.20, add);
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.10, total);
            }
            case DRAGONBORN -> {
                e.addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id(p + "armor"), 8.0, add);
                e.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id(p + "toughness"), 4.0, add);
                e.addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(p + "damage"), 0.25, total);
                e.addAttributeModifier(Attributes.SCALE, IsekaiMahou.id(p + "scale"), 0.15, base);
            }
            case ARACHNID -> {
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "move"), 0.20, total);
                e.addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(p + "speed"), 0.20, total);
                e.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(p + "fall"), 12.0, add);
            }
            case PHANTOM -> {
                e.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(p + "fall"), 64.0, add);
                e.addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id(p + "power"), 0.15, add);
            }
            case ANGEL -> {
                e.addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id(p + "power"), 0.20, add);
                e.addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id(p + "health"), 8.0, add);
            }
            case SLIME -> {
                e.addAttributeModifier(Attributes.SCALE, IsekaiMahou.id(p + "scale"), 0.6, base);
                e.addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id(p + "health"), 20.0, add);
                e.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(p + "steady"), 1.0, add);
                e.addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(p + "jump"), 0.40, base);
                e.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(p + "fall"), 16.0, add);
                e.addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "slow"), -0.05, total);
            }
        }
        return e;
    }

    /** MobEffect simples (só atributos; o comportamento extra está em {@link RacePassives}). */
    public static final class RaceMobEffect extends MobEffect {
        public RaceMobEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    public static CharacterData data(Player p) {
        return p.getData(DATA);
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
                e.accept(ALTAR_ITEM.get());
                for (Race r : Race.ALL) e.accept(relic(r).get());
            }
        });
    }

    private CharacterContent() {}
}
