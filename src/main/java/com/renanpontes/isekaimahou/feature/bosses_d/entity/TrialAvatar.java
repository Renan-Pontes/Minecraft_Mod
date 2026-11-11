package com.renanpontes.isekaimahou.feature.bosses_d.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.kit.AvatarKit;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.kit.AvatarKits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * "Avatar Elemental" — chefe das Câmaras de Provação. Humanoide flutuante com núcleo de cristal, um por elemento
 * (fusões usam o corpo do pai A e o brilho do pai B). Lê elemento/rank do persistentData no 1º tick
 * ({@link TrialElement}). Três fases (66% / 33%) com transição invulnerável, golpes TELEGRAFADOS por elemento
 * (kits em {@code kit/}), magias de glifo programáveis, anti-trapaça (salto/teleporte/projétil) e escala de grupo.
 */
public class TrialAvatar extends Monster implements GeoEntity {
    public static final String MINION_KEY = "isekai_mahou.bosses_d_master";
    private static final double BAR_RANGE = 64.0;
    private static final double INTRO_RANGE = 28.0;
    private static final double LEASH = 40.0;
    private static final int INTRO_TICKS = 70;
    private static final int TRANSITION_TICKS = 50;
    private static final int DEATH_TICKS = 70;
    private static final float[] PHASES = {0.66F, 0.33F};

    private static final EntityDataAccessor<String> DATA_ELEMENT = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_RANK = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_START = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_COUNT = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BEAM_SPEED = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_LEN = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_ANGLE = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_CHANNEL = SynchedEntityData.defineId(TrialAvatar.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.trial_avatar.idle");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("animation.trial_avatar.float");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.trial_avatar.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.trial_avatar.death");
    /** Animações de ação (controlador "action"): todas existem no .animation.json. */
    public static final List<String> ACTIONS = List.of("intro", "phase", "cast", "barrage", "slam", "dash", "channel",
            "summon", "meteor", "nova", "wave", "beam", "breath", "pillar", "vortex", "blink", "chains");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---------------- estado do servidor ----------------
    private boolean configured;
    private Element element = Element.ARCANE;
    private int rank = 1;
    @Nullable private ServerBossEvent bar;
    @Nullable private List<AvatarKit> kits;
    @Nullable private List<AvatarMove> moves;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<UUID, Long> beamHits = new HashMap<>();
    private final Set<UUID> introSeen = new HashSet<>();
    @Nullable private AvatarMove current;
    @Nullable private AvatarMove forcedNext;
    private int moveTick;
    private int globalCooldown = 30;
    private boolean introDone;
    private int introTicks = -1;
    private int transitionTicks;
    @Nullable private Vec3 home;
    private int noTargetTicks;
    private int unreachableTicks;
    private int leapTicks;
    private int strafeTicks;
    private int strafeSide = 1;
    private long ccImmuneUntil;
    private long lastSpellAnnounce;
    private boolean deathHandled;
    /** Última animação de ação disparada (para interrompê-la na morte: a animação "death" não pode ser coberta). */
    @Nullable private String lastAction;
    /** Muda a cada transição de fase: tarefas agendadas de golpes antigos param sozinhas. */
    private int epoch;

    public TrialAvatar(EntityType<? extends TrialAvatar> type, Level level) {
        super(type, level);
        this.xpReward = 45;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 210.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ELEMENT, Element.ARCANE.id);
        builder.define(DATA_RANK, 1);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_BEAM_START, -1);
        builder.define(DATA_BEAM_COUNT, 0);
        builder.define(DATA_BEAM_SPEED, 0F);
        builder.define(DATA_BEAM_LEN, 0F);
        builder.define(DATA_BEAM_ANGLE, 0F);
        builder.define(DATA_CHANNEL, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, TrialAvatar.class, TrialSentinel.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    /** Elemento (sincronizado: o cliente usa para textura, brilho e partículas). */
    public Element element() {
        Element e = Element.byId(entityData.get(DATA_ELEMENT));
        return e != null ? e : Element.ARCANE;
    }

    public int rank() {
        return entityData.get(DATA_RANK);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    /** Cor RGB do elemento (telegrafia, anúncios, partículas). */
    public int rgb() {
        return element().color;
    }

    /** Cor do brilho do modelo: elemento base; fusão = cor do pai B. */
    public int glowRgb() {
        return TrialElement.lighten(TrialElement.accent(element()).color, 0.18F);
    }

    public boolean isChanneling() {
        return entityData.get(DATA_CHANNEL);
    }

    public void setChanneling(boolean on) {
        entityData.set(DATA_CHANNEL, on);
    }

    public int epoch() {
        return epoch;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BdConfigured", configured);
        tag.putString("BdElement", element.id);
        tag.putInt("BdRank", rank);
        tag.putInt("BdPhase", phase());
        tag.putBoolean("BdIntroDone", introDone);
        if (home != null) {
            tag.putDouble("BdHomeX", home.x);
            tag.putDouble("BdHomeY", home.y);
            tag.putDouble("BdHomeZ", home.z);
        }
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        tag.put("BdIntroSeen", seen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = tag.getBoolean("BdConfigured");
        if (configured) {
            Element e = Element.byId(tag.getString("BdElement"));
            element = e != null ? e : Element.ARCANE;
            rank = Mth.clamp(tag.getInt("BdRank"), 1, 7);
            entityData.set(DATA_ELEMENT, element.id);
            entityData.set(DATA_RANK, rank);
            xpReward = 30 + 15 * rank;
        }
        if (tag.contains("BdPhase")) entityData.set(DATA_PHASE, Mth.clamp(tag.getInt("BdPhase"), 1, 3));
        introDone = tag.getBoolean("BdIntroDone");
        if (tag.contains("BdHomeX")) home = new Vec3(tag.getDouble("BdHomeX"), tag.getDouble("BdHomeY"), tag.getDouble("BdHomeZ"));
        introSeen.clear();
        for (Tag t : tag.getList("BdIntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        kits = null;
        moves = null;
        if (bar != null) bar.setName(barName());
    }

    /** 1º tick: lê elemento/rank e ajusta vida, armadura e dano ao rank. */
    private void configure() {
        configured = true;
        element = TrialElement.readElement(this);
        rank = TrialElement.readRank(this);
        entityData.set(DATA_ELEMENT, element.id);
        entityData.set(DATA_RANK, rank);
        setBase(Attributes.MAX_HEALTH, 120 + 90 * rank);
        setBase(Attributes.ARMOR, Math.min(18, 4 + 2 * rank));
        setBase(Attributes.ARMOR_TOUGHNESS, 2 + rank);
        setBase(Attributes.ATTACK_DAMAGE, 6 + 2 * rank);
        setBase(Attributes.MOVEMENT_SPEED, 0.25 + 0.006 * rank);
        setHealth(getMaxHealth());
        xpReward = 30 + 15 * rank;
        Vec3 arena = BossKit.arenaCenter(this);
        home = arena != null ? arena : position();
        kits = null;
        moves = null;
        if (bar != null) {
            bar.setName(barName());
            bar.setColor(TrialElement.barColor(element));
        }
    }

    private void setBase(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        AttributeInstance a = getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }

    @Override
    protected Component getTypeName() {
        return keyed("entity.isekai_mahou.trial_avatar." + element().id);
    }

    /** Tradução com chave montada em tempo de execução (todas as variantes existem em src/main/lang/bosses_d). */
    private static net.minecraft.network.chat.MutableComponent keyed(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private Component barName() {
        return Component.translatable("boss.isekai_mahou.trial_avatar.bar", getName(), TrialsApi.roman(rank()));
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bar != null) bar.setName(barName());
    }

    // =====================================================================================
    //  Regras de chefe
    // =====================================================================================

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    /** Não some no Pacífico (uma Provação em andamento não pode perder o chefe/elite). */
    @Override
    protected boolean shouldDespawnInPeaceful() {
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

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 1.0, 1.5);
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
        this.fallDistance = 0;
    }

    /** Avatares de fogo (e fusões com fogo) não queimam. */
    @Override
    public boolean fireImmune() {
        return element().matches(Element.FIRE) || super.fireImmune();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof TrialAvatar || other instanceof TrialSentinel || isMinion(other)) return true;
        if (other instanceof Vex v && v.getOwner() == this) return true;
        if (BossKit.isTrialBoss(this) && BossKit.isTrialBoss(other)) return true;
        return super.isAlliedTo(other);
    }

    /** Controles duram no máx. 1,5 s e dão 5 s de imunidade: o chefe não fica preso. */
    @Override
    public boolean addEffect(MobEffectInstance inst, @Nullable Entity source) {
        // Avatares de água não ficam "Encharcados"
        if (element().matches(Element.WATER) && inst.getEffect().is(CoreRegistries.SOAKED.getKey())) return false;
        if (isCrowdControl(inst.getEffect())) {
            long now = level().getGameTime();
            if (now < ccImmuneUntil) return false;
            ccImmuneUntil = now + 100;
            inst = new MobEffectInstance(inst.getEffect(), Math.min(inst.getDuration(), 30), Math.min(inst.getAmplifier(), 1),
                    inst.isAmbient(), inst.isVisible(), inst.showIcon());
        }
        return super.addEffect(inst, source);
    }

    static boolean isCrowdControl(Holder<MobEffect> e) {
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
        // Qualquer agressor (jogador, bicho do jogador, mob) acorda o chefe: ele nunca apanha parado sem reagir
        if (!introDone && introTicks < 0 && configured && attacker instanceof LivingEntity le
                && !(le instanceof Player p && p.isSpectator())) {
            startIntro((ServerLevel) level());
        }
        if (introTicks >= 0 || transitionTicks > 0 || deathHandled) {
            shieldFx();
            return false;
        }
        // Atirar de fora da área do chefe (onde ele não persegue) não vale: imune a quem está longe de casa
        if (attacker instanceof LivingEntity le && !withinLeash(le)) {
            shieldFx();
            if (le instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.trial_avatar.too_far"), true);
            }
            return false;
        }
        // Um golpe não leva mais de 20% da vida (lendas não pulam a luta inteira)
        amount = Math.min(amount, Math.max(30F, getMaxHealth() * 0.2F));
        amount *= elementMultiplier(source);
        if (amount <= 0) return false;
        return super.hurt(source, amount);
    }

    /** Resiste um pouco ao próprio elemento e é fraco ao oposto (lido da marca elemental do núcleo neste tick). */
    private float elementMultiplier(DamageSource source) {
        if (!source.is(CoreRegistries.SPELL_DAMAGE)) return 1F;
        Element hit = ElementReactions.lastElement(this, level().getGameTime(), 0);
        if (hit == null || hit == Element.ARCANE) return 1F;
        Element me = element();
        float mult = 1F;
        for (Element base : Element.BASE) {
            if (!hit.matches(base)) continue;
            if (me.matches(base)) mult *= 0.75F;
            Element opp = TrialElement.opposite(base);
            if (opp != null && me.matches(opp)) mult *= 1.35F;
        }
        return mult;
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        int p = phase();
        float floor = p <= PHASES.length ? getMaxHealth() * PHASES[p - 1] : 0F;
        super.actuallyHurt(source, amount);
        // A vida não pula uma fase: para no limiar e a transição acontece no próximo tick
        if (floor > 0 && getHealth() < floor && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) setHealth(floor);
    }

    private void shieldFx() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.55, getZ(), 12, 0.7, 1.0, 0.7, 0.2);
        sl.sendParticles(ElementFx.dust(element(), 1.4F), getX(), getY() + getBbHeight() * 0.55, getZ(), 10, 0.6, 0.9, 0.6, 0);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
    }

    private void escapeWall() {
        for (int i = 0; i < 12; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 8;
            double z = getZ() + (random.nextDouble() - 0.5) * 8;
            if (randomTeleport(x, getY() + random.nextInt(4), z, true)) return;
        }
        setPos(getX(), getY() + 2, getZ());
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && isAlive()) clientFx();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (!configured) configure();
        // Na Provação a casa é sempre o centro da arena (o módulo trials pode gravá-la depois do 1º tick)
        Vec3 arena = BossKit.arenaCenter(this);
        if (arena != null) home = arena;
        if (home == null) home = position();
        tickBar(sl);
        for (AvatarKit k : kits()) k.tickPassive(sl);
        if (!introDone) {
            tickIntro(sl);
            return;
        }
        if (tickCount % 20 == 0) lateIntros(sl);
        checkPhase(sl);
        if (transitionTicks > 0) {
            tickTransition(sl);
            return;
        }
        keepInArena(sl);

        LivingEntity target = getTarget();
        if (target != null && !validTarget(target)) {
            setTarget(null);
            target = null;
        }
        // "Agressivo" é sincronizado: o cliente usa para trocar a pose parada (idle → float de combate)
        setAggressive(target != null);
        if (target != null) {
            noTargetTicks = 0;
            combatTick(sl, target);
        } else {
            if (current != null) cancelMove();
            idleTick(sl);
        }
    }

    private void tickBar(ServerLevel sl) {
        if (bar == null) {
            bar = new ServerBossEvent(barName(), TrialElement.barColor(element()), BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setPlayBossMusic(true);
        }
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (tickCount % 10 != 0) return;
        boolean show = introDone || introTicks >= 0;
        for (ServerPlayer p : sl.players()) {
            boolean near = show && p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
            if (near) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
        // Quem saiu da dimensão (fim da Provação, /mahou trial leave) ou desconectou não fica com a barra presa
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.hasDisconnected() || p.level() != sl) bar.removePlayer(p);
        }
    }

    // ---------------- apresentação ----------------
    private void tickIntro(ServerLevel sl) {
        stopMoving();
        if (introTicks >= 0) {
            Player look = sl.getNearestPlayer(this, 40);
            if (look != null) getLookControl().setLookAt(look, 10F, 10F);
            int t = INTRO_TICKS - introTicks;
            introFx(sl, t);
            introTicks--;
            if (introTicks < 0) {
                introDone = true;
                globalCooldown = 16;
            }
            return;
        }
        if (tickCount % 10 != 0) return;
        boolean trial = BossKit.isTrialBoss(this);
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d2 = p.distanceToSqr(this);
            if ((trial && d2 < 48 * 48) || (d2 < INTRO_RANGE * INTRO_RANGE && hasLineOfSight(p))) {
                startIntro(sl);
                return;
            }
        }
    }

    private void startIntro(ServerLevel sl) {
        if (introTicks >= 0 || introDone) return;
        introTicks = INTRO_TICKS;
        BossKit.scaleToParty(this, 48);
        playAction("intro");
        Component subtitle = TrialsApi.title(element(), rank());
        Cutscenes.playNear(this, INTRO_RANGE + 20, "boss_intro", getName(), subtitle, rgb(), 80, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < (INTRO_RANGE + 20) * (INTRO_RANGE + 20))) {
            introSeen.add(p.getUUID());
        }
        Announce.send(this, getName().copy().withColor(rgb()), rgb(), Announce.STYLE_BOSS);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void introFx(ServerLevel sl, int t) {
        Element e = element();
        if (t < 40) {
            // Energia convergindo para o núcleo
            double r = 3.5 - t * 0.07;
            for (int i = 0; i < 4; i++) {
                double a = t * 0.3 + i * Math.PI / 2;
                sl.sendParticles(ElementFx.dust(e, 1.3F), getX() + Math.cos(a) * r, getY() + 1.8, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 4 == 0) sl.sendParticles(ElementFx.signature(e), getX(), getY() + 0.2, getZ(), 6, 1.2, 0.1, 1.2, 0.02);
        } else if (t == 40) {
            BossKit.roar(this, SoundEvents.WARDEN_ROAR, 1.3F);
            ElementFx.burst(sl, position().add(0, 1.8, 0), e, 60, 1.2);
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8, getZ(), 1, 0, 0, 0, 0);
            Announce.send(this, keyed("announce.isekai_mahou.trial_avatar.intro." + TrialElement.body(e).id), rgb(),
                    Announce.STYLE_BOSS);
        }
    }

    private void lateIntros(ServerLevel sl) {
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > INTRO_RANGE * INTRO_RANGE || !hasLineOfSight(p)) continue;
            if (introSeen.add(p.getUUID())) {
                Cutscenes.play(p, "boss_intro", this, getName(), TrialsApi.title(element(), rank()), rgb(), 60, false);
            }
        }
    }

    // ---------------- fases ----------------
    private void checkPhase(ServerLevel sl) {
        if (transitionTicks > 0) return;
        float frac = getHealth() / getMaxHealth();
        int want = 1;
        for (float t : PHASES) if (frac <= t + 1.0E-4F) want++;
        int ph = phase();
        if (want <= ph) return;
        int next = ph + 1;
        entityData.set(DATA_PHASE, next);
        cancelMove();
        Telegraph.cancelAll(this);
        for (AvatarKit k : kits()) k.reset();
        epoch++;
        transitionTicks = TRANSITION_TICKS;
        playAction("phase");
        Component sub = keyed("boss.isekai_mahou.trial_avatar.phase" + next);
        Cutscenes.playNear(this, 40, "boss_phase", getName(), sub, rgb(), 50, null);
        Announce.send(this, keyed("announce.isekai_mahou.trial_avatar.phase" + next + "." + TrialElement.body(element()).id),
                rgb(), Announce.STYLE_BOSS);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void tickTransition(ServerLevel sl) {
        stopMoving();
        int t = TRANSITION_TICKS - transitionTicks;
        Element e = element();
        if (t % 2 == 0) {
            double a = t * 0.4;
            for (int i = 0; i < 6; i++) {
                double ang = a + i * Math.PI / 3;
                double r = 2.5 + Math.sin(t * 0.3) * 0.5;
                sl.sendParticles(ElementFx.dust(e, 1.5F), getX() + Math.cos(ang) * r, getY() + 0.3 + t * 0.05, getZ() + Math.sin(ang) * r,
                        1, 0, 0, 0, 0);
            }
        }
        if (t == 20) {
            BossKit.roar(this);
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.8, getZ(), 1, 0, 0, 0, 0);
            ElementFx.burst(sl, position().add(0, 1.5, 0), e, 80, 2.0);
            // Onda sem dano: afasta todo mundo para o recomeço da luta
            for (LivingEntity f : foesNear(position(), 8)) {
                Vec3 d = f.position().subtract(position()).multiply(1, 0, 1);
                d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
                f.push(d.x * 1.1, 0.45, d.z * 1.1);
                f.hurtMarked = true;
            }
            BossKit.shake(sl, position(), 32, 1.6F, 20);
        }
        transitionTicks--;
        if (transitionTicks == 0) {
            globalCooldown = 8;
            // Abertura de cada fase: fase 2 invoca/assinatura, fase 3 o golpe máximo
            if (phase() >= 3) forcedNext = ultimateMove();
            else forcedNext = signatureMove();
        }
    }

    // ---------------- combate ----------------
    private void combatTick(ServerLevel sl, LivingEntity target) {
        if (leapTicks > 0) {
            leapTicks--;
            return;
        }
        if (current != null) {
            AvatarMove m = current;
            if (m.stationary) stopMoving();
            if (m.faceTarget) faceTarget(target);
            m.tick.run(sl, target, moveTick);
            if (current != m) return;
            moveTick++;
            if (moveTick >= m.duration) finishMove();
            return;
        }
        if (antiCheese(sl, target)) return;
        moveCombat(target);
        getLookControl().setLookAt(target, 30F, 30F);
        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }
        AvatarMove next = forcedNext != null ? forcedNext : pick(target);
        forcedNext = null;
        if (next != null) startMove(next);
        else globalCooldown = 5;
    }

    private List<AvatarKit> kits() {
        if (kits == null) kits = AvatarKits.create(this, element);
        return kits;
    }

    private List<AvatarMove> moves() {
        if (moves == null) {
            moves = new ArrayList<>();
            for (AvatarKit k : kits()) moves.addAll(k.moves());
            AvatarMove ult = kits().get(0).ultimate();
            if (ult != null) moves.add(ult);
        }
        return moves;
    }

    @Nullable
    private AvatarMove ultimateMove() {
        for (AvatarMove m : moves()) if (m.ultimate) return m;
        return null;
    }

    /** O 1º golpe do kit do corpo (o mais característico do elemento). */
    @Nullable
    private AvatarMove signatureMove() {
        List<AvatarMove> body = kits().get(0).moves();
        return body.isEmpty() ? null : body.get(0);
    }

    @Nullable
    private AvatarMove pick(LivingEntity target) {
        long now = level().getGameTime();
        double dist = distanceTo(target);
        int ph = phase();
        List<AvatarMove> ok = new ArrayList<>();
        int total = 0;
        for (AvatarMove m : moves()) {
            if (ph < m.minPhase || dist < m.minRange || dist > m.maxRange) continue;
            if (now < readyAt.getOrDefault(m.id, 0L) || !m.condition.test(target)) continue;
            // Perto demais: golpes corpo a corpo ganham peso (o jogador corpo a corpo também tem chance)
            int w = m.weight;
            if (dist < 5 && m.maxRange <= 12) w += 6;
            ok.add(m);
            total += w;
        }
        if (ok.isEmpty()) return null;
        int r = random.nextInt(total);
        for (AvatarMove m : ok) {
            int w = m.weight + (dist < 5 && m.maxRange <= 12 ? 6 : 0);
            r -= w;
            if (r < 0) return m;
        }
        return ok.get(0);
    }

    private void startMove(AvatarMove m) {
        if (current != null) cancelMove();
        current = m;
        moveTick = 0;
        float cdMult = 1.0F - 0.15F * (phase() - 1);
        readyAt.put(m.id, level().getGameTime() + Math.max(20, (int) (m.cooldown * cdMult)));
        if (m.anim != null) playAction(m.anim);
        if (m.ultimate) setChanneling(true);
    }

    /** Dispara uma animação do controlador "action" (todas as de {@link #ACTIONS}). */
    public void playAction(String anim) {
        lastAction = anim;
        triggerAnim("action", anim);
    }

    /** Id do golpe em andamento (null = nenhum). Tarefas agendadas conferem isto antes de ligar algo persistente. */
    @Nullable
    public String currentMoveId() {
        return current != null ? current.id : null;
    }

    private void finishMove() {
        AvatarMove m = current;
        current = null;
        if (m != null && m.onEnd != null) m.onEnd.run();
        setChanneling(false);
        globalCooldown = pauseBetweenMoves();
    }

    private void cancelMove() {
        AvatarMove m = current;
        current = null;
        if (m != null && m.onEnd != null) m.onEnd.run();
        setChanneling(false);
        stopBeams();
        setInvisible(false);
        globalCooldown = 20;
    }

    private int pauseBetweenMoves() {
        int base = switch (phase()) {
            case 1 -> 28;
            case 2 -> 20;
            default -> 14;
        };
        return Math.max(8, base - rank());
    }

    /** Movimento entre golpes: aproxima até ~7 blocos, recua se colado, e circula o alvo. */
    private void moveCombat(LivingEntity target) {
        double d = distanceTo(target);
        // Recalcula o caminho no máx. a cada 10 ticks (ou quando acabou): A* todo tick pesa no servidor
        boolean repath = getNavigation().isDone() || tickCount % 10 == 0;
        if (d > 10) {
            if (repath) getNavigation().moveTo(target, 1.05);
        } else if (d < 2.8) {
            if (!repath) return;
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = position().add(away.scale(4));
            getNavigation().moveTo(to.x, to.y, to.z, 1.0);
        } else if (--strafeTicks <= 0) {
            strafeTicks = 30 + random.nextInt(30);
            if (random.nextInt(3) == 0) strafeSide = -strafeSide;
            Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
            Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(4.0 * strafeSide);
            Vec3 dest = position().add(side).add(to.normalize().scale(d > 7 ? 2 : -1));
            getNavigation().moveTo(dest.x, dest.y, dest.z, 0.85);
        }
    }

    /**
     * Anti-trapaça: alvo inalcançável (pilar, buraco, barco, voo) por 2 s → salta até ele (pouso telegrafado),
     * ou teleporta para perto (com golpe telegrafado), ou dispara magia.
     */
    private boolean antiCheese(ServerLevel sl, LivingEntity target) {
        unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < 40) return false;
        unreachableTicks = 0;
        boolean airborne = !target.onGround() && !target.isInWater() && target.getVehicle() == null;
        double dy = target.getY() - getY();
        double flat = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (!airborne && onGround() && distanceTo(target) < 22 && !(dy > 6 && flat < 3)) {
            int t = BossKit.leapAt(this, target, 1.0);
            playAction("dash");
            leapTicks = t;
            Vec3 land = target.position();
            Telegraph.delayed(sl, Telegraph.Shape.circle(land, 3.5F).warmup(Math.max(10, t)).color(rgb()).owner(this), hits -> {
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    hitFoe(e, 1.0F);
                    Vec3 d = e.position().subtract(land).multiply(1, 0, 1);
                    d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
                    e.push(d.x * 0.9, 0.5, d.z * 0.9);
                    e.hurtMarked = true;
                }
                ElementFx.burst(sl, land.add(0, 0.3, 0), element(), 40, 1.6);
                sl.playSound(null, land.x, land.y, land.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
                BossKit.shake(sl, land, 14, 0.8F, 10);
            });
            return true;
        }
        if (!airborne && BossKit.blinkNear(this, target, 4)) {
            playAction("blink");
            Vec3 c = position();
            Telegraph.delayed(sl, Telegraph.Shape.circle(c, 3.8F).warmup(14).color(rgb()).owner(this), hits -> {
                for (LivingEntity e : hits) if (isFoe(e)) hitFoe(e, 0.9F);
                ElementFx.burst(sl, c.add(0, 0.5, 0), element(), 30, 1.4);
            });
            leapTicks = 14;
            return true;
        }
        // Último recurso (voando, sem chão perto): magia de glifo mirando o alvo
        playAction("cast");
        castSpell(target, false);
        globalCooldown = 20;
        return true;
    }

    private void keepInArena(ServerLevel sl) {
        if (current != null || leapTicks > 0 || home == null) return;
        double r = BossKit.arenaRadius(this);
        if (BossKit.isTrialBoss(this) && r > 0) {
            if (horizontalDistanceTo(home) > r + 3) {
                teleportFx(sl, position());
                teleportTo(home.x, home.y, home.z);
                teleportFx(sl, home);
            }
        } else if (distanceToSqr(home) > LEASH * LEASH) {
            teleportFx(sl, position());
            teleportTo(home.x, home.y, home.z);
            teleportFx(sl, home);
            setTarget(null);
        }
    }

    private double horizontalDistanceTo(Vec3 p) {
        return Math.hypot(getX() - p.x, getZ() - p.z);
    }

    private void teleportFx(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.4, at.z, 40, 0.5, 1.0, 0.5, 0.05);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private void idleTick(ServerLevel sl) {
        noTargetTicks++;
        if (home == null || noTargetTicks < 60) return;
        double d2 = distanceToSqr(home);
        if (d2 > 9) getNavigation().moveTo(home.x, home.y, home.z, 0.9);
        // Fora de uma Provação, sem ninguém por perto, o chefe se recompõe aos poucos
        if (!BossKit.isTrialBoss(this) && noTargetTicks > 200 && tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
            heal(getMaxHealth() * 0.01F);
        }
    }

    private boolean validTarget(LivingEntity t) {
        if (!t.isAlive() || t.isRemoved() || t.level() != level() || isAlliedTo(t)) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return withinLeash(t);
    }

    /** Dentro da área em que o chefe luta (arena da Provação + 10, ou {@link #LEASH} + 8 blocos de casa)? */
    private boolean withinLeash(Entity t) {
        if (home == null) return true;
        double r = BossKit.isTrialBoss(this) && BossKit.arenaRadius(this) > 0 ? BossKit.arenaRadius(this) + 10 : LEASH + 8;
        return t.distanceToSqr(home) < r * r;
    }

    // =====================================================================================
    //  API para os kits de golpes
    // =====================================================================================

    /** Dano-base dos golpes próprios: escala com rank, fase e grupo. */
    public float power() {
        return (4.0F + 1.8F * rank()) * (1.0F + 0.1F * (phase() - 1)) * BossKit.damageScale(this);
    }

    /** Poder das magias de glifo (multiplicador do castFrom). */
    public float spellPower() {
        return (0.8F + 0.22F * rank()) * (1.0F + 0.12F * (phase() - 1)) * BossKit.damageScale(this);
    }

    /**
     * Aquecimento da telegrafia: os longos (&gt; 20 ticks) ficam um pouco menores nos ranks altos e na fase 3 (nunca
     * menos de 80%); os curtos não mudam — sempre dá tempo de sair andando.
     */
    public int warm(int base) {
        if (base <= 20) return base;
        int cut = (rank() - 1) / 2 + (phase() >= 3 ? 2 : 0);
        return Math.max((int) Math.ceil(base * 0.8), base - cut);
    }

    public DamageSource spellSource() {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, this, this);
    }

    /** Causa {@code mult} × {@link #power()} num inimigo. */
    public boolean hitFoe(LivingEntity e, float mult) {
        if (!isFoe(e)) return false;
        return e.hurt(spellSource(), power() * mult);
    }

    /**
     * Quem os golpes atingem: jogadores (fora do criativo), bichos/invocações com dono e mobs brigando com o chefe.
     */
    public boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand || isAlliedTo(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e == getTarget() || e == getLastHurtByMob()) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        if (!(e instanceof Mob m) || m.getTarget() == null) return false;
        return m.getTarget() == this || isAlliedTo(m.getTarget());
    }

    public List<LivingEntity> foesNear(Vec3 c, double r) {
        List<LivingEntity> out = level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> isFoe(e) && e.distanceToSqr(c) <= r * r);
        // Jogadores primeiro, depois os mais perto
        out.sort(Comparator.<LivingEntity>comparingInt(e -> e instanceof Player ? 0 : 1).thenComparingDouble(e -> e.distanceToSqr(c)));
        return out;
    }

    @Nullable
    public Vec3 home() {
        return home;
    }

    /** O ponto está dentro da arena (ou, sem arena, a até 30 blocos de casa)? */
    public boolean inArena(Vec3 p) {
        if (home == null) return true;
        double r = BossKit.arenaRadius(this);
        if (r > 0) return Math.hypot(p.x - home.x, p.z - home.z) <= r - 1.0;
        return Math.hypot(p.x - home.x, p.z - home.z) <= 30;
    }

    /** Chão firme com espaço livre em cima perto de {@code p} (procura de Y+3 até Y−6), ou null. */
    @Nullable
    public Vec3 groundAt(Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int top = Mth.floor(p.y) + 3;
        for (int y = top; y >= top - 9; y--) {
            m.set(Mth.floor(p.x), y - 1, Mth.floor(p.z));
            BlockState below = level().getBlockState(m);
            VoxelShape s = below.getCollisionShape(level(), m);
            if (s.isEmpty()) continue;
            m.set(Mth.floor(p.x), y, Mth.floor(p.z));
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) continue;
            m.setY(y + 1);
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) continue;
            return new Vec3(p.x, y - 1 + s.max(Direction.Axis.Y), p.z);
        }
        return null;
    }

    public void stopMoving() {
        getNavigation().stop();
    }

    /** Vira o corpo e a cabeça para o alvo (as magias de glifo usam a direção do olhar). */
    public void faceTarget(Entity t) {
        Vec3 eye = getEyePosition();
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    /** Vira para uma direção horizontal (investidas, cones travados). */
    public void faceDir(Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    /** Ponto da mão direita (de onde saem as magias). */
    public Vec3 handPos() {
        double yaw = Math.toRadians(getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return position().add(0, 1.75, 0).add(fwd.scale(0.9)).add(right.scale(0.45));
    }

    /** Anúncio de golpe ("announce.isekai_mahou.trial_avatar.move.<key>"). */
    public void announceMove(String key) {
        Announce.send(this, keyed("announce.isekai_mahou.trial_avatar.move." + key), rgb(), Announce.STYLE_BOSS);
    }

    /**
     * Conjura uma magia de glifo do elemento na direção do alvo (com um pouco de mira à frente).
     * As programáveis (com ligações) são anunciadas pelo nome — no máx. uma vez a cada 8 s.
     */
    public void castSpell(LivingEntity target, boolean secondary) {
        if (!(level() instanceof ServerLevel sl)) return;
        faceTarget(target);
        Vec3 from = handPos();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0).add(target.getDeltaMovement().multiply(4, 0, 4));
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) dir = getLookAngle();
        AvatarSpells.Spell s = AvatarSpells.cast(this, element(), secondary, spellPower(), from, dir.normalize());
        ElementFx.castSound(sl, this, element());
        ElementFx.burst(sl, from, element(), 12, 0.25);
        long now = level().getGameTime();
        if (s != null && s.programmable() && now - lastSpellAnnounce > 160) {
            lastSpellAnnounce = now;
            Announce.send(this, keyed("announce.isekai_mahou.trial_avatar.spell." + s.key()), rgb(), Announce.STYLE_SPELL);
        }
    }

    // ---------------- lacaios ----------------
    public boolean isMinion(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.hasUUID(MINION_KEY) && d.getUUID(MINION_KEY).equals(getUUID());
    }

    public int minionCount() {
        return level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), m -> m.isAlive() && isMinion(m)).size();
    }

    /** Invoca um lacaio do elemento (criaturas do mod por id; reserva vanilla). Fica aliado e some quando o chefe morre. */
    @Nullable
    public Mob summonMinion(Vec3 at, @Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return null;
        Element body = random.nextBoolean() ? TrialElement.body(element()) : TrialElement.accent(element());
        String[] ids = switch (body) {
            case FIRE -> new String[] {"ember_imp", "salamander"};
            case WATER -> new String[] {"undine", "tide_serpent"};
            case EARTH -> new String[] {"moss_golem", "crystal_mole"};
            case WIND -> new String[] {"sylph", "storm_hawk"};
            case LIGHT -> new String[] {"lumen_sprite", "solar_sentinel"};
            case SHADOW -> new String[] {"creeping_shade", "void_bat"};
            default -> new String[] {"wandering_spirit", "arcane_golem"};
        };
        Entity e = null;
        String id = ids[random.nextInt(ids.length)];
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id)).orElse(null);
        if (type != null) e = type.create(sl);
        if (!(e instanceof Mob)) {
            if (e != null) e.discard();
            EntityType<? extends Mob> fb = body == Element.WATER ? EntityType.DROWNED
                    : body == Element.EARTH ? EntityType.SILVERFISH : EntityType.VEX;
            e = fb.create(sl);
        }
        if (!(e instanceof Mob mob)) return null;
        mob.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, sl, sl.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        mob.getPersistentData().putUUID(MINION_KEY, getUUID());
        if (mob instanceof Vex vex) {
            vex.setOwner(this);
            vex.setLimitedLife(20 * 30);
            vex.setBoundOrigin(BlockPos.containing(at));
        }
        if (target != null) mob.setTarget(target);
        sl.addFreshEntity(mob);
        ElementFx.burst(sl, at.add(0, 0.8, 0), element(), 24, 0.6);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 1.2F);
        return mob;
    }

    private void discardMinions(ServerLevel sl) {
        for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(64), this::isMinion)) {
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            m.discard();
        }
    }

    // ---------------- feixes giratórios (sincronizados) ----------------
    public boolean beamsActive() {
        return entityData.get(DATA_BEAM_START) >= 0 && entityData.get(DATA_BEAM_COUNT) > 0;
    }

    /** Liga {@code count} feixes horizontais girando {@code speed} graus/tick, a partir do ângulo {@code startDeg}. */
    public void startBeams(int count, float speed, float length, float startDeg) {
        entityData.set(DATA_BEAM_START, (int) level().getGameTime());
        entityData.set(DATA_BEAM_COUNT, count);
        entityData.set(DATA_BEAM_SPEED, speed);
        entityData.set(DATA_BEAM_LEN, length);
        entityData.set(DATA_BEAM_ANGLE, startDeg);
        beamHits.clear();
    }

    public void stopBeams() {
        entityData.set(DATA_BEAM_START, -1);
        entityData.set(DATA_BEAM_COUNT, 0);
    }

    public int beamCount() {
        return entityData.get(DATA_BEAM_COUNT);
    }

    public Vec3 beamOrigin() {
        return position().add(0, 1.55, 0);
    }

    /** Ângulo (radianos, no sistema x=cos, z=sin) do feixe {@code i}; igual no servidor e no cliente. */
    public double beamAngle(int i, float partial) {
        double t = (level().getGameTime() - entityData.get(DATA_BEAM_START)) + partial;
        int n = Math.max(1, beamCount());
        return Math.toRadians(entityData.get(DATA_BEAM_ANGLE) + t * entityData.get(DATA_BEAM_SPEED) + i * 360.0 / n);
    }

    public double beamReach(int i, float partial) {
        Vec3 o = beamOrigin();
        double a = beamAngle(i, partial);
        float len = entityData.get(DATA_BEAM_LEN);
        Vec3 end = o.add(Math.cos(a) * len, 0, Math.sin(a) * len);
        HitResult hit = level().clip(new ClipContext(o, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? len : hit.getLocation().distanceTo(o);
    }

    /** Servidor: dano em quem cruza os feixes (cada alvo no máx. a cada 10 ticks). */
    public void tickBeamDamage(float mult, @Nullable Consumer<LivingEntity> onHit) {
        if (!beamsActive()) return;
        Vec3 o = beamOrigin();
        float len = entityData.get(DATA_BEAM_LEN);
        List<LivingEntity> cand = foesNear(o, len + 1.5);
        if (cand.isEmpty()) return;
        long now = level().getGameTime();
        for (int i = 0; i < beamCount(); i++) {
            double a = beamAngle(i, 0F);
            double cx = Math.cos(a), cz = Math.sin(a);
            double reach = beamReach(i, 0F);
            for (LivingEntity e : cand) {
                if (beamHits.getOrDefault(e.getUUID(), 0L) > now) continue;
                double dx = e.getX() - o.x, dz = e.getZ() - o.z;
                double along = dx * cx + dz * cz;
                if (along < 0.8 || along > reach + 0.3) continue;
                double perp = Math.abs(dx * cz - dz * cx);
                if (perp > 0.6 + e.getBbWidth() / 2) continue;
                if (o.y < e.getY() - 0.4 || o.y > e.getY() + e.getBbHeight() + 0.3) continue;
                if (hitFoe(e, mult) && onHit != null) onHit.accept(e);
                beamHits.put(e.getUUID(), now + 10);
            }
        }
    }

    // =====================================================================================
    //  Cliente: partículas ambientes e feixes
    // =====================================================================================

    private void clientFx() {
        Element e = element();
        boolean reduced = reducedParticles();
        int every = reduced ? 6 : 2;
        if (tickCount % every == 0) {
            // Núcleo do peito
            double yaw = Math.toRadians(yBodyRot);
            double fx = -Math.sin(yaw) * 0.32, fz = Math.cos(yaw) * 0.32;
            level().addParticle(ElementFx.dust(e, 0.9F), getX() + fx, getY() + 1.95, getZ() + fz, 0, 0.01, 0);
            // Aura nos pés
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ElementFx.dust(TrialElement.accent(e), 1.0F), getX() + Math.cos(a) * 0.7, getY() + 0.05,
                    getZ() + Math.sin(a) * 0.7, 0, 0.02, 0);
        }
        if (!reduced && (tickCount % 5 == 0 || (isChanneling() && tickCount % 2 == 0))) {
            ParticleOptions sig = ElementFx.signature(e);
            level().addParticle(sig, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.4 + random.nextDouble() * 2.2,
                    getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
        }
        if (phase() >= 3 && !reduced && tickCount % 3 == 0) {
            double a = tickCount * 0.25;
            level().addParticle(ElementFx.dust(e, 1.3F), getX() + Math.cos(a) * 1.1, getY() + 1.0 + Math.sin(a * 0.5) * 0.6,
                    getZ() + Math.sin(a) * 1.1, 0, 0, 0);
        }
        if (beamsActive()) drawBeamsClient(e, reduced);
    }

    private void drawBeamsClient(Element e, boolean reduced) {
        Vec3 o = beamOrigin();
        double step = reduced ? 1.4 : 0.7;
        ParticleOptions core = ElementFx.dust(e, 1.6F);
        ParticleOptions accent = ElementFx.signature(e);
        for (int i = 0; i < beamCount(); i++) {
            double a = beamAngle(i, 0F);
            double cx = Math.cos(a), cz = Math.sin(a);
            double reach = beamReach(i, 0F);
            for (double d = 0.8; d < reach; d += step) {
                double j = (random.nextDouble() - 0.5) * 0.1;
                level().addParticle(core, o.x + cx * d + j, o.y + j, o.z + cz * d - j, 0, 0, 0);
            }
            level().addParticle(ParticleTypes.END_ROD, o.x + cx * reach, o.y, o.z + cz * reach,
                    (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            double r = 1 + random.nextDouble() * Math.max(0.5, reach - 1);
            level().addParticle(accent, o.x + cx * r, o.y, o.z + cz * r, 0, 0.02, 0);
        }
    }

    private static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // =====================================================================================
    //  Morte e recompensas
    // =====================================================================================

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel sl) || !this.dead || deathHandled) return;
        deathHandled = true;
        if (current != null) cancelMove();
        // Um golpe longo (ex.: "channel", 4,5 s) cobriria a animação de morte: interrompe
        if (lastAction != null) stopTriggeredAnim("action", lastAction);
        stopBeams();
        Telegraph.cancelAll(this);
        for (AvatarKit k : kits()) k.reset();
        epoch++;
        if (bar != null) bar.removeAllPlayers();
        discardMinions(sl);
        BossRewards.grant(this, 80 + 60 * rank(), 48);
        Announce.send(this, Component.translatable("announce.isekai_mahou.trial_avatar.defeated", getName()), rgb(), Announce.STYLE_BOSS);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // Essências do elemento (a loot table só tem o comum): base = 2+rank; fusão = um pouco de cada pai
        Element e = element();
        if (e.isFusion()) {
            dropEssence(e.parentA, 1 + rank() / 2);
            dropEssence(e.parentB, 1 + rank() / 2);
        } else {
            dropEssence(e, 2 + rank());
        }
    }

    private void dropEssence(Element e, int count) {
        var item = CoreRegistries.ESSENCES.get(e);
        if (item != null) spawnAtLocation(new ItemStack(item.get(), count));
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            Element e = element();
            int t = deathTime;
            if (t % 2 == 0) {
                double a = t * 0.5;
                double r = 1.6 - t * 0.018;
                for (int i = 0; i < 3; i++) {
                    double ang = a + i * Math.PI * 2 / 3;
                    sl.sendParticles(ElementFx.dust(e, 1.4F), getX() + Math.cos(ang) * r, getY() + 0.4 + t * 0.03,
                            getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                }
            }
            if (t % 10 == 0) {
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.4F,
                        0.6F + t * 0.01F);
            }
            if (t == 56) {
                // O núcleo se parte
                sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2.0, getZ(), 1, 0, 0, 0, 0);
                ElementFx.burst(sl, position().add(0, 2.0, 0), e, 90, 1.6);
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.0, getZ(), 40, 0.4, 0.4, 0.4, 0.25);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
                BossKit.shake(sl, position(), 24, 1.2F, 16);
            }
            if (t >= DEATH_TICKS && !isRemoved()) {
                level().broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // Removido de vez (morte, ou a Provação descartou o chefe ao perder/reiniciar): os lacaios vão junto.
        // Descarregar o chunk não conta (shouldDestroy() = false): eles continuam lá quando o chunk volta.
        if (reason.shouldDestroy() && level() instanceof ServerLevel sl) discardMinions(sl);
        super.remove(reason);
        if (bar != null) bar.removeAllPlayers();
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<TrialAvatar> base = new AnimationController<>(this, "base", 6, this::baseState);
        AnimationController<TrialAvatar> action = new AnimationController<>(this, "action", 3, s -> PlayState.STOP);
        for (String a : ACTIONS) action.triggerableAnim(a, RawAnimation.begin().thenPlay("animation.trial_avatar." + a));
        controllers.add(base, action);
    }

    private PlayState baseState(AnimationState<TrialAvatar> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        double dx = getX() - xo, dz = getZ() - zo;
        if (dx * dx + dz * dz > 2.5E-4) return state.setAndContinue(WALK);
        return state.setAndContinue(isAggressive() || phase() > 1 ? FLOAT : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
