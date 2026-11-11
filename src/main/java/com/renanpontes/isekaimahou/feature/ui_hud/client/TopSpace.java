package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.social.PartyHud;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.SocialPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Layout ESTÁVEL do topo central (v0.9): onde ficam o Olho Analítico e os banners, sem cobrir o card (canto superior
 * esquerdo), os ícones de efeito do vanilla, o MINIMAPA nem o rastreador de objetivos (canto superior direito).
 * <p>
 * Tudo aqui é função só de coisas que não mudam enquanto se joga — tamanho/escala da tela, número de barras de chefe,
 * o minimapa e uma "faixa" fixa para o card, os efeitos e o rastreador (o espaço que eles
 * PODEM ocupar, não o que ocupam neste quadro). Assim o painel não desliza quando a mira muda de alvo, quando um efeito
 * aparece, quando se entra numa cidade (linha do lugar) ou quando se fica Procurado. Só muda de lugar quando uma barra de
 * chefe aparece/some (recalculado na hora, sem animar) ou quando a tela muda de tamanho.
 * <p>
 * v1.0: segue o {@link HudLayout} do núcleo — {@link HudLayout#rightLimit} (nada por cima do minimapa) e
 * {@link HudLayout#tracker()} (o rastreador fica logo abaixo do minimapa, ou à esquerda dele em telas baixas). Quando o
 * Olho precisa descer para baixo do card (tela estreita), ele também desvia do HUD de grupo da esquerda.
 */
final class TopSpace {
    /** Os ícones de efeito do vanilla ocupam o topo direito até esta altura. */
    private static final int EFFECTS_BOTTOM = HudLayout.EFFECTS_BOTTOM;
    /** Faixa reservada para os ícones de efeito (3 colunas de 25 px + margem). */
    private static final int EFFECT_LANE = 25 * 3 + 3;
    /** Largura mínima aceitável do Olho Analítico no topo (abaixo disso ele desce para baixo do card). */
    private static final int EYE_MIN_TOP_W = 172;
    /** Largura e altura de uma linha do HUD de grupo do núcleo ({@code client/social/PartyHud}). */
    private static final int PARTY_W = 104, PARTY_ROW = 21, PARTY_HEAD = 12;

    /** Retângulo fixo do Olho Analítico: (x, y, w) e a altura RESERVADA h (o painel nunca passa dela). */
    record Rect(int x, int y, int w, int h) {
        int bottom() {
            return y + h;
        }

        boolean overlapsRows(int top, int bottom) {
            return top < y + h && bottom > y;
        }
    }

    private TopSpace() {}

    /** Base da faixa do card: card com as 2 linhas extras + a linha do lugar + folga. */
    static int cardLaneBottom() {
        return HudCard.maxBottom() + 2 + HudPlace.RESERVED + 2;
    }

    /**
     * Primeira linha livre no topo central: logo abaixo das barras de chefe (v1.1: sem a faixa do Jade — as dicas de
     * criaturas do Jade são desligadas pelo {@link JadeCompat}, então o painel fica sempre no alto, nunca no meio).
     */
    static int top(int gh) {
        return BossBarTracker.freeTop();
    }

    /** Borda direita da faixa do card (0 = card não desenhado neste quadro: espectador, F3). */
    private static int cardRight() {
        return HudCard.bottom > 0 ? HudCard.laneRight : 0;
    }

    /** O Olho Analítico está ligado? (a faixa dele fica reservada mesmo sem alvo: nada ao redor "pula") */
    static boolean eyeEnabled() {
        return HudDraw.config(MahouConfig.SHOW_TARGET_INFO);
    }

    /**
     * Lugar fixo do Olho Analítico. Ordem de preferência: centralizado logo abaixo das barras de chefe (e do Jade) com
     * largura cheia; se o card/efeitos/minimapa/rastreador apertam, a mesma faixa com largura menor (fixa para esta
     * tela); senão logo abaixo da faixa do card.
     */
    static Rect eye(int gw, int gh) {
        int top = top(gh);
        int h = TargetEye.RESERVED_H;
        int want = Math.min(TargetEye.WIDTH, gw - 8);
        int cardRight = cardRight();

        // 1) No topo, com largura cheia (ou um pouco menor, mas a mesma sempre nesta tela)
        int[] lane = lane(gw, top, h, cardRight);
        int avail = lane[1] - lane[0];
        if (avail >= Math.min(want, EYE_MIN_TOP_W)) {
            int w = Math.min(want, avail);
            return new Rect(Mth.clamp(gw / 2 - w / 2, lane[0], lane[1] - w), top, w, h);
        }
        // 2) Abaixo da faixa do card (tela estreita / GUI grande): decidido pelo tamanho da tela, não pelo quadro
        int y = Math.max(top, cardRight > 0 ? cardLaneBottom() + 1 : top);
        lane = lane(gw, y, h, cardRight);
        avail = lane[1] - lane[0];
        if (avail >= Math.min(want, EYE_MIN_TOP_W)) {
            int w = Math.min(want, avail);
            return new Rect(Mth.clamp(gw / 2 - w / 2, lane[0], lane[1] - w), y, w, h);
        }
        // 3) Tela minúscula: centralizado abaixo do card, encostando no que for preciso — menos no minimapa
        int right = Math.max(8, HudLayout.rightLimit(y, y + h));
        int w = Math.min(want, right - 4);
        return new Rect(Mth.clamp(gw / 2 - w / 2, 4, Math.max(4, right - w)), y, w, h);
    }

    /** Intervalo horizontal livre [esq, dir) para algo nas linhas [y, y + h) no topo central. */
    private static int[] lane(int gw, int y, int h, int cardRight) {
        int left = cardRight > 0 && y < cardLaneBottom() ? cardRight + 6 : 4;
        // HUD de grupo (núcleo) na coluna da esquerda, logo abaixo do card
        int[] party = partyBox();
        if (party != null && y < party[1] + party[3] && y + h > party[1]) left = Math.max(left, party[0] + party[2] + 6);
        int right = gw - 4;
        if (y < EFFECTS_BOTTOM) right = Math.min(right, gw - EFFECT_LANE);
        // Minimapa: nada por cima dele
        right = Math.min(right, HudLayout.rightLimit(y, y + h));
        // Rastreador de objetivos (abaixo do minimapa ou à esquerda dele)
        HudLayout.Rect tr = HudLayout.tracker();
        if (y + h > tr.y() && tr.x() > gw / 2) right = Math.min(right, tr.x() - 8);
        return new int[] {left, Math.max(left, right)};
    }

    /**
     * Caixa (x, y, w, h) do HUD de grupo do núcleo, ou null se ele não aparece. Mesma conta do {@code PartyHud}
     * (coluna da esquerda, abaixo do cartão); só muda quando alguém entra/sai do grupo.
     */
    private static int[] partyBox() {
        if (!PartyHud.enabled) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        List<SocialPayloads.Member> members = ClientSocialView.partyMembers();
        if (members.size() < 2) return null;
        UUID me = mc.player.getUUID();
        int others = 0;
        for (SocialPayloads.Member m : members) if (!m.id().equals(me)) others++;
        if (others == 0) return null;
        int x = HudLayout.leftEdge();
        HudLayout.Rect stack = HudLayout.lastDrawn(HudLayout.Region.STACK);
        int h = PARTY_HEAD + others * PARTY_ROW;
        int y = HudLayout.CARD_BOTTOM + HudLayout.MARGIN;
        if (stack != null && stack.intersects(x, y, PARTY_W, h)) x = stack.right() + 6;
        return new int[] {x, y, PARTY_W, h};
    }

    /** Primeira linha dos banners de nível/Círculo: logo abaixo da faixa (fixa) do Olho Analítico. */
    static int bannerTop(int gw, int gh) {
        return eyeEnabled() ? eye(gw, gh).bottom() + 4 : top(gh) + 4;
    }

    /** Meia-largura máxima centrada na tela para algo que ocupa as linhas [top, bottom) (banners). */
    static int maxHalf(Minecraft mc, int gw, int top, int bottom) {
        int cx = gw / 2;
        int[] lane = lane(gw, top, bottom - top, cardRight());
        return Math.max(0, Math.min(cx - lane[0], lane[1] - cx));
    }

    /**
     * Borda direita máxima para algo encostado na esquerda (linha do lugar sob o card) nas linhas [top, bottom):
     * não entra nas barras de chefe/faixa do Jade (centro), na faixa do Olho Analítico, nos ícones de efeito, no
     * minimapa nem no rastreador. Só usa faixas fixas — a largura disponível não muda de um quadro para o outro.
     */
    static int leftSideLimit(Minecraft mc, int gw, int gh, int top, int bottom) {
        int right = gw - 4;
        // Barras de chefe (182 px centradas) e a caixa do Jade logo abaixo delas
        if (top < top(gh) && top(gh) > 4) right = Math.min(right, gw / 2 - 91 - 4);
        if (eyeEnabled()) {
            Rect eye = eye(gw, gh);
            if (eye.overlapsRows(top, bottom)) right = Math.min(right, eye.x() - 4);
        }
        if (top < EFFECTS_BOTTOM) right = Math.min(right, gw - EFFECT_LANE);
        right = Math.min(right, HudLayout.rightLimit(top, bottom));
        HudLayout.Rect tr = HudLayout.tracker();
        if (bottom > tr.y() && tr.x() > gw / 2) right = Math.min(right, tr.x() - 8);
        return right;
    }
}
