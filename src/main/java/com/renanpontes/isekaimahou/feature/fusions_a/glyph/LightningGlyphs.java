package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Glifos de Raio (Fogo + Vento): Relâmpago, Paralisar, Corrente Elétrica, Ímã, Sobrecarga, Tempestade. */
public final class LightningGlyphs {

    public static void register() {
        GlyphRegistry.register(new LightningBolt());
        GlyphRegistry.register(new Paralyze());
        GlyphRegistry.register(new ArcLightning());
        GlyphRegistry.register(new Magnet());
        GlyphRegistry.register(new Overcharge());
        GlyphRegistry.register(new Thunderstorm());
    }

    static Vec3 chest(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    static void sparks(ServerLevel sl, Vec3 c, int n) {
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, n, 0.35, 0.35, 0.35, 0.25);
        sl.sendParticles(FaUtil.dust(0x7FDBFF, 1.0F), c.x, c.y, c.z, n / 2, 0.3, 0.3, 0.3, 0);
    }

    /** Relâmpago: um raio cai do céu no alvo (sem incendiar o mundo). */
    public static class LightningBolt extends EffectGlyph {
        public LightningBolt() {
            super("lightning_bolt", 12);
            element(Element.LIGHTNING);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            List<LivingEntity> list = FaUtil.offensiveTargets(ctx, target, 6.0);
            int n = 0;
            for (LivingEntity t : list) {
                if (n++ >= 3) break;
                strike(ctx, stats, t.position(), t);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            if (FaUtil.once(ctx, "bolt_block@" + hit.getBlockPos().asLong())) strike(ctx, stats, at, null);
        }

        private void strike(SpellContext ctx, SpellStats stats, Vec3 at, LivingEntity main) {
            ServerLevel sl = ctx.level();
            FaUtil.visualLightning(sl, at);
            sparks(sl, at.add(0, 0.6, 0), 30);
            if (main != null) {
                FaUtil.hurt(ctx, main, 7.0F, stats, null);
                main.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 10, 0), ctx.caster());
            }
            for (LivingEntity e : ctx.enemiesAround(at, 1.8)) {
                if (e != main) FaUtil.hurt(ctx, e, 2.5F, stats, null);
            }
        }
    }

    /** Paralisar: choque que trava os músculos (Atordoado); dura mais em alvos encharcados. */
    public static class Paralyze extends EffectGlyph {
        public Paralyze() {
            super("paralyze", 12);
            element(Element.LIGHTNING);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                boolean wet = t.hasEffect(CoreRegistries.SOAKED) || t.isInWaterRainOrBubble();
                int ticks = stats.duration(wet ? 75 : 50);
                FaUtil.hurt(ctx, t, 2.0F, stats, null);
                t.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, ticks, 0), ctx.caster());
                Vec3 c = chest(t);
                sparks(sl, c, 24);
                for (int i = 0; i < 3; i++) {
                    Vec3 off = c.add((sl.random.nextDouble() - 0.5) * 1.6, (sl.random.nextDouble() - 0.5) * 1.6, (sl.random.nextDouble() - 0.5) * 1.6);
                    FaUtil.zigzag(sl, c, off, FaUtil.dust(0xBFF4FF, 0.6F), ParticleTypes.ELECTRIC_SPARK);
                }
                FaUtil.sound(sl, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 1.8F);
            }
        }
    }

    /** Corrente Elétrica: o choque salta sozinho por até 3 inimigos próximos, perdendo um pouco de força a cada salto. */
    public static class ArcLightning extends EffectGlyph {
        private static final int JUMPS = 3;

        public ArcLightning() {
            super("arc_lightning", 14);
            element(Element.LIGHTNING);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Set<UUID> hit = new HashSet<>();
            LivingEntity first;
            Vec3 origin;
            if (FaUtil.isSelf(ctx, target)) {
                first = nearest(ctx, target.position(), 7.0, hit);
                origin = chest(target);
                if (first == null) {
                    sparks(sl, origin, 20);
                    return;
                }
            } else {
                if (!(target instanceof LivingEntity le)) return;
                first = le;
                origin = ctx.caster() != null ? ctx.eyePos().subtract(0, 0.2, 0) : chest(le);
            }
            hit.add(first.getUUID());
            FaUtil.zigzag(sl, origin, chest(first), FaUtil.dust(0x7FDBFF, 0.8F), ParticleTypes.ELECTRIC_SPARK);
            zap(ctx, stats, first, 5.0F);
            hop(ctx, stats, first, 5.0F * 0.8F, 1, hit);
        }

        private void hop(SpellContext ctx, SpellStats stats, LivingEntity from, float dmg, int jump, Set<UUID> hit) {
            if (jump > JUMPS) return;
            SpellScheduler.schedule(3, () -> {
                // Continua saltando mesmo que o alvo anterior tenha morrido no choque (usa a posição dele)
                LivingEntity next = nearest(ctx, from.position(), 6.0, hit);
                if (next == null) return;
                hit.add(next.getUUID());
                FaUtil.zigzag(ctx.level(), chest(from), chest(next), FaUtil.dust(0x7FDBFF, 0.8F), ParticleTypes.ELECTRIC_SPARK);
                zap(ctx, stats, next, dmg);
                hop(ctx, stats, next, dmg * 0.8F, jump + 1, hit);
            });
        }

        private void zap(SpellContext ctx, SpellStats stats, LivingEntity t, float dmg) {
            FaUtil.hurt(ctx, t, dmg, stats, null);
            sparks(ctx.level(), chest(t), 16);
            FaUtil.sound(ctx.level(), chest(t), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.5F);
        }

        private static LivingEntity nearest(SpellContext ctx, Vec3 from, double range, Set<UUID> exclude) {
            LivingEntity best = null;
            double bestD = range * range;
            for (LivingEntity e : ctx.enemiesAround(from, range)) {
                if (exclude.contains(e.getUUID())) continue;
                double d = e.distanceToSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }
    }

    /** Ímã: puxa itens de metal e criaturas de armadura para você (Invertido: repele). O choque fere mais quem veste metal. */
    public static class Magnet extends EffectGlyph {
        public Magnet() {
            super("magnet", 12);
            element(Element.LIGHTNING);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            LivingEntity caster = ctx.caster();
            Vec3 pole = caster != null ? caster.position().add(0, 1.0, 0) : target.position();
            float range = stats.radius(8.0F);
            List<LivingEntity> victims = new ArrayList<>();
            if (FaUtil.isSelf(ctx, target)) {
                victims.addAll(ctx.enemiesAround(pole, range));
            } else if (target instanceof LivingEntity le) {
                victims.add(le);
            }
            float p = Math.min(stats.power(), 2.5F);
            for (LivingEntity t : victims) {
                int armor = t.getArmorValue();
                boolean metal = armor > 0 || t instanceof IronGolem || isMetal(t.getItemBySlot(EquipmentSlot.MAINHAND));
                FaUtil.hurt(ctx, t, Math.min(9.0F, 2.0F + 0.3F * armor), stats, null);
                if (!metal) {
                    sparks(sl, chest(t), 6);
                    continue;
                }
                Vec3 d = pole.subtract(chest(t));
                if (d.lengthSqr() < 1.0) continue;
                Vec3 dir = d.normalize();
                if (stats.inverted) dir = dir.scale(-1);
                double k = (0.5 + 0.05 * Math.min(20, armor)) * p * Math.max(0.35, FaUtil.kbFactor(t));
                FaUtil.addMotion(t, new Vec3(dir.x * k, Math.max(0.15, dir.y * k + 0.2), dir.z * k));
                FaUtil.line(sl, pole, chest(t), FaUtil.dust(stats.inverted ? 0xFF4A4A : 0x4A8AFF, 0.7F), 3);
                sparks(sl, chest(t), 10);
            }
            // Itens de metal soltos no chão
            Vec3 center = FaUtil.isSelf(ctx, target) ? pole : target.position();
            int items = 0;
            for (ItemEntity it : sl.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(range),
                    i -> i.isAlive() && isMetal(i.getItem()))) {
                Vec3 d = pole.subtract(it.position());
                if (d.lengthSqr() < 0.5) continue;
                Vec3 v = d.normalize().scale(stats.inverted ? -0.6 : Math.min(1.2, 0.25 + d.length() * 0.09));
                FaUtil.setMotion(it, v.add(0, 0.15, 0));
                if (items++ < 8) FaUtil.line(sl, pole, it.position(), FaUtil.dust(0x9AB4D8, 0.5F), 2);
            }
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pole.x, pole.y, pole.z, 12, 0.3, 0.3, 0.3, 0.15);
            FaUtil.sound(sl, pole, SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
            FaUtil.sound(sl, pole, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 2.0F);
        }

        /** Item "de metal": lingotes, pepitas, minérios brutos e coisas de ferro/ouro/cobre/netherite/corrente. */
        static boolean isMetal(ItemStack s) {
            if (s.isEmpty()) return false;
            if (s.is(Tags.Items.INGOTS) || s.is(Tags.Items.NUGGETS) || s.is(Tags.Items.RAW_MATERIALS)) return true;
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            for (String m : METALS) if (path.contains(m)) return true;
            return false;
        }

        private static final String[] METALS = {"iron", "gold", "copper", "netherite", "chain", "anvil", "bucket", "minecart",
                "rail", "shears", "compass", "clock", "bell", "lantern", "hopper", "cauldron", "flint_and_steel"};
    }

    /** Sobrecarga: o corpo vira eletricidade — velocidade extrema por um tempo (e cansaço quando acaba). */
    public static class Overcharge extends EffectGlyph {
        public Overcharge() {
            super("overcharge", 18);
            element(Element.LIGHTNING);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Invertido: curto-circuito no inimigo
                le.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, stats.duration(30), 0), ctx.caster());
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(100), 1), ctx.caster());
                FaUtil.hurt(ctx, le, 3.0F, stats, null);
            } else {
                le.addEffect(new MobEffectInstance(FaRegistries.OVERCHARGE, stats.duration(300), Math.min(2, FaUtil.amp(ctx, stats))), ctx.caster());
            }
            Vec3 c = chest(le);
            sparks(sl, c, 40);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 0.5 * i + sl.random.nextDouble();
                Vec3 to = c.add(Math.cos(a) * 1.4, (sl.random.nextDouble() - 0.3) * 1.5, Math.sin(a) * 1.4);
                FaUtil.zigzag(sl, c, to, FaUtil.dust(0xE0FAFF, 0.6F), ParticleTypes.ELECTRIC_SPARK);
            }
            FaUtil.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.7F);
            FaUtil.sound(sl, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.6F);
        }
    }

    /** Tempestade: nuvem de trovoada sobre a área que derruba raios nos inimigos. */
    public static class Thunderstorm extends EffectGlyph {
        public Thunderstorm() {
            super("thunderstorm", 30);
            element(Element.LIGHTNING);
            tier(4);
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
            if (!FaUtil.once(ctx, "thunderstorm")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 6);
            float r = stats.radius(6.0F);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.THUNDERSTORM, c, r, stats.duration(200), null);
            sl.sendParticles(FaUtil.dust(0x3A3F4A, 3.5F), c.x, c.y + 7, c.z, 60, r * 0.6, 0.4, r * 0.6, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 7, c.z, 20, r * 0.5, 0.3, r * 0.5, 0.1);
            FaUtil.sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.2F);
        }
    }

    private LightningGlyphs() {}
}
