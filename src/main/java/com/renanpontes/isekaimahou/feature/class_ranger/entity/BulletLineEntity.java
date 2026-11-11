package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LINHA DE BALA (homenagem à atiradora de elite do jogo de tiro virtual): durante a mira, uma linha vermelha liga o cano
 * ao alvo — e o ALVO vê a linha (é o aviso). Ao fim da mira o tiro sai instantâneo pela linha: acerta o alvo onde ele
 * estiver (a não ser que um bloco fique no caminho), com poder +2% por bloco de distância (até ×2); Perfurar atravessa
 * inimigos na linha. Desenhada por {@code BulletLineRenderer}. Não é salva e some logo depois do tiro.
 */
public class BulletLineEntity extends Entity {
    public static final String ID = "ranger_bullet_line";
    public static final int FADE = 6;
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(BulletLineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_END = SynchedEntityData.defineId(BulletLineEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(BulletLineEntity.class, EntityDataSerializers.BYTE);

    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private Vec3 point = Vec3.ZERO;
    private int aim = 16;
    private int firedAt = -1;

    public BulletLineEntity(EntityType<? extends BulletLineEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Começa a mira de {@code owner} em {@code target} (ou num ponto fixo, se o alvo for null). */
    public static BulletLineEntity aim(SpellContext ctx, LivingEntity owner, @Nullable LivingEntity target, Vec3 point, int aimTicks) {
        BulletLineEntity e = new BulletLineEntity(RangerRegistries.BULLET_LINE.get(), ctx.level());
        e.ctx = ctx;
        e.ownerId = owner.getUUID();
        e.targetId = target != null ? target.getUUID() : null;
        e.point = point;
        e.aim = Math.max(4, aimTicks);
        e.entityData.set(DATA_OWNER, owner.getId());
        Vec3 eye = owner.getEyePosition();
        e.setPos(eye.x, eye.y, eye.z);
        e.setEnd(target != null ? center(target) : point);
        return e;
    }

    private static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private void setEnd(Vec3 v) {
        entityData.set(DATA_END, new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_END, new Vector3f());
        builder.define(DATA_PHASE, (byte) 0);
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public Vec3 end() {
        Vector3f v = entityData.get(DATA_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** 0 = mirando (linha vermelha), 1 = disparou (traçante). */
    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        if (ctx == null || owner == null || !owner.isAlive() || owner.level() != sl) {
            discard();
            return;
        }
        if (firedAt >= 0) {
            if (tickCount - firedAt > FADE) discard();
            return;
        }
        Vec3 eye = owner.getEyePosition();
        setPos(eye.x, eye.y, eye.z);
        LivingEntity target = targetId != null && sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive() && le.level() == sl ? le : null;
        Vec3 end = target != null ? center(target) : point;
        setEnd(end);
        // o "círculo de mira" que encolhe no alvo
        if (tickCount % 2 == 0) {
            double r = 1.2 * (1.0 - tickCount / (double) aim) + 0.25;
            RangerFx.ring(sl, end, r, 10, WeaponFx.dust(0xFF2A2A, 0.7F), 0);
        }
        if (tickCount == 1) RangerFx.sound(sl, eye, SoundEvents.CROSSBOW_LOADING_START.value(), 0.8F, 1.6F);
        if (tickCount >= aim) fire(sl, owner, target, eye, end);
    }

    private void fire(ServerLevel sl, LivingEntity owner, @Nullable LivingEntity target, Vec3 eye, Vec3 end) {
        firedAt = tickCount;
        entityData.set(DATA_PHASE, (byte) 1);
        SpellContext c = ctx;
        if (c == null) return;
        BlockHitResult block = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        boolean blocked = block.getType() == HitResult.Type.BLOCK && block.getLocation().distanceToSqr(eye) < end.distanceToSqr(eye) - 0.25;
        Vec3 stop = blocked ? block.getLocation() : end;
        setEnd(stop);
        double dist = eye.distanceTo(stop);
        float bonus = 1F + (float) Math.min(1.0, dist * 0.02);
        SpellContext shot = c.copy(c.powerMult() * bonus);
        SpellStats s = c.formStats();
        // Perfurar: inimigos no caminho também levam o tiro
        int pierce = Math.max(0, s.pierce);
        if (pierce > 0) {
            List<LivingEntity> line = new ArrayList<>();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1.0),
                    e -> e != owner && e != target && e.isAlive() && !c.isAlly(e))) {
                if (e.getBoundingBox().inflate(0.3).clip(eye, stop).isPresent()) line.add(e);
            }
            line.sort((a, b) -> Double.compare(a.distanceToSqr(eye), b.distanceToSqr(eye)));
            for (int i = 0; i < Math.min(pierce, line.size()); i++) {
                line.get(i).invulnerableTime = 0;
                shot.resolveOnEntity(line.get(i), this);
            }
        }
        if (target != null && !blocked) {
            target.invulnerableTime = 0;
            shot.resolveOnEntity(target, this);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            shot.resolveHit(block, this);
        }
        // traçante, clarão no cano e o estampido
        RangerFx.line(sl, eye, stop, 0.6, WeaponFx.dust(0xFFF2B0, 1.0F));
        RangerFx.line(sl, eye, stop, 2.0, ParticleTypes.END_ROD);
        sl.sendParticles(ParticleTypes.FIREWORK, eye.x, eye.y - 0.1, eye.z, 6, 0.05, 0.05, 0.05, 0.08);
        sl.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 12, 0.2, 0.2, 0.2, 0.3);
        RangerFx.sound(sl, eye, SoundEvents.FIREWORK_ROCKET_BLAST, 1.4F, 0.55F);
        RangerFx.sound(sl, eye, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.6F);
        RangerFx.sound(sl, stop, SoundEvents.ARROW_HIT, 1.0F, 0.7F);
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
