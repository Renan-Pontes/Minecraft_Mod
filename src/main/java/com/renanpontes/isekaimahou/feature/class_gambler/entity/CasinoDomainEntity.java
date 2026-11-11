package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.LawGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Domínio "Cassino da Fortuna" (forma G do Apostador): um salão de cassino aberto em volta do conjurador — mesa de
 * feltro com a roleta gigante girando no chão, cartas e dados enormes orbitando. Registrado no núcleo de Domínios
 * ({@link Domains#opened}) como domínio ABERTO (sem barreira) com Acerto Garantido + as Leis colocadas na magia.
 * <ul>
 *   <li>A cada 1 s o conjurador rola a sorte (com HUD); todos os inimigos ao alcance recebem os efeitos com esse poder;</li>
 *   <li>a Tensão do Apostador sobe +5 por pulso (o Jackpot costuma vir lá dentro) e o Jackpot dentro dele dura ×1,5;</li>
 *   <li>aliados (e você) dentro ficam Sortudos.</li>
 * </ul>
 * Um por conjurador (conjurar de novo fecha o anterior). Não é salvo no mundo.
 */
public class CasinoDomainEntity extends Entity implements GeoEntity {
    public static final int PULSE = 20;
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.casino_domain.spin");
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(CasinoDomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(CasinoDomainEntity.class, EntityDataSerializers.INT);
    /** Cassino aberto de cada conjurador. */
    private static final Map<UUID, CasinoDomainEntity> BY_OWNER = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID ownerId;
    @Nullable
    private Domains.Info info;
    private Set<ResourceLocation> laws = Set.of();

    public CasinoDomainEntity(EntityType<? extends CasinoDomainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static CasinoDomainEntity open(SpellContext ctx, LivingEntity owner, float radius, int life) {
        CasinoDomainEntity old = BY_OWNER.get(owner.getUUID());
        if (old != null && !old.isRemoved()) old.close();
        CasinoDomainEntity e = new CasinoDomainEntity(GbRegistries.CASINO_DOMAIN.get(), ctx.level());
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        e.ctx = ctx;
        e.ownerId = owner.getUUID();
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_LIFE, life);
        Set<ResourceLocation> l = new HashSet<>();
        l.add(Domains.SURE_HIT);
        for (LawGlyph law : ctx.spell().laws()) l.add(law.id());
        e.laws = l;
        BY_OWNER.put(owner.getUUID(), e);
        return e;
    }

    /** O jogador está dentro do próprio Cassino aberto? (o Jackpot lá dentro dura mais) */
    public static boolean insideOwnCasino(LivingEntity e) {
        CasinoDomainEntity c = BY_OWNER.get(e.getUUID());
        return c != null && !c.isRemoved() && c.level() == e.level() && c.position().distanceToSqr(e.position()) <= c.radius() * c.radius();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 10F);
        builder.define(DATA_LIFE, 240);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    /** 0..1: abre nos primeiros 15 ticks e fecha nos últimos 15 (o renderer cresce/encolhe o salão). */
    public float openness(float partialTick) {
        float t = tickCount + partialTick;
        return Math.max(0F, Math.min(1F, Math.min(t / 15F, (life() - t) / 15F)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        if (ctx == null || owner == null || !owner.isAlive() || owner.level() != sl || tickCount >= life()) {
            close();
            return;
        }
        float r = radius();
        Vec3 c = position();
        if (info == null) {
            info = new Domains.Info(getId(), sl, owner, c.add(0, 1, 0), r, true, laws, ctx.element(), ctx, sl.getGameTime(), life());
            Domains.opened(info);
            GbFx.sound(sl, c, SoundEvents.END_PORTAL_SPAWN, 0.7F, 1.6F);
            GbFx.jackpotBurst(sl, owner);
        }
        Domains.ticked(info, tickCount);
        if (tickCount % 5 == 0) GbFx.groundRing(sl, c, r, tickCount % 10 == 0 ? GbFx.GOLD : GbFx.PINK, 48);
        if (tickCount > 0 && tickCount % PULSE == 0) pulse(sl, owner, r);
    }

    private void pulse(ServerLevel sl, LivingEntity owner, float r) {
        LuckService.Roll roll = LuckService.rollAndShow(owner, LuckService.options(ctx.spell(), owner), LuckService.Kind.DOMAIN, 0);
        if (owner instanceof ServerPlayer sp && LuckService.isGambler(sp)) LuckService.addFever(sp, 5);
        SpellContext hit = ctx.copy(ctx.powerMult() * roll.mult());
        for (LivingEntity e : info.reachableEnemies()) {
            e.invulnerableTime = 0;
            hit.resolveOnEntity(e, this);
            sl.sendParticles(GbFx.dust(roll.band().rgb, 1.2F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 6, 0.2, 0.1, 0.2, 0);
        }
        AABB box = new AABB(position(), position()).inflate(r);
        for (LivingEntity a : sl.getEntitiesOfClass(LivingEntity.class, box,
                x -> x.isAlive() && x.distanceToSqr(this) <= r * r && (x == owner || ctx.isAlly(x)))) {
            a.addEffect(new MobEffectInstance(GbRegistries.LUCKY, PULSE * 2 + 5, 0, true, true, true), owner);
        }
        GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9F, 0.8F + roll.mult() * 0.3F);
    }

    /** Fecha: avisa o núcleo e some. */
    public void close() {
        if (isRemoved()) return;
        if (info != null) Domains.closed(info);
        info = null;
        if (ownerId != null && BY_OWNER.get(ownerId) == this) BY_OWNER.remove(ownerId);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(GbFx.dust(GbFx.GOLD, 1.5F), getX(), getY() + 0.5, getZ(), 40, radius() * 0.4, 0.5, radius() * 0.4, 0);
            GbFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.3F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (info != null && !level().isClientSide) {
            Domains.closed(info);
            info = null;
        }
        if (ownerId != null && BY_OWNER.get(ownerId) == this) BY_OWNER.remove(ownerId);
        super.remove(reason);
    }

    private void clientFx() {
        float r = radius() * openness(0);
        if (r <= 0.5F) return;
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            level().addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT, getX() + Math.cos(a) * d, getY() + 0.2,
                    getZ() + Math.sin(a) * d, 0, 0.04 + random.nextDouble() * 0.04, 0);
        }
    }

    /** Fecha tudo ao parar o servidor. */
    public static void clearAll() {
        BY_OWNER.clear();
    }

    /** O conjurador saiu: fecha o cassino dele. */
    public static void forget(UUID owner) {
        CasinoDomainEntity c = BY_OWNER.remove(owner);
        if (c != null && !c.isRemoved()) c.close();
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Usado pelo contador de cassinos abertos (requisito da Casa). */
    public static void countCast(ServerPlayer sp) {
        MagicApi.addCounter(sp, "gambler.casino", 1);
    }
}
