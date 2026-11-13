package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Basilisco do Deserto (covil {@code basilisk_tomb}). Serpente-lagarto coroada de seis patas.
 * <ul>
 *   <li><b>Olhar Petrificante</b>: ergue o corpo, os olhos acendem e um cone cinza marca o chão. Quem está no cone
 *       E olhando para ele quando o olhar acontece ganha Petrificação (status do núcleo; fase 2+: pedra na hora).
 *       <b>Olhar para longe</b> (virar a câmera) ou ficar atrás de uma parede evita;</li>
 *   <li><b>Mordida</b> (enrijece), <b>Chicote de Cauda</b> atrás (estilhaça quem está petrificado: dano ×1,5);</li>
 *   <li><b>Escavar e Emergir</b>: some na areia (invulnerável), o rastro de areia anda até um jogador e uma marca o
 *       segue e trava — sai dali com tudo. Fase 2+: emende um olhar rápido logo depois;</li>
 *   <li>fase 2: <b>Areia Movediça</b> (poças que prendem); fase 3: <b>Olhar Total</b> (círculo em volta dele: vire de costas).</li>
 * </ul>
 */
public class DesertBasilisk extends OverworldBoss {
    private static final int COLOR = 0xD8B060;
    private static final int STONE = 0xB8B2A0;
    private static final int SAND = 0xD9B26A;
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(DesertBasilisk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GAZING = SynchedEntityData.defineId(DesertBasilisk.class, EntityDataSerializers.BOOLEAN);
    private static final int ST_NORMAL = 0, ST_BURROW = 1, ST_HIDDEN = 2, ST_EMERGE = 3;
    private static final int GAZE_WARMUP = 30;
    private static final int GAZE_ALL_WARMUP = 40;
    private static final int BURROW_TICKS = 20;
    private static final int TUNNEL_WARMUP = 38;
    private static final int EMERGE_TICKS = 18;
    /** Cos do ângulo em que o jogador "está olhando" para o Basilisco (~60°). O aviso do cliente usa o mesmo. */
    public static final double LOOK_DOT = 0.5;

    private int gazeCd = 80, gazeAllCd = 0, biteCd = 0, tailCd = 30, burrowCd = 160, sandCd = 120;
    private int attackTimer;
    private float attackYaw;
    private int stateTimer;
    @Nullable
    private Vec3 tunnel;
    @Nullable
    private Vec3 emergeAt;
    private boolean comboGaze;
    private boolean gazeAllDone;

    public DesertBasilisk(EntityType<? extends DesertBasilisk> type, Level level) {
        super(type, level);
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(340.0, 12.0, 0.27, 13.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.8);
    }

    @Override
    public String bossId() {
        return "desert_basilisk";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.66F, 0.33F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("roar", "bite", "tail", "gaze", "gaze_all", "quicksand");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, ST_NORMAL);
        builder.define(DATA_GAZING, false);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public boolean isGazing() {
        return entityData.get(DATA_GAZING);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("eye_glow");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return isGazing();
    }

    @Override
    public boolean isPickable() {
        return (state() == ST_NORMAL || state() == ST_EMERGE) && super.isPickable();
    }

    @Override
    protected float modifyIncomingDamage(@Nullable BossPart part, DamageSource source, float amount) {
        return state() == ST_NORMAL || state() == ST_EMERGE ? amount : 0F;
    }

    /** A própria Petrificação não pega nele (o olhar é dele). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(Statuses.effect(Statuses.Kind.PETRIFIED)) && super.canBeAffected(effect);
    }

    /** Olhos (para as linhas do olhar e o teste de visão). */
    public Vec3 eyes() {
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(fwd.scale(2.6)).add(0, 1.3, 0);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || state() != ST_NORMAL;
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        gazeCd--;
        gazeAllCd--;
        biteCd--;
        tailCd--;
        burrowCd--;
        sandCd--;
        if (state() != ST_NORMAL) {
            tickBurrow(sl, target);
            return;
        }
        if (attackTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--attackTimer == 0) entityData.set(DATA_GAZING, false);
            if (isGazing() && attackTimer % 3 == 0) {
                Vec3 e = eyes();
                sl.sendParticles(BfFx.dust(0xD8FF5A, 1.2F), e.x, e.y, e.z, 4, 0.4, 0.15, 0.4, 0);
            }
            return;
        }
        if (target == null) return;
        int ph = phase();
        double dist = distanceTo(target);
        float ang = Math.abs(angleTo(target.position()));
        if (comboGaze) {
            comboGaze = false;
            startGaze(sl, target, 22);
            return;
        }
        if (ph >= 2 && (!gazeAllDone || gazeAllCd <= 0) && dist < 16) {
            startGazeAll(sl);
            return;
        }
        if (tailCd <= 0 && dist < 7.5 && ang > 100F) {
            startTail(sl);
            return;
        }
        if (biteCd <= 0 && dist < 4.8 && ang < 45F) {
            startBite(sl, target);
            return;
        }
        if (gazeCd <= 0 && dist < 17 && hasLineOfSight(target)) {
            startGaze(sl, target, GAZE_WARMUP);
            return;
        }
        if (burrowCd <= 0 && dist > 6) {
            startBurrow(sl);
            return;
        }
        if (ph >= 1 && sandCd <= 0) {
            startQuicksand(sl);
            return;
        }
        chase(target, 1.0);
    }

    // ------------------------------------------------------------------ Olhar Petrificante

    private void startGaze(ServerLevel sl, LivingEntity target, int warmup) {
        int ph = phase();
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        attackTimer = warmup + 10;
        gazeCd = ph >= 2 ? 120 : ph >= 1 ? 150 : 190;
        entityData.set(DATA_GAZING, true);
        action("gaze");
        announce("gaze");
        hintOnce(sl, "gaze");
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.6F, 1.4F);
        float angle = ph >= 1 ? 100F : 80F;
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), attackYaw, angle, 18F).warmup(warmup).color(STONE).owner(this).castName(attackName("gaze")),
                hits -> resolveGaze(sl, hits));
    }

    private void startGazeAll(ServerLevel sl) {
        gazeAllDone = true;
        gazeAllCd = 420;
        attackYaw = yBodyRot;
        getNavigation().stop();
        attackTimer = GAZE_ALL_WARMUP + 12;
        entityData.set(DATA_GAZING, true);
        action("gaze_all");
        announce("gaze_all");
        hintOnce(sl, "gaze_all");
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 2.2F, 0.9F);
        Telegraph.delayed(sl, Telegraph.Shape.circle(position(), 16F).warmup(GAZE_ALL_WARMUP).color(STONE).owner(this).castName(attackName("gaze_all")),
                hits -> resolveGaze(sl, hits));
    }

    /** Fim do olhar: petrifica só quem está olhando para ele (e o vê). Mobs no caminho enrijecem. */
    private void resolveGaze(ServerLevel sl, List<LivingEntity> hits) {
        if (!isAlive()) return;
        Vec3 eye = eyes();
        int stacks = phase() >= 1 ? 3 : 2;
        float dmg = scaled(4.0F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (!(e instanceof Player p)) {
                Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 80, this, 1F);
                continue;
            }
            boolean sees = sl.clip(new ClipContext(p.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                    .getType() == HitResult.Type.MISS;
            Vec3 to = eye.subtract(p.getEyePosition()).normalize();
            boolean looking = p.getViewVector(1.0F).dot(to) > LOOK_DOT;
            if (sees && looking) {
                Statuses.apply(p, Statuses.Kind.PETRIFIED, stacks, 120, this, 1F);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2), this);
                p.hurt(damageSources().indirectMagic(this, this), dmg);
                BfFx.line(sl, eye, p.getEyePosition(), BfFx.dust(STONE, 1.3F), 3.0);
                if (p instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(langKey("message", "stared")).withStyle(ChatFormatting.GRAY), true);
                }
            } else if (p instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable(langKey("message", "evaded")).withStyle(ChatFormatting.GREEN), true);
            }
        }
        sl.sendParticles(BfFx.dust(0xD8FF5A, 2.0F), eye.x, eye.y, eye.z, 30, 0.5, 0.3, 0.5, 0);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.6F);
    }

    // ------------------------------------------------------------------ Mordida e cauda

    private void startBite(ServerLevel sl, LivingEntity target) {
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        attackTimer = 14;
        biteCd = phase() >= 1 ? 22 : 30;
        action("bite");
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), attackYaw, 70F, 4.8F).warmup(10).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (doHurtTarget(e)) Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 100, this, 1F);
                    }
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.4F, 0.6F);
                });
    }

    private void startTail(ServerLevel sl) {
        attackYaw = yBodyRot;
        getNavigation().stop();
        attackTimer = 18;
        tailCd = phase() >= 1 ? 55 : 75;
        action("tail");
        float dmg = attackDamage() * 1.1F;
        Vec3 c = position();
        Telegraph.delayed(sl, Telegraph.Shape.cone(c, attackYaw + 180F, 180F, 7.5F).warmup(14).color(Telegraph.WARNING).owner(this).castName(attackName("tail")),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        boolean stone = Statuses.stacks(e, Statuses.Kind.PETRIFIED) >= 3 || Statuses.incapacitated(e);
                        if (e.hurt(damageSources().mobAttack(this), stone ? dmg * 1.5F : dmg)) {
                            Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.4, 0.45, push.z * 1.4));
                            e.hurtMarked = true;
                            if (stone) {
                                sl.sendParticles(BfFx.dust(STONE, 1.6F), e.getX(), e.getY() + 1, e.getZ(), 25, 0.4, 0.6, 0.4, 0);
                                playSound(SoundEvents.STONE_BREAK, 2.0F, 0.6F);
                            }
                        }
                    }
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
                });
    }

    // ------------------------------------------------------------------ Areia Movediça

    private void startQuicksand(ServerLevel sl) {
        sandCd = phase() >= 2 ? 240 : 300;
        attackTimer = 22;
        attackYaw = yBodyRot;
        action("quicksand");
        announce("quicksand");
        playSound(SoundEvents.SAND_BREAK, 2.5F, 0.5F);
        int n = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 28)) {
            if (n++ >= 3) break;
            Vec3 at = groundAt(sl, p.position(), 6);
            Telegraph.show(sl, Telegraph.Shape.circle(at, 3.0F).warmup(20).hold(100).color(SAND).owner(this));
            for (int t = 20; t <= 120; t += 10) {
                BossKit.schedule(sl, t, () -> {
                    if (!isAlive()) return;
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BfFx.ground(sl, at)), at.x, at.y + 0.1, at.z, 10, 1.2, 0.05, 1.2, 0.05);
                    for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(at, 3.0F))) {
                        if (!isFoe(e)) continue;
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 3), this);
                        e.hurt(damageSources().mobAttack(this), 1.0F);
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------ Escavar e Emergir

    private void startBurrow(ServerLevel sl) {
        burrowCd = phase() >= 1 ? 220 : 300;
        entityData.set(DATA_STATE, ST_BURROW);
        stateTimer = BURROW_TICKS;
        getNavigation().stop();
        announce("burrow");
        playSound(SoundEvents.SAND_BREAK, 3.0F, 0.4F);
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
    }

    private void tickBurrow(ServerLevel sl, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        stateTimer--;
        switch (state()) {
            case ST_BURROW -> {
                if (tickCount % 2 == 0) BfFx.groundBurst(sl, position(), 2.5, 20);
                if (stateTimer <= 0) {
                    entityData.set(DATA_STATE, ST_HIDDEN);
                    stateTimer = TUNNEL_WARMUP + 2;
                    tunnel = position();
                    emergeAt = null;
                    LivingEntity t = target != null ? target : sl.getNearestPlayer(this, 30);
                    if (t == null) return;
                    int[] id = new int[1];
                    id[0] = Telegraph.delayed(sl, Telegraph.Shape.follow(t, 3.2F).warmup(TUNNEL_WARMUP).lock(12).color(SAND).owner(this).castName(attackName("burrow")),
                            hits -> {
                                Vec3 at = Telegraph.centerOf(id[0]);
                                emergeAt = at;
                                emerge(sl, hits);
                            });
                }
            }
            case ST_HIDDEN -> {
                // O rastro de areia corre embaixo da terra até o alvo.
                if (tunnel != null && target != null) {
                    Vec3 d = target.position().subtract(tunnel).multiply(1, 0, 1);
                    if (d.length() > 0.6) tunnel = tunnel.add(d.normalize().scale(0.55));
                    Vec3 g = groundAt(sl, tunnel.add(0, 2, 0), 6);
                    tunnel = new Vec3(tunnel.x, g.y, tunnel.z);
                    if (tickCount % 2 == 0) {
                        BfFx.groundBurst(sl, tunnel, 1.4, 12);
                        if (tickCount % 6 == 0) {
                            sl.playSound(null, tunnel.x, tunnel.y, tunnel.z, SoundEvents.SAND_BREAK, getSoundSource(), 1.2F, 0.5F);
                        }
                    }
                }
                if (stateTimer <= 0 && emergeAt == null) emerge(sl, List.of());
            }
            case ST_EMERGE -> {
                if (stateTimer <= 0) entityData.set(DATA_STATE, ST_NORMAL);
            }
            default -> entityData.set(DATA_STATE, ST_NORMAL);
        }
    }

    private void emerge(ServerLevel sl, List<LivingEntity> hits) {
        if (!isAlive() || state() != ST_HIDDEN) return;
        Vec3 at = emergeAt != null ? groundAt(sl, emergeAt, 6) : (tunnel != null ? tunnel : position());
        if (target() != null) attackYaw = yawTo(target().position());
        teleportTo(at.x, at.y, at.z);
        holdYaw(attackYaw);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_STATE, ST_EMERGE);
        stateTimer = EMERGE_TICKS;
        float dmg = scaled(12.0F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                e.hurtMarked = true;
                Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 100, this, 1F);
            }
        }
        BfFx.groundBurst(sl, at, 4.0, 90);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.SAND_BREAK, 3.0F, 0.4F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 1.1F);
        BossKit.shake(sl, at, 24, 1.3F, 14);
        tunnel = null;
        emergeAt = null;
        if (phase() >= 1) comboGaze = true;
    }

    @Nullable
    private LivingEntity target() {
        return getTarget();
    }

    // ------------------------------------------------------------------ Fases, intro, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 1.0F);
        BossKit.shake(level, position(), 26, 1.2F, 18);
        BfFx.groundBurst(level, position(), 6.0, 80);
        sandCd = Math.min(sandCd, 60);
        gazeCd = Math.min(gazeCd, 60);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.SAND_BREAK, 3.0F, 0.4F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 1.1F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 3 == 0 && t < 30) BfFx.groundBurst(level, position(), 3.0, 25);
        if (t == 30) {
            BossKit.shake(level, position(), 26, 1.2F, 16);
            Vec3 e = eyes();
            level.sendParticles(BfFx.dust(0xD8FF5A, 2.0F), e.x, e.y, e.z, 30, 0.6, 0.3, 0.6, 0);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        entityData.set(DATA_STATE, ST_NORMAL);
        entityData.set(DATA_GAZING, false);
        attackTimer = 0;
        gazeCd = 80;
        gazeAllCd = 0;
        gazeAllDone = false;
        biteCd = 0;
        tailCd = 30;
        burrowCd = 160;
        sandCd = 120;
        comboGaze = false;
        tunnel = null;
        emergeAt = null;
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0) {
            switch (state()) {
                case ST_BURROW -> {
                    return hold("burrow");
                }
                case ST_HIDDEN -> {
                    return loop("hidden");
                }
                case ST_EMERGE -> {
                    return play("emerge");
                }
                default -> {
                }
            }
        }
        return super.mainAnimation(moving);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        int n = trophyCount();
        dropItem(BfRegistry.BASILISK_EYE.get(), n, n);
        dropItem(BfRegistry.BASILISK_SCALE.get(), 2 + n, 4 + n);
        dropEssence(Element.EARTH, 3, 5);
        dropEssence(Element.WIND, 1, 2);
        dropArcaneScrolls(2);
    }

    @Override
    protected void clientTick() {
        if (state() != ST_NORMAL) return;
        int every = BfFx.reduced() ? 10 : 4;
        if (random.nextInt(every) == 0) {
            float yaw = (float) Math.toRadians(yBodyRot);
            double fx = -Mth.sin(yaw) * 2.6, fz = Mth.cos(yaw) * 2.6;
            level().addParticle(BfFx.dust(isGazing() ? 0xF8FFC0 : 0xD8FF5A, isGazing() ? 1.4F : 0.7F), getX() + fx, getY() + 1.3, getZ() + fz, 0, 0.01, 0);
        }
        if (random.nextInt(every * 2) == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()),
                    getRandomX(1.0), getY() + 1.6, getRandomZ(1.0), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SNIFFER_IDLE;
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
        return 0.8F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("GazeAllDone", gazeAllDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        gazeAllDone = tag.getBoolean("GazeAllDone");
    }
}
