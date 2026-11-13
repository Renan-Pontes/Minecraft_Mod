package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Arquimago Renegado (Torre do Mago Ancião). Luta à distância:
 * <ul>
 *   <li><b>Mísseis Arcanos</b>: rajadas teleguiadas (3, ou 5 na fase 2);</li>
 *   <li><b>Barragem Arcana</b>: marca linhas no chão em leque e dispara mísseis retos por elas (na fase 2, duas
 *       ondas cruzadas) — fique ENTRE as linhas;</li>
 *   <li><b>Escudo Arcano</b>: três círculos mágicos que absorvem dano até quebrar (quebrado, ele fica atordoado);</li>
 *   <li><b>Piscar</b>: teleporta para longe quando você fica colado nele ou bate muito seguido — no máximo duas
 *       vezes seguidas; depois fica sem fôlego por 10 s (janela para o corpo a corpo);</li>
 *   <li><b>Ilusões</b>: cópias frágeis que também atiram (sem sombra e sem olhos brilhantes: dá para reconhecer);</li>
 *   <li><b>Nova Arcana</b> (fase 2): explosão telegrafada que empurra quem estiver colado nele.</li>
 * </ul>
 * Deixa Notas de Pesquisa (ritual do Mago) e Pergaminhos de Glifos arcanos.
 */
public class RenegadeArchmage extends ClassBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(RenegadeArchmage.class, EntityDataSerializers.BOOLEAN);
    private static final int COLOR = 0xB98CFF;
    private static final int NOVA_WARMUP = 18;
    private static final float NOVA_RADIUS = 6.0F;
    private static final int BARRAGE_WARMUP = 24;
    /** Tick da barragem (fase 2) em que a segunda onda de linhas é marcada. */
    private static final int BARRAGE_SECOND = 16;
    private static final double BARRAGE_RANGE = 22.0;
    private static final float BARRAGE_WIDTH = 1.6F;
    private static final float[] BARRAGE_P1 = {-22F, 0F, 22F};
    private static final float[] BARRAGE_P2_A = {-36F, -18F, 0F, 18F, 36F};
    private static final float[] BARRAGE_P2_B = {-27F, -9F, 9F, 27F};
    /** Piscadas seguidas permitidas antes de ficar sem fôlego. */
    private static final int BLINK_STREAK_MAX = 2;
    private static final int BLINK_LOCK = 200;

    private float shieldHp;
    private int shieldTicks;
    private int missileCd = 40;
    private int shieldCd = 120;
    private int blinkCd;
    private int illusionCd = 260;
    /** Troca de Espelhos: recarga e o tempo do brilho de aviso (0 = nada em andamento). */
    private int mirrorCd = 200;
    private int mirrorTimer;
    private int novaCd = 100;
    private int barrageCd = 160;
    private int castTimer;
    private int novaTimer;
    private int barrageTimer;
    private int barrageAge;
    private float barrageYaw;
    /** Barragem dupla (fase 2), decidida no início: trocar de fase no meio não cria uma onda sem tempo de acabar. */
    private boolean barrageDouble;
    /** Muda a cada barragem: visuais agendados de uma barragem interrompida não aparecem. */
    private int barrageGen;
    private int stagger;
    private int recentHits;
    private int hitDecay;
    private int closeTicks;
    private int blinkStreak;
    private int lastBlinkTick = -10000;
    private int blinkLock;
    private boolean swappedLast;

    public RenegadeArchmage(EntityType<? extends RenegadeArchmage> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(200.0, 6.0, 0.27, 4.0)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public String bossId() {
        return "renegade_archmage";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cast", "shield", "summon", "nova", "teleport", "barrage");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD, false);
    }

    public boolean isShielded() {
        return entityData.get(DATA_SHIELD);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("shield");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return !bone.equals("shield") || isShielded();
    }

    @Override
    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shield", 0, state -> state.setAndContinue(loop("shield_spin"))));
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return castTimer > 0 || novaTimer > 0 || barrageTimer > 0 || stagger > 0 || mirrorTimer > 0;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        boolean p2 = phase() >= 1;
        if (missileCd > 0) missileCd--;
        if (shieldCd > 0) shieldCd--;
        if (blinkCd > 0) blinkCd--;
        if (illusionCd > 0) illusionCd--;
        if (mirrorCd > 0) mirrorCd--;
        if (novaCd > 0) novaCd--;
        if (barrageCd > 0) barrageCd--;
        if (hitDecay > 0 && --hitDecay == 0) recentHits = 0;
        tickShield(level);
        tickWeary(level);

        if (stagger > 0) {
            stagger--;
            getNavigation().stop();
            if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.4, getZ(), 3, 0.3, 0.1, 0.3, 0.0);
            return;
        }
        if (barrageTimer > 0) {
            tickBarrage(level);
            return;
        }
        if (target == null) return;
        if (mirrorTimer > 0) {
            tickMirror(level);
            return;
        }

        // Ataques em preparação
        if (castTimer > 0) {
            castTimer--;
            getNavigation().stop();
            getLookControl().setLookAt(target, 30F, 30F);
            lookAt(target, 30F, 30F);
            Vec3 hand = handPos();
            level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 4, 0.3, 0.3, 0.3, 0.6);
            level.sendParticles(BossFx.dust(COLOR, 1.0F), hand.x, hand.y, hand.z, 2, 0.15, 0.15, 0.15, 0);
            if (castTimer == 0) fireMissiles(level, target, p2 ? 5 : 3, scaled(p2 ? 4.5F : 4.0F));
            return;
        }
        if (novaTimer > 0) {
            novaTimer--;
            getNavigation().stop();
            BossFx.ring(level, position().add(0, 0.2, 0), 0.5 + novaTimer * 0.3, 20, BossFx.dust(COLOR, 1.3F), 0);
            return;
        }

        double dist = distanceTo(target);
        // Piscar: colado nele por quase 1 s, ou apanhando muito seguido — com limite de piscadas seguidas.
        closeTicks = dist < 3.5 ? closeTicks + 1 : 0;
        boolean pressed = closeTicks >= 16 || recentHits >= 4;
        if (pressed && blinkCd <= 0 && blinkLock <= 0) {
            if (p2 && novaCd <= 0 && dist < 5) {
                startNova(level);
                return;
            }
            blink(level, target);
            return;
        }
        if (shieldCd <= 0 && !isShielded()) {
            raiseShield(level, p2 ? 40F : 30F);
            return;
        }
        if (illusionCd <= 0 && countIllusions(level) < 2) {
            summonIllusions(level, target, p2 ? 3 : 2);
            return;
        }
        if (mirrorCd <= 0 && countIllusions(level) >= 1) {
            startMirror(level);
            return;
        }
        if (p2 && novaCd <= 0 && dist < 5) {
            startNova(level);
            return;
        }
        if (barrageCd <= 0 && dist > 4 && dist < BARRAGE_RANGE && hasLineOfSight(target)) {
            startBarrage(level, target);
            return;
        }
        if (missileCd <= 0 && hasLineOfSight(target)) {
            castTimer = 12;
            missileCd = (p2 ? 35 : 55) + random.nextInt(20);
            action("cast");
            playSound(SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.2F, 1.2F);
            return;
        }
        kite(target, 7.0, 14.0, 1.0);
    }

    private Vec3 handPos() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x);
        return position().add(look.scale(0.7)).add(side.scale(0.4)).add(0, 1.6, 0);
    }

    /** Ponta do cajado (mão direita, erguido na barragem). */
    private Vec3 staffTip() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x);
        return position().add(look.scale(0.4)).add(side.scale(-0.45)).add(0, 3.1, 0);
    }

    private void fireMissiles(ServerLevel level, LivingEntity target, int count, float damage) {
        Vec3 from = handPos();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        for (int i = 0; i < count; i++) {
            double spread = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * 1.4;
            Vec3 dir = to.add(side.scale(spread)).add(0, 0.35 + random.nextDouble() * 0.2, 0).normalize();
            ClassBossBolt.fire(this, ClassBossBolt.Kind.ARCANE, from, dir, 0.55, damage, 0.12, target, 1.0F, 90);
        }
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.4F, 1.3F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 10, 0.2, 0.2, 0.2, 0.08);
    }

    // ------------------------------------------------------------------ Barragem Arcana

    private void startBarrage(ServerLevel level, LivingEntity target) {
        boolean p2 = phase() >= 1;
        barrageYaw = yawTo(target.position());
        barrageAge = 0;
        barrageDouble = p2;
        barrageTimer = (p2 ? BARRAGE_SECOND : 0) + BARRAGE_WARMUP + 6;
        barrageCd = (p2 ? 200 : 280) + random.nextInt(40);
        barrageGen++;
        getNavigation().stop();
        holdYaw(barrageYaw);
        action("barrage");
        announce("barrage");
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.8F, 1.4F);
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
        markBarrageWave(level, p2 ? BARRAGE_P2_A : BARRAGE_P1);
    }

    private void tickBarrage(ServerLevel level) {
        barrageAge++;
        barrageTimer--;
        getNavigation().stop();
        holdYaw(barrageYaw);
        Vec3 tip = staffTip();
        level.sendParticles(BossFx.dust(COLOR, 1.2F), tip.x, tip.y, tip.z, 3, 0.15, 0.15, 0.15, 0);
        if (barrageAge % 3 == 0) level.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 6, 0.4, 0.4, 0.4, 0.8);
        if (barrageDouble && barrageAge == BARRAGE_SECOND) {
            action("cast");
            playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.6F, 1.6F);
            markBarrageWave(level, BARRAGE_P2_B);
        }
    }

    /** Marca uma onda de linhas em leque; quando cada uma enche, os mísseis passam por ela. */
    private void markBarrageWave(ServerLevel level, float[] angles) {
        Vec3 from = position();
        float dmg = scaled(6.0F);
        int gen = barrageGen;
        for (int i = 0; i < angles.length; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, barrageYaw + angles[i]);
            Vec3 start = from.add(dir.scale(1.0));
            Vec3 end = lineEnd(level, start, dir, BARRAGE_RANGE);
            boolean loud = i == angles.length / 2;
            Telegraph.delayed(level, Telegraph.Shape.line(start, end, BARRAGE_WIDTH).warmup(BARRAGE_WARMUP).color(COLOR).owner(this),
                    hits -> barrageHit(level, dir, hits, dmg, loud));
            // Os mísseis saem do cajado um pouco antes do fim e chegam junto com o golpe.
            for (int k = 0; k < 5; k++) {
                int step = k;
                BossKit.schedule(level, BARRAGE_WARMUP - 5 + k, () -> {
                    if (gen != barrageGen || barrageTimer <= 0 || !isAlive()) return;
                    double len = start.distanceTo(end);
                    Vec3 a0 = start.add(dir.scale(len * step / 5.0)).add(0, 1.1, 0);
                    Vec3 a1 = start.add(dir.scale(len * (step + 1) / 5.0)).add(0, 1.1, 0);
                    BossFx.line(level, a0, a1, BossFx.dust(COLOR, 1.3F), 1.6);
                    level.sendParticles(ParticleTypes.END_ROD, a1.x, a1.y, a1.z, 2, 0.08, 0.08, 0.08, 0.01);
                });
            }
        }
    }

    private void barrageHit(ServerLevel level, Vec3 dir, List<LivingEntity> hits, float dmg, boolean loud) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                e.knockback(0.45, -dir.x, -dir.z);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
            }
        }
        if (loud) {
            playSound(SoundEvents.EVOKER_CAST_SPELL, 1.8F, 1.5F);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.8F);
        }
    }

    // ------------------------------------------------------------------ Escudo

    private void raiseShield(ServerLevel level, float hp) {
        shieldHp = hp;
        shieldTicks = 240;
        entityData.set(DATA_SHIELD, true);
        action("shield");
        announce("shield");
        playSound(SoundEvents.BEACON_ACTIVATE, 1.5F, 1.4F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.8F);
        BossFx.sphere(level, position().add(0, 1.2, 0), 1.8, 50, BossFx.dust(COLOR, 1.2F));
    }

    private void tickShield(ServerLevel level) {
        if (!isShielded()) return;
        if (tickCount % 5 == 0) {
            double a = tickCount * 0.3;
            level.sendParticles(BossFx.dust(COLOR, 1.0F), getX() + Math.cos(a) * 1.6, getY() + 1.2 + Math.sin(a * 0.7) * 0.8,
                    getZ() + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
        }
        if (--shieldTicks <= 0) {
            entityData.set(DATA_SHIELD, false);
            shieldCd = phase() >= 1 ? 200 : 300;
            playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.4F);
        }
    }

    private void breakShield(ServerLevel level) {
        entityData.set(DATA_SHIELD, false);
        shieldHp = 0;
        stagger = 30;
        shieldCd = phase() >= 1 ? 220 : 320;
        castTimer = 0;
        novaTimer = 0;
        barrageTimer = 0;
        // Atordoado: as marcas no chão somem junto com o feitiço interrompido.
        Telegraph.cancelAll(this);
        playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
        playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 2.0F, 0.8F);
        BossFx.sphere(level, position().add(0, 1.2, 0), 1.9, 70, BossFx.dust(COLOR, 1.6F));
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 30, 0.8, 0.8, 0.8, 0.3);
        announce("shield_broken");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAwake() && introTicks() <= 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            ServerLevel level = (ServerLevel) level();
            if (source.getEntity() instanceof Player) {
                recentHits++;
                hitDecay = 60;
            }
            if (isShielded() && amount > 0) {
                float absorbed = Math.min(shieldHp, amount);
                shieldHp -= absorbed;
                Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position();
                Vec3 dir = at.subtract(position()).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
                Vec3 p = position().add(dir.scale(1.6)).add(0, 1.2, 0);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 10, 0.2, 0.3, 0.2, 0.2);
                playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, 0.7F + random.nextFloat() * 0.4F);
                if (shieldHp <= 0) {
                    breakShield(level);
                    float rest = amount - absorbed;
                    return rest > 0 && super.hurt(source, rest);
                }
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Piscar, ilusões e nova

    private void blink(ServerLevel level, LivingEntity target) {
        Vec3 from = position();
        if (blinkAround(target, 8.0, 13.0)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.2, from.z, 40, 0.4, 0.8, 0.4, 0.05);
            level.sendParticles(BossFx.dust(COLOR, 1.5F), from.x, from.y + 1.2, from.z, 25, 0.4, 0.8, 0.4, 0);
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.2, getZ(), 50, 0.4, 0.8, 0.4, 0.4);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, getSoundSource(), 1.3F, 1.2F);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.3F);
            action("teleport");
            // Piscadas seguidas cansam: depois da segunda ele fica um tempo sem conseguir fugir.
            if (tickCount - lastBlinkTick > 240) blinkStreak = 0;
            lastBlinkTick = tickCount;
            if (++blinkStreak >= BLINK_STREAK_MAX) {
                blinkStreak = 0;
                blinkLock = BLINK_LOCK;
                playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 0.5F);
                hintOnce(level, "weary");
            }
        }
        blinkCd = phase() >= 1 ? 70 : 100;
        recentHits = 0;
        closeTicks = 0;
    }

    /** Sem fôlego: fumacinha na cabeça enquanto não pode piscar. */
    private void tickWeary(ServerLevel level) {
        if (blinkLock <= 0) return;
        blinkLock--;
        if (blinkLock % 8 == 0 && blinkLock > BLINK_LOCK - 120) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.6, getZ(), 3, 0.2, 0.05, 0.2, 0.01);
            level.sendParticles(BossFx.dust(0x8A8396, 0.9F), getX(), getY() + 2.5, getZ(), 2, 0.25, 0.1, 0.25, 0);
        }
    }

    private int countIllusions(ServerLevel level) {
        return level.getEntitiesOfClass(ArchmageIllusion.class, getBoundingBox().inflate(40), i -> getUUID().equals(i.ownerId())).size();
    }

    private void summonIllusions(ServerLevel level, LivingEntity target, int count) {
        action("summon");
        announce("illusions");
        playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 2.0F, 1.0F);
        ArchmageIllusion swap = null;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + random.nextDouble() * 0.4;
            Vec3 at = findGroundSpot(level, position().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5), 0.0, 1.5,
                    BossesCRegistry.ARCHMAGE_ILLUSION.get());
            if (at == null) continue;
            ArchmageIllusion ill = BossesCRegistry.ARCHMAGE_ILLUSION.get().create(level);
            if (ill == null) continue;
            ill.moveTo(at.x, at.y, at.z, getYRot(), 0F);
            ill.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            ill.setOwner(this);
            ill.setTarget(target);
            level.addFreshEntity(ill);
            level.sendParticles(BossFx.dust(COLOR, 1.5F), at.x, at.y + 1.2, at.z, 20, 0.3, 0.8, 0.3, 0);
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.2, at.z, 30, 0.4, 0.8, 0.4, 0.5);
            if (swap == null || random.nextBoolean()) swap = ill;
        }
        hintOnce(level, "illusions");
        // Às vezes troca de lugar com uma ilusão — nunca duas vezes seguidas, para não virar adivinhação.
        if (swap != null && !swappedLast && random.nextFloat() < 0.35F) {
            Vec3 mine = position();
            Vec3 theirs = swap.position();
            teleportTo(theirs.x, theirs.y, theirs.z);
            swap.teleportTo(mine.x, mine.y, mine.z);
            playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.5F, 1.0F);
            swappedLast = true;
        } else {
            swappedLast = false;
        }
        illusionCd = phase() >= 1 ? 360 : 500;
    }

    /**
     * ASSINATURA — Troca de Espelhos: ele e as ilusões brilham juntos por quase 1 s (o aviso) e, num estalo de vidro,
     * TODOS trocam de lugar entre si. Quem estava batendo no Arquimago de verdade agora pode estar diante de uma cópia:
     * procure a sombra e os olhos brilhantes de novo.
     */
    private void startMirror(ServerLevel level) {
        mirrorTimer = MIRROR_WARN;
        mirrorCd = (phase() >= 1 ? 170 : 240) + random.nextInt(60);
        getNavigation().stop();
        action("cast");
        playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 2.0F, 1.3F);
        hintOnce(level, "mirror_swap");
    }

    private static final int MIRROR_WARN = 18;

    private void tickMirror(ServerLevel level) {
        mirrorTimer--;
        getNavigation().stop();
        List<ArchmageIllusion> copies = level.getEntitiesOfClass(ArchmageIllusion.class, getBoundingBox().inflate(40),
                i -> getUUID().equals(i.ownerId()) && i.isAlive());
        if (mirrorTimer > 0) {
            // Antecipação: todos cintilam por igual (não dá para saber quem é quem só pelo brilho)
            if (mirrorTimer % 3 == 0) {
                shimmer(level, position());
                for (ArchmageIllusion c : copies) shimmer(level, c.position());
            }
            return;
        }
        if (copies.isEmpty()) return;
        List<LivingEntity> all = new java.util.ArrayList<>(copies);
        all.add(this);
        List<Vec3> spots = new java.util.ArrayList<>();
        for (LivingEntity e : all) spots.add(e.position());
        // Embaralha garantindo que o Arquimago saia do lugar
        java.util.Collections.rotate(spots, 1 + random.nextInt(Math.max(1, spots.size() - 1)));
        for (int i = 0; i < all.size(); i++) {
            LivingEntity e = all.get(i);
            Vec3 to = spots.get(i);
            Vec3 from = e.position();
            BossFx.line(level, from.add(0, 1.2, 0), to.add(0, 1.2, 0), BossFx.dust(COLOR, 0.9F), 1.2);
            e.teleportTo(to.x, to.y, to.z);
            e.setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.FLASH, to.x, to.y + 1.2, to.z, 1, 0, 0, 0, 0);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()), to.x, to.y + 1.2, to.z, 24, 0.4, 0.8, 0.4, 0.2);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
        playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.9F);
        for (ServerPlayer p : BossKit.partyAround(this, 32)) BossKit.flash(p, 0xD8C8FF, 0.35F, 8);
    }

    private void shimmer(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.2, at.z, 3, 0.35, 0.8, 0.35, 0.01);
        level.sendParticles(BossFx.dust(0xE8E0FF, 1.0F), at.x, at.y + 1.2, at.z, 4, 0.4, 0.9, 0.4, 0);
    }

    private void startNova(ServerLevel level) {
        novaTimer = NOVA_WARMUP;
        novaCd = 160;
        getNavigation().stop();
        action("nova");
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.6F, 1.3F);
        Vec3 c = position();
        float dmg = scaled(9.0F);
        Telegraph.delayed(level, Telegraph.Shape.circle(c, NOVA_RADIUS).warmup(NOVA_WARMUP).color(COLOR).owner(this),
                hits -> arcaneNova(level, c, hits, dmg));
    }

    private void arcaneNova(ServerLevel level, Vec3 c, List<LivingEntity> hits, float dmg) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            double d = e.position().subtract(c).multiply(1, 0, 1).length();
            if (e.hurt(damageSources().indirectMagic(this, this), dmg * (float) (1.0 - Math.min(d, NOVA_RADIUS) / 12.0))) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : push.normalize();
                e.push(push.x * 1.5, 0.5, push.z * 1.5);
                e.hurtMarked = true;
            }
        }
        for (int k = 1; k <= 3; k++) BossFx.ring(level, c.add(0, 0.3 * k, 0), 2.0 * k, 16 * k, BossFx.dust(COLOR, 1.6F), 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 40, 0.5, 0.5, 0.5, 0.35);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
        BossKit.shake(level, c, 16, 0.8F, 10);
    }

    /** Alvo escondido (buraco, pilar sem visão): reaparece num ponto de onde enxerga o alvo. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (hasLineOfSight(target)) return;
        if (BossKit.blinkNear(this, target, 8.0)) {
            action("teleport");
            blinkCd = Math.max(blinkCd, 40);
        }
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        // Fase 2: escudo e ilusões imediatamente, e a barragem dupla logo em seguida.
        shieldCd = 0;
        illusionCd = 40;
        missileCd = 20;
        barrageCd = Math.min(barrageCd, 60);
    }

    @Override
    protected void onReset(ServerLevel level) {
        entityData.set(DATA_SHIELD, false);
        shieldHp = 0;
        castTimer = novaTimer = stagger = barrageTimer = 0;
        closeTicks = blinkStreak = blinkLock = 0;
        swappedLast = false;
        missileCd = 40;
        shieldCd = 120;
        illusionCd = 260;
        barrageCd = 160;
        for (ArchmageIllusion i : level.getEntitiesOfClass(ArchmageIllusion.class, getBoundingBox().inflate(48), i -> getUUID().equals(i.ownerId()))) {
            i.vanish();
        }
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.9F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        super.introTick(level, t);
        if (t == 45) {
            playSound(SoundEvents.EVOKER_CAST_SPELL, 2.0F, 0.8F);
            BossFx.ring(level, position().add(0, 0.2, 0), 3.0, 40, ParticleTypes.END_ROD, 0.02);
        }
        if (t < 40) level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 2.8, getZ(), 6, 0.6, 0.3, 0.6, 0.8);
    }

    @Override
    protected void onDefeated(ServerLevel level) {
        super.onDefeated(level);
        for (ArchmageIllusion i : level.getEntitiesOfClass(ArchmageIllusion.class, getBoundingBox().inflate(48), i -> getUUID().equals(i.ownerId()))) {
            i.vanish();
        }
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropById("research_note", 2, 3);
        dropArcaneScrolls(2);
        dropItem(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 3, 5);
        dropItem(CoreRegistries.MANA_CRYSTAL.get(), 2, 4);
    }

    /** Runas aos pés (as ilusões não têm: é um dos jeitos de achar o verdadeiro). */
    @Override
    protected void clientTick() {
        if (random.nextInt(BossFx.reduced() ? 12 : 4) == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.6), getY() + 0.2, getRandomZ(0.6), 0, 0.3, 0);
        }
        if (isShielded() && random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(BossFx.dust(COLOR, 0.8F), getX() + Math.cos(a) * 1.7, getY() + 0.3 + random.nextDouble() * 2.2,
                    getZ() + Math.sin(a) * 1.7, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ILLUSIONER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ILLUSIONER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ILLUSIONER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ShieldHp", isShielded() ? shieldHp : 0);
        tag.putInt("ShieldTicks", shieldTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shieldHp = tag.getFloat("ShieldHp");
        shieldTicks = tag.getInt("ShieldTicks");
        entityData.set(DATA_SHIELD, shieldHp > 0 && shieldTicks > 0);
    }
}
