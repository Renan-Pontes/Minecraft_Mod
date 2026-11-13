package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Golem de Pedra invocado (Invocar Golem): tanque lento, muito resistente, que provoca os inimigos que
 * atacam o dono e esmaga com os punhos (pancada em area). Dura um tempo limitado e desmorona no fim.
 * Cada dono tem um golem por vez (invocar outro desfaz o antigo).
 */
public class SummonedStoneGolem extends PathfinderMob implements GeoEntity, OwnableEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_stone_golem.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.summoned_stone_golem.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.summoned_stone_golem.attack");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("animation.summoned_stone_golem.summon");
    /** Quantos golens um dono pode ter ao mesmo tempo (v1.0: Dividir no glifo chama mais de um). */
    public static final int MAX_PER_OWNER = 2;
    /** Golens atuais de cada dono (do mais antigo para o mais novo; no máximo {@link #MAX_PER_OWNER}). */
    private static final Map<UUID, List<UUID>> ACTIVE = new HashMap<>();
    /** Tick de jogo em que foi invocado (o cliente toca a animacao de "sair do chao" so nesse momento). */
    private static final EntityDataAccessor<Long> BORN = SynchedEntityData.defineId(SummonedStoneGolem.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private int life = 900;
    private int ownerMissing;

    public SummonedStoneGolem(EntityType<? extends SummonedStoneGolem> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** Cria e coloca no mundo um golem para {@code owner}. Desfaz o golem anterior do mesmo dono. */
    public static SummonedStoneGolem summon(ServerLevel level, LivingEntity owner, Vec3 pos, int lifeTicks, float power) {
        return summon(level, owner, pos, lifeTicks, power, 0);
    }

    /**
     * Igual ao outro, mas deixa ficar até {@code keep} golens que o dono já tem (os mais novos); os demais desmoronam.
     * O glifo usa isto para o Dividir: o 1º golem da conjuração troca os antigos, o 2º fica ao lado do 1º.
     */
    public static SummonedStoneGolem summon(ServerLevel level, LivingEntity owner, Vec3 pos, int lifeTicks, float power, int keep) {
        if (owner != null) {
            List<UUID> mine = ACTIVE.get(owner.getUUID());
            int allowed = Math.max(0, Math.min(MAX_PER_OWNER - 1, keep));
            while (mine != null && mine.size() > allowed) {
                UUID old = mine.remove(0);
                if (level.getEntity(old) instanceof SummonedStoneGolem g) g.crumble();
                mine = ACTIVE.get(owner.getUUID());
            }
        }
        SummonedStoneGolem g = new SummonedStoneGolem(EwRegistries.STONE_GOLEM.get(), level);
        g.ownerId = owner != null ? owner.getUUID() : null;
        g.life = lifeTicks;
        float yaw = owner != null ? owner.getYRot() : level.random.nextFloat() * 360F;
        g.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        g.setYHeadRot(yaw);
        var hp = g.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(80.0 * power);
        var dmg = g.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(9.0 * power);
        g.setHealth(g.getMaxHealth());
        g.setPersistenceRequired();
        g.entityData.set(BORN, level.getGameTime());
        level.addFreshEntity(g);
        if (owner != null) track(owner.getUUID(), g.getUUID());
        BlockPos ground = BlockPos.containing(pos).below();
        BlockState gs = EwFx.groundState(level, ground);
        EwFx.debris(level, pos.add(0, 0.5, 0), gs, 50, 0.8);
        EwFx.dustPillar(level, pos, gs, 30);
        EwFx.sound(level, pos, SoundEvents.WARDEN_EMERGE, 0.6F, 1.3F);
        EwFx.sound(level, pos, SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 0.6F);
        EwUtil.shake(level, pos, 12, 0.6F, 15);
        return g;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BORN, Long.MIN_VALUE / 2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, new SummonAi.FollowOwnerGoal<>(this, 1.0, 8.0F, 3.0F, false));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new SummonAi.DefendOwnerGoal<>(this, false));
        targetSelector.addGoal(2, new SummonAi.DefendOwnerGoal<>(this, true));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                e -> e instanceof Enemy && !SummonAi.isFriendly(this, e)));
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    public UUID getOwnerUUID() {
        return ownerId;
    }

    @Override
    public LivingEntity getOwner() {
        return SummonAi.owner(this, ownerId);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return SummonAi.isFriendly(this, other) || super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !SummonAi.isFriendly(this, target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // o dono e os aliados nao machucam o proprio golem
        if (source.getEntity() != null && SummonAi.isFriendly(this, source.getEntity())) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS)
                || super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // =====================================================================================
    //  Comportamento
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (--life <= 0) {
            crumble();
            return;
        }
        // o dono ja tem o maximo de golens mais novos (este estava num chunk descarregado): desfaz este
        if (ownerId != null && !track(ownerId, getUUID())) {
            crumble();
            return;
        }
        if (ownerId != null && getOwner() == null) {
            if (++ownerMissing > 200) {
                crumble();
                return;
            }
        } else {
            ownerMissing = 0;
        }
        if (tickCount % 40 == 0) taunt(level);
        if (life == 100) EwFx.sound(level, position(), SoundEvents.DEEPSLATE_BREAK, 1.0F, 0.5F);
        if (life < 100 && tickCount % 5 == 0) {
            EwFx.debris(level, position().add(0, getBbHeight() * 0.6, 0), EwFx.groundState(level, null), 4, 0.5);
        }
    }

    /** Provoca: inimigos perto que miram no dono (ou num aliado) passam a mirar no golem. */
    private void taunt(ServerLevel level) {
        int taunted = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12),
                m -> m instanceof Enemy && m.isAlive() && !SummonAi.isFriendly(this, m))) {
            LivingEntity t = m.getTarget();
            if (t != null && t != this && SummonAi.isFriendly(this, t)) {
                m.setTarget(this);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 1, 0, 0, 0, 0);
                taunted++;
            }
        }
        if (taunted > 0) EwFx.sound(level, position(), SoundEvents.RAVAGER_ROAR, 0.5F, 1.4F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        triggerAnim("main", "attack");
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
        if (ok && level() instanceof ServerLevel level) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.45, 0));
            target.hurtMarked = true;
            // pancada no chao: atinge de leve quem estiver ao redor do alvo
            Vec3 at = target.position();
            float splash = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4F;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                    e -> e != target && e.isAlive() && !SummonAi.isFriendly(this, e))) {
                if (e.hurt(damageSources().mobAttack(this), splash)) {
                    EwUtil.push(e, e.position().subtract(at).normalize().scale(0.6), 0.3);
                }
            }
            BlockState gs = EwFx.groundState(level, target.blockPosition().below());
            EwFx.dustPillar(level, at, gs, 20);
            EwFx.debris(level, at.add(0, 0.2, 0), gs, 16, 0.6);
            EwUtil.shake(level, at, 8, 0.35F, 8);
        }
        return ok;
    }

    /** Desmorona (fim do tempo, dono sumiu ou invocou outro). */
    public void crumble() {
        if (level() instanceof ServerLevel level) {
            BlockState gs = EwFx.groundState(level, blockPosition().below());
            EwFx.debris(level, position().add(0, 1.2, 0), gs, 60, 0.7);
            EwFx.dustPillar(level, position(), gs, 20);
            EwFx.sound(level, position(), SoundEvents.DEEPSLATE_BREAK, 1.2F, 0.6F);
            EwFx.sound(level, position(), SoundEvents.IRON_GOLEM_DEATH, 0.6F, 0.7F);
        }
        discard();
    }

    /** Esquece os registros de "invocacao atual" (ao fechar o servidor/mundo). */
    public static void clearActive() {
        ACTIVE.clear();
    }

    /** Põe o golem na lista do dono. false = a lista já está cheia de outros golens (este sobra). */
    private static boolean track(UUID owner, UUID golem) {
        List<UUID> mine = ACTIVE.computeIfAbsent(owner, k -> new ArrayList<>(MAX_PER_OWNER));
        if (mine.contains(golem)) return true;
        if (mine.size() >= MAX_PER_OWNER) return false;
        mine.add(golem);
        return true;
    }

    private static void untrack(UUID owner, UUID golem) {
        List<UUID> mine = ACTIVE.get(owner);
        if (mine == null) return;
        mine.remove(golem);
        if (mine.isEmpty()) ACTIVE.remove(owner);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (ownerId != null && reason.shouldDestroy()) untrack(ownerId, getUUID());
        super.remove(reason);
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.8F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        life = tag.contains("Life") ? tag.getInt("Life") : 600;
        if (ownerId != null) track(ownerId, getUUID());
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (level().getGameTime() - entityData.get(BORN) < 24) return state.setAndContinue(SUMMON);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }).triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
