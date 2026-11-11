package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcTasks;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionShockwaveEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de VAPOR das Misturas C: Explosão de Vapor (tier 3) e Canhão de Vapor (tier 3). */
public final class FcSteamGlyphs {

    public static void register() {
        GlyphRegistry.register(new SteamExplosion());
        GlyphRegistry.register(new SteamCannon());
    }

    /**
     * Explosão de Vapor: um estouro de vapor no alvo (dano 6, deixa Molhado) e, logo depois, TODO inimigo Molhado ou
     * dentro d'água a até 10 blocos DETONA em cadeia — explosão de vapor de dano 8 (×1,5 se estava Molhado), metade
     * nos vizinhos, cegueira e arremesso. Combine com chuva, Água e Abismo.
     */
    public static class SteamExplosion extends EffectGlyph {
        private static final int MAX_DETONATIONS = 12;

        public SteamExplosion() {
            super("steam_explosion", 22);
            element(Element.STEAM);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (FcUtil.isSelf(ctx, target)) {
                if (FcUtil.once(ctx, "steam_explosion")) blast(ctx, stats, target.position());
                return;
            }
            // Formas de área: um estouro (e uma cadeia de detonações) para o grupo, não um por alvo
            Vec3 c = FcUtil.center(target);
            if (FcUtil.onceNear(ctx, "steam_explosion", c, stats.radius(3.5F))) blast(ctx, stats, c);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FcUtil.isSelfFeetEcho(ctx, hit, "steam_explosion")) return;
            Vec3 c = hit.getLocation();
            if (FcUtil.onceNear(ctx, "steam_explosion", c, stats.radius(3.5F))) blast(ctx, stats, c);
        }

        private static void blast(SpellContext ctx, SpellStats stats, Vec3 c) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(3.5F);
            for (LivingEntity le : ctx.enemiesAround(c, r)) {
                le.invulnerableTime = 0;
                ctx.damage(le, 6F, stats, null, Element.STEAM);
                Statuses.apply(le, Statuses.Kind.WET, 1, 160, ctx.caster(), ctx.powerMult());
            }
            FusionShockwaveEntity.visual(sl, c, FusionShockwaveEntity.Kind.STEAM, r, 10);
            sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 40, r * 0.4, r * 0.3, r * 0.4, 0.15);
            sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 30, r * 0.4, 0.4, r * 0.4, 0.2);
            FcFx.sound(sl, c, SoundEvents.LAVA_EXTINGUISH, 2F, 0.5F);
            FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);

            // Detonações em cadeia: cada Molhado vira uma bomba de vapor, uma a cada 3 ticks (do mais perto ao mais longe)
            float range = stats.radius(10F);
            Set<UUID> done = new HashSet<>();
            List<LivingEntity> queue = new ArrayList<>();
            int[] tick = {0};
            FcTasks.later(6, () -> {
                for (LivingEntity le : ctx.enemiesAround(c, range)) {
                    if (Statuses.has(le, Statuses.Kind.WET) || le.isInWaterOrRain()) queue.add(le);
                }
                queue.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
                if (queue.size() > MAX_DETONATIONS) queue.subList(MAX_DETONATIONS, queue.size()).clear();
                FcTasks.add(() -> {
                    if (queue.isEmpty()) return false;
                    if (tick[0]++ % 3 != 0) return true;
                    LivingEntity le = queue.remove(0);
                    if (le.isAlive() && le.level() == sl && done.add(le.getUUID()) && FcUtil.ready(le, "steam_detonate", 10)) {
                        detonate(ctx, stats, sl, le);
                    }
                    return !queue.isEmpty();
                });
            });
        }

        private static void detonate(SpellContext ctx, SpellStats stats, ServerLevel sl, LivingEntity le) {
            boolean wet = Statuses.has(le, Statuses.Kind.WET);
            Statuses.clear(le, Statuses.Kind.WET);
            Vec3 c = FcUtil.center(le);
            float dmg = wet ? 12F : 8F;
            le.invulnerableTime = 0;
            ctx.damage(le, dmg, stats, null, Element.STEAM);
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), ctx.caster());
            FcUtil.knock(le, new Vec3(0, 0.6, 0));
            for (LivingEntity other : ctx.enemiesAround(c, 2.8)) {
                if (other == le) continue;
                other.invulnerableTime = 0;
                ctx.damage(other, dmg * 0.5F, stats, null, Element.STEAM);
                Vec3 out = FcUtil.flat(other.position().subtract(c));
                FcUtil.knock(other, new Vec3(out.x * 0.5, 0.3, out.z * 0.5));
            }
            FusionShockwaveEntity.visual(sl, le.position().add(0, 0.1, 0), FusionShockwaveEntity.Kind.STEAM, 3.2F, 9);
            sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0.25);
            sl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 16, 0.4, 0.4, 0.4, 0.15);
            FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.5F);
            FcFx.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.6F, 0.6F);
            BossKit.shake(sl, c, 10, 0.4F, 6);
        }
    }

    /**
     * Canhão de Vapor: um jato de vapor pressurizado sai da sua mão por 1,5 s e SEGUE A SUA MIRA: empurra longe tudo o que
     * acerta, escalda (dano a cada 0,2 s, Fraqueza), deixa Molhado e apaga o fogo. Agachado, você não recua.
     */
    public static class SteamCannon extends EffectGlyph {
        public SteamCannon() {
            super("steam_cannon", 24);
            element(Element.STEAM);
            tier(3);
            minCircle(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "steam_cannon")) return;
            Vec3 eye = ctx.eyePos();
            fire(ctx, stats, !FcUtil.isSelf(ctx, target) && !ctx.hasOrigin() ? FcUtil.center(target).subtract(eye) : ctx.look());
        }

        /** Projétil/Toque num bloco: o jato sai de você na direção do ponto atingido (o "eco" do Si Mesmo já foi). */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "steam_cannon")) return;
            fire(ctx, stats, ctx.hasOrigin() ? ctx.look() : hit.getLocation().subtract(ctx.eyePos()));
        }

        private static void fire(SpellContext ctx, SpellStats stats, Vec3 dir) {
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            Vec3 eye = ctx.eyePos();
            Vec3 from = eye.add(0, -0.25, 0).add(dir.normalize().scale(0.6));
            FusionBeamEntity.channel(ctx, stats, FusionBeamEntity.Kind.STEAM_JET, Element.STEAM, from, dir,
                    14F * (1F + 0.25F * stats.aoe), stats.radius(1.2F), stats.duration(30), 2.5F);
            FcFx.sound(ctx.level(), from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2F, 0.6F);
        }
    }

    private FcSteamGlyphs() {}
}
