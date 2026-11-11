package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellTurretEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Torre: cristal flutuante no ponto mirado (até 24 blocos) que dura 400×duração ticks e, a cada
 * 1,5 s, lança um projétil da magia no inimigo mais próximo (16 blocos). Os aumentos de trajetória
 * (Acelerar, Perfurar, Ricochete, Teleguiado, Dividir...) valem para os projéteis. Como estágio de magia
 * programável, nasce no ponto atingido; cada rajada que acerta pode disparar Ao Acertar.
 */
public class TurretForm extends FormGlyph {
    public TurretForm() {
        super("turret", 25);
        element(Element.ARCANE);
        tier(4);
        minCircle(3);
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 25; }

    @Override
    public int baseCooldown() { return 200; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at = FormsBUtil.aimGround(ctx, 24);
        float yaw = ctx.hasOrigin() ? FormsBUtil.yawOf(ctx.look()) : caster.getYRot();
        SpellTurretEntity turret = new SpellTurretEntity(FormsBRegistry.SPELL_TURRET.get(), level);
        turret.setup(ctx, s.duration(400), 0F);
        turret.moveTo(at.x, at.y + 0.5, at.z, yaw, 0F);
        level.addFreshEntity(turret);
        Vec3 c = turret.muzzle();
        for (Element e : FormsBUtil.spellElements(ctx)) ElementFx.burst(level, c, e, 18, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.2, 0.5, 0.2, 0.05);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
    }
}
