package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Chamas Infernais (Demônio): fogo do Abismo, preto e carmim — dano de fogo, Em Chamas (2 acúmulos), Corrompido e
 * fogo por 5 s. Água não apaga: um alvo molhado toma +25% (o vapor queima por dentro).
 */
public class HellfireEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 6.0F;
    private static final int CRIMSON = 0xD8455B, ASH = 0x2A1A22;

    public HellfireEffect() {
        super("race_hellfire", 10, Element.FIRE, 1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return 100; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        boolean wet = le.isInWaterRainOrBubble() || Statuses.has(le, Statuses.Kind.WET);
        ctx.damage(le, DAMAGE * (wet ? 1.25F : 1F), stats, null, Element.FIRE);
        Statuses.apply(le, Statuses.Kind.BURNING, 2, stats.duration(100), ctx.caster(), stats.power());
        Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, stats.duration(100), ctx.caster(), stats.power());
        le.igniteForSeconds(5F);

        // Coluna de fogo infernal: chamas de alma por dentro, carmim e cinza por fora
        Vec3 base = le.position();
        double h = le.getBbHeight();
        for (int i = 0; i < 6; i++) {
            double y = base.y + h * i / 5.0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, base.x, y, base.z, 2, 0.15, 0.05, 0.15, 0.02);
            level.sendParticles(RaceFx.dust(i % 2 == 0 ? CRIMSON : ASH, 1.3F), base.x, y, base.z, 3, 0.35, 0.08, 0.35, 0.02);
        }
        level.sendParticles(ParticleTypes.FLAME, base.x, base.y + h * 0.5, base.z, 10, 0.3, 0.4, 0.3, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + h, base.z, 4, 0.2, 0.1, 0.2, 0.02);
        if (wet) level.sendParticles(ParticleTypes.CLOUD, base.x, base.y + h * 0.6, base.z, 10, 0.3, 0.3, 0.3, 0.05);
        RaceFx.sound(level, base, SoundEvents.BLAZE_SHOOT, 0.7F, 0.6F);
        RaceFx.sound(level, base, SoundEvents.SOUL_ESCAPE.value(), 0.9F, 0.8F);
    }
}
