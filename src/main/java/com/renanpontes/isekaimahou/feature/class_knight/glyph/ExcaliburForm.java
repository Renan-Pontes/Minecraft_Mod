package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * EXCALIBUR (forma G — ultimate, lendária): o Cavaleiro ergue a lâmina, uma coluna de luz desce sobre ela e, num
 * golpe, um RAIO DE LUZ COLOSSAL varre 48 blocos à frente (atravessa paredes, raio 2,6 — Expandir). O feixe pulsa 3
 * vezes (a 0,5 s cada), aplicando os efeitos em tudo dentro dele a cada pulso (3 cópias no custo). Com a
 * <b>Espada do Lago</b> na mão: poder ×1,6, +1 de raio e o feixe fica branco-dourado. Não quebra blocos.
 */
public class ExcaliburForm extends FormGlyph {
    public static final float LENGTH = 48F;
    public static final int PULSES = 3;
    private static final int GOLD_WHITE = 0xFFF4C8;

    public ExcaliburForm() {
        super(KnightGlyphs.EXCALIBUR, 80);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        legendary();
        minCircle(5);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 400; }

    @Override
    public float copies(SpellStats stats, int circle) { return PULSES; }

    @Override
    public float hintRange() { return LENGTH; }

    @Override
    public float hintRadius() { return 2.6F; }

    @Override
    public int hintDuration() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        boolean lake = KnightSwords.legendIn(caster) == LegendSword.LAKE;
        SpellContext c = lake ? ctx.copy(ctx.powerMult() * 1.6F) : ctx;
        float radius = s.radius(2.6F) + (lake ? 1.0F : 0F);
        int rgb = lake && ctx.recipe().color() < 0 ? GOLD_WHITE : ctx.color() & 0xFFFFFF;
        Vec3 dir = ctx.look().normalize();
        Vec3 from = ctx.eyePos().add(0, -0.3, 0).add(dir.scale(1.0));
        Vec3 to = from.add(dir.scale(LENGTH));

        // a lâmina erguida recebe a luz do céu
        Vec3 top = caster.position().add(0, caster.getBbHeight() + 0.8, 0);
        KnightFx.pillar(level, caster.position(), 14, 0.6, rgb);
        level.sendParticles(ParticleTypes.FLASH, top.x, top.y, top.z, 1, 0, 0, 0, 0);
        KnightFx.sound(level, top, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.6F);
        KnightFx.sound(level, top, SoundEvents.BELL_RESONATE, 1.2F, 1.4F);
        caster.swing(InteractionHand.MAIN_HAND, true);

        for (int p = 0; p < PULSES; p++) {
            int pulse = p;
            SpellScheduler.schedule(6 + p * 10, () -> {
                if (caster.isRemoved() || caster.level() != level) return;
                pulse(c, caster, level, from, to, dir, radius, rgb, pulse == 0, pulse == PULSES - 1);
            });
        }
    }

    private static void pulse(SpellContext ctx, LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, Vec3 dir,
                              float radius, int rgb, boolean first, boolean last) {
        // visual: núcleo branco + bainha de luz na cor da magia + anéis ao longo do feixe
        KnightFx.line(level, from, to, 0xFFFFFF, 0.6, 2.4F);
        Vec3 side = KnightFx.side(dir);
        Vec3 up = side.cross(dir).normalize();
        int rings = (int) (LENGTH / 3);
        for (int i = 0; i <= rings; i++) {
            Vec3 c = from.add(dir.scale(i * 3.0));
            for (int k = 0; k < 8; k++) {
                double a = k * Mth.TWO_PI / 8 + i * 0.4;
                Vec3 q = c.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
                level.sendParticles(KnightFx.dust(rgb, 1.8F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 3, radius * 0.3, radius * 0.3, radius * 0.3, 0.05);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        KnightFx.sound(level, from, SoundEvents.LIGHTNING_BOLT_THUNDER, first ? 2.0F : 1.0F, 1.4F);
        KnightFx.sound(level, to, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.1F);
        if (first) KnightFx.sound(level, from, SoundEvents.TOTEM_USE, 0.8F, 1.6F);

        // alvos dentro do cilindro
        AABB box = new AABB(from, to).inflate(radius + 1);
        List<LivingEntity> inBeam = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster && !e.isSpectator())) {
            Vec3 d = KnightFx.center(e).subtract(from);
            double along = d.dot(dir);
            if (along < -1 || along > LENGTH + 1) continue;
            if (d.subtract(dir.scale(along)).length() > radius + e.getBbWidth() * 0.5) continue;
            inBeam.add(e);
        }
        inBeam.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
        boolean any = false;
        for (LivingEntity e : inBeam) {
            if (!any && !ctx.isAlly(e)) {
                ctx.impact(KnightFx.center(e), dir, e);
                any = true;
            }
            e.invulnerableTime = 0;
            ctx.resolveOnEntity(e, caster);
        }
        if (last) ctx.end(to, dir);
    }
}
