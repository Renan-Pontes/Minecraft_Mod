package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.legends_f.necro.ShadowArmy;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Soldado das Sombras (homenagem a Solo Leveling): a sombra de uma criatura derrotada, erguida pelo "Levante-se!".
 * Tem o tamanho e a silhueta da criatura original (o cliente desenha o modelo dela escurecido, com olhos roxos), a
 * força dela escalada pelo nível do dono e uma patente (Soldado, Cavaleiro, Elite, Marechal). Luta pelo dono, segue
 * ordens do Comando do Monarca e, se cair, volta para a sombra dele para se refazer. É uma MANIFESTAÇÃO: não é salva
 * (o exército fica guardado no dono, ver {@link ShadowArmy}).
 */
public class ShadowSoldierEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_W = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_H = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GRADE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RISE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MONARCH = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Nomes das patentes (tradução {@code entity.isekai_mahou.shadow_soldier.grade_N}). */
    public static final int GRADES = 4;
    private static final float[] GRADE_MULT = {1.0F, 1.15F, 1.3F, 1.5F};
    private static final String[] GRADE_KEYS = {"entity.isekai_mahou.shadow_soldier.grade_0", "entity.isekai_mahou.shadow_soldier.grade_1",
            "entity.isekai_mahou.shadow_soldier.grade_2", "entity.isekai_mahou.shadow_soldier.grade_3"};

    @Nullable
    private UUID owner;
    private int rosterId = -1;
    private float attack = 3F;
    private int riseLength = 1;
    @Nullable
    private Vec3 guard;
    private int noOwnerTicks;

    public ShadowSoldierEntity(EntityType<? extends ShadowSoldierEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    // =====================================================================================
    //  Configuração
    // =====================================================================================

    /** Monta o soldado a partir da entrada do exército (servidor, antes de entrar no mundo). */
    public void setup(ServerPlayer sp, CompoundTag e, boolean monarch) {
        owner = sp.getUUID();
        entityData.set(DATA_OWNER, Optional.of(owner));
        rosterId = e.getInt("id");
        String type = e.getString("type");
        entityData.set(DATA_TYPE, type);
        entityData.set(DATA_W, Mth.clamp(e.getFloat("w"), 0.35F, 3.6F));
        entityData.set(DATA_H, Mth.clamp(e.getFloat("h"), 0.35F, 5.0F));
        int grade = Mth.clamp(e.getInt("grade"), 0, GRADES - 1);
        entityData.set(DATA_GRADE, grade);
        entityData.set(DATA_MONARCH, monarch);
        refreshDimensions();
        float gm = GRADE_MULT[grade];
        int level = MagicApi.get(sp).level();
        float hpBase = Mth.clamp(e.getFloat("hp") * 0.55F * gm, 8F, 320F);
        float growth = 1F + level / 100F;
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(hpBase * growth);
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(2.0 + grade * 3.0);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.3 + grade * 0.015);
        setHealth(getMaxHealth());
        attack = Math.max(2F, e.getFloat("atk")) * 1.25F * gm * growth;
        Component kind = e.contains("name") ? Component.literal(e.getString("name")) : typeName(type);
        setCustomName(Component.translatable("entity.isekai_mahou.shadow_soldier.named",
                Component.translatable(GRADE_KEYS[grade]), kind));
        setCustomNameVisible(false);
    }

    private static Component typeName(String type) {
        ResourceLocation rl = ResourceLocation.tryParse(type);
        Optional<EntityType<?>> t = rl == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
        return t.<Component>map(EntityType::getDescription).orElse(Component.translatable("entity.isekai_mahou.shadow_soldier"));
    }

    /** Encontra um lugar livre perto de {@code at} para o tamanho deste soldado. */
    public void placeFree(ServerLevel level, Vec3 at) {
        if (level.noCollision(this)) return;
        for (int r = 1; r <= 3; r++) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                Vec3 p = at.add(Math.cos(a) * r, 0.5, Math.sin(a) * r);
                setPos(p.x, p.y, p.z);
                if (level.noCollision(this)) return;
            }
        }
        setPos(at.x, at.y + 0.5, at.z);
    }

    public void setRising(int ticks) {
        riseLength = Math.max(1, ticks);
        entityData.set(DATA_RISE, ticks);
    }

    public void setMonarch(boolean on) {
        entityData.set(DATA_MONARCH, on);
    }

    public void setGuardHere(boolean hold) {
        guard = hold ? position() : null;
        if (hold) {
            getNavigation().stop();
            setTarget(null);
        }
    }

    public int rosterId() { return rosterId; }

    // ---------------- Lidos pelo cliente ----------------
    public String sourceType() { return entityData.get(DATA_TYPE); }

    public int grade() { return entityData.get(DATA_GRADE); }

    public boolean monarch() { return entityData.get(DATA_MONARCH); }

    /** Ticks que faltam para terminar de sair do chão. */
    public int rising() { return entityData.get(DATA_RISE); }

    /** 0..1 do quanto já saiu do chão (o cliente desenha afundado no começo). */
    public float riseProgress(float pt) {
        int r = rising();
        if (r <= 0) return 1F;
        return Mth.clamp(1F - (r - pt) / Math.max(12F, riseLength), 0F, 1F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, "minecraft:zombie");
        builder.define(DATA_W, 0.6F);
        builder.define(DATA_H, 1.95F);
        builder.define(DATA_GRADE, 0);
        builder.define(DATA_RISE, 0);
        builder.define(DATA_MONARCH, false);
        builder.define(DATA_OWNER, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_W || key == DATA_H) refreshDimensions();
        if (key == DATA_RISE && level().isClientSide && entityData.get(DATA_RISE) > riseLength) riseLength = entityData.get(DATA_RISE);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.scalable(entityData.get(DATA_W), entityData.get(DATA_H));
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return level().isClientSide ? entityData.get(DATA_OWNER).orElse(null) : owner;
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (isFriend(e)) return true;
        return super.isAlliedTo(e);
    }

    /** Amigo do dono: ele, as criaturas dele, o grupo dele e (PvP desligado) outros jogadores. */
    public boolean isFriend(@Nullable Entity e) {
        UUID id = getOwnerUUID();
        if (e == null || id == null) return false;
        if (id.equals(e.getUUID())) return true;
        if (e instanceof OwnableEntity oe && id.equals(oe.getOwnerUUID())) return true;
        LivingEntity o = getOwner();
        if (o != null) {
            Boolean social = Party.allyOverride(o, e);
            if (social != null) return social;
        }
        return e instanceof Player && level().getServer() != null && !level().getServer().isPvpAllowed();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isFriend(target) && super.canAttack(target);
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(4, new FollowOwnerGoal());
        goalSelector.addGoal(5, new GuardGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new ArmyTargetGoal());
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::wantsToHunt));
    }

    private boolean wantsToHunt(LivingEntity e) {
        if (!(e instanceof Enemy) || e instanceof Creeper || isFriend(e) || guard != null) return false;
        if (e instanceof NeutralMob && !(e instanceof Mob m && m.getTarget() != null && isFriend(m.getTarget()))) return false;
        LivingEntity o = getOwner();
        return o != null && o.distanceToSqr(e) < 16 * 16;
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || rising() > 0;
    }

    @Override
    public void tick() {
        super.tick();
        int r = entityData.get(DATA_RISE);
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (r > 0) entityData.set(DATA_RISE, r - 1);
        ServerLevel sl = (ServerLevel) level();
        if (owner == null || rosterId < 0) {
            discard();
            return;
        }
        LivingEntity o = getOwner();
        if (o == null) {
            if (++noOwnerTicks > 40) vanish(true);
            return;
        }
        noOwnerTicks = 0;
        LivingEntity t = getTarget();
        if (t != null && (isFriend(t) || !t.isAlive() || distanceToSqr(t) > 40 * 40)) setTarget(null);
        if (guard == null && getTarget() == null && distanceToSqr(o) > 26 * 26 && o.onGround()) {
            Vec3 back = o.position().subtract(o.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
            ariseSmoke(sl);
            teleportTo(back.x, o.getY(), back.z);
            getNavigation().stop();
            resetFallDistance();
            ariseSmoke(sl);
        }
        if (tickCount % 10 == 0 && monarch()) {
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.1, getZ(), 1, getBbWidth() * 0.3, 0, getBbWidth() * 0.3, 0.01);
        }
    }

    private void clientFx() {
        if (random.nextInt(3) != 0) return;
        float w = getBbWidth() * 0.5F, h = getBbHeight();
        float rise = riseProgress(0);
        DustParticleOptions dust = new DustParticleOptions(monarch() ? new Vector3f(0.55F, 0.3F, 1F) : new Vector3f(0.3F, 0.1F, 0.55F), 1.2F);
        level().addParticle(dust, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * h * rise,
                getZ() + (random.nextDouble() - 0.5) * w * 2, 0, 0.02, 0);
        if (rise < 1F || random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + 0.1,
                    getZ() + (random.nextDouble() - 0.5) * w * 2, 0, 0.03, 0);
        }
    }

    private void ariseSmoke(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 10,
                getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
        sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2, getZ(), 3, getBbWidth() * 0.4, 0.1, getBbWidth() * 0.4, 0.02);
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(level() instanceof ServerLevel sl)) return false;
        LivingEntity o = getOwner();
        float dmg = attack * (monarch() ? 1.5F : 1F);
        DamageSource src = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MOB_ATTACK), this, o != null ? o : this);
        boolean wasAlive = target.isAlive();
        boolean ok = target.hurt(src, dmg);
        if (ok) {
            setLastHurtMob(target);
            // O dano conta para o dono (XP e saque), mas a vítima revida contra o soldado
            if (target instanceof LivingEntity le && le.isAlive()) {
                le.setLastHurtByMob(this);
                le.knockback(0.35 + grade() * 0.1, Math.sin(Math.toRadians(getYRot())), -Math.cos(Math.toRadians(getYRot())));
            }
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.4F, 0.15F, 0.8F), 1.0F), target.getX(),
                    target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.25, 0.25, 0.25, 0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SKELETON_STEP, SoundSource.HOSTILE, 0.6F, 0.6F);
            if (wasAlive && !target.isAlive() && o instanceof ServerPlayer sp) ShadowArmy.addKill(sp, rosterId);
        }
        return ok;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (!level().isClientSide && src != null && src != this && isFriend(src)) return false;
        if (rising() > 0) return false;
        return super.hurt(source, monarch() ? amount * 0.6F : amount);
    }

    /** Não morre de verdade: volta para a sombra do dono e se refaz em 60 s. */
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            ariseSmoke(sl);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
            LivingEntity o = getOwner();
            ShadowArmy.onDown(this, o instanceof ServerPlayer sp ? sp : null);
        }
        discard();
    }

    /** Volta para a sombra (sem perder o lugar no exército). */
    public void vanish(boolean fx) {
        if (fx && level() instanceof ServerLevel sl) {
            ariseSmoke(sl);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_BLOCK_BREAK, SoundSource.HOSTILE, 0.7F, 0.6F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide) ShadowArmy.forget(this);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    public boolean shouldDropExperience() { return false; }

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    // =====================================================================================
    //  Objetivos
    // =====================================================================================

    /** Alvo do exército: foco do Comando, quem feriu o dono, quem o dono feriu. */
    private class ArmyTargetGoal extends TargetGoal {
        private LivingEntity candidate;
        private int cooldown;

        ArmyTargetGoal() {
            super(ShadowSoldierEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (--cooldown > 0 || !(level() instanceof ServerLevel sl) || owner == null) return false;
            cooldown = 10;
            LivingEntity o = getOwner();
            if (o == null) return false;
            candidate = ShadowArmy.focusTarget(sl, owner);
            if (candidate == null && guard == null) {
                LivingEntity hurtBy = o.getLastHurtByMob();
                if (hurtBy != null && o.tickCount - o.getLastHurtByMobTimestamp() < 200) candidate = hurtBy;
            }
            if (candidate == null && guard == null) candidate = ShadowArmy.lastAttacked(sl, owner, 200);
            return candidate != null && candidate != getTarget() && candidate != ShadowSoldierEntity.this && !isFriend(candidate)
                    && canAttack(candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            super.start();
        }
    }

    /** Sem alvo, acompanha o dono. */
    private class FollowOwnerGoal extends Goal {
        private LivingEntity o;
        private int recalc;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (guard != null) return false;
            o = getOwner();
            return o != null && getTarget() == null && distanceToSqr(o) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            return o != null && o.isAlive() && guard == null && getTarget() == null && distanceToSqr(o) > 2.5 * 2.5
                    && !getNavigation().isDone();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(o, 10F, getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                getNavigation().moveTo(o, 1.15);
            }
        }

        @Override
        public void stop() {
            o = null;
            getNavigation().stop();
        }
    }

    /** "Guardem posição": volta para o ponto marcado quando não está lutando. */
    private class GuardGoal extends Goal {
        GuardGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return guard != null && getTarget() == null && distanceToSqr(guard) > 2.0 * 2.0;
        }

        @Override
        public void start() {
            if (guard != null) getNavigation().moveTo(guard.x, guard.y, guard.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return guard != null && getTarget() == null && !getNavigation().isDone();
        }
    }
}
