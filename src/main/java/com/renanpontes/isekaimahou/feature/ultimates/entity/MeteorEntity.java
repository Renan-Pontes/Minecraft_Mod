package com.renanpontes.isekaimahou.feature.ultimates.entity;

import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.ultimates.MeteorImpact;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.feature.ultimates.UltRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Meteoro: rocha incandescente que desce em linha reta do ponto de partida (céu) até o alvo. A trajetória é
 * determinística (partida, chegada, tick de início e duração sincronizados), então o cliente a calcula sozinho e o
 * movimento fica liso mesmo de longe. Bate no primeiro bloco ou criatura do caminho, ou no ponto mirado.
 * Não é salvo (some ao descarregar o chunk) e tem tempo máximo de vida.
 */
public class MeteorEntity extends Entity implements GeoEntity, TraceableEntity {
    private static final EntityDataAccessor<Vector3f> DATA_START = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_END = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> DATA_T0 = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_FALL = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.meteor.spin");

    /** Gancho de cliente (som crescente que segue a rocha). Preenchido pelo UltimatesClient. */
    public static volatile Consumer<MeteorEntity> clientSpawnHook = m -> {};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---------------- Servidor ----------------
    @Nullable private SpellContext ctx;
    @Nullable private UUID ownerId;
    private float radius = 4F;
    private float crater = 3F;
    private float impactDamage = 4F;
    private boolean guardCharge;
    private float volume = 1F;
    private boolean impacted;
    /** Este meteoro encerra o estágio (Ao Terminar nasce no impacto). */
    private boolean endsStage;
    private boolean clientStarted;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Cria (sem adicionar ao mundo) um meteoro de {@code start} até {@code end} em {@code fallTicks} ticks.
     * @param ctx contexto que aplica os efeitos no impacto (pode ser uma cópia com outro poder)
     */
    public static MeteorEntity create(SpellContext ctx, Vec3 start, Vec3 end, int fallTicks, float size, int rgb, float radius,
                                      float crater, float impactDamage, boolean guardCharge, float volume) {
        MeteorEntity m = new MeteorEntity(UltRegistries.METEOR.get(), ctx.level());
        m.ctx = ctx;
        m.ownerId = ctx.casterId();
        m.radius = radius;
        m.crater = crater;
        m.impactDamage = impactDamage;
        m.guardCharge = guardCharge;
        m.volume = volume;
        m.setPos(start);
        m.entityData.set(DATA_START, start.toVector3f());
        m.entityData.set(DATA_END, end.toVector3f());
        m.entityData.set(DATA_T0, ctx.level().getGameTime());
        m.entityData.set(DATA_FALL, Math.max(4, fallTicks));
        m.entityData.set(DATA_SIZE, size);
        m.entityData.set(DATA_COLOR, rgb);
        Vec3 d = end.subtract(start);
        m.setYRot((float) (Mth.atan2(d.x, d.z) * (180F / Math.PI)));
        m.yRotO = m.getYRot();
        return m;
    }

    /** Marca este meteoro como o que encerra o estágio da magia (dispara Ao Terminar no impacto). */
    public MeteorEntity endsStage() {
        this.endsStage = true;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_START, new Vector3f());
        builder.define(DATA_END, new Vector3f());
        builder.define(DATA_T0, 0L);
        builder.define(DATA_FALL, 20);
        builder.define(DATA_SIZE, 1F);
        builder.define(DATA_COLOR, MeteorImpact.FIRE);
    }

    // ---------------- Leitura (cliente e servidor) ----------------
    public Vec3 start() { return new Vec3(entityData.get(DATA_START)); }
    public Vec3 end() { return new Vec3(entityData.get(DATA_END)); }
    public float size() { return entityData.get(DATA_SIZE); }
    public int color() { return entityData.get(DATA_COLOR); }
    public int fallTicks() { return Math.max(1, entityData.get(DATA_FALL)); }

    /** Progresso da queda 0..1 (pode passar de 1 no último tick). */
    public float progress(float partial) {
        long t0 = entityData.get(DATA_T0);
        return ((level().getGameTime() - t0) + partial) / (float) fallTicks();
    }

    public Vec3 direction() {
        Vec3 d = end().subtract(start());
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
    }

    private Vec3 pointAt(float t) {
        return start().lerp(end(), Mth.clamp(t, 0F, 1F));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!(level() instanceof ServerLevel sl) || ctx == null || impacted) {
            discard();
            return;
        }
        if (tickCount > fallTicks() + 100) {
            discard();
            return;
        }
        float t = progress(0F);
        Vec3 prev = position();
        Vec3 next = pointAt(t);
        Vec3 dir = direction();
        // Bate no primeiro bloco do caminho (depois de sair da fenda)
        if (tickCount > 2 && prev.distanceToSqr(next) > 1.0E-4) {
            BlockHitResult bh = UltFx.clip(sl, prev, next, ClipContext.Fluid.ANY, null);
            if (bh.getType() == HitResult.Type.BLOCK) {
                setPos(bh.getLocation());
                impact(sl, bh.getLocation(), dir);
                return;
            }
        }
        LivingEntity caster = ctx.caster();
        // Parede de um Domínio FECHADO de outro dono no caminho: a rocha explode na barreira (que perde integridade
        // se o golpe vem do lado que quebra — regra do núcleo). O dono e os aliados atravessam a própria barreira.
        Vec3 wallHit = tickCount > 2 ? enemyBarrierHit(sl, prev, next, dir, caster) : null;
        if (wallHit != null) {
            setPos(wallHit);
            Domains.hitBarrier(sl, wallHit, prev.subtract(dir.scale(2.0)), this,
                    impactDamage * Math.max(0.5F, ctx.powerMult()) * (2.0F + size()));
            // A barreira segura o golpe: a rocha se espatifa nela (sem efeitos/cratera do outro lado da parede)
            impacted = true;
            MeteorImpact.shatterOnBarrier(sl, wallHit, size(), color(), volume);
            if (endsStage) ctx.end(wallHit.subtract(dir.scale(2.0)), dir);
            discard();
            return;
        }
        setPos(next);
        // Criatura no caminho (não aliada)
        float s = size();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(0.5 + s * 0.5),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !(e instanceof ArmorStand))) {
            if (ctx.isAlly(e)) continue;
            impact(sl, UltFx.groundBelow(sl, next, 6), dir);
            return;
        }
        if (t >= 1F) {
            impact(sl, next, dir);
            return;
        }
        // Rastro visível de longe também para quem ainda não rastreia a entidade
        if (tickCount % 3 == 0) {
            UltFx.far(sl, ParticleTypes.LARGE_SMOKE, next.x, next.y, next.z, 2, 0.3 * s, 0.3 * s, 0.3 * s, 0.01);
        }
    }

    /**
     * Ponto onde o trecho deste tick atravessa a parede de um Domínio fechado que não é do conjurador nem de um
     * aliado (null = não atravessa). Amostra o trecho a cada ~1 bloco: com Acelerar a rocha anda mais que a
     * espessura da faixa da parede num tick.
     */
    @Nullable
    private Vec3 enemyBarrierHit(ServerLevel sl, Vec3 prev, Vec3 next, Vec3 dir, @Nullable LivingEntity caster) {
        double len = prev.distanceTo(next);
        int steps = Mth.clamp((int) Math.ceil(len), 1, 8);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = prev.lerp(next, i / (double) steps);
            Domains.Info wall = Domains.barrierAt(sl, p);
            if (wall == null || wall.owner() == null) continue;
            if (caster != null && (caster == wall.owner() || caster.getUUID().equals(wall.owner().getUUID())
                    || ctx.isAlly(wall.owner()))) {
                continue;
            }
            // Só conta se de fato cruza (passar raspando por fora ou por dentro não explode)
            if (wall.contains(prev) != wall.contains(p.add(dir.scale(1.5)))) return p;
        }
        return null;
    }

    private void impact(ServerLevel sl, Vec3 at, Vec3 dir) {
        impacted = true;
        MeteorImpact.explode(ctx, sl, at, dir, size(), radius, crater, impactDamage, guardCharge, this, volume);
        if (endsStage) ctx.end(at.add(0, 1, 0), dir);
        discard();
    }

    // ---------------- Cliente: posição calculada e rastro de fogo ----------------
    private void clientTick() {
        float t = progress(0F);
        Vec3 p = pointAt(t);
        setPos(p);
        if (!clientStarted) {
            clientStarted = true;
            clientSpawnHook.accept(this);
        }
        if (t >= 1.05F) return;
        RandomSource r = random;
        float s = size();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        int n = reduced ? 2 : 5;
        Vec3 back = direction().scale(-1);
        for (int i = 0; i < n; i++) {
            double k = r.nextDouble() * 1.5 * s;
            double x = p.x + back.x * k + (r.nextDouble() - 0.5) * s;
            double y = p.y + back.y * k + (r.nextDouble() - 0.5) * s;
            double z = p.z + back.z * k + (r.nextDouble() - 0.5) * s;
            level().addParticle(ParticleTypes.FLAME, x, y, z, back.x * 0.05, back.y * 0.05, back.z * 0.05);
            if (i % 2 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, back.x * 0.08, 0.02, back.z * 0.08);
        }
        if (r.nextInt(reduced ? 4 : 2) == 0) level().addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
        if (!reduced && r.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + back.x * 2 * s, p.y + back.y * 2 * s, p.z + back.z * 2 * s,
                    0, 0.01, 0);
        }
    }

    /** Posição vem da trajetória calculada no próprio cliente: ignora as correções do servidor. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide) super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0 + size() * 3.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isOnFire() { return false; }

    @Override
    @Nullable
    public Entity getOwner() {
        if (ctx != null && ctx.caster() != null) return ctx.caster();
        if (ownerId != null && level() instanceof ServerLevel sl) return sl.getEntity(ownerId);
        return null;
    }

    // Nunca é salvo (EntityType.noSave): nada a gravar.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, s -> s.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
