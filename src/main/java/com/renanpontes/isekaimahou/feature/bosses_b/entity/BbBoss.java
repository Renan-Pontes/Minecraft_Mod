package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.bosses_b.polish.BossShow;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos chefes do módulo (Soberano do Abismo, Serafim, Arauto do Crepúsculo). Cuida de:
 * <ul>
 *   <li>barra de chefe, apresentação com cutscene "boss_intro", fala (Announce STYLE_BOSS) e escala de grupo
 *       ({@link BossKit#scaleToParty}) na apresentação;</li>
 *   <li>fases por limiar de vida, com cutscene "boss_phase", invulnerabilidade curta e onda de choque TELEGRAFADA;</li>
 *   <li>escolha dos golpes ({@link BossAttack}) por fase, distância, recarga e peso;</li>
 *   <li>telegrafia no chão ({@link Telegraph}) com atalhos {@link #warn}, {@link #strike} e {@link #foesIn};</li>
 *   <li>janelas de corpo a corpo dos voadores ({@link #land}: pousa exausto e leva +30% de dano);</li>
 *   <li>anti-trapaça (alvo inalcançável → salto/teleporte), correntes que puxam, lacaios por id;</li>
 *   <li>modo Provação ({@link BossKit#TRIAL_TAG}): sem reset por ociosidade, a arena é a casa;</li>
 *   <li>morte longa e recompensa de grupo ({@link BossRewards#grant}: XP + contador {@code boss.<id>}).</li>
 * </ul>
 */
public abstract class BbBoss extends Monster implements GeoEntity {
    public static final String MINION_TAG = "isekai_mahou_bb_master";
    protected static final double BAR_RANGE = 64.0;
    protected static final double INTRO_RANGE = 22.0;
    protected static final double LEASH = 60.0;
    /** Dano extra recebido enquanto o voador está pousado/exausto. */
    protected static final float GROUNDED_DAMAGE = 1.3F;
    private static final int INTRO_TICKS = 70;
    protected static final int TRANSITION_TICKS = 50;
    /** Prefixo das dicas na barra de ação (ex.: "...hint.tide"). */
    private static final String HINT_PREFIX = "message.isekai_mahou.bosses_b.hint.";
    /** Raio da onda de choque telegrafada no fim de cada transição de fase. */
    protected static final float PHASE_WAVE_RADIUS = 9.0F;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_START = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_COUNT = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BEAM_SPEED = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_LEN = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GROUNDED = SynchedEntityData.defineId(BbBoss.class, EntityDataSerializers.INT);
    /** Os feixes giratórios só ferem a partir daqui: colado no chefe é seguro (e dá para bater nele). */
    public static final double BEAM_SAFE_RADIUS = 2.2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation animIdle;
    private final RawAnimation animMove;
    private final RawAnimation animDeath;
    private final RawAnimation animRest;

    private ServerBossEvent bar;
    private List<BossAttack> attacks;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<UUID, Long> beamHits = new HashMap<>();
    private final Set<UUID> introSeen = new HashSet<>();
    /** Alvos presos por correntes (projétil que acertou): puxados quando o tempo acaba. */
    private final List<ChainLink> chains = new ArrayList<>();
    private BossAttack current;
    private int attackTick;
    private int globalCooldown = 30;
    private boolean introDone;
    private int introTicks = -1;
    private int transitionTicks;
    private BlockPos home;
    private int noTargetTicks;
    private int unreachableTicks;
    private long ccImmuneUntil;
    private boolean deathHandled;
    /** v1.0: trava das falas reativas (provocação / último fôlego). */
    private final BossShow.Voice voice = new BossShow.Voice();
    /** Golpes que travam a direção (cones corpo a corpo): o chefe não gira durante o aquecimento. */
    protected float lockedYaw;
    /** Voadores: enquanto true, o controle de voo não mexe na velocidade (investidas). */
    protected boolean manualVelocity;
    /** Última animação disparada no controlador "action" (para cortá-la quando o voador pousa). */
    @Nullable
    private String lastActionAnim;

    private record ChainLink(LivingEntity target, int[] left) {}

    protected BbBoss(EntityType<? extends BbBoss> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        String p = "animation." + bossId() + ".";
        this.animIdle = RawAnimation.begin().thenLoop(p + "idle");
        this.animMove = RawAnimation.begin().thenLoop(p + "move");
        this.animDeath = RawAnimation.begin().thenPlayAndHold(p + "death");
        this.animRest = RawAnimation.begin().thenLoop(p + "rest");
        if (flies()) {
            this.moveControl = new HoverMoveControl(this);
            setNoGravity(true);
        }
    }

    // =====================================================================================
    //  O que cada chefe define
    // =====================================================================================

    /** Id da entidade (também o nome do contador {@code boss.<id>} e prefixo das traduções). */
    public abstract String bossId();

    public abstract int bossRgb();

    protected abstract BossEvent.BossBarColor barColor();

    /** Golpes do chefe (criados uma vez por instância). */
    protected abstract List<BossAttack> createAttacks();

    /** Limiares de vida (fração) das fases 2, 3...; ex.: {0.66, 0.33}. */
    protected abstract float[] phaseThresholds();

    /** Movimento em combate (quando não está num golpe parado). */
    protected abstract void moveCombat(LivingEntity target);

    /** Nomes das animações de ação (controlador "action"), sem o prefixo "animation.<id>.". */
    protected abstract List<String> actionAnimations();

    /** XP mágico dado a cada participante quando o chefe cai ({@link BossRewards#grant}). */
    protected abstract int rewardXp();

    /** Cor da telegrafia dos golpes deste chefe. */
    protected int teleColor() {
        return Telegraph.DANGER;
    }

    public boolean flies() {
        return false;
    }

    /** Partículas ambientes (só no cliente). */
    protected void clientFx() {
    }

    /** Lógica passiva por tick (servidor), depois da apresentação. */
    protected void tickBoss(ServerLevel level) {
    }

    protected void onIntroStart(ServerLevel level) {
    }

    /** Começo da transição de fase (o chefe fica invulnerável por {@link #TRANSITION_TICKS}). */
    protected void onPhaseStart(ServerLevel level, int phase) {
    }

    /** Fim da transição: bom lugar para a invocação / golpe de abertura da nova fase. */
    protected void onPhaseEnd(ServerLevel level, int phase) {
    }

    /** Quem estava dentro do círculo telegrafado quando a transição terminou (empurrão/dano próprio do chefe). */
    protected void phaseWaveHit(ServerLevel level, LivingEntity foe) {
        Vec3 push = foe.position().subtract(position()).multiply(1, 0, 1).normalize();
        foe.setDeltaMovement(push.x * 1.6, 0.5, push.z * 1.6);
        foe.hurtMarked = true;
    }

    /** Partículas da transição de fase (servidor), {@code left} = ticks restantes. */
    protected void transitionFx(ServerLevel level, int left) {
    }

    /** Ajuste do dano recebido (resistências, posturas). Já limitado por {@link #maxHitDamage()}. */
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        return amount;
    }

    protected float maxHitDamage() {
        return 60.0F;
    }

    /** Duração da morte (ticks) — o corpo fica no mundo para a animação e o espetáculo. */
    protected int deathDuration() {
        return 60;
    }

    protected void deathFx(ServerLevel level, int tick) {
    }

    protected void onFinalDeath(ServerLevel level) {
    }

    protected void onBossDeath(ServerLevel level) {
    }

    /** Duração da morte em ticks (o renderer usa para desfazer o corpo no fim). */
    public int deathSpan() {
        return deathDuration();
    }

    /** v1.0: câmera da derrota (cutscene curta no golpe final). Falso para quem já tem a própria cena de morte. */
    protected boolean killCam() {
        return true;
    }

    /**
     * v1.0: o chefe diz as últimas palavras ao cair ("last_words") e a narração "defeated" fica para o estouro final —
     * uma fala de cada vez. Falso = comportamento antigo (só a narração, na hora).
     */
    protected boolean hasLastWords() {
        return true;
    }

    /** v1.0: um jogador caiu pelas mãos do chefe — provocação curta (com trava: nunca em rajada). */
    public void onPlayerSlain() {
        if (level().isClientSide || isDeadOrDying() || !introDone || !voice.taunt(this)) return;
        announce("taunt", Announce.STYLE_BOSS);
    }

    /** Pausa entre golpes por fase. */
    protected int pauseBetweenAttacks() {
        return switch (phase()) {
            case 1 -> 26;
            case 2 -> 18;
            default -> 12;
        };
    }

    // =====================================================================================
    //  Dados / registro
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_BEAM_START, -1);
        builder.define(DATA_BEAM_COUNT, 0);
        builder.define(DATA_BEAM_SPEED, 0F);
        builder.define(DATA_BEAM_LEN, 0F);
        builder.define(DATA_GROUNDED, 0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this, BbBoss.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BbPhase", phase());
        tag.putBoolean("BbIntroDone", introDone);
        tag.putInt("BbGrounded", entityData.get(DATA_GROUNDED));
        if (home != null) tag.put("BbHome", NbtUtils.writeBlockPos(home));
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        tag.put("BbIntroSeen", seen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BbPhase")) entityData.set(DATA_PHASE, Math.max(1, tag.getInt("BbPhase")));
        introDone = tag.getBoolean("BbIntroDone");
        entityData.set(DATA_GROUNDED, Math.max(0, tag.getInt("BbGrounded")));
        home = NbtUtils.readBlockPos(tag, "BbHome").orElse(null);
        introSeen.clear();
        for (Tag t : tag.getList("BbIntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        if (bar != null) bar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bar != null) bar.setName(getDisplayName());
    }

    // =====================================================================================
    //  Regras gerais de chefe
    // =====================================================================================

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Asas, chifres e auréolas passam bem da caixa de colisão: não sumir na borda da tela. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0, 1.0, 2.0);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!flies()) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof BbBoss || isMinion(other)) return true;
        if (other instanceof Vex v && v.getOwner() == this) return true;
        return super.isAlliedTo(other);
    }

    /** Controle (atordoar, congelar, prender, lentidão...) dura no máximo 1,5 s e dá 5 s de imunidade: o chefe não fica preso. */
    @Override
    public boolean addEffect(MobEffectInstance inst, @Nullable Entity source) {
        if (isCrowdControl(inst.getEffect())) {
            long now = level().getGameTime();
            if (now < ccImmuneUntil) return false;
            ccImmuneUntil = now + 100;
            inst = new MobEffectInstance(inst.getEffect(), Math.min(inst.getDuration(), 30), Math.min(inst.getAmplifier(), 1),
                    inst.isAmbient(), inst.isVisible(), inst.showIcon());
        }
        return super.addEffect(inst, source);
    }

    private static boolean isCrowdControl(Holder<MobEffect> e) {
        MobEffect v = e.value();
        return v == CoreRegistries.STUNNED.get() || v == CoreRegistries.FROZEN.get() || v == CoreRegistries.ROOTED.get()
                || v == CoreRegistries.SILENCED.get() || v == MobEffects.MOVEMENT_SLOWDOWN.value()
                || v == MobEffects.LEVITATION.value() || v == MobEffects.BLINDNESS.value() || v == MobEffects.DARKNESS.value()
                || v == MobEffects.WEAKNESS.value();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.is(DamageTypes.IN_WALL)) {
            escapeWall();
            return false;
        }
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && isAlliedTo(attacker)) return false;
        if (!introDone && introTicks < 0 && attacker instanceof Player p && !p.isSpectator()) startIntro((ServerLevel) level());
        if (introTicks >= 0 || transitionTicks > 0) {
            shieldFx();
            return false;
        }
        amount = Math.min(amount, maxHitDamage());
        amount = modifyIncomingDamage(source, amount);
        if (amount <= 0) return false;
        if (isGrounded()) {
            amount *= GROUNDED_DAMAGE;
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.5, 0.6, 0.5, 0.25);
            }
        }
        return super.hurt(source, amount);
    }

    private void shieldFx() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.5, getZ(), 12, 0.7, 1.0, 0.7, 0.2);
        playSound(SoundEvents.SHIELD_BLOCK, 0.8F, 0.7F);
    }

    /** Preso num bloco: sobe/teleporta para um espaço livre perto. */
    private void escapeWall() {
        for (int i = 0; i < 12; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 8;
            double z = getZ() + (random.nextDouble() - 0.5) * 8;
            if (randomTeleport(x, getY() + random.nextInt(4), z, true)) return;
        }
        setPos(getX(), getY() + 2, getZ());
    }

    // =====================================================================================
    //  Modo Provação / casa
    // =====================================================================================

    /** Chefe de Câmara de Provação (o módulo trials marca): não reseta por ociosidade e a arena é a casa. */
    public boolean trialMode() {
        return BossKit.isTrialBoss(this);
    }

    /** Casa do chefe: centro da arena da Provação (se houver) ou o ponto onde nasceu. */
    @Nullable
    protected Vec3 homePos() {
        Vec3 arena = BossKit.arenaCenter(this);
        if (arena != null) return arena;
        return home != null ? Vec3.atBottomCenterOf(home) : null;
    }

    /** Até onde o chefe persegue alguém a partir da casa. */
    protected double leash() {
        double r = BossKit.arenaRadius(this);
        return r > 0 ? r + 16 : LEASH;
    }

    private double introRange() {
        double r = BossKit.arenaRadius(this);
        return r > 0 ? Math.max(INTRO_RANGE, r + 10) : INTRO_RANGE;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && isAlive()) {
            clientFx();
            if (isGrounded()) groundedFx();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (home == null) home = blockPosition();
        tickBar(sl);

        if (!introDone) {
            tickIntro(sl);
            return;
        }
        if (tickCount % 20 == 0) lateIntros(sl);
        checkPhase(sl);
        // v1.0: fala de último fôlego (uma vez por luta, fora das transições)
        if (tickCount % 10 == 0 && transitionTicks <= 0 && voice.lastStand(this, 0.15F)) announce("last_stand", Announce.STYLE_BOSS);
        tickChains(sl);
        if (transitionTicks > 0) {
            transitionTicks--;
            stopMoving();
            transitionFx(sl, transitionTicks);
            if (transitionTicks == 0) {
                // A onda de choque acerta só quem ficou no círculo telegrafado desde o começo da transição.
                for (LivingEntity e : foesIn(Telegraph.Shape.circle(groundBelow(), PHASE_WAVE_RADIUS))) phaseWaveHit(sl, e);
                onPhaseEnd(sl, phase());
            }
            return;
        }

        tickBoss(sl);
        // Arena protegida viva enquanto houver luta (pausas sem alvo, descanso, domínio, contra invocações)
        if (tickCount % 40 == 0) BossKit.keepArena(this, 48);

        LivingEntity target = getTarget();
        if (target != null && !validTarget(target)) {
            setTarget(null);
            target = null;
        }
        if (current == null && isGrounded()) {
            tickGrounded(sl, target);
            return;
        }
        if (target != null) {
            noTargetTicks = 0;
            combatTick(sl, target);
        } else {
            if (current != null) cancelAttack();
            idleTick(sl);
        }
    }

    private void tickBar(ServerLevel sl) {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setPlayBossMusic(true);
        }
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (tickCount % 10 != 0) return;
        boolean show = introDone || introTicks >= 0;
        for (ServerPlayer p : sl.players()) {
            boolean near = show && p.isAlive() && p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
            if (near) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
    }

    protected ServerBossEvent bossBar() {
        return bar;
    }

    // ---------------- Apresentação ----------------
    private void tickIntro(ServerLevel sl) {
        stopMoving();
        if (introTicks >= 0) {
            Player look = sl.getNearestPlayer(this, 40);
            if (look != null) getLookControl().setLookAt(look, 10F, 10F);
            introTicks--;
            if (introTicks < 0) {
                introDone = true;
                globalCooldown = 20;
            }
            return;
        }
        if (tickCount % 10 != 0) return;
        double r = introRange();
        boolean trial = trialMode();
        for (ServerPlayer p : sl.players()) {
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(this) < r * r && (trial || hasLineOfSight(p))) {
                startIntro(sl);
                return;
            }
        }
    }

    private void startIntro(ServerLevel sl) {
        if (introTicks >= 0 || introDone) return;
        introTicks = INTRO_TICKS;
        // Grupo maior = chefe mais forte (vida e dano), aplicado uma única vez.
        BossKit.scaleToParty(this, 48);
        triggerAnim("action", "intro");
        double r = introRange() + 20;
        Cutscenes.playNear(this, r, "boss_intro", getDisplayName(), title("subtitle"), bossRgb(), 80, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < r * r)) introSeen.add(p.getUUID());
        announce("intro", Announce.STYLE_BOSS);
        BossKit.shake(sl, position(), 36, 1.0F, 30);
        // A arena fica inquebrável desde a apresentação
        BossKit.sealArena(this);
        onIntroStart(sl);
    }

    /** Quem chega depois vê uma cutscene curta individual. */
    private void lateIntros(ServerLevel sl) {
        double r = introRange();
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > r * r || !hasLineOfSight(p)) continue;
            if (introSeen.add(p.getUUID())) {
                Cutscenes.play(p, "boss_intro", this, getDisplayName(), title("subtitle"), bossRgb(), 60, false);
            }
        }
    }

    // ---------------- Fases ----------------
    private void checkPhase(ServerLevel sl) {
        if (transitionTicks > 0) return;
        float frac = getHealth() / getMaxHealth();
        float[] th = phaseThresholds();
        int want = 1;
        for (float t : th) if (frac <= t) want++;
        int ph = phase();
        if (want <= ph) return;
        int next = ph + 1;
        entityData.set(DATA_PHASE, next);
        cancelAttack();
        // Troca de fase "zera" a arena: golpes pendentes somem (sem dano) e o voador levanta.
        Telegraph.cancelAll(this);
        entityData.set(DATA_GROUNDED, 0);
        manualVelocity = false;
        transitionTicks = TRANSITION_TICKS;
        triggerAnim("action", "phase");
        Cutscenes.playNear(this, 40, "boss_phase", getDisplayName(), title("phase" + next), bossRgb(), 50, null);
        announce("phase" + next, Announce.STYLE_BOSS);
        warn(Telegraph.Shape.circle(groundBelow(), PHASE_WAVE_RADIUS).warmup(TRANSITION_TICKS).color(teleColor()));
        BossKit.shake(sl, position(), 32, 1.2F, 24);
        onPhaseStart(sl, next);
    }

    // ---------------- Combate ----------------
    private void combatTick(ServerLevel sl, LivingEntity target) {
        if (current != null) {
            if (current.stationary) stopMoving();
            else moveCombat(target);
            if (current.faceTarget) faceTarget(target);
            BossAttack a = current;
            a.tick.tick(target, attackTick);
            if (current != a) return; // o golpe pode ter se cancelado
            attackTick++;
            if (attackTick >= a.duration) finishAttack();
            return;
        }
        moveCombat(target);
        getLookControl().setLookAt(target, 30F, 30F);
        antiCheese(sl, target);
        if (current != null) return; // o anti-trapaça pode ter começado um golpe (salto)
        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }
        BossAttack next = pick(target);
        if (next != null) startAttack(next);
        else globalCooldown = 5; // nada disponível (recargas/distância): tenta de novo em 1/4 s, sem varrer entidades todo tick
    }

    private BossAttack pick(LivingEntity target) {
        if (attacks == null) attacks = createAttacks();
        long now = level().getGameTime();
        double dist = distanceTo(target);
        int ph = phase();
        List<BossAttack> ok = new ArrayList<>();
        int total = 0;
        for (BossAttack a : attacks) {
            if (ph < a.minPhase || dist < a.minRange || dist > a.maxRange) continue;
            if (now < readyAt.getOrDefault(a.id, 0L) || !a.condition.test(target)) continue;
            ok.add(a);
            total += a.weight;
        }
        if (ok.isEmpty()) return null;
        int r = random.nextInt(total);
        for (BossAttack a : ok) {
            r -= a.weight;
            if (r < 0) return a;
        }
        return ok.get(0);
    }

    /** Começa um golpe específico agora (ex.: Expansão de Domínio ao entrar na fase final). */
    protected void startAttack(@Nullable BossAttack a) {
        if (a == null) return;
        if (current != null) cancelAttack();
        current = a;
        attackTick = 0;
        float cdMult = 1.0F - 0.15F * (phase() - 1);
        readyAt.put(a.id, level().getGameTime() + Math.max(20, (int) (a.cooldown * cdMult)));
        if (a.anim != null) triggerAnim("action", a.anim);
    }

    @Nullable
    protected BossAttack attack(String id) {
        if (attacks == null) attacks = createAttacks();
        for (BossAttack a : attacks) if (a.id.equals(id)) return a;
        return null;
    }

    private void finishAttack() {
        BossAttack a = current;
        current = null;
        manualVelocity = false;
        if (a != null && a.onEnd != null) a.onEnd.run();
        globalCooldown = pauseBetweenAttacks();
    }

    /** Termina o golpe atual agora, como se tivesse chegado ao fim (pausa normal entre golpes). */
    protected void endAttack() {
        if (current != null) finishAttack();
    }

    protected void cancelAttack() {
        BossAttack a = current;
        current = null;
        manualVelocity = false;
        if (a != null && a.onEnd != null) a.onEnd.run();
        globalCooldown = 20;
    }

    @Nullable
    protected BossAttack currentAttack() {
        return current;
    }

    /** O golpe já saiu da recarga? */
    protected boolean isReady(String id) {
        return level().getGameTime() >= readyAt.getOrDefault(id, 0L);
    }

    private void idleTick(ServerLevel sl) {
        noTargetTicks++;
        Vec3 h = homePos();
        if (h == null || noTargetTicks < 60) return;
        double d2 = distanceToSqr(h);
        double far = trialMode() ? Math.max(10, BossKit.arenaRadius(this)) : 48;
        if (d2 > far * far) {
            teleportHome(sl, h);
        } else if (d2 > 9) {
            if (flies()) hoverToward(h.add(0, 1.5, 0), 0.8);
            else getNavigation().moveTo(h.x, h.y, h.z, 0.9);
        }
        // Sem ninguém por perto, o chefe de mundo aberto se recompõe aos poucos (na Provação, nunca).
        if (!trialMode() && noTargetTicks > 200 && tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
            heal(getMaxHealth() * 0.01F);
        }
    }

    private void teleportHome(ServerLevel sl, Vec3 h) {
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.05);
        teleportTo(h.x, h.y, h.z);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, h.x, h.y + 1.5, h.z, 40, 0.6, 1.2, 0.6, 0.05);
    }

    /**
     * Anti-trapaça: alvo sem caminho (pilar, buraco, barco) por 2 s — ou, para voadores, escondido fora da visão
     * por 4 s — faz o chefe reagir ({@link #onUnreachable}).
     */
    private void antiCheese(ServerLevel sl, LivingEntity target) {
        if (tickCount % 5 != 0) return;
        boolean stuck = flies()
                ? !hasLineOfSight(target) && distanceToSqr(target) > 16
                : BossKit.isUnreachable(this, target);
        unreachableTicks = stuck ? unreachableTicks + 5 : 0;
        if (unreachableTicks < (flies() ? 80 : 40)) return;
        unreachableTicks = 0;
        onUnreachable(sl, target);
    }

    /** Não consegue chegar ao alvo: por padrão aparece perto dele. */
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (!BossKit.blinkNear(this, target, 4.0)) blinkNear(target, 4.0);
    }

    /** Teleporta para perto do alvo com partículas. @return true se conseguiu */
    protected boolean blinkNear(LivingEntity target, double radius) {
        if (!(level() instanceof ServerLevel sl)) return false;
        Vec3 from = position();
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double x = target.getX() + Math.cos(a) * radius;
            double z = target.getZ() + Math.sin(a) * radius;
            if (randomTeleport(x, target.getY() + (flies() ? 2 : 0), z, false)) {
                sl.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.5, from.z, 50, 0.5, 1.2, 0.5, 0.3);
                sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 50, 0.5, 1.2, 0.5, 0.3);
                BbFx.sound(sl, position(), SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
                return true;
            }
        }
        return false;
    }

    protected boolean validTarget(LivingEntity t) {
        if (!t.isAlive() || t.isRemoved() || t.level() != level() || isAlliedTo(t)) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        Vec3 h = homePos();
        double leash = leash();
        return h == null || t.distanceToSqr(h) < leash * leash;
    }

    // =====================================================================================
    //  Pouso (janela de corpo a corpo dos voadores)
    // =====================================================================================

    public boolean isGrounded() {
        return entityData.get(DATA_GROUNDED) > 0;
    }

    /**
     * O voador pousa exausto por {@code ticks}: desce até o chão, não ataca e leva +30% de dano.
     * Avisa os jogadores por perto ("é a sua chance!").
     */
    protected void land(int ticks) {
        if (!flies() || ticks <= 0 || !isAlive() || !(level() instanceof ServerLevel sl)) return;
        entityData.set(DATA_GROUNDED, ticks);
        manualVelocity = false;
        // A pose de exausto ("rest", controlador principal) assume: corta o golpe que ainda estivesse animando.
        if (lastActionAnim != null) stopTriggeredAnim("action", lastActionAnim);
        BbFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.5F);
        BbFx.sound(sl, position(), SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
        for (ServerPlayer p : BossKit.partyAround(this, 40)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.bosses_b.grounded", getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    private void tickGrounded(ServerLevel sl, @Nullable LivingEntity target) {
        int left = entityData.get(DATA_GROUNDED) - 1;
        entityData.set(DATA_GROUNDED, left);
        Vec3 ground = groundBelow();
        double above = getY() - ground.y;
        if (above > 0.6 && left > 0) {
            // Cai de uma vez (asas sem força) em vez de descer flutuando: a janela no chão fica inteira para o grupo.
            manualVelocity = true;
            Vec3 dm = getDeltaMovement();
            setDeltaMovement(dm.x * 0.5, Math.max(-0.9, Math.min(0.0, dm.y) - 0.12), dm.z * 0.5);
            if (above < 1.6 && dm.y < -0.3) {
                sl.sendParticles(ParticleTypes.CLOUD, getX(), ground.y + 0.2, getZ(), 12, 0.9, 0.05, 0.9, 0.04);
            }
        } else {
            manualVelocity = false;
            hoverToward(new Vec3(getX(), ground.y + 0.05, getZ()), 1.4);
        }
        if (target != null) getLookControl().setLookAt(target, 8F, 8F);
        if (left % 10 == 0) {
            sl.sendParticles(BbFx.dust(bossRgb(), 1.2F), getX(), ground.y + 0.2, getZ(), 6, 0.8, 0.05, 0.8, 0);
        }
        if (left == 0) {
            manualVelocity = false;
            globalCooldown = Math.min(globalCooldown, 10);
            BbFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.9F);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), ground.y + 0.3, getZ(), 20, 1.0, 0.1, 1.0, 0.08);
        }
    }

    /** Cliente: estrelinhas girando sobre a cabeça enquanto está exausto. */
    private void groundedFx() {
        if (tickCount % 2 != 0) return;
        double a = tickCount * 0.35;
        double y = getY() + getBbHeight() + 0.2;
        level().addParticle(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.8, y, getZ() + Math.sin(a) * 0.8, 0, 0, 0);
        level().addParticle(ParticleTypes.WAX_ON, getX() + Math.cos(a + Math.PI) * 0.8, y, getZ() + Math.sin(a + Math.PI) * 0.8, 0, 0, 0);
    }

    // =====================================================================================
    //  Telegrafia (kit do núcleo)
    // =====================================================================================

    /** Só mostra a forma (aviso). O dono é o chefe: some sozinha se ele morrer. */
    protected int warn(Telegraph.Shape s) {
        return level() instanceof ServerLevel sl ? Telegraph.show(sl, s.owner(this)) : -1;
    }

    /**
     * Golpe atrasado: mostra a forma e, no fim do aquecimento, entrega a lista dos ADVERSÁRIOS que ainda estão dentro
     * (pode vir vazia — use para o visual do impacto). Não roda se o chefe morreu ou mudou de fase.
     */
    protected int strike(Telegraph.Shape s, Consumer<List<LivingEntity>> onFoes) {
        if (!(level() instanceof ServerLevel sl)) return -1;
        return Telegraph.delayed(sl, s.owner(this), hits -> {
            if (!isAlive()) return;
            hits.removeIf(e -> !isFoe(e));
            onFoes.accept(hits);
        });
    }

    /** Adversários dentro da forma agora. */
    protected List<LivingEntity> foesIn(Telegraph.Shape s) {
        if (!(level() instanceof ServerLevel sl)) return List.of();
        List<LivingEntity> list = new ArrayList<>(Telegraph.entitiesIn(sl, s));
        list.removeIf(e -> !isFoe(e));
        return list;
    }

    /** Cone corpo a corpo à frente (direção travada): mostra agora e acerta {@code warmup} ticks depois. */
    protected void meleeCone(float angle, float range, int warmup, Consumer<LivingEntity> onFoe) {
        strike(Telegraph.Shape.cone(groundBelow(), getYRot(), angle, range).warmup(warmup).color(teleColor()).noRunes(),
                foes -> foes.forEach(onFoe));
    }

    /** Linha do chão (Y dos pés de {@code at}) a partir de {@code from} na direção horizontal de {@code dir}. */
    protected static Vec3 groundAhead(Vec3 from, Vec3 dir, double length, double y) {
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
        return new Vec3(from.x + h.x * length, y, from.z + h.z * length);
    }

    /** Aviso na barra de ação para quem luta (mecânicas que não são óbvias). */
    protected void hint(String key) {
        for (ServerPlayer p : BossKit.partyAround(this, 48)) {
            p.displayClientMessage(Component.translatable(HINT_PREFIX + key)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // =====================================================================================
    //  Ajudantes para os golpes
    // =====================================================================================

    protected void stopMoving() {
        getNavigation().stop();
        if (flies()) getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
    }

    protected void hoverToward(Vec3 pos, double speed) {
        getMoveControl().setWantedPosition(pos.x, pos.y, pos.z, speed);
    }

    /** Vira o corpo e a cabeça para o alvo (as magias de glifo usam a direção do olhar). */
    public void faceTarget(Entity t) {
        Vec3 eye = getEyePosition();
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
        double dx = to.x - eye.x;
        double dy = to.y - eye.y;
        double dz = to.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    /** Encara o alvo e trava essa direção ({@link #holdFacing}) — golpes telegrafados não "seguem" o jogador. */
    protected void lockFacing(LivingEntity t) {
        faceTarget(t);
        lockedYaw = getYRot();
    }

    protected void holdFacing() {
        setYRot(lockedYaw);
        yHeadRot = lockedYaw;
        yBodyRot = lockedYaw;
    }

    /** Chão logo abaixo do chefe (voadores flutuam). */
    protected Vec3 groundBelow() {
        Vec3 from = position().add(0, 0.5, 0);
        HitResult hit = level().clip(new ClipContext(from, from.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? position() : hit.getLocation();
    }

    /** Chão sob um ponto qualquer (procura de {@code up} acima até {@code down} abaixo), ou null se for vazio. */
    @Nullable
    protected Vec3 groundAt(double x, double y, double z, double up, double down) {
        Vec3 from = new Vec3(x, y + up, z);
        HitResult hit = level().clip(new ClipContext(from, new Vec3(x, y - down, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    protected float attackPower() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Poder das magias de glifo do chefe, já com a escala de grupo. */
    protected float glyphPower(float base) {
        return base * BossKit.damageScale(this);
    }

    public DamageSource spellSource(@Nullable Entity direct) {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, direct != null ? direct : this, this);
    }

    /** Inimigos vivos numa esfera (só quem é {@link #isFoe adversário}). */
    public List<LivingEntity> enemiesNear(Vec3 c, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> isFoe(e) && e.distanceToSqr(c) <= r * r);
    }

    /**
     * Quem os golpes em área do chefe atingem: jogadores (fora do criativo/espectador), bichos e invocações com dono e
     * quem estiver brigando com ele ou com os lacaios. Vacas, aldeões e monstros neutros da arena ficam de fora.
     */
    public boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand || isAlliedTo(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e == getTarget() || e == getLastHurtByMob()) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        if (!(e instanceof Mob m) || m.getTarget() == null) return false;
        return m.getTarget() == this || isAlliedTo(m.getTarget());
    }

    protected void announce(String key, int style) {
        Announce.send(this, text(key), bossRgb(), style);
    }

    /** Fala do chefe: "announce.isekai_mahou.<id>.<chave>". */
    protected Component text(String key) {
        return Component.translatable(langKey("announce", key));
    }

    /** Título/subtítulo de cutscene: "boss.isekai_mahou.<id>.<chave>". */
    protected Component title(String key) {
        return Component.translatable(langKey("boss", key));
    }

    private String langKey(String kind, String key) {
        return kind + "." + IsekaiMahou.MODID + "." + bossId() + "." + key;
    }

    // ---------------- Correntes (projétil que acerta prende e depois puxa) ----------------

    /** Um projétil do chefe acertou alguém (correntes: prende e puxa depois de {@code pullDelay} ticks). */
    public void onBoltHit(BossBolt bolt, LivingEntity hit) {
        if (bolt.kind() != BossBolt.KIND_CHAIN || !isFoe(hit)) return;
        hit.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 30, 0), this);
        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), this);
        chains.removeIf(c -> c.target == hit);
        chains.add(new ChainLink(hit, new int[] {16}));
        if (level() instanceof ServerLevel sl) BbFx.sound(sl, hit.position(), SoundEvents.CHAIN_PLACE, 1.5F, 0.5F);
    }

    /** Um projétil do chefe estourou (no alvo ou num bloco) em {@code at}. */
    public void onBoltImpact(BossBolt bolt, Vec3 at) {
    }

    /** Mão de onde saem correntes/magias (padrão: à frente, na altura do peito). */
    protected Vec3 castHand() {
        return position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.0)).add(0, getBbHeight() * 0.8, 0);
    }

    private void tickChains(ServerLevel sl) {
        if (chains.isEmpty()) return;
        Iterator<ChainLink> it = chains.iterator();
        Vec3 hand = castHand();
        while (it.hasNext()) {
            ChainLink c = it.next();
            LivingEntity e = c.target;
            if (!e.isAlive() || e.level() != level() || e.distanceToSqr(this) > 48 * 48) {
                it.remove();
                continue;
            }
            int left = --c.left[0];
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
            if (left % 2 == 0) BbFx.line(sl, BbFx.dust(left % 4 == 0 ? BbFx.SHADOW_DEEP : BbFx.SHADOW, 1.1F), hand, to, 0.5);
            if (left > 0) continue;
            it.remove();
            Vec3 pull = position().subtract(e.position());
            double d = pull.length();
            if (d > 0.1) {
                Vec3 v = pull.scale(Math.min(1.8, d * 0.25) / d);
                e.setDeltaMovement(v.x, 0.45, v.z);
                e.hurtMarked = true;
            }
            e.hurt(spellSource(null), attackPower() * 0.4F);
            e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
            BbFx.sound(sl, e.position(), SoundEvents.CHAIN_BREAK, 1.5F, 0.6F);
        }
    }

    // ---------------- Lacaios ----------------
    public boolean isMinion(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.hasUUID(MINION_TAG) && d.getUUID(MINION_TAG).equals(getUUID());
    }

    public int minionCount() {
        return level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), m -> m.isAlive() && isMinion(m)).size();
    }

    /** Existe a entidade (de outro módulo) com este id? */
    protected static boolean entityExists(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id)).isPresent();
    }

    /**
     * Invoca um lacaio pelo id (de outro módulo); se não existir, usa {@code fallback}.
     * O lacaio fica aliado do chefe e some quando o chefe morre.
     */
    @Nullable
    protected Mob summon(String id, @Nullable EntityType<? extends Mob> fallback, Vec3 at, @Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id)).orElse(null);
        Entity e = type != null ? type.create(sl) : null;
        if (!(e instanceof Mob)) {
            if (e != null) e.discard();
            e = fallback != null ? fallback.create(sl) : null;
        }
        if (!(e instanceof Mob mob)) return null;
        mob.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, sl, sl.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        mob.getPersistentData().putUUID(MINION_TAG, getUUID());
        if (mob instanceof Vex vex) {
            vex.setOwner(this);
            vex.setLimitedLife(20 * 30);
            vex.setBoundOrigin(BlockPos.containing(at));
        }
        if (target != null) mob.setTarget(target);
        sl.addFreshEntity(mob);
        return mob;
    }

    /** Pontos de invocação ao redor (no chão), já marcados com um círculo de aviso que se completa em {@code warmup}. */
    protected List<Vec3> summonSpots(int n, double radius, int warmup) {
        List<Vec3> spots = new ArrayList<>();
        Vec3 c = groundBelow();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + random.nextDouble() * 0.6;
            Vec3 g = groundAt(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 3, 5);
            Vec3 at = g != null ? g : c;
            spots.add(at);
            warn(Telegraph.Shape.circle(at, 1.3F).warmup(warmup).color(teleColor()).noFlash().noRunes());
        }
        return spots;
    }

    private void discardMinions(ServerLevel sl) {
        for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(64), this::isMinion)) {
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            m.discard();
        }
    }

    // ---------------- Feixes giratórios (Serafim, Arauto) ----------------
    public boolean beamsActive() {
        return entityData.get(DATA_BEAM_START) >= 0 && entityData.get(DATA_BEAM_COUNT) > 0;
    }

    /** Liga N feixes horizontais girando a {@code speed} graus/tick, com {@code length} blocos. */
    protected void startBeams(int count, float speed, float length) {
        entityData.set(DATA_BEAM_START, (int) level().getGameTime());
        entityData.set(DATA_BEAM_COUNT, count);
        entityData.set(DATA_BEAM_SPEED, speed);
        entityData.set(DATA_BEAM_LEN, length);
        beamHits.clear();
    }

    /** Inverte o giro SEM pular: os feixes continuam de onde estavam, agora no outro sentido. */
    protected void reverseBeams(float newSpeed) {
        if (!beamsActive() || newSpeed == 0F) return;
        long now = level().getGameTime();
        double angle = (now - entityData.get(DATA_BEAM_START)) * entityData.get(DATA_BEAM_SPEED);
        entityData.set(DATA_BEAM_START, (int) Math.round(now - angle / newSpeed));
        entityData.set(DATA_BEAM_SPEED, newSpeed);
    }

    protected void stopBeams() {
        entityData.set(DATA_BEAM_START, -1);
        entityData.set(DATA_BEAM_COUNT, 0);
    }

    public int beamCount() {
        return entityData.get(DATA_BEAM_COUNT);
    }

    public Vec3 beamOrigin() {
        return position().add(0, 1.1, 0);
    }

    /** Ângulo (radianos) do feixe {@code i}, igual no servidor e no cliente (usa o tempo do mundo). */
    public double beamAngle(int i, float partial) {
        double t = (level().getGameTime() - entityData.get(DATA_BEAM_START)) + partial;
        int n = Math.max(1, beamCount());
        return Math.toRadians(t * entityData.get(DATA_BEAM_SPEED) + i * 360.0 / n);
    }

    /** Alcance do feixe {@code i} (para nas paredes). */
    public double beamReach(int i, float partial) {
        Vec3 o = beamOrigin();
        double a = beamAngle(i, partial);
        float len = entityData.get(DATA_BEAM_LEN);
        Vec3 end = o.add(Math.cos(a) * len, 0, Math.sin(a) * len);
        HitResult hit = level().clip(new ClipContext(o, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? len : hit.getLocation().distanceTo(o);
    }

    /** Telegrafia dos feixes ao redor de {@code c} (chão onde o chefe vai girar): posições iniciais e o miolo seguro. */
    protected void warnBeams(Vec3 c, int count, float length, int warmup, int duration, int rgb) {
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(i * 360.0 / count);
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            warn(Telegraph.Shape.line(c.add(d.scale(BEAM_SAFE_RADIUS)), c.add(d.scale(length)), 1.2F).warmup(warmup).color(rgb));
        }
        warn(Telegraph.Shape.safe(c, (float) BEAM_SAFE_RADIUS - 0.2F).warmup(warmup + duration));
    }

    /** Servidor: aplica dano de quem cruzar os feixes (cada alvo no máximo a cada 10 ticks). Colado no chefe é seguro. */
    protected void tickBeamDamage(float damage, @Nullable Consumer<LivingEntity> onHit) {
        if (!beamsActive()) return;
        Vec3 o = beamOrigin();
        float len = entityData.get(DATA_BEAM_LEN);
        List<LivingEntity> cand = enemiesNear(o, len + 1.5);
        if (cand.isEmpty()) return;
        long now = level().getGameTime();
        for (int i = 0; i < beamCount(); i++) {
            double a = beamAngle(i, 0F);
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            double reach = beamReach(i, 0F);
            for (LivingEntity e : cand) {
                if (beamHits.getOrDefault(e.getUUID(), 0L) > now) continue;
                double dx = e.getX() - o.x;
                double dz = e.getZ() - o.z;
                double along = dx * cx + dz * cz;
                if (along < BEAM_SAFE_RADIUS || along > reach + 0.3) continue;
                double perp = Math.abs(dx * cz - dz * cx);
                if (perp > 0.55 + e.getBbWidth() / 2) continue;
                if (o.y < e.getY() - 0.4 || o.y > e.getY() + e.getBbHeight() + 0.3) continue;
                if (e.hurt(spellSource(null), damage) && onHit != null) onHit.accept(e);
                beamHits.put(e.getUUID(), now + 10);
            }
        }
    }

    /** Cliente: desenha os feixes com partículas. */
    protected void drawBeamsClient(ParticleOptions core, ParticleOptions accent) {
        if (!beamsActive()) return;
        Vec3 o = beamOrigin();
        double step = reducedParticles() ? 1.5 : 0.8;
        for (int i = 0; i < beamCount(); i++) {
            double a = beamAngle(i, 0F);
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            double reach = beamReach(i, 0F);
            for (double d = BEAM_SAFE_RADIUS; d < reach; d += step) {
                double j = (random.nextDouble() - 0.5) * 0.12;
                level().addParticle(core, o.x + cx * d + j, o.y + j, o.z + cz * d - j, 0, 0, 0);
            }
            double r = BEAM_SAFE_RADIUS + random.nextDouble() * Math.max(0.5, reach - BEAM_SAFE_RADIUS);
            level().addParticle(accent, o.x + cx * r, o.y, o.z + cz * r, 0, 0.02, 0);
            level().addParticle(accent, o.x + cx * reach, o.y, o.z + cz * reach, (random.nextDouble() - 0.5) * 0.1, 0.05,
                    (random.nextDouble() - 0.5) * 0.1);
        }
    }

    /** Config de cliente "partículas reduzidas" (falso se não carregada). */
    protected static boolean reducedParticles() {
        try {
            return !com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // =====================================================================================
    //  Morte
    // =====================================================================================

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel sl) || !this.dead || deathHandled) return;
        deathHandled = true;
        if (current != null) cancelAttack();
        stopBeams();
        chains.clear();
        entityData.set(DATA_GROUNDED, 0);
        Telegraph.cancelAll(this);
        // XP + contador boss.<id> para todo o grupo (inclusive quem ajudou de longe nos últimos 30 s).
        BossRewards.grant(this, rewardXp(), 48);
        announce(hasLastWords() ? "last_words" : "defeated", Announce.STYLE_BOSS);
        if (bar != null) bar.removeAllPlayers();
        discardMinions(sl);
        BossKit.shake(sl, position(), 40, 1.5F, 40);
        // v1.0: golpe final marcado + câmera da derrota (curta, não trava ninguém)
        BossShow.deathStart(sl, this, bossRgb(), deathDuration(), killCam());
        onBossDeath(sl);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            deathFx(sl, deathTime);
            BossShow.deathTick(sl, this, deathTime, deathDuration(), bossRgb());
            if (deathTime >= deathDuration() && !isRemoved()) {
                if (hasLastWords()) announce("defeated", Announce.STYLE_BOSS);
                BossShow.deathFinale(sl, this, bossRgb());
                onFinalDeath(sl);
                level().broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (bar != null) bar.removeAllPlayers();
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BbBoss> main = new AnimationController<>(this, "main", 6, this::mainState);
        AnimationController<BbBoss> action = new AnimationController<>(this, "action", 3, s -> PlayState.STOP);
        String p = "animation." + bossId() + ".";
        List<String> all = new ArrayList<>(actionAnimations());
        all.add("intro");
        all.add("phase");
        for (String a : all) action.triggerableAnim(a, RawAnimation.begin().thenPlay(p + a));
        controllers.add(main, action);
    }

    private PlayState mainState(AnimationState<BbBoss> state) {
        if (isDeadOrDying()) return state.setAndContinue(animDeath);
        if (flies() && isGrounded()) return state.setAndContinue(animRest);
        double dx = getX() - xo;
        double dz = getZ() - zo;
        boolean moving = dx * dx + dz * dz > 2.5E-4;
        return state.setAndContinue(moving ? animMove : animIdle);
    }

    @Override
    public void triggerAnim(String controllerName, String animName) {
        if ("action".equals(controllerName)) lastActionAnim = animName;
        GeoEntity.super.triggerAnim(controllerName, animName);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Voo (estilo Ghast, suave): acelera em direção ao ponto desejado
    // =====================================================================================
    public static class HoverMoveControl extends MoveControl {
        public HoverMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (mob instanceof BbBoss b && b.manualVelocity) return;
            LivingEntity target = mob.getTarget();
            // Em combate, entre golpes, o corpo encara o alvo enquanto orbita (nos golpes quem gira é o próprio golpe).
            if (target != null && mob instanceof BbBoss b && b.current == null && b.isAlive()) {
                float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 12F));
                mob.yBodyRot = mob.getYRot();
            }
            if (operation != Operation.MOVE_TO) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.9));
                return;
            }
            Vec3 d = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double len = d.length();
            if (len < 0.25 || speedModifier <= 0) {
                operation = Operation.WAIT;
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                return;
            }
            double sp = speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED) * 0.075;
            Vec3 acc = d.scale(Math.min(1.0, len / 2.0) / len * sp);
            Vec3 v = mob.getDeltaMovement().add(acc.x, acc.y * 1.6, acc.z);
            mob.setDeltaMovement(v.x, v.y * 0.8, v.z);
            if (target == null && d.horizontalDistanceSqr() > 0.01) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 8F));
                mob.yBodyRot = mob.getYRot();
            }
        }
    }
}
