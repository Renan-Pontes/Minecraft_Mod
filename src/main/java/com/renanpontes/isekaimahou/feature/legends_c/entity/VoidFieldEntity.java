package com.renanpontes.isekaimahou.feature.legends_c.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Vazio Infinito: marcador no centro do domínio (lei {@code law_infinite_void}). Não faz nada no servidor além de
 * acompanhar o domínio; no cliente, quem está dentro vê o interior virar COSMOS (estrelas, nebulosa, céu escuro).
 */
public class VoidFieldEntity extends LcEntity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(VoidFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_CLOSE = SynchedEntityData.defineId(VoidFieldEntity.class, EntityDataSerializers.LONG);
    public static final int FADE_TICKS = 30;

    private static final Map<Integer, VoidFieldEntity> BY_DOMAIN = new HashMap<>();
    /** Instâncias vivas no CLIENTE. */
    public static final Set<VoidFieldEntity> CLIENT_LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private int domainId = -1;
    private int missing;

    public VoidFieldEntity(EntityType<? extends VoidFieldEntity> type, Level level) {
        super(type, level);
    }

    public static void open(Domains.Info d) {
        close(d.id());
        VoidFieldEntity v = new VoidFieldEntity(LcRegistry.VOID_FIELD.get(), d.level());
        v.bindOwner(d.owner());
        v.domainId = d.id();
        v.setPos(d.center().x, d.center().y, d.center().z);
        v.entityData.set(DATA_RADIUS, d.radius());
        d.level().addFreshEntity(v);
        BY_DOMAIN.put(d.id(), v);
    }

    public static void update(Domains.Info d, int age) {
        VoidFieldEntity v = BY_DOMAIN.get(d.id());
        if (v == null || v.isRemoved()) {
            // O marcador não é salvo: depois de sair e entrar no mundo (ou o chunk recarregar) o domínio continua aberto
            // mas o cosmos sumiu — recria em silêncio (sem anúncio nem som de abertura).
            if (age % 20 == 0) open(d);
            return;
        }
        if (Math.abs(v.radius() - d.radius()) > 0.05F) v.entityData.set(DATA_RADIUS, d.radius());
        Vec3 c = d.center();
        if (v.position().distanceToSqr(c) > 0.01) v.setPos(c.x, c.y, c.z);
    }

    public static void close(int domainId) {
        VoidFieldEntity v = BY_DOMAIN.remove(domainId);
        if (v != null && !v.isRemoved() && v.closingAge() < 0) v.entityData.set(DATA_CLOSE, v.level().getGameTime());
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 20F);
        builder.define(DATA_CLOSE, -1L);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    /** Ticks desde que começou a fechar (-1 = aberto). */
    public int closingAge() {
        long c = entityData.get(DATA_CLOSE);
        return c < 0 ? -1 : (int) (level().getGameTime() - c);
    }

    /** 0..1: o cosmos aparecendo/sumindo. */
    public float presence(float pt) {
        int c = closingAge();
        if (c >= 0) return Math.max(0F, 1F - (c + pt) / FADE_TICKS);
        return Math.min(1F, (tickCount + pt) / 20F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            CLIENT_LIVE.add(this);
            return;
        }
        ServerLevel l = (ServerLevel) level();
        int c = closingAge();
        if (c >= 0) {
            if (c >= FADE_TICKS) discard();
            return;
        }
        boolean alive = false;
        for (Domains.Info d : Domains.active(l)) if (d.id() == domainId) { alive = true; break; }
        missing = alive ? 0 : missing + 1;
        if (missing > 60) {
            BY_DOMAIN.remove(domainId, this);
            entityData.set(DATA_CLOSE, level().getGameTime());
            return;
        }
        if (tickCount % 60 == 0) LcFx.sound(l, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 0.5F);
    }

    public static void clear() {
        BY_DOMAIN.clear();
    }
}
