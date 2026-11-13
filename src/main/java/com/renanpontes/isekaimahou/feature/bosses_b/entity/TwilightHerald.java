package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_b.BbRegistries;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arauto do Crepúsculo — o CHEFE FINAL. Alterna posturas de LUZ e de SOMBRA (cada uma com golpes próprios e
 * resistência ao próprio elemento / fraqueza ao oposto); a cada troca ele pousa por um instante (janela de ataque).
 * Golpes das duas posturas: foice (mergulha para cortar), Sentença do Crepúsculo (magia PROGRAMÁVEL mista:
 * projétil de luz → AO ACERTAR → explosão de sombra) e Ruptura Crepuscular (piso em xadrez luz/sombra: a cor errada explode).
 * Fase 2 (70%): Nova Crepuscular e trocas mais rápidas. Fase 3 (35%): EXPANSÃO DE DOMÍNIO — pulsos de luz e sombra em
 * toda a arena, com ZONAS SEGURAS (verdes) que giram a cada pulso. Ao morrer: cutscene final e o Coração do Crepúsculo.
 */
public class TwilightHerald extends BbBoss {
    public static final int LIGHT = 0;
    public static final int SHADOW = 1;
    /** Raio do domínio próprio (sem o glifo) — também usado pela névoa do cliente. */
    public static final float DOMAIN_RADIUS = 16.0F;
    /** Raio do domo do glifo domain_expansion com Expandir (10 + 4). */
    private static final float GLYPH_DOMAIN_RADIUS = 14.0F;
    private static final int DOMAIN_TICKS = 250;
    private static final int FIRST_PULSE = 40;
    private static final int PULSE = 50;
    private static final float ZONE_RADIUS = 2.6F;
    /** Raio da órbita enquanto preso ao próprio domínio (o domo do glifo desaba se o dono passar de raio+4). */
    private static final double ANCHOR_ORBIT = 5.0;
    private static final int TWILIGHT_RGB = 0xC06AA0;
    private static final int LANCE_WARN = 8;
    private static final int[] LANCE_SHOTS = {10, 16, 22, 28};
    private static final int CHAIN_WARN = 16;
    private static final int VERDICT_WARN = 18;
    /** Sentença: velocidade do orbe de reserva, do projétil do glifo (forma projectile do núcleo) e raio da explosão (= nova). */
    private static final double VERDICT_ORB_SPEED = 1.0;
    private static final double GLYPH_BOLT_SPEED = 1.6;
    private static final float VERDICT_BLAST = 4.0F;
    /** Ruptura Crepuscular: casas de 3×3, tabuleiro 9×9; tempos das explosões (1ª cor, 2ª cor, 1ª de novo na fase 3). */
    private static final double TILE = 3.0;
    private static final int TILES = 9;
    private static final int RUPTURE_A = 30;
    private static final int RUPTURE_B = 54;
    private static final int RUPTURE_C = 78;

    private static final EntityDataAccessor<Integer> DATA_STANCE = SynchedEntityData.defineId(TwilightHerald.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DOMAIN = SynchedEntityData.defineId(TwilightHerald.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_DOMAIN_CENTER = SynchedEntityData.defineId(TwilightHerald.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_DOMAIN_GLYPH = SynchedEntityData.defineId(TwilightHerald.class, EntityDataSerializers.BOOLEAN);
    /** Raio do domínio atual, fixado quando ele nasce (o domo do glifo pode cair antes: a barreira própria mantém o raio). */
    private static final EntityDataAccessor<Float> DATA_DOMAIN_RADIUS = SynchedEntityData.defineId(TwilightHerald.class, EntityDataSerializers.FLOAT);

    private int stanceTimer;
    /** Pouso adiado (troca de postura, fim do domínio): espera a animação/o golpe em andamento terminar. */
    private int pendingLand;
    private int pendingLandAt;
    private int domainPulse;
    private float domainSpin;
    private double orbit;
    private int orbitDir = 1;
    private Vec3 beamCenter;
    private Vec3 novaCenter = Vec3.ZERO;
    private final List<Vec3> chainDirs = new ArrayList<>();
    private final Set<UUID> novaHitA = new HashSet<>();
    private final Set<UUID> novaHitB = new HashSet<>();
    private final Vec3[][] lanceAim = new Vec3[LANCE_SHOTS.length][];
    private final Vec3[] lanceFrom = new Vec3[LANCE_SHOTS.length];
    private Vec3 verdictFrom = Vec3.ZERO;
    private Vec3 verdictDir = new Vec3(0, 0, 1);
    @Nullable
    private SpellRecipe verdictSpell;
    private Vec3 ruptureCenter = Vec3.ZERO;
    private boolean ruptureLightFirst;
    private List<Vec3> echoSpots = List.of();

    public TwilightHerald(EntityType<? extends TwilightHerald> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String bossId() {
        return "twilight_herald";
    }

    @Override
    public int bossRgb() {
        return stance() == LIGHT ? 0xFFE6A0 : 0xB070E0;
    }

    @Override
    protected int teleColor() {
        return stance() == LIGHT ? Telegraph.HOLY : Telegraph.VOID;
    }

    @Override
    protected int rewardXp() {
        return 1500;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return stance() == LIGHT ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[]{0.7F, 0.35F};
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STANCE, LIGHT);
        builder.define(DATA_DOMAIN, 0);
        builder.define(DATA_DOMAIN_CENTER, BlockPos.ZERO);
        builder.define(DATA_DOMAIN_GLYPH, false);
        builder.define(DATA_DOMAIN_RADIUS, DOMAIN_RADIUS);
    }

    public int stance() {
        return entityData.get(DATA_STANCE);
    }

    /** Domínio do Arauto ativo (próprio ou com o domo do glifo)? */
    public boolean domainActive() {
        return entityData.get(DATA_DOMAIN) > 0;
    }

    /** O domo visível é o do glifo domain_expansion (entidade do módulo domain)? */
    public boolean domainByGlyph() {
        return entityData.get(DATA_DOMAIN_GLYPH);
    }

    public Vec3 domainCenter() {
        return Vec3.atBottomCenterOf(entityData.get(DATA_DOMAIN_CENTER));
    }

    /** Raio do domínio (barreira, pulsos, névoa do cliente): o do glifo (14) ou o próprio (16), fixo enquanto durar. */
    public float domainRadius() {
        return entityData.get(DATA_DOMAIN_RADIUS);
    }

    /** O domínio está de pé: o Arauto fica perto do centro e não se teleporta. */
    public boolean domainAnchored() {
        return domainActive();
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("sweep", "cast_light", "cast_shadow", "beams", "judgment", "teleport", "summon", "shift", "domain", "nova",
                "rupture", "verdict");
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
        // Ambas as posturas
        l.add(BossAttack.of("sweep", 22, 34, this::sweep).anim("sweep").range(0, 5.5).weight(6).stationary().freeFacing());
        l.add(BossAttack.of("twilight_verdict", 40, 170, this::verdict).anim("verdict").range(5, 30).weight(4).stationary()
                .freeFacing());
        l.add(BossAttack.of("twilight_rupture", 90, 380, this::rupture).anim("rupture").range(0, 24).weight(4).stationary()
                .when(t -> !domainActive()));
        l.add(BossAttack.of("twilight_nova", 56, 300, this::nova).anim("nova").phase(2).range(0, 14).weight(4).stationary()
                .when(t -> !domainActive())
                .onEnd(() -> {
                    novaHitA.clear();
                    novaHitB.clear();
                }));
        // Postura de LUZ
        l.add(BossAttack.of("dawn_lances", 34, 60, this::dawnLances).anim("cast_light").range(3, 40).weight(6).stationary()
                .when(t -> stance() == LIGHT));
        l.add(BossAttack.of("twin_beams", 110, 360, this::twinBeams).anim("beams").range(0, 18).weight(3).stationary().freeFacing()
                .when(t -> stance() == LIGHT && !domainActive()).onEnd(this::stopBeams));
        l.add(BossAttack.of("sunfall", 60, 260, this::sunfall).anim("judgment").range(0, 34).weight(4).stationary()
                .when(t -> stance() == LIGHT && !domainActive()));
        // Postura de SOMBRA
        l.add(BossAttack.of("dusk_orbs", 40, 80, this::duskOrbs).anim("cast_shadow").range(5, 36).weight(6)
                .when(t -> stance() == SHADOW));
        l.add(BossAttack.of("night_step", 30, 150, this::nightStep).anim("teleport").range(5, 32).weight(4).stationary().freeFacing()
                .when(t -> stance() == SHADOW && !domainActive()).onEnd(() -> setInvisible(false)));
        l.add(BossAttack.of("dusk_chains", 40, 200, this::duskChains).anim("cast_shadow").range(3, 24).weight(3).stationary()
                .freeFacing().when(t -> stance() == SHADOW && !domainActive()).onEnd(chainDirs::clear));
        l.add(BossAttack.of("echoes", 40, 480, this::echoes).anim("summon").weight(2).stationary()
                .when(t -> stance() == SHADOW && !domainActive() && minionCount() < 1 + phase()));
        // v1.3 ASSINATURA — Tempo Crepuscular: o relógio para para todos, menos para ele
        l.add(BossAttack.of("time_stop", 44, 640, this::timeStop).anim("shift").phase(2).range(0, 40).weight(5).stationary()
                .when(t -> !domainActive() && timeHaste <= 0));
        // Fase final
        l.add(BossAttack.of("domain", 70, 900, this::domain).anim("domain").phase(3).weight(10).stationary()
                .when(t -> !domainActive()));
        return l;
    }

    @Override
    protected int pauseBetweenAttacks() {
        int p = super.pauseBetweenAttacks();
        // Tempo Crepuscular: todos lentos, ele não — ataca no dobro do ritmo
        return timeHaste > 0 ? p / 2 : p;
    }

    /** Ticks restantes em que o Arauto age "fora do tempo" (Tempo Crepuscular). */
    private int timeHaste;
    /** Já voltou no tempo nesta luta (só uma vez; fica salvo). */
    private boolean rewound;
    /** Vida registrada a cada segundo (últimos 12 s) para o Rebobinar. */
    private final float[] healthHistory = new float[12];
    private int historyIndex = -1;

    /**
     * Tempo Crepuscular (assinatura do Arauto): ele ergue a mão, um relógio de luz e sombra gira e acelera em volta;
     * no estalo, TODOS a até 48 blocos ficam lentos (andar, golpes, pulo — até a queda fica lenta) e a tela fica sépia,
     * enquanto ele age no dobro do ritmo.
     */
    private void timeStop(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = position().add(0, 1.6, 0);
        if (t == 0) {
            announce("time_stop", Announce.STYLE_ULTIMATE);
            hint("time_stop");
            for (LivingEntity e : enemiesNear(position(), 48)) {
                if (e instanceof ServerPlayer sp) BossKit.vignette(sp, 0xC9A96E, 0.7F, 26);
            }
        }
        if (t < 24) {
            // Antecipação: as "horas" do relógio em volta e o ponteiro girando cada vez mais rápido
            double r = 3.2;
            for (int h = 0; h < 12; h += 2) {
                double a = h * Math.PI / 6;
                sl.sendParticles(BbFx.dust(h % 4 == 0 ? BbFx.LIGHT : BbFx.SHADOW, 1.4F), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
            double hand = t * t * 0.02;
            for (int k = 1; k <= 4; k++) {
                sl.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(hand) * k * 0.7, c.y, c.z + Math.sin(hand) * k * 0.7, 1, 0, 0, 0, 0);
            }
            if (t % Math.max(2, 8 - t / 4) == 0) {
                BbFx.sound(sl, c, SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.6F + t * 0.03F);
            }
            return;
        }
        if (t != 24) return;
        int amp = phase() >= 3 ? 1 : 0;
        int dur = 120 + 20 * phase();
        timeHaste = dur;
        BbFx.sound(sl, c, SoundEvents.BELL_RESONATE, 3.0F, 0.5F);
        BbFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 2.5F, 0.6F);
        BossKit.flashNear(sl, c, 48, 0xE8D8A8, 0.55F, 16);
        BossKit.shake(sl, c, 40, 1.0F, 18);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        BbFx.ring(sl, BbFx.dust(BbFx.LIGHT, 2.2F), position().add(0, 0.3, 0), 6.0, 40);
        BbFx.ring(sl, BbFx.dust(BbFx.SHADOW, 2.2F), position().add(0, 0.3, 0), 9.0, 52);
        for (LivingEntity e : enemiesNear(position(), 48)) BossCurses.apply(e, BossCurses.TIME_SLOW, dur, amp);
    }

    /**
     * Rebobinar (assinatura, uma vez por luta): na primeira vez que cai abaixo de 30% de vida na fase 2+, o Arauto volta
     * no tempo — a vida retorna ao valor de ~10 s atrás (no máx. +35% da vida máxima), com tudo "andando para trás".
     */
    private void tickRewind(ServerLevel sl) {
        if (tickCount % 20 == 0) {
            if (historyIndex < 0) java.util.Arrays.fill(healthHistory, getHealth());
            historyIndex = (historyIndex + 1) % healthHistory.length;
            healthHistory[historyIndex] = getHealth();
        }
        if (rewound || phase() < 2 || getHealth() > getMaxHealth() * 0.30F || historyIndex < 0) return;
        rewound = true;
        // ~10 s atrás = 2 posições à frente no anel de 12
        float past = healthHistory[(historyIndex + 2) % healthHistory.length];
        float to = Math.min(Math.max(past, getHealth()), getHealth() + getMaxHealth() * 0.35F);
        cancelAttack();
        triggerAnim("action", "shift");
        announce("rewind", Announce.STYLE_ULTIMATE);
        hint("rewind");
        Vec3 c = position().add(0, 1.5, 0);
        BossKit.flashNear(sl, c, 48, 0xB070FF, 0.6F, 20);
        BossKit.shake(sl, c, 40, 1.2F, 24);
        BbFx.sound(sl, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 2.5F, 0.6F);
        BbFx.sound(sl, c, SoundEvents.PORTAL_TRIGGER, 1.2F, 2.0F);
        // As partículas convergem (o "filme voltando") por 1 s enquanto a vida sobe
        float from = getHealth();
        for (int k = 1; k <= 10; k++) {
            int step = k;
            BossKit.schedule(sl, k * 2, () -> {
                if (!isAlive()) return;
                setHealth(Mth.lerp(step / 10F, from, to));
                for (int i = 0; i < 8; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * 3;
                    Vec3 p = c.add(Math.cos(a) * d, (random.nextDouble() - 0.5) * 3, Math.sin(a) * d);
                    Vec3 v = c.subtract(p).scale(0.12);
                    sl.sendParticles(step % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
                }
                BbFx.sound(sl, c, SoundEvents.NOTE_BLOCK_HAT.value(), 1.5F, 1.8F - step * 0.08F);
            });
        }
    }

    /** Foice crepuscular: o Arauto MERGULHA até o chão para cortar (cone marcado 9 ticks); efeito conforme a postura. */
    private void sweep(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            meleeCone(120F, 5.4F, 9, e -> {
                if (!e.hurt(damageSources().mobAttack(this), attackPower())) return;
                if (stance() == LIGHT) {
                    e.igniteForSeconds(3F);
                    e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), this);
                } else {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
                }
            });
        }
        holdFacing();
        Vec3 g = groundBelow();
        if (t < 14) hoverToward(new Vec3(getX(), g.y + 0.3, getZ()), 1.6);
        if (t != 9) return;
        Vec3 c = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.0)).add(0, 1.2, 0);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 4, 1.0, 0.3, 1.0, 0);
        sl.sendParticles(stanceDust(1.5F), c.x, c.y, c.z, 20, 1.2, 0.4, 1.2, 0);
        BbFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3F, 0.5F);
    }

    /** Nova Crepuscular: dois anéis no chão, um de cada elemento (pule os dois). A área é marcada antes de cada um. */
    private void nova(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        boolean light = stance() == LIGHT;
        if (t == 0) {
            novaCenter = groundBelow();
            warn(Telegraph.Shape.ring(novaCenter, 1.0F, 19.7F).warmup(16).color(light ? Telegraph.HOLY : Telegraph.VOID)
                    .noFlash().vertical(0.5, 1.0));
            hint("nova");
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
        }
        if (t == 16) {
            warn(Telegraph.Shape.ring(novaCenter, 1.0F, 19.7F).warmup(14).color(light ? Telegraph.VOID : Telegraph.HOLY)
                    .noFlash().vertical(0.5, 1.0));
        }
        if (t < 16 && t % 2 == 0) {
            sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.6F), getX(), getY() + 1.5, getZ(), 6, 1.2, 1.0, 1.2, 0);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.6F), getX(), getY() + 1.5, getZ(), 6, 1.2, 1.0, 1.2, 0);
        }
        novaRing(sl, novaCenter, t - 16, light, novaHitA);
        novaRing(sl, novaCenter, t - 30, !light, novaHitB);
    }

    private void novaRing(ServerLevel sl, Vec3 ground, int age, boolean light, Set<UUID> hit) {
        if (age < 0 || age > 22) return;
        if (age == 0) {
            hit.clear();
            BbFx.sound(sl, ground, light ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WARDEN_SONIC_BOOM, 1.5F, light ? 1.4F : 1.0F);
        }
        double r = 1.0 + age * 0.85;
        int pts = Math.min(72, (int) (r * 6));
        BbFx.ring(sl, BbFx.dust(light ? BbFx.LIGHT : BbFx.SHADOW, 1.8F), ground.add(0, 0.15, 0), r, pts);
        if (age % 2 == 0) BbFx.ring(sl, light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, ground.add(0, 0.3, 0), r, pts / 4);
        for (LivingEntity e : enemiesNear(ground, r + 1.5)) {
            double d = Math.sqrt(e.distanceToSqr(ground.x, e.getY(), ground.z));
            if (Math.abs(d - r) > 1.1 || e.getY() - ground.y > 1.1 || !e.onGround() || !hit.add(e.getUUID())) continue;
            if (e.hurt(spellSource(null), attackPower() * 0.7F)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(light ? CoreRegistries.EXPOSED : CoreRegistries.CORRUPTED, 80, 0), this);
            }
        }
    }

    // ---------------- Sentença do Crepúsculo (magia programável) ----------------

    /**
     * Sentença do Crepúsculo: as duas esferas se fundem na mão e o Arauto dispara uma MAGIA PROGRAMÁVEL mista —
     * Projétil de LUZ → AO ACERTAR → explosão de SOMBRA no ponto atingido (SpellCaster.castFrom com link_hit).
     * A linha do disparo e a área da explosão no fim dela ficam marcadas; a direção é travada.
     * Sem os glifos, um orbe próprio faz o mesmo em dois estágios.
     */
    private void verdict(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            verdictFrom = castHand();
            verdictDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(verdictFrom).normalize();
            verdictSpell = verdictRecipe();
            Vec3 far = verdictFrom.add(verdictDir.scale(32));
            HitResult hit = level().clip(new ClipContext(verdictFrom, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
            double gy = target.getY();
            warn(Telegraph.Shape.line(groundAhead(verdictFrom, verdictDir, 1.0, gy), new Vec3(end.x, gy, end.z), 1.6F)
                    .warmup(VERDICT_WARN).color(TWILIGHT_RGB));
            // Onde o projétil para (se ninguém entrar na frente) o 2º estágio de sombra explode: área marcada até lá.
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 g = groundAt(end.x, end.y, end.z, 2, 4);
                double speed = verdictSpell != null ? GLYPH_BOLT_SPEED : VERDICT_ORB_SPEED;
                int flight = Mth.ceil(verdictFrom.distanceTo(end) / speed);
                warn(Telegraph.Shape.circle(g != null ? g : end, VERDICT_BLAST).warmup(VERDICT_WARN + flight)
                        .color(Telegraph.VOID).noFlash());
            }
            Announce.send(this, Component.translatable("spell.isekai_mahou.twilight_verdict"), TWILIGHT_RGB, Announce.STYLE_SPELL);
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 1.6F, 1.4F);
        }
        holdFacing();
        if (t < VERDICT_WARN) {
            double a = t * 0.7;
            double r = 1.4 * (1.0 - t / (double) VERDICT_WARN) + 0.2;
            Vec3 h = verdictFrom;
            sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.3F), h.x + Math.cos(a) * r, h.y, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.3F), h.x - Math.cos(a) * r, h.y, h.z - Math.sin(a) * r, 1, 0, 0, 0, 0);
            return;
        }
        if (t != VERDICT_WARN) return;
        SpellRecipe r = verdictSpell;
        boolean glyph = r != null && BbSpells.castAt(this, Element.TWILIGHT, glyphPower(2.4F), verdictFrom, verdictDir, r);
        if (!glyph) {
            BossBolt.shoot(this, BbRegistries.TWILIGHT_ORB.get(), verdictFrom, verdictDir, VERDICT_ORB_SPEED, attackPower() * 0.6F,
                    null, 0F, BossBolt.KIND_VERDICT);
        }
        sl.sendParticles(ParticleTypes.FLASH, verdictFrom.x, verdictFrom.y, verdictFrom.z, 1, 0, 0, 0, 0);
        BbFx.sound(sl, verdictFrom, SoundEvents.WITHER_SHOOT, 1.0F, 1.3F);
        BbFx.sound(sl, verdictFrom, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.7F);
    }

    /**
     * Receita da Sentença (a primeira que validar): estágio 0 = Projétil de luz; link_hit; estágio 1 = forma de área
     * de sombra no ponto atingido. Formas de outros módulos só servem como estágio se já forem "stageable".
     */
    @Nullable
    private static SpellRecipe verdictRecipe() {
        return BbSpells.firstRecipe(
                new String[]{"projectile", "holy_ray", "link_hit", "nova", "curse"},
                new String[]{"projectile", "holy_ray", "link_hit", "zone", "curse"},
                new String[]{"projectile", "holy_ray", "link_hit", "nova", "life_drain"},
                new String[]{"projectile", "holy_ray", "link_hit", "projectile", "split", "split", "curse"},
                new String[]{"projectile", "holy_ray", "link_hit", "projectile", "split", "split", "life_drain"});
    }

    /** Reserva sem glifos: o orbe da Sentença estourou — o 2º estágio (sombra) nasce ali, marcado por 12 ticks. */
    @Override
    public void onBoltImpact(BossBolt bolt, Vec3 at) {
        if (bolt.kind() != BossBolt.KIND_VERDICT || !(level() instanceof ServerLevel sl)) return;
        Vec3 g = groundAt(at.x, at.y, at.z, 1.5, 4);
        Vec3 c = g != null ? g : at;
        sl.sendParticles(BbFx.dust(BbFx.SHADOW_DEEP, 1.8F), c.x, c.y + 0.3, c.z, 20, 1.2, 0.1, 1.2, 0);
        strike(Telegraph.Shape.circle(c, VERDICT_BLAST).warmup(12).color(Telegraph.VOID), foes -> {
            sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.8, c.z, 50, 1.6, 0.6, 1.6, 0.12);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 2.2F), c.x, c.y + 1.0, c.z, 30, 1.8, 1.0, 1.8, 0);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
            BbFx.sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.2F);
            for (LivingEntity e : foes) {
                if (e.hurt(spellSource(null), attackPower() * 0.6F)) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), this);
                }
            }
        });
    }

    // ---------------- Ruptura Crepuscular ----------------

    /**
     * Ruptura Crepuscular: o chão vira um tabuleiro de casas de LUZ (douradas) e de SOMBRA (violetas). A cor da postura
     * atual explode primeiro (casas enchem mais rápido), depois a outra; na fase 3 a primeira cor explode de novo.
     * Ficar na cor errada dói — é só pular de casa no tempo certo. No fim, o Arauto pousa exausto.
     */
    private void rupture(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int last = phase() >= 3 ? RUPTURE_C : RUPTURE_B;
        if (t == 0) {
            ruptureCenter = new Vec3(Math.floor(target.getX()) + 0.5, target.getY(), Math.floor(target.getZ()) + 0.5);
            ruptureLightFirst = stance() == LIGHT;
            announce("rupture", Announce.STYLE_SPELL);
            hint(ruptureLightFirst ? "rupture_light" : "rupture_shadow");
            ruptureWave(sl, 0, RUPTURE_A);
            ruptureWave(sl, 1, RUPTURE_B);
            BbFx.sound(sl, position(), SoundEvents.END_PORTAL_FRAME_FILL, 2.0F, 0.6F);
        }
        if (t == RUPTURE_A && phase() >= 3) ruptureWave(sl, 0, RUPTURE_C - RUPTURE_A);
        if (t == RUPTURE_A || t == RUPTURE_B || (phase() >= 3 && t == RUPTURE_C)) {
            boolean light = (t == RUPTURE_B) != ruptureLightFirst;
            BbFx.sound(sl, ruptureCenter, light ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WARDEN_SONIC_BOOM, 2.0F, light ? 1.3F : 0.8F);
            BbFx.sound(sl, ruptureCenter, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, light ? 1.4F : 0.7F);
            BossKit.shake(sl, ruptureCenter, 30, 0.9F, 10);
        }
        if (isGrounded()) {
            Vec3 g = groundBelow();
            hoverToward(new Vec3(getX(), g.y + 0.05, getZ()), 1.4);
        } else {
            hoverToward(ruptureCenter.add(0, 5.0, 0), 1.0);
        }
        if (t < RUPTURE_A && t % 3 == 0) {
            sl.sendParticles(stanceDust(1.6F), getX(), getY() + 1.0, getZ(), 6, 0.6, 0.2, 0.6, 0);
        }
        if (t == last + 4) {
            land(50);
            endAttack();
        }
    }

    /** Uma cor do tabuleiro: cada casa vira um quadrado telegrafado que explode depois de {@code warmup} ticks. */
    private void ruptureWave(ServerLevel sl, int parity, int warmup) {
        boolean light = (parity == 0) == ruptureLightFirst;
        int rgb = light ? Telegraph.HOLY : Telegraph.VOID;
        int half = TILES / 2;
        for (int i = -half; i <= half; i++) {
            for (int j = -half; j <= half; j++) {
                if (Math.floorMod(i + j, 2) != parity) continue;
                double x = ruptureCenter.x + i * TILE;
                double z = ruptureCenter.z + j * TILE;
                Vec3 g = groundAt(x, ruptureCenter.y, z, 3, 5);
                if (g == null) continue;
                Vec3 from = new Vec3(x - 1.35, g.y, z);
                Vec3 to = new Vec3(x + 1.35, g.y, z);
                strike(Telegraph.Shape.line(from, to, 2.7F).warmup(warmup).color(rgb).noRunes(), foes -> ruptureHit(sl, g, light, foes));
            }
        }
    }

    private void ruptureHit(ServerLevel sl, Vec3 tile, boolean light, List<LivingEntity> foes) {
        if (light) {
            sl.sendParticles(ParticleTypes.END_ROD, tile.x, tile.y + 1.2, tile.z, 4, 0.5, 1.0, 0.5, 0.02);
        } else {
            sl.sendParticles(ParticleTypes.SQUID_INK, tile.x, tile.y + 0.8, tile.z, 4, 0.5, 0.6, 0.5, 0.02);
        }
        sl.sendParticles(BbFx.dust(light ? BbFx.LIGHT_WHITE : BbFx.SHADOW, 1.8F), tile.x, tile.y + 0.4, tile.z, 3, 0.6, 0.2, 0.6, 0);
        for (LivingEntity e : foes) {
            if (!e.hurt(spellSource(null), attackPower() * 0.6F)) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(light ? CoreRegistries.EXPOSED : CoreRegistries.CORRUPTED, 80, 0), this);
        }
    }

    // ---------------- Postura de LUZ ----------------

    /** Lanças da aurora: direção travada e linha marcada 8 ticks antes de cada arremesso; a 1ª pode ser o glifo Raio Sagrado. */
    private void dawnLances(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) Arrays.fill(lanceAim, null);
        if (t < 10 && t % 2 == 0) {
            Vec3 h = castHand();
            sl.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        for (int i = 0; i < LANCE_SHOTS.length; i++) {
            int shot = LANCE_SHOTS[i];
            if (i == LANCE_SHOTS.length - 1 && phase() < 2) break;
            if (t == shot - LANCE_WARN) aimLance(i, target);
            if (t == shot) throwLance(sl, i);
        }
    }

    private void aimLance(int i, LivingEntity target) {
        Vec3 from = castHand();
        double speed = 1.8;
        double flight = from.distanceTo(target.position()) / speed + LANCE_WARN;
        Vec3 lead = target.position().add(target.getDeltaMovement().multiply(1, 0, 1).scale(flight * 0.6))
                .add(0, target.getBbHeight() * 0.55, 0);
        Vec3 aim = lead.subtract(from).normalize();
        int n = phase() >= 3 ? 3 : 1;
        lanceFrom[i] = from;
        lanceAim[i] = new Vec3[n];
        double len = Math.min(40, from.distanceTo(lead) + 8);
        for (int k = 0; k < n; k++) {
            float off = n == 1 ? 0 : (k - 1) * 10F;
            Vec3 dir = aim.yRot(off * Mth.DEG_TO_RAD);
            lanceAim[i][k] = dir;
            warn(Telegraph.Shape.line(groundAhead(from, dir, 1.5, target.getY()), groundAhead(from, dir, len, target.getY()), 1.0F)
                    .warmup(LANCE_WARN).color(Telegraph.HOLY).noRunes());
        }
    }

    private void throwLance(ServerLevel sl, int i) {
        Vec3 from = lanceFrom[i];
        Vec3[] dirs = lanceAim[i];
        if (from == null || dirs == null) return;
        for (int k = 0; k < dirs.length; k++) {
            boolean glyph = i == 0 && k == 0 && BbSpells.castAt(this, Element.LIGHT, glyphPower(2.6F), from, dirs[k], "projectile", "holy_ray");
            if (!glyph) BossBolt.shoot(this, BbRegistries.CELESTIAL_LANCE.get(), from, dirs[k], 1.8, attackPower() * 0.55F, null, 0F);
        }
        BbFx.sound(sl, from, SoundEvents.TRIDENT_THROW.value(), 1.2F, 1.3F);
        lanceAim[i] = null;
    }

    /** Feixes gêmeos: posições iniciais marcadas, miolo seguro; depois o Arauto pousa exausto (fora do domínio). */
    private void twinBeams(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int count = phase() >= 2 ? 4 : 3;
        if (t == 0) {
            Vec3 toMe = position().subtract(target.position()).multiply(1, 0, 1);
            Vec3 off = toMe.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : toMe.normalize();
            beamCenter = target.position().add(off.scale(4.0)).add(0, 0.1, 0);
            if (!level().noCollision(this, getBoundingBox().move(beamCenter.subtract(position())))) beamCenter = position();
            orbitDir = random.nextBoolean() ? 1 : -1;
            hint("beams");
        }
        hoverToward(beamCenter, 1.4);
        if (t < 20) {
            if (t == 6) warnBeams(beamCenter.add(0, -0.1, 0), count, 18F, 14, 90, Telegraph.HOLY);
            if (t % 4 == 0) BbFx.ring(sl, BbFx.dust(BbFx.LIGHT, 1.4F), position().add(0, 1.1, 0), 2.0, 18);
            return;
        }
        if (t == 20) {
            startBeams(count, 2.6F * orbitDir, 18F);
            BbFx.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
        }
        if (t % 20 == 0) BbFx.sound(sl, position(), SoundEvents.BEACON_AMBIENT, 1.6F, 1.1F);
        tickBeamDamage(attackPower() * 0.4F, e -> {
            e.igniteForSeconds(2F);
            e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), this);
        });
        if (t == 109) {
            stopBeams();
            land(40);
        }
    }

    /** Queda do Sol: círculos sob os inimigos (e alguns perto); os pilares caem quando enchem. */
    private void sunfall(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 28) BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 1.0F);
        if (t != 6) return;
        List<Vec3> marks = new ArrayList<>();
        List<LivingEntity> en = enemiesNear(position(), 32);
        for (LivingEntity e : en) if (marks.size() < 6) marks.add(e.position());
        for (int i = 0; i < phase() && !en.isEmpty(); i++) {
            LivingEntity e = en.get(random.nextInt(en.size()));
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 g = groundAt(e.getX() + Math.cos(a) * 3, e.getY(), e.getZ() + Math.sin(a) * 3, 3, 5);
            if (g != null) marks.add(g);
        }
        for (Vec3 m : marks) {
            strike(Telegraph.Shape.circle(m, 2.4F).warmup(30).color(Telegraph.HOLY), foes -> {
                BbFx.column(sl, ParticleTypes.END_ROD, m, 18, 30, 0.15);
                BbFx.column(sl, BbFx.dust(BbFx.LIGHT_WHITE, 2.4F), m, 10, 16, 0.4);
                sl.sendParticles(ParticleTypes.FLASH, m.x, m.y + 1, m.z, 1, 0, 0, 0, 0);
                BbFx.sound(sl, m, SoundEvents.TRIDENT_THUNDER.value(), 1.0F, 1.1F);
                for (LivingEntity e : foes) {
                    if (e.hurt(spellSource(null), attackPower() * 0.85F)) {
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                        e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), this);
                    }
                }
            });
        }
    }

    // ---------------- Postura de SOMBRA ----------------

    /** Orbes do ocaso: o primeiro pode ser o glifo Maldição. Teleguiado fraco; podem ser rebatidos! */
    private void duskOrbs(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t < 10 && t % 2 == 0) {
            Vec3 h = castHand();
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.5F), h.x, h.y, h.z, 4, 0.3, 0.3, 0.3, 0);
        }
        if (t == 10 && BbSpells.cast(this, Element.SHADOW, glyphPower(2.6F), "projectile", "curse")) return;
        boolean shot = t == 10 || t == 18 || t == 26 || (phase() >= 2 && t == 34);
        if (!shot) return;
        Vec3 from = castHand();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
        int n = phase() >= 3 ? 3 : 1;
        for (int i = 0; i < n; i++) {
            float off = n == 1 ? 0 : (i - 1) * 15F;
            BossBolt.shoot(this, BbRegistries.TWILIGHT_ORB.get(), from, aim.yRot(off * Mth.DEG_TO_RAD), 0.8,
                    attackPower() * 0.55F, target, 0.05F);
        }
        BbFx.sound(sl, from, SoundEvents.WITHER_SHOOT, 0.9F, 0.8F);
    }

    /** Passo Noturno: some e reaparece atrás do alvo; o corte sai num cone marcado 8 ticks depois. */
    private void nightStep(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.04);
            BbFx.sound(sl, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
        }
        if (t == 5) setInvisible(true);
        if (t == 12) {
            Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-2.8);
            Vec3 dest = target.position().add(back);
            if (!randomTeleport(dest.x, target.getY() + 0.3, dest.z, false)) blinkNear(target, 3.0);
            setInvisible(false);
            lockFacing(target);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.06);
            sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.4F), getX(), getY() + 1.5, getZ(), 20, 0.6, 1.2, 0.6, 0);
            BbFx.sound(sl, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.8F);
            triggerAnim("action", "sweep");
            meleeCone(120F, 5.0F, 8, e -> {
                if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.9F)) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                }
            });
        }
        if (t > 12) holdFacing();
        if (t == 20) BbFx.sound(sl, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
    }

    /** Correntes do Crepúsculo: linha marcada no chão e só então a corrente sai pela linha; se acertar, prende 1,5 s e puxa. */
    private void duskChains(LivingEntity target, int t) {
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
                warn(Telegraph.Shape.line(groundAhead(hand, dir, 1.0, e.getY()), groundAhead(hand, dir, 27, e.getY()), 1.8F)
                        .warmup(CHAIN_WARN).color(Telegraph.VOID));
            }
            hint("chains");
            BbFx.sound(sl, position(), SoundEvents.CHAIN_PLACE, 1.5F, 0.6F);
        }
        holdFacing();
        if (t != CHAIN_WARN) return;
        Vec3 hand = castHand();
        for (Vec3 dir : chainDirs) {
            BossBolt.shoot(this, BbRegistries.ABYSSAL_ORB.get(), hand, dir, 1.15, attackPower() * 0.35F, null, 0F, BossBolt.KIND_CHAIN);
        }
        BbFx.sound(sl, hand, SoundEvents.CHAIN_BREAK, 1.5F, 0.7F);
    }

    /** Ecos: sombras invocadas por id (vexes se não existirem), em pontos marcados no chão. */
    private void echoes(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) echoSpots = summonSpots(2, 3.5, 20);
        if (t < 20 && t % 4 == 0) BbFx.ring(sl, BbFx.dust(BbFx.TWILIGHT, 1.8F), groundBelow().add(0, 0.1, 0), 3.5, 24);
        if (t != 20) return;
        for (int i = 0; i < echoSpots.size(); i++) {
            Vec3 at = echoSpots.get(i).add(0, 0.2, 0);
            String id = i == 0 ? "creeping_shade" : "watcher_eye";
            if (summon(id, EntityType.VEX, at, target) != null) {
                sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 20, 0.4, 0.6, 0.4, 0.05);
            }
        }
        BbFx.sound(sl, position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.6F);
    }

    // ---------------- Expansão de Domínio ----------------

    /**
     * EXPANSÃO DE DOMÍNIO: anúncio + cutscene; o domo é o do glifo domain_expansion (se existir; com Leis que só
     * prendem e revelam) ou o próprio. Durante ~12 s a arena pulsa luz e sombra: a cada pulso surgem ZONAS SEGURAS
     * verdes num anel, que GIRAM de um pulso para o outro — quem estiver fora delas quando o círculo enche é atingido.
     */
    private void domain(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Component name = title("domain_name");
            Announce.send(this, name, TWILIGHT_RGB, Announce.STYLE_ULTIMATE);
            Cutscenes.playNear(this, 48, "domain", name, title("domain_subtitle"), TWILIGHT_RGB, 60, null);
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.5F);
        }
        // Desce até perto do chão: o domo nasce centrado no Arauto e precisa cobrir a arena.
        if (t < 30) hoverToward(groundBelow().add(0, 0.4, 0), 1.2);
        if (t < 30 && t % 2 == 0) {
            double a = t * 0.45;
            for (int i = 0; i < 2; i++) {
                double aa = a + i * Math.PI;
                double r = 6.0 * (1 - t / 30.0) + 0.8;
                sl.sendParticles(BbFx.dust(i == 0 ? BbFx.LIGHT : BbFx.SHADOW, 2.0F), getX() + Math.cos(aa) * r, getY() + 1.8,
                        getZ() + Math.sin(aa) * r, 2, 0.05, 0.3, 0.05, 0);
            }
        }
        if (t != 30) return;
        SpellRecipe r = domainRecipe();
        boolean glyph = r != null && BbSpells.cast(this, Element.TWILIGHT, glyphPower(2.0F), r);
        entityData.set(DATA_DOMAIN_GLYPH, glyph);
        entityData.set(DATA_DOMAIN_RADIUS, glyph ? GLYPH_DOMAIN_RADIUS : DOMAIN_RADIUS);
        entityData.set(DATA_DOMAIN_CENTER, BlockPos.containing(groundBelow().add(0, 0.5, 0)));
        entityData.set(DATA_DOMAIN, DOMAIN_TICKS);
        domainPulse = 0;
        domainSpin = random.nextFloat() * 360F;
        hint("domain");
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 2, 0, 0, 0, 0);
        sl.sendParticles(BbFx.dust(BbFx.LIGHT, 2.5F), getX(), getY() + 2, getZ(), 60, 4, 3, 4, 0);
        sl.sendParticles(BbFx.dust(BbFx.SHADOW, 2.5F), getX(), getY() + 2, getZ(), 60, 4, 3, 4, 0);
        BbFx.sound(sl, position(), SoundEvents.END_PORTAL_SPAWN, 2.0F, 0.8F);
        BbFx.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
        BossKit.shake(sl, position(), 40, 1.6F, 30);
    }

    /**
     * Receita do domo (só visual e barreira): Expansão de Domínio → Expandir → Silencioso → Revelar → Sem Fuga.
     * O dano do domínio é dos pulsos com zonas seguras (justos); por isso nada de Acerto Garantido aqui.
     */
    @Nullable
    private SpellRecipe domainRecipe() {
        return BbSpells.recipe("domain_expansion", "expand", "silent", "reveal", "law_no_escape");
    }

    // =====================================================================================
    //  Passivo: posturas e domínio
    // =====================================================================================
    @Override
    protected void tickBoss(ServerLevel sl) {
        stanceTimer++;
        if (timeHaste > 0) {
            timeHaste--;
            // Fora do tempo: rastro de "fantasmas" de luz e sombra quando se move
            if (tickCount % 3 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002) {
                sl.sendParticles(stanceDust(1.6F), getX(), getY() + 1.4, getZ(), 6, 0.4, 0.9, 0.4, 0);
            }
        }
        tickRewind(sl);
        int dur = switch (phase()) {
            case 1 -> 420;
            case 2 -> 320;
            default -> 260;
        };
        if (stanceTimer >= dur && currentAttack() == null && !isGrounded()) {
            switchStance(sl);
            // Trocar de postura cansa: depois do giro ("shift"), um instante no chão (fora do domínio).
            if (!domainActive()) {
                pendingLand = 30;
                pendingLandAt = tickCount + 24;
            }
        }

        int dom = entityData.get(DATA_DOMAIN);
        if (dom > 0) {
            tickDomain(sl, dom);
            entityData.set(DATA_DOMAIN, dom - 1);
            if (dom - 1 == 0) {
                entityData.set(DATA_DOMAIN_GLYPH, false);
                BbFx.sound(sl, domainCenter(), SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
                hint("domain_end");
                pendingLand = 60;
                pendingLandAt = tickCount;
            }
        }
        if (pendingLand > 0 && tickCount >= pendingLandAt && currentAttack() == null && !isGrounded()) {
            land(pendingLand);
            pendingLand = 0;
        }
    }

    /** O domo do glifo (entidade domain_dome do módulo domain, buscada por id) ainda está de pé no centro? */
    private boolean glyphDomeStanding(ServerLevel sl) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("domain_dome")).orElse(null);
        if (type == null) return false;
        Vec3 c = domainCenter();
        AABB box = new AABB(c, c).inflate(4.0);
        return !sl.getEntities(type, box, Entity::isAlive).isEmpty();
    }

    private void switchStance(ServerLevel sl) {
        stanceTimer = 0;
        int next = stance() == LIGHT ? SHADOW : LIGHT;
        entityData.set(DATA_STANCE, next);
        triggerAnim("action", "shift");
        if (bossBar() != null) bossBar().setColor(barColor());
        announce(next == LIGHT ? "stance_light" : "stance_shadow", Announce.STYLE_BOSS);
        sl.sendParticles(next == LIGHT ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, getX(), getY() + 2, getZ(), 50, 1.0, 1.5, 1.0, 0.15);
        sl.sendParticles(stanceDust(2.0F), getX(), getY() + 2, getZ(), 40, 1.2, 1.6, 1.2, 0);
        BbFx.sound(sl, position(), next == LIGHT ? SoundEvents.BEACON_ACTIVATE : SoundEvents.WARDEN_SONIC_CHARGE, 1.8F, 0.9F);
    }

    /** Um tick do domínio ({@code left} = ticks restantes antes deste): barreira, pulsos com zonas seguras e visual. */
    private void tickDomain(ServerLevel sl, int left) {
        int age = DOMAIN_TICKS - left;
        Vec3 c = domainCenter();
        float r = domainRadius();
        // O domo do glifo pode cair antes (choque de domínios, estilhaçado): a barreira própria assume.
        if (domainByGlyph() && age % 20 == 10 && !glyphDomeStanding(sl)) entityData.set(DATA_DOMAIN_GLYPH, false);
        if (!domainByGlyph()) {
            if (left % 5 == 0) BbFx.ring(sl, BbFx.dust(left % 10 == 0 ? BbFx.LIGHT : BbFx.SHADOW, 2.0F), c.add(0, 0.2, 0), r, 48);
            pullBack(sl, c, r);
        }
        if (age == 0) {
            scheduleDomainPulse(sl, FIRST_PULSE);
        } else if (age >= FIRST_PULSE && (age - FIRST_PULSE) % PULSE == 0 && left > PULSE) {
            scheduleDomainPulse(sl, PULSE);
        }
    }

    /** Sem Fuga (domínio próprio): a barreira devolve para dentro quem encosta na borda. */
    private void pullBack(ServerLevel sl, Vec3 c, float r) {
        for (LivingEntity e : enemiesNear(c, r + 8)) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= r - 0.8 || d < 0.01) continue;
            e.setDeltaMovement(-dx / d * 0.9, 0.25, -dz / d * 0.9);
            e.hurtMarked = true;
            Vec3 wall = c.add(dx / d * r, e.getY() - c.y + 1, dz / d * r);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, wall.x, wall.y, wall.z, 10, 0.3, 0.6, 0.3, 0.1);
        }
    }

    /**
     * Prepara o próximo pulso: zonas seguras (3, num anel a ~55% do raio) giradas 40° em relação às anteriores e o
     * círculo do domínio inteiro enchendo. No fim do aquecimento, quem estiver fora das zonas leva o pulso.
     */
    private void scheduleDomainPulse(ServerLevel sl, int warmup) {
        Vec3 c = domainCenter();
        float r = domainRadius();
        boolean light = domainPulse++ % 2 == 0;
        domainSpin += 40F;
        List<Telegraph.Shape> zones = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(domainSpin + i * 120);
            Vec3 p = c.add(Math.cos(a) * r * 0.55, 0, Math.sin(a) * r * 0.55);
            Vec3 g = groundAt(p.x, c.y, p.z, 3, 4);
            Telegraph.Shape zone = Telegraph.Shape.safe(g != null ? g : p, ZONE_RADIUS).warmup(warmup);
            warn(zone);
            zones.add(zone);
        }
        strike(Telegraph.Shape.circle(c, r).warmup(warmup).color(light ? Telegraph.HOLY : Telegraph.VOID).noRunes().vertical(4.0, 8.0),
                foes -> domainBurst(sl, c, light, foes, zones));
    }

    private void domainBurst(ServerLevel sl, Vec3 c, boolean light, List<LivingEntity> foes, List<Telegraph.Shape> zones) {
        BbFx.sound(sl, c, light ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WARDEN_HEARTBEAT, 2.5F, light ? 1.2F : 0.8F);
        BbFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, light ? 1.5F : 0.6F);
        BossKit.shake(sl, c, 30, 0.8F, 10);
        for (Telegraph.Shape z : zones) {
            Vec3 p = z.center();
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.5, p.z, 12, 1.0, 0.4, 1.0, 0);
        }
        for (LivingEntity e : foes) {
            boolean safe = false;
            for (Telegraph.Shape z : zones) {
                if (z.contains(e)) {
                    safe = true;
                    break;
                }
            }
            if (safe) continue;
            BbFx.column(sl, light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, e.position(), 3, 8, 0.2);
            if (!e.hurt(spellSource(null), attackPower() * 0.6F)) continue;
            if (light) {
                e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), this);
            } else {
                e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
            }
        }
    }

    private ParticleOptions stanceDust(float size) {
        return BbFx.dust(stance() == LIGHT ? BbFx.LIGHT : BbFx.SHADOW, size);
    }

    // =====================================================================================
    //  Movimento, dano, fases
    // =====================================================================================
    @Override
    protected void moveCombat(LivingEntity target) {
        orbit += (phase() >= 3 ? 0.03 : 0.02) * orbitDir;
        if (random.nextInt(400) == 0) orbitDir = -orbitDir;
        Vec3 want;
        if (domainAnchored()) {
            // Dentro do domínio voa baixo em volta do centro: dá para alcançá-lo entre um pulso e outro.
            Vec3 c = domainCenter();
            want = c.add(Math.cos(orbit) * ANCHOR_ORBIT, 2.2, Math.sin(orbit) * ANCHOR_ORBIT);
        } else {
            want = target.position().add(Math.cos(orbit) * 8, 3.5, Math.sin(orbit) * 8);
            HitResult hit = level().clip(new ClipContext(target.getEyePosition(), want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) want = hit.getLocation().add(target.getEyePosition().subtract(hit.getLocation()).normalize().scale(1.5));
        }
        hoverToward(want, 1.0);
        getLookControl().setLookAt(target, 30F, 30F);
    }

    /** Escondido fora da visão: fora do domínio aparece perto; dentro dele, não sai do centro (só dispara). */
    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (domainAnchored()) return;
        super.onUnreachable(sl, target);
    }

    /** Postura de LUZ resiste à luz e é fraca à sombra; a de SOMBRA, o contrário. Dano físico: -15%. */
    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        if (!source.is(CoreRegistries.SPELL_DAMAGE)) return amount * 0.85F;
        Element el = ElementReactions.lastElement(this, level().getGameTime(), 0);
        if (el == null) return amount;
        boolean light = el.matches(Element.LIGHT);
        boolean dark = el.matches(Element.SHADOW);
        float m = 1.0F;
        if (stance() == LIGHT) {
            if (light) m *= 0.35F;
            if (dark) m *= 1.5F;
        } else {
            if (dark) m *= 0.35F;
            if (light) m *= 1.5F;
        }
        if (m != 1.0F && level() instanceof ServerLevel sl) {
            sl.sendParticles(m < 1 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, getX(), getY() + 2, getZ(), 10, 0.6, 0.8, 0.6, 0.2);
        }
        return amount * m;
    }

    @Override
    protected void onIntroStart(ServerLevel sl) {
        BbFx.sound(sl, position(), SoundEvents.END_PORTAL_SPAWN, 2.0F, 0.6F);
        BbFx.column(sl, ParticleTypes.END_ROD, position().add(0.8, 0, 0), 10, 30, 0.3);
        BbFx.column(sl, ParticleTypes.SQUID_INK, position().add(-0.8, 0, 0), 10, 30, 0.3);
    }

    @Override
    protected void onPhaseStart(ServerLevel sl, int phase) {
        BbFx.sound(sl, position(), SoundEvents.WITHER_AMBIENT, 2.0F, 0.6F);
        BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 0.7F);
    }

    @Override
    protected void transitionFx(ServerLevel sl, int left) {
        hoverToward(position().add(0, 0.04, 0), 0.4);
        if (left % 2 != 0) return;
        double a = left * 0.3;
        for (int i = 0; i < 2; i++) {
            double aa = a + i * Math.PI;
            sl.sendParticles(BbFx.dust(i == 0 ? BbFx.LIGHT : BbFx.SHADOW, 2.0F), getX() + Math.cos(aa) * 2.4, getY() + 0.5 + (50 - left) * 0.06,
                    getZ() + Math.sin(aa) * 2.4, 2, 0.05, 0.05, 0.05, 0);
        }
    }

    @Override
    protected void phaseWaveHit(ServerLevel sl, LivingEntity e) {
        super.phaseWaveHit(sl, e);
        e.hurt(spellSource(null), attackPower() * 0.3F);
    }

    @Override
    protected void onPhaseEnd(ServerLevel sl, int phase) {
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
        if (phase == 2) {
            switchStance(sl);
            startAttack(attack("twilight_nova"));
        } else if (phase >= 3) {
            startAttack(attack("domain"));
        }
    }

    // =====================================================================================
    //  Morte: cutscene final
    // =====================================================================================
    @Override
    protected void onBossDeath(ServerLevel sl) {
        entityData.set(DATA_DOMAIN, 0);
        entityData.set(DATA_DOMAIN_GLYPH, false);
        setInvisible(false);
        Cutscenes.playNear(this, 64, "ascension", title("final_title"), title("final_subtitle"), TWILIGHT_RGB, 140, null);
        announce("last_words", Announce.STYLE_BOSS);
    }

    @Override
    protected int deathDuration() {
        return 140;
    }

    /** O Arauto já tem a cena final "ascension" e diz as últimas palavras nela. */
    @Override
    protected boolean killCam() {
        return false;
    }

    @Override
    protected boolean hasLastWords() {
        return false;
    }

    @Override
    protected void deathFx(ServerLevel sl, int t) {
        setDeltaMovement(0, 0.02, 0);
        if (t % 10 == 0) {
            boolean light = (t / 10) % 2 == 0;
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 b = position().add(Math.cos(a) * 1.5, 1.5, Math.sin(a) * 1.5);
            BbFx.column(sl, light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, b, 24, 40, 0.1);
            BbFx.sound(sl, b, light ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.5F + t / 200F);
        }
        if (t % 2 == 0) {
            sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.6F), getX(), getY() + 2, getZ(), 3, 0.8, 1.4, 0.8, 0);
            sl.sendParticles(BbFx.dust(BbFx.SHADOW, 1.6F), getX(), getY() + 2, getZ(), 3, 0.8, 1.4, 0.8, 0);
        }
        if (t == 2) BbFx.sound(sl, position(), SoundEvents.WITHER_DEATH, 2.0F, 1.3F);
        if (t == 70) BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 3.0F, 0.5F);
        if (t == 120) BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.5F);
    }

    @Override
    protected void onFinalDeath(ServerLevel sl) {
        Vec3 c = position().add(0, 2, 0);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 1.5, 2.0, 1.5, 0.4);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 120, 1.5, 2.0, 1.5, 0.3);
        sl.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 80, 1.0, 1.5, 1.0, 0.3);
        BbFx.sound(sl, c, SoundEvents.END_PORTAL_SPAWN, 3.0F, 1.2F);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) <= 64 * 64)) {
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.twilight_herald.victory").withStyle(s -> s.withColor(0xE0A0FF)));
        }
        summonPrimordialDragon(sl);
    }

    /**
     * v0.9: com a queda do Arauto a Fenda se rasga e o Dragão Primordial (módulo bosses_e) surge sobre a arena.
     * Só por id (sem depender do módulo); a tag "isekai_mahou.rift_birth" faz o dragão tocar a cutscene de surgimento.
     */
    private void summonPrimordialDragon(ServerLevel sl) {
        // Na Câmara de Provação o Arauto é só uma prova: o Dragão nasce da Fenda de verdade (ou do /mahou spawnboss).
        if (trialMode()) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("primordial_dragon")).orElse(null);
        Entity dragon = type != null ? type.create(sl) : null;
        if (dragon == null) return;
        Vec3 h = homePos() != null ? homePos() : position();
        dragon.moveTo(h.x, h.y + 24, h.z, getYRot(), 0F);
        dragon.getPersistentData().putBoolean("isekai_mahou.rift_birth", true);
        BossKit.setArena(dragon, h, 22);
        sl.addFreshEntity(dragon);
    }

    // =====================================================================================
    //  Cliente / sons / dados
    // =====================================================================================
    @Override
    protected void clientFx() {
        drawBeamsClient(BbFx.dust(BbFx.LIGHT, 1.5F), ParticleTypes.END_ROD);
        boolean light = stance() == LIGHT;
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.0 + random.nextDouble() * 1.5;
            level().addParticle(BbFx.dust(light ? BbFx.LIGHT : BbFx.SHADOW, 1.2F), getX() + Math.cos(a) * r, getY() + 1 + random.nextDouble() * 2.5,
                    getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
        if (random.nextInt(5) == 0) {
            level().addParticle(light ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 1.6,
                    getY() + 3.8, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
        }
        if (domainActive() && !domainByGlyph()) {
            Vec3 c = domainCenter();
            int pts = reducedParticles() ? 10 : 26;
            float dr = domainRadius();
            BbFx.clientSphere(level(), BbFx.dust(tickCount % 2 == 0 ? BbFx.LIGHT : BbFx.SHADOW, 1.6F), c, dr, pts, random);
            for (int i = 0; i < pts / 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * dr;
                level().addParticle(ParticleTypes.WITCH, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 0, 0.05, 0);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return stance() == LIGHT ? SoundEvents.AMETHYST_CLUSTER_BREAK : SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Stance", stance());
        tag.putInt("StanceTimer", stanceTimer);
        tag.putInt("Domain", entityData.get(DATA_DOMAIN));
        tag.put("DomainCenter", NbtUtils.writeBlockPos(entityData.get(DATA_DOMAIN_CENTER)));
        tag.putBoolean("DomainGlyph", domainByGlyph());
        tag.putFloat("DomainRadius", domainRadius());
        tag.putInt("DomainPulse", domainPulse);
        tag.putFloat("DomainSpin", domainSpin);
        tag.putBoolean("Rewound", rewound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_STANCE, tag.getInt("Stance") == SHADOW ? SHADOW : LIGHT);
        stanceTimer = tag.getInt("StanceTimer");
        int dom = Mth.clamp(tag.getInt("Domain"), 0, DOMAIN_TICKS);
        BlockPos center = NbtUtils.readBlockPos(tag, "DomainCenter").orElse(BlockPos.ZERO);
        boolean glyph = tag.getBoolean("DomainGlyph");
        // Mundos da v0.6: o domo do glifo ficava num contador à parte ("GlyphDomain" + centro em double).
        if (dom == 0 && tag.getInt("GlyphDomain") > 0) {
            dom = Mth.clamp(tag.getInt("GlyphDomain"), 0, DOMAIN_TICKS);
            center = BlockPos.containing(tag.getDouble("GlyphDomainX"), tag.getDouble("GlyphDomainY"), tag.getDouble("GlyphDomainZ"));
            glyph = true;
        }
        entityData.set(DATA_DOMAIN, dom);
        entityData.set(DATA_DOMAIN_CENTER, center);
        entityData.set(DATA_DOMAIN_GLYPH, glyph);
        float radius = tag.getFloat("DomainRadius");
        entityData.set(DATA_DOMAIN_RADIUS, radius > 0 ? radius : glyph ? GLYPH_DOMAIN_RADIUS : DOMAIN_RADIUS);
        domainPulse = tag.getInt("DomainPulse");
        domainSpin = tag.getFloat("DomainSpin");
        rewound = tag.getBoolean("Rewound");
    }
}
