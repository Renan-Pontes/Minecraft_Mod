package com.renanpontes.isekaimahou.feature.class_steelball.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbPart;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbTechniques;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.LoveTrainForm;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Aba "Rotação" do menu B (só aparece para o Steel Ball): as 5 Lições do Mestre com o que cada uma pede (Círculo,
 * Partes, treino com barra de progresso) e o que ensina; o Ato atual da unha; as Técnicas do Santo e o LOVE TRAIN; as 9
 * Partes do Cadáver Santo (as que faltam aparecem apagadas, com o poder na dica; o Coração, a última, brilha em dourado)
 * e os bônus de classe em vigor.
 */
public class SteelballTab implements MenuTab {
    private static final int GOLD = 0xFFFFD24A;
    private static final int OK = 0xFF7FE08A;
    private static final int LOCKED = 0xFF6E6880;
    /** Branco-dourado do Love Train. */
    private static final int LOVE = 0xFFF0B8;

    private int x, y, w, h;
    private int leftW, rightX, rightW;
    private int scroll, contentH;
    private Font font;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        leftW = Math.max(200, w * 58 / 100);
        rightX = x + leftW + 8;
        rightW = x + w - rightX;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < x + leftW && contentH > h) {
            scroll = Mth.clamp(scroll - (int) Math.round(sy * 14), 0, contentH - h + 4);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        MagicData d = ClientMagic.data();
        renderLessons(g, d);
        renderSide(g, d, mx, my);
    }

    // ------------------------------------------------------------------ lições
    private void renderLessons(GuiGraphics g, MagicData d) {
        int rank = SbProgress.rank(d);
        int act = SbProgress.act(d);
        g.enableScissor(x, y, x + leftW, y + h);
        int cy = y + 2 - scroll;
        g.drawString(font, Component.translatable("gui.isekai_mahou.steelball.tab.title"), x + 4, cy, GOLD, true);
        cy += 12;
        MutableComponent sub = Component.translatable("gui.isekai_mahou.steelball.tab.status", SbProgress.roman(rank),
                act, SbProgress.actName(act));
        cy = wrap(g, sub, x + 4, cy, leftW - 8, MahouUi.TEXT_DIM) + 4;
        if (d.path() != MagicPath.STEELBALL) {
            cy = wrap(g, Component.translatable("gui.isekai_mahou.steelball.tab.other"), x + 4, cy, leftW - 8, LOCKED) + 4;
        }
        for (SbProgress.Rank r : SbProgress.ranks()) {
            boolean done = rank >= r.number();
            boolean current = rank + 1 == r.number();
            int top = cy;
            int accent = done ? OK : current ? GOLD : LOCKED;
            int boxH = cardHeight(d, r, current);
            MahouUi.roundRect(g, x + 2, top, leftW - 6, boxH, 0x60181226, 0x600B0912);
            g.fill(x + 2, top, x + 4, top + boxH, accent);
            String head = SbProgress.roman(r.number()) + " · ";
            g.drawString(font, Component.literal(head).append(r.title()), x + 8, top + 4, accent, true);
            Component state = Component.translatable(done ? "gui.isekai_mahou.steelball.tab.done"
                    : current ? "gui.isekai_mahou.steelball.tab.current" : "gui.isekai_mahou.steelball.tab.locked");
            g.drawString(font, state, x + leftW - 8 - font.width(state), top + 4, accent, false);
            int ly = top + 16;
            ly = wrap(g, r.lesson(), x + 8, ly, leftW - 18, MahouUi.TEXT_DIM);
            if (current) {
                ly = req(g, Component.translatable("gui.isekai_mahou.steelball.req.circle", r.circle()), d.circle() >= r.circle(), ly);
                ly = req(g, Component.translatable("gui.isekai_mahou.steelball.req.parts", r.parts(), SbPart.count(d)),
                        SbPart.count(d) >= r.parts(), ly);
                for (SbProgress.Task t : r.tasks()) {
                    boolean ok = t.done(d);
                    ly = req(g, t.describe(), ok, ly);
                    MahouUi.bar(g, x + 20, ly, leftW - 40, 3, t.progress(d) / (float) t.amount(), ok ? OK : GOLD);
                    ly += 6;
                }
            }
            g.drawString(font, Component.translatable("gui.isekai_mahou.steelball.tab.teaches", glyphNames(r)), x + 8, ly,
                    done ? OK : 0xFF9FB0C0, false);
            cy = top + boxH + 4;
        }
        cy = renderTechniques(g, d, cy);
        contentH = cy + scroll - y;
        g.disableScissor();
        if (contentH > h) {
            int barH = Math.max(12, h * h / contentH);
            int by = y + (h - barH) * scroll / Math.max(1, contentH - h);
            g.fill(x + leftW - 2, by, x + leftW, by + barH, 0x80B4C0CC);
        }
    }

    /** Técnicas do Santo (Atos de Tusk etc.): ícone, nome, o que pede (lição + Partes) e se já foi aprendida. */
    private int renderTechniques(GuiGraphics g, MagicData d, int cy) {
        cy += 4;
        g.drawString(font, Component.translatable("gui.isekai_mahou.steelball.tech.title"), x + 4, cy, GOLD, true);
        cy += 12;
        cy = wrap(g, Component.translatable("gui.isekai_mahou.steelball.tech.hint"), x + 4, cy, leftW - 8, MahouUi.TEXT_DIM) + 2;
        var known = SbTechniques.known(d);
        for (SbTechniques.Technique t : SbTechniques.TECHNIQUES) {
            Glyph glyph = t.glyphObj();
            if (glyph == null) continue;
            boolean has = known.test(t);
            int boxH = 26;
            MahouUi.roundRect(g, x + 2, cy, leftW - 6, boxH, has ? 0x60302818 : 0x60181226, 0x600B0912);
            g.fill(x + 2, cy, x + 4, cy + boxH, has ? OK : 0xFF000000 | t.rgb());
            GlyphArt.glyph(g, glyph, x + 7, cy + 4, 16);
            g.drawString(font, MahouUi.fit(font, glyph.displayName(), leftW - 60), x + 27, cy + 4, has ? OK : 0xFF000000 | t.rgb(), true);
            Component state = Component.translatable(has ? "gui.isekai_mahou.steelball.tab.done" : "gui.isekai_mahou.steelball.tab.locked");
            g.drawString(font, state, x + leftW - 8 - font.width(state), cy + 4, has ? OK : LOCKED, false);
            MahouUi.smallText(g, font, t.requirement(), x + 27, cy + 15, 0.75F, MahouUi.TEXT_DIM, false);
            cy += boxH + 3;
        }
        return renderLoveTrain(g, d, cy);
    }

    /** O LOVE TRAIN: a técnica do Santo inteiro (o Coração do Cadáver Santo é a última parte). */
    private int renderLoveTrain(GuiGraphics g, MagicData d, int cy) {
        Glyph glyph = GlyphRegistry.get(IsekaiMahou.id(LoveTrainForm.ID));
        if (glyph == null) return cy;
        boolean has = d.knowsGlyph(glyph.id());
        int parts = SbPart.count(d);
        int total = SbPart.values().length;
        int textW = leftW - 40;
        List<FormattedCharSequence> lines = font.split(Component.translatable("gui.isekai_mahou.steelball.love_train.info"), textW);
        int boxH = 30 + lines.size() * 10 + 4;
        cy += 2;
        int pulse = (int) (40 + 30 * Math.sin(System.currentTimeMillis() / 400.0));
        MahouUi.roundRect(g, x + 2, cy, leftW - 6, boxH, has ? 0x80403418 : 0x60201A10, 0x800B0912);
        MahouUi.roundOutline(g, x + 2, cy, leftW - 6, boxH, has ? (0xFF000000 | LOVE) : ((pulse + 60) << 24) | 0xFFD24A);
        g.fill(x + 2, cy, x + 4, cy + boxH, has ? 0xFF000000 | LOVE : 0xFFFFD24A);
        GlyphArt.glyph(g, glyph, x + 7, cy + 5, 20);
        g.drawString(font, MahouUi.fit(font, glyph.displayName(), leftW - 70), x + 31, cy + 4, 0xFF000000 | LOVE, true);
        Component state = Component.translatable(has ? "gui.isekai_mahou.steelball.tab.done" : "gui.isekai_mahou.steelball.tab.locked");
        g.drawString(font, state, x + leftW - 8 - font.width(state), cy + 4, has ? OK : LOCKED, false);
        boolean heart = SbPart.FINAL.owned(d);
        MutableComponent req = Component.translatable("gui.isekai_mahou.steelball.love_train.req", SbPart.FINAL.displayName(), parts, total);
        MahouUi.smallText(g, font, req, x + 31, cy + 15, 0.75F, heart ? OK : GOLD, false);
        int ly = cy + 27;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 31, ly, MahouUi.TEXT_DIM, false);
            ly += 10;
        }
        return cy + boxH + 3;
    }

    private int cardHeight(MagicData d, SbProgress.Rank r, boolean current) {
        int lines = font.split(r.lesson(), leftW - 18).size();
        int hgt = 16 + lines * 10 + 12;
        if (current) hgt += 2 * 11 + r.tasks().size() * 17;
        return hgt;
    }

    private int req(GuiGraphics g, Component text, boolean ok, int ly) {
        g.drawString(font, ok ? "✔" : "•", x + 10, ly, ok ? OK : GOLD, false);
        g.drawString(font, text, x + 20, ly, ok ? OK : MahouUi.TEXT, false);
        return ly + 11;
    }

    private static MutableComponent glyphNames(SbProgress.Rank r) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String id : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(IsekaiMahou.id(id));
            if (glyph == null) continue;
            if (!first) out.append(", ");
            out.append(glyph.displayName());
            first = false;
        }
        return out;
    }

    private int wrap(GuiGraphics g, Component text, int tx, int ty, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }

    // ------------------------------------------------------------------ partes e bônus
    private void renderSide(GuiGraphics g, MagicData d, int mx, int my) {
        if (rightW < 80) return;
        int cy = y + 2;
        int owned = SbPart.count(d);
        g.drawString(font, Component.translatable("gui.isekai_mahou.steelball.tab.parts", owned, SbPart.values().length), rightX, cy, GOLD, true);
        cy += 14;
        int cell = 24;
        int gridX = rightX + Math.max(0, (rightW - cell * 3) / 2);
        SbPart hovered = null;
        for (int i = 0; i < SbPart.values().length; i++) {
            SbPart p = SbPart.values()[i];
            int sx = gridX + (i % 3) * cell, sy = cy + (i / 3) * cell;
            boolean has = p.owned(d);
            MahouUi.roundRect(g, sx, sy, cell - 4, cell - 4, has ? 0xA0302818 : 0x80181226, has ? 0xA0201808 : 0x800B0912);
            if (has) MahouUi.roundOutline(g, sx, sy, cell - 4, cell - 4, 0xFF000000 | p.rgb);
            // o Coração (a última parte) brilha em dourado: pulsa quando já pode aparecer
            if (p.isFinal() && !has) {
                int a = SbPart.readyForHeart(d) ? (int) (150 + 100 * Math.sin(System.currentTimeMillis() / 250.0)) : 90;
                MahouUi.roundOutline(g, sx - 1, sy - 1, cell - 2, cell - 2, (Mth.clamp(a, 0, 255) << 24) | 0xFFD24A);
            }
            g.renderItem(new ItemStack(SbRegistry.PARTS.get(p).get()), sx + 2, sy + 2);
            if (!has) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.fill(sx + 2, sy + 2, sx + 18, sy + 18, 0xC0101018);
                g.pose().popPose();
            }
            if (mx >= sx && mx < sx + cell - 4 && my >= sy && my < sy + cell - 4) hovered = p;
        }
        cy += cell * 3 + 6;
        g.drawString(font, Component.translatable("gui.isekai_mahou.steelball.tab.bonus"), rightX, cy, GOLD, true);
        cy += 12;
        int act = SbProgress.act(d);
        int spin = d.path() == MagicPath.STEELBALL ? 15 + 5 * act : 0;
        cy = wrap(g, Component.translatable("gui.isekai_mahou.steelball.tab.bonus.spin", spin), rightX, cy, rightW, MahouUi.TEXT);
        cy = wrap(g, Component.translatable("gui.isekai_mahou.steelball.tab.bonus.mounted"), rightX, cy, rightW, MahouUi.TEXT);
        cy = wrap(g, Component.translatable("gui.isekai_mahou.steelball.tab.bonus.return"), rightX, cy, rightW, MahouUi.TEXT);
        wrap(g, Component.translatable("gui.isekai_mahou.steelball.tab.hint"), rightX, cy + 4, rightW, MahouUi.TEXT_DIM);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.displayName().withColor(hovered.rgb));
            tip.add(hovered.bonus());
            if (hovered.isFinal()) {
                tip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.final").withColor(0xFFD24A));
                tip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.love_train").withColor(LOVE));
            }
            tip.add(Component.translatable(hovered.owned(d) ? "tooltip.isekai_mahou.steelball.part.owned"
                    : hovered.isFinal() ? (SbPart.readyForHeart(d) ? "gui.isekai_mahou.steelball.tab.heart_ready"
                            : "gui.isekai_mahou.steelball.tab.heart_missing")
                    : "gui.isekai_mahou.steelball.tab.part_missing"));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }
}
