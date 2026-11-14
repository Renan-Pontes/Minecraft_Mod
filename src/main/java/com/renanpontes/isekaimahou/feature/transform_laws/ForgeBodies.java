package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * v1.1 — as regras dos {@link TransformBody corpos de transformação} que moram fora deste pacote:
 * <ul>
 *   <li><b>migração</b> ({@link SpellParser.TransformHook}): toda receita salva (teclas Z/X/C/G, biblioteca, presets,
 *       pergaminhos de lenda, magias de NPC) que COMEÇA (depois dos gatilhos) com uma ex-forma que virou corpo é lida
 *       como {@code Transformação[ Suprema] → corpo …} — os aumentos que estavam na forma passam a valer no corpo
 *       (Estender continua esticando). A receita guardada não muda; só a leitura;</li>
 *   <li>a forma Transformação logo antes de um corpo conhecido vale sem ser aprendida e não conta no limite de glifos
 *       (magias antigas não quebram); e quem conhece um corpo APRENDE a forma (comum e, se o corpo era ultimate, a
 *       Suprema) para conseguir montar no Grimório;</li>
 *   <li>erros de montagem: corpo fora da Transformação ("Este efeito é uma transformação…"), corpo supremo na comum;</li>
 *   <li>recarga mínima pelo corpo; e a cutscene de começo ({@link #cutscene}).</li>
 * </ul>
 */
final class ForgeBodies {
    private ForgeBodies() {}

    static void init() {
        SpellParser.installTransformHook(new Hook());
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.GlyphLearned e) -> {
            if (GlyphRegistry.get(e.glyph) instanceof TransformBody b) teach(e.getPlayer(), b);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) teachAll(sp);
        });
    }

    /** Quem conhece um corpo aprende a forma de que ele precisa (sem mensagem). */
    private static void teach(ServerPlayer sp, TransformBody b) {
        if (sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (!d.knowsGlyph(TransformForge.FORM_ID)) MagicApi.learnGlyphSilently(sp, TransformForge.FORM_ID);
        if (b.supreme() && !d.knowsGlyph(TransformForge.SUPREME_ID)) MagicApi.learnGlyphSilently(sp, TransformForge.SUPREME_ID);
    }

    static void teachAll(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        for (Glyph g : GlyphRegistry.all()) {
            if (g instanceof TransformBody b && d.knowsGlyph(g.id())) teach(sp, b);
        }
    }

    // =====================================================================================
    //  Regras do parser
    // =====================================================================================
    private static final class Hook implements SpellParser.TransformHook {
        @Override
        public SpellRecipe migrate(SpellRecipe recipe) {
            List<ResourceLocation> ids = recipe.glyphs();
            for (int i = 0; i < ids.size(); i++) {
                Glyph g = GlyphRegistry.get(ids.get(i));
                if (g == null) return recipe;
                if (g.kind() == GlyphKind.TRIGGER) continue;
                if (!(g instanceof TransformBody b)) return recipe;
                // a receita começa com um corpo: era uma FORMA antes da v1.1
                List<ResourceLocation> out = new ArrayList<>(ids.size() + 1);
                out.addAll(ids.subList(0, i));
                out.add(b.supreme() ? TransformForge.SUPREME_ID : TransformForge.FORM_ID);
                out.addAll(ids.subList(i, ids.size()));
                return recipe.withGlyphs(out);
            }
            return recipe;
        }

        @Override
        public boolean implicitForm(List<Glyph> resolved, int formIndex, @Nullable MagicData data) {
            if (!TransformForge.isForm(resolved.get(formIndex).id())) return false;
            for (int i = formIndex + 1; i < resolved.size(); i++) {
                Glyph g = resolved.get(i);
                if (g.kind() == GlyphKind.AUGMENT) continue;
                return g instanceof TransformBody && (data == null || data.knowsGlyph(g.id()));
            }
            return false;
        }

        @Nullable
        @Override
        public Component check(int stage, FormGlyph form, List<ParsedSpell.EffectEntry> effects) {
            for (ParsedSpell.EffectEntry e : effects) {
                if (!(e.effect() instanceof TransformBody b)) continue;
                if (stage > 0 || form == null || !TransformForge.isForm(form.id())) {
                    return Component.translatable("spell_error.isekai_mahou.body_needs_transformation", e.effect().displayName());
                }
                if (b.supreme() && !form.isUltimate()) {
                    return Component.translatable("spell_error.isekai_mahou.body_needs_supreme", e.effect().displayName());
                }
            }
            return null;
        }

        @Override
        public int cooldown(FormGlyph form, List<ParsedSpell.EffectEntry> effects, int cooldown) {
            if (form == null || !TransformForge.isForm(form.id())) return cooldown;
            int min = 0;
            for (ParsedSpell.EffectEntry e : effects) {
                if (e.effect() instanceof TransformBody b) min = Math.max(min, b.minCooldown());
            }
            return Math.max(cooldown, min);
        }
    }

    // =====================================================================================
    //  Cutscene de começo (uma por ativação)
    // =====================================================================================
    /** Cutscene de transformação da forma conjurada: o corpo (se houver) dá título, estilo e cor. */
    static void cutscene(ServerPlayer sp, ParsedSpell spell, SpellRecipe recipe) {
        if (sp instanceof FakePlayer) return;
        Element el = spell.primary() == null ? Element.ARCANE : spell.primary();
        int rgb = recipe.color() >= 0 ? recipe.color() & 0xFFFFFF : el.color & 0xFFFFFF;
        Component formName = spell.form().displayName();
        Component title = recipe.name().isEmpty() ? formName : Component.literal(recipe.name());
        Component sub = el.displayName();
        String style = "default";
        ParsedSpell.EffectEntry body = TransformForge.timeBody(spell);
        if (body == null) {
            // efeito de transformação fixa (Kaioken, Instinto Superior...) como corpo
            for (ParsedSpell.EffectEntry e : spell.effects()) {
                if (TransformTargets.isEffect(e.effect().id())) {
                    body = e;
                    break;
                }
            }
        }
        if (body != null) {
            Component bodyName = body.effect().displayName();
            sub = recipe.name().isEmpty() ? formName : Component.literal(recipe.name());
            title = bodyName;
            if (body.effect() instanceof TransformBody tb) {
                Component custom = tb.cutsceneTitle(sp);
                if (custom != null) title = custom;
                style = tb.cutsceneStyle();
                if (recipe.color() < 0) rgb = tb.cutsceneRgb() & 0xFFFFFF;
            } else {
                String s = TransformForge.bodyStyle(body.effect().id());
                if (s != null) style = s;
            }
        }
        Cutscenes.transform(sp, title, sub, rgb, style, 60);
    }
}
