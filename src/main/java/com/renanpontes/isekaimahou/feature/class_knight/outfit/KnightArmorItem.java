package com.renanpontes.isekaimahou.feature.class_knight.outfit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;

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
 * Peça da armadura do Cavaleiro Rúnico: placas desenhadas como modelo 3D do GeckoLib (capa, tabardo, faldas e
 * crista se movem no renderer). Além da proteção do material, cada peça dá Vigor máximo, vida, dano corpo a corpo e
 * regeneração do recurso conforme o grau ({@link KnightOutfitTier}).
 */
public class KnightArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente (nula no servidor dedicado). */
    public static volatile Function<KnightArmorItem, GeoRenderProvider> renderFactory;

    private final KnightOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public KnightArmorItem(KnightOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
        this.tier = tier;
    }

    public KnightOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos modificadores do material (sem substituir a armadura). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        String p = "knight_outfit." + type.getName() + ".";
        if (tier.resource(type) > 0) {
            b.add(CoreRegistries.MAX_MANA, new AttributeModifier(IsekaiMahou.id(p + "max_mana"), tier.resource(type),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (tier.health(type) > 0) {
            b.add(Attributes.MAX_HEALTH, new AttributeModifier(IsekaiMahou.id(p + "max_health"), tier.health(type),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (tier.melee(type) > 0) {
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(IsekaiMahou.id(p + "attack_damage"), tier.melee(type),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (tier.regen(type) > 0) {
            b.add(CoreRegistries.MANA_REGEN, new AttributeModifier(IsekaiMahou.id(p + "mana_regen"), tier.regen(type),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        }
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<KnightArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // movimento procedural no renderer (capa, faldas, crista): sem arquivos de animação
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("tooltip", "outfit", tier.id)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.outfit_tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.outfit_class", MagicPath.KNIGHT.displayName())
                .withStyle(ChatFormatting.DARK_GRAY));
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.outfit_set", tier.totalDefense(),
                    Math.round(tier.total(0)), Math.round(tier.total(1)), String.format(java.util.Locale.ROOT, "%.1f", tier.total(2)))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.outfit_bonus").withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Menor grau da armadura completa vestida (4 peças do Cavaleiro), ou null. */
    public static KnightOutfitTier fullSet(LivingEntity e) {
        KnightOutfitTier low = null;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            if (!(e.getItemBySlot(slot).getItem() instanceof KnightArmorItem k)) return null;
            if (low == null || k.tier.ordinal() < low.ordinal()) low = k.tier;
        }
        return low;
    }
}
