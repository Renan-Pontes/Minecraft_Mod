package com.renanpontes.isekaimahou.feature.legends_i.glyph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.legends_i.LiGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;
import com.renanpontes.isekaimahou.feature.legends_i.LiRegistry;
import com.renanpontes.isekaimahou.feature.legends_i.LiUtil;
import com.renanpontes.isekaimahou.feature.legends_i.effect.AmaterasuEffect;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do clã Uchiha (Naruto — Mangekyō Sharingan):
 * <ul>
 *   <li>{@link Amaterasu} — chamas negras que a água não apaga.</li>
 *   <li>{@link Susanoo} — armadura espectral (costelas gigantes) com corte de longo alcance.</li>
 * </ul>
 */
public final class UchihaGlyphs {
    public static final int BLACK_FLAME = 0x22082A;
    public static final int SUSANOO = 0x9A4DFF;

    public static List<EffectGlyph> all() {
        return List.of(new Amaterasu(), new Susanoo());
    }

    // =====================================================================================
    //  Amaterasu
    // =====================================================================================
    /**
     * Chamas negras: 3 de dano de FOGO INFERNAL na hora e o alvo arde por 6 s (Estender +; máx. 20 s; chefes metade) —
     * a cada 0,5 s (0,6 + 0,25 por nível de poder) × escala de dano. NÃO apagam na água nem na chuva. Em você (Si
     * Mesmo): o olhar acende o primeiro inimigo que você encara até 24 blocos. Não põe fogo em blocos.
     */
    public static class Amaterasu extends EffectGlyph {
        public static final float DAMAGE = 3F;
        public static final int TICKS = 120;

        public Amaterasu() {
            super("amaterasu", 24);
            LiGlyphs.config(this, Element.HELLFIRE, 3, 4);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            LivingEntity le;
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "amaterasu")) return;
                le = gaze(ctx, c, 24);
                if (le == null) {
                    LiUtil.sound(ctx.level(), c.position(), SoundEvents.FIRE_EXTINGUISH, 0.5F, 0.6F);
                    return;
                }
            } else if (target instanceof LivingEntity t) {
                le = t;
            } else {
                return;
            }
            ignite(ctx, stats, c, le);
        }

        /** O primeiro inimigo na linha do olhar (para em paredes). */
        static LivingEntity gaze(SpellContext ctx, LivingEntity c, double range) {
            ServerLevel l = ctx.level();
            Vec3 eye = c.getEyePosition();
            Vec3 end = eye.add(c.getLookAngle().scale(range));
            BlockHitResult bh = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> LiUtil.isFoe(ctx, e))) {
                if (e.getBoundingBox().inflate(0.4).clip(eye, end).isEmpty()) continue;
                double d = e.distanceToSqr(eye);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        static void ignite(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity le) {
            ServerLevel l = ctx.level();
            ctx.damage(le, DAMAGE, stats, c, Element.HELLFIRE);
            if (!le.isAlive()) return;
            int ticks = Mth.clamp(stats.duration(TICKS), 40, 400);
            if (LiUtil.boss(le)) ticks /= 2;
            int amp = Mth.clamp(Math.round((LiUtil.power(ctx, stats) - 1F) * 2F), 0, 4);
            AmaterasuEffect.credit(le, c);
            le.addEffect(new MobEffectInstance(LiRegistry.AMATERASU, ticks, amp, false, true, true), c);
            ElementFx.aura(l, le, Element.HELLFIRE, BLACK_FLAME, Math.max(0.8F, le.getBbWidth()), 3, ticks);
            Vec3 tc = LiUtil.center(le);
            ElementFx.sendFar(l, ParticleTypes.SQUID_INK, tc.x, tc.y, tc.z, 18, le.getBbWidth() * 0.5, le.getBbHeight() * 0.4, le.getBbWidth() * 0.5, 0.08);
            ElementFx.sendFar(l, ParticleTypes.LARGE_SMOKE, tc.x, tc.y, tc.z, 8, 0.3, 0.4, 0.3, 0.03);
            ElementFx.flash(l, tc, 0x3A0010, 1.5F);
            LiUtil.sound(l, tc, SoundEvents.FIRECHARGE_USE, 1.0F, 0.5F);
            LiUtil.sound(l, tc, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.6F);
        }
    }

    // =====================================================================================
    //  Susanoo
    // =====================================================================================
    /**
     * Em você ou num aliado: as costelas espectrais do Susanoo se fecham em volta do corpo por 10 s (Estender +; máx.
     * 30 s) — dano recebido −35% (de chefes −20%), +4 de armadura e firmeza contra empurrões. Cada golpe corpo a corpo
     * solta um CORTE ESPECTRAL que alcança 6 blocos à frente (6 de dano × poder, 1 por 0,75 s). Conjurar de novo com o
     * Susanoo ativo dá o golpe da Espada de Totsuka: meia-lua de 9 blocos (14 de dano). Quando acaba, o corpo cobra:
     * Fraqueza por 5 s.
     */
    public static class Susanoo extends EffectGlyph {
        public static final int TICKS = 200;
        public static final float SLASH = 6F;
        public static final float TOTSUKA = 14F;
        /** quem está com Susanoo → poder com que foi conjurado (o corte usa). */
        public static final Map<UUID, Float> POWER = new HashMap<>();

        public Susanoo() {
            super("susanoo", 36);
            LiGlyphs.config(this, Element.SHADOW, 4, 5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public float baseDamage() { return SLASH; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            ServerLevel l = ctx.level();
            float power = LiUtil.power(ctx, stats);
            if (LiUtil.self(ctx, target) && le.hasEffect(LiRegistry.SUSANOO)) {
                // Já está no Susanoo: a Espada de Totsuka
                Vec3 look = le.getLookAngle();
                slash(le, look, 9F, TOTSUKA * power * Progression.DAMAGE_SCALE, 0.5, 14);
                LiUtil.sound(l, le.position(), SoundEvents.WITHER_SHOOT, 0.8F, 0.6F);
                return;
            }
            int ticks = Mth.clamp(stats.duration(TICKS), 60, 600);
            le.addEffect(new MobEffectInstance(LiRegistry.SUSANOO, ticks, 0, false, true, true), ctx.caster());
            POWER.put(le.getUUID(), power);
            LiUtil.state(le, LiNet.S_SUSANOO, ticks);
            ElementFx.pillar(l, le.position(), SUSANOO, 1.6F, 5F, 20);
            ElementFx.shockwave(l, le.position().add(0, 0.1, 0), SUSANOO, 4F, 16, null);
            ElementFx.shake(l, le.position(), 20, 0.6F, 12);
            LiUtil.sound(l, le.position(), SoundEvents.WARDEN_EMERGE, 0.7F, 1.4F);
            LiUtil.sound(l, le.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
        }

        /**
         * Corte espectral na frente de {@code owner}: atinge os inimigos dele num cone ({@code minDot} = cosseno do meio
         * ângulo) até {@code reach} blocos. Dano já escalado (vai direto, sem disparar eventos de magia).
         */
        public static int slash(LivingEntity owner, Vec3 look, float reach, float damage, double minDot, int life) {
            if (!(owner.level() instanceof ServerLevel l)) return 0;
            Vec3 eye = owner.getEyePosition();
            Vec3 flat = new Vec3(look.x, look.y * 0.5, look.z).normalize();
            LiUtil.fx(l, LiNet.SUSANOO_SLASH, eye.add(flat.scale(1.2)).subtract(0, 0.3, 0), flat, SUSANOO, life, reach);
            LiUtil.sound(l, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
            if (LiUtil.calm(owner)) return 0;
            int n = 0;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach),
                    e -> LiUtil.hostile(owner, e) && e.distanceToSqr(eye) <= reach * reach)) {
                Vec3 to = LiUtil.center(e).subtract(eye);
                if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(flat) < minDot) continue;
                e.invulnerableTime = 0;
                e.hurt(l.damageSources().indirectMagic(owner, owner), damage * (LiUtil.boss(e) ? 0.7F : 1F));
                e.knockback(0.5, -flat.x, -flat.z);
                ElementFx.impact(l, LiUtil.center(e), Element.SHADOW, SUSANOO, 2, flat.scale(-1));
                if (++n >= 10) break;
            }
            return n;
        }
    }

    private UchihaGlyphs() {}
}
