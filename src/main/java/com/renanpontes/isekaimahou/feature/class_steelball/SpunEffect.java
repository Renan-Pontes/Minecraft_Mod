package com.renanpontes.isekaimahou.feature.class_steelball;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Girado: o corpo do alvo foi tomado pela rotação. −15% de velocidade e −10% de dano por nível; criaturas comuns
 * rodopiam sem conseguir mirar (chefes e jogadores não perdem o controle da câmera/IA, só sofrem os redutores).
 */
public class SpunEffect extends MobEffect {
    public SpunEffect() {
        super(MobEffectCategory.HARMFUL, SbFx.STEEL);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("steelball_spun_speed"), -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("steelball_spun_damage"), -0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        if (e instanceof Mob mob && !BossKit.isBossLike(mob)) {
            // rodopio: o corpo gira sozinho (mais rápido nos níveis altos)
            float yaw = SbFx.wrap(mob.getYRot() + 22F * (amplifier + 1));
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
        if (e.tickCount % 8 == 0) {
            SbFx.spinRing(sl, e.position().add(0, e.getBbHeight() * 0.5, 0), SbFx.STEEL, e.getBbWidth() * 0.8 + 0.2, 6 + 2 * amplifier);
        }
        return true;
    }
}
