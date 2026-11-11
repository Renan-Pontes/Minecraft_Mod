package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * CHEFE: Lorde Orc, o líder do acampamento. Tudo telegrafado no chão:
 * <ul>
 *   <li>Cutelo: cone curto antes de cada golpe;</li>
 *   <li>Golpe no Chão (perto): círculo à frente; na fase 2 a pancada solta uma onda (anel que dá para pular);</li>
 *   <li>Pisão de Guerra: três pisões, cada um solta um anel cada vez mais longe — pule cada onda no tempo certo;</li>
 *   <li>Machado Arremessado (longe, ou alvo em pilar/barco): linha no chão até o alvo; o cutelo voa reto, derruba
 *       quem acertar; na fase 2 arremessa dois seguidos;</li>
 *   <li>Grito de Guerra: fortalece a Horda (na fase 2 também chama reforços).</li>
 * </ul>
 */
public class OrcLordEntity extends CbBoss {
    private static final int COLOR = 0x7BC043;
    private static final int MAX_REINFORCEMENTS = 6;
    private static final ResourceLocation PHASE2_SPEED = IsekaiMahou.id("orc_lord_phase2_speed");
    /** Impacto do Golpe no Chão = quadro 1,0 s da animação "slam" (1,5 s): 20 ticks para sair do círculo. */
    private static final int SLAM_HIT = 20;
    private static final int SLAM_LENGTH = 30;
    private static final int THROW_RELEASE = 12;
    /** A animação "throw" dura 1,1 s: o 2º machado da fase 2 só começa depois (senão a animação não recomeça). */
    private static final int THROW_ANIM = 23;

    private int slamCooldown = 80;
    private int warCryCooldown = 140;
    private int stompCooldown = 160;
    private int throwCooldown = 100;
    private int reinforcements;
    /** Ticks até o grito da mudança de fase (0 = nenhum). */
    private int phaseShout;
    /** Alvo inalcançável: o próximo ataque é o arremesso, sem esperar a recarga. */
    private boolean forceThrow;
    private boolean throwAnnounced;

    public OrcLordEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.HORDE;
    }

    @Override
    protected String animId() {
        return "orc_lord";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"attack", "slam", "warcry", "intro", "throw", "stomp", "leap", "land"};
    }

    /** Pouso do salto anti-pilar: desce o cutelo de uma vez (o "slam" tem preparação longa e ficaria atrasado). */
    @Override
    protected String landAnim() {
        return "land";
    }

    @Override
    protected String bossKey() {
        return "orc_lord";
    }

    @Override
    protected int bossColor() {
        return COLOR;
    }

    @Override
    protected int rewardXp() {
        return 160;
    }

    @Override
    protected float voicePitch() {
        return 0.55F;
    }

    @Override
    protected float meleeRange() {
        return 3.9F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(1, new WarCryGoal());
        goalSelector.addGoal(2, new ThrowGoal());
        goalSelector.addGoal(2, new SlamGoal());
        goalSelector.addGoal(2, new StompGoal());
        goalSelector.addGoal(3, new BossMeleeGoal(1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void onIntro() {
        playAction("intro");
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.7F);
        playSound(SoundEvents.RAID_HORN.value(), 4.0F, 0.9F);
    }

    @Override
    protected void onPhase2() {
        CbFx.permanentBonus(this, Attributes.MOVEMENT_SPEED, PHASE2_SPEED, 0.15);
        // Para durante a cutscene de fase (a câmera não deixa ver os avisos) e solta o Grito de Guerra da fase 2
        // (Força II na Horda + reforços) no quadro do grito da animação.
        interruptAttack(PHASE_PAUSE);
        playAction("warcry");
        phaseShout = 8;
        warCryCooldown = 260;
        slamCooldown = Math.min(slamCooldown, 50);
        stompCooldown = Math.min(stompCooldown, 80);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (phaseShout > 0 && --phaseShout == 0 && isAlive()) shout();
        if (slamCooldown > 0) slamCooldown--;
        if (warCryCooldown > 0) warCryCooldown--;
        if (stompCooldown > 0) stompCooldown--;
        if (throwCooldown > 0) throwCooldown--;
    }

    /** Alvo em pilar/barco/voando: arremessa o cutelo (com linha de visão) ou salta nele. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (hasLineOfSight(target)) {
            forceThrow = true;
            return;
        }
        double dy = target.getY() - getY();
        double dh = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (onGround() && dh < 20 && dy < 12 && !(dy > 6 && dh < 3)) leapAt(level, target, 4.5F);
    }

    private double distTo(LivingEntity t) {
        return Math.sqrt(distanceToSqr(t));
    }

    // =====================================================================================
    //  Golpe no Chão
    // =====================================================================================
    private class SlamGoal extends Goal {
        private int t;
        private float yaw;
        private Vec3 center = Vec3.ZERO;
        private float radius;
        private int marker = -1;

        SlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return canAct() && !forceThrow && slamCooldown <= 0 && onGround() && distanceToSqr(getTarget()) < 6.0 * 6.0;
        }

        @Override
        public boolean canContinueToUse() {
            return t < SLAM_LENGTH && isAlive() && !isBusy();
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            yaw = yawTo(getTarget());
            lockYaw(yaw);
            radius = isPhase2() ? 4.5F : 4.0F;
            center = position().add(Vec3.directionFromRotation(0, yaw).scale(2.0));
            playAction("slam");
            playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 2.0F, 0.5F);
            if (level() instanceof ServerLevel sl) {
                marker = Telegraph.show(sl, Telegraph.Shape.circle(center, radius).warmup(SLAM_HIT).color(Telegraph.DANGER).owner(OrcLordEntity.this));
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            lockYaw(yaw);
            if (t != SLAM_HIT || !(level() instanceof ServerLevel sl)) return;
            if (marker >= 0) Telegraph.finish(sl, marker);
            marker = -1;
            for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(center, radius))) {
                if (!isFoe(e)) continue;
                if (strike(e, 1.0F, 0.0)) {
                    push(e, center, 1.0, 0.65);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), OrcLordEntity.this);
                }
            }
            CbFx.groundBurst(sl, center, radius);
            BossKit.shake(sl, center, 24, 1.3F, 14);
            CbFx.sound(OrcLordEntity.this, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
            CbFx.sound(OrcLordEntity.this, SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
            if (isPhase2()) {
                // Fase 2: a pancada racha o chão — onda rasteira (pule!).
                Vec3 c = center;
                float r = radius;
                Telegraph.delayed(sl, Telegraph.Shape.ring(c, r, r + 4.0F).warmup(12).vertical(0.5, 1.0).color(Telegraph.WARNING)
                        .owner(OrcLordEntity.this), hits -> {
                            for (LivingEntity e : hits) {
                                if (isFoe(e) && strike(e, 0.55F, 0.0)) push(e, c, 0.8, 0.45);
                            }
                            CbFx.groundBurst(sl, c, r + 2.0);
                        });
            }
        }

        @Override
        public void stop() {
            if (t < SLAM_HIT && marker >= 0 && level() instanceof ServerLevel sl) Telegraph.cancel(sl, marker);
            marker = -1;
            slamCooldown = isPhase2() ? 100 : 160;
        }
    }

    // =====================================================================================
    //  Pisão de Guerra: três anéis em sequência (cada um no seu pisão)
    // =====================================================================================
    private class StompGoal extends Goal {
        private static final int[] STOMPS = {10, 20, 30};
        private int t;

        StompGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!canAct() || forceThrow || stompCooldown > 0 || !onGround()) return false;
            double d = distTo(getTarget());
            return d > 3.0 && d < 11.0;
        }

        @Override
        public boolean canContinueToUse() {
            return t < 36 && isAlive() && !isBusy();
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            playAction("stomp");
            Announce.send(OrcLordEntity.this, Component.translatable("boss.isekai_mahou.orc_lord.stomp"), COLOR, Announce.STYLE_BOSS);
            if (!(level() instanceof ServerLevel sl)) return;
            Vec3 c = position();
            int rings = isPhase2() ? 4 : 3;
            for (int i = 0; i < rings; i++) {
                float inner = 1.5F + 3.0F * i;
                float outer = inner + 3.0F;
                int warmup = 10 + 10 * Math.min(i, STOMPS.length - 1) + (i >= STOMPS.length ? 6 : 0);
                Telegraph.delayed(sl, Telegraph.Shape.ring(c, inner, outer).warmup(warmup).vertical(0.5, 1.0)
                        .color(Telegraph.WARNING).owner(OrcLordEntity.this), hits -> {
                            for (LivingEntity e : hits) {
                                if (!isFoe(e) || !strike(e, 0.6F, 0.0)) continue;
                                push(e, c, 0.6, 0.5);
                                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), OrcLordEntity.this);
                            }
                            ringFx(sl, c, (inner + outer) * 0.5);
                        });
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) return;
            for (int s : STOMPS) {
                if (t != s) continue;
                CbFx.sound(OrcLordEntity.this, SoundEvents.RAVAGER_STEP, 2.0F, 0.5F);
                CbFx.sound(OrcLordEntity.this, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.8F);
                CbFx.groundBurst(sl, position(), 1.5);
                BossKit.shake(sl, position(), 24, 0.9F, 8);
            }
        }

        @Override
        public void stop() {
            stompCooldown = isPhase2() ? 170 : 240;
        }
    }

    private static void ringFx(ServerLevel sl, Vec3 c, double r) {
        var dust = CbFx.groundParticle(sl, BlockPos.containing(c.x, c.y - 0.5, c.z));
        int points = Math.max(16, (int) (r * 8));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            sl.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 2, 0.15, 0.05, 0.15, 0.12);
        }
    }

    // =====================================================================================
    //  Machado Arremessado
    // =====================================================================================
    private class ThrowGoal extends Goal {
        private int t;
        private int throwsLeft;
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
            return throwCooldown <= 0 && d > 7.0 && d < 28.0 && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && !isBusy() && (throwsLeft > 0 || t < THROW_RELEASE + 10);
        }

        @Override
        public void start() {
            t = 0;
            throwsLeft = isPhase2() ? 2 : 1;
            getNavigation().stop();
            if (forceThrow || !throwAnnounced) {
                Announce.send(OrcLordEntity.this, Component.translatable("boss.isekai_mahou.orc_lord.throw"), COLOR, Announce.STYLE_BOSS);
                throwAnnounced = true;
            }
            forceThrow = false;
            windUp();
        }

        private void windUp() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || !(level() instanceof ServerLevel sl)) {
                throwsLeft = 0;
                return;
            }
            playAction("throw");
            playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.8F, 0.5F);
            aim = aimThrow(sl, target, THROW_RELEASE);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (aim != null) lockYaw(aimYaw(aim));
            if (!(level() instanceof ServerLevel sl)) return;
            if (t == THROW_RELEASE && aim != null) {
                releaseThrow(sl, aim, new ItemStack(CbRegistries.ORC_LORD_CLEAVER.get()), 0.85F, 1.5, 1.7F);
                aim = null;
                throwsLeft--;
            }
            // Fase 2: o segundo machado vem logo que a animação do primeiro termina (nova linha, nova mira).
            if (throwsLeft > 0 && aim == null && t >= THROW_ANIM) {
                t = 0;
                windUp();
            }
        }

        @Override
        public void stop() {
            if (aim != null && aim.marker() >= 0 && level() instanceof ServerLevel sl) Telegraph.cancel(sl, aim.marker());
            aim = null;
            throwsLeft = 0;
            throwCooldown = (isPhase2() ? 110 : 160) + random.nextInt(40);
        }
    }

    // =====================================================================================
    //  Grito de Guerra
    // =====================================================================================
    private class WarCryGoal extends Goal {
        private int t;

        WarCryGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return canAct() && !forceThrow && warCryCooldown <= 0 && distanceToSqr(getTarget()) < 24 * 24;
        }

        @Override
        public boolean canContinueToUse() {
            return t < 40 && isAlive() && !isBusy();
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            playAction("warcry");
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (t == 8) shout();
        }

        @Override
        public void stop() {
            warCryCooldown = isPhase2() ? 260 : 400;
        }
    }

    private void shout() {
        if (!(level() instanceof ServerLevel sl)) return;
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        Announce.send(this, Component.translatable("boss.isekai_mahou.orc_lord.warcry"), COLOR, Announce.STYLE_BOSS);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 2.0, getZ(), 1, 0, 0, 0, 0);
        BossKit.shake(sl, position(), 28, 1.0F, 20);
        // A Horda recebe Força (II na fase 2), Velocidade e cura; o Lorde só se protege (o cutelo dele já dói bastante).
        int strength = isPhase2() ? 1 : 0;
        for (CbMonster m : sl.getEntitiesOfClass(CbMonster.class, getBoundingBox().inflate(24.0),
                m -> m.isAlive() && m != this && m.faction() == CbFaction.HORDE)) {
            m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, strength), this);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0), this);
            m.heal(6.0F);
            if (getTarget() != null && m.getTarget() == null) m.setTarget(getTarget());
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0), this);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0), this);
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 5, 0.4, 0.2, 0.4, 0);
        if (isPhase2()) callReinforcements(sl);
    }

    private void callReinforcements(ServerLevel sl) {
        int n = Math.min(2, MAX_REINFORCEMENTS - reinforcements);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(getX() + Math.cos(a) * 6);
            int z = (int) Math.floor(getZ() + Math.sin(a) * 6);
            BlockPos pos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, (int) getY(), z));
            if (Math.abs(pos.getY() - getY()) > 6) pos = blockPosition();
            EntityType<? extends CbMonster> type = random.nextInt(3) == 0 ? CbRegistries.ORC_BERSERKER.get() : CbRegistries.ORC.get();
            CbMonster orc = type.spawn(sl, pos, MobSpawnType.MOB_SUMMONED);
            if (orc == null) continue;
            reinforcements++;
            if (getTarget() != null) orc.setTarget(getTarget());
            sl.sendParticles(ParticleTypes.POOF, orc.getX(), orc.getY() + 1, orc.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, orc.getX(), orc.getY() + 0.2, orc.getZ(), 6, 0.4, 0.1, 0.4, 0.01);
        }
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playAttackSound() {
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Reinforcements", reinforcements);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        reinforcements = tag.getInt("Reinforcements");
    }
}
