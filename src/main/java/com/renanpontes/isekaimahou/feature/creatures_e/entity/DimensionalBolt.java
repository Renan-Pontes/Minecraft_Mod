package com.renanpontes.isekaimahou.feature.creatures_e.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.CeRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Projetil magico das criaturas das dimensoes. O {@link BoltKind} define visual e efeito. */
public class DimensionalBolt extends ThrowableProjectile {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(DimensionalBolt.class, EntityDataSerializers.BYTE);
    private static final int MAX_AGE = 100;

    private float damage = 6.0F;
    private int age;
    private int homingId = -1;

    public DimensionalBolt(EntityType<? extends DimensionalBolt> type, Level level) {
        super(type, level);
    }

    /**
     * Dispara de {@code from} em direcao a {@code to}. Projeteis com gravidade compensam a queda
     * (mira um pouco acima para cair no alvo).
     */
    public static DimensionalBolt fire(DimensionalCreature owner, BoltKind kind, Vec3 from, Vec3 to, float speed, float damage, float inaccuracy) {
        DimensionalBolt b = new DimensionalBolt(CeRegistries.DIMENSIONAL_BOLT.get(), owner.level());
        b.setOwner(owner);
        b.entityData.set(DATA_KIND, (byte) kind.ordinal());
        b.damage = damage;
        b.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        // 0.93: compensa o arrasto do ar (0.99 por tick) que encurta o arco
        if (kind.gravity > 0) d = ballistic(d, speed * 0.93, kind.gravity);
        b.shoot(d.x, d.y, d.z, speed, inaccuracy);
        owner.level().addFreshEntity(b);
        return b;
    }

    /**
     * Direcao de tiro em arco baixo que acerta {@code d} com velocidade {@code v} e gravidade {@code g}
     * (por tick). Fora do alcance, atira a 45 graus (o mais longe possivel).
     */
    private static Vec3 ballistic(Vec3 d, double v, double g) {
        double x = d.horizontalDistance();
        if (x < 1.0E-3) return d;
        double v2 = v * v;
        double disc = v2 * v2 - g * (g * x * x + 2 * d.y * v2);
        double tan = disc < 0 ? 1.0 : (v2 - Math.sqrt(disc)) / (g * x);
        return new Vec3(d.x, tan * x, d.z);
    }

    /** Faz o projetil curvar levemente na direcao do alvo. */
    public DimensionalBolt homing(Entity target) {
        this.homingId = target == null ? -1 : target.getId();
        return this;
    }

    public BoltKind kind() {
        return BoltKind.byId(entityData.get(DATA_KIND));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, (byte) 0);
    }

    @Override
    protected double getDefaultGravity() {
        return kind().gravity;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail();
            return;
        }
        if (++age > MAX_AGE) {
            discard();
            return;
        }
        if (homingId >= 0 && age > 4 && level().getEntity(homingId) instanceof LivingEntity t && t.isAlive()) {
            Vec3 v = getDeltaMovement();
            Vec3 want = DimensionalCreature.center(t).subtract(position()).normalize().scale(v.length());
            setDeltaMovement(v.scale(0.88).add(want.scale(0.12)));
        }
    }

    /** Rastro de particulas (cliente). */
    private void trail() {
        Vec3 p = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 v = getDeltaMovement();
        var r = level().random;
        switch (kind()) {
            case MAGMA -> {
                level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0, 0);
                if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
                if (r.nextInt(4) == 0) level().addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
            }
            case PHOENIX -> {
                level().addParticle(ParticleTypes.FLAME, p.x - v.x * 0.5, p.y - v.y * 0.5, p.z - v.z * 0.5, 0, 0, 0);
                level().addParticle(CeFx.dust(0xFFD23F, 0.9F), p.x, p.y, p.z, 0, 0, 0);
            }
            case WATER -> {
                level().addParticle(ParticleTypes.SPLASH, p.x, p.y, p.z, 0, 0, 0);
                level().addParticle(ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, 0, 0, 0);
                if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 0, 0, 0);
            }
            case BOULDER -> {
                if (r.nextInt(2) == 0) level().addParticle(CeFx.block(Blocks.MOSSY_COBBLESTONE.defaultBlockState()), p.x, p.y, p.z, 0, 0, 0);
            }
            case GUST -> {
                level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, 0, 0);
                if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 0, 0, 0);
            }
            case LIGHT_SPEAR -> {
                level().addParticle(ParticleTypes.END_ROD, p.x - v.x * 0.5, p.y - v.y * 0.5, p.z - v.z * 0.5, 0, 0, 0);
                level().addParticle(CeFx.dust(0xFFE27A, 1.0F), p.x, p.y, p.z, 0, 0, 0);
            }
            case CRYSTAL_SHARD -> {
                if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 0, 0, 0);
                level().addParticle(CeFx.dust(0xE07AF0, 0.6F), p.x, p.y, p.z, 0, 0, 0);
            }
            case LIGHT_ORB -> level().addParticle(CeFx.dust(0xFFF4C0, 0.7F), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner instanceof DimensionalCreature d && d.isKin(target)) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level)) return;
        if (!(result.getEntity() instanceof LivingEntity t)) {
            // barcos, carrinhos, molduras...: o projetil se desfaz no impacto em vez de atravessar
            impact(level, position());
            return;
        }
        Entity owner = getOwner();
        DamageSource src = kind() == BoltKind.BOULDER || kind() == BoltKind.CRYSTAL_SHARD
                ? damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null)
                : (owner instanceof DimensionalCreature dc ? dc.magicSource(this) : damageSources().source(CoreRegistries.SPELL_DAMAGE, this));
        float dmg = damage;
        MobEffectInstance exposed = t.getEffect(CoreRegistries.EXPOSED);
        if (exposed != null) dmg *= 1F + 0.25F * (exposed.getAmplifier() + 1);
        boolean hit = t.hurt(src, dmg);
        Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize();
        switch (kind()) {
            case MAGMA, PHOENIX -> {
                if (hit) t.igniteForSeconds(kind() == BoltKind.MAGMA ? 5 : 4);
            }
            case WATER -> {
                t.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 120, 0), owner);
                if (t.isOnFire()) t.clearFire();
                t.push(push.x * 0.7, 0.2, push.z * 0.7);
                t.hurtMarked = true;
            }
            case GUST -> {
                t.push(push.x * 1.1, 0.75, push.z * 1.1);
                t.hurtMarked = true;
            }
            case LIGHT_SPEAR -> {
                t.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), owner);
                t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0), owner);
            }
            case CRYSTAL_SHARD -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), owner);
            case LIGHT_ORB -> t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), owner);
            case BOULDER -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), owner);
        }
        impact(level, position());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) impact(level, result.getLocation());
    }

    /** Efeito do impacto + dano em area do pedregulho e da bola de magma. */
    private void impact(ServerLevel level, Vec3 at) {
        switch (kind()) {
            case MAGMA -> {
                CeFx.burst(level, at, ParticleTypes.FLAME, 18, 0.4, 0.06);
                CeFx.burst(level, at, ParticleTypes.LAVA, 4, 0.3, 0.0);
                CeFx.burst(level, at, ParticleTypes.LARGE_SMOKE, 6, 0.3, 0.02);
                CeFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 0.8F, 0.8F);
                splash(level, at, 1.8, 3.0F, true);
            }
            case PHOENIX -> {
                CeFx.burst(level, at, ParticleTypes.FLAME, 12, 0.3, 0.05);
                CeFx.burst(level, at, CeFx.dust(0xFFD23F, 1.2F), 8, 0.3, 0.0);
                CeFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 0.6F, 1.5F);
            }
            case WATER -> {
                CeFx.burst(level, at, ParticleTypes.SPLASH, 30, 0.5, 0.2);
                CeFx.burst(level, at, ParticleTypes.BUBBLE_POP, 10, 0.4, 0.05);
                CeFx.sound(level, at, SoundEvents.GENERIC_SPLASH, 0.9F, 1.2F);
            }
            case BOULDER -> {
                CeFx.burst(level, at, CeFx.block(Blocks.MOSSY_COBBLESTONE.defaultBlockState()), 40, 0.6, 0.15);
                CeFx.burst(level, at, ParticleTypes.EXPLOSION, 1, 0.0, 0.0);
                CeFx.sound(level, at, SoundEvents.DEEPSLATE_BREAK, 1.2F, 0.6F);
                CeFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.3F);
                splash(level, at, 2.5, damage * 0.4F, false);
            }
            case GUST -> {
                CeFx.burst(level, at, ParticleTypes.GUST, 1, 0.0, 0.0);
                CeFx.burst(level, at, ParticleTypes.CLOUD, 12, 0.4, 0.08);
                CeFx.sound(level, at, SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 1.0F);
            }
            case LIGHT_SPEAR -> {
                CeFx.burst(level, at, ParticleTypes.END_ROD, 16, 0.2, 0.12);
                CeFx.burst(level, at, ParticleTypes.FLASH, 1, 0.0, 0.0);
                CeFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.6F);
            }
            case CRYSTAL_SHARD -> {
                CeFx.burst(level, at, CeFx.dust(0xE07AF0, 1.0F), 8, 0.2, 0.0);
                CeFx.burst(level, at, ParticleTypes.WAX_OFF, 5, 0.2, 0.05);
                CeFx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.4F);
            }
            case LIGHT_ORB -> {
                CeFx.burst(level, at, ParticleTypes.END_ROD, 6, 0.1, 0.05);
                CeFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.8F);
            }
        }
        discard();
    }

    /** Dano em area no impacto (respingo de magma, estilhacos de rocha). */
    private void splash(ServerLevel level, Vec3 at, double radius, float dmg, boolean fire) {
        Entity owner = getOwner();
        DimensionalCreature dc = owner instanceof DimensionalCreature d ? d : null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != owner && (dc == null || dc.isFoe(e)) && e.distanceToSqr(at) <= radius * radius)) {
            if (e.invulnerableTime > 10) continue;
            e.hurt(dc != null ? dc.magicSource(this) : damageSources().source(CoreRegistries.SPELL_DAMAGE, this), dmg);
            if (fire) e.igniteForSeconds(3);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", entityData.get(DATA_KIND));
        tag.putFloat("Damage", damage);
        tag.putInt("Age", age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getByte("Kind"));
        damage = tag.getFloat("Damage");
        age = tag.getInt("Age");
    }
}
