package com.renanpontes.isekaimahou.feature.legends_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base das entidades das formas lendárias (Convergência, Lanças do Céu, Esfera Espiral). Guarda a magia
 * ({@link SpellContext#save()}, salva de novo a cada gravação para não perder disparos de ligação), idade
 * sincronizada e dados de visual (elemento, cor). Não colide, não é atingível, não usa portais.
 */
public abstract class LegendSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(LegendSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LegendSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(LegendSpellEntity.class, EntityDataSerializers.INT);

    protected CompoundTag spellTag = new CompoundTag();
    private SpellContext ctx;
    private long nextReload;
    /** Idade em ticks (no cliente é corrigida pelo servidor de tempos em tempos). */
    protected int age;

    protected LegendSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Liga a entidade à magia. Chame antes de {@code addFreshEntity}. */
    protected void bind(SpellContext ctx) {
        this.ctx = ctx;
        this.spellTag = ctx.save();
        entityData.set(DATA_ELEMENT, ctx.element().ordinal());
        entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
    }

    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ELEMENT, 0);
        builder.define(DATA_COLOR, Element.ARCANE.color);
        builder.define(DATA_AGE, 0);
        defineExtraData(builder);
    }

    protected void defineExtraData(SynchedEntityData.Builder builder) {}

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(DATA_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    /** Cor RGB da magia (a escolhida na receita, ou a do elemento). */
    public int color() { return entityData.get(DATA_COLOR); }

    public int age() { return age; }

    /** Manda a idade agora (junto com uma troca de fase, para o cliente contar o tempo da fase certo). */
    protected void syncAge() {
        entityData.set(DATA_AGE, age);
    }

    /** Contexto da magia (servidor). Recarrega do NBT se a entidade veio do disco ou o conjurador voltou. */
    protected SpellContext context() {
        if (!(level() instanceof ServerLevel sl)) return null;
        boolean stale = ctx == null || (ctx.caster() == null && ctx.casterId() != null);
        if (stale && sl.getGameTime() >= nextReload) {
            nextReload = sl.getGameTime() + 20;
            // Recarrega a partir do estado ATUAL (disparos de ligação e orçamento já gastos não voltam a zero)
            if (ctx != null) spellTag = ctx.save();
            SpellContext fresh = SpellContext.load(sl, spellTag);
            if (fresh != null) ctx = fresh;
        }
        return ctx;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (age % 10 == 0) entityData.set(DATA_AGE, age);
        SpellContext c = context();
        if (c == null) {
            discard();
            return;
        }
        serverTick((ServerLevel) level(), c);
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
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 160 * 160; }

    @Override
    public boolean displayFireAnimation() { return false; }

    // ---------------- Salvar ----------------
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        spellTag = tag.getCompound("Spell");
        age = tag.getInt("Age");
        entityData.set(DATA_AGE, age);
        entityData.set(DATA_COLOR, tag.getInt("Color"));
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(DATA_ELEMENT, e.ordinal());
        readExtra(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Spell", ctx != null ? ctx.save() : spellTag);
        tag.putInt("Age", age);
        tag.putInt("Color", color());
        saveExtra(tag);
    }

    protected void readExtra(CompoundTag tag) {}

    protected void saveExtra(CompoundTag tag) {}
}
