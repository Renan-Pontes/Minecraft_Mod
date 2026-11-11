package com.renanpontes.isekaimahou.feature.class_quincy.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyEvents;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyNetwork;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyRanks;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyTechniques;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Quincy" do menu B. À esquerda: rank do Auswählen, Reishi guardado, chance de absorção, Letra e bônus da
 * classe. À direita (rola): a escolha da Letra (no rank III), os 5 ranks com requisitos/progresso/recompensas e o
 * botão "Realizar o Auswählen" do próximo, as TÉCNICAS AVANÇADAS (Heilig Bogen, Sankt Zwinger, A Antítese, Letzt Stil,
 * O Todo-Poderoso — requisitos e progresso) e o guia de como absorver outros Quincy.
 */
public class QuincyTab implements MenuTab {
    private static final int GREEN = 0xFF7BE07A;
    /** Prefixos das linhas numeradas (bônus 1–4 e guia 1–5). */
    private static final String BONUS_KEY = "screen.isekai_mahou.quincy.bonus.";
    private static final String GUIDE_KEY = "screen.isekai_mahou.quincy.guide.";
    private static final int BLUE = 0xFF000000 | Quincy.RGB;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Tip(int x, int y, int w, int h, List<Component> lines) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    private int x, y, w, h, listW;
    private int scroll, maxScroll;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Math.max(130, Math.min(176, (int) (w * 0.36F)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        tips.clear();
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        if (d == null) return;
        renderSide(g, font, d);
        renderPanel(g, font, d, mx, my);
        for (Tip t : tips) {
            if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component c : t.lines()) lines.addAll(font.split(c, 210));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ coluna da esquerda
    private void renderSide(GuiGraphics g, Font font, MagicData d) {
        MahouUi.frame(g, x, y, listW, h, BLUE);
        int rank = Quincy.rank(d);
        boolean quincy = d.path() == MagicPath.QUINCY;
        g.drawString(font, MagicPath.QUINCY.displayName().withStyle(ChatFormatting.BOLD), x + 7, y + 6, BLUE, true);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.quincy.rank_line", Quincy.roman(rank), Quincy.rankTitle(rank)),
                x + 7, y + 18, 0.8F, MahouUi.GOLD_PALE, false);
        for (int i = 0; i < Quincy.MAX_RANK; i++) {
            int cx = x + listW - 12 - (Quincy.MAX_RANK - 1 - i) * 10;
            MahouUi.diamond(g, cx, y + 10, 4, i < rank ? BLUE : 0x50FFFFFF);
            if (i < rank) MahouUi.diamond(g, cx, y + 10, 2, 0xFFFFFFFF);
        }
        int ly = y + 32;
        int stored = Quincy.reishi(d), cap = Quincy.reishiCap(d);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.quincy.reishi", stored, cap), listW - 14), x + 7, ly,
                MahouUi.TEXT, false);
        ly += 11;
        MahouUi.bar(g, x + 7, ly, listW - 14, 5, stored / (float) Math.max(1, cap), BLUE);
        ly += 10;
        if (quincy) {
            int chance = Math.round(QuincyEvents.absorbChance(d) * 100);
            ly = wrap(g, font, Component.translatable("screen.isekai_mahou.quincy.absorb_chance", chance), x + 7, ly, listW - 14, MahouUi.TEXT_DIM, 0);
            ly = wrap(g, font, Component.translatable("screen.isekai_mahou.quincy.power", Math.round((Quincy.rankPower(d) - 1) * 100)),
                    x + 7, ly, listW - 14, MahouUi.TEXT_DIM, 0);
        }
        int powerless = QuincyTechniques.powerless(d);
        if (powerless > 0) {
            ly = wrap(g, font, Component.translatable("screen.isekai_mahou.quincy.powerless",
                    String.format("%d:%02d", powerless / 60, powerless % 60)), x + 7, ly, listW - 14, 0xFFB03040, 0);
        }
        ly += 4;
        Quincy.Letter letter = Quincy.letter(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.letter").withStyle(ChatFormatting.GOLD), x + 7, ly, MahouUi.GOLD, false);
        ly += 11;
        if (letter != null) {
            g.drawString(font, MahouUi.fit(font, Component.literal(letter.symbol + " · ").append(letter.displayName()), listW - 14), x + 7, ly,
                    0xFF000000 | letter.rgb, true);
            ly += 11;
            ly = wrap(g, font, letter.passive(), x + 7, ly, listW - 14, MahouUi.TEXT_DIM, 0);
        } else {
            ly = wrap(g, font, Component.translatable(rank >= 3 ? "screen.isekai_mahou.quincy.letter_pick" : "screen.isekai_mahou.quincy.letter_locked"),
                    x + 7, ly, listW - 14, MahouUi.TEXT_DIM, 0);
        }
        ly += 4;
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.bonuses").withStyle(ChatFormatting.GOLD), x + 7, ly, MahouUi.GOLD, false);
        ly += 11;
        for (int i = 1; i <= 4 && ly < y + h - 10; i++) {
            ly = wrap(g, font, Component.literal("• ").append(Component.translatable(BONUS_KEY + i)), x + 7, ly, listW - 14,
                    MahouUi.TEXT_DIM, y + h - 6);
        }
        if (!quincy && ly < y + h - 20) {
            wrap(g, font, Component.translatable("screen.isekai_mahou.quincy.not_quincy").withStyle(ChatFormatting.ITALIC), x + 7, ly + 2,
                    listW - 14, 0xFFE0A070, y + h - 6);
        }
    }

    /** Texto com quebra de linha; devolve o y seguinte (corta em {@code limit} se > 0). */
    private static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int limit) {
        for (FormattedCharSequence line : font.split(text, width)) {
            if (limit > 0 && y + 9 > limit) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static int wrapHeight(Font font, Component text, int width) {
        return font.split(text, width).size() * 10;
    }

    // ------------------------------------------------------------------ painel da direita
    private void renderPanel(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int px = x + listW + 6, pw = w - listW - 6;
        MahouUi.frame(g, px, y, pw, h, BLUE);
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.title").withStyle(ChatFormatting.BOLD), px + 8, y + 6, BLUE, true);
        int top = y + 20, bottom = y + h - 4;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int cy = top - scroll;
        boolean quincy = d.path() == MagicPath.QUINCY;
        int rank = Quincy.rank(d);
        if (quincy && rank >= 3 && Quincy.letter(d) == null) cy += letters(g, font, px + 6, cy, pw - 12, mx, my, top, bottom) + 4;
        for (QuincyRanks.Rank r : QuincyRanks.ranks()) cy += card(g, font, d, r, px + 6, cy, pw - 12, quincy, rank, mx, my, top, bottom) + 4;
        cy += techniques(g, font, d, px + 6, cy, pw - 12, top, bottom) + 4;
        cy += guide(g, font, px + 6, cy, pw - 12);
        g.disableScissor();
        int contentH = cy + scroll - top;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int barH = Math.max(12, (bottom - top) * (bottom - top) / Math.max(1, contentH));
            int barY = top + (int) ((bottom - top - barH) * (scroll / (float) maxScroll));
            g.fill(px + pw - 4, top, px + pw - 2, bottom, 0x40000000);
            g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, BLUE);
        }
    }

    /** Escolha da Letra: 5 cartões (símbolo, nome, poder e passiva). */
    private int letters(GuiGraphics g, Font font, int cx, int cy, int cw, int mx, int my, int clipTop, int clipBottom) {
        int start = cy;
        MahouUi.roundRect(g, cx, cy, cw, 14, MahouUi.alpha(BLUE, 0x40), 0x60000000);
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.choose_letter").withStyle(ChatFormatting.BOLD), cx + 6, cy + 3,
                MahouUi.GOLD, true);
        cy += 18;
        for (Quincy.Letter l : Quincy.Letter.values()) {
            int textW = cw - 40 - 64;
            int ch = Math.max(30, 12 + wrapHeight(font, l.description(), textW));
            int color = 0xFF000000 | l.rgb;
            MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(color, 0x28), 0x70000000);
            MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(color, 0xB0));
            g.pose().pushPose();
            g.pose().translate(cx + 8, cy + 6, 0);
            g.pose().scale(2F, 2F, 1F);
            g.drawString(font, String.valueOf(l.symbol), 0, 0, color, true);
            g.pose().popPose();
            g.drawString(font, l.displayName().withStyle(ChatFormatting.BOLD), cx + 32, cy + 3, color, true);
            wrap(g, font, l.description(), cx + 32, cy + 14, textW, MahouUi.TEXT_DIM, 0);
            final Quincy.Letter pick = l;
            button(g, font, cx + cw - 64, cy + (ch - 15) / 2, 58, 15, Component.translatable("screen.isekai_mahou.quincy.pick"), true, color,
                    mx, my, clipTop, clipBottom, () -> PacketDistributor.sendToServer(new QuincyNetwork.ChooseLetter(pick.ordinal())),
                    List.of(l.passive().withStyle(ChatFormatting.GRAY)));
            cy += ch + 3;
        }
        return cy - start;
    }

    /** Um rank: título, estado, requisitos (barras), recompensas e o botão. Devolve a altura. */
    private int card(GuiGraphics g, Font font, MagicData d, QuincyRanks.Rank r, int cx, int cy, int cw, boolean quincy, int done,
                     int mx, int my, int clipTop, int clipBottom) {
        int n = r.number();
        boolean complete = n <= done;
        boolean next = n == done + 1;
        boolean showButton = quincy && next;
        int lines = r.reqs().size() + 1 + (n == 4 ? 1 : 0);
        int ch = 16 + lines * 11 + 13 + (showButton ? 19 : 0) + 3;
        int accent = complete ? MahouUi.GOLD : next ? BLUE : 0xFF6A6478;
        MahouUi.roundRect(g, cx, cy, cw, ch, complete ? 0x50403010 : next ? MahouUi.alpha(BLUE, 0x28) : 0x40000000, 0x70000000);
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(accent, 0xB0));
        MutableComponent title = Component.literal(Quincy.roman(n) + " · ").append(Quincy.rankTitle(n));
        g.drawString(font, MahouUi.fit(font, title.withStyle(ChatFormatting.BOLD), cw - 80), cx + 6, cy + 4, accent, true);
        Component state = Component.translatable(complete ? "screen.isekai_mahou.quincy.done"
                : next ? "screen.isekai_mahou.quincy.next" : "screen.isekai_mahou.quincy.locked");
        g.drawString(font, state, cx + cw - 6 - font.width(state), cy + 4, complete ? GREEN : next ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
        int ly = cy + 16;
        boolean circleOk = complete || d.circle() >= r.circle();
        check(g, cx + 6, ly, circleOk);
        g.drawString(font, MahouUi.fit(font, Component.translatable("quincy.isekai_mahou.req.circle", r.circle()), cw - 24), cx + 16, ly,
                circleOk ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
        ly += 11;
        for (QuincyRanks.Req q : r.reqs()) {
            int cur = Math.min(q.current().applyAsInt(d), q.amount());
            boolean ok = complete || cur >= q.amount();
            check(g, cx + 6, ly, ok);
            int barW = Math.min(60, cw / 5);
            g.drawString(font, MahouUi.fit(font, q.label(), cw - barW - 60), cx + 16, ly, ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, cx + cw - barW - 40, ly + 2, barW, 4, complete ? 1F : cur / (float) q.amount(), ok ? GREEN : BLUE);
            String num = complete ? "✔" : cur + "/" + q.amount();
            g.drawString(font, num, cx + cw - 6 - font.width(num), ly, ok ? GREEN : MahouUi.TEXT_DIM, false);
            ly += 11;
        }
        if (n == 4) {
            boolean ok = complete || Quincy.letter(d) != null;
            check(g, cx + 6, ly, ok);
            g.drawString(font, MahouUi.fit(font, Component.translatable("quincy.isekai_mahou.req.letter"), cw - 24), cx + 16, ly,
                    ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            ly += 11;
        }
        // recompensas: glifos (ícones com dica), itens, lenda e XP
        int rx = cx + 6;
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.reward").withStyle(ChatFormatting.GOLD), rx, ly + 1, MahouUi.GOLD, false);
        rx += font.width(Component.translatable("screen.isekai_mahou.quincy.reward")) + 4;
        for (String id : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(IsekaiMahou.id(id));
            if (glyph == null) continue;
            GlyphArt.glyph(g, glyph, rx, ly, 10);
            if (ly >= clipTop && ly + 10 <= clipBottom) {
                tips.add(new Tip(rx, ly, 10, 10, List.of(glyph.displayName().withColor(Quincy.RGB), glyph.description().withStyle(ChatFormatting.GRAY))));
            }
            rx += 12;
        }
        for (ItemStack it : r.items()) {
            g.pose().pushPose();
            g.pose().translate(rx, ly - 1, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.renderItem(it, 0, 0);
            g.renderItemDecorations(font, it, 0, 0);
            g.pose().popPose();
            if (ly >= clipTop && ly + 12 <= clipBottom) tips.add(new Tip(rx, ly - 1, 12, 12, List.of(it.getHoverName())));
            rx += 14;
        }
        MutableComponent extra = Component.translatable("screen.isekai_mahou.quincy.reward_xp", r.xp()).withStyle(ChatFormatting.AQUA);
        if (r.legend() != null) extra = Component.translatable("screen.isekai_mahou.quincy.reward_legend").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(" ").append(extra);
        g.drawString(font, MahouUi.fit(font, extra, cx + cw - rx - 6), rx + 2, ly + 1, MahouUi.TEXT, false);
        ly += 13;
        if (showButton) {
            List<Component> missing = new ArrayList<>();
            boolean can = QuincyRanks.meets(d, r, missing);
            List<Component> tip = can ? List.of(Component.translatable("screen.isekai_mahou.quincy.rank_up_tip").withStyle(ChatFormatting.GRAY)) : missing;
            int bw = Math.min(170, cw - 12);
            button(g, font, cx + 6, ly + 1, bw, 15, Component.translatable("screen.isekai_mahou.quincy.rank_up", Quincy.roman(n)), can, BLUE,
                    mx, my, clipTop, clipBottom, () -> PacketDistributor.sendToServer(new QuincyNetwork.RankUp()), tip);
        }
        return ch;
    }

    /** As técnicas avançadas: ícone, nome, estado, como chega e o progresso de cada requisito. */
    private int techniques(GuiGraphics g, Font font, MagicData d, int cx, int cy, int cw, int clipTop, int clipBottom) {
        int start = cy;
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.techniques").withStyle(ChatFormatting.BOLD), cx + 2, cy,
                MahouUi.GOLD, true);
        cy += 12;
        for (QuincyTechniques.Technique t : QuincyTechniques.all()) {
            boolean known = t.known(d);
            boolean ready = !known && t.ready(d);
            int color = 0xFF000000 | t.rgb();
            int textW = cw - 12;
            int howH = wrapHeight(font, t.howText(), textW);
            int lines = known ? 0 : 1 + t.reqs().size();
            int ch = 16 + howH + lines * 11 + 3;
            MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(color, known ? 0x38 : 0x18), 0x70000000);
            MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(color, known ? 0xFF : ready ? 0xC0 : 0x60));
            Glyph glyph = t.glyphObj();
            int ix = cx + 5;
            if (glyph != null) {
                GlyphArt.glyph(g, glyph, ix, cy + 3, 10);
                if (cy + 3 >= clipTop && cy + 13 <= clipBottom) {
                    tips.add(new Tip(ix, cy + 3, 10, 10, List.of(glyph.displayName().withColor(t.rgb()), glyph.description().withStyle(ChatFormatting.GRAY))));
                }
                ix += 12;
            }
            Component name = glyph == null ? Component.literal(t.id()) : glyph.displayName().withStyle(ChatFormatting.BOLD);
            Component state = Component.translatable(known ? "screen.isekai_mahou.quincy.tech_known"
                    : ready ? "screen.isekai_mahou.quincy.tech_ready" : "screen.isekai_mahou.quincy.locked");
            g.drawString(font, MahouUi.fit(font, name, cw - (ix - cx) - font.width(state) - 12), ix + 2, cy + 4, color, true);
            g.drawString(font, state, cx + cw - 6 - font.width(state), cy + 4, known ? GREEN : ready ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
            int ly = wrap(g, font, t.howText(), cx + 6, cy + 16, textW, MahouUi.TEXT_DIM, 0);
            if (!known) {
                boolean rankOk = Quincy.rank(d) >= t.rank();
                check(g, cx + 6, ly, rankOk);
                g.drawString(font, MahouUi.fit(font, Component.translatable("quincy.isekai_mahou.req.rank", Quincy.roman(t.rank())), cw - 24),
                        cx + 16, ly, rankOk ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
                ly += 11;
                for (QuincyRanks.Req q : t.reqs()) {
                    int cur = Math.min(q.current().applyAsInt(d), q.amount());
                    boolean ok = cur >= q.amount();
                    check(g, cx + 6, ly, ok);
                    int barW = Math.min(60, cw / 5);
                    g.drawString(font, MahouUi.fit(font, q.label(), cw - barW - 60), cx + 16, ly, ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
                    MahouUi.bar(g, cx + cw - barW - 40, ly + 2, barW, 4, cur / (float) q.amount(), ok ? GREEN : BLUE);
                    String num = cur + "/" + q.amount();
                    g.drawString(font, num, cx + cw - 6 - font.width(num), ly, ok ? GREEN : MahouUi.TEXT_DIM, false);
                    ly += 11;
                }
            }
            cy += ch + 3;
        }
        return cy - start;
    }

    /** Guia do Auswählen e das técnicas (texto). */
    private int guide(GuiGraphics g, Font font, int cx, int cy, int cw) {
        int start = cy;
        g.drawString(font, Component.translatable("screen.isekai_mahou.quincy.guide").withStyle(ChatFormatting.BOLD), cx + 2, cy, MahouUi.GOLD, true);
        cy += 12;
        for (int i = 1; i <= 5; i++) {
            cy = wrap(g, font, Component.literal("• ").append(Component.translatable(GUIDE_KEY + i)), cx + 2, cy, cw - 4,
                    MahouUi.TEXT_DIM, 0) + 2;
        }
        return cy - start;
    }

    private static void check(GuiGraphics g, int x, int y, boolean ok) {
        if (ok) {
            g.fill(x + 1, y + 4, x + 3, y + 6, GREEN);
            g.fill(x + 3, y + 5, x + 4, y + 7, GREEN);
            g.fill(x + 4, y + 2, x + 5, y + 6, GREEN);
            g.fill(x + 5, y + 1, x + 6, y + 3, GREEN);
        } else {
            MahouUi.diamond(g, x + 3, y + 4, 2, 0x80FFFFFF);
        }
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, boolean enabled, int color, int mx, int my,
                        int clipTop, int clipBottom, Runnable action, List<Component> tip) {
        boolean hover = enabled && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int base = enabled ? color : 0xFF555060;
        MahouUi.roundRect(g, bx, by, bw, bh, MahouUi.alpha(MahouUi.lighten(base, hover ? 0.3F : 0F), 0xD0), MahouUi.alpha(MahouUi.darken(base, 0.5F), 0xD0));
        MahouUi.roundOutline(g, bx, by, bw, bh, enabled ? 0xFFFFFFFF : 0xFF3A3640);
        FormattedCharSequence t = MahouUi.fit(font, label, bw - 6);
        g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + 4, enabled ? 0xFFFFFFFF : 0xFF9A96A6, true);
        boolean visible = by >= clipTop && by + bh <= clipBottom;
        if (enabled && visible) hits.add(new Hit(bx, by, bw, bh, action));
        if (!tip.isEmpty() && visible) tips.add(new Tip(bx, by, bw, bh, tip));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : hits) {
            if (hit.in(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < x + listW || maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        return true;
    }
}
