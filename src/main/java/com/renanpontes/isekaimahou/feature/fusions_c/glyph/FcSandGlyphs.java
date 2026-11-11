package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandCoffinEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandTsunamiEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de AREIA das Misturas C: Caixão de Areia (tier 3) e Tsunami de Areia (tier 4). */
public final class FcSandGlyphs {

    public static void register() {
        GlyphRegistry.register(new SandCoffin());
        GlyphRegistry.register(new SandTsunami());
    }

    /**
     * Caixão de Areia: a areia sobe em espiral e ENVOLVE o alvo, ergue-o do chão e aperta (Enraizado, cego, dano 3/s)
     * por 2,5 s; então o "Funeral do Deserto": o casulo implode esmagando (dano 14, Enrijecendo). Chefes: 1,5 s.
     * Em si mesmo: envolve até 3 inimigos próximos.
     */
    public static class SandCoffin extends EffectGlyph {
        public SandCoffin() {
            super("sand_coffin", 26);
            element(Element.SAND);
            tier(3);
            minCircle(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (FcUtil.isSelf(ctx, target)) {
                if (!FcUtil.once(ctx, "sand_coffin_self")) return;
                for (LivingEntity le : FcUtil.nearestEnemies(ctx, target.position(), stats.radius(6F), 3 + Math.max(0, stats.split))) {
                    SandCoffinEntity.wrap(ctx, stats, le, false, stats.duration(50), 14F);
                }
                return;
            }
            if (target instanceof LivingEntity le && le.isAlive() && !FcUtil.untouchable(le)) {
                SandCoffinEntity.wrap(ctx, stats, le, false, stats.duration(50), 14F);
            }
        }
    }

    /**
     * Tsunami de Areia: uma parede de areia de 9 blocos de largura e 5 de altura se ergue à sua frente e corre ~26 blocos
     * na direção do alvo, acompanhando o relevo: dano 10 em quem alcança, que é ARRASTADO junto; o chão por onde passa é
     * soterrado (vira cascalho/pedregulho e afunda). Quando a onda quebra, os arrastados ficam SOTERRADOS (presos,
     * cegos, sufocando por 3 s).
     */
    public static class SandTsunami extends EffectGlyph {
        public SandTsunami() {
            super("sand_tsunami", 38);
            element(Element.SAND);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "sand_tsunami")) return;
            Vec3 aim = FcUtil.isSelf(ctx, target) || ctx.hasOrigin() ? ctx.eyePos().add(ctx.look().scale(10)) : target.position();
            launch(ctx, stats, aim);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "sand_tsunami")) return;
            launch(ctx, stats, hit.getLocation());
        }

        private static void launch(SpellContext ctx, SpellStats stats, Vec3 aim) {
            Vec3 from = ctx.hasOrigin() || ctx.caster() == null ? ctx.eyePos() : ctx.caster().position();
            Vec3 dir = FcUtil.flat(aim.subtract(from));
            Vec3 start = from.add(dir.scale(1.5));
            SandTsunamiEntity.spawn(ctx, stats, start, dir, stats.radius(9F), 26F * (1F + 0.25F * Math.max(0, stats.duration)));
        }
    }

    private FcSandGlyphs() {}
}
