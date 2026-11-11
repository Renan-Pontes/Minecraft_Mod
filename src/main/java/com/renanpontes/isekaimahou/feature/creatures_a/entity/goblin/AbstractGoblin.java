package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base de todos os goblins: andam em bando (seguem o líder de maior patente), avisam o bando quando um é atacado,
 * fogem com pouca vida (e voltam depois de se recuperar), roubam itens do chão (devolvidos ao morrer) e atacam
 * jogadores e aldeões. Não se machucam entre si.
 */
public abstract class AbstractGoblin extends Monster implements GeoEntity, InventoryCarrier {
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(AbstractGoblin.class, EntityDataSerializers.BYTE);
    private static final int STATE_FLEEING = 1;
    private static final int STATE_CASTING = 2;

    protected static final RawAnimation IDLE = loop("idle");
    protected static final RawAnimation WALK = loop("walk");
    protected static final RawAnimation RUN = loop("run");
    protected static final RawAnimation AIM = loop("aim");
    protected static final RawAnimation CAST_LOOP = loop("cast_loop");
    protected static final RawAnimation ATTACK = once("attack");
    protected static final RawAnimation CAST = once("cast");
    protected static final RawAnimation BLOCK = once("block");
    protected static final RawAnimation STEAL = once("steal");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer inventory = new SimpleContainer(4);
    private boolean gearReady;

    protected AbstractGoblin(EntityType<? extends AbstractGoblin> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    protected static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.goblin." + name);
    }

    protected static RawAnimation once(String name) {
        return RawAnimation.begin().thenPlay("animation.goblin." + name);
    }

    public static AttributeSupplier.Builder baseAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 28.0);
    }

    // ------------------------------------------------------------------ Papel no bando

    /** Patente no bando: quem tem patente maior lidera (1 goblin comum, 2 hobgoblin, 3 rei). */
    public int rank() {
        return 1;
    }

    public boolean canFlee() {
        return true;
    }

    public boolean canSteal() {
        return true;
    }

    /** Equipamento inicial (armas/escudos). */
    protected abstract void equipGear(RandomSource random, DifficultyInstance difficulty);

    /** Metas de combate próprias (prioridades 2–4). */
    protected abstract void addCombatGoals();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GoblinGoals.FleeWhenWeak(this));
        addCombatGoals();
        goalSelector.addGoal(5, new GoblinGoals.StealItems(this));
        goalSelector.addGoal(6, new GoblinGoals.FollowLeader(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, AbstractGoblin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    // ------------------------------------------------------------------ Estado sincronizado

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
    }

    private void setFlag(int flag, boolean on) {
        byte b = entityData.get(DATA_STATE);
        entityData.set(DATA_STATE, (byte) (on ? b | flag : b & ~flag));
    }

    public boolean isFleeing() {
        return (entityData.get(DATA_STATE) & STATE_FLEEING) != 0;
    }

    public void setFleeing(boolean on) {
        setFlag(STATE_FLEEING, on);
    }

    public boolean isCasting() {
        return (entityData.get(DATA_STATE) & STATE_CASTING) != 0;
    }

    public void setCasting(boolean on) {
        setFlag(STATE_CASTING, on);
    }

    // ------------------------------------------------------------------ Bando / dano

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof AbstractGoblin || super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof AbstractGoblin) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Fogo amigo (flechas/magias de outro goblin) não machuca.
        if (source.getEntity() instanceof AbstractGoblin && source.getEntity() != this) return false;
        boolean ok = super.hurt(source, amount);
        if (ok && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof AbstractGoblin)) {
            alertPack(attacker);
        }
        return ok;
    }

    /** Chama o bando todo para cima de quem atacou. */
    protected void alertPack(LivingEntity attacker) {
        if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (AbstractGoblin g : level().getEntitiesOfClass(AbstractGoblin.class, getBoundingBox().inflate(16.0),
                g -> g != this && g.isAlive() && g.getTarget() == null)) {
            g.setTarget(attacker);
        }
    }

    // ------------------------------------------------------------------ Itens roubados

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return canSteal() && !stack.isEmpty() && inventory.canAddItem(stack);
    }

    /** Pega um item do chão (chamado pela meta de roubo). */
    public void stealItem(ItemEntity item) {
        int before = item.getItem().getCount();
        InventoryCarrier.pickUpItem(this, this, item);
        if (item.isRemoved() || item.getItem().getCount() < before) {
            playSound(SoundEvents.PIGLIN_ADMIRING_ITEM, 0.8F, getVoicePitch());
            triggerAnim("action", "steal");
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : inventory.removeAllItems()) spawnAtLocation(stack);
    }

    @Override
    public void checkDespawn() {
        super.checkDespawn();
        // Sumiu por distância (ou Pacífico) carregando algo roubado: larga tudo onde estava, para o item não sumir junto.
        // (Não deixamos o goblin persistente por isso: goblins que pegam qualquer tralha nunca sumiriam e lotariam o mundo.)
        if (isRemoved() && !level().isClientSide && !inventory.isEmpty()) {
            for (ItemStack stack : inventory.removeAllItems()) spawnAtLocation(stack);
        }
    }

    // ------------------------------------------------------------------ Nascimento / tick

    /**
     * O Monster vanilla recusa nascer em lugar claro (valor de caminhada pela luz). Goblins andam de dia também:
     * a luz/horário fica nas regras de spawn do módulo (CaSpawns), que já limitam bem os de dia.
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        equipGear(level.getRandom(), difficulty);
        gearReady = true;
        return result;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Goblins colocados por estrutura (sem finalizeSpawn) também recebem armas.
        if (!gearReady) {
            gearReady = true;
            if (getMainHandItem().isEmpty()) equipGear(random, level().getCurrentDifficultyAt(blockPosition()));
        }
        // Fugindo: recupera o fôlego aos poucos, para voltar à briga depois.
        if (isFleeing() && tickCount % 30 == 0) heal(1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeInventoryToTag(tag, registryAccess());
        tag.putBoolean("GearReady", gearReady);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readInventoryFromTag(tag, registryAccess());
        gearReady = tag.getBoolean("GearReady");
    }

    // ------------------------------------------------------------------ Sons

    /** Tom de voz (goblins pequenos têm voz fina). */
    protected float voiceBase() {
        return 1.5F;
    }

    @Override
    public float getVoicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.1F + voiceBase();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (isFleeing()) return SoundEvents.PIGLIN_RETREAT;
        return isAggressive() ? SoundEvents.PIGLIN_ANGRY : SoundEvents.PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.PIGLIN_STEP, 0.12F, 1.3F);
    }

    // ------------------------------------------------------------------ Ataque corpo a corpo (animação)

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", meleeAnim());
        return super.doHurtTarget(target);
    }

    /** Nome da animação de ataque corpo a corpo. */
    protected String meleeAnim() {
        return "attack";
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, this::movePredicate));
        controllers.add(new AnimationController<>(this, "arms", 4, this::armsPredicate));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("cast", CAST)
                .triggerableAnim("block", BLOCK)
                .triggerableAnim("steal", STEAL));
        addExtraControllers(controllers);
    }

    /** Animações a mais de subclasses (rei, montaria). */
    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    protected PlayState movePredicate(AnimationState<AbstractGoblin> state) {
        if (state.isMoving()) return state.setAndContinue(isFleeing() || isAggressive() ? RUN : WALK);
        return state.setAndContinue(IDLE);
    }

    protected PlayState armsPredicate(AnimationState<AbstractGoblin> state) {
        if (isCasting()) return state.setAndContinue(CAST_LOOP);
        if (isUsingItem()) return state.setAndContinue(AIM);
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
