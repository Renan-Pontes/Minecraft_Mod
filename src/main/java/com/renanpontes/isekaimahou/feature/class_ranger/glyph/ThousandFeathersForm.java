package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFxNet;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerRegistries;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerUtil;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.SpiritEagleEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestade das Mil Penas (forma G do Ranger, ultimate): uma Águia Espiritual gigante surge e circula sobre o ponto
 * mirado enquanto 30 + 4·Círculo flechas espectrais desabam numa área de raio 7 (+50% por Expandir) — cada flecha com
 * os efeitos da magia a 35% do poder (cada inimigo leva no máx. 4). No fim a águia MERGULHA no centro: os efeitos
 * acertam todos na metade interna da área com o poder inteiro.
 * <p>Custo por cópia: as flechas contam como uma chuva de forma moderada (1 + √flechas) + 1 do mergulho.
 */
public class ThousandFeathersForm extends FormGlyph {
    public static final double RANGE = 48.0;
    private static final int WINDUP = 24;
    private static final int RAIN_TICKS = 60;
    private static final float ARROW_POWER = 0.35F;

    public ThousandFeathersForm() {
        super(RangerGlyphIds.THOUSAND_FEATHERS, 80);
        origin(MagicPath.RANGER);
        tier(5);
        minCircle(4);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 25; }

    @Override
    public int baseCooldown() { return 400; }

    @Override
    public float hintRange() { return (float) RANGE; }

    @Override
    public float hintRadius() { return 7.0F; }

    @Override
    public int hintDuration() { return WINDUP + RAIN_TICKS + SpiritEagleEntity.DIVE_TICKS; }

    /** Flechas da tempestade para um Círculo. */
    public static int arrows(int circle) {
        return Mth.clamp(30 + 4 * circle, 30, 70);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 2F + (float) Math.sqrt(arrows(circle));
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 center = RangerUtil.aimGround(ctx, RANGE);
        float radius = Mth.clamp(s.radius(7F), 5F, 20F);
        int circle = caster instanceof Player p ? MagicApi.get(p).circle() : SpellCost.MOB_CIRCLE;
        int count = arrows(circle);
        int rgb = ctx.color() & 0xFFFFFF;

        Telegraph.show(level, Telegraph.Shape.circle(center, radius).warmup(WINDUP + RAIN_TICKS).color(rgb).owner(caster));
        SpiritEagleEntity eagle = RangerRegistries.SPIRIT_EAGLE.get().create(level);
        if (eagle != null) {
            eagle.setup(center, radius * 0.8F, WINDUP + RAIN_TICKS, rgb);
            level.addFreshEntity(eagle);
        }
        RangerFxNet.at(level, RangerFxNet.FEATHERS_START, center, Vec3.ZERO, radius, rgb, caster.getId());
        RangerFx.sound(level, center, SoundEvents.PHANTOM_FLAP, 2.0F, 0.5F);
        RangerFx.sound(level, caster.position(), SoundEvents.PARROT_IMITATE_PHANTOM, 2.0F, 0.6F);

        SpellContext arrowsCtx = ctx.copy(ctx.powerMult() * ARROW_POWER);
        RainArrows.Volley volley = new RainArrows.Volley(arrowsCtx, 4, 3,
                (t, arrow) -> {
                    arrowsCtx.resolveOnEntity(t, arrow);
                    ElementFx.burst(level, t.position().add(0, t.getBbHeight() * 0.5, 0), ctx.element(), 4, 0.2);
                },
                (hit, arrow) -> arrowsCtx.resolveOnBlock(hit, arrow));
        double sky = RangerUtil.skyY(level, center, 18);
        double fall = 2.4 * s.speedMult();
        for (int i = 0; i < count; i++) {
            int delay = WINDUP + (i * RAIN_TICKS) / count;
            SpellScheduler.schedule(delay, () -> {
                Vec3 land = RainArrows.randomIn(level, center, radius);
                Vec3 from = new Vec3(land.x + (level.random.nextDouble() - 0.5) * 2, sky, land.z + (level.random.nextDouble() - 0.5) * 2);
                RainArrows.spawn(volley, from, land.subtract(from).normalize().scale(fall));
                if (level.random.nextInt(6) == 0) RangerFx.sound(level, land, SoundEvents.ARROW_SHOOT, 0.7F, 0.5F + level.random.nextFloat() * 0.4F);
            });
        }
        // mergulho final da águia: o poder inteiro no miolo da área
        int diveAt = WINDUP + RAIN_TICKS + SpiritEagleEntity.DIVE_TICKS;
        SpellScheduler.schedule(diveAt, () -> {
            ctx.resolveArea(center.add(0, 0.5, 0), radius * 0.5F, null, true);
            ctx.impact(center.add(0, 0.5, 0), new Vec3(0, -1, 0), null);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
            RangerFxNet.at(level, RangerFxNet.FEATHERS_DIVE, center, Vec3.ZERO, radius, rgb, -1);
            ElementFx.burst(level, center.add(0, 0.5, 0), ctx.element(), 40, radius * 0.35);
            RangerFx.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.8F);
            RangerFx.sound(level, center, SoundEvents.PARROT_IMITATE_PHANTOM, 2.0F, 0.5F);
        });
        SpellScheduler.schedule(diveAt + 6, () -> ctx.end(center.add(0, 0.5, 0), new Vec3(0, -1, 0)));
    }
}
