package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Aura de Espada (efeito benéfico, tier 2, Luz): a lâmina se cobre de luz por 15 s (Estender) — +3 de dano corpo a corpo
 * por nível (Amplificar até III), +10% de velocidade de ataque e os golpes rúnicos custam METADE. Em aliados também
 * funciona. Inverter: o inimigo fica Fraco (II) pelo mesmo tempo.
 */
public class BladeAuraEffect extends EffectGlyph {
    public BladeAuraEffect() {
        super(KnightGlyphs.BLADE_AURA, 14);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return 300; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        int t = stats.duration(300);
        Vec3 c = KnightFx.center(le);
        if (stats.inverted && !ctx.isAlly(le)) {
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 1), ctx.caster());
            ctx.level().sendParticles(KnightFx.dust(0x6A6A7E, 1.0F), c.x, c.y, c.z, 14, 0.3, 0.5, 0.3, 0);
            return;
        }
        int amp = Mth.clamp(stats.amplify, 0, 2);
        le.addEffect(new MobEffectInstance(KnightRegistries.BLADE_AURA, t, amp), ctx.caster());
        // anel de luz subindo pelo corpo
        for (int i = 0; i < 16; i++) {
            float a = i * Mth.TWO_PI / 16;
            double y = le.getY() + i * le.getBbHeight() / 16.0;
            ctx.level().sendParticles(KnightFx.dust(0xFFE27A, 1.0F), le.getX() + Mth.cos(a) * 0.7, y, le.getZ() + Mth.sin(a) * 0.7,
                    1, 0, 0, 0, 0);
        }
        KnightFx.sound(ctx.level(), c, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.8F);
        KnightFx.sound(ctx.level(), c, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0F, 1.2F);
    }
}
