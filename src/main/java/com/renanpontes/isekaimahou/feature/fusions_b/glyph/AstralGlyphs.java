package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.AstralProjection;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbTasks;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.FallingStarEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos ASTRAIS (Vento + Luz).
 * Tier 1: Estrela Cadente · Tier 2: Teleporte Estelar, Gravidade Zero · Tier 3: Constelação · Tier 4: Projeção Astral.
 */
public final class AstralGlyphs {

    public static void register() {
        GlyphRegistry.register(new FallingStar());
        GlyphRegistry.register(new StarTeleport());
        GlyphRegistry.register(new ZeroGravity());
        GlyphRegistry.register(new Constellation());
        GlyphRegistry.register(new AstralProjectionGlyph());
    }

    // =====================================================================================
    //  Estrela Cadente
    // =====================================================================================

    /**
     * Estrela Cadente: marca o chão no alvo e, um instante depois, uma estrela cai do céu ali (dano 6 em área e
     * empurrão). Em Si Mesmo cai no inimigo mais próximo; acertando um bloco, cai naquele ponto. Dividir = +1 estrela.
     */
    public static class FallingStar extends EffectGlyph {
        private static final String KEY = "falling_star";

        public FallingStar() {
            super("falling_star", 14);
            element(Element.ASTRAL);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FbUtil.firstHit(ctx, KEY, target)) return;
            FbUtil.once(ctx, KEY);
            Vec3 at;
            if (FbUtil.isSelf(ctx, target)) {
                LivingEntity e = FbUtil.nearestEnemy(ctx, target.position(), 14);
                at = e != null ? e.position() : FbUtil.ground(ctx.level(), target.position().add(FbUtil.flatLook(ctx).scale(6)).add(0, 1, 0), 8);
            } else {
                at = FbUtil.ground(ctx.level(), target.position().add(0, 0.5, 0), 8);
            }
            rain(ctx, at, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FbUtil.usedThisTick(ctx, KEY) || !FbUtil.once(ctx, KEY + "_block")) return;
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection() == Direction.DOWN ? Direction.DOWN : Direction.UP));
            rain(ctx, FbUtil.ground(ctx.level(), at, 8), stats);
        }

        private static void rain(SpellContext ctx, Vec3 at, SpellStats stats) {
            int n = 1 + Math.max(0, stats.split);
            float dmg = 6.0F * stats.power();
            float radius = stats.radius(2.5F);
            for (int i = 0; i < n; i++) {
                Vec3 p = i == 0 ? at : FbUtil.ground(ctx.level(), at.add((ctx.random().nextDouble() - 0.5) * 5, 1,
                        (ctx.random().nextDouble() - 0.5) * 5), 6);
                if (i == 0) FallingStarEntity.launch(ctx, p, dmg, radius, stats);
                else SpellScheduler.schedule(i * 6, () -> FallingStarEntity.launch(ctx, p, dmg, radius, stats));
            }
        }
    }

    // =====================================================================================
    //  Teleporte Estelar
    // =====================================================================================

    /**
     * Teleporte Estelar: em Si Mesmo, salta até 24 blocos (×poder, máx. 48) para onde você olha, parando antes
     * de paredes, deixando um rastro de estrelas. Se a magia acerta um inimigo, você aparece ao lado dele;
     * se acerta um bloco, você aparece ali. Chegando no ar, cai devagar por alguns segundos.
     */
    public static class StarTeleport extends EffectGlyph {
        private static final String KEY = "star_teleport";

        public StarTeleport() {
            super("star_teleport", 15);
            element(Element.ASTRAL);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !c.isAlive() || !FbUtil.once(ctx, KEY)) return;
            Vec3 dest;
            if (target == c) {
                dest = blinkDestination(c, Math.min(48.0, 24.0 * FbUtil.power(ctx, stats)));
            } else {
                Vec3 side = c.position().subtract(target.position());
                side = new Vec3(side.x, 0, side.z);
                side = side.lengthSqr() < 1.0E-4 ? FbUtil.flatLook(ctx).scale(-1) : side.normalize();
                dest = FbUtil.findSafeSpot(c, target.position().add(side.scale(target.getBbWidth() * 0.5 + 1.2)), 3, 2);
            }
            go(ctx, c, dest);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !c.isAlive() || FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            Vec3 want = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            go(ctx, c, FbUtil.findSafeSpot(c, want, 3, 2));
        }

        /** Ponto de chegada do salto: no chão onde o olhar bate, ou no ar no fim do alcance. */
        @Nullable
        static Vec3 blinkDestination(LivingEntity c, double range) {
            BlockHitResult hit = FbUtil.lookClip(c, range);
            Vec3 look = c.getLookAngle();
            Vec3 feet;
            if (hit.getType() == HitResult.Type.MISS) {
                feet = hit.getLocation().subtract(0, c.getEyeHeight(), 0);
            } else if (hit.getDirection() == Direction.UP) {
                feet = hit.getLocation();
            } else if (hit.getDirection() == Direction.DOWN) {
                feet = hit.getLocation().subtract(0, c.getBbHeight() + 0.1, 0);
            } else {
                feet = hit.getLocation().subtract(look.scale(0.6)).subtract(0, c.getBbHeight() * 0.5, 0);
            }
            // Recua pelo caminho até achar um lugar onde caiba
            for (int i = 0; i < 12; i++) {
                Vec3 p = feet.subtract(look.scale(i * 0.8));
                if (p.distanceToSqr(c.position()) < 4) break;
                if (FbUtil.fits(c, p)) return p;
                if (FbUtil.fits(c, p.add(0, 1, 0))) return p.add(0, 1, 0);
            }
            return FbUtil.findSafeSpot(c, feet, 3, 1);
        }

        static void go(SpellContext ctx, LivingEntity c, @Nullable Vec3 dest) {
            ServerLevel sl = ctx.level();
            if (dest == null) {
                FbFx.burst(sl, c.position().add(0, 1, 0), Element.ASTRAL, 8, 0.3);
                FbFx.sound(sl, c.position(), SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.8F);
                return;
            }
            Vec3 from = c.position().add(0, c.getBbHeight() * 0.5, 0);
            FbFx.burst(sl, from, Element.ASTRAL, 18, 0.4);
            FbFx.sound(sl, from, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.3F);
            FbUtil.teleport(c, dest);
            Vec3 to = dest.add(0, c.getBbHeight() * 0.5, 0);
            FbFx.line(sl, from, to, FbFx.dust(FbFx.ASTRAL_LIGHT, 1.0F), 2.0);
            FbFx.line(sl, from, to, Vfx.endRod(), 0.4);
            sl.sendParticles(Vfx.firework(), to.x, to.y, to.z, 20, 0.3, 0.5, 0.3, 0.1);
            FbFx.sound(sl, to, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
            FbFx.sound(sl, to, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.5F);
            if (!FbUtil.hasSafeFloor(c, dest)) {
                c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
            }
        }
    }

    // =====================================================================================
    //  Gravidade Zero
    // =====================================================================================

    /**
     * Gravidade Zero: por 10s o alvo flutua sem gravidade; jogadores sobem segurando PULAR e descem AGACHANDO,
     * sem dano de queda (e caem devagar quando acaba). Invertido em inimigos: flutuam indefesos por 5s.
     */
    public static class ZeroGravity extends EffectGlyph {
        public ZeroGravity() {
            super("zero_gravity", 12);
            element(Element.ASTRAL);
            tier(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            boolean friendly = le == ctx.caster() || ctx.isAlly(le);
            if (!friendly && FbUtil.untouchable(le)) return;
            int ticks = friendly ? stats.duration(200) : stats.duration(100);
            if (!friendly && FbUtil.isBoss(le)) ticks = Math.max(10, ticks / 3);
            // Nível 0 = voo controlado (aliados); nível 1 = à deriva, sem controle (inimigos, inclusive jogadores)
            le.addEffect(new MobEffectInstance(FbRegistries.ZERO_GRAVITY, ticks, friendly ? 0 : 1, false, true, true), ctx.caster());
            if (friendly) {
                // Quando a gravidade volta, cai devagar (nunca morre de queda por causa da magia)
                le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks + 100, 0, false, false, true), ctx.caster());
            } else {
                FbUtil.forgetTarget(le);
                FbUtil.addMotion(le, new Vec3(0, 0.25, 0));
            }
            Vec3 feet = le.position().add(0, 0.1, 0);
            FbFx.ring(sl, feet, Vfx.cloud(), le.getBbWidth() + 0.6, 14, 0.02);
            FbFx.ring(sl, feet, FbFx.dust(FbFx.ASTRAL_LIGHT, 1.1F), le.getBbWidth() + 0.9, 20, 0);
            sl.sendParticles(Vfx.endRod(), le.getX(), le.getY() + 0.5, le.getZ(), 8, 0.3, 0.3, 0.3, 0.04);
            FbFx.sound(sl, feet, SoundEvents.SHULKER_BULLET_HIT, 1.0F, 1.3F);
            FbFx.sound(sl, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
        }
    }

    // =====================================================================================
    //  Constelação
    // =====================================================================================

    /**
     * Constelação: marca o alvo e até 2 (+Dividir, +Expandir) inimigos próximos como estrelas e liga todas com
     * linhas de luz. Depois de 2,5s cada estrela leva dano 5, +20% por estrela extra na constelação (máx. ×2).
     * Marcações da mesma conjuração (vários projéteis, área) entram na MESMA constelação.
     */
    public static class Constellation extends EffectGlyph {
        private static final Map<UUID, ConstellationTask> OPEN = new HashMap<>();

        public Constellation() {
            super("constellation", 20);
            element(Element.ASTRAL);
            tier(3);
        }

        /** Esquece as constelações abertas (servidor parou: não segurar mundos/entidades antigos na memória). */
        public static void clearOpen() {
            OPEN.clear();
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            List<LivingEntity> marks = new ArrayList<>();
            int extra = 2 + Math.max(0, stats.split) + Math.max(0, stats.aoe);
            if (FbUtil.isSelf(ctx, target)) {
                List<LivingEntity> around = ctx.enemiesAround(target.position(), stats.radius(10F));
                around.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
                for (int i = 0; i < Math.min(around.size(), extra + 1); i++) marks.add(around.get(i));
            } else if (target instanceof LivingEntity le) {
                marks.add(le);
                List<LivingEntity> around = ctx.enemiesAround(le.position(), stats.radius(6F));
                around.remove(le);
                around.sort((a, b) -> Double.compare(a.distanceToSqr(le), b.distanceToSqr(le)));
                for (int i = 0; i < Math.min(around.size(), extra); i++) marks.add(around.get(i));
            }
            if (marks.isEmpty()) return;
            UUID key = ctx.casterId() != null ? ctx.casterId() : target.getUUID();
            // Entradas de mundos/servidores anteriores (o servidor parou no meio de uma constelação)
            OPEN.values().removeIf(t -> t.level.getServer() != sl.getServer() || t.finished());
            ConstellationTask task = OPEN.get(key);
            if (task == null || !task.collecting() || task.level != sl) {
                task = new ConstellationTask(ctx, stats, key);
                OPEN.put(key, task);
                FbTasks.add(task);
            }
            for (LivingEntity e : marks) task.add(e);
        }

        /** A constelação em si: desenha as linhas e explode no fim. */
        static final class ConstellationTask implements FbTasks.Task {
            private static final int MAX_STARS = 8;
            final ServerLevel level;
            private final SpellContext ctx;
            private final SpellStats stats;
            private final UUID key;
            private final List<LivingEntity> stars = new ArrayList<>();
            private final int duration;
            private int age;

            ConstellationTask(SpellContext ctx, SpellStats stats, UUID key) {
                this.level = ctx.level();
                this.ctx = ctx;
                this.stats = stats;
                this.key = key;
                this.duration = Math.min(120, stats.duration(50));
            }

            private boolean done;

            /** Ainda aceita estrelas (meio segundo depois da primeira marca). */
            boolean collecting() { return age < 10 && !done; }

            boolean finished() { return done; }

            void add(LivingEntity e) {
                if (stars.contains(e) || stars.size() >= MAX_STARS) return;
                stars.add(e);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration - age + 5, 0, false, false, false), ctx.caster());
                Vec3 p = star(e);
                level.sendParticles(Vfx.firework(), p.x, p.y, p.z, 10, 0.1, 0.1, 0.1, 0.08);
                FbFx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F + stars.size() * 0.1F);
            }

            private static Vec3 star(LivingEntity e) {
                return e.position().add(0, e.getBbHeight() + 0.5, 0);
            }

            @Override
            public boolean tick() {
                age++;
                stars.removeIf(e -> !e.isAlive() || e.level() != level);
                if (age >= 10 && OPEN.get(key) == this) OPEN.remove(key);
                if (stars.isEmpty()) {
                    done = true;
                    if (OPEN.get(key) == this) OPEN.remove(key);
                    return false;
                }
                if (age < duration) {
                    if (age % 3 == 0) draw();
                    if (age % 12 == 0) FbFx.sound(level, star(stars.get(0)), SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.5F, 1.4F);
                    return true;
                }
                strike();
                done = true;
                return false;
            }

            private void draw() {
                for (int i = 0; i < stars.size(); i++) {
                    Vec3 a = star(stars.get(i));
                    level.sendParticles(Vfx.endRod(), a.x, a.y, a.z, 1, 0.02, 0.02, 0.02, 0.0);
                    level.sendParticles(FbFx.dust(FbFx.ASTRAL_LIGHT, 1.5F), a.x, a.y, a.z, 2, 0.08, 0.08, 0.08, 0);
                    if (i + 1 < stars.size() || stars.size() >= 3) {
                        Vec3 b = star(stars.get((i + 1) % stars.size()));
                        FbFx.line(level, a, b, FbFx.dust(i % 2 == 0 ? FbFx.ASTRAL : FbFx.ASTRAL_LIGHT, 0.8F), 1.5);
                    }
                }
            }

            private void strike() {
                float bonus = Math.min(2.0F, 1.0F + 0.2F * (stars.size() - 1));
                for (LivingEntity e : stars) {
                    Vec3 p = FbUtil.center(e);
                    FbUtil.hurt(ctx, e, 5.0F * bonus, stats, null);
                    level.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    FbFx.column(level, e.position(), Vfx.endRod(), 8, 16);
                    level.sendParticles(Vfx.firework(), p.x, p.y, p.z, 24, 0.2, 0.3, 0.2, 0.15);
                }
                Vec3 first = stars.get(0).position();
                FbFx.sound(level, first, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 1.2F);
                FbFx.sound(level, first, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.8F);
            }
        }
    }

    // =====================================================================================
    //  Projeção Astral
    // =====================================================================================

    /**
     * Projeção Astral: o seu espírito sai do corpo por 10s (Estender aumenta, máx. 60s) em modo espectador,
     * podendo atravessar paredes para explorar até 48 blocos de distância. Depois volta sozinho para o corpo,
     * no mesmo lugar e no mesmo modo de jogo. Só funciona em você mesmo (mobs ficam invisíveis).
     */
    public static class AstralProjectionGlyph extends EffectGlyph {
        public AstralProjectionGlyph() {
            super("astral_projection", 30);
            element(Element.ASTRAL);
            tier(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || target != c) return;
            int ticks = Math.min(1200, stats.duration(200));
            if (c instanceof ServerPlayer sp) {
                if (!AstralProjection.start(sp, ticks)) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.astral_projection.fail"), true);
                }
            } else {
                c.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0));
                FbFx.burst(ctx.level(), FbUtil.center(c), Element.ASTRAL, 20, 0.5);
            }
        }
    }

    private AstralGlyphs() {}
}
