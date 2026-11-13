package com.renanpontes.isekaimahou.feature.familiars.client;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarContent;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Parte de cliente do módulo "familiars": o renderer do familiar, a aba "Familiar" do menu B (só aparece depois de
 * despertar — não lota o menu de quem ainda não tem) e uma tecla opcional de chamar/guardar (sem tecla padrão: já há
 * teclas demais; a aba faz o mesmo).
 */
public class FamiliarsClient implements ClientModule {
    /** Id da aba (o ícone é {@code textures/gui/tab/familiar.png}). */
    public static final String TAB = "familiar";
    private static final KeyMapping CALL = new KeyMapping("key.isekai_mahou.familiar_call", GLFW.GLFW_KEY_UNKNOWN, CoreKeys.CATEGORY);

    @Override
    public void initClient(IEventBus modBus) {
        FamiliarNetwork.clientState = s -> ClientFamiliar.state = s;
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(FamiliarContent.FAMILIAR.get(), FamiliarRenderer::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(CALL));
        MenuTabs.register(TAB, 8, Component.translatable("screen.isekai_mahou.familiars.tab"), FamiliarTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.CHARACTER);
        MenuTabs.setVisibility(TAB, ClientFamiliar::awakened);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientFamiliar.state = FamiliarNetwork.State.EMPTY);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (CALL.consumeClick()) {
                if (Minecraft.getInstance().player != null && ClientFamiliar.awakened()) {
                    PacketDistributor.sendToServer(new FamiliarNetwork.Action(FamiliarNetwork.Action.TOGGLE_SUMMON, ""));
                }
            }
        });
    }
}
