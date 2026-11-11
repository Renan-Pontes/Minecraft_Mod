package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

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
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * Base dos chefes do módulo (Falsa Hidra, Rei Mímico, Manticora). Cuida de tudo o que é comum:
 * <ul>
 *   <li><b>adormecido</b> até um jogador chegar perto (ou bater nele) → cutscene "boss_intro" + fala, 3 s invulnerável;
 *       na apresentação a vida/dano se ajustam ao grupo ({@link BossKit#scaleToParty});</li>
 *   <li>barra de chefe a até 48 blocos, fases por porcentagem de vida (cutscene "boss_phase" + fala);</li>
 *   <li>volta a dormir (vida cheia, escala desfeita) se todos forem embora — menos em modo provação;</li>
 *   <li>anti-trapaça: alvo inalcançável por 2 s → reação do chefe ({@link #onUnreachable}); não entra em barco;</li>
 *   <li>covil: o chefe colocado pela estrutura (NBT {@code Lair}) registra o lugar no {@link Places} (letreiro na
 *       entrada, destino de viagem);</li>
 *   <li>morte lenta, loot por id, recompensa de grupo ({@link BossRewards}: XP + {@code boss.<id>}).</li>
 *   <li><b>arena protegida</b> (v1.0, {@link BossArena}): sem arena gravada (covil, ovo ou comando), a casa vira a arena
 *       ({@link BossKit#setArena}, raio {@link #arenaRadius()}); ela é SELADA na intro (ninguém quebra/coloca blocos,
 *       explosões não mexem, o chefe não cai do mapa) e liberada quando o chefe volta a dormir.</li>
 * </ul>
 */
public abstract class GBossEntity extends Monster implements GeoEntity, BgBones {
    private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(GBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(GBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tag das invocações dos chefes do módulo (limite e limpeza na morte). */
    public static final String MINION_TAG = "isekai_mahou_bosses_g_minion";
    /** Tipo do lugar registrado pelos covis. */
    public static final String LAIR_PLACE_TYPE = "boss_lair";

    protected static final int INTRO_TICKS = 60;
    private static final int DEATH_TICKS = 50;
    private static final double BAR_RANGE = 48.0;
    private static final double REWARD_RANGE = 48.0;
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
    /** Id do covil (vem do NBT do molde da estrutura); vazio = invocado por ovo/comando. */
    private String lairId = "";
    private boolean lairRegistered;
    private int unreachableTicks;
    private int cheeseCd;
    protected int leapTicks;
    /** gameTime até quando Atordoado/Congelado novos não pegam (anti-trava em cadeia). */
    private long ccImmuneUntil;

    protected GBossEntity(EntityType<? extends GBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder bossAttributes(double health, double armor, double speed, double damage) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.25);
    }

    // ------------------------------------------------------------------ O que cada chefe define

    /** Id do chefe (= id do tipo de entidade; traduções e contador boss.<id>). */
    public abstract String bossId();

    public abstract int bossColor();

    protected abstract BossEvent.BossBarColor barColor();

    /** Porcentagens de vida (decrescentes) em que o chefe muda de fase. */
    protected abstract float[] phaseThresholds();

    protected abstract void tickCombat(ServerLevel level, @Nullable LivingEntity target);

    protected abstract void dropBossLoot(ServerLevel level, DamageSource source);

    /** Animações disparáveis no controlador "action". */
    protected abstract List<String> actionAnimations();

    /** XP mágico de grupo pela vitória. */
    protected int rewardXp() {
        return 320;
    }

    /** Raio da arena protegida em volta da casa (24–48; cabe a sala do covil inteira). */
    protected double arenaRadius() {
        return 26.0;
    }

    /** Distância em que um jogador (com visão) acorda o chefe. */
    protected double wakeRange() {
        return 16.0;
    }

    protected void onIntroStart(ServerLevel level) {}

    protected void onIntroEnd(ServerLevel level) {}

    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) BgFx.ring(level, position().add(0, 0.1, 0), 1.5 + t * 0.03, 24, BgFx.dust(bossColor(), 1.4F), 0.0);
    }

    protected void onReset(ServerLevel level) {}

    protected void onPhaseChange(ServerLevel level, int phase) {
        Cutscenes.playNear(this, 40, "boss_phase", getDisplayName(),
                Component.translatable(langKey("boss", "phase" + phase)), bossColor(), 50, null);
        announce("phase" + phase);
    }

    protected boolean canChangePhase() {
        return true;
    }

    protected void clientTick() {}

    /** Ataque em preparo/execução (o anti-trapaça espera ele terminar). */
    protected boolean isBusy() {
        return false;
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

    @Nullable
    protected BlockPos home() {
        return spawnHome;
    }

    /** Colocado pela estrutura do covil (e não por ovo/comando). */
    protected boolean hasLair() {
        return !lairId.isEmpty();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_INTRO, 0);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_AWAKE, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, GBossEntity.class, FalseHydraHead.class, VaultMimic.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> isAwake()));
    }

    protected ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            configureBar(bar);
        }
        return bar;
    }

    protected void configureBar(ServerBossEvent bossBar) {}

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (!isDeadOrDying()) serverBossTick(sl);
        } else {
            clientTick();
        }
    }

    private void serverBossTick(ServerLevel sl) {
        if (spawnHome == null) spawnHome = blockPosition();
        // A casa é a arena protegida da luta (trials/outros que gravaram a sua continuam valendo).
        if (tickCount % 20 == 1 && BossKit.arenaCenter(this) == null) {
            BossKit.setArena(this, Vec3.atBottomCenterOf(spawnHome), Mth.clamp(arenaRadius(), BossArena.MIN_RADIUS, BossArena.MAX_RADIUS));
        }
        if (!lairRegistered && !lairId.isEmpty()) registerLair(sl);
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

    /** Registra o covil no {@link Places} (uma vez; idempotente pelo id): letreiro de entrada e destino de viagem. */
    private void registerLair(ServerLevel sl) {
        lairRegistered = true;
        BlockPos c = spawnHome != null ? spawnHome : blockPosition();
        String id = lairId + "_" + c.getX() + "_" + c.getZ();
        BoundingBox box = new BoundingBox(c.getX() - 30, c.getY() - 18, c.getZ() - 30, c.getX() + 30, c.getY() + 40, c.getZ() + 30);
        String nameKey = "place.isekai_mahou." + lairId;
        Places.register(sl, new Places.Place(id, Component.translatable(nameKey), LAIR_PLACE_TYPE, "",
                sl.dimension(), box, c, false, bossColor()));
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
            if (validPlayer(p) && p.distanceToSqr(this) < range * range && (trial || canWakeSee(p))) {
                startIntro(sl);
                return;
            }
        }
    }

    /** Linha de visão para acordar (a Hidra enterrada sente pelo chão: sobrescreve). */
    protected boolean canWakeSee(Player p) {
        return hasLineOfSight(p);
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
        // Sela a arena já na apresentação: o cenário fica protegido antes do primeiro golpe.
        BossKit.sealArena(this);
        Cutscenes.playNear(this, 40, "boss_intro", getDisplayName(), subtitle(), bossColor(), INTRO_TICKS + 20, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 40 * 40)) introSeen.add(p.getUUID());
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
        unscaleParty();
        setHealth(getMaxHealth());
        entityData.set(DATA_PHASE, 0);
        entityData.set(DATA_AWAKE, false);
        introSeen.clear();
        hintsShown.clear();
        leapTicks = 0;
        setTarget(null);
        getNavigation().stop();
        bar().removeAllPlayers();
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

    private void unscaleParty() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(BossKit.PARTY_HEALTH_ID);
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.removeModifier(BossKit.PARTY_DAMAGE_ID);
        getPersistentData().remove(BossKit.PARTY_KEY);
    }

    private void keepInArena(ServerLevel sl) {
        Vec3 c = BossKit.arenaCenter(this);
        double r = BossKit.arenaRadius(this);
        if (c == null || r <= 0) return;
        double dx = getX() - c.x, dz = getZ() - c.z;
        // Voadores (Manticora) sobem ~7 blocos acima do alvo: só conta como "fora" caindo/subindo demais.
        if (dx * dx + dz * dz <= (r + 3.0) * (r + 3.0) && getY() > c.y - 12 && getY() < c.y + 24) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        Vec3 to = findGroundSpot(sl, c, 0.0, Math.max(1.0, r * 0.4), getType(), null);
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
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        sl.sendParticles(BgFx.dust(bossColor(), 1.4F), to.x, to.y + getBbHeight() / 2, to.z, 20, 0.4, 0.6, 0.4, 0);
    }

    protected void dismissMinions(ServerLevel sl) {
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(48), e -> e.getTags().contains(MINION_TAG) && e.isAlive())) {
            sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            // Mímico do cofre guarda o loot de um baú de verdade: volta a ser baú (nada se perde).
            if (e instanceof VaultMimic vm) {
                vm.revertToChest(sl);
            } else {
                e.discard();
            }
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
        while (phase() < th.length && frac <= th[phase()]) {
            entityData.set(DATA_PHASE, phase() + 1);
            onPhaseChange(sl, phase());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl) || !isAwake() || introTicks() > 0) return;
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || (target instanceof Player p && !validPlayer(p)))) {
            setTarget(null);
            target = null;
        }
        if (leapTicks > 0) {
            leapTicks--;
            getNavigation().stop();
            return;
        }
        if (target != null) watchForCheese(sl, target);
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

    /** Padrão: salto balístico com pouso marcado no chão, ou teleporte para perto. */
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (onGround() && distanceTo(target) < 22) {
            int t = BossKit.leapAt(this, target, 1.0);
            leapTicks = t;
            float dmg = scaled(9.0F);
            Telegraph.delayed(sl, Telegraph.Shape.circle(target.position(), 3.5F).warmup(Math.max(10, t))
                    .color(Telegraph.WARNING).owner(this), hits -> landingSlam(sl, hits, dmg));
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.9F);
        } else if (BossKit.blinkNear(this, target, 4.0)) {
            getNavigation().stop();
        }
    }

    protected void landingSlam(ServerLevel sl, List<LivingEntity> hits, float dmg) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                e.hurtMarked = true;
            }
        }
        BgFx.ring(sl, position().add(0, 0.15, 0), 2.0, 24, ParticleTypes.CLOUD, 0.08);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.8F);
        BossKit.shake(sl, position(), 20, 1.0F, 12);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || introTicks() > 0;
    }

    // ------------------------------------------------------------------ Dano e morte

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity attacker = source.getEntity();
            if (attacker != null && attacker != this && BgAllies.isAlly(this, attacker)) return false;
            if (introTicks() > 0) {
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

    /** Controle (atordoar, congelar, enraizar) dura metade num chefe; depois de um atordoar/congelar, 4 s de resistência. */
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
        BossRewards.grant(this, rewardXp(), REWARD_RANGE);
        announce("death");
        bar().removeAllPlayers();
        dismissMinions(sl);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropBossLoot(level, source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            if (deathTime % 3 == 0) {
                sl.sendParticles(BgFx.dust(bossColor(), 1.6F), c.x, c.y, c.z, 10, getBbWidth() * 0.4, getBbHeight() * 0.3,
                        getBbWidth() * 0.4, 0.0);
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.01);
            }
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                BgFx.sphere(sl, c, getBbHeight() * 0.6, 60, BgFx.dust(bossColor(), 2.0F));
                sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
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

    /** O covil não fica vazio no Pacífico: o chefe só não acorda. */
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

    // ------------------------------------------------------------------ Ajudas

    public void announce(String key) {
        Announce.send(this, Component.translatable(langKey("announce", key)), bossColor(), Announce.STYLE_BOSS);
    }

    protected String langKey(String group, String name) {
        return group + ".isekai_mahou." + bossId() + "." + name;
    }

    /** Nome do golpe na Pilha / HUD ("Rei Mímico — Língua"). */
    protected Component attackName(String attack) {
        String key = "attack.isekai_mahou." + bossId() + "." + attack;
        return Component.translatable(key);
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected float scaled(float base) {
        return base * BossKit.damageScale(this);
    }

    public boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || BgAllies.isAlly(this, e)) return false;
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

    /** Fim de uma linha reta saindo de {@code from} na direção {@code dir}, parando antes de paredes. */
    protected Vec3 lineEnd(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        Vec3 probe = from.add(0, 1.1, 0);
        Vec3 to = probe.add(dir.scale(max));
        HitResult hit = sl.clip(new ClipContext(probe, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? max : Math.max(1.5, hit.getLocation().distanceTo(probe) - 0.3);
        return from.add(dir.scale(len));
    }

    /** Chão embaixo de um ponto (até 7 blocos abaixo). */
    protected Vec3 groundAt(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        for (int i = 0; i < 8; i++) {
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

    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, EntityType<?> type) {
        return findGroundSpot(sl, center, min, max, type, getEyePosition());
    }

    /** Lugar livre no chão a [min, max] blocos de {@code center}; com {@code seenFrom}, à vista dali. */
    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, EntityType<?> type, @Nullable Vec3 seenFrom) {
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            BlockPos base = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 3, center.z + Math.sin(a) * r);
            for (int dy = 0; dy < 8; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)
                        && sl.noCollision(type.getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
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

    protected int countMinions(ServerLevel sl, double radius) {
        return sl.getEntities(this, getBoundingBox().inflate(radius), e -> e.getTags().contains(MINION_TAG) && e.isAlive()).size();
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

    protected void dropEssence(Element e, int min, int max) {
        var holder = CoreRegistries.ESSENCES.get(e);
        if (holder != null) dropItem(holder.get(), min, max);
    }

    /** Pergaminhos de glifos ARCANOS (sem iniciais nem lendários), sem repetir; prefere Círculo 2–5. */
    protected void dropArcaneScrolls(int count) {
        List<Glyph> pool = new ArrayList<>(GlyphRegistry.all().stream()
                .filter(g -> g.element() == Element.ARCANE && g.origin() == null && !g.isLegendary()
                        && !CoreGlyphs.STARTER.contains(g.id().getPath()))
                .toList());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        pool.sort(java.util.Comparator.comparingInt(g -> g.tier() >= 2 && g.tier() <= 5 ? 0 : 1));
        for (int i = 0; i < Math.min(count, pool.size()); i++) spawnAtLocation(GlyphScrollItem.of(pool.get(i).id()));
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

    /** Animação de base (morte, apresentação, andar, parado). */
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        return loop(moving ? "walk" : "idle");
    }

    private PlayState mainPredicate(AnimationState<GBossEntity> state) {
        return state.setAndContinue(mainAnimation(state.isMoving()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<GBossEntity> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        for (String a : actionAnimations()) action.triggerableAnim(a, play(a));
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
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        b.put("IntroSeen", seen);
        if (spawnHome != null) b.put("SpawnHome", NbtUtils.writeBlockPos(spawnHome));
        b.putBoolean("LairRegistered", lairRegistered);
        tag.put("GBoss", b);
        if (!lairId.isEmpty()) tag.putString("Lair", lairId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag b = tag.getCompound("GBoss");
        entityData.set(DATA_AWAKE, b.getBoolean("Awake"));
        entityData.set(DATA_PHASE, b.getInt("Phase"));
        introSeen.clear();
        for (Tag t : b.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        spawnHome = NbtUtils.readBlockPos(b, "SpawnHome").orElse(null);
        lairRegistered = b.getBoolean("LairRegistered");
        lairId = tag.getString("Lair");
        if (bar != null) bar.setName(getDisplayName());
    }
}
