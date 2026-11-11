package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hadō nº 31 — Canhão Rubro (homenagem Shakkahō): uma esfera de Reiatsu vermelho que voa reta e explode no primeiro
 * acerto, aplicando os efeitos em área (raio 2,5; Expandir aumenta). Não quebra blocos. Dividir dispara mais esferas
 * em leque. Pode ser estágio de magia.
 */
public class HadoShakkahoForm extends FormGlyph {
    public HadoShakkahoForm() {
        super("kido_hado_31", 12);
        origin(MagicPath.SHINIGAMI);
        element(Element.FIRE);
        tier(2);
        minCircle(2);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 8;
    }

    @Override
    public int baseCooldown() {
        return 20;
    }

    @Override
    public float hintRange() {
        return 36F;
    }

    @Override
    public float hintRadius() {
        return 2.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float speed = 1.1F * s.speedMult();
        int life = Math.max(10, Math.round(36 / speed));
        Vec3 from = ctx.eyePos().add(0, -0.2, 0);
        Vec3 look = ctx.look();
        int n = s.count();
        for (int i = 0; i < n; i++) {
            Vec3 dir = MoonFangForm.rotateYaw(look, (i - (n - 1) / 2F) * 8F);
            ReishiWaveEntity w = ReishiWaveEntity.spell(ctx, caster, from.add(dir.scale(0.9)), ReishiWaveEntity.CANNON,
                    0.8F + 0.15F * Math.max(0, s.amplify), life, 0xFF3A22);
            w.shoot(dir.x, dir.y, dir.z, speed, 0F);
            level.addFreshEntity(w);
        }
        Vec3 hand = from.add(look.scale(0.9));
        level.sendParticles(ShinigamiFx.KIDO_RED, hand.x, hand.y, hand.z, 14, 0.25, 0.25, 0.25, 0.03);
        level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 6, 0.2, 0.2, 0.2, 0.02);
        ShinigamiFx.sound(level, from, SoundEvents.BLAZE_SHOOT, 0.9F, 0.8F);
        if (!ctx.hasOrigin()) caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }
}
