package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.effect.FaEffects;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Pureza (Água + Luz): Cura Maior, Água Benta, Limpeza Total, Bolha Protetora. */
public final class PurityGlyphs {

    public static void register() {
        GlyphRegistry.register(new GreaterHeal());
        GlyphRegistry.register(new HolyWater());
        GlyphRegistry.register(new Cleanse());
        GlyphRegistry.register(new ProtectiveBubble());
    }

    static void holyFx(ServerLevel sl, LivingEntity e) {
        FaUtil.spiral(sl, e, FaUtil.dustFade(0xFFFFFF, 0x9FE0FF, 1.1F), 2, 28);
        FaUtil.spiral(sl, e, Vfx.splash(), 3, 18);
        sl.sendParticles(Vfx.endRod(), e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 6, 0.3, 0.2, 0.3, 0.03);
    }

    /** Cura Maior: cura forte com água luminosa e um pouco de regeneração. Invertido: castiga (em dobro mortos-vivos). */
    public static class GreaterHeal extends EffectGlyph {
        public GreaterHeal() {
            super("greater_heal", 14);
            element(Element.PURITY);
            tier(1);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                FaUtil.hurt(ctx, le, FaUtil.isUndead(le) ? 10.0F : 4.0F, stats, null);
                FaUtil.around(sl, le, Vfx.endRod(), 12, 0.05);
                FaUtil.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F);
                return;
            }
            ctx.heal(le, 10.0F, stats);
            le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(80), 0), ctx.caster());
            le.clearFire();
            holyFx(sl, le);
            FaUtil.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.8F);
            FaUtil.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
        }
    }

    /** Água Benta: respingo sagrado — mortos-vivos queimam, outros inimigos ficam encharcados, aliados em volta são curados. */
    public static class HolyWater extends EffectGlyph {
        public HolyWater() {
            super("holy_water", 12);
            element(Element.PURITY);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            float r = stats.radius(3.0F);
            if (FaUtil.isSelf(ctx, target)) {
                for (LivingEntity e : ctx.enemiesAround(c, r)) smite(ctx, stats, e);
            } else if (target instanceof LivingEntity le) {
                smite(ctx, stats, le);
            }
            // Respingo que cura aliados (uma vez por conjuração em cada tick)
            if (FaUtil.once(ctx, "holy_water_splash")) healAround(ctx, stats, c, r);
            splashFx(ctx.level(), c, r);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            // Caiu no chão: o respingo pega todos em volta (se já respingou num alvo nesta tick, não repete)
            if (!FaUtil.once(ctx, "holy_water_splash")) return;
            Vec3 c = Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            float r = stats.radius(3.0F);
            for (LivingEntity e : ctx.enemiesAround(c, r)) smite(ctx, stats, e);
            healAround(ctx, stats, c, r);
            splashFx(ctx.level(), c, r);
        }

        private void healAround(SpellContext ctx, SpellStats stats, Vec3 c, float r) {
            for (LivingEntity a : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                    a -> a.isAlive() && (a == ctx.caster() || ctx.isAlly(a)) && a.distanceToSqr(c) <= r * r)) {
                ctx.heal(a, 4.0F, stats);
                a.clearFire();
            }
        }

        private static void splashFx(ServerLevel sl, Vec3 c, float r) {
            sl.sendParticles(Vfx.splash(), c.x, c.y, c.z, 40, r * 0.3, 0.3, r * 0.3, 0.2);
            sl.sendParticles(FaUtil.dustFade(0xFFFFFF, 0x9FE0FF, 1.2F), c.x, c.y, c.z, 20, r * 0.3, 0.3, r * 0.3, 0);
            sl.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.06);
            FaUtil.sound(sl, c, SoundEvents.SPLASH_POTION_BREAK, 1.0F, 1.0F);
            FaUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        }

        private void smite(SpellContext ctx, SpellStats stats, LivingEntity e) {
            ServerLevel sl = ctx.level();
            if (FaUtil.isUndead(e)) {
                FaUtil.hurt(ctx, e, 9.0F, stats, null);
                e.igniteForSeconds(3);
                FaUtil.around(sl, e, Vfx.endRod(), 10, 0.05);
                FaUtil.around(sl, e, Vfx.smoke(), 8, 0.02);
            } else {
                FaUtil.hurt(ctx, e, 3.0F, stats, null);
                e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, stats.duration(100), 0), ctx.caster());
            }
        }
    }

    /** Limpeza Total: remove todos os efeitos negativos, fogo e frio. Invertido: arranca as bênçãos do inimigo. */
    public static class Cleanse extends EffectGlyph {
        public Cleanse() {
            super("cleanse", 12);
            element(Element.PURITY);
            tier(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            boolean dispel = stats.inverted && !ctx.isAlly(le);
            MobEffectCategory remove = dispel ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL;
            List<Holder<MobEffect>> list = new ArrayList<>();
            for (MobEffectInstance inst : le.getActiveEffects()) {
                Holder<MobEffect> eff = inst.getEffect();
                // A Exaustão Mágica é o preço do Domínio: não pode ser limpa
                if (eff.value() == CoreRegistries.MAGIC_EXHAUSTION.get()) continue;
                if (eff.value().getCategory() == remove) list.add(eff);
            }
            for (Holder<MobEffect> eff : list) le.removeEffect(eff);
            if (dispel) {
                FaUtil.hurt(ctx, le, 2.0F, stats, null);
                FaUtil.around(sl, le, Vfx.whiteSmoke(), 16, 0.05);
            } else {
                le.clearFire();
                le.setTicksFrozen(0);
                holyFx(sl, le);
                FaUtil.column(sl, le.position(), FaUtil.dust(0xE8F8FF, 1.0F), le.getBbHeight() + 1.5, 6, 0.35, 0);
            }
            FaUtil.sound(sl, le.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.3F);
            FaUtil.sound(sl, le.position(), SoundEvents.PLAYER_SPLASH, 0.6F, 1.5F);
        }
    }

    /** Bolha Protetora: uma bolha de água sagrada absorve muito dano até estourar. Invertido: prende o inimigo numa bolha que flutua. */
    public static class ProtectiveBubble extends EffectGlyph {
        public ProtectiveBubble() {
            super("protective_bubble", 20);
            element(Element.PURITY);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
            double r = Math.max(le.getBbWidth(), le.getBbHeight()) * 0.5 + 0.55;
            if (stats.inverted && !ctx.isAlly(le)) {
                le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, stats.duration(40), 0), ctx.caster());
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(60), 2), ctx.caster());
                FaUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, r, 30);
            } else {
                float pool = 12.0F + 8.0F * FaUtil.power(ctx, stats);
                FaEffects.ProtectiveBubble.setPool(le, Math.max(pool, FaEffects.ProtectiveBubble.pool(le)));
                le.addEffect(new MobEffectInstance(FaRegistries.PROTECTIVE_BUBBLE, stats.duration(400), 0), ctx.caster());
                FaUtil.sphere(sl, c, FaUtil.dustFade(0xE8F8FF, 0x7FD4FF, 1.2F), r, 50);
            }
            FaUtil.sphere(sl, c, Vfx.splash(), r, 20);
            FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 1.2F);
            FaUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        }
    }

    private PurityGlyphs() {}
}
