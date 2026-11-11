package com.renanpontes.isekaimahou.feature.forms_b.entity;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Runa: armadilha quase invisível no chão. Arma depois de 1 segundo e dispara quando uma ameaça
 * chega a 1,5 bloco, aplicando os efeitos numa área ({@link #radius()}). Conta como persistente.
 * Magias programáveis: disparar = impacto em quem pisou (Ao Acertar/Saltar); sumir sem disparar = Ao Terminar.
 */
public class SpellRuneEntity extends SpellFormEntity {
    public static final float TRIGGER_RANGE = 1.5F;
    private static final int ARM_TICKS = 20;

    public SpellRuneEntity(EntityType<? extends SpellRuneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isPersistentSpell() { return true; }

    public boolean armed() { return age >= ARM_TICKS; }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (age == ARM_TICKS) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        if (!armed() || age % 2 != 0 || casterAbsent(ctx)) return;
        Vec3 c = position();
        for (LivingEntity e : ctx.enemiesAround(c.add(0, 0.5, 0), TRIGGER_RANGE + 1.0)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            boolean close = dx * dx + dz * dz <= TRIGGER_RANGE * TRIGGER_RANGE && Math.abs(e.getY() - c.y) <= 1.6;
            if (close && FormsBUtil.isThreat(ctx, e)) {
                detonate(level, ctx, e);
                return;
            }
        }
    }

    /** Explode em área; {@code trigger} é quem pisou na runa (o impacto real para Ao Acertar/Saltar). */
    private void detonate(ServerLevel level, SpellContext ctx, LivingEntity trigger) {
        Vec3 c = position().add(0, 0.4, 0);
        float r = radius();
        FormsBUtil.impactOn(ctx, trigger);
        ctx.resolveArea(c, r, this, ctx.formStats().sensitive);
        ElementFx.burst(level, c, element(), 24, 0.4);
        if (accentElement() != element()) ElementFx.burst(level, c, accentElement(), 16, 0.4);
        ElementFx.ring(level, position(), element(), r, Math.max(16, (int) (r * 10)));
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.0F, 1.1F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.3F);
        discard();
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        level.sendParticles(FormsBUtil.dust(color(), 0.6F), getX(), getY() + 0.1, getZ(), 6, 0.4, 0.02, 0.4, 0);
        // Sumiu sem ninguém pisar: Ao Terminar nasce aqui (uma runa que dispara conta como impacto, não como fim)
        stageEnd(ctx, position().add(0, 0.4, 0));
    }

    @Override
    protected void clientTick() {
        // Quase invisível: só um brilho fraco de vez em quando
        if (random.nextInt(FormsBUtil.reducedParticles() ? 40 : 14) != 0) return;
        double a = random.nextDouble() * Math.PI * 2;
        double d = random.nextDouble() * 0.7;
        level().addParticle(FormsBUtil.dust(random.nextInt(3) == 0 ? accentColor() : color(), 0.5F), getX() + Math.cos(a) * d, getY() + 0.05,
                getZ() + Math.sin(a) * d, 0, 0.01, 0);
    }
}
