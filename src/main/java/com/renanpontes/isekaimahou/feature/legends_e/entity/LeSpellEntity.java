package com.renanpontes.isekaimahou.feature.legends_e.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base das entidades visuais/de sequência das Magias de Nível Supremo. São TEMPORÁRIAS (tipo {@code noSave}: somem ao
 * descarregar o chunk ou reiniciar) e guardam a magia só na memória. Idade sincronizada com o cliente (o renderer
 * conta as fases por ela), cor da magia e elemento. Não colidem, não são atingíveis, não usam portais.
 */
public abstract class LeSpellEntity extends net.minecraft.world.entity.Entity {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LeSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(LeSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(LeSpellEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected SpellStats stats;
    /** Idade em ticks (no cliente corrigida pelo servidor de tempos em tempos). */
    protected int age;

    protected LeSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Liga a entidade à magia (antes de {@code addFreshEntity}). */
    protected void bind(SpellContext ctx, @Nullable SpellStats stats) {
        this.ctx = ctx;
        this.stats = stats;
        entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
        entityData.set(DATA_ELEMENT, ctx.element().ordinal());
    }

    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, 0xBFE3FF);
        builder.define(DATA_ELEMENT, 0);
        builder.define(DATA_AGE, 0);
        defineExtraData(builder);
    }

    protected void defineExtraData(SynchedEntityData.Builder builder) {}

    public int color() { return entityData.get(DATA_COLOR); }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(DATA_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public int age() { return age; }

    /** Manda a idade agora (junto com uma troca de fase). */
    protected void syncAge() {
        entityData.set(DATA_AGE, age);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (age % 10 == 0) syncAge();
        if (ctx == null) {
            discard();
            return;
        }
        serverTick((ServerLevel) level(), ctx);
    }

    protected abstract void serverTick(ServerLevel level, SpellContext ctx);

    protected void clientTick() {}

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_AGE && level().isClientSide) age = entityData.get(DATA_AGE);
    }

    // ---------------- É magia: sem física nem interação ----------------
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
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    @Override
    public boolean displayFireAnimation() { return false; }

    // ---------------- Não é salva (tipo noSave) ----------------
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
