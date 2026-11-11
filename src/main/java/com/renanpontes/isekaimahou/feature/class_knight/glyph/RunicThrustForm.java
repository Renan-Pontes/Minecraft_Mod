package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightMotion;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Investida Rúnica (forma, tier 2): o Cavaleiro dispara ~8 blocos para a frente com a lâmina estendida, aplicando os
 * efeitos em TODOS que atravessa (uma vez cada, empurrando para o lado) e termina com um golpe no chão (Ao Terminar).
 * Acelerar aumenta a velocidade, Estender a distância, Expandir a largura do corte. Não sai como estágio 2+ (move o
 * conjurador). Dividir não multiplica (1 cópia).
 */
public class RunicThrustForm extends FormGlyph {
    public RunicThrustForm() {
        super(KnightGlyphs.RUNIC_THRUST, 8);
        element(Element.ARCANE);
        origin(MagicPath.KNIGHT);
        tier(2);
    }

    @Override
    public int baseCooldown() { return 50; }

    @Override
    public float copies(SpellStats stats, int circle) { return 1F; }

    @Override
    public float hintRange() { return 8F; }

    @Override
    public float hintRadius() { return 1.2F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || caster.isPassenger() || caster.isSleeping()) return;
        SpellStats s = ctx.formStats();
        Vec3 look = caster.getLookAngle();
        if (caster instanceof Mob mob && mob.getTarget() != null) look = mob.getTarget().position().subtract(caster.position());
        Vec3 dir = KnightFx.flat(look, caster.getLookAngle());
        double speed = 1.35 * s.speedMult();
        int ticks = Math.max(3, Math.round(6 * (1.0F + 0.5F * s.duration)));
        double radius = s.radius(1.2F);
        KnightMotion.dash(caster, ctx, dir, speed, radius, ticks);
        caster.swing(InteractionHand.MAIN_HAND, true);
        ElementFx.castSound(ctx.level(), caster, ctx.element());
    }
}
