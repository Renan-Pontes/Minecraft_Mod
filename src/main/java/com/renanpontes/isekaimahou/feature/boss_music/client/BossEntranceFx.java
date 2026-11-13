package com.renanpontes.isekaimahou.feature.boss_music.client;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * SÓ CLIENTE. A "cena" da entrada do chefe, no ritmo da música: faixas de cinema entram, a tela escurece aos poucos com o
 * epíteto do chefe na faixa de baixo durante a intro; no drop (quando ele acorda) o NOME aparece gigante e encolhe até o
 * lugar, segura ~3,5 s e tudo sai. O tremor, o clarão e o rugido vêm do servidor.
 */
final class BossEntranceFx {
    private static final int BAR_IN = 14;
    private static final int TITLE_HOLD = 90;
    private static final int FADE = 20;

    private static boolean active;
    private static int ticks;
    private static int awaken;
    private static int color;
    @Nullable private static Component name;
    @Nullable private static Component subtitle;

    static void start(LivingEntity boss, String theme, int awakenTicks) {
        active = true;
        ticks = 0;
        awaken = Math.max(1, awakenTicks);
        color = colorOf(theme);
        name = boss.getDisplayName().copy().withStyle(ChatFormatting.BOLD);
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        String key = "boss_entrance." + id.getNamespace() + "." + id.getPath();
        subtitle = I18n.exists(key) ? Component.translatable(key) : null;
    }

    static void reset() {
        active = false;
    }

    static void tick() {
        if (!active) return;
        ticks++;
        if (ticks > awaken + TITLE_HOLD + FADE + BAR_IN + 2) active = false;
    }

    static void render(GuiGraphics g, float partial) {
        if (!active || Minecraft.getInstance().options.hideGui) return;
        // por cima de TODO o HUD (as camadas do HUD sobem no eixo Z): é uma cena de cinema
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        draw(g, partial);
        g.pose().popPose();
        g.flush();
    }

    private static void draw(GuiGraphics g, float partial) {
        Font font = Minecraft.getInstance().font;
        float t = ticks + partial;
        int w = g.guiWidth(), h = g.guiHeight();
        float end = awaken + TITLE_HOLD + FADE * 0.5F;

        // escurece devagar durante a intro; some depressa quando ele acorda
        float dark = t < awaken ? Mth.clamp(t / awaken, 0F, 1F) * 0.45F : Math.max(0F, 0.45F - (t - awaken) / 15F);
        if (dark > 0.01F) {
            int edge = h / 3;
            int a = (int) (dark * 255);
            g.fillGradient(0, 0, w, edge, (a << 24), 0);
            g.fillGradient(0, h - edge, w, h, 0, (a << 24));
            g.fill(0, 0, w, h, ((int) (dark * 120)) << 24);
        }

        // faixas de cinema
        float in = ease(Mth.clamp(t / BAR_IN, 0F, 1F));
        float out = t > end ? Mth.clamp((t - end) / BAR_IN, 0F, 1F) : 0F;
        int bh = (int) (h * 0.11F * in * (1F - ease(out)));
        if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }

        // intro: o epíteto na faixa de baixo
        if (subtitle != null && t < awaken && bh > 10) {
            float a = Math.min(Mth.clamp((t - 8) / 20F, 0F, 1F), Mth.clamp((awaken - t) / 8F, 0F, 1F));
            text(g, font, subtitle.copy().withStyle(ChatFormatting.ITALIC), w / 2F, h - bh / 2F - 4, 1F, dim(color), a);
        }

        // drop: o nome gigante
        if (t >= awaken && name != null) {
            float k = t - awaken;
            float a = k < TITLE_HOLD ? 1F : 1F - Mth.clamp((k - TITLE_HOLD) / FADE, 0F, 1F);
            if (a > 0.04F) {
                float s = 4.2F - 1.8F * ease(Mth.clamp(k / 7F, 0F, 1F));
                float cy = h * 0.36F;
                int lineW = (int) (Math.min(w * 0.42F, font.width(name) * s * 0.75F) * ease(Mth.clamp(k / 12F, 0F, 1F)));
                int la = (int) (a * 200);
                g.fill((int) (w / 2F - lineW), (int) (cy + 7 * s / 2.4F + 4), (int) (w / 2F + lineW), (int) (cy + 7 * s / 2.4F + 5),
                        (la << 24) | (color & 0xFFFFFF));
                text(g, font, name, w / 2F, cy - 4 * s, s, color, a);
                if (subtitle != null) {
                    float sa = a * Mth.clamp((k - 6) / 10F, 0F, 1F);
                    text(g, font, subtitle, w / 2F, cy + 7 * s / 2.4F + 10, 1.15F, 0xE0E0E0, sa);
                }
            }
        }
    }

    private static void text(GuiGraphics g, Font font, Component c, float cx, float y, float scale, int rgb, float alpha) {
        if (alpha < 0.04F) return; // alfa ~0 a fonte trata como opaco
        int col = ((int) (Mth.clamp(alpha, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, c, -font.width(c) / 2, 0, col, true);
        g.pose().popPose();
    }

    private static float ease(float x) {
        float u = 1F - x;
        return 1F - u * u * u;
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 4 + 40, gg = (rgb >> 8 & 255) * 3 / 4 + 40, b = (rgb & 255) * 3 / 4 + 40;
        return Math.min(255, r) << 16 | Math.min(255, gg) << 8 | Math.min(255, b);
    }

    private static int colorOf(String theme) {
        return switch (theme) {
            case "ulquiorra", "ulquiorra_segunda_etapa" -> 0x3FD18A;
            case "barragan" -> 0xD9B870;
            case "grimmjow" -> 0x4FB4FF;
            case "hollow_king" -> 0xF0F0F0;
            case "abyss_sovereign" -> 0xA85CFF;
            case "seraph" -> 0xFFE680;
            case "twilight_herald" -> 0xFF8A4A;
            case "primordial_dragon" -> 0xFF4A3A;
            case "lich" -> 0x7FFFD4;
            case "beast" -> 0xE08A3C;
            case "mimic_king" -> 0xFFC94A;
            case "elemental_guardian" -> 0x5AC8FF;
            default -> 0xFF5555;
        };
    }

    private BossEntranceFx() {}
}
