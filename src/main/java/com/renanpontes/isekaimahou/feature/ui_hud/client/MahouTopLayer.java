package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Camada do topo central (acima do HUD do mod, abaixo de títulos e chat): Olho Analítico logo abaixo das barras de
 * chefe e, abaixo dele, o banner de nível/Círculo. Funciona também como espectador (jogadores "Abatidos" na Provação).
 * <p>
 * v0.9: posição e tamanho FIXOS ({@link TopSpace#eye}). v1.1: sempre logo abaixo das barras de chefe (as dicas de
 * criaturas do Jade são desligadas uma vez pelo {@link JadeCompat}; as de blocos continuam).
 */
public class MahouTopLayer implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int gw = g.guiWidth(), gh = g.guiHeight();
        if (TopSpace.eyeEnabled()) TargetEye.render(g, mc, TopSpace.eye(gw, gh));
        HudBanner.render(g, mc, TopSpace.bannerTop(gw, gh));
        RenderSystem.disableBlend();
    }
}
