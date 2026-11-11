package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellOrbEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Órbita: 1+Dividir orbes giram em volta de você (raio 2×área) pela duração. Cada orbe aplica os
 * efeitos ao tocar um inimigo e some. Se a magia só cura/protege, os orbes tocam aliados.
 * Conjurar de novo substitui os orbes antigos.
 */
public class OrbitForm extends FormGlyph {
    public OrbitForm() {
        super("orbit", 12);
        element(Element.ARCANE);
        tier(3);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 120; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        for (SpellOrbEntity old : level.getEntitiesOfClass(SpellOrbEntity.class, caster.getBoundingBox().inflate(16),
                o -> caster.getUUID().equals(o.ownerId()))) {
            old.fizzle();
        }
        int n = s.count();
        float r = s.radius(2.0F);
        float speed = 0.12F * s.speedMult();
        boolean support = FormsBUtil.onlyBeneficial(ctx);
        int life = s.duration(300);
        for (int i = 0; i < n; i++) {
            SpellOrbEntity orb = new SpellOrbEntity(FormsBRegistry.SPELL_ORB.get(), level);
            orb.setup(ctx, life, r);
            orb.setOrbit(caster, (float) (Math.PI * 2 * i / n), speed, support);
            level.addFreshEntity(orb);
            ElementFx.burst(level, orb.position(), ctx.element(), 6, 0.1);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.5F);
    }
}
