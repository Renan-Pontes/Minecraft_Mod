package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ponto Vital (origem Ladino, Sombra): marca o alvo por 10 s (mira vermelha girando sobre ele) e o deixa Exposto. O
 * PRÓXIMO golpe ou magia SUA nele é crítico garantido ×2 e gasta a marca; matar um alvo marcado devolve 10% do seu
 * recurso. Uma marca por alvo (a mais nova vale).
 */
public class VitalMarkEffect extends EffectGlyph {
    public static final int TICKS = 200;

    public VitalMarkEffect() {
        super("rogue_vital_mark", 8);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(2);
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public int hintDuration() {
        return TICKS;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || caster == null || le == caster) return;
        ServerLevel level = ctx.level();
        int ticks = stats.duration(TICKS);
        RogueCombat.mark(le, caster, ticks, stats.power() * ctx.powerMult());
        Statuses.applyUpTo(le, Statuses.Kind.EXPOSED, 1, ticks, caster, stats.power() * ctx.powerMult(), 2);
        Vec3 c = le.position().add(0, le.getBbHeight() + 0.45, 0);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            level.sendParticles(RogueFx.dust(RogueFx.BLOOD, 1.0F), c.x + Math.cos(a) * 0.55, c.y, c.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y - 0.3, c.z, 3, 0.15, 0.1, 0.15, 0.05);
        RogueFx.sound(level, le.position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.9F, 1.8F);
        RogueFx.actionbar(caster, Component.translatable("message.isekai_mahou.class_rogue.vital.marked", le.getDisplayName()), RogueFx.BLOOD);
    }
}
