package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Barra do recurso VERTICAL na lateral esquerda: animação suave, "fantasma" do gasto, marcas de 25%,
 * moldura dourada ao meditar e vermelha piscando quando nenhuma magia do loadout pode ser paga.
 * v0.9: nome, cor e ícone pela categoria da classe — Mana (Mágicas), Vigor (Físicas) ou Foco (Destreza); o
 * valor salvo é o mesmo (ver {@link ResourceStyle}).
 * <p>
 * v1.0 — <b>Limite treinável</b> ({@link ResourceTraining}): embaixo dos números, o teto que o treino alcança neste
 * nível ("teto 624" / "no teto") e os 3 treinos do dia (losangos dourados). Quando o recurso passou de 95% o próximo
 * losango pulsa e uma linha dourada marca o fundo da barra: esvaziar até ali conta um treino. Treinou → "+N Limite!"
 * com brilho. <b>Fadiga</b> (regeneração reduzida depois de esvaziar): barra acinzentada + ampulheta com o tempo.
 */
final class HudManaBar {
    private static final int BAR_W = 8;
    private static final int BAR_X = 9;
    /** Espaço acima do ícone para o nome do recurso. */
    private static final int LABEL_H = 10;
    /** Rodapé abaixo da barra: atual, máximo, teto, treinos do dia e fadiga (sempre reservado: nada "pula"). */
    private static final int FOOT = 40;
    private static final int FATIGUE_GREY = 0xFF8C93A6;

    /** Geometria do último quadro (os ícones de condição do jogador ficam à direita da barra). */
    static int lastTop, lastBottom, lastBarBottom;
    /** Primeira coluna livre à direita da moldura e das marcas de 25%. */
    static final int RIGHT = BAR_X + BAR_W + 6;

    private HudManaBar() {}

    static void render(GuiGraphics g, Minecraft mc, MagicData d) {
        Font font = mc.font;
        int max = Math.max(1, ClientMagic.maxMana);
        float mana = Math.max(0F, Math.min(ClientMagic.mana, max));
        boolean creative = mc.player.getAbilities().instabuild;
        boolean med = ClientMagic.meditating;
        boolean broke = LoadoutCache.nothingAffordable(mc.player, d, mana);
        boolean flash = broke && HudDraw.blink(500);
        float pulse = HudDraw.pulse(1400);
        ResourceStyle style = ResourceStyle.of(mc.player);
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean fatigued = !creative && d.fatigued(gameTime);
        float popup = HudState.trainPopupTicks / (float) HudState.TRAIN_POPUP_TICKS;

        int bw = BAR_W, bx = BAR_X;
        int bh = Mth.clamp(Math.round(g.guiHeight() * 0.36F), 56, 120);
        // Reserva a linha do lugar atual sob o card e o nome do recurso (entrar numa cidade não faz a barra pular)
        int top = Math.max(HudCard.maxBottom() + 16 + HudPlace.RESERVED + LABEL_H, g.guiHeight() / 2 - bh / 2 - 6);
        // Não descer até a área do chat/hotbar (a barra de vida fica no canto de baixo)
        int maxBottom = g.guiHeight() - 60;
        if (top + bh + FOOT > maxBottom) bh = Math.max(28, maxBottom - FOOT - top);
        lastTop = top - 13;
        lastBottom = top + bh + FOOT;
        lastBarBottom = top + bh;

        // Nome do recurso ("Mana"/"Vigor"/"Foco") e o ícone dele acima da barra
        // Texto sem cor própria: a cor vem do recurso (MagicApi.resourceColor) e fica vermelha piscando quando nada sai
        Component label = style.plainName();
        float lw = font.width(label) * 0.75F;
        int cxBar = bx + bw / 2;
        int labelColor = broke && flash ? HudDraw.RED : fatigued ? FATIGUE_GREY : style.labelLight();
        HudDraw.scaledText(g, font, label, Math.max(2F, cxBar - lw / 2F), top - 22, 0.75F, labelColor, true);
        style.icon(g, cxBar - 5, top - 13, 10, fatigued ? 0.55F : 1F);

        int frame;
        if (broke) frame = flash ? HudDraw.RED : 0xFF7A2626;
        else if (popup > 0F) frame = HudDraw.lerp(popup, style.frame(), HudDraw.GOLD_LIGHT);
        else if (med) frame = HudDraw.lerp(pulse, HudDraw.GOLD, HudDraw.GOLD_LIGHT);
        else if (fatigued) frame = 0xFF4A5064;
        else frame = style.frame();
        if (med && !broke) HudDraw.glow(g, bx - 2, top - 2, bw + 4, bh + 4, HudDraw.GOLD, 0.45F + 0.55F * pulse, 3);
        if (broke && flash) HudDraw.glow(g, bx - 2, top - 2, bw + 4, bh + 4, HudDraw.RED, 1F, 3);
        if (popup > 0F) HudDraw.glow(g, bx - 2, top - 2, bw + 4, bh + 4, HudDraw.GOLD_LIGHT, popup, 4);

        g.fill(bx - 2, top - 2, bx + bw + 2, top + bh + 2, 0xFF05060C);
        g.renderOutline(bx - 2, top - 2, bw + 4, bh + 4, frame);
        g.fill(bx, top, bx + bw, top + bh, style.back());
        g.fill(bx, top, bx + 1, top + bh, 0x20FFFFFF);

        float shown = Math.max(0F, Math.min(HudState.shownMana, max));
        float ghost = Math.max(shown, Math.min(HudState.ghostMana, max));
        int fh = Math.round(bh * shown / max);
        int gh = Math.round(bh * ghost / max);
        int bottom = top + bh;

        // Fantasma: quanto acabou de ser gasto
        if (gh > fh) g.fill(bx, bottom - gh, bx + bw, bottom - fh, style.ghost());
        if (fh > 0) {
            int cTop = broke ? 0xFFE89AA8 : style.fillTop();
            int cBottom = broke ? 0xFF8A2238 : style.fillBottom();
            if (fatigued && !broke) {
                // Fadiga: a regeneração está a ×0,3 — a barra perde a cor
                cTop = HudDraw.lerp(0.65F, cTop, 0xFFB4B8C4);
                cBottom = HudDraw.lerp(0.65F, cBottom, 0xFF4A4E5C);
            }
            g.fillGradient(bx, bottom - fh, bx + bw, bottom, cTop, cBottom);
            g.fill(bx + 1, bottom - fh, bx + 2, bottom, 0x55FFFFFF);
            g.fill(bx + bw - 1, bottom - fh, bx + bw, bottom, 0x40000000);
            // Brilho que sobe pela barra enquanto medita (regeneração rápida)
            if (med) {
                float t = (Util.getMillis() % 1100) / 1100F;
                int band = bottom - Math.round(t * (fh + 6));
                int b0 = Math.max(band, bottom - fh), b1 = Math.min(band + 4, bottom);
                if (b1 > b0) g.fill(bx, b0, bx + bw, b1, 0x70FFF4D0);
            }
            g.fill(bx, bottom - fh, bx + bw, bottom - fh + 1, broke ? 0xFFFFD0D8 : HudDraw.brighten(cTop, 0.6F));
        }

        // Marcas de 25% (dentro e fora da moldura)
        int notch = HudDraw.fade(frame, 0.85F);
        for (int q = 1; q < 4; q++) {
            int my = bottom - Math.round(bh * q / 4F);
            g.fill(bx, my, bx + bw, my + 1, q == 2 ? 0x90000000 : 0x60000000);
            int len = q == 2 ? 3 : 2;
            g.fill(bx + bw + 2, my, bx + bw + 2 + len, my + 1, notch);
            g.fill(bx - 2 - len, my, bx - 2, my + 1, notch);
        }

        // Treino armado: linha dourada no fundo da barra ("esvazie até aqui")
        int limit = d.manaLimit(), ceiling = d.manaCeiling();
        boolean atCeiling = limit >= ceiling;
        int today = HudState.trainingsToday(mc, d);
        boolean canTrain = !creative && !atCeiling && today < ResourceTraining.TRAININGS_PER_DAY;
        boolean armed = canTrain && HudState.trainArmed;
        float armPulse = HudDraw.pulse(800);
        if (armed) {
            int gold = HudDraw.lerp(armPulse, HudDraw.GOLD, HudDraw.GOLD_LIGHT);
            g.fill(bx - 4, bottom + 2, bx + bw + 4, bottom + 3, HudDraw.fade(gold, 0.55F + 0.45F * armPulse));
            HudDraw.diamond(g, bx - 4, bottom + 2, 1, gold);
            HudDraw.diamond(g, bx + bw + 3, bottom + 2, 1, gold);
        }

        // ---- Rodapé: atual (grande), máximo, teto do Limite, treinos do dia e fadiga ----
        int cx = bx + bw / 2;
        String cur = String.valueOf((int) Math.floor(mana));
        int tc = broke ? (flash ? HudDraw.RED : 0xFFFFB4B4) : fatigued ? 0xFFC8CCD8 : 0xFFFFFFFF;
        int curX = Math.max(2, cx - font.width(cur) / 2);
        g.drawString(font, cur, curX, bottom + 5, tc, true);
        Component maxText = Component.literal("/" + max);
        float mw = font.width(maxText) * 0.75F;
        HudDraw.scaledText(g, font, maxText, Math.max(2F, cx - mw / 2F), bottom + 14, 0.75F, style.muted(), true);

        // Teto que o treino alcança neste nível (ou "+N Limite!" logo depois de treinar)
        Component ceil;
        int ceilColor;
        if (popup > 0F) {
            ceil = Component.translatable("hud.isekai_mahou.ui_hud.limit_gain", HudState.trainPopupGain);
            ceilColor = HudDraw.fade(HudDraw.lerp(HudDraw.pulse(300), HudDraw.GOLD, 0xFFFFFFFF), Math.min(1F, popup * 3F));
        } else if (atCeiling) {
            ceil = Component.translatable("hud.isekai_mahou.ui_hud.limit_capped");
            ceilColor = HudDraw.GOLD;
        } else {
            ceil = Component.translatable("hud.isekai_mahou.ui_hud.limit_ceiling", ceiling);
            ceilColor = 0xFFCDBF8E;
        }
        float cw = font.width(ceil) * 0.5F;
        float rise = popup > 0F ? (1F - popup) * 2F : 0F;
        HudDraw.scaledText(g, font, ceil, Math.max(2F, cx - cw / 2F), bottom + 22 - rise, 0.5F, ceilColor, true);

        // Treinos do dia: 3 losangos (dourado = feito hoje; o próximo pulsa quando está armado)
        int py = bottom + 29;
        if (atCeiling) {
            HudDraw.spriteCentered(g, HudDraw.STAR, cx, py, HudDraw.GOLD);
        } else {
            int n = ResourceTraining.TRAININGS_PER_DAY;
            int sx = cx - (n - 1) * 3;
            for (int i = 0; i < n; i++) {
                int px = sx + i * 6;
                if (i < today) {
                    HudDraw.diamond(g, px, py, 2, HudDraw.GOLD);
                    g.fill(px, py - 1, px + 1, py, 0xFFFFF4C8);
                } else if (armed && i == today) {
                    HudDraw.diamond(g, px, py, 2, HudDraw.lerp(armPulse, 0xFF6A5A2A, HudDraw.GOLD_LIGHT));
                    HudDraw.diamond(g, px, py, 1, 0xFF0A0C14);
                } else {
                    HudDraw.diamond(g, px, py, 2, creative ? 0xFF2A3040 : 0xFF4A4636);
                    HudDraw.diamond(g, px, py, 1, 0xFF0A0C14);
                }
            }
        }

        // Fadiga: ampulheta + tempo restante
        if (fatigued) {
            int left = (int) Math.max(0L, d.fatigueUntil() - gameTime);
            Component t = Component.literal(StatusView.time(left));
            float tw = font.width(t) * 0.5F;
            int fx = Math.round(cx - (6 + tw) / 2F);
            HudDraw.sprite(g, HudDraw.HOURGLASS, Math.max(2, fx), bottom + 33, HudDraw.lerp(pulse, 0xFF7A88A8, 0xFFB8C4E0));
            HudDraw.scaledText(g, font, t, Math.max(2, fx) + 6, bottom + 35F, 0.5F, 0xFFB8C4E0, true);
        }
    }
}
