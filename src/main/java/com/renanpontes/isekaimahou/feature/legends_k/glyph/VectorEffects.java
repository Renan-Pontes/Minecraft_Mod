package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.Guards;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Controle de Vetores (A Certain Magical Index — Accelerator), do Mago:
 * <ul>
 *   <li><b>Reflexão de Vetores</b> — em você ou num aliado, por 4 s (Estender +): projéteis voltam para quem atirou
 *       (mais rápidos), empurrões não te movem, e golpes corpo a corpo perdem 40% e jogam o atacante longe.</li>
 *   <li><b>Soco Vetorial</b> — dano 7 e um empurrão ENORME na direção em que você olha (chefes: bem menos).</li>
 *   <li><b>Explosão de Vetores</b> — o ar é puxado para um ponto por ⅓ s e explode para fora: dano 5 e empurrão
 *       em todos os inimigos no raio 4 (Expandir +). Em você: centrado em você.</li>
 * </ul>
 */
public final class VectorEffects {

    /** Reflexão de Vetores. */
    public static class Reflect extends EffectGlyph {
        public Reflect() {
            super("lk_vector_reflect", 16);
            origin(MagicPath.MAGE);
            element(Element.WIND);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 80; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity t) || !t.isAlive()) return;
            Guards.vector(t, stats.duration(80));
            ServerLevel l = ctx.level();
            Vec3 c = LkUtil.center(t);
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                Vec3 d = new Vec3(Math.cos(a), 0.3 * ((i % 3) - 1), Math.sin(a));
                LkFx.line(l, c.add(d.scale(0.4)), c.add(d.scale(1.2)), LkFx.VECTOR, 0.2F);
            }
            LkUtil.sound(l, c, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 1.4F);
            LkUtil.say(t, "vector_on", ChatFormatting.WHITE);
        }
    }

    /** Soco Vetorial. */
    public static class Punch extends EffectGlyph {
        public Punch() {
            super("lk_vector_punch", 12);
            origin(MagicPath.MAGE);
            element(Element.WIND);
            tier(2);
            minCircle(2);
        }

        @Override
        public float baseDamage() { return 7F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity t) || LkUtil.self(ctx, target)) return;
            ctx.damage(t, 7F, stats, c);
            Vec3 dir = c.getLookAngle();
            dir = new Vec3(dir.x, Math.max(0.0, dir.y), dir.z).normalize();
            double k = (2.2 + 0.4 * Math.max(0, stats.amplify)) * (LkUtil.boss(t) ? 0.25 : 1.0);
            LkUtil.impulse(t, dir.scale(k).add(0, 0.35, 0));
            ServerLevel l = ctx.level();
            Vec3 p = LkUtil.center(t);
            ElementFx.shockwave(l, p, LkFx.VECTOR, 1.6F, 10, dir);
            LkFx.line(l, p, p.add(dir.scale(3)), LkFx.VECTOR, 0.25F);
            LkUtil.sound(l, p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
        }
    }

    /** Explosão de Vetores. */
    public static class Burst extends EffectGlyph {
        public Burst() {
            super("lk_vector_burst", 20);
            origin(MagicPath.MAGE);
            element(Element.WIND);
            tier(4);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return 5F; }

        @Override
        public float hintRadius() { return 4F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !LkUtil.once(ctx, "vburst")) return;
            boolean self = LkUtil.self(ctx, target);
            if (!self && !LkUtil.isFoe(ctx, target)) return;
            Vec3 center = self ? c.position().add(0, 1, 0) : LkUtil.center(target);
            float r = stats.radius(4F);
            ServerLevel l = ctx.level();
            // 1) o ar é puxado para o centro
            List<LivingEntity> foes = LkUtil.foesAround(ctx, center, r, 16);
            for (LivingEntity o : foes) {
                Vec3 in = center.subtract(o.position());
                if (in.lengthSqr() > 1e-4 && !LkUtil.boss(o)) LkUtil.impulse(o, in.normalize().scale(0.45));
            }
            l.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            ElementFx.gatherAt(l, center, Element.WIND, LkFx.VECTOR, r, 3, 7);
            LkUtil.sound(l, center, SoundEvents.BREEZE_INHALE, 1.0F, 0.8F);
            // 2) e explode para fora
            SpellScheduler.schedule(7, () -> {
                ElementFx.shockwave(l, center, LkFx.VECTOR, r, 14, null);
                l.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 3, r * 0.3, 0.3, r * 0.3, 0);
                LkUtil.sound(l, center, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.7F);
                for (LivingEntity o : LkUtil.foesAround(ctx, center, r + 0.5, 16)) {
                    ctx.damage(o, 5F, stats, c);
                    Vec3 out = o.position().subtract(center);
                    out = new Vec3(out.x, 0, out.z);
                    if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
                    double k = LkUtil.boss(o) ? 0.3 : 1.4;
                    LkUtil.impulse(o, out.normalize().scale(k).add(0, 0.45, 0));
                }
            });
        }
    }

    private VectorEffects() {}
}
