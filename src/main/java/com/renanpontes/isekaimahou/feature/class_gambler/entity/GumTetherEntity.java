package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.class_gambler.AnimeTricks;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * GOMA ELÁSTICA (homenagem ao mágico das cartas que gruda a sua aura nas coisas): um elo rosa, elástico, entre você e o
 * alvo (ou entre você e um ponto do mundo). Desenhado no cliente por {@code GumTetherRenderer}; não é salvo e some sozinho.
 * <ul>
 *   <li><b>Puxar</b> (num inimigo): o alvo não consegue se afastar mais que {@link #REST} blocos — a goma o puxa de volta.
 *   <b>Agache</b> para CONTRAIR: o alvo é puxado até você e ARREMESSADO para onde você olha; ao bater (parede/chão) leva
 *   o dano do impacto. Chefes só são puxados de leve e levam o impacto direto.</li>
 *   <li><b>Fugir</b> (em você): a goma gruda no bloco para onde você olha e te puxa até lá (agache solta).</li>
 * </ul>
 * Até {@link #MAX_PER_OWNER} elos de Puxar por conjurador (o mais antigo se solta) e um só de Fugir.
 */
public class GumTetherEntity extends Entity {
    public static final double REST = 4.0;
    public static final double MAX_STRETCH = 28.0;
    public static final float IMPACT_DAMAGE = 8F;
    private static final int STRETCH = 0, CONTRACT = 1, FLUNG = 2;
    private static final int MODE_PULL = 0, MODE_GRAPPLE = 1;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GumTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(GumTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_ANCHOR = SynchedEntityData.defineId(GumTetherEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(GumTetherEntity.class, EntityDataSerializers.BYTE);

    public static final int MAX_PER_OWNER = 3;
    private static final Map<UUID, List<GumTetherEntity>> BY_OWNER = new HashMap<>();

    @Nullable
    private SpellContext ctx;
    @Nullable
    private SpellStats stats;
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private int life = 120;
    private int mode = MODE_PULL;
    private int stateTicks;

    public GumTetherEntity(EntityType<? extends GumTetherEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // =====================================================================================
    //  Criar
    // =====================================================================================
    /** Gruda a goma em um alvo (modo Puxar). */
    public static void attach(SpellContext ctx, LivingEntity owner, LivingEntity target, SpellStats stats, int life) {
        // o mesmo alvo não leva duas gomas
        for (GumTetherEntity old : new ArrayList<>(list(owner.getUUID()))) {
            if (old.mode == MODE_PULL && target.getUUID().equals(old.targetId)) old.snap(false);
        }
        GumTetherEntity e = base(ctx, owner, stats, life, MODE_PULL);
        e.targetId = target.getUUID();
        e.entityData.set(DATA_TARGET, target.getId());
        e.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        ctx.level().addFreshEntity(e);
    }

    /** Gruda a goma num ponto do mundo e puxa o dono até lá (modo Fugir). */
    public static void grapple(SpellContext ctx, LivingEntity owner, Vec3 anchor, SpellStats stats, int life) {
        GumTetherEntity e = base(ctx, owner, stats, life, MODE_GRAPPLE);
        e.entityData.set(DATA_TARGET, -1);
        e.entityData.set(DATA_ANCHOR, new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z));
        e.setPos(anchor.x, anchor.y, anchor.z);
        ctx.level().addFreshEntity(e);
    }

    /** Elos vivos do dono (limpa os que já sumiram). */
    private static List<GumTetherEntity> list(UUID owner) {
        List<GumTetherEntity> l = BY_OWNER.computeIfAbsent(owner, k -> new ArrayList<>());
        l.removeIf(Entity::isRemoved);
        return l;
    }

    private static GumTetherEntity base(SpellContext ctx, LivingEntity owner, SpellStats stats, int life, int mode) {
        List<GumTetherEntity> mine = list(owner.getUUID());
        // um só elo de Fugir; até MAX_PER_OWNER de Puxar (o mais antigo se solta)
        for (GumTetherEntity old : new ArrayList<>(mine)) {
            if (old.mode == MODE_GRAPPLE || mode == MODE_GRAPPLE) old.snap(false);
        }
        mine = list(owner.getUUID());
        while (mine.size() >= MAX_PER_OWNER) {
            mine.get(0).snap(false);
            mine = list(owner.getUUID());
        }
        GumTetherEntity e = new GumTetherEntity(GbRegistries.GUM_TETHER.get(), ctx.level());
        e.mode = mode;
        e.ctx = ctx;
        e.stats = stats;
        e.ownerId = owner.getUUID();
        e.life = Math.max(20, life);
        e.entityData.set(DATA_OWNER, owner.getId());
        mine.add(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_ANCHOR, new Vector3f());
        builder.define(DATA_STATE, (byte) STRETCH);
    }

    // =====================================================================================
    //  Leitura (cliente e servidor)
    // =====================================================================================
    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    /** O alvo preso, ou null no modo Fugir (aí vale {@link #anchor()}). */
    @Nullable
    public Entity target() {
        int id = entityData.get(DATA_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public boolean anchored() {
        return entityData.get(DATA_TARGET) < 0;
    }

    public Vec3 anchor() {
        Vector3f v = entityData.get(DATA_ANCHOR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** 0 esticada, 1 contraindo, 2 arremesso (o renderer deixa a goma mais esticada/fina). */
    public int state() {
        return entityData.get(DATA_STATE);
    }

    // =====================================================================================
    //  Tique
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        if (ctx == null || stats == null || owner == null || !owner.isAlive() || owner.level() != sl || tickCount > life) {
            snap(true);
            return;
        }
        if (mode == MODE_GRAPPLE) {
            grappleTick(sl, owner);
            return;
        }
        LivingEntity target = targetId != null && sl.getEntity(targetId) instanceof LivingEntity le ? le : null;
        if (target == null || !target.isAlive() || target.level() != sl) {
            snap(true);
            return;
        }
        setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        Vec3 a = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
        Vec3 b = position();
        double d = a.distanceTo(b);
        if (d > MAX_STRETCH) {
            snap(true);
            return;
        }
        boolean boss = AnimeTricks.isBoss(target);
        int state = state();
        if (state == STRETCH) {
            if (owner.isShiftKeyDown() && tickCount > 5) {
                setState(CONTRACT);
                GbFx.sound(sl, b, SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
                GbFx.sound(sl, a, SoundEvents.CROSSBOW_LOADING_END.value(), 0.8F, 1.6F);
            } else if (d > REST) {
                Vec3 dir = a.subtract(b).normalize();
                double resist = Math.min(1.0, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double str = Math.min(0.32, (d - REST) * 0.07) * (boss ? 0.2 : 1.0) * (1.0 - resist * 0.5);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.8).add(dir.scale(str)));
                target.hurtMarked = true;
            }
        } else if (state == CONTRACT) {
            stateTicks++;
            Vec3 look = owner.getLookAngle();
            Vec3 flat = look.multiply(1, 0, 1).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.multiply(1, 0, 1).normalize();
            Vec3 front = owner.position().add(flat.scale(1.6)).add(0, 0.6, 0);
            Vec3 v = front.subtract(target.position()).scale(boss ? 0.06 : 0.35);
            target.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
            target.hurtMarked = true;
            if (stateTicks >= 6 || target.position().distanceTo(front) < 1.6) {
                if (boss) {
                    impact(sl, target);
                    return;
                }
                Vec3 throwDir = look.lengthSqr() < 1.0E-4 ? flat : look.normalize();
                target.setDeltaMovement(throwDir.x * 1.9, Math.max(0.55, throwDir.y * 1.9 + 0.4), throwDir.z * 1.9);
                target.hurtMarked = true;
                setState(FLUNG);
                GbFx.sound(sl, target.position(), SoundEvents.SLIME_JUMP, 1.2F, 0.6F);
                GbFx.sound(sl, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
            }
        } else {
            stateTicks++;
            if (tickCount % 2 == 0) sl.sendParticles(GbFx.dust(0xFF6EC7, 1.1F), b.x, b.y, b.z, 2, 0.15, 0.15, 0.15, 0);
            if ((stateTicks > 3 && (target.horizontalCollision || target.onGround())) || stateTicks > 30) impact(sl, target);
        }
        if (tickCount % 6 == 0) sl.sendParticles(GbFx.dust(0xFFA8DD, 0.7F), b.x, b.y, b.z, 1, 0.2, 0.2, 0.2, 0);
    }

    private void setState(int s) {
        entityData.set(DATA_STATE, (byte) s);
        stateTicks = 0;
    }

    private void grappleTick(ServerLevel sl, LivingEntity owner) {
        Vec3 anchor = anchor();
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
        double d = c.distanceTo(anchor);
        owner.fallDistance = 0;
        if (d < 1.6 || (owner.isShiftKeyDown() && tickCount > 4)) {
            snap(true);
            return;
        }
        Vec3 dir = anchor.subtract(c).normalize();
        owner.setDeltaMovement(owner.getDeltaMovement().scale(0.6).add(dir.scale(0.55)));
        owner.hurtMarked = true;
        if (tickCount % 3 == 0) sl.sendParticles(GbFx.dust(0xFF6EC7, 0.8F), c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
    }

    /** O alvo arremessado bateu: dano do impacto (pela magia) e a goma se solta. */
    private void impact(ServerLevel sl, LivingEntity target) {
        if (ctx != null && stats != null) {
            target.invulnerableTime = 0;
            ctx.damage(target, IMPACT_DAMAGE, stats, this);
        }
        Vec3 p = target.position().add(0, target.getBbHeight() * 0.5, 0);
        GbFxNet.at(sl, GbFxNet.GUM_SNAP, p, Vec3.ZERO, 1F, 0xFF6EC7, target.getId());
        GbFx.sound(sl, p, SoundEvents.GENERIC_SMALL_FALL, 1.2F, 0.6F);
        GbFx.sound(sl, p, SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
        snap(true);
    }

    /** A goma se solta (some). */
    public void snap(boolean fx) {
        if (isRemoved()) return;
        if (fx && level() instanceof ServerLevel sl) {
            sl.sendParticles(GbFx.dust(0xFF6EC7, 1.0F), getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0);
            GbFx.sound(sl, position(), SoundEvents.SLIME_SQUISH_SMALL, 0.8F, 1.4F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (ownerId != null) {
            List<GumTetherEntity> l = BY_OWNER.get(ownerId);
            if (l != null) {
                l.remove(this);
                if (l.isEmpty()) BY_OWNER.remove(ownerId);
            }
        }
    }

    public static void forget(UUID owner) {
        List<GumTetherEntity> l = BY_OWNER.remove(owner);
        if (l == null) return;
        for (GumTetherEntity e : new ArrayList<>(l)) if (!e.isRemoved()) e.discard();
    }

    public static void clearAll() {
        BY_OWNER.clear();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
