package com.renanpontes.isekaimahou.feature.class_quincy.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyArmor;

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
 * Peça do traje Quincy (4 graus, modelo 3D GeckoLib). Além da proteção do material, cada peça soma Foco máximo,
 * regeneração de Foco, velocidade e Poder Mágico ({@link QuincyArmor.Tier}). O balanço da capa/abas do casaco é feito
 * no renderer (cliente).
 */
public class QuincyArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente (no servidor dedicado fica nula e nunca é pedida). */
    public static volatile Function<QuincyArmorItem, GeoRenderProvider> renderFactory;

    private final QuincyArmor.Tier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public QuincyArmorItem(QuincyArmor.Tier tier, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
        this.tier = tier;
    }

    public QuincyArmor.Tier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos do ArmorItem (o componente de atributos substituiria a armadura do material). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        String piece = type.getName();
        int i = QuincyArmor.pieceIndex(type);
        double focus = tier.focus[i], regen = tier.regen[i], speed = tier.speed[i], power = tier.power[i];
        if (focus != 0) b.add(CoreRegistries.MAX_MANA, mod(piece, "focus", focus, AttributeModifier.Operation.ADD_VALUE), group);
        if (regen != 0) b.add(CoreRegistries.MANA_REGEN, mod(piece, "regen", regen, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (speed != 0) b.add(Attributes.MOVEMENT_SPEED, mod(piece, "speed", speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (power != 0) b.add(CoreRegistries.SPELL_POWER, mod(piece, "power", power, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        return b.build();
    }

    private static AttributeModifier mod(String piece, String stat, double v, AttributeModifier.Operation op) {
        return new AttributeModifier(IsekaiMahou.id("quincy_outfit." + piece + "." + stat), v, op);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<QuincyArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: a capa e as abas do casaco se movem no renderer, pela pose do jogador
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tier.flavorKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_outfit.tier", Quincy_roman(tier.number), tier.displayName(), tier.circle)
                .withStyle(tier.chat));
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_outfit.set", QuincyArmor.totalDefense(tier),
                    QuincyArmor.fmt(QuincyArmor.sum(tier.focus)), QuincyArmor.fmt(QuincyArmor.sum(tier.regen) * 100),
                    QuincyArmor.fmt(QuincyArmor.sum(tier.speed) * 100), QuincyArmor.fmt(QuincyArmor.sum(tier.power) * 100))
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (tier.number > 1) tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_outfit.gift", tier.circle).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_outfit.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String Quincy_roman(int n) {
        return com.renanpontes.isekaimahou.feature.class_quincy.Quincy.roman(n);
    }

    /** Menor grau do traje Quincy completo vestido, ou null. */
    @Nullable
    public static QuincyArmor.Tier fullSet(LivingEntity e) {
        QuincyArmor.Tier low = null;
        for (ArmorItem.Type t : QuincyArmor.TYPES) {
            if (!(e.getItemBySlot(t.getSlot()).getItem() instanceof QuincyArmorItem piece)) return null;
            if (low == null || piece.tier.ordinal() < low.ordinal()) low = piece.tier;
        }
        return low;
    }
}
