package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import java.util.UUID;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;
import com.renanpontes.isekaimahou.feature.light_shadow.ai.LsAi;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Possuído (Possessão): mantém a IA temporária do mob (alvo = inimigos do possessor, segue o possessor).
 * Recoloca os objetivos depois de recarregar o mundo, já que objetivos de IA não são salvos.
 */
public class PossessionEffect extends MobEffect {
    public PossessionEffect() {
        super(MobEffectCategory.NEUTRAL, 0x6A2FA0);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        if (!(e instanceof Mob mob)) return false;
        UUID owner = LsUtil.possessor(mob);
        if (owner == null) return false;
        LsAi.ensurePossessed(mob);
        if (mob.getTarget() != null && LsUtil.isAlly(sl, owner, mob.getTarget())) LsAi.clearTarget(mob);
        // Olhos violeta e almas saindo da cabeça
        sl.sendParticles(LsFx.dust(LsFx.VIOLET, 1.0F), mob.getX(), mob.getEyeY() + 0.1, mob.getZ(), 2, 0.2, 0.05, 0.2, 0);
        if (mob.tickCount % 20 < 5) {
            sl.sendParticles(Vfx.soul(), mob.getX(), mob.getY() + mob.getBbHeight() + 0.1, mob.getZ(), 1, 0.15, 0.05, 0.15, 0.01);
        }
        return true;
    }
}
