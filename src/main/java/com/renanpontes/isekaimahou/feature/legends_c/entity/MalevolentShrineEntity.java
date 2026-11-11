package com.renanpontes.isekaimahou.feature.legends_c.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * O SANTUÁRIO MALIGNO que aparece atrás do dono do domínio (lei {@code law_shrine}): templo de madeira escura com
 * telhado em pagode, bocarra com dentes, chifres, pilhas de crânios e véu vermelho (modelo GeckoLib, ~12 blocos).
 * Ergue-se do chão ao abrir ("open"), respira ("idle") e afunda ao fechar o domínio ("close"). O cliente também usa
 * as instâncias vivas para pintar o céu/névoa de vermelho para quem está dentro do raio.
 */
public class MalevolentShrineEntity extends LcEntity implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_STATE_TICK = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CX = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CY = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CZ = SynchedEntityData.defineId(MalevolentShrineEntity.class, EntityDataSerializers.FLOAT);

    public static final int OPENING = 0, IDLE = 1, CLOSING = 2;
    public static final int OPEN_TICKS = 40;
    public static final int CLOSE_TICKS = 34;

    private static final RawAnimation OPEN = RawAnimation.begin()
            .thenPlay("animation.malevolent_shrine.open").thenLoop("animation.malevolent_shrine.idle");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.malevolent_shrine.close");
    /** Quem chega depois (entrou no alcance com o santuário já erguido) vê ele parado de pé, sem "nascer" de novo. */
    private static final RawAnimation IDLE_ONLY = RawAnimation.begin().thenLoop("animation.malevolent_shrine.idle");

    /** Santuários por domínio (servidor). */
    private static final Map<Integer, MalevolentShrineEntity> BY_DOMAIN = new HashMap<>();
    /** Instâncias vivas no CLIENTE (o céu vermelho lê daqui; sem classe de cliente aqui). */
    public static final Set<MalevolentShrineEntity> CLIENT_LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int domainId = -1;
    private int missing;

    public MalevolentShrineEntity(EntityType<? extends MalevolentShrineEntity> type, Level level) {
        super(type, level);
    }

    /** Ergue o santuário ~6 blocos atrás do dono, no chão, virado para a mesma direção que ele. */
    public static MalevolentShrineEntity raise(Domains.Info d) {
        return raise(d, false);
    }

    /**
     * @param standing já de pé (sem a animação de nascer): usado quando o santuário volta depois de o chunk recarregar
     *                 ou de sair e entrar no mundo com o domínio ainda aberto (a entidade não é salva).
     */
    public static MalevolentShrineEntity raise(Domains.Info d, boolean standing) {
        ServerLevel l = d.level();
        LivingEntity owner = d.owner();
        close(d.id());
        Vec3 back = LcUtil.flat(owner.getLookAngle()).scale(-6.5);
        Vec3 at = owner.position().add(back).add(0, 3, 0);
        at = LcUtil.ground(l, at, 12);
        MalevolentShrineEntity s = new MalevolentShrineEntity(LcRegistry.SHRINE.get(), l);
        s.bindOwner(owner);
        s.domainId = d.id();
        s.entityData.set(DATA_STATE_TICK, l.getGameTime());
        if (standing) s.entityData.set(DATA_STATE, IDLE);
        s.setPos(at.x, at.y, at.z);
        s.setYRot(owner.getYRot());
        s.yRotO = s.getYRot();
        s.setRadius(d.radius(), d.center());
        l.addFreshEntity(s);
        BY_DOMAIN.put(d.id(), s);
        return s;
    }

    /** Fecha (afunda) o santuário do domínio, se houver. */
    public static void close(int domainId) {
        MalevolentShrineEntity s = BY_DOMAIN.remove(domainId);
        if (s != null && !s.isRemoved()) s.startClosing();
    }

    @Nullable
    public static MalevolentShrineEntity of(int domainId) {
        MalevolentShrineEntity s = BY_DOMAIN.get(domainId);
        return s == null || s.isRemoved() ? null : s;
    }

    public void setRadius(float r, Vec3 center) {
        if (Math.abs(radius() - r) > 0.05F) entityData.set(DATA_RADIUS, r);
        if (center.distanceToSqr(domainCenter()) > 0.01) {
            entityData.set(DATA_CX, (float) center.x);
            entityData.set(DATA_CY, (float) center.y);
            entityData.set(DATA_CZ, (float) center.z);
        }
    }

    private void startClosing() {
        if (state() == CLOSING) return;
        entityData.set(DATA_STATE, CLOSING);
        entityData.set(DATA_STATE_TICK, level().getGameTime());
        if (level() instanceof ServerLevel l) {
            LcFx.sound(l, position(), SoundEvents.WARDEN_DIG, 3F, 0.6F);
            LcFx.sound(l, position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 2F, 0.5F);
        }
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, OPENING);
        builder.define(DATA_STATE_TICK, 0L);
        builder.define(DATA_RADIUS, 20F);
        builder.define(DATA_CX, 0F);
        builder.define(DATA_CY, 0F);
        builder.define(DATA_CZ, 0F);
    }

    public int state() { return entityData.get(DATA_STATE); }
    /** Ticks desde a última troca de estado (pelo relógio do mundo: igual no servidor e no cliente). */
    public int stateAge() { return (int) (level().getGameTime() - entityData.get(DATA_STATE_TICK)); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public Vec3 domainCenter() { return new Vec3(entityData.get(DATA_CX), entityData.get(DATA_CY), entityData.get(DATA_CZ)); }

    /** 0..1: quanto o santuário está "presente" (sobe ao abrir, some ao fechar) — para o céu e a aura. */
    public float presence(float pt) {
        float age = stateAge() + pt;
        return switch (state()) {
            case OPENING -> Math.min(1F, age / OPEN_TICKS);
            case CLOSING -> Math.max(0F, 1F - age / CLOSE_TICKS);
            default -> 1F;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            CLIENT_LIVE.add(this);
            clientFx();
            return;
        }
        ServerLevel l = (ServerLevel) level();
        int st = state();
        if (st == CLOSING) {
            if (stateAge() >= CLOSE_TICKS) discard();
            else if (stateAge() % 4 == 0) LcFx.far(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 2, getZ(), 6, 3, 1, 3, 0.02);
            return;
        }
        if (st == OPENING && stateAge() >= OPEN_TICKS) {
            entityData.set(DATA_STATE, IDLE);
            entityData.set(DATA_STATE_TICK, level().getGameTime());
        }
        // Domínio sumiu sem avisar (não deveria): fecha sozinho depois de 3 s
        boolean alive = false;
        for (Domains.Info d : Domains.active(l)) if (d.id() == domainId) { alive = true; break; }
        missing = alive ? 0 : missing + 1;
        if (missing > 60) {
            BY_DOMAIN.remove(domainId, this);
            startClosing();
            return;
        }
        if (tickCount % 50 == 0) LcFx.sound(l, position(), SoundEvents.WARDEN_HEARTBEAT, 3F, 0.5F);
        if (tickCount % 3 == 0) {
            LcFx.far(l, LcFx.dust(LcFx.RED, 2.2F), getX(), getY() + 6, getZ(), 6, 4.5, 4.0, 4.5, 0.02);
            LcFx.far(l, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 11.5, getZ(), 2, 2.5, 0.3, 2.5, 0.01);
        }
    }

    private void clientFx() {
        if (state() == CLOSING || random.nextInt(2) != 0) return;
        // Brasas vermelhas subindo do véu em volta do templo
        double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 3;
        level().addParticle(ParticleTypes.CRIMSON_SPORE, getX() + Math.cos(a) * r, getY() + random.nextDouble() * 8,
                getZ() + Math.sin(a) * r, 0, 0.02, 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, ev -> {
            int st = state();
            if (st == CLOSING) return ev.setAndContinue(CLOSE);
            if (st == IDLE && ev.getController().getCurrentRawAnimation() != OPEN) return ev.setAndContinue(IDLE_ONLY);
            return ev.setAndContinue(OPEN);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    public static void clear() {
        BY_DOMAIN.clear();
    }
}
