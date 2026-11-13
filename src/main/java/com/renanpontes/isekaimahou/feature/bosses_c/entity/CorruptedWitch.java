package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;
import com.renanpontes.isekaimahou.feature.bosses_c.polish.BossShow;

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
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Bruxa Corrompida (Cabana da Bruxa Anciã):
 * <ul>
 *   <li><b>Maldições engarrafadas</b> em arco: o ponto de queda fica marcado no chão (4 tipos; na fase 2 deixam nuvem);</li>
 *   <li><b>Caldeirão Venenoso</b>: mexe o caldeirão e marca poças sob os jogadores (e em volta) que borbulham,
 *       estouram e continuam envenenando quem pisar nelas;</li>
 *   <li><b>Conciliábulo</b>: invoca bruxas e gatos pretos amaldiçoados;</li>
 *   <li><b>Voo na vassoura</b>: sobe e circula sobre o alvo bombardeando (mais frequente e longo na fase 2);</li>
 *   <li>bebe uma poção de cura quando está fraca (uma vez a cada ~30 s).</li>
 * </ul>
 * Deixa Páginas de Receita (ritual da Bruxa) se o módulo existir.
 */
public class CorruptedWitch extends ClassBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(CorruptedWitch.class, EntityDataSerializers.BOOLEAN);
    private static final int COLOR = 0xC04AC8;
    private static final int POOL_COLOR = 0x7CFF4A;
    private static final int MAX_MINIONS = 6;
    private static final int CAULDRON_CAST = 20;
    private static final int POOL_WARMUP = 30;
    private static final int POOL_HOLD = 100;

    private int bottleCd = 30;
    private int summonCd = 200;
    private int flightCd = 300;
    private int cauldronCd = 160;
    private int flightTicks;
    private int drinkCd;
    private int drinkTimer;
    private int throwTimer;
    private int cauldronTimer;
    @Nullable
    private LivingEntity throwTarget;
    private double orbit;
    private int orbitDir = 1;
    /** Centro do voo quando não há alvo (onde decolou): sem isso ela subiria sem parar. */
    @Nullable
    private Vec3 flightAnchor;
    private int stuckTicks;

    public CorruptedWitch(EntityType<? extends CorruptedWitch> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(180.0, 4.0, 0.28, 3.0)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    public String bossId() {
        return "corrupted_witch";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PINK;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("throw", "summon", "drink", "cauldron");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLYING, false);
    }

    public boolean isFlying() {
        return entityData.get(DATA_FLYING);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("broom", "bottle");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return switch (bone) {
            case "broom" -> isFlying();
            case "bottle" -> !isDeadOrDying();
            default -> true;
        };
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0 && isFlying()) return loop("fly");
        return super.mainAnimation(moving);
    }

    /** Imune às próprias maldições (veneno, fome, corrupção). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.POISON) || effect.is(MobEffects.HUNGER) || effect.is(CoreRegistries.CORRUPTED)) return false;
        return super.canBeAffected(effect);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return throwTimer > 0 || drinkTimer > 0 || cauldronTimer > 0 || isFlying();
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        boolean p2 = phase() >= 1;
        if (bottleCd > 0) bottleCd--;
        if (summonCd > 0) summonCd--;
        if (flightCd > 0) flightCd--;
        if (drinkCd > 0) drinkCd--;
        if (cauldronCd > 0) cauldronCd--;

        if (isFlying()) {
            tickFlight(level, target);
            return;
        }
        if (drinkTimer > 0) {
            getNavigation().stop();
            if (--drinkTimer == 0) {
                heal(25.0F);
                playSound(SoundEvents.GENERIC_DRINK, 1.0F, 0.9F);
                level.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.2, getZ(), 5, 0.4, 0.2, 0.4, 0);
                level.sendParticles(BossFx.dust(0xF82423, 1.2F), getX(), getY() + 1.2, getZ(), 15, 0.4, 0.6, 0.4, 0);
            }
            return;
        }
        if (throwTimer > 0) {
            getNavigation().stop();
            if (throwTarget != null) getLookControl().setLookAt(throwTarget, 30F, 30F);
            if (--throwTimer == 0 && throwTarget != null && throwTarget.isAlive()) lobBottle(level, throwTarget, p2);
            return;
        }
        if (cauldronTimer > 0) {
            tickCauldron(level);
            return;
        }
        if (target == null) return;

        if (drinkCd <= 0 && getHealth() < getMaxHealth() * 0.35F) {
            drinkTimer = 22;
            drinkCd = 600;
            action("drink");
            announce("drink");
            playSound(SoundEvents.WITCH_DRINK, 1.2F, 0.8F);
            return;
        }
        if (summonCd <= 0 && countMinions(level, 32) < MAX_MINIONS) {
            summonCoven(level, target, p2);
            return;
        }
        double dist = distanceTo(target);
        if (flightCd <= 0 && (p2 || dist < 3.0 || random.nextInt(4) == 0)) {
            if (hasRoomToFly(level)) {
                takeOff(level, p2 ? 220 : 140);
                return;
            }
            // Sob um teto (dentro da cabana): tenta de novo daqui a pouco, quando a luta sair para fora.
            flightCd = 60;
        }
        if (cauldronCd <= 0 && dist < 20 && hasLineOfSight(target)) {
            startCauldron(level, target, p2);
            return;
        }
        if (bottleCd <= 0 && hasLineOfSight(target) && dist < 18) {
            startThrow(target, p2 ? 30 : 45);
            return;
        }
        kite(target, 5.0, 11.0, 1.0);
    }

    private void startThrow(LivingEntity target, int cooldown) {
        throwTarget = target;
        throwTimer = 8;
        bottleCd = cooldown + random.nextInt(15);
        action("throw");
    }

    /**
     * Arremessa uma garrafa em arco até um pouco à frente do alvo. O ponto de queda é marcado no chão na hora do
     * arremesso, com o tempo exato do voo: dá para sair da marca. Sob um teto o arco é mais baixo (não some no telhado).
     */
    private void lobBottle(ServerLevel level, LivingEntity target, boolean p2) {
        CursedBottle.Curse curse = CursedBottle.Curse.values()[random.nextInt(CursedBottle.Curse.values().length)];
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1, getZ());
        double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        int flight = Mth.clamp((int) (10 + dist * 0.9), 14, 30);
        // Antecipa metade do caminho que o alvo faria andando no mesmo ritmo (máx. 4 blocos).
        Vec3 moved = new Vec3(target.getX() - target.xo, 0, target.getZ() - target.zo);
        Vec3 lead = moved.scale(flight * 0.5);
        if (lead.length() > 4.0) lead = lead.normalize().scale(4.0);
        Vec3 landing = groundAt(level, target.position().add(lead));
        // Sob um teto (dentro da cabana) o arco fica mais baixo e rápido, sem atravessar o telhado (aviso mín. 10 ticks).
        flight = CursedBottle.clearFlight(level, this, from, landing, flight, 10);
        CursedBottle b = CursedBottle.lob(this, curse, from, landing, flight);
        level.addFreshEntity(b);
        float dmg = scaled(2.5F);
        Telegraph.delayed(level, Telegraph.Shape.circle(landing, CursedBottle.SPLASH_RADIUS).warmup(flight).color(curse.mark).owner(this),
                hits -> CursedBottle.splash(level, this, curse, p2, landing, hits, dmg));
        playSound(SoundEvents.WITCH_THROW, 1.2F, 0.8F + random.nextFloat() * 0.3F);
    }

    // ------------------------------------------------------------------ Caldeirão Venenoso

    private void startCauldron(ServerLevel level, LivingEntity target, boolean p2) {
        cauldronTimer = CAULDRON_CAST;
        cauldronCd = (p2 ? 240 : 340) + random.nextInt(40);
        getNavigation().stop();
        action("cauldron");
        announce("cauldron");
        playSound(SoundEvents.BREWING_STAND_BREW, 2.0F, 0.6F);
        playSound(SoundEvents.WITCH_CELEBRATE, 1.6F, 0.7F);
        float r = p2 ? 3.0F : 2.6F;
        List<Vec3> spots = new ArrayList<>();
        // Uma poça embaixo de cada jogador (até 3)...
        for (ServerPlayer p : BossKit.partyAround(this, 24)) {
            if (spots.size() >= 3) break;
            if (!p.isCreative()) spots.add(groundAt(level, p.position()));
        }
        if (spots.isEmpty()) spots.add(groundAt(level, target.position()));
        // ...e outras em volta do alvo, para fechar as rotas de fuga.
        int extra = p2 ? 3 : 1;
        for (int i = 0; i < extra; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 4.5 + random.nextDouble() * 3.0;
            spots.add(groundAt(level, target.position().add(Math.cos(a) * d, 0, Math.sin(a) * d)));
        }
        for (Vec3 c : spots) poisonPool(level, c, r);
    }

    /** Mexendo o caldeirão: um círculo de vapor verde à frente dela. */
    private void tickCauldron(ServerLevel level) {
        cauldronTimer--;
        getNavigation().stop();
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 pot = position().add(look.scale(1.1)).add(0, 0.9, 0);
        double a = cauldronTimer * 0.6;
        level.sendParticles(BossFx.dust(POOL_COLOR, 1.1F), pot.x + Math.cos(a) * 0.5, pot.y, pot.z + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
        if (cauldronTimer % 3 == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, pot.x, pot.y + 0.2, pot.z, 4, 0.3, 0.1, 0.3, 0.02);
        if (cauldronTimer % 5 == 0) level.sendParticles(ParticleTypes.WITCH, pot.x, pot.y + 0.4, pot.z, 3, 0.25, 0.2, 0.25, 0.02);
        if (cauldronTimer == 0) playSound(SoundEvents.WITCH_CELEBRATE, 1.4F, 1.1F);
    }

    /** Poça venenosa: marca e borbulha, estoura no fim do aviso e continua envenenando por 5 s. */
    private void poisonPool(ServerLevel level, Vec3 c, float r) {
        float burst = scaled(4.0F);
        float tick = scaled(1.0F);
        Telegraph.delayed(level, Telegraph.Shape.circle(c, r).warmup(POOL_WARMUP).hold(POOL_HOLD).color(POOL_COLOR).owner(this), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                if (e.hurt(damageSources().indirectMagic(this, this), burst)) {
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
                    e.hurtMarked = true;
                }
            }
            level.sendParticles(BossFx.dust(POOL_COLOR, 1.8F), c.x, c.y + 0.4, c.z, 30, r * 0.4, 0.3, r * 0.4, 0);
            level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 40, r * 0.4, 0.2, r * 0.4, 0.2);
            level.sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.6, c.z, 15, r * 0.3, 0.4, r * 0.3, 0.05);
            BossShow.impact(level, c, POOL_COLOR, r * 0.5F); // v1.0: o estouro da poça tem peso
            level.playSound(null, c.x, c.y, c.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 1.0F, 0.8F);
        });
        // Borbulhando durante o aviso.
        for (int t = 4; t < POOL_WARMUP; t += 6) {
            BossKit.schedule(level, t, () -> {
                if (!isAlive()) return;
                level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.15, c.z, 5, r * 0.45, 0.05, r * 0.45, 0.01);
            });
        }
        // Veneno contínuo enquanto a poça dura.
        Telegraph.Shape zone = Telegraph.Shape.circle(c, r);
        for (int t = POOL_WARMUP + 10; t <= POOL_WARMUP + POOL_HOLD; t += 10) {
            boolean sound = t % 30 == 0;
            BossKit.schedule(level, t, () -> {
                if (!isAlive()) return;
                for (LivingEntity e : Telegraph.entitiesIn(level, zone)) {
                    if (!isFoe(e)) continue;
                    e.hurt(damageSources().indirectMagic(this, this), tick);
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                }
                level.sendParticles(BossFx.dust(POOL_COLOR, 1.0F), c.x, c.y + 0.15, c.z, 8, r * 0.45, 0.05, r * 0.45, 0);
                level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.15, c.z, 4, r * 0.45, 0.05, r * 0.45, 0.01);
                if (sound) level.playSound(null, c.x, c.y, c.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 0.8F, 0.6F);
            });
        }
    }

    private void summonCoven(ServerLevel level, LivingEntity target, boolean p2) {
        action("summon");
        announce("summon");
        playSound(SoundEvents.WITCH_CELEBRATE, 2.0F, 0.7F);
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 1.2F);
        int room = MAX_MINIONS - countMinions(level, 32);
        int cats = Math.min(room, p2 ? 3 : 2);
        int witches = Math.min(room - cats, p2 ? 2 : 1);
        for (int i = 0; i < cats + witches; i++) {
            EntityType<? extends Mob> type = i < cats ? BossesCRegistry.HEX_CAT.get() : EntityType.WITCH;
            Vec3 at = findGroundSpot(level, position(), 2.0, 5.0, type);
            if (at == null) continue;
            Mob m = type.create(level);
            if (m == null) continue;
            m.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            m.addTag(MINION_TAG);
            m.setTarget(target);
            if (m instanceof Witch w) w.setCanJoinRaid(false);
            level.addFreshEntity(m);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 20, 0.3, 0.4, 0.3, 0.02);
            level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.8, at.z, 20, 0.4, 0.5, 0.4, 0.05);
        }
        summonCd = p2 ? 320 : 420;
    }

    // ------------------------------------------------------------------ Voo na vassoura

    /** Só decola com céu livre acima (5 blocos): voar dentro da cabana a deixaria presa no teto. */
    private boolean hasRoomToFly(ServerLevel level) {
        return level.noCollision(this, getBoundingBox().expandTowards(0, 5, 0));
    }

    private void takeOff(ServerLevel level, int ticks) {
        entityData.set(DATA_FLYING, true);
        setNoGravity(true);
        flightTicks = ticks;
        orbit = random.nextDouble() * Math.PI * 2;
        orbitDir = random.nextBoolean() ? 1 : -1;
        flightAnchor = position();
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().add(0, 0.6, 0));
        hasImpulse = true;
        announce("fly");
        playSound(SoundEvents.WITCH_CELEBRATE, 1.8F, 1.1F);
        playSound(SoundEvents.PHANTOM_FLAP, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 20, 0.6, 0.2, 0.6, 0.05);
    }

    private void land() {
        entityData.set(DATA_FLYING, false);
        setNoGravity(false);
        flightAnchor = null;
        stuckTicks = 0;
        flightCd = phase() >= 1 ? 260 : 420;
        playSound(SoundEvents.PHANTOM_FLAP, 1.0F, 1.2F);
    }

    private void tickFlight(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        // Presa numa parede por 1 s seguido (arena apertada): desce da vassoura.
        stuckTicks = horizontalCollision ? stuckTicks + 1 : 0;
        if (--flightTicks <= 0 || isInWater() || stuckTicks > 20) {
            land();
            return;
        }
        if (target == null && flightAnchor == null) flightAnchor = position();
        Vec3 center = target != null ? target.position() : flightAnchor;
        orbit += 0.045 * orbitDir;
        double radius = 7.0;
        Vec3 want = center.add(Math.cos(orbit) * radius, 6.0 + Math.sin(tickCount * 0.12) * 0.6, Math.sin(orbit) * radius);
        // Não entra em paredes/tetos: se o ponto estiver ocupado, sobe ou desce um pouco.
        if (!level.noCollision(this, getBoundingBox().move(want.subtract(position())))) want = want.add(0, -2.5, 0);
        Vec3 delta = want.subtract(position());
        Vec3 vel = delta.scale(0.12);
        if (vel.length() > 0.5) vel = vel.normalize().scale(0.5);
        setDeltaMovement(getDeltaMovement().scale(0.5).add(vel.scale(0.5)));
        hasImpulse = true;
        if (target != null) {
            getLookControl().setLookAt(target, 30F, 30F);
            lookAt(target, 30F, 30F);
            if (bottleCd <= 0 && hasLineOfSight(target)) {
                lobBottle(level, target, phase() >= 1);
                action("throw");
                bottleCd = 24 + random.nextInt(10);
            }
        }
        if (tickCount % 2 == 0) {
            Vec3 tail = position().subtract(Vec3.directionFromRotation(0, yBodyRot).scale(1.0)).add(0, 0.6, 0);
            level.sendParticles(ParticleTypes.WITCH, tail.x, tail.y, tail.z, 1, 0.1, 0.1, 0.1, 0);
            level.sendParticles(BossFx.dust(0x7CFF4A, 0.9F), tail.x, tail.y, tail.z, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    /** Alvo fora de alcance (pilar, buraco): sobe na vassoura e bombardeia de cima; sem espaço, some e reaparece perto. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (isFlying()) return;
        if (flightCd <= 120 && hasRoomToFly(level)) {
            takeOff(level, 140);
        } else if (BossKit.blinkNear(this, target, 6.0)) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.0, getZ(), 25, 0.4, 0.8, 0.4, 0.05);
        }
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        summonCd = 0;
        flightCd = 60;
        cauldronCd = Math.min(cauldronCd, 100);
        playSound(SoundEvents.WITCH_CELEBRATE, 2.5F, 0.5F);
    }

    /** Morre no ar: cai da vassoura em vez de ficar flutuando. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isFlying()) {
            entityData.set(DATA_FLYING, false);
            setNoGravity(false);
        }
        super.die(source);
    }

    @Override
    protected void onReset(ServerLevel level) {
        if (isFlying()) land();
        bottleCd = 30;
        summonCd = 200;
        flightCd = 300;
        cauldronCd = 160;
        drinkTimer = throwTimer = cauldronTimer = 0;
        throwTarget = null;
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.WITCH_CELEBRATE, 2.5F, 0.8F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 3 == 0) {
            BossFx.ring(level, position().add(0, 0.1, 0), 2.5, 18, ParticleTypes.WITCH, 0.02);
            level.sendParticles(BossFx.dust(0x7CFF4A, 1.2F), getX(), getY() + 0.2, getZ(), 6, 1.5, 0.1, 1.5, 0);
        }
        if (t == 20 || t == 40) playSound(SoundEvents.WITCH_CELEBRATE, 2.0F, 0.6F + t * 0.01F);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropById("witch_recipe_page", 2, 3);
        dropItem(CoreRegistries.ESSENCES.get(Element.SHADOW).get(), 2, 3);
        dropItem(CoreRegistries.ESSENCES.get(Element.WATER).get(), 1, 2);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(BossFx.reduced() ? 10 : 3) == 0) {
            level().addParticle(ParticleTypes.WITCH, getRandomX(0.6), getY() + 2.4, getRandomZ(0.6), 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITCH_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = tag.getInt("FlightTicks");
        boolean flying = tag.getBoolean("Flying") && flightTicks > 0;
        entityData.set(DATA_FLYING, flying);
        setNoGravity(flying);
    }
}
