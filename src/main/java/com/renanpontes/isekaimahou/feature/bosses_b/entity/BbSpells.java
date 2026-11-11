package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Magias de glifo dos chefes. Os glifos são de outros módulos, então tudo é resolvido por id em tempo de execução:
 * se algum glifo da receita não existir (ou a receita não validar), quem chamou usa o ataque próprio (fallback).
 */
public final class BbSpells {

    /** Receita com estes glifos (ids do mod), ou null se algum não existir / a gramática não validar. */
    @Nullable
    public static SpellRecipe recipe(String... ids) {
        List<ResourceLocation> list = new ArrayList<>(ids.length);
        for (String id : ids) {
            ResourceLocation rl = IsekaiMahou.id(id);
            if (GlyphRegistry.get(rl) == null) return null;
            list.add(rl);
        }
        SpellRecipe r = new SpellRecipe(list, "", -1, "");
        return SpellParser.parse(r, null, null).ok() ? r : null;
    }

    /** A primeira receita válida entre as alternativas (ex.: uma forma de outro módulo pode ainda não ser "estágio"). */
    @Nullable
    public static SpellRecipe firstRecipe(String[]... options) {
        for (String[] ids : options) {
            SpellRecipe r = recipe(ids);
            if (r != null) return r;
        }
        return null;
    }

    /** Primeiro glifo existente da lista (para montar receitas com alternativas), ou null. */
    @Nullable
    public static String firstAvailable(String... ids) {
        for (String id : ids) {
            if (GlyphRegistry.get(IsekaiMahou.id(id)) != null) return id;
        }
        return null;
    }

    public static boolean exists(String id) {
        return GlyphRegistry.get(IsekaiMahou.id(id)) != null;
    }

    /**
     * A forma sai da origem/direção dadas (v0.7: formas "de estágio" respeitam {@code ctx.eyePos()/look()}).
     * Formas antigas miram direto no alvo do mob — aí o chefe não as usa em golpes telegrafados (seriam indesviáveis).
     */
    public static boolean aimsFromOrigin(String formId) {
        Glyph g = GlyphRegistry.get(IsekaiMahou.id(formId));
        return g instanceof FormGlyph f && f.stageable();
    }

    /** Conjura como mob (mira no alvo). @return true se a magia saiu (glifos existem e nada cancelou). */
    public static boolean cast(LivingEntity caster, Element element, float power, String... ids) {
        SpellRecipe r = recipe(ids);
        return r != null && cast(caster, element, power, r);
    }

    public static boolean cast(LivingEntity caster, Element element, float power, SpellRecipe recipe) {
        SpellContext ctx = SpellCaster.castAsMob(caster, element, recipe, power);
        return ctx != null;
    }

    /**
     * Conjura a partir de {@code origin} na direção TRAVADA {@code dir} (SpellCaster.castFrom): a magia segue a linha
     * que foi telegrafada no chão. Só se a forma inicial respeitar a origem.
     */
    public static boolean castAt(LivingEntity caster, Element element, float power, Vec3 origin, Vec3 dir, String... ids) {
        if (ids.length == 0 || !aimsFromOrigin(ids[0])) return false;
        SpellRecipe r = recipe(ids);
        return r != null && castAt(caster, element, power, origin, dir, r);
    }

    public static boolean castAt(LivingEntity caster, Element element, float power, Vec3 origin, Vec3 dir, SpellRecipe recipe) {
        return SpellCaster.castFrom(caster, element, recipe, power, origin, dir.normalize(), true) != null;
    }

    private BbSpells() {}
}
