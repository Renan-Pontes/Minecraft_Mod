package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Asas Sombrias (Demônio): asas de sombra rasgam as costas — um impulso de asa para cima e para a frente e, enquanto
 * durarem, gravidade bem menor (dá para planar), queda segura e mais velocidade (efeito {@code race_shadow_wings}). As
 * asas aparecem no jogador para todo mundo. Recolocar no ar = mais uma batida de asa. Com Inverter num inimigo: a
 * sombra o puxa para o chão (Escuridão e Lentidão).
 */
public class ShadowWingsEffect extends RaceEffectGlyph {
    public static final int TICKS = 240;
    private static final int SHADOW = 0x4A2A5E, VOID = 0x14081C;

    public ShadowWingsEffect() {
        super("race_shadow_wings", 12, Element.SHADOW, 2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        Vec3 c = RaceFx.center(le);
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            le.push(0, -0.9, 0);
            le.hurtMarked = true;
            le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, stats.duration(80), 0, false, true, true), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(80), 1, false, true, true), ctx.caster());
            Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, stats.duration(80), ctx.caster(), stats.power());
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.02);
            RaceFx.sound(level, c, SoundEvents.PHANTOM_BITE, 0.8F, 0.6F);
            return;
        }
        int ticks = stats.duration(TICKS);
        // Amplificador SEMPRE 0: a gravidade do efeito escala com o nível
        le.addEffect(new MobEffectInstance(CharacterContent.SHADOW_WINGS, ticks, 0, false, true, true), ctx.caster());
        Vec3 look = le == ctx.caster() ? ctx.look() : le.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
        double k = 0.75 * Math.min(1.6, stats.power());
        le.setDeltaMovement(le.getDeltaMovement().multiply(0.5, 0, 0.5).add(flat.x * k, 0.85, flat.z * k));
        le.hurtMarked = true;
        le.resetFallDistance();
        RaceFx.entityFx(le, CharacterNetwork.FX_WINGS, ticks);

        // Batida de asa: penas de sombra para os lados e um anel de fumaça no chão
        Vec3 back = c.subtract(flat.scale(0.4));
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        for (int i = 1; i <= 8; i++) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = back.add(side.scale(s * i * 0.22)).add(0, 0.25 - Math.abs(i - 4) * 0.06, 0);
                level.sendParticles(RaceFx.dust(i % 2 == 0 ? SHADOW : VOID, 1.4F), p.x, p.y, p.z, 1, 0.02, 0.05, 0.02, 0);
            }
        }
        level.sendParticles(ParticleTypes.SQUID_INK, back.x, back.y, back.z, 10, 0.4, 0.2, 0.4, 0.04);
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 1.3, 18, ParticleTypes.LARGE_SMOKE);
        RaceFx.sound(level, c, SoundEvents.PHANTOM_FLAP, 1.0F, 0.7F);
        RaceFx.sound(level, c, SoundEvents.ENDER_DRAGON_FLAP, 0.5F, 1.4F);
    }
}
