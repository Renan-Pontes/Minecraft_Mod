package com.renanpontes.isekaimahou.feature.legends_d.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.legends_d.timestop.TimeStop;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Parar o Tempo (FORMA ultimate, slot G, Arcano, C6) — "Tempo, pare!". Uma onda de cores invertidas sai do
 * conjurador (raio 48, Expandir aumenta até 64), o mundo fica cinza e TUDO para por 3 s (+1 s por Estender, +1 s no
 * Círculo 8+, máx. 9 s). Quando o tempo volta, os efeitos da receita caem em todos os inimigos que ficaram parados,
 * junto com todo o dano guardado. Ver {@link TimeStop}.
 */
public class TimeStopForm extends FormGlyph {
    public static final float BASE_RADIUS = 48F;
    public static final int MAX_TICKS = 180;

    public TimeStopForm() {
        super("time_stop", 95);
        element(Element.ARCANE);
        tier(4);
        minCircle(6);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 400; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        SpellStats s = ctx.formStats();
        int bonus = 0;
        if (caster instanceof ServerPlayer sp && MagicApi.get(sp).circle() >= 8) bonus = 20;
        int ticks = Mth.clamp(60 + 20 * Math.max(0, s.duration) + bonus, 40, MAX_TICKS);
        float radius = Mth.clamp(s.radius(BASE_RADIUS), 16F, 64F);
        TimeStop.start(ctx, caster.position(), radius, ticks);
    }
}
