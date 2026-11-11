package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Instinto Selvagem (Bestial): os sentidos da fera despertam — mais velocidade e ataques mais rápidos, 20% de chance de
 * esquivar golpes e projéteis (efeito {@code race_wild_instinct}) e, em quem conjura, o faro marca (Brilho) as feras
 * hostis num raio de 20 blocos por 6 s. Com Inverter num inimigo: o rugido o assusta (Fraqueza e Lentidão).
 */
public class WildInstinctEffect extends RaceEffectGlyph {
    public static final int TICKS = 240;
    public static final double SCENT = 20.0;
    private static final int AMBER = 0xD08A3A;

    public WildInstinctEffect() {
        super("race_wild_instinct", 9, Element.ARCANE, 2);
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
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(100), 0, false, true, true), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(100), 0, false, true, true), ctx.caster());
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(100), ctx.caster(), stats.power());
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() + 0.2, le.getZ(), 3, 0.3, 0.1, 0.3, 0);
            RaceFx.sound(level, c, SoundEvents.WOLF_GROWL, 1.0F, 0.8F);
            return;
        }
        int amp = stats.power() >= 1.8F ? 1 : 0;
        le.addEffect(new MobEffectInstance(CharacterContent.WILD_INSTINCT, stats.duration(TICKS), amp, false, true, true), ctx.caster());
        if (le == ctx.caster()) {
            int marked = 0;
            for (LivingEntity m : ctx.enemiesAround(le.position(), SCENT)) {
                if (!(m instanceof Enemy) || ++marked > 24) continue;
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false, false), le);
                level.sendParticles(RaceFx.dust(AMBER, 0.9F), m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 4, 0.15, 0.1, 0.15, 0);
            }
        }
        // Olhos de fera: dois brilhos âmbar + poeira de corrida
        Vec3 eye = le.getEyePosition();
        Vec3 side = new Vec3(-le.getLookAngle().z, 0, le.getLookAngle().x);
        side = (side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize()).scale(0.18);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 p = eye.add(le.getLookAngle().scale(0.35)).add(side.scale(s));
            level.sendParticles(RaceFx.dust(0xFFC23A, 0.8F), p.x, p.y, p.z, 3, 0.01, 0.01, 0.01, 0);
        }
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 1.0, 16, RaceFx.dust(AMBER, 1.1F));
        level.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + 0.1, le.getZ(), 8, 0.4, 0.05, 0.4, 0.04);
        RaceFx.sound(level, c, SoundEvents.WOLF_HOWL, 0.5F, 1.3F);
        RaceFx.sound(level, c, SoundEvents.FOX_AGGRO, 0.8F, 1.0F);
    }
}
