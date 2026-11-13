package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellIllusionEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ilusão: cria 1+Dividir clones sombrios à sua frente por 200×duração ticks. Eles atraem os
 * monstros em 16 blocos e, ao sumir ou morrer, explodem aplicando os efeitos (raio 3×área).
 * Como estágio de magia programável, os clones surgem no ponto atingido, virados para a direção do estágio.
 */
public class IllusionForm extends FormGlyph {
    public IllusionForm() {
        super("illusion", 20);
        element(Element.ARCANE);
        tier(4);
        minCircle(3);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 240; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 stagePoint = FormsBUtil.stageGround(ctx);
        // Conjuração normal: à frente do conjurador, virados como ele. Estágio: no ponto, virados para onde o estágio ia.
        float yaw = stagePoint != null ? FormsBUtil.yawOf(ctx.look()) : caster.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 base = stagePoint != null ? stagePoint : caster.position().add(fwd.scale(1.5));
        int n = s.count();
        int rgb = ctx.color() & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            double lateral = (i - (n - 1) / 2.0) * 1.3;
            Vec3 want = base.add(side.scale(lateral));
            Vec3 pos = FormsBUtil.groundBelow(level, want.add(0, 1.5, 0), 5);
            if (pos == null) pos = stagePoint != null ? stagePoint : caster.position();
            SpellIllusionEntity clone = new SpellIllusionEntity(FormsBRegistry.SPELL_ILLUSION.get(), level);
            clone.setup(ctx, s.duration(200), 20.0F * s.power());
            clone.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
            clone.setYHeadRot(stagePoint != null ? yaw : caster.getYHeadRot());
            clone.setYBodyRot(yaw);
            level.addFreshEntity(clone);
            level.sendParticles(Vfx.largeSmoke(), pos.x, pos.y + 1, pos.z, 14, 0.3, 0.6, 0.3, 0.02);
            level.sendParticles(FormsBUtil.dust(rgb, 1.2F), pos.x, pos.y + 1, pos.z, 16, 0.3, 0.7, 0.3, 0);
        }
        level.playSound(null, base.x, base.y, base.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
