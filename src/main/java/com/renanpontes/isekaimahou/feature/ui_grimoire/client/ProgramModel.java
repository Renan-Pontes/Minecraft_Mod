package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * Estrutura do rascunho em ESTÁGIOS, só pela posição das ligações — funciona mesmo com a receita incompleta ou
 * inválida (o {@code ParsedSpell} só existe quando ela é válida). Segue a mesma numeração de estágios do parser:
 * cada ligação que não é Saltar fecha um estágio e abre o próximo; leis no fim da receita ficam numa linha própria.
 */
final class ProgramModel {
    /** Um glifo "cabeça" (forma, efeito, gatilho, lei...) e os aumentos que vêm logo depois dele. */
    record Group(List<Integer> members) {
        int head() {
            return members.get(0);
        }
    }

    static final class Stage {
        final int number; // 1 = primeiro
        final List<Group> groups = new ArrayList<>();
        /** Índice da ligação que fecha o estágio (-1 = nenhuma). */
        int link = -1;
        LinkGlyph linkGlyph;
        /** Índice da primeira forma do estágio (-1 = ainda sem forma). */
        int form = -1;

        Stage(int number) {
            this.number = number;
        }

        boolean isEmpty() {
            return groups.isEmpty();
        }

        boolean leap() {
            return linkGlyph != null && linkGlyph.linkKind() == LinkGlyph.LinkKind.LEAP;
        }
    }

    final List<Stage> stages = new ArrayList<>();
    /** Leis no fim da receita (linha própria); vazio se não há. */
    final List<Integer> laws = new ArrayList<>();
    final int size;

    private ProgramModel(int size) {
        this.size = size;
    }

    static ProgramModel build(List<ResourceLocation> glyphs) {
        int n = glyphs.size();
        ProgramModel m = new ProgramModel(n);
        if (n == 0) return m;
        // leis no final (contíguas) vão para a linha de Leis; leis no meio ficam onde estão (o parser aponta o erro)
        int lawStart = n;
        for (int i = n - 1; i >= 0; i--) {
            Glyph g = GlyphRegistry.get(glyphs.get(i));
            if (g != null && g.kind() == GlyphKind.LAW) lawStart = i;
            else break;
        }
        Stage cur = new Stage(1);
        m.stages.add(cur);
        for (int i = 0; i < lawStart; i++) {
            Glyph g = GlyphRegistry.get(glyphs.get(i));
            if (g instanceof LinkGlyph link) {
                cur.link = i;
                cur.linkGlyph = link;
                cur = new Stage(cur.number + 1);
                m.stages.add(cur);
                continue;
            }
            if (g != null && g.kind() == GlyphKind.AUGMENT && !cur.groups.isEmpty()) {
                cur.groups.get(cur.groups.size() - 1).members().add(i);
            } else {
                List<Integer> members = new ArrayList<>();
                members.add(i);
                cur.groups.add(new Group(members));
                if (g instanceof FormGlyph && cur.form < 0) cur.form = i;
            }
        }
        for (int i = lawStart; i < n; i++) m.laws.add(i);
        // depois de Saltar não existe próximo estágio: não mostra o "espaço para a forma" vazio
        if (m.stages.size() > 1 && cur.isEmpty() && m.stages.get(m.stages.size() - 2).leap()) m.stages.remove(m.stages.size() - 1);
        // só leis (sem nada antes): sem estágio vazio na frente
        if (m.stages.size() == 1 && cur.isEmpty() && !m.laws.isEmpty()) m.stages.clear();
        return m;
    }

    /** Estágio (0-based) que contém o índice do glifo, contando a ligação como parte do estágio que ela fecha; -1 = leis. */
    int stageOf(int index) {
        for (int s = 0; s < stages.size(); s++) {
            Stage st = stages.get(s);
            if (st.link == index) return s;
            for (Group gr : st.groups) if (gr.members().contains(index)) return s;
        }
        return -1;
    }

    /** O estágio número {@code number} (1-based) ou null. */
    Stage stage(int number) {
        return number >= 1 && number <= stages.size() ? stages.get(number - 1) : null;
    }
}
