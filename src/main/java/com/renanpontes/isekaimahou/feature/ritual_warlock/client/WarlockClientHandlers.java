package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import com.renanpontes.isekaimahou.feature.ritual_warlock.network.WarlockPayloads;

import net.minecraft.client.Minecraft;

/** Recebe os pacotes do servidor no cliente (chamado só de dentro dos handlers registrados no módulo). */
public final class WarlockClientHandlers {

    public static void openDialog(WarlockPayloads.PatronDialog payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new PatronDialogScreen(payload));
    }

    public static void hud(WarlockPayloads.PactHud payload) {
        PactHudOverlay.update(payload);
    }

    private WarlockClientHandlers() {}
}
