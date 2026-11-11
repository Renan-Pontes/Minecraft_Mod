package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellWaveEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Onda: lança uma {@link SpellWaveEntity} que corre pelo chão na direção do olhar (40 ticks, 0.55 bloco/tick).
 * Acelerar/Retardar = velocidade; Expandir = mais larga; Estender = dura mais; Dividir = ondas em leque (20°);
 * Espalhar = direções aleatórias; Teleguiado = curva para inimigos; Fantasma = atravessa paredes.
 * <p>
 * Estágios (v0.7): nasce no ponto de origem do estágio (descido até o chão) e corre na direção horizontal dele —
 * "queda → Ao Terminar → onda" espalha a onda a partir da cratera. A onda avisa {@code impact} em cada criatura que
 * toca e na parede onde quebra, e {@code end} quando se desfaz.
 */
public class WaveForm extends FormGlyph {
    private static final double FAN_DEGREES = 20.0;

    public WaveForm() {
        super("wave", 10);
        element(Element.ARCANE);
        tier(3);
        // v0.7: liberada já no 1º Círculo (o nível de desbloqueio vem do tier)
        minCircle(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 6; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        Vec3 base = FormAim.horizontalAim(ctx);

        // Pé de onde a onda sai: o conjurador, ou (num estágio) o ponto de origem descido até o chão
        Vec3 feet;
        if (ctx.hasOrigin()) {
            Vec3 o = ctx.eyePos();
            double g = FormAim.groundHeight(ctx.level(), o.x, o.y, o.z, 0.6, 4.0);
            feet = new Vec3(o.x, Double.isNaN(g) ? o.y : g, o.z);
        } else {
            feet = ctx.caster().position();
        }
        // Do conjurador sai um pouco à frente (para não nascer dentro dele); de um ponto de impacto, quase dele mesmo
        double ahead = ctx.hasOrigin() ? 0.3 : 0.8;

        int n = s.count();
        for (int i = 0; i < n; i++) {
            double yaw = n == 1 ? 0 : (i - (n - 1) / 2.0) * FAN_DEGREES;
            if (s.spread) yaw = (ctx.random().nextDouble() - 0.5) * 70.0;
            Vec3 dir = base.yRot((float) Math.toRadians(yaw));
            Vec3 start = feet.add(dir.scale(ahead));
            double g = FormAim.groundHeight(ctx.level(), start.x, feet.y, start.z, 1.1, 3.0);
            double y = Double.isNaN(g) || g > feet.y + 1.1 ? feet.y : g;
            ctx.level().addFreshEntity(SpellWaveEntity.create(ctx, new Vec3(start.x, y, start.z), dir));
        }

        // "Pisão" que solta a onda: anel no chão + terra levantada
        FormsFx.ring(ctx, feet.add(0, 0.1, 0), 1.2, 4.0);
        BlockState ground = ctx.level().getBlockState(BlockPos.containing(feet.x, feet.y - 0.2, feet.z));
        if (!ground.isAir() && ground.getRenderShape() != RenderShape.INVISIBLE) {
            FormsFx.send(ctx.level(), new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.add(0, 0.2, 0), 24, 0.6, 0.15);
        }
        FormsFx.sound(ctx, feet, SoundEvents.GENERIC_EXPLODE, 0.35F, 1.8F);
        FormsFx.sound(ctx, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.55F);
    }
}
