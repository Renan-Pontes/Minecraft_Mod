package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

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
import net.minecraft.world.level.material.PushReaction;

/**
 * Base das entidades de magia das Misturas D. Não são salvas (o tipo é {@code noSave}): somem ao descarregar o
 * chunk ou ao reiniciar. Guardam o {@link SpellContext} na memória para o dano contar como magia do conjurador.
 * Vida (ticks) e estado são sincronizados para o cliente animar.
 */
public abstract class FdSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(FdSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FdSpellEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected UUID ownerId;
    protected SpellStats hitStats = FdUtil.PLAIN;
    /** Tempo de vida total (servidor); o cliente usa o sincronizado. */
    protected int maxLife = 100;
    /** Ticks no estado atual (servidor e cliente: reinicia quando o estado muda). */
    protected int stateAge;

    protected FdSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Liga a entidade à conjuração e define a duração. */
    public void setup(@Nullable SpellContext ctx, int ticks, SpellStats stats) {
        this.ctx = ctx;
        this.ownerId = ctx != null ? ctx.casterId() : null;
        this.maxLife = Math.max(1, ticks);
        this.hitStats = FdUtil.hitStats(stats);
        entityData.set(MAX_LIFE, maxLife);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(MAX_LIFE, 100);
        b.define(STATE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) stateAge = 0;
    }

    public int maxLife() { return Math.max(1, entityData.get(MAX_LIFE)); }

    public int state() { return entityData.get(STATE); }

    public int stateAge() { return stateAge; }

    protected void setState(int s) {
        if (state() != s) {
            entityData.set(STATE, s);
            stateAge = 0;
        }
    }

    /** Alonga a vida (Estender etc.) sem reiniciar a animação de abertura. */
    protected void setMaxLife(int ticks) {
        maxLife = Math.max(1, ticks);
        entityData.set(MAX_LIFE, maxLife);
    }

    /** 0→1 nos primeiros {@code ticks} e 1→0 nos últimos {@code ticks} da vida. Para animação. */
    public float openness(float pt, int ticks) {
        float age = tickCount + pt;
        float left = maxLife() - age;
        return Math.max(0F, Math.min(1F, Math.min(age / ticks, left / ticks)));
    }

    @Override
    public void tick() {
        super.tick();
        stateAge++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount >= maxLife) {
            onExpire(sl);
            discard();
            return;
        }
        serverTick(sl);
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {}

    protected void onExpire(ServerLevel level) {}

    /** Encerra agora (com o efeito de fim). */
    public void end() {
        if (level() instanceof ServerLevel sl && isAlive()) {
            onExpire(sl);
            discard();
        }
    }

    // ---------- aliados e dano ----------
    protected boolean ally(Entity e) {
        if (ctx != null) return ctx.isAlly(e);
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    @Nullable
    protected LivingEntity owner() {
        if (ctx != null && ctx.caster() != null) return ctx.caster();
        return level() instanceof ServerLevel sl ? FdUtil.owner(sl, ownerId) : null;
    }

    /** Inimigos (não aliados, não criativos) numa esfera. */
    protected List<LivingEntity> foes(net.minecraft.world.phys.Vec3 at, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r),
                e -> e.isAlive() && !e.isSpectator() && !ally(e) && !FdUtil.untouchable(e) && e.distanceToSqr(at) <= r * r)) {
            out.add(e);
        }
        return out;
    }

    /** Dano mágico (o valor já vem com o poder do efeito; o contexto aplica conjuração, reações, crítico, XP). */
    protected float hit(LivingEntity target, float amount, @Nullable Element element) {
        target.invulnerableTime = 0;
        if (ctx != null) return element != null ? ctx.damage(target, amount, hitStats, this, element) : ctx.damage(target, amount, hitStats, this);
        target.hurt(damageSources().magic(), amount);
        return amount;
    }

    /** Quantas destas entidades o mesmo conjurador tem vivas; as mais velhas acima do teto são encerradas. */
    public static <T extends FdSpellEntity> void enforceCap(ServerLevel sl, EntityType<T> type, @Nullable UUID owner, int max) {
        if (owner == null) return;
        List<T> mine = new ArrayList<>(sl.getEntities(type, e -> e.isAlive() && owner.equals(e.ownerId)));
        mine.sort((a, b) -> Integer.compare(b.tickCount, a.tickCount));
        while (mine.size() >= max) mine.remove(0).end();
    }

    // ---------- entidade "fantasma" ----------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
