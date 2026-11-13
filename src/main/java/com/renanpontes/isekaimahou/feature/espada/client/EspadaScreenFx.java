package com.renanpontes.isekaimahou.feature.espada.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * SÓ CLIENTE. Efeitos de tela dos Espada:
 * <ul>
 *   <li><b>Envelhecido</b>: a tela fica sépia/cinza com vinheta marrom (mais forte por acúmulo), "visão cansada" que
 *       pulsa (borrão simulado por camadas escuras) e um tremor leve de câmera (mão trêmula);</li>
 *   <li><b>escuridão</b> da Segunda Etapa (cai devagar quando Ulquiorra libera e fica enquanto ele estiver perto);</li>
 *   <li><b>clarão</b> das explosões gigantes (Lanza del Relámpago).</li>
 * </ul>
 */
public final class EspadaScreenFx {
    private static final ResourceLocation VIGNETTE = IsekaiMahou.id("textures/misc/espada_vignette.png");

    /** Escuridão: alvo (mantido pelo chefe perto) e pico temporário (pacote). */
    private static float darkTarget;
    private static int darkHold;
    private static float dark, darkPrev;
    private static float darkPulse;
    /** Clarão. */
    private static float flash, flashPrev;
    private static int flashColor = 0xFFFFFF;
    /** Envelhecido suavizado (0..10). */
    private static float aged, agedPrev;
    private static long ticks;

    /** Pedido do chefe perto (Segunda Etapa): mantém a escuridão por alguns ticks. */
    static void keepDark(float level) {
        darkTarget = Math.max(darkTarget, level);
        darkHold = 12;
    }

    /** Escuridão que "cai" (pico) por {@code ticks}. */
    static void darkFall(int ticks) {
        darkPulse = Math.max(darkPulse, Math.min(200, ticks));
    }

    static void flash(int rgb, float strength) {
        flash = Math.max(flash, Mth.clamp(strength, 0F, 1F));
        flashColor = rgb;
    }

    static void tick() {
        ticks++;
        darkPrev = dark;
        flashPrev = flash;
        agedPrev = aged;
        if (darkHold > 0) darkHold--;
        else darkTarget = 0F;
        float want = darkTarget;
        if (darkPulse > 0) {
            darkPulse--;
            want = Math.max(want, 0.85F);
        }
        dark += (want - dark) * (want > dark ? 0.06F : 0.03F);
        flash *= 0.82F;
        if (flash < 0.01F) flash = 0F;
        Player p = Minecraft.getInstance().player;
        int stacks = 0;
        if (p != null) {
            MobEffectInstance inst = p.getEffect(EspadaRegistries.AGED);
            if (inst != null) stacks = inst.getAmplifier() + 1;
        }
        aged += (stacks - aged) * 0.05F;
        if (Math.abs(aged - stacks) < 0.01F) aged = stacks;
    }

    static void register(RegisterGuiLayersEvent e) {
        e.registerBelowAll(IsekaiMahou.id("espada_screen"), EspadaScreenFx::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui && dark <= 0 && aged <= 0) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float d = Mth.lerp(pt, darkPrev, dark);
        float a = Mth.lerp(pt, agedPrev, aged) / 10F;
        float f = Mth.lerp(pt, flashPrev, flash);
        if (d <= 0.002F && a <= 0.002F && f <= 0.002F) return;
        int w = g.guiWidth(), h = g.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        if (a > 0) {
            // Sépia + cinza: o mundo "desbota".
            g.fill(0, 0, w, h, argb(0.10F + 0.32F * a, 0x8A7550));
            g.fill(0, 0, w, h, argb(0.18F * a, 0x707070));
            // Visão cansada: escurece e clareia devagar (simula o borrão).
            float tired = 0.5F + 0.5F * Mth.sin((ticks + pt) * 0.07F);
            vignette(g, w, h, 0x2A1A0A, Math.min(0.95F, 0.25F + 0.6F * a + 0.15F * a * tired));
            if (FxQuality.atLeast(Graphics.HIGH) && a > 0.4F) g.fill(0, 0, w, h, argb(0.08F * a * tired, 0x1A1208));
        }
        if (d > 0) {
            g.fill(0, 0, w, h, argb(d * 0.45F, 0x020604));
            vignette(g, w, h, 0x000000, Math.min(1F, d * 1.1F));
        }
        if (f > 0) g.fill(0, 0, w, h, argb(f * 0.75F, flashColor));
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, Mth.clamp(alpha, 0F, 1F));
        g.blit(VIGNETTE, 0, 0, w, h, 0F, 0F, 256, 256, 256, 256);
        g.setColor(1F, 1F, 1F, 1F);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Mão trêmula do Envelhecido (roll/pitch pequenos). */
    static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float a = aged / 10F;
        if (a <= 0.01F) return;
        float t = (float) (ticks + e.getPartialTick());
        float amp = 0.25F + 1.1F * a;
        e.setRoll(e.getRoll() + Mth.sin(t * 0.21F) * amp * 0.6F);
        e.setPitch(e.getPitch() + Mth.sin(t * 0.37F + 1.3F) * amp * 0.25F);
        e.setYaw(e.getYaw() + Mth.sin(t * 0.29F + 0.7F) * amp * 0.2F);
    }

    static void reset() {
        dark = darkPrev = darkTarget = darkPulse = 0;
        flash = flashPrev = 0;
    }

    private EspadaScreenFx() {}
}
