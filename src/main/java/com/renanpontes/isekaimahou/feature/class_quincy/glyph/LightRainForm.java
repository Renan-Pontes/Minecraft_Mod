package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chuva de Luz (homenagem ao Licht Regen): uma estrela Quincy se abre no céu sobre o ponto mirado (até 32 blocos) e
 * dela caem flechas espirituais durante ~1,5 s, cada uma aplicando os efeitos. Flechas = 6 + 2 por Dividir + 2 por
 * Expandir (máx. 16) — cada flecha é uma cópia no custo. Estágio 2+: cai sobre o ponto de impacto anterior.
 */
public class LightRainForm extends FormGlyph {
    public LightRainForm() {
        super("quincy_light_rain", 14);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(3);
        minCircle(3);
    }

    public static int arrows(SpellStats s) {
        return Math.min(16, 6 + 2 * Math.max(0, s.split) + 2 * Math.max(0, s.aoe));
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return arrows(stats);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 10;
    }

    @Override
    public int baseCooldown() {
        return 60;
    }

    @Override
    public float hintRange() {
        return 32F;
    }

    @Override
    public float hintRadius() {
        return 3.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 target = ctx.anchor();
        if (target == null) {
            Vec3 eye = ctx.eyePos();
            Vec3 end = eye.add(ctx.look().scale(32));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        }
        float radius = s.radius(3.5F);
        int n = arrows(s);
        Vec3 sky = target.add(0, 14, 0);
        QuincyFx.pentacle(level, sky, radius + 0.6, 0F);
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.burstNear(level, com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.STAR,
                sky, Vec3.ZERO, radius + 0.6F, com.renanpontes.isekaimahou.feature.class_quincy.Quincy.REISHI_RGB, caster.getId());
        QuincyFx.ring(level, target.add(0, 0.1, 0), radius, 24, 0F, QuincyFx.REISHI);
        QuincyFx.sound(level, target, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.7F);
        final Vec3 center = target;
        for (int i = 0; i < n; i++) {
            int delay = 4 + i * 2;
            SpellScheduler.schedule(delay, () -> {
                if (caster.isRemoved()) return;
                double a = ctx.random().nextDouble() * Mth.TWO_PI;
                double r = Math.sqrt(ctx.random().nextDouble()) * radius;
                Vec3 ground = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Vec3 from = ground.add((ctx.random().nextDouble() - 0.5) * 1.5, 14, (ctx.random().nextDouble() - 0.5) * 1.5);
                SpiritArrowEntity arrow = SpiritArrowEntity.spell(ctx, caster, from).life(40);
                Vec3 d = ground.subtract(from).normalize();
                arrow.shoot(d.x, d.y, d.z, 2.4F, 0.0F);
                level.addFreshEntity(arrow);
                level.sendParticles(QuincyFx.WHITE, from.x, from.y, from.z, 4, 0.2, 0.2, 0.2, 0.01);
            });
        }
        SpellScheduler.schedule(4 + n * 2, () -> QuincyFx.sound(level, center, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.2F));
    }
}
