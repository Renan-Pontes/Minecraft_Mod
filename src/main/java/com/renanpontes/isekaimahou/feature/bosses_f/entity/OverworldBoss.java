package com.renanpontes.isekaimahou.feature.bosses_f.entity;

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
import com.renanpontes.isekaimahou.core.api.Statuses;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos 4 chefes do Overworld (covis do world_v09). Cuida de tudo que é comum:
 * <ul>
 *   <li><b>casa</b>: o ponto onde nasceu (molde da estrutura, ovo ou comando) fica salvo; nunca some por distância nem no
 *       Pacífico (no Pacífico só não acorda); se a luta o arrasta para longe do covil, volta andando/voando (ou piscando);</li>
 *   <li>fica <b>adormecido</b> até um jogador chegar perto (ou bater) → cutscene "boss_intro" + fala; 3 s invulnerável;
 *       a vida/dano se ajustam ao tamanho do grupo ({@link BossKit#scaleToParty});</li>
 *   <li>barra de chefe, fases por porcentagem de vida (cutscene "boss_phase" + fala);</li>
 *   <li>volta a dormir (vida cheia, escala desfeita, em casa) se todos forem embora — menos em modo provação;</li>
 *   <li>anti-trapaça (alvo inalcançável por 2 s → reação do chefe); sem barco/carrinho/portal;</li>
 *   <li>partes extras de acerto ({@link BossPart}) para cabeças/núcleo;</li>
 *   <li>morte com animação, loot por id e recompensa de grupo ({@link BossRewards}: XP + {@code boss.<id>}).</li>
 *   <li><b>arena protegida</b> (v1.0, {@link BossArena}): todo chefe tem arena gravada ({@link BossKit#setArena} — a do
 *       molde do covil, a da provação ou, por ovo/comando, a casa com {@link #arenaRadius()}); ela é SELADA na intro
 *       (ninguém quebra/coloca blocos, explosões não mexem, o chefe não cai do mapa) e liberada quando ele volta a dormir.
 *       Os golpes do chefe também não mexem em blocos: crateras e valas são só partículas.</li>
 * </ul>
 */
public abstract class OverworldBoss extends Monster implements GeoEntity, BfBones {
    private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(OverworldBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(OverworldBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(OverworldBoss.class, EntityDataSerializers.BOOLEAN);

    protected static final int INTRO_TICKS = 60;
    private static final double WAKE_RANGE = 18.0;
    private static final double BAR_RANGE = 56.0;
    private static final double REWARD_RANGE = 56.0;
    protected static final double PARTY_RANGE = 44.0;
    private static final int CHEESE_TICKS = 40;
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> introSeen = new HashSet<>();
    private final Set<String> hintsShown = new HashSet<>();
    private ServerBossEvent bar;
    private int calmTicks;
    @Nullable
    private BlockPos home;
    private int unreachableTicks;
    private int cheeseCd;
    /** gameTime até quando Atordoado/Congelado novos não pegam (anti-trava em cadeia). */
    private long ccImmuneUntil;
    /** Ticks restantes de um salto anti-trapaça (a IA fica quieta). */
    protected int leapTicks;
    /** Voltando para o covil (arrastado para longe). */
    private boolean homeward;
    private int homewardTicks;
    private BossPart[] parts = new BossPart[0];

    protected OverworldBoss(EntityType<? extends OverworldBoss> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder bossAttributes(double health, double armor, double speed, double damage) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.25);
    }

    // ------------------------------------------------------------------ O que cada chefe define

    public abstract String bossId();

    public abstract int bossColor();

    protected abstract BossEvent.BossBarColor barColor();

    /** Porcentagens de vida (decrescentes) em que o chefe muda de fase. */
    protected abstract float[] phaseThresholds();

    protected abstract void tickCombat(ServerLevel level, @Nullable LivingEntity target);

    protected abstract void dropBossLoot(ServerLevel level, DamageSource source);

    /** Animações disparáveis no controlador "action". */
    protected abstract List<String> actionAnimations();

    protected void onIntroStart(ServerLevel level) {}

    protected void onIntroEnd(ServerLevel level) {}

    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) BfFx.ring(level, position().add(0, 0.1, 0), 1.5 + t * 0.04, 24, BfFx.dust(bossColor(), 1.6F), 0.0);
    }

    /** Todos foram embora: o chefe volta a dormir (zere ataques/estados aqui). */
    protected void onReset(ServerLevel level) {}

    protected void onPhaseChange(ServerLevel level, int phase) {
        Cutscenes.playNear(this, 48, "boss_phase", getDisplayName(), Component.translatable(langKey("boss", "phase" + phase)), bossColor(), 50, null);
        announce("phase" + phase);
    }

    /** Partículas no cliente (ambiente). */
    protected void clientTick() {}

    /** XP mágico de grupo pela derrota. */
    protected int rewardXp() {
        return 320;
    }

    /** Raio em volta da casa em que a luta acontece; além disso o chefe volta. */
    protected double leashRadius() {
        return 40.0;
    }

    /** Raio da arena protegida quando ninguém gravou uma (ovo/comando): o raio de volta para casa, dentro de 24–48. */
    protected double arenaRadius() {
        return Mth.clamp(leashRadius(), BossArena.MIN_RADIUS, BossArena.MAX_RADIUS);
    }

    /** Duração da morte (ticks) — a animação "death" toca inteira. */
    protected int deathTicks() {
        return 50;
    }

    /** Verdadeiro enquanto um ataque está em preparo/execução (anti-trapaça e volta para casa esperam). */
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
    public BlockPos home() {
        return home;
    }

    /** Define a casa (ex.: o world_v09 pode chamar ao montar o covil; senão vale onde nasceu). */
    public void setHome(BlockPos pos) {
        this.home = pos.immutable();
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this, OverworldBoss.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> isAwake()));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        // Molde da estrutura, ovo ou comando: o covil é aqui.
        if (home == null) home = blockPosition();
        return out;
    }

    private ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            configureBar(bar);
        }
        return bar;
    }

    protected void configureBar(ServerBossEvent bossBar) {}

    // ------------------------------------------------------------------ Partes (caixas de acerto extras)

    /** Chame no construtor da subclasse: cria as partes e reserva ids seguidos (como o Dragão do Fim). */
    protected void initParts(BossPart[] newParts) {
        this.parts = newParts;
        setId(ENTITY_COUNTER.getAndAdd(newParts.length + 1) + 1);
    }

    protected BossPart[] bossParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts == null) return;
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override
    public boolean isMultipartEntity() {
        return parts.length > 0;
    }

    @Nullable
    @Override
    public PartEntity<?>[] getParts() {
        return parts.length > 0 ? parts : null;
    }

    /** A parte existe agora (cabeça viva, núcleo...)? */
    public boolean isPartActive(int index) {
        return true;
    }

    /** Posiciona as partes (roda no servidor E no cliente, todo tick). */
    protected void positionParts() {}

    /**
     * Ponto do modelo (px no espaço Java do GeckoLib, pose de repouso) → mundo, com a rotação do corpo e a escala do
     * renderer. Igual à conta do GeckoLib (rotação 180 − yBodyRot em Y).
     */
    protected Vec3 modelPoint(double jx, double jy, double jz, double scale) {
        double phi = Math.toRadians(180.0 - yBodyRot);
        double c = Math.cos(phi), s = Math.sin(phi);
        double k = scale / 16.0;
        return new Vec3(getX() + (jx * c + jz * s) * k, getY() + jy * k, getZ() + (-jx * s + jz * c) * k);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (parts.length > 0) positionParts();
    }

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
        if (home == null) home = blockPosition();
        // Sem arena gravada (ovo/comando): a casa vira a arena protegida da luta (o covil já grava a dele ao nascer).
        if (tickCount % 20 == 1 && BossKit.arenaCenter(this) == null) BossKit.setArena(this, Vec3.atBottomCenterOf(home), arenaRadius());
        if (isPassenger()) stopRiding();
        bar().setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        int intro = introTicks();
        if (intro > 0) {
            entityData.set(DATA_INTRO, intro - 1);
            Player near = sl.getNearestPlayer(this, 48);
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
                if (tickCount % 20 == 0) checkLeash(sl);
            }
        }
    }

    protected void faceTowards(Entity e) {
        float yaw = yawTo(e.position());
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void tryWake(ServerLevel sl) {
        if (sl.getDifficulty() == Difficulty.PEACEFUL) return;
        boolean trial = BossKit.isTrialBoss(this);
        double range = trial ? Math.max(40.0, BossKit.arenaRadius(this) + 8.0) : WAKE_RANGE;
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
        homeward = false;
        getNavigation().stop();
        BossKit.scaleToParty(this, PARTY_RANGE);
        // Sela a arena já na apresentação: o cenário fica protegido antes do primeiro golpe.
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
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 28 * 28 && !p.isSpectator())) {
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
                if (validPlayer(p) && p.distanceToSqr(this) < 48 * 48) {
                    anyone = true;
                    break;
                }
            }
        }
        calmTicks = anyone ? 0 : calmTicks + 10;
        if (calmTicks >= 600) resetBoss(sl);
    }

    /** Longe demais do covil: volta (a luta não sai da arena; kite infinito não funciona). */
    private void checkLeash(ServerLevel sl) {
        Vec3 c = centerOfFight();
        if (c == null) return;
        // Provação: a arena é fechada (raio exato). Covil do world_v09: o raio gravado é só o tamanho do covil — a luta
        // pode transbordar um pouco (o Wyvern orbita a 13 blocos do alvo e mergulha 24), então vale o maior dos dois.
        double arena = BossKit.arenaRadius(this);
        double r = arena <= 0 ? leashRadius() : BossKit.isTrialBoss(this) ? arena + 3.0 : Math.max(leashRadius(), arena + 3.0);
        double dx = getX() - c.x, dz = getZ() - c.z;
        double d2 = dx * dx + dz * dz;
        if (!homeward && d2 > r * r && !isBusy()) {
            homeward = true;
            homewardTicks = 0;
            hintOnce(sl, "leash");
        }
    }

    @Nullable
    private Vec3 centerOfFight() {
        Vec3 arena = BossKit.arenaCenter(this);
        if (arena != null) return arena;
        return home == null ? null : Vec3.atBottomCenterOf(home);
    }

    public boolean isHomeward() {
        return homeward;
    }

    /** Movimento de volta para casa (padrão: navegação; voadores sobrescrevem). Devolve true ao chegar. */
    protected boolean moveHome(ServerLevel sl, Vec3 to) {
        if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(to.x, to.y, to.z, 1.2);
        return position().distanceToSqr(to) < 16.0;
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
        homeward = false;
        setTarget(null);
        getNavigation().stop();
        bar().removeAllPlayers();
        onReset(sl);
        teleportHome(sl);
        BossArena.release(this);
    }

    private void unscaleParty() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(BossKit.PARTY_HEALTH_ID);
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.removeModifier(BossKit.PARTY_DAMAGE_ID);
        getPersistentData().remove(BossKit.PARTY_KEY);
    }

    /** Volta para o covil piscando (fim da luta, ou não conseguiu voltar andando). */
    protected void teleportHome(ServerLevel sl) {
        Vec3 to = centerOfFight();
        if (to == null || to.distanceToSqr(position()) < 9.0) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 30, getBbWidth() * 0.4, 0.8, getBbWidth() * 0.4, 0.02);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        sl.sendParticles(BfFx.dust(bossColor(), 2.0F), to.x, to.y + getBbHeight() / 2, to.z, 30, getBbWidth() * 0.4, 0.8, getBbWidth() * 0.4, 0);
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
        // Atordoado (contramágica da Pilha), Congelado ou Petrificado: perde a vez — a janela que o jogador conquistou.
        if (isControlled()) {
            getNavigation().stop();
            tickWhileControlled(sl, target);
            return;
        }
        if (homeward) {
            Vec3 c = centerOfFight();
            if (c == null || moveHome(sl, c) || ++homewardTicks > 200) {
                if (homewardTicks > 200) teleportHome(sl);
                homeward = false;
                homewardTicks = 0;
            }
            if (homeward) {
                tickWhileHomeward(sl, target);
                return;
            }
        }
        if (target != null) watchForCheese(sl, target);
        tickCombat(sl, target);
    }

    /** Enquanto volta para casa: só os relógios de recarga (padrão: nada). */
    protected void tickWhileHomeward(ServerLevel sl, @Nullable LivingEntity target) {}

    /** Preso por controle (Atordoado/Congelado/Petrificado): sem golpes novos nem movimento. */
    public boolean isControlled() {
        return hasEffect(CoreRegistries.STUNNED) || hasEffect(CoreRegistries.FROZEN) || Statuses.incapacitated(this);
    }

    /** Enquanto está preso por controle (padrão: os mesmos relógios da volta para casa). */
    protected void tickWhileControlled(ServerLevel sl, @Nullable LivingEntity target) {
        tickWhileHomeward(sl, target);
    }

    // ------------------------------------------------------------------ Anti-trapaça

    private void watchForCheese(ServerLevel sl, LivingEntity target) {
        if (cheeseCd > 0) cheeseCd--;
        unreachableTicks = isTargetUnreachable(target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < CHEESE_TICKS || cheeseCd > 0 || isBusy()) return;
        unreachableTicks = 0;
        cheeseCd = 80;
        onUnreachable(sl, target);
    }

    protected boolean isTargetUnreachable(LivingEntity target) {
        return BossKit.isUnreachable(this, target);
    }

    /** Reação padrão: salto balístico com pouso telegrafado, ou teleporte para perto. */
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (onGround() && distanceTo(target) < 22) {
            int t = BossKit.leapAt(this, target, 1.0);
            leapTicks = t;
            float dmg = scaled(9.0F);
            Telegraph.delayed(sl, Telegraph.Shape.circle(target.position(), 3.8F).warmup(Math.max(10, t)).color(Telegraph.WARNING).owner(this),
                    hits -> landingSlam(sl, hits, dmg));
            playSound(SoundEvents.RAVAGER_ROAR, 1.8F, 0.8F);
        } else if (BossKit.blinkNear(this, target, 4.0)) {
            getNavigation().stop();
        }
    }

    private void landingSlam(ServerLevel sl, List<LivingEntity> hits, float dmg) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                e.hurtMarked = true;
            }
        }
        BfFx.groundBurst(sl, position(), 3.0, 40);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.7F);
        BossKit.shake(sl, position(), 22, 1.1F, 12);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || introTicks() > 0;
    }

    // ------------------------------------------------------------------ Dano e morte

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return hurtFrom(null, source, amount);
    }

    /** Dano recebido por uma parte (cabeça, núcleo). */
    public boolean hurtPart(BossPart part, DamageSource source, float amount) {
        return hurtFrom(part, source, amount);
    }

    private boolean hurtFrom(@Nullable BossPart part, DamageSource source, float amount) {
        // Covis apertados e lagos não matam o chefe (nem viram trapaça): sem sufocar, espremer ou afogar.
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)) return false;
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity attacker = source.getEntity();
            if (attacker != null && attacker != this && isAllyOf(attacker)) return false;
            if (introTicks() > 0) {
                deflectFx();
                return false;
            }
            if (!isAwake()) {
                if (attacker instanceof Player p && !p.isSpectator() && level().getDifficulty() != Difficulty.PEACEFUL) {
                    startIntro((ServerLevel) level());
                }
                return false;
            }
            float before = amount;
            amount = modifyIncomingDamage(part, source, amount);
            if (amount <= 0F) {
                deflectFx();
                return false;
            }
            boolean ok = super.hurt(source, amount);
            if (ok) onDamaged(part, source, before, amount);
            return ok;
        }
        return super.hurt(source, amount);
    }

    /** Multiplicadores do chefe (armadura de pedra, ponto fraco...). 0 = defendido. */
    protected float modifyIncomingDamage(@Nullable BossPart part, DamageSource source, float amount) {
        return amount;
    }

    /** Depois de levar dano ({@code raw} = antes dos multiplicadores do chefe). */
    protected void onDamaged(@Nullable BossPart part, DamageSource source, float raw, float dealt) {}

    protected void deflectFx() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.4, 0.4, 0.4, 0.1);
        }
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    /** Controle (atordoar, congelar, enraizar) dura metade num chefe. */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        boolean hardCc = effect.is(CoreRegistries.STUNNED) || effect.is(CoreRegistries.FROZEN);
        if (hardCc || effect.is(CoreRegistries.ROOTED)) {
            if (hardCc && !level().isClientSide) {
                // Atordoar/congelar em cadeia não trava o chefe: depois de um, 4 s de resistência.
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
                sl.sendParticles(BfFx.dust(bossColor(), 1.8F), c.x, c.y, c.z, 10, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.0);
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.01);
            }
            if (deathTime >= deathTicks() && !isRemoved()) {
                sl.sendParticles(BfFx.dust(bossColor(), 2.4F), c.x, c.y, c.z, 80, getBbWidth() * 0.6, getBbHeight() * 0.5, getBbWidth() * 0.6, 0);
                sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0.0);
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

    /** Nome do golpe no HUD da Pilha ({@code attack.isekai_mahou.<chefe>.<golpe>}). */
    protected Component attackName(String key) {
        return Component.translatable(langKey("attack", key));
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected float scaled(float base) {
        return base * BossKit.damageScale(this);
    }

    protected float attackDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Ele mesmo ou outro chefe deste módulo: sem fogo amigo. */
    protected boolean isAllyOf(Entity e) {
        return e == this || e instanceof OverworldBoss;
    }

    public boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || isAllyOf(e)) return false;
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

    /** Diferença de ângulo (−180..180) entre a frente do corpo e o ponto. */
    protected float angleTo(Vec3 p) {
        return Mth.wrapDegrees(yawTo(p) - yBodyRot);
    }

    protected Vec3 lineEnd(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        Vec3 probe = from.add(0, 1.1, 0);
        Vec3 to = probe.add(dir.scale(max));
        HitResult hit = sl.clip(new ClipContext(probe, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? max : Math.max(1.5, hit.getLocation().distanceTo(probe) - 0.3);
        return from.add(dir.scale(len));
    }

    /** Altura do chão embaixo de um ponto (até {@code depth} blocos abaixo). */
    protected Vec3 groundAt(ServerLevel level, Vec3 pos, int depth) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        for (int i = 0; i < depth; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty()) {
                return new Vec3(pos.x, p.getY(), pos.z);
            }
            p = below;
        }
        return pos;
    }

    protected Vec3 groundAt(ServerLevel level, Vec3 pos) {
        return groundAt(level, pos, 8);
    }

    protected void hintOnce(ServerLevel sl, String key) {
        if (!hintsShown.add(key)) return;
        Component msg = Component.translatable(langKey("message", key)).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : BossKit.partyAround(this, 48)) p.displayClientMessage(msg, true);
    }

    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 8 == 0 || getNavigation().isDone()) {
            if (!getNavigation().moveTo(target, speed)) getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /** Lugar livre no chão a [min, max] blocos de {@code center}, visível de {@code seenFrom}. */
    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, @Nullable Vec3 seenFrom) {
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            BlockPos base = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 3, center.z + Math.sin(a) * r);
            for (int dy = 0; dy < 9; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP) && sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()) {
                    Vec3 spot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    if (seenFrom == null || sl.clip(new ClipContext(seenFrom, spot.add(0, 0.6, 0), ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                        return spot;
                    }
                    break;
                }
            }
        }
        return null;
    }

    /** Solta {@code min..max} de um item de OUTRO módulo pelo id (ignora se não existir). */
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

    /** Troféus para o grupo: 1 por jogador que lutou (máx. 4) — a Ascensão pede um por pessoa. */
    protected int trophyCount() {
        return Mth.clamp(BossKit.partySize(this), 1, 4);
    }

    /** Pergaminhos de glifos ARCANOS (sem iniciais nem lendários), sem repetir, preferindo Círculo 3–5. */
    protected void dropArcaneScrolls(int count) {
        List<Glyph> pool = new ArrayList<>(GlyphRegistry.all().stream()
                .filter(g -> g.element() == Element.ARCANE && g.origin() == null && !g.isLegendary()
                        && !CoreGlyphs.STARTER.contains(g.id().getPath()))
                .toList());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        pool.sort(java.util.Comparator.comparingInt(g -> g.tier() >= 2 && g.tier() <= 4 ? 0 : 1));
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

    /** Animação de base (morte, apresentação, andar, parado) — chefes com estados extras sobrescrevem. */
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        return loop(moving ? "walk" : "idle");
    }

    private PlayState mainPredicate(AnimationState<OverworldBoss> state) {
        return state.setAndContinue(mainAnimation(state.isMoving()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<OverworldBoss> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        for (String a : actionAnimations()) action.triggerableAnim(a, play(a));
        controllers.add(action);
        addExtraControllers(controllers);
    }

    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {}

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
        if (home != null) b.put("Home", NbtUtils.writeBlockPos(home));
        tag.put("OverworldBoss", b);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag b = tag.getCompound("OverworldBoss");
        entityData.set(DATA_AWAKE, b.getBoolean("Awake"));
        entityData.set(DATA_PHASE, b.getInt("Phase"));
        introSeen.clear();
        for (Tag t : b.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        // Molde de estrutura sem "Home": a casa é onde ele for colocado (definida no 1º tick).
        home = NbtUtils.readBlockPos(b, "Home").orElse(null);
        if (bar != null) bar.setName(getDisplayName());
    }
}
