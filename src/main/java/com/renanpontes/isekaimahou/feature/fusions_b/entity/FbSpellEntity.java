package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
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
 * Base das entidades de magia das fusões B (prisão de cristal, correntes, tentáculos, poço de gravidade,
 * fenda, eclipse, destroços). Não são salvas (EntityType.noSave): guardam o {@link SpellContext} na memória
 * e duram poucos segundos. O tempo de vida é sincronizado para o cliente animar a abertura e o fim.
 */
public abstract class FbSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FbSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(FbSpellEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected UUID ownerId;
    /** Aumentos usados nos golpes (Crítico/Vampírico; poder 1 — ver {@link FbUtil#hitStats}). */
    protected SpellStats hitStats = FbUtil.PLAIN;
    /** Ticks restantes (servidor). */
    protected int life = 100;
    /** Contagem local do cliente, corrigida quando chega um valor novo do servidor. */
    private int clientLife = 100;

    protected FbSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Liga a entidade à conjuração e define a duração. */
    public void setup(@Nullable SpellContext ctx, int ticks) {
        this.ctx = ctx;
        this.ownerId = ctx != null ? ctx.casterId() : null;
        this.life = ticks;
        entityData.set(LIFE, ticks);
        entityData.set(MAX_LIFE, ticks);
    }

    /** Aumentos do efeito que criou a entidade (Crítico/Vampírico continuam valendo nos golpes dela). */
    public void setHitStats(SpellStats effectStats) {
        this.hitStats = FbUtil.hitStats(effectStats);
    }

    /** Estende o tempo de vida sem reiniciar a animação de abertura. */
    public void extendLife(int ticks) {
        if (ticks <= life) return;
        int age = entityData.get(MAX_LIFE) - life;
        life = ticks;
        entityData.set(MAX_LIFE, age + ticks);
        entityData.set(LIFE, ticks);
    }

    @Nullable
    public UUID ownerId() { return ownerId; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFE, 100);
        builder.define(MAX_LIFE, 100);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LIFE.equals(key)) clientLife = entityData.get(LIFE);
    }

    /** Ticks restantes (no cliente, estimado). */
    public int lifeLeft() {
        return level().isClientSide ? clientLife : life;
    }

    public int maxLife() { return Math.max(1, entityData.get(MAX_LIFE)); }

    /** 0→1 na abertura (primeiros {@code ticks}) e 1→0 no fechamento (últimos {@code ticks}). Para animação. */
    public float openness(float partialTick, int ticks) {
        float age = maxLife() - lifeLeft() + partialTick;
        float left = lifeLeft() - partialTick;
        return Math.max(0F, Math.min(1F, Math.min(age / ticks, left / ticks)));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (clientLife > 0) clientLife--;
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (--life <= 0) {
            onExpire(sl);
            discard();
            return;
        }
        if (life % 5 == 0) entityData.set(LIFE, life);
        serverTick(sl);
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {}

    /** Fim natural do tempo (antes de sumir). */
    protected void onExpire(ServerLevel level) {}

    /** Encerra antes do tempo. */
    public void end() {
        if (level() instanceof ServerLevel sl && isAlive()) {
            onExpire(sl);
            discard();
        }
    }

    // =====================================================================================
    //  Aliados e dano
    // =====================================================================================

    protected boolean ally(Entity e) {
        if (ctx != null) return ctx.isAlly(e);
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    @Nullable
    protected LivingEntity owner() {
        if (ctx != null && ctx.caster() != null) return ctx.caster();
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) return le;
        return null;
    }

    /**
     * Dano mágico já multiplicado por {@code stats.power()} do efeito; o contexto aplica a força da conjuração,
     * o poder mágico do conjurador, crítico, reações, Vampírico e XP.
     */
    protected void hit(LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        if (ctx != null) {
            ctx.damage(target, amount, hitStats, this);
        } else {
            target.hurt(damageSources().magic(), amount);
        }
    }

    // =====================================================================================
    //  Regras de entidade "fantasma"
    // =====================================================================================

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
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 96 * 96; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
    }
}
