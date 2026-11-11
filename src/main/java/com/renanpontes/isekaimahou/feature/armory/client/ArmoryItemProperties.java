package com.renanpontes.isekaimahou.feature.armory.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.armory.ArmoryItems;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryBowItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryCrossbowItem;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Predicados de modelo das armas (os do vanilla só existem para o arco/besta/escudo comuns):
 * arco ({@code pull}/{@code pulling}, com a puxada longa), bestas ({@code pull}/{@code pulling}/{@code charged}/
 * {@code firework}, com a recarga do tier), escudo ({@code blocking}) e alaúde ({@code isekai_mahou:playing}).
 */
public final class ArmoryItemProperties {
    private static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");
    private static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");
    private static final ResourceLocation CHARGED = ResourceLocation.withDefaultNamespace("charged");
    private static final ResourceLocation FIREWORK = ResourceLocation.withDefaultNamespace("firework");
    private static final ResourceLocation BLOCKING = ResourceLocation.withDefaultNamespace("blocking");
    public static final ResourceLocation PLAYING = IsekaiMahou.id("playing");

    static void register() {
        for (WeaponKind kind : WeaponKind.values()) {
            for (DeferredItem<Item> holder : ArmoryItems.WEAPONS.get(kind)) {
                Item item = holder.get();
                if (item instanceof ArmoryBowItem bow) {
                    ItemProperties.register(item, PULL, (stack, level, e, seed) -> e == null || e.getUseItem() != stack ? 0F
                            : (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) bow.drawTicks());
                    ItemProperties.register(item, PULLING, (stack, level, e, seed) ->
                            e != null && e.isUsingItem() && e.getUseItem() == stack ? 1F : 0F);
                } else if (item instanceof ArmoryCrossbowItem crossbow) {
                    ItemProperties.register(item, PULL, (stack, level, e, seed) -> e == null || CrossbowItem.isCharged(stack) ? 0F
                            : (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) crossbow.chargeTicks(stack, e));
                    ItemProperties.register(item, PULLING, (stack, level, e, seed) ->
                            e != null && e.isUsingItem() && e.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1F : 0F);
                    ItemProperties.register(item, CHARGED, (stack, level, e, seed) -> CrossbowItem.isCharged(stack) ? 1F : 0F);
                    ItemProperties.register(item, FIREWORK, (stack, level, e, seed) -> {
                        ChargedProjectiles c = stack.get(DataComponents.CHARGED_PROJECTILES);
                        return c != null && c.contains(Items.FIREWORK_ROCKET) ? 1F : 0F;
                    });
                } else if (kind == WeaponKind.LUTE) {
                    ItemProperties.register(item, PLAYING, (stack, level, e, seed) ->
                            e != null && e.isUsingItem() && e.getUseItem() == stack ? 1F : 0F);
                }
            }
        }
        ItemProperties.register(ArmoryItems.KNIGHT_SHIELD.get(), BLOCKING, (stack, level, e, seed) ->
                e != null && e.isUsingItem() && e.getUseItem() == stack ? 1F : 0F);
    }

    private ArmoryItemProperties() {}
}
