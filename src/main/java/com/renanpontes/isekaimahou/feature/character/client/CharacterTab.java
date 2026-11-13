package com.renanpontes.isekaimahou.feature.character.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceGlyphs;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;
import com.renanpontes.isekaimahou.feature.character.SlimePredator;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Raça" do menu B. Cartão da raça (medalhão, classe e nível, NÍVEL DE RAÇA com a barra até o próximo, estado da
 * Transformação, ancestralidade), no meio a PASSIVA nível a nível, as 2 SKILLS RACIAIS, a TRANSFORMAÇÃO, as skills de
 * outras raças (Relíquias) e os traços; à direita o PROGRESSO RACIAL (I–V, o feito com a barra e o próximo desbloqueio)
 * e o Altar. Sem raça escolhida: botão "Escolher Raça".
 */
public class CharacterTab implements MenuTab {
    private StatusScreen screen;
    private int x, y, w, h;
    private int cardW, midX, midW, sideX, sideW;
    private int midScroll, midContentH, sideScroll, sideContentH;
    private int seenVersion = -1;
    private Font font;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        cardW = Mth.clamp(w * 28 / 100, 112, 150);
        midX = x + cardW + 8;
        int rest = x + w - midX;
        boolean wide = rest >= 300;
        midW = wide ? Mth.clamp(rest * 58 / 100, 176, 240) : rest - 4;
        sideX = midX + midW + 8;
        sideW = wide ? x + w - sideX - 4 : 0;
        seenVersion = ClientCharacter.version();

        CharacterData d = ClientCharacter.data();
        if (!d.created() && ClientMagic.data().path() != MagicPath.NONE) {
            screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.character.build"),
                    b -> Minecraft.getInstance().setScreen(new CreationScreen(CharacterNetwork.MODE_TAB, BlockPos.ZERO)))
                    .bounds(x + 4, y + h - 24, cardW - 8, 20).build());
        } else if (d.created() && (d.race().darkvision != Race.Darkvision.NONE || SlimePredator.has(d, SlimePredator.Mimic.BAT))) {
            screen.addTabWidget(Button.builder(Component.translatable(d.darkvision()
                            ? "screen.isekai_mahou.character.darkvision_on" : "screen.isekai_mahou.character.darkvision_off"), b -> {
                        PacketDistributor.sendToServer(new CharacterNetwork.Toggle(0, !ClientCharacter.data().darkvision()));
                        click(1.2F);
                    })
                    .bounds(x + 4, y + h - 24, cardW - 8, 20).build());
        }
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6F));
    }

    @Override
    public void tick() {
        // Dados novos (raça escolhida, visão alternada...): refaz os botões
        if (ClientCharacter.version() != seenVersion) screen.refreshTab();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sideW > 0 && mx >= sideX && sideContentH > h) {
            sideScroll = Mth.clamp(sideScroll - (int) Math.round(sy * 12), 0, sideContentH - h);
            return true;
        }
        if (mx >= midX && mx < midX + midW && midContentH > h) {
            midScroll = Mth.clamp(midScroll - (int) Math.round(sy * 12), 0, midContentH - h);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CharacterData d = ClientCharacter.data();
        List<Component> tip = new ArrayList<>();
        renderCard(g, d, mx, my, tip);
        g.fill(midX - 4, y + 2, midX - 3, y + h - 2, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
        renderMiddle(g, d, mx, my, tip);
        if (sideW > 0) {
            g.fill(sideX - 4, y + 2, sideX - 3, y + h - 2, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
            renderSide(g, d, mx, my, tip);
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    /** Ticks de jogo agora (mesma base do formReadyAt do servidor: todas as dimensões contam juntas). */
    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    // ---------------------------------------------------------------- Cartão
    private void renderCard(GuiGraphics g, CharacterData d, int mx, int my, List<Component> tip) {
        int cx = x + 2, cw = cardW - 4, cy = y + 2;
        Race r = d.race();
        int accent = d.created() ? r.argb() : 0xFF6A4BA0;
        MahouUi.roundRect(g, cx, cy, cw, h - 30, MahouUi.mix(0xF0201A2E, accent, 0.12F), 0xF0100C18);
        MahouUi.roundOutline(g, cx, cy, cw, h - 30, MahouUi.alpha(accent, 0xA0));
        int ey = cy + 8;
        int glow = (int) (0x40 + 0x30 * Math.sin(System.currentTimeMillis() / 400.0));
        MahouUi.diamond(g, cx + cw / 2, ey + 16, 19, MahouUi.alpha(accent, glow));
        CharacterUi.emblem(g, font, r, cx + cw / 2 - 16, ey, 32, !d.created());
        ey += 38;
        Component name = d.created() ? r.displayName() : Component.translatable("screen.isekai_mahou.character.unbuilt").withColor(0xA89EC0);
        g.pose().pushPose();
        float scale = font.width(name) * 1.4F <= cw - 6 ? 1.4F : 1F;
        g.pose().translate(cx + cw / 2F, ey, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, name, -font.width(name) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        ey += (int) (10 * scale) + 2;
        MagicData md = ClientMagic.data();
        MagicPath path = md.path();
        if (path != MagicPath.NONE) {
            Component sub = path.displayName().append(Component.literal(" · ").withColor(0xA89EC0))
                    .append(Component.translatable("screen.isekai_mahou.character.level", md.level()).withColor(0xA89EC0));
            g.drawCenteredString(font, MahouUi.fit(font, sub, cw - 6), cx + cw / 2, ey, 0xFFFFFFFF);
            ey += 12;
        }
        if (!d.created()) {
            CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.unbuilt_desc"), cx + 5, ey + 2, cw - 10, MahouUi.TEXT_DIM);
            return;
        }
        // Nível de raça + barra até o próximo
        int lv = RaceProgress.level(r, d, md.level());
        Component lvText = Component.translatable("screen.isekai_mahou.character.race_level", RaceProgress.roman(lv)).withColor(MahouUi.GOLD & 0xFFFFFF);
        g.drawCenteredString(font, MahouUi.fit(font, lvText, cw - 6), cx + cw / 2, ey, 0xFFFFFFFF);
        ey += 11;
        float prog = nextProgress(r, d, md.level(), lv);
        MahouUi.bar(g, cx + 8, ey, cw - 16, 4, prog, r.argb());
        if (CharacterUi.in(mx, my, cx + 6, ey - 12, cw - 12, 18)) {
            tip.clear();
            tip.add(lvText);
            tip.add(nextLine(r, d, md.level(), lv));
        }
        ey += 8;
        // Transformação
        Component form = formState(r, d, lv);
        ey = CharacterUi.paragraph(g, font, form, cx + 5, ey, cw - 10, 0xFFFFFFFF) + 2;
        if (r == Race.DRAGONBORN) {
            g.drawCenteredString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.ancestry_short",
                    d.ancestry().displayName()), cw - 6), cx + cw / 2, ey, 0xFFFFFFFF);
            ey += 11;
        }
        // v1.7: estágio de evolução (Kumoko, Rimuru, Geld) e as habilidades do Predador (passe o mouse para a lista)
        if (r.hasStages()) {
            g.drawCenteredString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.stage",
                    r.stageName(lv).withColor(r.rgb)).withColor(0xA89EC0), cw - 6), cx + cw / 2, ey, 0xFFFFFFFF);
            ey += 11;
        }
        if (r == Race.SLIME) {
            int cap = SlimePredator.capacity(lv);
            Component pred = Component.translatable("screen.isekai_mahou.character.predator", d.mimics().size(), cap).withColor(0x7FD4FF);
            g.drawCenteredString(font, MahouUi.fit(font, pred, cw - 6), cx + cw / 2, ey, 0xFFFFFFFF);
            if (CharacterUi.in(mx, my, cx + 6, ey - 1, cw - 12, 11)) {
                tip.clear();
                tip.add(pred);
                for (String id : d.mimics()) {
                    SlimePredator.Mimic m = SlimePredator.Mimic.byId(id);
                    if (m != null) tip.add(m.displayName().append(Component.literal(" — ").withColor(0x6C6480)).append(m.desc().withColor(0xE8E2F4)));
                }
                if (d.mimics().isEmpty()) tip.add(Component.translatable("screen.isekai_mahou.character.predator_none").withColor(0xA89EC0));
            }
            ey += 11;
        }
        if (d.rebirths() > 0) {
            g.drawCenteredString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.rebirths", d.rebirths()),
                    cw - 6), cx + cw / 2, ey, MahouUi.TEXT_DIM);
            ey += 11;
        }
        g.fill(cx + 6, ey, cx + cw - 6, ey + 1, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
        ey += 4;
        Component key = Component.literal("[").append(Component.keybind("key.isekai_mahou.racial_ability")).append("] ")
                .withColor(0xA89EC0).append(Component.translatable(lv >= RaceProgress.MAX_LEVEL
                        ? "screen.isekai_mahou.character.key_form" : r.darkvision != Race.Darkvision.NONE
                        ? "screen.isekai_mahou.character.key_vision" : "screen.isekai_mahou.character.key_none").withColor(r.rgb));
        ey = CharacterUi.paragraph(g, font, key, cx + 5, ey, cw - 10, 0xFFFFFFFF);
        if (r.hasShiftAction()) {
            // v1.7: Shift + tecla racial = Fio de Aranha / Grande Sábio / Devorar Elemento
            CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.key_shift." + r.id).withColor(0xA89EC0),
                    cx + 5, ey, cw - 10, 0xFFFFFFFF);
        } else if (r.darkvision != Race.Darkvision.NONE && lv >= RaceProgress.MAX_LEVEL) {
            CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.key_vision_shift").withColor(0xA89EC0),
                    cx + 5, ey, cw - 10, 0xFFFFFFFF);
        }
    }

    /** Progresso (0..1) até o próximo nível de raça: nível do personagem e, do IV para o V, também o feito. */
    private static float nextProgress(Race r, CharacterData d, int charLevel, int lv) {
        if (lv >= RaceProgress.MAX_LEVEL) return 1F;
        int from = RaceProgress.charLevelFor(lv), to = RaceProgress.charLevelFor(lv + 1);
        float lvl = Mth.clamp((charLevel - from) / (float) Math.max(1, to - from), 0F, 1F);
        if (lv + 1 < RaceProgress.MAX_LEVEL) return lvl;
        return 0.5F * lvl + 0.5F * RaceProgress.featProgress(d, r);
    }

    private static Component nextLine(Race r, CharacterData d, int charLevel, int lv) {
        if (lv >= RaceProgress.MAX_LEVEL) return Component.translatable("screen.isekai_mahou.character.race_level_max").withColor(0x8CE07A);
        int need = RaceProgress.charLevelFor(lv + 1);
        if (lv + 1 < RaceProgress.MAX_LEVEL) {
            return Component.translatable("screen.isekai_mahou.character.next_level", RaceProgress.roman(lv + 1), need, charLevel)
                    .withColor(0xE8E2F4);
        }
        int goal = RaceProgress.goal(r);
        return Component.translatable("screen.isekai_mahou.character.next_form", need, charLevel, r.featDesc(goal),
                Math.min(goal, RaceProgress.featCount(d, r)), goal).withColor(0xE8E2F4);
    }

    private static Component formState(Race r, CharacterData d, int lv) {
        Minecraft mc = Minecraft.getInstance();
        if (lv < RaceProgress.MAX_LEVEL) {
            return Component.translatable("screen.isekai_mahou.character.form_state_locked", r.formName().withColor(0x8A82A0))
                    .withColor(0xA89EC0);
        }
        ClientCharacter.Fx fx = mc.player == null ? null : ClientCharacter.form(mc.player.getId());
        if (fx != null) {
            int secs = (int) Math.max(0, (fx.until() - now()) / 20);
            return Component.translatable("screen.isekai_mahou.character.form_state_active", r.formName().withColor(r.rgb), secs)
                    .withColor(0x8CE07A);
        }
        long left = d.formReadyAt() - now();
        if (left > 0) {
            int secs = (int) (left / 20);
            return Component.translatable("screen.isekai_mahou.character.form_state_cooldown", r.formName().withColor(r.rgb),
                    secs / 60, String.format("%02d", secs % 60)).withColor(0xE0A040);
        }
        return Component.translatable("screen.isekai_mahou.character.form_state_ready", r.formName().withColor(r.rgb))
                .withColor(0x8CE07A);
    }

    // ---------------------------------------------------------------- Passiva, skills, Transformação e traços
    private void renderMiddle(GuiGraphics g, CharacterData d, int mx, int my, List<Component> tip) {
        g.enableScissor(midX - 2, y, midX + midW + 2, y + h);
        int sy = y + 3 - midScroll;
        Race r = d.created() ? d.race() : Race.HUMAN;
        boolean active = d.created();
        MagicData md = ClientMagic.data();
        int lv = active ? RaceProgress.level(r, d, md.level()) : 0;

        g.drawString(font, Component.translatable("screen.isekai_mahou.character.passive").withColor(MahouUi.GOLD & 0xFFFFFF), midX, sy, 0xFFFFFFFF, true);
        sy += 12;
        MahouUi.roundRect(g, midX, sy, midW, 1, MahouUi.alpha(r.argb(), 0x80), MahouUi.alpha(r.argb(), 0x80));
        sy += 4;
        MahouUi.diamond(g, midX + 3, sy + 4, 2, r.argb());
        g.drawString(font, MahouUi.fit(font, r.passiveName().withColor(active ? r.rgb : 0x6C6480), midW - 10), midX + 9, sy, 0xFFFFFFFF, true);
        sy += 12;
        for (int i = 1; i <= 4; i++) sy = CharacterUi.levelLine(g, font, r, i, lv >= i, midX + 4, sy, midW - 6) + 2;
        sy += 6;

        g.drawString(font, Component.translatable("screen.isekai_mahou.character.skills").withColor(MahouUi.GOLD & 0xFFFFFF), midX, sy, 0xFFFFFFFF, true);
        sy += 12;
        for (RaceGlyphs.Skill s : RaceGlyphs.of(r)) {
            boolean known = active && md.knowsGlyph(s.id());
            boolean hover = CharacterUi.in(mx, my, midX, sy, midW, 22) && my >= y && my < y + h;
            sy += CharacterUi.skillRow(g, font, s, known, md.level() >= s.level(), midX, sy, midW, hover) + 3;
            if (hover) {
                tip.clear();
                tip.addAll(CharacterUi.skillTooltip(s, known));
            }
        }
        sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.skills_tab_note",
                Component.keybind("key.isekai_mahou.menu")).withStyle(s -> s.withItalic(true)), midX, sy + 1, midW, MahouUi.TEXT_DIM);
        sy += 6;

        // Transformação
        boolean hoverForm = CharacterUi.in(mx, my, midX, sy, midW, 30) && my >= y && my < y + h;
        sy += CharacterUi.formCard(g, font, r, lv >= RaceProgress.MAX_LEVEL, midX, sy, midW, hoverForm) + 3;
        if (hoverForm) {
            tip.clear();
            tip.addAll(CharacterUi.formTooltip(r, d));
        }
        sy = CharacterUi.paragraph(g, font, r.formDesc(), midX + 4, sy, midW - 6, lv >= RaceProgress.MAX_LEVEL ? MahouUi.TEXT : MahouUi.TEXT_DIM);
        sy += 8;

        // Skills de outras raças: só com Relíquias Raciais
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.foreign_skills").withColor(MahouUi.GOLD & 0xFFFFFF), midX, sy, 0xFFFFFFFF, true);
        sy += 12;
        boolean anyForeign = false;
        for (RaceGlyphs.Skill s : RaceGlyphs.all()) {
            if (s.race() == r || !md.knowsGlyph(s.id())) continue;
            anyForeign = true;
            boolean hover = CharacterUi.in(mx, my, midX, sy, midW, 22) && my >= y && my < y + h;
            sy += CharacterUi.skillRow(g, font, s, true, true, midX, sy, midW, hover) + 3;
            if (hover) {
                tip.clear();
                tip.addAll(CharacterUi.skillTooltip(s, true));
                tip.add(Component.translatable("screen.isekai_mahou.character.foreign_skill_tip").withColor(0x9AD0FF));
            }
        }
        if (!anyForeign) {
            sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.foreign_none"), midX + 9, sy, midW - 10, MahouUi.TEXT_DIM);
        }
        sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.relics_note")
                .withStyle(s -> s.withItalic(true)), midX, sy + 1, midW, 0xFF9AD0FF);
        sy += 8;

        if (!r.traits.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.character.traits").withColor(MahouUi.GOLD & 0xFFFFFF), midX, sy, 0xFFFFFFFF, true);
            sy += 12;
            for (String t : r.traits) {
                MahouUi.diamond(g, midX + 3, sy + 4, 2, r.argb());
                g.drawString(font, MahouUi.fit(font, Race.traitName(t).withColor(r.rgb), midW - 10), midX + 9, sy, 0xFFFFFFFF, false);
                sy += 10;
                sy = CharacterUi.paragraph(g, font, Race.traitDesc(t), midX + 9, sy, midW - 10, MahouUi.TEXT_DIM);
                sy += 3;
            }
        }
        if (sideW <= 0) {
            sy += 4;
            sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.altar_info"), midX, sy, midW, MahouUi.TEXT_DIM);
        }
        g.disableScissor();
        midContentH = sy + midScroll - y;
        midScroll = Mth.clamp(midScroll, 0, Math.max(0, midContentH - h));
        CharacterUi.scrollbar(g, midX + midW + 1, y, h, midScroll, midContentH);
    }

    // ---------------------------------------------------------------- Progresso racial
    private void renderSide(GuiGraphics g, CharacterData d, int mx, int my, List<Component> tip) {
        g.enableScissor(sideX - 2, y, sideX + sideW + 2, y + h);
        int sy = y + 3 - sideScroll;
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.progress").withColor(MahouUi.GOLD & 0xFFFFFF), sideX, sy, 0xFFFFFFFF, true);
        sy += 12;
        Race r = d.created() ? d.race() : Race.HUMAN;
        int charLevel = ClientMagic.data().level();
        int lv = d.created() ? RaceProgress.level(r, d, charLevel) : 0;
        for (int i = 1; i <= RaceProgress.MAX_LEVEL; i++) {
            boolean done = lv >= i;
            boolean next = lv + 1 == i;
            int rowH = 22;
            boolean hover = CharacterUi.in(mx, my, sideX, sy, sideW, rowH) && my >= y && my < y + h;
            int col = done ? r.argb() : next ? 0xFFFFD86B : 0xFF4A4060;
            MahouUi.roundRect(g, sideX, sy, sideW, rowH, MahouUi.mix(0xE0201A2E, col, done ? 0.18F : 0.06F), 0xE0100C18);
            MahouUi.roundOutline(g, sideX, sy, sideW, rowH, MahouUi.alpha(col, hover ? 0xF0 : 0x90));
            String rn = RaceProgress.roman(i);
            g.drawString(font, rn, sideX + 4, sy + 7, done ? 0xFFFFFFFF : 0xFF8A82A0, true);
            Component what = i == 1 ? Component.translatable("screen.isekai_mahou.character.unlock_1")
                    : i == 2 ? Component.translatable("screen.isekai_mahou.character.unlock_2")
                    : i < RaceProgress.MAX_LEVEL ? Component.translatable("screen.isekai_mahou.character.unlock_passive")
                    : r.formName();
            g.drawString(font, MahouUi.fit(font, what.copy().withColor(done ? r.rgb : 0xC8C0D8), sideW - 30), sideX + 20, sy + 3, 0xFFFFFFFF, false);
            Component req = i < RaceProgress.MAX_LEVEL
                    ? Component.translatable("screen.isekai_mahou.character.level_req", RaceProgress.charLevelFor(i))
                    : Component.translatable("screen.isekai_mahou.character.level_req_feat", RaceProgress.charLevelFor(i));
            MahouUi.smallText(g, font, req, sideX + 20, sy + 13, 0.75F, done ? 0xFF8CE07A : 0xFFA89EC0, false);
            if (done) g.drawString(font, "✔", sideX + sideW - 10, sy + 7, 0xFF8CE07A, false);
            if (hover) {
                tip.clear();
                tip.add(Component.translatable("screen.isekai_mahou.character.race_level", rn).withColor(r.rgb));
                if (i < RaceProgress.MAX_LEVEL) tip.add(r.passiveLevel(i).withColor(0xE8E2F4));
                else tip.addAll(CharacterUi.formTooltip(r, d));
            }
            sy += rowH + 3;
        }
        // Feito da raça (nível V)
        sy += 3;
        int goal = RaceProgress.goal(r);
        int have = Math.min(goal, RaceProgress.featCount(d, r));
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.feat").withColor(MahouUi.GOLD & 0xFFFFFF), sideX, sy, 0xFFFFFFFF, true);
        sy += 11;
        sy = CharacterUi.paragraph(g, font, r.featDesc(goal), sideX, sy, sideW, MahouUi.TEXT_DIM);
        MahouUi.bar(g, sideX, sy + 2, sideW - 34, 4, have / (float) goal, have >= goal ? CharacterUi.GREEN : CharacterUi.PURPLE);
        g.drawString(font, have + "/" + goal, sideX + sideW - 30, sy, have >= goal ? CharacterUi.GREEN : MahouUi.TEXT_DIM, false);
        sy += 12;
        sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.feat_note")
                .withStyle(s -> s.withItalic(true)), sideX, sy, sideW, MahouUi.TEXT_DIM);
        sy += 6;
        sy = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.altar_info"), sideX, sy, sideW, MahouUi.TEXT_DIM);
        g.disableScissor();
        sideContentH = sy + sideScroll - y;
        sideScroll = Mth.clamp(sideScroll, 0, Math.max(0, sideContentH - h));
        CharacterUi.scrollbar(g, sideX + sideW + 1, y, h, sideScroll, sideContentH);
    }
}
