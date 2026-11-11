package com.renanpontes.isekaimahou.feature.class_rogue.client;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueKeys;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueNet;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueRanks;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Ladino" do menu B: o Livro-Razão do Ladrão (rank atual, o que falta para o próximo com barras de progresso,
 * as recompensas e o botão de subir), as passivas da classe e as páginas do Grimório Roubado (clique para escolher).
 */
public class RogueTab implements MenuTab {
    private static final int PURPLE = 0xFF8E5BD6;
    private static final int PAGE_H = 22;

    private int x, y, w, h;
    private Button rankUp;
    private int pagesTop;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int right = x + w / 2 + 6;
        int bw = Math.min(150, w / 2 - 16);
        rankUp = screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.class_rogue.rank_up"),
                b -> PacketDistributor.sendToServer(new RogueNet.Action(RogueNet.Action.RANK_UP, 0)))
                .bounds(right + (w / 2 - 12 - bw) / 2, y + h - 26, bw, 20).build());
        tick();
    }

    @Override
    public void tick() {
        MagicData d = ClientMagic.data();
        if (rankUp != null) rankUp.active = d != null && RogueRanks.ready(d);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        int half = w / 2 - 6;
        MahouUi.frame(g, x, y, half, h, PURPLE);
        MahouUi.frame(g, x + w / 2 + 6, y, half, h, PURPLE);
        if (d == null) return;
        boolean rogue = d.path() == MagicPath.ROGUE;
        int rank = RogueRanks.rank(d);

        // ---------------- Coluna da esquerda: rank, passivas e Grimório Roubado
        int lx = x + 10, ly = y + 9;
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_rogue.title").withColor(MahouUi.GOLD), lx, ly, MahouUi.GOLD, true);
        ly += 13;
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_rogue.rank", RogueRanks.rankName(rank), rank, RogueRanks.MAX_RANK),
                lx, ly, 0xFFC08AFF, false);
        ly += 12;
        if (!rogue) {
            for (var line : font.split(Component.translatable("screen.isekai_mahou.class_rogue.not_rogue"), half - 20)) {
                g.drawString(font, line, lx, ly, MahouUi.TEXT_DIM, false);
                ly += 10;
            }
        }
        float crit = RogueCombat.CRIT_BASE + RogueCombat.CRIT_PER_RANK * rank;
        String[] passives = {"crit", "back", "step", "vital"};
        for (String p : passives) {
            Component c = p.equals("crit") ? Component.translatable("screen.isekai_mahou.class_rogue.passive.crit", Math.round(crit * 100))
                    : Component.translatable(RogueKeys.k("screen.isekai_mahou.class_rogue.passive.", p));
            g.drawString(font, MahouUi.fit(font, Component.literal("• ").append(c), half - 20), lx, ly, MahouUi.TEXT, false);
            ly += 10;
        }
        ly += 6;
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_rogue.pages").withColor(MahouUi.GOLD), lx, ly, MahouUi.GOLD, true);
        ly += 12;
        pagesTop = ly;
        RogueNet.Sync view = RogueNet.clientView;
        List<RogueNet.PageView> pages = view.pages();
        if (pages.isEmpty()) {
            for (var line : font.split(Component.translatable("screen.isekai_mahou.class_rogue.no_pages"), half - 20)) {
                g.drawString(font, line, lx, ly, MahouUi.TEXT_DIM, false);
                ly += 10;
            }
        }
        for (int i = 0; i < pages.size() && pagesTop + (i + 1) * PAGE_H < y + h - 4; i++) {
            RogueNet.PageView p = pages.get(i);
            int py = pagesTop + i * PAGE_H;
            boolean sel = i == view.selected();
            boolean hover = mouseX >= lx && mouseX < x + half - 8 && mouseY >= py && mouseY < py + PAGE_H - 2;
            int col = 0xFF000000 | p.color();
            MahouUi.roundRect(g, lx, py, half - 18, PAGE_H - 2, MahouUi.alpha(col, sel ? 0x70 : hover ? 0x48 : 0x28),
                    MahouUi.alpha(MahouUi.darken(col, 0.5F), sel ? 0x90 : 0x50));
            if (sel) MahouUi.roundOutline(g, lx, py, half - 18, PAGE_H - 2, MahouUi.GOLD);
            g.drawString(font, MahouUi.fit(font, Component.literal(p.name()), half - 70), lx + 5, py + 2, MahouUi.TEXT, true);
            Element e = Element.byId(p.element());
            MutableComponent sub = Component.translatable("screen.isekai_mahou.class_rogue.page_from", p.from(),
                    e == null ? Element.ARCANE.displayName() : e.displayName());
            MahouUi.smallText(g, font, sub, lx + 5, py + 12, 0.7F, MahouUi.TEXT_DIM, false);
            // cargas: losangos
            for (int k = 0; k < p.charges(); k++) MahouUi.diamond(g, x + half - 24 - k * 8, py + 10, 3, col);
        }

        // ---------------- Coluna da direita: próximo rank
        int rx = x + w / 2 + 16, ry = y + 9, rw = half - 20;
        int next = rank + 1;
        if (next > RogueRanks.MAX_RANK) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.class_rogue.max").withColor(MahouUi.GOLD), rx, ry, MahouUi.GOLD, true);
            ry += 14;
            for (var line : font.split(Component.translatable("message.isekai_mahou.class_rogue.rank.flavor.5"), rw)) {
                g.drawString(font, line, rx, ry, MahouUi.TEXT_DIM, false);
                ry += 10;
            }
            return;
        }
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.class_rogue.next", RogueRanks.roman(next),
                RogueRanks.rankName(next)), rw), rx, ry, MahouUi.GOLD, true);
        ry += 14;
        int level = RogueRanks.levelFor(next);
        boolean levelOk = d.level() >= level;
        g.drawString(font, Component.literal(levelOk ? "✔ " : "✗ ").append(Component.translatable("class_rogue.isekai_mahou.req.level", level)),
                rx, ry, levelOk ? 0xFF7CE07C : 0xFFFF7070, false);
        ry += 12;
        int circle = RogueRanks.circleFor(next);
        boolean circleOk = d.circle() >= circle;
        g.drawString(font, Component.literal(circleOk ? "✔ " : "✗ ").append(Component.translatable("class_rogue.isekai_mahou.req.circle", circle)),
                rx, ry, circleOk ? 0xFF7CE07C : 0xFFFF7070, false);
        ry += 12;
        for (RogueRanks.Req q : RogueRanks.requirements(next)) {
            int have = d.counter(q.counter());
            boolean ok = have >= q.amount();
            g.drawString(font, MahouUi.fit(font, q.label(), rw - 40), rx, ry, ok ? 0xFF7CE07C : MahouUi.TEXT, false);
            String num = Math.min(have, q.amount()) + "/" + q.amount();
            g.drawString(font, num, rx + rw - font.width(num), ry, ok ? 0xFF7CE07C : MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, rx, ry + 10, rw, 2, q.amount() <= 0 ? 1F : have / (float) q.amount(), ok ? 0xFF5FD06A : PURPLE);
            ry += 16;
        }
        ry += 4;
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_rogue.rewards").withColor(MahouUi.GOLD), rx, ry, MahouUi.GOLD, false);
        ry += 11;
        for (ResourceLocation id : RogueRanks.glyphsFor(next)) {
            Glyph gl = GlyphRegistry.get(id);
            if (gl == null) continue;
            if (ry > y + h - 40) break;
            g.drawString(font, MahouUi.fit(font, Component.literal("✦ ").append(gl.displayName()), rw), rx, ry, 0xFFE0C8FF, false);
            ry += 10;
        }
        Component extra = Component.translatable(RogueKeys.k("screen.isekai_mahou.class_rogue.reward.", next), RogueRanks.xpFor(next));
        for (var line : font.split(extra, rw)) {
            if (ry > y + h - 40) break;
            g.drawString(font, line, rx, ry, MahouUi.TEXT_DIM, false);
            ry += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<RogueNet.PageView> pages = RogueNet.clientView.pages();
        int half = w / 2 - 6;
        int lx = x + 10;
        if (mx < lx || mx >= x + half - 8 || my < pagesTop) return false;
        int i = (int) ((my - pagesTop) / PAGE_H);
        if (i < 0 || i >= pages.size()) return false;
        PacketDistributor.sendToServer(new RogueNet.Action(RogueNet.Action.SELECT, i));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.2F));
        return true;
    }
}
