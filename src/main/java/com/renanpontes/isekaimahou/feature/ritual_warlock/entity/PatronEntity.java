package com.renanpontes.isekaimahou.feature.ritual_warlock.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.OfferingAltarBlock;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.CirclePattern;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.DemandPool;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactState;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronDemand;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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
 * Base dos três Patronos. Ciclo de vida:
 * <pre>
 * SUMMONING (surge do altar, cutscene) → OFFERING (exige o tributo, diálogo)
 *   → aceitar: PENDING (preso esperando o tributo) → cumprir: SEALED (some, pacto selado)
 *   → recusar / prazo / falhar / círculo quebrado: FREE (minichefe com barra de chefe)
 * </pre>
 * Enquanto preso (SUMMONING/OFFERING/PENDING/SEALED) fica parado sobre o altar e é imune a dano.
 * Pelo ovo, nasce direto LIVRE (útil para testes e encontros).
 */
public abstract class PatronEntity extends Monster implements GeoEntity {
    public enum Phase { SUMMONING, OFFERING, PENDING, FREE, SEALED }

    /** Motivos de o Patrono se libertar (chave das falas e mensagens). */
    public enum Release {
        REFUSED("refused"), CIRCLE("circle"), TIMEOUT("timeout"), FAILED("failed"), IMPATIENT("impatient");
        public final String key;
        Release(String key) { this.key = key; }
    }

    /** Quantas versões de cada fala existem nas traduções (patron.isekai_mahou.<id>.<chave>.<n>). */
    public static final Map<String, Integer> LINE_VARIANTS = Map.ofEntries(
            Map.entry("greet", 3), Map.entry("demand", 2), Map.entry("accept", 2), Map.entry("sealed", 2),
            Map.entry("taunt", 4), Map.entry("waiting", 2), Map.entry("missing", 1), Map.entry("stranger", 1),
            Map.entry("enrage", 1), Map.entry("death", 1), Map.entry("wild", 1),
            Map.entry("freed_refused", 1), Map.entry("freed_circle", 1), Map.entry("freed_timeout", 1),
            Map.entry("freed_failed", 1), Map.entry("freed_impatient", 1));

    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(PatronEntity.class, EntityDataSerializers.BYTE);
    public static final int SUMMON_TICKS = 70;
    public static final int SEAL_TICKS = 50;
    /** Tempo para responder ao diálogo antes de o Patrono perder a paciência. */
    public static final int OFFER_PATIENCE = 20 * 150;
    private static final Vector3f CHAIN_COLOR = new Vector3f(0.7F, 0.04F, 0.06F);

    protected final ServerBossEvent bossEvent;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation animIdle;
    private final RawAnimation animBound;
    private final RawAnimation animMove;
    private final RawAnimation animSummon;
    private final RawAnimation animVanish;

    // ---------- dados do ritual (servidor) ----------
    private UUID owner;
    private BlockPos altarPos;
    private CirclePattern circle = CirclePattern.SMALL;
    private int pactNumber = 3;
    private PatronDemand demand;
    private boolean favor;
    private long offerDeadline;
    private long deadline;
    private int phaseTicks;
    private int emptyTicks;
    private int tauntCooldown = 500;
    protected boolean enraged;
    private boolean rewarded;

    // ---------- combate ----------
    protected int attackCooldown;
    protected int attackWindup;
    protected int spellCooldown = 60;
    protected int specialCooldown = 120;

    protected PatronEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 80;
        this.bossEvent = new ServerBossEvent(getDisplayName(), kind().bossColor, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setVisible(true);
        String base = "animation." + kind().entityId() + ".";
        this.animIdle = RawAnimation.begin().thenLoop(base + "idle");
        this.animBound = RawAnimation.begin().thenLoop(base + "bound");
        this.animMove = RawAnimation.begin().thenLoop(base + "move");
        this.animSummon = RawAnimation.begin().thenPlayAndHold(base + "summon");
        this.animVanish = RawAnimation.begin().thenPlayAndHold(base + "vanish");
        setPersistenceRequired();
    }

    // =====================================================================================
    //  Ganchos dos Patronos
    // =====================================================================================
    public abstract PatronKind kind();

    protected abstract double baseHealth();

    protected abstract double baseDamage();

    /** Golpe especial do Patrono (sem depender de glifos). */
    protected abstract void special(ServerLevel level, LivingEntity target);

    /** Ataque à distância usado quando nenhuma magia de glifo está disponível. */
    protected abstract void fallbackRanged(ServerLevel level, LivingEntity target);

    /** Efeito extra quando o golpe corpo a corpo acerta. */
    protected abstract void onMeleeHit(LivingEntity target);

    protected abstract SoundEvent roarSound();

    /** Partículas de ambiente no cliente. */
    protected abstract void clientAmbient();

    protected void onEnrage(ServerLevel level) {}

    protected boolean flies() {
        return false;
    }

    protected double meleeReach() {
        return getBbWidth() * 0.5 + 2.4;
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, (byte) Phase.FREE.ordinal());
    }

    public Phase phase() {
        Phase[] v = Phase.values();
        int i = entityData.get(DATA_PHASE);
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    protected void setPhase(Phase p) {
        entityData.set(DATA_PHASE, (byte) p.ordinal());
        phaseTicks = 0;
        bossEvent.setVisible(p == Phase.FREE);
        setNoGravity(p != Phase.FREE || flies());
    }

    public boolean isFree() {
        return phase() == Phase.FREE;
    }

    /** Preso no círculo (inclui surgindo e sumindo). */
    public boolean isBound() {
        return phase() != Phase.FREE;
    }

    public UUID owner() { return owner; }
    public PatronDemand demand() { return demand; }
    public int pactNumber() { return pactNumber; }
    public boolean favor() { return favor; }
    public BlockPos altarPos() { return altarPos; }
    public long offerDeadline() { return offerDeadline; }
    public long deadline() { return deadline; }

    public boolean isOwner(Player p) {
        return owner != null && owner.equals(p.getUUID());
    }

    /** Nível de força (1..10) usado para vida, dano e magias. */
    public int tier() {
        return Math.max(1, Math.min(10, pactNumber));
    }

    /** Prepara um Patrono invocado pelo ritual (antes de entrar no mundo). */
    public void setupRitual(ServerPlayer player, BlockPos altar, CirclePattern circle, int pact, boolean favor) {
        this.owner = player.getUUID();
        this.altarPos = altar.immutable();
        this.circle = circle;
        this.pactNumber = pact;
        this.favor = favor;
        this.demand = DemandPool.roll(kind(), pact, favor, random);
        Vec3 a = anchor();
        double dx = player.getX() - a.x, dz = player.getZ() - a.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        moveTo(a.x, a.y, a.z, yaw, 0F);
        yHeadRot = yaw;
        yBodyRot = yaw;
        setPhase(Phase.SUMMONING);
    }

    private Vec3 anchor() {
        return new Vec3(altarPos.getX() + 0.5, altarPos.getY() + OfferingAltarBlock.TOP + 0.05, altarPos.getZ() + 0.5);
    }

    /** Aceito: espera o tributo até {@code deadline} (tempo de jogo do overworld). */
    public void acceptPending(long deadline) {
        this.deadline = deadline;
        setPhase(Phase.PENDING);
        speak("accept");
    }

    // =====================================================================================
    //  Falas
    // =====================================================================================
    public void speak(String key) {
        if (!(level() instanceof ServerLevel level)) return;
        Component line = kind().line(key, LINE_VARIANTS.getOrDefault(key, 1), random);
        Announce.send(this, line, kind().color, Announce.STYLE_BOSS);
        Component chat = Component.translatable("message.isekai_mahou.warlock.patron_says", kind().displayName(), line);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 32 * 32) p.sendSystemMessage(chat);
        }
        triggerAnim("action", "talk");
        level.playSound(null, getX(), getY(), getZ(), roarSound(), SoundSource.HOSTILE, 1.2F, 0.55F + random.nextFloat() * 0.1F);
    }

    /** Fala sorteada (só o texto, sem anunciar). */
    public Component lineText(String key) {
        return kind().line(key, LINE_VARIANTS.getOrDefault(key, 1), random);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientAmbient();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (isBound() && altarPos != null) {
            Vec3 a = anchor();
            if (distanceToSqr(a) > 0.0025) setPos(a.x, a.y, a.z);
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
            setTarget(null);
        }
        if (isAlive()) phaseTick(level);
    }

    private void phaseTick(ServerLevel level) {
        long now = PactRitual.now(level);
        phaseTicks++;
        switch (phase()) {
            case SUMMONING -> {
                summonFx(level);
                if (phaseTicks >= SUMMON_TICKS) {
                    setPhase(Phase.OFFERING);
                    offerDeadline = now + OFFER_PATIENCE;
                    speak("greet");
                    ServerPlayer o = ownerPlayer(level);
                    if (o != null && o.level() == level && o.distanceToSqr(this) < 32 * 32) PactRitual.openDialog(o, this);
                }
            }
            case OFFERING -> {
                boundFx(level);
                if (altarPos == null || (tickCount % 10 == 0 && !circle.isComplete(level, altarPos))) {
                    breakFree(Release.CIRCLE);
                } else if (now > offerDeadline) {
                    breakFree(Release.IMPATIENT);
                }
            }
            case PENDING -> {
                boundFx(level);
                if (altarPos == null || (tickCount % 10 == 0 && !circle.isComplete(level, altarPos))) {
                    breakFree(Release.CIRCLE);
                    return;
                }
                if (now > deadline) {
                    breakFree(Release.TIMEOUT);
                    return;
                }
                if (tickCount % 20 == 0) {
                    ServerPlayer o = ownerPlayer(level);
                    if (o != null) {
                        PactState s = PactRitual.state(o);
                        if (!getUUID().equals(s.patron)) {
                            breakFree(Release.TIMEOUT);
                            return;
                        }
                        if (s.failed) {
                            breakFree(Release.FAILED);
                            return;
                        }
                        if (s.fulfilled && demand != null && demand.isTracked() && o.level() == level && o.distanceToSqr(this) < 12 * 12) {
                            PactRitual.seal(o, this);
                            return;
                        }
                    }
                }
                if (--tauntCooldown <= 0) {
                    tauntCooldown = 600 + random.nextInt(900);
                    if (level.getNearestPlayer(this, 16) != null) speak("taunt");
                }
            }
            case FREE -> freeTick(level);
            case SEALED -> {
                sealFx(level);
                if (phaseTicks >= SEAL_TICKS) {
                    ElementFx.burst(level, position().add(0, getBbHeight() * 0.5, 0), kind().element, 60, 1.2);
                    level.sendParticles(Vfx.reversePortal(), getX(), getY() + 1.5, getZ(), 80, 0.6, 1.2, 0.6, 0.05);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.5F, 0.6F);
                    discard();
                }
            }
        }
    }

    private ServerPlayer ownerPlayer(ServerLevel level) {
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    // ---------------------------------------------------------------- efeitos visuais do ritual
    private void summonFx(ServerLevel level) {
        Vec3 c = position();
        if (phaseTicks == 1) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(c.x, c.y, c.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        // Espiral que converge do anel de giz para o altar.
        double r = (circle.radius + 0.5) * (1.0 - phaseTicks / (double) SUMMON_TICKS);
        for (int i = 0; i < 3; i++) {
            double a = phaseTicks * 0.35 + i * (Math.PI * 2 / 3);
            level.sendParticles(ElementFx.dust(kind().element, 1.4F), c.x + Math.cos(a) * r, c.y + 0.2 + phaseTicks * 0.03,
                    c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (phaseTicks % 4 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 4, 0.4, 0.2, 0.4, 0.02);
        if (phaseTicks == 35) {
            level.playSound(null, c.x, c.y, c.z, roarSound(), SoundSource.HOSTILE, 2.0F, 0.5F);
            ElementFx.burst(level, c.add(0, 1.0, 0), kind().element, 40, 0.8);
        }
        if (phaseTicks == SUMMON_TICKS - 1) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.4F);
            ElementFx.ring(level, c.add(0, 0.2, 0), kind().element, circle.radius + 0.5, 32);
        }
    }

    /** Correntes de sangue das velas até o Patrono + o anel brilhando. */
    private void boundFx(ServerLevel level) {
        if (altarPos == null) return;
        if (tickCount % 5 == 0) {
            Vec3 chest = position().add(0, getBbHeight() * 0.55, 0);
            DustParticleOptions dust = new DustParticleOptions(CHAIN_COLOR, 0.9F);
            for (BlockPos candle : circle.litCandles(level, altarPos)) {
                Vec3 from = Vec3.atCenterOf(candle).add(0, 0.1, 0);
                for (int i = 1; i <= 7; i++) {
                    Vec3 p = from.lerp(chest, i / 8.0);
                    level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (tickCount % 20 == 0) {
            ElementFx.ring(level, Vec3.atBottomCenterOf(altarPos).add(0, 0.15, 0), kind().element, circle.radius + 0.5, 20);
        }
    }

    private void sealFx(ServerLevel level) {
        if (phaseTicks % 3 == 0) {
            level.sendParticles(ElementFx.signature(kind().element), getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.5, 1.0, 0.5, 0.05);
            level.sendParticles(Vfx.enchant(), getX(), getY() + 1.0, getZ(), 10, 0.6, 1.2, 0.6, 0.5);
        }
    }

    // ---------------------------------------------------------------- ritual: selar / libertar
    /** Pacto cumprido: some com a animação de partida e o círculo se apaga. */
    public void sealPact() {
        if (!(level() instanceof ServerLevel level) || !isBound()) return;
        speak("sealed");
        setPhase(Phase.SEALED);
        if (altarPos != null) {
            circle.release(level, altarPos);
            PactRitual.clearAltar(level, altarPos, getUUID());
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    /** O Patrono se liberta e ataca (recusa, prazo, falha ou círculo quebrado). */
    public void breakFree(Release reason) {
        if (!(level() instanceof ServerLevel level) || phase() == Phase.FREE || phase() == Phase.SEALED) return;
        speak("freed_" + reason.key);
        if (altarPos != null) {
            circle.release(level, altarPos);
            PactRitual.clearAltar(level, altarPos, getUUID());
        }
        ServerPlayer o = ownerPlayer(level);
        if (o != null) PactRitual.onPactBroken(o, this, reason);
        setPhase(Phase.FREE);
        applyFreeStats();

        // Onda de choque: empurra quem estiver perto.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0), e -> e != this && e.isAlive())) {
            Vec3 d = e.position().subtract(position());
            d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
            e.push(d.x * 1.3, 0.55, d.z * 1.3);
            e.hurtMarked = true;
        }
        ElementFx.burst(level, position().add(0, 1.2, 0), kind().element, 80, 1.5);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.6F, 0.7F);
        Cutscenes.playNear(this, 32, "boss_intro", kind().displayName(),
                Component.translatable("warlock.isekai_mahou.unbound"), kind().color, 50, null);
        if (o != null && o.level() == level && o.distanceToSqr(this) < 40 * 40 && !o.isCreative() && !o.isSpectator()) setTarget(o);
    }

    /**
     * Vida/dano/armadura escalam com o nº do pacto. O 1º pacto acontece no começo do jogo, então o
     * Patrono liberto começa como um minichefe vencível (~55% da vida base) e chega a ~155% no 10º.
     */
    private void applyFreeStats() {
        int t = tier();
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.round(baseHealth() * (0.55 + 0.11 * (t - 1))));
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(baseDamage() * 0.65 + 0.9 * (t - 1));
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(4.0 + t);
        setHealth(getMaxHealth());
    }

    private void freeTick(ServerLevel level) {
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enraged = true;
            speak("enrage");
            ElementFx.burst(level, position().add(0, 1.5, 0), kind().element, 50, 1.0);
            onEnrage(level);
        }
        Player near = level.getNearestPlayer(this, 64);
        if (near == null || near.isSpectator()) emptyTicks++;
        else emptyTicks = 0;
        if (emptyTicks > 1200) {
            // Ninguém por perto há 1 minuto: volta para o seu plano.
            level.sendParticles(Vfx.reversePortal(), getX(), getY() + 1.5, getZ(), 60, 0.6, 1.2, 0.6, 0.05);
            discard();
        }
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return isFree() && !flies() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isFree() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20.0F, 1.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return isFree() && super.canUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override public boolean canUse() { return isFree() && super.canUse(); }
        });
    }

    /** Gira o corpo e a cabeça para o alvo (as formas de magia usam a rotação de quem conjura). */
    protected void faceTarget(LivingEntity t) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ(), dy = t.getEyeY() - getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    public float spellPower() {
        return 1.0F + 0.12F * (tier() - 1) + (enraged ? 0.25F : 0F);
    }

    protected void castSpellAt(ServerLevel level, LivingEntity target) {
        faceTarget(target);
        triggerAnim("action", "cast");
        SpellRecipe r = PatronSpells.pick(kind(), random);
        boolean cast = r != null && SpellCaster.castAsMob(this, kind().element, r, spellPower()) != null;
        if (!cast) fallbackRanged(level, target);
    }

    /** Movimento durante o combate (os voadores sobrescrevem). */
    protected void moveInCombat(LivingEntity target, double dist) {
        if (dist > meleeReach() - 0.6) getNavigation().moveTo(target, enraged ? 1.2 : 1.0);
        else getNavigation().stop();
    }

    protected void combatTick(LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        getLookControl().setLookAt(target, 30F, 30F);
        double dist = distanceTo(target);
        moveInCombat(target, dist);
        if (attackCooldown > 0) attackCooldown--;
        if (spellCooldown > 0) spellCooldown--;
        if (specialCooldown > 0) specialCooldown--;

        if (attackWindup > 0) {
            if (--attackWindup == 0 && distanceTo(target) <= meleeReach() + 1.0) doHurtTarget(target);
            return;
        }
        if (attackCooldown <= 0 && dist <= meleeReach()) {
            triggerAnim("action", "attack");
            attackWindup = 8;
            attackCooldown = enraged ? 22 : 32;
            return;
        }
        if (specialCooldown <= 0 && dist < 18) {
            faceTarget(target);
            triggerAnim("action", "special");
            special(level, target);
            specialCooldown = (enraged ? 140 : 220) + random.nextInt(60);
            spellCooldown = Math.max(spellCooldown, 30);
            return;
        }
        if (spellCooldown <= 0 && dist > 3.5 && dist < 26 && hasLineOfSight(target)) {
            castSpellAt(level, target);
            spellCooldown = (enraged ? 45 : 75) + random.nextInt(30);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) onMeleeHit(le);
        return hit;
    }

    /** Ataca o alvo do Patrono quando ele está livre. */
    static class CombatGoal extends Goal {
        private final PatronEntity patron;

        CombatGoal(PatronEntity patron) {
            this.patron = patron;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = patron.getTarget();
            return patron.isFree() && t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            patron.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = patron.getTarget();
            if (t != null) patron.combatTick(t);
        }
    }

    // =====================================================================================
    //  Dano, interação, morte
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBound() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // A barreira do círculo protege (e prende) o Patrono.
            if (level() instanceof ServerLevel level && source.getEntity() instanceof Player) {
                level.sendParticles(Vfx.magicCrit(), getX(), getY() + getBbHeight() * 0.5, getZ(), 12, 0.5, 0.8, 0.5, 0.2);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        Phase p = phase();
        if (p != Phase.OFFERING && p != Phase.PENDING) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer sp) PactRitual.openDialog(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void die(DamageSource source) {
        if (!rewarded && level() instanceof ServerLevel level) {
            rewarded = true;
            if (isFree()) {
                speak("death");
                // Convenção do mod: boss.<mob> para quem estava perto + XP mágico.
                List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48.0));
                for (ServerPlayer p : near) {
                    MagicApi.addCounter(p, "boss." + kind().entityId(), 1);
                    MagicApi.giveXp(p, 40 + 25 * tier(), "boss");
                }
            } else if (altarPos != null && phase() != Phase.SEALED) {
                // Removido à força enquanto preso (/kill): desfaz o ritual sem recompensa.
                circle.release(level, altarPos);
                PactRitual.clearAltar(level, altarPos, getUUID());
                ServerPlayer o = ownerPlayer(level);
                if (o != null) PactRitual.onPactBroken(o, this, Release.TIMEOUT);
            }
        }
        super.die(source);
    }

    /** Morte mais longa: some afundando (animação "vanish") em vez de tombar. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel level && deathTime % 3 == 0) {
            level.sendParticles(ElementFx.signature(kind().element), getX(), getY() + 1.0, getZ(), 6, 0.6, 1.0, 0.6, 0.05);
            level.sendParticles(Vfx.largeSmoke(), getX(), getY() + 0.5, getZ(), 3, 0.5, 0.3, 0.5, 0.02);
        }
        if (deathTime >= 40 && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, type, data);
        // Patrono "selvagem" (ovo / comando): já nasce livre e hostil.
        if (phase() == Phase.FREE) {
            applyFreeStats();
            setNoGravity(flies());
            if (level() instanceof ServerLevel sl) {
                ElementFx.burst(sl, position().add(0, 1.2, 0), kind().element, 40, 1.0);
                // Fala depois de entrar no mundo (o cliente precisa conhecer a entidade do anúncio).
                SpellScheduler.schedule(2, () -> {
                    if (isAlive() && isFree()) speak("wild");
                });
            }
        }
        return d;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return isFree();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return isFree() && super.isPushable();
    }

    @Override
    public boolean isPushedByFluid() {
        return isFree() && !flies();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Preso no círculo ele não impede o dono de dormir enquanto junta o tributo (o prazo é de 1 dia). */
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return isFree();
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return isFree();
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("PatronPhase", (byte) phase().ordinal());
        if (owner != null) tag.putUUID("PatronOwner", owner);
        if (altarPos != null) tag.putLong("PatronAltar", altarPos.asLong());
        tag.putInt("PatronCircle", circle.ordinal());
        tag.putInt("PatronPact", pactNumber);
        if (demand != null) tag.put("PatronDemand", demand.save());
        tag.putBoolean("PatronFavor", favor);
        tag.putLong("PatronOfferDeadline", offerDeadline);
        tag.putLong("PatronDeadline", deadline);
        tag.putInt("PatronPhaseTicks", phaseTicks);
        tag.putBoolean("PatronEnraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("PatronOwner") ? tag.getUUID("PatronOwner") : null;
        altarPos = tag.contains("PatronAltar") ? BlockPos.of(tag.getLong("PatronAltar")) : null;
        circle = CirclePattern.byOrdinal(tag.getInt("PatronCircle"));
        pactNumber = tag.contains("PatronPact") ? tag.getInt("PatronPact") : 3;
        demand = PatronDemand.load(tag.getCompound("PatronDemand"));
        favor = tag.getBoolean("PatronFavor");
        offerDeadline = tag.getLong("PatronOfferDeadline");
        deadline = tag.getLong("PatronDeadline");
        enraged = tag.getBoolean("PatronEnraged");
        Phase[] v = Phase.values();
        Phase p = tag.contains("PatronPhase") ? v[Math.max(0, Math.min(v.length - 1, tag.getByte("PatronPhase")))] : Phase.FREE;
        // Sem altar ou sem tributo não há como continuar preso.
        if (p != Phase.FREE && (altarPos == null || demand == null)) p = Phase.FREE;
        setPhase(p);
        phaseTicks = tag.getInt("PatronPhaseTicks");
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String base = "animation." + kind().entityId() + ".";
        controllers.add(new AnimationController<>(this, "main", 6, this::mainController));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay(base + "attack"))
                .triggerableAnim("cast", RawAnimation.begin().thenPlay(base + "cast"))
                .triggerableAnim("talk", RawAnimation.begin().thenPlay(base + "talk"))
                .triggerableAnim("special", RawAnimation.begin().thenPlay(base + "special")));
    }

    private PlayState mainController(AnimationState<PatronEntity> state) {
        Phase p = phase();
        if (isDeadOrDying() || p == Phase.SEALED) return state.setAndContinue(animVanish);
        if (p == Phase.SUMMONING) return state.setAndContinue(animSummon);
        if (p == Phase.FREE) return state.setAndContinue(state.isMoving() ? animMove : animIdle);
        return state.setAndContinue(animBound);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
