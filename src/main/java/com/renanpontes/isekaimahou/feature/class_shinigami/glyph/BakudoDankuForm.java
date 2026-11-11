package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.Barriers;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bakudō nº 81 — Muro do Vazio (homenagem Dankū): ergue uma parede de Reiatsu dourado 2,5 blocos à frente (5 de largura,
 * +2 por Expandir; 3,5 de altura) por 8 s (Estender aumenta). Desfaz projéteis inimigos, barra a passagem de inimigos e,
 * se a magia tiver efeitos, aplica-os uma vez por segundo em quem encosta (custo como pulsos, de forma moderada).
 * Funciona sem efeitos. Até 3 paredes por conjurador.
 */
public class BakudoDankuForm extends FormGlyph {
    public static final int DURATION = 160;

    public BakudoDankuForm() {
        super("kido_bakudo_81", 18);
        origin(MagicPath.SHINIGAMI);
        element(Element.ARCANE);
        tier(3);
        minCircle(4);
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), 20);
    }

    @Override
    public int baseCastTicks() {
        return 10;
    }

    @Override
    public int baseCooldown() {
        return 60;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 2.5F;
    }

    @Override
    public float hintRange() {
        return 2.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 look = caster.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 center = caster.position().add(flat.scale(2.5));
        double half = 2.5 + Math.max(0, s.aoe);
        Barriers.raise(ctx, center, flat, half, 3.5, s.duration(DURATION));
        caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }
}
