package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vacuo: o ar some num ponto. Por 4 s tudo em volta (raio 6) e sugado para o centro e quem esta la perde o
 * folego (o ar acaba e passa a sufocar). No fim o ar volta de uma vez num estouro que empurra todos.
 */
public class VacuumEffect extends EffectGlyph {
    public VacuumEffect() {
        super("vacuum", 28);
        element(Element.WIND);
        tier(4);
        minCircle(3);
    }

    /** Efeito de area num ponto: um projetil que acerta o chao tambem dispara. */
    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        Vec3 at = target == ctx.caster() ? ctx.eyePos().add(ctx.look().scale(5)) : target.position().add(0, target.getBbHeight() * 0.5, 0);
        start(ctx, at, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        start(ctx, hit.getLocation().add(0, 1.0, 0), stats);
    }

    private static void start(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!EwUtil.once(ctx, "vacuum")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(6.0F);
        int ticks = stats.duration(80);
        EwFx.sound(level, center, SoundEvents.BREEZE_INHALE, 1.5F, 0.6F);
        EwFx.sound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.6F);
        EwTasks.repeat(ticks + 1, age -> {
            if (age >= ticks) {
                burst(ctx, level, center, radius, stats);
                return false;
            }
            for (LivingEntity e : ctx.enemiesAround(center, radius)) {
                Vec3 d = center.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                double len = d.length();
                if (len > 0.8) EwUtil.setVelocity(e, d.scale(Math.min(0.35, 0.08 + 0.4 / len) / len * EwUtil.knockbackFactor(e)));
                e.resetFallDistance();
                EwUtil.holdAloft(e);
                // sem ar: o folego cai mais rapido do que volta e, zerado, sufoca
                int air = e.getAirSupply() - 12;
                e.setAirSupply(Math.max(-20, air));
                if (air <= -20 && age % 20 == 0) ctx.damage(e, 2.5F, stats, null);
            }
            for (Entity it : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), en -> en instanceof ItemEntity)) {
                Vec3 d = center.subtract(it.position());
                if (d.length() > 0.5) it.setDeltaMovement(d.normalize().scale(0.25));
            }
            // correntes de ar convergindo
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double b = (level.random.nextDouble() - 0.5) * Math.PI;
                Vec3 p = center.add(Math.cos(a) * Math.cos(b) * radius, Math.sin(b) * radius * 0.6, Math.sin(a) * Math.cos(b) * radius);
                EwFx.moving(level, Vfx.cloud(), p, center.subtract(p).scale(0.12));
            }
            if (age % 4 == 0) level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 2, 0.15, 0.15, 0.15, 0.01);
            if (age % 20 == 0) EwFx.sound(level, center, SoundEvents.BREEZE_INHALE, 0.8F, 0.5F);
            return true;
        });
    }

    private static void burst(SpellContext ctx, ServerLevel level, Vec3 center, float radius, SpellStats stats) {
        for (LivingEntity e : ctx.enemiesAround(center, radius)) {
            Vec3 out = e.position().subtract(center);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(0, 1, 0);
            EwUtil.push(e, EwUtil.horizontal(out).normalize().scale(1.0), 0.4);
            ctx.damage(e, 3.0F, stats, null);
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(Vfx.poof(), center.x, center.y, center.z, 30, radius * 0.3, radius * 0.3, radius * 0.3, 0.1);
        EwFx.sound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 1.5F, 0.6F);
    }
}
