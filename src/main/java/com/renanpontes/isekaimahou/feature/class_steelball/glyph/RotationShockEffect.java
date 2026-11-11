package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Choque de Rotação (efeito de origem, tier 1): a rotação entra no corpo — dano de Terra (5) e o alvo fica Girado
 * (gira sem controle, mais lento e fraco; acertos seguidos acumulam). Amplificar começa no nível II.
 * Inverter: a rotação é "para fora" — em vez de Girado, o alvo é arremessado girando para longe.
 */
public class RotationShockEffect extends EffectGlyph {
    public static final float DAMAGE = 5F;
    public static final int SPIN_TICKS = 60;

    public RotationShockEffect() {
        super("rotation_shock", 9);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return SPIN_TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ctx.damage(target, DAMAGE, stats, null);
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        if (stats.inverted) {
            Vec3 from = ctx.caster() != null ? ctx.caster().position() : ctx.eyePos();
            Vec3 dir = le.position().subtract(from).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            dir = dir.normalize().scale(1.1 * stats.power());
            le.push(dir.x, 0.45, dir.z);
            le.hurtMarked = true;
        } else {
            SbSpin.apply(le, ctx.caster(), stats.amplify > 0 ? 1 : 0, stats.duration(SPIN_TICKS));
        }
        SbFx.spiral(ctx.level(), le.position(), SbGlyphs.golden(ctx, stats) ? SbFx.GOLD : SbFx.STEEL,
                le.getBbWidth() * 0.7 + 0.3, le.getBbHeight(), 14, ctx.random().nextFloat() * 6F);
    }
}
