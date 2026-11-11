package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightPower;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSkyfall;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sentença do Juramento (efeito, tier 4, Luz): uma espada espectral GIGANTE cai sobre o alvo e o crava no chão —
 * 9 de dano, ×2,2 se o alvo estiver abaixo de 35% da vida (executa), e Atordoa por 1 s (Estender). Em chefes o
 * atordoamento segue as regras de resistência do núcleo.
 */
public class JudgmentEffect extends EffectGlyph {
    public static final float DAMAGE = 9F;
    public static final float EXECUTE = 2.2F;

    public JudgmentEffect() {
        super(KnightGlyphs.JUDGMENT, 26);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(4);
        minCircle(5);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return 20; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        int rgb = ctx.color() & 0xFFFFFF;
        Vec3 top = le.position().add(0, le.getBbHeight() + 7, 0);
        boolean ok = KnightSkyfall.drop(level, top, le.getY(), 1.4F, KnightPower.bladeOf(ctx.caster()), 3.2F, rgb,
                () -> strike(ctx, le, stats));
        if (!ok) strike(ctx, le, stats);
        KnightFx.sound(level, top, SoundEvents.BELL_BLOCK, 0.8F, 0.5F);
    }

    private static void strike(SpellContext ctx, LivingEntity le, SpellStats stats) {
        if (!le.isAlive() || le.level() != ctx.level()) return;
        ServerLevel level = ctx.level();
        float base = DAMAGE * KnightPower.mult(ctx);
        if (le.getHealth() / Math.max(1F, le.getMaxHealth()) < 0.35F) base *= EXECUTE;
        le.invulnerableTime = 0;
        ctx.damage(le, base, stats, null);
        if (le.isAlive()) Statuses.apply(le, Statuses.Kind.STUNNED, 1, stats.duration(20), ctx.caster(), ctx.powerMult());
        Vec3 p = le.position();
        KnightFx.runeCircle(level, p, 2.2, KnightFx.GOLD, 36, 0F);
        level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
        level.sendParticles(KnightFx.dust(0xFFFFFF, 2.0F), p.x, p.y + 1, p.z, 24, 0.5, 1.0, 0.5, 0);
        KnightFx.sound(level, p, SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
        KnightFx.sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.3F);
    }
}
