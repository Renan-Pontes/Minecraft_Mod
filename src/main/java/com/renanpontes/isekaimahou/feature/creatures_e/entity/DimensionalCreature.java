package com.renanpontes.isekaimahou.feature.creatures_e.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das criaturas das dimensoes (GeckoLib). Cuida de:
 * <ul>
 *   <li>golpes com preparacao ({@link CreatureMove}) escolhidos pelo {@link CreatureCombatGoal};</li>
 *   <li>modos de locomocao: chao, voo (estilo allay), anfibio (estilo afogado) e escalada (estilo aranha);</li>
 *   <li>faccoes (criaturas da mesma dimensao nao se ferem), animacoes "main" (idle/walk/fly/swim) e "action" (golpes).</li>
 * </ul>
 * Hostis implementam {@link Enemy}; as neutras/passivas nao.
 */
public abstract class DimensionalCreature extends PathfinderMob implements GeoEntity {
    /** Faccao: criaturas da mesma faccao sao aliadas. */
    public enum Faction { ELEMENTAL, ABYSS, CELESTIAL }

    private static final EntityDataAccessor<Integer> DATA_FOCUS = SynchedEntityData.defineId(DimensionalCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(DimensionalCreature.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(DimensionalCreature.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<String, RawAnimation> animCache = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private List<CreatureMove> moves;
    private CreatureMove current;
    private int currentTick;
    private int globalCooldown;
    private int actionStart;

    protected DimensionalCreature(EntityType<? extends DimensionalCreature> type, Level level) {
        super(type, level);
        if (flies()) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        } else if (swims()) {
            this.moveControl = new AmphibiousMoveControl(this);
            setPathfindingMalus(PathType.WATER, 0.0F);
        }
        this.xpReward = experience();
    }

    /** Atributos comuns (vida, armadura, dano, velocidade, alcance de perseguicao). */
    protected static AttributeSupplier.Builder baseAttributes(double health, double armor, double damage, double speed) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 36.0);
    }

    // =====================================================================================
    //  O que cada criatura define
    // =====================================================================================

    public abstract Faction faction();

    /** Golpes em ordem de prioridade (o primeiro disponivel e usado). Chamado uma vez por instancia. */
    protected abstract List<CreatureMove> createMoves();

    /** Animacoes de acao extras disparadas por codigo (ex.: renascer, refletir). */
    protected List<String> extraAnimations() {
        return List.of();
    }

    protected int experience() {
        return 20;
    }

    public boolean flies() {
        return false;
    }

    public boolean swims() {
        return false;
    }

    public boolean climbs() {
        return false;
    }

    /** Distancia (borda a borda) que a criatura tenta manter do alvo. 0 = corpo a corpo. */
    public double preferredRange() {
        return 0.5;
    }

    /** Voadoras: altura acima do alvo em que pairam durante o combate. */
    public double hoverHeight() {
        return 2.0;
    }

    public double chaseSpeed() {
        return 1.15;
    }

    /** Imune a veneno/definhar (seres elementais, golens). */
    protected boolean immuneToToxins() {
        return true;
    }

    protected boolean breathesUnderwater() {
        return swims();
    }

    protected boolean fallImmune() {
        return flies() || climbs();
    }

    /** Particulas ambientes (so no cliente). */
    protected void clientFx() {
    }

    /** Ajusta o dano recebido (resistencias nao-magicas: projeteis, explosoes...). */
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        return amount;
    }

    /** Chamado quando um ser vivo fere esta criatura. */
    protected void onHurtBy(LivingEntity attacker, DamageSource source, float amount) {
    }

    // =====================================================================================
    //  Locomocao
    // =====================================================================================

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (flies()) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            nav.setCanPassDoors(true);
            return nav;
        }
        if (swims()) return new AmphibiousPathNavigation(this, level);
        if (climbs()) return new WallClimberNavigation(this, level);
        return super.createNavigation(level);
    }

    @Override
    protected void registerGoals() {
        if (!flies() && !swims()) goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CreatureCombatGoal(this));
        if (flies()) goalSelector.addGoal(5, new SkyWanderGoal(this));
        else if (swims()) goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
        else goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        registerTargetGoals();
    }

    /** Alvos padrao: quem bater nela (e chama as do mesmo tipo); hostis tambem cacam jogadores. */
    protected void registerTargetGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        if (this instanceof Enemy) targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Voo suave como o do allay (sem gravidade, atrito no ar). Anfibias nadam como o afogado. */
    @Override
    public void travel(Vec3 input) {
        if (flies()) {
            if (isControlledByLocalInstance()) {
                boolean water = isInWater();
                moveRelative(water ? 0.02F : getSpeed(), input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(water ? 0.8 : 0.91));
            }
            calculateEntityAnimation(true);
            return;
        }
        if (swims() && isInWater() && isControlledByLocalInstance()) {
            moveRelative(0.02F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            calculateEntityAnimation(true);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean isPushedByFluid() {
        return !swims() && super.isPushedByFluid();
    }

    /** Anfibias podem nascer dentro da agua (o padrao recusa qualquer liquido na caixa). */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return swims() ? level.isUnobstructed(this) : super.checkSpawnObstruction(level);
    }

    @Override
    public boolean onClimbable() {
        return climbs() ? entityData.get(DATA_CLIMBING) : super.onClimbable();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !fallImmune() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!flies()) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return !breathesUnderwater() && super.canDrownInFluidType(type);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (immuneToToxins() && (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER))) return false;
        return super.canBeAffected(effect);
    }

    // =====================================================================================
    //  Faccao, dano e alvos
    // =====================================================================================

    public boolean isKin(Entity other) {
        return other instanceof DimensionalCreature d && d.faction() == faction();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return isKin(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isKin(target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != this && isKin(attacker)) return false;
        if (!level().isClientSide && !source.is(DamageTypes.GENERIC_KILL)) amount = modifyIncomingDamage(source, amount);
        if (amount <= 0) return false;
        boolean ok = super.hurt(source, amount);
        if (ok && attacker instanceof LivingEntity living && living != this && !level().isClientSide) onHurtBy(living, source, amount);
        return ok;
    }

    /** Alvo valido para golpes em area: o alvo atual, jogadores (fora do criativo) e quem ataca esta criatura. */
    public boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || isKin(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator() && (this instanceof Enemy || p == getTarget() || p == getLastHurtByMob());
        return e == getTarget() || (e instanceof Mob m && m.getTarget() == this);
    }

    public List<LivingEntity> foesNear(Vec3 c, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 2), e -> isFoe(e)
                && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(c) <= (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5));
    }

    /** Distancia de borda a borda ate o alvo. */
    public double gapTo(Entity t) {
        return Math.max(0, distanceTo(t) - (getBbWidth() + t.getBbWidth()) * 0.5);
    }

    /** Aplica dano considerando o status Exposto (o mesmo bonus que as magias recebem). */
    public boolean strike(LivingEntity target, float amount, DamageSource source) {
        MobEffectInstance exposed = target.getEffect(CoreRegistries.EXPOSED);
        if (exposed != null) amount *= 1F + 0.25F * (exposed.getAmplifier() + 1);
        return target.hurt(source, amount);
    }

    /** Dano magico (tipo "spell" do mod) causado por esta criatura. */
    public DamageSource magicSource(Entity direct) {
        return direct == null || direct == this ? damageSources().source(CoreRegistries.SPELL_DAMAGE, this)
                : damageSources().source(CoreRegistries.SPELL_DAMAGE, this, direct);
    }

    public float attackDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Golpe corpo a corpo: dano base x {@code mult}, empurra para longe com forca {@code knock}. */
    public boolean meleeHit(LivingEntity t, float mult, double knock) {
        boolean ok = strike(t, attackDamage() * mult, damageSources().mobAttack(this));
        if (ok) {
            if (knock > 0) t.knockback(knock, getX() - t.getX(), getZ() - t.getZ());
            setLastHurtMob(t);
        }
        return ok;
    }

    /** Ponto central do corpo do alvo. */
    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================

    public List<CreatureMove> moves() {
        if (moves == null) moves = createMoves();
        return moves;
    }

    protected CreatureMove current() {
        return current;
    }

    protected int globalCooldown() {
        return globalCooldown;
    }

    protected boolean ready(CreatureMove m) {
        return cooldowns.getOrDefault(m.name, 0) <= 0;
    }

    protected void beginMove(CreatureMove m, LivingEntity target) {
        current = m;
        currentTick = 0;
        actionStart = tickCount;
        cooldowns.put(m.name, m.cooldown);
        entityData.set(DATA_ACTION, (byte) (moves().indexOf(m) + 1));
        triggerAnim("action", m.name);
        if (m.start != null) m.start.run(target);
    }

    protected void tickMove(LivingEntity target) {
        if (current == null) return;
        CreatureMove m = current;
        currentTick++;
        if (currentTick == m.windup && m.perform != null) m.perform.run(target);
        if (m.during != null && current == m) m.during.tick(target, currentTick);
        if (current == m && currentTick >= m.length) endMove();
    }

    /** Encerra o golpe atual (ex.: alvo sumiu). */
    public void endMove() {
        if (current == null) return;
        current = null;
        globalCooldown = 8 + random.nextInt(8);
        entityData.set(DATA_ACTION, (byte) 0);
        setFocus(null);
    }

    /** Coloca um golpe em recarga (ex.: depois de uma reacao especial). */
    public void putOnCooldown(String move, int ticks) {
        cooldowns.put(move, ticks);
    }

    /** Nome do golpe em andamento (servidor e cliente), ou "". */
    public String actionName() {
        int idx = entityData.get(DATA_ACTION);
        List<CreatureMove> list = moves();
        return idx > 0 && idx <= list.size() ? list.get(idx - 1).name : "";
    }

    /** Ticks desde que o golpe atual comecou (servidor e cliente). */
    public int actionAge() {
        return tickCount - actionStart;
    }

    /** Entidade "em foco" sincronizada (feixes, curas), para os efeitos do cliente. */
    public void setFocus(Entity e) {
        entityData.set(DATA_FOCUS, e == null ? -1 : e.getId());
    }

    public Entity focus() {
        int id = entityData.get(DATA_FOCUS);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Dispara uma animacao de acao fora do sistema de golpes. */
    public void playAction(String name) {
        triggerAnim("action", name);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FOCUS, -1);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_CLIMBING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key) && level().isClientSide) actionStart = tickCount;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // "particulas reduzidas" (config de cliente do nucleo): ambientes so em ticks alternados
            if ((tickCount & 1) == 0 || !CeFx.reducedParticles()) clientFx();
        } else if (climbs()) {
            entityData.set(DATA_CLIMBING, horizontalCollision);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (!cooldowns.isEmpty()) cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
            if (globalCooldown > 0) globalCooldown--;
            if (current != null && (getTarget() == null || !getTarget().isAlive())) endMove();
        }
    }

    @Override
    public void die(DamageSource source) {
        endMove();
        super.die(source);
    }

    /** Efeito de morte (uma vez, no servidor). */
    protected void deathFx(ServerLevel level) {
    }

    @Override
    protected void tickDeath() {
        if (deathTime == 0 && level() instanceof ServerLevel sl) deathFx(sl);
        super.tickDeath();
    }

    /** Investida: lanca a criatura na direcao do alvo. */
    public void dashAt(LivingEntity t, double speed, double lift) {
        Vec3 dir = center(t).subtract(position().add(0, getBbHeight() * 0.5, 0)).normalize();
        setDeltaMovement(dir.x * speed, dir.y * speed + lift, dir.z * speed);
        hasImpulse = true;
    }

    /** Encostou no alvo (caixa de colisao levemente aumentada)? */
    public boolean touching(Entity t, double inflate) {
        return getBoundingBox().inflate(inflate).intersects(t.getBoundingBox());
    }

    @Override
    public SoundSource getSoundSource() {
        return this instanceof Enemy ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return this instanceof Enemy;
    }

    @Override
    public int getMaxHeadXRot() {
        return 50;
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    /** Animacao em loop do controlador principal. */
    protected String loopAnimation(boolean moving) {
        if (swims() && isInWater()) return "swim";
        if (flies()) return moving ? "fly" : "idle";
        return moving ? "walk" : "idle";
    }

    protected RawAnimation anim(String name, boolean loop) {
        return animCache.computeIfAbsent(name + (loop ? "#loop" : "#once"), k -> {
            String full = "animation." + BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath() + "." + name;
            return loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full);
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(anim(loopAnimation(state.isMoving()), true))));
        AnimationController<DimensionalCreature> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (CreatureMove m : moves()) action.triggerableAnim(m.name, anim(m.name, false));
        for (String extra : extraAnimations()) action.triggerableAnim(extra, anim(extra, false));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Nado anfibio (como o afogado): na agua segue o caminho em 3D; em terra anda normal
    // =====================================================================================

    static class AmphibiousMoveControl extends MoveControl {
        AmphibiousMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (mob.isInWater()) {
                LivingEntity t = mob.getTarget();
                if (t != null && t.getY() > mob.getY()) mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.004, 0));
                if (operation != MoveControl.Operation.MOVE_TO || mob.getNavigation().isDone()) {
                    mob.setSpeed(0.0F);
                    return;
                }
                double dx = wantedX - mob.getX();
                double dy = wantedY - mob.getY();
                double dz = wantedZ - mob.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1.0E-4) {
                    mob.setSpeed(0.0F);
                    return;
                }
                dy /= d;
                float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0F));
                mob.yBodyRot = mob.getYRot();
                float wanted = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 2.0F;
                float s = Mth.lerp(0.125F, mob.getSpeed(), wanted);
                mob.setSpeed(s);
                mob.setDeltaMovement(mob.getDeltaMovement().add(s * dx * 0.005, s * dy * 0.1, s * dz * 0.005));
            } else {
                if (!mob.onGround()) mob.setDeltaMovement(mob.getDeltaMovement().add(0, -0.008, 0));
                super.tick();
            }
        }
    }
}
