package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * Cabeça da Falsa Hidra: um pescoço que sai do chão com a cabeça no alto. <b>Some quando alguém olha direto para ela</b>
 * (o servidor mede o olhar de todos e sincroniza; o cliente ainda esconde pelo olhar de quem está vendo) e, enquanto
 * está sendo vigiada, não começa ataques novos — dá para "segurar" uma cabeça com o olhar, mas aí você não a vê.
 * <ul>
 *   <li><b>Mordida</b> em cone curto (marca no chão);</li>
 *   <li><b>Bote</b> em linha (fase 2 do corpo em diante);</li>
 *   <li><b>Mergulho</b>: afunda e irrompe perto de um jogador (círculo marcado).</li>
 * </ul>
 * Morta, avisa o corpo (que sangra e emerge) e volta a crescer depois. Sem corpo (ovo), vive como cabeça solta.
 */
public class FalseHydraHead extends Monster implements GeoEntity, BgBones {
    private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(FalseHydraHead.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SUNK = SynchedEntityData.defineId(FalseHydraHead.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation PARTY_HP = IsekaiMahou.id("false_hydra_head_party");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.false_hydra_head.idle");
    private static final RawAnimation SUNK = RawAnimation.begin().thenLoop("animation.false_hydra_head.sunk");
    private static final RawAnimation SING = RawAnimation.begin().thenLoop("animation.false_hydra_head.sing");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.false_hydra_head.death");
    private static final double WATCH_DOT = 0.86;   // ~30° do centro da visão
    private static final double WATCH_RANGE = 40.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int attackCd = 50;
    private int attackTimer;
    private float attackYaw;
    private int diveCd = 220;
    private int diveTimer;
    private int lifeWithoutOwner;
    /** Só no cliente: quanto ela "sumiu" pelo olhar dos outros (0 visível .. 1 sumida), suavizado. */
    public float clientWatchFade;
    public float prevClientWatchFade;
    /** Só no cliente: o corpo por perto está cantando (atualizado a cada 10 ticks). */
    private boolean clientSinging;

    public FalseHydraHead(EntityType<? extends FalseHydraHead> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.FOLLOW_RANGE, 36.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Nasce uma cabeça do corpo no ponto dado (com a vida ajustada ao grupo da luta). */
    @Nullable
    public static FalseHydraHead spawn(ServerLevel level, FalseHydra body, Vec3 at) {
        FalseHydraHead head = BgRegistries.FALSE_HYDRA_HEAD.get().create(level);
        if (head == null) return null;
        head.moveTo(at.x, at.y, at.z, body.getYRot(), 0);
        head.ownerId = body.getUUID();
        head.addTag(GBossEntity.MINION_TAG);
        int n = BossKit.partySize(body);
        AttributeInstance hp = head.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && n > 1) {
            hp.addPermanentModifier(new AttributeModifier(PARTY_HP, 0.4 * (n - 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        head.setHealth(head.getMaxHealth());
        head.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(head);
        head.emergeFx(level);
        return head;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WATCHED, false);
        builder.define(DATA_SUNK, false);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this, FalseHydraHead.class, GBossEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public boolean isWatched() {
        return entityData.get(DATA_WATCHED);
    }

    public boolean isSunk() {
        return entityData.get(DATA_SUNK);
    }

    @Nullable
    private FalseHydra owner() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(ownerId);
        return e instanceof FalseHydra h && h.isAlive() ? h : null;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            prevClientWatchFade = clientWatchFade;
            float target = isWatched() ? 1F : 0F;
            clientWatchFade += (target - clientWatchFade) * 0.25F;
            if (tickCount % 10 == 0) {
                clientSinging = !level().getEntitiesOfClass(FalseHydra.class, getBoundingBox().inflate(28), FalseHydra::isSinging).isEmpty();
            }
            if (!isSunk() && clientWatchFade < 0.5F && random.nextInt(BgFx.reduced() ? 30 : 12) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() - 0.3, getZ(), 0, 0.02, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        FalseHydra body = owner();
        if (ownerId != null && body == null) {
            // O corpo morreu ou sumiu: a cabeça murcha junto (com tolerância para o chunk do corpo carregar).
            if (++lifeWithoutOwner > 60) {
                sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 2, getZ(), 20, 0.3, 1.2, 0.3, 0.02);
                discard();
            }
            return;
        }
        lifeWithoutOwner = 0;
        if (tickCount % 4 == 0) entityData.set(DATA_WATCHED, computeWatched(sl));
        if (body != null && body.getTarget() != null && getTarget() == null) setTarget(body.getTarget());
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            target = null;
        }
        if (attackCd > 0) attackCd--;
        if (diveCd > 0) diveCd--;
        if (diveTimer > 0) {
            diveTimer--;
            return;
        }
        if (attackTimer > 0) {
            attackTimer--;
            holdYaw(attackYaw);
            return;
        }
        if (target == null || (body != null && !body.isAwake())) return;
        if (body != null && body.headsPaused()) return;
        lookAtTarget(target);
        double d = distanceTo(target);
        boolean watched = isWatched();
        if (!watched && attackCd <= 0 && d < 6.0) {
            startBite(sl, target);
        } else if (!watched && attackCd <= 0 && d < 13.0 && (body == null || body.phase() >= 1) && hasLineOfSight(target)) {
            startLunge(sl, target);
        } else if (diveCd <= 0 && (d > 9.0 || random.nextInt(4) == 0)) {
            startDive(sl, target);
        }
    }

    private void lookAtTarget(LivingEntity target) {
        float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180.0 / Math.PI)) - 90.0F;
        float cur = Mth.approachDegrees(getYRot(), yaw, 8F);
        setYRot(cur);
        yBodyRot = cur;
        yHeadRot = cur;
    }

    private void holdYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Alguém (vivo, perto, com visão) está olhando direto para esta cabeça? */
    private boolean computeWatched(ServerLevel sl) {
        if (isSunk()) return false;
        Vec3 mid = position().add(0, getBbHeight() * 0.6, 0);
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            Vec3 eye = p.getEyePosition();
            Vec3 to = mid.subtract(eye);
            double dist = to.length();
            if (dist > WATCH_RANGE || dist < 1.0E-3) continue;
            if (p.getViewVector(1F).dot(to.scale(1.0 / dist)) < WATCH_DOT) continue;
            if (p.hasLineOfSight(this)) return true;
        }
        return false;
    }

    private float damageScale() {
        FalseHydra body = owner();
        return body != null ? BossKit.damageScale(body) : 1F;
    }

    private boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || BgAllies.isAlly(this, e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    // ------------------------------------------------------------------ Ataques

    private void startBite(ServerLevel sl, LivingEntity target) {
        attackCd = 55 + random.nextInt(35);
        attackTimer = 14;
        attackYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180.0 / Math.PI)) - 90.0F;
        holdYaw(attackYaw);
        triggerAnim("action", "bite");
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.6F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale();
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), attackYaw, 80F, 6.0F).warmup(14).color(FalseHydra.COLOR).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
                        }
                    }
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 1.2F);
                });
    }

    private void startLunge(ServerLevel sl, LivingEntity target) {
        attackCd = 80 + random.nextInt(40);
        attackTimer = 18;
        Vec3 from = position();
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-3) return;
        dir = dir.normalize();
        attackYaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        holdYaw(attackYaw);
        Vec3 to = from.add(dir.scale(Math.min(14.0, distanceTo(target) + 3.0)));
        triggerAnim("action", "lunge");
        playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.6F);
        float dmg = 11F * damageScale();
        Vec3 push = dir;
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 2.0F).warmup(18).color(FalseHydra.COLOR).owner(this),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 0.9, 0.4, push.z * 0.9));
                            e.hurtMarked = true;
                        }
                    }
                    BgFx.line(sl, from.add(0, 1.0, 0), to.add(0, 1.0, 0), BgFx.dust(FalseHydra.COLOR, 1.3F), 2.0);
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.4F, 0.9F);
                });
    }

    /** Afunda e irrompe perto do alvo (o círculo marca onde ela vai sair). */
    private void startDive(ServerLevel sl, LivingEntity target) {
        diveCd = 200 + random.nextInt(120);
        Vec3 spot = null;
        for (int i = 0; i < 12 && spot == null; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 3.0 + random.nextDouble() * 3.0;
            BlockPos base = BlockPos.containing(target.getX() + Math.cos(a) * r, target.getY() + 2, target.getZ() + Math.sin(a) * r);
            for (int dy = 0; dy < 6; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), net.minecraft.core.Direction.UP)
                        && sl.noCollision(getType().getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
                    spot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    break;
                }
            }
        }
        if (spot == null) return;
        // A cabeça não persegue ninguém para fora da arena do corpo (nem irrompe no meio da vila lá em cima).
        FalseHydra body = owner();
        Vec3 arena = body != null ? BossKit.arenaCenter(body) : null;
        if (arena != null) {
            double r = BossKit.arenaRadius(body);
            double dx = spot.x - arena.x, dz = spot.z - arena.z;
            if (dx * dx + dz * dz > r * r || Math.abs(spot.y - arena.y) > 10) {
                diveCd = 60;
                return;
            }
        }
        diveTimer = 26;
        entityData.set(DATA_SUNK, true);
        entityData.set(DATA_WATCHED, false);
        emergeFx(sl);
        playSound(SoundEvents.WARDEN_DIG, 1.0F, 1.4F);
        float dmg = 7F * damageScale();
        Vec3 at = spot;
        Telegraph.delayed(sl, Telegraph.Shape.circle(at, 2.2F).warmup(24).color(FalseHydra.COLOR).owner(this).noRunes(), hits -> {
            if (!isAlive()) return;
            teleportTo(at.x, at.y, at.z);
            entityData.set(DATA_SUNK, false);
            emergeFx(sl);
            playSound(SoundEvents.WARDEN_EMERGE, 1.0F, 1.5F);
            for (LivingEntity e : hits) {
                if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                    e.hurtMarked = true;
                }
            }
        });
    }

    private void emergeFx(ServerLevel sl) {
        BlockState ground = sl.getBlockState(blockPosition().below());
        if (ground.isAir()) ground = Blocks.ROOTED_DIRT.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(), 24, 0.6, 0.2, 0.6, 0.15);
    }

    // ------------------------------------------------------------------ Dano e morte

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isSunk() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && BgAllies.isAlly(this, attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level() instanceof ServerLevel sl) {
            FalseHydra body = owner();
            if (body != null) body.onHeadSevered(this);
            sl.sendParticles(BgFx.dust(0x6A1830, 1.4F), getX(), getY() + 2.5, getZ(), 30, 0.4, 1.2, 0.4, 0);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 24 && !level().isClientSide && !isRemoved()) {
            if (level() instanceof ServerLevel sl) emergeFx(sl);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isPickable() {
        return !isSunk() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSunk() || isWatched() ? null : SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public List<String> toggledBones() {
        return List.of("neck_root");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return !isSunk();
    }

    private PlayState main(AnimationState<FalseHydraHead> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        if (isSunk()) return state.setAndContinue(SUNK);
        if (clientSinging) return state.setAndContinue(SING);
        return state.setAndContinue(IDLE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::main));
        controllers.add(new AnimationController<>(this, "action", 2, s -> PlayState.STOP)
                .triggerableAnim("bite", RawAnimation.begin().thenPlay("animation.false_hydra_head.bite"))
                .triggerableAnim("lunge", RawAnimation.begin().thenPlay("animation.false_hydra_head.lunge")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("HydraBody", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("HydraBody") ? tag.getUUID("HydraBody") : null;
        entityData.set(DATA_SUNK, false);
    }
}
