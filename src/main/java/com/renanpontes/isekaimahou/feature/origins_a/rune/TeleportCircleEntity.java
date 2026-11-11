package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.origins_a.OaData;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Círculo de Teleporte: fica no mundo para sempre (até o dono criar um 3º ou desfazer). Quem pisa num
 * círculo (o dono e aliados) vai para o outro do par. Os pares ficam no attachment do dono ({@link OaData});
 * a entidade guarda uma cópia do destino para funcionar mesmo com o dono desconectado.
 */
public class TeleportCircleEntity extends OriginRuneEntity {
    private static final EntityDataAccessor<Boolean> LINKED = SynchedEntityData.defineId(TeleportCircleEntity.class, EntityDataSerializers.BOOLEAN);
    public static final float RADIUS = 1.0F;

    private GlobalPos partner;
    /** Quem já está em cima (precisa sair e pisar de novo para usar). Não é salvo. */
    private final Set<UUID> inside = new HashSet<>();
    /** false até o primeiro tick depois de criado/carregado: quem já estava em cima não é teleportado na hora. */
    private boolean primed;

    public TeleportCircleEntity(EntityType<? extends TeleportCircleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LINKED, false);
    }

    public boolean linked() { return entityData.get(LINKED); }

    public void setPartner(GlobalPos pos) {
        partner = pos;
        entityData.set(LINKED, pos != null);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (age % 40 == 1) validate(level);
        if (age % 2 != 0) return;
        Set<UUID> now = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.1, 1.5, 0.1))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS || e.getY() < getY() - 0.5 || !e.isAlive()) continue;
            now.add(e.getUUID());
            if (inside.contains(e.getUUID())) continue;
            inside.add(e.getUUID());
            if (!primed) continue;
            if (CircleTravel.inGrace(e)) continue;
            if (!OaUtil.isAlly(level, ownerId(), e) || e.isPassenger() || e.isSpectator()) continue;
            if (partner != null) CircleTravel.travel(level, this, e, partner);
        }
        inside.retainAll(now);
        primed = true;
        if (age % 10 == 0) {
            // Brilho girando na borda (todos veem: é um portal)
            double a = age * 0.15;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Math.PI / 2;
                level.sendParticles(linked() ? ParticleTypes.PORTAL : ParticleTypes.ENCHANT,
                        getX() + Math.cos(ang) * RADIUS * 0.9, getY() + 0.1, getZ() + Math.sin(ang) * RADIUS * 0.9,
                        2, 0.02, 0.05, 0.02, 0.1);
            }
        }
    }

    /** Confere com o dono (se online) se este círculo ainda existe e quem é o par. */
    private void validate(ServerLevel level) {
        ServerPlayer owner = OaUtil.player(level, ownerId());
        if (owner == null) return;
        OaData data = OaData.of(owner);
        if (data.circle(getUUID()) == null) {
            vanish(level);
            return;
        }
        OaData.Circle p = data.partnerOf(getUUID());
        setPartner(p != null ? p.pos() : null);
    }

    /** Some com efeito (substituído ou desfeito). */
    public void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.2, getZ(), 40, 0.6, 0.1, 0.6, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.5F);
        discard();
    }

    /** Marca alguém que acabou de chegar neste círculo (não volta na hora). */
    public void markInside(Entity e) {
        inside.add(e.getUUID());
    }

    public Vec3 arrivalPos() {
        return position().add(0, 0.05, 0);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(linked() ? 3 : 8) != 0) return;
        double a = random.nextDouble() * Math.PI * 2;
        double d = random.nextDouble() * RADIUS;
        level().addParticle(linked() ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ENCHANT,
                getX() + Math.cos(a) * d, getY() + 0.05, getZ() + Math.sin(a) * d, 0, 0.04, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (partner != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, partner).result().ifPresent(t -> tag.put("Partner", t));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPartner(tag.contains("Partner") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("Partner")).result().orElse(null) : null);
    }
}
