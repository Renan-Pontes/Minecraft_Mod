package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ConjuredBladeEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Efeito "Campo de Lâminas" (Terra, tier 4): 3 espadas (+1 por Dividir) brotam cravadas no chão em volta do ponto
 * atingido — quem estiver em cima leva 40% do dano e é jogado para cima — e, uma a uma, se arrancam e voam nos
 * inimigos a até 8 blocos (Expandir aumenta), 4,5 de dano cada (perfuram 1 alvo, +1 por Perfurar). Sem inimigos,
 * ficam cravadas e somem. Também funciona ao acertar blocos. Dentro de um Domínio o campo brota em volta de cada
 * inimigo a cada pulso: um cemitério de espadas que não para de atacar.
 */
public class BladeFieldEffect extends EffectGlyph {
    public static final float DAMAGE = 4.5F;
    public static final float BASE_RANGE = 8.0F;

    public BladeFieldEffect() {
        super("blade_field", 26);
        element(Element.EARTH);
        // v1.0: o caminho das lâminas é do Ferreiro (glifo de origem: qualquer elemento; outras classes só por Lição)
        origin(MagicPath.SMITH);
        tier(4);
        minCircle(3);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        field(ctx, target.position(), stats, target);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        field(ctx, hit.getLocation(), stats, null);
    }

    private static void field(SpellContext ctx, Vec3 at, SpellStats stats, @Nullable Entity target) {
        ServerLevel sl = ctx.level();
        Vec3 ground = LegendFx.groundBelow(sl, at, 4.0);
        if (ground == null) ground = at;
        int n = 3 + Math.max(0, stats.split);
        float range = stats.radius(BASE_RANGE);
        int color = LegendFx.colorOf(ctx, Element.EARTH.color);
        int free = ConjuredBladeEntity.MAX_PER_OWNER - ConjuredBladeEntity.countFor(sl, ctx.casterId());
        int spawned = 0;
        double phase = sl.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n + (sl.random.nextDouble() - 0.5) * 0.5;
            double r = 1.3 + sl.random.nextDouble() * 1.1;
            Vec3 guess = ground.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos solid = LegendFx.plantable(sl, BlockPos.containing(guess.x, ground.y - 0.5, guess.z));
            if (solid == null || spawned >= free) continue;
            Vec3 spot = new Vec3(guess.x, solid.getY() + 1.0, guess.z);
            // Cravada levemente inclinada para fora, como num campo de batalha antigo
            Vec3 tilt = new Vec3(-Math.cos(a) * 0.25 + (sl.random.nextDouble() - 0.5) * 0.2, -1,
                    -Math.sin(a) * 0.25 + (sl.random.nextDouble() - 0.5) * 0.2);
            int launchAt = 12 + i * 4 + sl.random.nextInt(3);
            sl.addFreshEntity(ConjuredBladeEntity.sprout(ctx, stats, DAMAGE, spot, ground, range, launchAt,
                    LegendFx.fieldSword(sl.random), color, 1.45F, tilt));
            BlockState st = sl.getBlockState(solid);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), spot.x, spot.y + 0.1, spot.z, 8, 0.2, 0.05, 0.2, 0.1);
            spawned++;
        }
        if (spawned == 0 && target instanceof LivingEntity le && le.isAlive()) {
            // Sem chão para as espadas (ou limite de lâminas): estocada direta de pedra
            ctx.damage(le, DAMAGE, stats, null);
            Vec3 c = le.position();
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.5, c.z, 10, 0.3, 0.4, 0.3, 0.2);
        }
        sl.sendParticles(LegendFx.dust(color, 1.3F), ground.x, ground.y + 0.1, ground.z, 16, 1.4, 0.05, 1.4, 0);
        LegendFx.sound(sl, ground, SoundEvents.STONE_BREAK, 0.9F, 0.7F);
        LegendFx.sound(sl, ground, SoundEvents.CHAIN_BREAK, 0.7F, 0.8F);
    }
}
