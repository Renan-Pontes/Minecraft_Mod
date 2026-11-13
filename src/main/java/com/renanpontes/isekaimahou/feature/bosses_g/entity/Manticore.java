package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Manticora (covil nas rochas): leão com asas de morcego e cauda de escorpião cheia de espinhos.
 * <ul>
 *   <li><b>Garra</b> (cone curto), <b>Ferroada</b> (linha curta, veneno forte) e <b>Bote</b> (linha marcada até o alvo);</li>
 *   <li><b>Leque de Espinhos</b>: linhas em leque marcam o caminho dos espinhos de verdade (desvie saindo das linhas);</li>
 *   <li><b>Rugido Aterrorizante</b>: quem estiver no círculo fica Aterrorizado (fraco, lento, empurrado, e os golpes dele
 *       na Manticora valem metade);</li>
 *   <li><b>Voo Curto</b> (fase 2): levanta voo, chove espinhos sobre cada jogador e MERGULHA no ponto marcado — depois
 *       fica cansada no chão (leva +30% de dano);</li>
 *   <li>fase 3 (30%): frenesi — mais rápida, leque em duas ondas, rugidos mais frequentes.</li>
 * </ul>
 */
public class Manticore extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(Manticore.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TIRED = SynchedEntityData.defineId(Manticore.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xC0562E;
    private static final int SPIKE_COLOR = 0x9BE04A;
    private static final int FEAR_COLOR = 0x8A1C1C;
    private static final ResourceLocation FRENZY = IsekaiMahou.id("manticore_frenzy");

    private final Map<UUID, Long> frightened = new HashMap<>();
    private int swipeCd = 20;
    private int stingCd = 80;
    private int fanCd = 90;
    private int pounceCd = 120;
    private int roarCd = 220;
    private int flyCd = 160;
    private int attackTimer;
    private float attackYaw;
    private int flyStage;
    private int flyTimer;
    private int rainCd;
    private int tiredTicks;
    @Nullable
    private UUID diveTarget;

    public Manticore(EntityType<? extends Manticore> type, Level level) {
        super(type, level);
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(320.0, 8.0, 0.31, 13.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    public String bossId() {
        return "manticore";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.3F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("swipe", "sting", "tail_fan", "pounce", "roar", "takeoff", "dive", "rain");
    }

    @Override
    protected double arenaRadius() {
        return 30.0;
    }

    @Override
    protected int rewardXp() {
        return 330;
    }

    public boolean isFlying() {
        return entityData.get(DATA_FLYING);
    }

    public boolean isTired() {
        return entityData.get(DATA_TIRED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_TIRED, false);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        if (isFlying()) return loop("fly");
        if (isTired()) return loop("tired");
        return loop(moving ? "walk" : "idle");
    }

    // ------------------------------------------------------------------ Dano

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && isFrightened(p)) {
            amount *= 0.5F;
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.5, getZ(), 4, 0.4, 0.4, 0.4, 0.01);
        }
        if (isTired()) amount *= 1.3F;
        return super.hurt(source, amount);
    }

    private boolean isFrightened(Player p) {
        Long until = frightened.get(p.getUUID());
        return until != null && until > level().getGameTime();
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || flyStage > 0;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        if (swipeCd > 0) swipeCd--;
        if (stingCd > 0) stingCd--;
        if (fanCd > 0) fanCd--;
        if (pounceCd > 0) pounceCd--;
        if (roarCd > 0) roarCd--;
        if (flyCd > 0) flyCd--;
        if (tickCount % 100 == 0) frightened.values().removeIf(t -> t <= level.getGameTime());

        if (flyStage > 0) {
            tickFlight(level, target);
            return;
        }
        if (tiredTicks > 0) {
            getNavigation().stop();
            if (--tiredTicks == 0) entityData.set(DATA_TIRED, false);
            return;
        }
        if (attackTimer > 0) {
            attackTimer--;
            getNavigation().stop();
            holdYaw(attackYaw);
            return;
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        if (phase() >= 1 && flyCd <= 0) {
            startFlight(level);
        } else if (roarCd <= 0 && d < 9) {
            startRoar(level);
        } else if (d < 4.6 && swipeCd <= 0) {
            startSwipe(level, target);
        } else if (d < 7.5 && stingCd <= 0 && los) {
            startSting(level, target);
        } else if (fanCd <= 0 && d > 4 && d < 18 && los) {
            startFan(level, target);
        } else if (pounceCd <= 0 && d > 6 && d < 16 && los && onGround()) {
            startPounce(level, target);
        } else {
            chase(target, 1.1);
        }
    }

    // ------------------------------------------------------------------ Garra e ferroada

    private void startSwipe(ServerLevel level, LivingEntity target) {
        swipeCd = phase() >= 2 ? 22 : 30;
        attackTimer = 10;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("swipe");
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 110F, 4.4F).warmup(10).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
                        }
                    }
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 1.1F);
                });
    }

    private void startSting(ServerLevel level, LivingEntity target) {
        stingCd = (phase() >= 2 ? 70 : 100) + random.nextInt(30);
        attackTimer = 16;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("sting");
        playSound(SoundEvents.SPIDER_AMBIENT, 1.6F, 0.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(0.8));
        Vec3 to = lineEnd(level, from, dir, 7.0);
        float dmg = scaled(16F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 1.4F).warmup(14).color(SPIKE_COLOR).owner(this)
                .castName(attackName("sting")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
                        }
                    }
                    BgFx.line(level, from.add(0, 1.0, 0), to.add(0, 1.0, 0), BgFx.dust(SPIKE_COLOR, 1.2F), 2.0);
                    playSound(SoundEvents.TRIDENT_HIT, 1.6F, 0.6F);
                });
    }

    // ------------------------------------------------------------------ Leque de espinhos

    private Vec3 tailTip() {
        Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-0.4);
        return position().add(back.x, 2.6, back.z);
    }

    private void startFan(ServerLevel level, LivingEntity target) {
        fanCd = (phase() >= 2 ? 90 : phase() == 1 ? 120 : 150) + random.nextInt(30);
        attackTimer = 24;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("tail_fan");
        announce("fan");
        playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.6F, 0.6F);
        fanWave(level, target, attackYaw, 0F, true);
        if (phase() >= 2) {
            float yaw = attackYaw;
            BossKit.schedule(level, 14, () -> {
                if (isAlive() && flyStage == 0) {
                    action("tail_fan");
                    fanWave(level, target, yaw, 0.5F, false);
                }
            });
        }
    }

    private void fanWave(ServerLevel level, LivingEntity target, float yaw, float offset, boolean named) {
        int n = phase() >= 2 ? 7 : 5;
        float spread = phase() >= 2 ? 100F : 70F;
        Vec3 base = position();
        float dmg = scaled(7F);
        double dy = (target.getY() + target.getBbHeight() * 0.5) - (base.y + 2.6);
        float[] yaws = new float[n];
        for (int i = 0; i < n; i++) yaws[i] = yaw - spread / 2 + spread * ((i + offset) / (n - 1));
        for (int i = 0; i < n; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, yaws[i]);
            Vec3 to = lineEnd(level, base.add(dir.scale(1.0)), dir, 18.0);
            Telegraph.Shape s = Telegraph.Shape.line(base.add(dir.scale(1.0)), to, 1.0F).warmup(20).color(SPIKE_COLOR).owner(this).noRunes();
            if (i == 0) {
                if (named) s.castName(attackName("fan")).element(Element.WIND);
                // O disparo de verdade sai no fim da marca (contrariado = nenhum espinho sai).
                Telegraph.delayed(level, s, hits -> {
                    if (!isAlive()) return;
                    Vec3 tip = tailTip();
                    for (float a : yaws) {
                        Vec3 d = Vec3.directionFromRotation(0, a);
                        double dist = 12.0;
                        Vec3 v = new Vec3(d.x, Math.max(-0.4, Math.min(0.3, dy / dist)), d.z);
                        ManticoreSpike.shoot(level, this, tip.add(d.scale(0.6)), v, 2.2F, dmg, 19.0);
                    }
                    playSound(SoundEvents.ARROW_SHOOT, 2.0F, 0.5F);
                    playSound(SoundEvents.TRIDENT_THROW.value(), 1.6F, 0.8F);
                });
            } else {
                Telegraph.show(level, s);
            }
        }
    }

    // ------------------------------------------------------------------ Bote

    private void startPounce(ServerLevel level, LivingEntity target) {
        pounceCd = (phase() >= 2 ? 120 : 160) + random.nextInt(40);
        attackTimer = 18;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("pounce");
        playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 1.3F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position();
        Vec3 to = lineEnd(level, from, dir, Math.min(18.0, distanceTo(target) + 2.5));
        float dmg = scaled(12F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, getBbWidth() + 1.2F).warmup(16).color(Telegraph.WARNING).owner(this)
                .castName(attackName("pounce")), hits -> {
                    if (!isAlive()) return;
                    leapTicks = BossKit.leapTo(this, to, 1.5);
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.5, 0.5, dir.z * 0.5));
                            e.hurtMarked = true;
                        }
                    }
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.6F, 0.8F);
                });
    }

    // ------------------------------------------------------------------ Rugido Aterrorizante

    private void startRoar(ServerLevel level) {
        roarCd = (phase() >= 2 ? 240 : 340) + random.nextInt(60);
        attackTimer = 26;
        action("roar");
        playSound(SoundEvents.RAVAGER_STUNNED, 1.8F, 0.5F);
        Vec3 c = position();
        Telegraph.delayed(level, Telegraph.Shape.circle(c, 9F).warmup(24).color(FEAR_COLOR).owner(this)
                .castName(attackName("roar")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW), hits -> fearRoar(level, c, hits));
        // v1.3 — Puxa o ar: fumaça escura entrando na juba durante o aviso (o rugido vem quando ela para).
        for (int k = 0; k < 4; k++) {
            int at = 4 + k * 5;
            BossKit.schedule(level, at, () -> {
                if (!isAlive()) return;
                Vec3 head = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.6)).add(0, 1.6, 0);
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = head.add(Math.cos(a) * 3.5, random.nextDouble() * 1.5 - 0.5, Math.sin(a) * 3.5);
                    Vec3 v = head.subtract(from).scale(0.12);
                    level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
                }
            });
        }
    }

    private void fearRoar(ServerLevel level, Vec3 c, List<LivingEntity> hits) {
        if (!isAlive()) return;
        announce("roar");
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 0.6F);
        BossKit.shake(level, c, 24, 1.6F, 20);
        long until = level.getGameTime() + 100;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : away.normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.1, 0.35, away.z * 1.1));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), this);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), this);
            if (e instanceof ServerPlayer sp) {
                frightened.put(sp.getUUID(), until);
                sp.displayClientMessage(Component.translatable(langKey("message", "frightened")).withStyle(ChatFormatting.DARK_RED), true);
            }
            level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        BgFx.ring(level, c.add(0, 0.3, 0), 4.0, 32, BgFx.dust(FEAR_COLOR, 1.6F), 0.0);
        BgFx.ring(level, c.add(0, 0.3, 0), 8.0, 48, BgFx.dust(FEAR_COLOR, 1.6F), 0.0);
        // v1.3 — Onda sônica visível saindo da boca + escuridão pulsando em quem ouviu (o medo "aperta" a tela).
        Vec3 head = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.6)).add(0, 1.6, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, head.x, head.y, head.z, 1, 0, 0, 0, 0);
        for (int k = 1; k <= 3; k++) {
            int ring = k;
            BossKit.schedule(level, k * 2, () -> BgFx.ring(level, c.add(0, 1.0, 0), 2.8 * ring, 6 + ring * 3, ParticleTypes.SONIC_BOOM, 0.0));
        }
        for (LivingEntity e : hits) {
            if (isFoe(e) && e instanceof ServerPlayer sp) sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
        }
    }

    // ------------------------------------------------------------------ Voo Curto

    private void startFlight(ServerLevel level) {
        flyCd = (phase() >= 2 ? 380 : 520) + random.nextInt(80);
        flyStage = 1;
        flyTimer = 16;
        rainCd = 20;
        entityData.set(DATA_FLYING, true);
        setNoGravity(true);
        getNavigation().stop();
        action("takeoff");
        announce("flight");
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 30, 1.5, 0.1, 1.5, 0.08);
    }

    private void tickFlight(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (tickCount % 8 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.1F);
        switch (flyStage) {
            case 1 -> {
                setDeltaMovement(getDeltaMovement().multiply(0.6, 0, 0.6).add(0, 0.45, 0));
                if (--flyTimer <= 0) {
                    flyStage = 2;
                    flyTimer = phase() >= 2 ? 60 : 80;
                }
            }
            case 2 -> {
                if (target != null) {
                    double a = tickCount * 0.05;
                    Vec3 hover = target.position().add(Math.cos(a) * 5.0, 7.0, Math.sin(a) * 5.0);
                    Vec3 v = hover.subtract(position()).scale(0.08);
                    double max = 0.6;
                    if (v.length() > max) v = v.normalize().scale(max);
                    setDeltaMovement(v);
                    faceTowards(target);
                } else {
                    setDeltaMovement(Vec3.ZERO);
                }
                if (--rainCd <= 0) {
                    rainCd = 28;
                    spikeRain(level);
                }
                if (--flyTimer <= 0) startDive(level, target);
            }
            case 3 -> {
                // Desce em direção à presa enquanto a marca trava (o impacto de verdade acontece no fim da marca).
                Entity prey = diveTarget != null ? level.getEntity(diveTarget) : null;
                if (prey != null) {
                    Vec3 v = prey.position().add(0, 2.5, 0).subtract(position()).scale(0.12);
                    if (v.length() > 0.8) v = v.normalize().scale(0.8);
                    setDeltaMovement(v);
                    faceTowards(prey);
                } else {
                    setDeltaMovement(getDeltaMovement().scale(0.5));
                }
                if (--flyTimer <= 0) land(level);
            }
            default -> land(level);
        }
    }

    private void spikeRain(ServerLevel level) {
        action("rain");
        playSound(SoundEvents.ARROW_SHOOT, 1.6F, 0.6F);
        int n = 0;
        boolean named = false;
        float dmg = scaled(8F);
        for (ServerPlayer p : BossKit.partyAround(this, 30)) {
            if (!isFoe(p) || n++ >= 3) continue;
            Telegraph.Shape s = Telegraph.Shape.follow(p, 2.2F).warmup(30).color(SPIKE_COLOR).owner(this);
            if (!named) s.castName(attackName("rain"));
            named = true;
            int[] id = new int[1];
            id[0] = Telegraph.delayed(level, s, hits -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at != null) {
                    for (int k = 0; k < 8; k++) {
                        level.sendParticles(ParticleTypes.CRIT, at.x + (random.nextDouble() - 0.5) * 3, at.y + 3 + random.nextDouble() * 2,
                                at.z + (random.nextDouble() - 0.5) * 3, 0, 0, -1.5, 0, 1.0);
                    }
                    level.sendParticles(BgFx.dust(SPIKE_COLOR, 1.2F), at.x, at.y + 0.2, at.z, 14, 1.0, 0.1, 1.0, 0);
                }
                for (LivingEntity e : hits) {
                    if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                    }
                }
            });
        }
    }

    private void startDive(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            land(level);
            return;
        }
        flyStage = 3;
        flyTimer = 40;
        diveTarget = target.getUUID();
        action("dive");
        announce("dive");
        playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
        float dmg = scaled(14F);
        int[] id = new int[1];
        id[0] = Telegraph.delayed(level, Telegraph.Shape.follow(target, 3.5F).warmup(26).color(Telegraph.DANGER).owner(this)
                .castName(attackName("dive")).tier(SpellStack.Tier.MAJOR), hits -> {
                    if (!isAlive()) return;
                    Vec3 at = Telegraph.centerOf(id[0]);
                    if (at == null) at = target.position();
                    teleportTo(at.x, at.y, at.z);
                    setDeltaMovement(Vec3.ZERO);
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
                            push = push.lengthSqr() < 1.0E-3 ? new Vec3(0.5, 0, 0) : push.normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.0, 0.6, push.z * 1.0));
                            e.hurtMarked = true;
                        }
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 1.0, 0.2, 1.0, 0);
                    BgFx.ring(level, at.add(0, 0.2, 0), 3.0, 30, ParticleTypes.CLOUD, 0.1);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.8F);
                    BossKit.shake(level, at, 22, 1.4F, 14);
                    land(level);
                    tiredTicks = 45;
                    entityData.set(DATA_TIRED, true);
                    hintOnce(level, "tired");
                });
    }

    private void land(ServerLevel level) {
        flyStage = 0;
        flyTimer = 0;
        diveTarget = null;
        setNoGravity(false);
        entityData.set(DATA_FLYING, false);
    }

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        // Inalcançável no chão: levanta voo e mergulha nele (fora da fase 1, só salta).
        if (phase() >= 1 && flyStage == 0) {
            startFlight(sl);
        } else {
            super.onUnreachable(sl, target);
        }
    }

    // ------------------------------------------------------------------ Fases, intro, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.6F);
        BossKit.shake(level, position(), 24, 1.2F, 20);
        if (flyStage == 0) action("roar");
        if (phase >= 1) flyCd = Math.min(flyCd, 60);
        if (phase >= 2) {
            setFrenzy(true);
            roarCd = Math.min(roarCd, 40);
        }
    }

    private void setFrenzy(boolean on) {
        AttributeInstance inst = getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        inst.removeModifier(FRENZY);
        if (on) inst.addPermanentModifier(new AttributeModifier(FRENZY, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.7F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t == 24) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.7F);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 30, 1.5, 0.2, 1.5, 0.06);
        }
        if (t == 40) {
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
            BossKit.shake(level, position(), 24, 1.2F, 18);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        land(level);
        frightened.clear();
        attackTimer = tiredTicks = 0;
        swipeCd = 20;
        stingCd = 80;
        fanCd = 90;
        pounceCd = 120;
        roarCd = 220;
        flyCd = 160;
        entityData.set(DATA_TIRED, false);
        setFrenzy(false);
    }

    @Override
    public void die(DamageSource source) {
        setNoGravity(false);
        entityData.set(DATA_FLYING, false);
        super.die(source);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(BgRegistries.MANTICORE_STINGER.get(), 1, 1);
        dropEssence(Element.FIRE, 2, 4);
        dropEssence(Element.WIND, 2, 4);
        dropArcaneScrolls(2);
        dropById("mana_crystal", 4, 8);
    }

    @Override
    protected void clientTick() {
        if (isFlying() && random.nextInt(BgFx.reduced() ? 6 : 2) == 0) {
            level().addParticle(ParticleTypes.CLOUD, getRandomX(1.2), getY() + 0.3, getRandomZ(1.2), 0, -0.05, 0);
        }
        if (random.nextInt(BgFx.reduced() ? 20 : 8) == 0) {
            Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.4);
            level().addParticle(BgFx.dust(SPIKE_COLOR, 0.8F), getX() + back.x, getY() + 2.7, getZ() + back.z, 0, 0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.15F;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNoGravity(false);
        entityData.set(DATA_FLYING, false);
        if (phase() >= 2) setFrenzy(true);
    }
}
