package com.renanpontes.isekaimahou.feature.legends_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SkyLancesEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lanças do Céu: 3 (+1 por Dividir) lentes mágicas surgem no céu sobre a área mirada (até 48 blocos). Depois de
 * se formarem, por 3 s, a cada pulso disparam feixes de luz em TODOS os alvos da área — cada feixe aplica a magia
 * com 55% do poder (são vários pulsos). A área aparece marcada no chão enquanto dura.
 * Expandir = área maior · Estender/Persistente = mais tempo · Amplificar = feixes mais grossos.
 */
public class SkyLancesForm extends FormGlyph {
    private static final double RANGE = 48.0;
    /** Poder de cada pulso (a magia acerta várias vezes). */
    public static final float PULSE_POWER = 0.55F;

    public SkyLancesForm() {
        super("sky_lances", 30);
        element(Element.ARCANE);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 15; }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public void cast(SpellContext ctx) {
        if (!ctx.hasOrigin() && !LbUtil.casterReady(ctx)) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 ground = LbUtil.aimGround(ctx, RANGE);
        float area = Mth.clamp(s.radius(6F), 3F, 16F);
        int lenses = Mth.clamp(3 + Math.max(0, s.split), 3, 8);
        int active = Math.min(400, s.duration(60));
        float beam = 0.2F + 0.05F * Math.max(0, s.amplify) + 0.04F * Math.max(0, s.aoe);
        float height = skyHeight(level, ground, 13F + area * 0.25F);

        SkyLancesEntity e = SkyLancesEntity.create(ctx.copy(ctx.powerMult() * PULSE_POWER), ground, height, area, lenses, active, beam);
        level.addFreshEntity(e);

        int rgb = ctx.color() & 0xFFFFFF;
        Telegraph.show(level, Telegraph.Shape.circle(ground, area).warmup(SkyLancesEntity.FORM_TICKS).hold(active)
                .color(rgb).noFlash());
        LbFx.send(level, Vfx.endRod(), ground.add(0, 0.2, 0), 20, area * 0.4, 0.02);
        LbFx.sound(level, ground, SoundEvents.ILLUSIONER_CAST_SPELL, 1.4F, 1.2F);
        if (!ctx.hasOrigin() && ctx.caster() != null) {
            ctx.caster().swing(InteractionHand.MAIN_HAND, true);
            LbFx.line(level, ctx.caster().getEyePosition(), ground.add(0, height, 0), LbFx.dust(rgb, 0.9F), 1.5);
        }
    }

    /** Altura das lentes: céu aberto = {@code want}; embaixo de um teto, logo abaixo dele (mín. 3 blocos). */
    private static float skyHeight(ServerLevel level, Vec3 ground, float want) {
        Vec3 from = ground.add(0, 0.5, 0);
        BlockHitResult up = LbUtil.clip(level, from, from.add(0, want, 0), ClipContext.Fluid.NONE, null);
        if (up.getType() != HitResult.Type.BLOCK) return want;
        return (float) Math.max(3.0, up.getLocation().y - ground.y - 1.2);
    }
}
