package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * CHEFE: Minotauro, guardião do labirinto. Tudo telegrafado no chão:
 * <ul>
 *   <li>Lábris: cone curto antes de cada golpe;</li>
 *   <li>INVESTIDA: a linha do percurso aparece no chão durante a preparação — quem ficar nela é atropelado e
 *       atordoado; se ele bater numa parede fica tonto e vulnerável (+50% de dano); na fase 2 emenda uma segunda;</li>
 *   <li>Labrys Giratória: círculo em volta dele, depois gira três voltas (cada volta acerta quem ainda estiver dentro);</li>
 *   <li>Pisotear (fase 2, perto): círculo que explode;</li>
 *   <li>Lábris Arremessada (fase 2 longe, ou alvo em pilar/barco): linha até o alvo, a arma voa reta;</li>
 *   <li>anti-pilar: salta no alvo (pouso marcado) ou arremessa a lábris.</li>
 * </ul>
 */
public class MinotaurEntity extends CbBoss {
    private static final int COLOR = 0xC0392B;
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIZZY = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation PHASE2_SPEED = IsekaiMahou.id("minotaur_phase2_speed");
    private static final int SPIN_WINDUP = 16;
    private static final int[] SPIN_HITS = {16, 24, 32, 40};
    private static final float SPIN_RADIUS = 5.0F;
    /** Impacto do Pisotear = quadro 0,9 s da animação "stomp" (1,3 s): 18 ticks para sair do círculo. */
    private static final int STOMP_HIT = 18;
    private static final int STOMP_LENGTH = 26;
    private static final float STOMP_RADIUS = 5.0F;
    private static final int THROW_RELEASE = 10;

    private int chargeCooldown = 60;
    private int stompCooldown = 60;
    private int spinCooldown = 100;
    private int throwCooldown = 120;
    private int dizzyTicks;
    private boolean forceThrow;
    /** Pisotear em andamento (o círculo foi marcado onde ele está). */
    private boolean stomping;

    public MinotaurEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 280.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 13.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.MINOTAUR;
    }

    @Override
    protected String animId() {
        return "minotaur";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"attack", "charge_prep", "stomp", "roar", "spin", "throw", "leap", "slam"};
    }

    @Override
    protected String bossKey() {
        return "minotaur";
    }

    @Override
    protected int bossColor() {
        return COLOR;
    }

    @Override
    protected int rewardXp() {
        return 200;
    }

    @Override
    protected int introLength() {
        return 50;
    }

    @Override
    protected int meleeWindup() {
        return 10;
    }

    @Override
    protected float meleeRange() {
        return 4.4F;
    }

    @Override
    protected float meleeAngle() {
        return 130F;
    }

    @Override
    protected int meleeRecover() {
        return 20;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
        builder.define(DIZZY, false);
    }

    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    public boolean isDizzy() {
        return entityData.get(DIZZY);
    }

    @Override
    protected String loopAnim(boolean moving) {
        if (isDizzy()) return "stunned";
        if (isCharging()) return "charge";
        return super.loopAnim(moving);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(2, new ThrowGoal());
        goalSelector.addGoal(2, new SpinGoal());
        goalSelector.addGoal(2, new StompGoal());
        goalSelector.addGoal(3, new BossMeleeGoal(1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void onIntro() {
        playAction("roar");
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        playSound(SoundEvents.COW_AMBIENT, 3.0F, 0.4F);
    }

    @Override
    protected void onPhase2() {
        CbFx.permanentBonus(this, Attributes.MOVEMENT_SPEED, PHASE2_SPEED, 0.2);
        playAction("roar");
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
        // Para o que estava fazendo (o golpe em preparo some junto com o aviso; um salto em andamento termina normal).
        interruptAttack(PHASE_PAUSE);
        chargeCooldown = 0;
        throwCooldown = Math.min(throwCooldown, 60);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (chargeCooldown > 0) chargeCooldown--;
        if (stompCooldown > 0) stompCooldown--;
        if (spinCooldown > 0) spinCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (dizzyTicks > 0 && --dizzyTicks == 0) entityData.set(DIZZY, false);
        if (isDizzy() && level() instanceof ServerLevel sl && tickCount % 4 == 0) {
            double a = tickCount * 0.4;
            sl.sendParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.8, getY() + getBbHeight() + 0.2,
                    getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
        }
    }

    /** Alvo em pilar/barco/voando: salta nele (pouso marcado); colado num pilar alto, arremessa a lábris. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        double dy = target.getY() - getY();
        double dh = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (onGround() && dh < 22 && dy < 12 && !(dy > 6 && dh < 3)) {
            leapAt(level, target, 4.0F);
        } else if (hasLineOfSight(target)) {
            forceThrow = true;
        }
    }

    @Override
    protected String landAnim() {
        return "slam";
    }

    @Override
    protected boolean inCommittedAttack() {
        return isCharging() || stomping;
    }

    /** Tonto (depois de bater numa parede): recebe +50% de dano. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, isDizzy() ? amount * 1.5F : amount);
    }

    @Override
    protected boolean canAct() {
        return super.canAct() && !isDizzy();
    }

    private double distTo(LivingEntity t) {
        return Math.sqrt(distanceToSqr(t));
    }

    // =====================================================================================
    //  Investida (linha no chão durante a preparação)
    // =====================================================================================
    private class ChargeGoal extends Goal {
        private static final int PREP = 25;
        private static final int PREP_SECOND = 16;
        private int t;
        private int prep;
        private int run;
        private Vec3 dir = Vec3.ZERO;
        private Vec3 from = Vec3.ZERO;
        private double length;
        private int marker = -1;
        private boolean second;
        private final Set<UUID> hit = new HashSet<>();

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!canAct() || forceThrow || chargeCooldown > 0 || !onGround()) return false;
            double d = distanceToSqr(getTarget());
            return d > 5 * 5 && d < 26 * 26 && getSensing().hasLineOfSight(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && !isBusy() && !isDizzy() && (t < prep || run > 0);
        }

        @Override
        public void start() {
            second = false;
            playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.8F);
            aim(PREP);
        }

        /** Trava a direção AGORA e desenha o percurso inteiro no chão; o aquecimento é a preparação. */
        private void aim(int prepTicks) {
            t = 0;
            run = 0;
            prep = prepTicks;
            hit.clear();
            getNavigation().stop();
            playAction("charge_prep");
            LivingEntity target = getTarget();
            Vec3 to = target != null ? target.position() : position().add(getLookAngle());
            Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
            double dist = d.length();
            dir = dist < 1.0E-3 ? Vec3.directionFromRotation(0, getYRot()) : d.scale(1.0 / dist);
            // Passa um pouco do alvo (não dá para "esperar no fim da linha").
            length = Mth.clamp(dist + 6.0, 12.0, isPhase2() ? 32.0 : 28.0);
            from = position();
            lockYaw(yawOf(dir));
            if (level() instanceof ServerLevel sl) {
                // A linha continua acesa (cheia, pulsando) enquanto ele corre por ela; some quando a investida acaba.
                int runTicks = (int) Math.ceil(length / (isPhase2() ? 0.85 : 0.7)) + 2;
                marker = Telegraph.show(sl, Telegraph.Shape.line(from, from.add(dir.scale(length)), getBbWidth() + 1.2F)
                        .warmup(prepTicks).hold(runTicks).color(Telegraph.DANGER).owner(MinotaurEntity.this));
            }
        }

        private void clearMarker() {
            if (marker >= 0 && level() instanceof ServerLevel sl) Telegraph.cancel(sl, marker);
            marker = -1;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (t < prep) {
                t++;
                lockYaw(yawOf(dir));
                if (t % 6 == 0 && level() instanceof ServerLevel sl) {
                    playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.7F);
                    sl.sendParticles(CbFx.groundParticle(sl, blockPosition().below()), getX(), getY() + 0.1, getZ(), 12, 0.6, 0.05, 0.6, 0.1);
                }
                if (t == prep) {
                    double speed = isPhase2() ? 0.85 : 0.7;
                    run = (int) Math.ceil(length / speed);
                    entityData.set(CHARGING, true);
                    playSound(SoundEvents.COW_AMBIENT, 2.5F, 0.45F);
                }
                return;
            }
            // Nos ticks ímpares tick() roda sem checar canContinueToUse: investida já terminada/batida não continua.
            if (run <= 0) return;
            run--;
            double speed = isPhase2() ? 0.85 : 0.7;
            setDeltaMovement(dir.x * speed, getDeltaMovement().y, dir.z * speed);
            lockYaw(yawOf(dir));
            if (level() instanceof ServerLevel sl) {
                if (run % 3 == 0) playSound(SoundEvents.RAVAGER_STEP, 1.0F, 0.9F);
                sl.sendParticles(CbFx.groundParticle(sl, blockPosition().below()), getX(), getY() + 0.1, getZ(), 4, 0.5, 0.05, 0.5, 0.05);
                trample(sl);
            }
            if (horizontalCollision) {
                crash();
                return;
            }
            // Chegou ao fim da linha desenhada.
            if (position().subtract(from).multiply(1, 0, 1).lengthSqr() >= length * length) run = 0;
            if (run == 0) {
                entityData.set(CHARGING, false);
                clearMarker();
                // Fase 2: emenda uma segunda investida (nova linha, preparação mais curta).
                if (isPhase2() && !second && getTarget() != null && getTarget().isAlive()) {
                    second = true;
                    aim(PREP_SECOND);
                }
            }
        }

        private void trample(ServerLevel sl) {
            for (LivingEntity e : CbFx.enemies(MinotaurEntity.this, position().add(dir.scale(0.8)).add(0, 1, 0), 2.0)) {
                if (!isFoe(e) || !hit.add(e.getUUID())) continue;
                float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F;
                e.hurt(damageSources().mobAttack(MinotaurEntity.this), dmg);
                double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                e.push(dir.x * 2.2 * resist, 0.7 * resist, dir.z * 2.2 * resist);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 40, 0), MinotaurEntity.this);
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.6F);
                playSound(SoundEvents.ANVIL_LAND, 0.7F, 0.6F);
                sl.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
            }
        }

        private void crash() {
            run = 0;
            entityData.set(CHARGING, false);
            clearMarker();
            setDeltaMovement(dir.scale(-0.4).add(0, 0.2, 0));
            dizzyTicks = 60;
            entityData.set(DIZZY, true);
            setBusy(60);
            playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 2.0F, 0.5F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
            if (level() instanceof ServerLevel sl) {
                Vec3 p = position().add(dir.scale(1.2)).add(0, 1.2, 0);
                BlockState wall = sl.getBlockState(BlockPos.containing(p));
                sl.sendParticles(wall.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, wall),
                        p.x, p.y, p.z, 40, 0.6, 0.8, 0.6, 0.15);
                sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                BossKit.shake(sl, p, 20, 1.2F, 12);
            }
        }

        @Override
        public void stop() {
            clearMarker();
            entityData.set(CHARGING, false);
            chargeCooldown = isPhase2() ? 90 : 140;
            run = 0;
        }
    }

    private static float yawOf(Vec3 dir) {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    // =====================================================================================
    //  Labrys Giratória (círculo que fica marcado enquanto ele gira)
    // =====================================================================================
    private class SpinGoal extends Goal {
        private int t;
        private Vec3 center = Vec3.ZERO;
        private int marker = -1;

        SpinGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return canAct() && !forceThrow && spinCooldown <= 0 && onGround() && distanceToSqr(getTarget()) < 6.5 * 6.5;
        }

        @Override
        public boolean canContinueToUse() {
            return t < 46 && isAlive() && !isDizzy() && !isBusy();
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            center = position();
            playAction("spin");
            Announce.send(MinotaurEntity.this, Component.translatable("boss.isekai_mahou.minotaur.spin"), COLOR, Announce.STYLE_BOSS);
            playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.9F);
            if (level() instanceof ServerLevel sl) {
                int hold = SPIN_HITS[SPIN_HITS.length - 1] - SPIN_WINDUP + 2;
                marker = Telegraph.show(sl, Telegraph.Shape.circle(center, SPIN_RADIUS).warmup(SPIN_WINDUP).hold(hold)
                        .color(Telegraph.DANGER).owner(MinotaurEntity.this));
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            getNavigation().stop();
            if (!(level() instanceof ServerLevel sl)) return;
            for (int i = 0; i < SPIN_HITS.length; i++) {
                if (t != SPIN_HITS[i]) continue;
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(center, SPIN_RADIUS))) {
                    if (isFoe(e)) strike(e, 0.55F, 1.1);
                }
                spinFx(sl, i);
            }
        }

        private void spinFx(ServerLevel sl, int turn) {
            int points = 18;
            double a0 = turn * 0.6;
            for (int k = 0; k < points; k++) {
                double a = a0 + Math.PI * 2 * k / points;
                double r = SPIN_RADIUS * 0.7;
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(a) * r, center.y + 1.3, center.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
            sl.sendParticles(CbFx.groundParticle(sl, BlockPos.containing(center.x, center.y - 0.5, center.z)),
                    center.x, center.y + 0.1, center.z, 20, SPIN_RADIUS * 0.4, 0.05, SPIN_RADIUS * 0.4, 0.1);
            CbFx.sound(MinotaurEntity.this, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F + 0.1F * turn);
            BossKit.shake(sl, center, 14, 0.5F, 6);
        }

        @Override
        public void stop() {
            // Interrompido (atordoado, fase 2) no meio do giro: o círculo que ficaria aceso sem dano some junto.
            if (marker >= 0 && t < SPIN_HITS[SPIN_HITS.length - 1] && level() instanceof ServerLevel sl) Telegraph.cancel(sl, marker);
            marker = -1;
            spinCooldown = isPhase2() ? 150 : 220;
        }
    }

    // =====================================================================================
    //  Pisotear (fase 2)
    // =====================================================================================
    private class StompGoal extends Goal {
        private int t;
        private int marker = -1;
        private Vec3 center = Vec3.ZERO;

        StompGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isPhase2() && canAct() && !forceThrow && stompCooldown <= 0 && onGround() && distanceToSqr(getTarget()) < 4.5 * 4.5;
        }

        @Override
        public boolean canContinueToUse() {
            return t < STOMP_LENGTH && isAlive() && !isDizzy() && !isBusy();
        }

        @Override
        public void start() {
            t = 0;
            stomping = true;
            center = position();
            getNavigation().stop();
            playAction("stomp");
            if (level() instanceof ServerLevel sl) {
                marker = Telegraph.show(sl, Telegraph.Shape.circle(center, STOMP_RADIUS).warmup(STOMP_HIT).color(Telegraph.DANGER)
                        .owner(MinotaurEntity.this));
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (t != STOMP_HIT || !(level() instanceof ServerLevel sl)) return;
            if (marker >= 0) Telegraph.finish(sl, marker);
            marker = -1;
            Vec3 c = center;
            for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(c, STOMP_RADIUS))) {
                if (!isFoe(e) || !strike(e, 0.7F, 0.0)) continue;
                push(e, c, 1.1, 0.8);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), MinotaurEntity.this);
            }
            CbFx.groundBurst(sl, c, STOMP_RADIUS);
            BossKit.shake(sl, c, 22, 1.2F, 12);
            CbFx.sound(MinotaurEntity.this, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.5F);
        }

        @Override
        public void stop() {
            if (marker >= 0 && t < STOMP_HIT && level() instanceof ServerLevel sl) Telegraph.cancel(sl, marker);
            marker = -1;
            stomping = false;
            stompCooldown = 100;
        }
    }

    // =====================================================================================
    //  Lábris Arremessada (fase 2 / anti-pilar)
    // =====================================================================================
    private class ThrowGoal extends Goal {
        private int t;
        private ThrowAim aim;

        ThrowGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!canAct()) return false;
            LivingEntity target = getTarget();
            if (forceThrow) {
                if (getSensing().hasLineOfSight(target)) return true;
                forceThrow = false;
            }
            double d = distTo(target);
            return isPhase2() && throwCooldown <= 0 && d > 8.0 && d < 26.0 && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && !isBusy() && !isDizzy() && t < THROW_RELEASE + 10;
        }

        @Override
        public void start() {
            t = 0;
            forceThrow = false;
            getNavigation().stop();
            playAction("throw");
            Announce.send(MinotaurEntity.this, Component.translatable("boss.isekai_mahou.minotaur.throw"), COLOR, Announce.STYLE_BOSS);
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 1.0F);
            if (level() instanceof ServerLevel sl && getTarget() != null) aim = aimThrow(sl, getTarget(), THROW_RELEASE);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (aim != null) lockYaw(aimYaw(aim));
            if (t != THROW_RELEASE || aim == null || !(level() instanceof ServerLevel sl)) return;
            releaseThrow(sl, aim, new ItemStack(CbRegistries.MINOTAUR_LABRYS.get()), 0.9F, 1.8, 2.0F);
            aim = null;
        }

        @Override
        public void stop() {
            if (aim != null && aim.marker() >= 0 && level() instanceof ServerLevel sl) Telegraph.cancel(sl, aim.marker());
            aim = null;
            throwCooldown = 170 + random.nextInt(40);
        }
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.45F + random.nextFloat() * 0.1F;
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.7F, 0.8F);
    }

    @Override
    protected void playAttackSound() {
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
    }
}
