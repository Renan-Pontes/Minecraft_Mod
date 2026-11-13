package com.renanpontes.isekaimahou.feature.space_magic.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.space_magic.SpaceContent;
import com.renanpontes.isekaimahou.feature.space_magic.SpaceGates;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Portal Espacial do glifo Abrir Portal: um anel vertical que leva quem ATRAVESSA para o outro lado (outra dimensão
 * ou o portal de volta). Some sozinho (~30 s) e não é salvo no mundo. Quem acabou de chegar só é levado de novo
 * depois de sair do anel e entrar outra vez.
 */
public class SpaceGateEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SpaceGateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(SpaceGateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(SpaceGateEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_EXIT = SynchedEntityData.defineId(SpaceGateEntity.class, EntityDataSerializers.BOOLEAN);

    /** Meia largura do anel e altura útil (blocos). */
    public static final double HALF_WIDTH = 1.05, HEIGHT = 2.7;

    @Nullable private ResourceKey<Level> destDim;
    private Vec3 destPos = Vec3.ZERO;
    private float destYaw;
    @Nullable private UUID ownerId;
    private final Set<UUID> inside = new HashSet<>();

    public SpaceGateEntity(EntityType<? extends SpaceGateEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Cria (sem adicionar ao mundo) um portal em {@code pos} virado para {@code yaw}, que leva até {@code dest}. */
    public static SpaceGateEntity create(ServerLevel level, Vec3 pos, float yaw, ResourceKey<Level> destDim, Vec3 destPos, float destYaw,
                                         @Nullable UUID owner, int color, int life, boolean exit) {
        SpaceGateEntity g = new SpaceGateEntity(SpaceContent.SPACE_GATE.get(), level);
        g.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        g.setYRot(yaw);
        g.destDim = destDim;
        g.destPos = destPos;
        g.destYaw = destYaw;
        g.ownerId = owner;
        g.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        g.entityData.set(DATA_LIFE, life);
        g.entityData.set(DATA_EXIT, exit);
        g.entityData.set(DATA_BORN, level.getGameTime());
        return g;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_COLOR, 0xB98CFF);
        b.define(DATA_LIFE, SpaceGates.GATE_LIFE);
        b.define(DATA_EXIT, false);
        b.define(DATA_BORN, 0L);
    }

    public int color() { return entityData.get(DATA_COLOR); }
    public int life() { return Math.max(1, entityData.get(DATA_LIFE)); }
    public boolean isExit() { return entityData.get(DATA_EXIT); }
    @Nullable public UUID ownerId() { return ownerId; }

    /**
     * Idade em ticks pelo relógio do mundo (igual no servidor e no cliente, mesmo para quem chega depois e passa a
     * ver o portal só no meio da vida dele).
     */
    public long age() {
        long born = entityData.get(DATA_BORN);
        return born <= 0 ? tickCount : Math.max(0, level().getGameTime() - born);
    }

    /** Abertura 0..1 (abre nos primeiros 15 ticks, fecha nos últimos 20). */
    public float openness(float pt) {
        float t = age() + pt;
        float open = Mth.clamp(t / 15F, 0F, 1F);
        float close = Mth.clamp((life() - t) / 20F, 0F, 1F);
        return Math.min(open, close);
    }

    /** Direção "para dentro" do anel (perpendicular ao plano). */
    public Vec3 normal() {
        float r = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /** Direção ao longo do plano do anel (horizontal). */
    public Vec3 side() {
        float r = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0, Mth.sin(r));
    }

    /** Marca quem acabou de chegar por este portal (só volta depois de sair e entrar de novo). */
    public void markInside(Entity e) {
        inside.add(e.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        long age = age();
        if (age >= life() || tickCount > life() + 40) {
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + HEIGHT * 0.5, getZ(), 40, 0.4, 0.9, 0.4, 0.05);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.8F);
            discard();
            return;
        }
        if (tickCount % 40 == 5) {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.35F, 1.3F);
        }
        if (age < 12 || destDim == null) return; // ainda abrindo
        Vec3 n = normal(), s = side();
        Set<UUID> now = new HashSet<>();
        List<Entity> near = level().getEntities(this, new AABB(getX() - 2.2, getY() - 0.5, getZ() - 2.2, getX() + 2.2, getY() + HEIGHT + 0.5,
                getZ() + 2.2), e -> e.isAlive() && !e.isSpectator() && !(e instanceof SpaceGateEntity));
        for (Entity e : near) {
            Vec3 d = e.position().add(0, e.getBbHeight() * 0.4, 0).subtract(position());
            if (Math.abs(d.dot(n)) > 0.55 || Math.abs(d.dot(s)) > HALF_WIDTH || d.y < -0.3 || d.y > HEIGHT) continue;
            now.add(e.getUUID());
            if (inside.contains(e.getUUID()) || SpaceGates.justArrived(e)) continue;
            if (!SpaceGates.canPass(e)) continue;
            SpaceGates.pass(this, e, destDim, destPos, destYaw);
        }
        inside.retainAll(now);
        inside.addAll(now);
    }

    private void clientParticles() {
        float open = openness(0);
        if (open <= 0.2F || random.nextInt(2) != 0) return;
        Vec3 s = side();
        double a = random.nextDouble() * Math.PI * 2;
        double x = getX() + s.x * Math.cos(a) * HALF_WIDTH, z = getZ() + s.z * Math.cos(a) * HALF_WIDTH;
        double y = getY() + HEIGHT * 0.5 + Math.sin(a) * HEIGHT * 0.48;
        level().addParticle(ParticleTypes.PORTAL, x, y, z, (getX() - x) * 0.6, 0, (getZ() - z) * 0.6);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Não é salvo (noSave): o portal é temporário. Mantido só pelo contrato da Entity.
        if (tag.contains("dest")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("dest"));
            if (id != null) destDim = ResourceKey.create(Registries.DIMENSION, id);
            destPos = new Vec3(tag.getDouble("dx"), tag.getDouble("dy"), tag.getDouble("dz"));
            destYaw = tag.getFloat("dyaw");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (destDim != null) {
            tag.putString("dest", destDim.location().toString());
            tag.putDouble("dx", destPos.x);
            tag.putDouble("dy", destPos.y);
            tag.putDouble("dz", destPos.z);
            tag.putFloat("dyaw", destYaw);
        }
    }
}
