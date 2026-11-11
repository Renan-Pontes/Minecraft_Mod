package com.renanpontes.isekaimahou.feature.light_shadow.entity;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Santuário: círculo sagrado que cura os aliados e lhes dá Resistência enquanto estiverem dentro. */
public class SanctuaryZoneEntity extends HolyZoneEntity {

    public SanctuaryZoneEntity(EntityType<? extends SanctuaryZoneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onStart(ServerLevel level) {
        LsFx.sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
        LsFx.sound(level, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
        LsFx.ring(level, position().add(0, 0.1, 0), radius(), ParticleTypes.END_ROD, 40);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (age % 20 != 0) return;
        boolean any = false;
        for (LivingEntity e : inside(LivingEntity.class, 0.3)) {
            if (!LsUtil.isAlly(level, owner, e)) continue;
            if (e.getHealth() < e.getMaxHealth()) {
                e.heal(1.5F * power);
                level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
                any = true;
            }
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, power >= 1.6F ? 1 : 0, true, false, true));
            level.sendParticles(LsFx.dust(LsFx.WHITE_GOLD, 1.0F), e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.3, 0.1, 0.3, 0);
        }
        if (any) LsFx.sound(level, position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.6F);
    }

    @Override
    protected void onEnd(ServerLevel level) {
        LsFx.sound(level, position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
        LsFx.holyBurst(level, position().add(0, 0.5, 0), 16, radius() * 0.4);
    }

    @Override
    protected void clientTick() {
        Level lv = level();
        float r = radius();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        // Dois anéis girando em sentidos opostos (círculo mágico) e luz subindo do chão
        if (tickCount % 2 == 0) {
            int pts = reduced ? 14 : 32;
            double rot = tickCount * 0.03;
            for (int i = 0; i < pts; i++) {
                double a = rot + Math.PI * 2 * i / pts;
                lv.addParticle(LsFx.dust(LsFx.GOLD, 0.9F), getX() + Math.cos(a) * r, getY() + 0.06, getZ() + Math.sin(a) * r, 0, 0, 0);
                if (i % 2 == 0) {
                    double b = -rot * 1.5 + Math.PI * 2 * i / pts;
                    lv.addParticle(LsFx.dust(LsFx.WHITE_GOLD, 0.7F), getX() + Math.cos(b) * r * 0.6, getY() + 0.06,
                            getZ() + Math.sin(b) * r * 0.6, 0, 0, 0);
                }
            }
        }
        int motes = reduced ? 1 : 3;
        for (int i = 0; i < motes; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            lv.addParticle(random.nextInt(4) == 0 ? ParticleTypes.END_ROD : LsFx.dust(LsFx.WHITE_GOLD, 0.8F),
                    getX() + Math.cos(a) * d, getY() + 0.1, getZ() + Math.sin(a) * d, 0, 0.04 + random.nextDouble() * 0.04, 0);
        }
    }
}
