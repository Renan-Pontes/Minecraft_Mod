package com.renanpontes.isekaimahou.feature.class_smith.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithNet;
import com.renanpontes.isekaimahou.feature.class_smith.SmithProgress;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRanks;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Forja" do menu B: as Provas da Forja do Ferreiro. À esquerda os 5 ranks (concluído / pronto / em andamento /
 * bloqueado); à direita o rank escolhido com as tarefas (barras), a oferenda (itens), as recompensas (glifos e itens) e
 * o botão "Oferendar na bigorna" (precisa estar a até 6 blocos de uma Bigorna Arcana). Embaixo, os bônus de classe.
 * Para quem não é Ferreiro, explica a classe e a Forja Arcana (qualquer um pode usar a bigorna).
 */
public class SmithForgeTab implements MenuTab {
    private static final int DONE = 0xFF7CE07C, READY = 0xFFFFD86B;
    private int x, y, w, h, listW, cardH;
    private int selected = 1;
    private Button offer;
    private List<Component> tooltip;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        listW = Mth.clamp((int) (w * 0.32F), 110, 160);
        cardH = Mth.clamp((h - 92) / SmithRanks.MAX_RANK, 20, 34);
        MagicData d = ClientMagic.data();
        int current = d == null ? 0 : SmithProgress.rank(d);
        selected = Math.min(SmithRanks.MAX_RANK, current + 1);
        offer = Button.builder(Component.translatable("screen.isekai_mahou.class_smith.offer"),
                b -> PacketDistributor.sendToServer(new SmithNet.RankUp(selected))).bounds(x + w - 128, y + h - 56, 124, 16).build();
        screen.addTabWidget(offer);
    }

    private enum State { DONE, READY, PROGRESS, LOCKED }

    private static State state(MagicData d, int rank) {
        int current = SmithProgress.rank(d);
        if (rank <= current) return State.DONE;
        if (rank > current + 1) return State.LOCKED;
        SmithRanks.Rank r = SmithRanks.rank(rank);
        return r != null && SmithRanks.tasksDone(d, r) ? State.READY : State.PROGRESS;
    }

    @Override
    public void tick() {
        MagicData d = ClientMagic.data();
        boolean smith = d != null && d.path() == MagicPath.SMITH;
        offer.visible = smith;
        offer.active = smith && state(d, selected) == State.READY;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        tooltip = null;
        g.renderItem(new ItemStack(SmithRegistries.SMITH_HAMMER.get()), x + 6, y + 4);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.tab_title").withStyle(ChatFormatting.BOLD),
                x + 28, y + 4, MahouUi.GOLD, true);
        if (d == null || d.path() != MagicPath.SMITH) {
            renderOutsider(g, font);
            return;
        }
        int current = SmithProgress.rank(d);
        Component sub = current == 0 ? Component.translatable("screen.isekai_mahou.class_smith.rank_none")
                : Component.translatable("screen.isekai_mahou.class_smith.rank_now", SmithRanks.roman(current), SmithRanks.rank(current).title());
        MahouUi.smallText(g, font, sub, x + 28, y + 15, 0.8F, MahouUi.TEXT_DIM, false);
        for (int r = 1; r <= SmithRanks.MAX_RANK; r++) card(g, font, d, r, mx, my);
        detail(g, font, d, mx, my);
        bonuses(g, font, d);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void card(GuiGraphics g, Font font, MagicData d, int rank, int mx, int my) {
        int cx = x + 4, cy = y + 28 + (rank - 1) * cardH, ch = cardH - 3;
        State s = state(d, rank);
        int top, bottom, edge;
        switch (s) {
            case DONE -> { top = 0xE06B5418; bottom = 0xE03A2C0C; edge = MahouUi.GOLD; }
            case READY -> {
                float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 220F);
                top = MahouUi.mix(0xE0402010, 0xE0905020, pulse);
                bottom = 0xE0201008;
                edge = READY;
            }
            case PROGRESS -> { top = 0xE0302030; bottom = 0xE0181020; edge = 0xFFE8742C; }
            default -> { top = 0xC0181418; bottom = 0xC0100C10; edge = 0xFF3A3440; }
        }
        MahouUi.roundRect(g, cx, cy, listW, ch, top, bottom);
        MahouUi.roundOutline(g, cx, cy, listW, ch, rank == selected ? 0xFFFFFFFF : edge);
        int color = s == State.LOCKED ? 0xFF6A6474 : s == State.DONE ? DONE : 0xFFFFE8C0;
        g.drawString(font, SmithRanks.roman(rank), cx + 6, cy + (ch - 8) / 2, edge, true);
        g.drawString(font, MahouUi.fit(font, SmithRanks.rank(rank).title(), listW - 30), cx + 22, cy + (ch - 8) / 2, color, false);
        if (mx >= cx && mx < cx + listW && my >= cy && my < cy + ch) {
            tooltip = List.of(SmithRanks.rank(rank).title(), Component.translatable(SmithKeys.STATE + s.name().toLowerCase())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void detail(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        SmithRanks.Rank r = SmithRanks.rank(selected);
        if (r == null) return;
        int dx = x + listW + 12, dy = y + 28, dw = x + w - dx - 4;
        MahouUi.roundRect(g, dx, dy, dw, h - 92, 0xC0201610, 0xC00E0A08);
        MahouUi.roundOutline(g, dx, dy, dw, h - 92, MahouUi.GOLD_DIM);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.rank_header", SmithRanks.roman(r.number()), r.title()),
                dx + 6, dy + 5, MahouUi.GOLD, false);
        int ty = dy + 18;
        for (SmithRanks.Task t : r.tasks()) {
            boolean ok = t.done(d);
            g.drawString(font, MahouUi.fit(font, Component.literal(ok ? "✔ " : "• ").append(t.text()), dw - 70), dx + 6, ty,
                    ok ? DONE : MahouUi.TEXT, false);
            MahouUi.bar(g, dx + dw - 62, ty + 1, 40, 6, t.value(d) / (float) Math.max(1, t.goal()), ok ? 0xFF5FC85F : 0xFFE8742C);
            MahouUi.smallText(g, font, Component.literal(t.value(d) + "/" + t.goal()), dx + dw - 20, ty + 1, 0.6F, MahouUi.TEXT_DIM, false);
            ty += 11;
        }
        ty += 3;
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.offering"), dx + 6, ty + 4, MahouUi.TEXT_DIM, false);
        int ix = dx + 6 + font.width(Component.translatable("screen.isekai_mahou.class_smith.offering")) + 4;
        for (ItemStack s : r.offering().get()) {
            g.renderItem(s, ix, ty);
            g.renderItemDecorations(font, s, ix, ty);
            if (mx >= ix && mx < ix + 16 && my >= ty && my < ty + 16) tooltip = List.of(s.getHoverName());
            ix += 18;
        }
        ty += 20;
        List<Component> rewards = new ArrayList<>();
        for (String id : r.glyphs()) {
            Glyph gl = GlyphRegistry.get(IsekaiMahou.id(id));
            if (gl != null) rewards.add(gl.displayName());
        }
        Component line = Component.translatable("screen.isekai_mahou.class_smith.rewards", r.xp(),
                rewards.isEmpty() ? Component.translatable("screen.isekai_mahou.class_smith.rewards_items")
                        : joined(rewards));
        for (FormattedCharSequence seq : font.split(line, dw - 12)) {
            g.drawString(font, seq, dx + 6, ty, 0xFFB8E0FF, false);
            ty += 10;
        }
        for (FormattedCharSequence seq : font.split(Component.translatable(SmithKeys.REWARD_ITEMS + r.number()), dw - 12)) {
            g.drawString(font, seq, dx + 6, ty, MahouUi.TEXT_DIM, false);
            ty += 10;
        }
    }

    private static Component joined(List<Component> parts) {
        var out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }

    private void bonuses(GuiGraphics g, Font font, MagicData d) {
        int by = y + h - 60;
        int rank = SmithProgress.rank(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.bonus_title"), x + 4, by, MahouUi.GOLD, false);
        String[] keys = {"damage", "repair", "wear", "fire", "quality"};
        Object[][] args = {{10 + 3 * rank}, {1 + rank / 2}, {50}, {15}, {Math.min(5, 1 + rank)}};
        int ly = by + 11;
        for (int i = 0; i < keys.length; i++) {
            MahouUi.smallText(g, font, Component.translatable(SmithKeys.BONUS + keys[i], args[i]),
                    x + 8, ly, 0.75F, MahouUi.TEXT, false);
            ly += 8;
        }
    }

    private void renderOutsider(GuiGraphics g, Font font) {
        int bw = Math.min(w - 20, 340), bx = x + (w - bw) / 2, by = y + 40;
        MahouUi.roundRect(g, bx, by, bw, 110, 0xC0201810, 0xC0100A08);
        MahouUi.roundOutline(g, bx, by, bw, 110, MahouUi.GOLD_DIM);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.class_smith.only_smith"), bx + bw / 2, by + 10, MahouUi.GOLD);
        int ly = by + 26;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.class_smith.only_smith_hint"), bw - 16)) {
            g.drawString(font, line, bx + 8, ly, MahouUi.TEXT_DIM, false);
            ly += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int r = 1; r <= SmithRanks.MAX_RANK; r++) {
            int cx = x + 4, cy = y + 28 + (r - 1) * cardH;
            if (mx >= cx && mx < cx + listW && my >= cy && my < cy + cardH - 3) {
                selected = r;
                return true;
            }
        }
        return false;
    }
}
