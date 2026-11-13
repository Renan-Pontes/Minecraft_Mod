package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.AbyssTentacleEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do ABISMO (Água + Sombra).
 * Tier 1: Veneno, Tinta · Tier 2: Tentáculos · Tier 3: Pressão Abissal · Tier 4: Afogamento Abissal.
 */
public final class AbyssGlyphs {

    public static void register() {
        GlyphRegistry.register(new Venom());
        GlyphRegistry.register(new Ink());
        GlyphRegistry.register(new Tentacles());
        GlyphRegistry.register(new AbyssalPressure());
        GlyphRegistry.register(new AbyssalDrown());
    }

    /**
     * Veneno: dano 1,5 e Veneno Abissal por 7s — 1 de dano (+1 por nível) a cada 1,25s, que PODE matar e
     * funciona até em mortos-vivos.
     */
    public static class Venom extends EffectGlyph {
        public Venom() {
            super("venom", 8);
            element(Element.ABYSS);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                FbUtil.hurt(ctx, e, 1.5F, stats, null);
                if (!e.isAlive()) continue;
                e.addEffect(new MobEffectInstance(FbRegistries.ABYSSAL_VENOM, stats.duration(140), FbUtil.amp(ctx, stats)), ctx.caster());
                Vec3 c = FbUtil.center(e);
                sl.sendParticles(FbFx.fade(FbFx.VENOM, FbFx.ABYSS_DEEP, 1.3F), c.x, c.y, c.z, 16, e.getBbWidth() * 0.5,
                        e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0);
                sl.sendParticles(Vfx.ink(), c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.03);
                FbFx.sound(sl, c, SoundEvents.PUFFER_FISH_STING, 0.9F, 0.8F);
            }
        }
    }

    /**
     * Tinta: nuvem de tinta negra (raio 3,5) no alvo: inimigos dentro ficam cegos por 5s e perdem quem
     * estavam perseguindo (mobs só voltam a enxergar alvos bem perto).
     */
    public static class Ink extends EffectGlyph {
        public Ink() {
            super("ink", 10);
            element(Element.ABYSS);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = FbUtil.center(target);
            if (!FbUtil.once(ctx, FbUtil.posKey("ink", c))) return;
            float r = stats.radius(3.5F);
            List<LivingEntity> hit = new ArrayList<>(ctx.enemiesAround(c, r));
            if (!FbUtil.isSelf(ctx, target) && target instanceof LivingEntity le && !hit.contains(le) && !ctx.isAlly(le)) hit.add(le);
            int ticks = stats.duration(100);
            for (LivingEntity e : hit) {
                if (FbUtil.untouchable(e)) continue;
                if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), ctx.caster());
                e.addEffect(new MobEffectInstance(FbRegistries.INKED, ticks, 0), ctx.caster());
                FbUtil.forgetTarget(e);
            }
            sl.sendParticles(Vfx.ink(), c.x, c.y, c.z, 90, r * 0.45, r * 0.3, r * 0.45, 0.05);
            sl.sendParticles(Vfx.glowInk(), c.x, c.y, c.z, 10, r * 0.4, r * 0.3, r * 0.4, 0.03);
            sl.sendParticles(FbFx.dust(FbFx.ABYSS_DEEP, 2.0F), c.x, c.y, c.z, 30, r * 0.5, r * 0.3, r * 0.5, 0);
            FbFx.sound(sl, c, SoundEvents.SQUID_SQUIRT, 1.4F, 0.7F);
            FbFx.sound(sl, c, SoundEvents.GLOW_SQUID_SQUIRT, 0.8F, 0.6F);
        }
    }

    /**
     * Tentáculos: 3 tentáculos (+1 por Dividir) brotam em volta do alvo, prendem e puxam para baixo por 4s,
     * ferindo a cada segundo. Dentro d'água puxam para o fundo e ferem 50% mais.
     */
    public static class Tentacles extends EffectGlyph {
        public Tentacles() {
            super("tentacles", 16);
            element(Element.ABYSS);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            int count = 3 + Math.max(0, stats.split);
            float tickDmg = 1.5F * stats.power();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(4F))) {
                if (FbUtil.untouchable(e)) continue;
                int ticks = stats.duration(80);
                if (FbUtil.isBoss(e)) ticks = Math.max(10, ticks / 2);
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false, true), ctx.caster());
                AbyssTentacleEntity.grab(ctx, e, count, ticks, tickDmg, stats);
            }
        }
    }

    /**
     * Pressão Abissal: o peso do oceano esmaga o alvo. Dano 4, multiplicado pela "profundidade": +50% na água,
     * +50% submerso, até +100% conforme desce abaixo do nível do mar e +50% no Abismo (máx. ×3).
     * Com pressão alta (×1,8+) também deixa lento e com fadiga.
     */
    public static class AbyssalPressure extends EffectGlyph {
        public AbyssalPressure() {
            super("abyssal_pressure", 18);
            element(Element.ABYSS);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(3F))) {
                float f = pressure(sl, e);
                FbUtil.hurt(ctx, e, 4.0F * f, stats, null);
                if (e.isAlive() && f >= 1.8F) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), ctx.caster());
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1), ctx.caster());
                }
                Vec3 c = FbUtil.center(e);
                int pts = (int) (16 * f);
                FbFx.implode(sl, c, FbFx.dust(FbFx.ABYSS, 1.4F), 1.6, pts, 0.25);
                FbFx.implode(sl, c.add(0, 0.5, 0), FbFx.dust(FbFx.ABYSS_DEEP, 1.2F), 1.3, pts / 2, 0.2);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, (int) (12 * f), 0.4, 0.5, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.NAUTILUS, c.x, c.y + 0.5, c.z, 6, 0.3, 0.3, 0.3, 0.3);
                if (f >= 2F) sl.sendParticles(Vfx.crit(), c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.2);
                FbFx.sound(sl, c, SoundEvents.CONDUIT_ATTACK_TARGET, 1.0F, 1.2F - 0.15F * f);
                FbFx.sound(sl, c, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8F, 0.6F);
            }
        }

        /** Multiplicador de pressão do lugar onde a entidade está. */
        static float pressure(ServerLevel sl, LivingEntity e) {
            float f = 1.0F;
            if (e.isInWater()) f += 0.5F;
            if (e.isUnderWater()) f += 0.5F;
            double depth = sl.getChunkSource().getGenerator().getSeaLevel() - e.getY();
            if (depth > 0) f += (float) Math.min(1.0, depth / 64.0);
            if (sl.dimension().location().equals(IsekaiMahou.id("abyss"))) f += 0.5F;
            return Math.min(3.0F, f);
        }
    }

    /**
     * Afogamento Abissal: por 6s o ar do alvo some mesmo fora da água (em uns 2,5s acaba); sem ar ele se afoga,
     * levando 2 (+1 por nível) por segundo. Funciona até em quem respira debaixo d'água.
     */
    public static class AbyssalDrown extends EffectGlyph {
        public AbyssalDrown() {
            super("abyssal_drown", 22);
            element(Element.ABYSS);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                if (FbUtil.untouchable(e)) continue;
                FbUtil.hurt(ctx, e, 1.5F, stats, null);
                if (!e.isAlive()) continue;
                e.setAirSupply(Math.max(0, e.getAirSupply() - 100));
                e.addEffect(new MobEffectInstance(FbRegistries.ABYSSAL_DROWN, stats.duration(120), FbUtil.amp(ctx, stats)), ctx.caster());
                double headY = e.getEyeY();
                sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), headY, e.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
                sl.sendParticles(Vfx.splash(), e.getX(), headY, e.getZ(), 16, 0.3, 0.2, 0.3, 0.1);
                FbFx.ring(sl, new Vec3(e.getX(), headY, e.getZ()), FbFx.dust(FbFx.ABYSS, 1.2F), e.getBbWidth() * 0.6 + 0.3, 14, 0);
                FbFx.sound(sl, e.position(), SoundEvents.DROWNED_AMBIENT_WATER, 1.0F, 0.6F);
                FbFx.sound(sl, e.position(), SoundEvents.PLAYER_HURT_DROWN, 0.8F, 0.8F);
            }
        }
    }

    private AbyssGlyphs() {}
}
