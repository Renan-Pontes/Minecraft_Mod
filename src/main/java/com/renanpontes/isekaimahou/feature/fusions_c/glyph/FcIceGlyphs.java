package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionShockwaveEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.IceCoffinEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de GELO das Misturas C: Zero Absoluto (tier 4) e Caixão de Gelo (tier 3). */
public final class FcIceGlyphs {

    public static void register() {
        GlyphRegistry.register(new AbsoluteZero());
        GlyphRegistry.register(new IceCoffin());
    }

    /**
     * Zero Absoluto: o ar para; uma onda de frio absoluto se espalha até 10 blocos. Quem a frente atravessa leva dano 10,
     * fica CONGELADO e Gelado 3 (o fogo apaga). O chão vira gelo e neve: água congela, lava vira obsidiana
     * (Destruction FREEZE, respeita a regra). Em si mesmo: a onda sai de você.
     */
    public static class AbsoluteZero extends EffectGlyph {
        public AbsoluteZero() {
            super("absolute_zero", 40);
            element(Element.ICE);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 g = FcUtil.ground(ctx.level(), target.position(), 8);
            if (FcUtil.isSelf(ctx, target)) {
                if (FcUtil.once(ctx, "absolute_zero")) wave(ctx, stats, g);
                return;
            }
            // Formas de área: UMA onda para o grupo (não uma por alvo)
            if (FcUtil.onceNear(ctx, "absolute_zero", g, stats.radius(10F) * 0.75)) wave(ctx, stats, g);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FcUtil.isSelfFeetEcho(ctx, hit, "absolute_zero")) return;
            Vec3 g = FcUtil.onTop(hit);
            if (FcUtil.onceNear(ctx, "absolute_zero", g, stats.radius(10F) * 0.75)) wave(ctx, stats, g);
        }

        private static void wave(SpellContext ctx, SpellStats stats, Vec3 g) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(10F);
            LivingEntity caster = ctx.caster();
            // Zona/Aura que repete: no máx. 3 ondas de frio vivas por conjurador
            if (FcUtil.owned(sl, com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries.FUSION_SHOCKWAVE.get(), ctx.casterId()) >= 3) return;
            FusionShockwaveEntity.frost(ctx, stats, g.add(0, 0.05, 0), r, 28, 10F, stats.duration(80));
            Destruction.surface(sl, g, r, caster, Destruction.Mode.FREEZE);
            FcFx.implode(sl, g.add(0, 1, 0), FcFx.dust(FcFx.FROST, 1.4F), 3, 24, 0.3);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, g.x, g.y + 1, g.z, 80, 0.5, 0.5, 0.5, 0.5);
            sl.sendParticles(ParticleTypes.FLASH, g.x, g.y + 1, g.z, 1, 0, 0, 0, 0);
            FcFx.sound(sl, g, SoundEvents.AMETHYST_BLOCK_RESONATE, 2F, 0.5F);
            FcFx.sound(sl, g, SoundEvents.GLASS_BREAK, 2F, 0.4F);
            FcFx.sound(sl, g, SoundEvents.PLAYER_HURT_FREEZE, 2F, 0.5F);
            BossKit.shake(sl, g, r + 12, 1.2F, 20);
            FcNet.flash(sl, g, r + 10, 0xCFF4FF, 0.45F, 18);
        }
    }

    /**
     * Caixão de Gelo: um cristal hexagonal de gelo prende o alvo (Congelado, não sai do lugar) por 3 s e então
     * ESTILHAÇA: dano 12 (×1,5 se ele estava Molhado) e estilhaços que ferem e gelam quem está perto. Chefes: 1,5 s.
     * Em si mesmo: prende até 3 inimigos próximos.
     */
    public static class IceCoffin extends EffectGlyph {
        public IceCoffin() {
            super("ice_coffin", 24);
            element(Element.ICE);
            tier(3);
            minCircle(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (FcUtil.isSelf(ctx, target)) {
                if (!FcUtil.once(ctx, "ice_coffin_self")) return;
                for (LivingEntity le : FcUtil.nearestEnemies(ctx, target.position(), stats.radius(6F), 3 + Math.max(0, stats.split))) {
                    IceCoffinEntity.encase(ctx, stats, le, 12F);
                }
                return;
            }
            if (target instanceof LivingEntity le && le.isAlive() && !FcUtil.untouchable(le)) IceCoffinEntity.encase(ctx, stats, le, 12F);
        }
    }

    private FcIceGlyphs() {}
}
