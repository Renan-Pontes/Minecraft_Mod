package com.renanpontes.isekaimahou.feature.earth_wind.effect;

import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Planar: no ar, a queda fica lenta e o corpo desliza para onde se olha (como uma asa-delta).
 * Agachar cancela (cai normalmente). O movimento e aplicado no lado que controla a entidade
 * (cliente para o proprio jogador, servidor para mobs); o servidor zera a distancia de queda.
 */
public class GlideEffect extends EwMobEffect {
    public GlideEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Esta entidade esta planando agora? (no ar, sem agachar, sem voar/elytra/montaria/agua). */
    public static boolean isGliding(LivingEntity e) {
        if (e.onGround() || e.isInWater() || e.isInLava() || e.isFallFlying() || e.isPassenger() || e.isShiftKeyDown()) return false;
        return !(e instanceof Player p && p.getAbilities().flying);
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!isGliding(e)) return true;
        // Jogador no servidor: o movimento vem do cliente, entao so evitamos o dano de queda.
        if (e instanceof ServerPlayer) {
            e.resetFallDistance();
            return true;
        }
        if (!EwUtil.controlsMovement(e)) return true;
        Vec3 dm = e.getDeltaMovement();
        if (dm.y >= 0) return true;
        e.resetFallDistance();
        double maxSpeed = 0.5 + 0.1 * amplifier;
        Vec3 look = EwUtil.horizontal(e.getLookAngle());
        Vec3 h = new Vec3(dm.x, 0, dm.z);
        if (look.lengthSqr() > 1.0E-4) h = h.add(look.normalize().scale(0.03 + 0.01 * amplifier));
        if (h.length() > maxSpeed) h = h.normalize().scale(maxSpeed);
        e.setDeltaMovement(h.x, Math.max(dm.y, -0.09), h.z);
        if (e.level().isClientSide && e.tickCount % 2 == 0) {
            // rastros de vento nas "pontas das asas"
            Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.7);
            double y = e.getY() + e.getBbHeight() * 0.6;
            e.level().addParticle(ParticleTypes.CLOUD, e.getX() + side.x, y, e.getZ() + side.z, -h.x * 0.2, 0.01, -h.z * 0.2);
            e.level().addParticle(ParticleTypes.CLOUD, e.getX() - side.x, y, e.getZ() - side.z, -h.x * 0.2, 0.01, -h.z * 0.2);
        }
        return true;
    }
}
