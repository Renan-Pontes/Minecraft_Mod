package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Magnetismo: por 3 s, puxa para o conjurador todos os itens e orbes de XP em volta do alvo (raio 10) e
 * arrasta os inimigos que usam armadura/armas de ferro (e golens de ferro), que ainda levam dano.
 * Com Inverter, repele.
 */
public class MagnetismEffect extends EffectGlyph {
    private static final DustParticleOptions IRON_DUST = new DustParticleOptions(new Vector3f(0.75F, 0.78F, 0.82F), 0.9F);
    private static final DustParticleOptions FIELD_DUST = new DustParticleOptions(new Vector3f(0.85F, 0.25F, 0.25F), 0.8F);

    public MagnetismEffect() {
        super("magnetism", 20);
        element(Element.EARTH);
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
        start(ctx, target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        start(ctx, hit.getLocation(), stats);
    }

    private static void start(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!EwUtil.once(ctx, "magnetism")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(10.0F);
        int ticks = stats.duration(60);
        double sign = stats.inverted ? -1 : 1;
        EwFx.sound(level, center, SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
        EwFx.sound(level, center, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
        EwTasks.repeat(ticks, age -> {
            LivingEntity caster = ctx.caster();
            Vec3 anchor = caster != null && caster.isAlive() && caster.level() == level ? caster.position().add(0, 0.9, 0) : center;
            AABB box = new AABB(center, center).inflate(radius);
            for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                Vec3 d = anchor.subtract(e.position());
                double len = d.length();
                if (len < 1.0 || len > radius * 1.5) continue;
                Vec3 v = d.scale(sign * Math.min(0.6, 0.15 + len * 0.04) / len);
                e.setDeltaMovement(v.x, v.y + 0.05, v.z);
                e.hasImpulse = true;
                if (age % 4 == 0) level.sendParticles(IRON_DUST, e.getX(), e.getY() + 0.2, e.getZ(), 1, 0, 0, 0, 0);
            }
            for (LivingEntity e : ctx.enemiesAround(center, radius)) {
                if (!EwUtil.isMagnetic(e)) continue;
                Vec3 d = anchor.subtract(e.position());
                double len = d.length();
                if (len < 1.8 && sign > 0) continue;
                EwUtil.push(e, d.scale(sign * 0.12 / Math.max(1, len)).multiply(1, 0, 1), sign > 0 ? Math.min(0.08, d.y * 0.02 + 0.02) : 0.02);
                if (age % 20 == 0) ctx.damage(e, 1.5F, stats, null);
                if (age % 3 == 0) EwFx.moving(level, FIELD_DUST, e.position().add(0, e.getBbHeight() * 0.6, 0), d.normalize().scale(0.6 * sign));
            }
            if (age % 10 == 0) {
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + age * 0.1;
                    Vec3 p = center.add(Math.cos(a) * radius * 0.6, 0.5, Math.sin(a) * radius * 0.6);
                    EwFx.moving(level, IRON_DUST, p, anchor.subtract(p).normalize().scale(0.5 * sign));
                }
            }
            return true;
        });
    }
}
