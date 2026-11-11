package com.renanpontes.isekaimahou.feature.fusions_d.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.TwilightPillarEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.YinYangEntity;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
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

/** Misturas D do CREPÚSCULO (Luz + Sombra). */
public final class TwilightGlyphs {

    public static void register() {
        GlyphRegistry.register(new Ragnarok().element(Element.TWILIGHT).tier(4).minCircle(5));
        GlyphRegistry.register(new YinYang().element(Element.TWILIGHT).tier(3).minCircle(3));
    }

    /**
     * Ragnarök: três ondas de pilares telegrafados (sem dono: não entram na Pilha) — anel de luz, anel de sombra,
     * pilares sobre cada inimigo — e por fim o pilar duplo central, que abre uma cratera. Luz e sombra em sequência
     * provocam Ruptura; os de luz vitrificam o chão, os de sombra o apagam.
     */
    static class Ragnarok extends FdAreaGlyph {
        Ragnarok() { super("ragnarok", 52, 16); }

        @Override
        protected int minInterval() { return 40; }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = FdUtil.ground(sl, at, 10);
            float R = Math.min(22F, stats.radius(10F));
            float pr = Math.min(4F, 2.2F + 0.3F * stats.aoe);
            // Onda 1: anel de luz
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                pillar(ctx, c.add(Math.cos(a) * R * 0.5, 2, Math.sin(a) * R * 0.5), pr, 24, false, 9F, stats);
            }
            // Onda 2: anel de sombra, intercalado e mais largo
            BossKit.schedule(sl, 14, () -> {
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * (i + 0.5) / 6;
                    pillar(ctx, c.add(Math.cos(a) * R * 0.85, 2, Math.sin(a) * R * 0.85), pr, 24, true, 9F, stats);
                }
            });
            // Onda 3: um pilar sobre cada inimigo, alternando luz e sombra
            BossKit.schedule(sl, 32, () -> {
                List<LivingEntity> foes = new ArrayList<>(FdUtil.enemies(ctx, c, R + 2));
                for (int i = 0; i < Math.min(8, foes.size()); i++) pillar(ctx, foes.get(i).position(), pr * 0.9F, 20, i % 2 == 1, 8F, stats);
            });
            // Final: o pilar duplo central
            BossKit.schedule(sl, 54, () -> finale(ctx, c, Math.min(7F, 3.5F + 0.4F * stats.aoe), stats));
            FdFx.sound(sl, c, SoundEvents.BELL_RESONATE, 2F, 0.5F);
            FdFx.sound(sl, c, SoundEvents.WITHER_SPAWN, 0.8F, 1.4F);
            BossKit.shake(sl, c, R + 12, 0.8F, 70);
        }

        private static void pillar(SpellContext ctx, Vec3 spot, float r, int warmup, boolean shadow, float base, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 g = FdUtil.ground(sl, spot, 10);
            Telegraph.delayed(sl, Telegraph.Shape.circle(g, r).warmup(warmup).color(shadow ? Telegraph.VOID : Telegraph.HOLY)
                    .vertical(1.5, 6), hits -> {
                TwilightPillarEntity.spawn(sl, g, r, shadow);
                for (LivingEntity le : hits) {
                    if (ctx.isAlly(le) || FdUtil.untouchable(le)) continue;
                    le.invulnerableTime = 0;
                    ctx.damage(le, base, stats, null, shadow ? Element.SHADOW : Element.LIGHT);
                    if (shadow) Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 100, ctx.caster(), ctx.powerMult());
                    else {
                        Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 80, ctx.caster(), ctx.powerMult());
                        le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0), ctx.caster());
                    }
                }
                if (shadow) Destruction.cylinder(sl, g.add(0, -0.5, 0), r * 0.75F, -4F, ctx.caster(), Destruction.Mode.ERASE);
                else Destruction.surface(sl, g, r, ctx.caster(), Destruction.Mode.VITRIFY);
                sl.sendParticles(shadow ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD, g.x, g.y + 0.5, g.z, 30, r * 0.4, 0.3, r * 0.4, 0.2);
                FdFx.shockwave(sl, g.add(0, 0.2, 0), FdFx.dust(shadow ? FdFx.TWILIGHT_SHADOW : FdFx.TWILIGHT_GOLD, 1.8F), 20, 0.35);
                FdFx.sound(sl, g, shadow ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.BEACON_POWER_SELECT, 1.6F, shadow ? 0.6F : 1.4F);
                FdFx.sound(sl, g, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, shadow ? 0.5F : 1.2F);
            });
        }

        private static void finale(SpellContext ctx, Vec3 c, float r, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Telegraph.delayed(sl, Telegraph.Shape.circle(c, r).warmup(30).color(0xC06AA0).vertical(1.5, 10), hits -> {
                TwilightPillarEntity.spawn(sl, c.add(0.6, 0, 0), r * 0.75F, false);
                TwilightPillarEntity.spawn(sl, c.add(-0.6, 0, 0), r * 0.75F, true);
                for (LivingEntity le : hits) {
                    if (ctx.isAlly(le) || FdUtil.untouchable(le)) continue;
                    le.invulnerableTime = 0;
                    ctx.damage(le, 10F, stats, null, Element.LIGHT);
                    le.invulnerableTime = 0;
                    ctx.damage(le, 10F, stats, null, Element.SHADOW);
                    Statuses.apply(le, Statuses.Kind.STUNNED, 1, 30, ctx.caster(), 1F);
                }
                Destruction.crater(sl, c, r + 1.5F, ctx.caster());
                sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 2, 0, 0, 0, 0);
                FdFx.shockwave(sl, c.add(0, 0.4, 0), FdFx.fade(FdFx.TWILIGHT_GOLD, FdFx.TWILIGHT_SHADOW, 3F), 48, 0.8);
                FdFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.5F);
                FdFx.sound(sl, c, SoundEvents.BELL_BLOCK, 2F, 0.4F);
                BossKit.shake(sl, c, 40, 2.4F, 26);
            });
        }
    }

    /** Equilíbrio Absoluto: orbe yin-yang que cura e purifica aliados e fere inimigos com luz e sombra alternadas. */
    static class YinYang extends FdAreaGlyph {
        YinYang() { super("yin_yang", 28, 6); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            YinYangEntity.summon(ctx, at, target != null && FdUtil.isSelf(ctx, target), stats);
        }
    }

    private TwilightGlyphs() {}
}
