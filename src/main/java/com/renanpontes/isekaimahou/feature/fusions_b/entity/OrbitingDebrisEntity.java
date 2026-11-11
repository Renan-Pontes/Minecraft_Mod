package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Órbita de Destroços: blocos de terra/pedra que giram em volta do conjurador. Quando um inimigo chega perto
 * (ou o alvo marcado está ao alcance), um destroço por vez é arremessado nele e se desfaz no impacto.
 * Os blocos são cópias (nenhum bloco do mundo é removido). A órbita é calculada pelo tempo do mundo nos dois
 * lados, então fica lisa no cliente sem depender de pacotes de posição.
 */
public class OrbitingDebrisEntity extends FbSpellEntity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(OrbitingDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(OrbitingDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> INDEX = SynchedEntityData.defineId(OrbitingDebrisEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> TOTAL = SynchedEntityData.defineId(OrbitingDebrisEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(OrbitingDebrisEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int MAX_PER_OWNER = 6;
    private static final double ORBIT_RADIUS = 1.9;

    private float damage = 4F;
    @Nullable
    private UUID priority;
    @Nullable
    private LivingEntity launchTarget;
    private int flight;

    public OrbitingDebrisEntity(EntityType<? extends OrbitingDebrisEntity> type, Level level) {
        super(type, level);
    }

    /** Cria {@code count} destroços em volta do conjurador (renovando a órbita existente). */
    public static void summon(SpellContext ctx, List<BlockState> states, int count, int ticks, float damage, @Nullable LivingEntity priority,
                              SpellStats stats) {
        ServerLevel sl = ctx.level();
        LivingEntity owner = ctx.caster();
        if (owner == null) return;
        UUID id = owner.getUUID();
        List<OrbitingDebrisEntity> mine = new ArrayList<>(sl.getEntities(FbRegistries.ORBITING_DEBRIS.get(),
                d -> d.isAlive() && id.equals(d.ownerId()) && !d.launched()));
        for (OrbitingDebrisEntity d : mine) d.crumble(sl);
        int n = Math.max(1, Math.min(MAX_PER_OWNER, count));
        for (int i = 0; i < n; i++) {
            OrbitingDebrisEntity d = new OrbitingDebrisEntity(FbRegistries.ORBITING_DEBRIS.get(), sl);
            d.setup(ctx, ticks + i * 4);
            d.setHitStats(stats);
            d.damage = damage;
            d.priority = priority != null ? priority.getUUID() : null;
            d.entityData.set(STATE, states.get(i % states.size()));
            d.entityData.set(OWNER, owner.getId());
            d.entityData.set(INDEX, (byte) i);
            d.entityData.set(TOTAL, (byte) n);
            Vec3 p = d.orbitPos(owner, 0);
            d.setPos(p.x, p.y, p.z);
            sl.addFreshEntity(d);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, d.blockState()), p.x, owner.getY() + 0.1, p.z,
                    10, 0.3, 0.1, 0.3, 0.1);
        }
        FbFx.sound(sl, owner.position(), net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.6F);
        FbFx.sound(sl, owner.position(), net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, Blocks.DIRT.defaultBlockState());
        builder.define(OWNER, -1);
        builder.define(INDEX, (byte) 0);
        builder.define(TOTAL, (byte) 1);
        builder.define(LAUNCHED, false);
    }

    public BlockState blockState() { return entityData.get(STATE); }
    public boolean launched() { return entityData.get(LAUNCHED); }

    @Nullable
    private Entity orbitOwner() { return level().getEntity(entityData.get(OWNER)); }

    /** Posição na órbita (a mesma conta no servidor e no cliente). */
    public Vec3 orbitPos(Entity owner, float partialTick) {
        int total = Math.max(1, entityData.get(TOTAL));
        int index = entityData.get(INDEX);
        double t = level().getGameTime() + partialTick;
        double a = t * 0.11 + index * Math.PI * 2 / total;
        double bob = Math.sin(t * 0.09 + index * 1.7) * 0.25;
        Vec3 base = partialTick == 0 ? owner.position() : owner.getPosition(partialTick);
        return base.add(Math.cos(a) * ORBIT_RADIUS, owner.getBbHeight() * 0.55 + bob, Math.sin(a) * ORBIT_RADIUS);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (launched()) {
            flyTick(sl);
            return;
        }
        Entity owner = orbitOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            crumble(sl);
            return;
        }
        Vec3 p = orbitPos(owner, 0);
        setPos(p.x, p.y, p.z);
        int index = entityData.get(INDEX);
        if (maxLife() - life > 10 && (tickCount + index * 4) % 10 == 0) {
            LivingEntity t = pickTarget(sl, owner);
            if (t != null) launch(sl, t);
        }
    }

    @Nullable
    private LivingEntity pickTarget(ServerLevel sl, Entity owner) {
        if (priority != null && sl.getEntity(priority) instanceof LivingEntity pt && pt.isAlive() && !ally(pt)
                && pt.distanceToSqr(owner) < 14 * 14) {
            return pt;
        }
        LivingEntity best = null;
        double bestD = 3.8 * 3.8;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.8),
                e -> e.isAlive() && e != owner && !ally(e) && !e.isSpectator())) {
            double d = e.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private void launch(ServerLevel sl, LivingEntity t) {
        launchTarget = t;
        entityData.set(LAUNCHED, true);
        Vec3 dir = FbUtil.center(t).subtract(position()).normalize();
        setDeltaMovement(dir.scale(1.1));
        hasImpulse = true;
        FbFx.sound(sl, position(), net.minecraft.sounds.SoundEvents.SHULKER_SHOOT, 0.7F, 0.6F);
    }

    private void flyTick(ServerLevel sl) {
        flight++;
        Vec3 v = getDeltaMovement();
        if (launchTarget != null && launchTarget.isAlive()) {
            Vec3 want = FbUtil.center(launchTarget).subtract(position()).normalize().scale(1.1);
            v = v.scale(0.7).add(want.scale(0.3));
        }
        Vec3 from = position();
        Vec3 to = from.add(v);
        BlockHitResult bh = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.5),
                e -> e.isAlive() && !ally(e) && !e.isSpectator())) {
            if (e.getBoundingBox().inflate(0.35).clip(from, end).isPresent() || e.getBoundingBox().inflate(0.35).contains(from)) {
                hit(e, damage);
                FbUtil.addMotion(e, v.normalize().scale(0.45).add(0, 0.15, 0));
                crumble(sl);
                return;
            }
        }
        if (bh.getType() != HitResult.Type.MISS || flight > 30) {
            crumble(sl);
            return;
        }
        setDeltaMovement(v);
        setPos(end.x, end.y, end.z);
    }

    /** Desfaz o bloco em partículas com o som de quebra dele. */
    public void crumble(ServerLevel sl) {
        if (isRemoved()) return;
        BlockState s = blockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), getX(), getY() + 0.3, getZ(), 18, 0.25, 0.25, 0.25, 0.15);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.05);
        FbFx.sound(sl, position(), s.getSoundType(sl, blockPosition(), this).getBreakSound(), 1.0F, 0.8F);
        discard();
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        crumble(sl);
    }

    @Override
    protected void clientTick() {
        if (!launched()) {
            Entity owner = orbitOwner();
            if (owner != null) {
                Vec3 p = orbitPos(owner, 0);
                setPos(p.x, p.y, p.z);
            }
        } else {
            Vec3 v = getDeltaMovement();
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, blockState()), getX(), getY() + 0.3, getZ(), 0, 0, 0);
        }
        if (random.nextInt(8) == 0) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
        }
    }

    /** No cliente a posição vem da conta da órbita, não dos pacotes (evita tremedeira). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (launched() || orbitOwner() == null) super.lerpTo(x, y, z, yRot, xRot, steps);
    }
}
