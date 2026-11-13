package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbPart;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.InfiniteSpinEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinNailEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Rotação Infinita (forma G de origem, Ato 4, Círculo 8): o Steel Ball concentra o giro perfeito do Retângulo Dourado
 * numa única unha dourada. O alvo atingido fica preso na espiral por 10 s (Estender): não sai do lugar, gira sem
 * parar e recebe o giro + os efeitos da magia a cada 0,5 s. Cada Parte do Cadáver Santo absorvida dá +5% de poder.
 * Custo por cópia: pulsos moderados (1 + √20 ≈ 5,5 cópias com a duração base).
 */
public class InfiniteRotationForm extends FormGlyph {
    public static final int BASE_DURATION = 200;

    public InfiniteRotationForm() {
        super(SbGlyphs.INFINITE, 90);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        legendary();
        minCircle(8);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(BASE_DURATION), InfiniteSpinEntity.PULSE);
    }

    @Override
    public int baseCastTicks() { return 20; }

    @Override
    public int baseCooldown() { return 400; }

    @Override
    public float hintRange() { return 60F; }

    @Override
    public int hintDuration() { return BASE_DURATION; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        if (caster instanceof Player p) {
            int parts = SbPart.count(MagicApi.get(p));
            if (parts > 0) ctx.setPowerMult(ctx.powerMult() * (1F + 0.05F * parts));
        }
        Vec3 look = ctx.look();
        if (caster instanceof Mob mob && mob.getTarget() != null) {
            look = mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
        }
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.6)).subtract(0, 0.2, 0);
        int duration = ctx.formStats().duration(BASE_DURATION);
        level.addFreshEntity(SpinNailEntity.infinite(ctx, from, look, duration));
        // o Retângulo Dourado se forma diante do atirador antes do disparo
        SbFxNet.at(level, SbFxNet.GOLDEN_RECT, from.add(look.scale(1.2)), look, 2.0F, SbFx.GOLD, caster.getId());
        SbFxNet.at(level, SbFxNet.LEARN, caster.position(), look, 1F, SbFx.GOLD, caster.getId());
        SbFxNet.at(level, SbFxNet.NAIL_FIRE, from, look, 4F, SbFx.GOLD, caster.getId());
        SbFx.sound(level, from, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
        SbFx.sound(level, from, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0F, 1.2F);
    }
}
