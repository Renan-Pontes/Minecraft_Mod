package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Base das entidades de magia das Misturas C. São TEMPORÁRIAS (EntityType.noSave: somem ao descarregar o chunk ou
 * fechar o mundo) e guardam o {@link SpellContext} só na memória. O tempo de vida vai para o cliente animar a
 * abertura e o fim. O dano é sempre do elemento da fusão ({@link #element}), com crédito ao conjurador.
 */
public abstract class FcSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FcSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(FcSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(FcSpellEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected UUID ownerId;
    protected SpellStats hitStats = FcUtil.PLAIN;
    protected Element element = Element.ARCANE;
    /** Poder dos golpes (já com os aumentos do glifo; o contexto aplica a conjuração e o atributo). */
    protected float power = 1F;
    protected int life = 100;
    private int clientLife = 100;

    protected FcSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Liga a entidade à conjuração: duração, elemento do dano, aumentos e escala visual. */
    public void setup(@Nullable SpellContext ctx, SpellStats stats, Element element, int ticks, float scale) {
        this.ctx = ctx;
        this.ownerId = ctx != null ? ctx.casterId() : null;
        this.hitStats = FcUtil.hitStats(stats);
        this.power = stats.power();
        this.element = element;
        this.life = ticks;
        entityData.set(LIFE, ticks);
        entityData.set(MAX_LIFE, ticks);
        entityData.set(SCALE, scale);
    }

    @Nullable
    public UUID ownerId() { return ownerId; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFE, 100);
        builder.define(MAX_LIFE, 100);
        builder.define(SCALE, 1F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LIFE.equals(key)) clientLife = entityData.get(LIFE);
    }

    public int lifeLeft() { return level().isClientSide ? clientLife : life; }

    public int maxLife() { return Math.max(1, entityData.get(MAX_LIFE)); }

    public int age() { return maxLife() - lifeLeft(); }

    /** Tamanho visual/área (raio, largura...) definido por quem criou. */
    public float scale() { return entityData.get(SCALE); }

    protected void setScale(float s) { entityData.set(SCALE, s); }

    /** 0→1 nos primeiros {@code ticks} e 1→0 nos últimos {@code ticks} (animação de abrir/fechar). */
    public float openness(float partialTick, int ticks) {
        float age = maxLife() - lifeLeft() + partialTick;
        float left = lifeLeft() - partialTick;
        return Math.max(0F, Math.min(1F, Math.min(age / ticks, left / ticks)));
    }

    /** Estende a vida (sem reiniciar a animação de abertura). */
    protected void extendLife(int ticks) {
        if (ticks <= life) return;
        int age = maxLife() - life;
        life = ticks;
        entityData.set(MAX_LIFE, age + ticks);
        entityData.set(LIFE, ticks);
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

    /** Fim natural (antes de sumir). */
    protected void onExpire(ServerLevel level) {}

    /** Encerra agora (com o efeito de fim). */
    public void end() {
        if (level() instanceof ServerLevel sl && isAlive()) {
            onExpire(sl);
            discard();
        }
    }

    // =====================================================================================
    //  Aliados, alvos e dano
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

    /** Inimigos vivos (não aliados, não o dono, não criativo) numa esfera. */
    protected List<LivingEntity> enemiesIn(Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                le -> le.isAlive() && !le.isSpectator() && le.distanceToSqr(c) <= r * r)) {
            if (ally(le) || FcUtil.untouchable(le) || le instanceof ArmorStand) continue;
            out.add(le);
        }
        return out;
    }

    /** Dano mágico do elemento da fusão: {@code base × poder do glifo}; o contexto aplica o resto. */
    protected float hit(LivingEntity target, float base) {
        return hit(target, base, element);
    }

    protected float hit(LivingEntity target, float base, Element el) {
        target.invulnerableTime = 0;
        float amount = base * power;
        if (ctx != null) return ctx.damage(target, amount, hitStats, this, el);
        target.hurt(damageSources().magic(), amount);
        return amount;
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
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
    }
}
