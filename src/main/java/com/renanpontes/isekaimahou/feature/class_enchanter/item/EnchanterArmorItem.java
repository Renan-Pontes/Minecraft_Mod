package com.renanpontes.isekaimahou.feature.class_enchanter.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;

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
 * Peça do traje do Encantador: armadura desenhada com um modelo 3D do GeckoLib (um por grau). O balanço do casaco e da
 * capa é procedural no renderer (cliente). Além da proteção, cada peça dá Vigor/vida/velocidade/poder do grau.
 */
public class EnchanterArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente (servidor dedicado: nula, nunca pedida). */
    public static volatile Function<EnchanterArmorItem, GeoRenderProvider> renderFactory;

    private final EnchanterOutfit tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public EnchanterArmorItem(EnchanterOutfit tier, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, tier == EnchanterOutfit.SOVEREIGN ? props.fireResistant() : props);
        this.tier = tier;
    }

    public EnchanterOutfit tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (EnchanterOutfit.Bonus bonus : tier.bonuses(type)) {
            b.add(bonus.attribute(), new AttributeModifier(IsekaiMahou.id("enchanter_outfit." + type.getName() + "." + bonus.key()),
                    bonus.amount(), bonus.op()), group);
        }
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<EnchanterArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // balanço calculado no renderer a partir da pose (sem arquivos de animação)
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tier.flavorKey()).withStyle(ChatFormatting.GRAY,
                ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_outfit.tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_outfit.set",
                Math.round(tier.setDurationBonus() * 100)).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Menor grau do traje completo do Encantador vestido, ou null se faltar peça. */
    @Nullable
    public static EnchanterOutfit fullSet(LivingEntity e) {
        EnchanterOutfit lowest = null;
        for (ArmorItem.Type t : EnchRegistries.TYPES) {
            ItemStack worn = e.getItemBySlot(t.getSlot());
            if (!(worn.getItem() instanceof EnchanterArmorItem piece)) return null;
            if (lowest == null || piece.tier.ordinal() < lowest.ordinal()) lowest = piece.tier;
        }
        return lowest;
    }
}
