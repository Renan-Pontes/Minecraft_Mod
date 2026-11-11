package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.effect.FaEffects;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.SummonedEnt;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Natureza (Água + Terra): Crescimento, Espinhos, Vinhas, Esporos, Florescer, Fotossíntese, Invocar Ent. */
public final class NatureGlyphs {

    public static void register() {
        GlyphRegistry.register(new Growth());
        GlyphRegistry.register(new Thorns());
        GlyphRegistry.register(new Vines());
        GlyphRegistry.register(new Spores());
        GlyphRegistry.register(new Bloom());
        GlyphRegistry.register(new Photosynthesis());
        GlyphRegistry.register(new SummonEnt());
    }

    static final BlockParticleOption LEAF_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState());
    static final BlockParticleOption VINE_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState());

    /** Crescimento: faz plantas, mudas e plantações em volta crescerem na hora; filhotes viram adultos. */
    public static class Growth extends EffectGlyph {
        public Growth() {
            super("growth", 8);
            element(Element.NATURE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            if (target instanceof AgeableMob am && am.isBaby()) {
                am.setAge(0);
                FaUtil.around(sl, am, ParticleTypes.HAPPY_VILLAGER, 15, 0);
                FaUtil.sound(sl, am.position(), SoundEvents.BONE_MEAL_USE, 1.0F, 0.9F);
            }
            grow(ctx, stats, target.blockPosition());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            grow(ctx, stats, hit.getBlockPos());
        }

        private void grow(SpellContext ctx, SpellStats stats, BlockPos center) {
            if (!FaUtil.once(ctx, "growth@" + center.asLong())) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(2.5F);
            int ri = Mth.ceil(r);
            int budget = Math.round(10 * FaUtil.power(ctx, stats));
            ServerPlayer player = ctx.casterPlayer();
            int grown = 0;
            for (int pass = 0; pass < 2 && budget > 0; pass++) {
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-ri, -2, -ri), center.offset(ri, 2, ri))) {
                    if (budget <= 0) break;
                    if (p.distSqr(center) > r * r + 1) continue;
                    BlockState s = sl.getBlockState(p);
                    if (!(s.getBlock() instanceof BonemealableBlock bb) || !bb.isValidBonemealTarget(sl, p, s)) continue;
                    // Grama comum só floresce na primeira passada e com pouca chance (não enche tudo de mato)
                    if (s.is(Blocks.GRASS_BLOCK) && (pass > 0 || sl.random.nextInt(4) != 0)) continue;
                    if (!FaUtil.canPlace(ctx, p.immutable())) continue;
                    if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), sl, p.immutable(), player)) {
                        sl.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, p.immutable(), 15);
                        budget--;
                        grown++;
                    }
                }
            }
            Vec3 c = Vec3.atCenterOf(center);
            FaUtil.ring(sl, c.add(0, 0.6, 0), FaUtil.dust(0x6BD85A, 1.0F), r, 24, 0);
            if (grown > 0) FaUtil.sound(sl, c, SoundEvents.FLOWERING_AZALEA_PLACE, 1.0F, 1.1F);
        }
    }

    /** Espinhos: espinhos brotam do chão em volta do alvo — dano, lentidão e respingo nos vizinhos. */
    public static class Thorns extends EffectGlyph {
        public Thorns() {
            super("thorns", 10);
            element(Element.NATURE);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 5.0F, stats, null);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(60), 0), ctx.caster());
                for (LivingEntity e : ctx.enemiesAround(t.position(), 1.8)) {
                    if (e != t) FaUtil.hurt(ctx, e, 2.5F, stats, null);
                }
                Vec3 base = t.position();
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8 + sl.random.nextDouble() * 0.4;
                    double d = 0.5 + sl.random.nextDouble() * 0.6;
                    Vec3 sp = base.add(Math.cos(a) * d, 0.05, Math.sin(a) * d);
                    FaUtil.column(sl, sp, FaUtil.dustFade(0x3E6B2A, 0x1F3A14, 0.8F), 1.1 + sl.random.nextDouble() * 0.6, 6, 0.02, 0);
                }
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SWEET_BERRY_BUSH.defaultBlockState()),
                        base.x, base.y + 0.6, base.z, 20, 0.5, 0.4, 0.5, 0.1);
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, base.x, base.y + t.getBbHeight() * 0.6, base.z, 3, 0.3, 0.2, 0.3, 0.1);
                FaUtil.sound(sl, base, SoundEvents.SWEET_BERRY_BUSH_BREAK, 1.2F, 0.6F);
                FaUtil.sound(sl, base, SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 1.2F);
            }
        }
    }

    /** Vinhas: vinhas agarram o alvo e o prendem ao chão (Enraizado); puxam para baixo quem estiver voando. */
    public static class Vines extends EffectGlyph {
        public Vines() {
            super("vines", 12);
            element(Element.NATURE);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 4.0)) {
                FaUtil.hurt(ctx, t, 2.0F, stats, null);
                t.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, stats.duration(60), 0), ctx.caster());
                if (!t.onGround()) FaUtil.addMotion(t, new Vec3(0, -0.7 * FaUtil.kbFactor(t), 0));
                FaUtil.spiral(sl, t, VINE_BITS, 3, 36);
                FaUtil.spiral(sl, t, FaUtil.dust(0x3F8F2A, 1.0F), 3, 30);
                sl.sendParticles(LEAF_BITS, t.getX(), t.getY() + 0.2, t.getZ(), 10, 0.4, 0.1, 0.4, 0.05);
                FaUtil.sound(sl, t.position(), SoundEvents.CAVE_VINES_PLACE, 1.2F, 0.7F);
                FaUtil.sound(sl, t.position(), SoundEvents.VINE_STEP, 1.0F, 0.6F);
            }
        }
    }

    /** Esporos: nuvem de esporos que envenena — e às vezes faz dormir até levar dano. */
    public static class Spores extends EffectGlyph {
        public Spores() {
            super("spores", 14);
            element(Element.NATURE);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 1.5F, stats, null);
                // Sono OU veneno: quem dorme não é envenenado (e dano de veneno não acorda — ver FaEvents)
                float chance = stats.inverted ? 1.0F : 0.35F + 0.1F * Math.max(0, stats.amplify);
                if (t.isAlive() && !FaUtil.isBoss(t) && sl.random.nextFloat() < chance) {
                    t.addEffect(new MobEffectInstance(FaRegistries.SPORE_SLEEP, stats.duration(80), 0), ctx.caster());
                    if (t instanceof Player) t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), ctx.caster());
                }
                if (!t.hasEffect(FaRegistries.SPORE_SLEEP)) {
                    t.addEffect(new MobEffectInstance(MobEffects.POISON, stats.duration(100), FaUtil.amp(ctx, stats)), ctx.caster());
                }
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
                sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y, c.z, 30, 0.6, 0.5, 0.6, 0.02);
                sl.sendParticles(FaUtil.dust(0x9C6BD8, 1.4F), c.x, c.y, c.z, 14, 0.5, 0.4, 0.5, 0);
            }
            cloud(ctx, stats, target.position());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            cloud(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private void cloud(SpellContext ctx, SpellStats stats, Vec3 at) {
            if (!FaUtil.once(ctx, "spores")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 4);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.SPORE_CLOUD, c, stats.radius(2.5F), stats.duration(80), null);
            FaUtil.sound(sl, c, SoundEvents.PUFFER_FISH_BLOW_UP, 1.0F, 0.6F);
            FaUtil.sound(sl, c, SoundEvents.MOSS_BREAK, 1.0F, 0.8F);
        }
    }

    /** Florescer: uma explosão de flores cura o alvo e os aliados em volta e enche o chão de flores. */
    public static class Bloom extends EffectGlyph {
        private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY,
                Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP};

        public Bloom() {
            super("bloom", 16);
            element(Element.NATURE);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Invertido: pólen tóxico
                FaUtil.hurt(ctx, le, 4.0F, stats, null);
                le.addEffect(new MobEffectInstance(MobEffects.POISON, stats.duration(80), 0), ctx.caster());
                FaUtil.around(sl, le, ParticleTypes.SPORE_BLOSSOM_AIR, 20, 0.02);
                return;
            }
            ctx.heal(le, 6.0F, stats);
            le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(60), 0), ctx.caster());
            FaUtil.around(sl, le, ParticleTypes.CHERRY_LEAVES, 12, 0);
            if (!FaUtil.once(ctx, "bloom")) return;
            float r = stats.radius(4.0F);
            for (LivingEntity a : sl.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(r),
                    a -> a != le && a.isAlive() && (a == ctx.caster() || ctx.isAlly(a)) && a.distanceToSqr(le) <= r * r)) {
                ctx.heal(a, 4.0F, stats);
                FaUtil.around(sl, a, ParticleTypes.CHERRY_LEAVES, 6, 0);
            }
            plantFlowers(ctx, le.blockPosition(), r);
            Vec3 c = le.position().add(0, 0.2, 0);
            FaUtil.shockwave(sl, c, FaUtil.dustFade(0xFFB7E0, 0x7FDB6A, 1.2F), 36, 0.3);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1, c.z, 20, r * 0.4, 0.6, r * 0.4, 0);
            FaUtil.sound(sl, c, SoundEvents.FLOWERING_AZALEA_PLACE, 1.2F, 0.9F);
            FaUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        }

        private void plantFlowers(SpellContext ctx, BlockPos center, float r) {
            ServerLevel sl = ctx.level();
            int ri = Mth.ceil(r);
            int planted = 0;
            for (int i = 0; i < 24 && planted < 8; i++) {
                int dx = sl.random.nextInt(ri * 2 + 1) - ri, dz = sl.random.nextInt(ri * 2 + 1) - ri;
                if (dx * dx + dz * dz > r * r) continue;
                BlockPos g = FaUtil.groundBlock(sl, center.offset(dx, 0, dz), 1, 3);
                if (g == null || !sl.getBlockState(g).is(Blocks.GRASS_BLOCK)) continue;
                BlockPos at = g.above();
                if (!sl.getBlockState(at).isAir() || !FaUtil.canPlace(ctx, at)) continue;
                BlockState flower = FLOWERS[sl.random.nextInt(FLOWERS.length)].defaultBlockState();
                if (!flower.canSurvive(sl, at)) continue;
                sl.setBlock(at, flower, Block.UPDATE_ALL);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
                planted++;
            }
        }
    }

    /** Fotossíntese: por um bom tempo, ficar sob o sol regenera mana e um pouco de vida. */
    public static class Photosynthesis extends EffectGlyph {
        public Photosynthesis() {
            super("photosynthesis", 14);
            element(Element.NATURE);
            tier(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            le.addEffect(new MobEffectInstance(FaRegistries.PHOTOSYNTHESIS, stats.duration(1200), FaUtil.amp(ctx, stats)), ctx.caster());
            Vec3 top = le.position().add(0, le.getBbHeight() + 4, 0);
            FaUtil.line(sl, top, le.position().add(0, le.getBbHeight(), 0), ParticleTypes.END_ROD, 2);
            FaUtil.spiral(sl, le, FaUtil.dustFade(0xFFF07A, 0x58C84A, 1.1F), 2, 30);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 12, 0.4, 0.5, 0.4, 0);
            FaUtil.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.5F);
            FaUtil.sound(sl, le.position(), SoundEvents.AZALEA_LEAVES_PLACE, 1.0F, 1.2F);
            if (le instanceof ServerPlayer sp && !FaEffects.Photosynthesis.inSunlight(sp)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.photosynthesis_shade"), true);
            }
        }
    }

    /** Invocar Ent: um guardião-árvore brota do chão para lutar ao seu lado. */
    public static class SummonEnt extends EffectGlyph {
        public SummonEnt() {
            super("summon_ent", 30);
            element(Element.NATURE);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 near;
            if (FaUtil.isSelf(ctx, target)) {
                near = target.position().add(FaUtil.flatLook(ctx).scale(2.5));
            } else {
                Vec3 back = FaUtil.awayFromCaster(ctx, target).scale(-1.8);
                near = target.position().add(back);
            }
            SummonedEnt ent = summon(ctx, stats, near);
            if (ent != null && !FaUtil.isSelf(ctx, target) && target instanceof LivingEntity le && !ctx.isAlly(le)) {
                ent.setTarget(le);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            summon(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private SummonedEnt summon(SpellContext ctx, SpellStats stats, Vec3 near) {
            if (!FaUtil.once(ctx, "summon_ent")) return null;
            SummonedEnt ent = SummonedEnt.summon(ctx, near, stats.duration(900), Math.min(3.0F, FaUtil.power(ctx, stats)));
            if (ent != null && ctx.caster() instanceof Mob m && m.getTarget() != null) ent.setTarget(m.getTarget());
            return ent;
        }
    }

    private NatureGlyphs() {}
}
