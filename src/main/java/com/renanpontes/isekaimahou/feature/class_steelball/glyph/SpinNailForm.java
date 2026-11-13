package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinNailEntity;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Unha Giratória (forma de origem, tier 1): dispara uma rajada de unhas girando, uma a cada 2 ticks
 * (3 + 1 por Dividir, até 10). Cada unha leva os efeitos com poder menor (55% no Ato 1, +10% por Ato).
 * O comportamento muda com o ATO do Steel Ball (ver {@link SpinNailEntity}): Ato 2 persegue e deixa Girado; Ato 3 permite
 * entrar no buraco (agachado) e reaparecer atrás do alvo. Custo por cópia: cada unha é uma cópia dos efeitos.
 */
public class SpinNailForm extends FormGlyph {
    public static final int MAX_NAILS = 10;

    public SpinNailForm() {
        super(SbGlyphs.SPIN_NAIL, 5);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(1);
    }

    public static int nails(SpellStats s) {
        return Math.min(MAX_NAILS, 3 + Math.max(0, s.split));
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return nails(stats);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 16; }

    @Override
    public float hintRange() { return 40F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        int act = Math.min(3, SbProgress.actOf(caster));
        boolean golden = s.custom(SbGlyphs.GOLDEN) > 0;
        float per = 0.55F + 0.10F * (act - 1);
        SpellContext nailCtx = ctx.copy(ctx.powerMult() * per);
        int n = nails(s);
        float speed = 2.4F * s.speedMult();
        boolean staged = ctx.hasOrigin();
        Vec3 fixedFrom = ctx.eyePos();
        Vec3 fixedLook = ctx.look();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Runnable shot = () -> {
                if (!caster.isAlive() || caster.level() != level) return;
                Vec3 look = staged ? fixedLook : caster.getLookAngle();
                if (!staged && caster instanceof Mob mob && mob.getTarget() != null) {
                    look = mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
                }
                Vec3 from = staged ? fixedFrom : caster.getEyePosition().add(look.scale(0.5)).subtract(0, 0.25, 0);
                // dedos alternados: pequeno leque que fecha no centro
                float yaw = (idx % 2 == 0 ? 1 : -1) * (1.5F + (idx % 3));
                float pitch = s.spread ? (ctx.random().nextFloat() - 0.5F) * 10F : 0F;
                if (s.spread) yaw = (ctx.random().nextFloat() - 0.5F) * 30F;
                Vec3 d = CoreGlyphs.rotate(look, yaw, pitch);
                level.addFreshEntity(SpinNailEntity.spell(nailCtx, from, d, speed, act, golden));
                SbFxNet.at(level, SbFxNet.NAIL_FIRE, from, d, act, golden ? SbFx.GOLD : SbFx.NAIL, caster.getId());
                if (idx == 0 && !staged) caster.swing(InteractionHand.MAIN_HAND, true);
            };
            if (i == 0) shot.run();
            else SpellScheduler.schedule(2 * i, shot);
        }
        SbFx.sound(level, caster.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.4F, 1.7F);
    }
}
