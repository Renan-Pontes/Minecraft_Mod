package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Bloco arrancado do terreno, voando — SÓ visual: nunca coloca bloco, nunca vira item, não é salvo.
 * O movimento é uma função do estado sincronizado (ponto de partida, âncora, vaga na casca), calculado igual no
 * servidor e no cliente: quase nenhum pacote de posição, mesmo com dezenas de blocos no ar.
 * <ul>
 * <li>{@link #ATTRACT}: espirala até o centro da âncora (Buraco Negro) e é "engolido".</li>
 * <li>{@link #SHELL}: voa até uma vaga numa esfera em volta da âncora e fica girando com ela (Esmagamento Planetário);
 * quando a âncora solta (ou some), despenca e se desfaz.</li>
 * </ul>
 */
public class RubbleEntity extends Entity {
    public static final int ATTRACT = 0, SHELL = 1;
    /** Teto global de blocos voando (todas as magias). */
    public static final int MAX_ALIVE = 260;
    private static int alive;

    private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> ANCHOR = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> START = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SLOT = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.VECTOR3);

    /** Cliente: giro (graus por tick). */
    public final float spinX, spinZ;
    private boolean counted;
    /** Tick em que começou a cair (-1 = ainda preso à âncora). */
    private int fallStart = -1;
    private Vec3 fallVel = Vec3.ZERO;

    public RubbleEntity(EntityType<? extends RubbleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.spinX = (level.random.nextFloat() - 0.5F) * 30F;
        this.spinZ = (level.random.nextFloat() - 0.5F) * 30F;
    }

    /** Âncora de destroços (o centro para onde voam; a casca gira e encolhe com ela). */
    public interface Anchor {
        Vec3 rubbleCenter();
        /** Escala da casca (1 = raio normal). */
        default float shellScale() { return 1F; }
        /** Giro da casca em radianos. */
        default float shellAngle() { return 0F; }
        /** true = os destroços da casca soltam e despencam. */
        default boolean released() { return false; }
    }

    /** Destroço que espirala até a âncora e some ao chegar. */
    public static boolean attract(ServerLevel sl, BlockState state, Vec3 from, Entity anchor, int flight) {
        return spawn(sl, state, from, anchor, ATTRACT, Vec3.ZERO, flight);
    }

    /** Destroço que voa até {@code slot} (relativo ao centro da âncora) e forma a casca. */
    public static boolean shell(ServerLevel sl, BlockState state, Vec3 from, Entity anchor, Vec3 slot, int flight) {
        return spawn(sl, state, from, anchor, SHELL, slot, flight);
    }

    private static boolean spawn(ServerLevel sl, BlockState state, Vec3 from, Entity anchor, int mode, Vec3 slot, int flight) {
        if (alive >= MAX_ALIVE || state.isAir() || !state.getFluidState().isEmpty()) return false;
        RubbleEntity r = new RubbleEntity(FdRegistries.RUBBLE.get(), sl);
        r.setPos(from.x, from.y, from.z);
        r.entityData.set(BLOCK, state);
        r.entityData.set(ANCHOR, anchor.getId());
        r.entityData.set(MODE, mode);
        r.entityData.set(FLIGHT, Math.max(4, flight));
        r.entityData.set(START, from.toVector3f());
        r.entityData.set(SLOT, slot.toVector3f());
        return sl.addFreshEntity(r);
    }

    public static void resetCount() {
        alive = 0;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && !counted) {
            counted = true;
            alive++;
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide && counted) {
            counted = false;
            alive = Math.max(0, alive - 1);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(BLOCK, Blocks.STONE.defaultBlockState());
        b.define(ANCHOR, -1);
        b.define(MODE, ATTRACT);
        b.define(FLIGHT, 20);
        b.define(START, new Vector3f());
        b.define(SLOT, new Vector3f());
    }

    public BlockState blockState() { return entityData.get(BLOCK); }

    public int mode() { return entityData.get(MODE); }

    public int flight() { return entityData.get(FLIGHT); }

    public boolean falling() { return fallStart >= 0; }

    /** 0→1 durante o voo até a âncora/vaga. */
    public float progress(float pt) {
        return Mth.clamp((tickCount + pt) / flight(), 0F, 1F);
    }

    /** Escala visual: encolhe ao ser engolido e ao se desfazer depois de cair. */
    public float visualScale(float pt) {
        if (falling()) return Mth.clamp(1F - (tickCount + pt - fallStart - 22) / 14F, 0F, 1F);
        if (mode() == ATTRACT) return 1F - 0.75F * progress(pt) * progress(pt);
        return 1F;
    }

    @Override
    public void tick() {
        xo = getX();
        yo = getY();
        zo = getZ();
        super.tick();
        Entity a = level().getEntity(entityData.get(ANCHOR));
        Anchor anchor = a instanceof Anchor an && a.isAlive() ? an : null;
        Vec3 start = new Vec3(entityData.get(START));
        if (falling()) {
            fallVel = fallVel.add(0, -0.06, 0).scale(0.98);
            setPos(position().add(fallVel));
            if (!level().isClientSide && tickCount - fallStart > 36) discard();
            return;
        }
        if (anchor == null || (mode() == SHELL && anchor.released())) {
            // Âncora soltou ou sumiu: despenca para fora do centro
            Vec3 c = anchor != null ? anchor.rubbleCenter() : start;
            Vec3 out = position().subtract(c);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            fallVel = out.scale(0.35 + random.nextDouble() * 0.25).add(0, 0.25, 0);
            fallStart = tickCount;
            return;
        }
        Vec3 center = anchor.rubbleCenter();
        float t = progress(0F);
        if (mode() == ATTRACT) {
            float e = t * t * (3F - 2F * t);
            Vec3 d = center.subtract(start);
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(d).normalize();
            double swirl = d.length() * 0.22 * (1 - t);
            double ang = t * Math.PI * 3 + getId();
            Vec3 p = start.add(d.scale(e)).add(side.scale(Math.cos(ang) * swirl)).add(up.scale(Math.sin(ang) * swirl));
            setPos(p);
            if (!level().isClientSide && t >= 1F) discard();
        } else {
            Vector3f s = entityData.get(SLOT);
            float ang = anchor.shellAngle(), sc = anchor.shellScale();
            double cos = Math.cos(ang), sin = Math.sin(ang);
            Vec3 slot = new Vec3(s.x * cos - s.z * sin, s.y, s.x * sin + s.z * cos).scale(sc);
            Vec3 target = center.add(slot);
            float e = 1F - (1F - t) * (1F - t);
            setPos(t >= 1F ? target : start.lerp(target, e).add(0, Math.sin(t * Math.PI) * 2.0, 0));
        }
        if (!level().isClientSide && tickCount > 600) discard();
    }

    /** O cliente simula sozinho: as correções de posição do servidor são ignoradas (evita "pulos"). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {}

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 96 * 96; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
