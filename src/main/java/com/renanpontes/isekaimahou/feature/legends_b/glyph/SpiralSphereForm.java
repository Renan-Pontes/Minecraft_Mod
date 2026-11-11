package com.renanpontes.isekaimahou.feature.legends_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SpiralOrbEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Esfera Espiral: uma esfera de energia girando na mão por até 4 s. Encoste num inimigo (ou golpeie-o) e ela o
 * prende e tritura (vários acertos rápidos com 30% do poder) e explode em vento, aplicando a magia com poder cheio
 * e arremessando-o longe. Como estágio de magia programável, é lançada devagar para frente.
 * Expandir = esfera maior (alcance do toque) · Estender = dura mais na mão · Amplificar = mais acertos e arremesso.
 */
public class SpiralSphereForm extends FormGlyph {
    public SpiralSphereForm() {
        super("spiral_sphere", 18);
        element(Element.ARCANE);
        tier(3);
        minCircle(2);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 40; }

    @Override
    public void cast(SpellContext ctx) {
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float radius = Mth.clamp(s.radius(0.55F), 0.4F, 1.6F);
        int hits = Mth.clamp(5 + 2 * Math.max(0, s.amplify) + Math.max(0, s.charge), 5, 10);
        boolean held = !ctx.hasOrigin();
        LivingEntity caster = ctx.caster();
        if (held) {
            if (!LbUtil.casterReady(ctx)) return;
            SpiralOrbEntity.replaceHeld(caster);
            int life = Math.min(200, s.duration(80));
            SpiralOrbEntity orb = SpiralOrbEntity.create(ctx, SpiralOrbEntity.handPoint(caster, 1F), caster.getLookAngle(),
                    true, radius, life, 0F, 0, hits);
            level.addFreshEntity(orb);
            orb.markHeld();
            caster.swing(InteractionHand.MAIN_HAND, true);
            Vec3 p = orb.position();
            LbFx.send(level, ParticleTypes.CLOUD, p, 8, 0.15, 0.05);
            LbFx.sound(level, p, SoundEvents.BREEZE_CHARGE, 1.0F, 1.4F);
            LbFx.sound(level, p, SoundEvents.BREEZE_WHIRL, 0.8F, 1.8F);
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_b.spiral_ready")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        } else {
            float speed = 0.5F * s.speedMult();
            double range = 16 + 4 * Math.max(0, s.duration);
            SpiralOrbEntity orb = SpiralOrbEntity.create(ctx, ctx.eyePos(), ctx.look(), false, radius,
                    (int) (range / speed) + 10, speed, range, hits);
            level.addFreshEntity(orb);
            LbFx.sound(level, ctx.eyePos(), SoundEvents.BREEZE_SHOOT, 1.0F, 1.3F);
        }
    }
}
