package com.renanpontes.isekaimahou.feature.legends_k.client;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_k.LkNet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Efeitos de tela do legends_k (só cliente, só para quem está no efeito), leves e sem travar o controle:
 * <ul>
 *   <li>Dimensão do Kamui: a tela fica azulada, com bordas azul-escuras pulsando devagar.</li>
 *   <li>Entre Dimensões (D4C): o mesmo, num azul-claro.</li>
 *   <li>Retorno pela Morte: a tela apaga em preto e volta (2 s).</li>
 * </ul>
 * Nada de texto: o aviso curto vem na barra de ação pelo servidor.
 */
public final class LkScreenFx {
    private static long ticks;
    private static long phaseEnd;
    private static byte phaseStyle = LkNet.KAMUI;
    private static long rbdEnd, rbdLen = 1;

    public static void onScreen(LkNet.Screen p) {
        long end = ticks + p.ticks();
        switch (p.kind()) {
            case LkNet.KAMUI, LkNet.D4C -> {
                phaseStyle = p.kind();
                phaseEnd = p.ticks() <= 0 ? 0 : end;
            }
            case LkNet.RBD -> {
                rbdEnd = end;
                rbdLen = Math.max(1, p.ticks());
            }
            default -> {}
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        ticks++;
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        phaseEnd = 0;
        rbdEnd = 0;
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        if (phaseEnd <= ticks && rbdEnd <= ticks) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float now = ticks + delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth(), h = g.guiHeight();
        if (phaseEnd > ticks) {
            boolean kamui = phaseStyle == LkNet.KAMUI;
            int tint = kamui ? 0x1A3AC8 : 0x5AB4F0;
            int edge = kamui ? 0x0A1460 : 0x2A70B8;
            float pulse = 0.88F + 0.12F * Mth.sin(now * 0.12F);
            // entrando/saindo: some suave nos últimos 10 ticks
            float fade = Mth.clamp((phaseEnd - now) / 10F, 0F, 1F);
            g.fill(0, 0, w, h, argb(tint, 0.20F * pulse * fade));
            vignette(g, w, h, edge, 0.5F * pulse * fade);
        }
        if (rbdEnd > ticks) {
            float f = Mth.clamp((rbdEnd - now) / rbdLen, 0F, 1F);
            // preto total no começo, clareando
            g.fill(0, 0, w, h, argb(0x000000, Math.min(1F, f * 1.6F)));
        }
    }

    /** Bordas escuras (quatro gradientes). */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float a) {
        int c = argb(rgb, a), z = argb(rgb, 0F);
        int bh = h / 5, bw = w / 6;
        g.fillGradient(0, 0, w, bh, c, z);
        g.fillGradient(0, h - bh, w, h, z, c);
        g.pose().pushPose();
        g.pose().translate(0, h, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
        g.fillGradient(0, 0, h, bw, c, z);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w, 0, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        g.fillGradient(0, 0, h, bw, c, z);
        g.pose().popPose();
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private LkScreenFx() {}
}
