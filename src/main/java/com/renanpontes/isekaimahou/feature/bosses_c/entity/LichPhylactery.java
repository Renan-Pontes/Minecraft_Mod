package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Filactério do Lich: gema de alma numa gaiola de ossos, plantada perto dele quando a luta começa.
 * Enquanto existir, o Lich se recompõe ao "morrer" e se cura aos poucos. Quebrá-la deixa o Lich mortal.
 * Não anda, não é empurrado e é imune a poções.
 */
public class LichPhylactery extends Mob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lich_phylactery.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID lichId;
    /** Ticks seguidos sem achar o Lich (ex.: ele sumiu no Pacífico): depois de 2 min o filactério se desfaz. */
    private int orphanTicks;
    /** Último lugar onde o Lich foi visto: só conta como "sumido" se aquele pedaço do mundo estiver carregado. */
    @Nullable
    private BlockPos lastLichPos;

    public LichPhylactery(EntityType<? extends LichPhylactery> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void setLich(Lich lich) {
        this.lichId = lich.getUUID();
    }

    @Nullable
    private Lich lich() {
        if (lichId != null && level() instanceof ServerLevel sl && sl.getEntity(lichId) instanceof Lich l && l.isAlive()) return l;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(BossFx.reduced() ? 8 : 2) == 0) {
                level().addParticle(ParticleTypes.SOUL, getRandomX(0.4), getY() + 0.5 + random.nextDouble() * 0.6, getRandomZ(0.4), 0, 0.02, 0);
            }
            return;
        }
        if (tickCount % 60 == 0) playSound(SoundEvents.BEACON_AMBIENT, 0.8F, 0.6F);
        if (tickCount % 20 == 0 && level() instanceof ServerLevel sl) {
            Lich l = lich();
            if (l != null) {
                orphanTicks = 0;
                lastLichPos = l.blockPosition();
            } else if (lastLichPos == null || sl.areEntitiesLoaded(ChunkPos.asLong(lastLichPos))) {
                orphanTicks += 20;
                if (orphanTicks >= 2400) shatter();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && BossAllies.isAlly(attacker, this)) return false;
        if (attacker instanceof Lich) return false;
        boolean ok = super.hurt(source, amount);
        if (ok && !level().isClientSide && attacker instanceof Player p) {
            Lich l = lich();
            if (l != null) l.onPhylacteryAttacked(p);
            ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.6, getZ(), 6, 0.2, 0.3, 0.2, 0.05);
        }
        return ok;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead || !(level() instanceof ServerLevel sl)) return;
        BossFx.sphere(sl, position().add(0, 0.6, 0), 1.2, 50, BossFx.dust(0x5FD8E8, 1.6F));
        sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.6, getZ(), 40, 0.4, 0.5, 0.4, 0.12);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.6, getZ(), 15, 0.3, 0.4, 0.3, 0.08);
        playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
        playSound(SoundEvents.WITHER_HURT, 1.5F, 0.6F);
        Lich l = lich();
        if (l != null) l.onPhylacteryDestroyed(source);
    }

    /** Some num estouro, sem animação de queda. */
    @Override
    protected void tickDeath() {
        if (++deathTime >= 2 && !level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
    }

    /** Desfaz o filactério sem matar ninguém (morte de verdade do Lich). */
    public void shatter() {
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.6, getZ(), 25, 0.3, 0.4, 0.3, 0.08);
            playSound(SoundEvents.GLASS_BREAK, 1.5F, 0.7F);
        }
        discard();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Preso ao chão da cripta: não dá para arrastar com laço. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Uma gema de alma não se afoga nem sufoca: só cai por golpes. */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    protected boolean isImmobile() {
        return true;
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
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lichId != null) tag.putUUID("Lich", lichId);
        if (lastLichPos != null) tag.put("LastLichPos", NbtUtils.writeBlockPos(lastLichPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lichId = tag.hasUUID("Lich") ? tag.getUUID("Lich") : null;
        lastLichPos = NbtUtils.readBlockPos(tag, "LastLichPos").orElse(null);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
