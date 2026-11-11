package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.GravityWellEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.OrbitingDebrisEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de GRAVIDADE (Terra + Sombra).
 * Tier 1: Esmagar · Tier 2: Levitar Massa · Tier 3: Órbita de Destroços, Ponto de Atração · Tier 4: Inverter Gravidade.
 */
public final class GravityGlyphs {

    public static void register() {
        GlyphRegistry.register(new Crush());
        GlyphRegistry.register(new MassLevitate());
        GlyphRegistry.register(new DebrisOrbit());
        GlyphRegistry.register(new AttractionPoint());
        GlyphRegistry.register(new InvertGravity());
    }

    /**
     * Esmagar: a gravidade cai de uma vez sobre o alvo — dano 5 (7,5 se estava no ar), joga no chão com força
     * e deixa muito lento por 2s.
     */
    public static class Crush extends EffectGlyph {
        public Crush() {
            super("crush", 12);
            element(Element.GRAVITY);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                boolean airborne = !e.onGround() && !e.isInWater();
                FbUtil.hurt(ctx, e, airborne ? 7.5F : 5.0F, stats, null);
                if (!e.isAlive()) continue;
                Vec3 v = e.getDeltaMovement();
                FbUtil.setMotion(e, new Vec3(v.x * 0.2, -1.6, v.z * 0.2));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(40), 2), ctx.caster());
                Vec3 feet = FbUtil.ground(sl, e.position(), 6);
                BlockState below = sl.getBlockState(BlockPos.containing(feet).below());
                if (!below.isAir() && below.getRenderShape() == RenderShape.MODEL) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), feet.x, feet.y + 0.1, feet.z, 30,
                            e.getBbWidth() * 0.8, 0.05, e.getBbWidth() * 0.8, 0.2);
                }
                FbFx.shockwave(sl, feet.add(0, 0.15, 0), FbFx.dust(FbFx.GRAVITY, 1.6F), 24, 0.35);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 16,
                        e.getBbWidth() * 0.4, 0.3, e.getBbWidth() * 0.4, 0.02);
                FbFx.sound(sl, feet, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.8F);
                FbFx.sound(sl, feet, SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
            }
        }
    }

    /**
     * Levitar Massa: todos os inimigos num raio de 4 em volta do alvo flutuam (Levitação II) por 3s e depois
     * despencam. Chefes sobem bem menos.
     */
    public static class MassLevitate extends EffectGlyph {
        public MassLevitate() {
            super("mass_levitate", 16);
            element(Element.GRAVITY);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = target.position();
            if (!FbUtil.once(ctx, FbUtil.posKey("mass_levitate", c))) return;
            float r = stats.radius(4F);
            int ticks = stats.duration(60);
            List<LivingEntity> list = new ArrayList<>(ctx.enemiesAround(c, r));
            if (!FbUtil.isSelf(ctx, target) && target instanceof LivingEntity le && !ctx.isAlly(le) && !list.contains(le)) list.add(le);
            for (LivingEntity e : list) {
                if (FbUtil.untouchable(e)) continue;
                boolean boss = FbUtil.isBoss(e);
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, boss ? ticks / 3 : ticks, boss ? 0 : 1), ctx.caster());
                FbFx.column(sl, e.position(), ParticleTypes.REVERSE_PORTAL, 2.0, 8);
                FbFx.ring(sl, e.position().add(0, 0.1, 0), FbFx.dust(FbFx.GRAVITY, 1.1F), e.getBbWidth() + 0.4, 10, 0);
            }
            FbFx.ring(sl, c.add(0, 0.1, 0), FbFx.fade(FbFx.GRAVITY, FbFx.VOID_BLACK, 1.5F), r, 36, 0);
            FbFx.ring(sl, c.add(0, 0.2, 0), ParticleTypes.REVERSE_PORTAL, r * 0.8, 20, 0.05);
            FbFx.sound(sl, c, SoundEvents.SHULKER_BULLET_HIT, 1.2F, 0.7F);
            FbFx.sound(sl, c, SoundEvents.EVOKER_CAST_SPELL, 0.9F, 0.8F);
        }
    }

    /**
     * Órbita de Destroços: 3 blocos (+1 por Dividir, máx. 6) arrancados do chão (cópias, o terreno não muda)
     * giram em volta de você por 15s. Quando um inimigo chega perto, um destroço é arremessado nele (dano 4).
     * Se a magia acertou um inimigo, os destroços vão atrás dele primeiro.
     */
    public static class DebrisOrbit extends EffectGlyph {
        public DebrisOrbit() {
            super("debris_orbit", 20);
            element(Element.GRAVITY);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !FbUtil.once(ctx, "debris_orbit")) return;
            LivingEntity priority = !FbUtil.isSelf(ctx, target) && target instanceof LivingEntity le && !ctx.isAlly(le) ? le : null;
            int count = 3 + Math.max(0, stats.split);
            OrbitingDebrisEntity.summon(ctx, sample(ctx.level(), c.blockPosition().below()), count, stats.duration(300),
                    4.0F * stats.power(), priority, stats);
        }

        /** Até 3 tipos de bloco "de terreno" debaixo do conjurador (terra, pedra, areia...), ou uma mistura padrão. */
        static List<BlockState> sample(ServerLevel sl, BlockPos under) {
            List<BlockState> out = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(under.offset(-1, -1, -1), under.offset(1, 0, 1))) {
                BlockState s = sl.getBlockState(p);
                if (natural(s) && !out.contains(s)) out.add(s);
                if (out.size() >= 3) break;
            }
            if (out.isEmpty()) {
                out.add(Blocks.COARSE_DIRT.defaultBlockState());
                out.add(Blocks.COBBLESTONE.defaultBlockState());
                out.add(Blocks.STONE.defaultBlockState());
            }
            return out;
        }

        private static boolean natural(BlockState s) {
            if (s.getRenderShape() != RenderShape.MODEL || s.hasBlockEntity()) return false;
            return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                    || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.END_STONE)
                    || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE);
        }
    }

    /**
     * Ponto de Atração: abre um pequeno buraco negro (no alvo, no bloco atingido ou 5 blocos à frente em Si
     * Mesmo) que por 4s puxa inimigos, itens e XP num raio de 7. Quem chega ao núcleo leva dano; no fim ele
     * implode e arremessa todos em volta.
     */
    public static class AttractionPoint extends EffectGlyph {
        private static final String KEY = "attraction_point";

        public AttractionPoint() {
            super("attraction_point", 24);
            element(Element.GRAVITY);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FbUtil.once(ctx, KEY)) return;
            Vec3 at;
            if (FbUtil.isSelf(ctx, target) && ctx.caster() != null) {
                BlockHitResult hit = FbUtil.lookClip(ctx.caster(), 5);
                at = hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation().subtract(ctx.look().scale(1.0));
                at = at.subtract(0, 0.5, 0);
            } else {
                at = target.position().add(0, target.getBbHeight() * 0.5 - 0.5, 0);
            }
            open(ctx, at, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            open(ctx, hit.getLocation().add(0, 0.8, 0), stats);
        }

        private static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
            float power = FbUtil.power(ctx, stats);
            GravityWellEntity.open(ctx, at, stats.radius(7F), stats.duration(80), 2.0F * stats.power(),
                    0.12F * Math.min(2F, power), stats);
        }
    }

    /**
     * Inverter Gravidade: por 2,5s o alvo "cai para cima" (bate a cabeça no teto e se machuca) e, quando a
     * gravidade volta, despenca de onde estiver. Chefes ficam invertidos só 1/3 do tempo.
     */
    public static class InvertGravity extends EffectGlyph {
        public InvertGravity() {
            super("invert_gravity", 26);
            element(Element.GRAVITY);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(3F))) {
                if (FbUtil.untouchable(e)) continue;
                // Teto de 4s: a altura cresce com o tempo e uma queda de 25+ blocos já seria morte certa
                int ticks = Math.min(80, stats.duration(50));
                if (FbUtil.isBoss(e)) ticks = Math.max(10, ticks / 3);
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                e.addEffect(new MobEffectInstance(FbRegistries.INVERTED_GRAVITY, ticks, FbUtil.amp(ctx, stats), false, true, true), ctx.caster());
                FbUtil.addMotion(e, new Vec3(0, 0.15, 0));
                FbUtil.forgetTarget(e);
                FbFx.column(sl, e.position(), ParticleTypes.REVERSE_PORTAL, e.getBbHeight() + 2, 14);
                FbFx.ring(sl, e.position().add(0, 0.1, 0), FbFx.dust(FbFx.GRAVITY, 1.3F), e.getBbWidth() + 0.6, 16, 0);
                FbFx.sound(sl, e.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.5F);
                FbFx.sound(sl, e.position(), SoundEvents.SHULKER_SHOOT, 0.8F, 0.6F);
            }
        }
    }

    private GravityGlyphs() {}
}
