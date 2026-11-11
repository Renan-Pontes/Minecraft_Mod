package com.renanpontes.isekaimahou.feature.guild.entity;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos NPCs da Guilda (GeckoLib, humanoides, não hostis).
 * <ul>
 *   <li>Fala: um balão sobre a cabeça (chave de tradução sincronizada + momento em que começou).</li>
 *   <li>"Casa": ficam perto do lugar onde nasceram (balcão da Guilda); voltam se forem empurrados.</li>
 *   <li>Os funcionários da Guilda ({@link #isStaff()}) são invulneráveis (exceto /kill e o vazio) e persistentes.</li>
 * </ul>
 */
public abstract class GuildNpcEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_SPEECH = SynchedEntityData.defineId(GuildNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SPEECH_AT = SynchedEntityData.defineId(GuildNpcEntity.class, EntityDataSerializers.INT);
    /** Duração do balão de fala (ticks). */
    public static final int SPEECH_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos home;
    private Player lookTarget;
    private int lookTicks;
    private int chatterCooldown = 200;

    protected GuildNpcEntity(EntityType<? extends GuildNpcEntity> type, Level level) {
        super(type, level);
        if (isStaff()) setPersistenceRequired();
    }

    /** Funcionário da Guilda: invulnerável, persistente e preso ao balcão. */
    protected abstract boolean isStaff();

    /** Prefixo das animações: "animation.&lt;id&gt;". */
    protected abstract String animationPrefix();

    /** Conversa (clique direito) no servidor. */
    protected abstract void onTalk(ServerPlayer player);

    /** Fala solta quando há alguém perto (chave de tradução) ou null. */
    protected abstract String ambientLine();

    /** Raio que o NPC pode se afastar da "casa". */
    protected int homeRadius() {
        return isStaff() ? 3 : 24;
    }

    /** Fica preso à "casa"? (Companheiro seguindo o dono não fica.) */
    protected boolean useHome() {
        return true;
    }

    /** Pode passear à toa? (Companheiro seguindo o dono não passeia.) */
    protected boolean canWander() {
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.7));
        // Funcionários quase não saem do lugar (passeiam raramente, sem deixar o balcão vazio).
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return canWander() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canWander() && super.canContinueToUse();
            }
        };
        stroll.setInterval(isStaff() ? 900 : 120);
        goalSelector.addGoal(6, stroll);
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPEECH, "");
        builder.define(DATA_SPEECH_AT, Integer.MIN_VALUE / 2);
    }

    // =====================================================================================
    //  Fala
    // =====================================================================================
    /** Mostra um balão de fala. {@code arg} é texto literal, ou uma chave de tradução se começar com '#'. */
    public void say(String key, String arg) {
        entityData.set(DATA_SPEECH, arg == null || arg.isEmpty() ? key : key + "\n" + arg);
        entityData.set(DATA_SPEECH_AT, (int) level().getGameTime());
        chatterCooldown = 300 + random.nextInt(400);
    }

    public void say(String key) {
        say(key, null);
    }

    /** Ticks desde o início da fala atual (pode ser grande se não houver fala). */
    public float speechAge(float partialTick) {
        return (float) ((int) level().getGameTime() - entityData.get(DATA_SPEECH_AT)) + partialTick;
    }

    public boolean isSpeaking() {
        return !entityData.get(DATA_SPEECH).isEmpty() && speechAge(0) < SPEECH_TICKS;
    }

    /** Texto da fala atual (ou null). */
    public Component speech() {
        if (!isSpeaking()) return null;
        String raw = entityData.get(DATA_SPEECH);
        int nl = raw.indexOf('\n');
        if (nl < 0) return Component.translatable(raw);
        String arg = raw.substring(nl + 1);
        Object a = arg.startsWith("#") ? Component.translatable(arg.substring(1)) : arg;
        return Component.translatable(raw.substring(0, nl), a);
    }

    /** Vira para o jogador por alguns segundos (conversa). */
    public void faceFor(Player player, int ticks) {
        lookTarget = player;
        lookTicks = ticks;
        getNavigation().stop();
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (home == null) home = blockPosition();
        if (!useHome()) {
            if (hasRestriction()) clearRestriction();
        } else if (!hasRestriction() || !getRestrictCenter().equals(home) || getRestrictRadius() != homeRadius()) {
            restrictTo(home, homeRadius());
        }
        if (lookTicks > 0) {
            lookTicks--;
            if (lookTarget != null && lookTarget.isAlive() && distanceToSqr(lookTarget) < 100) {
                getLookControl().setLookAt(lookTarget, 30.0F, 30.0F);
                getNavigation().stop();
            } else {
                lookTicks = 0;
            }
        }
        if (--chatterCooldown <= 0) {
            chatterCooldown = 400 + random.nextInt(600);
            Player near = level().getNearestPlayer(this, 7.0);
            String line = near != null && !near.isSpectator() ? ambientLine() : null;
            if (line != null && !isSpeaking()) say(line);
        }
    }

    /** Muda a "casa" (ex.: NPC colocado por estrutura ou movido por comando). */
    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, homeRadius());
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.NAME_TAG)) return super.mobInteract(player, hand);
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            faceFor(player, 80);
            onTalk(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // =====================================================================================
    //  Funcionários: invulneráveis e permanentes
    // =====================================================================================
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isStaff() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(source);
    }

    /** Funcionários não pegam fogo (ficariam em chamas para sempre, já que não levam dano). */
    @Override
    public boolean fireImmune() {
        return isStaff() || super.fireImmune();
    }

    /** Funcionários não podem ser levados na guia (a guia brigaria com a "casa" no balcão). */
    @Override
    public boolean canBeLeashed() {
        return !isStaff() && super.canBeLeashed();
    }

    /** Funcionários não são empurrados para fora do balcão por jogadores e mobs. */
    @Override
    public boolean isPushable() {
        return !isStaff() && super.isPushable();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return isStaff() || super.requiresCustomPersistence();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("GuildHome", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "GuildHome").ifPresent(this::setHome);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    protected RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop(animationPrefix() + "." + name);
    }

    protected RawAnimation once(String name) {
        return RawAnimation.begin().thenPlay(animationPrefix() + "." + name);
    }

    /** Animações disparáveis do controlador "action" (ex.: acenar). */
    protected void addActions(AnimationController<GuildNpcEntity> action) {
        action.triggerableAnim("wave", once("wave"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = loop("idle"), walk = loop("walk"), talk = loop("talk");
        controllers.add(new AnimationController<GuildNpcEntity>(this, "main", 5, state -> {
            if (state.isMoving()) return state.setAndContinue(walk);
            if (isSpeaking()) return state.setAndContinue(talk);
            return state.setAndContinue(idle);
        }));
        AnimationController<GuildNpcEntity> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        addActions(action);
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static String key(String path) {
        return "dialogue." + IsekaiMahou.MODID + "." + path;
    }
}
