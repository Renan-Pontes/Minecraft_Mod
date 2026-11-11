package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lâmina Espiritual (homenagem ao Seele Schneider): uma lâmina de Reishi se forma na mão e corta em arco (120°) na
 * frente, aplicando os efeitos em até 8 inimigos — com 80% do poder (o Quincy não é um espadachim). Expandir aumenta o
 * alcance (base 3,5). Conta como 1 cópia. Estágio 2+: corta a partir da origem do estágio.
 */
public class SeeleBladeForm extends FormGlyph {
    public static final float WEAKER = 0.8F;

    public SeeleBladeForm() {
        super("quincy_seele_blade", 4);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(2);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public int baseCooldown() {
        return 14;
    }

    @Override
    public float hintRange() {
        return 3.5F;
    }

    @Override
    public float hintRadius() {
        return 3.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        float r = ctx.formStats().radius(3.5F);
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        List<LivingEntity> hits = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(eye, r + 1.0)) {
            if (ctx.ignores(e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            if (toFlat.lengthSqr() > 1.0E-4 && toFlat.normalize().dot(flat) < 0.5) continue; // fora do arco de 120°
            if (to.length() > r + e.getBbWidth()) continue;
            hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        SpellContext weak = ctx.copy(ctx.powerMult() * WEAKER);
        int n = 0;
        for (LivingEntity e : hits) {
            if (n++ >= 8) break;
            weak.impact(e.position().add(0, e.getBbHeight() / 2, 0), look, e);
            weak.resolveOnEntity(e, caster);
        }
        if (caster.level() == level && !ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        // o arco do corte: pó azul da esquerda para a direita
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        for (int i = 0; i <= 14; i++) {
            double a = Math.toRadians(-60 + 120 * i / 14.0);
            Vec3 dir = flat.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            Vec3 p = eye.add(0, -0.35, 0).add(dir.scale(r * 0.85));
            level.sendParticles(i % 2 == 0 ? QuincyFx.WHITE : QuincyFx.REISHI, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Vec3 mid = eye.add(flat.scale(r * 0.6)).add(0, -0.3, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
        QuincyFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.3F);
        QuincyFx.sound(level, eye, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.8F);
        if (hits.isEmpty()) ctx.end(eye.add(flat.scale(r)), look);
    }
}
