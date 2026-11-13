package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Eclipse: um disco escuro com coroa dourada cobre o céu acima da área. Inimigos dentro ficam no escuro
 * (Escuridão para jogadores, perda de visão para mobs), Fracos, Lentos e Expostos (recebem mais dano mágico).
 * Um eclipse por conjurador (o novo substitui o antigo).
 */
public class EclipseFieldEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(EclipseFieldEntity.class, EntityDataSerializers.FLOAT);
    public static final float SKY_HEIGHT = 7.0F;

    private int amp;

    public EclipseFieldEntity(EntityType<? extends EclipseFieldEntity> type, Level level) {
        super(type, level);
    }

    public static void cast(SpellContext ctx, Vec3 at, float radius, int ticks, int amp) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            for (EclipseFieldEntity e : sl.getEntities(FbRegistries.ECLIPSE_FIELD.get(), e -> e.isAlive() && owner.equals(e.ownerId()))) {
                e.end();
            }
        }
        EclipseFieldEntity f = new EclipseFieldEntity(FbRegistries.ECLIPSE_FIELD.get(), sl);
        f.setPos(at.x, at.y, at.z);
        f.setup(ctx, ticks);
        f.amp = amp;
        f.entityData.set(RADIUS, radius);
        sl.addFreshEntity(f);
        FbFx.ring(sl, at.add(0, 0.1, 0), FbFx.fade(FbFx.TWILIGHT_GOLD, FbFx.TWILIGHT_SHADOW, 1.5F), radius, 40, 0);
        sl.sendParticles(Vfx.endRod(), at.x, at.y + SKY_HEIGHT, at.z, 30, 1.2, 0.1, 1.2, 0.05);
        FbFx.sound(sl, at, SoundEvents.BEACON_DEACTIVATE, 1.6F, 0.5F);
        FbFx.sound(sl, at, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RADIUS, 6F);
    }

    public float radius() { return entityData.get(RADIUS); }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (tickCount % 20 != 1) return;
        float r = radius();
        Vec3 c = position();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 6, r),
                e -> e.isAlive() && !ally(e) && !FbUtil.untouchable(e))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) continue;
            if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, true), owner());
            else e.addEffect(new MobEffectInstance(FbRegistries.INKED, 30, 0, false, false, true), owner());
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, Math.min(2, amp + 1), false, true, true), owner());
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true, true), owner());
            e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 30, Math.min(1, amp), false, true, true), owner());
        }
        FbFx.ring(sl, c.add(0, 0.1, 0), FbFx.fade(FbFx.TWILIGHT_GOLD, FbFx.TWILIGHT_SHADOW, 1.2F), r, 28, 0);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 sky = position().add(0, SKY_HEIGHT, 0);
        sl.sendParticles(Vfx.endRod(), sky.x, sky.y, sky.z, 40, 1.5, 0.2, 1.5, 0.08);
        FbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
    }

    @Override
    protected void clientTick() {
        float r = radius();
        float open = openness(0, 20);
        if (open <= 0) return;
        // Cinzas escuras caindo do disco e brilho dourado da coroa
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            level().addParticle(FbFx.dust(FbFx.TWILIGHT_SHADOW, 1.2F), getX() + Math.cos(a) * d,
                    getY() + 1 + random.nextDouble() * (SKY_HEIGHT - 1.5), getZ() + Math.sin(a) * d, 0, -0.03, 0);
        }
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(Vfx.endRod(), getX() + Math.cos(a) * 2.4, getY() + SKY_HEIGHT, getZ() + Math.sin(a) * 2.4,
                    Math.cos(a) * 0.03, -0.01, Math.sin(a) * 0.03);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    /** O disco fica no alto: a caixa de visibilidade precisa incluir o céu acima. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.5, 0, 3.5).expandTowards(0, SKY_HEIGHT + 1.5, 0);
    }
}
