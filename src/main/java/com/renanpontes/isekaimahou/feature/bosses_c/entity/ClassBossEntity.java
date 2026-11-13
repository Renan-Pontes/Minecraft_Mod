package com.renanpontes.isekaimahou.feature.bosses_c.entity;

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
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_c.polish.BossShow;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.core.BlockPos;
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
 * Base dos minichefes das estruturas de classe. Cuida de tudo que é comum:
 * <ul>
 *   <li>fica <b>adormecido</b> até um jogador chegar perto (ou bater nele) → cutscene "boss_intro" + fala de apresentação,
 *       3 s invulnerável e parado enquanto se apresenta; na apresentação a vida/dano se ajustam ao tamanho do grupo;</li>
 *   <li>barra de chefe para quem estiver a até 48 blocos;</li>
 *   <li>fases por porcentagem de vida (cutscene "boss_phase" + fala);</li>
 *   <li>volta a dormir (vida cheia, escala de grupo desfeita) se todos os jogadores forem embora — menos em modo
 *       provação ({@link BossKit#TRIAL_TAG}), em que luta até o fim e usa o centro da arena como casa;</li>
 *   <li>anti-trapaça: alvo inalcançável (pilar, buraco, barco) por 2 s → salta/teleporta até ele; não entra em barcos;</li>
 *   <li>morte lenta com partículas, loot por id e recompensa de grupo ({@link BossRewards}: XP + {@code boss.<id>}).</li>
 * </ul>
 * Cada chefe implementa só o combate ({@link #tickCombat}). Ataques de área/linha usam a telegrafia do núcleo
 * ({@link Telegraph}): o chão marca exatamente onde o golpe vai cair.
 */
public abstract class ClassBossEntity extends Monster implements GeoEntity, BoneToggles {
    private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(ClassBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ClassBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(ClassBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tag de entidade das invocações dos chefes (para limitar quantas existem e sumir com elas na morte). */
    public static final String MINION_TAG = "isekai_mahou_class_boss_minion";

    protected static final int INTRO_TICKS = 60;
    /** v1.0: 64 (era 50) — a morte nova tem 3 s de animação + o corpo se desfazendo. */
    private static final int DEATH_TICKS = 64;
    private static final double WAKE_RANGE = 16.0;
    private static final double BAR_RANGE = 48.0;
    private static final double REWARD_RANGE = 48.0;
    /** XP mágico de grupo pela derrota (BossRewards). */
    private static final int REWARD_XP = 250;
    /** Raio em que os jogadores contam para a escala de grupo. */
    private static final double PARTY_RANGE = 40.0;
    /** Ticks seguidos com o alvo inalcançável até o chefe reagir (salto/teleporte). */
    private static final int CHEESE_TICKS = 40;
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> introSeen = new HashSet<>();
    /** Dicas já mostradas nesta luta (zeradas quando ele volta a dormir). */
    private final Set<String> hintsShown = new HashSet<>();
    private ServerBossEvent bar;
    private int calmTicks;
    private int strafeDir = 1;
    /** Onde o chefe nasceu (sala da estrutura): volta para cá quando todos vão embora. */
    @Nullable
    private BlockPos spawnHome;
    private int unreachableTicks;
    private int cheeseCd;
    /** Ticks restantes de um salto anti-trapaça (a IA fica quieta enquanto ele voa). */
    private int leapTicks;

    protected ClassBossEntity(EntityType<? extends ClassBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 150;
        setPersistenceRequired();
    }

    /** Atributos padrão dos minichefes (cada um ajusta o resto). */
    protected static AttributeSupplier.Builder bossAttributes(double health, double armor, double speed, double damage) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    // ------------------------------------------------------------------ O que cada chefe define

    /** Id do chefe (= id do tipo de entidade, usado nas traduções e no contador boss.<id>). */
    public abstract String bossId();

    /** Cor RGB do chefe (anúncios, cutscenes, partículas). */
    public abstract int bossColor();

    protected abstract BossEvent.BossBarColor barColor();

    /** Porcentagens de vida (decrescentes) em que o chefe muda de fase. */
    protected abstract float[] phaseThresholds();

    /** Combate (só roda acordado, fora da apresentação). */
    protected abstract void tickCombat(ServerLevel level, @Nullable LivingEntity target);

    /** Loot especial (itens de outros módulos por id, pergaminhos...). O loot do vanilla vem da loot table. */
    protected abstract void dropBossLoot(ServerLevel level, DamageSource source);

    /** Animações disparáveis no controlador "action". */
    protected abstract List<String> actionAnimations();

    protected void onIntroStart(ServerLevel level) {}

    protected void onIntroEnd(ServerLevel level) {}

    /** Partículas durante a apresentação ({@code t} = ticks desde o início). */
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) BossFx.ring(level, position().add(0, 0.1, 0), 1.5 + t * 0.03, 24, BossFx.dust(bossColor(), 1.4F), 0.0);
    }

    /** Todos os jogadores foram embora: o chefe volta a dormir (zere ataques/estados aqui). */
    protected void onReset(ServerLevel level) {}

    /** Mudança de fase: cutscene (se {@link #phaseCutscene}) e fala "phase<n>". */
    protected void onPhaseChange(ServerLevel level, int phase) {
        if (phaseCutscene(phase)) {
            Cutscenes.playNear(this, 40, "boss_phase", getDisplayName(),
                    Component.translatable(langKey("boss", "phase" + phase)), bossColor(), 50, null);
        }
        announce("phase" + phase);
        // v1.0: toda troca de fase tem a sua onda (mesmo as que não têm cutscene)
        BossShow.phaseBurst(level, this, bossColor());
    }

    protected boolean phaseCutscene(int phase) {
        return true;
    }

    /** Falso enquanto a vida não deve contar para as fases (ex.: Lich desabado se recompondo). */
    protected boolean canChangePhase() {
        return true;
    }

    /** Partículas no cliente (ambiente). */
    protected void clientTick() {}

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

    /** Força uma fase (ex.: Lich sem filactério) sem esperar a vida baixar. */
    protected void forcePhase(int phase) {
        if (!(level() instanceof ServerLevel sl)) return;
        while (phase() < phase) {
            entityData.set(DATA_PHASE, phase() + 1);
            onPhaseChange(sl, phase());
        }
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ClassBossEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> isAwake()));
    }

    /** v1.0: trava das falas reativas (provocação / último fôlego). */
    private final BossShow.Voice voice = new BossShow.Voice();

    private ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            configureBar(bar);
        }
        return bar;
    }

    /** Ajustes extras da barra (escurecer o céu, neblina...). */
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
        // Mundos antigos: um chefe que tenha ficado num barco/carrinho desce na hora.
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
        // Dormindo, a vida não muda (ver hurt), então as fases só andam com a luta em curso.
        if (isAwake()) checkPhase(sl);
        // v1.0: fala de último fôlego (uma vez por luta)
        if (isAwake() && tickCount % 10 == 0 && voice.lastStand(this, 0.15F)) announce("last_stand");
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

    /** Vira o corpo inteiro para um ponto (a IA está parada durante a apresentação). */
    protected void faceTowards(Entity e) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void tryWake(ServerLevel sl) {
        // Na arena da provação o chefe já nasce de frente para o grupo: acorda sem precisar de linha de visão.
        boolean trial = BossKit.isTrialBoss(this);
        double range = trial ? Math.max(40.0, BossKit.arenaRadius(this) + 8.0) : WAKE_RANGE;
        for (ServerPlayer p : sl.players()) {
            if (validPlayer(p) && p.distanceToSqr(this) < range * range && (trial || hasLineOfSight(p))) {
                startIntro(sl);
                return;
            }
        }
    }

    private static boolean validPlayer(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    /** Acorda o chefe: cutscene de apresentação para todos por perto. */
    protected void startIntro(ServerLevel sl) {
        if (isAwake()) return;
        entityData.set(DATA_AWAKE, true);
        entityData.set(DATA_INTRO, INTRO_TICKS);
        calmTicks = 0;
        getNavigation().stop();
        // Grupo maior = mais vida e golpes mais fortes (uma vez por luta; desfeito se ele voltar a dormir).
        BossKit.scaleToParty(this, PARTY_RANGE);
        Cutscenes.playNear(this, 40, "boss_intro", getDisplayName(), subtitle(), bossColor(), INTRO_TICKS + 20, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < 40 * 40)) introSeen.add(p.getUUID());
        updateBarPlayers(sl);
        announce("intro");
        // A arena fica inquebrável desde a apresentação
        BossKit.sealArena(this);
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
        // Chefe de provação nunca desiste da luta (a arena cuida da derrota do grupo).
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
    }

    /** Ponto para onde o chefe volta ao dormir de novo (padrão: centro da arena da provação, ou onde nasceu). */
    @Nullable
    protected BlockPos resetPos() {
        Vec3 arena = BossKit.arenaCenter(this);
        return arena != null ? BlockPos.containing(arena) : spawnHome;
    }

    /** Desfaz a escala de grupo: a próxima apresentação mede o grupo de novo (quem volta sozinho não enfrenta vida de 4). */
    private void unscaleParty() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(BossKit.PARTY_HEALTH_ID);
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.removeModifier(BossKit.PARTY_DAMAGE_ID);
        getPersistentData().remove(BossKit.PARTY_KEY);
    }

    /** Provação: se a luta o arrastou para fora da arena, ele volta (piscando) para perto do centro. */
    private void keepInArena(ServerLevel sl) {
        Vec3 c = BossKit.arenaCenter(this);
        double r = BossKit.arenaRadius(this);
        if (c == null || r <= 0) return;
        double dx = getX() - c.x, dz = getZ() - c.z;
        if (dx * dx + dz * dz <= (r + 3.0) * (r + 3.0) && Math.abs(getY() - c.y) < 12) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        Vec3 to = findGroundSpot(sl, c, 0.0, Math.max(1.0, r * 0.4), getType(), null);
        if (to == null) to = c;
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        sl.sendParticles(BossFx.dust(bossColor(), 1.4F), to.x, to.y + getBbHeight() / 2, to.z, 20, 0.4, 0.6, 0.4, 0);
    }

    /** Volta para a sala dele (piscando) se a luta o arrastou para longe. */
    private void returnHome(ServerLevel sl) {
        BlockPos home = resetPos();
        if (home == null) return;
        Vec3 to = Vec3.atBottomCenterOf(home);
        if (to.distanceToSqr(position()) < 9.0) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        sl.sendParticles(BossFx.dust(bossColor(), 1.4F), to.x, to.y + getBbHeight() / 2, to.z, 20, 0.4, 0.6, 0.4, 0);
    }

    /** Some com todas as invocações do chefe por perto (morte ou volta a dormir). */
    private void dismissMinions(ServerLevel sl) {
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(40), e -> e.getTags().contains(MINION_TAG) && e.isAlive())) {
            sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            e.discard();
        }
    }

    private void updateBarPlayers(ServerLevel sl) {
        ServerBossEvent b = bar();
        // Tira quem saiu de perto (ou foi para outra dimensão) e põe quem chegou.
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
            // No meio do salto: a navegação atrapalharia a trajetória calculada.
            leapTicks--;
            getNavigation().stop();
            return;
        }
        if (target != null) watchForCheese(sl, target);
        // Arena protegida viva enquanto ele estiver acordado e houver gente lutando por perto
        if (tickCount % 40 == 0) BossKit.keepArena(this, PARTY_RANGE);
        tickCombat(sl, target);
    }

    // ------------------------------------------------------------------ Anti-trapaça

    /** Alvo inalcançável por {@link #CHEESE_TICKS} ticks seguidos (e o chefe livre de golpes em preparo) → reage. */
    private void watchForCheese(ServerLevel sl, LivingEntity target) {
        if (cheeseCd > 0) cheeseCd--;
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < CHEESE_TICKS || cheeseCd > 0 || isBusy()) return;
        unreachableTicks = 0;
        cheeseCd = 80;
        onUnreachable(sl, target);
    }

    /** Verdadeiro enquanto um ataque está em preparo/execução (o anti-trapaça espera ele terminar). */
    protected boolean isBusy() {
        return false;
    }

    /**
     * Reação padrão a um alvo inalcançável: salto balístico até ele com a área de pouso telegrafada (dá para sair),
     * ou teleporte para perto se não der para saltar. Chefes de longe trocam por um teleporte com visão do alvo.
     */
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (onGround() && distanceTo(target) < 20) {
            int t = BossKit.leapAt(this, target, 1.0);
            leapTicks = t;
            float dmg = scaled(8.0F);
            Telegraph.delayed(sl, Telegraph.Shape.circle(target.position(), 3.5F).warmup(Math.max(10, t))
                    .color(Telegraph.WARNING).owner(this), hits -> landingSlam(sl, hits, dmg));
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.9F);
        } else if (BossKit.blinkNear(this, target, 4.0)) {
            getNavigation().stop();
        }
    }

    /** Pouso do salto anti-trapaça: empurra para cima e fere quem ficou na marca. */
    private void landingSlam(ServerLevel sl, List<LivingEntity> hits, float dmg) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                e.hurtMarked = true;
            }
        }
        BossFx.ring(sl, position().add(0, 0.15, 0), 2.0, 24, ParticleTypes.CLOUD, 0.08);
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
            // Sem fogo amigo: flechas dos esqueletos erguidos, poções das bruxas invocadas etc. não ferem
            // nem fazem o chefe se virar contra os próprios servos.
            if (attacker != null && attacker != this && BossAllies.isAlly(this, attacker)) return false;
            if (introTicks() > 0) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.6, getZ(),
                        8, 0.4, 0.4, 0.4, 0.1);
                return false;
            }
            if (!isAwake()) {
                // Adormecido: só um jogador o acorda; o resto (mobs, creepers, lava da sala) não o desgasta antes da luta.
                if (attacker instanceof Player p && !p.isSpectator()) startIntro((ServerLevel) level());
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    /** Chefe sempre deixa a experiência, mesmo se o golpe final não for de um jogador. */
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    /** Efeitos de controle (atordoar, congelar, enraizar, lentidão forte) duram metade num chefe. */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (effect.is(CoreRegistries.STUNNED) || effect.is(CoreRegistries.FROZEN) || effect.is(CoreRegistries.ROOTED)) {
            effect = new MobEffectInstance(effect.getEffect(), Math.max(10, effect.getDuration() / 2), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }
        return super.addEffect(effect, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level() instanceof ServerLevel sl) onDefeated(sl);
    }

    /** Derrota de verdade: XP + contador boss.<id> para todo o grupo, fala final, some com as invocações. */
    protected void onDefeated(ServerLevel sl) {
        if (lastActionAnim != null) stopTriggeredAnim("action", lastActionAnim);
        Telegraph.cancelAll(this);
        BossRewards.grant(this, REWARD_XP, REWARD_RANGE);
        announce("death");
        bar().removeAllPlayers();
        dismissMinions(sl);
        // v1.0: golpe final marcado + câmera da derrota (curta, não trava ninguém)
        BossShow.deathStart(sl, this, bossColor(), DEATH_TICKS, true);
    }

    /** v1.0: último golpe disparado no controlador "action" — interrompido na morte para não cobrir a animação "death". */
    @Nullable private String lastActionAnim;

    @Override
    public void triggerAnim(String controllerName, String animName) {
        if ("action".equals(controllerName)) lastActionAnim = animName;
        GeoEntity.super.triggerAnim(controllerName, animName);
    }

    /** v1.0: um jogador caiu pelas mãos do chefe — provocação curta (com trava: nunca em rajada). */
    public void onPlayerSlain() {
        if (level().isClientSide || isDeadOrDying() || !isAwake() || introTicks() > 0 || !voice.taunt(this)) return;
        announce("taunt");
    }

    /** Duração da morte em ticks (o renderer usa para desfazer o corpo no fim). */
    public int deathSpan() {
        return DEATH_TICKS;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropBossLoot(level, source);
    }

    /** Morte longa (a animação "death" toca inteira) com partículas da cor do chefe. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            if (deathTime % 4 == 0) {
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.01);
            }
            // v1.0: morte em três atos (vaza, converge, estoura)
            BossShow.deathTick(sl, this, deathTime, DEATH_TICKS, bossColor());
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                BossShow.deathFinale(sl, this, bossColor());
                BossFx.sphere(sl, c, getBbHeight() * 0.6, 40, BossFx.dust(bossColor(), 2.0F));
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

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** Barco/carrinho não prende chefe (trapaça clássica). */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    // ------------------------------------------------------------------ Ajudas para os chefes

    /** Fala do chefe (estilo chefe) para quem estiver perto: announce.isekai_mahou.<id>.<key>. */
    public void announce(String key) {
        Announce.send(this, Component.translatable(langKey("announce", key)), bossColor(), Announce.STYLE_BOSS);
    }

    /** Chave de tradução {@code <grupo>.isekai_mahou.<chefe>.<nome>} (ex.: announce.isekai_mahou.lich.intro). */
    protected String langKey(String group, String name) {
        return group + ".isekai_mahou." + bossId() + "." + name;
    }

    /** Dispara uma animação do controlador "action". */
    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    /** Dano próprio (magias, ondas, projéteis) ajustado ao tamanho do grupo. */
    protected float scaled(float base) {
        return base * BossKit.damageScale(this);
    }

    /** Inimigo de verdade: vivo, não aliado do chefe, e não é jogador no criativo/espectador. */
    protected boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || BossAllies.isAlly(this, e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /** Vira o corpo para um ângulo fixo (golpe com a direção travada durante o aquecimento). */
    protected void holdYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        getLookControl().setLookAt(getX() + dir.x * 6, getEyeY(), getZ() + dir.z * 6);
    }

    /** Ângulo (padrão do Minecraft) do chefe até um ponto. */
    protected float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * (180.0 / Math.PI)) - 90.0F;
    }

    /**
     * Fim de uma linha reta saindo de {@code from} na direção {@code dir} (horizontal) com até {@code max} blocos,
     * parando antes de paredes (o golpe não atravessa para a sala ao lado). Degraus, lajes e móveis de 1 bloco não
     * cortam a linha (a marca sobe neles e a faixa de acerto os cobre); cercas, muros e paredes cortam.
     */
    protected Vec3 lineEnd(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        Vec3 probe = from.add(0, 1.1, 0);
        Vec3 to = probe.add(dir.scale(max));
        HitResult hit = sl.clip(new ClipContext(probe, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? max : Math.max(1.5, hit.getLocation().distanceTo(probe) - 0.3);
        return from.add(dir.scale(len));
    }

    /** Altura do chão embaixo de um ponto (até 6 blocos abaixo): marcas e raízes nascem na superfície. */
    protected Vec3 groundAt(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        for (int i = 0; i < 7; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return new Vec3(pos.x, p.getY(), pos.z);
            p = below;
        }
        return pos;
    }

    /** Dica curta na barra de ação dos jogadores por perto — uma vez por luta para cada {@code key}. */
    protected void hintOnce(ServerLevel sl, String key) {
        if (!hintsShown.add(key)) return;
        Component msg = Component.translatable(langKey("message", key)).withStyle(net.minecraft.ChatFormatting.YELLOW);
        for (ServerPlayer p : BossKit.partyAround(this, 40)) p.displayClientMessage(msg, true);
    }

    /** Mantém distância do alvo (entre min e max), andando de lado quando está na faixa certa. */
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
        lookAt(target, 30.0F, 30.0F);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /** Vai atrás do alvo. */
    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /**
     * Teleporta para um ponto aleatório a [min, max] blocos do alvo (com chão). Só aceita lugares de onde ainda vê
     * o alvo: nada de sumir atrás de uma parede ou dentro de um bolsão de caverna fora da arena.
     */
    protected boolean blinkAround(LivingEntity target, double min, double max) {
        Vec3 from = position();
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            double x = target.getX() + Math.cos(a) * r;
            double z = target.getZ() + Math.sin(a) * r;
            double y = target.getY() + random.nextInt(5) - 1;
            if (randomTeleport(x, y, z, false)) {
                if (position().distanceToSqr(from) > 1.0 && hasLineOfSight(target)) {
                    getNavigation().stop();
                    return true;
                }
                teleportTo(from.x, from.y, from.z);
            }
        }
        return false;
    }

    /** Lugar livre no chão a [min, max] blocos de {@code center}, visível dos olhos do chefe. */
    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, EntityType<?> type) {
        return findGroundSpot(sl, center, min, max, type, getEyePosition());
    }

    /**
     * Lugar livre no chão a [min, max] blocos de {@code center} para invocar algo. Com {@code seenFrom}, o ponto
     * precisa estar à vista dali — assim nada nasce atrás de uma parede, num bolsão de caverna ou fora da sala.
     */
    @Nullable
    protected Vec3 findGroundSpot(ServerLevel sl, Vec3 center, double min, double max, EntityType<?> type, @Nullable Vec3 seenFrom) {
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            BlockPos base = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 3, center.z + Math.sin(a) * r);
            for (int dy = 0; dy < 8; dy++) {
                BlockPos p = base.below(dy);
                if (sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), net.minecraft.core.Direction.UP)
                        && sl.noCollision(type.getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
                    Vec3 spot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    if (seenFrom == null || canSee(sl, seenFrom, spot.add(0, 0.6, 0))) return spot;
                    break;
                }
            }
        }
        return null;
    }

    private boolean canSee(ServerLevel sl, Vec3 from, Vec3 to) {
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Quantas invocações deste chefe estão vivas por perto. */
    protected int countMinions(ServerLevel sl, double radius) {
        return sl.getEntities(this, getBoundingBox().inflate(radius), e -> e.getTags().contains(MINION_TAG) && e.isAlive()).size();
    }

    /** Solta {@code min..max} unidades de um item de OUTRO módulo pelo id (ignora se o módulo não existir). */
    protected void dropById(String path, int min, int max) {
        int n = min + random.nextInt(Math.max(1, max - min + 1));
        if (n <= 0) return;
        BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).ifPresent(item -> spawnAtLocation(new ItemStack(item, n)));
    }

    protected void dropItem(Item item, int min, int max) {
        int n = min + random.nextInt(Math.max(1, max - min + 1));
        if (n > 0) spawnAtLocation(new ItemStack(item, n));
    }

    /**
     * Pergaminhos de glifos ARCANOS (sem os iniciais nem lendários), sem repetir. Minichefe de meio de jogo:
     * prefere glifos de Círculo 1–3 e só completa com os mais altos se faltar opção.
     */
    protected void dropArcaneScrolls(int count) {
        List<Glyph> pool = new ArrayList<>(GlyphRegistry.all().stream()
                .filter(g -> g.element() == Element.ARCANE && g.origin() == null && !g.isLegendary()
                        && !CoreGlyphs.STARTER.contains(g.id().getPath()))
                .toList());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        pool.sort(java.util.Comparator.comparingInt(g -> g.tier() <= 3 ? 0 : 1));
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
        return loop(moving ? "walk" : "idle");
    }

    private PlayState mainPredicate(AnimationState<ClassBossEntity> state) {
        return state.setAndContinue(mainAnimation(state.isMoving()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<ClassBossEntity> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        for (String a : actionAnimations()) action.triggerableAnim(a, play(a));
        controllers.add(action);
        addExtraControllers(controllers);
    }

    /** Controladores extras (ex.: os anéis do escudo girando sem parar). */
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
        if (spawnHome != null) b.put("SpawnHome", NbtUtils.writeBlockPos(spawnHome));
        tag.put("ClassBoss", b);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag b = tag.getCompound("ClassBoss");
        entityData.set(DATA_AWAKE, b.getBoolean("Awake"));
        entityData.set(DATA_PHASE, b.getInt("Phase"));
        introSeen.clear();
        for (Tag t : b.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        spawnHome = NbtUtils.readBlockPos(b, "SpawnHome").orElse(null);
        if (bar != null) bar.setName(getDisplayName());
    }
}
