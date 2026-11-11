package com.renanpontes.isekaimahou.feature.class_gambler.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfits;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Mesa da Casa" do menu B: à esquerda a SORTE (pontos, Tensão, esquiva, valor médio e a tabela de chances de cada
 * faixa com a sua Sorte atual); à direita as 5 Fichas da Casa (requisitos com barras, recompensas e o botão da Rodada
 * da Casa com a aposta). Para outras classes mostra só as chances (quem usa glifo do Apostador por Lição também rola).
 */
public class CasinoTab implements MenuTab {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    private static final int GREEN = 0xFF7BE07A;
    private static final int LEFT_W = 150;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Tip(int x, int y, int w, int h, List<Component> lines) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, maxScroll;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        tips.clear();
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        renderLuck(g, font, d);
        renderRanks(g, font, d, mx, my);
        for (Tip t : tips) {
            if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component c : t.lines()) lines.addAll(font.split(c, 220));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

    /** Mesma conta do servidor ({@link LuckService#luck}), com os dados sincronizados do cliente. */
    private static int clientLuck(MagicData d) {
        LocalPlayer p = Minecraft.getInstance().player;
        int l = 0;
        if (d.path() == MagicPath.GAMBLER) {
            l += 1 + HouseRanks.rank(d);
            if (p != null && GamblerOutfits.fullSet(p)) l += 1;
        }
        if (p != null) {
            MobEffectInstance lucky = p.getEffect(GbRegistries.LUCKY);
            if (lucky != null) l += 1 + Math.min(2, lucky.getAmplifier());
            if (p.hasEffect(GbRegistries.JINXED)) l -= 2;
        }
        return l;
    }

    // ------------------------------------------------------------------ Sorte
    private void renderLuck(GuiGraphics g, Font font, MagicData d) {
        int accent = MagicPath.GAMBLER.argb();
        MahouUi.frame(g, x, y, LEFT_W, h, accent);
        g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.luck_title").withStyle(ChatFormatting.BOLD), x + 6, y + 6,
                MahouUi.GOLD, true);
        int luck = clientLuck(d);
        boolean gambler = d.path() == MagicPath.GAMBLER;
        int ly = y + 20;
        g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.luck_points", luck), x + 6, ly, MahouUi.TEXT, false);
        ly += 11;
        float ev = LuckService.expected(luck, LuckService.Options.NONE);
        g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.expected", String.format(java.util.Locale.ROOT, "%.2f", ev)),
                x + 6, ly, MahouUi.TEXT_DIM, false);
        ly += 11;
        if (gambler) {
            int fever = LuckService.fever(d);
            g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.fever", fever), x + 6, ly, MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, x + 6, ly + 10, LEFT_W - 12, 3, fever / (float) LuckService.FEVER_MAX, 0xFFFF4AD2);
            ly += 16;
        }
        // tabela de chances
        float[] wts = LuckService.weights(luck, false);
        float total = 0;
        for (float v : wts) total += v;
        float jp = LuckService.jackpotChance(luck, LuckService.Options.NONE);
        g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.odds"), x + 6, ly + 2, MahouUi.GOLD_PALE, false);
        ly += 13;
        for (LuckBand b : LuckBand.VALUES) {
            float chance = b == LuckBand.JACKPOT ? jp : (1F - jp) * (total > 0 ? wts[b.ordinal()] / total : 0F);
            int col = 0xFF000000 | b.rgb;
            MahouUi.smallText(g, font, b.displayName(), x + 6, ly, 0.75F, col, false);
            String pct = b == LuckBand.JACKPOT ? "+100%" : (b.mult >= 1 ? "+" : "−") + Math.abs(LuckBand.percent(b.mult)) + "%";
            MahouUi.smallText(g, font, Component.literal(pct), x + 64, ly, 0.75F, MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, x + 90, ly + 1, LEFT_W - 124, 4, Math.min(1F, chance * 3F), col);
            String c = String.format(java.util.Locale.ROOT, chance < 0.1F ? "%.1f%%" : "%.0f%%", chance * 100F);
            MahouUi.smallText(g, font, Component.literal(c), x + LEFT_W - 30, ly, 0.75F, MahouUi.TEXT, false);
            ly += 9;
            if (ly > y + h - 28) break;
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable(gambler ? "screen.isekai_mahou.gambler.hint"
                : "screen.isekai_mahou.gambler.hint_other"), LEFT_W - 12);
        int hy = Math.max(ly + 4, y + h - 4 - hint.size() * 9);
        for (FormattedCharSequence l : hint) {
            if (hy + 9 > y + h - 2) break;
            g.drawString(font, l, x + 6, hy, MahouUi.TEXT_DIM, false);
            hy += 9;
        }
    }

    // ------------------------------------------------------------------ Fichas da Casa
    private void renderRanks(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int px = x + LEFT_W + 6, pw = w - LEFT_W - 6;
        int accent = MagicPath.GAMBLER.argb();
        MahouUi.frame(g, px, y, pw, h, accent);
        int rank = HouseRanks.rank(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.gambler.house").withStyle(ChatFormatting.BOLD), px + 8, y + 6, accent, true);
        for (int i = 0; i < HouseRanks.MAX_RANK; i++) {
            int cx = px + pw - 14 - (HouseRanks.MAX_RANK - 1 - i) * 11;
            MahouUi.diamond(g, cx, y + 10, 4, i < rank ? MahouUi.GOLD : 0x60FFFFFF);
            if (i < rank) MahouUi.diamond(g, cx, y + 10, 2, MahouUi.GOLD_PALE);
        }
        boolean gambler = d.path() == MagicPath.GAMBLER;
        int top = y + 22, bottom = y + h - 4;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int cy = top - scroll;
        for (HouseRanks.Rank r : HouseRanks.ranks()) {
            cy += card(g, font, d, r, px + 6, cy, pw - 12, gambler, rank, mx, my, top, bottom) + 4;
        }
        g.disableScissor();
        int contentH = cy + scroll - top;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int barH = Math.max(12, (bottom - top) * (bottom - top) / Math.max(1, contentH));
            int barY = top + (int) ((bottom - top - barH) * (scroll / (float) maxScroll));
            g.fill(px + pw - 4, top, px + pw - 2, bottom, 0x40000000);
            g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, MahouUi.GOLD_DIM);
        }
    }

    private int card(GuiGraphics g, Font font, MagicData d, HouseRanks.Rank r, int cx, int cy, int cw, boolean gambler, int done,
                     int mx, int my, int clipTop, int clipBottom) {
        int n = r.number();
        boolean complete = n <= done;
        boolean next = n == done + 1;
        boolean button = gambler && next;
        int lines = r.reqs().size() + 2;
        int ch = 16 + lines * 11 + 12 + (button ? 18 : 0) + 2;
        int accent = complete ? MahouUi.GOLD : next ? MagicPath.GAMBLER.argb() : 0xFF6A6478;
        MahouUi.roundRect(g, cx, cy, cw, ch, complete ? 0x50403010 : next ? MahouUi.alpha(MagicPath.GAMBLER.argb(), 0x30) : 0x40000000, 0x70000000);
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(accent, 0xB0));
        MutableComponent title = Component.literal(ROMAN[n - 1] + " · ").append(r.title());
        g.drawString(font, MahouUi.fit(font, title.withStyle(ChatFormatting.BOLD), cw - 80), cx + 6, cy + 4, accent, true);
        Component state = Component.translatable(complete ? "screen.isekai_mahou.gambler.done"
                : next ? "screen.isekai_mahou.gambler.next" : "screen.isekai_mahou.gambler.locked");
        g.drawString(font, state, cx + cw - 6 - font.width(state), cy + 4, complete ? GREEN : next ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
        int ly = cy + 16;
        boolean lvOk = complete || (d.level() >= r.level() && d.circle() >= r.circle());
        check(g, cx + 6, ly, lvOk);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.gambler.req_level", r.level(), r.circle()), cw - 20),
                cx + 16, ly, lvOk ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
        ly += 11;
        for (HouseRanks.Req q : r.reqs()) {
            int cur = Math.min(q.current(d), q.amount());
            boolean ok = complete || cur >= q.amount();
            check(g, cx + 6, ly, ok);
            int barW = Math.min(60, cw / 5);
            g.drawString(font, MahouUi.fit(font, Component.translatable(q.langKey(), q.amount()), cw - barW - 58), cx + 16, ly,
                    ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, cx + cw - barW - 38, ly + 2, barW, 4, complete ? 1F : cur / (float) q.amount(), ok ? GREEN : MagicPath.GAMBLER.argb());
            String num = complete ? "✔" : cur + "/" + q.amount();
            g.drawString(font, num, cx + cw - 6 - font.width(num), ly, ok ? GREEN : MahouUi.TEXT_DIM, false);
            ly += 11;
        }
        // a Rodada
        check(g, cx + 6, ly, complete);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.gambler.req_round", r.kills(), r.seconds(), r.stake()),
                cw - 20), cx + 16, ly, complete ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
        ly += 11;
        // recompensas
        MutableComponent reward = Component.translatable("screen.isekai_mahou.gambler.reward").withStyle(ChatFormatting.GOLD);
        List<Component> tip = new ArrayList<>();
        int rx = cx + 6;
        for (String gid : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(gid);
            if (glyph == null) continue;
            GlyphArt.glyph(g, glyph, rx, ly, 9);
            rx += 11;
            tip.add(glyph.displayName().withColor(MagicPath.GAMBLER.rgb()));
            tip.add(glyph.description().withStyle(ChatFormatting.GRAY));
        }
        if (r.deckTier() > 0) reward.append(" ").append(Component.translatable("screen.isekai_mahou.gambler.reward_deck", ROMAN[r.deckTier() - 1]));
        if (r.legends()) reward.append(" ").append(Component.translatable("screen.isekai_mahou.gambler.reward_legends"));
        reward.append(" ").append(Component.translatable("screen.isekai_mahou.gambler.reward_xp", r.xp()).withStyle(ChatFormatting.AQUA));
        g.drawString(font, MahouUi.fit(font, reward, cw - (rx - cx) - 6), rx, ly + 1, MahouUi.TEXT, false);
        if (!tip.isEmpty() && ly >= clipTop && ly + 10 <= clipBottom) tips.add(new Tip(cx + 6, ly, cw - 12, 10, tip));
        ly += 12;
        if (button) {
            List<Component> missing = new ArrayList<>();
            boolean can = HouseRanks.meets(d, r, missing);
            int chips = chips();
            if (chips < r.stake()) {
                can = false;
                missing.add(Component.translatable("screen.isekai_mahou.gambler.need_chips", r.stake(), chips));
            }
            if (missing.isEmpty()) missing.add(Component.translatable("screen.isekai_mahou.gambler.round_tip").withStyle(ChatFormatting.GRAY));
            button(g, font, cx + 6, ly + 1, Math.min(170, cw - 12), 15,
                    Component.translatable("screen.isekai_mahou.gambler.start", r.stake()), can, mx, my, clipTop, clipBottom,
                    () -> PacketDistributor.sendToServer(GbNet.StartChallenge.INSTANCE), missing);
        }
        return ch;
    }

    private static int chips() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(GbRegistries.CASINO_CHIP.get())) n += s.getCount();
        return n;
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

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, boolean enabled, int mx, int my,
                        int clipTop, int clipBottom, Runnable action, List<Component> tip) {
        boolean hover = enabled && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int base = enabled ? MagicPath.GAMBLER.argb() : 0xFF555060;
        MahouUi.roundRect(g, bx, by, bw, bh, MahouUi.alpha(MahouUi.lighten(base, hover ? 0.3F : 0F), 0xD0), MahouUi.alpha(MahouUi.darken(base, 0.5F), 0xD0));
        MahouUi.roundOutline(g, bx, by, bw, bh, enabled ? MahouUi.GOLD : 0xFF3A3640);
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
        if (mx < x + LEFT_W || maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        return true;
    }
}
