package com.renanpontes.isekaimahou.feature.class_smith.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;

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
 * Peça da roupa do Ferreiro (avental de forja, braçadeiras, óculos de ferreiro...), desenhada como modelo 3D do
 * GeckoLib ({@code geo/item/armor/outfit_smith[_grau].geo.json}, gerado por {@code tools/gen/class_smith.py}).
 * O balanço do avental e da capa é procedural no renderer (sem arquivo de animação).
 */
public class SmithOutfitItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente. No servidor dedicado fica nula. */
    public static volatile Function<SmithOutfitItem, GeoRenderProvider> renderFactory;

    private final SmithOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public SmithOutfitItem(SmithOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.tier = tier;
    }

    public SmithOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos do material (armadura/resistência), sem substituir. */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) {
            b.add(e.attribute(), e.modifier(), e.slot());
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (SmithOutfitTier.Bonus bonus : tier.bonuses(type)) {
            b.add(bonus.attribute(), new AttributeModifier(IsekaiMahou.id("smith_outfit." + type.getName() + "." + bonus.id()),
                    bonus.amount(), bonus.op()), group);
        }
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<SmithOutfitItem, GeoRenderProvider> factory = renderFactory;
        if (factory != null) consumer.accept(factory.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: o renderer move avental e capa conforme a pose
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(SmithKeys.OUTFIT_FLAVOR + tier.id).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.outfit.tier", tier.roman(), tier.displayName(), tier.circle)
                .withStyle(tier.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.outfit.class", MagicPath.SMITH.displayName())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.outfit.fire", Math.round(tier.fireWard * 100))
                .withStyle(ChatFormatting.RED));
    }

    /** Quanto do dano de fogo as peças do Ferreiro vestidas cortam (soma, máx. 60%). */
    public static float fireWard(LivingEntity entity) {
        float sum = 0F;
        for (ArmorItem.Type type : SmithOutfitTier.TYPES) {
            if (entity.getItemBySlot(type.getSlot()).getItem() instanceof SmithOutfitItem piece) sum += piece.tier.fireWard;
        }
        return Math.min(0.6F, sum);
    }
}
