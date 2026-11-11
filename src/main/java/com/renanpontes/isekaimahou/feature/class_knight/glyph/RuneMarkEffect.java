package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightMarks;
import com.renanpontes.isekaimahou.feature.class_knight.KnightPower;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Marca Rúnica (efeito, tier 1, Luz): corte leve (3) que grava uma runa no alvo. A runa acumula (esta magia, outro
 * acerto dela ou um golpe de espada SEU no alvo); no 3º acúmulo ela se ROMPE: 8 de dano no alvo, metade em 2,5 blocos e
 * Exposto. Amplificar soma 2 acúmulos de uma vez; Estender segura a marca por mais tempo (10 s base).
 */
public class RuneMarkEffect extends EffectGlyph {
    public RuneMarkEffect() {
        super(KnightGlyphs.RUNE_MARK, 7);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(1);
    }

    @Override
    public float baseDamage() { return 3F; }

    @Override
    public int hintDuration() { return 200; }

    @Override
    public float hintRadius() { return 2.5F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ctx.damage(le, 3F * KnightPower.mult(ctx), stats, null);
        Vec3 c = KnightFx.center(le);
        ctx.level().sendParticles(KnightFx.dust(KnightFx.RUNE_BLUE, 1.0F), c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0);
        ctx.level().sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.5, c.z, 12, 0.3, 0.3, 0.3, 0.5);
        if (!le.isAlive()) return;
        KnightMarks.add(le, ctx, stats, stats.amplify > 0 ? 2 : 1, stats.duration(200));
    }
}
