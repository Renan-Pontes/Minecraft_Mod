package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunMarks;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos, aumento e gatilho de origem do Pistoleiro — as "munições" que vão nas formas de arma (ou em qualquer
 * outra forma: Projétil + Munição Explosiva também funciona).
 */
public final class GunEffects {

    /** Munição Incendiária (Fogo): dano de fogo, incendeia e acumula Queimando. No bloco: só faíscas (não põe fogo). */
    public static class IncendiaryEffect extends EffectGlyph {
        public IncendiaryEffect() {
            super("gun_incendiary", 10);
            origin(MagicPath.GUNSLINGER);
            element(Element.FIRE);
            tier(2);
        }

        @Override public float baseDamage() { return 4.5F; }
        @Override public int hintDuration() { return 80; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, 4.5F, stats, null, Element.FIRE);
            int ticks = stats.duration(80);
            target.igniteForSeconds(ticks / 20F);
            if (target instanceof LivingEntity le) Statuses.apply(le, Statuses.Kind.BURNING, 1, ticks, ctx.caster(), ctx.powerMult());
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            ctx.level().sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.2, 0.3, 0.2, 0.03);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            Vec3 at = hit.getLocation();
            ctx.level().sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0);
            ctx.level().sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.15, 0.1, 0.15, 0.02);
        }
    }

    /**
     * Munição Explosiva: a bala estoura num raio de 2,5 blocos (×Expandir) — dano em todos os inimigos e um empurrão.
     * Não quebra blocos (as arenas de chefe agradecem).
     */
    public static class ExplosiveEffect extends EffectGlyph {
        public ExplosiveEffect() {
            super("gun_explosive", 16);
            origin(MagicPath.GUNSLINGER);
            tier(3);
        }

        @Override public boolean wantsBlocks() { return true; }
        @Override public float baseDamage() { return 5.0F; }
        @Override public float hintRadius() { return 2.5F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            blast(ctx, target.position().add(0, target.getBbHeight() * 0.5, 0), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            blast(ctx, hit.getLocation(), stats);
        }

        private static void blast(SpellContext ctx, Vec3 at, SpellStats stats) {
            ServerLevel level = ctx.level();
            float r = stats.radius(2.5F);
            for (LivingEntity e : ctx.enemiesAround(at, r)) {
                e.invulnerableTime = 0;
                ctx.damage(e, 5.0F, stats, null);
                Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) {
                    push = push.normalize().scale(0.55);
                    e.push(push.x, 0.25, push.z);
                    e.hurtMarked = true;
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1 + (int) r / 2, r * 0.25, r * 0.2, r * 0.25, 0);
            level.sendParticles(GsFx.dust(ctx.color() & 0xFFFFFF, 1.4F), at.x, at.y, at.z, GsFx.reduced() ? 6 : 16, r * 0.4,
                    r * 0.3, r * 0.4, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, GsFx.reduced() ? 2 : 6, r * 0.3, 0.2, r * 0.3, 0.02);
            GsFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.25F + level.random.nextFloat() * 0.2F);
        }
    }

    /**
     * Marcar Alvo: um tiro leve que marca o alvo (brilha e fica Exposto). Enquanto a marca durar (6 s, ×Estender),
     * as SUAS balas acertam o alvo como tiro certeiro (×1,35, somado ao tiro na cabeça).
     */
    public static class MarkEffect extends EffectGlyph {
        public MarkEffect() {
            super("gun_mark", 6);
            origin(MagicPath.GUNSLINGER);
            tier(1);
        }

        @Override public float baseDamage() { return 1.5F; }
        @Override public int hintDuration() { return 120; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, 1.5F, stats, null);
            int ticks = stats.duration(120);
            GunMarks.mark(target, ctx.caster(), ctx.level().getGameTime(), ticks);
            if (target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), ctx.caster());
                Statuses.apply(le, Statuses.Kind.EXPOSED, 1, ticks, ctx.caster(), ctx.powerMult());
            }
            Vec3 c = target.position().add(0, target.getBbHeight() + 0.5, 0);
            GsFx.ring(ctx.level(), c, 0.5, 12, GsFx.dust(0xFFD86B, 0.8F));
            GsFx.sound(ctx.level(), c, SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.8F);
        }
    }

    /**
     * Leque do Cão (aumento de forma — o "fan the hammer"): cada tiro da forma de arma vira um leque de 1 + 2×N balas,
     * uma a cada 2 ticks. As cópias contam no custo (ver {@code GunForms.copies}).
     */
    public static class FanHammerAugment extends AugmentGlyph {
        public FanHammerAugment() {
            super("gun_fan_hammer", 10, s -> s.custom.merge(GunShots.FAN, 1, Integer::sum));
            origin(MagicPath.GUNSLINGER);
            tier(4);
        }
    }

    /**
     * Gatilho "Ao Recarregar": vai no começo da receita. Sempre que você recarrega uma arma de mana, os slots do loadout
     * ativo com este gatilho são conjurados sozinhos (pagando Foco e respeitando a recarga). A tecla deles não conjura.
     */
    public static class ReloadTrigger extends TriggerGlyph {
        public ReloadTrigger() {
            super("gun_on_reload", 4, true);
            origin(MagicPath.GUNSLINGER);
            tier(2);
        }
    }

    private GunEffects() {}
}
