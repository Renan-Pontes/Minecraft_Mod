package com.renanpontes.isekaimahou.feature.home_realm.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.menu.ContainerTab;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Parte de cliente do módulo "home_realm": a tecla "Ir para o Lar / Voltar" (padrão: tecla Home) e a seção "Lar" dentro
 * do Guia de Viagem do menu B — que só aparece para quem tem um lar ou foi convidado para um.
 */
public class HomeRealmClient implements ClientModule {
    /** Id da aba (o ícone é {@code textures/gui/tab/home_realm.png}). */
    public static final String TAB = "home_realm";

    public static final KeyMapping KEY = new KeyMapping("key.isekai_mahou.home_realm", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CoreKeys.CATEGORY);

    @Override
    public void initClient(IEventBus modBus) {
        HomeNetwork.clientInfo = HomeClientState::accept;
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        MenuTabs.register(TAB, 90, Component.translatable("tab.isekai_mahou.home_realm"), HomeTab::new);
        // "Não lote o menu": vira uma seção do Guia de Viagem, ao lado de Lugares
        MenuTabs.setParent(TAB, ContainerTab.GUIDE);
        MenuTabs.setVisibility(TAB, HomeClientState::relevant);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (KEY.consumeClick()) {
                // O servidor decide (tem lar? está em combate?) e responde com um aviso curto se não der
                if (mc.screen == null) PacketDistributor.sendToServer(new HomeNetwork.Action(HomeNetwork.Action.TOGGLE));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> HomeClientState.clear());
    }
}
