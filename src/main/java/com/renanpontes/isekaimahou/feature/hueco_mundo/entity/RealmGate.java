package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Portal temporário de um reino. Garganta (Mundo Oco): um rasgo negro que se abre no ar e escorre até o chão; Círculo
 * das Fadas: um anel de pétalas e luz no chão. Quem entra na coluna do portal atravessa (entra no reino, ou volta se já
 * estiver nele). Some sozinho no fim da vida; nada fica no mundo.
 */
public class RealmGate extends Entity {
    private static final EntityDataAccessor<Integer> DATA_REALM = SynchedEntityData.defineId(RealmGate.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(RealmGate.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(RealmGate.class, EntityDataSerializers.INT);
    /** Ticks abrindo/fechando (animação). */
    public static final int OPEN_TICKS = 30, CLOSE_TICKS = 20;
    private final Map<UUID, Long> crossed = new HashMap<>();

    public RealmGate(EntityType<? extends RealmGate> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setup(HmRealm realm, int lifetime) {
        entityData.set(DATA_REALM, realm.ordinal());
        entityData.set(DATA_LIFE, Math.max(OPEN_TICKS + CLOSE_TICKS + 20, lifetime));
    }

    public HmRealm realm() {
        int i = entityData.get(DATA_REALM);
        return HmRealm.values()[Mth.clamp(i, 0, HmRealm.values().length - 1)];
    }

    public int age() {
        return entityData.get(DATA_AGE);
    }

    public int lifetime() {
        return entityData.get(DATA_LIFE);
    }

    /** 0 → 1 abrindo, 1 aberto, 1 → 0 fechando (para o desenho). */
    public float openness(float partial) {
        float a = age() + partial;
        float life = lifetime();
        if (a < OPEN_TICKS) return Mth.clamp(a / OPEN_TICKS, 0F, 1F);
        if (a > life - CLOSE_TICKS) return Mth.clamp((life - a) / CLOSE_TICKS, 0F, 1F);
        return 1F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_REALM, 0);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_LIFE, 600);
    }

    @Override
    public void tick() {
        super.tick();
        int age = age();
        if (level().isClientSide) {
            clientFx(age);
            return;
        }
        entityData.set(DATA_AGE, age + 1);
        ServerLevel sl = (ServerLevel) level();
        if (age == 0) {
            sl.playSound(null, getX(), getY() + 2, getZ(), realm() == HmRealm.HOLLOW ? SoundEvents.WITHER_SPAWN : SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT, realm() == HmRealm.HOLLOW ? 0.6F : 1.4F, realm() == HmRealm.HOLLOW ? 1.6F : 0.7F);
        }
        if (age % 40 == 20) {
            sl.playSound(null, getX(), getY() + 2, getZ(), realm() == HmRealm.HOLLOW ? SoundEvents.PORTAL_AMBIENT : SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.AMBIENT, 0.6F, realm() == HmRealm.HOLLOW ? 0.5F : 1.2F);
        }
        if (age >= lifetime()) {
            sl.sendParticles(realm() == HmRealm.HOLLOW ? ParticleTypes.SQUID_INK : ParticleTypes.CHERRY_LEAVES,
                    getX(), getY() + 2, getZ(), 30, 0.6, 1.2, 0.6, 0.02);
            discard();
            return;
        }
        if (age >= OPEN_TICKS && age < lifetime() - CLOSE_TICKS / 2) pullIn(sl);
    }

    /** Quem está na coluna do portal atravessa (uma vez a cada 3 s por jogador — volta pelo portal do outro lado). */
    private void pullIn(ServerLevel sl) {
        AABB zone = new AABB(getX() - 1.0, getY() - 0.5, getZ() - 1.0, getX() + 1.0, getY() + 3.5, getZ() + 1.0);
        long now = sl.getGameTime();
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, zone, pl -> pl.isAlive() && !pl.isSpectator() && !pl.isPassenger())) {
            Long last = crossed.get(p.getUUID());
            if (last != null && now - last < 60) continue;
            Long arrivedAt = p.getPersistentData().contains("isekai_mahou.hm_gate_at") ? p.getPersistentData().getLong("isekai_mahou.hm_gate_at") : null;
            if (arrivedAt != null && Math.abs(now - arrivedAt) < 60) continue;
            crossed.put(p.getUUID(), now);
            HmRealm realm = realm();
            sl.sendParticles(HmFx.dust(realm.color, 1.8F), p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
            // Muda de dimensão fora do laço de entidades (fim do tick do servidor).
            BossKit.schedule(sl, 1, () -> {
                if (!p.isAlive() || p.level() != sl) return;
                HmTravel.cross(p, realm);
                p.getPersistentData().putLong("isekai_mahou.hm_gate_at", p.serverLevel().getGameTime());
            });
        }
    }

    private void clientFx(int age) {
        Level level = level();
        float open = openness(0F);
        if (open <= 0.05F) return;
        if (realm() == HmRealm.HOLLOW) {
            if (age % 2 == 0) {
                double y = getY() + 0.3 + random.nextDouble() * 4.0 * open;
                level.addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 0.6, y, getZ() + (random.nextDouble() - 0.5) * 0.6,
                        0, -0.02, 0);
                level.addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 1.6, y, getZ() + (random.nextDouble() - 0.5) * 1.6,
                        0, 0.02, 0);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.ASH, getX() + (random.nextDouble() - 0.5) * 3, getY() + random.nextDouble() * 5,
                        getZ() + (random.nextDouble() - 0.5) * 3, 0, 0, 0);
            }
        } else {
            if (age % 2 == 0) {
                double a = (age * 0.15) % (Math.PI * 2);
                for (int k = 0; k < 2; k++) {
                    double ang = a + k * Math.PI;
                    level.addParticle(ParticleTypes.CHERRY_LEAVES, getX() + Math.cos(ang) * 1.4, getY() + 0.2 + random.nextDouble() * 2.5 * open,
                            getZ() + Math.sin(ang) * 1.4, 0, 0.02, 0);
                }
                level.addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.06, 0);
            }
        }
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
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_REALM, tag.getInt("Realm"));
        entityData.set(DATA_AGE, tag.getInt("Age"));
        entityData.set(DATA_LIFE, Math.max(60, tag.getInt("Life")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Realm", entityData.get(DATA_REALM));
        tag.putInt("Age", age());
        tag.putInt("Life", lifetime());
    }
}
