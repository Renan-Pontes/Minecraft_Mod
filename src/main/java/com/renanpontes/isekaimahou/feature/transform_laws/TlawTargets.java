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
 * (Shikai, Bankai, Resurrección, Vollständig...) chamam {@code TransformLaws.begin} pelo espaço delas.
 * <p>
 * v1.1: as ex-FORMAS de transformação (O Todo-Poderoso, Vollständig, Letzt Stil, Bankai, Máscara do Vazio, Vasto Lorde,
 * Monarca das Sombras, Transcendência) são agora EFEITOS-corpo ({@link TransformBody}) registrados como efeitos: só
 * entram dentro da forma Transformação.
 */
final class TlawTargets {
    /** v1.1 — ex-formas que viraram corpos (id do glifo, efeito-sonda ou null, duração base). */
    static final String[][] BODIES = {
            {"quincy_almighty", null, "6000"},
            {"quincy_vollstandig", null, "240"},
            {"quincy_letzt_stil", null, "600"},
            {"zanpakuto_bankai", null, "400"},
            {"shinigami_visored", null, "900"},
            {"shinigami_vasto_lorde", null, "500"},
            {"necro_monarch", "shadow_monarch", "600"},
            {"transcend", "transcendence", "600"},
    };

    static void register() {
        // Druida — formas animais (origins_a): efeito com o mesmo id do glifo
        for (String s : new String[] {"wolf", "bear", "falcon", "dolphin", "spider", "elemental_beast"}) {
            TransformTargets.registerEffect(s + "_form", IsekaiMahou.id(s + "_form"), 1800);
            TransformForge.registerBodyStyle(s + "_form", "beast");
        }
        // Druida — Modo Sábio (3 s de meditação antes do efeito: a sessão espera)
        TransformTargets.registerEffect("druid_sage_mode", IsekaiMahou.id("sage_mode"), 800);
        TransformForge.registerBodyStyle("druid_sage_mode", "spirit");
        // Necromante — Forma de Lich
        TransformTargets.registerEffect("lich_form", IsekaiMahou.id("lich_form"), 600);
        TransformForge.registerBodyStyle("lich_form", "dark");
        // Bruxo — Asas Demoníacas e Demônio da Motosserra
        TransformTargets.registerEffect("demon_wings", IsekaiMahou.id("demon_wings"), 400);
        TransformForge.registerBodyStyle("demon_wings", "dark");
        TransformTargets.registerEffect("warlock_chainsaw", IsekaiMahou.id("chainsaw_devil"), 600);
        TransformForge.registerBodyStyle("warlock_chainsaw", "blood");
        // Encantador — transformações de anime (v1.1: como corpo duram a transformação inteira)
        TransformTargets.registerEffect("eight_gates", IsekaiMahou.id("enchanter_eight_gates"), 600);
        TransformForge.registerBodyStyle("eight_gates", "blood");
        TransformTargets.registerEffect("kaioken", IsekaiMahou.id("enchanter_kaioken"), 600);
        TransformForge.registerBodyStyle("kaioken", "fire");
        TransformTargets.registerEffect("enchanter_gear_second", IsekaiMahou.id("enchanter_gear_second"), 600);
        TransformForge.registerBodyStyle("enchanter_gear_second", "fire");
        TransformTargets.registerEffect("enchanter_gear_fourth", IsekaiMahou.id("enchanter_gear_fourth"), 600);
        TransformForge.registerBodyStyle("enchanter_gear_fourth", "fire");
        TransformTargets.registerEffect("ultra_instinct", IsekaiMahou.id("enchanter_ultra_instinct"), 600);
        TransformForge.registerBodyStyle("ultra_instinct", "speed");
        // Release 1.0 — forma Transformação (montada): a marca "forged_form" dura o tempo da receita (v1.1: e a Suprema)
        TransformTargets.registerForm(TransformForge.FORM, TransformForge.PROBE_ID, TransformForge.BASE_DURATION);
        TransformTargets.registerForm(TransformForge.SUPREME, TransformForge.PROBE_ID, TransformForge.BASE_DURATION);
        // v1.1 — corpos (ex-formas): papel CORPO dentro da Transformação; quem dita o tempo é o próprio corpo
        for (String[] b : BODIES) {
            TransformTargets.registerEffect(b[0], b[1] == null ? null : IsekaiMahou.id(b[1]), Integer.parseInt(b[2]));
        }

        // Onda 5 — "Transformações tem que ser editáveis pros efeitos que o player quiser": TODA transformação fixa ganha
        // um espaço de receita (id = path do glifo), moldado no editor do Grimório. A receita entra por cima quando o
        // glifo é conjurado SOLTO (TlawRules.onCastPost). v1.1: os corpos (ex-formas) não têm espaço — a própria magia
        // "Transformação → corpo → efeitos → leis" já é a receita deles. Shikai, Bankai, Vollständig e Resurrección
        // continuam com os espaços das liberações de classe (ForgeSessions.init).
        for (String s : new String[] {"wolf", "bear", "falcon", "dolphin", "spider", "elemental_beast"}) slot(s + "_form");
        for (String s : new String[] {"druid_sage_mode", "lich_form", "demon_wings", "warlock_chainsaw",
                "eight_gates", "kaioken", "enchanter_gear_second", "enchanter_gear_fourth", "ultra_instinct"}) {
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
