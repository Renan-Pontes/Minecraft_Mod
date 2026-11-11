package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Base de fenomenos que "guardam a magia" e agem sozinhos por um tempo (Tornado, Tempestade):
 * sem colisao, imunes a dano, com o contexto da magia salvo para aplicar dano e reconhecer aliados.
 */
public abstract class SpellBoundEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellBoundEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpellBoundEntity.class, EntityDataSerializers.INT);

    protected CompoundTag spellTag;
    protected SpellContext ctx;
    protected UUID ownerId;
    protected float power = 1.0F;

    protected SpellBoundEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    protected void bind(SpellContext ctx, float radius, int life, float power) {
        this.ctx = ctx;
        this.spellTag = ctx.save();
        this.ownerId = ctx.casterId();
        this.power = power;
        entityData.set(RADIUS, radius);
        entityData.set(LIFE, life);
    }

    /**
     * Limita quantos fenomenos deste tipo um mesmo conjurador mantem ativos (os mais antigos somem).
     * Chamar ANTES de adicionar o novo ao mundo.
     */
    protected static <T extends SpellBoundEntity> void limitPerOwner(ServerLevel level, Class<T> type, UUID owner, int max, double range,
            AABB around) {
        if (owner == null) return;
        List<T> mine = level.getEntitiesOfClass(type, around.inflate(range), e -> owner.equals(e.ownerId) && e.isAlive());
        if (mine.size() < max) return;
        mine.sort(Comparator.comparingInt((T e) -> e.tickCount).reversed());
        for (int i = 0; i <= mine.size() - max; i++) mine.get(i).expire();
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    /** Contexto da magia (recarregado depois de salvar). Pode ser null se a magia ficou invalida. */
    protected SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    /** Inimigo valido deste fenomeno (nunca o conjurador nem aliados dele). */
    protected boolean isEnemy(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (ownerId != null && ownerId.equals(e.getUUID())) return false;
        SpellContext c = context();
        return c == null || !c.isAlly(e);
    }

    /** Fim natural (efeito final + remover). */
    protected abstract void expire();

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life()) expire();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 2.0F);
        builder.define(LIFE, 200);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        spellTag = tag.contains("Spell") ? tag.getCompound("Spell") : null;
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        power = tag.contains("Power") ? tag.getFloat("Power") : 1.0F;
        entityData.set(RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : 2.0F);
        // o tickCount recomeca ao carregar: guardamos so o tempo que faltava
        entityData.set(LIFE, Math.max(20, tag.getInt("LifeLeft")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (spellTag != null) tag.put("Spell", spellTag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Power", power);
        tag.putFloat("Radius", radius());
        tag.putInt("LifeLeft", life() - tickCount);
    }
}
