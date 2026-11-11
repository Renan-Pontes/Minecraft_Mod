package com.renanpontes.isekaimahou.feature.character.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.character.Race;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Peças de desenho das telas do personagem (no estilo da Janela de Status do núcleo). */
final class CharacterUi {
    static final ResourceLocation LOCK = IsekaiMahou.id("textures/gui/character/lock.png");
    static final int GREEN = 0xFF8CE07A, RED = 0xFFE07A7A, PURPLE = 0xFFB98CFF;

    /** Medalhão da raça (arte 32×32 da v1.0); sem a textura, um losango na cor da raça com a inicial. */
    static void emblem(GuiGraphics g, Font font, Race r, int x, int y, int size, boolean dim) {
        if (dim) g.setColor(0.45F, 0.42F, 0.5F, 1F);
        if (GlyphArt.exists(r.emblem())) {
            GlyphArt.icon(g, r.emblem(), x, y, size);
        } else {
            MahouUi.diamond(g, x + size / 2, y + size / 2, size / 2, r.argb());
            g.drawCenteredString(font, r.displayName().getString().substring(0, 1), x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
        }
        g.setColor(1F, 1F, 1F, 1F);
    }

    static void lock(GuiGraphics g, int x, int y, int size) {
        if (GlyphArt.exists(LOCK)) {
            GlyphArt.icon(g, LOCK, x, y, size);
            return;
        }
        int w = Math.max(5, size - 4);
        g.fill(x + (size - w) / 2, y + size / 2, x + (size + w) / 2, y + size - 1, MahouUi.GOLD_DIM);
        g.renderOutline(x + size / 2 - w / 3, y + 1, 2 * w / 3, size / 2, MahouUi.GOLD_DIM);
    }

    /** Placa de título no meio da borda de cima do painel. */
    static void titlePlate(GuiGraphics g, Font font, Component title, int left, int top, int panelW) {
        int tw = Math.min(panelW - 16, font.width(title) + 22);
        int px = left + (panelW - tw) / 2, py = top - 6;
        MahouUi.roundRect(g, px, py, tw, 11, 0xFF2C2238, 0xFF14101C);
        MahouUi.roundOutline(g, px, py, tw, 11, MahouUi.GOLD);
        MahouUi.diamond(g, px + 5, py + 5, 2, MahouUi.GOLD);
        MahouUi.diamond(g, px + tw - 6, py + 5, 2, MahouUi.GOLD);
        g.drawString(font, MahouUi.fit(font, title, tw - 22), px + 11, py + 2, MahouUi.GOLD_PALE, false);
    }

    /** Aba do topo (ativa = dourada e aberta para baixo). */
    static void tab(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean active, boolean hover) {
        if (active) {
            MahouUi.roundRect(g, x, y, w, h + 1, 0xFF4E3C14, 0xFF1C1626);
            g.fill(x + 1, y, x + w - 1, y + 1, MahouUi.GOLD);
            g.fill(x, y + 1, x + 1, y + h + 1, MahouUi.GOLD);
            g.fill(x + w - 1, y + 1, x + w, y + h + 1, MahouUi.GOLD);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, MahouUi.alpha(MahouUi.GOLD_PALE, 0x90));
        } else {
            MahouUi.roundRect(g, x, y + 1, w, h - 1, hover ? 0xD8342A4C : 0xB81E182C, hover ? 0xD81C1628 : 0xB8120E1A);
            MahouUi.roundOutline(g, x, y + 1, w, h - 1, hover ? 0xFF8C7AB8 : 0xFF3E3458);
        }
        int color = active ? MahouUi.GOLD : hover ? 0xFFFFFFFF : MahouUi.TEXT_DIM;
        FormattedCharSequence s = MahouUi.fit(font, label, w - 8);
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2 + (active ? 0 : 1), color, true);
    }

    static int paragraph(GuiGraphics g, Font font, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(text, Math.max(10, w))) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    static int paragraphHeight(Font font, Component text, int w) {
        return font.split(text, Math.max(10, w)).size() * 10;
    }

    static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int contentH) {
        if (contentH <= h) return;
        int barH = Math.max(12, h * h / contentH);
        int by = y + (h - barH) * scroll / Math.max(1, contentH - h);
        g.fill(x, y, x + 2, y + h, 0x40FFFFFF);
        g.fill(x, by, x + 2, by + barH, MahouUi.GOLD_DIM);
    }

    /**
     * Linha de uma skill racial: ícone do glifo, nome na cor da raça, tipo e o estado (aprendida / nível N).
     * Devolve a altura usada.
     */
    static int skillRow(GuiGraphics g, Font font, com.renanpontes.isekaimahou.feature.character.RaceGlyphs.Skill skill,
                        boolean known, boolean reached, int x, int y, int w, boolean hover) {
        com.renanpontes.isekaimahou.glyph.Glyph glyph = skill.glyph();
        int h = 22;
        int race = skill.race().argb();
        MahouUi.roundRect(g, x, y, w, h, MahouUi.mix(0xE0201A2E, race, hover ? 0.20F : 0.09F), 0xE0100C18);
        MahouUi.roundOutline(g, x, y, w, h, MahouUi.alpha(race, hover ? 0xE0 : 0x80));
        if (glyph != null) {
            if (!known) g.setColor(0.55F, 0.52F, 0.6F, 1F);
            com.renanpontes.isekaimahou.client.GlyphArt.glyph(g, glyph, x + 3, y + 3, 16);
            g.setColor(1F, 1F, 1F, 1F);
        }
        Component name = glyph == null ? Component.literal(skill.path()) : glyph.displayName().withColor(skill.race().rgb);
        g.drawString(font, MahouUi.fit(font, name, w - 26), x + 23, y + 3, 0xFFFFFFFF, true);
        Component kind = glyph == null ? Component.empty() : Component.translatable(switch (glyph.kind()) {
            case FORM -> "screen.isekai_mahou.character.kind.form";
            case EFFECT -> "screen.isekai_mahou.character.kind.effect";
            case AUGMENT -> "screen.isekai_mahou.character.kind.augment";
            case TRIGGER -> "screen.isekai_mahou.character.kind.trigger";
            case LAW -> "screen.isekai_mahou.character.kind.law";
        });
        Component state = known ? Component.translatable("screen.isekai_mahou.character.skill_known").withColor(0x8CE07A)
                : Component.translatable("screen.isekai_mahou.character.skill_level", skill.level())
                        .withColor(reached ? 0xFFD86B : 0xA89EC0);
        MutableComponent sub = Component.empty().append(kind).append(Component.literal(" · ").withColor(0x6C6480)).append(state);
        MahouUi.smallText(g, font, sub, x + 23, y + 13, 0.75F, MahouUi.TEXT_DIM, false);
        return h;
    }

    /** Dica completa de uma skill racial (nome, tipo, custo, descrição, como liberar). */
    static java.util.List<Component> skillTooltip(com.renanpontes.isekaimahou.feature.character.RaceGlyphs.Skill skill, boolean known) {
        java.util.List<Component> tip = new java.util.ArrayList<>();
        com.renanpontes.isekaimahou.glyph.Glyph glyph = skill.glyph();
        if (glyph == null) return tip;
        tip.add(glyph.displayName().withColor(skill.race().rgb));
        tip.add(Component.translatable("screen.isekai_mahou.character.skill_origin", skill.race().displayName()).withColor(0xA89EC0));
        tip.add(Component.translatable("screen.isekai_mahou.character.skill_cost",
                Math.round(glyph.cost() * com.renanpontes.isekaimahou.glyph.SpellCost.COST_SCALE)).withColor(0x7FB8FF));
        tip.add(glyph.description().withColor(0xE8E2F4));
        tip.add(Component.translatable(known ? "screen.isekai_mahou.character.skill_known_tip"
                : "screen.isekai_mahou.character.skill_level_tip", skill.level()).withColor(known ? 0x8CE07A : 0xFFD86B));
        return tip;
    }

    /**
     * Uma linha do nível de raça {@code lv} (I–IV): selo com o numeral, "nível N" (do personagem) e o que a passiva
     * ganha. Devolve o y de baixo.
     */
    static int levelLine(GuiGraphics g, Font font, Race r, int lv, boolean reached, int x, int y, int w) {
        int col = reached ? r.argb() : 0xFF6C6480;
        int bw = 18;
        MahouUi.roundRect(g, x, y, bw, 10, MahouUi.mix(0xFF201A2E, col, reached ? 0.45F : 0.12F), 0xFF100C18);
        MahouUi.roundOutline(g, x, y, bw, 10, MahouUi.alpha(col, reached ? 0xE0 : 0x80));
        String rn = com.renanpontes.isekaimahou.feature.character.RaceProgress.roman(lv);
        g.drawString(font, rn, x + (bw - font.width(rn)) / 2, y + 1, reached ? 0xFFFFFFFF : 0xFF8A82A0, false);
        Component req = Component.translatable(reached ? "screen.isekai_mahou.character.level_reached"
                : "screen.isekai_mahou.character.level_req",
                com.renanpontes.isekaimahou.feature.character.RaceProgress.charLevelFor(lv));
        MahouUi.smallText(g, font, req, x + bw + 4, y + 2, 0.75F, reached ? GREEN : 0xFFA89EC0, false);
        return paragraph(g, font, r.passiveLevel(lv), x + bw + 4, y + 11, w - bw - 4, reached ? MahouUi.TEXT : MahouUi.TEXT_DIM);
    }

    /** Cartão da Transformação (nível V): medalhão, nome da forma e o golpe da forma. Devolve a altura. */
    static int formCard(GuiGraphics g, Font font, Race r, boolean unlocked, int x, int y, int w, boolean hover) {
        int h = 30;
        int col = unlocked ? r.argb() : 0xFF8A70C0;
        float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 350.0);
        MahouUi.roundRect(g, x, y, w, h, MahouUi.mix(0xF0241C34, col, hover ? 0.28F : 0.16F + 0.06F * pulse), 0xF0100C18);
        MahouUi.roundOutline(g, x, y, w, h, unlocked ? MahouUi.GOLD : MahouUi.alpha(col, 0xC0));
        MahouUi.diamond(g, x + 13, y + 15, 11, MahouUi.alpha(col, (int) (0x30 + 0x40 * pulse)));
        emblem(g, font, r, x + 5, y + 7, 16, !unlocked);
        Component title = Component.translatable("screen.isekai_mahou.character.form_title").withColor(MahouUi.GOLD & 0xFFFFFF)
                .append(Component.literal(" ")).append(r.formName().withColor(r.rgb));
        g.drawString(font, MahouUi.fit(font, title, w - 30), x + 26, y + 4, 0xFFFFFFFF, true);
        Component sub = Component.literal("[").append(Component.keybind("key.isekai_mahou.racial_ability")).append("] ")
                .append(r.formPower()).append(Component.literal(" · ").withColor(0x6C6480))
                .append(Component.translatable(unlocked ? "screen.isekai_mahou.character.form_ready_tag"
                        : "screen.isekai_mahou.character.form_locked_tag"));
        MahouUi.smallText(g, font, sub, x + 26, y + 17, 0.75F, unlocked ? GREEN : 0xFFA89EC0, false);
        return h;
    }

    /** Dica da Transformação: o que faz, o golpe, tempo/recarga e o feito. */
    static java.util.List<Component> formTooltip(Race r, com.renanpontes.isekaimahou.feature.character.CharacterData d) {
        java.util.List<Component> tip = new java.util.ArrayList<>();
        tip.add(r.formName().withColor(r.rgb));
        tip.add(r.formDesc().withColor(0xE8E2F4));
        tip.add(Component.literal("[").append(Component.keybind("key.isekai_mahou.racial_ability")).append("] ")
                .append(r.formPower()).append(": ").append(r.formPowerDesc()).withColor(0xFFD86B));
        tip.add(Component.translatable("screen.isekai_mahou.character.form_timing",
                com.renanpontes.isekaimahou.feature.character.RaceForms.DURATION / 20,
                com.renanpontes.isekaimahou.feature.character.RaceForms.COOLDOWN / 1200).withColor(0x7FB8FF));
        int goal = com.renanpontes.isekaimahou.feature.character.RaceProgress.goal(r);
        int have = Math.min(goal, com.renanpontes.isekaimahou.feature.character.RaceProgress.featCount(d, r));
        tip.add(Component.translatable("screen.isekai_mahou.character.form_feat", r.featDesc(goal), have, goal)
                .withColor(have >= goal ? 0x8CE07A : 0xB98CFF));
        return tip;
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private CharacterUi() {}
}
