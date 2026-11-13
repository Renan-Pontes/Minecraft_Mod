package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.polish.BossShow;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 * Base dos Guardiões Elementais (chefes GeckoLib).
 * <ul>
 *   <li>Barra de chefe; 3 fases por vida (padrão 60% e 25%). A troca de fase é uma "pausa dramática":
 *       cutscene "boss_phase", animação "phase", fala, tremor, invulnerável por alguns segundos. O dano nunca pula
 *       uma fase.</li>
 *   <li>Ao ver um jogador pela 1ª vez: cutscene "boss_intro", animação "intro" e escala de grupo
 *       ({@link BossKit#scaleToParty}: mais jogadores = mais vida e dano).</li>
 *   <li>Golpes ({@link GuardianMove}) sorteados por alcance/fase/recarga; cada um toca a animação de mesmo nome
 *       no controlador "action" e a subclasse executa tick a tick em {@link #performMove}. Todo golpe de área,
 *       linha ou investida é telegrafado no chão com {@link Telegraph} (justo: quem sai do desenho desvia).</li>
 *   <li>Anti-trapaça: alvo inalcançável (pilar, barco, voo) por 2 s → o Guardião salta até ele ("leap", com o pouso
 *       telegrafado), teleporta para perto ou, em último caso, dispara magias teleguiadas. Não monta em nada.</li>
 *   <li>Modo provação ({@link BossKit#TRIAL_TAG}): a casa é o centro da arena, nunca reseta nem se cura por
 *       ociosidade.</li>
 *   <li>Tenacidade: Atordoar/Congelar/Enraizar duram 1/3. Morte com animação longa; recompensa de grupo pelo núcleo
 *       ({@link BossRewards#grant}: XP + contador "boss.&lt;id&gt;"); pergaminhos de glifo do elemento no saque.</li>
 * </ul>
 */
public abstract class ElementalGuardian extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(ElementalGuardian.class, EntityDataSerializers.BYTE);
    /** 0 = combate, 1 = apresentação, 2 = troca de fase. */
    private static final EntityDataAccessor<Byte> DATA_STAGE = SynchedEntityData.defineId(ElementalGuardian.class, EntityDataSerializers.BYTE);

    public static final int INTRO_TICKS = 70;
    /** Tick da apresentação em que a animação "intro" bate no chão (tremor + explosão de elemento). */
    public static final int INTRO_IMPACT = 52;
    public static final int TRANSITION_TICKS = 50;
    /** v1.0: 74 (era 60) — 3 s de animação de morte + o corpo se desfazendo. */
    public static final int DEATH_TICKS = 74;
    /** Raio das cutscenes e da recompensa de grupo. */
    public static final double REWARD_RADIUS = 48.0;
    /** XP mágico dado a cada participante (via {@link BossRewards}). */
    public static final int REWARD_XP = 400;
    /** Raio em que a barra de chefe aparece. */
    public static final double BAR_RADIUS = 48.0;
    /** Distância máxima da "casa" (templo/arena) antes de desistir do alvo. */
    public static final double LEASH = 44.0;
    /** Ticks seguidos com o alvo inalcançável antes de o Guardião reagir. */
    private static final int UNREACHABLE_REACT = 40;
    /** Id do golpe genérico de salto (anti-trapaça). */
    public static final String LEAP = "leap";

    protected final ServerBossEvent bossEvent;
    protected final String bossId;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<GuardianMove> moves;
    private final int[] moveCooldowns;
    private final RawAnimation animIdle;
    private final RawAnimation animWalk;
    private final RawAnimation animIntro;
    private final RawAnimation animPhase;
    private final RawAnimation animDeath;

    @Nullable private GuardianMove current;
    private int moveTick;
    @Nullable private LivingEntity moveTarget;
    private int globalCooldown = 30;
    private int stageTicks;
    private boolean introDone;
    private boolean rewarded;
    /** v1.0: trava das falas reativas (provocação / último fôlego). */
    private final BossShow.Voice voice = new BossShow.Voice();
    private int idleTicks;
    private int unreachableTicks;
    private int leapLandTick = -1;
    @Nullable private BlockPos home;
    /** Dicas de golpe já mostradas nesta luta (cada uma aparece uma vez). */
    private final Set<String> hinted = new HashSet<>();

    protected ElementalGuardian(EntityType<? extends ElementalGuardian> type, Level level, List<GuardianMove> moves) {
        super(type, level);
        this.bossId = EntityType.getKey(type).getPath();
        this.moves = List.copyOf(moves);
        this.moveCooldowns = new int[moves.size()];
        this.xpReward = 400;
        this.bossEvent = new ServerBossEvent(getDisplayName(), barColor(1), BossEvent.BossBarOverlay.NOTCHED_10);
        String p = "animation." + bossId + ".";
        this.animIdle = RawAnimation.begin().thenLoop(p + "idle");
        this.animWalk = RawAnimation.begin().thenLoop(p + "walk");
        this.animIntro = RawAnimation.begin().thenPlayAndHold(p + "intro");
        this.animPhase = RawAnimation.begin().thenPlayAndHold(p + "phase");
        this.animDeath = RawAnimation.begin().thenPlayAndHold(p + "death");
        setPersistenceRequired();
    }

    /** Golpe de salto anti-trapaça (inclua na lista de golpes dos Guardiões que andam no chão). */
    public static GuardianMove leapMove() {
        return GuardianMove.of(LEAP, 46).forced().selfMoving();
    }

    // =====================================================================================
    //  Ganchos de cada Guardião
    // =====================================================================================
    public abstract Element element();

    /** Cor da barra de chefe na fase informada. */
    protected abstract BossEvent.BossBarColor barColor(int phase);

    protected abstract SoundEvent roarSound();

    protected float roarPitch() { return 0.7F; }

    /** Executa o golpe no tick {@code t} (0 = começo). {@code target} pode ser null se o alvo sumiu. */
    protected abstract void performMove(ServerLevel level, GuardianMove move, int t, @Nullable LivingEntity target);

    /** Multiplicador das magias do chefe (sobe com a fase e com o tamanho do grupo). */
    public float spellPower() {
        return (1.8F + 0.4F * (phase() - 1)) * BossKit.damageScale(this);
    }

    /** Multiplicador dos ataques próprios (ondas, explosões): +15% por fase e escala de grupo do núcleo. */
    protected float mult() {
        return (1F + 0.15F * (phase() - 1)) * BossKit.damageScale(this);
    }

    /** Dano corpo a corpo da fase atual (ATTACK_DAMAGE já inclui a escala de grupo). */
    protected float meleeDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.0F + 0.15F * (phase() - 1));
    }

    protected float phase2At() { return 0.6F; }

    protected float phase3At() { return 0.25F; }

    /** Distância (da borda do chefe) que ele tenta manter do alvo ao perseguir. */
    protected double preferredRange() { return 3.0; }

    protected double chaseSpeed() { return 1.0; }

    /** Intervalo entre golpes. */
    protected int moveGap() {
        return switch (phase()) { case 1 -> 32; case 2 -> 24; default -> 16; };
    }

    /** Condição extra de cada golpe (ex.: precisa estar na água). */
    protected boolean canUseMove(GuardianMove move, LivingEntity target, double dist) { return true; }

    /** Receitas de glifo para o último recurso do anti-trapaça (alvo voando alto, sem lugar para pousar). */
    protected String[] rangedRecipes() {
        return new String[] {"projectile homing amplify arcane_damage", "projectile homing arcane_damage"};
    }

    /** Animação contínua (idle/andar/nadar/voar...) quando não está apresentando/trocando de fase/morrendo. */
    protected RawAnimation baseAnimation(AnimationState<ElementalGuardian> state) {
        return state.isMoving() ? animWalk : animIdle;
    }

    /** Efeitos durante apresentação (stage 1) ou troca de fase (stage 2). {@code left} = ticks restantes. */
    protected void stageFx(ServerLevel level, int stage, int left) {
        if (left % 4 == 0) {
            ElementFx.ring(level, position().add(0, 0.1, 0), element(), 2.0 + (left % 16) * 0.25, 24);
        }
    }

    /** Momento da apresentação em que o Guardião bate no chão / abre as asas (tick {@link #INTRO_IMPACT}). */
    protected void introImpact(ServerLevel level) {
        Vec3 c = position();
        BossKit.shake(level, c, 32, 1.6F, 22);
        ElementFx.burst(level, c.add(0, 0.4, 0), element(), 70, getBbWidth() * 0.8);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    protected void onIntro(ServerLevel level) {}

    protected void onPhaseChange(ServerLevel level, int phase) {}

    /** Todo tick do servidor (auras, regeneração, estados especiais). */
    protected void passiveTick(ServerLevel level, @Nullable LivingEntity target) {}

    /** Partículas de ambiente no cliente. */
    protected void clientAmbient() {}

    /** Dano recebido depois das imunidades (escudos, vulnerabilidade). Retorne 0 para anular. */
    protected float modifyIncomingDamage(DamageSource source, float amount) { return amount; }

    /** Efeitos de status que o Guardião ignora (o do próprio elemento). */
    protected boolean immuneTo(Holder<MobEffect> effect) { return false; }

    /** Barra de chefe (o Golem mostra o escudo enquanto ele existe). */
    protected void updateBossBar() {
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    /** Partículas da morte (a cada tick, {@code t} = 1..DEATH_TICKS). */
    protected void deathFx(ServerLevel level, int t) {
        if (t % 3 == 0) {
            Vec3 p = position().add((random.nextDouble() - 0.5) * getBbWidth(), random.nextDouble() * getBbHeight(),
                    (random.nextDouble() - 0.5) * getBbWidth());
            ElementFx.burst(level, p, element(), 10, 0.4);
            level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // =====================================================================================
    //  Estado sincronizado
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, (byte) 1);
        builder.define(DATA_STAGE, (byte) 0);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    private void setPhase(int p) {
        entityData.set(DATA_PHASE, (byte) p);
    }

    /** 0 = combate, 1 = apresentação, 2 = troca de fase. */
    public int stage() {
        return entityData.get(DATA_STAGE);
    }

    private void setStage(int s) {
        entityData.set(DATA_STAGE, (byte) s);
    }

    /** Ocupado: apresentando, trocando de fase ou no meio de um golpe. */
    public boolean isBusy() {
        return stage() != 0 || current != null;
    }

    @Nullable
    public GuardianMove currentMove() {
        return current;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Prefixo das falas/nomes do chefe nas traduções: boss.isekai_mahou.<id>.<chave>. */
    private static final String LINE_PREFIX = "boss.isekai_mahou.";

    protected Component line(String key) {
        return Component.translatable(LINE_PREFIX + bossId + "." + key);
    }

    /** Dica de golpe na barra de ação (uma vez por luta) para quem está perto. */
    protected void hint(String move, double radius) {
        if (!hinted.add(move)) return;
        Component msg = line("hint." + move).copy().withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : BossKit.partyAround(this, radius)) p.displayClientMessage(msg, true);
    }

    // =====================================================================================
    //  Ajudantes de ataque (telegrafia)
    // =====================================================================================
    /** Inimigo do chefe (jogadores fora do criativo, quem luta contra ele, bichos de jogadores). */
    protected boolean isFoe(Entity e) {
        return BossSpells.isFoe(this, e);
    }

    /** Inimigos dentro de uma forma de telegrafia agora (a área de acerto é a desenhada). */
    protected List<LivingEntity> foesIn(ServerLevel level, Telegraph.Shape shape) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Telegraph.entitiesIn(level, shape)) if (isFoe(e)) out.add(e);
        return out;
    }

    /** Chão logo abaixo de um ponto. */
    protected Vec3 groundAt(Vec3 p) {
        return BossSpells.ground(level(), p);
    }

    /** Ângulo atual do corpo (padrão do Minecraft) — usado em cones e linhas. */
    protected float bodyYaw() {
        return getYRot();
    }

    // =====================================================================================
    //  IA
    // =====================================================================================
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CombatGoal());
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        addIdleGoals();
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ElementalGuardian.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> level().getDifficulty() != Difficulty.PEACEFUL));
    }

    /** Passeio quando está sem alvo (cada Guardião escolhe o seu). */
    protected abstract void addIdleGoals();

    /** Movimento durante o combate (padrão: andar até a distância preferida). */
    protected void combatMovement(LivingEntity target, double dist) {
        if (isBusy()) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (dist > preferredRange()) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, chaseSpeed());
        } else {
            getNavigation().stop();
        }
    }

    /** Distância da borda do chefe até o alvo. */
    public double edgeDistance(Entity e) {
        return Math.max(0, distanceTo(e) - getBbWidth() * 0.5);
    }

    private class CombatGoal extends Goal {
        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t != null) combatMovement(t, edgeDistance(t));
        }
    }

    /** Raio em que o Guardião persegue (na Câmara de Provação cobre a arena inteira com folga). */
    protected double leash() {
        double r = BossKit.arenaRadius(this);
        return r > 0 ? Math.max(LEASH, r + 16) : LEASH;
    }

    private Vec3 homeCenter() {
        return home != null ? Vec3.atBottomCenterOf(home) : position();
    }

    /** Casa = centro da arena da provação (se houver) ou o ponto onde o Guardião apareceu (ovo, comando, templo). */
    private void updateHome() {
        if (home != null && (tickCount % 20 != 0 || !BossKit.isTrialBoss(this))) return;
        Vec3 arena = BossKit.arenaCenter(this);
        BlockPos h = arena != null ? BlockPos.containing(arena) : home != null ? home : blockPosition();
        if (!h.equals(home)) {
            home = h;
            restrictTo(home, arena != null ? (int) Math.max(8, BossKit.arenaRadius(this) - 2) : 20);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        updateHome();
        for (int i = 0; i < moveCooldowns.length; i++) if (moveCooldowns[i] > 0) moveCooldowns[i]--;

        LivingEntity target = getTarget();
        double leash = leash();
        if (target != null && (!target.isAlive() || target.distanceToSqr(homeCenter()) > leash * leash
                || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            target = null;
        }
        // Câmara de Provação: todos estão na arena — o Guardião caça quem estiver nela mesmo sem linha de visão
        // (esconder-se atrás de uma coluna não "desliga" o chefe)
        if (target == null && tickCount % 10 == 0 && BossKit.isTrialBoss(this) && level.getDifficulty() != Difficulty.PEACEFUL) {
            target = nearestArenaFoe(leash);
            if (target != null) setTarget(target);
        }
        if (target == null) {
            idleTicks++;
            unreachableTicks = 0;
            // Na Câmara de Provação não existe "voltar a se curar": a luta só acaba com vitória ou derrota
            if (!BossKit.isTrialBoss(this) && idleTicks > 200 && tickCount % 20 == 0 && stageTicks == 0 && current == null) {
                if (getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.01F);
                // Recuperou tudo fora de combate: a luta recomeça da fase 1 (quem voltar não pega a fase final de cara)
                else if (phase() > 1) resetFight();
            }
        } else {
            idleTicks = 0;
        }

        updatePhase(level);
        // v1.0: fala de último fôlego (uma vez por luta, fora das transições)
        if (tickCount % 10 == 0 && stageTicks == 0 && introDone && voice.lastStand(this, 0.15F)) {
            Announce.send(this, line("last_stand"), element().color, Announce.STYLE_BOSS);
        }
        updateBossBar();
        if (tickCount % 10 == 0) updateBarViewers(level);

        if (stageTicks > 0) {
            stageTicks--;
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            int stage = stage();
            stageFx(level, stage, stageTicks);
            if (stage == 1 && INTRO_TICKS - stageTicks == INTRO_IMPACT) introImpact(level);
            if (stageTicks == 0) {
                setStage(0);
                globalCooldown = Math.max(globalCooldown, 15);
            }
            passiveTick(level, target);
            return;
        }

        if (!introDone && target instanceof Player && distanceTo(target) < 40
                && (hasLineOfSight(target) || getLastHurtByMob() == target || BossKit.isTrialBoss(this))) {
            startIntro(level);
            return;
        }

        if (current != null) {
            LivingEntity mt = moveTarget != null && moveTarget.isAlive() ? moveTarget : target;
            // Golpes "travados": só miram até aimUntil; depois a direção não muda (o desenho no chão é o golpe)
            if (current.rooted() && mt != null && moveTick < current.aimUntil()) {
                getLookControl().setLookAt(mt, 30.0F, 30.0F);
                BossSpells.face(this, mt.position());
            }
            GuardianMove m = current;
            if (LEAP.equals(m.id)) leap(level, moveTick, mt);
            else performMove(level, m, moveTick, mt);
            moveTick++;
            if (current == m && moveTick >= m.duration) endMove();
        } else if (target != null && (introDone || !(target instanceof Player))) {
            // Antes da apresentação só luta contra bichos (ex.: lobo do jogador); jogadores primeiro veem a cutscene
            if (globalCooldown > 0) globalCooldown--;
            else pickMove(target);
        }
        if (target != null && introDone) antiCheese(level, target);
        // Arena protegida viva enquanto a luta durar (mesmo contra invocações ou em pausas sem alvo jogador)
        if (introDone && target != null && tickCount % 40 == 0) BossKit.keepArena(this, REWARD_RADIUS);
        passiveTick(level, target);
    }

    /** Jogador (fora do criativo) mais perto dentro da área de perseguição — usado na Câmara de Provação. */
    @Nullable
    private LivingEntity nearestArenaFoe(double leash) {
        Vec3 c = homeCenter();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : BossKit.partyAround(this, leash + 8)) {
            if (p.isCreative() || p.distanceToSqr(c) > leash * leash) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Volta à fase 1 depois de se recuperar por completo sem alvo. */
    private void resetFight() {
        setPhase(1);
        bossEvent.setColor(barColor(1));
        resetBarName();
        hinted.clear();
        onFightReset();
    }

    /** Limpa estados especiais da luta (escudos etc.) quando o Guardião volta à fase 1. */
    protected void onFightReset() {}

    private void pickMove(LivingEntity target) {
        double dist = edgeDistance(target);
        int phase = phase();
        int total = 0;
        List<Integer> options = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            GuardianMove m = moves.get(i);
            if (!m.allowedIn(phase) || moveCooldowns[i] > 0 || dist < m.minRange() || dist > m.maxRange()
                    || !canUseMove(m, target, dist)) continue;
            options.add(i);
            total += m.weight();
        }
        if (total <= 0) return;
        int roll = random.nextInt(total);
        for (int i : options) {
            roll -= moves.get(i).weight();
            if (roll < 0) {
                startMove(i, target);
                return;
            }
        }
    }

    private int moveIndex(String id) {
        for (int i = 0; i < moves.size(); i++) if (moves.get(i).id.equals(id)) return i;
        return -1;
    }

    private void startMove(int index, LivingEntity target) {
        GuardianMove m = moves.get(index);
        current = m;
        moveTick = 0;
        moveTarget = target;
        moveCooldowns[index] = m.cooldown();
        getNavigation().stop();
        BossSpells.face(this, target.position());
        triggerAnim("action", m.animId());
        if (m.announces()) {
            Announce.send(this, line("move." + m.id), element().color, Announce.STYLE_BOSS);
        }
    }

    /** Termina o golpe atual normalmente (inclusive antes do fim da animação, ex.: pousou do salto). */
    protected void endMove() {
        current = null;
        moveTarget = null;
        globalCooldown = moveGap();
    }

    /** Interrompe o golpe atual (troca de fase, escudo quebrado...): os avisos pendentes somem sem acertar. */
    protected void cancelMove() {
        if (current != null) stopTriggeredAnim("action", current.animId());
        current = null;
        moveTarget = null;
        if (!level().isClientSide) Telegraph.cancelAll(this);
    }

    // =====================================================================================
    //  Anti-trapaça
    // =====================================================================================
    private void antiCheese(ServerLevel level, LivingEntity target) {
        if (stage() != 0) return;
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : Math.max(0, unreachableTicks - 2);
        if (unreachableTicks < UNREACHABLE_REACT || current != null) return;
        unreachableTicks = 0;
        if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("boss.isekai_mahou.guardian_no_hiding").withStyle(ChatFormatting.GOLD), true);
        }
        onUnreachable(level, target);
    }

    /**
     * Alvo inalcançável (pilar, barco, voo): salta até ele com o pouso telegrafado, teleporta para perto ou, em
     * último caso (voando alto), dispara uma rajada de magias teleguiadas.
     */
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        boolean airborne = !target.onGround() && BossSpells.heightAboveGround(level, target, 24) > 3.0;
        int leap = moveIndex(LEAP);
        if (!airborne && leap >= 0 && onGround() && distanceTo(target) < 24) {
            startMove(leap, target);
            return;
        }
        if (!airborne && BossKit.blinkNear(this, target, getBbWidth() * 0.5 + 2.5)) {
            triggerAnim("action", "roar");
            globalCooldown = Math.min(globalCooldown, 10);
            return;
        }
        triggerAnim("action", "roar");
        for (int i = 0; i < 3; i++) {
            BossKit.schedule(level, 6 + i * 8, () -> {
                if (isAlive() && target.isAlive() && stage() == 0) BossSpells.cast(this, element(), target, spellPower(), rangedRecipes());
            });
        }
    }

    /** Salto até o alvo inalcançável: agacha, salta e o pouso explode no círculo desenhado. */
    private void leap(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            leapLandTick = -1;
            level.playSound(null, getX(), getY(), getZ(), roarSound(), SoundSource.HOSTILE, 2.0F, roarPitch() * 1.15F);
        }
        if (t < 8) {
            if (target != null) BossSpells.face(this, target.position());
            if (t % 2 == 0) ElementFx.burst(level, position().add(0, 0.2, 0), element(), 8, getBbWidth() * 0.4);
            return;
        }
        if (t == 8) {
            if (target == null || !(onGround() || isInWater())) {
                endMove();
                return;
            }
            Vec3 land = groundAt(target.position());
            int flight = BossKit.leapTo(this, land, 1.0);
            float r = getBbWidth() * 0.5F + 2.5F;
            float dmg = 10F * mult();
            Telegraph.delayed(level, Telegraph.Shape.circle(land, r).warmup(Math.max(6, flight)).color(Telegraph.WARNING).owner(this),
                    hits -> landingBlast(level, land, r, dmg, hits));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.5F, 0.5F);
            ElementFx.burst(level, position().add(0, 0.3, 0), element(), 30, getBbWidth() * 0.5);
            return;
        }
        if (leapLandTick < 0 && t > 11 && (onGround() || isInWater())) leapLandTick = t;
        if (leapLandTick >= 0 && t >= leapLandTick + 8) endMove();
    }

    private void landingBlast(ServerLevel level, Vec3 at, float r, float dmg, List<LivingEntity> hits) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, element(), dmg);
            BossSpells.knock(e, e.position().subtract(at), 1.2, 0.5);
        }
        ElementFx.burst(level, at.add(0, 0.3, 0), element(), 50, r * 0.5);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.6, 0.2, 0.6, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
        BossKit.shake(level, at, 20, 1.2F, 14);
    }

    /** Chefes não montam em barcos/carrinhos (senão viram presa fácil). */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // Chefes da mesma Câmara de Provação não brigam entre si
        if (target != null && target != this && BossKit.isTrialBoss(this) && BossKit.isTrialBoss(target)) return;
        super.setTarget(target);
    }

    // =====================================================================================
    //  Apresentação e fases
    // =====================================================================================
    private void startIntro(ServerLevel level) {
        introDone = true;
        cancelMove();
        setStage(1);
        stageTicks = INTRO_TICKS;
        getNavigation().stop();
        // Escala de grupo do núcleo: vida e dano crescem com os jogadores presentes (aplicada uma vez só)
        BossKit.scaleToParty(this, REWARD_RADIUS);
        int rgb = element().color;
        Cutscenes.playNear(this, REWARD_RADIUS, "boss_intro", getDisplayName(), line("epithet"), rgb, INTRO_TICKS, null);
        Announce.send(this, line("intro"), rgb, Announce.STYLE_BOSS);
        BossKit.roar(this, roarSound(), roarPitch());
        // A arena fica inquebrável desde a apresentação (antes do primeiro golpe)
        BossKit.sealArena(this);
        onIntro(level);
    }

    private void updatePhase(ServerLevel level) {
        int p = phase();
        if (p >= 3 || isDeadOrDying()) return;
        float threshold = p == 1 ? phase2At() : phase3At();
        if (getHealth() <= getMaxHealth() * threshold + 0.01F) enterPhase(level, p + 1);
    }

    private void enterPhase(ServerLevel level, int p) {
        cancelMove();
        setPhase(p);
        setStage(2);
        stageTicks = TRANSITION_TICKS;
        introDone = true;
        getNavigation().stop();
        bossEvent.setColor(barColor(p));
        int rgb = element().color;
        Cutscenes.playNear(this, REWARD_RADIUS, "boss_phase", line("phase" + p), getDisplayName(), rgb, TRANSITION_TICKS, null);
        Announce.send(this, line("phase" + p + ".line"), rgb, Announce.STYLE_BOSS);
        level.playSound(null, getX(), getY(), getZ(), roarSound(), SoundSource.HOSTILE, 4.0F, roarPitch() * 0.9F);
        // Onda de choque que afasta quem estiver colado (só empurra, sem dano)
        Vec3 c = position();
        for (LivingEntity e : BossSpells.foesAround(this, c.add(0, 1, 0), 7.0)) {
            BossSpells.knock(e, e.position().subtract(c), 1.4, 0.45);
        }
        ElementFx.burst(level, c.add(0, getBbHeight() * 0.5, 0), element(), 60, getBbWidth() * 0.6);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        BossKit.shake(level, c, 36, 1.5F, 30);
        onPhaseChange(level, p);
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isDeadOrDying()) return super.hurt(source, amount);
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        Entity attacker = source.getEntity();
        // Fogo amigo entre chefes da mesma provação não conta
        if (!bypass && attacker != null && attacker != this && BossKit.isTrialBoss(this) && BossKit.isTrialBoss(attacker)) {
            return false;
        }
        if (!bypass && stage() != 0 && !isInvulnerableTo(source)) {
            if (level() instanceof ServerLevel sl && tickCount % 4 == 0) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.5, getZ(), 8,
                        getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.2);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            return false;
        }
        if (!bypass) {
            if (amount <= 0) {
                immuneFx();
                return false;
            }
            // O templo protege o Guardião: quem ataca de fora da arena (flechas/magias de longe) não o fere
            if (outsideArena(attacker)) {
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ElementFx.dust(element(), 1.6F), getX(), getY() + getBbHeight() * 0.6, getZ(), 16,
                            getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
                    sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.5F);
                }
                if (attacker instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("boss.isekai_mahou.guardian_out_of_arena")
                            .withStyle(ChatFormatting.GOLD), true);
                }
                return false;
            }
            amount = modifyIncomingDamage(source, amount);
            if (amount <= 0) return false;
        }
        return super.hurt(source, amount);
    }

    /** Atacante vivo longe demais da casa (fora do alcance em que o Guardião persegue). */
    private boolean outsideArena(@Nullable Entity attacker) {
        if (home == null || !(attacker instanceof LivingEntity) || attacker == this) return false;
        double leash = leash();
        return attacker.distanceToSqr(homeCenter()) > leash * leash;
    }

    /** Feedback de golpe que não fez efeito (elemento imune). */
    protected void immuneFx() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ElementFx.dust(element(), 1.6F), getX(), getY() + getBbHeight() * 0.6, getZ(), 14,
                    getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.4F);
        }
    }

    /** O dano nunca pula uma fase: a vida para no limite e a troca acontece no próximo tick. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        int p = phase();
        float floor = p == 1 ? getMaxHealth() * phase2At() : p == 2 ? getMaxHealth() * phase3At() : 0F;
        super.actuallyHurt(source, amount);
        if (floor > 0 && getHealth() < floor && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(floor);
        }
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        Holder<MobEffect> type = effect.getEffect();
        if (immuneTo(type)) return false;
        // Tenacidade de chefe: controles duram 1/3
        if (type.is(CoreRegistries.STUNNED.getKey()) || type.is(CoreRegistries.FROZEN.getKey())
                || type.is(CoreRegistries.ROOTED.getKey())) {
            effect = new MobEffectInstance(type, Math.max(10, effect.getDuration() / 3), effect.getAmplifier());
        }
        return super.addEffect(effect, source);
    }

    /** Chefes grandes não sufocam em tetos baixos, não se espremem nem se afogam (evita vencê-los "de graça"). */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
                || super.isInvulnerableTo(source);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // Voz de chefe: mais alta, mais grave e menos falante que um monstro comum
    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public boolean isPushedByFluid(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** v1.0: último golpe disparado no controlador "action" — interrompido na morte para não cobrir a animação "death". */
    @Nullable private String lastActionAnim;

    @Override
    public void triggerAnim(String controllerName, String animName) {
        if ("action".equals(controllerName)) lastActionAnim = animName;
        GeoEntity.super.triggerAnim(controllerName, animName);
    }

    /** v1.0: um jogador caiu pelas mãos do Guardião — provocação curta (com trava: nunca em rajada). */
    public void onPlayerSlain() {
        if (level().isClientSide || isDeadOrDying() || !voice.taunt(this)) return;
        Announce.send(this, line("taunt"), element().color, Announce.STYLE_BOSS);
    }

    // =====================================================================================
    //  Morte e recompensas
    // =====================================================================================
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level) || !dead || rewarded) return;
        rewarded = true;
        cancelMove();
        if (lastActionAnim != null) stopTriggeredAnim("action", lastActionAnim);
        bossEvent.setProgress(0F);
        Announce.send(this, line("death"), element().color, Announce.STYLE_BOSS);
        level.playSound(null, getX(), getY(), getZ(), roarSound(), SoundSource.HOSTILE, 4.0F, roarPitch() * 0.6F);
        BossKit.shake(level, position(), 40, 1.4F, 40);
        // v1.0: golpe final marcado + câmera da derrota (curta, não trava ninguém)
        BossShow.deathStart(level, this, element().color, DEATH_TICKS, true);
        // Recompensa de grupo do núcleo: XP mágico + contador boss.<id> para quem lutou (paga uma vez por chefe)
        Component msg = Component.translatable("boss.isekai_mahou.guardian_defeated", getDisplayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer p : BossRewards.grant(this, REWARD_XP, REWARD_RADIUS)) p.sendSystemMessage(msg);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            deathFx(level, deathTime);
            BossShow.deathTick(level, this, deathTime, DEATH_TICKS, element().color);
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
                BossShow.deathFinale(level, this, element().color);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
                ElementFx.burst(level, c, element(), 120, getBbWidth());
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
                BossKit.shake(level, c, 32, 1.2F, 16);
                level.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    /** Pergaminhos de glifo do elemento (2 comuns + chance de um lendário), além da loot table. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        List<Glyph> pool = new ArrayList<>();
        List<Glyph> legendary = new ArrayList<>();
        for (Glyph g : GlyphRegistry.byElement(element())) {
            if (g.kind() == GlyphKind.TRIGGER) continue;
            if (g.isLegendary()) legendary.add(g);
            else if (g.tier() >= 2) pool.add(g);
        }
        if (pool.isEmpty()) {
            for (Glyph g : GlyphRegistry.byElement(element())) if (!g.isLegendary() && g.kind() != GlyphKind.TRIGGER) pool.add(g);
        }
        for (int i = 0; i < 2 && !pool.isEmpty(); i++) {
            Glyph g = pool.remove(random.nextInt(pool.size()));
            spawnAtLocation(GlyphScrollItem.of(g.id()));
        }
        if (!legendary.isEmpty() && random.nextFloat() < 0.15F) {
            spawnAtLocation(GlyphScrollItem.of(legendary.get(random.nextInt(legendary.size())).id()));
        }
    }

    // =====================================================================================
    //  Barra de chefe, nome e salvamento
    // =====================================================================================
    /** A barra aparece para quem está perto da arena (não para todo mundo que enxerga a entidade de longe). */
    private void updateBarViewers(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(this);
            if (d < BAR_RADIUS * BAR_RADIUS && !p.isSpectator()) bossEvent.addPlayer(p);
            else if (d > (BAR_RADIUS + 16) * (BAR_RADIUS + 16) || p.isSpectator()) bossEvent.removePlayer(p);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    protected void setBarName(Component name) {
        bossEvent.setName(name);
    }

    protected void resetBarName() {
        bossEvent.setName(getDisplayName());
    }

    protected void setBarColor(BossEvent.BossBarColor color) {
        bossEvent.setColor(color);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", introDone);
        tag.putByte("Phase", (byte) phase());
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = tag.getBoolean("IntroDone");
        setPhase(Math.max(1, Math.min(3, tag.getByte("Phase"))));
        bossEvent.setColor(barColor(phase()));
        if (tag.contains("HomeX")) {
            home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            restrictTo(home, 20);
        }
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // =====================================================================================
    //  Cliente e animações
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !isDeadOrDying()) clientAmbient();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ElementalGuardian>(this, "base", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(animDeath);
            if (stage() == 1) return state.setAndContinue(animIntro);
            if (stage() == 2) return state.setAndContinue(animPhase);
            return state.setAndContinue(baseAnimation(state));
        }));
        AnimationController<ElementalGuardian> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        String p = "animation." + bossId + ".";
        for (GuardianMove m : moves) action.triggerableAnim(m.animId(), RawAnimation.begin().thenPlay(p + m.animId()));
        // Rugido curto ao reagir a quem tenta trapacear (teleporte/rajada)
        action.triggerableAnim("roar", RawAnimation.begin().thenPlay(p + "roar"));
        controllers.add(action);
    }

    /** Animações extras (ex.: andar na terra x nadar) a partir do id. */
    protected RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation." + bossId + "." + name);
    }

    protected RawAnimation idleAnim() {
        return animIdle;
    }

    protected RawAnimation walkAnim() {
        return animWalk;
    }

    /** Braços, asas e caudas passam da caixa de colisão: a caixa de "visibilidade" é maior para o modelo não sumir. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(cullMargin(), 1.0, cullMargin());
    }

    protected double cullMargin() {
        return 1.5;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
