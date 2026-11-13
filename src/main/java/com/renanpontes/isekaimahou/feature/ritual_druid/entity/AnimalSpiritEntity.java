package com.renanpontes.isekaimahou.feature.ritual_druid.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidProgress;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Espírito Animal (cervo, lobo ou falcão de luz). Surge da Árvore Coração quando ela cresce, corre/voa pelo ar até o
 * druida, conta a próxima tarefa de estação, dá uma bênção (Regeneração + Sorte) e se desfaz em luz.
 * É efêmero: não é salvo no mundo, não pode ser ferido nem empurrado.
 */
public class AnimalSpiritEntity extends Mob implements GeoEntity {
    public enum Kind {
        DEER(0x9CF5C8, 0.9F, 1.7F),
        WOLF(0xA9C8FF, 0.75F, 1.0F),
        HAWK(0xFFD86B, 0.7F, 0.7F);

        public final int rgb;
        public final float width, height;
        final RawAnimation idle, move;

        Kind(int rgb, float width, float height) {
            this.rgb = rgb;
            this.width = width;
            this.height = height;
            String base = "animation.animal_spirit_" + id() + ".";
            this.idle = RawAnimation.begin().thenLoop(base + "idle");
            this.move = RawAnimation.begin().thenLoop(base + (ordinal() == 2 ? "fly" : "walk"));
        }

        public String id() {
            return name().toLowerCase();
        }

        public MutableComponent displayName() {
            String key = "entity.isekai_mahou.animal_spirit." + id();
            return Component.translatable(key).withStyle(s -> s.withColor(rgb));
        }

        public static Kind byIndex(int i) {
            Kind[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(AnimalSpiritEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GREAT = SynchedEntityData.defineId(AnimalSpiritEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(AnimalSpiritEntity.class, EntityDataSerializers.INT);

    public static final int FADE_TICKS = 40;
    private static final int PHASE_EMERGE = 0, PHASE_APPROACH = 1, PHASE_STAY = 2, PHASE_FADE = 3;
    private static final int EMERGE_TICKS = 25, MAX_APPROACH_TICKS = 260, STAY_TICKS = 200;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID owner;
    /** Estágio que a árvore acabou de alcançar (a fala conta a tarefa do estágio seguinte). */
    private int stage;
    @Nullable private BlockPos home;
    private int phase = PHASE_EMERGE;
    private int phaseTicks;
    private long lastRepeat;
    private double orbit;
    /** Já falou com o druida e deu a bênção? (se não chegar até ele, entrega de longe ao se desfazer) */
    private boolean delivered;

    public AnimalSpiritEntity(EntityType<? extends AnimalSpiritEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
        this.xpReward = 0;
        this.orbit = this.random.nextDouble() * Math.PI * 2;
        // Espírito não ocupa espaço: não impede a árvore de crescer nem a colocação de blocos
        this.blocksBuilding = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** Faz surgir um espírito na Árvore Coração para falar com o dono. */
    public static AnimalSpiritEntity summon(ServerLevel level, BlockPos core, ServerPlayer owner, Kind kind, int stage) {
        AnimalSpiritEntity spirit = DruidContent.ANIMAL_SPIRIT.get().create(level);
        if (spirit == null) return null;
        spirit.setKind(kind);
        spirit.entityData.set(GREAT, stage >= 10);
        spirit.owner = owner.getUUID();
        spirit.stage = stage;
        spirit.home = core;
        Vec3 dir = owner.position().subtract(Vec3.atCenterOf(core)).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
        double y = core.getY() + (kind == Kind.HAWK ? 2.5 : 0.6);
        spirit.moveTo(core.getX() + 0.5 + dir.x * 1.4, y, core.getZ() + 0.5 + dir.z * 1.4,
                (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F, 0F);
        spirit.yHeadRot = spirit.getYRot();
        spirit.yBodyRot = spirit.getYRot();
        level.addFreshEntity(spirit);

        level.sendParticles(Vfx.endRod(), spirit.getX(), spirit.getY() + 0.6, spirit.getZ(), 40, 0.4, 0.6, 0.4, 0.08);
        level.sendParticles(dust(kind, 1.6F), spirit.getX(), spirit.getY() + 0.6, spirit.getZ(), 50, 0.6, 0.8, 0.6, 0.0);
        level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2F, 1.4F);
        level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(), kindSound(kind), SoundSource.NEUTRAL, 1.0F, 1.1F);
        return spirit;
    }

    private static SoundEvent kindSound(Kind kind) {
        return switch (kind) {
            case DEER -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            case WOLF -> SoundEvents.WOLF_HOWL;
            case HAWK -> SoundEvents.PARROT_AMBIENT;
        };
    }

    private static DustParticleOptions dust(Kind kind, float size) {
        int c = kind.rgb;
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), size);
    }

    // =====================================================================================
    //  Dados sincronizados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
        builder.define(GREAT, false);
        builder.define(FADE, 0);
    }

    public Kind kind() {
        return Kind.byIndex(entityData.get(KIND));
    }

    public void setKind(Kind kind) {
        entityData.set(KIND, kind.ordinal());
    }

    public boolean isGreat() {
        return entityData.get(GREAT);
    }

    /** Ticks desde que começou a se desfazer (0 = ainda inteiro). */
    public int fadeTicks() {
        return entityData.get(FADE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key) || GREAT.equals(key)) refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        Kind k = kind();
        return EntityDimensions.scalable(k.width, k.height).scale(isGreat() ? 1.5F : 1.0F);
    }

    // =====================================================================================
    //  Comportamento
    // =====================================================================================
    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (level().isClientSide) clientParticles();
    }

    private void clientParticles() {
        Kind k = kind();
        int fade = fadeTicks();
        if (random.nextInt(fade > 0 ? 1 : 3) == 0) {
            double w = getBbWidth() * 0.6;
            level().addParticle(dust(k, 0.8F), getX() + (random.nextDouble() - 0.5) * w, getY() + random.nextDouble() * getBbHeight(),
                    getZ() + (random.nextDouble() - 0.5) * w, 0, 0.02, 0);
        }
        if (random.nextInt(8) == 0) {
            level().addParticle(Vfx.endRod(), getX() + (random.nextDouble() - 0.5) * getBbWidth(),
                    getY() + random.nextDouble() * getBbHeight(), getZ() + (random.nextDouble() - 0.5) * getBbWidth(),
                    0, 0.01, 0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        phaseTicks++;
        ServerLevel sl = (ServerLevel) level();
        Player target = findTarget(sl);

        switch (phase) {
            case PHASE_EMERGE -> {
                // Sobe devagar saindo da árvore
                setDeltaMovement(0, 0.04, 0);
                if (phaseTicks >= EMERGE_TICKS) setPhase(target != null ? PHASE_APPROACH : PHASE_FADE);
            }
            case PHASE_APPROACH -> {
                if (target == null) {
                    setPhase(PHASE_FADE);
                    break;
                }
                Vec3 goal = goalNear(target);
                Vec3 to = goal.subtract(position());
                double dist = to.length();
                if (dist < 0.6 || phaseTicks > MAX_APPROACH_TICKS) {
                    setDeltaMovement(Vec3.ZERO);
                    setPhase(PHASE_STAY);
                    if (target instanceof ServerPlayer sp) arrive(sp);
                } else {
                    double speed = Math.min(isGreat() ? 0.38 : 0.32, 0.05 + dist * 0.12);
                    setDeltaMovement(to.scale(speed / dist));
                    face(to.x, to.z);
                }
            }
            case PHASE_STAY -> {
                if (target == null || phaseTicks > STAY_TICKS) {
                    setPhase(PHASE_FADE);
                    break;
                }
                // Fica perto do druida: o falcão circula no alto, os outros acompanham ao lado
                Vec3 goal;
                if (kind() == Kind.HAWK) {
                    orbit += 0.06;
                    goal = target.position().add(Math.cos(orbit) * 2.2, 2.4 + Math.sin(phaseTicks * 0.1) * 0.2, Math.sin(orbit) * 2.2);
                } else {
                    goal = goalNear(target);
                }
                Vec3 to = goal.subtract(position());
                setDeltaMovement(to.scale(0.15));
                if (kind() == Kind.HAWK) face(to.x, to.z);
                else face(target.getX() - getX(), target.getZ() - getZ());
            }
            case PHASE_FADE -> {
                // Volta flutuando para a copa da Árvore Coração enquanto se desfaz
                Vec3 drift = new Vec3(0, 0.015, 0);
                if (home != null) {
                    Vec3 to = Vec3.atCenterOf(home).add(0, 4, 0).subtract(position());
                    if (to.lengthSqr() > 1) drift = drift.add(to.normalize().scale(0.05));
                }
                setDeltaMovement(getDeltaMovement().scale(0.6).add(drift));
                entityData.set(FADE, Math.min(FADE_TICKS, phaseTicks));
                if (phaseTicks >= FADE_TICKS) {
                    sl.sendParticles(dust(kind(), 1.4F), getX(), getY() + getBbHeight() * 0.5, getZ(), 40, 0.4, 0.5, 0.4, 0.0);
                    sl.sendParticles(Vfx.endRod(), getX(), getY() + getBbHeight() * 0.5, getZ(), 18, 0.3, 0.4, 0.3, 0.05);
                    sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.6F);
                    discard();
                }
            }
            default -> setPhase(PHASE_FADE);
        }
    }

    private void setPhase(int p) {
        phase = p;
        phaseTicks = 0;
        if (p == PHASE_FADE) deliverFromAfar();
    }

    /**
     * O espírito não alcançou o druida (longe demais, outra dimensão, chunk descarregado...): a mensagem e a bênção
     * chegam mesmo assim, "levadas pelo vento", para o progresso nunca se perder.
     */
    private void deliverFromAfar() {
        if (delivered || owner == null || !(level() instanceof ServerLevel sl)) return;
        delivered = true;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return;
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.4F);
        DruidProgress.spiritSpeech(p, this, stage);
        DruidProgress.bless(p, stage, kind());
    }

    @Override
    public void onRemovedFromLevel() {
        if (!level().isClientSide && getRemovalReason() == RemovalReason.UNLOADED_TO_CHUNK) deliverFromAfar();
        super.onRemovedFromLevel();
    }

    /** O dono (se tiver) ou o jogador mais perto, enquanto estiverem no alcance. */
    @Nullable
    private Player findTarget(ServerLevel sl) {
        if (owner != null) {
            Player p = sl.getPlayerByUUID(owner);
            return p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 64 * 64 ? p : null;
        }
        Player p = sl.getNearestPlayer(this, 16.0);
        return p != null && !p.isSpectator() ? p : null;
    }

    /** Ponto ao lado do jogador, na altura dos pés (cervo/lobo) ou acima do ombro (falcão). */
    private Vec3 goalNear(Player target) {
        Vec3 side = position().subtract(target.position()).multiply(1, 0, 1);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        double d = isGreat() ? 3.0 : 2.2;
        double y = kind() == Kind.HAWK ? 2.0 : 0.05;
        return target.position().add(side.x * d, y, side.z * d);
    }

    private void face(double dx, double dz) {
        if (dx * dx + dz * dz < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float smooth = Mth.approachDegrees(getYRot(), yaw, 12F);
        setYRot(smooth);
        yBodyRot = smooth;
        yHeadRot = smooth;
    }

    /** Chegou ao druida: fala e abençoa. */
    private void arrive(ServerPlayer player) {
        ServerLevel sl = player.serverLevel();
        if (owner != null && owner.equals(player.getUUID())) {
            if (delivered) return;
            delivered = true;
        }
        Kind k = kind();
        sl.playSound(null, getX(), getY(), getZ(), kindSound(k), SoundSource.NEUTRAL, 1.0F, 1.0F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.8F, 1.2F);
        String greet = "message.isekai_mahou.ritual_druid.spirit.greet." + k.id();
        Announce.send(this, Component.translatable(greet), k.rgb, Announce.STYLE_SYSTEM);
        if (owner != null && owner.equals(player.getUUID())) {
            DruidProgress.spiritSpeech(player, this, stage);
            DruidProgress.bless(player, stage, k);
        } else {
            DruidProgress.wanderingSpiritSpeech(player, this);
        }
        Vec3 c = player.position().add(0, 1.0, 0);
        sl.sendParticles(dust(k, 1.2F), c.x, c.y, c.z, 30, 0.5, 0.7, 0.5, 0.0);
        sl.sendParticles(Vfx.happy(), c.x, c.y, c.z, 12, 0.6, 0.8, 0.6, 0.0);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && phase == PHASE_STAY && owner != null && owner.equals(sp.getUUID())
                && level().getGameTime() - lastRepeat > 40) {
            lastRepeat = level().getGameTime();
            DruidProgress.spiritSpeech(sp, this, stage);
            phaseTicks = Math.max(0, phaseTicks - 80); // fica mais um pouco
        }
        return InteractionResult.CONSUME;
    }

    // =====================================================================================
    //  Espírito: intocável, não salvo, não some sozinho
    // =====================================================================================
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        // Pelo ovo: um espírito errante de tipo aleatório
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) {
            setKind(Kind.byIndex(random.nextInt(Kind.values().length)));
        }
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("kind", kind().ordinal());
        tag.putBoolean("great", isGreat());
        tag.putInt("stage", stage);
        tag.putInt("phase", phase);
        if (owner != null) tag.putUUID("owner", owner);
        if (home != null) tag.put("home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setKind(Kind.byIndex(tag.getInt("kind")));
        entityData.set(GREAT, tag.getBoolean("great"));
        stage = tag.getInt("stage");
        phase = tag.getInt("phase");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            Kind k = kind();
            boolean moving = state.isMoving() || (k == Kind.HAWK && fadeTicks() == 0);
            return state.setAndContinue(moving ? k.move : k.idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
