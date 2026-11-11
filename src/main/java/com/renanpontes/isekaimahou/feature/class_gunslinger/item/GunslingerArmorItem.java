package com.renanpontes.isekaimahou.feature.class_gunslinger.item;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
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
 * Peça do traje do Pistoleiro: armadura desenhada como modelo 3D do GeckoLib (chapéu de aba larga, guarda-pó com
 * bandoleira, calças com coldre, botas de espora). O balanço das abas do guarda-pó e do poncho é feito no renderer.
 * Além da proteção do material, cada peça dá Foco máximo, velocidade, regeneração e poder conforme o grau; o conjunto
 * completo (4 peças do Pistoleiro, qualquer grau) recarrega as armas 15% mais rápido ({@code GunCombat}).
 */
public class GunslingerArmorItem extends ArmorItem implements GeoItem {
    /** Fábrica do renderer (preenchida pelo cliente; nula no servidor dedicado). */
    public static volatile Function<GunslingerArmorItem, GeoRenderProvider> renderFactory;

    private static final String[] STAT_IDS = {"max_mana", "movement_speed", "mana_regen", "spell_power"};

    private final GsOutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public GunslingerArmorItem(GsOutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.tier = tier;
    }

    public GsOutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** Soma os bônus do grau aos modificadores do material (sem substituir a armadura). */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) {
            builder.add(e.attribute(), e.modifier(), e.slot());
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (int stat = 0; stat < STAT_IDS.length; stat++) {
            double v = tier.bonus(stat, type);
            if (v == 0) continue;
            AttributeModifier.Operation op = stat == 0 ? AttributeModifier.Operation.ADD_VALUE
                    : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            // id por peça: capacete e peitoral somam em vez de um substituir o outro
            builder.add(attribute(stat), new AttributeModifier(IsekaiMahou.id("gunslinger_outfit." + type.getName() + "."
                    + STAT_IDS[stat]), v, op), group);
        }
        return builder.build();
    }

    private static Holder<Attribute> attribute(int stat) {
        return switch (stat) {
            case 0 -> CoreRegistries.MAX_MANA;
            case 1 -> Attributes.MOVEMENT_SPEED;
            case 2 -> CoreRegistries.MANA_REGEN;
            default -> CoreRegistries.SPELL_POWER;
        };
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<GunslingerArmorItem, GeoRenderProvider> f = renderFactory;
        if (f != null) consumer.accept(f.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: o balanço é calculado no renderer a partir da pose do jogador
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tier.flavorKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.outfit.tier", tier.roman(), tier.displayName(),
                tier.circle).withStyle(tier.chatColor()));
        if (!flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.outfit.set", tier.totalDefense(),
                fmt(tier.total(0)), fmt(tier.total(1) * 100)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.outfit.set_bonus").withStyle(ChatFormatting.GOLD));
        GsOutfitTier next = tier.next();
        if (next != null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.outfit.next", next.displayName(), next.circle)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    /** O jogador veste as 4 peças do Pistoleiro (qualquer grau)? */
    public static boolean fullSet(LivingEntity entity) {
        for (ArmorItem.Type type : GsOutfitTier.TYPES) {
            if (!(entity.getItemBySlot(type.getSlot()).getItem() instanceof GunslingerArmorItem)) return false;
        }
        return true;
    }

    /** Menor grau entre as 4 peças vestidas, ou null sem traje completo. */
    public static GsOutfitTier fullSetTier(LivingEntity entity) {
        GsOutfitTier lowest = null;
        for (ArmorItem.Type type : GsOutfitTier.TYPES) {
            if (!(entity.getItemBySlot(type.getSlot()).getItem() instanceof GunslingerArmorItem piece)) return null;
            if (lowest == null || piece.tier.ordinal() < lowest.ordinal()) lowest = piece.tier;
        }
        return lowest;
    }
}
