package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellBreakdown;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * DETALHAMENTO da magia (pedido do jogador: "mostrar melhor as informações das magias, como dano das partes e custo de
 * mana"). Desenhado na mesma caixa do programa quando o jogador troca para "Detalhes". Tudo sai de
 * {@link ParsedSpell#breakdown()} (núcleo, A1b): custo total com a barra de mana (parte das cópias destacada), dano e
 * cura estimados, conjuração/recarga, o AVISO de custo por cópia ("Atômico em cada cópia: 45 × 12 (Chuva de
 * Meteoros) = 540") e, por estágio, a forma (custo, cópias, alcance, raio, duração) e cada efeito (custo por cópia ×
 * cópias = total, dano/cura por acerto e total, duração, raio), com barrinhas da fatia de cada parte no custo.
 */
final class BreakdownView {
    private static final String K = "screen.isekai_mahou.grimoire.info.";
    private static final int PAD = 5;
    private static final int LINE = 10;
    private static final int LINK_COLOR = GlyphArt.kindColor(GlyphKind.TRIGGER, true);
    private static final int DMG = 0xFFFF7A6B;
    private static final int HEAL = 0xFF7DFF9A;

    private int bx, by, bw, bh;
    final ScrollState scroll = new ScrollState();
    /** Cache: o detalhamento só é recalculado quando a magia (o objeto ParsedSpell) muda. */
    private ParsedSpell cachedFor;
    private SpellBreakdown cached;

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    void bounds(int x, int y, int w, int h) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        scroll.bounds(x, y + 1, w, h - 2);
    }

    boolean over(double mx, double my) {
        return Gfx.in(mx, my, bx, by, bw, bh);
    }

    @Nullable
    SpellBreakdown breakdown(@Nullable ParsedSpell sp) {
        if (sp == null) return null;
        if (sp != cachedFor) {
            cachedFor = sp;
            cached = sp.breakdown();
        }
        return cached;
    }

    // =====================================================================================
    //  Aviso de custo por cópia (também usado na linha de situação do Grimório)
    // =====================================================================================
    /** Efeito que mais encarece por causa das cópias (para o aviso "×12 meteoros"), ou null se não há cópias. */
    @Nullable
    static Worst worstCopy(SpellBreakdown b) {
        Worst best = null;
        for (SpellBreakdown.StagePart st : b.stages()) {
            for (SpellBreakdown.EffectPart p : st.effects()) {
                float times = p.copies() * p.multiplier();
                if (times <= 1.001F) continue;
                int extra = p.totalCost() - p.costPerCopy();
                if (best == null || extra > best.extra) best = new Worst(st, p, times, extra);
            }
        }
        return best;
    }

    record Worst(SpellBreakdown.StagePart stage, SpellBreakdown.EffectPart part, float times, int extra) {}

    /** "Atômico em cada cópia: 45 × 12 (Chuva de Meteoros) = 540 de mana". */
    static MutableComponent copyLine(Worst w) {
        return tr("copy_line", w.part().effect().displayName(), w.part().costPerCopy(), SpellBreakdown.num(w.times()),
                w.stage().form().displayName(), w.part().totalCost());
    }

    /** Porcentagem do custo vinda das cópias. */
    static int copyPercent(SpellBreakdown b) {
        return b.manaCost() <= 0 ? 0 : Math.round(100F * b.perCopyExtra() / b.manaCost());
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    void render(GuiGraphics g, HotZones zones, SpellCheck check, int mx, int my) {
        Font font = Gfx.font();
        Gfx.box(g, bx, by, bw, bh, check.ok() ? Gfx.alpha(Gfx.MANA, 0x90) : Gfx.FRAME);
        SpellBreakdown b = check.ok() ? breakdown(check.spell()) : null;
        if (b == null) {
            renderInvalid(g, font, check);
            return;
        }
        int top = by + 1, bottom = by + bh - 1;
        int cw = bw - PAD * 2 - 4; // largura útil (sobra para a barra de rolagem)
        int x0 = bx + PAD;
        int off = scroll.offset();
        int y = by + 4 - off;
        g.enableScissor(bx + 1, top, bx + bw - 1, bottom);
        Clip clip = new Clip(bx, top, bw, bottom - top);

        y = renderSummary(g, zones, font, b, check, x0, y, cw, clip, mx, my);
        y = renderCopyWarning(g, zones, font, b, x0, y, cw, clip);
        for (SpellBreakdown.StagePart st : b.stages()) y = renderStage(g, zones, font, b, st, x0, y, cw, clip);
        y = renderFootnote(g, font, check, x0, y, cw);

        g.disableScissor();
        scroll.content(y + off - by + 2);
        scroll.renderBar(g);
    }

    private record Clip(int x, int y, int w, int h) {}

    private void renderInvalid(GuiGraphics g, Font font, SpellCheck check) {
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(tr("invalid").withStyle(ChatFormatting.GRAY), bw - 16));
        if (!check.ok() && check.result.error() != null && !GrimoireDraft.glyphs.isEmpty()) {
            lines.addAll(font.split(Component.literal("✖ ").append(check.result.error()), bw - 16));
        }
        int ty = by + Math.max(6, (bh - lines.size() * LINE) / 2);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence l = lines.get(i);
            int col = i == 0 ? Gfx.TEXT_DIM : 0xFFFFB8B8;
            g.drawString(font, l, bx + (bw - font.width(l)) / 2, ty, col, false);
            ty += LINE;
        }
    }

    // ------------------------------------------------------------------ resumo
    private int renderSummary(GuiGraphics g, HotZones zones, Font font, SpellBreakdown b, SpellCheck check, int x0, int y, int cw,
                              Clip clip, int mx, int my) {
        int max = Math.max(1, ClientMagic.maxMana);
        boolean over = b.manaCost() > max;
        // linha 1: runa do elemento + custo grande + dano/cura à direita
        GlyphArt.element(g, b.primary(), x0, y, 12);
        Component cost = Component.literal(String.valueOf(b.manaCost())).withStyle(ChatFormatting.BOLD);
        int costCol = over ? Gfx.BAD : b.heavyCopies() ? Gfx.WARN : Gfx.MANA;
        g.drawString(font, tr("mana_label"), x0 + 15, y + 2, Gfx.TEXT_DIM, false);
        int cx = x0 + 15 + font.width(tr("mana_label")) + 4;
        g.drawString(font, cost, cx, y + 2, costCol, true);
        cx += font.width(cost);
        String maxText = " / " + max;
        g.drawString(font, maxText, cx, y + 2, Gfx.TEXT_FAINT, false);
        cx += font.width(maxText);
        zones.addClipped(x0, y, cx - x0, 12, clip.x, clip.y, clip.w, clip.h, null, () -> manaTip(b, max));
        int rx = x0 + cw;
        if (b.heal() > 0) {
            Component h = tr("heal_total", SpellBreakdown.num(b.heal()));
            rx -= font.width(h);
            if (rx > cx + 4) {
                g.drawString(font, h, rx, y + 2, HEAL, false);
                final int hx = rx;
                zones.addClipped(hx, y, font.width(h), 12, clip.x, clip.y, clip.w, clip.h, null,
                        () -> List.of(tr("heal_total.title").withStyle(ChatFormatting.BOLD), tr("estimate.tip").withStyle(ChatFormatting.GRAY)));
            }
            rx -= 6;
        }
        if (b.damage() > 0) {
            Component d = tr("damage_total", SpellBreakdown.num(b.damage()));
            rx -= font.width(d);
            if (rx > cx + 4) {
                g.drawString(font, d, rx, y + 2, DMG, false);
                final int dx = rx;
                zones.addClipped(dx, y, font.width(d), 12, clip.x, clip.y, clip.w, clip.h, null,
                        () -> List.of(tr("damage_total.title").withStyle(ChatFormatting.BOLD), tr("damage_total.tip").withStyle(ChatFormatting.GRAY),
                                tr("estimate.tip").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        y += 15;

        // linha 2: barra de mana (parte base + parte das cópias)
        int barW = cw;
        int barH = 6;
        g.fill(x0, y, x0 + barW, y + barH, 0xFF0E0B16);
        Gfx.frame(g, x0 - 1, y - 1, barW + 2, barH + 2, Gfx.FRAME);
        float base = (b.manaCost() - b.perCopyExtra()) / (float) max;
        float all = b.manaCost() / (float) max;
        int wBase = Math.round(Math.min(1F, base) * barW);
        int wAll = Math.round(Math.min(1F, all) * barW);
        Gfx.hGradient(g, x0, y, wBase, barH, 0xFF4E7FD0, Gfx.MANA);
        if (wAll > wBase) {
            final int by0 = y;
            Gfx.batch(g, () -> {
                for (int i = wBase; i < wAll; i++) {
                    int c = ((i - wBase) / 2) % 2 == 0 ? Gfx.WARN : 0xFFD08A30;
                    g.fill(x0 + i, by0, x0 + i + 1, by0 + barH, c);
                }
            });
        }
        if (over) {
            g.fill(x0 + barW - 2, y - 2, x0 + barW, y + barH + 2, Gfx.BAD);
        }
        zones.addClipped(x0 - 1, y - 2, barW + 2, barH + 4, clip.x, clip.y, clip.w, clip.h, null, () -> manaTip(b, max));
        y += barH + 5;

        // linha 3: conjuração · recarga · glifos · ultimate · salto
        List<Component> parts = new ArrayList<>();
        parts.add(b.castTicks() <= 0 ? tr("cast_instant") : tr("cast", SpellBreakdown.seconds(b.castTicks())));
        parts.add(tr("cooldown", SpellBreakdown.seconds(b.cooldown())));
        parts.add(tr("glyphs", b.glyphCount(), check.maxGlyphs));
        if (b.stages().size() > 1) parts.add(tr("stages", b.stages().size()));
        if (b.ultimate()) parts.add(tr("ultimate").withColor(Gfx.GOLD & 0xFFFFFF));
        ParsedSpell sp = check.spell();
        if (ProgramView.hops(sp)) parts.add(tr("hop", ProgramView.hopText(sp)).withColor(LINK_COLOR & 0xFFFFFF));
        int y0 = y;
        y = flow(g, font, parts, x0, y, cw, Gfx.TEXT);
        zones.addClipped(x0, y0, cw, y - y0, clip.x, clip.y, clip.w, clip.h, null, () -> List.of(
                tr("times.title").withStyle(ChatFormatting.BOLD), tr("times.tip").withStyle(ChatFormatting.GRAY)));
        return y + 3;
    }

    private List<Component> manaTip(SpellBreakdown b, int max) {
        List<Component> t = new ArrayList<>();
        t.add(tr("mana.title").withStyle(ChatFormatting.BOLD).withColor(Gfx.MANA & 0xFFFFFF));
        t.add(tr("mana.cost", b.manaCost(), max).withStyle(b.manaCost() > max ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (b.perCopyExtra() > 0) {
            t.add(tr("mana.base", b.manaCost() - b.perCopyExtra()).withColor(Gfx.MANA & 0xFFFFFF));
            t.add(tr("mana.copies", b.perCopyExtra(), copyPercent(b)).withColor(Gfx.WARN & 0xFFFFFF));
        }
        t.add(tr("mana.rule").withStyle(ChatFormatting.DARK_GRAY));
        if (b.manaCost() > max) t.add(tr("mana.over").withStyle(ChatFormatting.RED));
        return t;
    }

    // ------------------------------------------------------------------ aviso de cópias
    private int renderCopyWarning(GuiGraphics g, HotZones zones, Font font, SpellBreakdown b, int x0, int y, int cw, Clip clip) {
        if (b.perCopyExtra() <= 0) return y;
        Worst w = worstCopy(b);
        boolean heavy = b.heavyCopies();
        int col = heavy ? Gfx.WARN : Gfx.TEXT_DIM;
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component head = Component.literal(heavy ? "⚠ " : "ⓘ ").append(tr(heavy ? "copies.heavy" : "copies.note", copyPercent(b), b.perCopyExtra()));
        lines.addAll(font.split(head, cw - 8));
        if (w != null) lines.addAll(font.split(copyLine(w).withStyle(ChatFormatting.WHITE), cw - 8));
        int boxH = lines.size() * LINE + 5;
        Gfx.round(g, x0 - 1, y, cw + 2, boxH, heavy ? 0x40FFA030 : 0x30FFFFFF);
        Gfx.frame(g, x0 - 1, y, cw + 2, boxH, Gfx.alpha(col, 0xA0));
        int ty = y + 3;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x0 + 3, ty, i == 0 ? col : 0xFFFFE6C0, false);
            ty += LINE;
        }
        zones.addClipped(x0 - 1, y, cw + 2, boxH, clip.x, clip.y, clip.w, clip.h, null, () -> {
            List<Component> t = new ArrayList<>();
            t.add(tr("copies.title").withStyle(ChatFormatting.BOLD).withColor(Gfx.WARN & 0xFFFFFF));
            t.add(tr("copies.tip1").withStyle(ChatFormatting.GRAY));
            t.add(tr("copies.tip2").withStyle(ChatFormatting.GRAY));
            if (w != null) t.add(copyLine(w).withColor(Gfx.WARN & 0xFFFFFF));
            t.add(tr("copies.tip3").withStyle(ChatFormatting.DARK_GRAY));
            return t;
        });
        return y + boxH + 4;
    }

    // ------------------------------------------------------------------ estágios
    private int renderStage(GuiGraphics g, HotZones zones, Font font, SpellBreakdown b, SpellBreakdown.StagePart st, int x0, int y,
                            int cw, Clip clip) {
        // cabeçalho: medalhão + forma + ligação de entrada | custo do estágio
        int med = 11;
        Gfx.circle(g, x0 + med / 2, y + med / 2, med / 2, 0xFF1C1428);
        Gfx.ring(g, x0 + med / 2, y + med / 2, med / 2, 1, MahouUi.GOLD);
        String num = String.valueOf(st.index() + 1);
        g.drawString(font, num, x0 + med / 2 - font.width(num) / 2 + 1, y + 2, MahouUi.GOLD_PALE, false);
        int ix = x0 + med + 3;
        GlyphIcons.draw(g, st.form(), ix, y - 1, 12, 0);
        int tx = ix + 15;
        Component cost = tr("cost_mana", st.totalCost());
        int costW = font.width(cost);
        MutableComponent title = Component.empty().append(st.form().displayName().copy().withStyle(ChatFormatting.BOLD));
        if (st.linkIn() != null) {
            title.append(Component.literal("  ")).append(linkName(st.linkIn()).withColor(LINK_COLOR & 0xFFFFFF));
        }
        int room = x0 + cw - costW - 6 - tx;
        g.drawString(font, MahouUi.fit(font, title, room), tx, y + 2, Gfx.GOLD, false);
        g.drawString(font, cost, x0 + cw - costW, y + 2, Gfx.MANA, false);
        int headY = y;
        zones.addClipped(x0, headY - 1, cw, 12, clip.x, clip.y, clip.w, clip.h, null, () -> stageTip(b, st));
        y += 12;
        shareBar(g, x0 + med + 3, y, cw - med - 3, st.totalCost(), b.manaCost(), 0xFFB98CFF);
        y += 4;

        // forma: custo, cópias, alcance, raio, duração
        int indent = x0 + med + 3;
        int iw = cw - med - 3;
        List<Component> fp = new ArrayList<>();
        fp.add(tr("form_cost", st.formCost()).withColor(Gfx.MANA & 0xFFFFFF));
        float copies = st.instances() * st.triggers();
        if (copies > 1.001F) fp.add(tr("copies", SpellBreakdown.num(copies)).withColor(Gfx.WARN & 0xFFFFFF));
        if (st.triggers() > 1.001F) fp.add(tr("fires", SpellBreakdown.num(st.triggers())).withColor(LINK_COLOR & 0xFFFFFF));
        if (st.range() >= 0) fp.add(tr("range", SpellBreakdown.num(st.range())));
        if (st.radius() >= 0) fp.add(tr("radius", SpellBreakdown.num(st.radius())));
        if (st.durationTicks() >= 0) fp.add(tr("duration", SpellBreakdown.seconds(st.durationTicks())));
        if (st.fixedCost() > 0) fp.add(tr("fixed_cost", st.fixedCost()).withColor(Gfx.MANA & 0xFFFFFF));
        int fy = y;
        y = flow(g, font, fp, indent, y, iw, Gfx.TEXT_DIM);
        zones.addClipped(indent, fy, iw, y - fy, clip.x, clip.y, clip.w, clip.h, null, () -> formTip(st));

        // efeitos
        for (SpellBreakdown.EffectPart p : st.effects()) y = renderEffect(g, zones, font, b, st, p, indent, y + 1, iw, clip);
        if (st.effects().isEmpty()) {
            g.drawString(font, tr("no_effects"), indent, y, Gfx.TEXT_FAINT, false);
            y += LINE;
        }
        // separador
        g.fill(x0, y + 2, x0 + cw, y + 3, Gfx.alpha(Gfx.FRAME, 0x90));
        return y + 6;
    }

    private int renderEffect(GuiGraphics g, HotZones zones, Font font, SpellBreakdown b, SpellBreakdown.StagePart st,
                             SpellBreakdown.EffectPart p, int x0, int y, int cw, Clip clip) {
        int top = y;
        GlyphIcons.draw(g, p.effect(), x0, y, 12, 0);
        int tx = x0 + 15;
        float times = p.copies() * p.multiplier();
        boolean many = times > 1.001F;
        Component cost = many
                ? tr("effect_cost_copies", p.costPerCopy(), SpellBreakdown.num(times), p.totalCost())
                : tr("cost_mana", p.totalCost());
        int costW = font.width(cost);
        int nameRoom = x0 + cw - costW - 5 - tx;
        Component name = p.effect().displayName().copy().withColor(MahouUi.lighten(GlyphIcons.baseColor(p.effect()), 0.3F) & 0xFFFFFF);
        if (nameRoom < 30) {
            // estreito: nome numa linha, custo na seguinte
            g.drawString(font, MahouUi.fit(font, name, x0 + cw - tx), tx, y + 2, 0xFFFFFFFF, false);
            y += LINE;
            g.drawString(font, MahouUi.fit(font, cost, x0 + cw - tx), tx, y + 2, many ? Gfx.WARN : Gfx.MANA, false);
        } else {
            g.drawString(font, MahouUi.fit(font, name, nameRoom), tx, y + 2, 0xFFFFFFFF, false);
            g.drawString(font, cost, x0 + cw - costW, y + 2, many ? Gfx.WARN : Gfx.MANA, false);
        }
        y += 12;
        List<Component> ep = new ArrayList<>();
        if (p.damagePerHit() > 0) {
            ep.add(many ? tr("damage_hit_total", SpellBreakdown.num(p.damagePerHit()), SpellBreakdown.num(p.damageTotal())).withColor(DMG & 0xFFFFFF)
                    : tr("damage_hit", SpellBreakdown.num(p.damagePerHit())).withColor(DMG & 0xFFFFFF));
        }
        if (p.healPerHit() > 0) {
            ep.add(many ? tr("heal_hit_total", SpellBreakdown.num(p.healPerHit()), SpellBreakdown.num(p.healTotal())).withColor(HEAL & 0xFFFFFF)
                    : tr("heal_hit", SpellBreakdown.num(p.healPerHit())).withColor(HEAL & 0xFFFFFF));
        }
        if (Math.abs(p.stats().power() - 1F) > 0.01F) ep.add(tr("power", String.format(Locale.ROOT, "%.2f", p.stats().power())));
        if (p.durationTicks() >= 0) ep.add(tr("duration", SpellBreakdown.seconds(p.durationTicks())));
        if (p.radius() >= 0) ep.add(tr("radius", SpellBreakdown.num(p.radius())));
        if (p.multiplier() > 1.001F) ep.add(tr("repeats", SpellBreakdown.num(p.multiplier())).withColor(LINK_COLOR & 0xFFFFFF));
        if (!ep.isEmpty()) y = flow(g, font, ep, tx, y - 1, x0 + cw - tx, Gfx.TEXT_DIM);
        shareBar(g, tx, y, x0 + cw - tx, p.totalCost(), b.manaCost(), many ? Gfx.WARN : Gfx.MANA);
        y += 4;
        zones.addClipped(x0, top, cw, y - top, clip.x, clip.y, clip.w, clip.h, null, () -> effectTip(b, st, p));
        return y;
    }

    private int renderFootnote(GuiGraphics g, Font font, SpellCheck check, int x0, int y, int cw) {
        for (FormattedCharSequence l : font.split(tr("footnote"), cw)) {
            g.drawString(font, l, x0, y, Gfx.TEXT_FAINT, false);
            y += 9;
        }
        return y + 2;
    }

    /** Barrinha fina com a fatia de {@code part} em {@code total}. */
    private static void shareBar(GuiGraphics g, int x, int y, int w, int part, int total, int color) {
        if (w <= 4 || total <= 0) return;
        g.fill(x, y, x + w, y + 2, 0x40FFFFFF);
        int fw = Math.max(1, Math.round(Math.min(1F, part / (float) total) * w));
        g.fill(x, y, x + fw, y + 2, color);
    }

    /** Desenha partes separadas por " · ", quebrando a linha quando não cabe. Devolve o y seguinte. */
    private static int flow(GuiGraphics g, Font font, List<Component> parts, int x0, int y, int w, int color) {
        int x = x0;
        String sep = " · ";
        int sepW = font.width(sep);
        for (int i = 0; i < parts.size(); i++) {
            Component c = parts.get(i);
            int cw = font.width(c);
            if (x > x0 && x + sepW + cw > x0 + w) {
                x = x0;
                y += LINE;
            } else if (x > x0) {
                g.drawString(font, sep, x, y + 2, Gfx.TEXT_FAINT, false);
                x += sepW;
            }
            if (cw > w) {
                g.drawString(font, MahouUi.fit(font, c, w), x, y + 2, color, false);
                x = x0 + w;
            } else {
                g.drawString(font, c, x, y + 2, color, false);
                x += cw;
            }
        }
        return y + LINE + 1;
    }

    /** "(Ao Acertar)"... — as mesmas traduções do detalhamento do núcleo. */
    private static MutableComponent linkName(LinkGlyph.LinkKind k) {
        return switch (k) {
            case HIT -> Component.translatable("spell_info.isekai_mahou.link.hit");
            case END -> Component.translatable("spell_info.isekai_mahou.link.end");
            case KILL -> Component.translatable("spell_info.isekai_mahou.link.kill");
            case LEAP -> Component.translatable("spell_info.isekai_mahou.link.leap");
        };
    }

    // ------------------------------------------------------------------ dicas
    private List<Component> stageTip(SpellBreakdown b, SpellBreakdown.StagePart st) {
        List<Component> t = new ArrayList<>();
        t.add(tr("stage.title", st.index() + 1, st.form().displayName()).withStyle(ChatFormatting.BOLD).withColor(MahouUi.GOLD & 0xFFFFFF));
        if (st.linkIn() != null) t.add(tr("stage.born", GlyphIcons.linkRole(st.linkIn())).withColor(LINK_COLOR & 0xFFFFFF));
        t.add(tr("stage.cost", st.totalCost(), pct(st.totalCost(), b.manaCost())).withColor(Gfx.MANA & 0xFFFFFF));
        if (st.damage() > 0) t.add(tr("stage.damage", SpellBreakdown.num(st.damage())).withColor(DMG & 0xFFFFFF));
        if (st.heal() > 0) t.add(tr("stage.heal", SpellBreakdown.num(st.heal())).withColor(HEAL & 0xFFFFFF));
        if (st.triggers() > 1.001F) t.add(tr("stage.fires", SpellBreakdown.num(st.triggers())).withStyle(ChatFormatting.GRAY));
        return t;
    }

    private List<Component> formTip(SpellBreakdown.StagePart st) {
        List<Component> t = new ArrayList<>();
        t.add(st.form().displayName().copy().withStyle(ChatFormatting.BOLD).withColor(GlyphIcons.kindColor(GlyphKind.FORM) & 0xFFFFFF));
        t.add(tr("form.cost", st.formCost()).withColor(Gfx.MANA & 0xFFFFFF));
        if (st.fixedCost() > 0) t.add(tr("form.fixed", st.fixedCost()).withColor(Gfx.MANA & 0xFFFFFF));
        t.add(tr("form.copies", SpellBreakdown.num(st.copies()), SpellBreakdown.num(st.instances())).withStyle(ChatFormatting.GRAY));
        t.add(tr("form.copies_rule").withStyle(ChatFormatting.DARK_GRAY));
        if (st.range() >= 0) t.add(tr("range", SpellBreakdown.num(st.range())).withStyle(ChatFormatting.GRAY));
        if (st.radius() >= 0) t.add(tr("radius", SpellBreakdown.num(st.radius())).withStyle(ChatFormatting.GRAY));
        if (st.durationTicks() >= 0) t.add(tr("duration", SpellBreakdown.seconds(st.durationTicks())).withStyle(ChatFormatting.GRAY));
        return t;
    }

    private List<Component> effectTip(SpellBreakdown b, SpellBreakdown.StagePart st, SpellBreakdown.EffectPart p) {
        List<Component> t = new ArrayList<>();
        t.add(p.effect().displayName().copy().withStyle(ChatFormatting.BOLD).withColor(MahouUi.lighten(GlyphIcons.baseColor(p.effect()), 0.3F) & 0xFFFFFF));
        t.add(tr("effect.per_copy", p.costPerCopy()).withColor(Gfx.MANA & 0xFFFFFF));
        float times = p.copies() * p.multiplier();
        if (times > 1.001F) {
            t.add(tr("effect.times", SpellBreakdown.num(p.copies()), SpellBreakdown.num(p.multiplier()), st.form().displayName()).withColor(Gfx.WARN & 0xFFFFFF));
        }
        t.add(tr("effect.total", p.totalCost(), pct(p.totalCost(), b.manaCost())).withColor(Gfx.MANA & 0xFFFFFF));
        if (p.damagePerHit() > 0) {
            t.add(tr("effect.damage", SpellBreakdown.num(p.damagePerHit()), SpellBreakdown.num(p.damageTotal())).withColor(DMG & 0xFFFFFF));
        }
        if (p.healPerHit() > 0) {
            t.add(tr("effect.heal", SpellBreakdown.num(p.healPerHit()), SpellBreakdown.num(p.healTotal())).withColor(HEAL & 0xFFFFFF));
        }
        if (Math.abs(p.stats().power() - 1F) > 0.01F) t.add(tr("effect.power", String.format(Locale.ROOT, "%.2f", p.stats().power())).withStyle(ChatFormatting.GRAY));
        if (p.durationTicks() >= 0) t.add(tr("duration", SpellBreakdown.seconds(p.durationTicks())).withStyle(ChatFormatting.GRAY));
        if (p.radius() >= 0) t.add(tr("radius", SpellBreakdown.num(p.radius())).withStyle(ChatFormatting.GRAY));
        t.add(tr("estimate.tip").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private static int pct(int part, int total) {
        return total <= 0 ? 0 : Math.round(100F * part / total);
    }

    /** Linhas curtas para dicas (célula de loadout, preset): custo, dano e o aviso de cópias. */
    static List<Component> summaryLines(@Nullable ParsedSpell sp) {
        List<Component> out = new ArrayList<>();
        if (sp == null) return out;
        SpellBreakdown b = sp.breakdown();
        MutableComponent l = tr("short", b.manaCost(), SpellBreakdown.seconds(b.castTicks()), SpellBreakdown.seconds(b.cooldown()));
        out.add(l.withColor(Gfx.MANA & 0xFFFFFF));
        if (b.damage() > 0 || b.heal() > 0) {
            MutableComponent e = Component.empty();
            if (b.damage() > 0) e.append(tr("damage_total", SpellBreakdown.num(b.damage())).withColor(DMG & 0xFFFFFF));
            if (b.heal() > 0) {
                if (b.damage() > 0) e.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
                e.append(tr("heal_total", SpellBreakdown.num(b.heal())).withColor(HEAL & 0xFFFFFF));
            }
            out.add(e);
        }
        if (b.heavyCopies()) {
            Worst w = worstCopy(b);
            out.add(Component.literal("⚠ ").append(w != null ? copyLine(w) : tr("copies.heavy", copyPercent(b), b.perCopyExtra()))
                    .withColor(Gfx.WARN & 0xFFFFFF));
        }
        return out;
    }
}
