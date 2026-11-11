package com.renanpontes.isekaimahou.feature.legends_e.glyph;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.feature.legends_e.SupremeCasting;
import com.renanpontes.isekaimahou.feature.legends_e.entity.SupremeCircleEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Magia de Nível Supremo (forma ULTIMATE, slot G): o conjurador fica parado e círculos mágicos azul-brancos enormes se
 * formam em camadas por 8 s — uma conjuração pendente SUPREMA na Pilha, que pode ser contrariada (aí tudo se estilhaça
 * e nada acontece). No fim, os efeitos da receita caem do céu com poder ×3 e área ×3 sobre TODOS os inimigos a até
 * 48 blocos do ponto mirado. Usos por dia: 1 + Círculo/3. Ampulheta Arcana no inventário: sai na hora.
 */
public class SuperTierForm extends FormGlyph {
    /** Alcance da mira (o ponto central da magia). */
    public static final double AIM_RANGE = 96.0;

    public SuperTierForm() {
        super("super_tier", 100);
        element(Element.ARCANE);
        tier(4);
        minCircle(6);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 0; }

    @Override
    public int baseCooldown() { return 600; }

    /**
     * Conjurações que já abriram os círculos. Repetir na forma chama {@code cast} de novo com o MESMO contexto
     * (20 ticks depois): sem isto, com a Ampulheta (0,8 s) a repetição abriria uma 2ª Magia de Nível Supremo de graça
     * (sem gastar uso do dia). Mapa fraco: some sozinho com o contexto.
     */
    private static final Set<SpellContext> STARTED = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.level() != ctx.level() || SupremeCasting.isChanneling(caster)) return;
        if (!STARTED.add(ctx)) return;
        Vec3 target = LeUtil.aimGround(ctx, AIM_RANGE);
        boolean instant = caster instanceof ServerPlayer sp && SupremeCasting.consumeHourglass(sp);
        SupremeCircleEntity.start(ctx, target, instant);
    }
}
