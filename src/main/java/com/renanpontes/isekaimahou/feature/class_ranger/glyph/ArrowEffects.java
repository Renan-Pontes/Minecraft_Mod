package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de FLECHA do Ranger (glifos de origem, montáveis com a forma Flecha — ou qualquer outra forma): Explosiva,
 * em Corrente, Rede, Perfurante e Chuva de Flechas. Todos usam o elemento/cor da magia, respeitam aliados e os
 * aumentos (Amplificar, Expandir, Estender...) e passam pelo {@code ctx.damage} (inflação ×1,5 do núcleo).
 */
public final class ArrowEffects {
    private ArrowEffects() {}

    /** Centro do alvo (para partículas e linhas). */
    static Vec3 mid(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /**
     * Um mesmo impacto pode chegar como criatura E como bloco (formas de área/Sensível): só o primeiro vale. Guarda,
     * por conjurador, o tick e o ponto do último disparo do efeito; devolve false se for repetição (mesmo tick, perto).
     */
    static boolean firstAt(Map<UUID, Object[]> last, SpellContext ctx, Vec3 at, double radius) {
        UUID who = ctx.casterId() != null ? ctx.casterId() : NO_CASTER;
        long now = ctx.level().getGameTime();
        Object[] prev = last.get(who);
        if (prev != null && (long) prev[0] == now && ((Vec3) prev[1]).distanceToSqr(at) < radius * radius) return false;
        if (last.size() > 512) last.clear();
        last.put(who, new Object[] {now, at});
        return true;
    }

    private static final UUID NO_CASTER = new UUID(0L, 0L);

    // =====================================================================================
    //  Flecha Explosiva
    // =====================================================================================

    /**
     * Flecha Explosiva: a ponta detona ao acertar (criatura OU chão) — dano em área com queda pela distância e
     * empurrão, sem quebrar blocos. Uma explosão por conjurador e ponto no mesmo tick (formas de área não multiplicam).
     */
    public static class Explosive extends EffectGlyph {
        public static final float DAMAGE = 5.0F;
        public static final float RADIUS = 2.5F;
        /** Última explosão de cada conjurador (tick + ponto), para não repetir no mesmo tick/lugar. */
        private static final Map<UUID, Object[]> LAST = new HashMap<>();

        public Explosive() {
            super(RangerGlyphIds.EXPLOSIVE, 12);
            origin(MagicPath.RANGER);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) return;
            explode(ctx, mid(target), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            explode(ctx, hit.getLocation(), stats);
        }

        private static void explode(SpellContext ctx, Vec3 at, SpellStats stats) {
            ServerLevel level = ctx.level();
            if (!firstAt(LAST, ctx, at, 2.0)) return;
            float r = stats.radius(RADIUS);
            for (LivingEntity e : ctx.enemiesAround(at, r + 0.5)) {
                double d = mid(e).distanceTo(at);
                float fall = (float) Math.max(0.45, 1.0 - d / (r + 0.5) * 0.55);
                e.invulnerableTime = 0;
                ctx.damage(e, DAMAGE * fall, stats, null);
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? ctx.look().multiply(1, 0, 1) : away.normalize();
                double kb = (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * 0.55 * stats.power();
                e.push(away.x * kb, 0.3 + 0.1 * stats.power(), away.z * kb);
                e.hurtMarked = true;
            }
            level.sendParticles(r > 3.2F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            ElementFx.burst(level, at, ctx.element(), (int) (14 + r * 6), r * 0.45);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 8, r * 0.3, 0.3, r * 0.3, 0.02);
            RangerFx.ring(level, at.add(0, 0.1, 0), r, 18, WeaponFx.dust(ctx.color() & 0xFFFFFF, 1.2F), 0);
            RangerFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 1.25F + level.random.nextFloat() * 0.2F);
        }

        static void reset() {
            LAST.clear();
        }
    }

    // =====================================================================================
    //  Flecha em Corrente
    // =====================================================================================

    /**
     * Flecha em Corrente: depois de acertar, flechas espectrais SALTAM para os inimigos mais próximos (até 8 blocos):
     * 3 saltos (+1 por Amplificar), cada um com 80% do anterior, um a cada 3 ticks.
     */
    public static class Chain extends EffectGlyph {
        public static final float DAMAGE = 4.0F;

        public Chain() {
            super(RangerGlyphIds.CHAIN, 14);
            origin(MagicPath.RANGER);
            tier(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return 8.0F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) return;
            ctx.damage(target, DAMAGE, stats, null);
            int jumps = 3 + Math.max(0, stats.amplify);
            Set<UUID> done = new HashSet<>();
            done.add(target.getUUID());
            hop(ctx, target, stats, jumps, DAMAGE * 0.8F, done);
        }

        private static void hop(SpellContext ctx, Entity from, SpellStats stats, int left, float dmg, Set<UUID> done) {
            if (left <= 0) return;
            SpellScheduler.schedule(3, () -> {
                ServerLevel level = ctx.level();
                if (from.level() != level) return;
                Vec3 a = mid(from);
                LivingEntity next = null;
                double best = 64;
                for (LivingEntity e : ctx.enemiesAround(a, 8.0)) {
                    if (done.contains(e.getUUID())) continue;
                    double d = mid(e).distanceToSqr(a);
                    if (d < best) {
                        best = d;
                        next = e;
                    }
                }
                if (next == null) return;
                done.add(next.getUUID());
                Vec3 b = mid(next);
                RangerFx.tracer(level, a, b, ctx.color() & 0xFFFFFF, 0.9F);
                RangerFx.line(level, a, b, 1.2, ParticleTypes.CRIT);
                RangerFx.sound(level, b, SoundEvents.ARROW_HIT, 0.8F, 1.6F);
                next.invulnerableTime = 0;
                ctx.damage(next, dmg, stats, null);
                ElementFx.burst(level, b, ctx.element(), 6, 0.2);
                hop(ctx, next, stats, left - 1, dmg * 0.8F, done);
            });
        }
    }

    // =====================================================================================
    //  Flecha-Rede
    // =====================================================================================

    /**
     * Flecha-Rede: abre uma rede de cordas encantadas — o alvo e quem estiver perto ficam ENRAIZADOS e lentos; criaturas
     * voadoras são puxadas para o chão. Também prende quem passa no ponto atingido no chão.
     */
    public static class Net extends EffectGlyph {
        public static final int DURATION = 60;
        public static final float RADIUS = 2.0F;

        public Net() {
            super(RangerGlyphIds.NET, 10);
            origin(MagicPath.RANGER);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return 1.0F; }

        @Override
        public int hintDuration() { return DURATION; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) return;
            ctx.damage(target, 1.0F, stats, null);
            snare(ctx, mid(target), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            snare(ctx, hit.getLocation(), stats);
        }

        private static void snare(SpellContext ctx, Vec3 at, SpellStats stats) {
            ServerLevel level = ctx.level();
            float r = stats.radius(RADIUS);
            int ticks = stats.duration(DURATION);
            for (LivingEntity e : ctx.enemiesAround(at, r + 0.5)) {
                ctx.status(e, CoreRegistries.ROOTED, DURATION, 0, stats);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 20, 2), ctx.caster());
                if (!e.onGround()) {
                    // a rede derruba quem voa
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -0.9, 0));
                    e.hurtMarked = true;
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
                        e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3,
                        e.getBbWidth() * 0.4, 0.05);
            }
            RangerFx.ring(level, at, r, 20, WeaponFx.dust(0xE8E4D8, 1.0F), 0);
            RangerFx.ring(level, at, r * 0.55, 12, WeaponFx.dust(ctx.color() & 0xFFFFFF, 0.9F), 0);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4;
                Vec3 off = new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
                RangerFx.line(level, at.add(off), at.subtract(off), 0.5, WeaponFx.dust(0xD8D2C0, 0.7F));
            }
            RangerFx.sound(level, at, SoundEvents.CROSSBOW_LOADING_END.value(), 0.9F, 0.8F);
            RangerFx.sound(level, at, SoundEvents.WOOL_BREAK, 1.0F, 0.7F);
        }
    }

    // =====================================================================================
    //  Flecha Perfurante
    // =====================================================================================

    /**
     * Flecha Perfurante: golpe pesado que deixa o alvo EXPOSTO (menos defesa) e atravessa — quem estiver atrás dele
     * (até 10 blocos na linha do tiro) leva 70% do dano.
     */
    public static class Piercing extends EffectGlyph {
        public static final float DAMAGE = 6.5F;

        public Piercing() {
            super(RangerGlyphIds.PIERCING, 14);
            origin(MagicPath.RANGER);
            tier(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return 80; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) return;
            ServerLevel level = ctx.level();
            ctx.damage(target, DAMAGE, stats, null);
            if (target instanceof LivingEntity le) ctx.status(le, CoreRegistries.EXPOSED, 80, 0, stats);
            Vec3 dir = ctx.look().normalize();
            Vec3 from = mid(target);
            Vec3 to = from.add(dir.scale(10));
            RangerFx.tracer(level, from, to, ctx.color() & 0xFFFFFF, 1.0F);
            RangerFx.line(level, from, to, 1.0, ParticleTypes.CRIT);
            List<LivingEntity> line = new ArrayList<>();
            for (LivingEntity e : ctx.enemiesAround(from.add(dir.scale(5)), 6.0)) {
                if (e == target) continue;
                Vec3 rel = mid(e).subtract(from);
                double along = rel.dot(dir);
                if (along < 0 || along > 10) continue;
                if (rel.subtract(dir.scale(along)).length() > 0.9 + e.getBbWidth() * 0.5) continue;
                line.add(e);
            }
            for (LivingEntity e : line) {
                e.invulnerableTime = 0;
                ctx.damage(e, DAMAGE * 0.7F, stats, null);
                level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
            }
            RangerFx.sound(level, from, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7F, 1.6F);
        }
    }

    // =====================================================================================
    //  Chuva de Flechas
    // =====================================================================================

    /**
     * Chuva de Flechas: onde a flecha acerta, meio segundo depois uma saraivada de 10 flechas espectrais (+3 por
     * Amplificar) cai num círculo de 3 blocos (Expandir aumenta). Cada flecha fere 1,6; cada inimigo leva no máx. 3.
     */
    public static class Rain extends EffectGlyph {
        public static final float DAMAGE = 1.6F;
        public static final float RADIUS = 3.0F;
        private static final Map<UUID, Object[]> LAST = new HashMap<>();

        public Rain() {
            super(RangerGlyphIds.RAIN, 18);
            origin(MagicPath.RANGER);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public int hintDuration() { return 24; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster()) return;
            rain(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            rain(ctx, hit.getLocation(), stats);
        }

        private static void rain(SpellContext ctx, Vec3 at, SpellStats stats) {
            ServerLevel level = ctx.level();
            if (!firstAt(LAST, ctx, at, 3.0)) return;
            Vec3 ground = RangerUtil.groundBelow(level, at.add(0, 0.5, 0));
            float r = stats.radius(RADIUS);
            int arrows = 10 + 3 * Math.max(0, stats.amplify);
            double sky = RangerUtil.skyY(level, ground, 14);
            int rgb = ctx.color() & 0xFFFFFF;
            RangerFx.ring(level, ground.add(0, 0.1, 0), r, 20, WeaponFx.dust(rgb, 1.0F), 0);
            RangerFx.sound(level, ground, SoundEvents.ARROW_SHOOT, 1.0F, 0.6F);
            RainArrows.Volley v = new RainArrows.Volley(ctx, 3, 0, (t, arrow) -> {
                ctx.damage(t, DAMAGE, stats, arrow);
                ElementFx.burst(level, mid(t), ctx.element(), 4, 0.15);
            }, null);
            for (int i = 0; i < arrows; i++) {
                int delay = 10 + (i * 14) / arrows;
                SpellScheduler.schedule(delay, () -> {
                    Vec3 land = RainArrows.randomIn(level, ground, r);
                    Vec3 from = new Vec3(land.x + (level.random.nextDouble() - 0.5), sky, land.z + (level.random.nextDouble() - 0.5));
                    RainArrows.spawn(v, from, land.subtract(from).normalize().scale(2.2));
                });
            }
            SpellScheduler.schedule(16, () -> RangerFx.sound(level, ground, SoundEvents.ARROW_HIT, 1.0F, 0.7F));
        }
    }

    /** Limpa o estado estático (servidor parando). */
    public static void reset() {
        Explosive.reset();
        Rain.LAST.clear();
    }
}
