package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcTasks;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.PollenCloudEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.WorldRootEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de NATUREZA das Misturas C: Raízes do Mundo (tier 4) e Tempestade de Pólen (tier 3). */
public final class FcNatureGlyphs {

    public static void register() {
        GlyphRegistry.register(new WorldRoots());
        GlyphRegistry.register(new PollenStorm());
    }

    /**
     * Raízes do Mundo: o chão treme e racha num raio de 6; meio segundo depois RAÍZES GIGANTES rompem o terreno sob
     * cada inimigo (até 8): arremessam (dano 8), se enrolam neles (Enraizado, aperto de 2,5/s, presos no alto por 3 s)
     * e no fim desabam esmagando (dano 6). Racham o chão por onde saem. Em si mesmo: as raízes brotam em volta de você.
     */
    public static class WorldRoots extends EffectGlyph {
        private static final int WARN = 12;

        public WorldRoots() {
            super("world_roots", 36);
            element(Element.NATURE);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 g = FcUtil.ground(ctx.level(), target.position(), 8);
            if (FcUtil.isSelf(ctx, target)) {
                if (FcUtil.once(ctx, "world_roots")) erupt(ctx, stats, g);
                return;
            }
            // Formas de área: uma erupção para o grupo (ela já pega até 8 inimigos no raio)
            if (FcUtil.onceNear(ctx, "world_roots", g, stats.radius(6F))) erupt(ctx, stats, g);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FcUtil.isSelfFeetEcho(ctx, hit, "world_roots")) return;
            Vec3 g = FcUtil.onTop(hit);
            if (FcUtil.onceNear(ctx, "world_roots", g, stats.radius(6F))) erupt(ctx, stats, g);
        }

        private static void erupt(SpellContext ctx, SpellStats stats, Vec3 g) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(6F);
            Telegraph.show(sl, Telegraph.Shape.circle(g.add(0, 0.05, 0), r).warmup(WARN).color(FcFx.NATURE));
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                    g.x, g.y + 0.1, g.z, 40, r * 0.5, 0.05, r * 0.5, 0.1);
            FcFx.sound(sl, g, SoundEvents.ROOTED_DIRT_BREAK, 2F, 0.4F);
            BossKit.shake(sl, g, r + 14, 0.8F, WARN + 10);
            int maxTargets = 8 + Math.max(0, stats.split) * 2;
            int hold = stats.duration(60);
            FcTasks.later(WARN, () -> {
                List<LivingEntity> foes = FcUtil.nearestEnemies(ctx, g, r, maxTargets);
                for (LivingEntity le : foes) {
                    WorldRootEntity.sprout(ctx, stats, FcUtil.ground(sl, le.position(), 6), le, hold, 8F);
                }
                // Algumas raízes extras rasgando a área (sem alvo) dão a escala do "mundo" se mexendo
                int extra = Math.max(2, 5 - foes.size());
                for (int i = 0; i < extra; i++) {
                    double a = sl.random.nextDouble() * Math.PI * 2, d = r * (0.4 + 0.6 * sl.random.nextDouble());
                    WorldRootEntity.sprout(ctx, stats, FcUtil.ground(sl, g.add(Math.cos(a) * d, 3, Math.sin(a) * d), 8), null, 0, 6F);
                }
                FcFx.sound(sl, g, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2F, 0.4F);
            });
        }
    }

    /**
     * Tempestade de Pólen: uma nuvem rodopiante de pólen e pétalas (raio 5) por 8 s. Inimigos dentro: Veneno e
     * Sonolência crescente — no nível III ADORMECEM. Aliados dentro: regeneração leve. O pólen é inflamável: um inimigo
     * Em Chamas (ou fogo) na nuvem faz tudo EXPLODIR (dano 12, Em Chamas 2).
     */
    public static class PollenStorm extends EffectGlyph {
        public PollenStorm() {
            super("pollen_storm", 20);
            element(Element.NATURE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "pollen_storm")) return;
            Vec3 c = FcUtil.isSelf(ctx, target) ? target.position() : FcUtil.ground(ctx.level(), target.position(), 8);
            PollenCloudEntity.spawn(ctx, stats, c);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "pollen_storm")) return;
            PollenCloudEntity.spawn(ctx, stats, FcUtil.onTop(hit));
        }
    }

    private FcNatureGlyphs() {}
}
