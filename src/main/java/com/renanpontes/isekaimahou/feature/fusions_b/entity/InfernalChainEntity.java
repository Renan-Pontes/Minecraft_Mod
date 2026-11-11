package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Corrente Infernal: três correntes saem de um selo em brasa no chão e prendem o alvo no centro.
 * Enquanto presas, as vítimas queimam (fogo + dano de chama infernal a cada segundo) e são puxadas de volta
 * se tentarem sair. O renderer desenha correntes de verdade (modelo do bloco de corrente) entre o chão e o alvo.
 */
public class InfernalChainEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(InfernalChainEntity.class, EntityDataSerializers.INT);
    public static final int CHAINS = 3;

    private float damage = 1.5F;

    public InfernalChainEntity(EntityType<? extends InfernalChainEntity> type, Level level) {
        super(type, level);
    }

    public static void bind(SpellContext ctx, LivingEntity target, int ticks, float damage, SpellStats stats) {
        ServerLevel sl = ctx.level();
        for (InfernalChainEntity c : sl.getEntities(FbRegistries.INFERNAL_CHAIN.get(), c -> c.isAlive() && c.targetId() == target.getId())) {
            c.extendLife(ticks);
            c.ctx = ctx;
            c.setHitStats(stats);
            c.damage = Math.max(c.damage, damage);
            target.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, c.life, 0), ctx.caster());
            return;
        }
        InfernalChainEntity c = new InfernalChainEntity(FbRegistries.INFERNAL_CHAIN.get(), sl);
        Vec3 at = FbUtil.ground(sl, target.position(), 4);
        c.setPos(at.x, at.y, at.z);
        c.setYRot(sl.random.nextFloat() * 360F);
        c.setup(ctx, ticks);
        c.setHitStats(stats);
        c.damage = damage;
        c.entityData.set(TARGET, target.getId());
        sl.addFreshEntity(c);
        target.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, ticks, 0), ctx.caster());
        FbFx.ring(sl, at.add(0, 0.1, 0), FbFx.dust(FbFx.HELLFIRE, 1.4F), 1.7, 28, 0);
        FbFx.ring(sl, at.add(0, 0.15, 0), ParticleTypes.FLAME, 1.7, 14, 0.02);
        sl.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 6, 0.8, 0.1, 0.8, 0);
        FbFx.sound(sl, at, SoundEvents.CHAIN_PLACE, 1.4F, 0.6F);
        FbFx.sound(sl, at, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
    }

    public int targetId() { return entityData.get(TARGET); }

    @Nullable
    public Entity target() { return level().getEntity(targetId()); }

    /** Ponto de onde sai a corrente {@code i} (no chão, em volta do selo). */
    public Vec3 anchor(int i) {
        double a = Math.toRadians(getYRot()) + i * Math.PI * 2 / CHAINS;
        return position().add(Math.cos(a) * 1.7, 0.05, Math.sin(a) * 1.7);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        Entity t = target();
        if (!(t instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(this) > 8 * 8) {
            end();
            return;
        }
        // Puxa de volta para o centro do selo
        Vec3 off = new Vec3(getX() - le.getX(), 0, getZ() - le.getZ());
        if (off.lengthSqr() > 0.6 * 0.6) {
            Vec3 pull = off.normalize().scale(Math.min(0.5, off.length() * 0.35));
            if (le instanceof ServerPlayer) FbUtil.setMotion(le, new Vec3(pull.x, Math.min(0, le.getDeltaMovement().y), pull.z));
            else le.setDeltaMovement(pull.x, Math.min(0, le.getDeltaMovement().y), pull.z);
        }
        if (tickCount % 20 == 0) {
            hit(le, damage);
            le.igniteForSeconds(2F);
            FbFx.around(sl, le, ParticleTypes.FLAME, 8, 0.03);
            FbFx.around(sl, le, ParticleTypes.SOUL_FIRE_FLAME, 3, 0.02);
        }
        if (tickCount % 6 == 0) {
            Vec3 body = le.position().add(0, le.getBbHeight() * 0.55, 0);
            for (int i = 0; i < CHAINS; i++) {
                Vec3 a = anchor(i);
                Vec3 p = a.add(body.subtract(a).scale(sl.random.nextDouble()));
                sl.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
            sl.sendParticles(FbFx.dust(FbFx.HELLFIRE, 1.0F), getX(), getY() + 0.1, getZ(), 3, 1.0, 0.02, 1.0, 0);
        }
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Entity t = target();
        if (t instanceof LivingEntity le) {
            le.removeEffect(CoreRegistries.ROOTED);
            Vec3 body = le.position().add(0, le.getBbHeight() * 0.55, 0);
            for (int i = 0; i < CHAINS; i++) {
                Vec3 a = anchor(i);
                FbFx.line(sl, a, body, ParticleTypes.SMOKE, 2.0);
            }
        }
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 10, 0.8, 0.2, 0.8, 0.02);
        FbFx.sound(sl, position(), SoundEvents.CHAIN_BREAK, 1.3F, 0.7F);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(4) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 1.7;
            level().addParticle(ParticleTypes.SMOKE, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.03, 0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 64 * 64; }

    /** As correntes vão do chão até o alvo: caixa de visibilidade maior que a entidade. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.2, 0, 2.2).expandTowards(0, 3, 0);
    }
}
