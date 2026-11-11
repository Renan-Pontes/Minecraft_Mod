package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.LavaGeyserEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.MiniVolcanoEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de MAGMA das Misturas C: Gêiser de Lava (tier 3) e Vulcão (tier 4). */
public final class FcMagmaGlyphs {

    public static void register() {
        GlyphRegistry.register(new LavaGeyser());
        GlyphRegistry.register(new Volcano());
    }

    /**
     * Gêiser de Lava: o chão sob o alvo racha (círculo de aviso) e uma coluna de lava explode para cima — dano 8,
     * arremesso alto e Em Chamas 2; a coluna jorra por 2 s (quem fica nela é jogado de novo e queima mais) e chamusca o
     * chão. Em si mesmo: gêiseres sob até 3 inimigos próximos.
     */
    public static class LavaGeyser extends EffectGlyph {
        public LavaGeyser() {
            super("lava_geyser", 20);
            element(Element.MAGMA);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (FcUtil.isSelf(ctx, target)) {
                if (!FcUtil.once(ctx, "lava_geyser_self")) return;
                int n = 3 + Math.max(0, stats.split);
                var foes = FcUtil.nearestEnemies(ctx, target.position(), stats.radius(8F), n);
                for (LivingEntity le : foes) {
                    geyser(ctx, stats, FcUtil.ground(ctx.level(), le.position(), 8));
                }
                // Ninguém por perto: o gêiser rompe onde você mira (a mana não se perde)
                if (foes.isEmpty()) geyser(ctx, stats, FcUtil.areaCenter(ctx, target, 16));
                return;
            }
            Vec3 g = FcUtil.ground(ctx.level(), target.position(), 8);
            if (FcUtil.once(ctx, FcUtil.posKey("lava_geyser", g))) geyser(ctx, stats, g);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FcUtil.isSelfFeetEcho(ctx, hit, "lava_geyser_self")) return;
            Vec3 g = FcUtil.onTop(hit);
            if (FcUtil.once(ctx, FcUtil.posKey("lava_geyser", g))) geyser(ctx, stats, g);
        }

        private static void geyser(SpellContext ctx, SpellStats stats, Vec3 ground) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(1.4F);
            Telegraph.show(sl, Telegraph.Shape.circle(ground.add(0, 0.05, 0), r + 0.8F)
                    .warmup(LavaGeyserEntity.WARMUP).color(FcFx.MAGMA).noRunes());
            LavaGeyserEntity.spawn(ctx, stats, ground, r, 8F);
        }
    }

    /**
     * Vulcão: um mini-vulcão rompe o chão e, por 11 s, cospe bombas de lava em arco nos inimigos até 16 blocos — cada
     * bomba é telegrafada onde vai cair (dano 9, Em Chamas 2). Encostar na base queima. No fim a montanha explode
     * (dano 14, Em Chamas 3) e desaba deixando uma cratera. Amplificar/Dividir: bombas mais frequentes.
     */
    public static class Volcano extends EffectGlyph {
        public Volcano() {
            super("volcano", 38);
            element(Element.MAGMA);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "volcano")) return;
            MiniVolcanoEntity.spawn(ctx, stats, FcUtil.areaCenter(ctx, target, 16));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "volcano")) return;
            MiniVolcanoEntity.spawn(ctx, stats, FcUtil.onTop(hit));
        }
    }

    private FcMagmaGlyphs() {}
}
