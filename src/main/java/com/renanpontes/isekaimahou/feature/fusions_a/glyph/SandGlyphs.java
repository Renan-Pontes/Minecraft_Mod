package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaShapes;
import com.renanpontes.isekaimahou.feature.fusions_a.FaTempBlocks;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.effect.FaEffects;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Areia (Terra + Vento): Duna, Vitrificar, Tempestade de Areia, Areia Movediça Maior, Miragem. */
public final class SandGlyphs {

    public static void register() {
        GlyphRegistry.register(new Dune());
        GlyphRegistry.register(new Vitrify());
        GlyphRegistry.register(new Sandstorm());
        GlyphRegistry.register(new Sinkhole());
        GlyphRegistry.register(new Mirage());
    }

    static final BlockParticleOption SAND_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    static final BlockParticleOption SAND_PILLAR = new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.SAND.defaultBlockState());

    /** Duna: uma onda de areia corre até o alvo, derruba e arrasta quem estiver no caminho. */
    public static class Dune extends EffectGlyph {
        public Dune() {
            super("dune", 10);
            element(Element.SAND);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 from = ctx.caster() != null ? ctx.caster().position() : target.position().subtract(ctx.look().scale(4));
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 4.0)) {
                // Onda visual do conjurador até o alvo
                Vec3 to = t.position();
                Vec3 d = to.subtract(from);
                int steps = Math.max(1, (int) (d.length() / 1.2));
                for (int i = 1; i <= steps; i++) {
                    Vec3 p = from.add(d.scale(i / (double) steps));
                    SpellScheduler.schedule(Math.max(1, i), () -> {
                        sl.sendParticles(SAND_PILLAR, p.x, p.y + 0.1, p.z, 6, 0.4, 0.05, 0.4, 0.15);
                        sl.sendParticles(FaUtil.dust(0xE3C77A, 1.5F), p.x, p.y + 0.4, p.z, 4, 0.4, 0.3, 0.4, 0);
                    });
                }
                int delay = Math.max(1, steps);
                SpellScheduler.schedule(delay, () -> crash(ctx, stats, t, from));
            }
        }

        private void crash(SpellContext ctx, SpellStats stats, LivingEntity t, Vec3 from) {
            if (!t.isAlive()) return;
            ServerLevel sl = ctx.level();
            Vec3 dirRaw = t.position().subtract(from);
            Vec3 dir = new Vec3(dirRaw.x, 0, dirRaw.z).lengthSqr() < 1.0E-4 ? FaUtil.flatLook(ctx) : new Vec3(dirRaw.x, 0, dirRaw.z).normalize();
            float p = Math.min(stats.power(), 2.5F);
            for (LivingEntity e : ctx.enemiesAround(t.position(), 2.2)) {
                boolean main = e == t;
                FaUtil.hurt(ctx, e, main ? 4.0F : 2.5F, stats, null);
                double k = (main ? 1.1 : 0.8) * p * FaUtil.kbFactor(e);
                FaUtil.addMotion(e, dir.scale(k).add(0, 0.4 * k, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(40), 0), ctx.caster());
            }
            Vec3 c = t.position();
            sl.sendParticles(SAND_PILLAR, c.x, c.y + 0.1, c.z, 30, 0.8, 0.1, 0.8, 0.3);
            sl.sendParticles(SAND_BITS, c.x, c.y + 0.8, c.z, 25, 0.6, 0.6, 0.6, 0.15);
            FaUtil.sound(sl, c, SoundEvents.SAND_BREAK, 1.5F, 0.6F);
            FaUtil.sound(sl, c, SoundEvents.BREEZE_SHOOT, 0.7F, 0.6F);
        }
    }

    /** Vitrificar: calor intenso — areia vira vidro e o alvo fica preso numa casca de vidro. */
    public static class Vitrify extends EffectGlyph {
        public Vitrify() {
            super("vitrify", 12);
            element(Element.SAND);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int ticks = stats.duration(50);
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 4.0F, stats, null);
                t.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, ticks, 0), ctx.caster());
                FaShapes.cage(ctx, t, FaRegistries.GLASS_PRISON.get().defaultBlockState(), ticks);
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                sl.sendParticles(Vfx.flame(), c.x, c.y, c.z, 10, 0.4, 0.5, 0.4, 0.02);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), c.x, c.y, c.z,
                        16, 0.5, 0.6, 0.5, 0.1);
                FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 1.2F, 0.7F);
                FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            BlockPos center = hit.getBlockPos();
            if (!FaUtil.once(ctx, "vitrify@" + center.asLong())) return;
            float r = stats.radius(2.0F);
            int ri = Mth.ceil(r);
            int n = 0;
            for (BlockPos mp : BlockPos.betweenClosed(center.offset(-ri, -ri, -ri), center.offset(ri, ri, ri))) {
                if (mp.distSqr(center) > r * r + 0.5) continue;
                BlockPos p = mp.immutable();
                BlockState s = sl.getBlockState(p);
                Block glass;
                if (s.is(Blocks.SAND)) glass = Blocks.GLASS;
                else if (s.is(Blocks.RED_SAND)) glass = Blocks.ORANGE_STAINED_GLASS;
                else continue;
                if (FaTempBlocks.isTracked(sl, p) || !FaUtil.canBreak(ctx, p)) continue;
                sl.setBlock(p, glass.defaultBlockState(), Block.UPDATE_ALL);
                sl.sendParticles(Vfx.flame(), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
                n++;
            }
            Vec3 c = Vec3.atCenterOf(center).add(0, 0.6, 0);
            sl.sendParticles(Vfx.flame(), c.x, c.y, c.z, 12, r * 0.4, 0.2, r * 0.4, 0.02);
            if (n > 0) {
                FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 1.2F, 0.8F);
                FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.2F);
            }
        }
    }

    /** Tempestade de Areia: redemoinho de areia que cega, deixa lento, arrasta e lixa quem estiver dentro. */
    public static class Sandstorm extends EffectGlyph {
        public Sandstorm() {
            super("sandstorm", 20);
            element(Element.SAND);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            storm(ctx, stats, target.position());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            storm(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private void storm(SpellContext ctx, SpellStats stats, Vec3 at) {
            if (!FaUtil.once(ctx, "sandstorm")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 6);
            float r = stats.radius(5.0F);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.SANDSTORM, c, r, stats.duration(160), null);
            sl.sendParticles(SAND_PILLAR, c.x, c.y + 0.1, c.z, 40, r * 0.5, 0.1, r * 0.5, 0.3);
            FaUtil.sound(sl, c, SoundEvents.SAND_BREAK, 1.5F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.BREEZE_SHOOT, 1.0F, 0.5F);
        }
    }

    /** Areia Movediça Maior: o chão vira areia movediça funda — inimigos afundam, ficam presos e sufocam. */
    public static class Sinkhole extends EffectGlyph {
        public Sinkhole() {
            super("sinkhole", 20);
            element(Element.SAND);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            sink(ctx, stats, target.blockPosition());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            sink(ctx, stats, hit.getBlockPos().relative(hit.getDirection()));
        }

        private void sink(SpellContext ctx, SpellStats stats, BlockPos center) {
            if (!FaUtil.once(ctx, "sinkhole")) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(2.5F);
            int ri = Mth.ceil(r);
            int life = stats.duration(120);
            BlockState sand = FaRegistries.SINKING_SAND.get().defaultBlockState();
            Integer topY = null;
            int placed = 0;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.5) continue;
                    BlockPos g = FaUtil.groundBlock(sl, center.offset(dx, 0, dz), 1, 3);
                    if (g == null) continue;
                    BlockPos under = g.below();
                    // Só onde há chão firme embaixo do poço (nada de cair numa caverna)
                    if (!FaUtil.isSolidGround(sl, under) || !FaUtil.isSolidGround(sl, under.below())) continue;
                    if (!FaUtil.canBreak(ctx, g) || !FaUtil.canBreak(ctx, under)) continue;
                    int extra = sl.random.nextInt(15);
                    if (!FaTempBlocks.clearPlantAbove(sl, g, life + extra)) continue;
                    if (FaTempBlocks.place(sl, g, sand, life + extra)) {
                        FaTempBlocks.place(sl, under, sand, life + extra);
                        placed++;
                        if (dx == 0 && dz == 0) topY = g.getY() + 1;
                    }
                }
            }
            if (placed == 0) return;
            Vec3 c = topY != null ? new Vec3(center.getX() + 0.5, topY, center.getZ() + 0.5) : FaUtil.ground(sl, Vec3.atBottomCenterOf(center), 4);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.SINKHOLE, c, r, life, null);
            sl.sendParticles(SAND_PILLAR, c.x, c.y + 0.1, c.z, 40, r * 0.5, 0.1, r * 0.5, 0.25);
            FaUtil.sound(sl, c, SoundEvents.SAND_BREAK, 1.5F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.5F);
        }
    }

    /** Miragem: imagens tremeluzentes surgem em volta — os inimigos se confundem, atacam uns aos outros ou correm atrás das ilusões. */
    public static class Mirage extends EffectGlyph {
        public Mirage() {
            super("mirage", 18);
            element(Element.SAND);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            conjure(ctx, stats, target.position(), target);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            conjure(ctx, stats, FaUtil.ground(ctx.level(), Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), 4), null);
        }

        private void conjure(SpellContext ctx, SpellStats stats, Vec3 center, Entity target) {
            if (!FaUtil.once(ctx, "mirage")) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(5.0F);
            int ticks = stats.duration(120);
            // Três ilusões em volta do ponto
            List<Vec3> images = new ArrayList<>();
            double base = sl.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 3; i++) {
                double a = base + Math.PI * 2 * i / 3;
                Vec3 p = FaUtil.ground(sl, center.add(Math.cos(a) * 4.5, 1.5, Math.sin(a) * 4.5), 4);
                images.add(p);
            }
            for (int t = 0; t < ticks; t += 8) {
                final int age = t;
                SpellScheduler.schedule(Math.max(1, t), () -> {
                    for (Vec3 p : images) drawFigure(sl, p, center, age);
                });
            }
            List<LivingEntity> foes = new ArrayList<>(ctx.enemiesAround(center, r));
            if (target instanceof LivingEntity le && !FaUtil.isSelf(ctx, le) && !foes.contains(le) && !ctx.isAlly(le)) foes.add(le);
            for (LivingEntity e : foes) {
                Vec3 img = images.get(sl.random.nextInt(images.size()));
                e.getPersistentData().putLong(FaEffects.MIRAGE_KEY, BlockPos.containing(img).asLong());
                e.addEffect(new MobEffectInstance(FaRegistries.BEWILDERED, ticks, 0), ctx.caster());
                if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0), ctx.caster());
                sl.sendParticles(FaUtil.dust(0xFFE9A8, 1.0F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 8, 0.3, 0.2, 0.3, 0);
            }
            FaUtil.sound(sl, center, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 1.0F);
            FaUtil.sound(sl, center, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.3F);
        }

        /** Silhueta humanoide de partículas tremeluzentes (cabeça, tronco, braços, pernas) virada para o centro. */
        private static void drawFigure(ServerLevel sl, Vec3 feet, Vec3 lookAt, int age) {
            Vec3 f = lookAt.subtract(feet);
            Vec3 fwd = new Vec3(f.x, 0, f.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(f.x, 0, f.z).normalize();
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            double sway = Math.sin(age * 0.3) * 0.08;
            ParticleOptions body = FaUtil.dustFade(0xFFF3C4, 0xE3C77A, 0.8F);
            ParticleOptions glow = FaUtil.dust(0xFFFFFF, 0.6F);
            Vec3 o = feet.add(side.scale(sway));
            // Pernas
            seg(sl, o.add(side.scale(0.15)), o.add(side.scale(0.12)).add(0, 0.75, 0), body, 4);
            seg(sl, o.add(side.scale(-0.15)), o.add(side.scale(-0.12)).add(0, 0.75, 0), body, 4);
            // Tronco
            seg(sl, o.add(0, 0.75, 0), o.add(0, 1.45, 0), body, 5);
            seg(sl, o.add(side.scale(0.25)).add(0, 1.4, 0), o.add(side.scale(-0.25)).add(0, 1.4, 0), body, 3);
            // Braços
            seg(sl, o.add(side.scale(0.28)).add(0, 1.4, 0), o.add(side.scale(0.35)).add(fwd.scale(0.15)).add(0, 0.85, 0), body, 4);
            seg(sl, o.add(side.scale(-0.28)).add(0, 1.4, 0), o.add(side.scale(-0.35)).add(fwd.scale(0.15)).add(0, 0.85, 0), body, 4);
            // Cabeça
            Vec3 head = o.add(0, 1.72, 0);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Vec3 p = head.add(side.scale(Math.cos(a) * 0.2)).add(0, Math.sin(a) * 0.2, 0);
                sl.sendParticles(body, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(glow, head.x, head.y, head.z, 2, 0.08, 0.08, 0.08, 0);
            if (sl.random.nextInt(3) == 0) sl.sendParticles(ParticleTypes.WAX_OFF, o.x, o.y + 1.0, o.z, 1, 0.2, 0.5, 0.2, 0);
        }

        private static void seg(ServerLevel sl, Vec3 a, Vec3 b, ParticleOptions p, int n) {
            for (int i = 0; i <= n; i++) {
                Vec3 q = a.add(b.subtract(a).scale(i / (double) n));
                sl.sendParticles(p, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
            }
        }
    }

    private SandGlyphs() {}
}
