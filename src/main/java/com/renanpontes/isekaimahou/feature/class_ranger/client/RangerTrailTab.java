package com.renanpontes.isekaimahou.feature.class_ranger.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerNet;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerProgress;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerRegistries;
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
 * Aba "Trilha do Ranger" do menu B: à esquerda os companheiros (estágio, nível e quando cada um chega) e os bônus de
 * classe; à direita os 5 ranks da Trilha com os requisitos (barras), as recompensas e o botão "Concluir Rank".
 * Rola com a roda do mouse.
 */
public class RangerTrailTab implements MenuTab {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    private static final int GREEN = 0xFF7BE07A;
    private static final int LEFT_W = 148;

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
        renderLeft(g, font, d);
        renderRanks(g, font, d, mx, my);
        for (Tip t : tips) {
            if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component c : t.lines()) lines.addAll(font.split(c, 210));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ Companheiros e bônus
    private void renderLeft(GuiGraphics g, Font font, MagicData d) {
        int accent = MagicPath.RANGER.argb();
        MahouUi.frame(g, x, y, LEFT_W, h, accent);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_ranger.companions")
                .withStyle(ChatFormatting.BOLD), LEFT_W - 12), x + 6, y + 6, MahouUi.GOLD, true);
        boolean ranger = d.path() == MagicPath.RANGER;
        int lvl = RangerCompanions.level(d);
        int stage = RangerCompanions.stageOf(lvl);
        int rank = RangerProgress.rank(d);
        int ry = y + 19;
        ry = companion(g, font, ry, new ItemStack(RangerRegistries.WOLF_EGG.get()), RangerRegistries.WOLF.get().getDescription(),
                true, lvl, stage, Component.translatable("screen.isekai_mahou.class_ranger.wolf_role"));
        ry = companion(g, font, ry, new ItemStack(RangerRegistries.EAGLE_EGG.get()), RangerRegistries.EAGLE.get().getDescription(),
                RangerCompanions.eagleUnlocked(d), lvl, stage, Component.translatable(
                        RangerCompanions.eagleUnlocked(d) ? "screen.isekai_mahou.class_ranger.eagle_role" : "screen.isekai_mahou.class_ranger.unlock_rank", 1));
        ry = companion(g, font, ry, new ItemStack(RangerRegistries.STEED_EGG.get()), RangerRegistries.STEED.get().getDescription(),
                !ranger || rank >= 2, lvl, stage, Component.translatable(
                        !ranger || rank >= 2 ? "screen.isekai_mahou.class_ranger.steed_role" : "screen.isekai_mahou.class_ranger.unlock_rank", 2));

        ry += 4;
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_ranger.bonuses").withStyle(ChatFormatting.BOLD),
                LEFT_W - 12), x + 6, ry, MahouUi.GOLD, true);
        ry += 12;
        int arrowBonus = 10 + 3 * rank;
        Component[] bonuses = {
                Component.translatable("screen.isekai_mahou.class_ranger.bonus.fast"),
                Component.translatable("screen.isekai_mahou.class_ranger.bonus.eye", arrowBonus),
                Component.translatable("screen.isekai_mahou.class_ranger.bonus.mounted"),
                Component.translatable("screen.isekai_mahou.class_ranger.bonus.feet"),
                Component.translatable("screen.isekai_mahou.class_ranger.bonus.pack")};
        for (Component b : bonuses) {
            List<FormattedCharSequence> lines = font.split(b, LEFT_W - 18);
            for (int i = 0; i < lines.size(); i++) {
                if (ry + 9 > y + h - 4) return;
                if (i == 0) MahouUi.diamond(g, x + 8, ry + 4, 2, ranger ? GREEN : MahouUi.TEXT_DIM);
                g.drawString(font, lines.get(i), x + 13, ry, ranger ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
                ry += 9;
            }
            ry += 1;
        }
    }

    private int companion(GuiGraphics g, Font font, int ry, ItemStack icon, Component name, boolean unlocked, int lvl, int stage,
                          Component role) {
        int cw = LEFT_W - 8;
        MahouUi.roundRect(g, x + 4, ry, cw, 30, unlocked ? MahouUi.alpha(MagicPath.RANGER.argb(), 0x40) : 0x30000000, 0x60000000);
        g.renderItem(icon, x + 8, ry + 7);
        g.drawString(font, MahouUi.fit(font, name.copy(), cw - 30), x + 28, ry + 4, unlocked ? MagicPath.RANGER.argb() : MahouUi.TEXT_DIM, true);
        String stageKey = "screen.isekai_mahou.class_ranger.stage." + stage;
        Component sub = unlocked
                ? Component.translatable("screen.isekai_mahou.class_ranger.stage", Component.translatable(stageKey), lvl)
                : Component.translatable("screen.isekai_mahou.class_ranger.locked");
        MahouUi.smallText(g, font, sub, x + 28, ry + 14, 0.75F, unlocked ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
        MahouUi.smallText(g, font, role, x + 28, ry + 21, 0.7F, MahouUi.TEXT_DIM, false);
        tips.add(new Tip(x + 4, ry, cw, 30, List.of(name, role.copy().withStyle(ChatFormatting.GRAY))));
        return ry + 33;
    }

    // ------------------------------------------------------------------ Ranks
    private void renderRanks(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int px = x + LEFT_W + 6, pw = w - LEFT_W - 6;
        int accent = MagicPath.RANGER.argb();
        MahouUi.frame(g, px, y, pw, h, accent);
        int rank = RangerProgress.rank(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_ranger.title").withStyle(ChatFormatting.BOLD), px + 8, y + 6, accent, true);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.class_ranger.subtitle"), px + 8, y + 17, 0.75F, MahouUi.TEXT_DIM, false);
        for (int i = 0; i < RangerProgress.MAX_RANK; i++) {
            int cx = px + pw - 14 - (RangerProgress.MAX_RANK - 1 - i) * 11;
            MahouUi.diamond(g, cx, y + 11, 4, i < rank ? MahouUi.GOLD : 0x60FFFFFF);
            if (i < rank) MahouUi.diamond(g, cx, y + 11, 2, MahouUi.GOLD_PALE);
        }
        boolean own = d.path() == MagicPath.RANGER;
        int top = y + 30, bottom = y + h - 4;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int cy = top - scroll;
        for (RangerProgress.Rank r : RangerProgress.ranks()) {
            cy += card(g, font, d, r, px + 6, cy, pw - 12, own, rank, mx, my, top, bottom) + 4;
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

    /** Um rank: título, estado, requisitos, recompensas e botão. Devolve a altura. */
    private int card(GuiGraphics g, Font font, MagicData d, RangerProgress.Rank r, int cx, int cy, int cw, boolean own, int done,
                     int mx, int my, int clipTop, int clipBottom) {
        int n = r.number();
        boolean complete = n <= done;
        boolean next = n == done + 1;
        boolean button = own && next;
        int lines = r.reqs().size() + 1;
        int ch = 16 + lines * 11 + 12 + (button ? 18 : 0) + 4;
        int accent = complete ? MahouUi.GOLD : next ? MagicPath.RANGER.argb() : 0xFF6A6478;
        MahouUi.roundRect(g, cx, cy, cw, ch, complete ? 0x50403010 : next ? MahouUi.alpha(MagicPath.RANGER.argb(), 0x30) : 0x40000000, 0x70000000);
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(accent, 0xB0));

        MutableComponent title = Component.literal(ROMAN[n - 1] + " · ").append(RangerProgress.title(n));
        g.drawString(font, MahouUi.fit(font, title.withStyle(ChatFormatting.BOLD), cw - 80), cx + 6, cy + 4, accent, true);
        Component state = Component.translatable(complete ? "screen.isekai_mahou.class_ranger.done"
                : next ? "screen.isekai_mahou.class_ranger.next" : "screen.isekai_mahou.class_ranger.locked");
        g.drawString(font, state, cx + cw - 6 - font.width(state), cy + 4, complete ? GREEN : next ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);

        int ly = cy + 16;
        boolean lvOk = complete || (d.level() >= r.level() && d.circle() >= r.circle());
        check(g, cx + 6, ly, lvOk);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_ranger.req_level", r.level(), r.circle()), cw - 20),
                cx + 16, ly, lvOk ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
        ly += 11;
        for (RangerProgress.Req q : r.reqs()) {
            int cur = Math.min(q.current(d), q.amount());
            boolean ok = complete || cur >= q.amount();
            check(g, cx + 6, ly, ok);
            int barW = Math.min(70, cw / 4);
            g.drawString(font, MahouUi.fit(font, Component.translatable(q.langKey(), q.amount()), cw - barW - 62), cx + 16, ly,
                    ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            int bx = cx + cw - barW - 42;
            MahouUi.bar(g, bx, ly + 2, barW, 4, complete ? 1F : cur / (float) q.amount(), ok ? GREEN : MagicPath.RANGER.argb());
            String num = complete ? "✔" : cur + "/" + q.amount();
            g.drawString(font, num, cx + cw - 6 - font.width(num), ly, ok ? GREEN : MahouUi.TEXT_DIM, false);
            ly += 11;
        }

        // Recompensa: glifos (ícones com dica), arco, companheiro, lendas, XP
        List<Component> tip = new ArrayList<>();
        MutableComponent reward = Component.translatable("screen.isekai_mahou.class_ranger.reward").withStyle(ChatFormatting.GOLD);
        int rx = cx + 6;
        for (String id : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(id);
            if (glyph == null) continue;
            GlyphArt.glyph(g, glyph, rx, ly, 9);
            rx += 11;
            tip.add(glyph.displayName().withColor(MagicPath.RANGER.rgb()));
            tip.add(glyph.description().withStyle(ChatFormatting.GRAY));
        }
        if (r.bowTier() > 0) reward.append(" ").append(Component.translatable("screen.isekai_mahou.class_ranger.reward_bow", ROMAN[r.bowTier() - 1]));
        if (n == 1) reward.append(" ").append(RangerRegistries.EAGLE.get().getDescription().copy().withStyle(ChatFormatting.GREEN));
        if (n == 2) reward.append(" ").append(RangerRegistries.STEED.get().getDescription().copy().withStyle(ChatFormatting.GREEN));
        if (n == 3) reward.append(" ").append(Component.translatable("trail.isekai_mahou.class_ranger.reward_alpha").withStyle(ChatFormatting.GREEN));
        if (r.legends()) reward.append(" ").append(Component.translatable("screen.isekai_mahou.class_ranger.reward_legends").withStyle(ChatFormatting.LIGHT_PURPLE));
        reward.append(" ").append(Component.translatable("screen.isekai_mahou.class_ranger.reward_xp", r.xp()).withStyle(ChatFormatting.AQUA));
        g.drawString(font, MahouUi.fit(font, reward, cw - (rx - cx) - 6), rx, ly + 1, MahouUi.TEXT, false);
        if (!tip.isEmpty() && ly >= clipTop && ly + 10 <= clipBottom) tips.add(new Tip(cx + 6, ly, Math.max(10, rx - cx - 6), 10, tip));
        ly += 12;

        if (button) {
            List<Component> missing = new ArrayList<>();
            boolean can = RangerProgress.meets(d, n, missing);
            int bw = Math.min(130, cw - 12);
            boolean hover = can && mx >= cx + 6 && mx < cx + 6 + bw && my >= ly + 1 && my < ly + 16;
            int base = can ? MagicPath.RANGER.argb() : 0xFF555060;
            MahouUi.roundRect(g, cx + 6, ly + 1, bw, 15, MahouUi.alpha(MahouUi.lighten(base, hover ? 0.3F : 0F), 0xD0),
                    MahouUi.alpha(MahouUi.darken(base, 0.5F), 0xD0));
            MahouUi.roundOutline(g, cx + 6, ly + 1, bw, 15, can ? MahouUi.GOLD : 0xFF3A3640);
            FormattedCharSequence label = MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_ranger.claim"), bw - 6);
            g.drawString(font, label, cx + 6 + (bw - font.width(label)) / 2, ly + 5, can ? 0xFFFFFFFF : 0xFF9A96A6, true);
            boolean visible = ly + 1 >= clipTop && ly + 16 <= clipBottom;
            if (visible && can) hits.add(new Hit(cx + 6, ly + 1, bw, 15, () -> PacketDistributor.sendToServer(new RangerNet.ClaimRank(n))));
            if (visible && !missing.isEmpty()) tips.add(new Tip(cx + 6, ly + 1, bw, 15, missing));
        }
        return ch;
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
