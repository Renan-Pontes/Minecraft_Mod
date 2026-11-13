package com.renanpontes.isekaimahou.feature.transform_laws;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Transformações que já existem no mod e passam a aceitar Leis de Transformação (registradas em nome dos módulos donos,
 * sem mexer neles: só o id do glifo e o MobEffect que marca "transformado"). As "liberações" do Shinigami/Hollow/Quincy
 * (Bankai, Resurrección, Vollständig...) ficam para os donos chamarem {@code TransformLaws.begin} ou registrarem aqui.
 */
final class TlawTargets {
    static void register() {
        // Druida — formas animais (origins_a): efeito com o mesmo id do glifo
        for (String s : new String[] {"wolf", "bear", "falcon", "dolphin", "spider", "elemental_beast"}) {
            TransformTargets.registerEffect(s + "_form", IsekaiMahou.id(s + "_form"), 1800);
        }
        // Druida — Modo Sábio (3 s de meditação antes do efeito: a sessão espera)
        TransformTargets.registerEffect("druid_sage_mode", IsekaiMahou.id("sage_mode"), 800);
        // Necromante — Forma de Lich; Monarca das Sombras (forma ultimate)
        TransformTargets.registerEffect("lich_form", IsekaiMahou.id("lich_form"), 600);
        TransformTargets.registerForm("necro_monarch", IsekaiMahou.id("shadow_monarch"), 600);
        // Bruxo — Asas Demoníacas e Demônio da Motosserra
        TransformTargets.registerEffect("demon_wings", IsekaiMahou.id("demon_wings"), 400);
        TransformTargets.registerEffect("warlock_chainsaw", IsekaiMahou.id("chainsaw_devil"), 600);
        // Encantador — transformações de anime
        TransformTargets.registerEffect("eight_gates", IsekaiMahou.id("enchanter_eight_gates"), 600);
        TransformTargets.registerEffect("kaioken", IsekaiMahou.id("enchanter_kaioken"), 600);
        TransformTargets.registerEffect("enchanter_gear_second", IsekaiMahou.id("enchanter_gear_second"), 600);
        TransformTargets.registerEffect("enchanter_gear_fourth", IsekaiMahou.id("enchanter_gear_fourth"), 600);
        TransformTargets.registerEffect("ultra_instinct", IsekaiMahou.id("enchanter_ultra_instinct"), 600);
        // Release 1.0 — forma Transformação (montada): a marca "forged_form" dura o tempo da receita
        TransformTargets.registerForm(TransformForge.FORM, TransformForge.PROBE_ID, TransformForge.BASE_DURATION);
        // Ultimate arcana — Transcendência
        TransformTargets.registerForm("transcend", IsekaiMahou.id("transcendence"), 600);
        // v1.4 (coordenador): liberações de Bleach — sem efeito-sonda próprio, a sessão vale pelo tempo base
        // onda 5: a máscara treinada dura até 45 s — a sessão cobre o teto e a classe encerra na hora certa (TransformLaws.end)
        TransformTargets.registerForm("shinigami_visored", null, 900);
        TransformTargets.registerForm("shinigami_vasto_lorde", null, 500);
        TransformTargets.registerForm("quincy_vollstandig", null, 240);
        TransformTargets.registerForm("quincy_letzt_stil", null, 600);

        // Onda 5 — "Transformações tem que ser editáveis pros efeitos que o player quiser": TODA transformação fixa ganha
        // um espaço de receita (id = path do glifo), moldado na aba Votos. A receita entra por cima quando o glifo é
        // conjurado (TlawRules.onCastPost). Ficam de fora só: a própria forma Transformação (ela JÁ é a receita), o
        // glifo quincy_vollstandig (o espaço dele é "vollstandig") e o glifo aposentado hollowfied_resurreccion.
        for (String s : new String[] {"wolf", "bear", "falcon", "dolphin", "spider", "elemental_beast"}) slot(s + "_form");
        for (String s : new String[] {"druid_sage_mode", "lich_form", "necro_monarch", "demon_wings", "warlock_chainsaw",
                "eight_gates", "kaioken", "enchanter_gear_second", "enchanter_gear_fourth", "ultra_instinct", "transcend",
                "shinigami_visored", "shinigami_vasto_lorde", "quincy_letzt_stil"}) {
            slot(s);
        }
    }

    /** Espaço de receita de uma transformação fixa: aparece para quem CONHECE o glifo dela. */
    private static void slot(String glyphPath) {
        ResourceLocation id = IsekaiMahou.id(glyphPath);
        TransformForge.registerSlot(glyphPath, () -> {
            Glyph g = GlyphRegistry.get(id);
            return g == null ? Component.literal(glyphPath) : g.displayName();
        }, d -> d.knowsGlyph(id) && !GlyphRegistry.isHidden(id));
    }

    private TlawTargets() {}
}
