package com.renanpontes.isekaimahou.feature.class_knight.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.feature.class_knight.RuneData;
import com.renanpontes.isekaimahou.feature.class_knight.oath.KnightOaths;
import com.renanpontes.isekaimahou.feature.class_knight.oath.Offer;
import com.renanpontes.isekaimahou.feature.class_knight.oath.Req;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Aba "Juramentos" (menu B): à esquerda os 5 Juramentos do Cavaleiro Rúnico (feito / pronto / em andamento /
 * bloqueado), à direita o escolhido — requisitos com barras, oferenda (tem/precisa) e recompensas — e embaixo as 6
 * Espadas Lendárias (despertada / pronta / bloqueada; passe o mouse para ver o feito). A cerimônia é no Altar.
 */
public class OathTab implements MenuTab {
    private static final int OK = 0xFF7CE07C, MUTED = 0xFF8A8796, READY = 0xFFFFD86B, BAD = 0xFFE06060;
    private static int lastSelected;

    private int x, y, w, h;
    private int listX, listY, listW, cardH, detailX, detailW;
    private int swordsY;
    private int selected = 1;
    private List<Component> tooltip;

    private enum State { DONE, READY, PROGRESS, LOCKED }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        listX = x + 4;
        listY = y + 28;
        listW = Mth.clamp((int) (w * 0.32F), 112, 160);
        swordsY = y + h - 30;
        cardH = Mth.clamp((swordsY - 6 - listY) / KnightOaths.MAX_RANK, 18, 40);
        detailX = listX + listW + 8;
        detailW = x + w - detailX - 4;
        MagicData d = ClientMagic.data();
        int cur = d == null ? 0 : KnightOaths.rank(d);
        selected = lastSelected > 0 ? lastSelected : Math.min(KnightOaths.MAX_RANK, cur + 1);
    }

    private State state(MagicData d, int rank) {
        int cur = KnightOaths.rank(d);
        if (rank <= cur) return State.DONE;
        if (rank > cur + 1) return State.LOCKED;
        return KnightOaths.RANKS.get(rank - 1).reqsMet(d) ? State.READY : State.PROGRESS;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        tooltip = null;
        if (d == null || d.path() != MagicPath.KNIGHT) {
            renderOther(g, font);
            return;
        }
        renderHeader(g, font, d);
        for (int r = 1; r <= KnightOaths.MAX_RANK; r++) renderCard(g, font, d, r, mx, my);
        renderDetail(g, font, d);
        renderSwords(g, font, d, mx, my);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void renderOther(GuiGraphics g, Font font) {
        int bw = Math.min(w - 20, 330), bx = x + (w - bw) / 2, by = y + h / 2 - 40;
        MahouUi.roundRect(g, bx, by, bw, 80, 0xC0201830, 0xC0100A1A);
        MahouUi.roundOutline(g, bx, by, bw, 80, MahouUi.GOLD_DIM);
        g.renderItem(new ItemStack(KnightRegistries.LEGENDS.get(LegendSword.LAKE).get()), bx + 8, by + 8);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_knight.only_knight"), bx + 30, by + 12, MahouUi.GOLD, false);
        int ly = by + 30;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.class_knight.only_knight_hint"), bw - 16)) {
            if (ly > by + 72) break;
            g.drawString(font, line, bx + 8, ly, MahouUi.TEXT_DIM, false);
            ly += 10;
        }
    }

    private void renderHeader(GuiGraphics g, Font font, MagicData d) {
        int cur = KnightOaths.rank(d);
        g.renderItem(new ItemStack(KnightRegistries.OATH_ALTAR_ITEM.get()), x + 6, y + 4);
        Component title = Component.translatable("screen.isekai_mahou.class_knight.title").withStyle(ChatFormatting.BOLD);
        g.drawString(font, MahouUi.fit(font, title, w - 140), x + 28, y + 3, MahouUi.GOLD, true);
        Component sub = cur == 0 ? Component.translatable("screen.isekai_mahou.class_knight.rank_none")
                : Component.translatable("screen.isekai_mahou.class_knight.rank_now", KnightOaths.roman(cur),
                KnightOaths.RANKS.get(cur - 1).title());
        MahouUi.smallText(g, font, sub, x + 28, y + 15, 0.8F, MahouUi.TEXT_DIM, false);
        // lâmina na mão: runas gravadas
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            ItemStack held = p.getMainHandItem();
            int slots = KnightSwords.runeSlots(held, d);
            Component line = slots <= 0 ? Component.translatable("screen.isekai_mahou.class_knight.no_blade")
                    : Component.translatable("screen.isekai_mahou.class_knight.blade_runes", KnightSwords.runes(held).size(), slots);
            int lw = (int) (font.width(line) * 0.8F);
            MahouUi.smallText(g, font, line, x + w - lw - 6, y + 6, 0.8F, slots <= 0 ? MUTED : 0xFF8FD8FF, false);
            RuneData rd = KnightSwords.runes(held);
            if (!rd.isEmpty()) {
                // nomes das runas gravadas, na ordem do rodízio
                StringBuilder names = new StringBuilder();
                for (var r : rd.runes()) {
                    if (names.length() > 0) names.append(" · ");
                    names.append(r.name().isBlank() ? "?" : r.name());
                }
                Component nxt = Component.translatable("screen.isekai_mahou.class_knight.next_rune", names.toString());
                MahouUi.smallText(g, font, Component.literal("").append(nxt), x + w - Math.min(w / 2, (int) (font.width(nxt) * 0.7F)) - 6,
                        y + 16, 0.7F, MahouUi.TEXT_DIM, false);
            }
        }
    }

    private void renderCard(GuiGraphics g, Font font, MagicData d, int rank, int mx, int my) {
        int cy = listY + (rank - 1) * cardH;
        int ch = cardH - 3;
        State s = state(d, rank);
        int top, bottom, edge;
        switch (s) {
            case DONE -> { top = 0xE06B5418; bottom = 0xE03A2C0C; edge = MahouUi.GOLD; }
            case READY -> {
                float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 220F);
                top = MahouUi.mix(0xE0403010, 0xE0806020, pulse);
                bottom = 0xE0201008;
                edge = MahouUi.mix(MahouUi.GOLD_DIM, MahouUi.GOLD, pulse);
            }
            case PROGRESS -> { top = 0xE0243050; bottom = 0xE0121828; edge = 0xFF7FA8FF; }
            default -> { top = 0xC0181820; bottom = 0xC0101014; edge = 0xFF3A3A48; }
        }
        boolean hover = mx >= listX && mx < listX + listW && my >= cy && my < cy + ch;
        if (rank == selected) edge = MahouUi.GOLD_PALE;
        MahouUi.roundRect(g, listX, cy, listW, ch, hover ? MahouUi.lighten(top, 0.1F) : top, bottom);
        MahouUi.roundOutline(g, listX, cy, listW, ch, edge);
        Component num = Component.literal(KnightOaths.roman(rank)).withStyle(ChatFormatting.BOLD);
        g.drawString(font, num, listX + 5, cy + (ch - 8) / 2, s == State.LOCKED ? MUTED : MahouUi.GOLD, true);
        Component name = KnightOaths.RANKS.get(rank - 1).title();
        int tx = listX + 24;
        g.drawString(font, MahouUi.fit(font, name, listW - 28), tx, cy + (ch >= 26 ? 4 : (ch - 8) / 2),
                s == State.LOCKED ? MUTED : MahouUi.TEXT, false);
        if (ch >= 26) {
            Component st = com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("screen", "state", s.name().toLowerCase(java.util.Locale.ROOT));
            MahouUi.smallText(g, font, st, tx, cy + 15, 0.7F, s == State.DONE ? OK : s == State.READY ? READY : MUTED, false);
        }
    }

    private void renderDetail(GuiGraphics g, Font font, MagicData d) {
        int dy = listY, dh = swordsY - 6 - listY;
        MahouUi.roundRect(g, detailX, dy, detailW, dh, 0xC0181226, 0xC00B0912);
        MahouUi.roundOutline(g, detailX, dy, detailW, dh, MahouUi.GOLD_DIM);
        KnightOaths.Rank r = KnightOaths.RANKS.get(selected - 1);
        State s = state(d, selected);
        int ix = detailX + 6, iw = detailW - 12;
        int ly = dy + 5;
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_knight.oath_head",
                KnightOaths.roman(selected), r.title()).withStyle(ChatFormatting.BOLD), iw), ix, ly, MahouUi.GOLD, false);
        ly += 12;
        for (FormattedCharSequence line : font.split(com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("oath", selected, "desc"), iw)) {
            if (ly > dy + dh - 10) return;
            g.drawString(font, line, ix, ly, MahouUi.TEXT_DIM, false);
            ly += 9;
        }
        ly += 3;
        // requisitos
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_knight.reqs"), ix, ly, 0.75F, MahouUi.GOLD_PALE, false);
        ly += 9;
        for (Req q : r.reqs()) {
            if (ly > dy + dh - 10) return;
            boolean met = s == State.DONE || q.met(d);
            MahouUi.smallText(g, font, q.text(d), ix + 2, ly, 0.75F, met ? OK : MahouUi.TEXT, false);
            int bx = ix + iw - 56;
            MahouUi.bar(g, bx, ly + 2, 54, 3, s == State.DONE ? 1F : q.fraction(d), met ? OK : 0xFF7FA8FF);
            ly += 9;
        }
        ly += 3;
        if (ly > dy + dh - 10) return;
        // oferenda
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_knight.offering"), ix, ly, 0.75F, MahouUi.GOLD_PALE, false);
        ly += 9;
        LocalPlayer p = Minecraft.getInstance().player;
        int ox = ix;
        for (Offer o : r.offers()) {
            if (!o.available()) continue;
            if (ox + 34 > ix + iw) {
                ox = ix;
                ly += 18;
            }
            if (ly > dy + dh - 18) return;
            g.renderItem(o.icon(), ox, ly);
            int have = p == null ? 0 : o.have(p);
            Component cnt = Component.literal(Math.min(have, o.count()) + "/" + o.count());
            MahouUi.smallText(g, font, cnt, ox + 17, ly + 8, 0.6F, have >= o.count() || s == State.DONE ? OK : BAD, false);
            ox += 36;
        }
        ly += 20;
        if (ly > dy + dh - 10) return;
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_knight.reward"), ix, ly, 0.75F, MahouUi.GOLD_PALE, false);
        ly += 9;
        for (FormattedCharSequence line : font.split(r.reward(), (int) (iw / 0.75F))) {
            if (ly > dy + dh - 8) return;
            g.pose().pushPose();
            g.pose().translate(ix + 2, ly, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, line, 0, 0, MahouUi.TEXT, false);
            g.pose().popPose();
            ly += 8;
        }
        if (s == State.READY && ly <= dy + dh - 10) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_knight.go_altar"), ix, ly + 2, 0.75F, READY, false);
        }
    }

    private void renderSwords(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        LegendSword[] all = LegendSword.values();
        int gap = 4, size = 22;
        int total = all.length * size + (all.length - 1) * gap;
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_knight.swords",
                d.counter(com.renanpontes.isekaimahou.feature.class_knight.KnightCounters.LEGEND_SWORDS)), listX, swordsY + 8, 0.8F, MahouUi.GOLD, false);
        int sx = x + w - total - 6;
        for (int i = 0; i < all.length; i++) {
            LegendSword s = all[i];
            int bx = sx + i * (size + gap), by = swordsY + 2;
            boolean awake = d.hasFlag(s.flag());
            boolean ready = !awake && KnightOaths.reqsMet(d, s);
            int edge = awake ? MahouUi.GOLD : ready ? MahouUi.mix(MahouUi.GOLD_DIM, MahouUi.GOLD, 0.5F + 0.5F * Mth.sin(Util.getMillis() / 200F))
                    : 0xFF3A3A48;
            MahouUi.roundRect(g, bx, by, size, size, awake ? 0xE0302010 : 0xC0141420, 0xC00A0A10);
            MahouUi.roundOutline(g, bx, by, size, size, edge);
            g.renderItem(new ItemStack(KnightRegistries.LEGENDS.get(s).get()), bx + 3, by + 3);
            if (!awake) g.fill(bx + 1, by + 1, bx + size - 1, by + size - 1, ready ? 0x40000000 : 0x90000000);
            if (mx >= bx && mx < bx + size && my >= by && my < by + size) tooltip = swordTooltip(d, s, awake);
        }
    }

    private static List<Component> swordTooltip(MagicData d, LegendSword s, boolean awake) {
        List<Component> t = new ArrayList<>();
        t.add(s.displayName().withStyle(ChatFormatting.BOLD));
        t.add(s.featName().withStyle(ChatFormatting.GOLD));
        t.add(s.featHint().withStyle(ChatFormatting.GRAY));
        if (awake) {
            t.add(Component.translatable("screen.isekai_mahou.class_knight.sword_awake").withStyle(ChatFormatting.GREEN));
        } else {
            for (Req q : s.reqs) t.add(Component.literal(q.met(d) ? "✔ " : "✘ ").append(q.text(d))
                    .withStyle(q.met(d) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (s.site != LegendSword.Site.NONE) t.add(Component.translatable("screen.isekai_mahou.class_knight.site", s.site.text())
                    .withStyle(ChatFormatting.AQUA));
            for (Offer o : s.offers) {
                if (o.available()) t.add(Component.literal("• " + o.count() + "× ").append(o.item().getDescription())
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        t.add(Component.translatable("tooltip.isekai_mahou.class_knight.legend_release", s.abilityName()).withStyle(ChatFormatting.YELLOW));
        return t;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int r = 1; r <= KnightOaths.MAX_RANK; r++) {
            int cy = listY + (r - 1) * cardH;
            if (mx >= listX && mx < listX + listW && my >= cy && my < cy + cardH - 3) {
                selected = r;
                lastSelected = r;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }
}
