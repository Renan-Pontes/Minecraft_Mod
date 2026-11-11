package com.renanpontes.isekaimahou.feature.class_gambler.outfit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Peça do traje do Apostador (modelo 3D GeckoLib por grau, no padrão dos trajes de classe). Além da proteção do
 * material, soma Foco máximo, velocidade, regeneração, Poder Mágico e Sorte (saques) conforme o grau.
 */
public class GamblerOutfitItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer (preenchida pelo cliente; nula no servidor dedicado). */
    public static volatile Function<GamblerOutfitItem, GeoRenderProvider> renderFactory;

    private final GamblerOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public GamblerOutfitItem(GamblerOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
        this.tier = tier;
    }

    public GamblerOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos modificadores do ArmorItem (o componente de atributos substituiria a armadura). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        add(b, group, CoreRegistries.MAX_MANA, "focus", tier.focus(type), AttributeModifier.Operation.ADD_VALUE);
        add(b, group, Attributes.MOVEMENT_SPEED, "speed", tier.speed(type), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(b, group, CoreRegistries.MANA_REGEN, "regen", tier.regen(type), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(b, group, CoreRegistries.SPELL_POWER, "power", tier.power(type), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(b, group, Attributes.LUCK, "luck", tier.luck, AttributeModifier.Operation.ADD_VALUE);
        return b.build();
    }

    private void add(ItemAttributeModifiers.Builder b, EquipmentSlotGroup group, Holder<Attribute> attr, String stat, double v,
            AttributeModifier.Operation op) {
        if (v == 0) return;
        b.add(attr, new AttributeModifier(IsekaiMahou.id("gambler_outfit." + type.getName() + "." + stat), v, op), group);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<GamblerOutfitItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // o balanço das abas do fraque e do chapéu é procedural no renderer
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(String.join("", "tooltip.isekai_mahou.gambler_outfit.", tier.id)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_outfit.tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_outfit.set").withStyle(ChatFormatting.DARK_AQUA));
    }
}
