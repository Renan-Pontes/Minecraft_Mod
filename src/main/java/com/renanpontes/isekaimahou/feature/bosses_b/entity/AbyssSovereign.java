package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_b.BbRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Soberano do Abismo — lorde de sombra coroado de chifres (chefe do Abismo, deixa o Núcleo do Guardião da Sombra).
 * Fase 1: cetro, garras, Passo Sombrio, Correntes do Abismo (linha telegrafada + corrente arremessada), orbes do vazio,
 * sombras invocadas, Maré Sombria (anel que se expande com brechas seguras) e Queda do Soberano (salto com pouso marcado).
 * Fase 2 (66%): Eclipse (escurece tudo e faz tentáculos brotarem em círculos marcados) e Erupção Abissal (anel que se pula).
 * Fase 3 (33%): Ascensão Abissal — mais rápido, ondas e marés duplas, eclipse imediato.
 * Todo golpe em área/linha é telegrafado no chão pelo kit do núcleo.
 */
public class AbyssSovereign extends BbBoss {
    private static final EntityDataAccessor<Integer> DATA_ECLIPSE = SynchedEntityData.defineId(AbyssSovereign.class, EntityDataSerializers.INT);
    private static final net.minecraft.resources.ResourceLocation ASCENDANCE = IsekaiMahou.id("abyss_ascendance");
    /** Aquecimento das correntes (linha no chão até o arremesso). */
    private static final int CHAIN_WARN = 16;
    /** Maré Sombria: aviso, alcance e velocidade da onda. */
    private static final int TIDE_WARN = 24;
    private static final float TIDE_RANGE = 24F;
    private static final double TIDE_SPEED = 0.7;
    /** Ticks que cada onda leva para ir do centro até a borda. */
    private static final int TIDE_TRAVEL = Mth.ceil(TIDE_RANGE / TIDE_SPEED);
    /**
     * Fase 3: a 2ª maré sai {@code TIDE_GAP} ticks depois da 1ª. Como as duas correm na mesma velocidade, em QUALQUER
     * distância do centro sobram 2 s entre a 1ª passar pelo jogador e a 2ª chegar — tempo de trocar de brecha.
     */
    private static final int TIDE_GAP = 40;
    /** A 2ª maré (fase 3) é revelada quando a crista da 1ª já passou das zonas verdes (raio 8). */
    private static final int TIDE_SECOND_SHOW = TIDE_WARN + 14;
    private static final int TIDE_DURATION = TIDE_WARN + TIDE_GAP + TIDE_TRAVEL + 1;

    private final Set<UUID> eruptionHit = new HashSet<>();
    private final Set<UUID> eruptionHit2 = new HashSet<>();
    /** Zonas verdes da 1ª maré (somem quando a 2ª é revelada, para não confundir). */
    private final List<Integer> tideSafeA = new ArrayList<>();
    private final List<Vec3> chainDirs = new ArrayList<>();
    private List<Vec3> summonAt = List.of();
    /** Maré Sombria: centro, setores de perigo (para o acerto) e arcos (para o visual) de cada onda. */
    private Vec3 tideCenter = Vec3.ZERO;
    private float tideBase;
    private final List<Telegraph.Shape> tideDangerA = new ArrayList<>();
    private final List<Telegraph.Shape> tideDangerB = new ArrayList<>();
    private final List<float[]> tideArcsA = new ArrayList<>();
    private final List<float[]> tideArcsB = new ArrayList<>();
    private final Set<UUID> tideHitA = new HashSet<>();
    private final Set<UUID> tideHitB = new HashSet<>();

    public AbyssSovereign(EntityType<? extends AbyssSovereign> type, Level level) {
        super(type, level);
        this.xpReward = 600;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 750.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    public String bossId() {
        return "abyss_sovereign";
    }

    @Override
    public int bossRgb() {
        return 0x9B4DFF;
    }

    @Override
    protected int teleColor() {
        return Telegraph.VOID;
    }

    @Override
    protected int rewardXp() {
        return 600;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[]{0.66F, 0.33F};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ECLIPSE, 0);
    }

    public boolean eclipseActive() {
        return entityData.get(DATA_ECLIPSE) > 0;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("slam", "claw", "teleport", "cast", "summon", "eclipse", "eruption", "tide", "leap");
    }

    @Override
    protected Vec3 castHand() {
        return position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.0)).add(0, 3.0, 0);
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected List<BossAttack> createAttacks() {
        List<BossAttack> l = new ArrayList<>();
        l.add(BossAttack.of("slam", 24, 40, this::slam).anim("slam").range(0, 4.8).weight(6).stationary().freeFacing());
        l.add(BossAttack.of("claw", 18, 28, this::claw).anim("claw").range(0, 4.0).weight(4).stationary().freeFacing());
        l.add(BossAttack.of("shadow_step", 34, 140, this::shadowStep).anim("teleport").range(5, 32).weight(4).stationary()
                .freeFacing().onEnd(() -> setInvisible(false)));
        l.add(BossAttack.of("chains", 40, 180, this::chains).anim("cast").range(3, 24).weight(4).stationary().freeFacing()
                .onEnd(chainDirs::clear));
        l.add(BossAttack.of("void_orbs", 40, 90, this::voidOrbs).anim("cast").range(6, 34).weight(5).stationary());
        l.add(BossAttack.of("summon", 40, 420, this::summonShades).anim("summon").weight(3).stationary()
                .when(t -> minionCount() < 1 + phase()));
        l.add(BossAttack.of("shadow_tide", TIDE_DURATION, 320, this::shadowTide).anim("tide").range(0, 20).weight(4).stationary()
                .onEnd(this::clearTide));
        l.add(BossAttack.of("abyssal_leap", 50, 220, this::abyssalLeap).anim("leap").range(9, 22).weight(3).stationary()
                .freeFacing().when(t -> onGround()));
        // v1.3 ASSINATURA — Sussurros do Abismo: a visão escurece e pisca invertida, vozes; uma marca persegue cada um
        l.add(BossAttack.of("whispers", 40, 480, this::whispers).anim("cast").range(0, 32).weight(4).stationary()
                .when(t -> !BossCurses.has(t, BossCurses.WHISPERS)));
        l.add(BossAttack.of("eclipse", 60, 900, this::eclipse).anim("eclipse").phase(2).weight(3).stationary()
                .when(t -> !eclipseActive()));
        l.add(BossAttack.of("eruption", 54, 260, this::eruption).anim("eruption").phase(2).range(0, 12).weight(4).stationary()
                .onEnd(() -> {
                    eruptionHit.clear();
                    eruptionHit2.clear();
                }));
        return l;
    }

    /** Golpe do cetro: cone telegrafado na frente (12 ticks), empurra. */
    private void slam(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            meleeCone(110F, 5.4F, 12, e -> {
                if (e.hurt(damageSources().mobAttack(this), attackPower())) {
                    Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.push(push.x * 1.3, 0.45, push.z * 1.3);
                    e.hurtMarked = true;
                }
            });
        }
        holdFacing();
        if (t == 8) BbFx.sound(sl, position(), SoundEvents.WARDEN_ATTACK_IMPACT, 0.6F, 0.6F);
        if (t != 12) return;
        Vec3 c = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.4));
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 30, 1.0, 0.2, 1.0, 0.08);
        BbFx.ring(sl, BbFx.dust(BbFx.SHADOW, 1.6F), c.add(0, 0.2, 0), 2.6, 28);
        BbFx.sound(sl, c, SoundEvents.WARDEN_ATTACK_IMPACT, 1.4F, 0.7F);
        BossKit.shake(sl, c, 14, 0.7F, 8);
    }

    /** Garras rápidas: cone curto telegrafado (8 ticks), corrompem. */
    private void claw(LivingEntity target, int t) {
        if (t == 0) {
            lockFacing(target);
            meleeCone(130F, 4.4F, 8, e -> {
                if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.7F)) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
                }
            });
        }
        holdFacing();
        if (t != 8) return;
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.8)).add(0, 1.6, 0);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 3, 0.8, 0.3, 0.8, 0);
        sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.3F), c.x, c.y, c.z, 16, 1.0, 0.4, 1.0, 0);
        BbFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
    }

    /** Passo Sombrio: afunda na sombra e reaparece atrás do alvo; o golpe sai num cone marcado 8 ticks depois. */
    private void shadowStep(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 40, 0.8, 0.1, 0.8, 0.02);
            BbFx.sound(sl, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
        }
        if (t < 6) {
            sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 2.0F), getX(), getY() + 3.6 - t * 0.6, getZ(), 10, 0.6, 0.3, 0.6, 0);
        }
        if (t == 6) {
            setInvisible(true);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 20, 0.6, 0.1, 0.6, 0.01);
        }
        if (t == 14) {
            Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-2.8);
            Vec3 dest = target.position().add(back);
            if (!randomTeleport(dest.x, target.getY(), dest.z, false)) blinkNear(target, 3.0);
            setInvisible(false);
            lockFacing(target);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.06);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 30, 0.5, 1.0, 0.5, 0.05);
            BbFx.sound(sl, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.7F);
            triggerAnim("action", "claw");
            meleeCone(130F, 4.8F, 8, e -> {
                if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.85F)) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                }
            });
        }
        if (t > 14) holdFacing();
        if (t == 22) BbFx.sound(sl, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
    }

    /**
     * Correntes do Abismo: uma linha marca no chão o caminho de cada corrente (16 ticks) e só então ela é arremessada
     * pela linha travada. Se acertar, prende por 1,5 s e o Soberano puxa a vítima (o glifo Correntes prenderia 5 s —
     * longo demais para desviar do golpe seguinte, por isso o chefe usa a corrente própria).
     */
    private void chains(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            chainDirs.clear();
            lockFacing(target);
            List<LivingEntity> picks = new ArrayList<>();
            picks.add(target);
            for (LivingEntity e : enemiesNear(position(), 22)) {
                if (picks.size() >= phase()) break;
                if (e != target && e instanceof Player && hasLineOfSight(e)) picks.add(e);
            }
            Vec3 hand = castHand();
            for (LivingEntity e : picks) {
                Vec3 dir = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(hand).normalize();
                chainDirs.add(dir);
                warn(Telegraph.Shape.line(groundAhead(position(), dir, 1.0, e.getY()), groundAhead(position(), dir, 27, e.getY()), 1.8F)
                        .warmup(CHAIN_WARN).color(Telegraph.VOID));
            }
            announce("chains", Announce.STYLE_SPELL);
            hint("chains");
            BbFx.sound(sl, position(), SoundEvents.CHAIN_PLACE, 1.5F, 0.4F);
        }
        holdFacing();
        Vec3 hand = castHand();
        if (t < CHAIN_WARN && t % 2 == 0) {
            double a = t * 0.6;
            sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 1.2F), hand.x + Math.cos(a) * 0.6, hand.y, hand.z + Math.sin(a) * 0.6, 2, 0.05, 0.05, 0.05, 0);
        }
        if (t != CHAIN_WARN) return;
        for (Vec3 dir : chainDirs) {
            BossBolt.shoot(this, BbRegistries.ABYSSAL_ORB.get(), hand, dir, 1.15, attackPower() * 0.35F, null, 0F, BossBolt.KIND_CHAIN);
        }
        BbFx.sound(sl, hand, SoundEvents.CHAIN_BREAK, 1.5F, 0.5F);
        BbFx.sound(sl, hand, SoundEvents.TRIDENT_THROW.value(), 1.2F, 0.6F);
    }

    /** Orbes do vazio (teleguiados fracos, podem ser rebatidos): o primeiro pode ser o glifo Drenar Vida. */
    private void voidOrbs(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t < 10 && t % 2 == 0) {
            Vec3 h = castHand();
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.5F), h.x, h.y, h.z, 4, 0.3, 0.3, 0.3, 0);
        }
        if (t == 10 && BbSpells.cast(this, Element.SHADOW, glyphPower(2.4F), "projectile", "life_drain")) return;
        boolean shot = t == 10 || t == 18 || t == 26 || (phase() >= 3 && t == 34);
        if (!shot) return;
        Vec3 from = castHand();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
        int n = phase() >= 3 ? 3 : 1;
        for (int i = 0; i < n; i++) {
            float off = n == 1 ? 0 : (i - 1) * 14F;
            Vec3 dir = aim.yRot(off * Mth.DEG_TO_RAD);
            BossBolt.shoot(this, BbRegistries.ABYSSAL_ORB.get(), from, dir, 0.85, attackPower() * 0.55F, target, 0.06F);
        }
        BbFx.sound(sl, from, SoundEvents.WITHER_SHOOT, 0.9F, 0.6F);
    }

    /** Sombras invocadas por id (creeping_shade / void_crawler); se não existirem, vexes do vanilla. Os pontos são marcados antes. */
    private void summonShades(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int n = phase() >= 2 ? 3 : 2;
        if (t == 0) summonAt = summonSpots(n, 4, 20);
        if (t < 20 && t % 4 == 0) BbFx.ring(sl, BbFx.dust(BbFx.SHADOW_DEEP, 1.8F), position().add(0, 0.1, 0), 3.5, 24);
        if (t != 20) return;
        for (int i = 0; i < summonAt.size(); i++) {
            Vec3 at = summonAt.get(i);
            String id = phase() >= 2 && i % 2 == 0 ? "void_crawler" : "creeping_shade";
            if (summon(id, EntityType.VEX, at, target) != null) {
                sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 25, 0.4, 0.6, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 20, 0.3, 0.8, 0.3, 0.05);
            }
        }
        announce("summon", Announce.STYLE_BOSS);
        BbFx.sound(sl, position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.5F);
    }

    // ---------------- Maré Sombria ----------------

    /**
     * Maré Sombria: o Soberano crava o cetro e uma maré de sombra se espalha em anel — alta demais para pular.
     * Os setores de perigo são telegrafados (cones violeta) e as BRECHAS seguras ficam sem marca, com uma zona verde.
     * Fase 3: quando a 1ª maré já passou das zonas verdes, surge a 2ª com as brechas giradas; ela sai 2 s depois da
     * 1ª (é preciso trocar de brecha assim que a 1ª passar por você).
     */
    private void shadowTide(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        boolean twice = phase() >= 3;
        if (t == 0) {
            clearTide();
            tideCenter = position();
            tideBase = random.nextFloat() * 360F;
            tideTelegraph(tideBase, tideDangerA, tideArcsA, TIDE_WARN, tideSafeA, Telegraph.VOID);
            announce("shadow_tide", Announce.STYLE_SPELL);
            hint("tide");
            BbFx.sound(sl, position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.6F, 0.6F);
        }
        // Fases 1–2: uma maré só; o golpe acaba junto com a animação, quando a onda chega à borda.
        if (!twice && t >= TIDE_WARN + TIDE_TRAVEL + 1) {
            endAttack();
            return;
        }
        if (t < TIDE_WARN && t % 2 == 0) {
            double a = t * 0.5;
            for (int i = 0; i < 3; i++) {
                double aa = a + i * Math.PI * 2 / 3;
                sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 1.8F), getX() + Math.cos(aa) * 1.8, getY() + 0.2 + t * 0.08,
                        getZ() + Math.sin(aa) * 1.8, 1, 0, 0, 0, 0);
            }
        }
        int second = TIDE_WARN + TIDE_GAP;
        if (t == TIDE_WARN || (twice && t == second)) {
            BbFx.sound(sl, position(), SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.5F);
            BbFx.sound(sl, position(), SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.6F);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.4, getZ(), 60, 1.5, 0.2, 1.5, 0.15);
            BossKit.shake(sl, position(), 30, 1.2F, 18);
        }
        if (twice && t == TIDE_SECOND_SHOW) {
            // Segunda maré: brechas giradas (no meio dos setores de perigo da 1ª). As zonas verdes antigas somem.
            for (int id : tideSafeA) Telegraph.cancel(sl, id);
            tideSafeA.clear();
            // Cor diferente (vermelho) para não confundir com os setores da 1ª maré, que continuam marcados até ela acabar.
            tideTelegraph(tideBase + 60F, tideDangerB, tideArcsB, second - TIDE_SECOND_SHOW, null, Telegraph.DANGER);
            hint("tide_again");
            BbFx.sound(sl, position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.4F, 0.8F);
        }
        // O cetro sobe de novo e crava junto com a 2ª maré (impacto da animação "slam" no tick 12).
        if (twice && t == second - 12) triggerAnim("action", "slam");
        tideWave(sl, t - TIDE_WARN, tideDangerA, tideArcsA, tideHitA);
        if (twice) tideWave(sl, t - second, tideDangerB, tideArcsB, tideHitB);
    }

    private void tideTelegraph(float base, List<Telegraph.Shape> dangers, List<float[]> arcs, int warmup,
                               @javax.annotation.Nullable List<Integer> safeIds, int rgb) {
        dangers.clear();
        arcs.clear();
        int gaps = 3;
        float gapWidth = phase() >= 3 ? 30F : 36F;
        float step = 360F / gaps;
        float[] centers = new float[gaps];
        for (int i = 0; i < gaps; i++) centers[i] = base + i * step + (random.nextFloat() - 0.5F) * 24F;
        int travel = TIDE_TRAVEL;
        for (int i = 0; i < gaps; i++) {
            float start = centers[i] + gapWidth / 2;
            float end = centers[(i + 1) % gaps] + (i + 1 == gaps ? 360F : 0F) - gapWidth / 2;
            float arc = end - start;
            float mid = Mth.wrapDegrees(start + arc / 2);
            Telegraph.Shape cone = Telegraph.Shape.cone(tideCenter, mid, arc, TIDE_RANGE).warmup(warmup).hold(travel)
                    .color(rgb).vertical(1.5, 3.5);
            warn(cone);
            dangers.add(cone);
            arcs.add(new float[]{mid, arc / 2});
            // Zona verde no meio da brecha: "fique aqui".
            Vec3 gap = tideCenter.add(Vec3.directionFromRotation(0, centers[i]).scale(8.0));
            Vec3 g = groundAt(gap.x, tideCenter.y, gap.z, 3, 4);
            int id = warn(Telegraph.Shape.safe(g != null ? g : gap, 2.2F).warmup(warmup + travel));
            if (safeIds != null && id >= 0) safeIds.add(id);
        }
    }

    private void tideWave(ServerLevel sl, int age, List<Telegraph.Shape> dangers, List<float[]> arcs, Set<UUID> hit) {
        if (age < 0 || dangers.isEmpty()) return;
        double r = 1.0 + age * TIDE_SPEED;
        if (r > TIDE_RANGE + 0.5) return;
        // Visual: a crista da maré só nos setores de perigo (as brechas ficam limpas).
        int pts = Math.min(60, (int) (r * 5));
        for (int i = 0; i < pts; i++) {
            float yaw = i * 360F / pts;
            if (!inArcs(arcs, yaw)) continue;
            Vec3 p = tideCenter.add(Vec3.directionFromRotation(0, yaw).scale(r));
            sl.sendParticles(BbFx.dust(i % 2 == 0 ? BbFx.SHADOW_DEEP : BbFx.SHADOW, 2.2F), p.x, p.y + 0.3 + (age % 2) * 0.6, p.z, 1, 0.05, 0.3, 0.05, 0);
            if (i % 4 == age % 4) sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 1.2, p.z, 1, 0.1, 0.4, 0.1, 0.01);
        }
        if (age % 6 == 0) BbFx.sound(sl, tideCenter, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, 1.5F, 0.5F);
        for (LivingEntity e : enemiesNear(tideCenter, r + 1.5)) {
            double d = Math.sqrt(e.distanceToSqr(tideCenter.x, e.getY(), tideCenter.z));
            if (Math.abs(d - r) > 1.2 || e.getY() - tideCenter.y > 3.5 || tideCenter.y - e.getY() > 2.0) continue;
            boolean danger = false;
            for (Telegraph.Shape s : dangers) {
                if (s.contains(e)) {
                    danger = true;
                    break;
                }
            }
            if (!danger || !hit.add(e.getUUID())) continue;
            if (e.hurt(spellSource(null), attackPower() * 0.8F)) {
                Vec3 push = e.position().subtract(tideCenter).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(push.x * 1.2, 0.45, push.z * 1.2);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            }
        }
    }

    private static boolean inArcs(List<float[]> arcs, float yaw) {
        for (float[] a : arcs) if (Math.abs(Mth.wrapDegrees(yaw - a[0])) <= a[1]) return true;
        return false;
    }

    private void clearTide() {
        tideDangerA.clear();
        tideDangerB.clear();
        tideArcsA.clear();
        tideArcsB.clear();
        tideHitA.clear();
        tideHitB.clear();
        tideSafeA.clear();
    }

    // ---------------- Queda do Soberano ----------------

    /**
     * Queda do Soberano: agacha (12 ticks), salta até onde o alvo ESTAVA e cai com uma onda de choque.
     * O ponto de pouso fica marcado durante todo o voo — dá para sair. Também é a resposta a quem foge num pilar.
     */
    private void abyssalLeap(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            BbFx.sound(sl, position(), SoundEvents.WARDEN_ROAR, 1.0F, 1.2F);
        }
        if (t < 12) {
            holdFacing();
            if (t % 3 == 0) sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 1.6F), getX(), getY() + 0.2, getZ(), 8, 0.8, 0.1, 0.8, 0.02);
            return;
        }
        if (t == 12) {
            lockFacing(target);
            Vec3 dest = target.position();
            double dist = Math.sqrt(dest.subtract(position()).horizontalDistanceSqr());
            double power = Mth.clamp((8 + dist * 1.1) / 20.0, 0.5, 1.4);
            int flight = BossKit.leapTo(this, dest, power);
            BbFx.sound(sl, position(), SoundEvents.RAVAGER_ROAR, 1.2F, 0.6F);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 30, 1.0, 0.1, 1.0, 0.1);
            strike(Telegraph.Shape.circle(dest, 4.2F).warmup(Math.max(12, flight)).color(Telegraph.VOID), foes -> {
                for (LivingEntity e : foes) {
                    if (!e.hurt(damageSources().mobAttack(this), attackPower() * 0.9F)) continue;
                    Vec3 push = e.position().subtract(dest).multiply(1, 0, 1);
                    push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : push.normalize();
                    e.setDeltaMovement(push.x * 1.1, 0.7, push.z * 1.1);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
                }
                sl.sendParticles(ParticleTypes.EXPLOSION, dest.x, dest.y + 0.5, dest.z, 2, 1.0, 0.2, 1.0, 0);
                sl.sendParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 0.3, dest.z, 60, 2.0, 0.2, 2.0, 0.15);
                BbFx.ring(sl, BbFx.dust(BbFx.SHADOW, 2.0F), dest.add(0, 0.2, 0), 4.0, 36);
                BbFx.sound(sl, dest, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
                BossKit.shake(sl, dest, 24, 1.6F, 16);
            });
            return;
        }
        holdFacing();
        if (!onGround()) sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.6F), getX(), getY() + 1.5, getZ(), 3, 0.5, 0.8, 0.5, 0);
    }

    /**
     * Sussurros do Abismo (assinatura do Soberano): ele sussurra na mente de todos — a tela escurece, vozes vêm de trás
     * e a visão PISCA invertida (gráficos Alto+). Ao mesmo tempo uma marca do vazio persegue cada um e trava no fim:
     * continue se mexendo mesmo meio cego.
     */
    private void whispers(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("whispers", Announce.STYLE_SPELL);
            BbFx.sound(sl, position(), SoundEvents.WARDEN_NEARBY_CLOSEST, 2.5F, 0.5F);
            for (LivingEntity e : enemiesNear(position(), 32)) {
                if (e instanceof ServerPlayer sp) BossKit.vignette(sp, 0x3A0A6A, 0.8F, 20);
            }
        }
        if (t < 16 && t % 2 == 0) {
            // Antecipação: sombra sendo sugada para a boca do Soberano
            Vec3 head = position().add(0, getBbHeight() * 0.85, 0);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 p = head.add(Math.cos(a) * 4, (random.nextDouble() - 0.3) * 2, Math.sin(a) * 4);
                Vec3 v = head.subtract(p).normalize();
                sl.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 0, v.x, v.y, v.z, 0.3);
            }
        }
        if (t != 16) return;
        int amp = phase() >= 3 ? 1 : 0;
        BbFx.sound(sl, position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.5F, 0.4F);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() * 0.8, getZ(), 30, 1.5, 1.0, 1.5, 0.08);
        for (LivingEntity e : enemiesNear(position(), 32)) {
            BossCurses.apply(e, BossCurses.WHISPERS, 160 + 40 * phase(), amp);
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
            int warm = 56 - 6 * phase();
            strike(Telegraph.Shape.follow(e, 2.4F).warmup(warm).lock(14).color(Telegraph.VOID), foes -> {
                Vec3 at = foes.isEmpty() ? e.position() : foes.get(0).position();
                BbFx.column(sl, BbFx.dust(BbFx.SHADOW, 2.0F), at, 4.0, 12, 0.3);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 1, at.z, 12, 0.6, 0.8, 0.6, 0.05);
                BbFx.sound(sl, at, SoundEvents.WARDEN_ATTACK_IMPACT, 1.4F, 0.6F);
                for (LivingEntity f : foes) {
                    if (f.hurt(spellSource(null), attackPower() * 0.7F)) {
                        BossCurses.apply(f, BossCurses.WHISPERS, 100, amp);
                        f.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                    }
                }
            });
        }
        hint("whispers");
    }

    // ---------------- Fase 2+ ----------------

    /** Eclipse: a escuridão engole a arena; tentáculos brotam em círculos marcados e o Soberano se regenera. */
    private void eclipse(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("eclipse", Announce.STYLE_ULTIMATE);
            BbFx.sound(sl, position(), SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.5F);
        }
        if (t < 40 && t % 2 == 0) {
            double r = 9.0 * (1.0 - t / 40.0) + 1.0;
            BbFx.ring(sl, ParticleTypes.SQUID_INK, position().add(0, 1.0 + t * 0.05, 0), r, 20);
        }
        if (t == 40) {
            entityData.set(DATA_ECLIPSE, phase() >= 3 ? 400 : 320);
            if (bossBar() != null) bossBar().setDarkenScreen(true);
            for (LivingEntity e : enemiesNear(position(), 40)) {
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 340, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                BossCurses.apply(e, BossCurses.WHISPERS, 200, phase() >= 3 ? 1 : 0);
            }
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 2, getZ(), 120, 3.0, 2.0, 3.0, 0.15);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            BbFx.sound(sl, position(), SoundEvents.WITHER_SPAWN, 1.6F, 0.5F);
            BossKit.shake(sl, position(), 40, 1.4F, 30);
            hint("eclipse");
        }
    }

    /** Erupção Abissal: anel de sombra que se expande pelo chão (pule por cima!). Fase 3: duas ondas. */
    private void eruption(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            warn(Telegraph.Shape.ring(position(), 1.0F, 22.6F).warmup(18).color(Telegraph.VOID).noFlash().vertical(0.5, 1.0));
            hint("eruption");
        }
        if (t == 18 && phase() >= 3) {
            warn(Telegraph.Shape.ring(position(), 1.0F, 22.6F).warmup(12).color(Telegraph.DANGER).noFlash().vertical(0.5, 1.0));
        }
        if (t < 18 && t % 2 == 0) {
            sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 2.0F), getX(), getY() + 1.0, getZ(), 12, 1.5, 1.0, 1.5, 0);
            if (t == 0) BbFx.sound(sl, position(), SoundEvents.WARDEN_ROAR, 1.2F, 0.8F);
        }
        if (t == 18) {
            BbFx.sound(sl, position(), SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
            sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 3, 1.0, 0.2, 1.0, 0);
            BossKit.shake(sl, position(), 24, 1.0F, 12);
        }
        wave(sl, t - 18, eruptionHit);
        if (phase() >= 3) wave(sl, t - 30, eruptionHit2);
    }

    /** Uma onda da Erupção; cada onda tem o próprio registro de quem já levou (a 2ª não "zera" a 1ª). */
    private void wave(ServerLevel sl, int age, Set<UUID> hitSet) {
        if (age < 0 || age > 24) return;
        if (age == 0) hitSet.clear();
        double r = 1.0 + age * 0.9;
        int pts = Math.min(72, (int) (r * 6));
        Vec3 c = position().add(0, 0.15, 0);
        BbFx.ring(sl, BbFx.dust(BbFx.SHADOW, 1.8F), c, r, pts);
        if (age % 2 == 0) BbFx.ring(sl, ParticleTypes.SQUID_INK, c.add(0, 0.3, 0), r, pts / 3);
        for (LivingEntity e : enemiesNear(position(), r + 1.5)) {
            double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
            if (Math.abs(d - r) > 1.1 || e.getY() - getY() > 1.1 || !e.onGround() || !hitSet.add(e.getUUID())) continue;
            if (e.hurt(spellSource(null), attackPower() * 0.75F)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.75, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
            }
        }
    }

    // =====================================================================================
    //  Passivo: Eclipse, movimento, fases
    // =====================================================================================
    @Override
    protected void tickBoss(ServerLevel sl) {
        int ec = entityData.get(DATA_ECLIPSE);
        // A barra escurece a tela enquanto durar o Eclipse (também depois de recarregar o mundo).
        if (bossBar() != null) bossBar().setDarkenScreen(ec > 0);
        if (ec <= 0) return;
        entityData.set(DATA_ECLIPSE, ec - 1);
        if (ec - 1 == 0) {
            if (bossBar() != null) bossBar().setDarkenScreen(false);
            BbFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.5F);
        }
        int every = phase() >= 3 ? 18 : 26;
        if (ec % every == 0) {
            List<LivingEntity> en = enemiesNear(position(), 28);
            if (!en.isEmpty()) tendril(sl, en.get(random.nextInt(en.size())).position());
        }
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.0025F);
    }

    /** Tentáculo do Eclipse: círculo marcado sob o alvo; brota 26 ticks depois. */
    private void tendril(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.2, at.z, 6, 0.6, 0.05, 0.6, 0.01);
        strike(Telegraph.Shape.circle(at, 2.0F).warmup(26).color(Telegraph.VOID), foes -> {
            BbFx.column(sl, BbFx.dust(BbFx.SHADOW, 2.0F), at, 4.0, 14, 0.25);
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.5, at.z, 25, 0.4, 1.2, 0.4, 0.05);
            BbFx.sound(sl, at, SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.6F);
            for (LivingEntity e : foes) {
                if (e.hurt(spellSource(null), attackPower() * 0.6F)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                }
            }
        });
    }

    @Override
    protected void moveCombat(LivingEntity target) {
        double d = distanceTo(target);
        if (d > 3.4) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    /** Alvo num pilar/buraco/barco: salta até ele (pouso marcado); longe demais, aparece do lado. */
    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        BossAttack leap = attack("abyssal_leap");
        if (onGround() && distanceTo(target) < 22 && leap != null && isReady("abyssal_leap")) {
            startAttack(leap);
        } else if (!BossKit.blinkNear(this, target, 3.5)) {
            blinkNear(target, 3.0);
        }
    }

    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        // Na escuridão total ele é mais resistente.
        return eclipseActive() ? amount * 0.8F : amount;
    }

    @Override
    protected void onIntroStart(ServerLevel sl) {
        BbFx.sound(sl, position(), SoundEvents.WARDEN_EMERGE, 2.0F, 0.6F);
        BbFx.column(sl, ParticleTypes.SQUID_INK, position(), 5.0, 30, 0.8);
        BossKit.roar(this, SoundEvents.WARDEN_ROAR, 0.7F);
    }

    @Override
    protected void onPhaseStart(ServerLevel sl, int phase) {
        BbFx.sound(sl, position(), SoundEvents.WITHER_AMBIENT, 2.0F, 0.4F);
    }

    @Override
    protected void transitionFx(ServerLevel sl, int left) {
        if (left % 2 != 0) return;
        double r = 1.0 + left / 10.0;
        BbFx.ring(sl, BbFx.dust(BbFx.SHADOW_DEEP, 2.0F), position().add(0, 0.5 + (50 - left) * 0.06, 0), r, 18);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 6, 0.6, 1.2, 0.6, 0.02);
    }

    /** Onda de choque da troca de fase: empurra e fere quem ficou no círculo marcado. */
    @Override
    protected void phaseWaveHit(ServerLevel sl, LivingEntity e) {
        Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize();
        e.setDeltaMovement(push.x * 1.6, 0.6, push.z * 1.6);
        e.hurtMarked = true;
        e.hurt(spellSource(null), attackPower() * 0.4F);
    }

    @Override
    protected void onPhaseEnd(ServerLevel sl, int phase) {
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 80, 2.0, 1.0, 2.0, 0.2);
        BbFx.sound(sl, position(), SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 0.7F);
        if (phase == 2) {
            LivingEntity t = getTarget();
            startAttack(attack("summon"));
            if (t != null) faceTarget(t);
        }
        if (phase >= 3) {
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(ASCENDANCE)) {
                speed.addPermanentModifier(new AttributeModifier(ASCENDANCE, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            if (!eclipseActive()) startAttack(attack("eclipse"));
        }
    }

    // =====================================================================================
    //  Morte
    // =====================================================================================
    @Override
    protected void onBossDeath(ServerLevel sl) {
        entityData.set(DATA_ECLIPSE, 0);
        clearTide();
        setInvisible(false);
    }

    @Override
    protected int deathDuration() {
        return 94; // v1.0: 4 s de animação + o corpo se desfazendo
    }

    @Override
    protected void deathFx(ServerLevel sl, int t) {
        if (t % 3 == 0) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1 + t * 0.03, getZ(), 8, 0.8, 1.2, 0.8, 0.04);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 10, 0.6, 1.4, 0.6, 0.08);
        }
        if (t == 2) BbFx.sound(sl, position(), SoundEvents.WITHER_DEATH, 1.6F, 0.6F);
        if (t == 50) BbFx.sound(sl, position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.5F, 0.5F);
    }

    @Override
    protected void onFinalDeath(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 2, getZ(), 150, 1.5, 2.0, 1.5, 0.25);
        sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2, getZ(), 40, 1.0, 1.5, 1.0, 0.08);
        BbFx.sound(sl, position(), SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.5F);
    }

    // =====================================================================================
    //  Cliente / sons / dados
    // =====================================================================================
    @Override
    protected void clientFx() {
        if (isInvisible()) return;
        boolean ec = eclipseActive();
        int n = ec ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.6 + random.nextDouble() * (ec ? 2.5 : 1.0);
            level().addParticle(BbFx.dust(BbFx.SHADOW_DEEP, 1.4F), getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 3.5,
                    getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + 1 + random.nextDouble() * 2,
                    getZ() + (random.nextDouble() - 0.5) * 1.5, 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WARDEN_STEP, 0.8F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Eclipse", entityData.get(DATA_ECLIPSE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_ECLIPSE, tag.getInt("Eclipse"));
    }
}
