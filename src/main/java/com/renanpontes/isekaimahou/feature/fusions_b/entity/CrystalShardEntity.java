package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Fragmento de cristal (Estilhaços): voa reto e rápido, fere o primeiro inimigo e se parte. */
public class CrystalShardEntity extends ThrowableItemProjectile {
    @Nullable
    private SpellContext ctx;
    /** Dano já multiplicado por stats.power() do efeito (o contexto aplica o resto do poder). */
    private float damage = 2.5F;
    private SpellStats hitStats = FbUtil.PLAIN;
    /** Alvo que gerou os estilhaços (os fragmentos nascem dentro dele e não devem acertá-lo de novo). */
    @Nullable
    private UUID excluded;
    private int age;

    public CrystalShardEntity(EntityType<? extends CrystalShardEntity> type, Level level) {
        super(type, level);
    }

    public static CrystalShardEntity create(SpellContext ctx, Vec3 from, Vec3 dir, float speed, float damage, @Nullable Entity excluded,
                                            SpellStats stats) {
        CrystalShardEntity s = new CrystalShardEntity(FbRegistries.CRYSTAL_SHARD.get(), ctx.level());
        s.ctx = ctx;
        s.damage = damage;
        s.hitStats = FbUtil.hitStats(stats);
        s.excluded = excluded != null ? excluded.getUUID() : null;
        if (ctx.caster() != null) s.setOwner(ctx.caster());
        s.setPos(from.x, from.y, from.z);
        s.shoot(dir.x, dir.y, dir.z, speed, 1.5F);
        return s;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.012;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || !(target instanceof LivingEntity)) return false;
        if (excluded != null && excluded.equals(target.getUUID())) return false;
        if (ctx != null) return !ctx.isAlly(target);
        return target != getOwner();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                level().addParticle(FbFx.dust(FbFx.CRYSTAL_LIGHT, 0.6F), getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        if (++age > 28) {
            shatter((ServerLevel) level(), position());
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel sl) || !(hit.getEntity() instanceof LivingEntity le)) return;
        le.invulnerableTime = 0;
        if (ctx != null) ctx.damage(le, damage, hitStats, this);
        else le.hurt(damageSources().thrown(this, getOwner()), damage);
        le.knockback(0.25, -getDeltaMovement().x, -getDeltaMovement().z);
        shatter(sl, hit.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel sl) shatter(sl, hit.getLocation());
    }

    private void shatter(ServerLevel sl, Vec3 at) {
        if (isRemoved()) return;
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.AMETHYST_SHARD)), at.x, at.y, at.z,
                6, 0.1, 0.1, 0.1, 0.08);
        sl.sendParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.05);
        FbFx.sound(sl, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.4F, 1.5F);
        discard();
    }
}
