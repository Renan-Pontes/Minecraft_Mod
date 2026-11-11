package com.renanpontes.isekaimahou.feature.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.armory.ArmoryItems;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryBowItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Parte de cliente do módulo "armory". Os modelos 3D das armas são JSON (sem renderer próprio); aqui ficam:
 * os predicados de modelo (arco/besta/escudo/alaúde), a segunda adaga/manopla na mão vazia, a pose de tocar o
 * alaúde, o zoom do Arco Longo e o brilho ambiente das armas de tier III/IV.
 */
public class ArmoryClient implements ClientModule {
    /** Zoom do Arco Longo na puxada completa (o arco comum aproxima 15%). */
    public static final float LONGBOW_ZOOM = 0.15F;
    public static final float LONGBOW_ZOOM_PER_TIER = 0.03F;

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(ArmoryItemProperties::register));
        modBus.addListener(EntityRenderersEvent.AddLayers.class, TwinWeaponRender::addLayers);
        modBus.addListener(RegisterClientExtensionsEvent.class, ArmoryClient::registerExtensions);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, WeaponAmbience::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, TwinWeaponRender::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ComputeFovModifierEvent.class, ArmoryClient::onFov);
    }

    /** Olho de Falcão visual: o Arco Longo aproxima mais que o arco comum enquanto puxa. */
    private static void onFov(ComputeFovModifierEvent event) {
        Player p = event.getPlayer();
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof ArmoryBowItem bow)) return;
        float f = p.getTicksUsingItem() / (float) bow.drawTicks();
        f = f > 1F ? 1F : f * f;
        event.setNewFovModifier(event.getNewFovModifier() * (1F - f * (LONGBOW_ZOOM + LONGBOW_ZOOM_PER_TIER * bow.tier())));
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        Item[] lutes = ArmoryItems.WEAPONS.get(WeaponKind.LUTE).stream().map(DeferredItem::get).toArray(Item[]::new);
        event.registerItem(new LuteExtensions(), lutes);
    }

    /** Alaúde: dois braços segurando o instrumento enquanto toca, e um leve balanço do dedilhado em 1ª pessoa. */
    static final class LuteExtensions implements IClientItemExtensions {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return entity.isUsingItem() && entity.getUseItem() == stack ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equip, float swing) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            float t = player.getTicksUsingItem() + partialTick;
            pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
            // Traz o alaúde para o meio da tela e balança no ritmo das notas (uma a cada 5 ticks)
            pose.translate(i * -0.22F, 0.1F + Mth.sin(t * (float) Math.PI * 0.4F) * 0.012F, 0.06F);
            pose.mulPose(Axis.ZP.rotationDegrees(i * Mth.sin(t * 0.2F) * 2.5F));
            return true;
        }
    }
}
