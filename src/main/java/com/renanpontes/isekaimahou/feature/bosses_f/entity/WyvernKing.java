package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Wyvern Rei dos Picos (covil {@code wyvern_peak}). Luta quase toda no ar, pousando de tempos em tempos.
 * <ul>
 *   <li><b>Mergulho em Linha</b>: alinha rente ao chão e risca o campo — a faixa enche em cascata, segmento por segmento,
 *       no ritmo exato do voo (fase 2: 2 mergulhos seguidos; fase 3: 3);</li>
 *   <li><b>Raios</b>: um círculo segue cada jogador e trava; o raio cai no ponto travado (Eletrizado). Fase 2+: raios
 *       extras espalhados;</li>
 *   <li><b>Rajada de Vento</b>: cone que empurra forte; <b>Sopro de Trovão</b> (fase 2+): faixa longa;</li>
 *   <li><b>Tempestade do Rei</b> (fase 3): só as zonas verdes protegem;</li>
 *   <li>em terra (janela para o corpo a corpo): mordida, cauda e rajada; decola com um empurrão em anel.</li>
 * </ul>
 */
public class WyvernKing extends OverworldBoss {
    private static final int COLOR = 0x7FDBFF;
    private static final int BOLT = 0x9FD8FF;
    private static final int GUST = 0xE6FFF6;
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(WyvernKing.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(WyvernKing.class, EntityDataSerializers.BOOLEAN);

    private static final int M_NONE = 0, M_ALIGN = 1, M_DIVE_WIND = 2, M_DIVE_RUN = 3, M_RECOVER = 4, M_LIGHTNING = 5, M_BREATH = 6,
            M_GUST = 7, M_STORM = 8, M_LANDING = 9, M_BITE = 10, M_TAIL = 11, M_TAKEOFF = 12;
    private static final int DIVE_WIND = 18;
    private static final double DIVE_SPEED = 1.0;
    private static final double SEGMENT = 2.0;

    private int move = M_NONE;
    private int moveTimer;
    private int airTime;
    private int groundTime;
    private int airGap = 40;
    private int diveCd = 60, boltCd = 120, breathCd = 160, gustCd = 80, stormCd = 0, biteCd = 0, tailCd = 0;
    private int divesLeft;
    private float orbitAngle;
    private int orbitDir = 1;
    private float attackYaw;
    private Vec3 diveFrom = Vec3.ZERO;
    private Vec3 diveDir = Vec3.ZERO;
    private int diveTicks;
    private final Set<UUID> diveHit = new HashSet<>();
    @Nullable
    private Vec3 landAt;
    private boolean stormDone;

    public WyvernKing(EntityType<? extends WyvernKing> type, Level level) {
        super(type, level);
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(360.0, 8.0, 0.3, 12.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    public String bossId() {
        return "wyvern_king";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.BLUE;
    }

    @Override
    protected void configureBar(ServerBossEvent bossBar) {
        bossBar.setDarkenScreen(true);
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.66F, 0.33F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("roar", "bite", "tail", "gust", "breath", "lightning");
    }

    @Override
    protected double leashRadius() {
        return 52.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_DIVING, false);
    }

    public boolean isFlying() {
        return entityData.get(DATA_FLYING);
    }

    private void setFlying(boolean on) {
        entityData.set(DATA_FLYING, on);
        setNoGravity(on);
        if (on) getNavigation().stop();
    }

    private void setDiving(boolean on) {
        entityData.set(DATA_DIVING, on);
    }

    // ------------------------------------------------------------------ Voo

    /** Acelera em direção a um ponto (voo próprio; a navegação fica parada). */
    private void steer(Vec3 to, double accel, double max) {
        Vec3 d = to.subtract(position());
        double len = d.length();
        Vec3 v = getDeltaMovement().scale(0.9);
        if (len > 0.05) v = v.add(d.scale(Math.min(accel, len * 0.25) / len));
        if (v.length() > max) v = v.normalize().scale(max);
        setDeltaMovement(v);
    }

    private void face(Vec3 p) {
        float yaw = Mth.approachDegrees(yBodyRot, yawTo(p), 12F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Altura de voo: alguns blocos acima do chão sob o alvo (ou de casa), sem passar do teto do mundo. */
    private double cruiseY(ServerLevel sl, Vec3 around, double above) {
        Vec3 g = groundAt(sl, around.add(0, 2, 0), 24);
        return Math.min(sl.getMaxBuildHeight() - 4, g.y + above);
    }

    @Override
    protected boolean moveHome(ServerLevel sl, Vec3 to) {
        if (!isFlying()) setFlying(true);
        Vec3 aim = new Vec3(to.x, Math.max(to.y + 4, getY()), to.z);
        steer(aim, 0.12, 0.9);
        face(aim);
        boolean arrived = position().distanceToSqr(aim) < 25;
        if (arrived && getTarget() == null) {
            setFlying(false);
            setDeltaMovement(Vec3.ZERO);
        }
        return arrived;
    }

    @Override
    protected boolean isTargetUnreachable(LivingEntity target) {
        // No ar ele alcança qualquer lugar; em terra vale o teste normal (aí decola).
        return !isFlying() && super.isTargetUnreachable(target);
    }

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (move == M_NONE) takeOff(sl);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return move != M_NONE;
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        diveCd--;
        boltCd--;
        breathCd--;
        gustCd--;
        stormCd--;
        biteCd--;
        tailCd--;
        if (move != M_NONE) {
            tickMove(sl, target);
            return;
        }
        if (target == null) {
            if (isFlying()) steer(position().add(0, 0, 0), 0.05, 0.3);
            return;
        }
        if (isFlying()) airLoop(sl, target);
        else groundLoop(sl, target);
    }

    private void airLoop(ServerLevel sl, LivingEntity target) {
        airTime++;
        // Órbita em volta do alvo, olhando para ele.
        orbitAngle += 0.025F * orbitDir;
        if (random.nextInt(200) == 0) orbitDir = -orbitDir;
        double r = 13.0;
        Vec3 c = target.position();
        Vec3 want = new Vec3(c.x + Math.cos(orbitAngle) * r, cruiseY(sl, c, 8.5), c.z + Math.sin(orbitAngle) * r);
        steer(want, 0.09, 0.7);
        face(target.position());
        if (airGap > 0) {
            airGap--;
            return;
        }
        int ph = phase();
        double dist = distanceTo(target);
        if (ph >= 2 && (!stormDone || stormCd <= 0)) {
            startStorm(sl);
        } else if (diveCd <= 0 && hasLineOfSight(target)) {
            divesLeft = ph >= 2 ? 3 : ph >= 1 ? 2 : 1;
            startDive(sl, target, true);
        } else if (boltCd <= 0) {
            startLightning(sl);
        } else if (ph >= 1 && breathCd <= 0 && dist < 24 && hasLineOfSight(target)) {
            startBreath(sl, target);
        } else if (gustCd <= 0 && dist < 16 && hasLineOfSight(target)) {
            startGust(sl, target);
        } else if (airTime > (ph >= 2 ? 220 : 300)) {
            startLanding(sl, target);
        }
    }

    private void groundLoop(ServerLevel sl, LivingEntity target) {
        groundTime++;
        double dist = distanceTo(target);
        float ang = Math.abs(angleTo(target.position()));
        if (groundTime > (phase() >= 1 ? 110 : 140)) {
            takeOff(sl);
            return;
        }
        if (tailCd <= 0 && dist < 7 && ang > 110F) {
            startTail(sl);
        } else if (biteCd <= 0 && dist < 4.6 && ang < 50F) {
            startBite(sl, target);
        } else if (gustCd <= 0 && dist < 12 && dist > 5 && ang < 45F) {
            startGust(sl, target);
        } else {
            chase(target, 1.15);
        }
    }

    private void tickMove(ServerLevel sl, @Nullable LivingEntity target) {
        moveTimer--;
        switch (move) {
            case M_ALIGN -> {
                // Vai rápido até o começo da faixa, rente ao chão.
                Vec3 at = diveFrom.add(0, 1.2, 0);
                steer(at, 0.25, 1.4);
                face(at.add(diveDir.scale(10)));
                if (position().distanceToSqr(at) < 2.5 || moveTimer <= 0) {
                    setDeltaMovement(Vec3.ZERO);
                    teleportTo(at.x, at.y, at.z);
                    beginDiveRun(sl);
                }
            }
            case M_DIVE_WIND -> {
                setDeltaMovement(Vec3.ZERO);
                holdYaw(attackYaw);
                if (moveTimer <= 0) {
                    move = M_DIVE_RUN;
                    moveTimer = diveTicks + 2;
                    playSound(SoundEvents.PHANTOM_SWOOP, 3.0F, 0.6F);
                }
            }
            case M_DIVE_RUN -> {
                holdYaw(attackYaw);
                setDeltaMovement(diveDir.scale(DIVE_SPEED));
                if (tickCount % 2 == 0) {
                    sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 6, 0.8, 0.2, 0.8, 0.05);
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 6, 0.8, 0.4, 0.8, 0.1);
                }
                if (moveTimer <= 0 || horizontalCollision) endDive(sl, target);
            }
            case M_RECOVER -> {
                setDiving(false);
                steer(position().add(diveDir.scale(4)).add(0, 6, 0), 0.15, 0.9);
                if (moveTimer <= 0) {
                    move = M_NONE;
                    if (divesLeft > 0 && target != null && target.isAlive()) startDive(sl, target, false);
                }
            }
            case M_LIGHTNING, M_BREATH, M_GUST -> {
                setDeltaMovement(getDeltaMovement().scale(0.6));
                holdYaw(attackYaw);
                if (move == M_BREATH && moveTimer < 14 && moveTimer > 4) breathChargeFx(sl);
                if (moveTimer <= 0) move = M_NONE;
            }
            case M_STORM -> {
                if (isFlying()) steer(new Vec3(getX(), cruiseY(sl, position(), 14), getZ()), 0.08, 0.4);
                if (moveTimer % 10 == 0) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 20, 1.5, 1.0, 1.5, 0.2);
                if (moveTimer <= 0) move = M_NONE;
            }
            case M_LANDING -> {
                if (landAt == null) {
                    move = M_NONE;
                    return;
                }
                steer(landAt, 0.12, 0.9);
                face(landAt);
                if (moveTimer <= 0 || position().distanceToSqr(landAt) < 1.2) {
                    touchDown(sl);
                }
            }
            case M_BITE, M_TAIL -> {
                getNavigation().stop();
                holdYaw(attackYaw);
                if (moveTimer <= 0) move = M_NONE;
            }
            case M_TAKEOFF -> {
                setDeltaMovement(new Vec3(0, 0.45, 0));
                if (moveTimer <= 0) move = M_NONE;
            }
            default -> move = M_NONE;
        }
    }

    // ------------------------------------------------------------------ Mergulho em Linha

    private void startDive(ServerLevel sl, LivingEntity target, boolean first) {
        divesLeft--;
        diveCd = phase() >= 2 ? 150 : 200;
        airGap = 30;
        Vec3 here = position();
        Vec3 dir = target.position().subtract(here).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-3) dir = Vec3.directionFromRotation(0, yBodyRot);
        diveDir = dir.normalize();
        // Começa 9 blocos ANTES do alvo e passa por ele (mais perto se ali for rocha: picos e paredões).
        diveFrom = diveStart(sl, target.position());
        move = M_ALIGN;
        moveTimer = 40;
        setDiving(true);
        attackYaw = yawTo(diveFrom.add(diveDir));
        if (first) announce("dive");
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.7F);
    }

    /** Começo da faixa: o primeiro ponto livre (cabe o corpo rente ao chão) a 9, 7, 5 ou 3 blocos antes do alvo. */
    private Vec3 diveStart(ServerLevel sl, Vec3 target) {
        for (double back : new double[] {9, 7, 5, 3}) {
            Vec3 p = groundAt(sl, target.subtract(diveDir.scale(back)).add(0, 3, 0), 12);
            Vec3 body = p.add(0, 1.2, 0);
            if (sl.noCollision(this, getDimensions(getPose()).makeBoundingBox(body))) return p;
        }
        return groundAt(sl, target.add(0, 1, 0), 6);
    }

    private void beginDiveRun(ServerLevel sl) {
        attackYaw = (float) (Mth.atan2(diveDir.z, diveDir.x) * (180.0 / Math.PI)) - 90.0F;
        holdYaw(attackYaw);
        Vec3 start = position().subtract(0, 1.2, 0);
        Vec3 end = lineEnd(sl, start, diveDir, 24.0);
        double len = start.distanceTo(end);
        diveTicks = (int) Math.ceil(len / DIVE_SPEED);
        int segments = Math.max(1, (int) Math.ceil(len / SEGMENT));
        double step = len / segments;
        diveHit.clear();
        float dmg = attackDamage() * 1.1F;
        for (int k = 0; k < segments; k++) {
            Vec3 a = groundAt(sl, start.add(diveDir.scale(step * k)), 6);
            Vec3 b = groundAt(sl, start.add(diveDir.scale(step * (k + 1))), 6);
            int warm = DIVE_WIND + (int) Math.round(step * (k + 1) / DIVE_SPEED);
            Telegraph.Shape seg = Telegraph.Shape.line(a, b, 3.2F).warmup(warm).color(Telegraph.WARNING).owner(this).noRunes();
            if (k == 0) seg.castName(attackName("dive"));
            Telegraph.delayed(sl, seg, hits -> diveSegment(sl, hits, dmg));
        }
        move = M_DIVE_WIND;
        moveTimer = DIVE_WIND;
    }

    private void diveSegment(ServerLevel sl, List<LivingEntity> hits, float dmg) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e) || !diveHit.add(e.getUUID())) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(diveDir.x * 1.3, 0.6, diveDir.z * 1.3));
                e.hurtMarked = true;
                if (phase() >= 2) Statuses.apply(e, Statuses.Kind.SHOCKED, 1, 80, this, 1F);
            }
        }
    }

    private void endDive(ServerLevel sl, @Nullable LivingEntity target) {
        move = M_RECOVER;
        moveTimer = 18;
        BfFx.groundBurst(sl, position(), 3.0, 30);
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.9F);
    }

    // ------------------------------------------------------------------ Raios

    private void startLightning(ServerLevel sl) {
        int ph = phase();
        move = M_LIGHTNING;
        moveTimer = 30;
        boltCd = ph >= 2 ? 150 : 210;
        airGap = 30;
        attackYaw = yBodyRot;
        action("lightning");
        announce("lightning");
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.4F);
        float dmg = scaled(10.0F);
        List<ServerPlayer> party = BossKit.partyAround(this, 44);
        int n = 0;
        for (ServerPlayer p : party) {
            if (n++ >= 6) break;
            int[] id = new int[1];
            id[0] = Telegraph.delayed(sl, Telegraph.Shape.follow(p, 2.4F).warmup(40).lock(10).color(BOLT).owner(this).castName(attackName("lightning")),
                    hits -> strike(sl, Telegraph.centerOf(id[0]), hits, dmg, 2));
            if (ph >= 1) {
                for (int k = 0; k < 2; k++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = 3.0 + random.nextDouble() * 3.5;
                    Vec3 spot = groundAt(sl, p.position().add(Math.cos(a) * r, 2, Math.sin(a) * r), 8);
                    Telegraph.delayed(sl, Telegraph.Shape.circle(spot, 2.2F).warmup(30 + 13 * k + random.nextInt(8)).color(BOLT).owner(this).noRunes(),
                            hits -> strike(sl, spot, hits, dmg * 0.8F, 1));
                }
            }
        }
    }

    private void strike(ServerLevel sl, @Nullable Vec3 at, List<LivingEntity> hits, float dmg, int shock) {
        if (!isAlive() || at == null) return;
        BfFx.visualBolt(sl, at);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 30, 1.0, 0.6, 1.0, 0.3);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) Statuses.apply(e, Statuses.Kind.SHOCKED, shock, 100, this, 1F);
        }
    }

    // ------------------------------------------------------------------ Sopro de Trovão

    private void startBreath(ServerLevel sl, LivingEntity target) {
        move = M_BREATH;
        moveTimer = 36;
        breathCd = phase() >= 2 ? 150 : 190;
        airGap = 30;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("breath");
        announce("breath");
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = groundAt(sl, position(), 24).add(dir.scale(2));
        Vec3 to = lineEnd(sl, from, dir, 24.0);
        float dmg = scaled(12.0F);
        Vec3 head = position().add(dir.scale(2.5)).add(0, 1.6, 0);
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 2.6F).warmup(26).color(BOLT).owner(this).castName(attackName("breath")), hits -> {
            if (!isAlive()) return;
            BfFx.zigzag(sl, head, to.add(0, 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 8, 1.2);
            BfFx.line(sl, from.add(0, 0.3, 0), to.add(0, 0.3, 0), BfFx.dust(BOLT, 1.6F), 3.0);
            playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 3.0F, 0.8F);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().indirectMagic(this, this), dmg)) Statuses.apply(e, Statuses.Kind.SHOCKED, 2, 100, this, 1F);
            }
        });
    }

    private void breathChargeFx(ServerLevel sl) {
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 head = position().add(dir.scale(2.5)).add(0, 1.6, 0);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 6, 0.3, 0.3, 0.3, 0.15);
    }

    // ------------------------------------------------------------------ Rajada de Vento

    private void startGust(ServerLevel sl, LivingEntity target) {
        move = M_GUST;
        moveTimer = 26;
        gustCd = phase() >= 1 ? 110 : 150;
        airGap = 20;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("gust");
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.5F);
        Vec3 o = isFlying() ? groundAt(sl, position(), 24) : position();
        float dmg = scaled(6.0F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Telegraph.delayed(sl, Telegraph.Shape.cone(o, attackYaw, 70F, 15F).warmup(18).color(GUST).owner(this).castName(attackName("gust")), hits -> {
            if (!isAlive()) return;
            for (int k = 0; k < 30; k++) {
                double a = Math.toRadians(attackYaw + (random.nextDouble() - 0.5) * 70);
                Vec3 v = new Vec3(-Math.sin(a), 0.05, Math.cos(a)).scale(1.4);
                sl.sendParticles(ParticleTypes.CLOUD, o.x, o.y + 1.0, o.z, 0, v.x, v.y, v.z, 1.0);
            }
            playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2.5F, 0.6F);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                e.hurt(damageSources().mobAttack(this), dmg);
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 2.0, 0.45, dir.z * 2.0));
                e.hurtMarked = true;
            }
        });
    }

    // ------------------------------------------------------------------ Tempestade do Rei

    private void startStorm(ServerLevel sl) {
        stormDone = true;
        stormCd = 600;
        move = M_STORM;
        moveTimer = 80;
        airGap = 40;
        action("lightning");
        announce("storm");
        hintOnce(sl, "storm");
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.6F);
        List<ServerPlayer> party = BossKit.partyAround(this, 40);
        Vec3 c = Vec3.ZERO;
        for (ServerPlayer p : party) c = c.add(p.position());
        c = party.isEmpty() ? position() : c.scale(1.0 / party.size());
        List<Vec3> zones = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            Vec3 z = findGroundSpot(sl, c, 4.0, 11.0, null);
            if (z == null) z = groundAt(sl, c.add((random.nextDouble() - 0.5) * 10, 2, (random.nextDouble() - 0.5) * 10), 10);
            zones.add(z);
            Telegraph.safe(sl, z, 3.2F, 70);
        }
        float dmg = scaled(16.0F);
        BossKit.schedule(sl, 70, () -> {
            if (!isAlive()) return;
            for (ServerPlayer p : BossKit.partyAround(this, 44)) {
                boolean safe = false;
                for (Vec3 z : zones) {
                    if (Telegraph.Shape.safe(z, 3.2F).contains(p)) {
                        safe = true;
                        break;
                    }
                }
                if (safe || !isFoe(p)) continue;
                BfFx.visualBolt(sl, p.position());
                if (p.hurt(damageSources().indirectMagic(this, this), dmg)) Statuses.apply(p, Statuses.Kind.SHOCKED, 3, 120, this, 1F);
            }
            for (int k = 0; k < 6; k++) {
                Vec3 at = groundAt(sl, position().add((random.nextDouble() - 0.5) * 30, 0, (random.nextDouble() - 0.5) * 30), 30);
                BfFx.visualBolt(sl, at);
            }
            BossKit.shake(sl, position(), 44, 1.6F, 20);
        });
    }

    // ------------------------------------------------------------------ Pouso e decolagem

    private void startLanding(ServerLevel sl, LivingEntity target) {
        Vec3 spot = findGroundSpot(sl, target.position(), 5.0, 9.0, null);
        if (spot == null) {
            airTime = 0;
            return;
        }
        landAt = spot;
        move = M_LANDING;
        int t = (int) Mth.clamp(position().distanceTo(spot) / 0.7, 18, 50);
        moveTimer = t + 10;
        announce("land");
        float dmg = scaled(8.0F);
        Telegraph.delayed(sl, Telegraph.Shape.circle(spot, 3.6F).warmup(t).color(Telegraph.WARNING).owner(this).castName(attackName("land")), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    Vec3 push = e.position().subtract(spot).multiply(1, 0, 1);
                    push = push.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : push.normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.5, push.z));
                    e.hurtMarked = true;
                }
            }
        });
    }

    private void touchDown(ServerLevel sl) {
        if (landAt != null) teleportTo(landAt.x, landAt.y, landAt.z);
        setDeltaMovement(Vec3.ZERO);
        setFlying(false);
        move = M_NONE;
        groundTime = 0;
        landAt = null;
        BfFx.groundBurst(sl, position(), 3.5, 50);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 30, 2.0, 0.1, 2.0, 0.1);
        playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.6F);
        BossKit.shake(sl, position(), 22, 1.0F, 12);
        hintOnce(sl, "landed");
        biteCd = 10;
        tailCd = 30;
    }

    private void takeOff(ServerLevel sl) {
        move = M_TAKEOFF;
        moveTimer = 14;
        airTime = 0;
        airGap = 30;
        setFlying(true);
        action("gust");
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
        Vec3 c = position();
        Telegraph.delayed(sl, Telegraph.Shape.circle(c, 5.0F).warmup(10).color(GUST).owner(this).noRunes(), hits -> {
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                e.hurt(damageSources().mobAttack(this), scaled(4.0F));
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : push.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.4, 0.4, push.z * 1.4));
                e.hurtMarked = true;
            }
            BfFx.ring(sl, c.add(0, 0.2, 0), 2.5, 30, ParticleTypes.CLOUD, 0.15);
        });
    }

    // ------------------------------------------------------------------ Golpes em terra

    private void startBite(ServerLevel sl, LivingEntity target) {
        move = M_BITE;
        moveTimer = 14;
        biteCd = 26;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        action("bite");
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), attackYaw, 80F, 4.8F).warmup(8).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) if (isFoe(e)) doHurtTarget(e);
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.4F, 0.6F);
                });
    }

    private void startTail(ServerLevel sl) {
        move = M_TAIL;
        moveTimer = 18;
        tailCd = 60;
        attackYaw = yBodyRot;
        getNavigation().stop();
        action("tail");
        float dmg = attackDamage();
        Vec3 c = position();
        Telegraph.delayed(sl, Telegraph.Shape.cone(c, attackYaw + 180F, 150F, 6.5F).warmup(14).color(Telegraph.WARNING).owner(this),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (e.hurt(damageSources().mobAttack(this), dmg)) {
                            Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.3, 0.45, push.z * 1.3));
                            e.hurtMarked = true;
                            if (phase() >= 1) Statuses.apply(e, Statuses.Kind.SHOCKED, 1, 80, this, 1F);
                        }
                    }
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
                });
    }

    // ------------------------------------------------------------------ Fases, intro, reset, loot

    @Override
    protected void onIntroEnd(ServerLevel level) {
        takeOff(level);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 1.2F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 5 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 15, 1.5, 1.0, 1.5, 0.2);
        if (t == 28) {
            BfFx.visualBolt(level, position().add(4, 0, 3));
            BfFx.visualBolt(level, position().add(-3, 0, -4));
            BossKit.shake(level, position(), 28, 1.2F, 16);
        }
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.9F);
        BossKit.shake(level, position(), 30, 1.2F, 20);
        for (int k = 0; k < 3; k++) {
            BfFx.visualBolt(level, groundAt(level, position().add((random.nextDouble() - 0.5) * 16, 0, (random.nextDouble() - 0.5) * 16), 30));
        }
        boltCd = Math.min(boltCd, 40);
        breathCd = Math.min(breathCd, 80);
    }

    @Override
    protected void onReset(ServerLevel level) {
        move = M_NONE;
        moveTimer = 0;
        airTime = groundTime = 0;
        airGap = 40;
        diveCd = 60;
        boltCd = 120;
        breathCd = 160;
        gustCd = 80;
        stormCd = 0;
        stormDone = false;
        landAt = null;
        setDiving(false);
        setFlying(false);
    }

    @Override
    protected void teleportHome(ServerLevel sl) {
        super.teleportHome(sl);
        if (!isAwake()) setFlying(false);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0 && isFlying()) {
            return loop(entityData.get(DATA_DIVING) ? "dive" : "fly");
        }
        return super.mainAnimation(moving);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setNoGravity(false);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        int n = trophyCount();
        dropItem(BfRegistry.WYVERN_HORN.get(), n, n);
        dropItem(BfRegistry.WYVERN_SCALE.get(), 2 + n, 4 + n);
        dropEssence(Element.WIND, 3, 5);
        dropEssence(Element.FIRE, 1, 2);
        dropArcaneScrolls(2);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(BfFx.reduced() ? 8 : 3) == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(1.0), getY() + 1.0 + random.nextDouble() * 1.2, getRandomZ(1.0), 0, 0, 0);
        }
        if (isFlying() && entityData.get(DATA_DIVING) && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.CLOUD, getRandomX(1.2), getY() + 0.8, getRandomZ(1.2), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WyvernFlying", isFlying());
        tag.putBoolean("StormDone", stormDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFlying(tag.getBoolean("WyvernFlying") && isAwake());
        stormDone = tag.getBoolean("StormDone");
    }
}
