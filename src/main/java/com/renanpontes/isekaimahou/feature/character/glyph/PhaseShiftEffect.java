package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Desvanecer (Fantasma): em você, o corpo vira névoa — some (Invisibilidade 5 s), fica intangível por um instante
 * (Resistência alta por 1 s) e desliza até 6 blocos à frente ATRAVESSANDO criaturas (nunca paredes), mais rápido.
 * Em aliados: Invisibilidade e Velocidade. Com Inverter num inimigo: o terror de um fantasma (Cegueira, Exposto).
 */
public class PhaseShiftEffect extends RaceEffectGlyph {
    public static final int TICKS = 100;
    public static final float RANGE = 6.0F;

    public PhaseShiftEffect() {
        super("race_phase_shift", 12, Element.SHADOW, 2);
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
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stats.duration(60), 0, false, true, true), ctx.caster());
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(TICKS), ctx.caster(), stats.power());
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.03);
            RaceFx.sound(level, c, SoundEvents.PHANTOM_BITE, 0.8F, 0.6F);
            return;
        }
        int ticks = stats.duration(TICKS);
        le.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true), ctx.caster());
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true, true), ctx.caster());
        if (le == ctx.caster()) {
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 3, false, false, true), ctx.caster());
            // Desliza para a frente atravessando criaturas (o raio só para em blocos)
            Vec3 look = ctx.look();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1e-4) {
                flat = flat.normalize();
                Vec3 from = le.position().add(0, 0.5, 0);
                Vec3 to = from.add(flat.scale(RANGE * Math.min(1.5F, stats.power())));
                BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, le));
                Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().subtract(flat.scale(0.6)) : to;
                RaceFx.line(level, from, end, RaceFx.dust(0x9FD8E8, 1.0F));
                le.teleportTo(end.x, end.y - 0.5, end.z);
                le.resetFallDistance();
                if (le instanceof ServerPlayer sp) MagicHover.allow(sp, 40);
            }
        }
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 16, 0.3, 0.6, 0.3, 0.03);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.02);
        RaceFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 1.2F);
        RaceFx.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1.0F);
    }
}
