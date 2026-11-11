package com.renanpontes.isekaimahou.feature.settlements.entity;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsNames;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos NPCs das cidades governadas por jogadores (construtor, morador, guarda, mercador, miliciano): modelo
 * GeckoLib único com peças por papel/estilo, balão de fala, ligação com a cidade ({@code cityId} + casa), sinal de
 * vida para a cidade saber quem ainda existe e as regras de GUERRA (docs/V10_DESIGN.md §3): moradores de uma cidade
 * inimiga do jogador FOGEM e não conversam/negociam; guardas e milicianos LUTAM ({@link Allegiance#isEnemyOf}).
 */
public abstract class SettlementNpc extends PathfinderMob implements GeoEntity, Reputation.FactionMember {
    private static final EntityDataAccessor<String> DATA_STYLE = SynchedEntityData.defineId(SettlementNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(SettlementNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(SettlementNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Component> DATA_SPEECH = SynchedEntityData.defineId(SettlementNpc.class, EntityDataSerializers.COMPONENT);
    public static final int SPEECH_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected String cityId = "";
    @Nullable protected BlockPos home;
    /** Facção (cidade conquistada = a do reino; cidade própria = sem facção). */
    protected String faction = Reputation.NO_FACTION;
    private boolean named;
    private int speechStart = -1000;
    private int chatterCooldown = 400;
    private int heartbeat;
    private int warnCooldown;

    protected SettlementNpc(EntityType<? extends SettlementNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    public abstract Settlement.Role role();

    /** Peças do modelo visíveis (as outras ficam escondidas). */
    public abstract Set<String> visibleBones();

    protected int variantCount() {
        return 2;
    }

    /** Raio de passeio em volta da casa. */
    protected int wanderRadius() {
        return 24;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STYLE, SettlementStyle.KINGDOM.id);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_WORKING, false);
        builder.define(DATA_SPEECH, Component.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SPEECH.equals(key)) speechStart = tickCount;
    }

    public SettlementStyle style() {
        SettlementStyle s = SettlementStyle.byId(entityData.get(DATA_STYLE));
        return s == null ? SettlementStyle.KINGDOM : s;
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    public boolean working() {
        return entityData.get(DATA_WORKING);
    }

    public void setWorking(boolean on) {
        if (working() != on) entityData.set(DATA_WORKING, on);
    }

    /** Nome da textura: {@code <papel>_<estilo>_<variante>}. */
    public String textureName() {
        return role().id() + "_" + style().id + "_" + Math.floorMod(variant(), variantCount());
    }

    public String cityId() {
        return cityId;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Liga o NPC a uma cidade (chamado por quem o traz ao mundo). */
    public void bind(Settlement city, BlockPos homePos, String factionId) {
        cityId = city.id;
        faction = factionId;
        entityData.set(DATA_STYLE, city.style.id);
        entityData.set(DATA_VARIANT, random.nextInt(variantCount()));
        setHome(homePos);
        giveName();
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, wanderRadius());
    }

    private void giveName() {
        if (named) return;
        named = true;
        boolean female = variant() % 2 == 1;
        setCustomName(Component.translatable("settlements.isekai_mahou.npc.named",
                Component.literal(SettlementsNames.pick(style(), female, random)), getType().getDescription()));
        setCustomNameVisible(false);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (!named) {
            entityData.set(DATA_VARIANT, random.nextInt(variantCount()));
            entityData.set(DATA_STYLE, SettlementStyle.values()[random.nextInt(SettlementStyle.values().length)].id);
        }
        if (home == null) setHome(blockPosition());
        return out;
    }

    @Override
    @Nullable
    public String factionId() {
        return faction;
    }

    @Nullable
    public Settlement settlement() {
        if (cityId.isEmpty() || !(level() instanceof ServerLevel sl)) return null;
        return SettlementService.city(sl.getServer(), cityId);
    }

    // =====================================================================================
    //  Fala
    // =====================================================================================
    public void say(Component text) {
        entityData.set(DATA_SPEECH, text);
        speechStart = tickCount;
    }

    public float speechAge(float partialTick) {
        return tickCount - speechStart + partialTick;
    }

    @Nullable
    public Component speech() {
        Component c = entityData.get(DATA_SPEECH);
        return c.getString().isEmpty() || tickCount - speechStart > SPEECH_TICKS ? null : c;
    }

    protected MutableComponent line(String group, int count) {
        return SettlementsLang.tr("line." + group + "." + random.nextInt(count));
    }

    /** Fala solta perto do jogador (null = calado). */
    @Nullable
    protected Component ambientLine(Player near) {
        return line(role().id() + ".ambient", 4);
    }

    // =====================================================================================
    //  Guerra e lealdade
    // =====================================================================================
    /** O jogador está em guerra com esta cidade? */
    public boolean isEnemy(@Nullable Entity e) {
        return e instanceof ServerPlayer sp && !cityId.isEmpty() && !sp.isCreative() && !sp.isSpectator()
                && Allegiance.isEnemyOf(sp, cityId);
    }

    @Nullable
    protected ServerPlayer nearestEnemy(double range) {
        if (!(level() instanceof ServerLevel sl) || cityId.isEmpty()) return null;
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : sl.players()) {
            double d = p.distanceToSqr(this);
            if (d < bestD && isEnemy(p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof SettlementNpc n && n.cityId.equals(cityId)) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof SettlementNpc n && n.cityId.equals(cityId)) return false;
        return super.canAttack(target);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.004F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (home == null) setHome(blockPosition());
        if (++heartbeat >= 100) {
            heartbeat = 0;
            if (!cityId.isEmpty() && !SettlementService.heartbeat(sl, this)) {
                // a cidade deixou de existir: o NPC vai embora
                discard();
                return;
            }
        }
        if (warnCooldown > 0) warnCooldown--;
        if (--chatterCooldown <= 0) {
            chatterCooldown = 600 + random.nextInt(800);
            Player near = level().getNearestPlayer(this, 7.0);
            if (near != null && !near.isSpectator() && getTarget() == null && speech() == null) {
                Component l = isEnemy(near) ? line("war", 4) : ambientLine(near);
                if (l != null) say(l);
            }
        }
    }

    // =====================================================================================
    //  Interação
    // =====================================================================================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(true);
        if (isEnemy(sp)) {
            if (warnCooldown <= 0) {
                warnCooldown = 40;
                say(line("refuse", 3));
                sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.refuse",
                        Allegiance.cityName(sp.server, cityId)).withColor(0xE8735E), true);
                playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            }
            return InteractionResult.CONSUME;
        }
        getLookControl().setLookAt(sp, 30.0F, 30.0F);
        getNavigation().stop();
        return interact(sp);
    }

    /** O que acontece ao clicar (não inimigo). Padrão: conversa por Dialogue. */
    protected InteractionResult interact(ServerPlayer sp) {
        Dialogue.show(sp, "settlements.npc", getDisplayName(), style().color, List.of(
                Dialogue.Page.of(line(role().id() + ".talk", 4)),
                Dialogue.Page.of(statusLine())));
        triggerAnim("action", "wave");
        return InteractionResult.CONSUME;
    }

    /** Segunda página da conversa: algo sobre a cidade. */
    protected Component statusLine() {
        Settlement s = settlement();
        if (s == null) return line("generic", 3);
        return SettlementService.npcStatusLine(this, s);
    }

    /** Foge de uma posição (usado pela IA de pânico de guerra). */
    protected boolean fleeFrom(Vec3 from, double speed) {
        Vec3 away = DefaultRandomPos.getPosAway(this, 16, 6, from);
        return away != null && getNavigation().moveTo(away.x, away.y, away.z, speed);
    }

    // =====================================================================================
    //  Regras gerais
    // =====================================================================================
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && !cityId.isEmpty()) SettlementService.onNpcDeath(sl, this, source);
        super.die(source);
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SettlementCity", cityId);
        if (home != null) tag.put("SettlementHome", NbtUtils.writeBlockPos(home));
        tag.putString("SettlementFaction", faction);
        tag.putString("SettlementStyle", entityData.get(DATA_STYLE));
        tag.putInt("SettlementVariant", variant());
        tag.putBoolean("SettlementNamed", named);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cityId = tag.getString("SettlementCity");
        NbtUtils.readBlockPos(tag, "SettlementHome").ifPresent(this::setHome);
        faction = tag.getString("SettlementFaction");
        if (tag.contains("SettlementStyle")) entityData.set(DATA_STYLE, tag.getString("SettlementStyle"));
        entityData.set(DATA_VARIANT, tag.getInt("SettlementVariant"));
        named = tag.getBoolean("SettlementNamed");
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    protected static final String ANIM = "animation.settlement_npc.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(ANIM + "run");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop(ANIM + "work");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop(ANIM + "talk");
    private static final RawAnimation GUARD = RawAnimation.begin().thenLoop(ANIM + "guard");

    /** Animação parada (guardas ficam em posição de sentido). */
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<SettlementNpc>(this, "main", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.012 ? RUN : WALK);
            }
            if (working()) return state.setAndContinue(WORK);
            if (speech() != null) return state.setAndContinue(TALK);
            return state.setAndContinue(idleAnimation());
        }));
        AnimationController<SettlementNpc> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : new String[] {"wave", "attack", "cheer", "bow"}) {
            action.triggerableAnim(a, RawAnimation.begin().thenPlay(ANIM + a));
        }
        controllers.add(action);
    }

    protected static RawAnimation guardIdle() {
        return GUARD;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Objetivos comuns
    // =====================================================================================
    /** Civis: correm de jogadores inimigos (guerra) e gritam por ajuda. */
    protected static class FleeEnemiesGoal extends Goal {
        private final SettlementNpc npc;
        @Nullable private ServerPlayer threat;
        private int cooldown;

        public FleeEnemiesGoal(SettlementNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--cooldown > 0) return false;
            cooldown = 10;
            threat = npc.nearestEnemy(12.0);
            return threat != null;
        }

        @Override
        public void start() {
            if (threat != null) {
                npc.fleeFrom(threat.position(), 1.35);
                if (npc.speech() == null) npc.say(npc.line("flee", 3));
            }
        }

        @Override
        public boolean canContinueToUse() {
            return threat != null && threat.isAlive() && !npc.getNavigation().isDone() && npc.distanceToSqr(threat) < 20 * 20;
        }
    }
}
