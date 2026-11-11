package com.renanpontes.isekaimahou.client.onboard;

import javax.annotation.Nullable;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Letreiro "Parabéns!" da entrada (~4 s, depois da classe e da raça): véu escuro, raios girando na cor da classe atrás do ícone,
 * "PARABÉNS!" dourado, o nome da classe grande na cor, categoria e recurso (na cor do recurso) e o estilo da classe.
 * Fanfarra ao começar. Não é uma tela: o jogador continua com o controle.
 */
final class CongratsOverlay {
    private static final int LIFE = 80;
    private static final int IN = 10, OUT = 14;

    @Nullable
    private static OnboardPayloads.Congrats data;
    private static int age;

    static void show(OnboardPayloads.Congrats p) {
        data = p;
        age = 0;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F));
    }

    static void tick() {
        if (data == null) return;
        if (++age >= LIFE) data = null;
        else if (age == 12) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F));
        }
    }

    static boolean active() {
        return data != null;
    }

    static void render(GuiGraphics g, DeltaTracker dt) {
        OnboardPayloads.Congrats d = data;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.options.hideGui) return;
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        float alpha = Math.min(Mth.clamp(t / IN, 0F, 1F), Mth.clamp((LIFE - t) / OUT, 0F, 1F));
        if (alpha <= 0.05F) return; // texto com alfa < 4 o Font desenha opaco
        Font font = mc.font;
        int gw = g.guiWidth(), gh = g.guiHeight();
        int cx = gw / 2;
        int cy = gh / 2 - 22;
        int rgb = 0xFF000000 | (d.rgb() & 0xFFFFFF);
        int al = (int) (alpha * 255);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // Véu: mais escuro no meio, some nas bordas de cima/baixo
        int veil = (int) (alpha * 0x90);
        g.fillGradient(0, cy - 70, gw, cy, MahouUi.alpha(0xFF000000, 0), MahouUi.alpha(0xFF0A0614, veil));
        g.fillGradient(0, cy, gw, cy + 90 + d.extras().size() * 12, MahouUi.alpha(0xFF0A0614, veil), MahouUi.alpha(0xFF000000, 0));

        // Raios girando atrás do ícone
        g.pose().pushPose();
        g.pose().translate(cx, cy - 26, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(t * 1.5F));
        int rays = 12;
        for (int i = 0; i < rays; i++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(i * 360F / rays));
            int len = 34 + (i % 2) * 10;
            g.fillGradient(-2, -len, 2, 0, MahouUi.alpha(rgb, 0), MahouUi.alpha(rgb, (int) (alpha * 0x70)));
            g.pose().popPose();
        }
        g.pose().popPose();

        // Ícone da classe (pulando de leve)
        float pop = easeBack(Mth.clamp(t / 14F, 0F, 1F));
        float bob = Mth.sin(t * 0.15F) * 1.5F;
        g.pose().pushPose();
        g.pose().translate(cx, cy - 26 + bob, 0);
        g.pose().scale(2.4F * pop, 2.4F * pop, 1F);
        g.renderItem(d.icon(), -8, -8);
        g.pose().popPose();

        // "PARABÉNS!"
        Component title = Component.translatable("onboard.isekai_mahou.congrats").withStyle(ChatFormatting.BOLD);
        float ts = 1.6F + 0.4F * (1F - Mth.clamp(t / 8F, 0F, 1F));
        drawCentered(g, font, title, cx, cy + 4, ts, MahouUi.alpha(MahouUi.GOLD, al));
        int lineW = (int) (font.width(title) * ts / 2) + 10;
        g.fill(cx - lineW - 40, cy + 9, cx - lineW, cy + 10, MahouUi.alpha(MahouUi.GOLD_DIM, al));
        g.fill(cx + lineW, cy + 9, cx + lineW + 40, cy + 10, MahouUi.alpha(MahouUi.GOLD_DIM, al));

        // "Sua classe agora é" + nome da classe
        float nameIn = Mth.clamp((t - 6F) / 10F, 0F, 1F);
        if (nameIn > 0.05F) {
            Component name = d.className().copy().withColor(rgb & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
            drawCentered(g, font, Component.translatable("onboard.isekai_mahou.you_are"), cx, cy + 17, 0.8F,
                    MahouUi.alpha(MahouUi.TEXT_DIM, (int) (al * nameIn)));
            drawCentered(g, font, name, cx, cy + 25, 2.0F * easeBack(nameIn), MahouUi.alpha(MahouUi.TEXT, (int) (al * nameIn)));
        }

        // Categoria · Recurso, e o estilo
        float infoIn = Mth.clamp((t - 14F) / 10F, 0F, 1F);
        if (infoIn > 0.05F) {
            int ia = (int) (al * infoIn);
            Component info = Component.translatable("onboard.isekai_mahou.class_info", d.category().copy().withStyle(ChatFormatting.WHITE),
                    d.resource().copy().withColor(d.resourceRgb() & 0xFFFFFF));
            drawCentered(g, font, info, cx, cy + 48, 1F, MahouUi.alpha(MahouUi.TEXT_DIM, ia));
            if (!d.style().getString().isEmpty() && !d.style().getString().startsWith("path.isekai_mahou.")) {
                drawCentered(g, font, d.style().copy().withStyle(ChatFormatting.ITALIC), cx, cy + 60, 0.85F,
                        MahouUi.alpha(MahouUi.TEXT_DIM, (int) (ia * 0.85F)));
            }
        }

        // Linhas extras da entrada (v1.0: a raça escolhida etc.), entrando uma depois da outra
        for (int i = 0; i < d.extras().size(); i++) {
            float in = Mth.clamp((t - 20F - i * 5F) / 10F, 0F, 1F);
            if (in <= 0.05F) continue;
            int la = (int) (al * in);
            int ly = cy + 74 + i * 12;
            Component line = d.extras().get(i);
            int half = (int) (font.width(line) * 0.95F / 2) + 8;
            MahouUi.diamond(g, cx - half, ly + 4, 2, MahouUi.alpha(MahouUi.GOLD, la));
            MahouUi.diamond(g, cx + half, ly + 4, 2, MahouUi.alpha(MahouUi.GOLD, la));
            drawCentered(g, font, line, cx, ly, 0.95F, MahouUi.alpha(MahouUi.TEXT, la));
        }
        g.pose().popPose();
    }

    private static void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }

    /** Ultrapassa um pouco e volta (efeito de "pop"). */
    private static float easeBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1F;
        return 1F + c3 * (float) Math.pow(x - 1F, 3) + c1 * (float) Math.pow(x - 1F, 2);
    }

    static void clear() {
        data = null;
        age = 0;
    }

    private CongratsOverlay() {}
}
