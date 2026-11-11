package com.renanpontes.isekaimahou.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.effect.BleedingEffect;
import com.renanpontes.isekaimahou.core.effect.BurningEffect;
import com.renanpontes.isekaimahou.core.effect.ChilledEffect;
import com.renanpontes.isekaimahou.core.effect.CorruptedEffect;
import com.renanpontes.isekaimahou.core.effect.FrozenEffect;
import com.renanpontes.isekaimahou.core.effect.MagicEffect;
import com.renanpontes.isekaimahou.core.effect.PetrifiedEffect;
import com.renanpontes.isekaimahou.core.effect.ShockedEffect;
import com.renanpontes.isekaimahou.core.effect.SoakedEffect;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.core.item.SpellScrollItem;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do NÚCLEO. Módulos criam os seus próprios DeferredRegister (pode ter vários por tipo).
 */
public final class CoreRegistries {
    public static final String MODID = IsekaiMahou.MODID;

    // ---------------- Dano de magia ----------------
    /** Tipo de dano de todas as magias (data/isekai_mahou/damage_type/spell.json). */
    public static final ResourceKey<DamageType> SPELL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, IsekaiMahou.id("spell"));

    // ---------------- Dados do jogador ----------------
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final Supplier<AttachmentType<MagicData>> MAGIC = ATTACHMENTS.register("magic",
            () -> AttachmentType.builder(() -> new MagicData()).serialize(MagicData.CODEC).copyOnDeath().build());

    // ---------------- Entidades do núcleo ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final Supplier<EntityType<SpellProjectileEntity>> SPELL_PROJECTILE = ENTITIES.register("spell_projectile",
            () -> EntityType.Builder.<SpellProjectileEntity>of(SpellProjectileEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(5).build("spell_projectile"));

    // ---------------- Efeitos de status ----------------
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    /** Molhado (id antigo "soaked"): apaga fogo; Raio causa Eletrocutar, Gelo congela, Fogo −50%. Regras em core/status. */
    public static final DeferredHolder<MobEffect, MobEffect> SOAKED = EFFECTS.register("soaked",
            () -> new SoakedEffect(MobEffectCategory.HARMFUL, 0x3FA9FF));
    /** Congelado: preso no gelo — não anda, não pula, não ataca, não conjura (regras em core/status). */
    public static final DeferredHolder<MobEffect, MobEffect> FROZEN = EFFECTS.register("frozen",
            () -> new FrozenEffect(MobEffectCategory.HARMFUL, 0xA8E8FF)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.frozen"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.FLYING_SPEED, IsekaiMahou.id("effect.frozen"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.frozen"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.frozen"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Em Chamas (1–5): dano mágico contínuo, +8% de dano de Fogo e −30% de cura por acúmulo (regras em core/status). */
    public static final DeferredHolder<MobEffect, MobEffect> BURNING = EFFECTS.register("burning",
            () -> new BurningEffect(MobEffectCategory.HARMFUL, 0xFF6A2B));
    /** Gelado (1–5): −12% velocidade, −8% vel. de ataque, −20% regeneração de mana por acúmulo; 5 = Congelado. */
    public static final DeferredHolder<MobEffect, MobEffect> CHILLED = EFFECTS.register("chilled",
            () -> new ChilledEffect(MobEffectCategory.HARMFUL, 0x9FDFFF)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.chilled"), -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.FLYING_SPEED, IsekaiMahou.id("effect.chilled"), -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.chilled"), -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    // Nome qualificado: o atributo é declarado mais abaixo (a lambda só roda no registro)
                    .addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("effect.chilled"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> -Math.min(1.0, 0.2 * (amp + 1))));
    /** Eletrizado (1–3): Raio +15%/acúmulo, micro-atordoamentos, drena mana, arqueia entre alvos Molhados. */
    public static final DeferredHolder<MobEffect, MobEffect> SHOCKED = EFFECTS.register("shocked",
            () -> new ShockedEffect(MobEffectCategory.HARMFUL, 0x7FDBFF));
    /** Sangrando (1–10): dano contínuo, dobrado se o alvo se move. */
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding",
            () -> new BleedingEffect(MobEffectCategory.HARMFUL, 0xC0182E));
    /** Petrificação (1–3): 1–2 Enrijecendo (−20% velocidade/acúmulo); 3 Petrificado (pedra: preso, +50% armadura). */
    public static final DeferredHolder<MobEffect, MobEffect> PETRIFIED = EFFECTS.register("petrified",
            () -> new PetrifiedEffect(MobEffectCategory.HARMFUL, 0x9A948A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> amp >= 2 ? -1.0 : -0.2 * (amp + 1))
                    .addAttributeModifier(Attributes.FLYING_SPEED, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> amp >= 2 ? -1.0 : -0.2 * (amp + 1))
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> amp >= 2 ? -1.0 : 0.0)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> amp >= 2 ? -1.0 : 0.0)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                            amp -> amp >= 2 ? 0.5 : 0.0)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.petrified"), AttributeModifier.Operation.ADD_VALUE,
                            amp -> amp >= 2 ? 1.0 : 0.0));
    /** Atordoado: não anda, não pula, ataca muito devagar. */
    public static final DeferredHolder<MobEffect, MobEffect> STUNNED = EFFECTS.register("stunned",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0xFFF27A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.stunned"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.stunned"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.stunned"), -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Enraizado: não anda nem pula, mas pode atacar. */
    public static final DeferredHolder<MobEffect, MobEffect> ROOTED = EFFECTS.register("rooted",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0x6B8E23)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.rooted"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.rooted"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Corrompido: dano contínuo de sombra. */
    public static final DeferredHolder<MobEffect, MobEffect> CORRUPTED = EFFECTS.register("corrupted",
            () -> new CorruptedEffect(MobEffectCategory.HARMFUL, 0x5A1E7A));
    /** Exposto: recebe +25% de dano por nível. */
    public static final DeferredHolder<MobEffect, MobEffect> EXPOSED = EFFECTS.register("exposed",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0xFFD86B));
    /** Exaustão Mágica: não conjura e não regenera mana (depois de um Domínio). */
    public static final DeferredHolder<MobEffect, MobEffect> MAGIC_EXHAUSTION = EFFECTS.register("magic_exhaustion",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0x555577));
    /** Silenciado: não conjura magias. */
    public static final DeferredHolder<MobEffect, MobEffect> SILENCED = EFFECTS.register("silenced",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0x9A9AB0));

    // ---------------- Atributos do jogador (equipamentos/Curios mexem nestes) ----------------
    public static final DeferredRegister<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, MODID);
    /** Mana máximo extra (soma). */
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> MAX_MANA =
            ATTRIBUTES.register("max_mana", () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                    "attribute.isekai_mahou.max_mana", 0.0, 0.0, 10000.0).setSyncable(true));
    /** Multiplicador da regeneração de mana (1.0 = normal). */
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> MANA_REGEN =
            ATTRIBUTES.register("mana_regen", () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                    "attribute.isekai_mahou.mana_regen", 1.0, 0.0, 30.0).setSyncable(true));
    /** Multiplicador do poder das magias (dano, cura...). */
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> SPELL_POWER =
            ATTRIBUTES.register("spell_power", () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                    "attribute.isekai_mahou.spell_power", 1.0, 0.0, 10.0).setSyncable(true));

    // ---------------- Componentes de item ----------------
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    /** Id de glifo guardado num Pergaminho de Glifo. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GLYPH = COMPONENTS.registerComponentType("glyph",
            b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    /** Magia guardada num Pergaminho de Magia. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpellRecipe>> SPELL = COMPONENTS.registerComponentType("spell",
            b -> b.persistent(SpellRecipe.CODEC).networkSynchronized(SpellRecipe.STREAM_CODEC));

    // ---------------- Itens do núcleo (materiais compartilhados entre módulos) ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** Essências: arcane + 6 base. Usadas em rituais (Ascensão etc.). Deixadas cair por espíritos/mobs elementais. */
    public static final Map<Element, DeferredItem<Item>> ESSENCES = new EnumMap<>(Element.class);
    /** Núcleos dos Guardiões (6 base): liberam ultimates e Círculos altos. Deixados cair pelos chefes. */
    public static final Map<Element, DeferredItem<Item>> GUARDIAN_CORES = new EnumMap<>(Element.class);
    public static final DeferredItem<Item> MANA_CRYSTAL = ITEMS.registerSimpleItem("mana_crystal", new Item.Properties());
    public static final DeferredItem<GlyphScrollItem> GLYPH_SCROLL = ITEMS.register("glyph_scroll",
            () -> new GlyphScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<SpellScrollItem> SPELL_SCROLL = ITEMS.register("spell_scroll",
            () -> new SpellScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        ESSENCES.put(Element.ARCANE, ITEMS.registerSimpleItem("essence_arcane", new Item.Properties()));
        for (Element e : Element.BASE) {
            ESSENCES.put(e, ITEMS.registerSimpleItem("essence_" + e.id, new Item.Properties()));
            GUARDIAN_CORES.put(e, ITEMS.registerSimpleItem("guardian_core_" + e.id,
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));
        }
    }

    // ---------------- Abas do modo criativo ----------------
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    /** Magia: pergaminhos, essências, focos, cristais. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MAGIC = TABS.register("magic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isekai_mahou.magic"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> MANA_CRYSTAL.get().getDefaultInstance())
            .displayItems((p, out) -> {
                out.accept(MANA_CRYSTAL.get());
                ESSENCES.values().forEach(i -> out.accept(i.get()));
                GUARDIAN_CORES.values().forEach(i -> out.accept(i.get()));
                out.accept(SPELL_SCROLL.get());
                GlyphScrollItem.addAllToTab(out);
            }).build());
    /** Criaturas: ovos de mob. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_CREATURES = TABS.register("creatures", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isekai_mahou.creatures"))
            .withTabsBefore(TAB_MAGIC.getKey())
            .icon(() -> ESSENCES.get(Element.EARTH).get().getDefaultInstance())
            .build());
    /** Rituais e classes: itens/blocos de ritual, roupas. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_RITUALS = TABS.register("rituals", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isekai_mahou.rituals"))
            .withTabsBefore(TAB_CREATURES.getKey())
            .icon(() -> ESSENCES.get(Element.SHADOW).get().getDefaultInstance())
            .build());
    /** Mundo: blocos de estrutura, portais, decoração. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_WORLD = TABS.register("world", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isekai_mahou.world"))
            .withTabsBefore(TAB_RITUALS.getKey())
            .icon(() -> ESSENCES.get(Element.LIGHT).get().getDefaultInstance())
            .build());

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent e) -> {
            e.add(EntityType.PLAYER, MAX_MANA);
            e.add(EntityType.PLAYER, MANA_REGEN);
            e.add(EntityType.PLAYER, SPELL_POWER);
        });
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private CoreRegistries() {}
}
