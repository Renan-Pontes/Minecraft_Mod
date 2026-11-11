package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Eco: uma onda de vento se espalha (raio 32) e revela todas as criaturas — elas brilham atraves das
 * paredes por 10 s. O conjurador recebe quantas encontrou.
 */
public class SonarEffect extends EffectGlyph {
    public SonarEffect() {
        super("sonar", 15);
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
        pulse(ctx, target.position().add(0, target.getBbHeight() * 0.5, 0), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        pulse(ctx, hit.getLocation().add(0, 1, 0), stats);
    }

    private static void pulse(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!EwUtil.once(ctx, "sonar")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(32.0F);
        int ticks = stats.duration(200);
        LivingEntity caster = ctx.caster();
        int found = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e != caster && e.distanceToSqr(center) <= radius * radius)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), caster);
            found++;
        }
        // anel de vento se expandindo
        EwTasks.repeat(16, age -> {
            double r = radius * (age + 1) / 16.0;
            int points = (int) Math.min(90, 12 + r * 2.5);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(ElementFx.dust(Element.WIND, 1.6F), center.x + Math.cos(a) * r, center.y,
                        center.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
            }
            return true;
        });
        EwFx.sound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 1.8F);
        EwFx.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.6F);
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.sonar", found).withColor(Element.WIND.color), true);
        }
    }
}
