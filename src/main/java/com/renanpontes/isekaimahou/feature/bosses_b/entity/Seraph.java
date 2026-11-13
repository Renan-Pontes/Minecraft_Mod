package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_b.BbRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Serafim de seis asas (chefe do Santuário Celestial, deixa o Núcleo do Guardião da Luz).
 * Fase 1: lanças de luz (cada uma com a linha marcada), Raio Sagrado (3 feixes telegrafados), Julgamento em área,
 * Julgamento Cruzado (feixes em cruz), rajada das asas, mergulho (cravado no chão se errar) e feixes giratórios.
 * Fase 2 (60%): seis feixes, coro celestial (invoca anjos por id), cruz dupla.
 * Fase 3 (25%): Ascensão — feixes em sentido alternado, Julgamento duplo e Oração (pousa e canaliza; interrompível).
 * Depois de golpes grandes ele POUSA exausto (janela para corpo a corpo, +30% de dano).
 */
public class Seraph extends BbBoss {
    private static final int LANCE_WARN = 8;
    private static final int[] LANCE_SHOTS = {10, 16, 22, 28};
    private static final int[] RAY_SHOTS = {18, 32, 46};
    private static final int RAY_WARN = 14;
    private static final int CROSS_WARN = 26;
    /** Mergulho: velocidade (blocos/tick), duração do voo e raio da onda quando crava no chão. */
    private static final double DIVE_SPEED = 1.35;
    private static final int DIVE_TICKS = 16;
    private static final double CRASH_RADIUS = 2.8;

    private double orbit = 0;
    private int orbitDir = 1;
    private Vec3 beamCenter;
    private final Vec3[][] lanceAim = new Vec3[LANCE_SHOTS.length][];
    private final Vec3[] lanceFrom = new Vec3[LANCE_SHOTS.length];
    private final Vec3[] rayAim = new Vec3[RAY_SHOTS.length];
    private final double[] rayY = new double[RAY_SHOTS.length];
    private Vec3 diveDir;
    private boolean diveCrashed;
    private final Set<UUID> diveHit = new HashSet<>();
    private Vec3 crossCenter = Vec3.ZERO;
    private List<Vec3> summonSpotsCache = List.of();
    private float crossAngle;
    private int healsUsed;
    private float prayerDamage;

    public Seraph(EntityType<? extends Seraph> type, Level level) {
        super(type, level);
        this.xpReward = 600;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 650.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String bossId() {
        return "seraph";
    }

    @Override
    public int bossRgb() {
        return 0xFFD75A;
    }

    @Override
    protected int teleColor() {
        return Telegraph.HOLY;
    }

    @Override
    protected int rewardXp() {
        return 600;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[]{0.6F, 0.25F};
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
    protected List<String> actionAnimations() {
        return List.of("throw", "ray", "beams", "judgment", "gust", "dive", "summon", "pray", "cross");
    }

    @Override
    protected Vec3 castHand() {
        return shoulder(1);
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected List<BossAttack> createAttacks() {
        List<BossAttack> l = new ArrayList<>();
        l.add(BossAttack.of("lances", 34, 50, this::lances).anim("throw").range(3, 40).weight(6).stationary());
        l.add(BossAttack.of("holy_ray", 56, 130, this::holyRay).anim("ray").range(4, 30).weight(4).stationary().freeFacing());
        l.add(BossAttack.of("beams", 130, 380, this::beams).anim("beams").range(0, 18).weight(3).stationary().freeFacing()
                .onEnd(this::stopBeams));
        l.add(BossAttack.of("judgment", 72, 280, this::judgment).anim("judgment").range(0, 34).weight(4).stationary());
        l.add(BossAttack.of("cross_judgment", 62, 320, this::crossJudgment).anim("cross").range(0, 30).weight(4).stationary()
                .freeFacing());
        l.add(BossAttack.of("gust", 24, 90, this::gust).anim("gust").range(0, 5.5).weight(5).stationary());
        l.add(BossAttack.of("dive", 50, 160, this::dive).anim("dive").range(7, 24).weight(3).stationary().freeFacing()
                .onEnd(() -> {
                    diveHit.clear();
                    manualVelocity = false;
                }));
        l.add(BossAttack.of("choir", 40, 600, this::choir).anim("summon").phase(2).weight(2).stationary()
                .when(t -> (entityExists("cherub") || entityExists("angel_sentinel")) && minionCount() < 3));
        l.add(BossAttack.of("prayer", 64, 520, this::prayer).anim("pray").phase(3).weight(8).stationary()
                .when(t -> healsUsed < 3 && getHealth() < getMaxHealth() * 0.3F));
        return l;
    }

    /**
     * Lanças de luz: cada arremesso mira onde o alvo VAI estar, mas a direção é travada 8 ticks antes e marcada no
     * chão — quem sai da linha desvia. O primeiro pode ser o glifo Raio Sagrado (pela mesma linha).
     */
    private void lances(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) Arrays.fill(lanceAim, null);
        if (t < 10 && t % 2 == 0) {
            Vec3 h = shoulder(t % 4 == 0 ? 1 : -1);
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
        Vec3 from = shoulder(i % 2 == 0 ? 1 : -1);
        double speed = lanceSpeed();
        double flight = from.distanceTo(target.position()) / speed + LANCE_WARN;
        Vec3 lead = target.position().add(target.getDeltaMovement().multiply(1, 0, 1).scale(flight * 0.6))
                .add(0, target.getBbHeight() * 0.55, 0);
        Vec3 aim = lead.subtract(from).normalize();
        int n = phase() >= 3 ? 3 : 1;
        lanceFrom[i] = from;
        lanceAim[i] = new Vec3[n];
        double len = Math.min(40, from.distanceTo(lead) + 8);
        for (int k = 0; k < n; k++) {
            float off = n == 1 ? 0 : (k - 1) * 9F;
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
            boolean glyph = i == 0 && k == 0 && BbSpells.castAt(this, Element.LIGHT, glyphPower(2.4F), from, dirs[k],
                    "projectile", "holy_ray", "pierce");
            if (!glyph) {
                BossBolt.shoot(this, BbRegistries.CELESTIAL_LANCE.get(), from, dirs[k], lanceSpeed(), attackPower() * 0.6F, null, 0F);
            }
        }
        BbFx.sound(sl, from, SoundEvents.TRIDENT_THROW.value(), 1.2F, 1.4F);
        lanceAim[i] = null;
    }

    private double lanceSpeed() {
        return phase() >= 2 ? 1.9 : 1.6;
    }

    private Vec3 shoulder(int side) {
        Vec3 right = Vec3.directionFromRotation(0, getYRot() + 90);
        return position().add(right.scale(side * 1.4)).add(0, 3.2, 0);
    }

    /**
     * Raio Sagrado: três feixes, cada um com a linha marcada 14 ticks antes (direção travada). Usa o glifo
     * Feixe→Raio Sagrado→Amplificar pela linha se a forma respeitar a origem; senão, o raio próprio.
     */
    private void holyRay(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            faceTarget(target);
            Arrays.fill(rayAim, null);
        }
        for (int i = 0; i < RAY_SHOTS.length; i++) {
            int shot = RAY_SHOTS[i];
            if (t == shot - RAY_WARN) {
                faceTarget(target);
                Vec3 eye = getEyePosition();
                Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye).normalize();
                rayAim[i] = dir;
                rayY[i] = target.getY();
                Vec3 end = rayEnd(eye, dir);
                warn(Telegraph.Shape.line(groundAhead(eye, dir, 1.0, target.getY()), new Vec3(end.x, target.getY(), end.z), 1.6F)
                        .warmup(RAY_WARN).color(Telegraph.HOLY));
                BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.8F);
            }
            if (t == shot && rayAim[i] != null) fireRay(sl, rayAim[i], rayY[i]);
        }
        // Mira travada: o corpo aponta para a linha do próximo raio.
        for (int i = RAY_SHOTS.length - 1; i >= 0; i--) {
            if (rayAim[i] != null && t <= RAY_SHOTS[i]) {
                Vec3 d = rayAim[i];
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
                setYRot(yaw);
                yHeadRot = yaw;
                yBodyRot = yaw;
            }
        }
        if (t < RAY_SHOTS[0] - RAY_WARN || t % 3 != 0) return;
        Vec3 eye = getEyePosition();
        sl.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 2, 0.3, 0.3, 0.3, 0.02);
    }

    private Vec3 rayEnd(Vec3 eye, Vec3 dir) {
        Vec3 end = eye.add(dir.scale(32));
        HitResult hit = level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private void fireRay(ServerLevel sl, Vec3 dir, double groundY) {
        Vec3 eye = getEyePosition();
        Vec3 end = rayEnd(eye, dir);
        BbFx.sound(sl, eye, SoundEvents.BEACON_ACTIVATE, 1.6F, 1.8F);
        if (BbSpells.castAt(this, Element.LIGHT, glyphPower(2.5F), eye, dir, "beam", "holy_ray", "amplify")) return;
        // Raio próprio: acerta quem está na faixa marcada (mesma área do aviso) e desenha o feixe descendo.
        BbFx.line(sl, BbFx.dust(BbFx.LIGHT, 1.8F), eye, end, 0.5);
        BbFx.line(sl, ParticleTypes.END_ROD, eye, end, 1.4);
        sl.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        Telegraph.Shape band = Telegraph.Shape.line(groundAhead(eye, dir, 1.0, groundY), new Vec3(end.x, groundY, end.z), 1.6F);
        for (LivingEntity e : foesIn(band)) {
            if (e.hurt(spellSource(null), attackPower() * 0.6F)) {
                e.igniteForSeconds(2F);
                e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), this);
            }
        }
    }

    /**
     * Feixes giratórios: o Serafim desce à altura do alvo e gira 4 (fase 2+: 6) feixes horizontais. As posições
     * iniciais são marcadas e o MIOLO é seguro (zona verde): colado nele dá para bater. Depois pousa exausto.
     */
    private void beams(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int count = phase() >= 2 ? 6 : 4;
        if (t == 0) {
            Vec3 toMe = position().subtract(target.position()).multiply(1, 0, 1);
            Vec3 off = toMe.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : toMe.normalize();
            beamCenter = target.position().add(off.scale(4.5)).add(0, 0.1, 0);
            if (!level().noCollision(this, getBoundingBox().move(beamCenter.subtract(position())))) beamCenter = position();
            orbitDir = random.nextBoolean() ? 1 : -1;
            announce("beams", Announce.STYLE_SPELL);
            hint("beams");
        }
        hoverToward(beamCenter, 1.4);
        if (t < 20) {
            if (t == 6) warnBeams(beamCenter.add(0, -0.1, 0), count, 20F, 14, 110, Telegraph.HOLY);
            if (t % 4 == 0) BbFx.ring(sl, BbFx.dust(BbFx.LIGHT, 1.4F), position().add(0, 1.1, 0), 2.2, 20);
            return;
        }
        if (t == 20) {
            float speed = (phase() >= 3 ? 3.0F : 2.3F) * orbitDir;
            startBeams(count, speed, 20F);
            BbFx.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 1.2F);
            BbFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.6F);
        }
        // Fase 3: o sentido inverte no meio (o anel marca o aviso).
        if (phase() >= 3 && t == 63) warn(Telegraph.Shape.ring(groundBelow(), (float) BEAM_SAFE_RADIUS, 20F).warmup(12)
                .color(Telegraph.WARNING).noFlash().noRunes());
        if (phase() >= 3 && t == 75) {
            reverseBeams(-3.0F * orbitDir);
            BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 1.5F, 1.5F);
        }
        if (t % 20 == 0) BbFx.sound(sl, position(), SoundEvents.BEACON_AMBIENT, 1.6F, 1.3F);
        tickBeamDamage(attackPower() * 0.45F, e -> {
            e.igniteForSeconds(2F);
            e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), this);
        });
        if (t == 129) {
            stopBeams();
            land(40);
        }
    }

    /** Julgamento: círculos de luz marcam o chão sob os inimigos e os pilares caem quando o círculo enche. */
    private void judgment(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0 && phase() >= 2) announce("judgment", Announce.STYLE_ULTIMATE);
        if (t == 8) markJudgment(sl, 32);
        if (phase() >= 3 && t == 44) markJudgment(sl, 20);
        if (t == 30) BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 1.2F);
        // ASSINATURA — Olhar do Julgamento: a auréola acende; quem estiver OLHANDO para o Serafim quando ela explodir
        // fica cego e exposto. Desvie o olhar (vire de costas) no momento certo.
        if (t == 34) {
            hint("gaze");
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 2.5F, 1.6F);
            for (LivingEntity e : enemiesNear(position(), 40)) {
                if (e instanceof ServerPlayer sp) BossKit.vignette(sp, 0xFFF2B0, 0.6F, 26);
            }
        }
        if (t > 34 && t < 60 && t % 2 == 0) {
            // Antecipação: a luz junta na auréola, cada vez mais rápida
            Vec3 halo = position().add(0, getBbHeight() + 0.4, 0);
            double r = 3.0 * (1.0 - (t - 34) / 26.0) + 0.3;
            for (int i = 0; i < 4; i++) {
                double a = t * 0.5 + i * Math.PI / 2;
                sl.sendParticles(ParticleTypes.END_ROD, halo.x + Math.cos(a) * r, halo.y, halo.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 8 == 0) BbFx.sound(sl, halo, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.8F + (t - 34) / 30F);
        }
        if (t == 60) judgmentGaze(sl);
    }

    /** Quem tem linha de visão e está virado para o Serafim (≈ ±65°) é ofuscado; quem desviou só vê um brilho. */
    private void judgmentGaze(ServerLevel sl) {
        Vec3 halo = position().add(0, getBbHeight() + 0.4, 0);
        sl.sendParticles(ParticleTypes.FLASH, halo.x, halo.y, halo.z, 2, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(ParticleTypes.END_ROD, halo.x, halo.y, halo.z, 80, 0.4, 0.4, 0.4, 0.6);
        BbFx.sound(sl, halo, SoundEvents.TOTEM_USE, 1.6F, 1.5F);
        BbFx.sound(sl, halo, SoundEvents.TRIDENT_THUNDER.value(), 1.4F, 1.6F);
        for (LivingEntity e : enemiesNear(position(), 40)) {
            Vec3 eye = e.getEyePosition();
            Vec3 to = halo.subtract(eye).normalize();
            boolean looking = e.getViewVector(1F).dot(to) > 0.42 && e.hasLineOfSight(this);
            if (!looking) {
                if (e instanceof ServerPlayer sp) BossKit.flash(sp, 0xFFF6D0, 0.25F, 10);
                continue;
            }
            if (e.hurt(spellSource(null), attackPower() * 0.5F)) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), this);
                e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 120, 0), this);
            }
            if (e instanceof ServerPlayer sp) {
                BossKit.flash(sp, 0xFFFFFF, 0.95F, 40);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.bosses_b.gaze_blinded")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    private void markJudgment(ServerLevel sl, int warmup) {
        List<Vec3> marks = new ArrayList<>();
        List<LivingEntity> en = enemiesNear(position(), 32);
        for (LivingEntity e : en) {
            if (marks.size() >= 6) break;
            marks.add(e.position());
        }
        int extra = phase() + 1;
        for (int i = 0; i < extra && !en.isEmpty(); i++) {
            LivingEntity e = en.get(random.nextInt(en.size()));
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2.5 + random.nextDouble() * 4;
            Vec3 g = groundAt(e.getX() + Math.cos(a) * r, e.getY(), e.getZ() + Math.sin(a) * r, 3, 5);
            if (g != null) marks.add(g);
        }
        for (Vec3 m : marks) {
            strike(Telegraph.Shape.circle(m, 2.4F).warmup(warmup).color(Telegraph.HOLY), foes -> {
                pillar(sl, m);
                for (LivingEntity e : foes) {
                    if (e.hurt(spellSource(null), attackPower() * 0.9F)) {
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                        e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), this);
                    }
                }
            });
        }
    }

    private void pillar(ServerLevel sl, Vec3 m) {
        BbFx.column(sl, ParticleTypes.END_ROD, m, 18, 30, 0.15);
        BbFx.column(sl, BbFx.dust(BbFx.LIGHT_WHITE, 2.4F), m, 10, 16, 0.4);
        sl.sendParticles(ParticleTypes.FLASH, m.x, m.y + 1, m.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FIREWORK, m.x, m.y + 0.5, m.z, 20, 0.5, 0.2, 0.5, 0.15);
        BbFx.sound(sl, m, SoundEvents.TRIDENT_THUNDER.value(), 1.0F, 1.3F);
    }

    /**
     * Julgamento Cruzado: o Serafim abre os braços em cruz e duas faixas de luz perpendiculares cruzam o alvo
     * (26 ticks de aviso). Fase 2+: uma segunda cruz girada 45° logo depois (é preciso trocar de quadrante).
     * Fase 3: faixas mais largas e a 2ª cruz mais rápida. No fim, pousa exausto.
     */
    private void crossJudgment(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int second = phase() >= 3 ? 14 : 18;
        if (t == 0) {
            lockFacing(target);
            crossCenter = target.position();
            crossAngle = random.nextFloat() * 90F;
            announce("cross_judgment", Announce.STYLE_SPELL);
            hint("cross");
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.9F);
            crossLines(sl, crossAngle, CROSS_WARN);
        }
        holdFacing();
        if (t < CROSS_WARN) {
            hoverToward(position().add(0, 0.12, 0), 0.5);
            if (t % 3 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = Math.toRadians(crossAngle + i * 90);
                    sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * 1.8, getY() + 2.4, getZ() + Math.sin(a) * 1.8, 1, 0, 0, 0, 0.02);
                }
            }
        }
        if (t == CROSS_WARN && phase() >= 2) crossLines(sl, crossAngle + 45F, second);
        if (t == CROSS_WARN + 2) BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 1.4F);
        // Logo depois da última cruz ele pousa exausto (a janela começa já, sem ficar parado no ar).
        int lastHit = phase() >= 2 ? CROSS_WARN + second : CROSS_WARN;
        if (t >= lastHit + 6) {
            land(40);
            endAttack();
        }
    }

    private void crossLines(ServerLevel sl, float angleDeg, int warmup) {
        float width = phase() >= 3 ? 3.6F : 3.0F;
        for (int i = 0; i < 2; i++) {
            double a = Math.toRadians(angleDeg + i * 90);
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 from = crossCenter.add(d.scale(-22));
            Vec3 to = crossCenter.add(d.scale(22));
            strike(Telegraph.Shape.line(from, to, width).warmup(warmup).color(Telegraph.HOLY), foes -> {
                for (int k = 0; k <= 22; k += 2) {
                    Vec3 p = from.add(d.scale(k * 2.0));
                    sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.5, p.z, 3, 0.4, 2.0, 0.4, 0.02);
                    sl.sendParticles(BbFx.dust(BbFx.LIGHT_WHITE, 2.2F), p.x, p.y + 1.0, p.z, 2, 0.5, 1.2, 0.5, 0);
                }
                sl.sendParticles(ParticleTypes.FLASH, crossCenter.x, crossCenter.y + 1, crossCenter.z, 1, 0, 0, 0, 0);
                BbFx.sound(sl, crossCenter, SoundEvents.TRIDENT_THUNDER.value(), 1.4F, 1.5F);
                BossKit.shake(sl, crossCenter, 20, 0.8F, 10);
                for (LivingEntity e : foes) {
                    if (e.hurt(spellSource(null), attackPower() * 0.85F)) {
                        e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), this);
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), this);
                    }
                }
            });
        }
    }

    /** Rajada das asas: círculo marcado (10 ticks) ao redor; empurra e cega quem ficou. */
    private void gust(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            strike(Telegraph.Shape.circle(groundBelow(), 6.5F).warmup(10).color(Telegraph.WARNING).noRunes(), foes -> {
                for (LivingEntity e : foes) {
                    Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(push.x * 2.2, 0.55, push.z * 2.2);
                    e.hurtMarked = true;
                    e.hurt(spellSource(null), attackPower() * 0.4F);
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0), this);
                }
            });
        }
        if (t != 10) return;
        BbFx.ring(sl, ParticleTypes.CLOUD, position().add(0, 1.0, 0), 3.0, 24);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 30, 1.5, 1.0, 1.5, 0.2);
        BbFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 1.2F);
    }

    /**
     * Mergulho: sobe, TRAVA a direção (linha marcada no chão por 12 ticks) e atravessa com a lança. Se cravar no chão,
     * fica preso e exausto (janela de corpo a corpo bem generosa).
     */
    private void dive(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t < 8) {
            hoverToward(position().add(0, 2.5, 0), 1.0);
            faceTarget(target);
            return;
        }
        if (t == 8) {
            stopMoving();
            diveCrashed = false;
            Vec3 start = position().add(0, 1.7, 0);
            diveDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(start).normalize();
            Vec3 end = start.add(diveDir.scale(24));
            HitResult hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            warn(Telegraph.Shape.line(groundAhead(position(), diveDir, 0.5, target.getY()), new Vec3(stop.x, target.getY(), stop.z), 2.6F)
                    .warmup(12).color(Telegraph.HOLY));
            // Onde a lança vai cravar (os pés batem no chão antes do peito): a onda do impacto também fica marcada.
            Vec3 feet = position().add(0, 0.2, 0);
            HitResult fh = level().clip(new ClipContext(feet, feet.add(diveDir.scale(DIVE_SPEED * DIVE_TICKS)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (fh.getType() != HitResult.Type.MISS) {
                Vec3 at = fh.getLocation();
                Vec3 g = groundAt(at.x, at.y + 0.5, at.z, 1.5, 4);
                int travel = Mth.ceil(at.distanceTo(feet) / DIVE_SPEED);
                warn(Telegraph.Shape.circle(g != null ? g : at, (float) CRASH_RADIUS).warmup(12 + travel)
                        .color(Telegraph.HOLY).noFlash().noRunes());
            }
            BbFx.sound(sl, position(), SoundEvents.PHANTOM_SWOOP, 2.0F, 0.7F);
        }
        if (diveDir == null) {
            manualVelocity = false;
            return;
        }
        float yaw = (float) (Mth.atan2(diveDir.z, diveDir.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (t < 20) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.7, getZ(), 2, 0.4, 0.4, 0.4, 0.02);
            return;
        }
        if (t < 20 + DIVE_TICKS && !diveCrashed) {
            manualVelocity = true;
            setDeltaMovement(diveDir.scale(DIVE_SPEED));
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.7, getZ(), 3, 0.3, 0.3, 0.3, 0.02);
            for (LivingEntity e : enemiesNear(position().add(0, 1.7, 0), 2.2)) {
                if (!diveHit.add(e.getUUID())) continue;
                if (e.hurt(damageSources().mobAttack(this), attackPower())) {
                    e.setDeltaMovement(diveDir.x * 1.5, 0.6, diveDir.z * 1.5);
                    e.hurtMarked = true;
                }
            }
            if (horizontalCollision || verticalCollision || onGround()) crash(sl);
            return;
        }
        manualVelocity = false;
        if (!diveCrashed) setDeltaMovement(getDeltaMovement().scale(0.7));
    }

    /** Cravou a lança no chão: onda pequena (no fim da linha marcada) e fica preso, exausto. */
    private void crash(ServerLevel sl) {
        diveCrashed = true;
        manualVelocity = false;
        setDeltaMovement(Vec3.ZERO);
        Vec3 g = groundBelow();
        sl.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.5, g.z, 2, 0.8, 0.2, 0.8, 0);
        sl.sendParticles(ParticleTypes.END_ROD, g.x, g.y + 0.5, g.z, 40, 1.5, 0.3, 1.5, 0.2);
        BbFx.sound(sl, g, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F);
        BbFx.sound(sl, g, SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
        BossKit.shake(sl, g, 20, 1.2F, 12);
        for (LivingEntity e : enemiesNear(g, CRASH_RADIUS)) {
            if (diveHit.add(e.getUUID()) && e.hurt(damageSources().mobAttack(this), attackPower() * 0.6F)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                e.hurtMarked = true;
            }
        }
        land(50);
    }

    /** Coro Celestial: invoca querubins / anjos sentinela (por id) em pontos marcados. */
    private void choir(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) summonSpotsCache = summonSpots(2, 3.5, 20);
        if (t < 20 && t % 4 == 0) BbFx.ring(sl, ParticleTypes.END_ROD, position().add(0, 0.5, 0), 3.0, 16);
        if (t != 20) return;
        String[] ids = {"cherub", "angel_sentinel"};
        int made = 0;
        for (int i = 0; i < summonSpotsCache.size(); i++) {
            Vec3 at = summonSpotsCache.get(i).add(0, 0.5, 0);
            String id = entityExists(ids[i % 2]) ? ids[i % 2] : ids[1 - i % 2];
            if (summon(id, null, at, target) != null) {
                made++;
                sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 25, 0.3, 0.6, 0.3, 0.08);
            }
        }
        if (made > 0) {
            announce("choir", Announce.STYLE_BOSS);
            BbFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.8F);
        }
    }

    /**
     * Oração: o Serafim POUSA e canaliza 2,5 s para se curar (glifo Si Mesmo→Curar→Amplificar se existir; no mínimo
     * 10%). Dano alto (escala com o grupo) interrompe — corpo a corpo chega nele.
     */
    private void prayer(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            prayerDamage = 0;
            announce("prayer", Announce.STYLE_BOSS);
            hint("prayer");
            BbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.8F);
        }
        Vec3 ground = groundBelow();
        hoverToward(new Vec3(getX(), ground.y + 0.05, getZ()), 1.0);
        if (t < 50 && t % 2 == 0) {
            double a = t * 0.4;
            for (int i = 0; i < 3; i++) {
                double aa = a + i * Math.PI * 2 / 3;
                sl.sendParticles(BbFx.dust(BbFx.LIGHT, 1.3F), getX() + Math.cos(aa) * 1.6, getY() + t * 0.07, getZ() + Math.sin(aa) * 1.6,
                        1, 0, 0, 0, 0);
            }
        }
        float breakAt = 60F * (1 + 0.3F * (BossKit.partySize(this) - 1));
        if (prayerDamage >= breakAt && t < 50) {
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 2, getZ(), 30, 0.8, 1.0, 0.8, 0.3);
            BbFx.sound(sl, position(), SoundEvents.SHIELD_BREAK, 1.5F, 0.7F);
            announce("prayer_broken", Announce.STYLE_SYSTEM);
            cancelAttack();
            land(40);
            return;
        }
        if (t == 50) {
            float before = getHealth();
            BbSpells.cast(this, Element.LIGHT, 4.0F, "self", "heal", "amplify");
            float min = getMaxHealth() * 0.1F;
            float healed = getHealth() - before;
            if (healed < min) heal(min - healed);
            healsUsed++;
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 2, getZ(), 60, 1.0, 1.4, 1.0, 0.4);
            sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 3.6, getZ(), 6, 0.6, 0.3, 0.6, 0);
            BbFx.sound(sl, position(), SoundEvents.TOTEM_USE, 1.2F, 1.2F);
        }
    }

    // =====================================================================================
    //  Movimento, dano, fases
    // =====================================================================================
    @Override
    protected void moveCombat(LivingEntity target) {
        orbit += (phase() >= 3 ? 0.032 : 0.022) * orbitDir;
        if (random.nextInt(400) == 0) orbitDir = -orbitDir;
        double r = 9.0;
        Vec3 want = target.position().add(Math.cos(orbit) * r, 4.5, Math.sin(orbit) * r);
        // Se a posição desejada estiver atrás de uma parede, chega mais perto.
        HitResult hit = level().clip(new ClipContext(target.getEyePosition(), want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) want = hit.getLocation().add(target.getEyePosition().subtract(hit.getLocation()).normalize().scale(1.5));
        hoverToward(want, 1.0);
        getLookControl().setLookAt(target, 30F, 30F);
    }

    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        BossAttack cur = currentAttack();
        if (cur != null && cur.id().equals("prayer")) prayerDamage += amount;
        return amount;
    }

    @Override
    protected void onIntroStart(ServerLevel sl) {
        BbFx.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.7F);
        BbFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F);
        BbFx.column(sl, ParticleTypes.END_ROD, position(), 12, 40, 0.5);
    }

    @Override
    protected void onPhaseStart(ServerLevel sl, int phase) {
        BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 0.8F);
    }

    @Override
    protected void transitionFx(ServerLevel sl, int left) {
        hoverToward(position().add(0, 0.05, 0), 0.4);
        if (left % 2 == 0) {
            double a = left * 0.35;
            for (int i = 0; i < 6; i++) {
                double aa = a + i * Math.PI / 3;
                sl.sendParticles(BbFx.dust(BbFx.LIGHT_WHITE, 1.6F), getX() + Math.cos(aa) * 2.5, getY() + 2.0, getZ() + Math.sin(aa) * 2.5,
                        1, 0, 0, 0, 0);
            }
        }
    }

    /** Clarão da troca de fase: empurra e cega quem ficou no círculo marcado. */
    @Override
    protected void phaseWaveHit(ServerLevel sl, LivingEntity e) {
        Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize();
        e.setDeltaMovement(push.x * 1.6, 0.5, push.z * 1.6);
        e.hurtMarked = true;
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
        e.hurt(spellSource(null), attackPower() * 0.3F);
    }

    @Override
    protected void onPhaseEnd(ServerLevel sl, int phase) {
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 80, 2.0, 2.0, 2.0, 0.3);
        BbFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.8F);
        if (phase == 2) {
            BossAttack c = attack("choir");
            if (c != null && c.condition.test(getTarget())) startAttack(c);
        }
        if (phase >= 3) startAttack(attack("judgment"));
    }

    // =====================================================================================
    //  Morte
    // =====================================================================================
    @Override
    protected int deathDuration() {
        return 94; // v1.0: 4 s de animação + o corpo se desfazendo
    }

    @Override
    protected void deathFx(ServerLevel sl, int t) {
        setDeltaMovement(0, -0.02, 0);
        if (t % 3 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 6, 1.4, 1.2, 1.4, 0.05);
            sl.sendParticles(BbFx.dust(BbFx.LIGHT_WHITE, 1.8F), getX(), getY() + 2, getZ(), 6, 1.6, 1.4, 1.6, 0);
        }
        if (t == 2) BbFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
        if (t == 40) BbFx.sound(sl, position(), SoundEvents.BELL_RESONATE, 2.0F, 0.6F);
    }

    @Override
    protected void onFinalDeath(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 2, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 150, 1.5, 2.0, 1.5, 0.35);
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 2, getZ(), 60, 1.0, 1.5, 1.0, 0.25);
        BbFx.sound(sl, position(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0F, 0.8F);
    }

    // =====================================================================================
    //  Cliente / sons / dados
    // =====================================================================================
    @Override
    protected void clientFx() {
        drawBeamsClient(BbFx.dust(BbFx.LIGHT, 1.5F), ParticleTypes.END_ROD);
        int n = phase() >= 3 ? 3 : 1;
        for (int i = 0; i < n; i++) {
            if (random.nextInt(3) != 0) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.2 + random.nextDouble() * 1.8;
            level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + 1.5 + random.nextDouble() * 2,
                    getZ() + Math.sin(a) * r, 0, -0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.WAX_OFF, getX() + (random.nextDouble() - 0.5), getY() + 3.5, getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
        }
        if (random.nextInt(2) == 0 && !isGrounded()) {
            level().addParticle(BbFx.dust(BbFx.LIGHT_WHITE, 1.0F), getX() + (random.nextDouble() - 0.5) * 0.8, getY() - 0.1,
                    getZ() + (random.nextDouble() - 0.5) * 0.8, 0, -0.05, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HealsUsed", healsUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        healsUsed = tag.getInt("HealsUsed");
    }
}
