package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Bola de Aço (forma de origem, tier 1): arremessa uma bola de aço girando que leva a magia e, depois de acertar,
 * VOLTA para a mão. Com uma Bola de Aço na mão, cada grau dela dá +6% de poder. Dividir = leque de bolas;
 * Perfurar, Ricochete, Teleguiado e Acelerar valem; Retângulo Dourado deixa a bola dourada (+1 alvo atravessado);
 * Energia do Galope montado faz a bola sair 40% mais rápida.
 */
public class SpinBallForm extends FormGlyph {
    public SpinBallForm() {
        super(SbGlyphs.SPIN_BALL, 6);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 14; }

    @Override
    public float hintRange() { return 36F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        boolean golden = s.custom(SbGlyphs.GOLDEN) > 0;
        int held = ctx.hasOrigin() ? -1 : SbGlyphs.heldBallTier(caster);
        if (held > 0) ctx.setPowerMult(ctx.powerMult() * (1F + 0.06F * held));
        Vec3 look = ctx.look();
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) {
            look = mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
        }
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.5)).subtract(0, 0.2, 0);
        float speed = 1.5F * s.speedMult();
        if (s.custom(SbGlyphs.GALLOP) > 0 && SbGlyphs.mounted(caster)) speed *= 1.4F;
        int n = s.count();
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 9.0F;
            float pitch = 0;
            if (s.spread) {
                yaw = (ctx.random().nextFloat() - 0.5F) * 34F;
                pitch = (ctx.random().nextFloat() - 0.5F) * 12F;
            }
            Vec3 d = CoreGlyphs.rotate(look, yaw, pitch);
            level.addFreshEntity(SteelBallEntity.spell(ctx, from, d, speed, golden, Math.max(0, held)));
        }
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        // anéis na mão, Retângulo Dourado e o estalo: SteelBallEntity.spell manda o BALL_THROW para o cliente
        SbFx.sound(level, from, SoundEvents.CHAIN_STEP, 0.6F, 1.9F);
    }
}
