package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.client.stack.ClientStack;
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Condições de status do PRÓPRIO jogador (Em Chamas, Gelado, Congelado...) numa coluna logo à direita da barra de
 * mana: ícone com acúmulos, barrinha de tempo que esvazia, segundos restantes e, quando a condição aparece (ou
 * ganha acúmulos), o nome dela por alguns segundos. Presa (Congelado/Atordoado/Petrificado) = moldura vermelha
 * pulsando. A Pilha (painel do meio à esquerda) é empurrada para a direita enquanto a coluna existe.
 */
final class HudPlayerStatuses {
    private static final int ICON = 12;
    private static final int ROW = 16;
    private static final int COL_W = 30;
    private static final long POP_MS = 260L;
    private static final long NAME_MS = 2600L;

    /** Quando cada condição apareceu / ganhou acúmulos (anima o "pop" e mostra o nome). */
    private static final Map<Statuses.Kind, long[]> SEEN = new EnumMap<>(Statuses.Kind.class);
    private static int lastRight;
    private static long lastDrawn;

    private HudPlayerStatuses() {}

    static void clear() {
        SEEN.clear();
        lastRight = 0;
        lastDrawn = 0;
    }

    /** Margem esquerda mínima da Pilha neste momento (0 = a coluna não está na tela). */
    static int stackEdge() {
        return lastRight > 0 && Util.getMillis() - lastDrawn < 300 ? HudScale.toReal(lastRight + 6) : 0;
    }

    static void render(GuiGraphics g, Minecraft mc) {
        Player p = mc.player;
        List<ClientStatuses.Entry> list = p == null ? List.of() : ClientStatuses.get(p);
        long now = Util.getMillis();
        // Esquece as que acabaram (a próxima vez "pula" de novo)
        SEEN.keySet().removeIf(k -> list.stream().noneMatch(en -> en.kind() == k));
        if (p == null || list.isEmpty()) {
            lastRight = 0;
            return;
        }
        StatusView.forgetMissing(p.getId(), list);

        Font font = mc.font;
        int x0 = HudManaBar.RIGHT;
        int top = HudManaBar.lastTop + 1;
        // Só ao lado da barra em si: abaixo dela ficam os números da mana (que podem ter 4 dígitos)
        int avail = Math.max(ROW, HudManaBar.lastBarBottom + 2 - top);
        int perCol = Math.max(1, (avail + 2) / ROW);
        // Nomes só quando nada mais disputa o espaço à direita (a Pilha fica no meio à esquerda)
        boolean names = !ClientStack.any();
        int cols = (list.size() + perCol - 1) / perCol;

        for (int i = 0; i < list.size(); i++) {
            ClientStatuses.Entry en = list.get(i);
            long[] seen = SEEN.get(en.kind());
            if (seen == null) {
                seen = new long[] {now, en.stacks()};
                SEEN.put(en.kind(), seen);
            } else if (en.stacks() > seen[1]) {
                seen[0] = now;
                seen[1] = en.stacks();
            } else if (en.stacks() < seen[1]) {
                seen[1] = en.stacks();
            }
            int col = i / perCol, row = i % perCol;
            int x = x0 + col * COL_W, y = top + row * ROW;
            chip(g, font, p.getId(), en, x, y, now - seen[0], names && col == cols - 1);
        }
        lastRight = x0 + cols * COL_W;
        lastDrawn = now;
    }

    private static void chip(GuiGraphics g, Font font, int ownerId, ClientStatuses.Entry en, int x, int y, long age, boolean showName) {
        int c = StatusView.color(en.kind());
        boolean stuck = StatusView.incapacitating(en);
        boolean blink = StatusView.expiring(en) && (en.ticksLeft() / 4) % 2 == 0;
        float pop = age < POP_MS ? HudDraw.easeOutBack(age / (float) POP_MS) : 1F;

        int frame = stuck ? HudDraw.lerp(HudDraw.pulse(500), 0xFFFF3030, 0xFFFFC0B0) : c;
        if (age < POP_MS) HudDraw.glow(g, x, y, ICON, ICON, c, 1F - age / (float) POP_MS, 2);
        if (stuck) HudDraw.glow(g, x, y, ICON, ICON, 0xFFFF3030, 0.5F + 0.5F * HudDraw.pulse(500), 2);

        g.pose().pushPose();
        g.pose().translate(x + ICON / 2F, y + ICON / 2F, 0);
        float s = Math.max(0.2F, pop);
        g.pose().scale(s, s, 1F);
        g.pose().translate(-ICON / 2F, -ICON / 2F, 0);
        g.fill(0, 0, ICON, ICON, 0xD0070A12);
        g.renderOutline(0, 0, ICON, ICON, HudDraw.fade(frame, blink ? 0.45F : 1F));
        StatusView.icon(g, en, 1, 1, ICON - 2, blink ? 0.55F : 1F);
        g.pose().popPose();

        // Tempo: barrinha que esvazia + segundos
        float frac = StatusView.fraction(ownerId, en);
        g.fill(x, y + ICON + 1, x + ICON, y + ICON + 2, 0x90000000);
        int fw = Math.round(ICON * frac);
        if (fw > 0) g.fill(x, y + ICON + 1, x + fw, y + ICON + 2, HudDraw.fade(HudDraw.brighten(c, 0.3F), blink ? 0.5F : 1F));
        String time = StatusView.time(en.ticksLeft());
        HudDraw.scaledText(g, font, Component.literal(time), x + ICON + 2, y + 3F, 0.6F,
                StatusView.expiring(en) ? 0xFFFF9A9A : 0xFFD8E4F0, true);

        // Nome por alguns segundos quando aparece / acumula
        if (showName && age < NAME_MS) {
            float a = age < 200 ? age / 200F : age > NAME_MS - 500 ? (NAME_MS - age) / 500F : 1F;
            int tx = x + ICON + 2 + Math.round(font.width(time) * 0.6F) + 3;
            Component name = en.kind().displayName(en.stacks());
            if (en.stacks() > 1 && en.kind().maxStacks() > 1) name = name.copy().append(Component.literal(" " + en.stacks() + "/" + en.kind().maxStacks()));
            HudDraw.scaledText(g, font, name, tx, y + 3F, 0.75F, HudDraw.fade(HudDraw.brighten(c, 0.4F), Math.max(0F, a)), true);
        }
    }
}
