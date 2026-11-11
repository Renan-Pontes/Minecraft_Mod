package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.feature.class_smith.entity.BladeWorldEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Forma G "Mundo das Lâminas" (homenagem ao Campo de Lâminas Infinitas — "Eu sou o osso da minha espada"; origem do
 * Ferreiro, Círculo 8, ultimate): o Ferreiro recita o cântico e um anel de fogo corre pelo chão até 14 blocos
 * (Expandir +). Quando fecha, o mundo interior se sobrepõe à realidade por 10 s (Estender +): céu de cinzas,
 * engrenagens no alto e um campo de espadas cravadas. A cada 0,5 s até 6 espadas se arrancam do chão (ou caem do céu)
 * e voam nos inimigos lá dentro, cada uma aplicando os efeitos; quem tenta sair é empurrado de volta pela borda.
 * Custo por cópia: pulsos moderados (1 + √rajadas). Uma por conjurador.
 */
public class BladeWorldForm extends FormGlyph {
    public static final float RADIUS = 14.0F;
    public static final int DURATION = 200;

    public BladeWorldForm() {
        super("smith_blade_world", 90);
        origin(MagicPath.SMITH);
        tier(5);
        minCircle(8);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public int baseCastTicks() { return 40; }

    @Override
    public int baseCooldown() { return 1200; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), BladeWorldEntity.VOLLEY);
    }

    @Override
    public float hintRadius() { return RADIUS; }

    @Override
    public int hintDuration() { return DURATION; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        // um mundo por conjurador: o antigo se desfaz
        sl.getEntities(SmithRegistries.BLADE_WORLD.get(), w -> w.isAlive() && w.distanceToSqr(caster) < 128 * 128).forEach(w -> {
            if (w.ownedBy(ctx)) w.discard();
        });
        SpellStats s = ctx.formStats();
        float r = s.radius(RADIUS);
        Vec3 center = SmithFx.ground(sl, caster.position().add(0, 0.5, 0), 4);
        int color = ctx.recipe().color() >= 0 && ctx.recipe().color() != 0xFFFFFF ? ctx.recipe().color() : 0xE8A04A;
        sl.addFreshEntity(BladeWorldEntity.create(sl, ctx, center, r, s.duration(DURATION), color));
        sl.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.2, center.z, 30, 0.6, 0.1, 0.6, 0.05);
        SmithFx.sound(sl, center, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
        SmithFx.sound(sl, center, SoundEvents.ANVIL_PLACE, 1.0F, 0.6F);
    }
}
