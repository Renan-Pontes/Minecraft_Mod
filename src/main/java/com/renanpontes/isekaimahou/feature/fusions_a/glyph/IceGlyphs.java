package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Gelo (Água + Vento): Congelar, Lança de Gelo, Ponte de Gelo, Chão Escorregadio, Armadura de Gelo, Nevasca, Estátua de Gelo. */
public final class IceGlyphs {

    public static void register() {
        GlyphRegistry.register(new Freeze());
        GlyphRegistry.register(new IceLance());
        GlyphRegistry.register(new IceBridge());
        GlyphRegistry.register(new SlipperyGround());
        GlyphRegistry.register(new IceArmor());
        GlyphRegistry.register(new Blizzard());
        GlyphRegistry.register(new IceStatue());
    }

    static final BlockParticleOption ICE_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());

    static void frostBurst(ServerLevel sl, Vec3 c, int n) {
        sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, n, 0.35, 0.35, 0.35, 0.05);
        sl.sendParticles(ICE_BITS, c.x, c.y, c.z, n / 2, 0.3, 0.3, 0.3, 0.1);
        sl.sendParticles(FaUtil.dust(0xCFF4FF, 1.1F), c.x, c.y, c.z, n / 2, 0.35, 0.35, 0.35, 0);
    }

    /** Congelar: congela o alvo (quase não se move) — e a superfície da água vira gelo onde acertar. */
    public static class Freeze extends EffectGlyph {
        public Freeze() {
            super("freeze", 10);
            element(Element.ICE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 2.0F, stats, null);
                t.clearFire();
                ctx.status(t, CoreRegistries.FROZEN, 80, 0, stats);
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                frostBurst(sl, c, 20);
                FaUtil.sound(sl, c, SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 1.0F);
                FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 0.6F, 1.4F);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            BlockPos surf = FaShapes.waterSurface(sl, hit.getBlockPos(), 5);
            if (surf == null) surf = FaShapes.waterSurface(sl, hit.getBlockPos().relative(hit.getDirection()), 5);
            if (surf == null || !FaUtil.once(ctx, "freeze_water@" + surf.asLong())) return;
            float r = stats.radius(2.5F);
            int ri = Mth.ceil(r);
            int life = stats.duration(300);
            int n = 0;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.5) continue;
                    BlockPos p = surf.offset(dx, 0, dz);
                    if (!FaShapes.isLiquid(sl, p) || !sl.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) continue;
                    if (!sl.getBlockState(p.above()).isAir() || !FaUtil.canPlace(ctx, p)) continue;
                    if (FaTempBlocks.place(sl, p, FaRegistries.FROST_ICE.get().defaultBlockState(), life + sl.random.nextInt(40))) n++;
                }
            }
            if (n > 0) {
                Vec3 c = Vec3.atCenterOf(surf).add(0, 0.6, 0);
                sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 30, r * 0.5, 0.1, r * 0.5, 0.02);
                FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 1.0F, 0.8F);
            }
        }
    }

    /** Lança de Gelo: estaca de gelo que atravessa o alvo e acerta quem estiver logo atrás. */
    public static class IceLance extends EffectGlyph {
        public IceLance() {
            super("ice_lance", 12);
            element(Element.ICE);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (FaUtil.isSelf(ctx, target)) {
                List<LivingEntity> foes = new ArrayList<>(ctx.enemiesAround(target.position(), 7.0));
                foes.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
                for (int i = 0; i < Math.min(3, foes.size()); i++) lance(ctx, stats, ctx.eyePos(), foes.get(i));
            } else if (target instanceof LivingEntity le) {
                Vec3 from = ctx.caster() != null ? ctx.eyePos().subtract(0, 0.3, 0) : le.position().add(0, le.getBbHeight() + 3, 0);
                lance(ctx, stats, from, le);
            }
        }

        private void lance(SpellContext ctx, SpellStats stats, Vec3 from, LivingEntity t) {
            ServerLevel sl = ctx.level();
            Vec3 hitAt = t.position().add(0, t.getBbHeight() * 0.55, 0);
            FaUtil.line(sl, from, hitAt, FaUtil.dustFade(0xFFFFFF, 0x8FD8FF, 0.9F), 5);
            FaUtil.line(sl, hitAt.subtract(hitAt.subtract(from).normalize().scale(1.5)), hitAt, ParticleTypes.SNOWFLAKE, 6);
            FaUtil.hurt(ctx, t, 7.0F, stats, null);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(40), 1), ctx.caster());
            frostBurst(sl, hitAt, 16);
            // Atravessa: fere quem estiver até 4 blocos atrás do alvo
            Vec3 dir = hitAt.subtract(from);
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize();
                Vec3 end = hitAt.add(dir.scale(4.0));
                AABB box = new AABB(hitAt, end).inflate(0.8);
                for (LivingEntity e : ctx.enemiesAround(hitAt.add(dir.scale(2.0)), 3.5)) {
                    if (e == t || !box.intersects(e.getBoundingBox())) continue;
                    FaUtil.hurt(ctx, e, 4.0F, stats, null);
                    frostBurst(sl, e.position().add(0, e.getBbHeight() * 0.5, 0), 8);
                }
                FaUtil.line(sl, hitAt, end, ParticleTypes.SNOWFLAKE, 3);
            }
            FaUtil.sound(sl, hitAt, SoundEvents.TRIDENT_HIT, 1.0F, 1.3F);
            FaUtil.sound(sl, hitAt, SoundEvents.GLASS_BREAK, 0.7F, 1.7F);
        }
    }

    /** Ponte de Gelo: cria uma ponte de gelo de 3 blocos de largura na direção do olhar/alvo, que derrete depois. */
    public static class IceBridge extends EffectGlyph {
        public IceBridge() {
            super("ice_bridge", 14);
            element(Element.ICE);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            float max = stats.radius(14.0F);
            if (FaUtil.isSelf(ctx, target)) {
                build(ctx, stats, caster.position(), FaUtil.flatLook(ctx), max);
            } else {
                Vec3 d = target.position().subtract(caster.position());
                double len = Math.sqrt(d.x * d.x + d.z * d.z);
                if (len < 1.5) build(ctx, stats, caster.position(), FaUtil.flatLook(ctx), max);
                else build(ctx, stats, caster.position(), new Vec3(d.x, 0, d.z).normalize(), Math.min(len, max * 1.5));
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            float max = stats.radius(14.0F);
            Vec3 d = hit.getLocation().subtract(caster.position());
            double len = Math.sqrt(d.x * d.x + d.z * d.z);
            if (len < 1.5) build(ctx, stats, caster.position(), FaUtil.flatLook(ctx), max);
            else build(ctx, stats, caster.position(), new Vec3(d.x, 0, d.z).normalize(), Math.min(len, max * 1.5));
        }

        private void build(SpellContext ctx, SpellStats stats, Vec3 feet, Vec3 dir, double length) {
            if (!FaUtil.once(ctx, "ice_bridge")) return;
            ServerLevel sl = ctx.level();
            Vec3 perp = new Vec3(-dir.z, 0, dir.x);
            int y = Mth.floor(feet.y - 0.5);
            int life = stats.duration(300);
            BlockState ice = FaRegistries.FROST_ICE.get().defaultBlockState();
            Set<BlockPos> seen = new HashSet<>();
            int steps = (int) Math.ceil(length * 2);
            for (int s = 0; s <= steps; s++) {
                Vec3 c = feet.add(dir.scale(s * 0.5));
                List<BlockPos> row = new ArrayList<>(3);
                for (int w = -1; w <= 1; w++) {
                    Vec3 q = c.add(perp.scale(w));
                    BlockPos p = new BlockPos(Mth.floor(q.x), y, Mth.floor(q.z));
                    if (seen.add(p)) row.add(p);
                }
                if (row.isEmpty()) continue;
                final int rowLife = life + s;
                final boolean ping = s % 6 == 0;
                SpellScheduler.schedule(1 + s / 3, () -> {
                    for (BlockPos p : row) {
                        if (!(FaUtil.isOpen(sl, p) || FaShapes.isLiquid(sl, p)) || !FaUtil.canPlace(ctx, p)) continue;
                        if (FaTempBlocks.place(sl, p, ice, rowLife)) {
                            sl.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 2, 0.3, 0.02, 0.3, 0.01);
                        }
                    }
                    if (ping) FaUtil.sound(sl, Vec3.atCenterOf(row.get(0)), SoundEvents.GLASS_PLACE, 0.7F, 1.3F);
                });
            }
            FaUtil.sound(sl, feet, SoundEvents.POWDER_SNOW_PLACE, 1.0F, 0.8F);
        }
    }

    /** Chão Escorregadio: o chão vira gelo liso — inimigos escorregam e às vezes caem. */
    public static class SlipperyGround extends EffectGlyph {
        public SlipperyGround() {
            super("slippery_ground", 12);
            element(Element.ICE);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            slick(ctx, stats, target.blockPosition());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            slick(ctx, stats, hit.getBlockPos().relative(hit.getDirection()));
        }

        private void slick(SpellContext ctx, SpellStats stats, BlockPos center) {
            if (!FaUtil.once(ctx, "slippery_ground")) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(3.0F);
            int ri = Mth.ceil(r);
            int life = stats.duration(200);
            BlockState slick = FaRegistries.SLICK_ICE.get().defaultBlockState();
            Integer surfaceY = null;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.5) continue;
                    BlockPos g = FaUtil.groundBlock(sl, center.offset(dx, 0, dz), 1, 3);
                    if (g == null || !FaUtil.canBreak(ctx, g)) continue;
                    int t = life + sl.random.nextInt(20);
                    if (!FaTempBlocks.clearPlantAbove(sl, g, t)) continue;
                    if (FaTempBlocks.place(sl, g, slick, t)) {
                        if (dx == 0 && dz == 0) surfaceY = g.getY() + 1;
                        if (sl.random.nextInt(3) == 0) {
                            sl.sendParticles(ParticleTypes.SNOWFLAKE, g.getX() + 0.5, g.getY() + 1.05, g.getZ() + 0.5, 1, 0.3, 0.02, 0.3, 0.01);
                        }
                    }
                }
            }
            Vec3 c = surfaceY != null ? new Vec3(center.getX() + 0.5, surfaceY, center.getZ() + 0.5)
                    : FaUtil.ground(sl, Vec3.atBottomCenterOf(center), 4);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.SLIPPERY, c, r, life, null);
            FaUtil.shockwave(sl, c.add(0, 0.1, 0), FaUtil.dust(0xCFF4FF, 1.2F), 30, 0.3);
            FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 1.0F, 0.7F);
            FaUtil.sound(sl, c, SoundEvents.POWDER_SNOW_PLACE, 1.0F, 1.2F);
        }
    }

    /** Armadura de Gelo: casca de gelo que protege, segura empurrões e congela quem bate de perto. */
    public static class IceArmor extends EffectGlyph {
        public IceArmor() {
            super("ice_armor", 16);
            element(Element.ICE);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Invertido: a casca de gelo prende o inimigo
                ctx.status(le, CoreRegistries.FROZEN, 60, 0, stats);
                FaUtil.hurt(ctx, le, 2.0F, stats, null);
            } else {
                le.removeEffect(CoreRegistries.FROZEN);
                le.setTicksFrozen(0);
                le.addEffect(new MobEffectInstance(FaRegistries.ICE_ARMOR, stats.duration(600), Math.min(2, FaUtil.amp(ctx, stats))), ctx.caster());
            }
            FaUtil.spiral(sl, le, ICE_BITS, 3, 30);
            FaUtil.spiral(sl, le, ParticleTypes.SNOWFLAKE, 2, 20);
            FaUtil.sound(sl, le.position(), SoundEvents.GLASS_PLACE, 1.0F, 0.9F);
            FaUtil.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
        }
    }

    /** Nevasca: tempestade de neve na área — deixa lento, fere aos poucos e congela quem fica dentro. */
    public static class Blizzard extends EffectGlyph {
        public Blizzard() {
            super("blizzard", 24);
            element(Element.ICE);
            tier(3);
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
            if (!FaUtil.once(ctx, "blizzard")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 6);
            float r = stats.radius(5.0F);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.BLIZZARD, c, r, stats.duration(160), null);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 2, c.z, 60, r * 0.5, 1.0, r * 0.5, 0.1);
            FaUtil.sound(sl, c, SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.PLAYER_HURT_FREEZE, 0.6F, 0.6F);
        }
    }

    /** Estátua de Gelo: congela o alvo por inteiro numa casca de gelo — não se move nem ataca por alguns segundos. */
    public static class IceStatue extends EffectGlyph {
        public IceStatue() {
            super("ice_statue", 26);
            element(Element.ICE);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int ticks = stats.duration(60);
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 4.0)) {
                FaUtil.hurt(ctx, t, 3.0F, stats, null);
                if (!t.isAlive()) continue;
                t.clearFire();
                t.addEffect(new MobEffectInstance(FaRegistries.ICE_STATUE, ticks, 0), ctx.caster());
                if (t.hasEffect(FaRegistries.ICE_STATUE)) {
                    FaShapes.cage(ctx, t, FaRegistries.FROST_ICE.get().defaultBlockState(), ticks);
                } else {
                    // Chefes resistem: só congelam por um instante
                    t.addEffect(new MobEffectInstance(CoreRegistries.FROZEN, Math.max(20, ticks / 2), 0), ctx.caster());
                }
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                frostBurst(sl, c, 40);
                FaUtil.sound(sl, c, SoundEvents.GLASS_PLACE, 1.2F, 0.6F);
                FaUtil.sound(sl, c, SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 0.8F);
            }
        }
    }

    private IceGlyphs() {}
}
