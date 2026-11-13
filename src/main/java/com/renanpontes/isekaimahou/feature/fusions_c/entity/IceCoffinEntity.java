package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Caixão de Gelo: um cristal hexagonal de gelo nasce em volta do alvo e o prende (Congelado + preso no lugar).
 * Quando o tempo acaba — ou o caixão é destruído pela morte do alvo — ele ESTILHAÇA: dano alto no preso (+50% se ele
 * estava Molhado quando foi preso) e estilhaços que ferem e gelam quem estiver perto.
 */
public class IceCoffinEntity extends FcSpellEntity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(IceCoffinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(IceCoffinEntity.class, EntityDataSerializers.FLOAT);

    private float shatterDamage = 12F;
    private boolean wasWet;
    @Nullable
    private Vec3 holdAt;

    public IceCoffinEntity(EntityType<? extends IceCoffinEntity> type, Level level) {
        super(type, level);
    }

    /** Teto de caixões vivos por conjurador (Zona/Aura que repetem não prendem o mapa inteiro). */
    private static final int MAX_PER_OWNER = 12;

    public static void encase(SpellContext ctx, SpellStats stats, LivingEntity target, float shatterDamage) {
        ServerLevel sl = ctx.level();
        if (FcUtil.owned(sl, FcRegistries.ICE_COFFIN.get(), ctx.casterId()) >= MAX_PER_OWNER) return;
        // Um caixão por alvo: o novo renova o antigo
        for (IceCoffinEntity old : sl.getEntities(FcRegistries.ICE_COFFIN.get(), c -> c.isAlive() && c.targetId() == target.getId())) {
            old.discard();
        }
        boolean boss = Statuses.isBossLike(target);
        int ticks = boss ? 30 : stats.duration(60);
        IceCoffinEntity c = new IceCoffinEntity(FcRegistries.ICE_COFFIN.get(), sl);
        c.setPos(target.getX(), target.getY(), target.getZ());
        c.setup(ctx, stats, Element.ICE, ticks, Math.max(0.6F, target.getBbWidth() * 0.5F + 0.35F));
        c.entityData.set(TARGET, target.getId());
        c.entityData.set(HEIGHT, target.getBbHeight() + 0.5F);
        c.shatterDamage = shatterDamage;
        c.wasWet = Statuses.has(target, Statuses.Kind.WET);
        c.holdAt = target.position();
        sl.addFreshEntity(c);
        Statuses.apply(target, Statuses.Kind.FROZEN, 1, ticks, ctx.caster(), ctx.powerMult());
        Statuses.clear(target, Statuses.Kind.BURNING);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 30,
                target.getBbWidth() * 0.6, target.getBbHeight() * 0.5, target.getBbWidth() * 0.6, 0.1);
        FcFx.sound(sl, target.position(), SoundEvents.GLASS_PLACE, 1.4F, 0.6F);
        FcFx.sound(sl, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
        builder.define(HEIGHT, 2F);
    }

    public int targetId() { return entityData.get(TARGET); }

    public float coffinHeight() { return entityData.get(HEIGHT); }

    @Nullable
    private LivingEntity target() {
        Entity e = level().getEntity(targetId());
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        LivingEntity t = target();
        if (t == null) {
            end();
            return;
        }
        // Preso dentro do cristal: não anda nem é empurrado (chefes ficam só o tempo curto do caixão)
        if (holdAt != null && !FcUtil.untouchable(t)) {
            if (t.position().distanceToSqr(holdAt) > 0.04) t.teleportTo(holdAt.x, holdAt.y, holdAt.z);
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
            FcUtil.stopMob(t);
        }
        setPos(t.getX(), t.getY(), t.getZ());
        if (age() % 10 == 0) {
            sl.sendParticles(Vfx.snowflake(), getX(), getY() + coffinHeight() * 0.5, getZ(), 3, scale(), coffinHeight() * 0.4, scale(), 0.01);
        }
        if (lifeLeft() == 10) FcFx.sound(sl, position(), SoundEvents.GLASS_HIT, 1.2F, 0.5F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position().add(0, coffinHeight() * 0.5, 0);
        LivingEntity t = target();
        if (t != null) {
            Statuses.clear(t, Statuses.Kind.FROZEN);
            hit(t, wasWet ? shatterDamage * 1.5F : shatterDamage);
            if (t.isAlive()) Statuses.apply(t, Statuses.Kind.CHILLED, 2, 100, owner(), power);
        }
        // Estilhaços: ferem e gelam quem está perto do caixão
        for (LivingEntity le : enemiesIn(c, 3.5)) {
            if (le == t) continue;
            hit(le, 4F);
            Statuses.apply(le, Statuses.Kind.CHILLED, 2, 100, owner(), power);
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                c.x, c.y, c.z, 60, scale(), coffinHeight() * 0.4, scale(), 0.35);
        sl.sendParticles(ParticleTypes.ITEM_SNOWBALL, c.x, c.y, c.z, 30, scale(), coffinHeight() * 0.4, scale(), 0.4);
        FcFx.sphere(sl, c, FcFx.dust(FcFx.FROST, 1.2F), 1.5, 30, 0.3);
        FcFx.sound(sl, c, SoundEvents.GLASS_BREAK, 2F, 0.6F);
        FcFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4F, 0.6F);
    }
}
