package com.renanpontes.isekaimahou.feature.class_knight;

import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Efeitos do Cavaleiro Rúnico:
 * <ul>
 *   <li>{@code knight_blade_aura} (Aura de Espada): +3 de dano corpo a corpo por nível, +10% de velocidade de ataque e
 *   golpes rúnicos pela METADE do custo; chamas de luz sobem pela lâmina.</li>
 *   <li>{@code knight_rune_mark} (Marca Rúnica): só o indicador dos acúmulos da marca (a lógica fica em
 *   {@link KnightMarks}); runa girando sobre a cabeça.</li>
 * </ul>
 */
public class KnightMobEffect extends MobEffect {
    private final boolean aura;

    public KnightMobEffect(MobEffectCategory category, int color, boolean aura) {
        super(category, color);
        this.aura = aura;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        int rgb = getColor();
        if (aura) {
            if (KnightSwords.holdsSword(e)) WeaponFx.weaponGlow(level, e, com.renanpontes.isekaimahou.core.Element.LIGHT, rgb);
        } else {
            float a = e.tickCount * 0.25F;
            double y = e.getY() + e.getBbHeight() + 0.45;
            for (int i = 0; i <= Math.min(2, amplifier); i++) {
                float ang = a + i * Mth.TWO_PI / 3F;
                level.sendParticles(WeaponFx.dust(rgb, 0.7F), e.getX() + Mth.cos(ang) * 0.45, y, e.getZ() + Mth.sin(ang) * 0.45,
                        1, 0, 0, 0, 0);
            }
        }
        return true;
    }
}
