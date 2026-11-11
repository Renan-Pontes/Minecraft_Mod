package com.renanpontes.isekaimahou.feature.character.client;

import javax.annotation.Nullable;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;

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
 * Letreiro dramático de NÍVEL DE RAÇA (v1.0.1; ~5,5 s): véu escuro com pulso na cor da raça, círculo rúnico girando,
 * o emblema crescendo, "O SANGUE DESPERTA" (ou "TRANSFORMAÇÃO DESPERTOU" no nível V), "RAÇA II" / o nome da forma
 * surgindo letra a letra e o que mudou. Não é uma tela (o jogador continua jogando).
 */
final class UnlockOverlay {
    private static final int LIFE = 110, IN = 12, OUT = 18;
    @Nullable
    private static Race race;
    private static int level;
    private static int age;

    static void show(Race r, int lv) {
        race = r;
        level = lv;
        age = 0;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.0F));
    }

    static void tick() {
        if (race == null) return;
        age++;
        if (age == 20 || age == 34) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, age == 20 ? 0.9F : 1.3F, 1.0F));
        }
        if (age >= LIFE) race = null;
    }

    static void clear() {
        race = null;
    }

    static void render(GuiGraphics g, DeltaTracker dt) {
        Race r = race;
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.options.hideGui) return;
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        float alpha = Math.min(Mth.clamp(t / IN, 0F, 1F), Mth.clamp((LIFE - t) / OUT, 0F, 1F));
        if (alpha <= 0.05F) return;
        Font font = mc.font;
        int gw = g.guiWidth(), gh = g.guiHeight();
        int cx = gw / 2, cy = gh / 2 - 30;
        int rgb = r.argb();
        int al = (int) (alpha * 255);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // Véu com pulso na cor da raça
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F);
        g.fill(0, 0, gw, gh, MahouUi.alpha(0xFF05030A, (int) (alpha * 0x70)));
        g.fillGradient(0, cy - 90, gw, cy + 10, MahouUi.alpha(rgb, 0), MahouUi.alpha(MahouUi.darken(rgb, 0.6F), (int) (alpha * (0x40 + 0x30 * pulse))));
        g.fillGradient(0, cy + 10, gw, cy + 110, MahouUi.alpha(MahouUi.darken(rgb, 0.6F), (int) (alpha * (0x40 + 0x30 * pulse))), MahouUi.alpha(rgb, 0));

        // Círculo rúnico: dois anéis de losangos girando em sentidos opostos
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? 16 : 10;
            float rad = ring == 0 ? 40F : 28F;
            float rot = (ring == 0 ? 1 : -1.6F) * t * 1.2F;
            for (int i = 0; i < n; i++) {
                float ang = (float) Math.toRadians(rot + i * 360F / n);
                int px = cx + Math.round(Mth.cos(ang) * rad), py = cy + Math.round(Mth.sin(ang) * rad);
                MahouUi.diamond(g, px, py, ring == 0 ? 2 : 1, MahouUi.alpha(ring == 0 ? MahouUi.GOLD : rgb, (int) (al * 0.85F)));
            }
        }
        // Raios
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(t * 0.8F));
        for (int i = 0; i < 8; i++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(i * 45F));
            g.fillGradient(-1, -58, 1, -34, MahouUi.alpha(rgb, 0), MahouUi.alpha(rgb, (int) (alpha * 0x90)));
            g.pose().popPose();
        }
        g.pose().popPose();

        // Emblema crescendo
        float pop = easeBack(Mth.clamp(t / 16F, 0F, 1F));
        int size = Math.max(1, Math.round(32 * pop));
        g.setColor(1F, 1F, 1F, alpha);
        CharacterUi.emblem(g, font, r, cx - size / 2, cy - size / 2, size, false);
        g.setColor(1F, 1F, 1F, 1F);

        // Título
        float titleIn = Mth.clamp((t - 8F) / 10F, 0F, 1F);
        if (titleIn > 0.05F) {
            drawCentered(g, font, Component.translatable(level >= RaceProgress.MAX_LEVEL ? "overlay.isekai_mahou.character.form"
                            : "overlay.isekai_mahou.character.awakens").withStyle(ChatFormatting.BOLD),
                    cx, cy + 50, 1.2F, MahouUi.alpha(MahouUi.GOLD, (int) (al * titleIn)));
        }
        // Nome da raça, letra a letra
        String full = (level >= RaceProgress.MAX_LEVEL ? r.formName().getString()
                : r.displayName().getString() + " " + RaceProgress.roman(level)).toUpperCase(java.util.Locale.ROOT);
        int letters = Mth.clamp((int) ((t - 16F) / 2.2F), 0, full.length());
        if (letters > 0) {
            Component name = Component.literal(full.substring(0, letters)).withStyle(ChatFormatting.BOLD);
            float scale = Math.min(2.6F, (gw - 24F) / Math.max(1, font.width(Component.literal(full).withStyle(ChatFormatting.BOLD))));
            drawCentered(g, font, name, cx, cy + 64, scale, MahouUi.alpha(rgb, al));
        }
        // Convite
        float subIn = Mth.clamp((t - 16F - full.length() * 2.2F) / 10F, 0F, 1F);
        if (subIn > 0.05F) {
            Component sub = level >= RaceProgress.MAX_LEVEL
                    ? Component.translatable("overlay.isekai_mahou.character.form_key", Component.keybind("key.isekai_mahou.racial_ability"))
                    : Component.translatable("overlay.isekai_mahou.character.level_sub");
            drawCentered(g, font, sub, cx, cy + 92, 0.9F, MahouUi.alpha(MahouUi.TEXT, (int) (al * subIn)));
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

    private static float easeBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1F;
        return 1F + c3 * (float) Math.pow(x - 1F, 3) + c1 * (float) Math.pow(x - 1F, 2);
    }

    private UnlockOverlay() {}
}
