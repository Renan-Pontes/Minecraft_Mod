package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SupernovaEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de SOL das Misturas C: Supernova (tier 4) e Erupção Solar (tier 3). */
public final class FcSunGlyphs {

    public static void register() {
        GlyphRegistry.register(new Supernova());
        GlyphRegistry.register(new SolarFlare());
    }

    /**
     * Supernova: uma estrela em miniatura se forma no ponto (1,7 s — dá para ser contrariada!) e EXPLODE: dano 20 no
     * centro (10 na borda, ×1,5 em mortos-vivos), Em Chamas 3, cegueira em quem olhou, clarão e cratera com o chão
     * queimado em volta. Em si mesmo: a estrela nasce no ponto que você mira.
     */
    public static class Supernova extends EffectGlyph {
        public Supernova() {
            super("supernova", 42);
            element(Element.SUN);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "supernova")) return;
            Vec3 at = FcUtil.isSelf(ctx, target)
                    ? FcUtil.aimPoint(ctx.level(), ctx.eyePos(), ctx.look(), 20, ctx.caster()).add(0, 2.5, 0)
                    : FcUtil.center(target).add(0, 1.5, 0);
            SupernovaEntity.spawn(ctx, stats, at);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "supernova")) return;
            SupernovaEntity.spawn(ctx, stats, FcUtil.onTop(hit).add(0, 2.5, 0));
        }
    }

    /**
     * Erupção Solar: um feixe de plasma solar sai da sua mão por 2,5 s e SEGUE A SUA MIRA (pesado: gira devagar).
     * Queima tudo o que toca (dano 3,2 a cada 0,2 s, Em Chamas; ×1,5 em mortos-vivos) e deixa uma trilha de terra
     * queimada e fogo no chão. Você anda devagar enquanto segura o feixe.
     */
    public static class SolarFlare extends EffectGlyph {
        public SolarFlare() {
            super("solar_flare", 26);
            element(Element.SUN);
            tier(3);
            minCircle(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "solar_flare")) return;
            Vec3 eye = ctx.eyePos();
            fire(ctx, stats, !FcUtil.isSelf(ctx, target) && !ctx.hasOrigin() ? FcUtil.center(target).subtract(eye) : ctx.look());
        }

        /** Projétil/Toque num bloco: o feixe sai de você na direção do ponto atingido (o "eco" do Si Mesmo já foi). */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "solar_flare")) return;
            fire(ctx, stats, ctx.hasOrigin() ? ctx.look() : hit.getLocation().subtract(ctx.eyePos()));
        }

        private static void fire(SpellContext ctx, SpellStats stats, Vec3 dir) {
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            Vec3 eye = ctx.eyePos();
            Vec3 from = eye.add(0, -0.25, 0).add(dir.normalize().scale(0.6));
            FusionBeamEntity.channel(ctx, stats, FusionBeamEntity.Kind.SOLAR_FLARE, Element.SUN, from, dir,
                    24F * (1F + 0.25F * stats.aoe), stats.radius(0.7F), stats.duration(50), 3.2F);
            FcFx.sound(ctx.level(), from, SoundEvents.BEACON_ACTIVATE, 1.6F, 1.4F);
            FcFx.sound(ctx.level(), from, SoundEvents.BLAZE_SHOOT, 1.2F, 0.6F);
        }
    }

    private FcSunGlyphs() {}
}
