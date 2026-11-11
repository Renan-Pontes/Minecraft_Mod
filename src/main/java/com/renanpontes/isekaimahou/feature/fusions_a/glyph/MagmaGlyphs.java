package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaShapes;
import com.renanpontes.isekaimahou.feature.fusions_a.FaTempBlocks;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Magma (Fogo + Terra): Rocha Incandescente, Poça de Lava, Obsidiana, Armadura de Magma, Erupção. */
public final class MagmaGlyphs {

    public static void register() {
        GlyphRegistry.register(new MoltenRock());
        GlyphRegistry.register(new LavaPool());
        GlyphRegistry.register(new Obsidian());
        GlyphRegistry.register(new MagmaArmor());
        GlyphRegistry.register(new Eruption());
    }

    private static final BlockParticleOption MAGMA_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState());
    private static final BlockParticleOption OBSIDIAN_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());

    /** Rocha Incandescente: pedra derretida que queima e arremessa o alvo para longe. */
    public static class MoltenRock extends EffectGlyph {
        public MoltenRock() {
            super("molten_rock", 10);
            element(Element.MAGMA);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 from = FaUtil.isSelf(ctx, target) || ctx.caster() == null ? target.position() : ctx.caster().position();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 6.0F, stats, null);
                t.igniteForSeconds(stats.duration(80) / 20F);
                Vec3 d = t.position().subtract(from);
                Vec3 dir = new Vec3(d.x, 0, d.z).lengthSqr() < 1.0E-4 ? FaUtil.flatLook(ctx) : new Vec3(d.x, 0, d.z).normalize();
                double k = 0.75 * Math.min(stats.power(), 2.5F) * FaUtil.kbFactor(t);
                FaUtil.addMotion(t, dir.scale(k).add(0, 0.25 + 0.1 * k, 0));
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                sl.sendParticles(MAGMA_BITS, c.x, c.y, c.z, 18, 0.3, 0.3, 0.3, 0.15);
                sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 5, 0.2, 0.2, 0.2, 0.03);
                FaUtil.sound(sl, c, SoundEvents.BASALT_BREAK, 1.2F, 0.7F);
                FaUtil.sound(sl, c, SoundEvents.FIRECHARGE_USE, 0.8F, 0.8F);
            }
        }
    }

    /** Poça de Lava: cobre o chão com magma temporário que queima os inimigos que pisam nele. */
    public static class LavaPool extends EffectGlyph {
        public LavaPool() {
            super("lava_pool", 14);
            element(Element.MAGMA);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le && !FaUtil.isSelf(ctx, target)) le.igniteForSeconds(2);
            pool(ctx, stats, target.blockPosition());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            BlockPos p = hit.getDirection() == Direction.UP ? hit.getBlockPos().above() : hit.getBlockPos().relative(hit.getDirection());
            pool(ctx, stats, p);
        }

        private void pool(SpellContext ctx, SpellStats stats, BlockPos center) {
            if (!FaUtil.once(ctx, "lava_pool@" + center.asLong())) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(1.8F);
            int life = stats.duration(120);
            int ri = Mth.ceil(r);
            int placed = 0;
            BlockPos surface = null;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.5) continue;
                    BlockPos ground = FaUtil.groundBlock(sl, center.offset(dx, 0, dz), 1, 3);
                    if (ground == null) continue;
                    BlockPos at = ground.above();
                    if (!FaUtil.canPlace(ctx, at)) continue;
                    if (FaTempBlocks.place(sl, at, FaRegistries.MOLTEN_POOL.get().defaultBlockState(), life + sl.random.nextInt(20))) {
                        placed++;
                        if (dx == 0 && dz == 0) surface = at;
                    }
                }
            }
            Vec3 c = surface != null ? Vec3.atBottomCenterOf(surface) : FaUtil.ground(sl, Vec3.atBottomCenterOf(center), 4);
            if (placed > 0) FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.LAVA_POOL, c, r + 0.3F, life, null);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.2, c.z, 12, r * 0.4, 0.1, r * 0.4, 0);
            sl.sendParticles(MAGMA_BITS, c.x, c.y + 0.2, c.z, 20, r * 0.4, 0.1, r * 0.4, 0.1);
            FaUtil.sound(sl, c, SoundEvents.BUCKET_EMPTY_LAVA, 1.0F, 0.8F);
            FaUtil.sound(sl, c, SoundEvents.LAVA_POP, 1.0F, 1.0F);
        }
    }

    /** Obsidiana: prende o alvo numa casca de obsidiana — ou cria uma plataforma onde acertar (até sobre lava/água). */
    public static class Obsidian extends EffectGlyph {
        public Obsidian() {
            super("obsidian", 16);
            element(Element.MAGMA);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int ticks = stats.duration(70);
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 4.0)) {
                FaUtil.hurt(ctx, t, 3.0F, stats, null);
                t.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, ticks, 0), ctx.caster());
                FaShapes.cage(ctx, t, FaRegistries.MAGMA_PRISON.get().defaultBlockState(), ticks);
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                sl.sendParticles(OBSIDIAN_BITS, c.x, c.y, c.z, 24, 0.5, 0.6, 0.5, 0.1);
                sl.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, c.x, c.y, c.z, 8, 0.5, 0.6, 0.5, 0);
                FaUtil.sound(sl, c, SoundEvents.BASALT_PLACE, 1.3F, 0.6F);
                FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.7F);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            BlockPos center = hit.getDirection() == Direction.UP ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            // Acertou o fundo de um lago de lava/água: a plataforma nasce na superfície (o líquido esfria em obsidiana)
            BlockPos surface = FaShapes.liquidSurface(sl, hit.getBlockPos().relative(hit.getDirection()), 12);
            if (surface != null) center = surface;
            if (!FaUtil.once(ctx, "obsidian_platform@" + center.asLong())) return;
            float r = stats.radius(1.6F);
            int ri = Mth.ceil(r);
            int life = stats.duration(240);
            int placed = 0;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.5) continue;
                    BlockPos p = center.offset(dx, 0, dz);
                    if (!(FaUtil.isOpen(sl, p) || FaShapes.isLiquid(sl, p)) || !FaUtil.canPlace(ctx, p)) continue;
                    if (FaTempBlocks.place(sl, p, FaRegistries.MAGMA_PRISON.get().defaultBlockState(), life + (dx * dx + dz * dz) * 4)) {
                        placed++;
                        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.01);
                    }
                }
            }
            Vec3 c = Vec3.atCenterOf(center);
            if (placed > 0) {
                sl.sendParticles(OBSIDIAN_BITS, c.x, c.y + 0.5, c.z, 20, r * 0.5, 0.1, r * 0.5, 0.1);
                FaUtil.sound(sl, c, SoundEvents.BASALT_PLACE, 1.2F, 0.7F);
                FaUtil.sound(sl, c, SoundEvents.LAVA_EXTINGUISH, 0.8F, 1.0F);
            } else {
                // Sem espaço livre (chão plano, parede cheia): a rocha derretida só chia e esfria
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.6, c.z, 8, 0.4, 0.1, 0.4, 0.02);
                sl.sendParticles(OBSIDIAN_BITS, c.x, c.y + 0.6, c.z, 8, 0.3, 0.1, 0.3, 0.05);
                FaUtil.sound(sl, c, SoundEvents.LAVA_EXTINGUISH, 0.7F, 1.3F);
            }
        }
    }

    /** Armadura de Magma: crosta de rocha derretida — armadura, imunidade a fogo e queima quem te bate. */
    public static class MagmaArmor extends EffectGlyph {
        public MagmaArmor() {
            super("magma_armor", 18);
            element(Element.MAGMA);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Invertido: a crosta prende e queima o inimigo
                le.igniteForSeconds(stats.duration(100) / 20F);
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(100), 1), ctx.caster());
                FaUtil.hurt(ctx, le, 2.0F, stats, null);
            } else {
                int ticks = stats.duration(600);
                le.addEffect(new MobEffectInstance(FaRegistries.MAGMA_ARMOR, ticks, Math.min(2, FaUtil.amp(ctx, stats))), ctx.caster());
                le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0), ctx.caster());
                le.clearFire();
            }
            FaUtil.spiral(sl, le, MAGMA_BITS, 3, 30);
            FaUtil.spiral(sl, le, FaUtil.dustFade(0xFF6A00, 0x2A1208, 1.2F), 2, 24);
            sl.sendParticles(ParticleTypes.LAVA, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.3, 0.4, 0.3, 0);
            FaUtil.sound(sl, le.position(), SoundEvents.BASALT_PLACE, 1.0F, 0.8F);
            FaUtil.sound(sl, le.position(), SoundEvents.MAGMA_CUBE_SQUISH, 0.9F, 0.7F);
        }
    }

    /** Erupção: o chão em volta cospe jatos de magma por alguns segundos, mirando nos inimigos. */
    public static class Eruption extends EffectGlyph {
        public Eruption() {
            super("eruption", 28);
            element(Element.MAGMA);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            erupt(ctx, stats, target.position());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            erupt(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private void erupt(SpellContext ctx, SpellStats stats, Vec3 at) {
            if (!FaUtil.once(ctx, "eruption")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 6);
            float r = stats.radius(5.0F);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.ERUPTION, c, r, stats.duration(80), null);
            FaUtil.shockwave(sl, c.add(0, 0.2, 0), FaUtil.dust(0xFF4500, 1.6F), 40, 0.4);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MAGMA_BLOCK.defaultBlockState()),
                    c.x, c.y + 0.1, c.z, 40, r * 0.4, 0.1, r * 0.4, 0.2);
            FaUtil.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.LAVA_AMBIENT, 1.5F, 0.6F);
        }
    }

    private MagmaGlyphs() {}
}
