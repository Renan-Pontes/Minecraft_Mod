package com.renanpontes.isekaimahou.feature.fire_water;

import java.util.Optional;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fire_water.entity.ElementalFieldEntity;
import com.renanpontes.isekaimahou.feature.fire_water.entity.FwSummon;
import com.renanpontes.isekaimahou.feature.fire_water.entity.SummonedSalamander;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de efeito de FOGO.
 * Tier 1: Incendiar, Explosão · Tier 2: Brasa Persistente, Propulsão, Fumaça ·
 * Tier 3: Combustão, Muralha de Fogo, Fundir + Invocar Salamandra · Tier 4: Superaquecer, Chama Azul, Derreter Gelo ·
 * Tier 5: Renascer da Fênix (lendário).
 *
 * Regra do módulo: efeito ofensivo lançado em Si Mesmo vira uma "nova" nos inimigos em volta (nunca machuca quem conjurou).
 */
public final class FireGlyphs {

    public static void register() {
        GlyphRegistry.register(new Ignite());
        GlyphRegistry.register(new ExplosionEffect());
        GlyphRegistry.register(new Ember());
        GlyphRegistry.register(new Propulsion());
        GlyphRegistry.register(new Smoke());
        GlyphRegistry.register(new Combustion());
        GlyphRegistry.register(new FireWall());
        GlyphRegistry.register(new Smelt());
        GlyphRegistry.register(new SummonSalamander());
        GlyphRegistry.register(new Overheat());
        GlyphRegistry.register(new BlueFlame());
        GlyphRegistry.register(new Melt());
        GlyphRegistry.register(new PhoenixRebirth());
    }

    /** Acende fogo no bloco (ou fogueira/vela apagada). Usado por Incendiar e Chama Azul com Sensível. */
    static void lightBlock(SpellContext ctx, BlockHitResult hit) {
        ServerLevel sl = ctx.level();
        BlockPos pos = hit.getBlockPos();
        BlockState s = sl.getBlockState(pos);
        if ((CampfireBlock.canLight(s) || CandleBlock.canLight(s) || CandleCakeBlock.canLight(s)) && FwUtil.canPlace(ctx, pos)) {
            sl.setBlock(pos, s.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
            FwUtil.sound(sl, Vec3.atCenterOf(pos), SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            return;
        }
        BlockPos firePos = pos.relative(hit.getDirection());
        // Nunca acende fogo dentro de quem conjurou (Si Mesmo + Sensível)
        if (ctx.caster() != null && ctx.caster().getBoundingBox().intersects(new AABB(firePos))) return;
        if (BaseFireBlock.canBePlacedAt(sl, firePos, hit.getDirection()) && FwUtil.canPlace(ctx, firePos)) {
            sl.setBlock(firePos, BaseFireBlock.getState(sl, firePos), Block.UPDATE_ALL_IMMEDIATE);
            FwUtil.sound(sl, Vec3.atCenterOf(firePos), SoundEvents.FIRECHARGE_USE, 0.8F, 1.0F);
        }
    }

    static boolean burning(LivingEntity e) {
        return e.isOnFire() || e.hasEffect(FwRegistries.LINGERING_EMBER) || e.hasEffect(FwRegistries.AZURE_FLAME);
    }

    static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    // =====================================================================================
    //  Tier 1
    // =====================================================================================

    /** Incendiar: dano 3 + fogo. Em Si Mesmo: corpo em chamas que queima quem está colado. Sensível: acende blocos. */
    public static class Ignite extends EffectGlyph {
        public Ignite() {
            super("ignite", 8);
            element(Element.FIRE);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float secs = stats.duration(80) / 20F;
            boolean self = FwUtil.isSelf(ctx, target);
            if (self) FwUtil.ring(sl, target.position().add(0, 0.2, 0), ParticleTypes.FLAME, stats.radius(2.5F), 28, 0.02);
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                FwUtil.hurt(ctx, e, 3.0F, stats, null);
                e.igniteForSeconds(secs);
                FwUtil.around(sl, e, ParticleTypes.FLAME, 14, 0.04);
                sl.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.2, 0.2, 0.2, 0);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.FIRECHARGE_USE, 0.6F, 1.2F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            lightBlock(ctx, hit);
        }
    }

    /**
     * Explosão: dano em área + empurrão, SEM quebrar blocos. Explode também ao bater no chão.
     * Com Sensível quebra blocos frágeis (vidro, folhas, plantas).
     * v0.8: com Amplificar ≥ 2 ou Colossal no glifo (ou Colossal na magia), abre uma cratera pequena (BREAK, raio
     * ~2–4,5) pela API {@code Destruction} do núcleo — respeita a regra de destruição, cidades e Provações.
     */
    public static class ExplosionEffect extends EffectGlyph {
        public ExplosionEffect() {
            super("explosion", 14);
            element(Element.FIRE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            boolean self = FwUtil.isSelf(ctx, target);
            blast(ctx, self ? target.position().add(0, 0.4, 0) : center(target), self ? null : target, stats, false);
            // Si Mesmo (nova) nunca abre buraco debaixo de quem conjurou
            if (!self) crater(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            boolean sensitive = stats.sensitive || ctx.formStats().sensitive;
            blast(ctx, hit.getLocation(), null, stats, sensitive);
            crater(ctx, hit.getLocation(), stats);
        }

        /** Colossal (do módulo legends_a), lido pelo id: sem ele carregado, só o Amplificar conta. */
        private static final net.minecraft.resources.ResourceLocation COLOSSAL =
                com.renanpontes.isekaimahou.IsekaiMahou.id("colossal");

        /** Explosão forte (Amplificar ≥ 2 ou Colossal): cratera pequena de blocos quebrados (BREAK) no chão do impacto. */
        static void crater(SpellContext ctx, Vec3 at, SpellStats stats) {
            boolean colossal = stats.custom(COLOSSAL) > 0 || ctx.spell().anyStats(st -> st.custom(COLOSSAL) > 0);
            if (stats.amplify < 2 && !colossal) return;
            ServerLevel sl = ctx.level();
            if (!com.renanpontes.isekaimahou.core.api.Destruction.allowed(sl, ctx.caster())) return;
            // Um buraco por ponto de impacto por conjuração (formas de área chamam várias vezes no mesmo tick)
            if (!FwUtil.once(ctx, "explosion_crater@" + BlockPos.containing(at).asLong())) return;
            Vec3 ground = at;
            BlockPos p = BlockPos.containing(at);
            for (int i = 0; i < 4 && sl.isLoaded(p) && sl.getBlockState(p).getCollisionShape(sl, p).isEmpty(); i++) {
                p = p.below();
                ground = new Vec3(at.x, p.getY() + 1.0, at.z);
            }
            if (sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()) return;
            float r = Mth.clamp(1.5F + 0.6F * Math.max(0, stats.amplify) + (colossal ? 1.5F : 0F) + 0.35F * Math.max(0, stats.aoe), 2.0F, 4.5F);
            net.minecraft.world.entity.LivingEntity caster = ctx.caster();
            if (caster != null && caster.level() == sl && caster.position().distanceToSqr(ground) < (r + 1.0) * (r + 1.0)) return;
            int n = com.renanpontes.isekaimahou.core.api.Destruction.sphere(sl, ground.add(0, -r * 0.45, 0), r, caster,
                    com.renanpontes.isekaimahou.core.api.Destruction.Mode.BREAK);
            if (n <= 0) return;
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 0.3, ground.z, 8, r * 0.4, 0.2, r * 0.4, 0.02);
            FwUtil.sound(sl, ground, SoundEvents.GENERIC_EXPLODE.value(), 1.8F, 0.7F);
        }

        static void blast(SpellContext ctx, Vec3 c, Entity main, SpellStats stats, boolean breakBlocks) {
            ServerLevel sl = ctx.level();
            float r = stats.radius(2.5F);
            for (LivingEntity e : ctx.enemiesAround(c, r + 0.5)) {
                if (!FwUtil.firstHit(ctx, "explosion", e)) continue;
                double d = center(e).distanceTo(c);
                float falloff = e == main ? 1.0F : (float) Math.max(0.4, 1.0 - d / (r + 0.5) * 0.6);
                FwUtil.hurt(ctx, e, 6.0F * falloff, stats, null);
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? FwUtil.flatLook(ctx) : push.normalize();
                double kb = (0.5 + 0.35 * Math.min(stats.power(), 2.5)) * falloff;
                FwUtil.addMotion(e, push.scale(kb).add(0, 0.25 + 0.1 * falloff, 0));
            }
            if (breakBlocks) breakWeak(ctx, c, Math.min(r, 3.5F));
            // Si Mesmo chama no corpo e no bloco do pé no mesmo tick: um estouro só de visual/som
            if (!FwUtil.once(ctx, "explosion_fx@" + BlockPos.containing(c).asLong())) return;
            sl.sendParticles(r >= 3.5F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, Math.round(r * 2), r * 0.35, r * 0.3, r * 0.35, 0);
            FwUtil.shockwave(sl, c, ParticleTypes.FLAME, 30, 0.1 * r);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 14, r * 0.3, r * 0.2, r * 0.3, 0.03);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, r * 0.2, 0.2, r * 0.2, 0);
            FwUtil.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.0F);
        }

        /** Quebra (com drops) só blocos muito frágeis: dureza até 0.35. */
        static void breakWeak(SpellContext ctx, Vec3 c, float r) {
            ServerLevel sl = ctx.level();
            BlockPos center = BlockPos.containing(c);
            int ir = Mth.ceil(r), broken = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-ir, -ir, -ir), center.offset(ir, ir, ir))) {
                if (p.distToCenterSqr(c) > r * r) continue;
                BlockState s = sl.getBlockState(p);
                if (s.isAir() || s.getBlock() instanceof LiquidBlock || s.getBlock() instanceof BaseFireBlock) continue;
                float h = s.getDestroySpeed(sl, p);
                if (h < 0 || h > 0.35F) continue;
                BlockPos fixed = p.immutable();
                if (!FwUtil.canBreak(ctx, fixed)) continue;
                sl.destroyBlock(fixed, true, ctx.caster());
                if (++broken >= 40) return;
            }
        }
    }

    // =====================================================================================
    //  Tier 2
    // =====================================================================================

    /** Brasa Persistente: dano contínuo que reacende o alvo a cada segundo (água apaga). */
    public static class Ember extends EffectGlyph {
        public Ember() {
            super("ember", 12);
            element(Element.FIRE);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int amp = FwUtil.amp(ctx, stats);
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                FwUtil.hurt(ctx, e, 2.0F, stats, null);
                e.igniteForSeconds(3.0F);
                e.addEffect(new MobEffectInstance(FwRegistries.LINGERING_EMBER, stats.duration(160), amp), ctx.caster());
                FwUtil.around(sl, e, ParticleTypes.SMALL_FLAME, 16, 0.03);
                sl.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4, 0.25, 0.3, 0.25, 0);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.LAVA_POP, 1.0F, 0.9F);
            FwUtil.sound(sl, target.position(), SoundEvents.FIRE_AMBIENT, 1.0F, 1.2F);
        }
    }

    /**
     * Propulsão: em Si Mesmo, impulso para onde você olha (no chão) ou para cima (no ar), sem dano de queda depois.
     * Em outro alvo: arremessa o alvo na direção em que você olha. Inverter: impulso para trás.
     */
    public static class Propulsion extends EffectGlyph {
        public Propulsion() {
            super("propulsion", 8);
            element(Element.FIRE);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float p = Math.min(stats.power(), 2.2F);
            Vec3 v;
            if (FwUtil.isSelf(ctx, target)) {
                Vec3 look = ctx.look();
                Vec3 cur = target.getDeltaMovement();
                if (target.onGround()) {
                    v = new Vec3(look.x, Math.max(0.35, look.y + 0.35), look.z).normalize().scale(1.25 * p);
                } else {
                    v = new Vec3(look.x * 0.5 * p, 0.95 * p, look.z * 0.5 * p);
                }
                if (stats.inverted) v = new Vec3(-v.x, v.y, -v.z);
                FwUtil.setMotion(target, new Vec3(cur.x * 0.4 + v.x, v.y, cur.z * 0.4 + v.z));
                if (target instanceof LivingEntity le) FwEvents.cushion(le, 120);
            } else {
                Vec3 dir = FwUtil.flatLook(ctx);
                if (stats.inverted) dir = dir.scale(-1);
                double kbRes = target instanceof LivingEntity le
                        ? le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) : 0;
                double k = Math.max(0.2, 1.0 - kbRes);
                v = dir.scale(1.1 * p * k).add(0, 0.55 * p * k, 0);
                FwUtil.addMotion(target, v);
                if (target instanceof LivingEntity le && ctx.isAlly(le)) FwEvents.cushion(le, 120);
            }
            // Jato de fogo saindo na direção oposta ao impulso
            Vec3 base = target.position().add(0, 0.2, 0);
            Vec3 back = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(-1) : new Vec3(0, -1, 0);
            for (int i = 0; i < 14; i++) {
                double j = 0.25;
                sl.sendParticles(i % 4 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, base.x, base.y, base.z, 0,
                        back.x + (sl.random.nextDouble() - 0.5) * j, back.y + (sl.random.nextDouble() - 0.5) * j,
                        back.z + (sl.random.nextDouble() - 0.5) * j, 0.25 + sl.random.nextDouble() * 0.2);
            }
            FwUtil.sound(sl, base, SoundEvents.FIRECHARGE_USE, 1.0F, 1.3F);
            FwUtil.sound(sl, base, SoundEvents.BLAZE_SHOOT, 0.6F, 1.5F);
        }
    }

    /** Fumaça: nuvem que cega os inimigos (e faz mobs perderem o alvo). */
    public static class Smoke extends EffectGlyph {
        public Smoke() {
            super("smoke", 10);
            element(Element.FIRE);
            tier(2);
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
            if (!FwUtil.once(ctx, "smoke")) return;
            ElementalFieldEntity.spawn(ctx, ElementalFieldEntity.Kind.SMOKE, FwUtil.ground(ctx.level(), at, 4),
                    stats.radius(3.0F), stats.duration(120), 0, FwUtil.amp(ctx, stats), 0);
        }
    }

    // =====================================================================================
    //  Tier 3
    // =====================================================================================

    /** Combustão: quem está em chamas EXPLODE (dano alto + área). Quem não está só começa a queimar. */
    public static class Combustion extends EffectGlyph {
        public Combustion() {
            super("combustion", 20);
            element(Element.FIRE);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            if (FwUtil.isSelf(ctx, target)) {
                // Em Si Mesmo: detona todos os inimigos queimando ao redor
                float r = stats.radius(6.0F);
                FwUtil.ring(sl, target.position().add(0, 0.3, 0), ParticleTypes.FLAME, r, 40, 0.01);
                for (LivingEntity e : ctx.enemiesAround(target.position(), r)) if (burning(e)) detonate(ctx, e, stats);
                FwUtil.sound(sl, target.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            if (burning(le)) {
                detonate(ctx, le, stats);
            } else {
                FwUtil.hurt(ctx, le, 2.0F, stats, null);
                le.igniteForSeconds(3.0F);
                FwUtil.around(sl, le, ParticleTypes.SMOKE, 10, 0.02);
                FwUtil.around(sl, le, ParticleTypes.FLAME, 6, 0.02);
                FwUtil.sound(sl, le.position(), SoundEvents.FIRECHARGE_USE, 0.6F, 1.5F);
            }
        }

        private static void detonate(SpellContext ctx, LivingEntity le, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = center(le);
            boolean azure = le.hasEffect(FwRegistries.AZURE_FLAME);
            le.clearFire();
            le.removeEffect(FwRegistries.LINGERING_EMBER);
            le.removeEffect(FwRegistries.AZURE_FLAME);
            FwUtil.hurt(ctx, le, azure ? 12.0F : 9.0F, stats, null);
            float r = stats.radius(2.5F);
            for (LivingEntity e : ctx.enemiesAround(c, r)) {
                if (e == le || !FwUtil.firstHit(ctx, "combustion", e)) continue;
                FwUtil.hurt(ctx, e, 4.5F, stats, null);
                e.igniteForSeconds(3.0F);
                Vec3 push = e.position().subtract(le.position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) FwUtil.addMotion(e, push.normalize().scale(0.6).add(0, 0.3, 0));
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0);
            FwUtil.shockwave(sl, c, azure ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, 36, 0.3);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 12, 0.5, 0.5, 0.5, 0.04);
            FwUtil.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.2F);
            FwUtil.sound(sl, c, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
        }
    }

    /** Muralha de Fogo: linha de chamas no chão, perpendicular ao seu olhar, que queima e repele inimigos e some depois. */
    public static class FireWall extends EffectGlyph {
        public FireWall() {
            super("fire_wall", 18);
            element(Element.FIRE);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 at = FwUtil.isSelf(ctx, target) ? target.position().add(FwUtil.flatLook(ctx).scale(3.0)) : target.position();
            spawn(ctx, at, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, outOf(hit), stats);
        }

        private void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!FwUtil.once(ctx, "fire_wall")) return;
            ServerLevel sl = ctx.level();
            Vec3 pos = FwUtil.ground(sl, at, 4);
            // Debaixo d'água as chamas não pegam: só vapor e chiado
            if (sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER)) {
                sl.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z, 24, stats.radius(3.5F) * 0.4, 0.3, 0.6, 0.02);
                sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, pos.x, pos.y + 0.5, pos.z, 20, 1.2, 0.3, 0.6, 0.05);
                FwUtil.sound(sl, pos, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
                return;
            }
            float yaw = ctx.caster() != null ? ctx.caster().getYRot() : 0;
            ElementalFieldEntity.spawn(ctx, ElementalFieldEntity.Kind.FIRE_WALL, pos,
                    stats.radius(3.5F), stats.duration(160), 3.0F * stats.power(), FwUtil.amp(ctx, stats), yaw);
        }
    }

    /**
     * Fundir: cozinha como uma fornalha. Blocos: areia → vidro, pedregulho → pedra, minério → lingote (solto no chão)...
     * Itens no chão em volta também são cozidos (carne crua, minérios brutos...). Em criaturas: dano de calor.
     */
    public static class Smelt extends EffectGlyph {
        public Smelt() {
            super("smelt", 12);
            element(Element.FIRE);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        private record Smelted(ItemStack result, float xp) {}

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            if (!FwUtil.isSelf(ctx, target) && target instanceof LivingEntity le) {
                FwUtil.hurt(ctx, le, 4.0F, stats, null);
                le.igniteForSeconds(2.0F);
                FwUtil.around(sl, le, ParticleTypes.FLAME, 10, 0.03);
            }
            if (cookItems(ctx, target.position(), 1.5 + Math.max(0, stats.aoe)) > 0) crackle(sl, target.position());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int r = Mth.clamp(stats.aoe, 0, 2);
            BlockPos c = hit.getBlockPos();
            int done = 0;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                if (smeltBlock(ctx, p.immutable())) done++;
            }
            done += cookItems(ctx, hit.getLocation(), 1.5 + r);
            if (done > 0) crackle(sl, hit.getLocation());
        }

        private static void crackle(ServerLevel sl, Vec3 at) {
            FwUtil.sound(sl, at, SoundEvents.FURNACE_FIRE_CRACKLE, 1.2F, 1.0F);
            FwUtil.sound(sl, at, SoundEvents.FIRE_AMBIENT, 0.8F, 1.3F);
        }

        private static Optional<Smelted> smelt(ServerLevel sl, ItemStack stack) {
            SingleRecipeInput in = new SingleRecipeInput(stack);
            return sl.getRecipeManager().getRecipeFor(RecipeType.SMELTING, in, sl)
                    .map(h -> new Smelted(h.value().assemble(in, sl.registryAccess()), h.value().getExperience()))
                    .filter(s -> !s.result().isEmpty());
        }

        private static boolean smeltBlock(SpellContext ctx, BlockPos pos) {
            ServerLevel sl = ctx.level();
            BlockState s = sl.getBlockState(pos);
            if (s.isAir() || s.hasBlockEntity()) return false;
            Item item = s.getBlock().asItem();
            if (item == Items.AIR) return false;
            Optional<Smelted> res = smelt(sl, new ItemStack(item));
            if (res.isEmpty() || !FwUtil.canBreak(ctx, pos)) return false;
            ItemStack out = res.get().result();
            if (out.getItem() instanceof BlockItem bi && out.getCount() == 1 && bi.getBlock() != s.getBlock()) {
                sl.setBlockAndUpdate(pos, bi.getBlock().defaultBlockState());
            } else {
                sl.removeBlock(pos, false);
                Block.popResource(sl, pos, out.copy());
            }
            awardXp(sl, Vec3.atCenterOf(pos), res.get().xp());
            Vec3 c = Vec3.atCenterOf(pos);
            sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.35, 0.35, 0.35, 0.02);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.4, c.z, 3, 0.25, 0.2, 0.25, 0.02);
            if (sl.random.nextInt(3) == 0) sl.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 1, 0.2, 0, 0.2, 0);
            return true;
        }

        /** Cozinha os itens soltos no chão. Retorna quantas pilhas foram cozidas. */
        private static int cookItems(SpellContext ctx, Vec3 c, double radius) {
            ServerLevel sl = ctx.level();
            int cooked = 0;
            for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(radius), ItemEntity::isAlive)) {
                // Cada item é cozido uma vez por conjuração (senão pedregulho viraria pedra lisa na mesma tick)
                if (!FwUtil.firstHit(ctx, "smelt_item", ie)) continue;
                ItemStack st = ie.getItem();
                Optional<Smelted> res = smelt(sl, st.copyWithCount(1));
                if (res.isEmpty()) continue;
                ItemStack one = res.get().result();
                int total = one.getCount() * st.getCount();
                int max = one.getMaxStackSize();
                ie.setItem(one.copyWithCount(Math.min(total, max)));
                for (int left = total - max; left > 0; left -= max) {
                    ItemEntity extra = new ItemEntity(sl, ie.getX(), ie.getY(), ie.getZ(), one.copyWithCount(Math.min(left, max)));
                    extra.setDefaultPickUpDelay();
                    sl.addFreshEntity(extra);
                }
                awardXp(sl, ie.position(), res.get().xp() * st.getCount());
                sl.sendParticles(ParticleTypes.FLAME, ie.getX(), ie.getY() + 0.2, ie.getZ(), 6, 0.15, 0.1, 0.15, 0.02);
                sl.sendParticles(ParticleTypes.SMOKE, ie.getX(), ie.getY() + 0.3, ie.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                cooked++;
            }
            return cooked;
        }

        private static void awardXp(ServerLevel sl, Vec3 at, float xp) {
            int whole = Mth.floor(xp);
            if (sl.random.nextFloat() < xp - whole) whole++;
            if (whole > 0) ExperienceOrb.award(sl, at, whole);
        }
    }

    /** Invocar Salamandra: lagarto de fogo aliado que cospe chamas (30 s). */
    public static class SummonSalamander extends EffectGlyph {
        public SummonSalamander() {
            super("summon_salamander", 30);
            element(Element.FIRE);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            boolean self = FwUtil.isSelf(ctx, target);
            Vec3 near = self ? target.position().add(FwUtil.flatLook(ctx).scale(1.6)) : target.position();
            SummonedSalamander s = summon(ctx, near, stats);
            if (s != null && !self && target instanceof LivingEntity le && !ctx.isAlly(le)) s.setTarget(le);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            summon(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
        }

        private SummonedSalamander summon(SpellContext ctx, Vec3 near, SpellStats stats) {
            if (!FwUtil.once(ctx, "summon_salamander")) return null;
            return FwSummon.summon(ctx, FwRegistries.SALAMANDER.get(), near, stats.duration(600), FwUtil.power(ctx, stats), 2);
        }
    }

    // =====================================================================================
    //  Tier 4
    // =====================================================================================

    /** Superaquecer: o calor gasta a durabilidade da armadura e deixa o alvo fraco e lento. Sem armadura: mais dano. */
    public static class Overheat extends EffectGlyph {
        private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        public Overheat() {
            super("overheat", 22);
            element(Element.FIRE);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                int pieces = 0;
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack st = e.getItemBySlot(slot);
                    if (st.isEmpty() || !st.isDamageableItem()) continue;
                    int wear = Math.round(Math.max(4F, st.getMaxDamage() * 0.12F) * stats.power());
                    st.hurtAndBreak(wear, e, slot);
                    pieces++;
                }
                FwUtil.hurt(ctx, e, pieces == 0 ? 6.0F : 3.0F, stats, null);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(160), 1), ctx.caster());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(60), 0), ctx.caster());
                FwUtil.around(sl, e, FwUtil.dust(0xFF3B1A, 1.3F), 24, 0);
                FwUtil.around(sl, e, ParticleTypes.SMOKE, 12, 0.03);
                sl.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.25, 0.3, 0.25, 0);
                FwUtil.sound(sl, e.position(), SoundEvents.LAVA_POP, 1.2F, 0.8F);
                FwUtil.sound(sl, e.position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.6F);
            }
        }
    }

    /** Chama Azul: fogo de alma fortíssimo; queima até criaturas de fogo e não apaga na água. */
    public static class BlueFlame extends EffectGlyph {
        public BlueFlame() {
            super("blue_flame", 28);
            element(Element.FIRE);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int amp = FwUtil.amp(ctx, stats);
            if (FwUtil.isSelf(ctx, target)) {
                FwUtil.ring(sl, target.position().add(0, 0.2, 0), ParticleTypes.SOUL_FIRE_FLAME, stats.radius(3.0F), 32, 0.02);
            }
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                FwUtil.hurt(ctx, e, 8.0F, stats, null);
                e.addEffect(new MobEffectInstance(FwRegistries.AZURE_FLAME, stats.duration(120), amp), ctx.caster());
                FwUtil.around(sl, e, ParticleTypes.SOUL_FIRE_FLAME, 28, 0.05);
                FwUtil.around(sl, e, ParticleTypes.SOUL, 4, 0.03);
                FwUtil.around(sl, e, FwUtil.dust(0x3FC8FF, 1.4F), 12, 0);
            }
            FwUtil.sound(sl, target.position(), SoundEvents.SOUL_ESCAPE.value(), 1.4F, 0.8F);
            FwUtil.sound(sl, target.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.6F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            lightBlock(ctx, hit);
        }
    }

    /** Derreter Gelo: derrete gelo e neve em volta, descongela aliados e queima criaturas do frio. */
    public static class Melt extends EffectGlyph {
        public Melt() {
            super("melt", 10);
            element(Element.FIRE);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FwUtil.offensiveTargets(ctx, target, stats.radius(3.0F))) {
                e.removeEffect(CoreRegistries.FROZEN);
                e.setTicksFrozen(0);
                boolean cold = e.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
                FwUtil.hurt(ctx, e, cold ? 7.5F : 3.0F, stats, null);
                FwUtil.around(sl, e, ParticleTypes.CLOUD, 8, 0.03);
                FwUtil.around(sl, e, ParticleTypes.DRIPPING_WATER, 8, 0);
            }
            meltAround(ctx, target.blockPosition(), stats.radius(2.5F));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            meltAround(ctx, hit.getBlockPos(), stats.radius(3.0F));
        }

        private static void meltAround(SpellContext ctx, BlockPos center, float r) {
            ServerLevel sl = ctx.level();
            boolean hot = sl.dimensionType().ultraWarm();
            // Descongela os aliados próximos
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(r), ctx::isAlly)) {
                if (e.hasEffect(CoreRegistries.FROZEN) || e.getTicksFrozen() > 0) {
                    e.removeEffect(CoreRegistries.FROZEN);
                    e.setTicksFrozen(0);
                    FwUtil.around(sl, e, ParticleTypes.CLOUD, 6, 0.02);
                }
            }
            int ir = Mth.ceil(r), melted = 0;
            Vec3 c = Vec3.atCenterOf(center);
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-ir, -ir, -ir), center.offset(ir, ir, ir))) {
                if (p.distToCenterSqr(c) > r * r) continue;
                BlockState s = sl.getBlockState(p);
                BlockState to;
                if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE) || s.is(Blocks.PACKED_ICE)) {
                    to = hot ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
                } else if (s.is(Blocks.BLUE_ICE)) {
                    to = Blocks.PACKED_ICE.defaultBlockState();
                } else if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
                    to = Blocks.AIR.defaultBlockState();
                } else {
                    continue;
                }
                BlockPos fixed = p.immutable();
                if (!FwUtil.canBreak(ctx, fixed)) continue;
                sl.setBlockAndUpdate(fixed, to);
                Vec3 pc = Vec3.atCenterOf(fixed);
                sl.sendParticles(ParticleTypes.CLOUD, pc.x, pc.y + 0.3, pc.z, 2, 0.25, 0.2, 0.25, 0.02);
                sl.sendParticles(ParticleTypes.DRIPPING_WATER, pc.x, pc.y, pc.z, 2, 0.3, 0.3, 0.3, 0);
                if (++melted >= 160) break;
            }
            if (melted > 0) {
                FwUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.7F, 1.4F);
                FwUtil.sound(sl, c, SoundEvents.LAVA_EXTINGUISH, 0.5F, 1.6F);
            }
        }
    }

    // =====================================================================================
    //  Tier 5 (lendário)
    // =====================================================================================

    /**
     * Renascer da Fênix: bênção que, se o alvo morrer enquanto durar, o faz renascer com metade da vida numa
     * explosão de chamas. Afeta você e aliados. Inverter (num inimigo): brasas amaldiçoadas.
     */
    public static class PhoenixRebirth extends EffectGlyph {
        public PhoenixRebirth() {
            super("phoenix_rebirth", 70);
            element(Element.FIRE);
            legendary();
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            int amp = FwUtil.amp(ctx, stats);
            if (stats.inverted && !ctx.isAlly(le)) {
                le.addEffect(new MobEffectInstance(FwRegistries.LINGERING_EMBER, stats.duration(400), Math.min(4, amp + 1)), ctx.caster());
                le.igniteForSeconds(5.0F);
                FwUtil.around(sl, le, ParticleTypes.SOUL_FIRE_FLAME, 20, 0.04);
                FwUtil.sound(sl, le.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.6F);
                return;
            }
            le.addEffect(new MobEffectInstance(FwRegistries.PHOENIX_BLESSING, stats.duration(1200), amp, false, true, true), ctx.caster());
            // Espiral de fogo subindo em volta do abençoado
            for (int i = 0; i < 30; i++) {
                double a = i * 0.55, h = i * 0.075;
                double r = 0.8 - i * 0.012;
                sl.sendParticles(ParticleTypes.FLAME, le.getX() + Math.cos(a) * r, le.getY() + h, le.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.005);
                if (i % 3 == 0) {
                    sl.sendParticles(FwUtil.dust(0xFFC23D, 1.2F), le.getX() + Math.cos(a + Math.PI) * r, le.getY() + h,
                            le.getZ() + Math.sin(a + Math.PI) * r, 1, 0, 0, 0, 0);
                }
            }
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 16, 0.3, 0.3, 0.3, 0.2);
            FwUtil.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
            FwUtil.sound(sl, le.position(), SoundEvents.BLAZE_SHOOT, 0.8F, 0.7F);
        }
    }

    /** Ponto de impacto levemente para fora do bloco atingido (Fumaça, Muralha). */
    static Vec3 outOf(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return hit.getLocation().add(d.getStepX() * 0.3, d.getStepY() * 0.3, d.getStepZ() * 0.3);
    }

    private FireGlyphs() {}
}
