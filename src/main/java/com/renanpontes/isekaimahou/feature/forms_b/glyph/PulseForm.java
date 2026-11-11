package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellZoneEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pulso: marca o ponto onde você está; dali saem ondas a cada segundo (raio 3,5×área).
 * Como estágio de magia programável, o ponto é o chão onde o estágio anterior acertou/terminou.
 */
public class PulseForm extends FormGlyph {
    public PulseForm() {
        super("pulse", 10);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at = FormsBUtil.stageGround(ctx);
        if (at == null) {
            at = caster.onGround() ? caster.position() : FormsBUtil.groundBelow(level, caster.position(), 8);
            if (at == null) at = caster.position();
        }
        float r = s.radius(3.5F);
        SpellZoneEntity pulse = new SpellZoneEntity(FormsBRegistry.SPELL_ZONE.get(), level);
        pulse.setup(ctx, s.duration(100), r);
        pulse.setVariant(SpellZoneEntity.PULSE);
        pulse.setPos(at.x, at.y + 0.01, at.z);
        level.addFreshEntity(pulse);
        for (Element e : FormsBUtil.spellElements(ctx)) ElementFx.burst(level, at.add(0, 0.4, 0), e, 14, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
    }
}
