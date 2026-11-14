package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbPart;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.feature.class_steelball.SbTechniques;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.BallBreakerEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.ScanBallEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas do Santo que não são Atos: Bola Escâner, Lição do Cavalo — Corrida Dourada, Rotação Restauradora e Esfera
 * Quebrantadora (homenagem ao Ball Breaker). Glifos de origem do Steel Ball (ver {@link SbTechniques}).
 */
public final class SbTechniqueGlyphs {

    // =====================================================================================
    //  Bola Escâner
    // =====================================================================================

    /**
     * Bola Escâner (forma, tier 2): arremessa a bola cravejada de luzes; ela para no ar e solta 3 ondas de varredura
     * (raio 20, ×Expandir; Olhos do Santo ×1,5). Todos os inimigos brilham por 10 s; os 3 mais perigosos (+1 por
     * Dividir) ganham PONTO FRACO (Exposto II) e recebem os efeitos. Funciona sem efeito nenhum.
     */
    public static class ScanBallForm extends FormGlyph {
        public static final float RADIUS = 20F;

        public ScanBallForm() {
            super("scan_ball", 6);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(2);
        }

        public static int weakPoints(SpellStats s) {
            return Math.min(8, 3 + Math.max(0, s.split));
        }

        @Override public boolean requiresEffect() { return false; }
        @Override public float copies(SpellStats s, int circle) { return weakPoints(s); }
        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 120; }
        @Override public float hintRange() { return 24F; }
        @Override public float hintRadius() { return RADIUS; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            float r = ctx.formStats().radius(RADIUS);
            if (caster instanceof Player p && SbPart.EYES.owned(MagicApi.get(p))) r *= 1.5F;
            Vec3 look = ctx.hasOrigin() ? ctx.look() : TuskActForms.aim(caster);
            Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.6)).subtract(0, 0.2, 0);
            level.addFreshEntity(ScanBallEntity.spell(ctx, from, look, r, weakPoints(ctx.formStats())));
            SbFx.sound(level, from, SoundEvents.SNOWBALL_THROW, 0.8F, 0.7F);
            SbFx.sound(level, from, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.8F);
            if (!ctx.hasOrigin()) {
                caster.swing(InteractionHand.MAIN_HAND, true);
                SbTechniques.actionbar(caster instanceof Player p ? p : null, Component.translatable("callout.isekai_mahou.steelball.scan_ball"),
                        0x7FE8FF);
            }
        }
    }

    // =====================================================================================
    //  Lição do Cavalo — Corrida Dourada
    // =====================================================================================

    /**
     * Lição do Cavalo — Corrida Dourada (forma, tier 2): montado, o cavalo entra no galope dourado por 8 s (×Estender):
     * Velocidade III, Salto II, Resistência II e um rastro de espiral dourada; quem ele atropelar (1 vez por segundo
     * cada) gira, é jogado de lado e recebe os efeitos (80% do poder). A pé: Velocidade II por 4 s e o atropelo com o
     * próprio corpo (50%). Funciona sem efeito nenhum. Custo por cópia: atropelos moderados.
     */
    public static class GoldenGallopForm extends FormGlyph {
        public static final int DURATION = 160;
        private static final Map<UUID, Run> RUNS = new HashMap<>();

        private record Run(SpellContext ctx, long until, boolean mounted, Map<UUID, Long> lastHit) {}

        public GoldenGallopForm() {
            super("golden_gallop", 9);
            origin(MagicPath.STEELBALL);
            element(Element.WIND);
            tier(2);
            minCircle(2);
        }

        @Override public boolean requiresEffect() { return false; }
        @Override public float copies(SpellStats s, int circle) { return SpellCost.pulseCopies(s.duration(DURATION), 20); }
        @Override public int baseCooldown() { return 300; }
        @Override public int hintDuration() { return DURATION; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            boolean mounted = caster.getVehicle() instanceof LivingEntity;
            int ticks = ctx.formStats().duration(DURATION);
            if (!mounted) ticks /= 2;
            if (mounted && caster.getVehicle() instanceof LivingEntity steed) {
                steed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, true));
                steed.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, true));
                steed.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, false, true));
                SbFx.sound(level, steed.position(), SoundEvents.HORSE_ANGRY, 1.0F, 1.0F);
                SbFx.sound(level, steed.position(), SoundEvents.HORSE_GALLOP, 1.0F, 1.3F);
            } else {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
            }
            SbFxNet.at(level, SbFxNet.GOLDEN_RECT, caster.position().add(0, 1.4, 0), caster.getLookAngle(), 1.6F, SbFx.GOLD, caster.getId());
            SbFxNet.at(level, SbFxNet.GALLOP, caster.position(), caster.getLookAngle(), 1F, SbFx.GOLD, caster.getId());
            SbFx.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.7F);
            RUNS.put(caster.getUUID(), new Run(ctx, level.getGameTime() + ticks, mounted, new HashMap<>()));
            if (caster instanceof ServerPlayer sp) {
                SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.golden_gallop"),
                        Component.translatable(mounted ? "callout.isekai_mahou.steelball.golden_gallop.sub"
                                : "callout.isekai_mahou.steelball.golden_gallop.foot"), SbFx.GOLD);
            }
        }

        /** Todo tick do servidor: rastro e atropelos de quem está correndo. */
        public static void tickRuns(ServerLevel any) {
            if (RUNS.isEmpty()) return;
            // cópia: um atropelo pode disparar outra magia (estágios) que mexa no mapa
            for (Map.Entry<UUID, Run> entry : new ArrayList<>(RUNS.entrySet())) {
                Run run = entry.getValue();
                LivingEntity caster = run.ctx().caster();
                ServerLevel level = run.ctx().level();
                if (caster == null || !caster.isAlive() || caster.level() != level || level.getGameTime() > run.until()) {
                    RUNS.remove(entry.getKey(), run);
                    continue;
                }
                Entity body = run.mounted() && caster.getVehicle() instanceof LivingEntity steed ? steed : caster;
                if (run.mounted() && body == caster) {
                    RUNS.remove(entry.getKey(), run); // desceu do cavalo: a corrida acaba
                    continue;
                }
                Vec3 v = body.getDeltaMovement();
                Vec3 p = body.position();
                if (level.getGameTime() % 2 == 0) {
                    double a = level.getGameTime() * 0.7;
                    for (int k = 0; k < 2; k++) {
                        double ang = a + k * Math.PI;
                        level.sendParticles(SbFx.dust(SbFx.GOLD, 1.0F), p.x + Math.cos(ang) * 0.6, p.y + 0.15, p.z + Math.sin(ang) * 0.6,
                                1, 0, 0, 0, 0);
                    }
                }
                if (v.horizontalDistanceSqr() < 0.01) continue;
                float power = run.mounted() ? 0.8F : 0.5F;
                for (LivingEntity e : run.ctx().enemiesAround(p, body.getBbWidth() * 0.5 + 1.1)) {
                    Long last = run.lastHit().get(e.getUUID());
                    if (last != null && level.getGameTime() - last < 20) continue;
                    run.lastHit().put(e.getUUID(), level.getGameTime());
                    trample(run.ctx(), caster, body, e, power);
                }
            }
        }

        private static void trample(SpellContext ctx, LivingEntity caster, Entity body, LivingEntity e, float power) {
            ServerLevel level = ctx.level();
            Vec3 side = e.position().subtract(body.position()).multiply(1, 0, 1);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            e.push(side.x * 0.9, 0.35, side.z * 0.9);
            e.hurtMarked = true;
            SbSpin.apply(e, caster, 0, 50);
            if (ctx.spell().effects().isEmpty()) {
                e.invulnerableTime = 0;
                ctx.damage(e, 3F * power, ctx.formStats(), body);
            } else {
                e.invulnerableTime = 0;
                ctx.copy(ctx.powerMult() * power).resolveOnEntity(e, body);
            }
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            SbFx.spinRing(level, c, SbFx.GOLD, e.getBbWidth() * 0.7 + 0.3, 12);
            level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.3);
            SbFx.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.8F);
        }

        public static void forget(UUID id) {
            RUNS.remove(id);
        }

        public static void clear() {
            RUNS.clear();
        }
    }

    // =====================================================================================
    //  Rotação Restauradora (cura avançada)
    // =====================================================================================

    /**
     * Rotação Restauradora (efeito benéfico, tier 4, Círculo 4): a técnica máxima da família de médicos. Cura 10 na
     * hora e mais 2 por segundo por 5 s (a espiral verde que sobe), tira TODOS os efeitos ruins e as condições
     * (queimando, congelado, sangrando...), dá Absorção II e Resistência I por 5 s. Inverter, em inimigos: "desenrosca"
     * o corpo — dano 6, tira todos os efeitos bons e deixa Fraco.
     */
    public static class SpinMendEffect extends EffectGlyph {
        public static final float HEAL = 10F;
        public static final float HEAL_TICK = 2F;
        public static final int TICKS = 5;
        public static final float INVERTED_DAMAGE = 6F;

        public SpinMendEffect() {
            super("spin_mend", 24);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(4);
            minCircle(4);
        }

        @Override public boolean isBeneficial() { return true; }
        @Override public float baseHeal() { return HEAL + HEAL_TICK * TICKS; }
        @Override public int hintDuration() { return TICKS * 20; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            ServerLevel sl = ctx.level();
            boolean ally = target == ctx.caster() || ctx.isAlly(target);
            if (stats.inverted && !ally) {
                ctx.damage(le, INVERTED_DAMAGE, stats, null);
                strip(le, MobEffectCategory.BENEFICIAL);
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(100), 1), ctx.caster());
                SbFx.spiral(sl, le.position(), 0x6A2A3A, le.getBbWidth() * 0.8 + 0.3, le.getBbHeight() + 0.3, 18, 0F);
                SbFx.sound(sl, le.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 0.4F, 1.8F);
                return;
            }
            ctx.heal(le, HEAL, stats);
            strip(le, MobEffectCategory.HARMFUL);
            for (Statuses.Kind k : Statuses.Kind.values()) {
                if (Statuses.has(le, k)) Statuses.clear(le, k);
            }
            le.clearFire();
            le.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.duration(100), 1), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, stats.duration(100), 0), ctx.caster());
            for (int i = 1; i <= TICKS; i++) {
                final int k = i;
                SpellScheduler.schedule(20 * i, () -> {
                    if (!le.isAlive() || le.level() != sl) return;
                    ctx.heal(le, HEAL_TICK, stats);
                    SbFx.spiral(sl, le.position(), k % 2 == 0 ? SbFx.GOLD : SbFx.HEAL, le.getBbWidth() * 0.6 + 0.3,
                            le.getBbHeight() + 0.3, 12, k * 1.1F);
                });
            }
            SbFxNet.at(sl, SbFxNet.SPIN_HEAL, le.position(), Vec3.ZERO, 1F, SbFx.HEAL, le.getId());
            SbFxNet.at(sl, SbFxNet.GOLDEN_RECT, le.position().add(0, le.getBbHeight() + 0.5, 0), new Vec3(0, 0, 1), 0.9F, SbFx.GOLD, le.getId());
            sl.sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 3, 0.3, 0.2, 0.3, 0);
            SbFx.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.2F);
            SbFx.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.8F);
            if (ctx.casterPlayer() instanceof ServerPlayer sp) MagicApi.addCounter(sp, SbProgress.SPIN_HEALS, 1);
        }

        private static void strip(LivingEntity le, MobEffectCategory category) {
            List<Holder<MobEffect>> remove = new ArrayList<>();
            for (MobEffectInstance i : le.getActiveEffects()) {
                if (i.getEffect().value().getCategory() == category && !i.isInfiniteDuration()) remove.add(i.getEffect());
            }
            for (Holder<MobEffect> h : remove) le.removeEffect(h);
        }
    }

    // =====================================================================================
    //  Esfera Quebrantadora (homenagem ao Ball Breaker)
    // =====================================================================================

    /**
     * Esfera Quebrantadora (efeito, tier 5, Círculo 7): a rotação dourada perfeita que DESGASTA — o espectro da bola
     * paira sobre o alvo por 8 s (×Estender) e a cada segundo o envelhece: dano crescente (1, 1,5, 2... base),
     * Lentidão e Fraqueza que pioram, Cansaço e a defesa quebrada (Exposto). Chefes desgastam mais devagar.
     */
    public static class BallBreakerEffect extends EffectGlyph {
        public BallBreakerEffect() {
            super("ball_breaker", 30);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(5);
            minCircle(7);
        }

        @Override public float baseDamage() { return BallBreakerEntity.totalDamage(BallBreakerEntity.BASE_TICKS); }
        @Override public int hintDuration() { return BallBreakerEntity.BASE_TICKS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            BallBreakerEntity.attach(ctx, stats, le, stats.duration(BallBreakerEntity.BASE_TICKS));
            if (ctx.caster() instanceof ServerPlayer sp && !ctx.hasOrigin()) {
                SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.ball_breaker"),
                        Component.translatable("callout.isekai_mahou.steelball.ball_breaker.sub"), 0xFFB43A);
            }
        }
    }

    // =====================================================================================
    //  Surra de 7 Páginas (Tusk ACT4 × Valentine)
    // =====================================================================================

    /**
     * Surra de 7 Páginas (efeito, tier 5, Círculo 8, Lição V + 6 Partes — o mesmo ponto do Ato 4): o Tusk ACT4 desce
     * uma rajada de socos que dura "sete páginas" (28 golpes em 2,8 s, ×Estender = mais páginas, máx. 60). O alvo fica
     * preso no lugar enquanto apanha; cada soco carrega um pouco da Rotação Infinita e o último joga longe, Girado por
     * 10 s. Para o Steel Ball aparece o stand Tusk ACT4 em rajada.
     */
    public static class SevenPageBeatdownEffect extends EffectGlyph {
        public static final int HITS = 28;
        public static final int GAP = 2;
        public static final float HIT = 1.4F;
        public static final float FINAL = 12F;

        public SevenPageBeatdownEffect() {
            super("seven_page_beatdown", 32);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(5);
            minCircle(8);
        }

        @Override public float baseDamage() { return HIT * HITS + FINAL; }
        @Override public int hintDuration() { return HITS * GAP; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            LivingEntity caster = ctx.caster();
            if (caster instanceof ServerPlayer sp && !ctx.hasOrigin()) {
                SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.seven_page"),
                        Component.translatable("callout.isekai_mahou.steelball.seven_page.sub"), SbFx.NAIL);
            }
            int hits = Math.max(8, Math.min(60, Math.round(HITS * stats.duration(HITS * GAP) / (float) (HITS * GAP))));
            net.minecraft.world.phys.Vec3 pin = le.position();
            for (int i = 0; i < hits; i++) {
                final int n = i;
                com.renanpontes.isekaimahou.glyph.SpellScheduler.schedule(i * GAP, () -> {
                    if (!le.isAlive() || le.isRemoved() || !(le.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    // preso na rajada: não foge nem é empurrado pelos próprios socos
                    le.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    if (le.position().distanceToSqr(pin) > 1.0) le.teleportTo(pin.x, pin.y, pin.z);
                    le.invulnerableTime = 0;
                    ctx.damage(le, HIT, stats, null);
                    net.minecraft.world.phys.Vec3 at = le.position().add((sl.random.nextDouble() - 0.5) * le.getBbWidth(),
                            le.getBbHeight() * (0.3 + sl.random.nextDouble() * 0.6), (sl.random.nextDouble() - 0.5) * le.getBbWidth());
                    sl.sendParticles(SbFx.dust(n % 3 == 0 ? SbFx.GOLD : SbFx.NAIL, 1.1F), at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0);
                    if (n % 3 == 0) SbFx.sound(sl, at, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 0.6F,
                            1.1F + sl.random.nextFloat() * 0.5F);
                });
            }
            com.renanpontes.isekaimahou.glyph.SpellScheduler.schedule(hits * GAP + 4, () -> {
                if (!le.isAlive() || le.isRemoved() || !(le.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                le.invulnerableTime = 0;
                ctx.damage(le, FINAL, stats, null);
                if (!le.isAlive()) return;
                com.renanpontes.isekaimahou.feature.class_steelball.SbSpin.apply(le, caster, 1, stats.duration(200));
                net.minecraft.world.phys.Vec3 from = caster != null ? caster.position() : ctx.eyePos();
                net.minecraft.world.phys.Vec3 dir = le.position().subtract(from).multiply(1, 0, 1);
                if (dir.lengthSqr() < 1.0E-4) dir = ctx.look();
                dir = dir.normalize().scale(1.4 * stats.power());
                le.push(dir.x, 0.5, dir.z);
                le.hurtMarked = true;
                SbFx.impact(sl, le.position().add(0, le.getBbHeight() * 0.5, 0), true);
                SbFx.sound(sl, le.position(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.3F);
            });
        }
    }

    private SbTechniqueGlyphs() {}
}
