package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Espinho da cauda da Manticora: voa reto (sem gravidade) pela linha marcada no chão, envenena e faz sangrar.
 * Não pode ser recolhido, some ao bater num bloco (1 s depois) e some no fim da linha marcada (nunca acerta quem está
 * além do alcance telegrafado; no máx. 4 s).
 */
public class ManticoreSpike extends AbstractArrow {
    private static final int MAX_LIFE = 80;
    private float spikeDamage = 7F;
    private int lifeTicks = MAX_LIFE;

    public ManticoreSpike(EntityType<? extends ManticoreSpike> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    /** Dispara um espinho de {@code from} na direção {@code dir}; some depois de voar ~{@code range} blocos. */
    public static void shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float speed, float damage, double range) {
        ManticoreSpike s = new ManticoreSpike(BgRegistries.MANTICORE_SPIKE.get(), level);
        s.setOwner(owner);
        s.setPos(from.x, from.y, from.z);
        s.setNoGravity(true);
        s.spikeDamage = damage;
        s.lifeTicks = Math.max(4, Math.min(MAX_LIFE, (int) Math.ceil(range / Math.max(0.1F, speed)) + 2));
        s.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
        level.addFreshEntity(s);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!inGround && tickCount % 2 == 0) level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
            return;
        }
        if (tickCount > lifeTicks || (inGround && inGroundTime > 20)) discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !BgAllies.isAlly(getOwner(), target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        Entity owner = getOwner();
        if (e instanceof LivingEntity le && !level().isClientSide) {
            if (le.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity lo ? lo : null), spikeDamage)) {
                le.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), owner);
                Statuses.apply(le, Statuses.Kind.BLEEDING, 1, 80, owner instanceof LivingEntity lo ? lo : null, 1F);
            }
            playSound(SoundEvents.ARROW_HIT_PLAYER, 0.8F, 1.4F);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        playSound(SoundEvents.ARROW_HIT, 0.8F, 1.6F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("SpikeDamage", spikeDamage);
        tag.putInt("LifeTicks", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spikeDamage = tag.contains("SpikeDamage") ? tag.getFloat("SpikeDamage") : 7F;
        lifeTicks = tag.contains("LifeTicks") ? Math.min(MAX_LIFE, tag.getInt("LifeTicks")) : MAX_LIFE;
        this.pickup = Pickup.DISALLOWED;
    }
}
