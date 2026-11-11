package com.renanpontes.isekaimahou.feature.class_rogue.client;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueContent;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueGear;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueNet;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueArmorItem;
import com.renanpontes.isekaimahou.feature.class_rogue.item.ThiefLedgerItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/**
 * Parte de cliente do Ladino: renderer 3D (GeckoLib) do Traje das Sombras, a faca voadora, a cópia das páginas do
 * Grimório Roubado e a aba "Ladino" do menu B (ordem 16, logo depois das Provações).
 */
public class ClassRogueClient implements ClientModule {
    /** Um renderer por grau (todos os jogadores com o mesmo grau compartilham). */
    private static final Map<RogueGear, RogueArmorRenderer> RENDERERS = new EnumMap<>(RogueGear.class);

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(ClassRogueClient::renderers);
        RogueArmorItem.renderFactory = item -> new GeoRenderProvider() {
            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                    @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
                return RENDERERS.computeIfAbsent(item.gear(), RogueArmorRenderer::new);
            }
        };
        RogueNet.clientSync = s -> RogueNet.clientView = s;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) ->
                RogueNet.clientView = new RogueNet.Sync(java.util.List.of(), 0));
        ThiefLedgerItem.openTab = () -> StatusScreen.open("rogue");
        MenuTabs.register("rogue", 16, Component.translatable("screen.isekai_mahou.class_rogue.tab"), RogueTab::new);
        MenuTabs.setIcon("rogue", IsekaiMahou.id("textures/gui/tab/rogue.png"));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(RogueContent.KNIFE.get(), RogueKnifeRenderer::new);
        e.registerEntityRenderer(RogueContent.AFTERIMAGE.get(), RogueAfterimageRenderer::new);
    }
}
