package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bolha da Prisão de Bolha (Maré): prende uma criatura, sobe devagar e a afoga aos poucos (dano de água e
 * Encharcado). Estoura sozinha depois de alguns segundos ou quando leva dano suficiente — o preso pode golpeá-la
 * de dentro e os aliados de fora (corpo a corpo ou flechas). Não é salva no mundo.
 */
public class TideBubble extends Entity implements GeoEntity {
    private static final RawAnimation WOBBLE = RawAnimation.begin().thenLoop("animation.tide_bubble.wobble");
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(TideBubble.class, EntityDataSerializers.INT);
    /** Fração de "vida" que resta (0..1): a bolha treme mais quando está quase estourando. */
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(TideBubble.class, EntityDataSerializers.FLOAT);
    /** Quanto a bolha sobe a partir do chão (blocos). */
    private static final double RISE = 1.6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ownerId;
    @Nullable private UUID victimId;
    private Vec3 anchor = Vec3.ZERO;
    private float hp = 10F;
    private float maxHp = 10F;
    private float damage = 2F;

    public TideBubble(EntityType<? extends TideBubble> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Prende {@code victim} numa bolha (se ainda não estiver preso).
     * @return true se prendeu
     */
    public static boolean trap(LivingEntity owner, LivingEntity victim, int life, float hp, float damage) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        if (isTrapped(level, victim)) return false;
        TideBubble b = new TideBubble(BossesARegistry.TIDE_BUBBLE.get(), level);
        b.ownerId = owner.getUUID();
        b.victimId = victim.getUUID();
        b.anchor = victim.position();
        b.hp = hp;
        b.maxHp = hp;
        b.damage = damage;
        b.entityData.set(DATA_LIFE, life);
        b.setPos(b.anchor.x, b.anchor.y - 0.3, b.anchor.z);
        level.addFreshEntity(b);
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.SPLASH, b.getX(), b.getY() + 1, b.getZ(), 40, 0.8, 0.8, 0.8, 0.2);
        victim.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, life + 40, 0), owner);
        if (victim instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("boss.isekai_mahou.tide_guardian.hint.bubble_trapped")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        }
        return true;
    }

    /** A criatura já está numa bolha? */
    public static boolean isTrapped(ServerLevel level, LivingEntity victim) {
        UUID id = victim.getUUID();
        return !level.getEntitiesOfClass(TideBubble.class, victim.getBoundingBox().inflate(3.0),
                b -> id.equals(b.victimId) && !b.isRemoved()).isEmpty();
    }

    public int life() { return entityData.get(DATA_LIFE); }

    public float healthFraction() { return entityData.get(DATA_HEALTH); }

    /** Escala visual: incha ao surgir e treme mais quando está quase estourando. */
    public float visualScale(float partialTick) {
        float age = tickCount + partialTick;
        float grow = Math.min(1F, age / 6F);
        float wobble = (1F - healthFraction()) * 0.06F * (float) Math.sin(age * 1.7F);
        return grow * (1F + wobble);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE, 60);
        builder.define(DATA_HEALTH, 1F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.BUBBLE_POP, getX() + (random.nextDouble() - 0.5) * 1.8,
                        getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - 0.5) * 1.8, 0, 0.04, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity ownerEntity = ownerId != null ? sl.getEntity(ownerId) : null;
        Entity victimEntity = victimId != null ? sl.getEntity(victimId) : null;
        if (!(victimEntity instanceof LivingEntity victim) || !victim.isAlive() || victim.isSpectator()
                || !(ownerEntity instanceof LivingEntity owner) || !owner.isAlive() || tickCount >= life()) {
            pop(sl, false);
            return;
        }
        // Sobe devagar e segura o preso no centro
        double rise = Math.min(RISE, tickCount * 0.08);
        Vec3 hold = anchor.add(0, rise, 0);
        setPos(hold.x, hold.y - 0.3, hold.z);
        Vec3 pull = hold.subtract(victim.position());
        victim.setDeltaMovement(pull.scale(0.6));
        victim.hurtMarked = true;
        victim.fallDistance = 0;
        if (tickCount % 20 == 10) {
            BossSpells.hit(owner, victim, Element.WATER, damage);
            victim.setAirSupply(Math.max(0, victim.getAirSupply() - 40));
            sl.sendParticles(ParticleTypes.BUBBLE, victim.getX(), victim.getEyeY(), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        }
        if (tickCount % 15 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 0.9F, 1.1F);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        Entity attacker = source.getEntity();
        // A própria Maré e os ataques dela não estouram a bolha
        if (attacker != null && attacker.getUUID().equals(ownerId)) return false;
        hp -= Math.max(1F, amount);
        entityData.set(DATA_HEALTH, Math.max(0F, hp / maxHp));
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.2, getZ(), 16, 0.6, 0.6, 0.6, 0.15);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 1.2F, 1.4F);
        if (hp <= 0) pop(sl, true);
        return true;
    }

    private void pop(ServerLevel sl, boolean broken) {
        if (isRemoved()) return;
        Vec3 c = position().add(0, 1.1, 0);
        sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 60, 1.0, 1.0, 1.0, 0.3);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 30, 0.9, 0.9, 0.9, 0.1);
        ElementFx.burst(sl, c, Element.WATER, 20, 0.8);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, 1.5F, broken ? 1.2F : 0.9F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 2.0F, 0.7F);
        Entity victim = victimId != null ? sl.getEntity(victimId) : null;
        if (victim != null) {
            victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.25, 0));
            victim.hurtMarked = true;
            victim.fallDistance = 0;
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "wobble", 0, state -> state.setAndContinue(WOBBLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
