package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Doze Badaladas (forma G / ultimate do Pistoleiro — homenagem ao "meio-dia" dos faroestes, com nome e visual
 * próprios). O sino do meio-dia toca três vezes: a cada badalada os inimigos à sua frente (até 32 blocos, ×Expandir)
 * ficam marcados com a mira dourada e lentos; na última, você saca e acerta TODOS de uma vez — cada tiro conta como
 * tiro na cabeça e sai com ×2 de poder. Alvos: 6 (+2 por Dividir, máx. 14). Sem morte instantânea: é dano puro.
 */
public class HighNoonForm extends FormGlyph {
    public static final float RADIUS = 32F;
    public static final int TOLL = 10;
    public static final int TOLLS = 3;

    public HighNoonForm() {
        super("gun_high_noon", 70);
        origin(MagicPath.GUNSLINGER);
        tier(5);
        minCircle(4);
    }

    @Override public boolean isUltimate() { return true; }
    @Override public int baseCooldown() { return 200; }
    @Override public float hintRange() { return RADIUS; }
    @Override public float hintRadius() { return RADIUS; }
    @Override public int hintDuration() { return TOLL * TOLLS; }

    public static int cap(SpellStats s) {
        return Math.min(14, 6 + 2 * Math.max(0, s.split));
    }

    @Override
    public float copies(SpellStats s, int circle) {
        return cap(s);
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float radius = s.radius(RADIUS);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(caster.position(), radius)) {
            Vec3 to = e.getEyePosition().subtract(eye);
            if (to.normalize().dot(look) < 0.15 || !caster.hasLineOfSight(e)) continue;
            targets.add(e);
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        if (targets.size() > cap(s)) targets = new ArrayList<>(targets.subList(0, cap(s)));
        if (targets.isEmpty()) {
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.noon_empty")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            ctx.end(eye, look);
            return;
        }
        final List<LivingEntity> marked = targets;
        float power = GunCombat.formPower(ctx) * 2.0F * GunCombat.headshotMultiplier(caster);
        SpellContext shot = ctx.copy(ctx.powerMult() * power);
        if (caster instanceof ServerPlayer sp) {
            GsNetwork.anim(sp, GsNetwork.NOON, TOLL * TOLLS, ctx.color() & 0xFFFFFF);
            title(sp, Component.translatable("title.isekai_mahou.gunslinger.noon").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("title.isekai_mahou.gunslinger.noon.sub", marked.size()).withStyle(ChatFormatting.YELLOW));
        }
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TOLL * TOLLS, 1, false, false));
        // As badaladas: marca e trava os alvos
        for (int t = 0; t < TOLLS; t++) {
            int tick = t * TOLL;
            float pitch = 0.9F - 0.15F * t;
            SpellScheduler.schedule(Math.max(0, tick), () -> toll(level, caster, marked, pitch));
        }
        // O saque: um tiro por alvo, um tick de diferença entre cada (o estalo vira uma rajada só)
        for (int i = 0; i < marked.size(); i++) {
            LivingEntity target = marked.get(i);
            SpellScheduler.schedule(TOLL * TOLLS + i, () -> fireAt(shot, caster, target));
        }
        SpellScheduler.schedule(TOLL * TOLLS, () -> {
            if (caster instanceof ServerPlayer sp) GsNetwork.anim(sp, GsNetwork.FAN, GunKind.REVOLVER.ordinal(), ctx.color() & 0xFFFFFF);
        });
    }

    private static void toll(ServerLevel level, LivingEntity caster, List<LivingEntity> targets, float pitch) {
        if (caster.isRemoved()) return;
        GsFx.sound(level, caster.position(), SoundEvents.BELL_BLOCK, 1.6F, pitch);
        GsFx.sound(level, caster.position(), SoundEvents.BELL_RESONATE, 0.6F, pitch);
        GsFx.ring(level, caster.position().add(0, 0.1, 0), 1.6, 20, GsFx.dust(0xFFD86B, 1.0F));
        for (LivingEntity e : targets) {
            if (!e.isAlive()) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TOLL + 6, 3, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, TOLL * TOLLS + 10, 0, false, false));
            Vec3 c = e.position().add(0, e.getBbHeight() + 0.6, 0);
            GsFx.ring(level, c, 0.45, 10, GsFx.dust(0xFFC23A, 0.8F));
            level.sendParticles(GsFx.dust(0xFFC23A, 0.8F), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
    }

    private static void fireAt(SpellContext ctx, LivingEntity caster, LivingEntity target) {
        if (caster.isRemoved() || !target.isAlive() || target.level() != caster.level()) return;
        ServerLevel level = ctx.level();
        Vec3 look = target.getEyePosition().subtract(caster.getEyePosition()).normalize();
        Vec3 from = GsFx.muzzle(caster, look, 1);
        Vec3 at = target.getEyePosition();
        GsFx.tracer(level, from, at, ctx.color() & 0xFFFFFF, 1.1F);
        GsFx.shot(level, from, look, ctx.color() & 0xFFFFFF, GunKind.REVOLVER);
        ctx.impact(at, look, target);
        target.invulnerableTime = 0;
        ctx.resolveOnEntity(target, null);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.5);
        if (caster instanceof ServerPlayer sp) GunCombat.onBulletHit(sp, target, true);
    }

    static void title(ServerPlayer sp, Component title, Component sub) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(4, 30, 10));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
