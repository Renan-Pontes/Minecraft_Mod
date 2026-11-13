package com.renanpontes.isekaimahou.feature.earth_wind.effect;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Velocidade do Vento: os atributos dao a velocidade; aqui so o rastro de vento nos pes enquanto corre. */
public class SwiftWindEffect extends EwMobEffect {
    public SwiftWindEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 2 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel level && !e.isInvisible()) {
            // xo/zo ja foram igualados a posicao antes deste tick: o movimento real vem do getKnownMovement
            // (para jogadores e o ultimo movimento enviado pelo cliente; para mobs, a velocidade)
            Vec3 mv = e.getKnownMovement();
            if (mv.x * mv.x + mv.z * mv.z > 0.004 && e.onGround()) {
                level.sendParticles(Vfx.cloud(), e.getX() - mv.x * 2, e.getY() + 0.1, e.getZ() - mv.z * 2, 1, 0.15, 0.02, 0.15, 0.01);
            }
        }
        return true;
    }
}
