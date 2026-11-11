package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Pele de Rocha (Anão): a pele vira granito — +armadura, +firmeza, −15% de todo dano recebido, um pouco mais lento
 * (efeito {@code race_stone_skin}; Amplificar forte = nível II). Em aliados também. Com Inverter num inimigo: a pele
 * DELE endurece até travar (Petrificação, 2 acúmulos).
 */
public class StoneSkinEffect extends RaceEffectGlyph {
    public static final int TICKS = 240;

    public StoneSkinEffect() {
        super("race_stone_skin", 10, Element.EARTH, 1);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            Statuses.apply(le, Statuses.Kind.PETRIFIED, 2, stats.duration(100), ctx.caster(), stats.power());
            RaceFx.send(level, stone, RaceFx.center(le), 30, 0.35, 0.1);
            RaceFx.sound(level, le.position(), SoundEvents.STONE_PLACE, 1.0F, 0.6F);
            return;
        }
        int amp = stats.power() >= 1.8F ? 1 : 0;
        le.addEffect(new MobEffectInstance(CharacterContent.STONE_SKIN, stats.duration(TICKS), amp, false, true, true), ctx.caster());
        // Casca de pedra se fechando: anéis subindo + lascas
        for (int i = 0; i < 4; i++) {
            Vec3 at = le.position().add(0, 0.25 + i * le.getBbHeight() / 4.0, 0);
            RaceFx.ring(level, at, 0.55, 10, stone);
        }
        RaceFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                RaceFx.center(le), 20, 0.3, 0.05);
        RaceFx.dustBurst(level, RaceFx.center(le), 0xC98A4B, 10, 0.4);
        RaceFx.sound(level, le.position(), SoundEvents.DEEPSLATE_PLACE, 1.0F, 0.7F);
        RaceFx.sound(level, le.position(), SoundEvents.ANVIL_LAND, 0.25F, 1.6F);
    }
}
