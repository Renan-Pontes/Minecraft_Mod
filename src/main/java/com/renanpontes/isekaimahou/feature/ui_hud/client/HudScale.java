package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Tamanho do HUD (opção "Tamanho do HUD", 70–130%). A camada do HUD ({@link MahouHudLayer}) é desenhada inteira com
 * uma escala e cada peça faz as contas numa <b>tela virtual</b> de {@code largura/escala × altura/escala}: o que é
 * ancorado embaixo continua embaixo, o que é centrado continua centrado, e nada sai da tela.
 * <p>
 * O resto do jogo (minimapa, barra de itens, {@link HudLayout}, a camada do topo) trabalha em pixels REAIS da GUI:
 * <ul>
 *   <li>o que vem de fora entra pela conversão real → virtual ({@link #minimap}, {@link #v}, {@link #limitV});</li>
 *   <li>o que sai (retângulos contados ao {@link HudLayout}, reservas, a faixa do cartão que o topo desvia) passa por
 *       {@link #toReal}.</li>
 * </ul>
 * Com 100% tudo aqui é identidade — o HUD fica exatamente como sempre foi.
 */
final class HudScale {
    private static float s = 1F;

    private HudScale() {}

    /** Lê a opção (uma vez por quadro, no começo da camada do HUD). */
    static void frame() {
        float want = MahouConfig.hudScale();
        s = Math.abs(want - 1F) < 0.005F ? 1F : want;
    }

    static float get() {
        return s;
    }

    /** Largura da tela virtual. */
    static int w(GuiGraphics g) {
        return s == 1F ? g.guiWidth() : Math.round(g.guiWidth() / s);
    }

    /** Altura da tela virtual. */
    static int h(GuiGraphics g) {
        return s == 1F ? g.guiHeight() : Math.round(g.guiHeight() / s);
    }

    /** Altura da tela virtual (sem o GuiGraphics à mão). */
    static int height() {
        return s == 1F ? HudLayout.height() : Math.round(HudLayout.height() / s);
    }

    /** Uma distância em pixels reais (meia barra de itens, folga do vanilla...) na tela virtual. */
    static int v(int real) {
        return s == 1F ? real : Math.round(real / s);
    }

    /** Um limite direito/inferior real na tela virtual (arredonda para dentro: nunca passa do limite). */
    static int limitV(int real) {
        return s == 1F ? real : Mth.floor(real / s);
    }

    /** Uma medida da tela virtual em pixels reais (arredonda para fora: reserva espaço que chegue). */
    static int toReal(int virtual) {
        return s == 1F ? virtual : Mth.ceil(virtual * s);
    }

    static HudLayout.Rect toReal(HudLayout.Rect r) {
        if (s == 1F || r.isEmpty()) return r;
        int x0 = Mth.floor(r.x() * s), y0 = Mth.floor(r.y() * s);
        return new HudLayout.Rect(x0, y0, Mth.ceil(r.right() * s) - x0, Mth.ceil(r.bottom() * s) - y0);
    }

    /** Retângulo do minimapa na tela virtual (crescido para fora: nada encosta nele). */
    static HudLayout.Rect minimap() {
        HudLayout.Rect m = HudLayout.minimap();
        if (s == 1F || m.isEmpty()) return m;
        int x0 = Mth.floor(m.x() / s), y0 = Mth.floor(m.y() / s);
        return new HudLayout.Rect(x0, y0, Mth.ceil(m.right() / s) - x0, Mth.ceil(m.bottom() / s) - y0);
    }

    /** Igual a {@link HudLayout#belowMinimap}, na tela virtual. */
    static int belowMinimap(int x, int w, int y) {
        if (s == 1F) return HudLayout.belowMinimap(x, w, y);
        HudLayout.Rect m = minimap();
        if (m.isEmpty() || x >= m.right() || x + w <= m.x() || y >= m.bottom()) return y;
        return m.bottom() + HudLayout.MARGIN;
    }
}
