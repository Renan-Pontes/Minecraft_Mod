package com.renanpontes.isekaimahou.client.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.stack.StackHud;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Layout FIXO do HUD do Isekai Mahou (v1.0 — A3). Cada coisa tem a sua região e nada da nossa UI desenha por cima do
 * minimapa:
 * <pre>
 *  ┌─────────────┬──────── topo central ────────┬─────────────┐
 *  │ CARD        │ (barras de chefe, Olho        │ MINIMAP     │ ← Xaero/JourneyMap (tamanho lido/estimado)
 *  │ (sup. esq.) │  Analítico, banners: ui_hud)  │ TRACKER     │ ← rastreador de objetivos logo abaixo
 *  │ STACK       │                               │             │
 *  │ (Pilha)     │          ARENA (aviso)        │             │
 *  │ TOASTS      │                               │             │
 *  │ (esq.-centro│        DIALOGUE (embaixo)     │             │
 *  └─────────────┴──────── barra de itens ───────┴─────────────┘
 * </pre>
 * Todas as coordenadas são da GUI escalada da janela atual ({@code guiScaledWidth/Height}). Cliente.
 *
 * <p><b>Para módulos:</b> use {@link #minimap()} / {@link #overlapsMinimap} / {@link #rightLimit} para desviar do
 * minimapa, {@link #rightColumnTop()} para algo no canto superior direito, e {@link #reserve} para empilhar algo numa
 * região (o rastreador, os avisos da esquerda...) sem cobrir os outros. {@link #drawn} / {@link #lastDrawn} contam
 * onde algo foi desenhado no último quadro (a Pilha usa: os avisos desviam dela).
 */
public final class HudLayout {
    /** Uma área da tela (GUI escalada). */
    public record Rect(int x, int y, int w, int h) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public int right() { return x + w; }

        public int bottom() { return y + h; }

        public boolean isEmpty() { return w <= 0 || h <= 0; }

        public boolean intersects(int ox, int oy, int ow, int oh) {
            return !isEmpty() && ow > 0 && oh > 0 && ox < right() && ox + ow > x && oy < bottom() && oy + oh > y;
        }

        public boolean contains(double px, double py) {
            return !isEmpty() && px >= x && px < right() && py >= y && py < bottom();
        }

        /** Cópia crescida {@code d} pixels para cada lado. */
        public Rect inflate(int d) {
            return isEmpty() ? this : new Rect(x - d, y - d, w + 2 * d, h + 2 * d);
        }
    }

    /** Regiões fixas do HUD. */
    public enum Region { CARD, MINIMAP, TRACKER, TOASTS, STACK, DIALOGUE, ARENA }

    public static final int MARGIN = 4;
    /** Os ícones de efeito do vanilla ocupam o canto superior direito até esta altura (sem minimapa empurrando). */
    public static final int EFFECTS_BOTTOM = 52;
    /** Base da faixa do cartão do canto superior esquerdo (cartão + 2 linhas extras + linha do lugar). */
    public static final int CARD_BOTTOM = 70;
    public static final int CARD_WIDTH = 128;
    /** Topo do rastreador sem minimapa (logo abaixo das 2 fileiras de ícones de efeito). */
    public static final int RIGHT_COLUMN_TOP = 56;
    public static final int TRACKER_MAX_WIDTH = 160;
    /** Menos espaço que isto abaixo do minimapa: o rastreador vai para o lado esquerdo dele. */
    private static final int MIN_TRACKER_ROOM = 64;
    /** Abaixo disto ficam a barra de ação, a barra de itens e o HUD de recursos. */
    public static final int BOTTOM_RESERVED = 64;
    /** Quanto tempo um {@link #drawn} vale. */
    private static final long DRAWN_MS = 250;

    private record Reservation(String id, IntSupplier height) {}

    private static final Map<Region, Map<String, Reservation>> RESERVES = new EnumMap<>(Region.class);
    private static final Map<Region, Rect> DRAWN = new EnumMap<>(Region.class);
    private static final Map<Region, Long> DRAWN_AT = new EnumMap<>(Region.class);
    @Nullable
    private static volatile Supplier<Rect> minimapProvider;
    private static boolean providerFailed;

    // =====================================================================================
    //  Tela
    // =====================================================================================
    public static int width() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int height() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    // =====================================================================================
    //  Minimapa
    // =====================================================================================
    /**
     * Retângulo ocupado pelo minimapa agora (com as linhas de coordenadas embaixo e uma margem), ou {@link Rect#EMPTY}
     * sem minimapa (mod ausente, minimapa desligado, config {@code layoutAvoidMinimap} falsa). Xaero: lido do próprio
     * mod (com reserva estimada pela config dele); JourneyMap: estimado.
     */
    public static Rect minimap() {
        if (!MahouConfig.safe(MahouConfig.LAYOUT_AVOID_MINIMAP)) return Rect.EMPTY;
        Supplier<Rect> custom = minimapProvider;
        if (custom != null && !providerFailed) {
            try {
                Rect r = custom.get();
                return r == null ? Rect.EMPTY : r;
            } catch (RuntimeException e) {
                providerFailed = true;
                IsekaiMahou.LOGGER.error("Provedor de minimapa do HudLayout falhou; usando a estimativa do núcleo", e);
            }
        }
        return MinimapProbe.rect(Minecraft.getInstance(), width(), height());
    }

    /** Há um minimapa na tela? */
    public static boolean hasMinimap() {
        return !minimap().isEmpty();
    }

    /** O minimapa está no canto superior direito (o caso normal)? */
    public static boolean minimapTopRight() {
        Rect m = minimap();
        return !m.isEmpty() && m.right() >= width() - 12 && m.y() < height() / 3;
    }

    /**
     * Substitui a leitura do minimapa (outro mod de minimapa, ou um módulo que sabe melhor). {@code null} volta ao
     * padrão. O fornecedor roda a cada consulta: precisa ser barato.
     */
    public static void setMinimapProvider(@Nullable Supplier<Rect> provider) {
        minimapProvider = provider;
        providerFailed = false;
    }

    /** O retângulo cobre o minimapa? */
    public static boolean overlapsMinimap(int x, int y, int w, int h) {
        return minimap().intersects(x, y, w, h);
    }

    /**
     * Borda direita máxima (x) para algo nas linhas [top, bottom) que não pode cobrir o minimapa (e fica à esquerda
     * dele). Sem minimapa nessas linhas: {@code largura - MARGIN}.
     */
    public static int rightLimit(int top, int bottom) {
        Rect m = minimap();
        int gw = width();
        if (m.isEmpty() || bottom <= m.y() || top >= m.bottom() || m.x() < gw / 2) return gw - MARGIN;
        return m.x() - MARGIN;
    }

    /** Y para algo em [x, x+w) começando em {@code y}: abaixo do minimapa se cruzar com ele. */
    public static int belowMinimap(int x, int w, int y) {
        Rect m = minimap();
        if (m.isEmpty() || x >= m.right() || x + w <= m.x() || y >= m.bottom()) return y;
        return m.bottom() + MARGIN;
    }

    // =====================================================================================
    //  Regiões fixas
    // =====================================================================================
    /** Faixa do cartão do canto superior esquerdo (o cartão em si é do ui_hud). */
    public static Rect card() {
        return new Rect(MARGIN, MARGIN, CARD_WIDTH, CARD_BOTTOM - MARGIN);
    }

    /** Primeira linha livre da coluna direita: abaixo do minimapa (se estiver no canto) ou dos ícones de efeito. */
    public static int rightColumnTop() {
        Rect m = minimap();
        if (!m.isEmpty() && m.right() >= width() - 12 && m.y() < height() / 3) {
            return Math.max(m.bottom() + MARGIN, MARGIN);
        }
        return RIGHT_COLUMN_TOP;
    }

    /** Largura do rastreador de objetivos nesta tela. */
    public static int trackerWidth() {
        return Math.min(TRACKER_MAX_WIDTH, Math.max(110, width() / 3));
    }

    /**
     * Onde fica o rastreador: x/y do topo esquerdo do painel (já somadas as reservas da região TRACKER) e {@code h} =
     * altura disponível até a margem de baixo. Normalmente logo abaixo do minimapa; se sobrar pouco espaço embaixo
     * (minimapa grande em tela baixa), à esquerda do minimapa, abaixo dos ícones de efeito.
     */
    public static Rect tracker() {
        int gw = width(), gh = height();
        int w = trackerWidth();
        int reserved = reserved(Region.TRACKER);
        int bottom = gh - BOTTOM_RESERVED - 8;
        int y = rightColumnTop() + reserved;
        Rect m = minimap();
        if (minimapTopRight() && bottom - y < MIN_TRACKER_ROOM) {
            int x = m.x() - MARGIN - w;
            int y2 = RIGHT_COLUMN_TOP + reserved;
            if (x >= gw / 2 - 20) return new Rect(x, y2, w, Math.max(0, bottom - y2));
        }
        return new Rect(gw - w - MARGIN, y, w, Math.max(0, bottom - y));
    }

    /** Borda esquerda dos painéis do meio-esquerdo (à direita da barra de mana/condições do ui_hud). */
    public static int leftEdge() {
        try {
            return Math.max(2, StackHud.leftEdge.getAsInt());
        } catch (RuntimeException e) {
            return 30;
        }
    }

    /** Região da Pilha (meio-esquerdo) para um painel de altura {@code contentH}: centrada, entre o cartão e a barra de itens. */
    public static Rect stack(int contentH) {
        int gw = width(), gh = height();
        int x = leftEdge();
        int w = Mth.clamp(gw / 2 - 138 - x, 104, 136);
        int top = Mth.clamp(gh / 2 - contentH / 2 - 8, CARD_BOTTOM - 4, Math.max(CARD_BOTTOM - 4, gh - BOTTOM_RESERVED - contentH));
        return new Rect(x, top, w, contentH);
    }

    /**
     * Região dos avisos (reputação, recompensas): coluna da esquerda, a partir de ~42% da altura (esquerda-centro).
     * Se a Pilha foi desenhada agora há pouco, os avisos passam para a direita dela (ou para baixo dela). {@code h} =
     * espaço até a margem de baixo. A coluna é dividida por reservas: cada dono desenha em
     * {@code toasts().y() + reservedBefore(Region.TOASTS, seuId)} (o núcleo registra "reputation_notices" e depois
     * "reward_toasts").
     */
    public static Rect toasts() {
        int gw = width(), gh = height();
        int w = Math.min(170, Math.max(124, gw / 4));
        int x = leftEdge();
        int y = Mth.clamp(Math.round(gh * 0.42F), CARD_BOTTOM + MARGIN, Math.max(CARD_BOTTOM + MARGIN, gh - BOTTOM_RESERVED - 60));
        Rect stack = lastDrawn(Region.STACK);
        if (stack != null) {
            // Ao lado da Pilha se couber antes do meio da tela (cântico/barra de conjuração); senão logo abaixo dela
            if (stack.right() + 6 + w <= gw / 2 - 60) x = stack.right() + 6;
            else y = Math.max(y, stack.bottom() + 6);
        }
        return new Rect(x, y, w, Math.max(0, gh - BOTTOM_RESERVED - y));
    }

    /** Caixa de diálogo de {@code w}×{@code h} embaixo, centrada (acima da barra de itens e do HUD de recursos). */
    public static Rect dialogue(int w, int h) {
        int gw = width(), gh = height();
        int y = Math.max(30, gh - BOTTOM_RESERVED - h - reserved(Region.DIALOGUE));
        return new Rect((gw - w) / 2, y, w, h);
    }

    /** Aviso da arena ("Limite da arena: N blocos") de {@code w}×{@code h}: centrado, logo abaixo da mira. */
    public static Rect arenaNotice(int w, int h) {
        int gw = width(), gh = height();
        return new Rect((gw - w) / 2, gh / 2 + 22 + reserved(Region.ARENA), w, h);
    }

    // =====================================================================================
    //  Reservas (empilhar coisas numa região)
    // =====================================================================================
    /**
     * Algo ocupa {@code height} pixels no começo de uma região (0 quando escondido): o dono principal da região
     * ({@link #tracker()}, {@link #dialogue}, {@link #arenaNotice}) desce/sobe esse tanto. Na região TOASTS os
     * participantes são iguais: cada um começa em {@link #reservedBefore} (ordem de registro).
     * Ex.: um painel no canto superior direito abaixo do minimapa: {@code reserve(Region.TRACKER, "meu_painel", () -> h)}
     * e desenhe em {@code rightColumnTop() + reservedBefore(Region.TRACKER, "meu_painel")}.
     */
    public static synchronized void reserve(Region region, String id, IntSupplier height) {
        if (region == null || id == null || height == null) return;
        RESERVES.computeIfAbsent(region, r -> new LinkedHashMap<>()).put(id, new Reservation(id, height));
    }

    public static synchronized void release(Region region, String id) {
        Map<String, Reservation> m = RESERVES.get(region);
        if (m != null) m.remove(id);
    }

    /** Soma das reservas da região agora. */
    public static int reserved(Region region) {
        return sum(region, null);
    }

    /** Soma das reservas registradas ANTES de {@code id} (onde o dono de {@code id} começa a desenhar). */
    public static int reservedBefore(Region region, String id) {
        return sum(region, id);
    }

    private static int sum(Region region, @Nullable String stopAt) {
        List<Reservation> list;
        synchronized (HudLayout.class) {
            Map<String, Reservation> m = RESERVES.get(region);
            if (m == null || m.isEmpty()) return 0;
            list = new ArrayList<>(m.values());
        }
        int total = 0;
        for (Reservation r : list) {
            if (r.id().equals(stopAt)) break;
            try {
                total += Math.max(0, r.height().getAsInt());
            } catch (RuntimeException e) {
                // reserva quebrada não derruba o HUD
            }
        }
        return total;
    }

    // =====================================================================================
    //  O que foi desenhado no último quadro
    // =====================================================================================
    /** Conta que a região foi desenhada neste quadro com este retângulo (chame a cada quadro em que desenhar). */
    public static synchronized void drawn(Region region, Rect rect) {
        if (region == null || rect == null) return;
        DRAWN.put(region, rect);
        DRAWN_AT.put(region, Util.getMillis());
    }

    /** Retângulo desenhado na região há menos de 250 ms, ou null. */
    @Nullable
    public static synchronized Rect lastDrawn(Region region) {
        Long at = DRAWN_AT.get(region);
        if (at == null || Util.getMillis() - at > DRAWN_MS) return null;
        return DRAWN.get(region);
    }

    /** Saída do mundo: esquece o que foi desenhado (as reservas são registro e ficam). */
    public static synchronized void clearFrame() {
        DRAWN.clear();
        DRAWN_AT.clear();
        MinimapProbe.invalidate();
    }

    private HudLayout() {}
}
