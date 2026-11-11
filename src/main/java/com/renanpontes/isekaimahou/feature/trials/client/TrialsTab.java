package com.renanpontes.isekaimahou.feature.trials.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.feature.trials.TrialTheme;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Provações" do menu B: grade elementos despertados × ranks I–VII (concluído / disponível / bloqueado) e, à
 * direita, os detalhes do rank escolhido (requisitos com ✔/✘, ondas, chefe, recompensas) com o botão Iniciar/Repetir.
 */
public class TrialsTab implements MenuTab {
    private static final int ROW_H = 18;
    private static final int OK = 0xFF7CE07C, BAD = 0xFFE06A6A, MUTED = 0xFF8A8796;
    private static Element lastElement;
    private static int lastRank;

    private int x, y, w, h;
    private int gridX, gridY, labelW, cell, detailX, detailW, visibleRows;
    private final List<Element> rows = new ArrayList<>();
    private double scroll;
    private Element selected;
    private int selectedRank = 1;
    private Button button;
    private List<Component> tooltip;

    private enum State { CLEARED, AVAILABLE, BLOCKED }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        MagicData d = ClientMagic.data();
        rows.clear();
        for (Element e : Element.values()) if (e != Element.ARCANE && d.isAwakened(e)) rows.add(e);

        labelW = Mth.clamp((int) (w * 0.2F), 66, 100);
        cell = Mth.clamp((int) ((w * 0.56F - labelW) / 7), 16, 24);
        gridX = x + 4;
        gridY = y + 34;
        detailX = gridX + labelW + 7 * cell + 10;
        detailW = x + w - detailX - 2;
        visibleRows = Math.max(1, (y + h - 4 - gridY) / ROW_H);

        selected = rows.contains(lastElement) ? lastElement : defaultElement(d);
        selectedRank = selected != null && lastElement == selected && lastRank > 0 ? lastRank : defaultRank(d, selected);
        button = screen.addTabWidget(Button.builder(Component.empty(), b -> request()).bounds(detailX, y + h - 22, detailW, 20).build());
        updateButton();
    }

    private Element defaultElement(MagicData d) {
        for (Element e : rows) {
            int r = TrialsApi.nextRank(d, e);
            if (r > 0 && TrialsApi.meetsRequirements(d, e, r, new ArrayList<>())) return e;
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static int defaultRank(MagicData d, Element e) {
        if (e == null) return 1;
        int r = TrialsApi.nextRank(d, e);
        return r == 0 ? Progression.MAX_TRIAL_RANK : r;
    }

    private State state(MagicData d, Element e, int rank) {
        if (TrialsApi.isCleared(d, e, rank)) return State.CLEARED;
        return TrialsApi.meetsRequirements(d, e, rank, new ArrayList<>()) ? State.AVAILABLE : State.BLOCKED;
    }

    private void updateButton() {
        if (button == null) return;
        MagicData d = ClientMagic.data();
        if (selected == null) {
            button.visible = false;
            return;
        }
        button.visible = true;
        if (TrialClientState.active()) {
            button.active = false;
            button.setMessage(Component.translatable("screen.isekai_mahou.trials.button_busy"));
            return;
        }
        boolean meets = TrialsApi.meetsRequirements(d, selected, selectedRank, new ArrayList<>());
        boolean cleared = TrialsApi.isCleared(d, selected, selectedRank);
        button.active = meets;
        button.setMessage(Component.translatable(!meets ? "screen.isekai_mahou.trials.button_locked"
                : cleared ? "screen.isekai_mahou.trials.button_repeat" : "screen.isekai_mahou.trials.button_start"));
    }

    private void request() {
        if (selected == null) return;
        PacketDistributor.sendToServer(new CorePayloads.RequestTrial(selected.id, selectedRank));
        Minecraft.getInstance().setScreen(null);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        tooltip = null;
        g.drawString(font, Component.translatable("screen.isekai_mahou.trials.title").withStyle(ChatFormatting.BOLD), x + 4, y + 2, MahouUi.GOLD, true);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.trials.subtitle"), x + 4, y + 13, 0.75F, MahouUi.TEXT_DIM, false);

        if (rows.isEmpty()) {
            renderEmpty(g, font);
            return;
        }
        renderGrid(g, font, d, mx, my);
        if (selected != null) renderDetail(g, font, d);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void renderEmpty(GuiGraphics g, Font font) {
        int bw = Math.min(w - 20, 320), bx = x + (w - bw) / 2, by = y + h / 2 - 34;
        MahouUi.roundRect(g, bx, by, bw, 68, 0xC0201830, 0xC0100A1A);
        MahouUi.roundOutline(g, bx, by, bw, 68, MahouUi.GOLD_DIM);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.trials.empty_title"), bx + bw / 2, by + 10, MahouUi.GOLD);
        int ly = by + 26;
        for (var line : font.split(Component.translatable("screen.isekai_mahou.trials.empty_hint"), bw - 16)) {
            g.drawString(font, line, bx + 8, ly, MahouUi.TEXT_DIM, false);
            ly += 10;
        }
    }

    private void renderGrid(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int colX = gridX + labelW;
        // cabeçalho: I..VII
        for (int r = 1; r <= Progression.MAX_TRIAL_RANK; r++) {
            int cx = colX + (r - 1) * cell;
            Component roman = Component.literal(TrialsApi.roman(r));
            g.drawCenteredString(font, roman, cx + cell / 2, gridY - 12, MahouUi.GOLD_PALE);
            if (mx >= cx && mx < cx + cell && my >= gridY - 13 && my < gridY - 2) {
                tooltip = List.of(Component.translatable("screen.isekai_mahou.trials.rank_header", TrialsApi.roman(r)).withStyle(ChatFormatting.GOLD),
                        Component.translatable("screen.isekai_mahou.trials.rank_req", Progression.trialElementLevel(r), Progression.trialCircle(r))
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("screen.isekai_mahou.trials.rank_node",
                                TrialTheme.nodeName(r)).withStyle(ChatFormatting.AQUA));
            }
        }
        int first = Mth.clamp((int) scroll, 0, Math.max(0, rows.size() - visibleRows));
        scroll = first;
        long now = Util.getMillis();
        for (int i = first; i < Math.min(rows.size(), first + visibleRows); i++) {
            Element e = rows.get(i);
            int ry = gridY + (i - first) * ROW_H;
            if ((i & 1) == 0) g.fill(gridX - 2, ry, colX + 7 * cell, ry + ROW_H, 0x18FFFFFF);
            GlyphArt.element(g, e, gridX, ry + 3, 12);
            g.drawString(font, MahouUi.fit(font, e.displayName(), labelW - 18), gridX + 15, ry + 5, e.argb(), false);
            for (int r = 1; r <= Progression.MAX_TRIAL_RANK; r++) {
                int cx = colX + (r - 1) * cell;
                State s = state(d, e, r);
                drawCell(g, font, cx + 1, ry + 1, cell - 2, ROW_H - 2, e, r, s, now);
                if (e == selected && r == selectedRank) g.renderOutline(cx, ry, cell, ROW_H, 0xFFFFFFFF);
                if (mx >= cx && mx < cx + cell && my >= ry && my < ry + ROW_H) cellTooltip(d, e, r, s);
            }
        }
        if (rows.size() > visibleRows) {
            int trackY = gridY, trackH = visibleRows * ROW_H;
            int barH = Math.max(8, trackH * visibleRows / rows.size());
            int barY = trackY + (trackH - barH) * first / Math.max(1, rows.size() - visibleRows);
            int sx = colX + 7 * cell + 3;
            g.fill(sx, trackY, sx + 2, trackY + trackH, 0x40FFFFFF);
            g.fill(sx, barY, sx + 2, barY + barH, MahouUi.GOLD);
        }
    }

    private void drawCell(GuiGraphics g, Font font, int cx, int cy, int cw, int ch, Element e, int rank, State s, long now) {
        switch (s) {
            case CLEARED -> {
                MahouUi.roundRect(g, cx, cy, cw, ch, 0xE06B5418, 0xE03A2C0C);
                MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.GOLD);
                g.drawCenteredString(font, "✔", cx + cw / 2, cy + (ch - 8) / 2, MahouUi.GOLD_PALE);
            }
            case AVAILABLE -> {
                float pulse = 0.55F + 0.45F * Mth.sin(now / 260F + rank);
                MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(e.color, (int) (90 + 70 * pulse)), MahouUi.alpha(e.color, 50));
                MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(MahouUi.lighten(e.argb(), 0.4F), (int) (160 + 95 * pulse)));
                g.drawCenteredString(font, TrialsApi.roman(rank), cx + cw / 2, cy + (ch - 8) / 2, 0xFFFFFFFF);
            }
            case BLOCKED -> {
                MahouUi.roundRect(g, cx, cy, cw, ch, 0x80181420, 0x80100C16);
                MahouUi.roundOutline(g, cx, cy, cw, ch, 0x60FFFFFF & MUTED);
                drawLock(g, cx + cw / 2, cy + ch / 2, 0xFF6E6880);
            }
        }
    }

    /** Cadeado pequeno (6×7) centrado em (cx, cy). */
    private static void drawLock(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 3, cy - 1, cx + 3, cy + 4, color);
        g.fill(cx - 2, cy - 4, cx - 1, cy - 1, color);
        g.fill(cx + 1, cy - 4, cx + 2, cy - 1, color);
        g.fill(cx - 2, cy - 4, cx + 2, cy - 3, color);
        g.fill(cx - 1, cy + 1, cx, cy + 3, 0xFF1A1622);
    }

    private void cellTooltip(MagicData d, Element e, int rank, State s) {
        List<Component> lines = new ArrayList<>();
        lines.add(TrialsApi.title(e, rank).withStyle(ChatFormatting.BOLD));
        switch (s) {
            case CLEARED -> lines.add(Component.translatable("screen.isekai_mahou.trials.state_cleared").withStyle(ChatFormatting.GOLD));
            case AVAILABLE -> lines.add(Component.translatable("screen.isekai_mahou.trials.state_available").withStyle(ChatFormatting.GREEN));
            case BLOCKED -> {
                lines.add(Component.translatable("screen.isekai_mahou.trials.state_blocked").withStyle(ChatFormatting.RED));
                List<Component> missing = new ArrayList<>();
                TrialsApi.meetsRequirements(d, e, rank, missing);
                for (Component m : missing) lines.add(Component.literal(" ✘ ").withStyle(ChatFormatting.RED).append(m.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
        lines.add(Component.translatable("screen.isekai_mahou.trials.click_select").withStyle(ChatFormatting.DARK_GRAY));
        tooltip = lines;
    }

    private void renderDetail(GuiGraphics g, Font font, MagicData d) {
        int dx = detailX, dy = y + 2, dw = detailW, dh = h - 28;
        Element e = selected;
        MahouUi.roundRect(g, dx, dy, dw, dh, 0xC0221A34, 0xC0100A18);
        MahouUi.roundOutline(g, dx, dy, dw, dh, MahouUi.alpha(e.color, 0xC0));
        MahouUi.brackets(g, dx + 2, dy + 2, dw - 4, dh - 4, 5, MahouUi.GOLD_DIM);
        // telas pequenas: o texto nunca invade o botão
        g.enableScissor(dx + 2, dy + 2, dx + dw - 2, dy + dh - 2);
        renderDetailText(g, font, d, dx, dy, dw, dh);
        g.disableScissor();
    }

    private void renderDetailText(GuiGraphics g, Font font, MagicData d, int dx, int dy, int dw, int dh) {
        Element e = selected;
        int r = selectedRank;
        int tx = dx + 7, ty = dy + 7, tw = dw - 14;
        GlyphArt.element(g, e, tx, ty - 1, 12);
        g.drawString(font, MahouUi.fit(font, TrialsApi.title(e, r).withStyle(ChatFormatting.BOLD), tw - 16), tx + 15, ty + 1, e.argb(), true);
        ty += 15;

        State s = state(d, e, r);
        Component status = switch (s) {
            case CLEARED -> Component.translatable("screen.isekai_mahou.trials.detail_cleared").withStyle(ChatFormatting.GOLD);
            case AVAILABLE -> Component.translatable("screen.isekai_mahou.trials.detail_available").withStyle(ChatFormatting.GREEN);
            case BLOCKED -> Component.translatable("screen.isekai_mahou.trials.detail_blocked").withStyle(ChatFormatting.RED);
        };
        ty = wrap(g, font, status, tx, ty, tw, 0xFFFFFFFF) + 3;

        g.drawString(font, Component.translatable("screen.isekai_mahou.trials.requirements"), tx, ty, MahouUi.GOLD_PALE, false);
        ty += 10;
        ty = req(g, font, tx, ty, tw, d.isAwakened(e), Component.translatable("screen.isekai_mahou.trials.req_awakened", e.displayName()));
        if (r > 1) {
            ty = req(g, font, tx, ty, tw, TrialsApi.isCleared(d, e, r - 1),
                    Component.translatable("screen.isekai_mahou.trials.req_previous", TrialsApi.roman(r - 1)));
        }
        ty = req(g, font, tx, ty, tw, d.elementLevel(e) >= Progression.trialElementLevel(r),
                Component.translatable("screen.isekai_mahou.trials.req_level", Progression.trialElementLevel(r), Math.max(0, d.elementLevel(e))));
        ty = req(g, font, tx, ty, tw, d.circle() >= Progression.trialCircle(r),
                Component.translatable("screen.isekai_mahou.trials.req_circle", Progression.trialCircle(r), d.circle()));
        ty += 3;

        int waves = r <= 2 ? 3 : r <= 4 ? 4 : 5;
        g.drawString(font, Component.translatable("screen.isekai_mahou.trials.challenge"), tx, ty, MahouUi.GOLD_PALE, false);
        ty += 10;
        ty = wrap(g, font, Component.translatable("screen.isekai_mahou.trials.waves_boss", waves, bossLine(e, r)), tx, ty, tw, MahouUi.TEXT) + 3;

        boolean repeat = s == State.CLEARED;
        g.drawString(font, Component.translatable("screen.isekai_mahou.trials.rewards"), tx, ty, MahouUi.GOLD_PALE, false);
        ty += 10;
        int xp = 150 * r, exp = 60 * r;
        if (repeat) {
            xp /= 2;
            exp /= 2;
        }
        ty = wrap(g, font, Component.translatable("screen.isekai_mahou.trials.reward_xp", xp, exp, e.displayName()), tx, ty, tw, MahouUi.TEXT);
        ty = wrap(g, font, Component.translatable(r >= 6 ? "screen.isekai_mahou.trials.reward_items_legend" : "screen.isekai_mahou.trials.reward_items"),
                tx, ty, tw, MahouUi.TEXT_DIM);
        if (!repeat) {
            ty = wrap(g, font, Component.translatable("screen.isekai_mahou.trials.reward_node",
                    TrialTheme.nodeName(r)), tx, ty, tw, 0xFF9FE8FF);
        } else {
            ty = wrap(g, font, Component.translatable("screen.isekai_mahou.trials.reward_repeat"), tx, ty, tw, 0xFF9FE8FF);
        }
        if (TrialClientState.active() && ty < dy + dh - 12) {
            wrap(g, font, Component.translatable("screen.isekai_mahou.trials.in_trial"), tx, ty + 3, tw, 0xFFFFB347);
        }
    }

    private static Component bossLine(Element e, int rank) {
        if (rank <= 4) return Component.translatable("panel.isekai_mahou.trials.boss_avatar");
        if (rank == 6) return Component.translatable("panel.isekai_mahou.trials.boss_sentinels");
        String id = TrialTheme.guardian(e);
        Component name = id == null ? null : TrialTheme.type(id).map(EntityType::getDescription).orElse(null);
        if (name == null) name = Component.translatable("panel.isekai_mahou.trials.boss_avatar");
        if (rank == 5) return e.isFusion() ? Component.translatable("panel.isekai_mahou.trials.boss_avatar") : name;
        return Component.translatable("panel.isekai_mahou.trials.boss_pair", name);
    }

    private static int req(GuiGraphics g, Font font, int x, int y, int w, boolean ok, Component text) {
        g.drawString(font, ok ? "✔" : "✘", x + 2, y, ok ? OK : BAD, false);
        MutableComponent t = text.copy();
        return wrap(g, font, t, x + 12, y, w - 12, ok ? 0xFFCFE8CF : 0xFFE8CFCF);
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int w, int color) {
        for (var line : font.split(text, Math.max(20, w))) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || rows.isEmpty()) return false;
        int colX = gridX + labelW;
        int first = (int) scroll;
        for (int i = first; i < Math.min(rows.size(), first + visibleRows); i++) {
            int ry = gridY + (i - first) * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;
            for (int r = 1; r <= Progression.MAX_TRIAL_RANK; r++) {
                int cx = colX + (r - 1) * cell;
                if (mx >= cx && mx < cx + cell) {
                    select(rows.get(i), r);
                    return true;
                }
            }
            if (mx >= gridX && mx < colX) {
                select(rows.get(i), defaultRank(ClientMagic.data(), rows.get(i)));
                return true;
            }
        }
        return false;
    }

    private void select(Element e, int rank) {
        selected = e;
        selectedRank = rank;
        lastElement = e;
        lastRank = rank;
        updateButton();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.5F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (rows.size() <= visibleRows || mx >= detailX) return false;
        scroll = Mth.clamp(scroll - Math.signum(sy), 0, rows.size() - visibleRows);
        return true;
    }

    @Override
    public void tick() {
        updateButton();
    }
}
