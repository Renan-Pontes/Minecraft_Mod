package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_e.BeRegistries;
import com.renanpontes.isekaimahou.feature.bosses_e.DragonLegends;
import com.renanpontes.isekaimahou.feature.bosses_e.world.ArenaTerraform;
import com.renanpontes.isekaimahou.feature.bosses_e.world.ArenaWorks;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * DRAGÃO PRIMORDIAL — o chefe final (v0.9, refeito na v1.0), que surge da Fenda quando o Arauto do Crepúsculo cai. Controla todos
 * os elementos em 5 FASES (a cada 20% da vida): 1 Fogo · 2 Água/Gelo · 3 Terra · 4 Vento/Raio · 5 Luz+Sombra
 * (domínio "Fim dos Elementos" e a Aniquilação Primordial). A cada fase: transição invulnerável com rugido e
 * cutscene, escamas mudando de cor, e a ARENA se transforma (blocos temporários que voltam sozinhos).
 * <p>Voa (paira e circula), pousa para lutar no chão (mordida, garra, cauda, asas) e decola de novo; mergulha numa
 * linha marcada e fica EXAUSTO no chão (janela de dano). Todo golpe grande é telegrafado com dono (entra na Pilha);
 * a ultimate só é evitada contrariando-a (Suprema), com um Domínio Simples ou rachando a carga com dano.
 * <p>Vida: o vanilla limita a vida máxima em 1024, então o dragão usa ~1000 de vida "real" e divide o dano para
 * valer {@link #EFFECTIVE_HEALTH} (+70% por jogador extra: feito para 6 jogadores de nível 150 ≈ 36.000).
 * <p><b>v1.0 — o chefe final de verdade:</b> SEM morte instantânea (nenhum golpe tira mais que 35% da vida de um
 * jogador; a Aniquilação deixa em 10%), mas TRAVA, DRENA e humilha ({@link DragonCurses}): Olhar da Górgona (quem olha
 * vira ESTÁTUA), Correntes do Abismo ({@link PrimordialChain}), Sifão Primordial (vida, recurso e Essência), Inversão do
 * Destino (troca de lugar), Selo do Esquecimento (apaga o loadout), Maldição do Esquecimento (magias somem), Marca do
 * Sacrifício (dividam o golpe!) e Ruína. As 3 SUPREMAS (Crepúsculo de Pedra, Maré do Esquecimento, Aniquilação) só são
 * impedidas por CONTRAMÁGICA na Pilha (poder 2) — o dragão espera a recarga de contramágica do chefe acabar antes de
 * usá-las, para que sempre exista a chance. Aliado agachado ao lado por 2 s quebra a estátua/maldição.
 */
public class PrimordialDragon extends Monster implements GeoEntity {
    public static final String ID = "primordial_dragon";
    /** Tag posta pelo gancho do Arauto: nasce rasgando a Fenda (cutscene de surgimento). */
    public static final String RIFT_TAG = "isekai_mahou.rift_birth";
    /** Vida "real" (o MobScaling da v1.0 dobra: ~1000, dentro do teto de 1024 do vanilla). */
    public static final float BASE_HEALTH = 500F;
    /** Vida efetiva com 1 jogador; cada jogador extra soma {@link #HEALTH_PER_PLAYER}. 6 jogadores ≈ 36.000. */
    public static final float EFFECTIVE_HEALTH = 8000F;
    public static final float HEALTH_PER_PLAYER = 0.7F;
    /** XP mágico de cada participante (o 150→200 custa ~377 mil). */
    public static final int REWARD_XP = 20000;
    public static final int TWILIGHT_RGB = 0xC06AA0;
    private static final float[] THRESHOLDS = {0.8F, 0.6F, 0.4F, 0.2F};
    private static final double BAR_RANGE = 112;
    /** Raio de luta na arena grande da Fenda (v1.0); a BossArena sela {@code arenaRadius + 4} (≤ 48). */
    public static final double DEFAULT_ARENA = 40;
    private static final double MAX_ARENA = 44;
    private static final double INTRO_RANGE = 36;
    private static final int INTRO_TICKS = 90;
    private static final int EMERGE_TICKS = 150;
    private static final int TRANSITION_TICKS = 110;
    private static final int DEATH_TICKS = 200;
    private static final float MAX_HIT = 140F;
    /** Recarga compartilhada entre as 3 Supremas (uma de cada vez). */
    private static final int ULT_SHARED_CD = 700;
    private static final List<String> ULTIMATES = List.of("stone_twilight", "oblivion_tide", "annihilation");
    private static final float EXHAUSTED_BONUS = 1.3F;
    private static final String HINT = "message.isekai_mahou.primordial_dragon.hint.";
    public static final String TITLE_KEY = "title.isekai_mahou.dragon_slayer";
    private static final String ANNOUNCE = "announce.isekai_mahou.primordial_dragon.";
    private static final String TITLE = "boss.isekai_mahou.primordial_dragon.";
    private static final String ATTACK = "attack.isekai_mahou.primordial_dragon.";

    static final int ST_DORMANT = 0;
    static final int ST_EMERGING = 1;
    static final int ST_INTRO = 2;
    static final int ST_FIGHT = 3;
    static final int ST_TRANSITION = 4;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GROUNDED = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_EXHAUSTED = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.BOOLEAN);
    /** Domínio próprio (reserva sem o módulo domain): ticks restantes, centro e raio — o cliente desenha a esfera. */
    private static final EntityDataAccessor<Integer> DATA_DOMAIN = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_DOMAIN_CENTER = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> DATA_DOMAIN_RADIUS = SynchedEntityData.defineId(PrimordialDragon.class, EntityDataSerializers.FLOAT);

    /** Dragões lutando agora (resgate do vazio). Limpo ao parar o servidor. */
    private static final Set<PrimordialDragon> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    final DragonHazards hazards = new DragonHazards(this);

    private ServerBossEvent bar;
    private List<DragonAttack> attacks;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Set<UUID> introSeen = new HashSet<>();
    @Nullable private DragonAttack current;
    private int attackTick;
    private int globalCooldown = 30;
    private int stateTicks;
    private int modeTimer = 400;
    private int noTargetTicks;
    private int unseenTicks;
    private int unreachTicks;
    private long ccImmuneUntil;
    private boolean deathHandled;
    private boolean floorBypass;
    private boolean loading;
    private boolean themeWave;
    private double themeRadius;
    @Nullable private Vec3 home;
    private double arenaRadius = DEFAULT_ARENA;
    private float lockedYaw;
    boolean manualVelocity;
    @Nullable private String lastActionAnim;
    private int shadeCount;
    private String lastBarName = "";
    // Estado dos golpes
    private final Set<UUID> hitOnce = new HashSet<>();
    private Vec3 aimA = Vec3.ZERO;
    private Vec3 aimB = Vec3.ZERO;
    private Vec3 aimDir = new Vec3(0, 0, 1);
    private float aimYaw;
    private boolean go;
    private int breathUntil;
    // Domínio e ultimate
    private boolean domainGlyph;
    @Nullable private Vec3 glyphDomainCenter;
    private long domainOpenedAt;
    private boolean ultFired;
    /** Última vez que uma conjuração deste dragão foi contrariada (a Pilha só deixa 1 a cada 12 s). */
    private long lastCounteredAt = -100000L;
    /** Todos os jogadores que estiveram na luta (contador boss.primordial_dragon para todos, mesmo os que caíram). */
    private final Set<UUID> participants = new HashSet<>();
    private int siphonUntil = -1;
    private float siphonDamage;
    private int ultTele = -1;
    private int heardHints;
    // Morte
    private boolean crashed;
    /** Só no cliente: [fase anterior, tick da troca, fase vista] para a transição das escamas no renderer. */
    public final int[] clientFade = {0, -1000, 0};
    /** Onde o corpo despenca: o chão embaixo, ou o centro da arena se ele morreu sobre o vazio. */
    @Nullable private Vec3 deathSpot;

    public PrimordialDragon(EntityType<? extends PrimordialDragon> type, Level level) {
        super(type, level);
        this.xpReward = 2500;
        this.moveControl = new DragonFlight(this);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                // A armadura age sobre o dano JÁ dividido (vida real ~1000 → efetiva 8000+): 7/4 aqui equivale a ~12 de
                // armadura sobre o dano efetivo. Mais que isso deixaria a luta muito mais longa do que a vida prometida.
                .add(Attributes.ARMOR, 7.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.75)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_STATE, ST_DORMANT);
        builder.define(DATA_GROUNDED, true);
        builder.define(DATA_EXHAUSTED, 0);
        builder.define(DATA_CHARGING, false);
        builder.define(DATA_DOMAIN, 0);
        builder.define(DATA_DOMAIN_CENTER, BlockPos.ZERO);
        builder.define(DATA_DOMAIN_RADIUS, 20F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public DragonPhase phase() {
        return DragonPhase.of(entityData.get(DATA_PHASE));
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public boolean isGrounded() {
        return entityData.get(DATA_GROUNDED);
    }

    public boolean isExhausted() {
        return entityData.get(DATA_EXHAUSTED) > 0;
    }

    public boolean isCharging() {
        return entityData.get(DATA_CHARGING);
    }

    public boolean emerging() {
        return state() == ST_EMERGING;
    }

    public boolean ownDomainActive() {
        return entityData.get(DATA_DOMAIN) > 0;
    }

    public Vec3 ownDomainCenter() {
        return Vec3.atBottomCenterOf(entityData.get(DATA_DOMAIN_CENTER));
    }

    public float ownDomainRadius() {
        return entityData.get(DATA_DOMAIN_RADIUS);
    }

    /** Fase 5 com o domínio de pé (do glifo ou o próprio)? */
    public boolean domainActive() {
        return ownDomainActive() || (domainGlyph && glyphDomainCenter != null);
    }

    public Vec3 homeCenter() {
        return home != null ? home : position();
    }

    public boolean fighting() {
        return state() == ST_FIGHT || state() == ST_TRANSITION;
    }

    /** Vida efetiva máxima ({@link #EFFECTIVE_HEALTH}, +70% por jogador extra). */
    public float effectiveMaxHealth() {
        return EFFECTIVE_HEALTH * (1F + HEALTH_PER_PLAYER * (BossKit.partySize(this) - 1));
    }

    /**
     * Cura uma fração da vida máxima (drenos, estátuas). Nunca passa do teto da fase atual (o limiar da fase anterior):
     * a cura atrasa, mas não desfaz uma fase.
     */
    public void healEffectiveFraction(float frac) {
        if (!fighting() || frac <= 0 || isDeadOrDying()) return;
        int p = entityData.get(DATA_PHASE);
        float cap = p <= 1 ? getMaxHealth() : THRESHOLDS[p - 2] * getMaxHealth();
        float room = cap - getHealth();
        if (room <= 0) return;
        heal(Math.min(room, getMaxHealth() * frac));
        if (level() instanceof ServerLevel sl && tickCount % 4 == 0) {
            sl.sendParticles(DragonFx.dust(0xC0182E, 1.6F), getX(), getY() + 3, getZ(), 6, 1.5, 1, 1.5, 0);
        }
    }

    /** O núcleo contrariou uma conjuração deste dragão (BeEvents). */
    public void onCountered(long gameTime) {
        lastCounteredAt = gameTime;
    }

    /** A Pilha já deixa contrariar este dragão de novo? As Supremas esperam por isso (sempre há uma chance). */
    private boolean counterReady() {
        return level().getGameTime() - lastCounteredAt >= SpellStack.BOSS_COUNTER_COOLDOWN + 20;
    }

    private float hpDivisor() {
        return effectiveMaxHealth() / Math.max(1F, getMaxHealth());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DragonPhase", entityData.get(DATA_PHASE));
        int st = state();
        // Cutscenes não são retomadas: quem estava surgindo/apresentando volta direto para a luta.
        tag.putInt("DragonState", st == ST_DORMANT ? ST_DORMANT : ST_FIGHT);
        tag.putBoolean("DragonGrounded", isGrounded());
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
        tag.putDouble("ArenaRadius", arenaRadius);
        ListTag seen = new ListTag();
        for (UUID id : introSeen) seen.add(NbtUtils.createUUID(id));
        tag.put("IntroSeen", seen);
        ListTag part = new ListTag();
        for (UUID id : participants) part.add(NbtUtils.createUUID(id));
        tag.put("Participants", part);
        tag.putInt("Hints", heardHints);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        loading = true;
        super.readAdditionalSaveData(tag);
        loading = false;
        if (tag.contains("DragonPhase")) entityData.set(DATA_PHASE, Mth.clamp(tag.getInt("DragonPhase"), 1, DragonPhase.COUNT));
        if (tag.contains("DragonState")) entityData.set(DATA_STATE, tag.getInt("DragonState") == ST_DORMANT ? ST_DORMANT : ST_FIGHT);
        entityData.set(DATA_GROUNDED, tag.getBoolean("DragonGrounded"));
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        if (tag.contains("ArenaRadius")) arenaRadius = Mth.clamp(tag.getDouble("ArenaRadius"), 12, MAX_ARENA);
        introSeen.clear();
        for (Tag t : tag.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        participants.clear();
        for (Tag t : tag.getList("Participants", Tag.TAG_INT_ARRAY)) participants.add(NbtUtils.loadUUID(t));
        heardHints = tag.getInt("Hints");
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
    }

    /** Asas de ~23 blocos: não sumir na borda da tela. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(14.0, 4.0, 14.0);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof PrimordialShade s && s.ownedBy(this)) return true;
        if (other instanceof PrimordialChain c && c.ownedBy(this)) return true;
        return super.isAlliedTo(other);
    }

    /** Controle (atordoar, congelar, prender...) dura no máx. 1,5 s e dá 6 s de imunidade. */
    @Override
    public boolean addEffect(MobEffectInstance inst, @Nullable Entity source) {
        if (isCrowdControl(inst.getEffect())) {
            long now = level().getGameTime();
            if (now < ccImmuneUntil) return false;
            ccImmuneUntil = now + 120;
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
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            floorBypass = true;
            try {
                return super.hurt(source, amount);
            } finally {
                floorBypass = false;
            }
        }
        if (source.is(DamageTypes.IN_WALL)) {
            escapeWall();
            return false;
        }
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && isAlliedTo(attacker)) return false;
        if (state() == ST_DORMANT && attacker instanceof Player p && !p.isSpectator() && level() instanceof ServerLevel sl) {
            startIntro(sl);
        }
        if (state() != ST_FIGHT) {
            shieldFx();
            return false;
        }
        amount = Math.min(amount, MAX_HIT);
        if (source.is(CoreRegistries.SPELL_DAMAGE)) {
            Element el = ElementReactions.lastElement(this, level().getGameTime(), 0);
            if (el != null) {
                float m = phase().elementMultiplier(el);
                amount *= m;
                if (m != 1F && level() instanceof ServerLevel sl) {
                    sl.sendParticles(m < 1 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 10, 1, 1, 1, 0.2);
                }
            }
        } else {
            amount *= 0.9F;
        }
        if (shadeCount > 0) {
            amount *= 0.5F;
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 3, getZ(), 6, 1, 1, 1, 0.02);
        }
        if (isExhausted()) amount *= EXHAUSTED_BONUS;
        if (amount <= 0) return false;
        // Sifão Primordial: dano suficiente durante o canal ROMPE o sifão (ele fica atordoado de dor).
        if (siphonUntil > attackTick && current != null && "life_siphon".equals(current.id)) {
            siphonDamage += amount;
            if (siphonDamage >= effectiveMaxHealth() * 0.03F && level() instanceof ServerLevel sl) breakSiphon(sl);
        }
        return super.hurt(source, amount / hpDivisor());
    }

    /** Fases não são puladas: a vida para no limiar até a transição começar (só /kill passa). */
    @Override
    public void setHealth(float health) {
        if (!loading && !floorBypass && !level().isClientSide && entityData != null && health < getHealth()) {
            int p = entityData.get(DATA_PHASE);
            if (p < DragonPhase.COUNT) {
                float floor = THRESHOLDS[p - 1] * getMaxHealth();
                if (health < floor) health = Math.min(getHealth(), floor);
            }
        }
        super.setHealth(health);
    }

    private void shieldFx() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 2.5, getZ(), 14, 1.5, 1.2, 1.5, 0.2);
        playSound(SoundEvents.SHIELD_BLOCK, 0.9F, 0.5F);
    }

    private void escapeWall() {
        for (int i = 0; i < 12; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 10;
            double z = getZ() + (random.nextDouble() - 0.5) * 10;
            if (randomTeleport(x, getY() + 2 + random.nextInt(4), z, false)) return;
        }
        setPos(getX(), getY() + 3, getZ());
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
        if (home == null) initHome(sl);
        if (isRemoved()) return;
        tickBar(sl);
        hazards.tick(sl);
        if (themeWave) tickThemeWave(sl);
        if (tickCount % 10 == 0) refreshShades(sl);
        if (tickCount % 400 == 0 && fighting()) ArenaTerraform.get(sl).extendOwner(getUUID(), sl.getGameTime() + ArenaWorks.PHASE_TICKS);
        switch (state()) {
            case ST_DORMANT -> tickDormant(sl);
            case ST_EMERGING -> tickEmerging(sl);
            case ST_INTRO -> tickIntro(sl);
            case ST_TRANSITION -> tickTransition(sl);
            default -> tickFight(sl);
        }
    }

    private void initHome(ServerLevel sl) {
        Vec3 arena = BossKit.arenaCenter(this);
        double r = BossKit.arenaRadius(this);
        if (arena != null) {
            home = arena;
            if (r > 0) arenaRadius = Mth.clamp(r, 14, MAX_ARENA);
        } else {
            Vec3 g = groundAt(getX(), getY(), getZ(), 2, 40);
            home = g != null ? g : position();
        }
        // v1.0: a arena da Fenda é grande (raio ~42). O gancho do Arauto ainda grava 22: aqui vira a arena inteira.
        if (sl.dimension().location().equals(IsekaiMahou.id("twilight_rift")) || arena == null) {
            arenaRadius = Math.max(arenaRadius, DEFAULT_ARENA);
        }
        // A arena PROTEGIDA da BossArena (núcleo) segue o mesmo centro, com folga para a borda (máx. 48).
        BossKit.setArena(this, home, Math.min(48, arenaRadius + 4));
        if (getPersistentData().getBoolean(RIFT_TAG)) {
            getPersistentData().remove(RIFT_TAG);
            // Só um Dragão por arena: se já existe outro vivo por perto, este some.
            if (!sl.getEntitiesOfClass(PrimordialDragon.class, getBoundingBox().inflate(160), d -> d != this && d.isAlive()).isEmpty()) {
                discard();
                return;
            }
            startEmerging(sl);
        }
    }

    private void tickBar(ServerLevel sl) {
        if (bar == null) {
            bar = new ServerBossEvent(getDisplayName(), phase().bar, BossEvent.BossBarOverlay.NOTCHED_20);
            bar.setPlayBossMusic(true);
            bar.setDarkenScreen(false);
        }
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (tickCount % 10 != 0) return;
        DragonPhase ph = phase();
        BossEvent.BossBarColor color = ph == DragonPhase.TWILIGHT && (tickCount / 40) % 2 == 0 ? BossEvent.BossBarColor.WHITE : ph.bar;
        if (bar.getColor() != color) bar.setColor(color);
        int eff = Math.round(getHealth() * hpDivisor());
        int max = Math.round(effectiveMaxHealth());
        String key = ph.id + eff + "/" + max;
        if (!key.equals(lastBarName)) {
            lastBarName = key;
            bar.setName(Component.translatable("boss.isekai_mahou.primordial_dragon.bar", getDisplayName(), ph.displayName(), eff, max));
        }
        boolean show = state() != ST_DORMANT && !emergingHidden();
        for (ServerPlayer p : sl.players()) {
            boolean near = show && p.isAlive() && p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
            if (near) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
    }

    private boolean emergingHidden() {
        return state() == ST_EMERGING && stateTicks < 60;
    }

    // ---------------- Dormente / apresentação ----------------
    private void tickDormant(ServerLevel sl) {
        stopMoving();
        if (tickCount % 10 != 0) return;
        for (ServerPlayer p : sl.players()) {
            if (!p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(this) < INTRO_RANGE * INTRO_RANGE && hasLineOfSight(p)) {
                startIntro(sl);
                return;
            }
        }
    }

    private void startIntro(ServerLevel sl) {
        if (state() != ST_DORMANT) return;
        setState(ST_INTRO);
        BossKit.scaleToParty(this, 80);
        setHealth(getMaxHealth());
        BossKit.sealArena(this);
        triggerAnim("action", "intro");
        Cutscenes.playNear(this, BAR_RANGE, "boss_intro", getDisplayName(), title("subtitle"), TWILIGHT_RGB, 90, null);
        for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE)) introSeen.add(p.getUUID());
        announce("intro", Announce.STYLE_BOSS);
        BossKit.shake(sl, position(), 48, 1.4F, 40);
        DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_GROWL, 4F, 0.6F);
    }

    private void tickIntro(ServerLevel sl) {
        stopMoving();
        Player look = sl.getNearestPlayer(this, 48);
        if (look != null) getLookControl().setLookAt(look, 10F, 10F);
        if (stateTicks == 35) {
            BossKit.roar(this, SoundEvents.ENDER_DRAGON_GROWL, 0.55F);
            beginTheme(sl);
        }
        if (stateTicks >= INTRO_TICKS) beginFight(sl);
    }

    /** Nascimento pela Fenda: o céu se rasga, rachaduras correm pela arena e o Dragão desce rugindo. */
    private void startEmerging(ServerLevel sl) {
        setState(ST_EMERGING);
        Vec3 h = homeCenter();
        moveTo(h.x, h.y + 28, h.z, getYRot(), 0F);
        entityData.set(DATA_GROUNDED, false);
        setInvisible(true);
        Cutscenes.playNear(this, BAR_RANGE, "portal", title("rift_title"), title("rift_subtitle"), TWILIGHT_RGB, 80, null);
        announce("rift", Announce.STYLE_BOSS);
        DragonFx.sound(sl, h, SoundEvents.END_PORTAL_SPAWN, 4F, 0.5F);
    }

    private void tickEmerging(ServerLevel sl) {
        int t = stateTicks;
        Vec3 h = homeCenter();
        Vec3 sky = h.add(0, 28, 0);
        manualVelocity = true;
        if (t < 60) {
            setDeltaMovement(Vec3.ZERO);
            if (t % 2 == 0) drawTear(sl, sky, Math.min(1.0, t / 50.0));
            if (t % 12 == 0) {
                // Rachaduras luminosas correndo pelo chão da arena (só visuais).
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 g = groundAt(h.x, h.y, h.z, 3, 6);
                Vec3 base = g != null ? g : h;
                Telegraph.show(sl, Telegraph.Shape.line(base, base.add(dir.scale(arenaRadius)), 1.0F).warmup(30).hold(60)
                        .color(TWILIGHT_RGB).noFlash().owner(this));
                DragonFx.sound(sl, base, SoundEvents.DEEPSLATE_BREAK, 2F, 0.5F);
            }
            if (t == 20) DragonFx.sound(sl, sky, SoundEvents.WITHER_SPAWN, 3F, 0.5F);
            if (t == 40) DragonFx.sound(sl, sky, SoundEvents.LIGHTNING_BOLT_THUNDER, 4F, 0.6F);
            if (t % 10 == 0) BossKit.shake(sl, h, 64, 0.6F + t / 60F, 14);
        } else if (t == 60) {
            setInvisible(false);
            triggerAnim("action", "intro");
            BossKit.scaleToParty(this, 80);
            setHealth(getMaxHealth());
            BossKit.sealArena(this);
            Cutscenes.playNear(this, BAR_RANGE, "boss_intro", getDisplayName(), title("subtitle"), TWILIGHT_RGB, 90, null);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE)) introSeen.add(p.getUUID());
            sl.sendParticles(ParticleTypes.FLASH, sky.x, sky.y, sky.z, 3, 1, 1, 1, 0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, sky.x, sky.y, sky.z, 200, 4, 2, 4, 0.4);
            announce("intro", Announce.STYLE_BOSS);
        }
        if (t >= 60) {
            double f = Math.min(1.0, (t - 60) / 50.0);
            Vec3 want = sky.lerp(h.add(0, 10, 0), f * f * (3 - 2 * f));
            setDeltaMovement(want.subtract(position()));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(Vec3.ZERO);
            if (t % 3 == 0) drawTear(sl, sky, Math.max(0, 1.0 - (t - 60) / 80.0));
        }
        if (t == 95) {
            BossKit.roar(this, SoundEvents.ENDER_DRAGON_GROWL, 0.5F);
            BossKit.shake(sl, h, 64, 2.6F, 40);
            beginTheme(sl);
        }
        if (t >= EMERGE_TICKS) {
            manualVelocity = false;
            beginFight(sl);
        }
    }

    /** A Fenda no céu: um rasgo em zigue-zague que se abre ({@code open} 0..1). */
    private void drawTear(ServerLevel sl, Vec3 c, double open) {
        long seed = getUUID().getLeastSignificantBits();
        Vec3 prev = null;
        for (int i = -7; i <= 7; i++) {
            double jitter = ((seed >> ((i + 7) * 3)) & 7) - 3.5;
            Vec3 p = c.add(i * 2.0 * open, jitter * 0.6, jitter * 0.4);
            if (prev != null) {
                DragonFx.line(sl, DragonFx.dust(i % 2 == 0 ? DragonFx.LIGHT : DragonFx.SHADOW, 2.5F), prev, p, 0.7);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 4, 0.3, 0.8 * open, 0.3, 0.05);
            }
            prev = p;
        }
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 8 * open, 0.4, 1.5, 0.05);
    }

    private void beginFight(ServerLevel sl) {
        setState(ST_FIGHT);
        globalCooldown = 30;
        entityData.set(DATA_GROUNDED, false);
        modeTimer = airTime();
        ACTIVE.add(this);
        hint("start");
    }

    private void setState(int st) {
        entityData.set(DATA_STATE, st);
        stateTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) stateTicks++;
    }

    // ---------------- Terreno de cada fase ----------------
    private void beginTheme(ServerLevel sl) {
        themeWave = true;
        themeRadius = 0;
        DragonFx.sound(sl, homeCenter(), switch (phase()) {
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WATER -> SoundEvents.GLASS_BREAK;
            case EARTH -> SoundEvents.DEEPSLATE_BREAK;
            case STORM -> SoundEvents.TRIDENT_THUNDER.value();
            case TWILIGHT -> SoundEvents.BEACON_POWER_SELECT;
        }, 3F, 0.6F);
    }

    private void tickThemeWave(ServerLevel sl) {
        double next = themeRadius + 0.7;
        ArenaWorks.themeRing(sl, homeCenter(), themeRadius, next, phase(), getUUID());
        themeRadius = next;
        if (themeRadius > arenaRadius) themeWave = false;
    }

    // =====================================================================================
    //  Luta
    // =====================================================================================

    private void tickFight(ServerLevel sl) {
        ACTIVE.add(this);
        if (tickCount % 40 == 0) trackParticipants(sl);
        if (checkPhase(sl)) return;
        tickDomain(sl);
        if (tickCount % 10 == 0) groundHazards(sl);
        int ex = entityData.get(DATA_EXHAUSTED);
        if (ex > 0) {
            entityData.set(DATA_EXHAUSTED, ex - 1);
            tickExhausted(sl, ex - 1);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && !validTarget(target)) {
            setTarget(null);
            target = null;
        }
        if (target == null) {
            if (current != null) cancelAttack();
            idleTick(sl);
            return;
        }
        if (noTargetTicks >= 600) beginTheme(sl);
        noTargetTicks = 0;
        if (current != null) {
            if (current.stationary) stopMoving();
            else moveCombat(target);
            DragonAttack a = current;
            a.tick.tick(target, attackTick);
            if (current != a) return;
            attackTick++;
            if (attackTick >= a.duration) finishAttack();
            return;
        }
        moveCombat(target);
        getLookControl().setLookAt(target, 20F, 20F);
        antiCheese(sl, target);
        if (current != null) return;
        if (--modeTimer <= 0) {
            startAttack(attack(isGrounded() ? "takeoff" : "land"));
            return;
        }
        if (globalCooldown > 0) {
            globalCooldown--;
            return;
        }
        DragonAttack next = pick(target);
        if (next != null) startAttack(next);
        else globalCooldown = 5;
    }

    private int airTime() {
        return switch (phase()) {
            case STORM -> 560 + random.nextInt(120);
            case TWILIGHT -> 380 + random.nextInt(80);
            default -> 340 + random.nextInt(100);
        };
    }

    private int groundTime() {
        return phase() == DragonPhase.STORM ? 140 : 220 + random.nextInt(60);
    }

    private int pauseBetweenAttacks() {
        return switch (phase()) {
            case FIRE -> 24;
            case WATER, EARTH -> 20;
            case STORM -> 16;
            case TWILIGHT -> 14;
        };
    }

    private void moveCombat(LivingEntity target) {
        if (isGrounded()) {
            Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
            double d = to.length();
            Vec3 g = groundBelow();
            Vec3 want = d > 6.5 ? position().add(to.normalize().scale(Math.min(3, d - 6))) : position();
            hoverToward(new Vec3(want.x, g.y + 0.05, want.z), 0.9);
            return;
        }
        double radius = phase() == DragonPhase.STORM ? 16 : 13;
        double height = phase() == DragonPhase.STORM ? 15 : 9;
        orbitAngle += 0.016 * orbitDir;
        if (random.nextInt(500) == 0) orbitDir = -orbitDir;
        Vec3 c = domainActive() ? domainCenterNow() : target.position();
        double r = domainActive() ? Math.min(radius, domainRadiusNow() * 0.55) : radius;
        Vec3 g = groundAt(c.x, target.getY() + 2, c.z, 4, 24);
        double baseY = g != null ? g.y : target.getY();
        Vec3 want = new Vec3(c.x + Math.cos(orbitAngle) * r, baseY + height, c.z + Math.sin(orbitAngle) * r);
        hoverToward(want, 1.0);
    }

    private double orbitAngle;
    private int orbitDir = 1;

    private void idleTick(ServerLevel sl) {
        noTargetTicks++;
        Vec3 h = homeCenter();
        if (noTargetTicks > 40) {
            hoverToward(h.add(0, isGrounded() ? 0.05 : 9, 0), 0.7);
        }
        if (noTargetTicks > 200 && tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.005F);
        if (noTargetTicks == 600) {
            // Ninguém por perto há 30 s: a arena volta ao normal e o domínio cai.
            closeDomain(sl);
            ArenaTerraform.get(sl).releaseOwner(sl, getUUID(), h, 1.5);
        }
    }

    private boolean validTarget(LivingEntity t) {
        if (!t.isAlive() || t.isRemoved() || t.level() != level() || isAlliedTo(t)) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        double leash = arenaRadius + 32;
        return t.distanceToSqr(homeCenter()) < leash * leash;
    }

    /**
     * Anti-trapaça: escondido fora da visão por 3 s → orbes caçadores (e teleporte, se der); inalcançável no chão
     * (buraco, pilar) por 3 s → decola na hora (do ar ele alcança).
     */
    private void antiCheese(ServerLevel sl, LivingEntity target) {
        if (tickCount % 5 != 0) return;
        boolean hidden = !hasLineOfSight(target) && distanceToSqr(target) > 36;
        unseenTicks = hidden ? unseenTicks + 5 : 0;
        if (unseenTicks >= 60) {
            unseenTicks = 0;
            if (!isGrounded()) BossKit.blinkNear(this, target, 10);
            hazards.seeker(mouthPos(), target);
            hazards.seeker(mouthPos().add(0, 1, 0), target);
            hint("hidden");
        }
        if (isGrounded()) {
            unreachTicks = BossKit.isUnreachable(this, target) ? unreachTicks + 5 : 0;
            if (unreachTicks >= 60) {
                unreachTicks = 0;
                modeTimer = 0;
            }
        }
    }

    // ---------------- Golpes ----------------
    private DragonAttack pick(LivingEntity target) {
        if (attacks == null) attacks = createAttacks();
        long now = level().getGameTime();
        double dist = Math.sqrt(distanceToSqr(target.getX(), target.getY(), target.getZ()));
        int ph = phase().number();
        boolean ground = isGrounded();
        List<DragonAttack> ok = new ArrayList<>();
        int total = 0;
        for (DragonAttack a : attacks) {
            if (a.weight <= 0 || !a.inPhase(ph) || dist < a.minRange || dist > a.maxRange) continue;
            if (a.where == DragonAttack.Where.AIR && ground || a.where == DragonAttack.Where.GROUND && !ground) continue;
            if (now < readyAt.getOrDefault(a.id, 0L) || !a.condition.test(target)) continue;
            int w = ph == 5 && a.phaseMask != (1 << 4) && a.inPhase(5) && !a.id.equals("breath") && !a.id.equals("dive") ? Math.max(1, a.weight / 2) : a.weight;
            ok.add(a);
            total += w;
        }
        if (ok.isEmpty()) return null;
        int r = random.nextInt(total);
        for (DragonAttack a : ok) {
            int w = ph == 5 && a.phaseMask != (1 << 4) && a.inPhase(5) && !a.id.equals("breath") && !a.id.equals("dive") ? Math.max(1, a.weight / 2) : a.weight;
            r -= w;
            if (r < 0) return a;
        }
        return ok.get(0);
    }

    private void startAttack(@Nullable DragonAttack a) {
        if (a == null) return;
        if (current != null) cancelAttack();
        current = a;
        attackTick = 0;
        hitOnce.clear();
        go = false;
        readyAt.put(a.id, level().getGameTime() + a.cooldown);
        if (a.anim != null) triggerAnim("action", a.anim);
    }

    @Nullable
    private DragonAttack attack(String id) {
        if (attacks == null) attacks = createAttacks();
        for (DragonAttack a : attacks) if (a.id.equals(id)) return a;
        return null;
    }

    private void finishAttack() {
        DragonAttack a = current;
        current = null;
        manualVelocity = false;
        if (a != null && a.onEnd != null) a.onEnd.run();
        globalCooldown = pauseBetweenAttacks();
    }

    private void endAttack() {
        if (current != null) finishAttack();
    }

    private void cancelAttack() {
        DragonAttack a = current;
        current = null;
        manualVelocity = false;
        if (a != null && a.onEnd != null) a.onEnd.run();
        globalCooldown = 20;
    }

    private List<DragonAttack> createAttacks() {
        List<DragonAttack> l = new ArrayList<>();
        DragonAttack.Where AIR = DragonAttack.Where.AIR;
        DragonAttack.Where GROUND = DragonAttack.Where.GROUND;
        // ---- movimento ----
        l.add(DragonAttack.of("land", 40, 0, this::landMove).anim("land").weight(0).stationary());
        l.add(DragonAttack.of("takeoff", 30, 0, this::takeoffMove).anim("takeoff").weight(0).stationary());
        // ---- todas as fases ----
        l.add(DragonAttack.of("bite", 24, 30, this::bite).anim("bite").where(GROUND).range(0, 10).weight(6).stationary());
        l.add(DragonAttack.of("claw", 26, 46, this::claw).anim("claw").where(GROUND).range(0, 8.5).weight(5).stationary());
        l.add(DragonAttack.of("tail_swipe", 32, 90, this::tailSwipe).anim("tail_swipe").where(GROUND).range(0, 13).weight(4).stationary());
        l.add(DragonAttack.of("wing_buffet", 30, 120, this::wingBuffet).anim("gust").where(GROUND).range(0, 7).weight(4).stationary());
        l.add(DragonAttack.of("breath", 64, 150, this::breath).anim("breath").range(5, 28).weight(5).stationary()
                .onEnd(() -> breathUntil = 0));
        l.add(DragonAttack.of("dive", 76, 240, this::dive).anim("dive").where(AIR).range(9, 42).weight(4).stationary());
        l.add(DragonAttack.of("gale", 40, 160, this::gale).anim("gust").where(AIR).range(0, 20).weight(3).stationary());
        // ---- 1 FOGO ----
        l.add(DragonAttack.of("meteor_rain", 70, 280, this::meteorRain).anim("meteor_call").phases(1, 5).weight(5).stationary());
        l.add(DragonAttack.of("lava_floor", 60, 380, this::lavaFloor).anim("roar").phases(1, 5).weight(4).stationary());
        // ---- 2 ÁGUA / GELO ----
        l.add(DragonAttack.of("tsunami", 90, 300, this::tsunami).anim("tidal_roar").phases(2, 5).weight(5).stationary());
        l.add(DragonAttack.of("ice_prison", 56, 260, this::icePrison).anim("roar").phases(2, 5).range(0, 36).weight(4).stationary());
        l.add(DragonAttack.of("glacial_spikes", 40, 140, this::glacialSpikes).anim("tidal_roar").phases(2).range(0, 24).weight(5).stationary());
        // ---- 3 TERRA ----
        l.add(DragonAttack.of("quake", 64, 220, this::quake).anim("quake_stomp").phases(3, 5).where(GROUND).weight(6).stationary());
        l.add(DragonAttack.of("stone_pillars", 50, 200, this::stonePillars).anim("roar").phases(3, 5).range(0, 34).weight(5).stationary());
        l.add(DragonAttack.of("crater_slam", 80, 320, this::craterSlam).anim("dive").phases(3).where(AIR).range(4, 40).weight(4).stationary());
        // ---- 4 VENTO / RAIO ----
        l.add(DragonAttack.of("tornados", 40, 320, this::tornados).anim("storm_call").phases(4, 5).where(AIR).weight(4).stationary()
                .when(t -> hazards.tornadoCount() < DragonHazards.MAX_TORNADOS));
        l.add(DragonAttack.of("lightning_storm", 80, 220, this::lightningStorm).anim("storm_call").phases(4, 5).where(AIR).weight(5)
                .stationary());
        l.add(DragonAttack.of("gale_lines", 56, 170, this::galeLines).anim("gust").phases(4).where(AIR).weight(4).stationary());
        // ---- 5 LUZ + SOMBRA ----
        l.add(DragonAttack.of("domain", 70, 200, this::domainCast).anim("domain").phases(5).weight(10).stationary()
                .when(t -> !domainActive()));
        l.add(DragonAttack.of("shadow_clones", 40, 520, this::shadowClones).anim("summon").phases(5).weight(4).stationary()
                .when(t -> shadeCount == 0));
        l.add(DragonAttack.of("eclipse_rain", 60, 200, this::eclipseRain).anim("roar").phases(5).weight(5).stationary());
        l.add(DragonAttack.of("annihilation", 170, 1000, this::annihilation).anim("ultimate").phases(5).weight(12).stationary()
                .when(t -> domainActive() && counterReady()).onEnd(() -> entityData.set(DATA_CHARGING, false)));
        // ---- v1.0: CONTROLE (nada mata de uma vez; tudo trava, drena e humilha) ----
        l.add(DragonAttack.of("abyss_chains", 44, 360, this::abyssChains).anim("chains").weight(4).stationary()
                .when(t -> !playerFoes(1).isEmpty()));
        l.add(DragonAttack.of("sacrifice_mark", 104, 420, this::sacrificeMark).anim("roar").phases(2, 3, 4, 5).weight(3).stationary()
                .when(t -> !playerFoes(1).isEmpty()));
        l.add(DragonAttack.of("gorgon_gaze", 80, 460, this::gorgonGaze).anim("gaze").phases(2, 3, 4, 5).weight(4).stationary());
        l.add(DragonAttack.of("fate_swap", 46, 300, this::fateSwap).anim("swap").phases(2, 4, 5).weight(3).stationary()
                .when(t -> !playerFoes(1).isEmpty()));
        l.add(DragonAttack.of("life_siphon", 130, 520, this::lifeSiphon).anim("siphon").phases(3, 4, 5).weight(4).stationary()
                .onEnd(() -> siphonUntil = -1));
        l.add(DragonAttack.of("oblivion_seal", 50, 380, this::oblivionSeal).anim("curse").phases(3, 4, 5).weight(4).stationary()
                .when(t -> !playerFoes(1).isEmpty()));
        l.add(DragonAttack.of("forget_curse", 44, 480, this::forgetCurse).anim("curse").phases(4, 5).weight(3).stationary()
                .when(t -> !playerFoes(1).isEmpty()));
        // ---- v1.0: SUPREMAS — só CONTRAMÁGICA (poder 2) impede ----
        l.add(DragonAttack.of("stone_twilight", 150, 1400, this::stoneTwilight).anim("ultimate").phases(3, 4).weight(6).stationary()
                .when(t -> counterReady()).onEnd(() -> entityData.set(DATA_CHARGING, false)));
        l.add(DragonAttack.of("oblivion_tide", 150, 1500, this::oblivionTide).anim("ultimate").phases(4, 5).weight(5).stationary()
                .when(t -> counterReady()).onEnd(() -> entityData.set(DATA_CHARGING, false)));
        return l;
    }

    // ---------------- Pouso / decolagem / exaustão ----------------

    /** Pouso: plana até um ponto marcado perto do alvo; quem está lá leva o impacto. */
    private void landMove(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Vec3 off = position().subtract(target.position()).multiply(1, 0, 1);
            off = off.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : off.normalize();
            Vec3 spot = target.position().add(off.scale(7));
            Vec3 g = groundAt(spot.x, target.getY() + 2, spot.z, 4, 8);
            if (g == null) g = groundAt(homeCenter().x, homeCenter().y + 2, homeCenter().z, 4, 10);
            aimA = g != null ? g : target.position();
            aimB = position();
            strike(Telegraph.Shape.circle(aimA, 5.5F).warmup(30).color(Telegraph.WARNING).castName(attackName("land")), foes -> {
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.6F)) knockFrom(e, aimA, 1.2, 0.5);
                }
                landingDust(sl, aimA, 6);
            });
        }
        manualVelocity = true;
        if (t <= 30) {
            double f = t / 30.0;
            Vec3 want = aimB.lerp(aimA.add(0, 0.05, 0), 1 - (1 - f) * (1 - f));
            move(MoverType.SELF, want.subtract(position()));
            setDeltaMovement(Vec3.ZERO);
            faceToward(aimA);
        }
        if (t == 30) {
            entityData.set(DATA_GROUNDED, true);
            modeTimer = groundTime();
            DragonFx.sound(sl, aimA, SoundEvents.ANVIL_LAND, 1.2F, 0.4F);
            BossKit.shake(sl, aimA, 30, 1.2F, 16);
        }
    }

    /** Decolagem: a rajada das asas empurra quem está colado (anel que dá para pular). */
    private void takeoffMove(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Vec3 g = groundBelow();
            strike(Telegraph.Shape.ring(g, 2.5F, 9F).warmup(12).vertical(0.5, 1.0).color(phase().rgb).noRunes(), foes -> {
                for (LivingEntity e : foes) {
                    e.hurt(damageSources().mobAttack(this), attackPower() * 0.3F);
                    knockFrom(e, g, 1.4, 0.45);
                }
                landingDust(sl, g, 8);
            });
        }
        if (t == 12) {
            entityData.set(DATA_GROUNDED, false);
            DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 3F, 0.6F);
        }
        if (t >= 12) hoverToward(position().add(0, 6, 0), 1.6);
        if (t == 29) modeTimer = airTime();
    }

    /** Fica exausto no chão: sem golpes, leva +30% de dano. */
    private void exhaust(int ticks) {
        if (!(level() instanceof ServerLevel sl)) return;
        entityData.set(DATA_GROUNDED, true);
        entityData.set(DATA_EXHAUSTED, ticks);
        manualVelocity = false;
        if (lastActionAnim != null) stopTriggeredAnim("action", lastActionAnim);
        DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_HURT, 2F, 0.5F);
        for (ServerPlayer p : BossKit.partyAround(this, 64)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.primordial_dragon.exhausted").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private void tickExhausted(ServerLevel sl, int left) {
        Vec3 g = groundBelow();
        double above = getY() - g.y;
        if (above > 0.6) {
            manualVelocity = true;
            move(MoverType.SELF, new Vec3(0, -Math.min(1.0, above), 0));
            setDeltaMovement(Vec3.ZERO);
        } else {
            manualVelocity = false;
            stopMoving();
        }
        if (left % 8 == 0) sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.5, getZ(), 6, 1.2, 0.3, 1.2, 0.05);
        if (left == 0) {
            modeTimer = 40;
            globalCooldown = 10;
        }
    }

    // ---------------- Comuns: chão ----------------
    private void bite(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            strike(Telegraph.Shape.cone(groundBelow(), getYRot(), 70F, 10F).warmup(11).color(phase().rgb).noRunes(), foes -> {
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower())) applyPhaseStatus(e, 1);
                }
            });
        }
        holdFacing();
        if (t == 11) {
            Vec3 m = mouthPos();
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, m.x, m.y - 1.5, m.z, 3, 1, 0.3, 1, 0);
            DragonFx.sound(sl, m, SoundEvents.EVOKER_FANGS_ATTACK, 2F, 0.5F);
            DragonFx.sound(sl, m, SoundEvents.RAVAGER_ATTACK, 2F, 0.6F);
        }
    }

    private void claw(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            strike(Telegraph.Shape.cone(groundBelow(), getYRot(), 150F, 8.5F).warmup(13).color(phase().rgb).noRunes(), foes -> {
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.85F)) {
                        Statuses.apply(e, Statuses.Kind.BLEEDING, 3, 120, this, 1F);
                    }
                }
            });
        }
        holdFacing();
        if (t == 13) {
            Vec3 c = position().add(Vec3.directionFromRotation(0, getYRot()).scale(5)).add(0, 1, 0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 5, 2, 0.4, 2, 0);
            DragonFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 2F, 0.4F);
        }
    }

    /** Cauda: varre o cone ATRÁS do dragão (quem flanqueia por trás é punido). */
    private void tailSwipe(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockedYaw = getYRot();
            Vec3 g = groundBelow();
            strike(Telegraph.Shape.cone(g, getYRot() + 180F, 200F, 13F).warmup(15).color(phase().rgb).noRunes(), foes -> {
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.8F)) knockFrom(e, g, 1.7, 0.55);
                }
            });
        }
        holdFacing();
        if (t == 15) {
            DragonFx.sound(sl, position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.5F, 0.4F);
            Vec3 back = position().add(Vec3.directionFromRotation(0, getYRot() + 180F).scale(7));
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, back.x, back.y + 1, back.z, 6, 3, 0.3, 3, 0);
        }
    }

    /** Asas no chão: anel de vento em volta (pule!). */
    private void wingBuffet(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Vec3 g = groundBelow();
            strike(Telegraph.Shape.ring(g, 2.5F, 9.5F).warmup(16).vertical(0.5, 1.0).color(phase().rgb), foes -> {
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower() * 0.5F)) knockFrom(e, g, 1.5, 0.5);
                }
                DragonFx.ring(sl, ParticleTypes.CLOUD, g.add(0, 0.4, 0), 6, 30);
                DragonFx.sound(sl, g, SoundEvents.ENDER_DRAGON_FLAP, 3F, 0.5F);
            });
        }
    }

    // ---------------- Comuns: sopro, mergulho, rajada ----------------

    /**
     * Sopro do elemento da fase: cone marcado no chão por 1 s (contrariável), depois 1,7 s de jato contínuo pelo cone
     * travado. O cone fica desenhado enquanto o jato dura.
     */
    private void breath(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            aimA = groundBelow();
            aimYaw = getYRot();
            breathUntil = 0;
            strike(Telegraph.Shape.cone(aimA, aimYaw, 46F, 25F).warmup(20).hold(34).color(phase().rgb)
                    .castName(attackName("breath_" + phase().id)).element(phase().element), foes -> breathUntil = attackTick + 34);
            hint("breath");
        }
        holdFacing();
        Vec3 m = mouthPos();
        if (t < 20) {
            if (t % 3 == 0) sl.sendParticles(breathParticle(), m.x, m.y, m.z, 4, 0.4, 0.4, 0.4, 0.02);
            return;
        }
        if (attackTick >= breathUntil) return;
        Vec3 dir = Vec3.directionFromRotation(0, aimYaw);
        Vec3 groundEnd = aimA.add(dir.scale(22));
        Vec3 flow = groundEnd.subtract(m).normalize();
        for (int i = 0; i < (DragonFx.reduced() ? 4 : 9); i++) {
            Vec3 v = flow.add((random.nextDouble() - 0.5) * 0.45, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.45);
            sl.sendParticles(breathParticle(), m.x, m.y, m.z, 0, v.x, v.y, v.z, 1.2 + random.nextDouble() * 0.6);
        }
        if (t % 6 == 0) DragonFx.sound(sl, m, breathSound(), 2F, 0.6F + random.nextFloat() * 0.2F);
        if (t % 4 == 0) {
            for (LivingEntity e : foesIn(Telegraph.Shape.cone(aimA, aimYaw, 46F, 25F))) {
                if (e.hurt(spellSource(null), spellPower() * 0.22F)) applyPhaseStatus(e, 1);
            }
        }
    }

    private ParticleOptions breathParticle() {
        return switch (phase()) {
            case FIRE -> random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
            case WATER -> random.nextBoolean() ? ParticleTypes.SNOWFLAKE : DragonFx.dust(DragonFx.ICE, 1.6F);
            case EARTH -> DragonFx.dust(random.nextBoolean() ? DragonFx.EARTH : DragonFx.AMBER, 2.0F);
            case STORM -> random.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : DragonFx.dust(DragonFx.STORM, 1.5F);
            case TWILIGHT -> random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK;
        };
    }

    private SoundEvent breathSound() {
        return switch (phase()) {
            case FIRE -> SoundEvents.BLAZE_SHOOT;
            case WATER -> SoundEvents.POWDER_SNOW_STEP;
            case EARTH -> SoundEvents.SAND_BREAK;
            case STORM -> SoundEvents.BEEHIVE_WORK;
            case TWILIGHT -> SoundEvents.BEACON_AMBIENT;
        };
    }

    /**
     * Mergulho Primordial: sobe, marca uma faixa larga até o alvo (e além) e atravessa a arena rente ao chão. No fim
     * bate no chão e fica EXAUSTO (a grande janela de dano das fases aéreas).
     */
    private void dive(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t < 14) {
            hoverToward(position().add(0, 2.5, 0), 1.6);
            return;
        }
        if (t == 14) {
            lockFacing(target);
            Vec3 g = groundAt(getX(), target.getY() + 2, getZ(), 6, 24);
            Vec3 from = new Vec3(getX(), g != null ? g.y : target.getY(), getZ());
            Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
            Vec3 to = target.position().add(dir.scale(7));
            to = clampToArena(to);
            Vec3 tg = groundAt(to.x, target.getY() + 2, to.z, 4, 10);
            aimA = from;
            aimB = new Vec3(to.x, tg != null ? tg.y : target.getY(), to.z);
            aimDir = dir;
            strike(Telegraph.Shape.line(aimA, new Vec3(aimB.x, aimA.y, aimB.z), 6F).warmup(16).hold(14).color(phase().rgb)
                    .castName(attackName("dive")).element(phase().element), foes -> go = true);
            DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.9F);
        }
        holdFacing();
        if (t < 30) {
            hoverToward(new Vec3(getX(), aimA.y + 8, getZ()), 1.0);
            return;
        }
        // O callback do Telegraph roda no FIM do tick 30: a corrida começa no 31 (se não foi contrariado).
        if (!go) {
            if (t >= 31) {
                exhaust(60);
                endAttack();
            }
            return;
        }
        manualVelocity = true;
        int k = t - 31;
        if (k <= 12) {
            Vec3 start = new Vec3(aimA.x, aimA.y + 1.5, aimA.z);
            Vec3 end = new Vec3(aimB.x, aimB.y + 0.5, aimB.z);
            Vec3 want = start.lerp(end, k / 12.0);
            move(MoverType.SELF, want.subtract(position()));
            setDeltaMovement(Vec3.ZERO);
            sl.sendParticles(breathParticle(), getX(), getY() + 1.5, getZ(), 6, 2, 0.6, 2, 0.05);
            for (LivingEntity e : enemiesNear(position().add(0, 1.5, 0), 4.2)) {
                if (!hitOnce.add(e.getUUID())) continue;
                if (e.hurt(damageSources().mobAttack(this), attackPower() * 1.2F)) {
                    applyPhaseStatus(e, 2);
                    e.setDeltaMovement(aimDir.x * 1.4 + (random.nextDouble() - 0.5), 0.7, aimDir.z * 1.4);
                    e.hurtMarked = true;
                }
            }
            if (k % 3 == 0) DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_FLAP, 2F, 1.3F);
        }
        if (k == 12) {
            Vec3 c = groundBelow();
            for (LivingEntity e : enemiesNear(c, 5)) {
                if (hitOnce.add(e.getUUID()) && e.hurt(damageSources().mobAttack(this), attackPower() * 0.6F)) knockFrom(e, c, 1.0, 0.4);
            }
            landingDust(sl, c, 7);
            BossKit.shake(sl, c, 36, 1.8F, 20);
            DragonFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 2F, 0.6F);
            exhaust(55);
            endAttack();
        }
    }

    /** Rajada de asas do ar: cone largo no chão empurrando para longe. */
    private void gale(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            Vec3 g = groundAt(getX(), target.getY() + 2, getZ(), 4, 24);
            Vec3 o = g != null ? g : target.position();
            strike(Telegraph.Shape.cone(o, getYRot(), 110F, 17F).warmup(18).color(phase().rgb), foes -> {
                for (LivingEntity e : foes) {
                    e.hurt(spellSource(null), spellPower() * 0.35F);
                    knockFrom(e, o, phase() == DragonPhase.STORM ? 1.9 : 1.5, 0.45);
                }
                DragonFx.sound(sl, o, SoundEvents.ENDER_DRAGON_FLAP, 3F, 0.4F);
            });
        }
        holdFacing();
        if (t >= 10 && t <= 22 && t % 2 == 0) {
            Vec3 d = Vec3.directionFromRotation(10, getYRot());
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 0, d.x, -0.3, d.z, 1.4);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 6, 3, 0.5, 3, 0.1);
        }
    }

    // ---------------- 1 FOGO ----------------

    /** Chuva de Meteoros: um meteoro persegue cada inimigo (trava no fim) e outros caem em pontos da arena. */
    private void meteorRain(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("meteor", Announce.STYLE_SPELL);
            hint("meteor");
        }
        if (t < 10 && t % 2 == 0) {
            Vec3 m = mouthPos();
            sl.sendParticles(ParticleTypes.FLAME, m.x, m.y + 1, m.z, 10, 0.5, 1.5, 0.5, 0.1);
        }
        if (t != 10) return;
        int rgb = Telegraph.color(Element.FIRE);
        boolean first = true;
        for (LivingEntity e : arenaFoes(6)) {
            int[] id = new int[1];
            Telegraph.Shape s = Telegraph.Shape.follow(e, 3F).warmup(46).lock(12).color(rgb);
            if (first) s.castName(attackName("meteor_rain")).tier(SpellStack.Tier.MAJOR);
            first = false;
            id[0] = strike(s, foes -> meteorImpact(sl, Telegraph.centerOf(id[0]), foes));
            hazards.meteor(id[0], 46, Blocks.MAGMA_BLOCK.defaultBlockState(), 2.6F);
        }
        int extra = (phase() == DragonPhase.FIRE ? 4 : 2) + BossKit.partySize(this);
        for (int i = 0; i < extra; i++) {
            Vec3 p = randomArenaPoint(arenaRadius - 2);
            if (p == null) continue;
            int warm = 36 + i * 6;
            int[] id = new int[1];
            id[0] = strike(Telegraph.Shape.circle(p, 3.5F).warmup(warm).color(rgb), foes -> meteorImpact(sl, p, foes));
            hazards.meteor(id[0], warm, Blocks.MAGMA_BLOCK.defaultBlockState(), 2.2F);
        }
    }

    private void meteorImpact(ServerLevel sl, @Nullable Vec3 at, List<LivingEntity> foes) {
        if (at == null) return;
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 40, 1.5, 0.6, 1.5, 0.15);
        sl.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 12, 1, 0.3, 1, 0);
        DragonFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.7F);
        BossKit.shake(sl, at, 18, 0.8F, 10);
        for (LivingEntity e : foes) {
            if (e.hurt(spellSource(null), spellPower() * 0.9F)) Statuses.apply(e, Statuses.Kind.BURNING, 2, 120, this, 1F);
        }
        ArenaWorks.magmaPatch(sl, at, 2.2, getUUID(), 160);
    }

    /** Mar de Lava: 4 fatias alternadas da arena viram lava (magma) por 7 s; as outras 4 são o refúgio. */
    private void lavaFloor(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            aimYaw = random.nextFloat() * 90F;
            Vec3 c = arenaGround();
            for (int i = 0; i < 4; i++) {
                float yaw = aimYaw + i * 90F;
                Telegraph.Shape s = Telegraph.Shape.cone(c, yaw, 45F, (float) arenaRadius + 2).warmup(40).hold(140)
                        .color(Telegraph.color(Element.FIRE)).noRunes();
                if (i == 0) s.castName(attackName("lava_floor")).tier(SpellStack.Tier.MAJOR);
                strike(s, foes -> {
                    ArenaWorks.magmaSector(sl, c, yaw, 45F, arenaRadius + 2, getUUID(), 150);
                    for (LivingEntity e : foes) Statuses.apply(e, Statuses.Kind.BURNING, 2, 100, this, 1F);
                });
            }
            announce("lava", Announce.STYLE_SPELL);
            hint("lava");
            DragonFx.sound(sl, c, SoundEvents.LAVA_AMBIENT, 3F, 0.5F);
        }
        if (t == 40) DragonFx.sound(sl, arenaGround(), SoundEvents.LAVA_POP, 3F, 0.4F);
    }

    // ---------------- 2 ÁGUA / GELO ----------------

    /**
     * Maremoto: faixa de 10 blocos atravessando a arena (marcada 1,5 s); depois a onda CORRE pela faixa e arrasta
     * quem ela tocar. Na fase 2 vem uma segunda onda cruzada.
     */
    private void tsunami(LivingEntity target, int t) {
        if (t == 0) {
            announce("tsunami", Announce.STYLE_SPELL);
            hint("tsunami");
        }
        if (t == 6) waveAt(target, random.nextFloat() * 360F, true);
        if (t == 34 && phase() == DragonPhase.WATER) waveAt(target, aimYaw + 70F + random.nextFloat() * 40F, false);
    }

    private void waveAt(LivingEntity target, float yaw, boolean first) {
        ServerLevel sl = (ServerLevel) level();
        aimYaw = yaw;
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 c = arenaGround();
        double offset = target.position().subtract(c).dot(side) + (random.nextDouble() - 0.5) * 4;
        Vec3 mid = c.add(side.scale(offset));
        double half = arenaRadius + 4;
        Vec3 from = mid.subtract(dir.scale(half));
        Vec3 to = mid.add(dir.scale(half));
        Vec3 gf = groundAt(from.x, c.y, from.z, 4, 6);
        Vec3 gt = groundAt(to.x, c.y, to.z, 4, 6);
        Vec3 f = new Vec3(from.x, gf != null ? gf.y : c.y, from.z);
        Vec3 e = new Vec3(to.x, gt != null ? gt.y : c.y, to.z);
        Telegraph.Shape s = Telegraph.Shape.line(f, e, 10F).warmup(30).hold(18).color(Telegraph.color(Element.WATER)).vertical(1.5, 4.0);
        if (first) s.castName(attackName("tsunami")).tier(SpellStack.Tier.MAJOR);
        strike(s, foes -> hazards.wave(f, e, 10, 18));
        DragonFx.sound(sl, f, SoundEvents.ELDER_GUARDIAN_CURSE, 1.5F, 0.5F);
    }

    /** Prisão de Gelo: círculos que seguem até 2 inimigos e travam; quem ficar dentro é Congelado numa jaula de gelo. */
    private void icePrison(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 6) return;
        int max = Math.min(2, BossKit.partySize(this));
        int n = 0;
        for (LivingEntity e : arenaFoes(max)) {
            Telegraph.Shape s = Telegraph.Shape.follow(e, 1.8F).warmup(40).lock(14).color(Telegraph.FROST);
            if (n++ == 0) s.castName(attackName("ice_prison")).tier(SpellStack.Tier.MAJOR);
            strike(s, foes -> {
                for (LivingEntity f : foes) {
                    BlockPos b = f.blockPosition();
                    f.teleportTo(b.getX() + 0.5, f.getY(), b.getZ() + 0.5);
                    f.setDeltaMovement(Vec3.ZERO);
                    Statuses.apply(f, Statuses.Kind.FROZEN, 1, 60, this, 1F);
                    f.hurt(spellSource(null), spellPower() * 0.5F);
                    ArenaWorks.iceCage(sl, f, getUUID(), 70);
                    sl.sendParticles(ParticleTypes.SNOWFLAKE, f.getX(), f.getY() + 1, f.getZ(), 40, 0.8, 1, 0.8, 0.05);
                    DragonFx.sound(sl, f.position(), SoundEvents.GLASS_PLACE, 2F, 0.5F);
                }
            });
        }
        hint("ice_prison");
    }

    /** Estacas Glaciais: 6 linhas radiais; molhado + gelo = CONGELAR. Estacas de gelo ficam um instante. */
    private void glacialSpikes(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 4) return;
        Vec3 c = groundAt(getX(), target.getY() + 2, getZ(), 4, 24);
        Vec3 o = c != null ? c : target.position();
        float base = (float) (Mth.atan2(target.getZ() - o.z, target.getX() - o.x) * Mth.RAD_TO_DEG);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(base + i * 60);
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 end = o.add(dir.scale(19));
            Telegraph.Shape s = Telegraph.Shape.line(o.add(dir.scale(2)), end, 2.6F).warmup(22).color(Telegraph.FROST);
            if (i == 0) s.castName(attackName("glacial_spikes"));
            strike(s, foes -> {
                for (int k = 3; k < 19; k += 2) {
                    Vec3 p = o.add(dir.scale(k));
                    BlockPos top = ArenaWorks.surface(sl, Mth.floor(p.x), Mth.floor(p.z), Mth.floor(o.y));
                    if (top == null) continue;
                    ArenaTerraform tf = ArenaTerraform.get(sl);
                    for (int h = 1; h <= 1 + (k % 4 == 1 ? 1 : 0); h++) {
                        BlockPos q = top.above(h);
                        if (sl.getBlockState(q).isAir() && sl.getEntitiesOfClass(LivingEntity.class, new AABB(q), Entity::isAlive).isEmpty()) {
                            tf.place(sl, q, Blocks.PACKED_ICE.defaultBlockState(), 60, getUUID());
                        }
                    }
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), p.x, top.getY() + 1.5,
                            p.z, 6, 0.3, 0.5, 0.3, 0.1);
                }
                for (LivingEntity e : foes) {
                    if (!e.hurt(spellSource(null), spellPower() * 0.7F)) continue;
                    if (Statuses.has(e, Statuses.Kind.WET)) {
                        Statuses.clear(e, Statuses.Kind.WET);
                        Statuses.apply(e, Statuses.Kind.FROZEN, 1, 60, this, 1F);
                    } else {
                        Statuses.apply(e, Statuses.Kind.CHILLED, 2, 120, this, 1F);
                    }
                }
                DragonFx.sound(sl, end, SoundEvents.GLASS_BREAK, 1.5F, 0.6F);
            });
        }
    }

    // ---------------- 3 TERRA ----------------

    /** Terremoto: 3 anéis que saem do dragão, um depois do outro (pule cada um). */
    private void quake(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Vec3 c = groundBelow();
            aimA = c;
            float[][] rings = {{3F, 9F}, {9F, 15F}, {15F, 21F}};
            int[] warm = {22, 32, 42};
            for (int i = 0; i < 3; i++) {
                float inner = rings[i][0];
                float outer = rings[i][1];
                Telegraph.Shape s = Telegraph.Shape.ring(c, inner, outer).warmup(warm[i]).vertical(0.5, 1.0)
                        .color(Telegraph.color(Element.EARTH));
                if (i == 0) s.castName(attackName("quake")).tier(SpellStack.Tier.MAJOR);
                strike(s, foes -> {
                    BlockState dirt = Blocks.COARSE_DIRT.defaultBlockState();
                    for (int k = 0; k < 24; k++) {
                        double a = Math.PI * 2 * k / 24;
                        double r = (inner + outer) / 2;
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dirt), c.x + Math.cos(a) * r, c.y + 0.3,
                                c.z + Math.sin(a) * r, 4, 0.8, 0.2, 0.8, 0.1);
                    }
                    DragonFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.5F);
                    for (LivingEntity e : foes) {
                        if (e.hurt(spellSource(null), spellPower() * 0.75F)) {
                            Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 120, this, 1F);
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                            e.hurtMarked = true;
                        }
                    }
                });
            }
            hint("quake");
        }
        if (t == 20) {
            BossKit.shake(sl, aimA, 40, 2.0F, 30);
            DragonFx.sound(sl, aimA, SoundEvents.WARDEN_SONIC_BOOM, 2F, 0.5F);
        }
    }

    /** Pilares da Terra: o chão sob cada inimigo explode num pilar de pedra que fica como cobertura. */
    private void stonePillars(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 6) return;
        List<LivingEntity> foes = arenaFoes(4);
        boolean first = true;
        for (LivingEntity e : foes) {
            int[] id = new int[1];
            Telegraph.Shape s = Telegraph.Shape.follow(e, 2.0F).warmup(30).lock(10).color(Telegraph.color(Element.EARTH));
            if (first) s.castName(attackName("stone_pillars"));
            first = false;
            id[0] = strike(s, hit -> pillarBurst(sl, Telegraph.centerOf(id[0]), hit));
        }
        Vec3 p = randomArenaPoint(arenaRadius - 4);
        if (p != null) strike(Telegraph.Shape.circle(p, 2.0F).warmup(30).color(Telegraph.color(Element.EARTH)), hit -> pillarBurst(sl, p, hit));
        DragonFx.sound(sl, position(), SoundEvents.DEEPSLATE_BREAK, 2F, 0.4F);
    }

    private void pillarBurst(ServerLevel sl, @Nullable Vec3 at, List<LivingEntity> foes) {
        if (at == null) return;
        for (LivingEntity e : foes) {
            if (e.hurt(spellSource(null), spellPower() * 0.6F)) Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 100, this, 1F);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.15, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), at.x, at.y + 1, at.z, 40, 1, 1.5, 1, 0.2);
        DragonFx.sound(sl, at, SoundEvents.DRIPSTONE_BLOCK_BREAK, 2F, 0.5F);
        // Um instante depois (quem estava em cima já subiu) a pedra termina de subir.
        BossKit.schedule(sl, 3, () -> {
            if (isAlive()) ArenaWorks.pillar(sl, at, 5, getUUID(), 260, Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.POLISHED_BASALT.defaultBlockState());
        });
    }

    /** Impacto Sísmico: sobe, marca um círculo enorme e despenca abrindo uma CRATERA de verdade; depois fica preso. */
    private void craterSlam(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t < 16) {
            hoverToward(position().add(0, 2.5, 0), 1.6);
            return;
        }
        if (t == 16) {
            lockFacing(target);
            Vec3 g = groundAt(target.getX(), target.getY() + 2, target.getZ(), 4, 8);
            aimA = g != null ? g : target.position();
            aimB = position();
            strike(Telegraph.Shape.circle(aimA, 6.5F).warmup(42).color(Telegraph.color(Element.EARTH)).castName(attackName("crater_slam"))
                    .tier(SpellStack.Tier.MAJOR).element(Element.EARTH), foes -> {
                go = true;
                for (LivingEntity e : foes) {
                    if (e.hurt(damageSources().mobAttack(this), attackPower() * 1.4F)) {
                        Statuses.apply(e, Statuses.Kind.PETRIFIED, 2, 120, this, 1F);
                        knockFrom(e, aimA, 1.3, 0.8);
                    }
                }
                ArenaWorks.crater(sl, aimA, 5.5, getUUID(), 900);
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, aimA.x, aimA.y + 0.5, aimA.z, 2, 1, 0.2, 1, 0);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), aimA.x, aimA.y + 1, aimA.z,
                        80, 3, 1.5, 3, 0.3);
                DragonFx.sound(sl, aimA, SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.4F);
                BossKit.shake(sl, aimA, 48, 2.8F, 30);
            });
            hint("crater");
        }
        holdFacing();
        if (t < 50) {
            hoverToward(new Vec3(aimA.x, aimA.y + 12, aimA.z), 1.2);
            return;
        }
        if (t <= 58) {
            manualVelocity = true;
            Vec3 want = position().lerp(aimA.add(0, 0.3, 0), (t - 49) / 9.0);
            move(MoverType.SELF, want.subtract(position()));
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (t == 60) {
            exhaust(go ? 45 : 70);
            endAttack();
        }
    }

    // ---------------- 4 VENTO / RAIO ----------------

    /** Tornados: nascem em pontos marcados e perseguem devagar, sugando e erguendo quem chega perto. */
    private void tornados(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 10) return;
        int n = Math.min(DragonHazards.MAX_TORNADOS - hazards.tornadoCount(),
                1 + BossKit.partySize(this) / 2 + (phase() == DragonPhase.STORM ? 1 : 0));
        for (int i = 0; i < n; i++) {
            Vec3 p = randomArenaPoint(arenaRadius - 3);
            if (p == null) continue;
            Telegraph.Shape s = Telegraph.Shape.circle(p, 2.5F).warmup(20).color(Telegraph.color(Element.WIND)).noFlash();
            if (i == 0) s.castName(attackName("tornados"));
            strike(s, foes -> hazards.tornado(p, 240));
        }
        hint("tornado");
        DragonFx.sound(sl, position(), SoundEvents.ELYTRA_FLYING, 2F, 0.5F);
    }

    /**
     * Tempestade de Raios: primeiro uma chuva MOLHA todo mundo; depois raios marcados caem em cada um — e o raio
     * ARQUEIA para quem estiver molhado a até 7 blocos (espalhem-se!).
     */
    private void lightningStorm(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("storm", Announce.STYLE_SPELL);
            hint("storm");
        }
        if (t < 16 && t % 2 == 0) {
            for (LivingEntity e : arenaFoes(8)) {
                sl.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + 4, e.getZ(), 12, 1.5, 0.2, 1.5, 0);
                if (t == 14) Statuses.apply(e, Statuses.Kind.WET, 1, 240, this, 1F);
            }
        }
        if (t == 16 || (t == 44 && phase() == DragonPhase.STORM)) {
            boolean first = t == 16;
            for (LivingEntity e : arenaFoes(8)) {
                int[] id = new int[1];
                Telegraph.Shape s = Telegraph.Shape.follow(e, 2.5F).warmup(34).lock(10).color(DragonFx.STORM);
                if (first) s.castName(attackName("lightning_storm")).tier(SpellStack.Tier.MAJOR);
                first = false;
                id[0] = strike(s, foes -> lightningAt(sl, Telegraph.centerOf(id[0]), foes));
            }
            if (t == 44) {
                for (int i = 0; i < 3; i++) {
                    Vec3 p = randomArenaPoint(arenaRadius - 2);
                    if (p != null) strike(Telegraph.Shape.circle(p, 2.5F).warmup(30).color(DragonFx.STORM), foes -> lightningAt(sl, p, foes));
                }
            }
        }
    }

    private void lightningAt(ServerLevel sl, @Nullable Vec3 at, List<LivingEntity> foes) {
        if (at == null) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        DragonFx.sound(sl, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 2F, 1.0F);
        Set<UUID> struck = new HashSet<>();
        for (LivingEntity e : foes) {
            struck.add(e.getUUID());
            if (e.hurt(spellSource(null), spellPower() * 0.8F)) Statuses.apply(e, Statuses.Kind.SHOCKED, 2, 100, this, 1F);
        }
        // Arco elétrico para os Molhados próximos de quem foi atingido.
        for (LivingEntity e : foes) {
            for (LivingEntity o : enemiesNear(e.position(), 7)) {
                if (struck.contains(o.getUUID()) || !Statuses.has(o, Statuses.Kind.WET)) continue;
                struck.add(o.getUUID());
                DragonFx.bolt(sl, DragonFx.dust(DragonFx.STORM, 1.2F), e.position().add(0, 1, 0), o.position().add(0, 1, 0), 1.2, random);
                if (o.hurt(spellSource(null), spellPower() * 0.45F)) Statuses.apply(o, Statuses.Kind.SHOCKED, 1, 80, this, 1F);
                DragonFx.sound(sl, o.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.8F, 1.6F);
            }
        }
    }

    /** Rajadas: 3 faixas paralelas de vento atravessam a arena, uma depois da outra, empurrando ao longo delas. */
    private void galeLines(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            aimYaw = random.nextFloat() * 360F;
            hint("gale");
        }
        if (t != 4 && t != 12 && t != 20) return;
        int i = (t - 4) / 8;
        Vec3 dir = Vec3.directionFromRotation(0, aimYaw);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 c = arenaGround();
        Vec3 mid = c.add(side.scale((i - 1) * 9.0));
        double half = arenaRadius + 3;
        Vec3 from = mid.subtract(dir.scale(half));
        Vec3 to = mid.add(dir.scale(half));
        Telegraph.Shape s = Telegraph.Shape.line(from, to, 4.5F).warmup(22).color(Telegraph.color(Element.WIND)).vertical(1.5, 5);
        if (i == 0) s.castName(attackName("gale_lines"));
        strike(s, foes -> {
            for (LivingEntity e : foes) {
                e.hurt(spellSource(null), spellPower() * 0.4F);
                e.setDeltaMovement(dir.x * 1.2, 0.35, dir.z * 1.2);
                e.hurtMarked = true;
            }
            DragonFx.line(sl, ParticleTypes.CLOUD, from.add(0, 1, 0), to.add(0, 1, 0), 1.2);
            DragonFx.sound(sl, mid, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2F, 0.6F);
        });
    }

    // ---------------- 5 LUZ + SOMBRA ----------------

    /**
     * Expansão de Domínio "Fim dos Elementos": o domo FECHADO do módulo domain (Acerto Garantido + Sem Fuga; a regra da
     * barreira vale) — ou, sem ele, uma barreira própria registrada no núcleo com Acerto Garantido.
     */
    private void domainCast(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            Component name = title("domain_name");
            Announce.send(this, name, TWILIGHT_RGB, Announce.STYLE_ULTIMATE);
            Cutscenes.playNear(this, BAR_RANGE, "domain", name, title("domain_subtitle"), TWILIGHT_RGB, 70, null);
            DragonFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 3F, 0.4F);
        }
        Vec3 g = arenaGround();
        if (t < 40) {
            hoverToward(g.add(0, 1.5, 0), 1.4);
            if (t % 2 == 0) {
                double a = t * 0.4;
                for (int i = 0; i < 2; i++) {
                    double aa = a + i * Math.PI;
                    double r = 8.0 * (1 - t / 40.0) + 1;
                    sl.sendParticles(DragonFx.dust(i == 0 ? DragonFx.LIGHT : DragonFx.SHADOW, 2.5F), getX() + Math.cos(aa) * r, getY() + 3,
                            getZ() + Math.sin(aa) * r, 2, 0.05, 0.4, 0.05, 0);
                }
            }
            return;
        }
        if (t != 40) return;
        SpellRecipe r = domainRecipe();
        boolean glyph = r != null && SpellCaster.castAsMob(this, Element.TWILIGHT, r, 2.0F * BossKit.damageScale(this)) != null;
        domainOpenedAt = sl.getGameTime();
        if (glyph && glyphDomeAt(sl, position())) {
            domainGlyph = true;
            glyphDomainCenter = position();
        } else {
            domainGlyph = false;
            glyphDomainCenter = null;
            float radius = (float) Math.min(arenaRadius + 2, 46);
            entityData.set(DATA_DOMAIN_CENTER, BlockPos.containing(g));
            entityData.set(DATA_DOMAIN_RADIUS, radius);
            entityData.set(DATA_DOMAIN, 700);
            Domains.opened(ownDomainInfo(sl));
        }
        hint("domain");
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 3, getZ(), 2, 0, 0, 0, 0);
        sl.sendParticles(DragonFx.dust(DragonFx.LIGHT, 3F), getX(), getY() + 3, getZ(), 80, 5, 3, 5, 0);
        sl.sendParticles(DragonFx.dust(DragonFx.SHADOW, 3F), getX(), getY() + 3, getZ(), 80, 5, 3, 5, 0);
        DragonFx.sound(sl, position(), SoundEvents.END_PORTAL_SPAWN, 3F, 0.6F);
        BossKit.shake(sl, position(), 48, 2.0F, 30);
    }

    /** Receita do domo (a primeira que validar). */
    @Nullable
    private static SpellRecipe domainRecipe() {
        String[][] options = {
                {"domain_expansion", "silent", "law_sure_hit", "law_no_escape"},
                {"domain_expansion", "law_sure_hit", "law_no_escape"},
                {"domain_expansion", "silent", "reveal", "law_sure_hit", "law_no_escape"},
                {"domain_expansion", "reveal", "law_sure_hit"},
        };
        for (String[] ids : options) {
            List<ResourceLocation> list = new ArrayList<>();
            boolean ok = true;
            for (String id : ids) {
                ResourceLocation rl = IsekaiMahou.id(id);
                if (GlyphRegistry.get(rl) == null) {
                    ok = false;
                    break;
                }
                list.add(rl);
            }
            if (!ok) continue;
            SpellRecipe r = new SpellRecipe(list, "", TWILIGHT_RGB, "");
            if (SpellParser.parse(r, null, null).ok()) return r;
        }
        return null;
    }

    /** O domo do glifo (entidade domain_dome do módulo domain, por id) está de pé perto de {@code c}? */
    private boolean glyphDomeAt(ServerLevel sl, Vec3 c) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("domain_dome")).orElse(null);
        if (type == null) return false;
        return !sl.getEntities(type, new AABB(c, c).inflate(6.0), Entity::isAlive).isEmpty();
    }

    private Domains.Info ownDomainInfo(ServerLevel sl) {
        return new Domains.Info(-1 - getId(), sl, this, ownDomainCenter().add(0, 1, 0), ownDomainRadius(), false, Set.of(Domains.SURE_HIT),
                Element.TWILIGHT, null, domainOpenedAt, 700);
    }

    public Vec3 domainCenterNow() {
        if (ownDomainActive()) return ownDomainCenter();
        return glyphDomainCenter != null ? glyphDomainCenter : homeCenter();
    }

    public double domainRadiusNow() {
        return ownDomainActive() ? ownDomainRadius() : arenaRadius;
    }

    private void tickDomain(ServerLevel sl) {
        if (domainGlyph && glyphDomainCenter != null && tickCount % 20 == 0 && !glyphDomeAt(sl, glyphDomainCenter)) {
            domainGlyph = false;
            glyphDomainCenter = null;
            hint("domain_end");
        }
        int left = entityData.get(DATA_DOMAIN);
        if (left <= 0) return;
        Domains.Info info = ownDomainInfo(sl);
        Domains.ticked(info, (int) (sl.getGameTime() - domainOpenedAt));
        entityData.set(DATA_DOMAIN, left - 1);
        // Barreira fechada: quem encosta na borda volta para dentro.
        Vec3 c = ownDomainCenter();
        float r = ownDomainRadius();
        for (LivingEntity e : enemiesNear(c, r + 8)) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= r - 0.8 || d < 0.01) continue;
            e.setDeltaMovement(-dx / d * 0.9, 0.25, -dz / d * 0.9);
            e.hurtMarked = true;
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.6, 0.3, 0.1);
        }
        if (left % 5 == 0) DragonFx.ring(sl, DragonFx.dust(left % 10 == 0 ? DragonFx.LIGHT : DragonFx.SHADOW, 2.0F), c.add(0, 0.3, 0), r, 48);
        if (left - 1 == 0) {
            Domains.closed(info);
            hint("domain_end");
        }
    }

    private void closeDomain(ServerLevel sl) {
        if (entityData.get(DATA_DOMAIN) > 0) {
            Domains.closed(ownDomainInfo(sl));
            entityData.set(DATA_DOMAIN, 0);
        }
        domainGlyph = false;
        glyphDomainCenter = null;
    }

    /** Clones de Sombra: 2–3 dragões de sombra. Enquanto existirem, o dragão recebe metade do dano. */
    private void shadowClones(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        int n = 2 + (BossKit.partySize(this) >= 3 ? 1 : 0);
        if (t == 0) {
            aimYaw = random.nextFloat() * 360F;
            for (int i = 0; i < n; i++) {
                Vec3 p = arenaGround().add(Vec3.directionFromRotation(0, aimYaw + i * 360F / n).scale(9));
                warn(Telegraph.Shape.circle(p, 2.5F).warmup(20).color(Telegraph.VOID).noFlash());
            }
        }
        if (t != 20) return;
        float hp = 70F * (1F + 0.5F * (BossKit.partySize(this) - 1));
        for (int i = 0; i < n; i++) {
            Vec3 p = arenaGround().add(Vec3.directionFromRotation(0, aimYaw + i * 360F / n).scale(9)).add(0, 4, 0);
            PrimordialShade s = BeRegistries.PRIMORDIAL_SHADE.get().create(sl);
            if (s == null) continue;
            s.moveTo(p.x, p.y, p.z, random.nextFloat() * 360F, 0F);
            s.setup(this, hp, Math.toRadians(aimYaw + i * 360F / n));
            sl.addFreshEntity(s);
            sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 40, 1.5, 1, 1.5, 0.08);
        }
        shadeCount = n;
        announce("shades", Announce.STYLE_BOSS);
        hint("shades");
        DragonFx.sound(sl, position(), SoundEvents.EVOKER_PREPARE_SUMMON, 3F, 0.5F);
    }

    private void refreshShades(ServerLevel sl) {
        shadeCount = sl.getEntitiesOfClass(PrimordialShade.class, getBoundingBox().inflate(96), s -> s.isAlive() && s.ownedBy(this)).size();
    }

    private void discardShades(ServerLevel sl) {
        for (PrimordialShade s : sl.getEntitiesOfClass(PrimordialShade.class, getBoundingBox().inflate(128), s -> s.ownedBy(this))) {
            sl.sendParticles(ParticleTypes.SQUID_INK, s.getX(), s.getY() + 1, s.getZ(), 30, 1, 1, 1, 0.05);
            s.discard();
        }
        shadeCount = 0;
    }

    /** Chuva do Eclipse: marcas douradas (Luz: Exposto, cega) e violetas (Sombra: Corrompido) em todos. */
    private void eclipseRain(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 8) return;
        int i = 0;
        List<Vec3> spots = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            Vec3 p = randomArenaPoint(arenaRadius - 2);
            if (p != null) spots.add(p);
        }
        for (LivingEntity e : arenaFoes(8)) {
            boolean light = i++ % 2 == 0;
            int[] id = new int[1];
            Telegraph.Shape s = Telegraph.Shape.follow(e, 2.6F).warmup(34).lock(10).color(light ? Telegraph.HOLY : Telegraph.VOID);
            if (i == 1) s.castName(attackName("eclipse_rain"));
            id[0] = strike(s, foes -> eclipseHit(sl, Telegraph.centerOf(id[0]), light, foes));
        }
        for (Vec3 p : spots) {
            boolean light = i++ % 2 == 0;
            strike(Telegraph.Shape.circle(p, 2.6F).warmup(34).color(light ? Telegraph.HOLY : Telegraph.VOID), foes -> eclipseHit(sl, p, light, foes));
        }
    }

    private void eclipseHit(ServerLevel sl, @Nullable Vec3 at, boolean light, List<LivingEntity> foes) {
        if (at == null) return;
        DragonFx.column(sl, light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, at, 18, 24, 0.2);
        DragonFx.column(sl, DragonFx.dust(light ? DragonFx.LIGHT : DragonFx.SHADOW, 2.4F), at, 10, 14, 0.4);
        DragonFx.sound(sl, at, light ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WARDEN_SONIC_BOOM, 1.5F, light ? 1.3F : 0.9F);
        for (LivingEntity e : foes) {
            if (!e.hurt(spellSource(null), spellPower() * 0.8F)) continue;
            if (light) {
                Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 120, this, 1F);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), this);
            } else {
                Statuses.apply(e, Statuses.Kind.CORRUPTED, 2, 120, this, 1F);
            }
        }
    }

    /**
     * ANIQUILAÇÃO PRIMORDIAL (Suprema, só com o domínio de pé): 6 s de carga com a arena inteira marcada (Suprema na
     * Pilha). v1.0: NÃO mata — quem estiver no domínio cai para 10% da vida, perde TODO o recurso, vira estátua por 6 s
     * e ganha um acúmulo de Ruína (−15% de vida máxima até o fim da luta). Só a CONTRAMÁGICA (poder 2) impede; um DOMÍNIO
     * SIMPLES (seu ou de um aliado) segura o golpe, mas não a Ruína. Contrariada, ele despenca exausto.
     */
    private void annihilation(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = domainCenterNow();
        if (t == 0) beginUltimate("annihilation", TWILIGHT_RGB);
        if (t < 20) {
            hoverToward(c.add(0, 9, 0), 1.4);
            return;
        }
        stopMoving();
        if (t == 20) {
            float radius = (float) Math.max(domainRadiusNow(), arenaRadius) + 4F;
            Vec3 g = groundAt(c.x, c.y + 2, c.z, 4, 12);
            ultTele = strike(Telegraph.Shape.circle(g != null ? g : c, radius).warmup(120).color(TWILIGHT_RGB).vertical(8, 40)
                    .castName(attackName("annihilation")).tier(SpellStack.Tier.ULTIMATE).element(Element.TWILIGHT), this::annihilationBlast);
        }
        if (t > 20 && t < 140) {
            double prog = (t - 20) / 120.0;
            Vec3 core = position().add(0, 2.5, 0);
            int pts = DragonFx.reduced() ? 2 : 5;
            for (int i = 0; i < pts; i++) {
                double a = t * 0.3 + i * Math.PI * 2 / pts;
                double r = 10 * (1 - prog) + 1.5;
                int rgb = switch (i % 5) {
                    case 0 -> DragonFx.FIRE;
                    case 1 -> DragonFx.WATER;
                    case 2 -> DragonFx.EARTH;
                    case 3 -> DragonFx.STORM;
                    default -> i % 2 == 0 ? DragonFx.LIGHT : DragonFx.SHADOW;
                };
                sl.sendParticles(DragonFx.dust(rgb, 2.5F), core.x + Math.cos(a) * r, core.y + Math.sin(a * 0.5) * 2, core.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
            if (t % 20 == 0) DragonFx.sound(sl, core, SoundEvents.BEACON_AMBIENT, 3F, 0.5F + (float) prog);
            if (t % 30 == 0) BossKit.shake(sl, c, 48, 0.4F + (float) prog, 20);
        }
        if (t == 143) finishUltimate(90);
    }

    private void annihilationBlast(List<LivingEntity> foes) {
        if (!(level() instanceof ServerLevel sl)) return;
        ultFired = true;
        entityData.set(DATA_CHARGING, false);
        Vec3 c = position();
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 2, c.z, 5, 4, 2, 4, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, 8, 2, 8, 0);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            Vec3 p = c.add(Math.cos(a) * 12, -6, Math.sin(a) * 12);
            DragonFx.column(sl, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, p, 14, 20, 0.3);
        }
        DragonFx.sound(sl, c, SoundEvents.END_PORTAL_SPAWN, 4F, 0.5F);
        DragonFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 4F, 0.4F);
        DragonFx.sound(sl, c, SoundEvents.WITHER_DEATH, 2F, 0.4F);
        BossKit.shake(sl, c, 64, 3.0F, 40);
        for (LivingEntity e : foes) {
            boolean shielded = simpleShielded(sl, e);
            DragonCurses.ruin(e, 1, 6000, this);
            if (shielded) {
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, e.getX(), e.getY() + 1, e.getZ(), 30, 0.6, 1, 0.6, 0.3);
                if (e instanceof ServerPlayer p) {
                    p.displayClientMessage(Component.translatable(HINT + "simple_saved").withStyle(ChatFormatting.AQUA), true);
                }
                continue;
            }
            // Sem morte instantânea: a vida CAI para 10% (nunca abaixo), o recurso zera e a pedra fecha.
            float floor = e.getMaxHealth() * 0.10F;
            if (e.getHealth() > floor) {
                e.setHealth(floor);
                e.hurtTime = 10;
                e.hurtDuration = 10;
                DragonFx.sound(sl, e.position(), SoundEvents.PLAYER_HURT, 1F, 0.6F);
            }
            if (e instanceof ServerPlayer sp) DragonCurses.drainResource(sp, 1.0F);
            DragonCurses.statue(e, 120, this);
            Statuses.apply(e, Statuses.Kind.EXPOSED, 2, 160, this, 1F);
        }
        hint("annihilation_hit");
    }

    /** Está num Domínio Simples seu, de um aliado (ou de outro jogador, para jogadores)? */
    private static boolean simpleShielded(ServerLevel sl, LivingEntity e) {
        for (Domains.Info d : Domains.active(sl)) {
            if (!d.simple() || !d.contains(e)) continue;
            LivingEntity o = d.owner();
            if (o == e || o.isAlliedTo(e) || (o instanceof Player && e instanceof Player)
                    || (e instanceof OwnableEntity own && o.getUUID().equals(own.getOwnerUUID()))) return true;
        }
        return false;
    }

    // =====================================================================================
    //  v1.0 — CONTROLE: estátuas, correntes, drenos, troca de lugar, esquecimento, sacrifício
    // =====================================================================================

    /**
     * Correntes do Abismo: marca 1–4 jogadores (1 + metade do grupo); quem continuar no círculo quando ele travar é
     * ACORRENTADO a uma âncora cravada no chão ({@link PrimordialChain}) — preso num raio curto, drenado, e esmagado em
     * estátua se ninguém quebrar a âncora em 15 s.
     */
    private void abyssChains(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("chains", Announce.STYLE_SPELL);
            hint("chains");
            DragonFx.sound(sl, position(), SoundEvents.CHAIN_PLACE, 3F, 0.4F);
        }
        if (t < 8 && t % 2 == 0) {
            Vec3 m = mouthPos();
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 8, 0.6, 0.6, 0.6, 0.05);
        }
        if (t != 8) return;
        int max = Math.min(4, 1 + BossKit.partySize(this) / 2);
        boolean first = true;
        for (LivingEntity e : playerFoes(max)) {
            Telegraph.Shape s = Telegraph.Shape.follow(e, 1.8F).warmup(30).lock(8).color(0x6A4A8A);
            if (first) s.castName(attackName("abyss_chains")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW);
            first = false;
            strike(s, foes -> {
                for (LivingEntity f : foes) {
                    if (f instanceof Player) chainUp(sl, f);
                }
            });
        }
    }

    private void chainUp(ServerLevel sl, LivingEntity victim) {
        if (!sl.getEntitiesOfClass(PrimordialChain.class, victim.getBoundingBox().inflate(16), c -> c.victim() == victim).isEmpty()) return;
        Vec3 toC = homeCenter().subtract(victim.position()).multiply(1, 0, 1);
        Vec3 dir = toC.lengthSqr() < 1 ? Vec3.directionFromRotation(0, random.nextFloat() * 360F) : toC.normalize();
        Vec3 spot = victim.position().add(dir.scale(3.0));
        Vec3 g = groundAt(spot.x, victim.getY() + 3, spot.z, 4, 10);
        if (g == null) g = victim.position();
        PrimordialChain c = BeRegistries.PRIMORDIAL_CHAIN.get().create(sl);
        if (c == null) return;
        c.moveTo(g.x, g.y, g.z, random.nextFloat() * 360F, 0F);
        c.setup(this, victim, 1F + 0.5F * (BossKit.partySize(this) - 1));
        sl.addFreshEntity(c);
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.defaultBlockState()), g.x, g.y + 0.5, g.z, 30, 0.6, 0.4,
                0.6, 0.2);
        DragonFx.line(sl, DragonFx.dust(0x9A4FD8, 1.2F), g.add(0, 2.2, 0), victim.position().add(0, 1, 0), 0.5);
        DragonFx.sound(sl, g, SoundEvents.ANVIL_PLACE, 1.2F, 0.5F);
        if (victim instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(HINT + "chained").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /**
     * Marca do Sacrifício: um jogador é marcado por 4,5 s. O golpe é DIVIDIDO entre todos dentro do círculo — com
     * menos de 3 (ou do grupo todo, se for menor) o marcado vira estátua e ganha Ruína. Contrariável.
     */
    private void sacrificeMark(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 4) return;
        List<LivingEntity> ps = playerFoes(8);
        if (ps.isEmpty()) return;
        LivingEntity marked = ps.get(0);
        int need = Math.min(3, ps.size());
        int[] id = new int[1];
        id[0] = strike(Telegraph.Shape.follow(marked, 4F).warmup(90).lock(14).color(0xC0182E).castName(attackName("sacrifice_mark"))
                .tier(SpellStack.Tier.MAJOR).element(Element.SHADOW), foes -> sacrificeResolve(sl, marked, need, Telegraph.centerOf(id[0]), foes));
        Announce.send(this, Component.translatable(ANNOUNCE + "sacrifice", marked.getDisplayName()), 0xC0182E, Announce.STYLE_SPELL);
        for (ServerPlayer p : BossKit.partyAround(this, BAR_RANGE)) {
            p.displayClientMessage(Component.translatable(HINT + "sacrifice", marked.getDisplayName(), need).withStyle(ChatFormatting.RED), true);
        }
        DragonFx.sound(sl, marked.position(), SoundEvents.WITHER_SPAWN, 1.0F, 1.4F);
    }

    private void sacrificeResolve(ServerLevel sl, LivingEntity marked, int need, @Nullable Vec3 at, List<LivingEntity> foes) {
        Vec3 c = at != null ? at : marked.position();
        int n = Math.max(1, foes.size());
        float each = spellPower() * 2.4F / n;
        DragonFx.column(sl, DragonFx.dust(0xC0182E, 2.6F), c, 16, 14, 0.4);
        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y + 1, c.z, 20, 1.5, 0.5, 1.5, 0.2);
        DragonFx.sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 0.7F);
        for (LivingEntity e : foes) {
            e.hurt(spellSource(null), each);
            if (e != marked) DragonFx.line(sl, DragonFx.dust(0xFF6A6A, 1.0F), e.position().add(0, 1, 0), marked.position().add(0, 1, 0), 0.6);
        }
        if (foes.size() < need && marked.isAlive()) {
            DragonCurses.statue(marked, 160, this);
            DragonCurses.ruin(marked, 1, 6000, this);
            for (LivingEntity e : foes) Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 120, this, 1F);
            hint("sacrifice_failed");
        } else {
            hint("sacrifice_shared");
        }
    }

    /**
     * Olhar da Górgona Primordial: 3,5 s encarando; quem estiver OLHANDO para a cabeça do dragão (com linha de visão)
     * no fim vira ESTÁTUA por 8 s. Quem virou de costas só enrijece. Contrariável (Maior).
     */
    private void gorgonGaze(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            lockFacing(target);
            Vec3 g = arenaGround();
            strike(Telegraph.Shape.circle(g, (float) arenaRadius + 4F).warmup(70).vertical(2, 30).color(0x9A948A).noRunes()
                    .castName(attackName("gorgon_gaze")).tier(SpellStack.Tier.MAJOR).element(Element.EARTH), this::gazeResolve);
            announce("gaze", Announce.STYLE_SPELL);
            hint("gaze");
            DragonFx.sound(sl, position(), SoundEvents.ELDER_GUARDIAN_CURSE, 3F, 0.5F);
        }
        holdFacing();
        if (t < 70 && t % 2 == 0) {
            Vec3 eye = headPos();
            sl.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 2, 0.4, 0.2, 0.4, 0.01);
            sl.sendParticles(DragonFx.dust(0xFFF2B0, 2.0F), eye.x, eye.y, eye.z, 3, 0.6, 0.3, 0.6, 0);
            if (t % 20 == 0) DragonFx.sound(sl, eye, SoundEvents.BEACON_AMBIENT, 2F, 1.5F - t / 100F);
        }
    }

    private void gazeResolve(List<LivingEntity> foes) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 eye = headPos();
        sl.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 2, 0, 0, 0, 0);
        DragonFx.sound(sl, eye, SoundEvents.ZOMBIE_VILLAGER_CURE, 2.5F, 0.5F);
        for (LivingEntity e : foes) {
            if (!(e instanceof Player)) {
                Statuses.apply(e, Statuses.Kind.PETRIFIED, 2, 100, this, 1F);
                continue;
            }
            Vec3 toEye = eye.subtract(e.getEyePosition()).normalize();
            boolean looking = e.getViewVector(1F).dot(toEye) > 0.35 && e.hasLineOfSight(this);
            if (looking) {
                DragonFx.line(sl, DragonFx.dust(0xFFF2B0, 1.3F), eye, e.getEyePosition(), 0.6);
                DragonCurses.statue(e, 160, this);
            } else {
                Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 100, this, 1F);
                if (e instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(HINT + "gaze_dodged").withStyle(ChatFormatting.AQUA), true);
                }
            }
        }
    }

    /**
     * Inversão do Destino: os jogadores são marcados em PARES e trocam de lugar (estátuas e acorrentados também!); quem
     * sobra é jogado para um canto da arena. Desorienta (Náusea) e expõe. Contrariável.
     */
    private void fateSwap(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) DragonFx.sound(sl, position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 3F, 0.6F);
        if (t != 6) return;
        List<LivingEntity> ps = new ArrayList<>();
        for (LivingEntity e : enemiesNear(homeCenter(), arenaRadius + 14)) if (e instanceof Player) ps.add(e);
        if (ps.isEmpty()) return;
        Collections.shuffle(ps, new Random(random.nextLong()));
        boolean first = true;
        for (int i = 0; i < ps.size() && i < 8; i += 2) {
            LivingEntity a = ps.get(i);
            LivingEntity b = i + 1 < ps.size() ? ps.get(i + 1) : null;
            Telegraph.Shape s = Telegraph.Shape.follow(a, 1.6F).warmup(36).color(Telegraph.VOID);
            if (first) s.castName(attackName("fate_swap")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW);
            first = false;
            strike(s, foes -> swapResolve(sl, a, b));
            if (b != null) {
                strike(Telegraph.Shape.follow(b, 1.6F).warmup(36).color(Telegraph.VOID), foes -> {});
                DragonFx.line(sl, DragonFx.dust(0x9B4DFF, 1.0F), a.position().add(0, 1, 0), b.position().add(0, 1, 0), 0.8);
            }
        }
        hint("swap");
    }

    private void swapResolve(ServerLevel sl, LivingEntity a, @Nullable LivingEntity b) {
        if (!a.isAlive() || a.level() != level()) return;
        Vec3 pa = a.position();
        Vec3 pb;
        if (b != null && b.isAlive() && b.level() == level()) {
            pb = b.position();
            relocate(sl, b, pa);
        } else {
            Vec3 far = randomArenaPoint(arenaRadius - 3);
            if (far == null) return;
            pb = far.add(0, 0.1, 0);
        }
        relocate(sl, a, pb);
        DragonFx.sound(sl, pa, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.6F);
        DragonFx.sound(sl, pb, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.6F);
    }

    private void relocate(ServerLevel sl, LivingEntity e, Vec3 to) {
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
        e.teleportTo(to.x, to.y, to.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
        e.hurtMarked = true;
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), this);
        Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 100, this, 1F);
        sl.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 40, 0.4, 0.8, 0.4, 0.3);
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(HINT + "swapped").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * Sifão Primordial: a arena é marcada (contrariável); depois, por 4,5 s, o dragão SUGA todos que ele enxerga:
     * 2% da vida (até 10%) e 4% do recurso a cada meio segundo, e acumula Essência Drenada (−8% de poder por acúmulo).
     * Esconder-se atrás de pilares corta o fio; causar 3% da vida dele durante o canal ROMPE o sifão.
     */
    private void lifeSiphon(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            siphonUntil = -1;
            siphonDamage = 0;
            strike(Telegraph.Shape.circle(arenaGround(), (float) arenaRadius + 4F).warmup(30).vertical(2, 24).color(0xC0182E).noRunes()
                    .castName(attackName("life_siphon")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW), foes -> siphonUntil = attackTick + 90);
            announce("siphon", Announce.STYLE_SPELL);
            hint("siphon");
        }
        Vec3 m = mouthPos();
        if (t < 30) {
            if (t % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = m.add(Math.cos(a) * 8, random.nextDouble() * 4 - 2, Math.sin(a) * 8);
                    Vec3 v = m.subtract(from).scale(0.08);
                    sl.sendParticles(DragonFx.dust(0xC0182E, 1.4F), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
                }
            }
            return;
        }
        if (siphonUntil < 0 || attackTick >= siphonUntil) return;
        if (t % 10 != 0) return;
        int drained = 0;
        boolean essence = (t / 10) % 2 == 0;
        for (LivingEntity e : enemiesNear(homeCenter(), arenaRadius + 12)) {
            if (!(e instanceof Player) && !(e instanceof OwnableEntity)) continue;
            if (!hasLineOfSight(e)) continue;
            float took = DragonCurses.drainHealth(e, 0.02F, 0.10F, this);
            if (e instanceof ServerPlayer sp) DragonCurses.drainResource(sp, 0.04F);
            if (essence) DragonCurses.drainEssence(e, 1, 800, this);
            if (took > 0) drained++;
            DragonFx.line(sl, DragonFx.dust(0xC0182E, 1.3F), e.position().add(0, 1, 0), m, 0.7);
        }
        if (drained > 0) healEffectiveFraction(0.0015F * drained);
        DragonFx.sound(sl, m, SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.6F);
    }

    /** Dano suficiente durante o canal: o Sifão rompe e o dragão fica exausto um instante. */
    private void breakSiphon(ServerLevel sl) {
        siphonUntil = -1;
        siphonDamage = 0;
        Vec3 m = mouthPos();
        sl.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 3, 0.5, 0.5, 0.5, 0);
        sl.sendParticles(DragonFx.dust(0xC0182E, 2.5F), m.x, m.y, m.z, 40, 1.5, 1, 1.5, 0.1);
        DragonFx.sound(sl, m, SoundEvents.GLASS_BREAK, 3F, 0.5F);
        announce("siphon_broken", Announce.STYLE_BOSS);
        exhaust(50);
        endAttack();
    }

    /** Selo do Esquecimento: até 3 marcas que travam; quem ficar dentro tem o LOADOUT ATIVO apagado por 20 s. */
    private void oblivionSeal(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t != 4) return;
        int max = Math.min(3, Math.max(1, (BossKit.partySize(this) + 1) / 2));
        boolean first = true;
        for (LivingEntity e : playerFoes(max)) {
            Telegraph.Shape s = Telegraph.Shape.follow(e, 2.5F).warmup(40).lock(12).color(0x5A1E7A);
            if (first) s.castName(attackName("oblivion_seal")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW);
            first = false;
            int[] id = new int[1];
            id[0] = strike(s, foes -> {
                Vec3 c = Telegraph.centerOf(id[0]);
                if (c != null) {
                    DragonFx.ring(sl, ParticleTypes.ENCHANT, c.add(0, 0.3, 0), 2.5, 24);
                    DragonFx.column(sl, DragonFx.dust(0x5A1E7A, 2.2F), c, 12, 8, 0.3);
                }
                for (LivingEntity f : foes) {
                    if (f instanceof ServerPlayer sp) DragonCurses.eraseLoadout(sp, 400, this);
                    else f.addEffect(new MobEffectInstance(CoreRegistries.SILENCED, 100, 0), this);
                }
            });
        }
        hint("seal");
        DragonFx.sound(sl, position(), SoundEvents.ILLUSIONER_CAST_SPELL, 3F, 0.5F);
    }

    /** Maldição do Esquecimento: marca TODOS; quem não sair a tempo esquece 35% das magias por 25 s. */
    private void forgetCurse(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        if (t == 0) {
            announce("curse", Announce.STYLE_SPELL);
            DragonFx.sound(sl, position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 3F, 0.5F);
        }
        if (t != 4) return;
        boolean first = true;
        for (LivingEntity e : playerFoes(8)) {
            Telegraph.Shape s = Telegraph.Shape.follow(e, 2.2F).warmup(34).lock(10).color(0x3A2A5A);
            if (first) s.castName(attackName("forget_curse")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW);
            first = false;
            strike(s, foes -> {
                for (LivingEntity f : foes) {
                    if (f instanceof ServerPlayer sp) DragonCurses.curse(sp, 500, this);
                }
            });
        }
        hint("curse");
    }

    // ---------------- Supremas ----------------

    /** Começo comum das Supremas: anúncio, cutscene, aviso de contramágica e a recarga compartilhada. */
    private void beginUltimate(String id, int rgb) {
        ultFired = false;
        entityData.set(DATA_CHARGING, true);
        Component name = attackName(id);
        Announce.send(this, name, rgb, Announce.STYLE_ULTIMATE);
        Cutscenes.playNear(this, BAR_RANGE, "ultimate", name, title(id + "_subtitle"), rgb, 60, null);
        hint("ultimate");
        long until = level().getGameTime() + ULT_SHARED_CD;
        for (String u : ULTIMATES) readyAt.put(u, Math.max(readyAt.getOrDefault(u, 0L), until));
    }

    /** Fim comum: contrariada → ele despenca exausto; senão, só uma pausa curta. */
    private void finishUltimate(int exhaustedTicks) {
        entityData.set(DATA_CHARGING, false);
        if (!ultFired) {
            announce("ult_countered", Announce.STYLE_BOSS);
            exhaust(exhaustedTicks + 40);
        } else {
            globalCooldown = 30;
        }
        endAttack();
    }

    /** Carga visual das Supremas (espiral de cores convergindo no peito). */
    private void chargeFx(ServerLevel sl, int t, int[] colors) {
        double prog = (t - 20) / 120.0;
        Vec3 core = position().add(0, 2.5, 0);
        int pts = DragonFx.reduced() ? 2 : 5;
        for (int i = 0; i < pts; i++) {
            double a = t * 0.3 + i * Math.PI * 2 / pts;
            double r = 10 * (1 - prog) + 1.5;
            sl.sendParticles(DragonFx.dust(colors[i % colors.length], 2.5F), core.x + Math.cos(a) * r, core.y + Math.sin(a * 0.5) * 2,
                    core.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (t % 20 == 0) DragonFx.sound(sl, core, SoundEvents.BEACON_AMBIENT, 3F, 0.5F + (float) prog);
        if (t % 30 == 0) BossKit.shake(sl, homeCenter(), 48, 0.4F + (float) prog, 20);
    }

    /**
     * CREPÚSCULO DE PEDRA (Suprema, fases 3–4): 6 s de carga com a arena inteira marcada. Se não for CONTRARIADA, TODOS
     * na arena viram estátua por 10 s (Domínio Simples: 4 s) — drenados enquanto o dragão continua batendo.
     */
    private void stoneTwilight(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = arenaGround();
        if (t == 0) beginUltimate("stone_twilight", 0x9A948A);
        if (t < 20) {
            hoverToward(c.add(0, 12, 0), 1.4);
            return;
        }
        stopMoving();
        if (t == 20) {
            strike(Telegraph.Shape.circle(c, (float) arenaRadius + 4F).warmup(120).color(0x9A948A).vertical(8, 40)
                    .castName(attackName("stone_twilight")).tier(SpellStack.Tier.ULTIMATE).element(Element.EARTH), this::stoneTwilightBlast);
        }
        if (t > 20 && t < 140) chargeFx(sl, t, new int[]{0x9A948A, 0xD8C48A, 0x5A5048, 0xFFE6A0, 0x8A4FC8});
        if (t == 143) finishUltimate(80);
    }

    private void stoneTwilightBlast(List<LivingEntity> foes) {
        if (!(level() instanceof ServerLevel sl)) return;
        ultFired = true;
        entityData.set(DATA_CHARGING, false);
        Vec3 c = arenaGround();
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 3, c.z, 4, 6, 2, 6, 0);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            DragonFx.column(sl, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                    c.add(Math.cos(a) * arenaRadius * 0.6, 0, Math.sin(a) * arenaRadius * 0.6), 10, 12, 0.4);
        }
        DragonFx.sound(sl, c, SoundEvents.DEEPSLATE_BRICKS_PLACE, 4F, 0.4F);
        DragonFx.sound(sl, c, SoundEvents.ELDER_GUARDIAN_CURSE, 4F, 0.5F);
        BossKit.shake(sl, c, 64, 2.4F, 30);
        for (LivingEntity e : foes) {
            boolean shielded = simpleShielded(sl, e);
            DragonCurses.statue(e, shielded ? 80 : 200, this);
            if (shielded && e instanceof ServerPlayer p) {
                p.displayClientMessage(Component.translatable(HINT + "simple_halved").withStyle(ChatFormatting.AQUA), true);
            }
        }
        hint("statues");
    }

    /**
     * MARÉ DO ESQUECIMENTO (Suprema, fases 4–5): se não for CONTRARIADA, todos na arena têm o loadout ativo APAGADO por
     * 30 s, ganham a Maldição do Esquecimento (30 s), perdem TODO o recurso e ficam Silenciados 3 s (Domínio Simples
     * segura o Silêncio e o dreno, não o esquecimento).
     */
    private void oblivionTide(LivingEntity target, int t) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = arenaGround();
        if (t == 0) beginUltimate("oblivion_tide", 0x5A1E7A);
        if (t < 20) {
            hoverToward(c.add(0, 12, 0), 1.4);
            return;
        }
        stopMoving();
        if (t == 20) {
            strike(Telegraph.Shape.circle(c, (float) arenaRadius + 4F).warmup(120).color(0x5A1E7A).vertical(8, 40)
                    .castName(attackName("oblivion_tide")).tier(SpellStack.Tier.ULTIMATE).element(Element.SHADOW), this::oblivionTideBlast);
        }
        if (t > 20 && t < 140) {
            chargeFx(sl, t, new int[]{0x5A1E7A, 0x3A2A5A, 0xB070E0, 0x1A1424});
            if (t % 4 == 0) {
                Vec3 m = mouthPos();
                sl.sendParticles(ParticleTypes.ENCHANT, m.x, m.y, m.z, 20, 4, 2, 4, 1.5);
            }
        }
        if (t == 143) finishUltimate(80);
    }

    private void oblivionTideBlast(List<LivingEntity> foes) {
        if (!(level() instanceof ServerLevel sl)) return;
        ultFired = true;
        entityData.set(DATA_CHARGING, false);
        Vec3 c = arenaGround();
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 3, c.z, 3, 6, 2, 6, 0);
        DragonFx.ring(sl, ParticleTypes.SCULK_SOUL, c.add(0, 0.5, 0), arenaRadius * 0.5, 64);
        DragonFx.ring(sl, ParticleTypes.ENCHANT, c.add(0, 1.0, 0), arenaRadius * 0.8, 96);
        DragonFx.sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 4F, 0.4F);
        DragonFx.sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 4F, 0.5F);
        BossKit.shake(sl, c, 64, 2.0F, 30);
        for (LivingEntity e : foes) {
            boolean shielded = simpleShielded(sl, e);
            if (e instanceof ServerPlayer sp) {
                DragonCurses.eraseLoadout(sp, 600, this);
                DragonCurses.curse(sp, 600, this);
                if (!shielded) DragonCurses.drainResource(sp, 1.0F);
            }
            if (!shielded) e.addEffect(new MobEffectInstance(CoreRegistries.SILENCED, 60, 0), this);
        }
        hint("oblivion_hit");
    }

    // =====================================================================================
    //  Fases
    // =====================================================================================

    private boolean checkPhase(ServerLevel sl) {
        int ph = entityData.get(DATA_PHASE);
        if (ph >= DragonPhase.COUNT) return false;
        if (getHealth() > THRESHOLDS[ph - 1] * getMaxHealth() + 0.01F) return false;
        startTransition(sl, ph + 1);
        return true;
    }

    private void startTransition(ServerLevel sl, int next) {
        if (current != null) cancelAttack();
        Telegraph.cancelAll(this);
        hazards.clear();
        entityData.set(DATA_CHARGING, false);
        entityData.set(DATA_EXHAUSTED, 0);
        entityData.set(DATA_GROUNDED, false);
        manualVelocity = false;
        // A arena da fase anterior volta numa onda e a nova cresce do centro.
        ArenaTerraform.get(sl).releaseOwner(sl, getUUID(), homeCenter(), 1.0);
        entityData.set(DATA_PHASE, next);
        setState(ST_TRANSITION);
        lastBarName = "";
        if (bar != null) bar.setColor(phase().bar);
        triggerAnim("action", "phase");
        Component t = Component.translatable("boss.isekai_mahou.primordial_dragon.phase_title", next, phase().displayName());
        Cutscenes.playNear(this, BAR_RANGE, "boss_phase", t, title("phase" + next), phase().rgb, 70, null);
        announce("phase" + next, Announce.STYLE_BOSS);
        Vec3 g = arenaGround();
        warn(Telegraph.Shape.circle(g, 9F).warmup(TRANSITION_TICKS - 10).color(phase().rgb));
        BossKit.shake(sl, position(), 48, 1.6F, 30);
    }

    private void tickTransition(ServerLevel sl) {
        int t = stateTicks;
        Vec3 g = arenaGround();
        hoverToward(g.add(0, 9, 0), 1.2);
        if (t % 2 == 0) {
            double a = t * 0.25;
            for (int i = 0; i < 3; i++) {
                double aa = a + i * Math.PI * 2 / 3;
                sl.sendParticles(DragonFx.dust(phase().rgb, 2.5F), getX() + Math.cos(aa) * 5, getY() + 1 + t * 0.03, getZ() + Math.sin(aa) * 5,
                        2, 0.1, 0.1, 0.1, 0);
            }
        }
        if (t == 25) beginTheme(sl);
        if (t == 60) BossKit.roar(this, SoundEvents.ENDER_DRAGON_GROWL, 0.55F + 0.05F * phase().number());
        if (t == TRANSITION_TICKS - 10) {
            for (LivingEntity e : foesIn(Telegraph.Shape.circle(g, 9F))) {
                e.hurt(spellSource(null), spellPower() * 0.3F);
                knockFrom(e, g, 1.6, 0.6);
            }
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
        }
        if (t >= TRANSITION_TICKS) {
            setState(ST_FIGHT);
            modeTimer = airTime();
            globalCooldown = 10;
            String opener = switch (phase()) {
                case WATER -> "tsunami";
                case EARTH -> "crater_slam";
                case STORM -> "lightning_storm";
                case TWILIGHT -> "domain";
                default -> null;
            };
            LivingEntity target = getTarget();
            if (opener != null && target != null) startAttack(attack(opener));
        }
    }

    /** Quem pisa no magma temporário do dragão queima (o "chão de lava"). */
    private void groundHazards(ServerLevel sl) {
        ArenaTerraform tf = ArenaTerraform.peek(sl);
        if (tf == null || tf.size() == 0) return;
        for (LivingEntity e : enemiesNear(homeCenter(), arenaRadius + 12)) {
            BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
            if (!e.onGround() || !tf.ownedBy(below, getUUID()) || !sl.getBlockState(below).is(Blocks.MAGMA_BLOCK)) continue;
            Statuses.apply(e, Statuses.Kind.BURNING, 1, 80, this, 1F);
            e.hurt(damageSources().hotFloor(), attackPower() * 0.12F);
            sl.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + 0.2, e.getZ(), 2, 0.3, 0.1, 0.3, 0);
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
        Telegraph.cancelAll(this);
        hazards.clear();
        closeDomain(sl);
        discardShades(sl);
        entityData.set(DATA_CHARGING, false);
        entityData.set(DATA_EXHAUSTED, 0);
        ACTIVE.remove(this);
        discardChains(sl);
        DragonCurses.releaseFrom(sl.getServer(), getUUID());
        trackParticipants(sl);
        List<ServerPlayer> rewarded = new ArrayList<>(BossRewards.grant(this, REWARD_XP, BAR_RANGE));
        // O contador boss.primordial_dragon (que libera o nível 150→200) vai para TODOS que lutaram — inclusive quem
        // caiu e renasceu longe da arena há mais de 30 s.
        Set<UUID> paid = new HashSet<>();
        for (ServerPlayer p : rewarded) paid.add(p.getUUID());
        for (UUID id : participants) {
            if (paid.contains(id)) continue;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            MagicApi.giveXp(p, REWARD_XP, "boss");
            MagicApi.addCounter(p, Progression.HIDDEN_CAP_COUNTER, 1);
            rewarded.add(p);
        }
        for (ServerPlayer p : rewarded) grantPersonal(p);
        announce("defeated", Announce.STYLE_BOSS);
        Cutscenes.playNear(this, BAR_RANGE, "ascension", title("final_title"), title("final_subtitle"), TWILIGHT_RGB, 180, null);
        if (bar != null) bar.removeAllPlayers();
        BossKit.shake(sl, position(), 64, 2.0F, 60);
        DragonFx.sound(sl, position(), SoundEvents.ENDER_DRAGON_DEATH, 4F, 0.7F);
    }

    /**
     * Onde o saque cai: no chão sob o corpo, ou no centro da arena se ele morreu sobre o vazio (a ilha da Fenda) —
     * a estrela do Nether não pode sumir no abismo.
     */
    private Vec3 safeDropPos() {
        Vec3 g = groundAt(getX(), getY() + 1, getZ(), 0, 64);
        if (g == null) g = arenaGround();
        return g.add(0, 0.6, 0);
    }

    @Nullable
    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        if (stack.isEmpty() || level().isClientSide) return null;
        Vec3 p = safeDropPos();
        ItemEntity item = new ItemEntity(level(), p.x, p.y + Math.max(0F, offsetY), p.z, stack);
        item.setDefaultPickUpDelay();
        item.setExtendedLifetime();
        if (captureDrops() != null) captureDrops().add(item);
        else level().addFreshEntity(item);
        return item;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        if (!(level() instanceof ServerLevel sl) || wasExperienceConsumed()) return;
        if (!isAlwaysExperienceDropper() && !(lastHurtByPlayerTime > 0 && sl.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) return;
        int reward = EventHooks.getExperienceDrop(this, lastHurtByPlayer, getExperienceReward(sl, killer));
        ExperienceOrb.award(sl, safeDropPos(), reward);
    }

    /** Recompensas de cada um que lutou (além do XP e do contador boss.primordial_dragon do BossRewards). */
    private void grantPersonal(ServerPlayer p) {
        ItemStack heart = new ItemStack(BeRegistries.PRIMORDIAL_DRAGON_HEART.get());
        Rewards.give(p, heart);
        ItemStack legend = DragonLegends.scroll();
        if (!legend.isEmpty()) Rewards.give(p, legend);
        ItemStack extra = Legends.randomScroll(p.getRandom(), MagicApi.get(p).circle());
        if (!extra.isEmpty()) Rewards.give(p, extra);
        boolean first = !Reputation.hasTitle(p, TITLE_KEY);
        Reputation.grantTitle(p, TITLE_KEY);
        if (first) Reputation.selectTitle(p, TITLE_KEY);
        MagicApi.addCounter(p, "feat.dragon_slayer", 1);
        Rewards.banner(p, Component.translatable("banner.isekai_mahou.primordial_dragon.slayer"), 0xFFB347);
        Rewards.toast(p, Component.translatable(TITLE_KEY), Component.translatable("toast.isekai_mahou.primordial_dragon.title"), heart, 0xFFB347);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        int t = deathTime;
        if (deathSpot == null) {
            Vec3 g = groundAt(getX(), getY(), getZ(), 1, 48);
            deathSpot = g != null ? g : arenaGround();
        }
        if (t < 60) {
            if (t % 8 == 0) {
                int rgb = switch ((t / 8) % 5) {
                    case 0 -> DragonFx.FIRE;
                    case 1 -> DragonFx.ICE;
                    case 2 -> DragonFx.AMBER;
                    case 3 -> DragonFx.STORM;
                    default -> DragonFx.TWILIGHT;
                };
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 b = position().add(Math.cos(a) * 2.5, 2, Math.sin(a) * 2.5);
                DragonFx.column(sl, DragonFx.dust(rgb, 3F), b, 16, 20, 0.2);
                DragonFx.sound(sl, b, SoundEvents.ENDER_DRAGON_HURT, 2F, 0.5F + t / 120F);
            }
        } else if (!crashed) {
            Vec3 fall = deathSpot.subtract(position());
            double dist = fall.length();
            if (dist > 0.3 && t < 150) {
                move(MoverType.SELF, fall.scale(Math.min(dist, 0.25 + (t - 60) * 0.03) / dist));
            } else {
                crashed = true;
                Vec3 c = position();
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 2, 2, 0.2, 2, 0);
                landingDust(sl, c, 10);
                BossKit.shake(sl, c, 64, 3.0F, 30);
                DragonFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 4F, 0.4F);
                DragonFx.sound(sl, c, SoundEvents.ANVIL_LAND, 3F, 0.3F);
            }
        }
        setDeltaMovement(Vec3.ZERO);
        if (t >= 120 && t < 190 && t % 3 == 0) {
            // Os cinco elementos deixam o corpo em espirais.
            for (int i = 0; i < 5; i++) {
                double a = t * 0.2 + i * Math.PI * 2 / 5;
                double r = 2 + (t - 120) * 0.05;
                int rgb = new int[]{DragonFx.FIRE, DragonFx.WATER, DragonFx.EARTH, DragonFx.STORM, DragonFx.TWILIGHT}[i];
                sl.sendParticles(DragonFx.dust(rgb, 2.6F), getX() + Math.cos(a) * r, getY() + 1 + (t - 120) * 0.2, getZ() + Math.sin(a) * r,
                        2, 0.1, 0.1, 0.1, 0);
            }
            if (t % 15 == 0) DragonFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2F, 0.5F + (t - 120) / 100F);
        }
        if (t >= DEATH_TICKS && !isRemoved()) {
            Vec3 c = position().add(0, 2, 0);
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 4, 1, 1, 1, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 150, 3, 2, 3, 0.4);
            sl.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 100, 2, 2, 2, 0.4);
            DragonFx.sound(sl, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 3F, 1.0F);
            for (ServerPlayer p : sl.getPlayers(p -> p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE)) {
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.primordial_dragon.victory").withStyle(s -> s.withColor(0xFFB347)));
            }
            ArenaTerraform.get(sl).releaseOwner(sl, getUUID(), homeCenter(), 0.8);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        ACTIVE.remove(this);
        if (bar != null) bar.removeAllPlayers();
        hazards.clear();
        // O domínio próprio não é salvo: some também quando o chunk descarrega (senão o núcleo guardaria um domínio órfão).
        if (level() instanceof ServerLevel sl) closeDomain(sl);
        if (reason.shouldDestroy() && level() instanceof ServerLevel sl) {
            discardShades(sl);
            discardChains(sl);
            DragonCurses.releaseFrom(sl.getServer(), getUUID());
            if (!isDeadOrDying()) ArenaTerraform.get(sl).releaseOwner(sl, getUUID(), homeCenter(), 2.0);
        }
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================

    private int warn(Telegraph.Shape s) {
        return level() instanceof ServerLevel sl ? Telegraph.show(sl, s.owner(this)) : -1;
    }

    /** Golpe atrasado com dono (entra na Pilha); o callback recebe só os ADVERSÁRIOS dentro. */
    private int strike(Telegraph.Shape s, Consumer<List<LivingEntity>> onFoes) {
        if (!(level() instanceof ServerLevel sl)) return -1;
        return Telegraph.delayed(sl, s.owner(this), hits -> {
            if (!isAlive()) return;
            hits.removeIf(e -> !isFoe(e));
            onFoes.accept(hits);
        });
    }

    private List<LivingEntity> foesIn(Telegraph.Shape s) {
        if (!(level() instanceof ServerLevel sl)) return List.of();
        List<LivingEntity> list = new ArrayList<>(Telegraph.entitiesIn(sl, s));
        list.removeIf(e -> !isFoe(e));
        return list;
    }

    /** Até {@code max} adversários na arena (o alvo primeiro). */
    private List<LivingEntity> arenaFoes(int max) {
        List<LivingEntity> out = new ArrayList<>();
        LivingEntity t = getTarget();
        if (t != null && isFoe(t)) out.add(t);
        for (LivingEntity e : enemiesNear(homeCenter(), arenaRadius + 14)) {
            if (out.size() >= max) break;
            if (!out.contains(e) && (e instanceof Player || e == getLastHurtByMob())) out.add(e);
        }
        return out;
    }

    @Nullable
    private Vec3 randomArenaPoint(double radius) {
        Vec3 c = homeCenter();
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * radius;
            Vec3 g = groundAt(c.x + Math.cos(a) * r, c.y + 2, c.z + Math.sin(a) * r, 6, 10);
            if (g != null) return g;
        }
        return null;
    }

    private Vec3 arenaGround() {
        Vec3 c = homeCenter();
        Vec3 g = groundAt(c.x, c.y + 2, c.z, 6, 12);
        return g != null ? g : c;
    }

    private Vec3 clampToArena(Vec3 p) {
        Vec3 c = homeCenter();
        Vec3 d = p.subtract(c).multiply(1, 0, 1);
        double max = arenaRadius + 2;
        return d.length() > max ? c.add(d.normalize().scale(max)).add(0, p.y - c.y, 0) : p;
    }

    /** Boca do dragão (o modelo é desenhado a 1/2,5 e aumentado: ~8 blocos à frente, ~3,8 de altura). */
    public Vec3 mouthPos() {
        return position().add(Vec3.directionFromRotation(0, getYRot()).scale(7.5)).add(0, 3.6, 0);
    }

    private void landingDust(ServerLevel sl, Vec3 c, double r) {
        BlockState below = sl.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (below.isAir()) below = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), c.x + Math.cos(a) * r * 0.6, c.y + 0.3, c.z + Math.sin(a) * r * 0.6,
                    3, 0.5, 0.2, 0.5, 0.15);
        }
        DragonFx.ring(sl, ParticleTypes.CLOUD, c.add(0, 0.3, 0), r, 32);
    }

    private void knockFrom(LivingEntity e, Vec3 from, double power, double up) {
        Vec3 push = e.position().subtract(from).multiply(1, 0, 1);
        push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
        e.setDeltaMovement(push.x * power, up, push.z * power);
        e.hurtMarked = true;
    }

    /** Condição de status do elemento da fase (força 1 ou 2). */
    public void applyPhaseStatus(LivingEntity e, int strength) {
        switch (phase()) {
            case FIRE -> Statuses.apply(e, Statuses.Kind.BURNING, strength, 100, this, 1F);
            case WATER -> {
                if (Statuses.has(e, Statuses.Kind.WET) && strength >= 2) {
                    Statuses.clear(e, Statuses.Kind.WET);
                    Statuses.apply(e, Statuses.Kind.FROZEN, 1, 50, this, 1F);
                } else {
                    Statuses.apply(e, Statuses.Kind.CHILLED, strength, 120, this, 1F);
                    if (strength >= 2) Statuses.apply(e, Statuses.Kind.WET, 1, 120, this, 1F);
                }
            }
            case EARTH -> Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 100, this, 1F);
            case STORM -> Statuses.apply(e, Statuses.Kind.SHOCKED, strength, 100, this, 1F);
            case TWILIGHT -> Statuses.apply(e, random.nextBoolean() ? Statuses.Kind.EXPOSED : Statuses.Kind.CORRUPTED, strength, 100, this, 1F);
        }
    }

    private void stopMoving() {
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
    }

    private void hoverToward(Vec3 pos, double speed) {
        getMoveControl().setWantedPosition(pos.x, pos.y, pos.z, speed);
    }

    private void faceToward(Vec3 p) {
        float yaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void lockFacing(LivingEntity t) {
        faceToward(t.position());
        lockedYaw = getYRot();
    }

    private void holdFacing() {
        setYRot(lockedYaw);
        yHeadRot = lockedYaw;
        yBodyRot = lockedYaw;
    }

    private Vec3 groundBelow() {
        Vec3 g = groundAt(getX(), getY() + 0.5, getZ(), 0, 32);
        return g != null ? g : position();
    }

    /** Chão sob um ponto (procura de {@code up} acima até {@code down} abaixo), ou null. */
    @Nullable
    public Vec3 groundAt(double x, double y, double z, double up, double down) {
        Vec3 from = new Vec3(x, y + up, z);
        HitResult hit = level().clip(new ClipContext(from, new Vec3(x, y - down, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    public float attackPower() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /**
     * Dano dos golpes MÁGICOS do dragão (tipo isekai_mahou:spell): o MobScaling da v1.0 não escala magia (ela já leva o
     * DAMAGE_SCALE nas magias de glifo), então aqui aplicamos a mesma inflação dos golpes físicos.
     */
    public float spellPower() {
        return attackPower() * Progression.MOB_DAMAGE_SCALE;
    }

    /** Cabeça (olhos) do dragão: um pouco acima e atrás da boca. */
    public Vec3 headPos() {
        return position().add(Vec3.directionFromRotation(0, getYRot()).scale(6.0)).add(0, 4.4, 0);
    }

    /** Jogadores (não-estátuas) na arena, em ordem aleatória, até {@code max}. */
    private List<LivingEntity> playerFoes(int max) {
        List<LivingEntity> all = new ArrayList<>();
        for (LivingEntity e : enemiesNear(homeCenter(), arenaRadius + 14)) {
            if (e instanceof Player && !DragonCurses.isStatue(e)) all.add(e);
        }
        Collections.shuffle(all, new Random(random.nextLong()));
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    /** Guarda quem está lutando (raio da arena + folga). */
    private void trackParticipants(ServerLevel sl) {
        Vec3 h = homeCenter();
        double r = arenaRadius + 24;
        for (ServerPlayer p : sl.players()) {
            if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(h) <= r * r) participants.add(p.getUUID());
        }
    }

    private void discardChains(ServerLevel sl) {
        for (PrimordialChain c : sl.getEntitiesOfClass(PrimordialChain.class, getBoundingBox().inflate(arenaRadius + 48), c -> c.ownedBy(this))) {
            c.discard();
        }
    }

    public DamageSource spellSource(@Nullable Entity direct) {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, direct != null ? direct : this, this);
    }

    public List<LivingEntity> enemiesNear(Vec3 c, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> isFoe(e) && e.distanceToSqr(c) <= r * r);
    }

    /** Jogadores (fora do criativo), bichos/invocações com dono e quem briga com ele; mobs neutros ficam de fora. */
    public boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand || isAlliedTo(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e == getTarget() || e == getLastHurtByMob()) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        return e instanceof Mob m && m.getTarget() == this;
    }

    private void announce(String key, int style) {
        Announce.send(this, Component.translatable(ANNOUNCE + key), phase().rgb, style);
    }

    private Component title(String key) {
        return Component.translatable(TITLE + key);
    }

    private Component attackName(String key) {
        return Component.translatable(ATTACK + key);
    }

    /** Dica na barra de ação (as primeiras vezes de cada mecânica importam mais; repete sem spam). */
    private void hint(String key) {
        for (ServerPlayer p : BossKit.partyAround(this, 64)) {
            p.displayClientMessage(Component.translatable(HINT + key).withStyle(ChatFormatting.YELLOW), true);
        }
        heardHints++;
    }

    // =====================================================================================
    //  Resgate do vazio (arena flutuante da Fenda)
    // =====================================================================================

    /**
     * Jogador caindo no vazio perto de um Dragão em luta: as correntes elementais o devolvem à arena (perde 20% da
     * vida). Cair da ilha não deve ser o jeito de perder para o chefe — o jeito é o chefe.
     */
    public static void rescueFromVoid(ServerPlayer p) {
        if (ACTIVE.isEmpty() || p.isCreative() || p.isSpectator()) return;
        for (PrimordialDragon d : ACTIVE) {
            if (d.isRemoved() || !d.isAlive() || d.level() != p.level() || !d.fighting()) continue;
            Vec3 h = d.homeCenter();
            double dx = p.getX() - h.x;
            double dz = p.getZ() - h.z;
            if (dx * dx + dz * dz > (d.arenaRadius + 40) * (d.arenaRadius + 40) || p.getY() > h.y - 18 || p.getDeltaMovement().y > -0.3) continue;
            if (d.groundAt(p.getX(), p.getY(), p.getZ(), 0, 40) != null) continue;
            Vec3 back = d.randomArenaPoint(Math.max(4, d.arenaRadius - 6));
            if (back == null) back = d.arenaGround();
            p.teleportTo(back.x, back.y + 1.5, back.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
            p.hurt(p.damageSources().magic(), p.getMaxHealth() * 0.2F);
            if (p.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, back.x, back.y + 1, back.z, 60, 0.6, 1.2, 0.6, 0.1);
            }
            p.displayClientMessage(Component.translatable(HINT + "rescued").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
    }

    public static void clearStatic() {
        ACTIVE.clear();
    }

    // =====================================================================================
    //  Cliente
    // =====================================================================================

    private void clientFx() {
        if (isInvisible()) return;
        boolean reduced = DragonFx.reduced();
        DragonPhase ph = phase();
        if (random.nextInt(reduced ? 4 : 2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2 + random.nextDouble() * 6;
            double x = getX() + Math.cos(a) * r;
            double z = getZ() + Math.sin(a) * r;
            double y = getY() + 2 + random.nextDouble() * 2.5;
            switch (ph) {
                case FIRE -> level().addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.03, 0);
                case WATER -> level().addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.02, 0);
                case EARTH -> level().addParticle(DragonFx.dust(DragonFx.EARTH, 1.6F), x, y, z, 0, -0.05, 0);
                case STORM -> level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
                case TWILIGHT -> level().addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, x, y, z, 0, 0.02, 0);
            }
        }
        Vec3 m = mouthPos();
        if (random.nextInt(4) == 0) {
            level().addParticle(ph == DragonPhase.FIRE ? ParticleTypes.SMOKE : DragonFx.dust(ph.rgb, 1.0F), m.x, m.y, m.z, 0, 0.03, 0);
        }
        if (isCharging()) {
            for (int i = 0; i < (reduced ? 1 : 3); i++) {
                Vec3 from = position().add((random.nextDouble() - 0.5) * 24, random.nextDouble() * 10, (random.nextDouble() - 0.5) * 24);
                Vec3 to = position().add(0, 2.5, 0);
                Vec3 v = to.subtract(from).scale(0.06);
                level().addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, from.x, from.y, from.z, v.x, v.y, v.z);
            }
        }
        if (ownDomainActive()) {
            Vec3 c = ownDomainCenter();
            float r = ownDomainRadius();
            int pts = reduced ? 8 : 20;
            for (int i = 0; i < pts; i++) {
                double u = random.nextDouble() * 2 - 1;
                double a = random.nextDouble() * Math.PI * 2;
                double s = Math.sqrt(1 - u * u);
                level().addParticle(DragonFx.dust(i % 2 == 0 ? DragonFx.LIGHT : DragonFx.SHADOW, 1.6F), c.x + s * Math.cos(a) * r,
                        c.y + Math.abs(u) * r, c.z + s * Math.sin(a) * r, 0, 0, 0);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    private static final String A = "animation.primordial_dragon.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop(A + "hover");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(A + "fly");
    private static final RawAnimation EXHAUSTED = RawAnimation.begin().thenLoop(A + "exhausted");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(A + "death");
    static final List<String> ACTIONS = List.of("intro", "phase", "roar", "bite", "claw", "tail_swipe", "breath", "dive", "land", "takeoff",
            "gust", "meteor_call", "tidal_roar", "quake_stomp", "storm_call", "summon", "domain", "ultimate", "gaze", "siphon",
            "chains", "curse", "swap");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PrimordialDragon> main = new AnimationController<>(this, "main", 6, this::mainState);
        AnimationController<PrimordialDragon> action = new AnimationController<>(this, "action", 4, s -> PlayState.STOP);
        for (String a : ACTIONS) action.triggerableAnim(a, RawAnimation.begin().thenPlay(A + a));
        controllers.add(main, action);
    }

    private PlayState mainState(AnimationState<PrimordialDragon> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        if (isExhausted()) return state.setAndContinue(EXHAUSTED);
        double dx = getX() - xo;
        double dz = getZ() - zo;
        boolean moving = dx * dx + dz * dz > 4.0E-3;
        if (isGrounded()) return state.setAndContinue(moving ? WALK : IDLE);
        return state.setAndContinue(moving ? FLY : HOVER);
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
    //  Voo
    // =====================================================================================

    /** Voo suave (acelera em direção ao ponto desejado). Encara o alvo entre golpes; durante golpes quem gira é o golpe. */
    static final class DragonFlight extends MoveControl {
        DragonFlight(PrimordialDragon mob) {
            super(mob);
        }

        @Override
        public void tick() {
            PrimordialDragon d = (PrimordialDragon) mob;
            if (d.manualVelocity || d.isDeadOrDying()) return;
            LivingEntity target = mob.getTarget();
            if (target != null && d.current == null && d.state() == ST_FIGHT && !d.isExhausted()) {
                float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 6F));
                mob.yBodyRot = mob.getYRot();
            }
            if (operation != Operation.MOVE_TO) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.85));
                return;
            }
            Vec3 dv = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double len = dv.length();
            if (len < 0.3 || speedModifier <= 0) {
                operation = Operation.WAIT;
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                return;
            }
            double sp = speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED) * 0.07;
            Vec3 acc = dv.scale(Math.min(1.0, len / 3.0) / len * sp);
            Vec3 v = mob.getDeltaMovement().add(acc.x, acc.y * 1.5, acc.z);
            mob.setDeltaMovement(v.x * 0.9, v.y * 0.8, v.z * 0.9);
            if ((target == null || d.state() != ST_FIGHT) && dv.horizontalDistanceSqr() > 0.05) {
                float yaw = (float) (Mth.atan2(dv.z, dv.x) * Mth.RAD_TO_DEG) - 90F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 5F));
                mob.yBodyRot = mob.getYRot();
            }
        }
    }
}
