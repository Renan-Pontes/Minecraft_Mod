package com.renanpontes.isekaimahou.feature.class_knight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Vento Invisível ({@code knight_invisible_air}): a barreira de vento que esconde a lâmina. Quem olha não vê a espada
 * (o cliente esconde o item da mão), só o ar distorcido em volta da mão: correntes de vento giram onde a lâmina estaria.
 * Os modificadores (+2 de dano, +1 de alcance) ficam no registro.
 */
public class InvisibleAirEffect extends MobEffect {
    public InvisibleAirEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xBFF4FF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 3 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        Vec3 look = e.getLookAngle();
        Vec3 side = KnightFx.side(KnightFx.flat(look, new Vec3(0, 0, 1)));
        Vec3 hand = e.getEyePosition().add(look.scale(0.5)).add(side.scale(-0.4)).subtract(0, 0.55, 0);
        float a = e.tickCount * 0.6F;
        for (int i = 0; i < 3; i++) {
            float ang = a + i * Mth.TWO_PI / 3;
            double up = 0.2 + i * 0.35;
            Vec3 p = hand.add(look.scale(up)).add(side.scale(Mth.cos(ang) * 0.18)).add(0, Mth.sin(ang) * 0.18, 0);
            level.sendParticles(KnightFx.dust(0xE8FBFF, 0.5F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        if (e.tickCount % 12 == 0) level.sendParticles(ParticleTypes.SMALL_GUST, hand.x, hand.y + 0.3, hand.z, 1, 0.05, 0.1, 0.05, 0);
        return true;
    }
}
