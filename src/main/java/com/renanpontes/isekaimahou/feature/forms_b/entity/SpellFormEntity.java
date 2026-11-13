package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.feature.forms_b.util.PersistentSpells;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Base das entidades de magia do forms_b (zona, runa, órbita, torre, invocação, portal, muralha).
 * Guarda a magia ({@link SpellContext#save()}), o tempo de vida e dados sincronizados para o visual
 * (elemento, cor, raio, dono). Não colide, não é atingível e não é empurrada.
 */
public abstract class SpellFormEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** v0.7: cor e elemento de destaque (2º elemento de magias multi-elemento) para o visual. */
    private static final EntityDataAccessor<Integer> DATA_ACCENT = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACCENT_ELEMENT = SynchedEntityData.defineId(SpellFormEntity.class, EntityDataSerializers.INT);

    protected CompoundTag spellTag = new CompoundTag();
    private SpellContext ctx;
    private long nextReload;
    /** Idade em ticks (salva; no cliente é corrigida pelo servidor a cada 10 ticks). */
    protected int age;
    /** Momento da criação (ordem para o limite de magias persistentes). */
    private long created;
    private boolean tracked;
    /** Quem conjurou era jogador (para a magia "dormir" enquanto ele estiver fora do mundo). */
    private boolean byPlayer;
    /** O estágio já avisou o núcleo que terminou (Ao Terminar dispara uma vez só). */
    private boolean stageEnded;

    protected SpellFormEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Liga a entidade à magia. Chame antes de {@code addFreshEntity}. */
    public void setup(SpellContext ctx, int lifetime, float radius) {
        this.ctx = ctx;
        this.spellTag = ctx.save();
        this.created = ctx.level().getGameTime();
        this.byPlayer = ctx.caster() instanceof Player;
        entityData.set(DATA_ELEMENT, ctx.element().ordinal());
        entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
        entityData.set(DATA_LIFETIME, lifetime);
        entityData.set(DATA_RADIUS, radius);
        entityData.set(DATA_OWNER, Optional.ofNullable(ctx.casterId()));
        List<Element> els = FormsBUtil.spellElements(ctx);
        entityData.set(DATA_ACCENT, FormsBUtil.accentColor(ctx));
        entityData.set(DATA_ACCENT_ELEMENT, els.get(els.size() > 1 ? 1 : 0).ordinal());
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ELEMENT, 0);
        builder.define(DATA_COLOR, Element.ARCANE.color);
        builder.define(DATA_LIFETIME, 100);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_RADIUS, 1.0F);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_ACCENT, 0xFFFFFF);
        builder.define(DATA_ACCENT_ELEMENT, -1);
        defineExtraData(builder);
    }

    /** Subclasses definem aqui os seus dados sincronizados extras. */
    protected void defineExtraData(SynchedEntityData.Builder builder) {}

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(DATA_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    /** Cor RGB da magia (a escolhida pelo jogador ou a do elemento). */
    public int color() { return entityData.get(DATA_COLOR); }

    /** Cor de destaque (detalhes internos): a do 2º elemento numa magia multi-elemento, senão a cor clareada. */
    public int accentColor() { return entityData.get(DATA_ACCENT); }

    /** Elemento de destaque (o 2º de uma magia multi-elemento; senão o próprio). */
    public Element accentElement() {
        Element[] v = Element.values();
        int i = entityData.get(DATA_ACCENT_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : element();
    }

    /** Elemento para a {@code n}-ésima partícula/braço: alterna primário e destaque em magias multi-elemento. */
    public Element elementFor(int n) {
        return (n & 1) == 0 ? element() : accentElement();
    }
    public int lifetime() { return entityData.get(DATA_LIFETIME); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public int age() { return age; }
    public UUID ownerId() { return entityData.get(DATA_OWNER).orElse(null); }
    public long createdTime() { return created; }

    protected void setLifetime(int ticks) { entityData.set(DATA_LIFETIME, ticks); }

    /** 0..1: aparece nos primeiros 10 ticks e some nos últimos 20 (para o renderer). */
    public float fade(float partialTick) {
        float a = age + partialTick;
        float in = Math.min(1F, a / 10F);
        float out = Math.min(1F, Math.max(0F, (lifetime() - a) / 20F));
        return Math.min(in, out);
    }

    /**
     * Contexto da magia (servidor). Recarrega se o conjurador saiu/morreu e voltou — a partir do estado ATUAL
     * (disparos de ligação e orçamento já gastos continuam valendo), não do estado da criação.
     */
    protected SpellContext context() {
        if (!(level() instanceof ServerLevel sl)) return null;
        boolean stale = ctx == null || (ctx.caster() == null && ctx.casterId() != null)
                || (ctx.caster() != null && ctx.caster().isRemoved());
        if (stale && sl.getGameTime() >= nextReload) {
            nextReload = sl.getGameTime() + 20;
            if (ctx != null) spellTag = ctx.save();
            SpellContext fresh = SpellContext.load(sl, spellTag);
            if (fresh != null) ctx = fresh;
        }
        return ctx;
    }

    /**
     * O estágio acabou aqui: avisa o núcleo uma vez só (dispara Ao Terminar, se a magia tiver). Conjurador fora do
     * mundo (magia dormente) ou morto: acaba em silêncio — nenhum estágio novo nasce sem quem o conjurou.
     */
    protected void stageEnd(SpellContext c, Vec3 at) {
        if (stageEnded || c == null) return;
        stageEnded = true;
        LivingEntity caster = c.caster();
        if (caster == null || !caster.isAlive()) return;
        c.end(at, c.look());
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (age % 10 == 0) entityData.set(DATA_AGE, age);
        SpellContext c = context();
        if (c == null) {
            discard();
            return;
        }
        if (!tracked && isPersistentSpell()) {
            tracked = true;
            PersistentSpells.track(this);
            if (isRemoved()) return;
        }
        if (age >= lifetime()) {
            onExpire(sl, c);
            discard();
            return;
        }
        serverTick(sl, c);
    }

    /**
     * O conjurador é um jogador que não está neste mundo agora (saiu ou foi para outra dimensão).
     * Sem ele não dá para saber quem é aliado (PvP desligado, time...), então a magia fica dormente
     * em vez de atingir outros jogadores.
     */
    protected boolean casterAbsent(SpellContext ctx) {
        return byPlayer && ctx.caster() == null;
    }

    /** Lógica do servidor a cada tick (antes de expirar). */
    protected abstract void serverTick(ServerLevel level, SpellContext ctx);

    /** Partículas/efeitos visuais do cliente. */
    protected void clientTick() {}

    /** Chamado quando o tempo de vida acaba (antes de sumir). */
    protected void onExpire(ServerLevel level, SpellContext ctx) {}

    /** Esta magia conta para o limite de magias persistentes por jogador. */
    public boolean isPersistentSpell() { return false; }

    /** Desfaz a magia antes da hora (limite de persistentes atingido). */
    public void fizzle() {
        if (level() instanceof ServerLevel sl) {
            ElementFx.burst(sl, position().add(0, 0.3, 0), element(), 14, 0.3);
            sl.sendParticles(Vfx.smoke(), getX(), getY() + 0.3, getZ(), 10, 0.3, 0.2, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        discard();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_AGE && level().isClientSide) age = entityData.get(DATA_AGE);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide && tracked) PersistentSpells.untrack(this);
    }

    // =====================================================================================
    //  Física e interação: nada (é magia)
    // =====================================================================================
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
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 96 * 96; }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        spellTag = tag.getCompound("Spell");
        age = tag.getInt("Age");
        created = tag.getLong("Created");
        byPlayer = tag.getBoolean("ByPlayer");
        entityData.set(DATA_LIFETIME, tag.getInt("Lifetime"));
        entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        entityData.set(DATA_COLOR, tag.getInt("Color"));
        // Mundos da v0.6 não têm destaque: usa a própria cor clareada
        int col = tag.getInt("Color");
        entityData.set(DATA_ACCENT, tag.contains("Accent") ? tag.getInt("Accent") : lighten(col));
        entityData.set(DATA_ACCENT_ELEMENT, tag.contains("AccentElement") ? tag.getInt("AccentElement") : -1);
        stageEnded = tag.getBoolean("StageEnded");
        entityData.set(DATA_AGE, age);
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(DATA_ELEMENT, e.ordinal());
        entityData.set(DATA_OWNER, spellTag.hasUUID("caster") ? Optional.of(spellTag.getUUID("caster")) : Optional.empty());
        readExtra(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Estado ATUAL do contexto (disparos/orçamento do estágio), não o da criação
        if (ctx != null) spellTag = ctx.save();
        tag.put("Spell", spellTag);
        tag.putInt("Age", age);
        tag.putLong("Created", created);
        tag.putBoolean("ByPlayer", byPlayer);
        tag.putInt("Lifetime", lifetime());
        tag.putFloat("Radius", radius());
        tag.putInt("Color", color());
        tag.putInt("Accent", accentColor());
        tag.putInt("AccentElement", entityData.get(DATA_ACCENT_ELEMENT));
        tag.putBoolean("StageEnded", stageEnded);
        saveExtra(tag);
    }

    protected void readExtra(CompoundTag tag) {}

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + (255 - r) * 35 / 100) << 16) | ((g + (255 - g) * 35 / 100) << 8) | (b + (255 - b) * 35 / 100);
    }

    protected void saveExtra(CompoundTag tag) {}
}
