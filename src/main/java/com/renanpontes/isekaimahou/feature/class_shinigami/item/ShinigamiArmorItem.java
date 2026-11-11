package com.renanpontes.isekaimahou.feature.class_shinigami.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiArmor;

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
 * Peça do traje Shinigami (4 graus, modelo 3D GeckoLib). Além da proteção do material, cada peça soma Vigor máximo,
 * regeneração de Vigor, velocidade e dano corpo a corpo ({@link ShinigamiArmor.Tier}). As mangas largas, a faixa e o
 * haori balançam no renderer (cliente).
 */
public class ShinigamiArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer, preenchida pelo cliente (no servidor dedicado fica nula e nunca é pedida). */
    public static volatile Function<ShinigamiArmorItem, GeoRenderProvider> renderFactory;

    private final ShinigamiArmor.Tier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public ShinigamiArmorItem(ShinigamiArmor.Tier tier, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
        this.tier = tier;
    }

    public ShinigamiArmor.Tier tier() {
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
        int i = ShinigamiArmor.pieceIndex(type);
        double vigor = tier.vigor[i], regen = tier.regen[i], speed = tier.speed[i], melee = tier.melee[i];
        if (vigor != 0) b.add(CoreRegistries.MAX_MANA, mod(piece, "vigor", vigor, AttributeModifier.Operation.ADD_VALUE), group);
        if (regen != 0) b.add(CoreRegistries.MANA_REGEN, mod(piece, "regen", regen, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (speed != 0) b.add(Attributes.MOVEMENT_SPEED, mod(piece, "speed", speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        if (melee != 0) b.add(Attributes.ATTACK_DAMAGE, mod(piece, "melee", melee, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        return b.build();
    }

    private static AttributeModifier mod(String piece, String stat, double v, AttributeModifier.Operation op) {
        return new AttributeModifier(IsekaiMahou.id("shinigami_outfit." + piece + "." + stat), v, op);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<ShinigamiArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: mangas, faixa e haori se movem no renderer, pela pose do jogador
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tier.flavorKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.shinigami_outfit.tier", Shinigami.roman(tier.number), tier.displayName(), tier.circle)
                .withStyle(tier.chat));
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.shinigami_outfit.set", ShinigamiArmor.totalDefense(tier),
                    ShinigamiArmor.fmt(ShinigamiArmor.sum(tier.vigor)), ShinigamiArmor.fmt(ShinigamiArmor.sum(tier.regen) * 100),
                    ShinigamiArmor.fmt(ShinigamiArmor.sum(tier.speed) * 100), ShinigamiArmor.fmt(ShinigamiArmor.sum(tier.melee) * 100))
                    .withColor(Shinigami.RGB));
            if (tier.number > 1) {
                tooltip.add(Component.translatable("tooltip.isekai_mahou.shinigami_outfit.gift", tier.circle).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.shinigami_outfit.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Menor grau do traje Shinigami completo vestido, ou null. */
    @Nullable
    public static ShinigamiArmor.Tier fullSet(LivingEntity e) {
        ShinigamiArmor.Tier low = null;
        for (ArmorItem.Type t : ShinigamiArmor.TYPES) {
            if (!(e.getItemBySlot(t.getSlot()).getItem() instanceof ShinigamiArmorItem piece)) return null;
            if (low == null || piece.tier.ordinal() < low.ordinal()) low = piece.tier;
        }
        return low;
    }
}
