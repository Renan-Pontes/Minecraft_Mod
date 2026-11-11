package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cortina de Ventos (Zéfiro): paredes de vento que saem do centro como raios de uma roda e GIRAM devagar.
 * Encostar numa parede empurra na direção do giro e fere; o jeito é andar junto com a brecha entre duas paredes.
 * Desenhada no cliente como faixas translúcidas de vento (CurtainRenderer). O ângulo vem do tempo do mundo, então
 * servidor e cliente veem as paredes no mesmo lugar. Não é salva.
 */
public class ZephyrCurtain extends Entity {
    private static final EntityDataAccessor<Integer> DATA_WALLS = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_START = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_INNER = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OUTER = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(ZephyrCurtain.class, EntityDataSerializers.LONG);
    /** Altura das paredes (acima do chão do centro). */
    public static final float HEIGHT = 3.6F;
    /** Meia espessura da parede para o acerto (o desenho tem a mesma largura). */
    public static final float HALF_WIDTH = 0.9F;
    private static final int HIT_COOLDOWN = 12;

    @Nullable private UUID ownerId;
    private float damage = 4F;
    private final Map<UUID, Long> lastHit = new HashMap<>();

    public ZephyrCurtain(EntityType<? extends ZephyrCurtain> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    /**
     * Cria a cortina centrada no chão {@code center}.
     * @param startDeg ângulo (padrão do Minecraft) da 1ª parede no início
     * @param speedDeg graus por tick (sinal = sentido do giro)
     */
    public static ZephyrCurtain spawn(LivingEntity owner, Vec3 center, int walls, float startDeg, float speedDeg, float inner,
                                      float outer, int life, float damage) {
        ZephyrCurtain c = new ZephyrCurtain(BossesARegistry.ZEPHYR_CURTAIN.get(), owner.level());
        c.ownerId = owner.getUUID();
        c.damage = damage;
        c.entityData.set(DATA_WALLS, Math.max(1, walls));
        c.entityData.set(DATA_START, startDeg);
        c.entityData.set(DATA_SPEED, speedDeg);
        c.entityData.set(DATA_INNER, inner);
        c.entityData.set(DATA_OUTER, outer);
        c.entityData.set(DATA_LIFE, life);
        c.entityData.set(DATA_BORN, owner.level().getGameTime());
        c.setPos(center.x, center.y, center.z);
        owner.level().addFreshEntity(c);
        return c;
    }

    public int walls() { return entityData.get(DATA_WALLS); }

    public float inner() { return entityData.get(DATA_INNER); }

    public float outer() { return entityData.get(DATA_OUTER); }

    public int life() { return entityData.get(DATA_LIFE); }

    /** Idade em ticks pelo relógio do mundo (igual no servidor e no cliente). */
    public float age(float partialTick) {
        return Math.max(0F, level().getGameTime() - entityData.get(DATA_BORN) + partialTick);
    }

    /** Ângulo (graus, padrão do Minecraft) da parede {@code k} na idade dada. */
    public float wallYaw(int k, float age) {
        return entityData.get(DATA_START) + entityData.get(DATA_SPEED) * age + k * 360F / walls();
    }

    public float speed() { return entityData.get(DATA_SPEED); }

    /** Opacidade: surge em 6 ticks e some nos últimos 10. */
    public float fade(float age) {
        return Mth.clamp(age / 6F, 0F, 1F) * Mth.clamp((life() - age) / 10F, 0F, 1F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WALLS, 3);
        builder.define(DATA_START, 0F);
        builder.define(DATA_SPEED, 2F);
        builder.define(DATA_INNER, 2.5F);
        builder.define(DATA_OUTER, 15F);
        builder.define(DATA_LIFE, 90);
        builder.define(DATA_BORN, 0L);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        Entity ownerEntity = ownerId != null ? sl.getEntity(ownerId) : null;
        float age = age(0);
        if (!(ownerEntity instanceof LivingEntity owner) || !owner.isAlive() || age >= life()) {
            discard();
            return;
        }
        if (tickCount % 20 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (tickCount % 3 == 0) particles(sl, age);
        // Paredes ainda se formando ou já sumindo não ferem
        if (age < 4 || age > life() - 6) return;
        hits(sl, owner, age);
    }

    private void particles(ServerLevel sl, float age) {
        float in = inner(), out = outer();
        float sign = Math.signum(speed());
        for (int k = 0; k < walls(); k++) {
            float yaw = wallYaw(k, age);
            Vec3 dir = BossSpells.dirOf(yaw);
            Vec3 tangent = new Vec3(-dir.z, 0, dir.x).scale(sign);
            for (int i = 0; i < 3; i++) {
                double r = in + random.nextDouble() * (out - in);
                Vec3 p = position().add(dir.scale(r)).add(0, random.nextDouble() * HEIGHT, 0);
                sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, tangent.x, 0.05, tangent.z, 0.25);
            }
            Vec3 tip = position().add(dir.scale(out)).add(0, random.nextDouble() * HEIGHT, 0);
            sl.sendParticles(ElementFx.dust(Element.WIND, 1.4F), tip.x, tip.y, tip.z, 2, 0.2, 0.4, 0.2, 0);
        }
    }

    private void hits(ServerLevel sl, LivingEntity owner, float age) {
        float in = inner(), out = outer();
        long now = sl.getGameTime();
        AABB box = new AABB(getX() - out - 1, getY() - 1, getZ() - out - 1, getX() + out + 1, getY() + HEIGHT, getZ() + out + 1);
        float sign = Math.signum(speed());
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> BossSpells.isFoe(owner, e))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < in - 0.5 || d > out + 0.5) continue;
            Long last = lastHit.get(e.getUUID());
            if (last != null && now - last < HIT_COOLDOWN) continue;
            float eYaw = BossSpells.yawOf(new Vec3(dx, 0, dz));
            for (int k = 0; k < walls(); k++) {
                float wYaw = wallYaw(k, age);
                double delta = Math.abs(Mth.wrapDegrees(eYaw - wYaw));
                if (delta >= 90) continue;
                double lateral = d * Math.sin(Math.toRadians(delta));
                if (lateral > HALF_WIDTH + e.getBbWidth() * 0.5) continue;
                lastHit.put(e.getUUID(), now);
                BossSpells.hit(owner, e, Element.WIND, damage);
                // Empurra no sentido do giro (e um pouco para fora): a parede "varre" quem encosta
                Vec3 wall = BossSpells.dirOf(wYaw);
                Vec3 push = new Vec3(-wall.z, 0, wall.x).scale(sign).add(wall.scale(0.35));
                BossSpells.knock(e, push, 1.1, 0.35);
                sl.sendParticles(ParticleTypes.GUST, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 0.8F, 1.3F);
                break;
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 112 * 112;
    }
}
