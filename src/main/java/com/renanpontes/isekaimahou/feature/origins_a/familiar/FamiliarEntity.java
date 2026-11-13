package com.renanpontes.isekaimahou.feature.origins_a.familiar;

import java.util.UUID;

import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos Familiares da Bruxa (Corvo e Gato). Invocados têm dono e tempo (10 min); os de ovo não têm dono
 * e ficam no mundo como bichos comuns. Seguem o dono (teleportam se ficarem longe) e nunca atacam aliados.
 */
public abstract class FamiliarEntity extends PathfinderMob implements GeoEntity, OwnableEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected UUID owner;
    /** Tempo de jogo em que o familiar se desfaz (-1 = sem prazo, ex.: de ovo). Absoluto: conta mesmo com o chunk descarregado. */
    protected long expireAt = -1;
    protected float power = 1.0F;
    private int ownerMissing;

    protected FamiliarEntity(EntityType<? extends FamiliarEntity> type, Level level) {
        super(type, level);
    }

    public void setup(LivingEntity owner, int life, float power) {
        this.owner = owner != null ? owner.getUUID() : null;
        this.expireAt = life > 0 ? level().getGameTime() + life : -1;
        this.power = power;
        setPersistenceRequired();
    }

    // ---------------- Dono ----------------
    @Override
    public UUID getOwnerUUID() {
        return owner;
    }

    @Override
    public LivingEntity getOwner() {
        if (owner == null) return null;
        if (level() instanceof ServerLevel sl) {
            LivingEntity o = OaUtil.owner(sl, owner);
            return o != null && o.level() == level() ? o : null;
        }
        return level().getPlayerByUUID(owner);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (owner != null) {
            if (owner.equals(e.getUUID())) return true;
            if (e instanceof OwnableEntity oe && owner.equals(oe.getOwnerUUID())) return true;
            LivingEntity o = getOwner();
            if (o != null && o != this && o.isAlliedTo(e)) return true;
        }
        return super.isAlliedTo(e);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // O dono não machuca o próprio familiar sem querer
        if (owner != null && source.getEntity() != null && owner.equals(source.getEntity().getUUID())) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ---------------- Tempo de vida ----------------
    /** Chamar no começo do customServerAiStep. @return true se o familiar sumiu. */
    protected boolean tickLife(ServerLevel level) {
        if (owner == null) return false;
        if (expireAt > 0 && level.getGameTime() >= expireAt) {
            vanish(level);
            return true;
        }
        if (getOwner() == null) {
            // Dono saiu do mundo ou foi para outra dimensão
            if (++ownerMissing > 60) {
                vanish(level);
                return true;
            }
        } else {
            ownerMissing = 0;
        }
        return false;
    }

    /** Teleporta para perto do dono se ficou muito longe. */
    protected void keepUp(ServerLevel level, LivingEntity o, double maxDist, double yOffset) {
        if (o == null || distanceToSqr(o) < maxDist * maxDist) return;
        Vec3 to = o.position().add(o.getLookAngle().scale(-1.5)).add(0, yOffset, 0);
        level.sendParticles(Vfx.smoke(), getX(), getY() + 0.3, getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        teleportTo(to.x, to.y, to.z);
        getNavigation().stop();
        level.sendParticles(Vfx.witch(), to.x, to.y + 0.3, to.z, 10, 0.2, 0.2, 0.2, 0.05);
    }

    /** Some num puf de fumaça (fim do tempo ou dispensado). */
    public void vanish(ServerLevel level) {
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        level.sendParticles(Vfx.largeSmoke(), c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(Vfx.witch(), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.8F, 1.4F);
        onVanish(level);
        discard();
    }

    protected void onVanish(ServerLevel level) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("FamiliarOwner", owner);
        tag.putLong("FamiliarExpire", expireAt);
        tag.putFloat("FamiliarPower", power);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("FamiliarOwner") ? tag.getUUID("FamiliarOwner") : null;
        expireAt = tag.contains("FamiliarExpire") ? tag.getLong("FamiliarExpire") : -1;
        power = tag.contains("FamiliarPower") ? tag.getFloat("FamiliarPower") : 1.0F;
    }
}
