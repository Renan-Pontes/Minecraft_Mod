package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Meteoro da Chuva de Meteoros do Ignis. Cai em linha reta do céu e chega ao chão EXATAMENTE quando o círculo de
 * telegrafia (desenhado pelo Ignis com o mesmo tempo) termina de encher: quem saiu do círculo não leva dano.
 * Explode sem quebrar blocos e às vezes deixa uma poça de lava temporária.
 */
public class IgnisMeteor extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_BLUE = SynchedEntityData.defineId(IgnisMeteor.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(IgnisMeteor.class, EntityDataSerializers.FLOAT);

    @Nullable private UUID ownerId;
    private Vec3 impact = Vec3.ZERO;
    private int fallTicks = 30;
    private float damage = 12F;
    private float radius = 2.6F;
    private float poolChance = 0.35F;
    /** Círculo de aviso deste meteoro: se ele for cancelado (troca de fase), o meteoro se desfaz no ar. */
    private int telegraphId = -1;

    public IgnisMeteor(EntityType<? extends IgnisMeteor> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Raio de explosão de um meteoro de tamanho {@code size} (use o mesmo raio na telegrafia). */
    public static float radiusFor(float size) {
        return 1.7F + size;
    }

    /**
     * Lança um meteoro de {@code from} até {@code impact} em {@code fallTicks} ticks.
     * @param size        0.8..1.4 (visual e raio, ver {@link #radiusFor})
     * @param telegraphId id do círculo de aviso desenhado no impacto (-1 = nenhum)
     */
    public static IgnisMeteor launch(LivingEntity owner, Vec3 from, Vec3 impact, int fallTicks, float size, float damage,
                                     float poolChance, boolean blue, int telegraphId) {
        IgnisMeteor m = new IgnisMeteor(BossesARegistry.METEOR.get(), owner.level());
        m.ownerId = owner.getUUID();
        m.telegraphId = telegraphId;
        m.impact = impact;
        m.fallTicks = Math.max(1, fallTicks);
        m.damage = damage;
        m.radius = radiusFor(size);
        m.poolChance = poolChance;
        m.entityData.set(DATA_BLUE, blue);
        m.entityData.set(DATA_SIZE, size);
        m.setPos(from.x, from.y, from.z);
        m.setDeltaMovement(impact.subtract(from).scale(1.0 / m.fallTicks));
        owner.level().addFreshEntity(m);
        return m;
    }

    public boolean isBlue() { return entityData.get(DATA_BLUE); }

    public float size() { return entityData.get(DATA_SIZE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLUE, false);
        builder.define(DATA_SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        // Movimento reto e constante dos dois lados (o cliente não depende de pacotes para ficar suave)
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                double f = random.nextDouble();
                double x = getX() - v.x * f + (random.nextDouble() - 0.5) * 0.6;
                double y = getY() + 0.5 - v.y * f + (random.nextDouble() - 0.5) * 0.6;
                double z = getZ() - v.z * f + (random.nextDouble() - 0.5) * 0.6;
                level().addParticle(isBlue() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
            }
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.05, 0);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 0, 0, 0);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity owner = ownerId != null ? sl.getEntity(ownerId) : null;
        // Some junto com o Ignis (a telegrafia dele também some)
        if (!(owner instanceof LivingEntity caster) || !caster.isAlive() || tickCount > 200) {
            discard();
            return;
        }
        if (telegraphId >= 0 && !Telegraph.isActive(telegraphId)) {
            // O aviso sumiu (golpe interrompido): nada de dano sem aviso — o meteoro se desfaz em fumaça
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 12, 0.4, 0.4, 0.4, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.8F);
            discard();
            return;
        }
        if (tickCount >= fallTicks) {
            explode(sl, caster);
            discard();
            return;
        }
        if (tickCount % 10 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 0.5F);
    }

    private void explode(ServerLevel sl, LivingEntity caster) {
        Vec3 p = impact;
        boolean blue = isBlue();
        sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 2, 0.6, 0.3, 0.6, 0);
        sl.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, p.x, p.y + 0.4, p.z, 40, radius * 0.35, 0.4,
                radius * 0.35, 0.12);
        sl.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, 10, 0.8, 0.2, 0.8, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.6, p.z, 16, 0.9, 0.4, 0.9, 0.03);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 0.8F + random.nextFloat() * 0.2F);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2F, 0.6F);
        // A área de acerto é exatamente o círculo desenhado
        Telegraph.Shape zone = Telegraph.Shape.circle(p, radius);
        for (LivingEntity e : Telegraph.entitiesIn(sl, zone)) {
            if (!BossSpells.isFoe(caster, e)) continue;
            double d = Math.sqrt(e.distanceToSqr(p.x, e.getY(), p.z));
            float dmg = (float) (damage * Math.max(0.6, 1.0 - d / (radius + 1.5)));
            BossSpells.hit(caster, e, Element.FIRE, dmg);
            e.igniteForSeconds(5);
            BossSpells.knock(e, e.position().subtract(p), 0.6, 0.35);
        }
        if (random.nextFloat() < poolChance) {
            IgnisLavaPool.spawn(sl, caster, p, 1.6F + random.nextFloat() * 0.8F, 160, blue);
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
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
