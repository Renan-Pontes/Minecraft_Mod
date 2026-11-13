package com.renanpontes.isekaimahou.feature.fire_water;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fire_water.effect.FwMobEffects;
import com.renanpontes.isekaimahou.feature.fire_water.entity.ElementalFieldEntity;
import com.renanpontes.isekaimahou.feature.fire_water.entity.FwSummon;
import com.renanpontes.isekaimahou.feature.fire_water.entity.SummonedUndine;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de efeito de ÁGUA.
 * Tier 1: Encharcar, Onda d'Água · Tier 2: Bolha, Lâmina d'Água, Apagar, Hidratar, Criar Água, Respiração ·
 * Tier 3: Correnteza, Névoa, Cura Leve + Invocar Ondina · Tier 4: Afogar, Chuva Local, Espelho d'Água ·
 * Tier 5: Dilúvio (lendário).
 */
public final class WaterGlyphs {

    public static void register() {
        GlyphRegistry.register(new Soak());
        GlyphRegistry.register(new Tide());
        GlyphRegistry.register(new Bubble());
        GlyphRegistry.register(new WaterBlade());
        GlyphRegistry.register(new Extinguish());
        GlyphRegistry.register(new Hydrate());
        GlyphRegistry.register(new CreateWater());
        GlyphRegistry.register(new WaterBreathing());
        GlyphRegistry.register(new Current());
        GlyphRegistry.register(new Mist());
        GlyphRegistry.register(new Mend());
        GlyphRegistry.register(new SummonUndine());
        GlyphRegistry.register(new Drown());
        GlyphRegistry.register(new LocalRain());
        GlyphRegistry.register(new WaterMirror());
        GlyphRegistry.register(new Deluge());
    }

    static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    static Vec3 outOf(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return hit.getLocation().add(d.getStepX() * 0.3, d.getStepY() * 0.3, d.getStepZ() * 0.3);
    }

    static void splashFx(ServerLevel sl, Entity e, int amount) {
        FwUtil.around(sl, e, Vfx.splash(), amount, 0.15);
        sl.sendParticles(Vfx.water(), e.getX(), e.getY() + e.getBbHeight() * 0.9, e.getZ(), amount / 3,
                e.getBbWidth() * 0.4, 0.1, e.getBbWidth() * 0.4, 0);
    }

    /** Tira o fogo e as brasas mágicas da entidade. */
    static void douse(LivingEntity e, boolean evenAzure) {
        e.clearFire();
        e.removeEffect(FwRegistries.LINGERING_EMBER);
        if (evenAzure) e.removeEffect(FwRegistries.AZURE_FLAME);
    }

    // =====================================================================================
    //  Tier 1
    // =====================================================================================

    /** Encharcar: dano 2 + Encharcado (apaga fogo; prepara combos de Raio e Gelo). Sensível: água no bloco apaga fogo e esfria lava. */
    public static class Soak extends EffectGlyph {
        public Soak() {
            super("soak", 6);
            element(Element.WATER);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            if (!(target instanceof LivingEntity le)) return;
            if (FwUtil.isSelf(ctx, target)) {
                // Em você mesmo: um balde d'água mágico que apaga o fogo
                douse(le, false);
                le.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 60, 0));
            } else {
                FwUtil.hurt(ctx, le, 2.0F, stats, null);
                ctx.status(le, CoreRegistries.SOAKED, 160, 0, stats);
                douse(le, false);
            }
            splashFx(sl, le, 20);
            FwUtil.sound(sl, le.position(), SoundEvents.PLAYER_SPLASH, 0.6F, 1.3F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (BlockPos p : new BlockPos[]{hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection())}) {
                BlockState s = sl.getBlockState(p);
                if (!FwUtil.canPlace(ctx, p)) continue;
                if (s.getBlock() instanceof BaseFireBlock) {
                    sl.removeBlock(p, false);
                } else if (s.is(Blocks.LAVA)) {
                    sl.setBlockAndUpdate(p, s.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    sl.levelEvent(1501, p, 0); // chiado + fumaça da lava esfriando
                } else if (s.is(Blocks.FARMLAND)) {
                    sl.setBlock(p, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                } else {
                    continue;
                }
                Vec3 c = Vec3.atCenterOf(p);
                sl.sendParticles(Vfx.splash(), c.x, c.y + 0.4, c.z, 12, 0.3, 0.1, 0.3, 0.1);
            }
        }
    }

    /** Onda d'Água: empurrão forte para longe de você + Encharcado. Inverter: puxa. Em Si Mesmo: onda em volta. */
    public static class Tide extends EffectGlyph {
        public Tide() {
            super("tide", 9);
            element(Element.WATER);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            boolean self = FwUtil.isSelf(ctx, target);
            Vec3 origin = ctx.caster() != null ? ctx.caster().position() : target.position().subtract(ctx.look());
            if (self) {
                FwUtil.shockwave(sl, target.position().add(0, 0.3, 0), Vfx.splash(), 48, 0.6);
                FwUtil.ring(sl, target.position().add(0, 0.2, 0), FwUtil.dust(0x3FA9FF, 1.5F), 1.5, 24, 0);
            }
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(4.0F))) {
                Vec3 dir = e.position().subtract(origin).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1.0E-4 ? FwUtil.flatLook(ctx) : dir.normalize();
                if (stats.inverted) dir = dir.scale(-1);
                double k = Math.max(0.2, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double strength = Math.min(1.6 * stats.power(), 3.0) * k * (self ? 0.85 : 1.0);
                FwUtil.addMotion(e, dir.scale(strength).add(0, 0.35 * k, 0));
                FwUtil.hurt(ctx, e, self ? 1.5F : 2.0F, stats, null);
                ctx.status(e, CoreRegistries.SOAKED, 80, 0, stats);
                douse(e, false);
                // Paredão de água no impacto
                sl.sendParticles(Vfx.splash(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 30,
                        e.getBbWidth() * 0.6, e.getBbHeight() * 0.5, e.getBbWidth() * 0.6, 0.3);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 1.0F);
            FwUtil.sound(sl, target.position(), SoundEvents.GENERIC_SPLASH, 0.7F, 0.8F);
        }
    }

    // =====================================================================================
    //  Tier 2
    // =====================================================================================

    /** Bolha: prende o alvo numa bolha que flutua devagar (Enraizado + levitação leve), depois desce suave. */
    public static class Bubble extends EffectGlyph {
        public Bubble() {
            super("bubble", 14);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int d = stats.duration(60);
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                ctx.status(e, CoreRegistries.ROOTED, 60, 0, stats);
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, d, 0), ctx.caster());
                e.addEffect(new MobEffectInstance(FwRegistries.BUBBLE_PRISON, d, 0), ctx.caster());
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, d + 60, 0, false, false, true), ctx.caster());
                Vec3 c = center(e);
                double r = Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5 + 0.35;
                FwUtil.sphere(sl, c, FwUtil.dust(0xA8ECFF, 1.1F), r, 36);
                FwUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, r, 12);
                FwUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2F, 1.2F);
                FwUtil.sound(sl, c, SoundEvents.PLAYER_SPLASH, 0.5F, 1.6F);
            }
        }
    }

    /** Lâmina d'Água: corte de água pressurizada, dano alto (+25% em alvo Encharcado). Em Si Mesmo: giro cortante. Sensível: corta folhas e plantas. */
    public static class WaterBlade extends EffectGlyph {
        public WaterBlade() {
            super("water_blade", 14);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            boolean self = FwUtil.isSelf(ctx, target);
            if (self) {
                float r = stats.radius(3.0F);
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10;
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX() + Math.cos(a) * r * 0.6, target.getY() + 1.0,
                            target.getZ() + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0);
                }
                FwUtil.ring(sl, target.position().add(0, 1.0, 0), Vfx.splash(), r * 0.7, 30, 0.2);
            }
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                float base = self ? 6.0F : 8.0F;
                if (e.hasEffect(CoreRegistries.SOAKED)) base *= 1.25F;
                FwUtil.hurt(ctx, e, base, stats, null);
                slashFx(sl, e, sl.random);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
            FwUtil.sound(sl, target.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.6F, 1.5F);
        }

        private static void slashFx(ServerLevel sl, LivingEntity e, RandomSource rnd) {
            Vec3 c = center(e);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            // Corte diagonal atravessando o alvo
            double tilt = rnd.nextBoolean() ? 1 : -1;
            for (int i = -4; i <= 4; i++) {
                double t = i / 4.0;
                double x = c.x + t * e.getBbWidth() * 0.9, y = c.y + t * tilt * e.getBbHeight() * 0.5, z = c.z + t * 0.2;
                sl.sendParticles(FwUtil.dust(i % 2 == 0 ? 0xE6FAFF : 0x3FA9FF, 0.9F), x, y, z, 1, 0, 0, 0, 0);
                if (i % 2 == 0) sl.sendParticles(ParticleTypes.SPLASH, x, y, z, 1, 0.02, 0.02, 0.02, 0.05);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int r = 1 + Mth.clamp(stats.aoe, 0, 2);
            BlockPos c = hit.getBlockPos();
            int cut = 0;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                BlockState s = sl.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.getBlock() instanceof BaseFireBlock) continue;
                if (!(s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS) || s.is(Blocks.VINE)
                        || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.COBWEB))) continue;
                BlockPos fixed = p.immutable();
                if (!FwUtil.canBreak(ctx, fixed)) continue;
                sl.destroyBlock(fixed, true, ctx.caster());
                if (++cut >= 30) break;
            }
            if (cut > 0) FwUtil.sound(sl, hit.getLocation(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        }
    }

    /** Apagar: apaga o fogo de criaturas (aliadas também) e de blocos na área; fere criaturas de fogo. */
    public static class Extinguish extends EffectGlyph {
        public Extinguish() {
            super("extinguish", 8);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(2.5F);
            if (!FwUtil.isSelf(ctx, target) && target instanceof LivingEntity le && le.fireImmune()) {
                FwUtil.hurt(ctx, le, 5.0F, stats, null); // água fere criaturas feitas de fogo
                FwUtil.around(sl, le, Vfx.largeSmoke(), 16, 0.04);
            }
            douseArea(ctx, target.position(), r);
            douseBlocks(ctx, target.blockPosition(), r);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            float r = stats.radius(3.0F);
            douseBlocks(ctx, hit.getBlockPos(), r);
            douseArea(ctx, hit.getLocation(), r);
        }

        private static void douseArea(SpellContext ctx, Vec3 c, float r) {
            ServerLevel sl = ctx.level();
            if (!FwUtil.once(ctx, "extinguish_fx@" + BlockPos.containing(c))) return;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r))) {
                if (!e.isOnFire() && !e.hasEffect(FwRegistries.LINGERING_EMBER) && !e.hasEffect(FwRegistries.AZURE_FLAME)) continue;
                douse(e, true);
                FwUtil.around(sl, e, Vfx.cloud(), 8, 0.03);
                splashFx(sl, e, 10);
            }
            sl.sendParticles(Vfx.splash(), c.x, c.y + 0.5, c.z, 30, r * 0.4, 0.4, r * 0.4, 0.15);
            sl.sendParticles(Vfx.whiteSmoke(), c.x, c.y + 0.5, c.z, 10, r * 0.3, 0.3, r * 0.3, 0.02);
            FwUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
            FwUtil.sound(sl, c, SoundEvents.GENERIC_SPLASH, 0.6F, 1.2F);
        }

        private static void douseBlocks(SpellContext ctx, BlockPos center, float r) {
            ServerLevel sl = ctx.level();
            int ir = Mth.ceil(r);
            Vec3 c = Vec3.atCenterOf(center);
            Player player = ctx.casterPlayer();
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-ir, -ir, -ir), center.offset(ir, ir, ir))) {
                if (p.distToCenterSqr(c) > r * r) continue;
                BlockState s = sl.getBlockState(p);
                boolean fire = s.getBlock() instanceof BaseFireBlock;
                boolean campfire = s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
                boolean candle = s.getBlock() instanceof AbstractCandleBlock && s.hasProperty(BlockStateProperties.LIT)
                        && s.getValue(BlockStateProperties.LIT);
                if (!fire && !campfire && !candle) continue;
                BlockPos fixed = p.immutable();
                if (!FwUtil.canPlace(ctx, fixed)) continue;
                if (fire) {
                    sl.removeBlock(fixed, false);
                } else if (campfire) {
                    CampfireBlock.dowse(ctx.caster(), sl, fixed, s);
                    sl.setBlock(fixed, s.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
                } else {
                    AbstractCandleBlock.extinguish(player, s, sl, fixed);
                }
                Vec3 pc = Vec3.atCenterOf(fixed);
                sl.sendParticles(Vfx.largeSmoke(), pc.x, pc.y, pc.z, 4, 0.25, 0.2, 0.25, 0.02);
                sl.sendParticles(Vfx.splash(), pc.x, pc.y, pc.z, 4, 0.25, 0.1, 0.25, 0.1);
            }
        }
    }

    /** Hidratar: chuva mágica rápida que faz plantas crescerem e molha a terra arada. */
    public static class Hydrate extends EffectGlyph {
        public Hydrate() {
            super("hydrate", 8);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            target.clearFire();
            hydrate(ctx, target.blockPosition(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            hydrate(ctx, hit.getBlockPos(), stats);
        }

        private static void hydrate(SpellContext ctx, BlockPos center, SpellStats stats) {
            if (!FwUtil.once(ctx, "hydrate")) return;
            ServerLevel sl = ctx.level();
            RandomSource rnd = sl.random;
            int r = Mth.clamp(Math.round(stats.radius(2.0F)), 1, 5);
            int grown = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
                BlockState s = sl.getBlockState(p);
                if (s.isAir()) continue;
                BlockPos fixed = p.immutable();
                if (s.is(Blocks.FARMLAND)) {
                    if (s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE && FwUtil.canPlace(ctx, fixed)) {
                        sl.setBlock(fixed, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                    }
                    continue;
                }
                if (grown >= 24 || s.is(BlockTags.REPLACEABLE) || s.isSolidRender(sl, fixed)) continue;
                if (s.getBlock() instanceof BonemealableBlock bm) {
                    if (rnd.nextFloat() < 0.65F && bm.isValidBonemealTarget(sl, fixed, s) && bm.isBonemealSuccess(sl, rnd, fixed, s)
                            && FwUtil.canPlace(ctx, fixed)) {
                        bm.performBonemeal(sl, rnd, fixed, s);
                        sl.sendParticles(Vfx.happy(), fixed.getX() + 0.5, fixed.getY() + 0.6, fixed.getZ() + 0.5,
                                5, 0.3, 0.3, 0.3, 0);
                        grown++;
                    }
                } else if (s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.KELP) || s.is(Blocks.BAMBOO)) {
                    if (FwUtil.canPlace(ctx, fixed)) {
                        for (int i = 0; i < 4; i++) sl.getBlockState(fixed).randomTick(sl, fixed, rnd);
                        sl.sendParticles(Vfx.happy(), fixed.getX() + 0.5, fixed.getY() + 0.6, fixed.getZ() + 0.5,
                                3, 0.3, 0.3, 0.3, 0);
                        grown++;
                    }
                }
            }
            // Garoa sobre a área
            Vec3 c = Vec3.atCenterOf(center);
            int drops = 10 + r * r * 3;
            for (int i = 0; i < drops; i++) {
                double x = c.x + (rnd.nextDouble() * 2 - 1) * r, z = c.z + (rnd.nextDouble() * 2 - 1) * r;
                sl.sendParticles(Vfx.water(), x, c.y + 2.5 + rnd.nextDouble(), z, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(FwUtil.dust(0x8A93A6, 2.5F), c.x, c.y + 3.2, c.z, 8, r * 0.5, 0.15, r * 0.5, 0);
            FwUtil.sound(sl, c, SoundEvents.WEATHER_RAIN, 0.4F, 1.3F);
            if (grown > 0) FwUtil.sound(sl, c, SoundEvents.BONE_MEAL_USE, 1.0F, 1.0F);
        }
    }

    /** Criar Água: cria uma fonte de água (enche caldeirões, alaga blocos que aceitam água, esfria lava). */
    public static class CreateWater extends EffectGlyph {
        public CreateWater() {
            super("create_water", 6);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            if (target instanceof LivingEntity le) {
                douse(le, false);
                if (!FwUtil.isSelf(ctx, le) && !ctx.isAlly(le)) ctx.status(le, CoreRegistries.SOAKED, 60, 0, stats);
                splashFx(sl, le, 16);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            BlockPos pos = hit.getBlockPos();
            BlockState s = sl.getBlockState(pos);
            if (s.is(Blocks.CAULDRON) || s.is(Blocks.WATER_CAULDRON)) {
                if (FwUtil.canPlace(ctx, pos)) {
                    sl.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
                    FwUtil.sound(sl, Vec3.atCenterOf(pos), SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
                }
                return;
            }
            if (s.getBlock() instanceof SimpleWaterloggedBlock && s.hasProperty(BlockStateProperties.WATERLOGGED)
                    && !s.getValue(BlockStateProperties.WATERLOGGED) && !sl.dimensionType().ultraWarm()) {
                if (FwUtil.canPlace(ctx, pos)) {
                    sl.setBlock(pos, s.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                    sl.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(sl));
                    FwUtil.sound(sl, Vec3.atCenterOf(pos), SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
                }
                return;
            }
            BlockPos first = s.canBeReplaced() && s.getFluidState().isEmpty() ? pos : pos.relative(hit.getDirection());
            int r = Mth.clamp(stats.aoe, 0, 2);
            int placed = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (place(ctx, first.offset(dx, 0, dz))) placed++;
                }
            }
            Vec3 c = Vec3.atCenterOf(first);
            if (placed > 0) {
                FwUtil.sound(sl, c, SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
                sl.sendParticles(Vfx.splash(), c.x, c.y, c.z, 20, 0.4 + r, 0.2, 0.4 + r, 0.1);
            }
        }

        private static boolean place(SpellContext ctx, BlockPos p) {
            ServerLevel sl = ctx.level();
            BlockState t = sl.getBlockState(p);
            FluidState fluid = t.getFluidState();
            Vec3 c = Vec3.atCenterOf(p);
            if (t.is(Blocks.LAVA)) {
                if (!FwUtil.canPlace(ctx, p)) return false;
                sl.setBlockAndUpdate(p, fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                sl.levelEvent(1501, p, 0);
                return true;
            }
            if (!(t.isAir() || (t.canBeReplaced() && fluid.isEmpty() && !(t.getBlock() instanceof LiquidBlock)))) return false;
            if (sl.dimensionType().ultraWarm()) {
                // No Nether a água evapora na hora (igual ao balde)
                sl.sendParticles(Vfx.largeSmoke(), c.x, c.y, c.z, 8, 0.3, 0.2, 0.3, 0.02);
                FwUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.5F, 2.0F);
                return false;
            }
            if (!FwUtil.canPlace(ctx, p)) return false;
            sl.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
            return true;
        }
    }

    /** Respiração: respira debaixo d'água (e nada mais rápido se já estiver na água). Afeta você e aliados. */
    public static class WaterBreathing extends EffectGlyph {
        public WaterBreathing() {
            super("water_breathing", 8);
            element(Element.WATER);
            tier(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Inverter: rouba o fôlego por um instante
                if (!FwEvents.immuneToDrowning(le)) {
                    le.setAirSupply(Math.max(0, le.getAirSupply() - 120));
                    le.addEffect(new MobEffectInstance(FwRegistries.WATER_CHOKE, stats.duration(40), 0), ctx.caster());
                }
                sl.sendParticles(ParticleTypes.BUBBLE_POP, le.getX(), le.getEyeY(), le.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
                return;
            }
            le.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, stats.duration(1200), 0), ctx.caster());
            le.setAirSupply(le.getMaxAirSupply());
            if (le.isInWater()) le.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, stats.duration(300), 0), ctx.caster());
            for (int i = 0; i < 12; i++) {
                double a = i * 0.9;
                sl.sendParticles(ParticleTypes.BUBBLE_POP, le.getX() + Math.cos(a) * 0.5, le.getY() + i * 0.15,
                        le.getZ() + Math.sin(a) * 0.5, 1, 0, 0.02, 0, 0.01);
            }
            sl.sendParticles(FwUtil.dust(0x7FE0FF, 1.0F), le.getX(), le.getEyeY(), le.getZ(), 10, 0.3, 0.2, 0.3, 0);
            FwUtil.sound(sl, le.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 1.4F);
            FwUtil.sound(sl, le.position(), SoundEvents.BOTTLE_FILL, 0.7F, 1.2F);
        }
    }

    // =====================================================================================
    //  Tier 3
    // =====================================================================================

    /** Correnteza: arrasta o alvo continuamente na direção em que você olha. Inverter: puxa. Em Si Mesmo: surfa para frente. */
    public static class Current extends EffectGlyph {
        public Current() {
            super("current", 16);
            element(Element.WATER);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float s = Math.min(stats.power(), 2.0F);
            Vec3 look = FwUtil.flatLook(ctx);
            if (FwUtil.isSelf(ctx, target)) {
                // Surfar: avanço rápido e nado veloz
                Vec3 dir = stats.inverted ? look.scale(-1) : look;
                FwUtil.setMotion(target, dir.scale(1.5 * s).add(0, 0.25, 0));
                if (target instanceof LivingEntity le) {
                    le.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, stats.duration(60), 0), le);
                    FwEvents.cushion(le, 60);
                }
                trail(sl, target.position(), dir);
                FwUtil.sound(sl, target.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.2F);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            Vec3 dir = look;
            if (stats.inverted && ctx.caster() != null) {
                Vec3 to = ctx.caster().position().subtract(le.position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0E-4) dir = to.normalize();
            }
            FwUtil.hurt(ctx, le, 2.0F, stats, null);
            ctx.status(le, CoreRegistries.SOAKED, 80, 0, stats);
            FwMobEffects.Undertow.setFlow(le, dir, s);
            le.addEffect(new MobEffectInstance(FwRegistries.UNDERTOW, stats.duration(60), 0), ctx.caster());
            trail(sl, le.position(), dir);
            FwUtil.sound(sl, le.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8F, 1.0F);
        }

        private static void trail(ServerLevel sl, Vec3 from, Vec3 dir) {
            for (int i = 0; i < 10; i++) {
                Vec3 p = from.add(dir.scale(i * 0.4)).add(0, 0.15, 0);
                sl.sendParticles(Vfx.splash(), p.x, p.y, p.z, 3, 0.2, 0.05, 0.2, 0.1);
                sl.sendParticles(ParticleTypes.FISHING, p.x, p.y, p.z, 1, 0.1, 0.02, 0.1, 0.02);
            }
        }
    }

    /** Névoa: área que cega os inimigos e deixa os aliados invisíveis enquanto estiverem dentro. */
    public static class Mist extends EffectGlyph {
        public Mist() {
            super("mist", 16);
            element(Element.WATER);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            spawn(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, outOf(hit), stats);
        }

        private void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!FwUtil.once(ctx, "mist")) return;
            ElementalFieldEntity.spawn(ctx, ElementalFieldEntity.Kind.MIST, FwUtil.ground(ctx.level(), at, 4),
                    stats.radius(4.0F), stats.duration(200), 0, FwUtil.amp(ctx, stats), 0);
        }
    }

    /** Cura Leve: cura 5 + Regeneração curta, apaga o fogo. Inverter (num inimigo): vira dano. */
    public static class Mend extends EffectGlyph {
        public Mend() {
            super("mend", 14);
            element(Element.WATER);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                FwUtil.hurt(ctx, le, 4.0F, stats, null);
                ctx.status(le, CoreRegistries.SOAKED, 80, 0, stats);
                splashFx(sl, le, 14);
                return;
            }
            ctx.heal(le, 5.0F, stats);
            le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(80), 0), ctx.caster());
            douse(le, false);
            // Espiral de água subindo
            for (int i = 0; i < 20; i++) {
                double a = i * 0.6, h = i * 0.1;
                sl.sendParticles(FwUtil.dust(i % 2 == 0 ? 0x7FD8FF : 0xD8F6FF, 1.0F), le.getX() + Math.cos(a) * 0.6, le.getY() + h,
                        le.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
            }
            FwUtil.around(sl, le, Vfx.splash(), 10, 0.1);
            FwUtil.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
            FwUtil.sound(sl, le.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.6F, 1.4F);
        }
    }

    /** Invocar Ondina: espírito da água aliado que cura aliados e atira jatos d'água (30 s). */
    public static class SummonUndine extends EffectGlyph {
        public SummonUndine() {
            super("summon_undine", 30);
            element(Element.WATER);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            boolean self = FwUtil.isSelf(ctx, target);
            Vec3 near = self ? target.position().add(FwUtil.flatLook(ctx).scale(1.5)).add(0, 1.0, 0) : target.position().add(0, 1.0, 0);
            SummonedUndine u = summon(ctx, near, stats);
            if (u != null && !self && target instanceof LivingEntity le && !ctx.isAlly(le)) u.setTarget(le);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            summon(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())).add(0, 0.5, 0), stats);
        }

        private SummonedUndine summon(SpellContext ctx, Vec3 near, SpellStats stats) {
            if (!FwUtil.once(ctx, "summon_undine")) return null;
            // v1.0: Dividir chama a segunda ondina de uma vez (o limite por dono continua 2)
            SummonedUndine first = null;
            for (int i = 0; i < Math.min(2, stats.count()); i++) {
                SummonedUndine u = FwSummon.summon(ctx, FwRegistries.UNDINE.get(), near, stats.duration(600), FwUtil.power(ctx, stats), 2);
                if (first == null) first = u;
            }
            return first;
        }
    }

    // =====================================================================================
    //  Tier 4
    // =====================================================================================

    /** Afogar: água enche os pulmões do alvo — perde o ar mesmo em terra e se afoga. Quem respira na água é imune. */
    public static class Drown extends EffectGlyph {
        public Drown() {
            super("drown", 22);
            element(Element.WATER);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int amp = FwUtil.amp(ctx, stats);
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                FwUtil.hurt(ctx, e, 2.0F, stats, null);
                Vec3 head = new Vec3(e.getX(), e.getEyeY(), e.getZ());
                if (FwEvents.immuneToDrowning(e)) {
                    sl.sendParticles(ParticleTypes.BUBBLE_POP, head.x, head.y, head.z, 8, 0.2, 0.2, 0.2, 0.02);
                    continue;
                }
                e.setAirSupply(Math.max(-19, e.getAirSupply() - Math.round(150 * stats.power())));
                e.addEffect(new MobEffectInstance(FwRegistries.WATER_CHOKE, stats.duration(120), amp), ctx.caster());
                // Esfera de água presa na cabeça
                FwUtil.sphere(sl, head, FwUtil.dust(0x2F7FD8, 1.0F), Math.max(0.45, e.getBbWidth() * 0.6), 30);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, head.x, head.y, head.z, 10, 0.25, 0.25, 0.25, 0.02);
                sl.sendParticles(Vfx.water(), head.x, head.y - 0.2, head.z, 6, 0.25, 0.1, 0.25, 0);
                FwUtil.sound(sl, head, SoundEvents.PLAYER_HURT_DROWN, 1.0F, 1.0F);
                FwUtil.sound(sl, head, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 0.8F);
            }
        }
    }

    /** Chuva Local: nuvem de chuva numa área — encharca inimigos, apaga fogo (até brasas mágicas) e rega as plantas. */
    public static class LocalRain extends EffectGlyph {
        public LocalRain() {
            super("local_rain", 20);
            element(Element.WATER);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            spawn(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, outOf(hit), stats);
        }

        private void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!FwUtil.once(ctx, "local_rain")) return;
            ElementalFieldEntity.spawn(ctx, ElementalFieldEntity.Kind.RAIN, FwUtil.ground(ctx.level(), at, 4),
                    stats.radius(5.0F), stats.duration(300), 0, FwUtil.amp(ctx, stats), 0);
        }
    }

    /** Espelho d'Água: o próximo projétil que te atingir volta para quem atirou. Inverter (num inimigo): deixa Exposto. */
    public static class WaterMirror extends EffectGlyph {
        public WaterMirror() {
            super("water_mirror", 18);
            element(Element.WATER);
            tier(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            Vec3 c = center(le);
            if (stats.inverted && !ctx.isAlly(le)) {
                ctx.status(le, CoreRegistries.EXPOSED, 100, 0, stats);
                sl.sendParticles(Vfx.splash(), c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.1);
                FwUtil.sound(sl, c, SoundEvents.GLASS_BREAK, 0.7F, 1.5F);
                return;
            }
            le.addEffect(new MobEffectInstance(FwRegistries.WATER_MIRROR, stats.duration(300), 0), ctx.caster());
            double r = le.getBbWidth() * 0.5 + 0.6;
            for (int ring = 0; ring < 3; ring++) {
                FwUtil.ring(sl, le.position().add(0, 0.3 + ring * le.getBbHeight() * 0.35, 0), FwUtil.dust(0xBFF4FF, 1.1F), r, 18, 0);
            }
            sl.sendParticles(Vfx.splash(), c.x, c.y, c.z, 16, 0.4, 0.5, 0.4, 0.1);
            FwUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
            FwUtil.sound(sl, c, SoundEvents.BOTTLE_FILL, 0.8F, 1.0F);
        }
    }

    // =====================================================================================
    //  Tier 5 (lendário)
    // =====================================================================================

    /**
     * Dilúvio: tempestade enorme num redemoinho. Cura e apaga o fogo dos aliados; encharca, desacelera,
     * gira e afoga os inimigos.
     */
    public static class Deluge extends EffectGlyph {
        public Deluge() {
            super("deluge", 75);
            element(Element.WATER);
            legendary();
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            spawn(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, outOf(hit), stats);
        }

        private void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!FwUtil.once(ctx, "deluge")) return;
            Vec3 g = FwUtil.ground(ctx.level(), at, 6);
            ElementalFieldEntity.spawn(ctx, ElementalFieldEntity.Kind.DELUGE, g,
                    stats.radius(9.0F), stats.duration(240), 1.5F * stats.power(), FwUtil.amp(ctx, stats), 0);
            // v1.3: o céu se abre (golpe do céu) e a chuva cai sobre a área inteira enquanto o Dilúvio durar
            com.renanpontes.isekaimahou.core.ElementFx.skyStrike(ctx.level(), g, Element.WATER, ctx.color(), stats.radius(9.0F) * 0.6F, 5, 12);
            com.renanpontes.isekaimahou.core.ElementFx.area(ctx.level(), g, Element.WATER, ctx.color(), stats.radius(9.0F), 4, Math.min(400, stats.duration(240)));
        }
    }

    private WaterGlyphs() {}
}
