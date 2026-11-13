package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Colosso das Ruínas (covil {@code colossus_ruins}). Golem de alvenaria de ~8 blocos, lento e devastador.
 * <ul>
 *   <li><b>Armadura de pedra</b>: com o peito fechado o corpo recebe 35% e o núcleo 60%. O <b>núcleo</b> (caixa de acerto
 *       própria no peito) é o ponto fraco: aberto, recebe 250%;</li>
 *   <li><b>Pisão</b>: marca embaixo do pé, cratera de estilhaços (só visual: a luta não destrói o cenário) e onda de choque em anel (pule!);</li>
 *   <li><b>Arremesso de Pedra</b>: arranca rocha do chão e arremessa em arco até a marca (anti-trapaça também);</li>
 *   <li><b>Punho Cravado</b>: faixa à frente, estilhaça o chão — o punho prende e ele AJOELHA: o núcleo fica exposto
 *       e ao alcance do corpo a corpo por 5 s;</li>
 *   <li>fase 2: <b>Varredura Baixa</b> (anel: pule) e <b>Chuva de Pedras</b> (depois dela o núcleo superaquece e abre);</li>
 *   <li>fase 3: o núcleo racha e fica entreaberto (160% / corpo 60%), golpes mais rápidos e duas rochas por arremesso.</li>
 * </ul>
 */
public class RuinColossus extends OverworldBoss {
    private static final int COLOR = 0x3FE0D0;
    private static final int EARTH = 0xA0743C;
    public static final float SCALE = 2.0F;
    private static final EntityDataAccessor<Boolean> DATA_KNEEL = SynchedEntityData.defineId(RuinColossus.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CORE = SynchedEntityData.defineId(RuinColossus.class, EntityDataSerializers.INT);
    private static final int CORE_CLOSED = 0, CORE_OPEN = 1, CORE_CRACKED = 2;
    /** Núcleo em pé e ajoelhado (px no espaço Java do modelo; saída de tools/gen/bosses_f.py + pose "kneel"). */
    private static final double[] CORE_STAND = {0.0, 39.0, -12.0};
    private static final double[] CORE_KNEEL = {0.0, 21.0, -15.5};

    private static final int STOMP_WARMUP = 22;
    private static final int THROW_GRAB = 12;
    private static final int THROW_RELEASE = 24;
    private static final int FIST_WARMUP = 26;
    private static final int SWEEP_WARMUP = 24;
    private static final int KNEEL_TICKS = 100;
    private static final int VENT_TICKS = 70;
    /** v1.3 — Terremoto: braços sobem (aviso longo), os dois punhos descem e 4 ondas saem em sequência. */
    private static final int QUAKE_WARMUP = 32;
    private static final int QUAKE_WAVES = 4;

    private int stompCd = 30, throwCd = 60, fistCd = 140, sweepCd = 100, rainCd = 160, quakeCd = 200;
    private int attackTimer;
    private int throwTimer;
    private float attackYaw;
    private int kneelTimer;
    private int ventTimer;
    /** Interpolação do núcleo (cliente e servidor): 0 em pé, 1 ajoelhado. */
    private float kneelProgress;

    public RuinColossus(EntityType<? extends RuinColossus> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        initParts(new BossPart[] {new BossPart(this, 0, 1.5F, 1.5F)});
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(480.0, 14.0, 0.18, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.STEP_HEIGHT, 2.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0);
    }

    @Override
    public String bossId() {
        return "ruin_colossus";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.WHITE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.3F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("roar", "stomp_l", "stomp_r", "throw", "fist", "sweep", "rain", "quake");
    }

    @Override
    protected int deathTicks() {
        return 84; // v1.0: 3,5 s de animação + o corpo se desfazendo
    }

    @Override
    protected int rewardXp() {
        return 360;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KNEEL, false);
        builder.define(DATA_CORE, CORE_CLOSED);
    }

    public boolean isKneeling() {
        return entityData.get(DATA_KNEEL);
    }

    public int coreState() {
        return entityData.get(DATA_CORE);
    }

    private void updateCore() {
        int want = (kneelTimer > 0 || ventTimer > 0) ? CORE_OPEN : phase() >= 2 ? CORE_CRACKED : CORE_CLOSED;
        if (coreState() != want) entityData.set(DATA_CORE, want);
        boolean kneel = kneelTimer > 0;
        if (isKneeling() != kneel) entityData.set(DATA_KNEEL, kneel);
    }

    // ------------------------------------------------------------------ Núcleo (parte) e armadura

    @Override
    protected void positionParts() {
        kneelProgress = Mth.approach(kneelProgress, isKneeling() ? 1F : 0F, 0.08F);
        double y = Mth.lerp(kneelProgress, CORE_STAND[1], CORE_KNEEL[1]);
        double z = Mth.lerp(kneelProgress, CORE_STAND[2], CORE_KNEEL[2]);
        Vec3 p = modelPoint(0, y, z, SCALE);
        bossParts()[0].moveTo(p.x, p.y - 0.75, p.z);
    }

    private Vec3 corePos() {
        BossPart c = bossParts()[0];
        return new Vec3(c.getX(), c.getY() + 0.75, c.getZ());
    }

    @Override
    protected float modifyIncomingDamage(@Nullable BossPart part, DamageSource source, float amount) {
        int core = coreState();
        if (part != null) {
            float m = core == CORE_OPEN ? 2.5F : core == CORE_CRACKED ? 1.6F : 0.6F;
            if (core != CORE_CLOSED && level() instanceof ServerLevel sl) {
                Vec3 c = corePos();
                sl.sendParticles(BfFx.dust(COLOR, 1.6F), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0);
                sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.3);
                playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.5F, 0.6F);
            }
            return amount * m;
        }
        float m = core == CORE_OPEN ? 1.0F : core == CORE_CRACKED ? 0.6F : 0.35F;
        if (core == CORE_CLOSED && level() instanceof ServerLevel sl && random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 6, 1.0, 1.5, 1.0, 0.2);
            playSound(SoundEvents.STONE_HIT, 1.2F, 0.5F);
            hintOnce(sl, "armor");
        }
        return amount * m;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || throwTimer > 0 || kneelTimer > 0;
    }

    @Override
    protected void tickWhileHomeward(ServerLevel sl, @Nullable LivingEntity target) {
        updateCore();
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        int ph = phase();
        stompCd--;
        throwCd--;
        fistCd--;
        sweepCd--;
        rainCd--;
        quakeCd--;
        if (ventTimer > 0) {
            ventTimer--;
            if (ventTimer % 4 == 0) {
                Vec3 c = corePos();
                sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
        updateCore();
        if (kneelTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (kneelTimer % 6 == 0) {
                Vec3 c = corePos();
                sl.sendParticles(BfFx.dust(COLOR, 1.4F), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0);
            }
            if (--kneelTimer == 0) {
                updateCore();
                playSound(SoundEvents.IRON_GOLEM_REPAIR, 2.0F, 0.5F);
                BossKit.shake(sl, position(), 18, 0.7F, 10);
            }
            return;
        }
        if (throwTimer > 0) {
            getNavigation().stop();
            if (target != null) attackYaw = Mth.approachDegrees(attackYaw, yawTo(target.position()), 4F);
            holdYaw(attackYaw);
            throwTimer--;
            if (throwTimer == (THROW_RELEASE + 6) - THROW_GRAB) ripRock(sl);
            if (throwTimer == 6 && target != null) releaseRocks(sl, target);
            return;
        }
        if (attackTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            attackTimer--;
            return;
        }
        if (target == null) return;
        double dist = distanceTo(target);
        if (ph >= 1 && rainCd <= 0) {
            startRain(sl);
            return;
        }
        if (ph >= 1 && quakeCd <= 0 && dist < 16) {
            startQuake(sl);
            return;
        }
        if (ph >= 1 && sweepCd <= 0 && dist < 8.5) {
            startSweep(sl);
            return;
        }
        if (fistCd <= 0 && dist < 12 && hasLineOfSight(target)) {
            startFist(sl, target);
            return;
        }
        if (stompCd <= 0 && dist < 7.5) {
            startStomp(sl, target);
            return;
        }
        if (throwCd <= 0 && dist > 9) {
            startThrow(sl, target);
            return;
        }
        chase(target, 1.0);
    }

    // ------------------------------------------------------------------ Pisão

    private void startStomp(ServerLevel sl, LivingEntity target) {
        int ph = phase();
        boolean right = angleTo(target.position()) > 0;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        attackTimer = STOMP_WARMUP + 8;
        stompCd = ph >= 2 ? 34 : ph >= 1 ? 42 : 50;
        action(right ? "stomp_r" : "stomp_l");
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.0F, 0.4F);
        // Pé que pisa: na frente, do lado do alvo (Java do modelo: +X = direita).
        Vec3 foot = groundAt(sl, modelPoint(right ? 7 : -7, 0, -12, SCALE).add(0, 1, 0), 6);
        float dmg = attackDamage();
        Telegraph.delayed(sl, Telegraph.Shape.circle(foot, 4.2F).warmup(STOMP_WARMUP).color(Telegraph.DANGER).owner(this).castName(attackName("stomp")), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                    e.hurtMarked = true;
                }
            }
            BfFx.impactCrater(sl, foot, 2.6);
            BfFx.groundBurst(sl, foot, 4.0, 60);
            sl.sendParticles(ParticleTypes.EXPLOSION, foot.x, foot.y + 0.4, foot.z, 2, 0.8, 0.1, 0.8, 0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
            BossKit.shake(sl, foot, 30, 1.6F, 16);
            shockwave(sl, foot);
        });
    }

    /** Onda de choque depois do pisão: anel que dá para pular. */
    private void shockwave(ServerLevel sl, Vec3 c) {
        float dmg = scaled(7.0F);
        Telegraph.delayed(sl, Telegraph.Shape.ring(c, 4.2F, 9.5F).warmup(10).vertical(0.5, 1.0).color(EARTH).owner(this).noRunes()
                .castName(attackName("shockwave")), hits -> {
            if (!isAlive()) return;
            BfFx.ring(sl, c.add(0, 0.2, 0), 7.0, 40, ParticleTypes.CLOUD, 0.05);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                    push = push.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : push.normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.1, 0.35, push.z * 1.1));
                    e.hurtMarked = true;
                }
            }
        });
        hintOnce(sl, "jump");
    }

    // ------------------------------------------------------------------ Arremesso de Pedra

    private void startThrow(ServerLevel sl, LivingEntity target) {
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        throwTimer = THROW_RELEASE + 6;
        throwCd = phase() >= 2 ? 80 : 110;
        action("throw");
        playSound(SoundEvents.IRON_GOLEM_HURT, 2.0F, 0.4F);
    }

    /** Arranca a rocha do chão do lado dele (estilhaços; nenhum bloco some). */
    private void ripRock(ServerLevel sl) {
        Vec3 side = groundAt(sl, modelPoint(-14, 0, -4, SCALE).add(0, 1, 0), 6);
        BfFx.impactCrater(sl, side, 1.6);
        BfFx.groundBurst(sl, side, 2.0, 40);
        playSound(SoundEvents.STONE_BREAK, 2.5F, 0.4F);
    }

    private void releaseRocks(ServerLevel sl, LivingEntity target) {
        int rocks = phase() >= 2 ? 2 : 1;
        Vec3 hand = modelPoint(0, 64, -4, SCALE);
        float dmg = scaled(14.0F);
        for (int k = 0; k < rocks; k++) {
            LivingEntity t = target;
            if (k > 0) {
                List<ServerPlayer> party = BossKit.partyAround(this, 32);
                if (party.size() > 1) t = party.get(random.nextInt(party.size()));
            }
            Vec3 aim = t.position().add(t.getDeltaMovement().multiply(12, 0, 12));
            if (k > 0 && t == target) aim = aim.add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
            Vec3 land = groundAt(sl, aim.add(0, 2, 0), 10);
            int ticks = ColossusBoulder.throwAt(this, hand, land, 1.6F, dmg, 3.2F, 2.2F);
            Telegraph.show(sl, Telegraph.Shape.circle(land, 3.2F).warmup(ticks).color(EARTH).owner(this));
        }
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.5F, 0.5F);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
    }

    // ------------------------------------------------------------------ Punho Cravado (expõe o núcleo)

    private void startFist(ServerLevel sl, LivingEntity target) {
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        attackTimer = FIST_WARMUP + 2;
        fistCd = phase() >= 2 ? 200 : 260;
        action("fist");
        announce("fist");
        playSound(SoundEvents.IRON_GOLEM_HURT, 2.5F, 0.3F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(2.0));
        Vec3 to = lineEnd(sl, from, dir, 13.0);
        float dmg = attackDamage() * 0.9F;
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 3.4F).warmup(FIST_WARMUP).color(Telegraph.DANGER).owner(this).castName(attackName("fist")), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.75, 0));
                    e.hurtMarked = true;
                }
            }
            // Vala só visual: estilhaços ao longo da faixa (v1.0 — a arena não é destruída).
            for (int i = 0; i <= 4; i++) BfFx.impactCrater(sl, from.lerp(to, i / 4.0), 1.2);
            BfFx.line(sl, from.add(0, 0.3, 0), to.add(0, 0.3, 0), ParticleTypes.EXPLOSION, 0.5);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.4F);
            BossKit.shake(sl, position(), 32, 1.8F, 18);
            // O punho fica preso: ajoelha e o núcleo se abre.
            kneelTimer = KNEEL_TICKS;
            updateCore();
            announce("stuck");
            hintOnce(sl, "core");
            playSound(SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
        });
    }

    // ------------------------------------------------------------------ Varredura Baixa

    private void startSweep(ServerLevel sl) {
        attackYaw = yBodyRot;
        getNavigation().stop();
        attackTimer = SWEEP_WARMUP + 8;
        sweepCd = phase() >= 2 ? 150 : 200;
        action("sweep");
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.5F, 0.3F);
        Vec3 c = position();
        float dmg = attackDamage() * 0.8F;
        Telegraph.delayed(sl, Telegraph.Shape.ring(c, 1.5F, 8.5F).warmup(SWEEP_WARMUP).vertical(0.5, 1.2).color(Telegraph.WARNING).owner(this)
                        .castName(attackName("sweep")),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (e.hurt(damageSources().mobAttack(this), dmg)) {
                            Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                            push = push.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : push.normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.6, 0.4, push.z * 1.6));
                            e.hurtMarked = true;
                        }
                    }
                    BfFx.ring(sl, c.add(0, 0.5, 0), 6.0, 36, ParticleTypes.SWEEP_ATTACK, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.5F, 0.3F);
                });
        hintOnce(sl, "jump");
    }

    // ------------------------------------------------------------------ Terremoto (v1.3)

    /**
     * Ergue os dois braços (32 ticks: dá tempo de ler), afunda os punhos no chão e solta {@link #QUAKE_WAVES} ondas de
     * choque em anéis cada vez mais largos, uma atrás da outra — cada uma rasteira (dá para PULAR). O tremor solta
     * entulho do alto sobre quem estiver longe (marcas marrons).
     */
    private void startQuake(ServerLevel sl) {
        int ph = phase();
        attackYaw = yBodyRot;
        getNavigation().stop();
        attackTimer = QUAKE_WARMUP + QUAKE_WAVES * 9 + 6;
        quakeCd = ph >= 2 ? 240 : 320;
        action("quake");
        announce("quake");
        hintOnce(sl, "quake");
        BossKit.roar(this, SoundEvents.IRON_GOLEM_DEATH, 0.3F);
        Vec3 c = groundAt(sl, position().add(0, 1, 0), 6);
        float dmgSlam = attackDamage() * 0.9F;
        float dmgWave = scaled(7.5F);
        // 1) os punhos: círculo no meio (quem está colado nele leva o golpe cheio)
        Telegraph.delayed(sl, Telegraph.Shape.circle(c, 4.0F).warmup(QUAKE_WARMUP).color(Telegraph.DANGER).owner(this)
                .castName(attackName("quake")), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmgSlam)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
            }
            BfFx.impactCrater(sl, c, 3.2);
            BfFx.groundBurst(sl, c, 5.0, 90);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.35F);
            BossKit.shake(sl, c, 40, 2.2F, 26);
            quakeRubble(sl, c);
        });
        // 2) as ondas: anéis de 3 blocos de largura que se afastam (cada um some antes do próximo chegar)
        for (int k = 0; k < QUAKE_WAVES; k++) {
            float inner = 4.0F + k * 3.5F;
            int warm = QUAKE_WARMUP + 6 + k * 9;
            final int wave = k;
            Telegraph.delayed(sl, Telegraph.Shape.ring(c, inner, inner + 3.0F).warmup(warm).vertical(0.5, 1.0).color(EARTH).owner(this)
                    .noRunes(), hits -> {
                if (!isAlive()) return;
                BfFx.ring(sl, c.add(0, 0.25, 0), inner + 1.5, 28 + wave * 8, ParticleTypes.CLOUD, 0.04);
                BfFx.ring(sl, c.add(0, 0.15, 0), inner + 1.5, 20 + wave * 6, BfFx.dust(EARTH, 2.2F), 0);
                playSound(SoundEvents.STONE_BREAK, 2.0F, 0.5F + wave * 0.08F);
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    if (e.hurt(damageSources().mobAttack(this), dmgWave)) {
                        Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                        push = push.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : push.normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(push.x * 0.9, 0.45, push.z * 0.9));
                        e.hurtMarked = true;
                    }
                }
            });
        }
        hintOnce(sl, "jump");
    }

    /** Entulho caindo do alto depois do terremoto: uma pedra perto de cada jogador (até 4), marca marrom antes. */
    private void quakeRubble(ServerLevel sl, Vec3 c) {
        float dmg = scaled(8.0F);
        int n = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 36)) {
            if (n++ >= 4) break;
            double a = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * 3.0;
            Vec3 at = groundAt(sl, p.position().add(Math.cos(a) * r, 2, Math.sin(a) * r), 8);
            int warm = 34 + random.nextInt(10);
            Telegraph.show(sl, Telegraph.Shape.circle(at, 2.2F).warmup(warm).color(EARTH).owner(this).noRunes());
            BossKit.schedule(sl, warm - 20, () -> {
                if (!isAlive()) return;
                ColossusBoulder.dropFrom(this, at.add(0, 22, 0), 1.1, 0.8F, dmg, 2.2F, 0F);
            });
            // poeira escorrendo do alto: olhe para cima
            sl.sendParticles(BfFx.dust(EARTH, 1.5F), at.x, at.y + 14, at.z, 12, 1.2, 1.5, 1.2, 0);
        }
    }

    // ------------------------------------------------------------------ Chuva de Pedras

    private void startRain(ServerLevel sl) {
        int ph = phase();
        rainCd = ph >= 2 ? 300 : 380;
        attackYaw = yBodyRot;
        getNavigation().stop();
        attackTimer = 34;
        action("rain");
        announce("rain");
        BossKit.roar(this, SoundEvents.IRON_GOLEM_DEATH, 0.4F);
        float dmg = scaled(10.0F);
        int n = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 36)) {
            if (n++ >= 5) break;
            for (int k = 0; k < 4; k++) {
                Vec3 spot;
                if (k == 0) {
                    spot = groundAt(sl, p.position(), 6);
                } else {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = 2.5 + random.nextDouble() * 5.0;
                    spot = groundAt(sl, p.position().add(Math.cos(a) * r, 2, Math.sin(a) * r), 8);
                }
                int warm = 40 + 12 * k + random.nextInt(6);
                final Vec3 at = spot;
                final float crater = k == 0 ? 1.4F : 0F;
                Telegraph.show(sl, Telegraph.Shape.circle(at, 2.4F).warmup(warm).color(EARTH).owner(this).noRunes());
                // A rocha cai reto 22 blocos a 1,1/tick = 20 ticks: sai 20 ticks antes do fim da marca.
                BossKit.schedule(sl, warm - 20, () -> {
                    if (!isAlive()) return;
                    ColossusBoulder.dropFrom(this, at.add(0, 22, 0), 1.1, 1.0F, dmg, 2.4F, crater);
                });
            }
        }
        // Depois da chuva o núcleo superaquece e abre.
        BossKit.schedule(sl, 90, () -> {
            if (!isAlive()) return;
            ventTimer = VENT_TICKS;
            updateCore();
            announce("vent");
            hintOnce(sl, "vent");
            playSound(SoundEvents.LAVA_EXTINGUISH, 2.0F, 0.5F);
        });
    }

    // ------------------------------------------------------------------ Anti-trapaça: pedra no alvo

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (!isBusy()) {
            throwCd = 0;
            startThrow(sl, target);
        }
    }

    // ------------------------------------------------------------------ Fases, intro, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        BossKit.roar(this, SoundEvents.IRON_GOLEM_DEATH, 0.35F);
        BossKit.shake(level, position(), 32, 1.6F, 22);
        BfFx.groundBurst(level, position(), 6.0, 90);
        if (phase >= 2) {
            Vec3 c = corePos();
            level.sendParticles(BfFx.dust(COLOR, 2.0F), c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0);
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 2.5F, 0.4F);
        }
        rainCd = Math.min(rainCd, 60);
        sweepCd = Math.min(sweepCd, 80);
        quakeCd = Math.min(quakeCd, phase >= 2 ? 40 : 140);
        updateCore();
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 3.0F, 0.3F);
        playSound(SoundEvents.STONE_BREAK, 3.0F, 0.4F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) {
            BfFx.groundBurst(level, position(), 3.5, 25);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 6, getZ(), 6, 1.5, 1.0, 1.5, 0.02);
        }
        if (t == 44) {
            playSound(SoundEvents.IRON_GOLEM_DEATH, 3.0F, 0.3F);
            BossKit.shake(level, position(), 32, 1.5F, 20);
            Vec3 c = corePos();
            level.sendParticles(BfFx.dust(COLOR, 2.0F), c.x, c.y, c.z, 40, 0.5, 0.5, 0.5, 0);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        attackTimer = throwTimer = kneelTimer = ventTimer = 0;
        stompCd = 30;
        throwCd = 60;
        fistCd = 140;
        sweepCd = 100;
        rainCd = 160;
        quakeCd = 200;
        entityData.set(DATA_KNEEL, false);
        entityData.set(DATA_CORE, CORE_CLOSED);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0 && isKneeling()) return loop("kneel");
        return super.mainAnimation(moving);
    }

    @Override
    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "core", 6, state -> state.setAndContinue(switch (coreState()) {
            case CORE_OPEN -> loop("core_open");
            case CORE_CRACKED -> loop("core_cracked");
            default -> loop("core_closed");
        })));
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        int n = trophyCount();
        dropItem(BfRegistry.COLOSSUS_CORE.get(), n, n);
        dropItem(BfRegistry.COLOSSUS_RUNE.get(), 2 + n, 4 + n);
        dropEssence(Element.EARTH, 4, 6);
        dropEssence(Element.ARCANE, 2, 3);
        dropArcaneScrolls(3);
    }

    @Override
    protected void clientTick() {
        positionCoreParticles();
    }

    private void positionCoreParticles() {
        if (coreState() == CORE_CLOSED || random.nextInt(BfFx.reduced() ? 6 : 2) != 0) return;
        BossPart c = bossParts()[0];
        level().addParticle(BfFx.dust(COLOR, 1.0F), c.getX() + (random.nextDouble() - 0.5), c.getY() + 0.75 + (random.nextDouble() - 0.5),
                c.getZ() + (random.nextDouble() - 0.5), 0, 0.03, 0);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.4F);
        if (level() instanceof ServerLevel sl && isAwake()) BossKit.shake(sl, position(), 14, 0.35F, 6);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GRINDSTONE_USE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        updateCore();
    }
}
