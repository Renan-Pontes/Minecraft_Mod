package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.KnightWards;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Avalon — A Bainha Distante" (a bainha da espada do rei, que cura e protege; Luz, benéfico, origem do Cavaleiro).
 * Liberada ao DESPERTAR a Espada do Lago. Em você ou num aliado: a bainha se abre em mil partículas de luz e afasta o
 * alvo do mundo por 4 s (Estender +; ×1,5 com a Espada do Lago na mão de quem conjura) — QUASE invulnerável (só o vazio
 * e o /kill passam) —, cura na hora 25% da vida máxima (× poder, até 40%), tira as maldições, apaga o fogo e dá
 * Regeneração II por 6 s.
 */
public class AvalonEffect extends EffectGlyph {
    public static final int TICKS = 80;
    public static final int GOLD = 0xFFE9A8;

    public AvalonEffect() {
        super("knight_avalon", 40);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(5);
        minCircle(6);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        LivingEntity caster = ctx.caster();
        boolean lake = KnightSwords.legendIn(caster) == LegendSword.LAKE;
        int ticks = Math.round(stats.duration(TICKS) * (lake ? 1.5F : 1F));
        List<Holder<MobEffect>> curses = new ArrayList<>();
        for (MobEffectInstance inst : le.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) curses.add(inst.getEffect());
        }
        for (Holder<MobEffect> h : curses) le.removeEffect(h);
        le.clearFire();
        float frac = Math.min(0.40F, 0.25F * stats.power() * ctx.powerMult());
        le.heal(le.getMaxHealth() * frac);
        le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(120), 1), caster);
        KnightWards.avalon(le, ticks);
        if (!stats.silent && caster != null) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.knight_avalon"), GOLD, Announce.STYLE_ULTIMATE);
        }
    }
}
