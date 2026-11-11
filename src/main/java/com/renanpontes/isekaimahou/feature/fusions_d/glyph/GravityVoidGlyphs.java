package com.renanpontes.isekaimahou.feature.fusions_d.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.DimensionCutEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.GravityFieldEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PlanetCoreEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.VoidTearEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Misturas D de GRAVIDADE (Terra + Sombra) e VAZIO (Vento + Sombra). */
public final class GravityVoidGlyphs {

    public static void register() {
        GlyphRegistry.register(new PlanetCrush().element(Element.GRAVITY).tier(4).minCircle(5));
        GlyphRegistry.register(new GravityWell().element(Element.GRAVITY).tier(3).minCircle(3));
        GlyphRegistry.register(new DimensionSlash().element(Element.VOID).tier(4).minCircle(4));
        GlyphRegistry.register(new VoidRift().element(Element.VOID).tier(3).minCircle(4));
    }

    /** Esmagamento Planetário: núcleo que arranca o terreno, prende os inimigos numa esfera de rocha e a faz desabar. */
    static class PlanetCrush extends FdAreaGlyph {
        PlanetCrush() { super("planet_crush", 55, 14); }

        /** A esfera leva ~5 s para se formar e desabar: pulsos de zona não a reiniciam antes de fechar. */
        @Override
        protected int minInterval() { return 60; }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            PlanetCoreEntity.cast(ctx, at, stats);
        }
    }

    /** Poço Gravitacional: campo de gravidade multiplicada que afunda o chão e prega todos nele. */
    static class GravityWell extends FdAreaGlyph {
        GravityWell() { super("gravity_well", 26, 12); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            GravityFieldEntity.open(ctx, at, stats);
        }
    }

    /** Corte Dimensional: rasgo no espaço em linha reta que ignora armadura e corta o terreno. */
    static class DimensionSlash extends EffectGlyph {
        DimensionSlash() { super("dimension_slash", 38); }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FdUtil.once(ctx, "dimension_slash") || !FdUtil.ready(ctx, "dimension_slash", 8)) return;
            Vec3 from = origin(ctx, target.position());
            Vec3 aim = FdUtil.isSelf(ctx, target) ? null : FdUtil.center(target);
            slash(ctx, from, aim, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FdUtil.once(ctx, "dimension_slash") || !FdUtil.ready(ctx, "dimension_slash", 8)) return;
            Vec3 from = origin(ctx, hit.getLocation());
            slash(ctx, from, hit.getLocation(), stats);
        }

        private static Vec3 origin(SpellContext ctx, Vec3 fallback) {
            return ctx.caster() != null || ctx.hasOrigin() ? ctx.eyePos().subtract(0, 0.35, 0) : fallback.add(0, 1, 0);
        }

        /** Corte principal mirando {@code aim} (ou o olhar) + cortes em leque com Dividir. */
        private static void slash(SpellContext ctx, Vec3 from, Vec3 aim, SpellStats stats) {
            Vec3 dir = aim != null ? aim.subtract(from) : ctx.look();
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            dir = dir.normalize();
            double len = Math.min(48, Math.max(22 * (1 + 0.5 * stats.aoe), aim != null ? aim.distanceTo(from) + 6 : 0));
            int n = Math.min(3, 1 + Math.max(0, stats.split));
            for (int i = 0; i < n; i++) {
                double ang = Math.toRadians(i == 0 ? 0 : (i % 2 == 1 ? 14 : -14));
                Vec3 d = new Vec3(dir.x * Math.cos(ang) - dir.z * Math.sin(ang), dir.y, dir.x * Math.sin(ang) + dir.z * Math.cos(ang)).normalize();
                Vec3 a = from.add(d.scale(1.2));
                Vec3 b = from.add(d.scale(len));
                if (i == 0) DimensionCutEntity.slash(ctx, a, b, stats);
                else BossKit.schedule(ctx.level(), 3 * i, () -> DimensionCutEntity.slash(ctx, a, b, stats));
            }
        }
    }

    /** Fenda do Vazio: suga para uma fenda e cospe pela outra, lá no alto. */
    static class VoidRift extends FdAreaGlyph {
        VoidRift() { super("void_rift", 30, 10); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            VoidTearEntity.open(ctx, at, stats);
        }
    }

    private GravityVoidGlyphs() {}
}
