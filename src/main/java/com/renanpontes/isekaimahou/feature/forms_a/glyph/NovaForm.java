package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Nova: explosão em volta do conjurador, raio 4 (× área). Aplica em todos no raio (o conjurador fica de fora,
 * a não ser que o efeito seja benéfico e o alvo seja aliado — as regras normais de alvo).
 * Sensível = também aplica no bloco onde você pisa. Visual: anel de choque que se expande pelo chão.
 * <p>
 * Estágios (v0.7): explode no ponto de impacto que gerou o estágio ({@code ctx.anchor()}, descido até o chão se ele
 * estiver perto; no ar, explode no ar). Com Saltar, cai no próximo inimigo. Avisa {@code impact} em cada criatura
 * atingida (a mais perto do centro primeiro, na direção de dentro para fora) e {@code end} no centro.
 */
public class NovaForm extends FormGlyph {
    private static final float BASE_RADIUS = 4.0F;
    private static final int WAVE_TICKS = 5;
    /** Até quantos blocos abaixo do ponto de impacto a nova procura o chão para "pousar". */
    private static final double GROUND_SNAP = 3.0;

    public NovaForm() {
        super("nova", 8);
        element(Element.ARCANE);
        tier(2);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 5; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        LivingEntity caster = ctx.caster();
        SpellStats s = ctx.formStats();
        float radius = s.radius(BASE_RADIUS);

        // Centro: o ponto de impacto (estágio), a origem (castFrom/torres) ou o conjurador
        Vec3 base = ctx.anchor() != null ? ctx.anchor() : ctx.hasOrigin() ? ctx.eyePos() : null;
        Vec3 feet;
        Vec3 center;
        if (base == null) {
            feet = caster.position();
            center = feet.add(0, caster.getBbHeight() * 0.5, 0);
        } else {
            Vec3 ground = FormAim.findGround(ctx, FormAim.anchorProbe(ctx, base), GROUND_SNAP);
            feet = ground != null ? ground : base.subtract(0, 0.9, 0);
            center = ground != null ? ground.add(0, 0.9, 0) : base;
        }

        // Impactos reais: cada criatura que a explosão pega, da mais perto do centro para a mais longe
        List<LivingEntity> caught = new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= radius * radius && FormAim.affects(ctx, e)));
        caught.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        for (LivingEntity e : caught) {
            Vec3 c = FormAim.center(e);
            Vec3 out = c.subtract(center);
            ctx.impact(c, out.lengthSqr() > 1.0E-3 ? out.normalize() : ctx.look(), e);
        }
        ctx.resolveArea(center, radius, null, s.sensitive);

        // Clarão no centro + anel que cresce em alguns ticks
        FormsFx.burst(ctx, center, 30, 0.6);
        int tier = SpellVisuals.tier(ctx);
        ElementFx.shockwave(ctx.level(), feet.add(0, 0.12, 0), ctx.color(), radius * 1.15F, 8 + WAVE_TICKS + tier, null);
        ElementFx.runeCircle(ctx.level(), feet.add(0, 0.04, 0), ctx.color(), radius * 0.85F, 18 + 4 * tier, tier);
        if (tier >= 3) ElementFx.pillar(ctx.level(), feet, ctx.color(), 0.5F + 0.2F * tier, 3.0F + tier, 12);
        FormsFx.send(ctx.level(), ParticleTypes.EXPLOSION, center, 1, 0, 0);
        FormsFx.send(ctx.level(), ParticleTypes.FLASH, center, 1, 0, 0);
        shock(ctx, feet, radius, 0);
        for (int t = 1; t < WAVE_TICKS; t++) {
            final int tick = t;
            SpellScheduler.schedule(t, () -> shock(ctx, feet, radius, tick));
        }
        FormsFx.sound(ctx, center, SoundEvents.GENERIC_EXPLODE, 0.7F, 1.35F);
        FormsFx.sound(ctx, center, SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 0.8F);
        // A explosão é instantânea: o estágio termina no centro (Ao Terminar nasce aqui)
        ctx.end(center, ctx.look());
    }

    /** Um quadro do anel de choque: raio cresce até o raio final; o último quadro solta jatos para fora. */
    private static void shock(SpellContext ctx, Vec3 feet, float radius, int tick) {
        double r = radius * (tick + 1) / (double) WAVE_TICKS;
        FormsFx.ring(ctx, feet.add(0, 0.15, 0), r, 3.0);
        FormsFx.ring(ctx, feet.add(0, 0.9, 0), r * 0.85, 1.5);
        if (tick == WAVE_TICKS - 1) {
            int jets = Math.max(10, (int) (radius * 5));
            for (int i = 0; i < jets; i++) {
                double a = Math.PI * 2 * i / jets;
                Vec3 dir = new Vec3(Math.cos(a), 0.15, Math.sin(a));
                FormsFx.moving(ctx.level(), FormsFx.jet(ctx.element()), feet.add(dir.scale(r * 0.8)).add(0, 0.3, 0), dir, 0.2);
            }
        }
    }
}
