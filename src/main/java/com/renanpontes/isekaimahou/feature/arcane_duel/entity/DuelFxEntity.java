package com.renanpontes.isekaimahou.feature.arcane_duel.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Visual dos glifos de duelo (só desenho: não colide, não é atingível, não é salvo). O tipo ({@link #kind()}) diz
 * o que o cliente desenha; pode ficar PRESO a uma entidade (resguardo, égide, relógio) e some quando ela some
 * ou perde o efeito correspondente.
 */
public class DuelFxEntity extends Entity {
    /** Vidro mágico (círculo rúnico) que trinca e estilhaça: Contramágica, Vazamento de Mana, Toque Mortal. */
    public static final int SHATTER = 0;
    /** Casca de hexágonos girando em volta do alvo: Resguardo. */
    public static final int HEX_WARD = 1;
    /** Coluna e anéis dourados: Indestrutível. */
    public static final int AEGIS = 2;
    /** Fenda para o nada que engole o alvo: Exilar. */
    public static final int RIFT = 3;
    /** Sigilo enorme no céu: Ira Divina. */
    public static final int SKY_SIGIL = 4;
    /** Pilar de luz do julgamento: Ira Divina. */
    public static final int PILLAR = 5;
    /** Relógio que gira para trás: Passo no Tempo. */
    public static final int CLOCK = 6;
    /** Carta de magia que surge girando e sobe: Copiar Magia, Roubar Encanto, Em Resposta. */
    public static final int CARD = 7;
    /** Espelho rúnico que gira: Redirecionar. */
    public static final int MIRROR = 8;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PARAM = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ATTACH = SynchedEntityData.defineId(DuelFxEntity.class, EntityDataSerializers.INT);

    public DuelFxEntity(EntityType<? extends DuelFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Cria (sem adicionar ao mundo). */
    public static DuelFxEntity create(Level level, int kind, double x, double y, double z, float radius, int life, int rgb,
                                      float param, @Nullable Entity attach) {
        DuelFxEntity fx = new DuelFxEntity(DuelRegistry.FX.get(), level);
        fx.setPos(x, y, z);
        fx.entityData.set(DATA_KIND, kind);
        fx.entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
        fx.entityData.set(DATA_LIFE, Math.max(2, life));
        fx.entityData.set(DATA_RADIUS, radius);
        fx.entityData.set(DATA_PARAM, param);
        fx.entityData.set(DATA_ATTACH, attach != null ? attach.getId() : -1);
        return fx;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, SHATTER);
        b.define(DATA_COLOR, 0xFFFFFF);
        b.define(DATA_LIFE, 20);
        b.define(DATA_RADIUS, 1F);
        b.define(DATA_PARAM, 0F);
        b.define(DATA_ATTACH, -1);
    }

    public int kind() { return entityData.get(DATA_KIND); }
    public int color() { return entityData.get(DATA_COLOR); }
    public int life() { return entityData.get(DATA_LIFE); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public float param() { return entityData.get(DATA_PARAM); }
    public int attachId() { return entityData.get(DATA_ATTACH); }

    /** Estende a vida (reaplicar Resguardo/Indestrutível não cria outra casca). */
    public void extendLife(int totalFromNow) {
        entityData.set(DATA_LIFE, Math.max(life(), tickCount + totalFromNow));
    }

    /** Entidade a que o visual está preso (null se nenhuma ou se já sumiu). */
    @Nullable
    public Entity attached() {
        int id = attachId();
        return id < 0 ? null : level().getEntity(id);
    }

    /** 0..1 da vida já passada (com o tick parcial no cliente). */
    public float progress(float partial) {
        return Math.min(1F, (tickCount + partial) / life());
    }

    @Override
    public void tick() {
        super.tick();
        if (attachId() >= 0) {
            Entity a = attached();
            if (a == null || !a.isAlive() || a.isRemoved()) {
                if (!level().isClientSide) discard();
                return;
            }
            setPos(a.getX(), a.getY(), a.getZ());
            // A casca/égide acompanha o efeito: dissipado (Dissipar, leite) = some na hora
            if (!level().isClientSide && tickCount % 10 == 0 && a instanceof LivingEntity le) {
                if ((kind() == HEX_WARD && !le.hasEffect(DuelRegistry.HEXPROOF))
                        || (kind() == AEGIS && !le.hasEffect(DuelRegistry.INDESTRUCTIBLE))) {
                    discard();
                    return;
                }
            }
        }
        if (!level().isClientSide && tickCount >= life()) discard();
    }

    // ---------------- Só visual ----------------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 192 * 192; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
