package com.renanpontes.isekaimahou.client.ux;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** v1.6 — cliente do {@link PeaceMode}: tecla (padrão N), selo "Modo Paz" ao lado da barra de itens e trava da esquiva. */
public final class PeaceClient {
    public static final KeyMapping KEY = new KeyMapping("key.isekai_mahou.peace_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CoreKeys.CATEGORY);
    private static boolean on;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR, IsekaiMahou.id("core_peace"), PeaceClient::render));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (KEY.consumeClick()) {
                if (mc.screen == null) PacketDistributor.sendToServer(new PeaceMode.Toggle());
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> on = false);
    }

    /** Recebido do servidor. */
    public static void apply(boolean state) {
        on = state;
    }

    /** O jogador local está no Modo Paz? (a esquiva não sai) */
    public static boolean isOn() {
        return on;
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        Component text = Component.translatable("hud.isekai_mahou.peace", KEY.getTranslatedKeyMessage());
        // À ESQUERDA da barra de itens (a direita é da barra de magias); folga para o espaço da mão secundária
        int w = mc.font.width(text);
        int x = g.guiWidth() / 2 - 91 - 34 - w;
        int y = g.guiHeight() - 15;
        g.fill(x - 4, y - 4, x + w + 4, y + 11, 0xE0182A22);
        g.fill(x - 4, y - 4, x - 2, y + 11, 0xFF8FE3B0);
        g.drawString(mc.font, text, x, y, 0xFFBFF5D2, true);
    }

    private PeaceClient() {}
}
