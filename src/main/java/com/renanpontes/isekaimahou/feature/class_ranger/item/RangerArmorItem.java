package com.renanpontes.isekaimahou.feature.class_ranger.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
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
 * Peça do Traje Selvagem do Ranger: armadura com modelo 3D GeckoLib (um modelo por grau, gerado por
 * tools/gen/class_ranger.py). Além da defesa do material dá Foco máximo, velocidade, regeneração e (botas IV) queda
 * segura. Capa, cauda do capuz e penas balançam no renderer (sem arquivo de animação).
 */
public class RangerArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderizador, preenchida pelo cliente; no servidor dedicado fica nula. */
    public static volatile Function<RangerArmorItem, GeoRenderProvider> renderFactory;

    private final RangerOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public RangerArmorItem(RangerOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.tier = tier;
    }

    public RangerOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos modificadores do material (defesa/resistência). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        String piece = type.getName();
        if (tier.focus(type) > 0) b.add(CoreRegistries.MAX_MANA, new AttributeModifier(IsekaiMahou.id("ranger_garb." + piece + ".focus"),
                tier.focus(type), AttributeModifier.Operation.ADD_VALUE), group);
        if (tier.speed(type) > 0) b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(IsekaiMahou.id("ranger_garb." + piece + ".speed"),
                tier.speed(type), AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (tier.regen(type) > 0) b.add(CoreRegistries.MANA_REGEN, new AttributeModifier(IsekaiMahou.id("ranger_garb." + piece + ".regen"),
                tier.regen(type), AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (type == Type.BOOTS && tier.safeFall > 0) b.add(Attributes.SAFE_FALL_DISTANCE,
                new AttributeModifier(IsekaiMahou.id("ranger_garb.boots.fall"), tier.safeFall, AttributeModifier.Operation.ADD_VALUE), group);
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<RangerArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // movimento procedural no renderer
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tier.langKey("tooltip")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_garb.tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_garb.set", tier.totalDefense(),
                Math.round(tier.totalFocus()), Math.round(tier.totalSpeed() * 100)).withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_garb.full", Math.round(setArrowBonus(tier) * 100))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Bônus de dano das flechas com o traje COMPLETO (vale o grau da peça mais simples). */
    public static float setArrowBonus(RangerOutfitTier tier) {
        return switch (tier) {
            case SCOUT -> 0.05F;
            case TRACKER -> 0.08F;
            case WARDEN -> 0.12F;
            case BEASTLORD -> 0.16F;
        };
    }

    /** Menor grau do traje completo vestido (4 peças do Ranger), ou null. */
    @Nullable
    public static RangerOutfitTier fullSet(@Nullable LivingEntity e) {
        if (e == null) return null;
        RangerOutfitTier low = null;
        for (Type t : RangerOutfitTier.TYPES) {
            ItemStack worn = e.getItemBySlot(t.getSlot());
            if (!(worn.getItem() instanceof RangerArmorItem piece)) return null;
            if (low == null || piece.tier.ordinal() < low.ordinal()) low = piece.tier;
        }
        return low;
    }
}
