package com.renanpontes.isekaimahou.feature.legends_h.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Efeitos de tela do legends_h (só cliente), sempre leves e sem travar o controle:
 * <ul>
 *   <li>King Crimson em você: bordas vermelho-escuras e o mundo levemente tingido enquanto você está fora do tempo.
 *       Os outros jogadores simplesmente não te veem (o desenho do seu personagem é pulado).</li>
 *   <li>Seu tempo foi apagado: véu vermelho-roxo pulsando.</li>
 *   <li>O tempo pulou perto de você: um clarão vermelho rápido (corte de cena).</li>
 *   <li>Bites the Dust: sépia e um relógio no alto girando ao contrário.</li>
 * </ul>
 */
public final class LhScreenFx {
    /** Mostrador do relógio (o mesmo do Parar o Tempo, do legends_d). */
    private static final ResourceLocation CLOCK = IsekaiMahou.id("textures/entity/legends_d/clock_face.png");

    private static final Map<Integer, Long> PHASED = new HashMap<>();
    private static long ticks;
    private static long erasedEnd, erasedLen = 1;
    private static long skipEnd, skipLen = 1;
    private static long rewindEnd, rewindLen = 1;

    public static void onScreen(LhNet.Screen p) {
        long end = ticks + p.ticks();
        switch (p.kind()) {
            case LhNet.PHASE -> {
                if (p.ticks() <= 0) PHASED.remove(p.entity());
                else PHASED.put(p.entity(), end);
            }
            case LhNet.ERASED -> {
                erasedEnd = end;
                erasedLen = Math.max(1, p.ticks());
            }
            case LhNet.SKIP -> {
                skipEnd = end;
                skipLen = Math.max(1, p.ticks());
            }
            case LhNet.REWIND -> {
                rewindEnd = end;
                rewindLen = Math.max(1, p.ticks());
            }
            default -> {}
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        ticks++;
        if (!PHASED.isEmpty() && ticks % 20 == 0) PHASED.values().removeIf(t -> t < ticks);
    }

    /** Quem está fora do tempo não é desenhado para os outros. */
    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        if (PHASED.isEmpty()) return;
        Long end = PHASED.get(e.getEntity().getId());
        if (end != null && end >= ticks && e.getEntity() != Minecraft.getInstance().player) e.setCanceled(true);
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        PHASED.clear();
        erasedEnd = skipEnd = rewindEnd = 0;
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        float now = ticks + pt;
        int w = g.guiWidth(), h = g.guiHeight();

        Long phase = PHASED.get(mc.player.getId());
        if (phase != null && phase >= ticks) {
            float pulse = 0.85F + 0.15F * Mth.sin(now * 0.3F);
            g.fill(0, 0, w, h, argb(0x5A0018, 0.16F * pulse));
            vignette(g, w, h, 0x8A0A2A, 0.55F * pulse);
        }
        if (erasedEnd > ticks) {
            float left = (erasedEnd - now) / erasedLen;
            float pulse = 0.75F + 0.25F * Mth.sin(now * 0.5F);
            g.fill(0, 0, w, h, argb(0x6A1050, 0.22F * pulse * Mth.clamp(left * 4, 0, 1)));
            vignette(g, w, h, 0xB01040, 0.5F * pulse);
        }
        if (skipEnd > ticks) {
            float f = Mth.clamp((skipEnd - now) / skipLen, 0F, 1F);
            g.fill(0, 0, w, h, argb(0xFF2050, 0.45F * f * f));
        }
        if (rewindEnd > ticks) {
            float f = Mth.clamp((rewindEnd - now) / rewindLen, 0F, 1F);
            g.fill(0, 0, w, h, argb(0x7A4A20, 0.32F * f));
            vignette(g, w, h, 0xFF7AC8, 0.4F * f);
            // "Fita voltando": faixas claras correndo de baixo para cima
            for (int i = 0; i < 3; i++) {
                int yy = h - (int) ((now * 9F + i * (h / 3F)) % (h + 20F)) + 10;
                g.fill(0, yy, w, yy + 2, argb(0xFFE0F0, 0.16F * f));
            }
            if (!mc.options.hideGui) clock(g, w / 2, 44, now, f);
        }
    }

    /** Relógio com os ponteiros girando ao CONTRÁRIO. */
    private static void clock(GuiGraphics g, int cx, int cy, float now, float alpha) {
        RenderSystem.enableBlend();
        g.setColor(1F, 0.85F, 0.95F, 0.9F * alpha);
        g.blit(CLOCK, cx - 20, cy - 20, 40, 40, 0, 0, 128, 128, 128, 128);
        g.setColor(1F, 1F, 1F, 1F);
        hand(g, cx, cy, -now * 36F, 15, argb(0xFFF2F8, alpha));
        hand(g, cx, cy, -now * 6F, 10, argb(0xFF7AC8, alpha));
        RenderSystem.disableBlend();
    }

    private static void hand(GuiGraphics g, int cx, int cy, float deg, int len, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
        g.fill(-1, -len, 1, 1, argb);
        g.pose().popPose();
    }

    /** Bordas escuras (quatro gradientes). */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float a) {
        int c = argb(rgb, a), z = argb(rgb, 0F);
        int bh = h / 5, bw = w / 6;
        g.fillGradient(0, 0, w, bh, c, z);
        g.fillGradient(0, h - bh, w, h, z, c);
        // laterais: gradiente vertical girado
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

    private LhScreenFx() {}
}
