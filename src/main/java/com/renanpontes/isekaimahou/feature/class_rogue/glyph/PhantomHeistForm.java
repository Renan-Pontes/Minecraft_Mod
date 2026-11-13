package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueSteal;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Golpe do Ladrão Fantasma (forma G / ultimate de origem do Ladino, Sombra — homenagem aos ladrões fantasmas que deixam
 * cartão de visita): você vira sombra (invisível e quase intocável), salta de costas em costas por até 6 inimigos num
 * raio de 14 blocos (Dividir +2 alvos, máx. 12; Expandir aumenta o raio) aplicando os efeitos SEMPRE pelas costas e
 * levando algo do bolso de cada um (furto garantido, uma vez por alvo); no fim volta ao ponto de partida deixando o
 * cartão de visita. Custo por cópia: uma cópia por alvo.
 */
public class PhantomHeistForm extends FormGlyph {
    public static final float RADIUS = 14F;
    public static final int MAX_TARGETS = 12;
    private static final int HOP_TICKS = 5;

    public PhantomHeistForm() {
        super("rogue_phantom_heist", 40);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(4);
        minCircle(6);
    }

    public static int targets(SpellStats s) {
        return Math.min(MAX_TARGETS, 6 + 2 * Math.max(0, s.split));
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 10;
    }

    @Override
    public int baseCooldown() {
        return 80;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return targets(stats);
    }

    @Override
    public float hintRadius() {
        return RADIUS;
    }

    @Override
    public int hintDuration() {
        return 40;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float radius = s.radius(RADIUS);
        List<LivingEntity> list = new ArrayList<>(ctx.enemiesAround(caster.position(), radius));
        list.removeIf(e -> !e.isAlive() || e.isSpectator());
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        int n = Math.min(targets(s), list.size());
        Vec3 start = caster.position();
        if (n == 0) {
            RogueFx.vanish(level, start);
            RogueFx.actionbar(caster, Component.translatable("message.isekai_mahou.class_rogue.heist.nobody"), 0xA89EC0);
            return;
        }
        int total = 8 + HOP_TICKS * n + 6;
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, total, 0, false, false));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, total, 3, false, false));
        RogueFx.callingCard(level, start);
        RogueFx.vanish(level, start);
        RogueFx.sound(level, start, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);
        RogueFx.sound(level, start, SoundEvents.BELL_RESONATE, 0.6F, 1.8F);
        float power = s.power() * ctx.powerMult();
        for (int i = 0; i < n; i++) {
            LivingEntity target = list.get(i);
            SpellScheduler.schedule(8 + HOP_TICKS * i, () -> hop(ctx, level, caster, target, radius, power));
        }
        final int hops = n;
        SpellScheduler.schedule(8 + HOP_TICKS * n + 4, () -> finish(ctx, level, caster, start, hops));
    }

    private static void hop(SpellContext ctx, ServerLevel level, LivingEntity caster, LivingEntity target, float radius, float power) {
        if (!caster.isAlive() || caster.level() != level || !target.isAlive() || target.level() != level) return;
        boolean moved = RogueCombat.teleportBehind(level, caster, target, radius + 16);
        Vec3 c = RogueFx.center(target);
        if (!moved) {
            // sem espaço nas costas: a lâmina fantasma corta à distância
            RogueFx.line(level, RogueFx.center(caster), c, RogueFx.dust(RogueFx.PURPLE, 1.0F), 0.5);
        }
        RogueCombat.forceBack(caster, true);
        try {
            target.invulnerableTime = 0;
            ctx.resolveOnEntity(target, null);
        } finally {
            RogueCombat.forceBack(caster, false);
        }
        if (caster instanceof ServerPlayer sp && target.isAlive()) RogueSteal.steal(sp, target, power, true);
        RogueFx.slash(level, target, RogueFx.PURPLE);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(RogueFx.dust(RogueFx.CARD, 0.9F), c.x, c.y + 0.6, c.z, 6, 0.2, 0.2, 0.2, 0);
        RogueFx.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.5F);
        RogueFx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 0.9F);
    }

    private static void finish(SpellContext ctx, ServerLevel level, LivingEntity caster, Vec3 start, int hops) {
        if (!caster.isAlive() || caster.level() != level) return;
        Vec3 here = caster.position();
        if (here.distanceToSqr(start) < 64 * 64
                && level.noCollision(caster, caster.getBoundingBox().move(start.subtract(here)))) {
            RogueFx.vanish(level, here);
            caster.teleportTo(start.x, start.y, start.z);
            if (caster instanceof ServerPlayer sp) sp.connection.teleport(start.x, start.y, start.z, sp.getYRot(), sp.getXRot());
            caster.resetFallDistance();
        }
        Vec3 at = caster.position();
        RogueFx.callingCard(level, at);
        MahouAnims.play(caster, "taunt");
        RogueFx.sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0F, 1.0F);
        RogueFx.sound(level, at, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.3F);
        ctx.end(at, caster.getLookAngle());
        if (caster instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, "rogue.heist", 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.heist.done", hops).withColor(RogueFx.CARD), true);
        }
    }
}
