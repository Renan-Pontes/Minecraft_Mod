package com.renanpontes.isekaimahou.feature.bosses_d.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * "Sentinela da Provação": elite com escudo de torre que BLOQUEIA ataques à distância vindos da frente (flanqueie,
 * lute de perto ou use magias sem projétil). Golpes: Corte em cone (curto), Impacto em área e Investida em linha —
 * todos telegrafados. Se a investida bate numa parede, fica atordoada e sem escudo por 2,5 s.
 * Tingida pelo elemento gravado em {@code isekai_mahou.trial_element} (rank em {@code isekai_mahou.trial_rank}).
 */
public class TrialSentinel extends Monster implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_ELEMENT = SynchedEntityData.defineId(TrialSentinel.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_STUNNED = SynchedEntityData.defineId(TrialSentinel.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.trial_sentinel.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.trial_sentinel.walk");
    private static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("animation.trial_sentinel.stunned");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.trial_sentinel.death");
    private static final List<String> ACTIONS = List.of("swing", "slam", "charge", "block", "spawn");
    private static final float SHIELD_ARC = 70F;
    private static final int DEATH_TICKS = 50;

    private enum Move { SWING, SLAM, CHARGE }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean configured;
    private Element element = Element.ARCANE;
    private int rank = 1;
    @Nullable private Move move;
    private int moveTick;
    private int pause = 20;
    private final Map<Move, Long> readyAt = new HashMap<>();
    private int spawnTicks;
    private int stunTicks;
    private int leapTicks;
    private int unreachableTicks;
    private Vec3 chargeDir = new Vec3(0, 0, 1);
    private int chargeWarm;
    private boolean chargeDone;
    private final Set<UUID> chargeHit = new HashSet<>();
    private final Map<UUID, Long> hintAt = new HashMap<>();
    private boolean deathHandled;
    @Nullable private String lastAction;

    public TrialSentinel(EntityType<? extends TrialSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 36.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ELEMENT, Element.ARCANE.id);
        builder.define(DATA_STUNNED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, TrialAvatar.class, TrialSentinel.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    public Element element() {
        Element e = Element.byId(entityData.get(DATA_ELEMENT));
        return e != null ? e : Element.ARCANE;
    }

    public boolean isStunned() {
        return entityData.get(DATA_STUNNED);
    }

    /** Cor do brilho (runas, viseira, escudo). */
    public int glowRgb() {
        return TrialElement.lighten(element().color, 0.2F);
    }

    /** Tinta do corpo: pedra clara puxada para a cor do elemento. */
    public int bodyRgb() {
        return TrialElement.mix(0xFFFFFF, element().color, 0.35F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BdConfigured", configured);
        tag.putString("BdElement", element.id);
        tag.putInt("BdRank", rank);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = tag.getBoolean("BdConfigured");
        if (configured) {
            Element e = Element.byId(tag.getString("BdElement"));
            element = e != null ? e : Element.ARCANE;
            rank = Mth.clamp(tag.getInt("BdRank"), 1, 7);
            entityData.set(DATA_ELEMENT, element.id);
            xpReward = 10 + 5 * rank;
        }
    }

    private void configure() {
        configured = true;
        element = TrialElement.readElement(this);
        rank = TrialElement.readRank(this);
        entityData.set(DATA_ELEMENT, element.id);
        setBase(Attributes.MAX_HEALTH, 60 + 30 * rank);
        setBase(Attributes.ARMOR, Math.min(16, 6 + rank));
        setBase(Attributes.ARMOR_TOUGHNESS, 1 + rank * 0.5);
        setBase(Attributes.ATTACK_DAMAGE, 4 + 1.5 * rank);
        setHealth(getMaxHealth());
        xpReward = 10 + 5 * rank;
        BossKit.scaleToParty(this, 48);
        spawnTicks = 42;
        if (level() instanceof ServerLevel sl) {
            ElementFx.burst(sl, position().add(0, 1, 0), element, 30, 0.8);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
    }

    private void setBase(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        AttributeInstance a = getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }

    // ---------------- regras ----------------
    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    /** Não some no Pacífico (uma Provação em andamento não pode perder o chefe/elite). */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        this.fallDistance = 0;
    }

    @Override
    public boolean fireImmune() {
        return element().matches(Element.FIRE) || super.fireImmune();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof TrialAvatar || other instanceof TrialSentinel) return true;
        // Lacaios invocados por um Avatar também são aliados da Sentinela
        if (other.getPersistentData().hasUUID(TrialAvatar.MINION_KEY)) return true;
        if (BossKit.isTrialBoss(other)) return true;
        return super.isAlliedTo(other);
    }

    /** Controles duram no máx. 2 s (a elite não fica presa o tempo todo). */
    @Override
    public boolean addEffect(MobEffectInstance inst, @Nullable Entity source) {
        if (TrialAvatar.isCrowdControl(inst.getEffect()) && inst.getDuration() > 40) {
            inst = new MobEffectInstance(inst.getEffect(), 40, Math.min(inst.getAmplifier(), 1), inst.isAmbient(), inst.isVisible(),
                    inst.showIcon());
        }
        return super.addEffect(inst, source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.is(DamageTypes.IN_WALL)) {
            // Presa num bloco (investida, salto): reaparece num espaço livre perto
            for (int i = 0; i < 12; i++) {
                double x = getX() + (random.nextDouble() - 0.5) * 6;
                double z = getZ() + (random.nextDouble() - 0.5) * 6;
                if (randomTeleport(x, getY() + random.nextInt(3), z, true)) break;
            }
            return false;
        }
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && isAlliedTo(attacker)) return false;
        if (!isStunned() && blockedByShield(source)) {
            shieldBlock((ServerLevel) level(), source);
            return false;
        }
        // Atordoada (bateu na parede): sem escudo e leva mais dano
        if (isStunned()) amount *= 1.5F;
        return super.hurt(source, amount);
    }

    /** Projétil (ou dano indireto) vindo da frente, dentro do arco do escudo. */
    private boolean blockedByShield(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || (direct != null && causing != null && direct != causing);
        if (!ranged) return false;
        Vec3 from = direct != null ? direct.position() : source.getSourcePosition();
        if (from == null) return false;
        double dx = from.x - getX(), dz = from.z - getZ();
        if (dx * dx + dz * dz < 1.0E-3) return false;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        return Math.abs(Mth.wrapDegrees(toward - yBodyRot)) <= SHIELD_ARC;
    }

    private void shieldBlock(ServerLevel sl, DamageSource source) {
        double yaw = Math.toRadians(yBodyRot);
        Vec3 front = position().add(-Math.sin(yaw) * 0.8, 1.2, Math.cos(yaw) * 0.8);
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, front.x, front.y, front.z, 10, 0.3, 0.4, 0.3, 0.2);
        sl.sendParticles(ElementFx.dust(element(), 1.2F), front.x, front.y, front.z, 8, 0.3, 0.4, 0.3, 0);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2F, 0.7F);
        playAction("block");
        if (source.getEntity() instanceof ServerPlayer p) {
            long now = level().getGameTime();
            if (now >= hintAt.getOrDefault(p.getUUID(), 0L)) {
                hintAt.put(p.getUUID(), now + 200);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.trial_sentinel.blocked"), true);
            }
        }
    }

    // ---------------- tick ----------------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (!configured) configure();
        if (spawnTicks > 0) {
            // Dispara a animação de surgimento 2 ticks depois de aparecer (o cliente já conhece a entidade)
            if (spawnTicks == 40) playAction("spawn");
            spawnTicks--;
            getNavigation().stop();
            return;
        }
        if (stunTicks > 0) {
            getNavigation().stop();
            if (stunTicks % 6 == 0) sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.2, getZ(), 3, 0.3, 0.1, 0.3, 0.05);
            if (--stunTicks == 0) entityData.set(DATA_STUNNED, false);
            return;
        }
        if (leapTicks > 0) {
            leapTicks--;
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            target = null;
        }
        if (target == null) {
            move = null;
            return;
        }
        if (move != null) {
            tickMove(sl, target);
            return;
        }
        if (antiCheese(sl, target)) return;
        chase(target);
        if (pause > 0) {
            pause--;
            return;
        }
        Move next = pick(target);
        if (next != null) startMove(next);
    }

    private void chase(LivingEntity target) {
        double d = distanceTo(target);
        // Caminho recalculado no máx. a cada 8 ticks (A* todo tick pesa com várias Sentinelas)
        if (d > 2.6) {
            if (getNavigation().isDone() || tickCount % 8 == 0) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
        // Gira devagar para o alvo: dá para flanquear o escudo
        float want = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
        float yaw = Mth.approachDegrees(yBodyRot, want, 9F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = Mth.approachDegrees(yHeadRot, want, 20F);
    }

    @Nullable
    private Move pick(LivingEntity target) {
        long now = level().getGameTime();
        double d = distanceTo(target);
        if (d <= 3.6 && now >= readyAt.getOrDefault(Move.SWING, 0L)) {
            if (d <= 3.0 && now >= readyAt.getOrDefault(Move.SLAM, 0L) && random.nextInt(3) == 0) return Move.SLAM;
            return Move.SWING;
        }
        if (d <= 5.5 && now >= readyAt.getOrDefault(Move.SLAM, 0L)) return Move.SLAM;
        if (d >= 5 && d <= 18 && now >= readyAt.getOrDefault(Move.CHARGE, 0L) && hasLineOfSight(target)) return Move.CHARGE;
        return null;
    }

    private void startMove(Move m) {
        move = m;
        moveTick = 0;
        int cd = switch (m) {
            case SWING -> 26;
            case SLAM -> 100;
            case CHARGE -> 140;
        };
        readyAt.put(m, level().getGameTime() + cd);
        playAction(switch (m) {
            case SWING -> "swing";
            case SLAM -> "slam";
            case CHARGE -> "charge";
        });
    }

    private void endMove(int pauseTicks) {
        move = null;
        pause = pauseTicks;
    }

    private void playAction(String anim) {
        lastAction = anim;
        triggerAnim("action", anim);
    }

    private float power() {
        return (3.0F + 1.4F * rank) * BossKit.damageScale(this);
    }

    private DamageSource spellSource() {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, this, this);
    }

    private boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand || isAlliedTo(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e == getTarget() || e == getLastHurtByMob()) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        return e instanceof Mob m && m.getTarget() == this;
    }

    private void strike(List<LivingEntity> hits, float mult, Vec3 from, double push) {
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            e.hurt(spellSource(), power() * mult);
            Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
            d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
            e.push(d.x * push, 0.3, d.z * push);
            e.hurtMarked = true;
            elementFlavor(e);
        }
    }

    private void elementFlavor(LivingEntity e) {
        Element el = TrialElement.body(element());
        switch (el) {
            case FIRE -> e.igniteForSeconds(3);
            case WATER -> e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), this);
            case EARTH -> e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            case LIGHT -> e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), this);
            case SHADOW -> e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
            default -> { }
        }
    }

    private void tickMove(ServerLevel sl, LivingEntity target) {
        int t = moveTick++;
        int rgb = element().color;
        switch (move) {
            case SWING -> {
                getNavigation().stop();
                if (t == 0) {
                    faceToward(target);
                    Vec3 o = position();
                    float yaw = yBodyRot;
                    Telegraph.delayed(sl, Telegraph.Shape.cone(o, yaw, 110F, 3.8F).warmup(10).color(rgb).owner(this).noRunes(), hits -> {
                        strike(hits, 1.0F, o, 0.6);
                        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, o.x - Math.sin(Math.toRadians(yaw)) * 1.8, o.y + 1.1,
                                o.z + Math.cos(Math.toRadians(yaw)) * 1.8, 1, 0, 0, 0, 0);
                        sl.playSound(null, o.x, o.y, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.7F);
                    });
                }
                if (t >= 20) endMove(10);
            }
            case SLAM -> {
                getNavigation().stop();
                if (t == 0) {
                    faceToward(target);
                    Vec3 c = position();
                    Telegraph.delayed(sl, Telegraph.Shape.circle(c, 4F).warmup(20).color(rgb).owner(this), hits -> {
                        strike(hits, 1.3F, c, 0.9);
                        for (LivingEntity e : hits) if (isFoe(e)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), this);
                        ElementFx.ring(sl, c.add(0, 0.1, 0), element(), 2.0, 20);
                        ElementFx.ring(sl, c.add(0, 0.1, 0), element(), 4.0, 32);
                        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.4, c.z, 1, 0, 0, 0, 0);
                        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
                        BossKit.shake(sl, c, 12, 0.7F, 10);
                    });
                }
                if (t >= 34) endMove(14);
            }
            case CHARGE -> tickCharge(sl, target, t, rgb);
            default -> endMove(10);
        }
    }

    private void tickCharge(ServerLevel sl, LivingEntity target, int t, int rgb) {
        getNavigation().stop();
        if (t == 0) {
            Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
            chargeDir = d.lengthSqr() < 1.0E-4 ? getLookAngle().multiply(1, 0, 1).normalize() : d.normalize();
            chargeWarm = 18;
            chargeDone = false;
            chargeHit.clear();
            faceDir(chargeDir);
            Telegraph.show(sl, Telegraph.Shape.line(position(), position().add(chargeDir.scale(14)), getBbWidth() + 1.0F)
                    .warmup(chargeWarm).color(rgb).owner(this));
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        faceDir(chargeDir);
        if (t < chargeWarm) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (t % 3 == 0) sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 3, 0.3, 0.05, 0.3, 0.02);
            return;
        }
        if (!chargeDone) {
            Vec3 step = chargeDir.scale(14.0 / 16);
            move(MoverType.SELF, step);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), this::isFoe)) {
                if (!chargeHit.add(e.getUUID())) continue;
                e.hurt(spellSource(), power() * 1.2F);
                Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
                if (side.dot(e.position().subtract(position())) < 0) side = side.scale(-1);
                Vec3 k = side.add(chargeDir.scale(0.8)).normalize();
                e.push(k.x * 1.2, 0.45, k.z * 1.2);
                e.hurtMarked = true;
                elementFlavor(e);
            }
            sl.sendParticles(ElementFx.dust(element(), 1.2F), getX(), getY() + 1.0, getZ(), 4, 0.4, 0.5, 0.4, 0);
            if (horizontalCollision) {
                // Bateu na parede: atordoada e sem escudo
                chargeDone = true;
                stunTicks = 50;
                entityData.set(DATA_STUNNED, true);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.4F, 0.6F);
                BossKit.shake(sl, position(), 12, 0.8F, 10);
                endMove(10);
                return;
            }
            if (t >= chargeWarm + 16) chargeDone = true;
        }
        if (t >= 48) endMove(16);
    }

    private void faceToward(LivingEntity t) {
        Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
        if (d.lengthSqr() > 1.0E-4) faceDir(d.normalize());
    }

    private void faceDir(Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Alvo inalcançável por 2 s: salta até ele (pouso telegrafado) ou teleporta perto. */
    private boolean antiCheese(ServerLevel sl, LivingEntity target) {
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < 40) return false;
        unreachableTicks = 0;
        double dy = target.getY() - getY();
        double flat = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (onGround() && target.onGround() && distanceTo(target) < 18 && !(dy > 6 && flat < 3)) {
            int t = BossKit.leapAt(this, target, 1.0);
            leapTicks = t;
            Vec3 land = target.position();
            Telegraph.delayed(sl, Telegraph.Shape.circle(land, 3F).warmup(Math.max(10, t)).color(element().color).owner(this),
                    hits -> strike(hits, 1.0F, land, 0.8));
            return true;
        }
        if (BossKit.blinkNear(this, target, 3)) {
            leapTicks = 10;
            return true;
        }
        return false;
    }

    // ---------------- cliente ----------------
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && isAlive() && tickCount % 4 == 0) {
            double yaw = Math.toRadians(yBodyRot);
            level().addParticle(ElementFx.dust(element(), 0.8F), getX() - Math.sin(yaw) * 0.3, getY() + 1.4, getZ() + Math.cos(yaw) * 0.3,
                    0, 0.01, 0);
        }
    }

    // ---------------- morte ----------------
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel) || !this.dead || deathHandled) return;
        deathHandled = true;
        move = null;
        // A animação de golpe em andamento cobriria a de morte: interrompe
        if (lastAction != null) stopTriggeredAnim("action", lastAction);
        Telegraph.cancelAll(this);
        BossRewards.grant(this, 25 + 10 * rank, 32);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        var item = CoreRegistries.ESSENCES.get(TrialElement.body(element()));
        if (item != null) spawnAtLocation(new ItemStack(item.get(), 1 + rank / 3));
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            if (deathTime % 3 == 0) {
                sl.sendParticles(ElementFx.dust(element(), 1.2F), getX(), getY() + 1.0, getZ(), 4, 0.5, 0.6, 0.5, 0);
            }
            if (deathTime == 28) {
                ElementFx.burst(sl, position().add(0, 1.2, 0), element(), 40, 0.8);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.2F, 0.8F);
            }
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                level().broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.0, 1.2, 1.0);
    }

    // ---------------- GeckoLib ----------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<TrialSentinel> base = new AnimationController<>(this, "base", 5, this::baseState);
        AnimationController<TrialSentinel> action = new AnimationController<>(this, "action", 2, s -> PlayState.STOP);
        for (String a : ACTIONS) action.triggerableAnim(a, RawAnimation.begin().thenPlay("animation.trial_sentinel." + a));
        controllers.add(base, action);
    }

    private PlayState baseState(AnimationState<TrialSentinel> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        if (isStunned()) return state.setAndContinue(STUNNED);
        double dx = getX() - xo, dz = getZ() - zo;
        return state.setAndContinue(dx * dx + dz * dz > 2.5E-4 ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
