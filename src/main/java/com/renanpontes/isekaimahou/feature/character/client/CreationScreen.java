package com.renanpontes.isekaimahou.feature.character.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.CharacterService;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceGlyphs;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Escolha a sua Raça" (v1.0.1: as 11 raças são livres). À esquerda a lista; à direita o medalhão, a descrição, a
 * PASSIVA nível a nível (I–IV, com o nível do personagem pedido), as 2 SKILLS RACIAIS (glifos do Grimório), a prévia da
 * TRANSFORMAÇÃO (nível V: nível 90 + feito da raça) e os traços.
 * Modos: criação (entrada no jogo, não fecha com Esc), aba (personagem antigo escolhendo pela 1ª vez) e altar (renascer:
 * troca de raça, custa Cristais de Mana). Teclado: ↑/↓ escolhem, Enter confirma.
 */
public class CreationScreen extends Screen {
    private static final int PAD = 8, FOOT_H = 24, HEAD_H = 10, GAP = 2;

    private final int mode;
    /** Posição do altar (modo altar; o servidor confere a distância). */
    private final BlockPos altar;
    private Race race;

    private int left, top, panelW, panelH, contentTop, contentBottom;
    private int listW, rowH;
    private int detailX, detailW, scroll, contentH;
    private Button confirmButton;
    private int footTextW;

    public CreationScreen(int mode, BlockPos altar) {
        super(Component.translatable(mode == CharacterNetwork.MODE_ALTAR
                ? "screen.isekai_mahou.character.altar_title" : "screen.isekai_mahou.character.title"));
        this.mode = mode;
        this.altar = altar;
        CharacterData d = ClientCharacter.data();
        this.race = d.created() ? d.race() : Race.HUMAN;
    }

    private static CharacterData data() {
        return ClientCharacter.data();
    }

    // =====================================================================================
    //  Layout
    // =====================================================================================
    @Override
    protected void init() {
        panelW = Mth.clamp(width - 24, 340, 500);
        panelH = Mth.clamp(height - 40, 220, 320);
        left = (width - panelW) / 2;
        top = Math.max(14, (height - panelH) / 2 + 2);
        contentTop = top + PAD + HEAD_H;
        contentBottom = top + panelH - PAD - FOOT_H;
        int contentHeight = contentBottom - contentTop;

        listW = Mth.clamp(panelW * 30 / 100, 104, 140);
        rowH = Mth.clamp((contentHeight - (Race.ALL.length - 1) * GAP) / Race.ALL.length, 11, 22);
        detailX = left + PAD + listW + 10;
        detailW = left + panelW - PAD - detailX - 4;

        int fy = top + panelH - PAD - 20;
        int confirmW = Math.min(150, panelW / 3);
        confirmButton = addRenderableWidget(Button.builder(confirmLabel(), b -> confirm())
                .bounds(left + panelW - PAD - confirmW, fy, confirmW, 20).build());
        footTextW = Math.max(40, panelW - 2 * PAD - confirmW - 8);
        updateConfirm();
    }

    private Component confirmLabel() {
        return Component.translatable(mode == CharacterNetwork.MODE_ALTAR
                ? "screen.isekai_mahou.character.reborn" : "screen.isekai_mahou.character.confirm");
    }

    private void updateConfirm() {
        if (confirmButton == null) return;
        confirmButton.setMessage(confirmLabel());
        CharacterData d = data();
        boolean same = mode == CharacterNetwork.MODE_ALTAR && d.created() && d.race() == race;
        confirmButton.active = !same && (mode != CharacterNetwork.MODE_ALTAR || canPayAltar());
    }

    private boolean canPayAltar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        // Primeira escolha (personagem antigo) é grátis também no altar
        return !data().created() || mc.player.getAbilities().instabuild
                || mc.player.getInventory().countItem(CoreRegistries.MANA_CRYSTAL.get()) >= CharacterService.ALTAR_COST;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6F));
    }

    private void confirm() {
        PacketDistributor.sendToServer(new CharacterNetwork.Confirm(mode, race.ordinal()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.3F, 0.5F));
        minecraft.setScreen(null);
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private void selectRace(int i) {
        i = Math.floorMod(i, Race.ALL.length);
        if (i == race.ordinal()) return;
        race = Race.byIndex(i);
        scroll = 0;
        updateConfirm();
        click(1.2F + 0.04F * i);
    }

    private int raceRowY(int i) { return contentTop + i * (rowH + GAP); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < Race.ALL.length; i++) {
            if (CharacterUi.in(mx, my, left + PAD, raceRowY(i), listW, rowH)) {
                selectRace(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= detailX && contentH > contentBottom - contentTop) {
            scroll = Mth.clamp(scroll - (int) Math.round(sy * 12), 0, contentH - (contentBottom - contentTop));
            return true;
        }
        if (mx < detailX && sy != 0) {
            selectRace(race.ordinal() + (sy > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> { selectRace(race.ordinal() - 1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { selectRace(race.ordinal() + 1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (confirmButton != null && confirmButton.active) confirm();
                return true;
            }
            default -> { return super.keyPressed(key, scan, mods); }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return mode != CharacterNetwork.MODE_CREATION;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MahouUi.frame(g, left, top, panelW, panelH, race.argb());
        CharacterUi.titlePlate(g, font, title, left, top, panelW);
        int dx = left + PAD + listW + 4;
        g.fill(dx, contentTop, dx + 1, contentBottom, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
        g.fill(left + 4, contentBottom + 2, left + panelW - 4, contentBottom + 3, MahouUi.alpha(MahouUi.GOLD_DIM, 0x60));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        List<Component> tip = renderRacePage(g, mx, my);
        renderFooter(g);
        Component note = Component.translatable(mode == CharacterNetwork.MODE_CREATION
                ? "screen.isekai_mahou.character.note" : mode == CharacterNetwork.MODE_ALTAR
                ? "screen.isekai_mahou.character.altar_note" : "screen.isekai_mahou.character.tab_note");
        g.drawCenteredString(font, note, width / 2, Math.min(height - 10, top + panelH + 5), MahouUi.TEXT_DIM);
        if (tip != null && !tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    private void renderFooter(GuiGraphics g) {
        int x = left + PAD, y = top + panelH - PAD - 16;
        MutableComponent line = Component.translatable("screen.isekai_mahou.character.footer_race", race.displayName());
        g.drawString(font, MahouUi.fit(font, line, footTextW), x, y, MahouUi.TEXT, false);
        CharacterData d = data();
        if (mode == CharacterNetwork.MODE_ALTAR && d.created()) {
            Minecraft mc = Minecraft.getInstance();
            int have = mc.player == null ? 0 : mc.player.getInventory().countItem(CoreRegistries.MANA_CRYSTAL.get());
            Component cost = d.race() == race
                    ? Component.translatable("screen.isekai_mahou.character.altar_same")
                    : Component.translatable("screen.isekai_mahou.character.altar_cost", CharacterService.ALTAR_COST,
                    CoreRegistries.MANA_CRYSTAL.get().getDescription(), have);
            g.drawString(font, MahouUi.fit(font, cost, footTextW), x, y + 10,
                    d.race() != race && canPayAltar() ? MahouUi.TEXT_DIM : 0xFFE07A7A, false);
        } else {
            g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.all_free"), footTextW),
                    x, y + 10, MahouUi.TEXT_DIM, false);
        }
    }

    private List<Component> renderRacePage(GuiGraphics g, int mx, int my) {
        CharacterData d = data();
        int x = left + PAD;
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.races").withColor(MahouUi.GOLD & 0xFFFFFF),
                x, top + PAD, 0xFFFFFFFF, true);
        for (int i = 0; i < Race.ALL.length; i++) {
            Race r = Race.ALL[i];
            int y = raceRowY(i);
            boolean sel = r == race, mine = d.created() && r == d.race();
            boolean hover = CharacterUi.in(mx, my, x, y, listW, rowH);
            if (sel) {
                MahouUi.roundRect(g, x, y, listW, rowH, MahouUi.mix(0xFF2A2240, r.argb(), 0.2F), 0xFF17131F);
                MahouUi.roundOutline(g, x, y, listW, rowH, r.argb());
            } else {
                MahouUi.roundRect(g, x, y, listW, rowH, hover ? 0xE0262036 : 0xC0181422, hover ? 0xE0181422 : 0xC0100C18);
                MahouUi.roundOutline(g, x, y, listW, rowH, hover ? 0xFF6C5C90 : 0xFF2E2642);
            }
            if (sel) g.fill(x + 1, y + 2, x + 3, y + rowH - 2, r.argb());
            int icon = Math.max(8, Math.min(16, rowH - 3));
            CharacterUi.emblem(g, font, r, x + 5, y + (rowH - icon) / 2, icon, false);
            int tx = x + 9 + icon;
            g.drawString(font, MahouUi.fit(font, r.displayName(), listW - (tx - x) - 12), tx, y + (rowH - 8) / 2, 0xFFFFFFFF, true);
            if (mine) MahouUi.diamond(g, x + listW - 7, y + rowH / 2, 2, MahouUi.GOLD);
        }
        return renderRaceDetails(g, race, d, mx, my);
    }

    private List<Component> renderRaceDetails(GuiGraphics g, Race r, CharacterData d, int mx, int my) {
        int x = detailX, w = detailW, viewH = contentBottom - contentTop;
        g.enableScissor(detailX - 2, contentTop, detailX + detailW + 2, contentBottom);
        int y = contentTop - scroll;
        int big = 32;
        float glow = 0.5F + 0.5F * Mth.sin((System.currentTimeMillis() % 100000L) / 300F);
        MahouUi.diamond(g, x + big / 2, y + big / 2, big / 2 + 2, MahouUi.alpha(r.argb(), (int) (0x30 + 0x30 * glow)));
        CharacterUi.emblem(g, font, r, x, y, big, false);
        g.pose().pushPose();
        g.pose().translate(x + big + 6, y + 3, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawString(font, MahouUi.fit(font, r.displayName(), (int) ((w - big - 6) / 1.5F)), 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.race_levels_tag")
                .withColor(0x8CE07A), w - big - 6), x + big + 6, y + 20, 0xFFFFFFFF, false);
        y += big + 6;
        y = CharacterUi.paragraph(g, font, r.description(), x, y, w, 0xFFDDD8EA);
        y += 6;

        int charLevel = ClientMagic.data().level();
        int lvNow = RaceProgress.level(r, d, charLevel);
        // Passiva por nível
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.passive").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
        y += 11;
        MahouUi.diamond(g, x + 3, y + 4, 2, r.argb());
        g.drawString(font, MahouUi.fit(font, r.passiveName().withColor(r.rgb), w - 10), x + 9, y, 0xFFFFFFFF, false);
        y += 11;
        for (int lv = 1; lv <= 4; lv++) {
            y = CharacterUi.levelLine(g, font, r, lv, lvNow >= lv, x + 4, y, w - 4) + 2;
        }
        y += 4;

        // Skills raciais (glifos)
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.skills").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
        y += 11;
        List<Component> tip = null;
        for (RaceGlyphs.Skill s : RaceGlyphs.of(r)) {
            boolean known = d.created() && ClientMagic.data().knowsGlyph(s.id()); // de outra raça: só por relíquia
            boolean hover = CharacterUi.in(mx, my, x, y, w, 22) && my >= contentTop && my < contentBottom;
            int h = CharacterUi.skillRow(g, font, s, known, charLevel >= s.level(), x, y, w, hover);
            if (hover) tip = CharacterUi.skillTooltip(s, known);
            y += h + 3;
        }
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.skills_note", RaceGlyphs.SECOND_SKILL_LEVEL)
                .withStyle(s -> s.withItalic(true)), x, y + 1, w, MahouUi.TEXT_DIM);
        y += 6;

        // Transformação (nível V)
        boolean hoverForm = CharacterUi.in(mx, my, x, y, w, 30) && my >= contentTop && my < contentBottom;
        int fh = CharacterUi.formCard(g, font, r, lvNow >= RaceProgress.MAX_LEVEL, x, y, w, hoverForm);
        if (hoverForm) tip = CharacterUi.formTooltip(r, d);
        y += fh + 3;
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.form_requirement",
                        RaceProgress.charLevelFor(RaceProgress.MAX_LEVEL), r.featDesc(RaceProgress.goal(r)))
                .withStyle(s -> s.withItalic(true)), x, y + 1, w, CharacterUi.PURPLE);
        y += 4;
        // Skills de outras raças: Relíquias Raciais (a desta raça)
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.relic_line",
                        CharacterContent.relic(r).get().getDescription().copy().withColor(r.rgb))
                .withStyle(s -> s.withItalic(true)), x, y + 1, w, 0xFF9AD0FF);
        y += 6;

        // Traços
        if (!r.traits.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.character.traits").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
            y += 12;
            for (String t : r.traits) {
                MahouUi.diamond(g, x + 3, y + 4, 2, r.argb());
                g.drawString(font, MahouUi.fit(font, Race.traitName(t).withColor(r.rgb), w - 10), x + 9, y, 0xFFFFFFFF, false);
                y += 10;
                y = CharacterUi.paragraph(g, font, Race.traitDesc(t), x + 9, y, w - 10, MahouUi.TEXT_DIM);
                y += 3;
            }
        }
        if (r == Race.DRAGONBORN) {
            Element anc = d.race() == Race.DRAGONBORN && d.created() ? d.ancestry() : ancestryPreview();
            y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.ancestry", anc.displayName()),
                    x, y + 2, w, MahouUi.TEXT_DIM);
        }
        g.disableScissor();
        contentH = y + scroll - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        CharacterUi.scrollbar(g, detailX + detailW + 2, contentTop, viewH, scroll, contentH);
        return tip;
    }

    private static Element ancestryPreview() {
        Element e = ClientMagic.data().path().primaryAffinity();
        return e != null && Element.BASE.contains(e) ? e : Element.FIRE;
    }
}
