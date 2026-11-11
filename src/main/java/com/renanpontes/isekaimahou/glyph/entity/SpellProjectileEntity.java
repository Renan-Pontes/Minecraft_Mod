package com.renanpontes.isekaimahou.glyph.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil genérico das magias (forma Projétil e outras). Guarda a magia e aplica ao acertar.
 * Suporta os aumentos: Perfurar, Ricochete, Teleguiado, Fantasma, Arco, Sensível, Expandir área.
 * Visual (v1.1): núcleo brilhante + fita de rastro ({@code client/spellfx/SpellProjectileRenderer}), partículas do
 * elemento e impacto com clarão/onda/estilhaços ({@link ElementFx#impact}), tudo escalado pelo tier visual da magia.
 */
public class SpellProjectileEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> ELEMENT =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.FLOAT);
    /** v1.1: tier visual (0 fraca .. 5 ultimate) e cor da magia (RGB) — só visual. */
    private static final EntityDataAccessor<Integer> TIER =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.INT);
    /** Cliente: últimas posições (rastro desenhado pelo renderer). */
    public static final int TRAIL_LEN = 14;
    private final double[] trail = new double[TRAIL_LEN * 3];
    private int trailHead;
    private int trailCount;

    private CompoundTag spellTag;
    private SpellContext ctx;
    private int pierceLeft;
    private int ricochetLeft;
    private boolean homing;
    private boolean ghost;
    private boolean arc;
    private boolean sensitive;
    private float impactRadius;
    private int lifetime = 100;
    private final Set<UUID> hitEntities = new HashSet<>();

    public SpellProjectileEntity(EntityType<? extends SpellProjectileEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Cria um projétil a partir do contexto, aplicando os aumentos da forma. Depois chame shoot(...) e addFreshEntity.
     * Se o contexto tem origem própria (estágio de magia programável), nasce nela; senão, nos olhos do atirador.
     * Ignora a entidade atingida pelo estágio anterior.
     */
    public static SpellProjectileEntity create(SpellContext ctx, LivingEntity shooter) {
        SpellProjectileEntity p = new SpellProjectileEntity(CoreRegistries.SPELL_PROJECTILE.get(), ctx.level());
        p.setOwner(shooter);
        if (ctx.hasOrigin()) {
            Vec3 o = ctx.eyePos();
            p.setPos(o.x, o.y, o.z);
        } else {
            p.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
        }
        p.ctx = ctx;
        p.spellTag = ctx.save();
        if (ctx.previousTarget() != null) p.hitEntities.add(ctx.previousTarget());
        SpellStats s = ctx.formStats();
        p.pierceLeft = Math.max(0, s.pierce);
        p.ricochetLeft = Math.max(0, s.ricochet);
        p.homing = s.homing;
        p.ghost = s.ghost;
        p.arc = s.arc;
        p.sensitive = s.sensitive;
        p.impactRadius = s.aoe > 0 ? s.radius(1.5F) : 0;
        p.entityData.set(ELEMENT, ctx.element().ordinal());
        p.entityData.set(SIZE, 1.0F + 0.25F * Math.max(0, s.amplify + s.charge));
        p.entityData.set(TIER, SpellVisuals.tier(ctx));
        p.entityData.set(COLOR, ctx.color() & 0xFFFFFF);
        return p;
    }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public float size() { return entityData.get(SIZE); }

    /** Tier visual (0..5). */
    public int visualTier() { return entityData.get(TIER); }

    /** Cor da magia (RGB). */
    public int color() { return entityData.get(COLOR); }

    /** Cliente: quantas posições de rastro existem. */
    public int trailCount() { return trailCount; }

    /** Cliente: posição de rastro {@code i} (0 = a mais recente). */
    public Vec3 trailAt(int i) {
        int idx = Math.floorMod(trailHead - 1 - i, TRAIL_LEN) * 3;
        return new Vec3(trail[idx], trail[idx + 1], trail[idx + 2]);
    }

    /** Impacto com área (Expandir) parece um tier acima (sem virar "ultimate"). */
    private int areaTier() {
        int t = visualTier();
        return t >= 5 ? 5 : Math.min(4, t + 1);
    }

    private void pushTrail(Vec3 p) {
        int idx = trailHead * 3;
        trail[idx] = p.x;
        trail[idx + 1] = p.y;
        trail[idx + 2] = p.z;
        trailHead = (trailHead + 1) % TRAIL_LEN;
        if (trailCount < TRAIL_LEN) trailCount++;
    }

    public void setLifetime(int ticks) { this.lifetime = ticks; }

    /** Faz o projétil atravessar esta entidade sem atingi-la (ex.: quem acabou de ser atingido). */
    public void exclude(Entity e) {
        if (e != null) hitEntities.add(e.getUUID());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ELEMENT, 0);
        builder.define(SIZE, 1.0F);
        builder.define(TIER, 1);
        builder.define(COLOR, 0xB98CFF);
    }

    @Override
    protected double getDefaultGravity() {
        return arc ? 0.04 : 0.0;
    }

    private SpellContext context() {
        if (ctx == null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 c = position().add(0, getBbHeight() / 2, 0);
            pushTrail(c);
            ElementFx.clientTrail(level(), c, getDeltaMovement(), element(), color(), size(), visualTier());
            return;
        }
        if (isRemoved()) return;
        if (tickCount > lifetime) {
            // Expirou sem acertar: dispara a ligação Ao Terminar
            SpellContext c = context();
            if (c != null) c.end(position(), getDeltaMovement());
            discard();
            return;
        }
        if (homing && tickCount > 3) steerToTarget();
        if (ghost) noPhysics = true;
    }

    private void steerToTarget() {
        SpellContext c = context();
        if (c == null) return;
        LivingEntity best = null;
        double bestD = 18 * 18;
        for (LivingEntity e : c.enemiesAround(position(), 18)) {
            if (hitEntities.contains(e.getUUID())) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(position()).normalize();
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        setDeltaMovement(v.normalize().lerp(to, 0.2).normalize().scale(speed));
        hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !hitEntities.contains(target.getUUID());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        SpellContext c = context();
        if (c == null) { discard(); return; }
        Entity target = result.getEntity();
        hitEntities.add(target.getUUID());
        // Avisa o impacto antes de aplicar (Ao Acertar/Saltar usam o ponto e a direção exatos do projétil)
        c.impact(result.getLocation(), getDeltaMovement(), target);
        if (impactRadius > 0) {
            c.resolveArea(result.getLocation(), impactRadius, this, sensitive);
            ElementFx.burst((ServerLevel) level(), result.getLocation(), c.element(), (int) (12 + impactRadius * 8), impactRadius * 0.4);
            ElementFx.impact((ServerLevel) level(), result.getLocation(), c.element(), color(), areaTier(), getDeltaMovement().scale(-1));
        } else {
            c.resolveOnEntity(target, this);
            ElementFx.impact((ServerLevel) level(), result.getLocation(), c.element(), color(), visualTier(), getDeltaMovement().scale(-1));
        }
        if (pierceLeft > 0) {
            pierceLeft--;
        } else {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide || ghost) return;
        SpellContext c = context();
        if (c == null) { discard(); return; }
        if (ricochetLeft > 0) {
            ricochetLeft--;
            Vec3 v = getDeltaMovement();
            Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))));
            hasImpulse = true;
            ElementFx.impact((ServerLevel) level(), result.getLocation(), c.element(), color(), Math.max(0, visualTier() - 1), n);
            return;
        }
        c.impact(result.getLocation(), getDeltaMovement(), null);
        if (impactRadius > 0) {
            c.resolveArea(result.getLocation(), impactRadius, this, true);
        } else {
            c.resolveOnBlock(result, this);
        }
        if (impactRadius > 0) {
            ElementFx.burst((ServerLevel) level(), result.getLocation(), c.element(), (int) (10 + impactRadius * 8), Math.max(0.25, impactRadius * 0.4));
        }
        ElementFx.impact((ServerLevel) level(), result.getLocation(), c.element(), color(),
                impactRadius > 0 ? areaTier() : visualTier(), Vec3.atLowerCornerOf(result.getDirection().getNormal()));
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Salva o contexto atual (com disparos de ligação já feitos), não só o do momento em que nasceu
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
        tag.putInt("Pierce", pierceLeft);
        tag.putInt("Ricochet", ricochetLeft);
        tag.putBoolean("Homing", homing);
        tag.putBoolean("Ghost", ghost);
        tag.putBoolean("Arc", arc);
        tag.putBoolean("Sensitive", sensitive);
        tag.putFloat("Radius", impactRadius);
        tag.putInt("Lifetime", lifetime);
        tag.putInt("FxTier", visualTier());
        tag.putInt("FxColor", color());
        // Quem já foi atingido/ignorado (Perfurar, alvo anterior de um estágio): não acerta de novo depois de recarregar
        ListTag hit = new ListTag();
        for (UUID id : hitEntities) hit.add(NbtUtils.createUUID(id));
        tag.put("Hit", hit);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spellTag = tag.getCompound("Spell");
        pierceLeft = tag.getInt("Pierce");
        ricochetLeft = tag.getInt("Ricochet");
        homing = tag.getBoolean("Homing");
        ghost = tag.getBoolean("Ghost");
        arc = tag.getBoolean("Arc");
        sensitive = tag.getBoolean("Sensitive");
        impactRadius = tag.getFloat("Radius");
        lifetime = tag.contains("Lifetime") ? tag.getInt("Lifetime") : 100;
        if (tag.contains("FxTier")) entityData.set(TIER, tag.getInt("FxTier"));
        if (tag.contains("FxColor")) entityData.set(COLOR, tag.getInt("FxColor"));
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(ELEMENT, e.ordinal());
        if (e != null && !tag.contains("FxColor")) entityData.set(COLOR, e.color);
        hitEntities.clear();
        for (Tag t : tag.getList("Hit", Tag.TAG_INT_ARRAY)) hitEntities.add(NbtUtils.loadUUID(t));
    }
}
