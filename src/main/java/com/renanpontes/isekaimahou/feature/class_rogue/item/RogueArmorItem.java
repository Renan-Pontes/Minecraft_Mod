package com.renanpontes.isekaimahou.feature.class_rogue.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueKeys;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueGear;

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
 * Peça do Traje das Sombras (Ladino): armadura desenhada como modelo 3D do GeckoLib (um modelo por grau, gerado por
 * {@code tools/gen/class_rogue.py}). O balanço da capa e das abas do gibão é procedural no renderer. Além da proteção,
 * cada peça dá os bônus do grau ({@link RogueGear}); o conjunto completo soma chance de crítico à passiva do Ladino.
 */
public class RogueArmorItem extends ArmorItem implements GeoItem {
    /** Preenchida pelo cliente (ClassRogueClient). No servidor dedicado fica nula. */
    public static volatile Function<RogueArmorItem, GeoRenderProvider> renderFactory;

    private final RogueGear gear;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public RogueArmorItem(RogueGear gear, Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
        this.gear = gear;
    }

    public RogueGear gear() {
        return gear;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Armadura do material + bônus do grau (somados, sem substituir a armadura). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (RogueGear.Bonus bonus : gear.bonuses(type)) {
            RogueGear.Stat s = bonus.stat();
            b.add(s.attribute, new AttributeModifier(IsekaiMahou.id("rogue_shade." + type.getName() + "." + s.id), bonus.amount(),
                    s.operation), group);
        }
        return b.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<RogueArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animação em arquivo: o renderer balança capa e abas pela pose do jogador
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(RogueKeys.k("tooltip.isekai_mahou.class_rogue.gear.flavor.", gear.id)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.gear.tier", gear.roman(), gear.displayName(), gear.circle)
                .withStyle(gear.chatColor()));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.gear.class", MagicPath.ROGUE.displayName())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.gear.set", gear.totalDefense(),
                RogueGear.fmt(gear.total(RogueGear.Stat.RESOURCE)), RogueGear.fmt(gear.total(RogueGear.Stat.SPEED) * 100),
                RogueGear.fmt(gear.critBonus * 100)).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Menor grau do traje completo vestido (4 peças do Traje das Sombras), ou null. */
    @Nullable
    public static RogueGear fullSet(LivingEntity e) {
        RogueGear lowest = null;
        for (Type type : RogueGear.TYPES) {
            ItemStack worn = e.getItemBySlot(type.getSlot());
            if (!(worn.getItem() instanceof RogueArmorItem piece)) return null;
            if (lowest == null || piece.gear.ordinal() < lowest.ordinal()) lowest = piece.gear;
        }
        return lowest;
    }
}
