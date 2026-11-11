package com.renanpontes.isekaimahou.client.classes;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ClassesPayloads;
import com.renanpontes.isekaimahou.core.classes.GlyphLearning;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela simples de escrever uma Lição: lista os glifos de origem da classe do jogador que ele conhece (o servidor manda a
 * lista), mostra o glifo escolhido (descrição e o que um aluno vai precisar) e "Escrever Lição" manda
 * {@link ClassesPayloads.WriteLesson}. Teclado: ↑/↓ escolhem, Enter escreve, Esc fecha.
 */
public class LessonWriteScreen extends Screen {
    private static final int PAD = 8, ROW_H = 20;

    private final InteractionHand hand;
    private final List<Glyph> glyphs = new ArrayList<>();
    private int selected;
    private int scroll;
    private int left, top, panelW, panelH, listW, listTop, listH, detailX, detailW;
    private Button writeButton;

    public LessonWriteScreen(InteractionHand hand, List<ResourceLocation> ids) {
        super(Component.translatable("screen.isekai_mahou.lesson.title"));
        this.hand = hand;
        for (ResourceLocation id : ids) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null) glyphs.add(g);
        }
    }

    @Override
    protected void init() {
        panelW = Mth.clamp(width - 24, 280, 420);
        panelH = Mth.clamp(height - 40, 170, 240);
        left = (width - panelW) / 2;
        top = Math.max(12, (height - panelH) / 2);
        listW = Mth.clamp(panelW * 40 / 100, 110, 170);
        listTop = top + PAD + 14;
        listH = top + panelH - PAD - listTop;
        detailX = left + PAD + listW + 10;
        detailW = left + panelW - PAD - detailX;
        writeButton = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.lesson.write"), b -> write())
                .bounds(detailX, top + panelH - PAD - 20, detailW, 20).build());
        writeButton.active = !glyphs.isEmpty();
        selected = Mth.clamp(selected, 0, Math.max(0, glyphs.size() - 1));
    }

    private void write() {
        if (glyphs.isEmpty()) return;
        PacketDistributor.sendToServer(new ClassesPayloads.WriteLesson(hand.ordinal(), glyphs.get(selected).id()));
        onClose();
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW_H);
    }

    private void select(int i) {
        if (glyphs.isEmpty()) return;
        int n = Mth.clamp(i, 0, glyphs.size() - 1);
        if (n == selected) return;
        selected = n;
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.5F));
    }

    private int rowAt(double mx, double my) {
        int x = left + PAD;
        if (mx < x || mx >= x + listW || my < listTop || my >= listTop + visibleRows() * ROW_H) return -1;
        int i = scroll + (int) ((my - listTop) / ROW_H);
        return i < glyphs.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int r = rowAt(mx, my);
        if (r >= 0) {
            select(r);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, glyphs.size() - visibleRows());
        if (max > 0 && mx < detailX) {
            scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> { select(selected - 1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { select(selected + 1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { write(); return true; }
            default -> { return super.keyPressed(key, scan, mods); }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MagicPath path = ClientMagic.data().path();
        MahouUi.frame(g, left, top, panelW, panelH, path == MagicPath.NONE ? MahouUi.GOLD : path.argb());
        int dx = left + PAD + listW + 4;
        g.fill(dx, listTop, dx + 1, top + panelH - PAD, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, MahouUi.fit(font, title, panelW - 2 * PAD), left + PAD, top + PAD, MahouUi.GOLD, true);
        renderList(g, mx, my);
        renderDetail(g);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int x = left + PAD;
        int hover = rowAt(mx, my);
        int rows = visibleRows();
        for (int r = 0; r < rows && scroll + r < glyphs.size(); r++) {
            int i = scroll + r;
            Glyph gl = glyphs.get(i);
            int y = listTop + r * ROW_H;
            int accent = 0xFF000000 | GlyphArt.color(gl);
            if (i == selected) {
                MahouUi.roundRect(g, x, y, listW, ROW_H - 2, MahouUi.mix(0xFF2A2240, accent, 0.2F), 0xFF17131F);
                MahouUi.roundOutline(g, x, y, listW, ROW_H - 2, accent);
            } else {
                MahouUi.roundRect(g, x, y, listW, ROW_H - 2, i == hover ? 0xE0262036 : 0xC0181422, 0xC0100C18);
            }
            GlyphArt.glyph(g, gl, x + 3, y + 1, 16);
            g.drawString(font, MahouUi.fit(font, gl.displayName(), listW - 26), x + 23, y + 5, 0xFFFFFFFF, true);
        }
        if (glyphs.size() > rows) {
            int bx = x + listW + 1;
            int barH = Math.max(10, listH * rows / glyphs.size());
            int by = listTop + (listH - barH) * scroll / Math.max(1, glyphs.size() - rows);
            g.fill(bx, listTop, bx + 2, listTop + listH, 0x40FFFFFF);
            g.fill(bx, by, bx + 2, by + barH, MahouUi.GOLD_DIM);
        }
    }

    private void renderDetail(GuiGraphics g) {
        int x = detailX, y = listTop;
        if (glyphs.isEmpty()) {
            paragraph(g, Component.translatable("screen.isekai_mahou.lesson.empty"), x, y, MahouUi.TEXT_DIM);
            return;
        }
        Glyph gl = glyphs.get(selected);
        GlyphArt.glyph(g, gl, x, y, 20);
        g.drawString(font, MahouUi.fit(font, gl.displayName(), detailW - 26), x + 25, y + 2, 0xFF000000 | GlyphArt.color(gl), true);
        g.drawString(font, MahouUi.fit(font, Component.translatable("glyph_kind.isekai_mahou." + gl.kind().name().toLowerCase()),
                detailW - 26), x + 25, y + 12, MahouUi.TEXT_DIM, false);
        y += 26;
        int bottom = top + panelH - PAD - 24;
        y = paragraph(g, gl.description(), x, y, 0xFFDDD8EA);
        y += 4;
        if (y + 10 > bottom) return;
        g.drawString(font, Component.translatable("screen.isekai_mahou.lesson.student"), x, y, MahouUi.GOLD, true);
        y += 11;
        int same = Progression.lessonCircle(gl.tier(), gl.minCircle(), false);
        int other = Progression.lessonCircle(gl.tier(), gl.minCircle(), true);
        y = paragraph(g, Component.translatable("screen.isekai_mahou.lesson.same_class", same,
                GlyphLearning.costText(gl, Progression.lessonEssenceCost(false))), x + 4, y, MahouUi.TEXT);
        if (y + 10 > bottom) return;
        y = paragraph(g, Component.translatable("screen.isekai_mahou.lesson.other_class", other,
                GlyphLearning.costText(gl, Progression.lessonEssenceCost(true))), x + 4, y, MahouUi.TEXT);
        if (y + 10 > bottom) return;
        paragraph(g, Component.translatable("screen.isekai_mahou.lesson.teacher_xp", Progression.lessonTeacherXp(gl.tier())),
                x, y + 2, MahouUi.TEXT_DIM);
    }

    private int paragraph(GuiGraphics g, Component text, int x, int y, int color) {
        int bottom = top + panelH - PAD - 24;
        for (FormattedCharSequence line : font.split(text, left + panelW - PAD - x)) {
            if (y + 9 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
