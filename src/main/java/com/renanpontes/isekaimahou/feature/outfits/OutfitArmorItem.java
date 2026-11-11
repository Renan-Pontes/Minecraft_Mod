package com.renanpontes.isekaimahou.feature.outfits;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Suppliers;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Peça de roupa de classe: armadura comum desenhada como modelo 3D do GeckoLib (um modelo por traje e grau).
 * O movimento (saia, capa, ponta do chapéu) é procedural no renderer, então não há controladores de animação.
 * Além da proteção do material, cada peça dá os bônus do grau conforme a categoria da classe ({@link OutfitStats}):
 * mana/vigor/foco máximo, regeneração e poder em todas; vida e dano nas Guerreiras; velocidade nas À Distância.
 */
public class OutfitArmorItem extends ArmorItem implements GeoItem {
    /**
     * Fábrica do renderizador, preenchida pelo lado do cliente (OutfitsClient). No servidor dedicado fica nula
     * e o GeckoLib nem chega a pedir o renderizador.
     */
    public static volatile Function<OutfitArmorItem, GeoRenderProvider> renderFactory;

    /** Modelo de melhoria do módulo armory (procurado por id; sem ele vale a receita reserva com Essência Arcana). */
    public static final String UPGRADE_TEMPLATE = "arcane_upgrade_template";

    private final OutfitSet set;
    private final OutfitTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Modificadores do material (armadura/resistência) + os bônus do grau, montados uma vez. */
    private final Supplier<ItemAttributeModifiers> modifiers = Suppliers.memoize(this::buildModifiers);

    public OutfitArmorItem(OutfitSet set, OutfitTier tier, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.set = set;
        this.tier = tier;
    }

    public OutfitSet set() {
        return set;
    }

    public OutfitTier tier() {
        return tier;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /**
     * Soma os bônus do grau aos modificadores do ArmorItem. Não usamos {@code Item.Properties.attributes(...)}: o
     * componente de atributos substituiria a armadura do material em vez de somar.
     */
    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : super.getDefaultAttributeModifiers().modifiers()) {
            builder.add(e.attribute(), e.modifier(), e.slot());
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        for (OutfitStats.Bonus bonus : OutfitStats.bonuses(set, tier, type)) {
            OutfitStats.Stat stat = bonus.stat();
            // id por peça: capacete e peitoral somam em vez de um substituir o outro (mesmos ids da v0.7 nas Mágicas)
            builder.add(stat.attribute, new AttributeModifier(IsekaiMahou.id("outfit." + type.getName() + "." + stat.id),
                    bonus.amount(), stat.operation), group);
        }
        return builder.build();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Function<OutfitArmorItem, GeoRenderProvider> factory = renderFactory;
        if (factory != null) consumer.accept(factory.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sem animações em arquivo: o balanço das peças é calculado no renderer a partir da pose do jogador
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(set.flavorKey(tier)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.tier", tier.roman(), set.tierName(tier), tier.circle)
                .withStyle(tier.chatColor()));
        // v1.0: os trajes da v0.9 são avulsos (sem classe): qualquer um veste, com os bônus da categoria
        tooltip.add((set.isGeneric()
                ? Component.translatable("tooltip.isekai_mahou.outfit.generic", set.ownerName())
                : Component.translatable("tooltip.isekai_mahou.outfit.class", set.ownerName()))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (tier != OutfitTier.APPRENTICE) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.gift", tier.circle).withStyle(ChatFormatting.DARK_AQUA));
        }
        OutfitTier next = tier.next();
        if (next != null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.upgrade", templateName(), upgradeMaterialName(next),
                    set.tierName(next).withStyle(next.chatColor())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.upgrade_max").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.outfit.retailor").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Nome do modelo usado na Mesa de Ferraria (o do armory, ou a Essência Arcana da receita reserva). */
    public static Component templateName() {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(UPGRADE_TEMPLATE)).map(Item::getDescription)
                .orElseGet(() -> CoreRegistries.ESSENCES.get(Element.ARCANE).get().getDescription());
    }

    /** Material da melhoria PARA o grau {@code target} (igual às tags outfits/upgrade/<grau>). */
    public static Component upgradeMaterialName(OutfitTier target) {
        return switch (target) {
            case ADEPT -> CoreRegistries.MANA_CRYSTAL.get().getDescription();
            case MASTER -> Items.DIAMOND.getDescription();
            default -> Component.translatable("tooltip.isekai_mahou.outfit.material_archmage");
        };
    }

    /** Traje completo (4 peças da mesma classe, em qualquer grau) vestido pela entidade, ou null. */
    @Nullable
    public static OutfitSet fullSet(LivingEntity entity) {
        OutfitSet found = null;
        for (ArmorItem.Type type : OutfitRegistries.TYPES) {
            ItemStack worn = entity.getItemBySlot(type.getSlot());
            if (!(worn.getItem() instanceof OutfitArmorItem piece)) return null;
            if (found == null) found = piece.set;
            else if (found != piece.set) return null;
        }
        return found;
    }

    /** Menor grau entre as 4 peças vestidas (o brilho do traje completo segue a peça mais simples), ou null. */
    @Nullable
    public static OutfitTier fullSetTier(LivingEntity entity) {
        OutfitTier lowest = null;
        for (ArmorItem.Type type : OutfitRegistries.TYPES) {
            ItemStack worn = entity.getItemBySlot(type.getSlot());
            if (!(worn.getItem() instanceof OutfitArmorItem piece)) return null;
            if (lowest == null || piece.tier.ordinal() < lowest.ordinal()) lowest = piece.tier;
        }
        return lowest;
    }
}
