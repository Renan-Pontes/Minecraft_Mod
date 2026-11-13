package com.renanpontes.isekaimahou.feature.class_smith.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFxNet;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rho Aias — os Sete Anéis que Cobrem o Céu (homenagem): um escudo de 7 pétalas de luz na frente de quem o recebeu.
 * Acompanha o olhar do dono; cada pétala aguenta {@link #petalHp} de dano vindo da FRENTE (golpes, flechas, magias,
 * explosões) e se parte quando passa disso. Projéteis inimigos que cruzam o escudo são parados no ar. Um por criatura
 * (reaplicar renova). Não é salvo; some com o dono, no fim do tempo ou sem pétalas.
 */
public class RhoAiasEntity extends Entity {
    public static final int PETALS = 7;
    /** Raio do disco do escudo (blocos). */
    public static final double DISK = 2.3;
    private static final int CAP = 64;
    private static final Map<UUID, RhoAiasEntity> BY_OWNER = new HashMap<>();

    private static final EntityDataAccessor<Integer> DATA_PETALS = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CRACK = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.FLOAT);

    @Nullable private LivingEntity owner;
    @Nullable private SpellContext ctx;
    private float petalHp = 15F;
    private float currentHp = 15F;
    private int life = 160;

    public RhoAiasEntity(EntityType<? extends RhoAiasEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Ergue (ou renova) o escudo de {@code target}. */
    public static void raise(ServerLevel level, LivingEntity target, @Nullable SpellContext ctx, float petalHp, int ticks, int color) {
        RhoAiasEntity e = BY_OWNER.get(target.getUUID());
        if (e != null && (e.isRemoved() || e.level() != level)) {
            BY_OWNER.remove(target.getUUID());
            e = null;
        }
        if (e == null) {
            if (BY_OWNER.size() >= CAP) return;
            e = new RhoAiasEntity(SmithRegistries.RHO_AIAS.get(), level);
            e.owner = target;
            e.follow();
            level.addFreshEntity(e);
            BY_OWNER.put(target.getUUID(), e);
        }
        e.ctx = ctx;
        e.petalHp = Math.max(4F, petalHp);
        e.currentHp = e.petalHp;
        e.life = e.tickCount + ticks;
        e.entityData.set(DATA_PETALS, PETALS);
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        e.entityData.set(DATA_CRACK, 0F);
        Vec3 c = e.position();
        SmithFxNet.at(level, SmithFxNet.RHO_AIAS_UP, c, e.facing(), 1F, color, e.getId());
        SmithFx.sound(level, c, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        SmithFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
        if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.rho_aias", PETALS).withColor(color), true);
        }
    }

    @Nullable
    public static RhoAiasEntity of(LivingEntity owner) {
        RhoAiasEntity e = BY_OWNER.get(owner.getUUID());
        return e == null || e.isRemoved() ? null : e;
    }

    public static void clearAll() {
        BY_OWNER.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PETALS, PETALS);
        builder.define(DATA_COLOR, 0xFF8AD0);
        builder.define(DATA_CRACK, 0F);
    }

    public int petals() { return entityData.get(DATA_PETALS); }
    public int color() { return entityData.get(DATA_COLOR); }
    /** Quanto a pétala de fora já rachou (0..1), para o renderer. */
    public float crack() { return entityData.get(DATA_CRACK); }

    /** Para onde o escudo está virado (o olhar do dono). */
    public Vec3 facing() {
        return owner != null ? owner.getLookAngle() : Vec3.directionFromRotation(getXRot(), getYRot());
    }

    private void follow() {
        if (owner == null) return;
        Vec3 look = owner.getLookAngle();
        Vec3 at = owner.getEyePosition().add(look.scale(1.5)).subtract(0, 0.25, 0);
        setPos(at.x, at.y, at.z);
        setYRot(owner.getYHeadRot());
        setXRot(owner.getXRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 f = Vec3.directionFromRotation(getXRot(), getYRot());
                Vec3 s = f.cross(new Vec3(0, 1, 0));
                if (s.lengthSqr() < 1.0E-4) s = new Vec3(1, 0, 0);
                s = s.normalize();
                Vec3 u = s.cross(f).normalize();
                Vec3 q = position().add(s.scale(Math.cos(a) * DISK)).add(u.scale(Math.sin(a) * DISK));
                level().addParticle(SmithFx.spark(color()), q.x, q.y, q.z, 0, 0.01, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (owner == null || !owner.isAlive() || owner.level() != sl || tickCount > life || petals() <= 0) {
            vanish(sl);
            return;
        }
        follow();
        interceptProjectiles(sl);
    }

    /** Projéteis inimigos que cruzam o disco param no ar e gastam o escudo. */
    private void interceptProjectiles(ServerLevel sl) {
        Vec3 c = position();
        Vec3 f = facing();
        int stopped = 0;
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(DISK + 1.5))) {
            if (stopped >= 6 || petals() <= 0) break;
            Entity shooter = p.getOwner();
            if (shooter == owner || (shooter != null && ctx != null && ctx.isAlly(shooter))) continue;
            Vec3 v = p.getDeltaMovement();
            Vec3 toOwner = owner.position().add(0, owner.getBbHeight() * 0.5, 0).subtract(p.position());
            if (v.lengthSqr() < 1.0E-4 || v.dot(toOwner) <= 0) continue;
            Vec3 rel = p.position().subtract(c);
            double along = rel.dot(f);
            double radial = rel.subtract(f.scale(along)).length();
            if (Math.abs(along) > 1.4 || radial > DISK) continue;
            Vec3 at = p.position();
            p.discard();
            stopped++;
            SmithFxNet.at(sl, SmithFxNet.RHO_AIAS_BLOCK, at, facing(), 1F, color(), getId());
            SmithFx.sound(sl, at, SoundEvents.SHIELD_BLOCK, 0.8F, 1.3F);
            absorb(6F);
        }
    }

    /**
     * O golpe vem da frente do escudo? ({@code source} com posição e na frente: até ~75° do olhar.)
     */
    public boolean covers(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null || owner == null) return false;
        Vec3 to = from.subtract(owner.getEyePosition());
        if (to.lengthSqr() < 1.0E-4) return true;
        return to.normalize().dot(facing()) > 0.25;
    }

    /** Absorve dano nas pétalas; devolve o que sobrou depois da última (0 = tudo segurado). */
    public float absorb(float amount) {
        ServerLevel sl = (ServerLevel) level();
        float left = amount;
        while (left > 0 && petals() > 0) {
            if (left < currentHp) {
                currentHp -= left;
                left = 0;
            } else {
                left -= currentHp;
                breakPetal(sl);
                currentHp = petalHp;
            }
        }
        entityData.set(DATA_CRACK, petals() > 0 ? Math.max(0F, Math.min(1F, 1F - currentHp / petalHp)) : 0F);
        Vec3 c = position();
        sl.sendParticles(SmithFx.dust(color(), 1.0F), c.x, c.y, c.z, 6, 0.8, 0.8, 0.8, 0);
        SmithFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.2F);
        return left;
    }

    private void breakPetal(ServerLevel sl) {
        int left = petals() - 1;
        entityData.set(DATA_PETALS, Math.max(0, left));
        Vec3 c = position();
        SmithFxNet.at(sl, SmithFxNet.RHO_AIAS_BREAK, c, facing(), 1F, color(), getId(), Math.max(0, left));
        SmithFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.9F + 0.08F * left);
        SmithFx.sound(sl, c, SoundEvents.GLASS_BREAK, 0.8F, 1.4F);
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.rho_aias", Math.max(0, left)).withColor(color()), true);
        }
    }

    private void vanish(ServerLevel sl) {
        Vec3 c = position();
        sl.sendParticles(SmithFx.dust(color(), 1.2F), c.x, c.y, c.z, 20, 1.0, 1.0, 1.0, 0.02);
        SmithFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.5F);
        if (owner != null && BY_OWNER.get(owner.getUUID()) == this) BY_OWNER.remove(owner.getUUID());
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (owner != null && BY_OWNER.get(owner.getUUID()) == this) BY_OWNER.remove(owner.getUUID());
        super.remove(reason);
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
