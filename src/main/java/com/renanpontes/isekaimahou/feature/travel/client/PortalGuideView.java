package com.renanpontes.isekaimahou.feature.travel.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideNet;
import com.renanpontes.isekaimahou.feature.travel.guide.PortalGuide;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Sub-aba "Guia de Portais" da aba Viagem: à esquerda as dimensões do mod; à direita, da escolhida, onde fica o portal,
 * como abrir, os requisitos (✔/✘ pelo que o jogador já tem) e o botão "Rastrear o mais próximo", que põe uma seta no
 * rastreador de objetivos. Tudo desenhado à mão (sem widgets), então trocar de sub-aba não deixa botão sobrando.
 */
final class PortalGuideView {
    private static final int ROW_H = 24, BUTTON_H = 18;
    private static final int OK = 0xFF7CE07C, BAD = 0xFFE06A6A, WARN = 0xFFFFC56A;

    /** Lembrado entre aberturas do menu. */
    private static String selectedId = "";

    private int x, y, w, h;
    private int listW, panelX, panelW;
    private int btnX, btnY, btnW;
    private boolean btnVisible;
    private double scroll;
    private List<PortalGuide.Entry> entries = List.of();

    void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        listW = Mth.clamp((int) (w * 0.36F), 104, 168);
        panelX = x + listW + 8;
        panelW = x + w - panelX - 2;
        entries = PortalGuide.entries();
        if (PortalGuide.get(selectedId) == null) {
            // Começa na que o jogador está rastreando, ou na primeira ainda não visitada
            GuideClient.Target t = GuideClient.target();
            MagicData d = ClientMagic.data();
            selectedId = t != null ? t.id() : entries.stream().filter(e -> e.trackable() && !e.visited(d)).findFirst()
                    .or(() -> entries.stream().findFirst()).map(PortalGuide.Entry::id).orElse("");
        }
    }

    @Nullable
    private PortalGuide.Entry selected() {
        return PortalGuide.get(selectedId);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        renderList(g, font, d, mx, my);
        PortalGuide.Entry e = selected();
        btnVisible = false;
        if (e != null) renderPanel(g, font, d, e, mx, my);
    }

    private void renderList(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int content = entries.size() * ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h));
        MahouUi.roundRect(g, x, y, listW, h, 0x80141022, 0x800A0812);
        g.enableScissor(x + 1, y + 1, x + listW - 1, y + h - 1);
        for (int i = 0; i < entries.size(); i++) {
            PortalGuide.Entry e = entries.get(i);
            int ry = y + i * ROW_H - (int) scroll;
            if (ry + ROW_H < y || ry > y + h) continue;
            boolean sel = e.id().equals(selectedId);
            boolean hov = mx >= x && mx < x + listW && my >= ry && my < ry + ROW_H && my >= y && my < y + h;
            int col = 0xFF000000 | e.color();
            if (sel) {
                MahouUi.roundRect(g, x + 2, ry + 1, listW - 4, ROW_H - 2, MahouUi.alpha(col, 80), MahouUi.alpha(col, 24));
                MahouUi.roundOutline(g, x + 2, ry + 1, listW - 4, ROW_H - 2, MahouUi.alpha(MahouUi.lighten(col, 0.3F), 0xD0));
            } else if (hov) {
                g.fill(x + 2, ry + 1, x + listW - 2, ry + ROW_H - 1, 0x22FFFFFF);
            }
            g.renderItem(GuideClient.icon(e), x + 5, ry + 4);
            g.drawString(font, MahouUi.fit(font, e.name(), listW - 30), x + 25, ry + 3, 0xFFFFFFFF, false);
            MahouUi.smallText(g, font, rowStatus(e, d), x + 25, ry + 14, 0.65F, rowStatusColor(e, d), false);
            if (GuideClient.tracking(e.id())) MahouUi.diamond(g, x + listW - 9, ry + ROW_H / 2, 3, MahouUi.GOLD);
        }
        g.disableScissor();
        if (content > h) {
            int bh = Math.max(10, (h - 4) * h / content);
            int by = y + 2 + (int) ((h - 4 - bh) * (scroll / Math.max(1, content - h)));
            g.fill(x + listW - 3, by, x + listW - 1, by + bh, MahouUi.GOLD);
        }
    }

    private static Component rowStatus(PortalGuide.Entry e, MagicData d) {
        if (e.visited(d)) return Component.translatable("guide.isekai_mahou.portals.status.visited");
        if (!e.trackable()) return Component.translatable("guide.isekai_mahou.portals.status.no_portal");
        if (!e.circleOk(d)) return Component.translatable("guide.isekai_mahou.portals.req_circle", e.minCircle());
        if (!e.ready(d)) return Component.translatable("guide.isekai_mahou.portals.status.pending");
        return Component.translatable("guide.isekai_mahou.portals.status.ready");
    }

    private static int rowStatusColor(PortalGuide.Entry e, MagicData d) {
        if (e.visited(d)) return OK;
        if (!e.trackable()) return MahouUi.TEXT_DIM;
        return e.ready(d) ? WARN : BAD;
    }

    private void renderPanel(GuiGraphics g, Font font, MagicData d, PortalGuide.Entry e, int mx, int my) {
        int col = 0xFF000000 | e.color();
        MahouUi.roundRect(g, panelX, y, panelW, h, 0x80141022, 0x800A0812);
        MahouUi.roundOutline(g, panelX, y, panelW, h, MahouUi.alpha(col, 0x70));
        int tx = panelX + 8, tw = panelW - 16;
        int bottom = y + h - 6;
        // Rodapé primeiro (botão + estado), para o texto de cima saber até onde pode ir
        if (e.trackable()) {
            Component status = trackStatus(e);
            List<FormattedCharSequence> statusLines = status == null ? List.of() : font.split(status, tw);
            int sy = bottom - statusLines.size() * 9;
            int sc = statusColor(e);
            for (FormattedCharSequence line : statusLines) {
                g.drawString(font, line, tx, sy, sc, false);
                sy += 9;
            }
            btnW = Math.min(tw, 190);
            btnX = tx;
            btnY = bottom - statusLines.size() * 9 - BUTTON_H - (statusLines.isEmpty() ? 0 : 3);
            btnVisible = true;
            boolean tracking = GuideClient.tracking(e.id());
            boolean busy = GuideClient.pending();
            boolean hov = !busy && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BUTTON_H;
            int base = tracking ? 0xFF3A2A18 : 0xFF1C2A38;
            MahouUi.roundRect(g, btnX, btnY, btnW, BUTTON_H, hov ? MahouUi.lighten(base, 0.18F) : base, MahouUi.darken(base, 0.4F));
            MahouUi.roundOutline(g, btnX, btnY, btnW, BUTTON_H, hov ? MahouUi.GOLD : MahouUi.alpha(col, 0xD0));
            Component label = Component.translatable(busy ? "guide.isekai_mahou.portals.searching"
                    : tracking ? "guide.isekai_mahou.portals.untrack" : "guide.isekai_mahou.portals.track");
            g.drawCenteredString(font, MahouUi.fit(font, label, btnW - 8), btnX + btnW / 2, btnY + 5, busy ? MahouUi.TEXT_DIM : 0xFFFFFFFF);
            bottom = btnY - 4;
        }

        g.enableScissor(panelX + 1, y + 1, panelX + panelW - 1, Math.max(y + 2, bottom));
        int cy = y + 6;
        g.renderItem(GuideClient.icon(e), tx, cy - 1);
        g.drawString(font, e.name().copy().withStyle(ChatFormatting.BOLD), tx + 20, cy, col, true);
        Component chip = rowStatus(e, d);
        MahouUi.smallText(g, font, chip, tx + 20, cy + 10, 0.7F, rowStatusColor(e, d), false);
        cy += 22;
        g.fill(tx, cy - 3, tx + tw, cy - 2, MahouUi.alpha(MahouUi.GOLD_DIM, 0x90));

        if (e.trackable()) {
            cy = heading(g, font, Component.translatable("guide.isekai_mahou.portals.h_portal"), tx, cy);
            cy = paragraph(g, font, e.site().withStyle(ChatFormatting.WHITE).append(Component.literal(" — ")
                    .withStyle(ChatFormatting.DARK_GRAY)).append(e.where().withStyle(ChatFormatting.GRAY)), tx, cy, tw);
        }
        cy = heading(g, font, Component.translatable("guide.isekai_mahou.portals.h_how"), tx, cy);
        cy = paragraph(g, font, e.how().withStyle(ChatFormatting.GRAY), tx, cy, tw);

        List<MutableComponent> reqs = requirements(e, d);
        if (!reqs.isEmpty()) {
            cy = heading(g, font, Component.translatable("guide.isekai_mahou.portals.h_reqs"), tx, cy);
            for (MutableComponent r : reqs) cy = paragraph(g, font, r, tx, cy, tw) - 2;
        }
        g.disableScissor();
    }

    /** Linhas de requisito: ✔ verde / ✘ vermelho pelo que o jogador já cumpre; a condição extra (hora) fica neutra. */
    private static List<MutableComponent> requirements(PortalGuide.Entry e, MagicData d) {
        List<MutableComponent> out = new ArrayList<>();
        if (e.minCircle() > 0) {
            out.add(mark(e.circleOk(d)).append(Component.translatable("guide.isekai_mahou.portals.req_circle_you",
                    e.minCircle(), d.circle())));
        }
        for (String boss : e.bosses()) {
            out.add(mark(e.bossDone(d, boss)).append(Component.translatable("guide.isekai_mahou.portals.req_boss", PortalGuide.bossName(boss))));
        }
        if (e.hasExtra()) out.add(Component.literal("• ").withStyle(ChatFormatting.GOLD).append(e.extra().withStyle(ChatFormatting.GRAY)));
        return out;
    }

    private static MutableComponent mark(boolean ok) {
        return Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static int heading(GuiGraphics g, Font font, Component text, int tx, int cy) {
        MahouUi.smallText(g, font, text, tx, cy + 1, 0.7F, MahouUi.GOLD_PALE, false);
        return cy + 9;
    }

    private static int paragraph(GuiGraphics g, Font font, Component text, int tx, int cy, int tw) {
        for (FormattedCharSequence line : font.split(text, tw)) {
            g.drawString(font, line, tx, cy, MahouUi.TEXT, false);
            cy += 9;
        }
        return cy + 4;
    }

    /** Texto abaixo do botão: resultado da última busca desta dimensão (ou a distância do que está sendo seguido). */
    @Nullable
    private static Component trackStatus(PortalGuide.Entry e) {
        if (GuideClient.pending()) return null;
        GuideClient.Target t = GuideClient.target();
        if (t != null && t.id().equals(e.id())) {
            Component dist = GuideClient.distanceText(t);
            return dist != null ? Component.translatable("guide.isekai_mahou.portals.result.found", dist)
                    : Component.translatable("guide.isekai_mahou.portals.result.found_other");
        }
        if (!GuideClient.lastId.equals(e.id())) return Component.translatable("guide.isekai_mahou.portals.track_hint");
        return switch (GuideClient.lastStatus) {
            case GuideNet.NONE -> Component.translatable("guide.isekai_mahou.portals.result.none", GuideClient.lastReach);
            case GuideNet.WRONG_DIM -> Component.translatable("guide.isekai_mahou.portals.result.wrong_dim");
            case GuideNet.COOLDOWN -> Component.translatable("guide.isekai_mahou.portals.result.cooldown");
            default -> Component.translatable("guide.isekai_mahou.portals.track_hint");
        };
    }

    private static int statusColor(PortalGuide.Entry e) {
        if (GuideClient.tracking(e.id())) return OK;
        if (!GuideClient.lastId.equals(e.id())) return MahouUi.TEXT_DIM;
        return switch (GuideClient.lastStatus) {
            case GuideNet.NONE, GuideNet.WRONG_DIM -> WARN;
            default -> MahouUi.TEXT_DIM;
        };
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (mx >= x && mx < x + listW && my >= y && my < y + h) {
            int idx = (int) ((my - y + scroll) / ROW_H);
            if (idx >= 0 && idx < entries.size()) {
                String id = entries.get(idx).id();
                if (!id.equals(selectedId)) click(1.4F);
                selectedId = id;
                return true;
            }
            return false;
        }
        PortalGuide.Entry e = selected();
        if (e != null && btnVisible && !GuideClient.pending() && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BUTTON_H) {
            click(1.0F);
            GuideClient.request(GuideClient.tracking(e.id()) ? "" : e.id());
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (mx >= x && mx < x + listW && my >= y && my < y + h) {
            scroll -= sy * ROW_H;
            return true;
        }
        return false;
    }

    boolean keyPressed(int key) {
        if (entries.isEmpty() || (key != 264 && key != 265)) return false; // baixo / cima
        int idx = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(selectedId)) idx = i;
        idx = Mth.clamp(idx + (key == 264 ? 1 : -1), 0, entries.size() - 1);
        selectedId = entries.get(idx).id();
        scroll = Mth.clamp(scroll, (idx + 1) * ROW_H - h, idx * ROW_H);
        return true;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35F));
    }
}
