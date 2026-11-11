package com.renanpontes.isekaimahou.feature.fusions_d.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.BlackHoleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalLotusEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PrismBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.StarfallStarEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Misturas D de CRISTAL (Terra + Luz) e ASTRAL (Vento + Luz). */
public final class CrystalAstralGlyphs {

    public static void register() {
        GlyphRegistry.register(new CrystalLotus().element(Element.CRYSTAL).tier(3).minCircle(3));
        GlyphRegistry.register(new PrismCannon().element(Element.CRYSTAL).tier(4).minCircle(4));
        GlyphRegistry.register(new BlackHole().element(Element.ASTRAL).tier(4).minCircle(5));
        GlyphRegistry.register(new Starfall().element(Element.ASTRAL).tier(3).minCircle(3));
    }

    /** Lótus de Cristal: flor de cristal que gira disparando estilhaços em espiral (Petrificação acumula). */
    static class CrystalLotus extends FdAreaGlyph {
        CrystalLotus() { super("crystal_lotus", 26, 8); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            CrystalLotusEntity.bloom(ctx, at, stats);
        }
    }

    /** Canhão Prismático: feixe branco que um prisma divide em sete feixes de sete elementos. */
    static class PrismCannon extends EffectGlyph {
        PrismCannon() { super("prism_cannon", 34); }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FdUtil.once(ctx, "prism_cannon") || !FdUtil.ready(ctx, "prism_cannon", 16)) return;
            Vec3 origin = origin(ctx, target.position());
            Vec3 dir = FdUtil.isSelf(ctx, target) ? ctx.look() : FdUtil.center(target).subtract(origin);
            fire(ctx, origin, dir, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FdUtil.once(ctx, "prism_cannon") || !FdUtil.ready(ctx, "prism_cannon", 16)) return;
            Vec3 origin = origin(ctx, hit.getLocation());
            fire(ctx, origin, hit.getLocation().subtract(origin), stats);
        }

        private static Vec3 origin(SpellContext ctx, Vec3 fallback) {
            return ctx.caster() != null || ctx.hasOrigin() ? ctx.eyePos().subtract(0, 0.25, 0) : fallback.add(0, 1.5, 0);
        }

        private static void fire(SpellContext ctx, Vec3 origin, Vec3 dir, SpellStats stats) {
            if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
            PrismBeamEntity.fire(ctx, origin, dir, stats);
        }
    }

    /** Buraco Negro: singularidade que puxa tudo e, com a destruição ligada, devora o terreno. */
    static class BlackHole extends FdAreaGlyph {
        BlackHole() { super("black_hole", 48, 14); }

        /** Zonas/chuvas que reaplicam o efeito não trocam (e explodem) o buraco negro a cada pulso. */
        @Override
        protected int minInterval() { return 40; }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            Vec3 g = FdUtil.ground(ctx.level(), at, 10);
            BlackHoleEntity.open(ctx, g.add(0, 2.6, 0), stats);
        }
    }

    /** Chuva de Estrelas: estrelas caem do céu sobre a área (as primeiras miram inimigos). */
    static class Starfall extends FdAreaGlyph {
        Starfall() { super("starfall", 28, 16); }

        @Override
        protected int minInterval() { return 30; }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = FdUtil.ground(sl, at, 10);
            float r = Math.min(18F, stats.radius(7F));
            int count = Math.min(21, 9 + 3 * Math.max(0, stats.split));
            List<Vec3> aimed = new ArrayList<>();
            for (LivingEntity e : FdUtil.enemies(ctx, c, r)) {
                aimed.add(e.position());
                if (aimed.size() >= count / 3) break;
            }
            for (int i = 0; i < count; i++) {
                Vec3 spot;
                if (i < aimed.size()) spot = aimed.get(i);
                else {
                    double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * r;
                    spot = c.add(Math.cos(a) * d, 4, Math.sin(a) * d);
                }
                Vec3 impact = FdUtil.ground(sl, spot, 12);
                int delay = i * 50 / count;
                int fall = 18 + sl.random.nextInt(8);
                if (delay == 0) StarfallStarEntity.drop(ctx, impact, fall, stats);
                else BossKit.schedule(sl, delay, () -> StarfallStarEntity.drop(ctx, impact, fall, stats));
            }
            FdFx.ring(sl, c.add(0, 0.2, 0), FdFx.fade(FdFx.STAR_WHITE, FdFx.ASTRAL, 1.4F), r, 48, 0);
            FdFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 1.8F);
            FdFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 2F, 0.8F);
        }
    }

    private CrystalAstralGlyphs() {}
}
