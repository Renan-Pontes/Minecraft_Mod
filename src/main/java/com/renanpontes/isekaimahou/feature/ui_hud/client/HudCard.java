package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Card do canto superior esquerdo: classe, nível, Círculo, XP, rank da Guilda e meditação.
 * v1.0: segue o {@link HudLayout} — fica na região CARD e, se o minimapa estiver no canto superior ESQUERDO (o Xaero
 * permite), desce para logo abaixo dele (nada do HUD desenha por cima do minimapa). Avisa {@code drawn(CARD)}.
 */
final class HudCard {
    /** Letras dos ranks da Guilda (contador "guild_rank": 1 = F ... 7 = S). */
    static final String[] GUILD_RANKS = {"F", "E", "D", "C", "B", "A", "S"};
    static final int[] GUILD_RANK_COLORS = {0xFF9AA4B0, 0xFF6BD66B, 0xFF4FD8D8, 0xFF5B9BFF, 0xFFB46BFF, 0xFFFFC94F, 0xFFFF5B5B};

    /** Base do card com as 2 linhas extras (Ascensão + meditação), com o card no topo (y = 4). */
    static final int MAX_BOTTOM = 4 + 34 + 2 * 10;
    /** Base do card sem linhas extras (card no topo). */
    static final int MIN_BOTTOM = 4 + 34;

    /** Topo do card neste quadro: 4, ou abaixo do minimapa quando ele está no canto superior esquerdo. */
    static int originY = 4;

    private record Line(Component text, int color) {}

    /** Borda direita e base do card no último quadro (0 = não desenhado). */
    static int right;
    static int bottom;
    /**
     * Borda direita MÁXIMA que o card pode ter com a classe atual (todas as linhas extras e o selo de rank contados).
     * O topo central se organiza por ela, não pela largura do quadro: meditar ou chegar ao limite não empurra nada.
     */
    static int laneRight = 4 + 124;

    /** Ícone da classe em cache (evita criar o ItemStack a cada quadro). */
    private static MagicPath iconPath;
    private static ItemStack iconStack = ItemStack.EMPTY;

    private HudCard() {}

    /** Base do card com as 2 linhas extras na posição atual (a barra de mana se ancora aqui e não "pula"). */
    static int maxBottom() {
        return MAX_BOTTOM - 4 + originY;
    }

    /** Base do card sem linhas extras na posição atual. */
    static int minBottom() {
        return MIN_BOTTOM - 4 + originY;
    }

    /** Recalcula o topo do card (uma vez por quadro, antes de desenhar). */
    static void place() {
        int y = HudLayout.MARGIN;
        try {
            // O minimapa só empurra o card quando cobre a faixa dele (canto superior esquerdo)
            y = HudScale.belowMinimap(4, laneRight - 4, HudLayout.MARGIN);
            if (y > HudScale.height() / 3) y = HudLayout.MARGIN;
        } catch (RuntimeException ignored) {
            // leitura do minimapa falhou: o card fica no lugar padrão
        }
        originY = y;
    }

    static void render(GuiGraphics g, Minecraft mc, MagicData d) {
        Font font = mc.font;
        int x = 4, y = originY;
        MagicPath path = d.path();
        int pathColor = path == MagicPath.NONE ? 0xFF9AA4B0 : path.argb();
        boolean meditating = ClientMagic.meditating;
        float pulse = HudDraw.pulse(1400);

        Component name = path.displayName().withStyle(ChatFormatting.BOLD);
        Component level = Component.translatable("hud.isekai_mahou.ui_hud.level_circle", d.level(), d.circle());
        int rank = Math.min(GUILD_RANKS.length, d.counter("guild_rank"));

        List<Line> extra = new ArrayList<>();
        boolean cap = d.atLevelCap();
        if (cap && d.circle() >= Progression.MAX_CIRCLE) {
            extra.add(new Line(Component.translatable("hud.isekai_mahou.ui_hud.max_level"), HudDraw.GOLD));
        } else if (cap) {
            extra.add(new Line(Component.translatable("hud.isekai_mahou.ui_hud.ascension_ready"),
                    HudDraw.lerp(pulse, HudDraw.GOLD, HudDraw.GOLD_LIGHT)));
        }
        if (meditating) {
            extra.add(new Line(Component.translatable("hud.isekai_mahou.ui_hud.meditating"),
                    HudDraw.lerp(pulse, 0xFFFFE9A8, 0xFFFFFFFF)));
        }

        int rankW = rank > 0 ? 12 : 0;
        int textW = Math.max(font.width(name) + rankW, font.width(level));
        for (Line l : extra) textW = Math.max(textW, font.width(l.text()));
        int w = Math.max(124, 31 + textW + 7);
        int h = 34 + extra.size() * 10;
        right = x + w;
        bottom = y + h;
        laneRight = x + maxWidth(font, name, d);
        // Em pixels reais da GUI (o cartão pode estar ampliado/reduzido pela opção de tamanho do HUD)
        HudLayout.drawn(HudLayout.Region.CARD, HudScale.toReal(new HudLayout.Rect(x, y, w, h)));

        int accent = meditating ? HudDraw.lerp(pulse, HudDraw.GOLD, HudDraw.GOLD_LIGHT) : HudDraw.SYS_BORDER;
        if (meditating) HudDraw.glow(g, x, y, w, h, HudDraw.GOLD, 0.5F + 0.5F * pulse, 3);
        HudDraw.systemPanel(g, x, y, w, h, accent);
        // Faixa da classe na borda esquerda
        g.fill(x + 1, y + 1, x + 3, y + h - 1, pathColor);

        // Ícone da classe numa moldura
        int ix = x + 6, iy = y + 6;
        g.fill(ix, iy, ix + 20, iy + 20, 0xFF0A0E18);
        g.fillGradient(ix + 1, iy + 1, ix + 19, iy + 19, HudDraw.fade(pathColor, 0.35F), HudDraw.fade(pathColor, 0.08F));
        g.renderOutline(ix, iy, 20, 20, pathColor);
        if (path != iconPath || iconStack.isEmpty()) {
            iconPath = path;
            iconStack = ClassKits.icon(path);
        }
        g.renderItem(iconStack, ix + 2, iy + 2);

        int tx = x + 31;
        g.drawString(font, name, tx, y + 5, pathColor, true);
        if (rank > 0) {
            int rx = x + w - 13, ry = y + 4;
            int rc = GUILD_RANK_COLORS[rank - 1];
            g.fill(rx, ry, rx + 10, ry + 10, 0xFF0A0E18);
            g.renderOutline(rx, ry, 10, 10, rc);
            g.drawCenteredString(font, GUILD_RANKS[rank - 1], rx + 5, ry + 1, rc);
        }
        g.drawString(font, level, tx, y + 16, 0xFFFFE6A0, true);

        // Barra de XP (dourada quando chegou ao limite do Círculo)
        int bx = tx, by = y + 27, bw = x + w - 6 - tx;
        float frac = cap ? 1F : Math.min(1F, (float) d.xp() / Progression.xpToNext(d.level()));
        if (cap) {
            HudDraw.bar(g, bx, by, bw, 3, 1F, HudDraw.GOLD_LIGHT, 0xFFD69A1E, HudDraw.fade(HudDraw.GOLD, 0.6F + 0.4F * pulse));
            // brilho que percorre a barra
            int sx = bx + Math.round((bw + 10) * ((net.minecraft.Util.getMillis() % 1600) / 1600F)) - 6;
            int s0 = Math.max(bx, sx), s1 = Math.min(bx + bw, sx + 5);
            if (s1 > s0) g.fill(s0, by, s1, by + 3, 0xA0FFFFFF);
        } else {
            HudDraw.bar(g, bx, by, bw, 3, frac, 0xFF8FEFFF, 0xFF2A9FD6, 0xFF1E3A55);
        }

        int ly = y + 34;
        for (Line l : extra) {
            g.drawString(font, l.text(), tx, ly, l.color(), true);
            ly += 10;
        }
    }

    /** Largura que o card teria no pior caso (selo de rank, nível de 3 dígitos e qualquer linha extra). */
    private static int maxWidth(Font font, Component name, MagicData d) {
        int textW = font.width(name) + 12;
        textW = Math.max(textW, font.width(Component.translatable("hud.isekai_mahou.ui_hud.level_circle",
                Math.max(100, d.level()), Math.max(9, d.circle()))));
        textW = Math.max(textW, font.width(Component.translatable("hud.isekai_mahou.ui_hud.max_level")));
        textW = Math.max(textW, font.width(Component.translatable("hud.isekai_mahou.ui_hud.ascension_ready")));
        textW = Math.max(textW, font.width(Component.translatable("hud.isekai_mahou.ui_hud.meditating")));
        return Math.max(124, 31 + textW + 7);
    }
}
