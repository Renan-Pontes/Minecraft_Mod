package com.renanpontes.isekaimahou.feature.transform_laws.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;
import com.renanpontes.isekaimahou.feature.character.client.ClientCharacter;
import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.GrimoireTransform;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Onda 9 — o editor de transformações DENTRO do Grimório (aberto pelo glifo Transformação da paleta). Por cima, o
 * título e o botão "◂ Grimório" (volta ao editor de magias; Backspace também); embaixo, o {@link ShaperView}: a lista
 * de todas as transformações do jogador e o editor da escolhida, com as abas Efeitos / Aumentos / Votos.
 * <p>
 * Substitui a antiga aba "Votos" (menu B → Personagem), que saiu da barra: os dados são os mesmos (receitas dos espaços
 * em {@code transform_forge}, votos soltos nas marcas {@code tlaw.race.*}/{@code tlaw.hollow.*}), então mundos salvos
 * abrem com tudo como estava.
 */
final class TransformPanel implements GrimoireTransform.Panel {
    private static final int FORGE = 0xFFB98CFF;
    private static final int HEAD = 30;
    private static final String K = "screen.isekai_mahou.tforge.";

    private final ShaperView shaper = new ShaperView();
    private int x, y, w, h;
    private int[] backRect;
    private Font font;

    /**
     * Quem vê o glifo Transformação no Grimório: as mesmas pessoas que viam a antiga aba Votos (conhece a forma, alguma
     * Lei de Transformação, tem liberação/transformação de classe com espaço, ou já usou a Transformação Racial) e, além
     * delas, quem já liberou a forma da raça. Ninguém perde o acesso que tinha.
     */
    static boolean shown(MagicData d) {
        if (d.knowsGlyph(TransformForge.FORM_ID)) return true;
        if (d.hasFlag(TransformForge.SEEN_FLAG + TransformForge.RACE_SLOT)) return true;
        if (TransformForge.slotsFor(d).size() > 1) return true;
        for (Glyph g : GlyphRegistry.byKind(GlyphKind.LAW)) {
            if (g instanceof TransformLawGlyph && d.knowsGlyph(g.id())) return true;
        }
        CharacterData cd = ClientCharacter.data();
        return cd != null && cd.created() && RaceProgress.level(cd.race(), cd, d.level()) >= RaceProgress.MAX_LEVEL;
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        shaper.layout(x, y + HEAD, w, h - HEAD - 4);
    }

    @Override
    public List<Component> render(GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        // ---- botão "◂ Grimório" (canto de cima, à direita)
        Component back = Component.translatable(K + "grim_back");
        int bw = font.width(back) + 10;
        backRect = new int[] {x + w - 4 - bw, y + 2, bw, 14};
        boolean hover = in(backRect, mx, my);
        MahouUi.roundRect(g, backRect[0], backRect[1], bw, 14, hover ? 0xFF4A3A70 : 0xFF2F2548, 0xFF241C38);
        g.drawString(font, back, backRect[0] + 5, backRect[1] + 3, 0xFFFFFFFF, false);
        if (hover) {
            tip = new ArrayList<>();
            tip.add(Component.translatable(K + "grim_back_tip").withStyle(ChatFormatting.GRAY));
        }
        // ---- título e subtítulo
        int tw = backRect[0] - x - 10;
        g.drawString(font, MahouUi.fit(font, Component.translatable(K + "grim_title").withStyle(ChatFormatting.BOLD), tw), x + 6, y + 4, FORGE, true);
        g.drawString(font, MahouUi.fit(font, Component.translatable(K + "grim_subtitle"), w - 12), x + 6, y + 17, MahouUi.TEXT_DIM, false);
        // ---- lista + editor
        List<Component> st = shaper.render(g, mx, my);
        if (st != null && !st.isEmpty()) tip = st;
        return tip;
    }

    private static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && in(backRect, mx, my)) {
            GrimoireTransform.close();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
            return true;
        }
        return shaper.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sy) {
        return shaper.mouseScrolled(mx, my, sy);
    }
}
