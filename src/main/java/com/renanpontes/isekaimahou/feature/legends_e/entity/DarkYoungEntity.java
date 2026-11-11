package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Filhote Sombrio (homenagem aos "Dark Young" de Lovecraft, domínio público): árvore-cabra de ~4 blocos, massa de
 * tentáculos com bocas que babam verde e cascos pretos. Nasce do selo da Cabra Negra e luta por quem conjurou por
 * 60 s: chicotadas de tentáculo, PISÕES em área telegrafados no chão (sem dono na telegrafia: não entram na Pilha) e um
 * rugido que enfraquece os inimigos. Nunca fere o dono nem os aliados dele. Temporário (não é salvo).
 */
public class DarkYoungEntity extends PathfinderMob implements OwnableEntity, GeoEntity {
    public static final int LIFE = 1200;
    private static final int EMERGE_TICKS = 40;
    private static final int STOMP_WARMUP = 20;
    private static final float STOMP_RADIUS = 5.5F;
    private static final int MAX_PER_OWNER = 6;
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("dark_young_power");

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dark_young.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dark_young.walk");
    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("animation.dark_young.spawn");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.dark_young.stomp");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.dark_young.roar");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.dark_young.attack");

    private static final EntityDataAccessor<Boolean> DATA_EMERGING = SynchedEntityData.defineId(DarkYoungEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = LIFE;
    private float power = 1F;
    private int emerge = EMERGE_TICKS;
    private int stompCooldown = 50;
    private int stompTicks = -1;
    private int roarCooldown = 0;
    private int orphanTicks;

    public DarkYoungEntity(EntityType<? extends DarkYoungEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.4)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    // =====================================================================================
    //  Invocação
    // =====================================================================================

    /** Brota um Filhote do chão em {@code pos} ligado ao conjurador (máx. 6 por dono: além disso renova o mais velho). */
    @Nullable
    public static DarkYoungEntity summon(SpellContext ctx, Vec3 pos, float power, int life) {
        ServerLevel level = ctx.level();
        LivingEntity owner = ctx.caster();
        UUID id = ctx.casterId();
        if (id != null) {
            List<DarkYoungEntity> mine = new ArrayList<>(level.getEntities(LeRegistry.DARK_YOUNG.get(), e -> e.isAlive() && id.equals(e.ownerId)));
            if (mine.size() >= MAX_PER_OWNER) {
                DarkYoungEntity oldest = mine.get(0);
                for (DarkYoungEntity d : mine) if (d.life < oldest.life) oldest = d;
                oldest.life = Math.max(oldest.life, life);
                oldest.heal(oldest.getMaxHealth());
                return oldest;
            }
        }
        DarkYoungEntity e = LeRegistry.DARK_YOUNG.get().create(level);
        if (e == null) return null;
        Vec3 p = LeUtil.groundBelow(level, pos.add(0, 1, 0), 8);
        e.moveTo(p.x, p.y, p.z, owner != null ? owner.getYRot() : level.random.nextFloat() * 360F, 0);
        e.setYHeadRot(e.getYRot());
        e.setYBodyRot(e.getYRot());
        e.setup(id, life, power);
        level.addFreshEntity(e);
        e.emergeFx(level);
        return e;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.SPAWN_EGG && ownerId == null) {
            Player p = level.getNearestPlayer(this, 8.0);
            setup(p != null ? p.getUUID() : null, LIFE, 1F);
            emergeFx(level.getLevel());
        }
        return out;
    }

    private void setup(@Nullable UUID owner, int life, float power) {
        this.ownerId = owner;
        this.life = life;
        this.power = power;
        if (power > 1.01F) {
            scale(getAttribute(Attributes.ATTACK_DAMAGE), power - 1F);
            // Vida cresce pouco: o Filhote continua abaixo da marca de "chefe" (150)
            scale(getAttribute(Attributes.MAX_HEALTH), Math.min(0.2F, 0.08F * (power - 1F)));
        }
        setHealth(getMaxHealth());
    }

    private static void scale(@Nullable AttributeInstance a, float amount) {
        if (a == null || a.getModifier(POWER_ID) != null || amount <= 0) return;
        a.addPermanentModifier(new AttributeModifier(POWER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    @Nullable
    public UUID getOwnerUUID() { return ownerId; }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (ownerId == null) return null;
        Player p = level().getPlayerByUUID(ownerId);
        if (p != null) return p;
        if (level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) return le;
        return null;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == null) return false;
        if (other == this) return true;
        if (ownerId != null) {
            if (ownerId.equals(other.getUUID())) return true;
            if (other instanceof OwnableEntity o && ownerId.equals(o.getOwnerUUID())) return true;
            LivingEntity owner = getOwner();
            if (owner != null && owner != other) {
                if (owner.isAlliedTo(other)) return true;
                if (owner instanceof Player && other instanceof Player && level().getServer() != null
                        && !level().getServer().isPvpAllowed()) return true;
            }
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isAlliedTo(target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (src != null && isAlliedTo(src) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new DefendOwnerGoal(this));
        targetSelector.addGoal(2, new AssistOwnerGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && !isAlliedTo(e)));
    }

    /** Saindo do chão ou no meio de um pisão: fica parado. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || entityData.get(DATA_EMERGING) || stompTicks >= 0;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isAlliedTo(target)) return false;
        boolean hit = super.doHurtTarget(target);
        triggerAnim("action", "attack");
        if (hit && level() instanceof ServerLevel sl) {
            if (target instanceof LivingEntity le) Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 80, this, power);
            LeFx.send(sl, ParticleTypes.SQUID_INK, LeUtil.center(target), 10, 0.3, 0.05);
            LeFx.sound(sl, target.position(), SoundEvents.SLIME_ATTACK, 1.2F, 0.5F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (emerge > 0 && --emerge == 0) {
            entityData.set(DATA_EMERGING, false);
            roar(sl);
        }
        if (--life <= 0) {
            vanish();
            return;
        }
        if (tickCount % 20 == 0) {
            LivingEntity owner = getOwner();
            if (ownerId != null && (owner == null || !owner.isAlive())) {
                orphanTicks += 20;
                if (orphanTicks >= 200) {
                    vanish();
                    return;
                }
            } else {
                orphanTicks = 0;
            }
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || isAlliedTo(t))) {
            setTarget(null);
            t = null;
        }
        if (stompCooldown > 0) stompCooldown--;
        if (roarCooldown > 0) roarCooldown--;
        if (stompTicks >= 0 && ++stompTicks > STOMP_WARMUP + 8) stompTicks = -1;
        if (t != null && !entityData.get(DATA_EMERGING) && stompTicks < 0 && stompCooldown <= 0 && onGround()
                && distanceToSqr(t) < 7.0 * 7.0) {
            startStomp(sl);
        } else if (t != null && roarCooldown <= 0 && stompTicks < 0 && distanceToSqr(t) < 10 * 10) {
            roar(sl);
        }
    }

    /** Empina e pisa: a área aparece no chão (telegrafia SEM dono — não é conjuração, não entra na Pilha). */
    private void startStomp(ServerLevel sl) {
        stompTicks = 0;
        stompCooldown = 100;
        getNavigation().stop();
        triggerAnim("action", "stomp");
        Vec3 at = position();
        LeFx.sound(sl, at, SoundEvents.RAVAGER_ROAR, 1.6F, 0.5F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.3F;
        Telegraph.delayed(sl, Telegraph.Shape.circle(at, STOMP_RADIUS).warmup(STOMP_WARMUP).color(0x6A2AA8).vertical(1.5, 2.0), hits -> {
            if (!isAlive()) return;
            LivingEntity owner = getOwner();
            for (LivingEntity e : hits) {
                if (e == this || e == owner || isAlliedTo(e) || !e.isAlive()) continue;
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (!(e instanceof Enemy) && !(e instanceof Player) && e != getTarget() && !(e instanceof Mob m && m.getTarget() != null
                        && (m.getTarget() == owner || isAlliedTo(m.getTarget())))) continue;
                e.hurt(damageSources().mobAttack(this), dmg);
                if (!Statuses.isBossLike(e)) {
                    Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
                    push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.8);
                    e.push(push.x, 0.65, push.z);
                    e.hurtMarked = true;
                }
                Statuses.apply(e, Statuses.Kind.STUNNED, 1, 16, this, power);
            }
            BlockState below = sl.getBlockState(BlockPos.containing(at.subtract(0, 0.2, 0)));
            if (below.isAir()) below = Blocks.DIRT.defaultBlockState();
            LeFx.send(sl, new BlockParticleOption(ParticleTypes.DUST_PILLAR, below), at.x, at.y + 0.1, at.z, 70, STOMP_RADIUS * 0.6, 0.1, STOMP_RADIUS * 0.6, 0.3);
            LeFx.send(sl, ParticleTypes.SQUID_INK, at.x, at.y + 0.3, at.z, 30, STOMP_RADIUS * 0.5, 0.2, STOMP_RADIUS * 0.5, 0.08);
            LeFx.ring(sl, at.add(0, 0.2, 0), new Vec3(0, 1, 0), STOMP_RADIUS + 1F, 0x8A4FC8, 12);
            LeFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F);
            LeFx.sound(sl, at, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.6F);
            BossKit.shake(sl, at, 20, 1.0F, 14);
            // O chão afunda sob os cascos (respeita a regra de destruição e o mobGriefing do dono)
            Destruction.surface(sl, at, 2.5F, owner != null ? owner : this, Destruction.Mode.CRUSH);
        });
    }

    /** Rugido: as bocas se abrem e os inimigos perto ficam lentos e fracos. */
    private void roar(ServerLevel sl) {
        roarCooldown = 300;
        triggerAnim("action", "roar");
        LeFx.sound(sl, position(), SoundEvents.WARDEN_ROAR, 2.0F, 0.7F);
        LeFx.sound(sl, position(), SoundEvents.GOAT_SCREAMING_AMBIENT, 2.0F, 0.4F);
        LivingEntity owner = getOwner();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(9),
                e -> e != this && e != owner && e.isAlive() && !isAlliedTo(e) && (e instanceof Enemy || e == getTarget()))) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
        }
        LeFx.send(sl, ParticleTypes.SCULK_SOUL, getX(), getY() + 3.2, getZ(), 20, 1.2, 0.8, 1.2, 0.05);
    }

    private void clientFx() {
        if (entityData.get(DATA_EMERGING)) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 2.2, getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * 2.2, 0, 0.15, 0);
            }
            return;
        }
        if (LeUtil.reducedParticles() || random.nextInt(6) != 0) return;
        // Baba verde escorrendo das bocas e névoa escura
        double yaw = Math.toRadians(yBodyRot);
        double fx = -Math.sin(yaw) * 0.45, fz = Math.cos(yaw) * 0.45;
        level().addParticle(ParticleTypes.ITEM_SLIME, getX() + fx, getY() + 2.5, getZ() + fz, 0, -0.05, 0);
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 2, getY() + 3.5 + random.nextDouble(),
                    getZ() + (random.nextDouble() - 0.5) * 2, 0, 0.02, 0);
        }
    }

    // =====================================================================================
    //  Entrada/saída de cena
    // =====================================================================================

    private void emergeFx(ServerLevel sl) {
        Vec3 c = position();
        LeFx.send(sl, ParticleTypes.SQUID_INK, c.x, c.y + 0.2, c.z, 60, 1.2, 0.2, 1.2, 0.15);
        LeFx.send(sl, ParticleTypes.SCULK_SOUL, c.x, c.y + 0.5, c.z, 20, 1.0, 0.5, 1.0, 0.05);
        LeFx.sound(sl, c, SoundEvents.WARDEN_EMERGE, 2.0F, 0.7F);
        LeFx.sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
    }

    /** Fim do tempo (ou dono foi embora): desfaz-se em lodo negro. */
    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position();
            LeFx.send(sl, ParticleTypes.SQUID_INK, c.x, c.y + 2, c.z, 80, 1.0, 1.6, 1.0, 0.08);
            LeFx.send(sl, LeFx.fade(0x2A0A3A, 0x000000, 1.5F), c.x, c.y + 2, c.z, 40, 1.0, 1.6, 1.0, 0.02);
            LeFx.sound(sl, c, SoundEvents.WARDEN_DIG, 1.8F, 0.6F);
        }
        discard();
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.GOAT_SCREAMING_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.6F);
    }

    @Override
    public float getVoicePitch() { return 0.45F; }

    @Override
    public int getAmbientSoundInterval() { return 200; }

    // =====================================================================================
    //  Regras de invocação
    // =====================================================================================

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_EMERGING, true);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (entityData.get(DATA_EMERGING)) return state.setAndContinue(SPAWN);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("stomp", STOMP)
                .triggerableAnim("roar", ROAR));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // =====================================================================================
    //  Objetivos de IA
    // =====================================================================================

    /** Segue o dono; muito longe, afunda e brota de novo perto dele. */
    static class FollowOwnerGoal extends Goal {
        private final DarkYoungEntity mob;
        private int recalc;

        FollowOwnerGoal(DarkYoungEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = mob.getOwner();
            return o != null && !o.isSpectator() && mob.getTarget() == null && mob.distanceToSqr(o) > 12 * 12;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = mob.getOwner();
            return o != null && mob.getTarget() == null && !mob.getNavigation().isDone() && mob.distanceToSqr(o) > 4 * 4;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() { mob.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity o = mob.getOwner();
            if (o == null) return;
            mob.getLookControl().setLookAt(o, 10.0F, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (mob.distanceToSqr(o) > 28 * 28 && mob.level() instanceof ServerLevel sl && o.level() == sl) {
                Vec3 p = LeUtil.groundBelow(sl, o.position().add(o.getLookAngle().multiply(-3, 0, -3)).add(0, 1, 0), 8);
                mob.moveTo(p.x, p.y, p.z, mob.getYRot(), 0);
                mob.getNavigation().stop();
                mob.emergeFx(sl);
            } else {
                mob.getNavigation().moveTo(o, 1.1);
            }
        }
    }

    /** Ataca quem machucou o dono. */
    static class DefendOwnerGoal extends TargetGoal {
        private final DarkYoungEntity young;
        private LivingEntity attacker;
        private int timestamp;

        DefendOwnerGoal(DarkYoungEntity young) {
            super(young, false);
            this.young = young;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = young.getOwner();
            if (owner == null) return false;
            attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != timestamp && attacker != null && !young.isAlliedTo(attacker)
                    && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            LivingEntity owner = young.getOwner();
            if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** Ataca o mesmo alvo do dono. */
    static class AssistOwnerGoal extends TargetGoal {
        private final DarkYoungEntity young;
        private LivingEntity victim;
        private int timestamp;

        AssistOwnerGoal(DarkYoungEntity young) {
            super(young, false);
            this.young = young;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = young.getOwner();
            if (owner == null) return false;
            if (owner instanceof Mob m && m.getTarget() != null && !young.isAlliedTo(m.getTarget())) {
                victim = m.getTarget();
                return victim != mob.getTarget() && canAttack(victim, TargetingConditions.DEFAULT);
            }
            victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != timestamp && victim != null && !young.isAlliedTo(victim)
                    && canAttack(victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(victim);
            LivingEntity owner = young.getOwner();
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
