package com.renanpontes.isekaimahou.feature.legends_a.entity;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.LegendsARegistry;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.ArsenalGate;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Um portal dourado do Portão do Arsenal. Fica num arco atrás do conjurador (acompanhando-o) — ou, num estágio de
 * magia programável, atrás do ponto de origem — e de tempos em tempos uma arma sai dele e voa até o alvo.
 * Toda a lógica de mira/disparo fica no {@link ArsenalGate} compartilhado pelos portais do mesmo portão.
 */
public class ArsenalPortalEntity extends Entity {
    public static final int OPEN_TICKS = 10;
    public static final int CLOSE_TICKS = 8;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOTS = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INTERVAL = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ArsenalPortalEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Servidor: o portão a que este portal pertence. */
    private ArsenalGate gate;

    public ArsenalPortalEntity(EntityType<? extends ArsenalPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ArsenalPortalEntity create(ServerLevel level, ArsenalGate gate, int slot, @Nullable Entity owner, int color,
                                             int lifetime, int interval, float size) {
        ArsenalPortalEntity p = new ArsenalPortalEntity(LegendsARegistry.ARSENAL_PORTAL.get(), level);
        p.gate = gate;
        p.entityData.set(DATA_OWNER, owner != null ? owner.getId() : -1);
        p.entityData.set(DATA_SLOT, slot);
        p.entityData.set(DATA_SLOTS, gate.slots());
        p.entityData.set(DATA_COLOR, color);
        p.entityData.set(DATA_LIFETIME, lifetime);
        p.entityData.set(DATA_INTERVAL, interval);
        p.entityData.set(DATA_PHASE, slot * interval / Math.max(1, gate.slots()));
        p.entityData.set(DATA_SIZE, size);
        p.entityData.set(DATA_ITEM, LegendFx.treasureWeapon(level.random));
        Vec3 pos = gate.slotPos(slot);
        p.setPos(pos.x, pos.y, pos.z);
        p.setAim(gate.forward());
        return p;
    }

    // =====================================================================================
    //  Geometria do arco (compartilhada: servidor dispara, cliente desenha no mesmo lugar)
    // =====================================================================================

    /**
     * Posição de um portal no arco atrás das costas. {@code base} = pés do conjurador (ou o ponto-base de um estágio),
     * {@code yawDeg} = para onde ele olha (padrão do Minecraft: 0 = +Z).
     */
    public static Vec3 slotPos(Vec3 base, float yawDeg, int slot, int slots) {
        double spread = slots <= 1 ? 0 : Math.toRadians(Math.min(165, 30 + 20 * slots));
        double a = slots <= 1 ? 0 : -spread / 2 + spread * slot / (slots - 1);
        double r = 2.0 + 0.22 * slots;
        double yaw = Math.toRadians(yawDeg);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double up = 1.5 + r * Math.cos(a) * 0.8;
        double back = 1.0 + 0.45 * Math.cos(a);
        return base.add(right.scale(r * Math.sin(a))).add(0, up, 0).subtract(fwd.scale(back));
    }

    public static float yawOf(Vec3 forward) {
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_SLOT, 0);
        builder.define(DATA_SLOTS, 4);
        builder.define(DATA_COLOR, LegendFx.GOLD);
        builder.define(DATA_LIFETIME, 80);
        builder.define(DATA_INTERVAL, 20);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_SIZE, 0.9F);
        builder.define(DATA_AIM, new Vector3f(0, 0, 1));
        builder.define(DATA_ITEM, new ItemStack(Items.GOLDEN_SWORD));
    }

    @Nullable
    public Entity owner() {
        int id = entityData.get(DATA_OWNER);
        return id < 0 ? null : level().getEntity(id);
    }

    public boolean bound() { return entityData.get(DATA_OWNER) >= 0; }
    public int slot() { return entityData.get(DATA_SLOT); }
    public int slots() { return entityData.get(DATA_SLOTS); }
    public int color() { return entityData.get(DATA_COLOR); }
    public int lifetime() { return entityData.get(DATA_LIFETIME); }
    public int interval() { return entityData.get(DATA_INTERVAL); }
    public int phase() { return entityData.get(DATA_PHASE); }
    public float size() { return entityData.get(DATA_SIZE); }
    public ItemStack item() { return entityData.get(DATA_ITEM); }

    public Vec3 aim() {
        Vector3f v = entityData.get(DATA_AIM);
        Vec3 d = new Vec3(v.x(), v.y(), v.z());
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    public void setAim(Vec3 d) {
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 n = d.normalize();
        Vector3f old = entityData.get(DATA_AIM);
        // Só sincroniza quando muda de verdade (evita pacote a cada tick)
        if (Math.abs(old.x() - n.x) + Math.abs(old.y() - n.y) + Math.abs(old.z() - n.z) > 0.02) {
            entityData.set(DATA_AIM, new Vector3f((float) n.x, (float) n.y, (float) n.z));
        }
    }

    public void setItem(ItemStack s) {
        entityData.set(DATA_ITEM, s);
    }

    /** Tick em que o próximo disparo acontece: progresso 0..1 da arma saindo do portal (cliente). */
    public float chargeProgress(float partialTick) {
        float t = tickCount + partialTick - OPEN_TICKS - phase();
        if (t < 0 || tickCount > lifetime() - CLOSE_TICKS) return 0;
        return (t % interval()) / interval();
    }

    /** Abertura/fechamento (0..1). */
    public float openness(float partialTick) {
        float a = tickCount + partialTick;
        float in = Mth.clamp(a / OPEN_TICKS, 0, 1);
        float out = Mth.clamp((lifetime() - a) / CLOSE_TICKS, 0, 1);
        return Math.min(in, out);
    }

    /** Este tick é de disparo? (mesma regra do cliente) */
    public boolean firesNow() {
        int t = tickCount - OPEN_TICKS - phase();
        return t >= 0 && t % interval() == 0 && tickCount < lifetime() - CLOSE_TICKS;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (bound() && owner != null) {
            Vec3 p = slotPos(owner.position(), owner.getYRot(), slot(), slots());
            setPos(p.x, p.y, p.z);
        }
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                Vec3 p = position();
                double s = size();
                level().addParticle(LegendFx.dust(color(), 0.7F), p.x + (random.nextDouble() - 0.5) * s * 1.6,
                        p.y + (random.nextDouble() - 0.5) * s * 1.6, p.z + (random.nextDouble() - 0.5) * s * 1.6, 0, 0.01, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (gate == null || (bound() && (owner == null || !owner.isAlive() || owner.level() != sl))) {
            if (gate != null) gate.portalGone(sl);
            discard();
            return;
        }
        gate.refresh(sl);
        Vec3 aimPoint = gate.aimPoint();
        if (aimPoint != null) setAim(aimPoint.subtract(position()));
        if (tickCount == 1) {
            sl.sendParticles(LegendFx.dust(color(), 1.4F), getX(), getY(), getZ(), 16, 0.4, 0.4, 0.4, 0);
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 4, 0.3, 0.3, 0.3, 0.02);
        }
        if (firesNow()) gate.fire(sl, this);
        if (tickCount >= lifetime()) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.3, 0.3, 0.3, 0.03);
            gate.portalGone(sl);
            discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Entity owner = owner();
        AABB box = super.getBoundingBoxForCulling().inflate(1.5);
        return owner != null ? box.minmax(owner.getBoundingBox().inflate(5)) : box;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Som de abertura do portão (tocado uma vez pelo {@link ArsenalGate}). */
    public static void openSound(ServerLevel sl, Vec3 at) {
        LegendFx.sound(sl, at, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
        LegendFx.sound(sl, at, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.2F);
    }
}
