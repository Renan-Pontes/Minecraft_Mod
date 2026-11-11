package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
 * Peça da roupa do Steel Ball: armadura desenhada como modelo 3D (GeckoLib), com os bônus do grau ({@link SbOutfitTier}).
 * O balanço do cachecol/capa e da aba do chapéu é procedural no renderer (lado do cliente).
 */
public class SbOutfitItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente; nula no servidor dedicado. */
    public static volatile Function<SbOutfitItem, GeoRenderProvider> renderFactory;

    private final SbOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public SbOutfitItem(SbOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.tier = tier;
    }

    public SbOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos modificadores do material (armadura/resistência) — não substitui. */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) {
            b.add(e.attribute(), e.modifier(), e.slot());
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (SbOutfitTier.Bonus bonus : tier.bonuses(type)) {
            b.add(bonus.stat().attribute, new AttributeModifier(IsekaiMahou.id("steelball_outfit." + type.getName() + "." + bonus.stat().id),
                    bonus.amount(), bonus.stat().operation), group);
        }
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<SbOutfitItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: o balanço é calculado no renderer
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(tier.flavor()
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.outfit.tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        for (SbOutfitTier.Bonus b : tier.bonuses(type)) {
            tooltip.add(b.stat().describe(b.amount()).withStyle(ChatFormatting.BLUE));
        }
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.outfit.set", tier.totalDefense()).withStyle(ChatFormatting.DARK_GRAY));
            for (SbOutfitTier.Bonus b : tier.totals()) {
                tooltip.add(Component.literal("  ").append(b.stat().describe(b.amount())).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.outfit.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Roupa completa (as 4 peças) do Steel Ball vestida? Devolve o menor grau, ou null. */
    @javax.annotation.Nullable
    public static SbOutfitTier fullSet(LivingEntity e) {
        SbOutfitTier low = null;
        for (ArmorItem.Type t : SbOutfitTier.TYPES) {
            if (!(e.getItemBySlot(t.getSlot()).getItem() instanceof SbOutfitItem piece)) return null;
            if (low == null || piece.tier.ordinal() < low.ordinal()) low = piece.tier;
        }
        return low;
    }
}
