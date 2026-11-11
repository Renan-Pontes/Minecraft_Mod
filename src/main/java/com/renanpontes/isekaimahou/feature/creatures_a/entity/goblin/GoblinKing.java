package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.creatures_a.CaRegistry;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.BossFeatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Rei Goblin — chefe da fortaleza goblin. Coroa, machado enorme e:
 * <ul>
 *   <li>Machadada Larga: cone vermelho no chão por meio segundo antes do golpe (acerta todos dentro);</li>
 *   <li>Golpe Giratório: anel em volta dele (colado no rei ou longe = seguro); na Fúria emenda uma segunda volta
 *       cuja onda pega quem fugiu para longe (aí o seguro é ficar perto);</li>
 *   <li>Pulo Esmagador: o ponto de queda é marcado no chão durante o voo; na Fúria a queda solta uma onda (pule);</li>
 *   <li>Às Armas!: leva a corneta à boca e chama goblins — círculos verdes mostram onde vão surgir;</li>
 *   <li>Fúria (abaixo de 50%): mais rápido, cutscene de fase, ataques encadeados;</li>
 *   <li>anti-pilar: alvo inalcançável → salta nele; se não der, bomba de fumaça (some e reaparece ao lado);</li>
 *   <li>apresentação com escala de grupo, tenacidade contra controle, não monta em barcos, recompensa de grupo.</li>
 * </ul>
 */
public class GoblinKing extends AbstractGoblin {
    private static final RawAnimation K_IDLE = kingLoop("idle");
    private static final RawAnimation K_WALK = kingLoop("walk");
    private static final RawAnimation K_RUN = kingLoop("run");
    private static final RawAnimation CLEAVE = king("cleave");
    private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold("animation.goblin_king.leap");
    private static final RawAnimation SLAM = king("slam");
    private static final RawAnimation HORN = king("horn");
    private static final RawAnimation SPIN = king("spin");
    private static final RawAnimation INTRO = king("intro");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.goblin_king.death");
    private static final List<String> OWN_ANIMS = List.of("cleave", "leap", "slam", "horn", "spin", "intro");

    private static final ResourceLocation RAGE_ID = IsekaiMahou.id("goblin_king_rage");
    private static final int COLOR = 0x7BCB45;
    private static final int MAX_MINIONS = 7;
    private static final int INTRO_TICKS = 50;
    private static final int DEATH_TICKS = 50;
    private static final int CLEAVE_WINDUP = 10;
    private static final float CLEAVE_RANGE = 3.8F;
    private static final float CLEAVE_ANGLE = 120F;
    private static final int SPIN_WINDUP = 14;
    /** Fúria: a 2ª volta recomeça a animação depois que a 1ª termina (a animação dura 20 ticks) e acerta 14 ticks depois. */
    private static final int SPIN2_START = 22;
    private static final int SPIN2_HIT = SPIN2_START + SPIN_WINDUP;
    /** Miolo seguro do Golpe Giratório: colado no rei o machado passa por cima. */
    private static final float SPIN_INNER = 2.0F;
    private static final int HORN_BLOW = 8;
    private static final int HORN_TICKS = 32;
    private static final int SUMMON_DELAY = 20;
    /** Fúria: crava o machado (tick 14 da animação "intro") e ruge; fica parado (e vulnerável) por 2,2 s. */
    private static final int ROAR_TICKS = 44;

    private enum Move { NONE, CLEAVE, SPIN, HORN, ROAR }

    private final BossFeatures boss = new BossFeatures(this, "goblin_king", COLOR, BossEvent.BossBarColor.GREEN, 180,
            e -> e instanceof AbstractGoblin).introLength(INTRO_TICKS);
    private int leapCooldown = 100;
    private int summonCooldown = 200;
    private int spinCooldown = 80;
    private int meleeCooldown;
    private boolean leaping;
    private int leapTicks;
    private int leapFlight;
    private int leapMarker = -1;
    private float leapRadius;
    /** Centro do círculo de pouso marcado no chão. */
    @Nullable
    private Vec3 leapMark;
    private boolean enraged;
    private boolean pendingRoar;
    /** Este Golpe Giratório tem a 2ª volta (Fúria quando ele começou). */
    private boolean spinTwice;
    private boolean summonedAt66;
    private int introTicks;
    private Move move = Move.NONE;
    private int moveTicks;
    private float moveYaw;
    private int moveMarker = -1;
    private final List<Vec3> summonSpots = new ArrayList<>();

    public GoblinKing(EntityType<? extends GoblinKing> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        boss.onEngage(this::startIntro);
    }

    private static RawAnimation king(String name) {
        return RawAnimation.begin().thenPlay("animation.goblin_king." + name);
    }

    private static RawAnimation kingLoop(String name) {
        return RawAnimation.begin().thenLoop("animation.goblin_king." + name);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 280.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public int rank() {
        return 3;
    }

    @Override
    public boolean canFlee() {
        return false;
    }

    @Override
    public boolean canSteal() {
        return false;
    }

    @Override
    protected void addCombatGoals() {
        // Chamado no construtor do Mob: as metas só leem o estado do rei em jogo.
        goalSelector.addGoal(0, new Goal() {
            {
                setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
            }

            @Override
            public boolean canUse() {
                return isBusy();
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }

            @Override
            public void tick() {
                getNavigation().stop();
            }
        });
        goalSelector.addGoal(3, new KingMeleeGoal());
    }

    /** Persegue como o corpo a corpo vanilla, mas o golpe é a Machadada Larga telegrafada. */
    private class KingMeleeGoal extends MeleeAttackGoal {
        KingMeleeGoal() {
            super(GoblinKing.this, 1.1, true);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (canPerformAttack(target) && meleeCooldown <= 0 && !isBusy() && !boss.controlled()) {
                resetAttackCooldown();
                startCleave(target);
            }
        }
    }

    private boolean isBusy() {
        return introTicks > 0 || leaping || move != Move.NONE;
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        // Machado e coroa fazem parte do modelo (e caem pela tabela de loot).
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boss.tick(isBusy());
        if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
        LivingEntity target = getTarget();
        if (!boss.engaged() && target instanceof Player p && !p.isCreative() && !p.isSpectator() && distanceTo(p) < 32) boss.engage();
        if (introTicks > 0) {
            introTicks--;
            if (introTicks == INTRO_TICKS - 14) {
                // Crava o machado no chão (quadro do "slam" da intro).
                BossFeatures.groundRing(sl, position(), 3.0, ParticleTypes.CRIT);
                BossKit.shake(sl, position(), 24, 1.0F, 12);
                playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
            }
            return;
        }

        // Fúria abaixo de 50%: cutscene de fase, mais velocidade e uma convocação extra.
        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enraged = true;
            boss.phaseChange("rage");
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(RAGE_ID)) {
                speed.addPermanentModifier(new AttributeModifier(RAGE_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            // Assim que terminar o golpe atual: para, crava o machado e ruge (durante a cutscene de fase).
            pendingRoar = true;
            summonCooldown = 0;
        }
        if (!summonedAt66 && getHealth() < getMaxHealth() * 0.66F) {
            summonedAt66 = true;
            summonCooldown = 0;
        }

        if (summonCooldown > 0) summonCooldown--;
        if (leapCooldown > 0) leapCooldown--;
        if (spinCooldown > 0) spinCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;

        if (leaping) {
            tickLeap(sl);
            return;
        }
        if (move != Move.NONE) {
            tickMove(sl);
            return;
        }
        if (pendingRoar) {
            startRoar();
            return;
        }
        if (target == null || !target.isAlive() || boss.controlled()) return;
        if (boss.unreachable(target, 40)) {
            antiCheese(sl, target);
            return;
        }
        double d = distanceTo(target);
        if (summonCooldown <= 0 && hasLineOfSight(target)) {
            if (!startHorn(sl)) summonCooldown = 100;
            return;
        }
        if (spinCooldown <= 0 && d < 5.0 && onGround()) {
            startSpin(sl);
            return;
        }
        if (leapCooldown <= 0 && onGround() && d > 6.0 && d < 18.0 && hasLineOfSight(target)) startLeap(sl, target);
    }

    private void tickMove(ServerLevel sl) {
        moveTicks++;
        lockYaw();
        switch (move) {
            case CLEAVE -> {
                if (moveTicks == CLEAVE_WINDUP) cleaveHit(sl);
                if (moveTicks >= CLEAVE_WINDUP + 5) endMove(enraged ? 12 : 18);
            }
            case SPIN -> {
                if (moveTicks == SPIN_WINDUP) {
                    spinHit(sl, SPIN_INNER, 5.0F, 1.1F);
                    // Fúria: segunda volta — agora a onda do machado pega quem fugiu para longe (o seguro é voltar perto).
                    if (spinTwice) {
                        moveMarker = Telegraph.show(sl, Telegraph.Shape.ring(position(), 4.0F, 8.5F).warmup(SPIN2_HIT - SPIN_WINDUP)
                                .color(Telegraph.DANGER).owner(this));
                    }
                }
                // A animação só recomeça depois de terminar (o GeckoLib ignora disparar a mesma animação em andamento).
                if (spinTwice && moveTicks == SPIN2_START) triggerAnim("boss", "spin");
                if (spinTwice && moveTicks == SPIN2_HIT) spinHit(sl, 4.0F, 8.5F, 0.8F);
                if (moveTicks >= (spinTwice ? SPIN2_HIT + 6 : SPIN_WINDUP + 8)) endMove(10);
            }
            case ROAR -> {
                if (moveTicks == 14) {
                    BossFeatures.groundRing(sl, position(), 3.0, ParticleTypes.CRIT);
                    BossKit.shake(sl, position(), 24, 1.0F, 12);
                    playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
                }
                if (moveTicks == 30) BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 1.2F);
                if (moveTicks >= ROAR_TICKS) endMove(0);
            }
            case HORN -> {
                if (moveTicks == HORN_BLOW) blowHorn(sl);
                if (moveTicks == HORN_BLOW + SUMMON_DELAY) spawnMinions(sl);
                if (moveTicks >= HORN_TICKS) endMove(0);
            }
            default -> endMove(0);
        }
    }

    /** Fúria: fica parado (vulnerável) 2,2 s com a pose da apresentação — o machado bate no chão e ele ruge. */
    private void startRoar() {
        pendingRoar = false;
        move = Move.ROAR;
        moveTicks = 0;
        moveYaw = getYRot();
        getNavigation().stop();
        triggerAnim("boss", "intro");
    }

    private void endMove(int meleeRecover) {
        move = Move.NONE;
        moveTicks = 0;
        moveMarker = -1;
        meleeCooldown = Math.max(meleeCooldown, meleeRecover);
    }

    /** Durante um golpe o rei não gira (o cone/anel no chão é exatamente onde ele vai acertar). */
    private void lockYaw() {
        setYRot(moveYaw);
        setYHeadRot(moveYaw);
        yBodyRot = moveYaw;
    }

    private float yawTo(Entity e) {
        return (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    // ------------------------------------------------------------------ Machadada Larga

    private void startCleave(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        move = Move.CLEAVE;
        moveTicks = 0;
        moveYaw = yawTo(target);
        lockYaw();
        getNavigation().stop();
        triggerAnim("boss", "cleave");
        moveMarker = Telegraph.show(sl, Telegraph.Shape.cone(position(), moveYaw, CLEAVE_ANGLE, CLEAVE_RANGE)
                .warmup(CLEAVE_WINDUP).color(Telegraph.DANGER).owner(this).noRunes());
        playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.2F, 0.7F);
    }

    private void cleaveHit(ServerLevel sl) {
        Telegraph.Shape cone = Telegraph.Shape.cone(position(), moveYaw, CLEAVE_ANGLE, CLEAVE_RANGE);
        for (LivingEntity e : Telegraph.entitiesIn(sl, cone)) {
            if (boss.isFoe(e)) strike(e, 1.0F, 0.6);
        }
        if (moveMarker >= 0) Telegraph.finish(sl, moveMarker);
        Vec3 look = Vec3.directionFromRotation(0, moveYaw);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2.0, getY() + getBbHeight() * 0.45, getZ() + look.z * 2.0,
                3, 0.6, 0.1, 0.6, 0.0);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
    }

    /** Golpe físico do machado (dano do atributo: já inclui a escala de grupo e Força). */
    private void strike(LivingEntity e, float mult, double knock) {
        DamageSource src = damageSources().mobAttack(this);
        if (!e.hurt(src, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * mult)) return;
        e.knockback(knock, getX() - e.getX(), getZ() - e.getZ());
        e.hurtMarked = true;
        EnchantmentHelper.doPostAttackEffects((ServerLevel) level(), e, src);
        setLastHurtMob(e);
    }

    // ------------------------------------------------------------------ Golpe Giratório

    private void startSpin(ServerLevel sl) {
        move = Move.SPIN;
        moveTicks = 0;
        // Decide no início (se a Fúria começar no meio do giro, a 2ª volta não aparece sem aviso).
        spinTwice = enraged;
        moveYaw = getYRot();
        getNavigation().stop();
        boss.announce("spin");
        triggerAnim("boss", "spin");
        spinCooldown = enraged ? 120 : 170;
        // Anel: colado no rei (dentro do miolo) ou longe = seguro.
        moveMarker = Telegraph.show(sl, Telegraph.Shape.ring(position(), SPIN_INNER, 5.0F).warmup(SPIN_WINDUP)
                .color(Telegraph.DANGER).owner(this));
        playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.6F, 0.6F);
    }

    private void spinHit(ServerLevel sl, float inner, float outer, float mult) {
        Vec3 c = position();
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.ring(c, inner, outer))) {
            if (boss.isFoe(e)) strike(e, mult, 1.4);
        }
        if (moveMarker >= 0) Telegraph.finish(sl, moveMarker);
        moveMarker = -1;
        BossFeatures.ringFx(sl, c.add(0, 1.0, 0), (inner + outer) * 0.5, ParticleTypes.SWEEP_ATTACK);
        BossFeatures.ringFx(sl, c, outer, ParticleTypes.CRIT);
        BossKit.shake(sl, c, 16, 0.6F, 8);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
        playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 1.2F);
    }

    // ------------------------------------------------------------------ Pulo Esmagador

    private void startLeap(ServerLevel sl, LivingEntity target) {
        boss.announce("leap");
        triggerAnim("boss", "leap");
        leapRadius = enraged ? 5.0F : 4.0F;
        // Voo longo o bastante para sair do círculo andando (de perto o arco fica mais alto).
        leapFlight = BossKit.leapAt(this, target, BossFeatures.fairLeapPower(this, target.position(), leapRadius, 1.0));
        leapMark = target.position();
        leapMarker = Telegraph.show(sl, Telegraph.Shape.circle(leapMark, leapRadius).warmup(leapFlight)
                .color(Telegraph.DANGER).owner(this));
        leaping = true;
        leapTicks = 0;
        leapCooldown = (enraged ? 90 : 140) + random.nextInt(60);
        playSound(SoundEvents.RAVAGER_STEP, 1.5F, 0.6F);
        playSound(getAmbientSound(), 2.0F, 0.6F);
    }

    private void tickLeap(ServerLevel sl) {
        leapTicks++;
        if ((onGround() && leapTicks > 3) || leapTicks > leapFlight + 30 || isInWater()) {
            leaping = false;
            landSlam(sl);
        }
    }

    private void landSlam(ServerLevel sl) {
        triggerAnim("boss", "slam");
        if (leapMarker >= 0) Telegraph.finish(sl, leapMarker);
        leapMarker = -1;
        Vec3 c = position();
        float r = BossFeatures.landingRadius(this, leapMark, leapRadius);
        leapMark = null;
        float dmg = 12.0F * BossKit.damageScale(this);
        MobEffectInstance slow = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(c, r))) {
            if (!boss.isFoe(e)) continue;
            float falloff = (float) (1.0 - 0.5 * Math.min(1.0, e.position().distanceTo(c) / r));
            boss.hurtFoe(e, dmg * falloff, c, 1.6 * falloff, slow);
        }
        BossFeatures.groundRing(sl, c, r, ParticleTypes.CRIT);
        BossKit.shake(sl, c, 24, 1.4F, 16);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.75F);
        playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
        meleeCooldown = Math.max(meleeCooldown, 10);
        if (enraged) {
            // Fúria: a cratera solta uma onda rasteira — pule!
            float dmg2 = 7.0F * BossKit.damageScale(this);
            Telegraph.delayed(sl, Telegraph.Shape.ring(c, r, r + 4.0F).warmup(12).vertical(0.5, 1.0).color(Telegraph.WARNING).owner(this),
                    hits -> {
                        for (LivingEntity e : hits) {
                            if (boss.isFoe(e)) boss.hurtFoe(e, dmg2, c, 1.0, null);
                        }
                        BossFeatures.ringFx(sl, c, r + 2.0, ParticleTypes.CLOUD);
                        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 0.8F, 1.1F);
                    });
        }
    }

    // ------------------------------------------------------------------ Às Armas! (corneta)

    /** Leva a corneta à boca; os goblins surgem nos círculos verdes 1 s depois do toque. Falso se não cabe ninguém. */
    private boolean startHorn(ServerLevel sl) {
        int nearby = sl.getEntitiesOfClass(AbstractGoblin.class, getBoundingBox().inflate(24.0), g -> g != this && g.isAlive()).size();
        int count = Math.min(MAX_MINIONS - nearby, enraged ? 4 : 3);
        if (count <= 0) return false;
        summonSpots.clear();
        for (int i = 0; i < count; i++) {
            Vec3 pos = findSpawnSpot(sl, CaRegistry.GOBLIN.get());
            if (pos != null) summonSpots.add(pos);
        }
        if (summonSpots.isEmpty()) return false;
        move = Move.HORN;
        moveTicks = 0;
        moveYaw = getYRot();
        getNavigation().stop();
        boss.announce("summon");
        triggerAnim("boss", "horn");
        summonCooldown = enraged ? 400 : 600;
        return true;
    }

    private void blowHorn(ServerLevel sl) {
        playSound(SoundEvents.RAID_HORN.value(), 3.0F, 1.1F);
        BossKit.shake(sl, position(), 24, 0.5F, 20);
        sl.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.4, getZ(), 6, 0.5, 0.3, 0.5, 1.0);
        for (Vec3 p : summonSpots) {
            Telegraph.show(sl, Telegraph.Shape.circle(p, 0.9F).warmup(SUMMON_DELAY).color(COLOR).owner(this).noRunes().noFlash());
        }
    }

    private void spawnMinions(ServerLevel sl) {
        LivingEntity target = getTarget();
        for (Vec3 pos : summonSpots) {
            float r = random.nextFloat();
            EntityType<? extends AbstractGoblin> type = r < 0.5F ? CaRegistry.GOBLIN.get()
                    : r < 0.8F ? CaRegistry.GOBLIN_ARCHER.get() : CaRegistry.GOBLIN_SHAMAN.get();
            summonAt(sl, type, pos, target);
        }
        summonSpots.clear();
    }

    private void summonAt(ServerLevel sl, EntityType<? extends AbstractGoblin> type, Vec3 pos, @Nullable LivingEntity target) {
        AbstractGoblin minion = type.create(sl);
        if (minion == null) return;
        minion.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360F, 0F);
        minion.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null);
        if (target != null) minion.setTarget(target);
        sl.addFreshEntity(minion);
        sl.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.6, pos.z, 15, 0.3, 0.5, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x, pos.y + 1.4, pos.z, 2, 0.2, 0.1, 0.2, 0.0);
    }

    @Nullable
    private Vec3 findSpawnSpot(ServerLevel sl, EntityType<?> type) {
        for (int tries = 0; tries < 10; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2.5 + random.nextDouble() * 3.0;
            BlockPos base = BlockPos.containing(getX() + Math.cos(a) * r, getY() + 2, getZ() + Math.sin(a) * r);
            for (int dy = 0; dy < 6; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)
                        && sl.noCollision(type.getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
                    return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ anti-pilar

    /** Alvo inalcançável há 2 s: salta nele; se não der, bomba de fumaça (some e reaparece ao lado do alvo). */
    private void antiCheese(ServerLevel sl, LivingEntity target) {
        double dy = target.getY() - getY();
        double dh = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        boolean tooSteep = dy > 6 && dh < 3;
        if (onGround() && dh < 20 && dy < 12 && !tooSteep) {
            startLeap(sl, target);
            return;
        }
        // Colado num pilar alto: reaparece mais longe (com espaço para o próximo salto); senão, ao lado do alvo.
        Vec3 from = position();
        if (BossKit.blinkNear(this, target, tooSteep || dy > 2 ? 6.0 : 3.0)) {
            boss.announce("smoke");
            smoke(sl, from);
            smoke(sl, position());
            playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.6F);
            meleeCooldown = Math.max(meleeCooldown, 12);
        } else if (summonCooldown < 300) {
            // Nem salto nem fumaça: chama o bando (arqueiros e xamãs alcançam o alvo).
            startHorn(sl);
        }
    }

    private void smoke(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 40, 0.8, 0.9, 0.8, 0.02);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.4, at.z, 12, 0.7, 0.3, 0.7, 0.02);
    }

    // ------------------------------------------------------------------ Apresentação / morte

    private void startIntro() {
        BossKit.scaleToParty(this, 48);
        introTicks = INTRO_TICKS;
        getNavigation().stop();
        triggerAnim("boss", "intro");
        boss.announce("intro");
        BossKit.roar(this, SoundEvents.PIGLIN_BRUTE_ANGRY, 0.6F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !boss.engaged() && source.getEntity() instanceof Player p && !p.isCreative() && !p.isSpectator()) {
            boss.engage();
        }
        // Apresentação: invulnerável (a cena é de graça para todos).
        if (introTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // Só conta a morte uma vez e só se ela não foi cancelada pelo evento de morte.
        boolean wasDead = dead;
        super.die(source);
        if (!level().isClientSide && !wasDead && dead) {
            boss.onDeath();
            move = Move.NONE;
            leaping = false;
        }
    }

    /** Morte animada: desaba por 2,5 s (a vanilla tomba e some em 1 s). */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        if (deathTime == 32) {
            BossFeatures.groundRing(sl, position(), 2.0, null);
            playSound(SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
        }
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 30, 0.8, 0.4, 0.8, 0.02);
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        MobEffectInstance e = level().isClientSide ? effect : boss.tenacity(effect);
        boolean added = e != null && super.addEffect(e, source);
        if (added && !level().isClientSide && BossFeatures.interrupts(e)) interruptMove();
        return added;
    }

    /** Atordoado/congelado: o golpe em preparo é cancelado (o aviso no chão some sem flash). */
    private void interruptMove() {
        if (move == Move.NONE || !(level() instanceof ServerLevel sl)) return;
        if (moveMarker >= 0) Telegraph.cancel(sl, moveMarker);
        boolean hornReady = move == Move.HORN && moveTicks >= HORN_BLOW;
        endMove(10);
        // A corneta já tocou: os goblins chegam mesmo assim.
        if (hornReady) spawnMinions(sl);
        else summonSpots.clear();
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        // Barco/carrinho seguraria o rei parado (trapaça clássica).
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // O pulo esmagador é de propósito: o rei não sente a queda.
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        boss.startSeen(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        boss.stopSeen(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        boss.onNameChanged();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        boss.save(tag);
        tag.putBoolean("Enraged", enraged);
        tag.putBoolean("Summoned66", summonedAt66);
        tag.putInt("LeapCooldown", leapCooldown);
        tag.putInt("SummonCooldown", summonCooldown);
        tag.putInt("SpinCooldown", spinCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boss.load(tag);
        enraged = tag.getBoolean("Enraged");
        summonedAt66 = tag.getBoolean("Summoned66");
        leapCooldown = tag.getInt("LeapCooldown");
        summonCooldown = tag.getInt("SummonCooldown");
        spinCooldown = tag.getInt("SpinCooldown");
    }

    // ------------------------------------------------------------------ Sons e animações

    @Override
    protected String meleeAnim() {
        return "cleave";
    }

    @Override
    protected float voiceBase() {
        return 0.75F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAggressive() ? SoundEvents.PIGLIN_BRUTE_ANGRY : SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.PIGLIN_BRUTE_STEP, 0.4F, 0.7F);
    }

    @Override
    protected PlayState movePredicate(AnimationState<AbstractGoblin> state) {
        // Arquivo de animação próprio (goblin_king.animation.json).
        if (state.isMoving() && !isBusy()) return state.setAndContinue(isAggressive() ? K_RUN : K_WALK);
        return state.setAndContinue(K_IDLE);
    }

    @Override
    protected PlayState armsPredicate(AnimationState<AbstractGoblin> state) {
        return PlayState.STOP;
    }

    @Override
    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "boss", 2, state -> PlayState.STOP)
                .triggerableAnim("cleave", CLEAVE)
                .triggerableAnim("leap", LEAP)
                .triggerableAnim("slam", SLAM)
                .triggerableAnim("horn", HORN)
                .triggerableAnim("spin", SPIN)
                .triggerableAnim("intro", INTRO));
        // Último controlador: na morte sobrepõe todos os outros e segura o último quadro.
        controllers.add(new AnimationController<>(this, "death", 3,
                state -> isDeadOrDying() ? state.setAndContinue(DEATH) : PlayState.STOP));
    }

    @Override
    public void triggerAnim(@Nullable String controller, String anim) {
        // Animações exclusivas do rei moram no controlador "boss".
        super.triggerAnim(OWN_ANIMS.contains(anim) ? "boss" : controller, anim);
    }
}
