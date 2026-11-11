package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Camada de HUD do Isekai Mahou (substitui o HUD básico do núcleo):
 * card (com o lugar atual e o selo Procurado logo abaixo), barra do recurso vertical com o Limite treinável (com as
 * condições de status do jogador ao lado), barra numérica de VIDA (canto de baixo à esquerda), cabeçalho "Loadout N/9"
 * com o seletor dos 9 loadouts, slots Z X C G e barra de conjuração. Tudo segue o {@code HudLayout} do núcleo (nada
 * por cima do minimapa). O Olho Analítico e os banners ficam na {@link MahouTopLayer}, registrada logo acima desta.
 */
public class MahouHudLayer implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        // Limites do card neste quadro (a camada do topo desvia dele); zero = card não desenhado
        HudCard.right = HudCard.bottom = 0;
        // F1 esconde tudo; espectador não conjura
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        // Com uma tela aberta (inventário, menu B, Grimório...) o HUD sai do caminho; o chat é a exceção
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;

        MagicData d = ClientMagic.data();
        LoadoutCache.refresh(d, mc.player.getAbilities().instabuild);
        HudState.updateMana(Math.max(0F, Math.min(ClientMagic.mana, ClientMagic.maxMana)));
        float pt = dt.getGameTimeDeltaPartialTick(true);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Com o F3 aberto o canto esquerdo é do texto de depuração
        if (!mc.getDebugOverlay().showDebugScreen()) {
            HudCard.place();
            HudCard.render(g, mc, d);
            HudPlace.render(g, mc);
            HudManaBar.render(g, mc, d);
            HudPlayerStatuses.render(g, mc);
            HudHealthBar.render(g, mc);
        }
        HudSpellBar.render(g, mc, d);
        HudCastBar.render(g, mc, pt);
        RenderSystem.disableBlend();
    }
}
