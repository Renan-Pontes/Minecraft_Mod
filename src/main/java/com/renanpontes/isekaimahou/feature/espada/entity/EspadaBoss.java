package com.renanpontes.isekaimahou.feature.espada.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.BossThemed;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos três Espada (Ulquiorra, Barragan, Grimmjow). Cuida do que é comum:
 * <ul>
 *   <li><b>parado</b> até um jogador chegar perto (ou bater) → cutscene "boss_intro" + fala; 3 s invulnerável;
 *       vida/dano ajustados ao grupo ({@link BossKit#scaleToParty}); arena selada ({@link BossKit#sealArena});</li>
 *   <li>fases por porcentagem de vida; a LIBERAÇÃO (Resurrección) é um estado próprio: {@link #startTransform}
 *       deixa o chefe invulnerável por alguns ticks, troca a {@link #form() forma} (sincronizada: modelo, textura e
 *       música mudam no cliente) e só então volta a lutar;</li>
 *   <li>música tema ({@link BossThemed}) pela forma sincronizada;</li>
 *   <li>volta ao normal (vida cheia, forma selada) se todos forem embora; anti-trapaça (alvo inalcançável →
 *       Sonído para perto ou salto marcado);</li>
 *   <li>morte lenta própria de cada um, recompensa de grupo + título ({@link Reputation#grantTitle}).</li>
 * </ul>
 */
public abstract class EspadaBoss extends Monster implements GeoEntity, BossThemed {
    private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TRANSFORM = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.BOOLEAN);
    /** Forma de destino da liberação em andamento (o cliente escolhe a animação por ela). */
    private static final EntityDataAccessor<Integer> DATA_TRANSFORM_TO = SynchedEntityData.defineId(EspadaBoss.class, EntityDataSerializers.INT);
    /** Tag das invocações (Fracción): limite e limpeza na morte/reset; nunca são alvo dos Espada. */
    public static final String MINION_TAG = "isekai_mahou_espada_minion";
    /**
     * persistentData (int): quem invoca pode pedir que o Espada já nasça numa forma liberada (ex.: o Palácio de Hueco
     * Mundo converte um Rei Vazio antigo em Barragan já em Arrogante). Lido e apagado no primeiro tick do servidor.
     */
    public static final String START_FORM_KEY = "isekai_mahou.espada.start_form";

    /**
     * Gancho de cliente: chamado a cada tick do chefe NO CLIENTE (aura, cinzas da morte, escuridão da Segunda Etapa).
     * A parte de cliente do módulo preenche; no servidor dedicado fica vazio.
     */
    public static volatile Consumer<EspadaBoss> clientTickHook = b -> {};

    protected static final int INTRO_TICKS = 60;
    private static final double BAR_RANGE = 56.0;
    private static final double REWARD_RANGE = 56.0;
    private static final double PARTY_RANGE = 40.0;
    private static final int CHEESE_TICKS = 40;
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> introSeen = new HashSet<>();
    private final Set<String> hintsShown = new HashSet<>();
    private ServerBossEvent bar;
    private int calmTicks;
    private int strafeDir = 1;
    @Nullable
    private BlockPos spawnHome;
    private int unreachableTicks;
    private int cheeseCd;
    protected int leapTicks;
    /** Forma de destino do {@link #startTransform} em andamento. */
    private int pendingForm = -1;
    private int transformTotal;
    private long ccImmuneUntil;
    private int quoteCd = 200;

    protected EspadaBoss(EntityType<? extends EspadaBoss> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder bossAttributes(double health, double armor, double speed, double damage) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.FOLLOW_RANGE, 56.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.25)
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    // ------------------------------------------------------------------ O que cada Espada define

    /** Id do chefe (= id do tipo de entidade; traduções, contador boss.<id>, animações). */
    public abstract String bossId();

    public abstract int bossColor();

    protected abstract BossEvent.BossBarColor barColor();

    /** Porcentagens de vida (decrescentes) em que o chefe muda de fase. */
    protected abstract float[] phaseThresholds();

    protected abstract void tickCombat(ServerLevel level, @Nullable LivingEntity target);

    protected abstract void dropBossLoot(ServerLevel level, DamageSource source);

    /** Animações disparáveis no controlador "action". */
    protected abstract List<String> actionAnimations();

    /** Textura atual (sem pasta/extensão), ex.: "ulquiorra_murcielago". A glowmask segue o mesmo nome. */
    public abstract String textureName();

    /** Modelo GeckoLib atual (geo/entity/&lt;nome&gt;.geo.json). Uma forma pode trocar de modelo (Arrogante). */
    public String modelName() {
        return bossId();
    }

    /** Arquivo de animações do modelo atual (animations/entity/&lt;nome&gt;.animation.json; os nomes são sempre animation.&lt;bossId&gt;.*). */
    public String animationName() {
        return modelName();
    }

    /** Forma inicial pedida por quem invocou ({@link #START_FORM_KEY}): aplica sem a cena de liberação. */
    protected void applyStartForm(ServerLevel level, int form) {
        setForm(form);
    }

    /** Ossos que o renderer liga/desliga conforme a forma. */
    public List<String> toggledBones() {
        return List.of();
    }

    public boolean isBoneVisible(String bone) {
        return true;
    }

    /** Ticks da morte (animação + efeito). */
    protected int deathTicks() {
        return 60;
    }

    protected int rewardXp() {
        return 450;
    }

    protected double arenaRadius() {
        return 30.0;
    }

    protected double wakeRange() {
        return 18.0;
    }

    protected void onIntroStart(ServerLevel level) {}

    protected void onIntroEnd(ServerLevel level) {}

    protected void introTick(ServerLevel level, int t) {}

    protected void onReset(ServerLevel level) {}

    protected void onPhaseChange(ServerLevel level, int phase) {
        Cutscenes.playNear(this, 48, "boss_phase", getDisplayName(),
                Component.translatable(langKey("boss", "phase" + phase)), bossColor(), 60, null);
        announce("phase" + phase);
    }

    /** Liberação concluída (a forma nova já está valendo). */
    protected void onTransformDone(ServerLevel level, int form) {}

    /** Cada tick da liberação ({@code t} = ticks passados). */
    protected void transformTick(ServerLevel level, int t, int total, int form) {}

    protected boolean canChangePhase() {
        return !isTransforming();
    }

    /** Ataque em preparo/execução (o anti-trapaça espera ele terminar). */
    protected boolean isBusy() {
        return false;
    }

    /** Chaves de falas curtas sorteadas durante a luta ({@code quote.isekai_mahou.<id>.<chave>}). */
    protected String[] combatQuotes() {
        return new String[0];
    }

    // ------------------------------------------------------------------ Estado

    public boolean isAwake() {
        return entityData.get(DATA_AWAKE);
    }

    public int introTicks() {
        return entityData.get(DATA_INTRO);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    /** Forma sincronizada (0 = selada; 1, 2... = liberações). */
    public int form() {
        return entityData.get(DATA_FORM);
    }

    protected void setForm(int form) {
        entityData.set(DATA_FORM, form);
    }

    /** Fase direta (forma inicial pedida por quem invoca): sem as cenas de mudança de fase. */
    protected void setPhase(int phase) {
        entityData.set(DATA_PHASE, Math.max(0, phase));
    }

    /** Ticks restantes da liberação em andamento (0 = não está liberando). */
    public int transformTicks() {
        return entityData.get(DATA_TRANSFORM);
    }

    public boolean isTransforming() {
        return transformTicks() > 0;
    }

    /** Para qual forma a liberação em andamento leva (sincronizado). */
    public int transformTarget() {
        return entityData.get(DATA_TRANSFORM_TO);
    }

    public boolean isFlying() {
        return entityData.get(DATA_FLYING);
    }

    protected void setFlying(boolean flying) {
        if (flying == isFlying()) return;
        entityData.set(DATA_FLYING, flying);
        setNoGravity(flying);
        if (!flying) setDeltaMovement(getDeltaMovement().multiply(1, 0.3, 1));
    }

    @Nullable
    protected BlockPos home() {
        return spawnHome;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_INTRO, 0);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_AWAKE, false);
        builder.define(DATA_FORM, 0);
        builder.define(DATA_TRANSFORM, 0);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_TRANSFORM_TO, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // A forma pode mudar o tamanho do corpo (Arrogante = rei de ossos maior).
        if (DATA_FORM.equals(key)) refreshDimensions();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, EspadaBoss.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> isAwake()));
    }

    protected ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(false);
        }
        return bar;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (!isDeadOrDying()) serverBossTick(sl);
        } else {
            clientTickHook.accept(this);
        }
    }

    private void serverBossTick(ServerLevel sl) {
        if (spawnHome == null) spawnHome = blockPosition();
        if (getPersistentData().contains(START_FORM_KEY)) {
            int f = getPersistentData().getInt(START_FORM_KEY);
            getPersistentData().remove(START_FORM_KEY);
            if (f > 0 && form() == 0) applyStartForm(sl, f);
        }
        if (tickCount % 20 == 1 && BossKit.arenaCenter(this) == null) {
            BossKit.setArena(this, Vec3.atBottomCenterOf(spawnHome), Mth.clamp(arenaRadius(), BossArena.MIN_RADIUS, BossArena.MAX_RADIUS));
        }
        if (isPassenger()) stopRiding();
        bar().setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        int intro = introTicks();
        if (intro > 0) {
            entityData.set(DATA_INTRO, intro - 1);
            Player near = sl.getNearestPlayer(this, 40);
            if (near != null) faceTowards(near);
            introTick(sl, INTRO_TICKS - intro);
            if (intro - 1 == 0) onIntroEnd(sl);
            return;
        }
        int tr = transformTicks();
        if (tr > 0) {
            entityData.set(DATA_TRANSFORM, tr - 1);
            getNavigation().stop();
            Player near = sl.getNearestPlayer(this, 40);
            if (near != null) faceTowards(near);
            transformTick(sl, transformTotal - tr, transformTotal, pendingForm);
            if (tr - 1 == 0) {
                int f = pendingForm;
                pendingForm = -1;
                setForm(f);
                bar().setName(getDisplayName());
                onTransformDone(sl, f);
            }
        }
        if (isAwake()) checkPhase(sl);
        if (tickCount % 10 == 0) {
            updateBarPlayers(sl);
            if (!isAwake()) {
                tryWake(sl);
            } else {
                greetLateComers(sl);
                checkCalm(sl);
                if (tickCount % 20 == 0) keepInArena(sl);
            }
        }
    }

    /** Começa a liberação: invulnerável por {@code ticks}, depois a forma vira {@code toForm}. */
    protected void startTransform(ServerLevel sl, int toForm, int ticks) {
        Telegraph.cancelAll(this);
        pendingForm = toForm;
        transformTotal = ticks;
        entityData.set(DATA_TRANSFORM_TO, toForm);
        entityData.set(DATA_TRANSFORM, ticks);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        action("release");
    }

    protected void faceTowards(Entity e) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void tryWake(ServerLevel sl) {
        if (sl.getDifficulty() == Difficulty.PEACEFUL) return;
        boolean trial = BossKit.isTrialBoss(this);
        double range = trial ? Math.max(40.0, BossKit.arenaRadius(this) + 8.0) : wakeRange();
        for (ServerPlayer p : sl.players()) {
            if (validPlayer(p) && p.distanceToSqr(this) < range * range && (trial || hasLineOfSight(p))) {
                startIntro(sl);
                return;
            }
        }
    }

    protected static boolean validPlayer(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    protected void startIntro(ServerLevel sl) {
        if (isAwake()) return;
        entityData.set(DATA_AWAKE, true);
        entityData.set(DATA_INTRO, INTRO_TICKS);
        calmTicks = 0;
        getNavigation().stop();
        BossKit.scaleToParty(this, PARTY_RANGE);
        BossKit.sealArena(this);
        Cutscenes.playNear(this, 48, "boss_intro", getDisplayName(), subtitle(), bossColor(), INTRO_TICKS + 20, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) introSeen.add(p.getUUID());
        updateBarPlayers(sl);
        announce("intro");
        onIntroStart(sl);
    }

    private Component subtitle() {
        return Component.translatable(langKey("boss", "subtitle"));
    }

    private void greetLateComers(ServerLevel sl) {
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 24 * 24 && !p.isSpectator())) {
            if (!introSeen.contains(p.getUUID()) && hasLineOfSight(p)) {
                introSeen.add(p.getUUID());
                Cutscenes.play(p, "boss_intro", this, getDisplayName(), subtitle(), bossColor(), 60, false);
            }
        }
    }

    private void checkCalm(ServerLevel sl) {
        if (BossKit.isTrialBoss(this)) {
            calmTicks = 0;
            return;
        }
        boolean anyone = getTarget() != null;
        if (!anyone) {
            for (ServerPlayer p : sl.players()) {
                if (validPlayer(p) && p.distanceToSqr(this) < 40 * 40) {
                    anyone = true;
                    break;
                }
            }
        }
        calmTicks = anyone ? 0 : calmTicks + 10;
        if (calmTicks >= 600) resetBoss(sl);
    }

    private void resetBoss(ServerLevel sl) {
        calmTicks = 0;
        Telegraph.cancelAll(this);
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(BossKit.PARTY_HEALTH_ID);
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.removeModifier(BossKit.PARTY_DAMAGE_ID);
        getPersistentData().remove(BossKit.PARTY_KEY);
        setHealth(getMaxHealth());
        entityData.set(DATA_PHASE, 0);
        entityData.set(DATA_AWAKE, false);
        entityData.set(DATA_TRANSFORM, 0);
        pendingForm = -1;
        setForm(0);
        setFlying(false);
        introSeen.clear();
        hintsShown.clear();
        leapTicks = 0;
        setTarget(null);
        getNavigation().stop();
        bar().removeAllPlayers();
        bar().setName(getDisplayName());
        dismissMinions(sl);
        onReset(sl);
        returnHome(sl);
        BossArena.release(this);
    }

    @Nullable
    protected BlockPos resetPos() {
        Vec3 arena = BossKit.arenaCenter(this);
        return arena != null ? BlockPos.containing(arena) : spawnHome;
    }

    private void keepInArena(ServerLevel sl) {
        Vec3 c = BossKit.arenaCenter(this);
        double r = BossKit.arenaRadius(this);
        if (c == null || r <= 0) return;
        double dx = getX() - c.x, dz = getZ() - c.z;
        if (dx * dx + dz * dz <= (r + 3.0) * (r + 3.0) && getY() > c.y - 12 && getY() < c.y + 26) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        Vec3 to = findGroundSpot(sl, c, 0.0, Math.max(1.0, r * 0.4), null);
        if (to == null) to = c;
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }

    protected void returnHome(ServerLevel sl) {
        BlockPos home = resetPos();
        if (home == null) return;
        Vec3 to = Vec3.atBottomCenterOf(home);
        if (to.distanceToSqr(position()) < 9.0) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
    }

    protected void dismissMinions(ServerLevel sl) {
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(48), e -> e.getTags().contains(MINION_TAG) && e.isAlive())) {
            sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            e.discard();
        }
    }

    private void updateBarPlayers(ServerLevel sl) {
        ServerBossEvent b = bar();
        for (ServerPlayer p : List.copyOf(b.getPlayers())) {
            if (!isAwake() || p.level() != sl || p.distanceToSqr(this) > BAR_RANGE * BAR_RANGE) b.removePlayer(p);
        }
        if (!isAwake()) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE) b.addPlayer(p);
        }
    }

    private void checkPhase(ServerLevel sl) {
        if (!canChangePhase()) return;
        float[] th = phaseThresholds();
        float frac = getHealth() / getMaxHealth();
        if (phase() < th.length && frac <= th[phase()]) {
            entityData.set(DATA_PHASE, phase() + 1);
            onPhaseChange(sl, phase());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl) || !isAwake() || introTicks() > 0 || isTransforming()) return;
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || (target instanceof Player p && !validPlayer(p)) || isMinion(target))) {
            setTarget(null);
            target = null;
        }
        if (leapTicks > 0) {
            leapTicks--;
            getNavigation().stop();
            return;
        }
        if (target != null) {
            watchForCheese(sl, target);
            if (quoteCd > 0) quoteCd--;
            if (quoteCd <= 0 && !isBusy()) {
                quoteCd = 360 + random.nextInt(300);
                String[] q = combatQuotes();
                if (q.length > 0) quote(q[random.nextInt(q.length)]);
            }
        }
        tickCombat(sl, target);
    }

    // ------------------------------------------------------------------ Anti-trapaça

    private void watchForCheese(ServerLevel sl, LivingEntity target) {
        if (cheeseCd > 0) cheeseCd--;
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < CHEESE_TICKS || cheeseCd > 0 || isBusy()) return;
        unreachableTicks = 0;
        cheeseCd = 80;
        onUnreachable(sl, target);
    }

    /** Padrão dos Espada: Sonído para perto (teleporte seguro); sem lugar, salto marcado. */
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        Vec3 from = position();
        if (BossKit.blinkNear(this, target, 3.5)) {
            getNavigation().stop();
            EspadaFx.sonido(sl, this, from, position(), bossColor());
        } else if (onGround() && distanceTo(target) < 22) {
            leapTicks = BossKit.leapAt(this, target, 1.1);
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || introTicks() > 0 || isTransforming();
    }

    // ------------------------------------------------------------------ Dano e morte

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity attacker = source.getEntity();
            if (attacker != null && attacker != this && (attacker instanceof EspadaBoss || isMinion(attacker))) return false;
            if (introTicks() > 0 || isTransforming()) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.6, getZ(),
                        8, 0.4, 0.4, 0.4, 0.1);
                return false;
            }
            if (!isAwake()) {
                if (attacker instanceof Player p && !p.isSpectator() && level().getDifficulty() != Difficulty.PEACEFUL) {
                    startIntro((ServerLevel) level());
                }
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    /** Controle dura metade num Espada; depois de um atordoar/congelar, 4 s de resistência. */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        boolean hardCc = effect.is(CoreRegistries.STUNNED) || effect.is(CoreRegistries.FROZEN);
        if (hardCc || effect.is(CoreRegistries.ROOTED)) {
            if (hardCc && !level().isClientSide) {
                long now = level().getGameTime();
                if (!hasEffect(effect.getEffect()) && now < ccImmuneUntil) return false;
            }
            effect = new MobEffectInstance(effect.getEffect(), Math.max(10, effect.getDuration() / 2), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon());
            if (hardCc && !level().isClientSide) ccImmuneUntil = level().getGameTime() + effect.getDuration() + 80;
        }
        return super.addEffect(effect, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level() instanceof ServerLevel sl) onDefeated(sl);
    }

    protected void onDefeated(ServerLevel sl) {
        Telegraph.cancelAll(this);
        setFlying(false);
        List<ServerPlayer> winners = BossRewards.grant(this, rewardXp(), REWARD_RANGE);
        for (ServerPlayer p : winners) Reputation.grantTitle(p, "title.isekai_mahou.espada." + bossId());
        announce("death");
        bar().removeAllPlayers();
        dismissMinions(sl);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropBossLoot(level, source);
    }

    /** Cada tick da morte no servidor (o efeito de cliente vem pelo {@link #clientTickHook}). */
    protected void deathTick(ServerLevel sl, int t) {
        if (t % 4 == 0) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.01);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            deathTick(sl, deathTime);
            if (deathTime >= deathTicks() && !isRemoved()) {
                sl.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (bar != null) bar.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bar != null) bar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bar != null) bar.setName(getDisplayName());
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    // ------------------------------------------------------------------ Teste (comandos de operador)

    /** {@code /mahou espada phase}: acorda já (sem a cena) para os testes. */
    public void debugWake(ServerLevel sl) {
        if (isAwake()) return;
        startIntro(sl);
        entityData.set(DATA_INTRO, 1);
    }

    /** Porcentagem de vida em que a fase {@code n} (1..) começa, ou -1. */
    public float debugPhaseThreshold(int n) {
        float[] th = phaseThresholds();
        return n >= 1 && n <= th.length ? th[n - 1] : -1F;
    }

    /** {@code /mahou espada cast <golpe>}: força um golpe pelo nome; devolve false se o nome não existe nesta forma. */
    public boolean debugCast(ServerLevel sl, String attack, @Nullable LivingEntity target) {
        return false;
    }

    /** Nomes aceitos por {@link #debugCast} (sugestões do comando). */
    public List<String> debugAttacks() {
        return List.of();
    }

    // ------------------------------------------------------------------ Ajudas

    public void announce(String key) {
        Announce.send(this, Component.translatable(langKey("announce", key)), bossColor(), Announce.STYLE_BOSS);
    }

    /** v1.0: até quando o chefe fica calado depois de uma provocação (uma fala de cada vez). */
    private long tauntSilentUntil;

    /** v1.0: um jogador caiu pelas mãos do Espada — provocação curta, no máximo uma a cada 15 s. */
    public void onPlayerSlain() {
        long now = level().getGameTime();
        if (level().isClientSide || isDeadOrDying() || !isAwake() || introTicks() > 0 || now < tauntSilentUntil) return;
        tauntSilentUntil = now + 300;
        quote("taunt");
    }

    /** Fala curta (estoica/arrogante) no anúncio de chefe. */
    public void quote(String key) {
        Announce.send(this, Component.translatable("quote.isekai_mahou." + bossId() + "." + key), bossColor(), Announce.STYLE_BOSS);
    }

    protected String langKey(String group, String name) {
        return group + ".isekai_mahou." + bossId() + "." + name;
    }

    /** Nome do golpe na Pilha / HUD. */
    protected Component attackName(String attack) {
        return Component.translatable("attack.isekai_mahou." + bossId() + "." + attack);
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected float scaled(float base) {
        return base * BossKit.damageScale(this);
    }

    public static boolean isMinion(Entity e) {
        return e.getTags().contains(MINION_TAG);
    }

    public boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || e instanceof EspadaBoss || isMinion(e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    protected void holdYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        getLookControl().setLookAt(getX() + dir.x * 6, getEyeY(), getZ() + dir.z * 6);
    }

    protected float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * (180.0 / Math.PI)) - 90.0F;
    }

    /** Fim de uma linha saindo de {@code from} na direção {@code dir}, parando antes de paredes. */
    protected Vec3 lineEnd(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        Vec3 to = from.add(dir.scale(max));
        HitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? max : Math.max(1.5, hit.getLocation().distanceTo(from) - 0.2);
        return from.add(dir.scale(len));
    }

    /** Chão embaixo de um ponto (até 12 blocos abaixo). */
    protected Vec3 groundAt(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        for (int i = 0; i < 13; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return new Vec3(pos.x, p.getY(), pos.z);
            p = below;
        }
        return pos;
    }

    protected void hintOnce(ServerLevel sl, String key) {
        if (!hintsShown.add(key)) return;
        Component msg = Component.translatable(langKey("message", key)).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : BossKit.partyAround(this, 40)) p.displayClientMessage(msg, true);
    }

    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    protected void kite(LivingEntity target, double min, double max, double speed) {
        double d = distanceTo(target);
        if (d > max || !hasLineOfSight(target)) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        } else if (d < min) {
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = position().add(away.scale(6));
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(to.x, to.y, to.z, speed * 1.15);
        } else {
            getNavigation().stop();
            if (tickCount % 50 == 0 || random.nextInt(80) == 0) strafeDir = -strafeDir;
            getMoveControl().strafe(0.0F, strafeDir * 0.45F);
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /**
     * Voo simples (formas aladas): acelera em direção a {@code dest}, freando perto; sem caminho (voa por cima de
     * tudo), sem passar do teto da arena.
     */
    protected void flyTo(Vec3 dest, double speed) {
        Vec3 d = dest.subtract(position());
        double len = d.length();
        Vec3 want = len < 0.3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
        Vec3 v = getDeltaMovement().scale(0.8).add(want.scale(0.2 * 5));
        if (v.length() > speed) v = v.normalize().scale(speed);
        setDeltaMovement(v);
        hasImpulse = true;
    }

    /** Lugar livre no chão a [min, max] blocos de {@code center}; com {@code seenFrom}, à vista dali. */
    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, @Nullable Vec3 seenFrom) {
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            BlockPos base = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 3, center.z + Math.sin(a) * r);
            for (int dy = 0; dy < 8; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)
                        && sl.noCollision(getType().getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
                    Vec3 spot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    if (seenFrom == null || canSee(sl, seenFrom, spot.add(0, 0.6, 0))) return spot;
                    break;
                }
            }
        }
        return null;
    }

    protected boolean canSee(ServerLevel sl, Vec3 from, Vec3 to) {
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Inimigos cujo corpo encosta no segmento {@code a → b} (feixes em 3D, inclusive no ar). */
    protected List<LivingEntity> foesAlong(ServerLevel sl, Vec3 a, Vec3 b, double radius) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(a, b).inflate(radius + 1.0);
        Vec3 d = b.subtract(a);
        double len2 = Math.max(1.0E-6, d.lengthSqr());
        return sl.getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (!isFoe(e)) return false;
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double t = Mth.clamp(c.subtract(a).dot(d) / len2, 0.0, 1.0);
            Vec3 p = a.add(d.scale(t));
            double r = radius + e.getBbWidth() * 0.5;
            double dy = Math.abs(c.y - p.y) - e.getBbHeight() * 0.5;
            double dxz = Math.hypot(c.x - p.x, c.z - p.z);
            return dxz <= r && dy <= r;
        });
    }

    /** Inimigos a até {@code radius} de {@code center} (esfera). */
    protected List<LivingEntity> foesNear(ServerLevel sl, Vec3 center, double radius) {
        return sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> isFoe(e) && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius);
    }

    /** Empurra para longe de {@code from} (horizontal + {@code up}). */
    protected static void knock(LivingEntity e, Vec3 from, double strength, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
        double k = strength * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6);
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * k, up, d.z * k));
        e.hurtMarked = true;
    }

    protected int countMinions(ServerLevel sl, double radius) {
        return sl.getEntities(this, getBoundingBox().inflate(radius), e -> isMinion(e) && e.isAlive()).size();
    }

    /** Solta itens de OUTRO módulo pelo id (ignora se não existir). */
    protected void dropById(String path, int min, int max) {
        int n = min + random.nextInt(Math.max(1, max - min + 1));
        if (n <= 0) return;
        BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).ifPresent(item -> spawnAtLocation(new ItemStack(item, n)));
    }

    protected void dropItem(Item item, int min, int max) {
        int n = min + random.nextInt(Math.max(1, max - min + 1));
        if (n > 0) spawnAtLocation(new ItemStack(item, n));
    }

    // ------------------------------------------------------------------ Animações

    private RawAnimation anim(String kind, String name) {
        String full = "animation." + bossId() + "." + name;
        return ANIMS.computeIfAbsent(kind + ":" + full, k -> switch (kind) {
            case "loop" -> RawAnimation.begin().thenLoop(full);
            case "hold" -> RawAnimation.begin().thenPlayAndHold(full);
            default -> RawAnimation.begin().thenPlay(full);
        });
    }

    protected RawAnimation loop(String name) {
        return anim("loop", name);
    }

    protected RawAnimation play(String name) {
        return anim("play", name);
    }

    protected RawAnimation hold(String name) {
        return anim("hold", name);
    }

    /** Animação de base (morte, apresentação, liberação, voo, andar, parado). */
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (isTransforming()) return loop("transform");
        if (!isAwake()) return loop("sleep");
        if (isFlying()) return loop("fly");
        return loop(moving ? "walk" : "idle");
    }

    private PlayState mainPredicate(AnimationState<EspadaBoss> state) {
        return state.setAndContinue(mainAnimation(state.isMoving()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<EspadaBoss> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : actionAnimations()) action.triggerableAnim(a, play(a));
        action.triggerableAnim("release", play("release"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag b = new CompoundTag();
        b.putBoolean("Awake", isAwake());
        b.putInt("Phase", phase());
        // Liberação interrompida pelo salvamento: grava já a forma final.
        b.putInt("Form", pendingForm >= 0 ? pendingForm : form());
        b.putBoolean("Flying", isFlying());
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        b.put("IntroSeen", seen);
        if (spawnHome != null) b.put("SpawnHome", NbtUtils.writeBlockPos(spawnHome));
        tag.put("Espada", b);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag b = tag.getCompound("Espada");
        entityData.set(DATA_AWAKE, b.getBoolean("Awake"));
        entityData.set(DATA_PHASE, b.getInt("Phase"));
        entityData.set(DATA_FORM, b.getInt("Form"));
        entityData.set(DATA_FLYING, b.getBoolean("Flying"));
        setNoGravity(b.getBoolean("Flying"));
        introSeen.clear();
        for (Tag t : b.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        spawnHome = NbtUtils.readBlockPos(b, "SpawnHome").orElse(null);
        if (bar != null) bar.setName(getDisplayName());
    }
}
