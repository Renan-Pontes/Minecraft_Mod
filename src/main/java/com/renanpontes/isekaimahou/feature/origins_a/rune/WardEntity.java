package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.UUID;

import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3f;

/**
 * Ward: área protegida (padrão 10 min, raio 6). Monstros hostis — ou qualquer mob mirando alguém protegido —
 * são empurrados para fora da barreira, e projéteis inimigos se desfazem ao cruzá-la.
 */
public class WardEntity extends OriginRuneEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(WardEntity.class, EntityDataSerializers.FLOAT);
    public static final int MAX_PER_OWNER = 2;

    public WardEntity(EntityType<? extends WardEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RADIUS, 6.0F);
    }

    public float radius() { return entityData.get(RADIUS); }

    public static WardEntity place(SpellContext ctx, BlockPos at, SpellStats stats) {
        ServerLevel level = ctx.level();
        UUID owner = ctx.casterId();
        Vec3 pos = Vec3.atBottomCenterOf(at).add(0, 0.02, 0);
        // Conjurar de novo sobre um Ward seu renova o tempo (formas de área chamariam várias vezes na mesma conjuração)
        for (WardEntity old : ownedBy(level, OaRegistries.WARD.get(), owner)) {
            if (old.position().distanceToSqr(pos) < 2.5 * 2.5) {
                old.renew(stats.duration(12000));
                old.entityData.set(RADIUS, Math.max(old.radius(), stats.radius(6.0F)));
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
                old.ringFx(level, 48);
                return old;
            }
        }
        enforceLimit(level, OaRegistries.WARD.get(), owner, MAX_PER_OWNER);
        WardEntity ward = new WardEntity(OaRegistries.WARD.get(), level);
        ward.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ward.setOwner(owner);
        ward.setColor(ctx.color());
        ward.entityData.set(RADIUS, stats.radius(6.0F));
        ward.setLifetime(stats.duration(12000));
        level.addFreshEntity(ward);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        ward.ringFx(level, 48);
        return ward;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (age % 2 == 0) repel(level);
        if (age % 30 == 0) ringFx(level, Math.max(24, (int) (radius() * 6)));
    }

    private void repel(ServerLevel level) {
        float r = radius();
        UUID owner = ownerId();
        Vec3 c = position();
        AABB box = new AABB(c, c).inflate(r + 1.5, 4.0, r + 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!(e instanceof Mob mob) || !isRepelled(level, owner, mob)) continue;
            Vec3 d = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double dist = d.length();
            if (dist > r + 0.6) continue;
            Vec3 dir = dist < 0.05 ? new Vec3(1, 0, 0) : d.scale(1.0 / dist);
            double strength = dist < r - 1.0 ? 0.9 : 0.55;
            e.setDeltaMovement(dir.x * strength, 0.25, dir.z * strength);
            e.hurtMarked = true;
            if (mob.getTarget() != null && OaUtil.isAlly(level, owner, mob.getTarget())
                    && mob.getTarget().distanceToSqr(c) < r * r) {
                mob.getNavigation().stop();
            }
            if (age % 10 == 0) {
                Vec3 hit = c.add(dir.scale(r)).add(0, e.getBbHeight() * 0.5, 0);
                level.sendParticles(dust(color(), 1.4F), hit.x, e.getY() + e.getBbHeight() * 0.5, hit.z, 8, 0.1, 0.4, 0.1, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 4, 0.1, 0.3, 0.1, 0.05);
                level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        }
        // Projéteis de inimigos se desfazem na barreira
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, box)) {
            Entity shooter = p.getOwner();
            if (shooter == null || !(shooter instanceof Mob m) || !isRepelled(level, owner, m)) continue;
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) continue;
            level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
            level.sendParticles(dust(color(), 1.0F), p.getX(), p.getY(), p.getZ(), 6, 0.15, 0.15, 0.15, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 1.5F);
            p.discard();
        }
    }

    /** Monstros (Enemy) que não são do dono, ou mobs mirando alguém protegido. Chefes atravessam a barreira. */
    private static boolean isRepelled(ServerLevel level, UUID owner, Mob mob) {
        if (!mob.isAlive() || mob.getType().is(Tags.EntityTypes.BOSSES) || OaUtil.isAlly(level, owner, mob)) return false;
        if (mob instanceof Enemy) return true;
        LivingEntity t = mob.getTarget();
        return t != null && OaUtil.isAlly(level, owner, t);
    }

    private void ringFx(ServerLevel level, int points) {
        float r = radius();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points + age * 0.01;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            level.sendParticles(dust(color(), 1.1F), x, getY() + 0.15, z, 1, 0.02, 0.05, 0.02, 0);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, x, getY() + 0.3, z, 1, 0, 0.1, 0, 0.01);
        }
    }

    @Override
    protected void onExpire(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.1F);
        ringFx(level, 40);
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    // Culling: a barreira é bem maior que a caixa da entidade
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = radius();
        return getBoundingBox().inflate(r, 3.0, r);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(3) != 0) return;
        float r = radius();
        double a = random.nextDouble() * Math.PI * 2;
        level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + 0.1 + random.nextDouble() * 2.5,
                getZ() + Math.sin(a) * r, 0, 0.02, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Radius", radius());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Radius")) entityData.set(RADIUS, tag.getFloat("Radius"));
    }
}
