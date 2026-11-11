package com.renanpontes.isekaimahou.feature.hueco_mundo.boss;

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
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmSites;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmAllies;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmMob;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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
 * Base dos chefes do módulo (Rei Vazio, Imperatriz das Areias, Rainha dos Espinhos):
 * <ul>
 *   <li>adormecido até um jogador chegar perto (ou bater) → cutscene "boss_intro", fala, arena SELADA
 *       ({@link BossKit#sealArena}: ninguém quebra/coloca blocos, o chefe não sai), escala de grupo;</li>
 *   <li>barra de chefe, fases por porcentagem de vida (cutscene "boss_phase" + fala);</li>
 *   <li>volta a dormir com vida cheia se todos forem embora; anti-trapaça (salto marcado / teleporte);</li>
 *   <li>morte lenta, loot, recompensa de grupo ({@link BossRewards}) e, se é o chefe de um lugar (palácio/corte), o
 *       lugar o chama de volta um dia depois ({@link HmSites}).</li>
 * </ul>
 */
public abstract class HmBoss extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(HmBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(HmBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(HmBoss.class, EntityDataSerializers.BOOLEAN);
    /** Tag das invocações dos chefes do módulo (limite e limpeza na morte/reset). */
    public static final String MINION_TAG = "isekai_mahou_hm_minion";
    protected static final int INTRO_TICKS = 60;
    private static final int DEATH_TICKS = 50;
    private static final double BAR_RANGE = 56.0, REWARD_RANGE = 56.0, PARTY_RANGE = 44.0;
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> introSeen = new HashSet<>();
    private final Set<String> hintsShown = new HashSet<>();
    private ServerBossEvent bar;
    private int calmTicks, unreachableTicks, cheeseCd;
    @Nullable
    private BlockPos spawnHome;
    private boolean siteBoss;
    protected int leapTicks;

    protected HmBoss(EntityType<? extends HmBoss> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder bossAttributes(double health, double armor, double speed, double damage) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0).add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.FOLLOW_RANGE, 56.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.95).add(Attributes.STEP_HEIGHT, 1.25);
    }

    // ------------------------------------------------------------------ O que cada chefe define

    /** Id (= id do tipo; traduções, contador boss.&lt;id&gt;, modelo). */
    public abstract String bossId();

    public abstract int bossColor();

    public abstract HmRealm side();

    protected abstract BossEvent.BossBarColor barColor();

    /** Frações de vida (decrescentes) em que muda de fase. */
    protected abstract float[] phaseThresholds();

    protected abstract void tickCombat(ServerLevel level, @Nullable LivingEntity target);

    protected abstract void dropBossLoot(ServerLevel level);

    protected abstract List<String> actionAnimations();

    protected int rewardXp() {
        return 600;
    }

    protected double wakeRange() {
        return 18.0;
    }

    /** Ataque em preparo (o anti-trapaça espera). */
    protected boolean isBusy() {
        return false;
    }

    protected void onIntroStart(ServerLevel level) {}

    protected void onPhaseChange(ServerLevel level, int phase) {
        Cutscenes.playNear(this, 48, "boss_phase", getDisplayName(), Component.translatable(langKey("boss", "phase" + phase)), bossColor(), 50, null);
        announce("phase" + phase);
    }

    protected void onReset(ServerLevel level) {}

    protected void clientTick() {}

    /** Sufixo da textura ("" ou "_p2"...) — o renderer troca a pele na Ressurreição. */
    public String textureSuffix() {
        return "";
    }

    /** Ossos do modelo que aparecem/somem conforme o estado (barbatana da Ressurreição, asas abertas...). */
    public List<String> toggledBones() {
        return List.of();
    }

    public boolean isBoneVisible(String bone) {
        return true;
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

    /** Chefe do palácio/corte: arena fixa e volta um dia depois de vencido. */
    public void markSiteBoss(Vec3 home, double arenaRadius) {
        siteBoss = true;
        spawnHome = BlockPos.containing(home);
        BossKit.setArena(this, home, arenaRadius);
    }

    @Nullable
    protected Vec3 arenaCenter() {
        Vec3 c = BossKit.arenaCenter(this);
        return c != null ? c : spawnHome != null ? Vec3.atBottomCenterOf(spawnHome) : null;
    }

    protected double arenaRadius() {
        double r = BossKit.arenaRadius(this);
        return r > 0 ? r : 16.0;
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this, HmBoss.class, HmMob.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> isAwake()));
    }

    protected ServerBossEvent bar() {
        if (bar == null) bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
        return bar;
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
        if (spawnHome == null) spawnHome = blockPosition();
        if (BossKit.arenaCenter(this) == null) BossKit.setArena(this, Vec3.atBottomCenterOf(spawnHome), 18.0);
        if (isPassenger()) stopRiding();
        bar().setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        int intro = introTicks();
        if (intro > 0) {
            entityData.set(DATA_INTRO, intro - 1);
            Player near = sl.getNearestPlayer(this, 40);
            if (near != null) faceTowards(near);
            int t = INTRO_TICKS - intro;
            if (t % 4 == 0) HmFx.ring(sl, position().add(0, 0.1, 0), 1.5 + t * 0.05, 28, HmFx.dust(bossColor(), 1.5F), 0.0);
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

    protected void faceTowards(Entity e) {
        float yaw = (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * (180.0 / Math.PI)) - 90.0F;
        holdYaw(yaw);
    }

    private void tryWake(ServerLevel sl) {
        if (sl.getDifficulty() == Difficulty.PEACEFUL) return;
        double range = wakeRange();
        for (ServerPlayer p : sl.players()) {
            if (validPlayer(p) && p.distanceToSqr(this) < range * range && hasLineOfSight(p)) {
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
        Cutscenes.playNear(this, 48, "boss_intro", getDisplayName(), Component.translatable(langKey("boss", "subtitle")), bossColor(),
                INTRO_TICKS + 20, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) introSeen.add(p.getUUID());
        updateBarPlayers(sl);
        announce("intro");
        BossKit.roar(this);
        onIntroStart(sl);
    }

    private void greetLateComers(ServerLevel sl) {
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 28 * 28 && !p.isSpectator())) {
            if (!introSeen.contains(p.getUUID()) && hasLineOfSight(p)) {
                introSeen.add(p.getUUID());
                Cutscenes.play(p, "boss_intro", this, getDisplayName(), Component.translatable(langKey("boss", "subtitle")), bossColor(), 60, false);
            }
        }
    }

    private void checkCalm(ServerLevel sl) {
        boolean anyone = getTarget() != null;
        if (!anyone) {
            for (ServerPlayer p : sl.players()) {
                if (validPlayer(p) && p.distanceToSqr(this) < 44 * 44) {
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
        introSeen.clear();
        hintsShown.clear();
        leapTicks = 0;
        setTarget(null);
        getNavigation().stop();
        bar().removeAllPlayers();
        dismissMinions(sl);
        onReset(sl);
        Vec3 home = arenaCenter();
        if (home != null && home.distanceToSqr(position()) > 9) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            teleportTo(home.x, home.y, home.z);
            holdYaw(siteYaw());
        }
    }

    /** Para onde o chefe do lugar olha quando dorme (o trono do Rei olha para o sul). */
    protected float siteYaw() {
        return getYRot();
    }

    private void keepInArena(ServerLevel sl) {
        Vec3 c = arenaCenter();
        double r = arenaRadius();
        if (c == null) return;
        double dx = getX() - c.x, dz = getZ() - c.z;
        if (dx * dx + dz * dz <= (r + 3.0) * (r + 3.0) && Math.abs(getY() - c.y) < 14) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        teleportTo(c.x, c.y, c.z);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }

    protected void dismissMinions(ServerLevel sl) {
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(56), e -> e.getTags().contains(MINION_TAG) && e.isAlive())) {
            sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
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
        float[] th = phaseThresholds();
        float frac = getHealth() / getMaxHealth();
        while (phase() < th.length && frac <= th[phase()]) {
            entityData.set(DATA_PHASE, phase() + 1);
            Telegraph.cancelAll(this);
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

    private void watchForCheese(ServerLevel sl, LivingEntity target) {
        if (cheeseCd > 0) cheeseCd--;
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < 40 || cheeseCd > 0 || isBusy()) return;
        unreachableTicks = 0;
        cheeseCd = 80;
        if (onGround() && distanceTo(target) < 22) {
            leapTicks = BossKit.leapAt(this, target, 1.0);
            float dmg = scaled(10F);
            Telegraph.delayed(sl, Telegraph.Shape.circle(target.position(), 3.5F).warmup(Math.max(10, leapTicks)).color(Telegraph.WARNING).owner(this),
                    hits -> {
                        for (LivingEntity e : hits) if (isFoe(e)) e.hurt(damageSources().mobAttack(this), dmg);
                        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
                        BossKit.shake(sl, position(), 20, 1.0F, 12);
                    });
        } else {
            BossKit.blinkNear(this, target, 4.0);
        }
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
            if (attacker != null && attacker != this && HmAllies.isAlly(this, attacker)) return false;
            if (introTicks() > 0) return false;
            if (!isAwake()) {
                if (attacker instanceof Player p && !p.isSpectator() && level().getDifficulty() != Difficulty.PEACEFUL) startIntro((ServerLevel) level());
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    /** Controle (atordoar, congelar, enraizar, petrificar) dura metade num chefe. */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (effect.is(CoreRegistries.STUNNED) || effect.is(CoreRegistries.FROZEN) || effect.is(CoreRegistries.ROOTED)
                || effect.is(CoreRegistries.PETRIFIED)) {
            effect = new MobEffectInstance(effect.getEffect(), Math.max(10, effect.getDuration() / 2), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }
        return super.addEffect(effect, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level() instanceof ServerLevel sl) {
            Telegraph.cancelAll(this);
            BossRewards.grant(this, rewardXp(), REWARD_RANGE);
            announce("death");
            bar().removeAllPlayers();
            dismissMinions(sl);
            if (siteBoss) HmSites.bossDefeated(sl, bossId());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropBossLoot(level);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            if (deathTime % 3 == 0) {
                sl.sendParticles(HmFx.dust(bossColor(), 1.6F), c.x, c.y, c.z, 10, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.0);
                sl.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 2, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.02);
            }
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                HmFx.sphere(sl, c, getBbHeight() * 0.7, 70, HmFx.dust(bossColor(), 2.0F));
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
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

    // ------------------------------------------------------------------ Ajudas

    public void announce(String key) {
        Announce.send(this, Component.translatable(langKey("announce", key)), bossColor(), Announce.STYLE_BOSS);
    }

    protected String langKey(String group, String name) {
        return group + "." + IsekaiMahou.MODID + "." + bossId() + "." + name;
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected float scaled(float base) {
        return base * BossKit.damageScale(this);
    }

    public boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || HmAllies.isAlly(this, e) || e.getTags().contains(MINION_TAG)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /** Fere todos os inimigos da lista com um golpe do chefe; devolve quantos acertou. */
    protected int hurtAll(List<LivingEntity> hits, float dmg, boolean magic) {
        if (!isAlive()) return 0;
        int n = 0;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            DamageSource src = magic ? damageSources().indirectMagic(this, this) : damageSources().mobAttack(this);
            if (e.hurt(src, dmg)) n++;
        }
        return n;
    }

    protected void holdYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    protected float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * (180.0 / Math.PI)) - 90.0F;
    }

    /** Fim de uma linha reta saindo de {@code from}, parando antes de paredes. */
    protected Vec3 lineEnd(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        Vec3 probe = from.add(0, 1.1, 0);
        HitResult hit = sl.clip(new ClipContext(probe, probe.add(dir.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? max : Math.max(1.5, hit.getLocation().distanceTo(probe) - 0.3);
        return from.add(dir.scale(len));
    }

    protected void hintOnce(String key) {
        if (!hintsShown.add(key)) return;
        Component msg = Component.translatable(langKey("message", key)).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : BossKit.partyAround(this, 44)) p.displayClientMessage(msg, true);
    }

    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /** Ponto livre no chão da arena a [min, max] do centro. */
    @Nullable
    protected Vec3 arenaSpot(ServerLevel sl, double min, double max) {
        Vec3 c = arenaCenter();
        if (c == null) c = position();
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            BlockPos base = BlockPos.containing(c.x + Math.cos(a) * r, c.y + 3, c.z + Math.sin(a) * r);
            for (int dy = 0; dy < 8; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP) && sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()
                        && sl.getBlockState(p.above()).getCollisionShape(sl, p.above()).isEmpty()) {
                    return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    /** Invoca lacaios do chefe (com teto), marcados para sumir quando ele morre/reseta. */
    protected int summonMinions(ServerLevel sl, EntityType<? extends Mob> type, int count, int cap) {
        int have = sl.getEntities(this, getBoundingBox().inflate(48), e -> e.getTags().contains(MINION_TAG) && e.isAlive()).size();
        int n = 0;
        for (int i = 0; i < count && have + n < cap; i++) {
            Vec3 at = arenaSpot(sl, 4, Math.max(6, arenaRadius() - 3));
            if (at == null) continue;
            Mob m = type.create(sl);
            if (m == null) continue;
            m.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            m.addTag(MINION_TAG);
            if (getTarget() != null) m.setTarget(getTarget());
            sl.addFreshEntity(m);
            sl.sendParticles(HmFx.dust(bossColor(), 1.4F), at.x, at.y + 0.5, at.z, 16, 0.4, 0.5, 0.4, 0);
            n++;
        }
        return n;
    }

    protected void dropEssence(Element e, int min, int max) {
        var holder = CoreRegistries.ESSENCES.get(e);
        if (holder == null) return;
        int n = min + random.nextInt(Math.max(1, max - min + 1));
        if (n > 0) spawnAtLocation(new ItemStack(holder.get(), n));
    }

    protected void dropScroll(String glyphPath) {
        spawnAtLocation(GlyphScrollItem.of(IsekaiMahou.id(glyphPath)));
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

    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return anim("hold", "death");
        if (introTicks() > 0) return anim("play", "intro");
        if (!isAwake()) return anim("loop", "sleep");
        return anim("loop", moving ? "walk" : "idle");
    }

    private PlayState mainPredicate(AnimationState<HmBoss> state) {
        return state.setAndContinue(mainAnimation(state.isMoving()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<HmBoss> act = new AnimationController<>(this, "action", 3, s -> PlayState.STOP);
        for (String a : actionAnimations()) act.triggerableAnim(a, anim("play", a));
        controllers.add(act);
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
        b.putBoolean("Site", siteBoss);
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        b.put("IntroSeen", seen);
        if (spawnHome != null) b.put("Home", NbtUtils.writeBlockPos(spawnHome));
        tag.put("HmBoss", b);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag b = tag.getCompound("HmBoss");
        entityData.set(DATA_AWAKE, b.getBoolean("Awake"));
        entityData.set(DATA_PHASE, b.getInt("Phase"));
        siteBoss = b.getBoolean("Site");
        introSeen.clear();
        for (Tag t : b.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        spawnHome = NbtUtils.readBlockPos(b, "Home").orElse(null);
        if (bar != null) bar.setName(getDisplayName());
    }
}
