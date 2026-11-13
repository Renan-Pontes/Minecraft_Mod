package com.renanpontes.isekaimahou.feature.domain.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.domain.DomainBarrier;
import com.renanpontes.isekaimahou.feature.domain.DomainFx;
import com.renanpontes.isekaimahou.feature.domain.DomainLandscape;
import com.renanpontes.isekaimahou.feature.domain.DomainRegistries;
import com.renanpontes.isekaimahou.feature.domain.UltimateCharge;
import com.renanpontes.isekaimahou.glyph.LawGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Domo de Domínio. Uma única entidade serve para:
 * <ul>
 *   <li><b>Expansão de Domínio</b>: domo fixo onde o conjurador o ergueu. A cada pulso aplica os efeitos da magia
 *       em todos os inimigos dentro (com Acerto Garantido) ou só nos que o conjurador enxerga (sem ele). As Leis
 *       mudam as regras lá dentro. Ao acabar, o dono recebe Exaustão Mágica.</li>
 *   <li><b>Domínio Simples</b> ({@link #F_SIMPLE}): domo pequeno que segue o conjurador. Anula o Acerto Garantido
 *       de domínios inimigos para ele e seus aliados, e golpeia (com os efeitos da magia) quem entrar.</li>
 * </ul>
 * v0.8 — <b>Barreira</b> (§11.1 do design): todo domínio FECHADO tem integridade (barra de chefe para quem está perto).
 * Barreira normal só quebra POR FORA (projéteis, explosões, magias e golpes de quem está fora acertando a parede);
 * invertida ({@link #F_INVERTED}) só POR DENTRO. Um domínio maior de outro dono que envolve este só ataca a barreira
 * por fora (cada pulso dele tira integridade); domínios fechados que se sobrepõem em parte disputam um cabo de guerra.
 * Zerou: despedaça e o dono fica exausto. O registro do núcleo ({@link Domains}) é avisado de tudo
 * (opened/ticked/closed), e as leis de outros módulos rodam por ele.
 * <p>
 * O visual é desenhado no cliente (renderer de esfera + névoa/tonalidade interna). Os dados de visual
 * (raio, cor, estado, leis, integridade) são sincronizados; a magia em si só existe no servidor.
 */
public class DomainDomeEntity extends Entity {
    public static final int EXPAND_TICKS = 20;
    public static final int COLLAPSE_TICKS = 16;
    public static final int SHATTER_TICKS = 24;

    // Bits de configuração (sincronizados)
    public static final int F_SIMPLE = 1;
    public static final int F_OPEN = 1 << 1;
    public static final int F_SURE_HIT = 1 << 2;
    public static final int F_NO_ESCAPE = 1 << 3;
    public static final int F_TERRITORY = 1 << 4;
    public static final int F_NULLIFY = 1 << 5;
    public static final int F_SLOW = 1 << 6;
    public static final int F_GRAVITY = 1 << 7;
    public static final int F_LANDSCAPE = 1 << 8;
    public static final int F_ABSOLUTE = 1 << 9;
    /** v0.8: barreira invertida (aumento Inverter logo depois da forma): só quebra por DENTRO. */
    public static final int F_INVERTED = 1 << 10;
    /** v0.8: Lei Ruína (só para o visual de rachaduras no cliente; a lei roda pelo registro do núcleo). */
    public static final int F_RUIN = 1 << 11;

    public static final byte ACTIVE = 0;
    public static final byte COLLAPSING = 1;
    public static final byte SHATTERED = 2;

    /** Pontuação fixa de chefes no Choque de Domínios (bem acima de um jogador de Círculo médio). */
    public static final int BOSS_SCORE = 160;
    /** Pontuação base de mobs comuns que conjuram domínios. */
    public static final int MOB_SCORE = 50;
    /** Anulação absoluta: mana drenada por segundo de cada inimigo (v0.8: 8; ×3 com a mana da v1.0). */
    public static final float NULLIFY_DRAIN = 8.0F * ResourceTraining.MANA_SCALE;
    /** Margem (blocos) da busca de entidades em volta do domo, a cada tick: cobre a parede e quem está chegando. */
    private static final double SCAN_MARGIN = 16.0;
    /** Teto de partículas dos anéis grandes (domos de raio 80+). */
    private static final int MAX_RING_POINTS = 160;

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> BORN = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> STATE_TIME = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.LONG);
    /** v0.7: elementos da magia (bit = ordinal) e cor de destaque, para o visual de magias multi-elemento. */
    private static final EntityDataAccessor<Integer> ELEMENTS = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACCENT = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.INT);
    /** v0.8: integridade da barreira (0..1) e o último golpe nela (relógio do mundo), para rachaduras e clarão. */
    private static final EntityDataAccessor<Float> INTEGRITY = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT_TIME = SynchedEntityData.defineId(DomainDomeEntity.class, EntityDataSerializers.LONG);

    /**
     * Domos carregados no SERVIDOR (todas as dimensões). Consultas de eventos (Anulação, blocos da Paisagem,
     * Domínio Simples, barreira, choque) varrem esta lista pequena em vez de procurar entidades numa caixa enorme.
     */
    private static final Set<DomainDomeEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    // ---------------- Servidor ----------------
    private UUID ownerId;
    private CompoundTag spellTag = new CompoundTag();
    private int duration = 200;
    private int age;
    private int score;
    private int stateTicks;
    private int ownerMissing;
    private SpellContext ctx;
    /** Presos pela Lei Sem Fuga, pelo id de rede (quem morre e renasce é outra entidade: fica livre). */
    private final Set<Integer> trapped = new HashSet<>();
    private final Map<Integer, Vec3> lastInside = new HashMap<>();
    private final Map<UUID, Long> counterCooldown = new HashMap<>();
    private final DomainLandscape landscape = new DomainLandscape();
    /** Barreira invertida: quem já estava dentro (os de fora não conseguem entrar). */
    private final Set<Integer> insiders = new HashSet<>();
    private float integrity = -1F;
    private float maxIntegrity = -1F;
    @Nullable private ServerBossEvent bossBar;
    @Nullable private Set<ResourceLocation> lawIds;
    /** Entidades em volta (uma busca por tick; domos grandes chegam a 100+ blocos de raio). */
    private List<LivingEntity> nearLiving = List.of();
    private List<Projectile> nearProjectiles = List.of();
    private long scanTick = Long.MIN_VALUE;
    private boolean registered;
    /** Último Info mandado ao núcleo (para fechar o registro mesmo sem o dono carregado). */
    @Nullable private Domains.Info lastInfo;
    /** O domínio acabou "de verdade" (tempo, dono saiu/morreu, barreira quebrada) — não substituído/limpo/descarregado. */
    private boolean endedForReal;

    public DomainDomeEntity(EntityType<? extends DomainDomeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Cria (sem adicionar ao mundo) um domo a partir da magia.
     * @param flags  bits F_* (leis + simples + invertida)
     * @param score  pontuação para o Choque de Domínios (cabo de guerra)
     */
    public static DomainDomeEntity create(SpellContext ctx, Vec3 center, float radius, int durationTicks, int flags, int score) {
        DomainDomeEntity d = new DomainDomeEntity(DomainRegistries.DOMAIN_DOME.get(), ctx.level());
        d.setPos(center.x, center.y, center.z);
        d.ownerId = ctx.casterId();
        d.spellTag = ctx.save();
        d.ctx = ctx;
        d.duration = durationTicks;
        d.score = score;
        d.entityData.set(RADIUS, radius);
        d.entityData.set(COLOR, ctx.color() & 0xFFFFFF);
        d.entityData.set(ELEMENT, ctx.element().ordinal());
        d.entityData.set(FLAGS, flags);
        d.entityData.set(OWNER_ID, ctx.caster() != null ? ctx.caster().getId() : -1);
        d.entityData.set(BORN, ctx.level().getGameTime());
        d.entityData.set(STATE_TIME, ctx.level().getGameTime());
        d.entityData.set(ELEMENTS, elementMask(ctx));
        d.entityData.set(ACCENT, accentOf(ctx));
        return d;
    }

    /** Integridade máxima da barreira (chamado pela forma ao criar; domos sem barreira ignoram). */
    public void initBarrier(float max) {
        maxIntegrity = Math.max(1F, max);
        integrity = maxIntegrity;
        entityData.set(INTEGRITY, 1F);
    }

    /** Bits dos elementos da magia inteira (o primário sempre incluso). */
    private static int elementMask(SpellContext ctx) {
        int mask = 1 << ctx.primaryElement().ordinal();
        for (Element e : ctx.rootSpell().elements()) mask |= 1 << e.ordinal();
        return mask;
    }

    /**
     * Cor de destaque: com cor escolhida na receita, ela mesma clareada (a receita manda); sem cor escolhida, a do
     * 2º elemento de uma magia multi-elemento; senão a do elemento clareada.
     */
    private static int accentOf(SpellContext ctx) {
        int main = ctx.color() & 0xFFFFFF;
        if (ctx.recipe().color() < 0) {
            Element primary = ctx.primaryElement();
            for (Element e : ctx.rootSpell().elements()) if (e != primary) return e.color & 0xFFFFFF;
        }
        return lighten(main, 0.35F);
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * t) << 16) | ((int) (g + (255 - g) * t) << 8) | (int) (b + (255 - b) * t);
    }

    /**
     * Pontuação do Choque de Domínios: Círculo×10 + custo BASE da magia + 5 por Lei (chefes/mobs: valor fixo).
     * v1.0: usa {@link ParsedSpell#baseCost()} (sem custo por cópia nem a escala ×3), o mesmo patamar da v0.8.
     */
    public static int scoreOf(LivingEntity caster, ParsedSpell spell) {
        int laws = spell.laws().size() * 5;
        if (caster instanceof ServerPlayer sp) return MagicApi.get(sp).circle() * 10 + spell.baseCost() + laws;
        boolean boss = caster.getType().is(Tags.EntityTypes.BOSSES) || caster.getMaxHealth() >= 150.0F;
        return (boss ? BOSS_SCORE : MOB_SCORE) + spell.baseCost() + laws;
    }

    /** "Círculo" de quem conjura, para raio e integridade (mobs: 4; chefes: 8). */
    public static int circleOf(LivingEntity caster) {
        if (caster instanceof ServerPlayer sp) return MagicApi.get(sp).circle();
        return BossKit.isBossLike(caster) || caster.getType().is(Tags.EntityTypes.BOSSES) ? 8 : 4;
    }

    // =====================================================================================
    //  Dados sincronizados / getters (comum)
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 10.0F);
        builder.define(COLOR, Element.ARCANE.color);
        builder.define(ELEMENT, 0);
        builder.define(FLAGS, 0);
        builder.define(STATE, ACTIVE);
        builder.define(OWNER_ID, -1);
        builder.define(BORN, 0L);
        builder.define(STATE_TIME, 0L);
        builder.define(ELEMENTS, 0);
        builder.define(ACCENT, 0xFFFFFF);
        builder.define(INTEGRITY, 1F);
        builder.define(HIT_TIME, Long.MIN_VALUE / 2);
    }

    public float radius() { return entityData.get(RADIUS); }
    public int color() { return entityData.get(COLOR); }
    public int flags() { return entityData.get(FLAGS); }
    public boolean has(int flag) { return (flags() & flag) != 0; }
    public boolean isSimple() { return has(F_SIMPLE); }
    public boolean isOpen() { return has(F_OPEN); }
    /** Barreira invertida (só domínio fechado). */
    public boolean isInverted() { return has(F_INVERTED) && !isOpen() && !isSimple(); }
    public byte state() { return entityData.get(STATE); }
    /** Integridade da barreira de 0 a 1 (sincronizada; 1 em domos sem barreira). */
    public float integrityFraction() { return entityData.get(INTEGRITY); }
    /** Ticks desde o último golpe na barreira (para o clarão no cliente). */
    public float sinceHit(float partialTick) { return (level().getGameTime() - entityData.get(HIT_TIME)) + partialTick; }
    public UUID ownerUuid() { return ownerId; }
    public int score() { return score; }
    public float integrity() { return Math.max(0F, integrity); }
    /**
     * O domínio acabou de verdade (tempo, dono saiu/morreu, barreira quebrada)? Falso enquanto ativo e quando foi
     * substituído por outro domínio do mesmo dono, limpo por comando ou descarregado (servidor).
     */
    public boolean endedForReal() { return endedForReal && state() != ACTIVE; }
    public float maxIntegrity() { return Math.max(1F, maxIntegrity); }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    /** Cor de destaque (2º elemento de magias multi-elemento; senão a cor do domo clareada). */
    public int accentColor() { return entityData.get(ACCENT); }

    /** Elementos da magia (o primário primeiro). Magias de um elemento só: só ele. */
    public List<Element> elements() {
        Element primary = element();
        List<Element> out = new ArrayList<>();
        out.add(primary);
        int mask = entityData.get(ELEMENTS);
        for (Element e : Element.values()) {
            if (e != primary && (mask & (1 << e.ordinal())) != 0) out.add(e);
        }
        return out;
    }

    /**
     * Elemento do chão da Paisagem Elemental. Magia multi-elemento com dois elementos BASE que formam uma fusão
     * (ex.: Fogo + Água) vira o chão da fusão, que mistura os dois; senão, o elemento primário.
     */
    public Element landscapeElement() {
        List<Element> els = elements();
        Element primary = els.get(0);
        if (primary.isBase()) {
            for (int i = 1; i < els.size(); i++) {
                Element f = els.get(i).isBase() ? Element.fusionOf(primary, els.get(i)) : null;
                if (f != null) return f;
            }
        }
        return primary;
    }

    /** Elemento da {@code n}-ésima partícula/alvo: alterna entre os elementos da magia. */
    public Element elementAt(int n) {
        List<Element> els = elements();
        return els.get(Math.floorMod(n, els.size()));
    }

    /** Domo "fechado" (paredes e céu próprio): expansão completa, sem Barreira Aberta. */
    public boolean isEnclosing() {
        return !isSimple() && !isOpen() && state() != SHATTERED;
    }

    /** Barreira de pé e valendo (fechado, ativo, já expandido): pode ser golpeada. */
    public boolean barrierUp() {
        return !isSimple() && !isOpen() && isLive();
    }

    public Vec3 center() { return position(); }

    /** O ponto (normalmente o centro do corpo) está dentro do domo, com folga {@code extra}. */
    public boolean contains(Vec3 p, double extra) {
        double r = radius() + extra;
        return p.distanceToSqr(center()) <= r * r;
    }

    public boolean contains(Entity e, double extra) {
        return contains(e.position().add(0, e.getBbHeight() * 0.5, 0), extra);
    }

    /** Este domo envolve TODO o domo {@code other} (o outro está inteiro aqui dentro). */
    public boolean envelops(DomainDomeEntity other) {
        return other != this && radius() > other.radius()
                && center().distanceTo(other.center()) + other.radius() <= radius() + 0.5;
    }

    /** As duas esferas se cruzam. */
    public boolean overlaps(DomainDomeEntity other) {
        double r = radius() + other.radius();
        return other != this && center().distanceToSqr(other.center()) < r * r;
    }

    /** Ativo e já terminou de se expandir (leis valendo). */
    public boolean isLive() {
        return state() == ACTIVE && age >= EXPAND_TICKS;
    }

    /** {@code state} em {@code pos} é um bloco colocado pela Paisagem Elemental deste domo (protegido até o fim). */
    public boolean isLandscapeBlock(BlockPos pos, BlockState state) {
        return landscape.isPlaced(pos, state);
    }

    // ---------------- Visual (lido pelo cliente) ----------------
    /** Ticks desde que o domo nasceu, pelo relógio do mundo (funciona para quem chega depois). */
    public float visualAge(float partialTick) {
        return (level().getGameTime() - entityData.get(BORN)) + partialTick;
    }

    /** Ticks desde a última troca de estado (desmoronando / despedaçado). */
    public float stateAge(float partialTick) {
        return (level().getGameTime() - entityData.get(STATE_TIME)) + partialTick;
    }

    /** Raio desenhado: cresce ao surgir (ease-out), encolhe ao desmoronar. */
    public float visualRadius(float partialTick) {
        float r = radius();
        byte st = state();
        if (st == COLLAPSING) {
            float s = Mth.clamp(stateAge(partialTick) / COLLAPSE_TICKS, 0, 1);
            return r * (1 - s * s);
        }
        if (st == SHATTERED) return r;
        float t = Mth.clamp(visualAge(partialTick) / EXPAND_TICKS, 0, 1);
        float inv = 1 - t;
        return r * (1 - inv * inv * inv);
    }

    /** 0 = acabou de surgir / sumindo, 1 = domo completo. Usado para a névoa/tonalidade interna. */
    public float presence(float partialTick) {
        byte st = state();
        if (st == COLLAPSING) return 1 - Mth.clamp(stateAge(partialTick) / COLLAPSE_TICKS, 0, 1);
        if (st == SHATTERED) return 1 - Mth.clamp(stateAge(partialTick) / (SHATTER_TICKS * 0.5F), 0, 1);
        return Mth.clamp(visualAge(partialTick) / EXPAND_TICKS, 0, 1);
    }

    /** Progresso da quebra (0..1), ou -1 se não estiver despedaçado. */
    public float shatterProgress(float partialTick) {
        return state() == SHATTERED ? Mth.clamp(stateAge(partialTick) / SHATTER_TICKS, 0, 1) : -1;
    }

    /** Entidade dona (no cliente: pelo id de rede). */
    public Entity ownerEntity() {
        int id = entityData.get(OWNER_ID);
        return id >= 0 ? level().getEntity(id) : null;
    }

    // =====================================================================================
    //  Tick (servidor)
    // =====================================================================================
    @Override
    public void tick() {
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        age++;

        byte st = state();
        if (st != ACTIVE) {
            stateTicks++;
            if (st == SHATTERED && stateTicks < 8) shatterDebris(sl);
            if (stateTicks >= (st == SHATTERED ? SHATTER_TICKS : COLLAPSE_TICKS)) discard();
            return;
        }

        LivingEntity owner = owner(sl);
        if (owner == null) {
            if (++ownerMissing > 40) collapse(false);
            return;
        }
        ownerMissing = 0;
        if (!owner.isAlive()) {
            collapse(true);
            return;
        }
        if (isSimple()) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        } else if (!contains(owner, 4.0)) {
            // O conjurador saiu do próprio domínio: a barreira se desfaz.
            if (owner instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_left").withStyle(ChatFormatting.GRAY), true);
            }
            collapse(true);
            return;
        }
        if (age >= duration + EXPAND_TICKS) {
            collapse(true);
            return;
        }
        SpellContext c = context(sl);
        if (c == null) {
            collapse(true);
            return;
        }

        if (age < EXPAND_TICKS) {
            expandFx(sl);
            return;
        }
        int active = age - EXPAND_TICKS;
        scan(sl);
        if (active == 0) onActivated(sl, owner, c);
        if (isSimple()) simpleTick(sl, owner, c, active);
        else domainTick(sl, owner, c, active);
        // Leis de outros módulos (Santuário, Vazio Infinito, Ruína...) rodam pelo registro do núcleo
        if (state() == ACTIVE && !isRemoved()) {
            Domains.Info info = info(sl, owner, c);
            if (info != null) {
                lastInfo = info;
                Domains.ticked(info, active);
                registered = true;
            }
        }
        if (state() == ACTIVE && !isSimple() && !isOpen()) bossBarTick(sl, active);
    }

    private LivingEntity owner(ServerLevel sl) {
        if (ownerId == null) return null;
        Entity e = sl.getEntity(ownerId);
        if (e instanceof LivingEntity le) {
            if (entityData.get(OWNER_ID) != le.getId()) entityData.set(OWNER_ID, le.getId());
            return le;
        }
        return null;
    }

    /** O dono, se estiver carregado (servidor). */
    @Nullable
    public LivingEntity ownerLiving() {
        return level() instanceof ServerLevel sl ? owner(sl) : null;
    }

    /** Contexto da magia; recarrega (do estado ATUAL, com o orçamento já gasto) se o dono saiu/renasceu. */
    private SpellContext context(ServerLevel sl) {
        if (ctx == null || ctx.caster() == null || ctx.caster().isRemoved()) {
            if (ctx != null) spellTag = ctx.save();
            SpellContext fresh = SpellContext.load(sl, spellTag);
            if (fresh != null || ctx == null) ctx = fresh;
        }
        return ctx;
    }

    /** Contexto atual (servidor), ou null. */
    @Nullable
    public SpellContext spellContext() {
        return level() instanceof ServerLevel sl ? context(sl) : null;
    }

    /**
     * Contexto novo a cada pulso: o conjunto de "já encadeados" do Encadear recomeça e cada pulso pode disparar
     * as ligações (Ao Acertar/Abater) de novo. O orçamento de estágios-filho/saltos da conjuração continua o
     * mesmo (vem do estado atual), então um domínio longo não cria estágios sem fim.
     */
    private SpellContext freshContext(ServerLevel sl) {
        CompoundTag tag = ctx != null ? ctx.save() : spellTag;
        tag.remove("fires");
        SpellContext fresh = SpellContext.load(sl, tag);
        if (fresh != null) {
            ctx = fresh;
            spellTag = tag;
        }
        return ctx;
    }

    // =====================================================================================
    //  Registro do núcleo (Domains)
    // =====================================================================================
    /** Ids das leis da magia (inclui as de outros módulos); Domínio Simples ganha a pseudo-lei {@link Domains#SIMPLE}. */
    private Set<ResourceLocation> lawIds(SpellContext c) {
        if (lawIds == null) {
            Set<ResourceLocation> out = new LinkedHashSet<>();
            if (c != null) for (LawGlyph law : c.spell().laws()) out.add(law.id());
            if (isSimple()) out.add(Domains.SIMPLE);
            // Sem contexto ainda (domo recarregado): não guarda o conjunto vazio, senão as leis sumiriam para sempre
            if (c == null) return out;
            lawIds = out;
        }
        return lawIds;
    }

    /** Leis da magia (para comandos/informação). */
    public Set<ResourceLocation> laws() {
        return lawIds != null ? lawIds : Set.of();
    }

    @Nullable
    private Domains.Info info(ServerLevel sl, @Nullable LivingEntity owner, @Nullable SpellContext c) {
        if (owner == null) return null;
        return new Domains.Info(getId(), sl, owner, center(), radius(), isOpen() || isSimple(), lawIds(c), element(), c,
                entityData.get(BORN) + EXPAND_TICKS, duration, isInverted());
    }

    /** Info atual para o registro do núcleo (servidor), ou null sem dono carregado. */
    @Nullable
    public Domains.Info currentInfo() {
        if (!(level() instanceof ServerLevel sl)) return null;
        return info(sl, owner(sl), context(sl));
    }

    /** Avisa o núcleo que o domínio acabou (onClose das leis roda uma vez). */
    private void unregister(ServerLevel sl) {
        if (!registered) return;
        registered = false;
        LivingEntity owner = owner(sl);
        Domains.Info info = owner != null ? info(sl, owner, ctx) : null;
        // Sem dono carregado: fecha pelo último Info (o núcleo só precisa do id; o onClose recebe o Info guardado)
        if (info == null) info = lastInfo;
        if (info != null) Domains.closed(info);
        lastInfo = null;
    }

    // =====================================================================================
    //  Alvos
    // =====================================================================================
    /** Uma busca por tick: criaturas e projéteis até {@link #SCAN_MARGIN} blocos além da parede. */
    private void scan(ServerLevel sl) {
        long now = sl.getGameTime();
        if (scanTick == now) return;
        scanTick = now;
        double r = radius() + SCAN_MARGIN;
        List<LivingEntity> living = new ArrayList<>();
        List<Projectile> proj = new ArrayList<>();
        Vec3 c = center();
        double r2 = r * r;
        for (Entity e : sl.getEntities(this, getBoundingBox().inflate(r), e -> e.isAlive() && !e.isSpectator())) {
            if (e.position().distanceToSqr(c) > r2) continue;
            if (e instanceof LivingEntity le) {
                if (!(e instanceof ArmorStand)) living.add(le);
            } else if (e instanceof Projectile p) {
                proj.add(p);
            }
        }
        nearLiving = living;
        nearProjectiles = proj;
    }

    private List<LivingEntity> livingInside(ServerLevel sl, double extra) {
        scan(sl);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : nearLiving) if (e.isAlive() && !e.isRemoved() && contains(e, extra)) out.add(e);
        return out;
    }

    /**
     * Inimigo do dono do domo. Jogadores: qualquer um que não seja aliado (regras do SpellContext).
     * Mobs (chefes): só jogadores, os bichos deles e quem estiver brigando com o mob — o domínio de um chefe
     * não fica acertando zumbis e vacas.
     */
    public boolean isEnemy(SpellContext c, LivingEntity owner, Entity e) {
        if (e == owner || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (c.isAlly(e)) return false;
        if (owner instanceof Player) return true;
        if (e instanceof Player) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        if (e instanceof Mob m && m.getTarget() == owner) return true;
        return owner instanceof Mob om && om.getTarget() == e;
    }

    /** {@code e} é o dono ou aliado do dono (atravessa a própria barreira; nunca a danifica). */
    public boolean isFriendly(@Nullable Entity e) {
        if (e == null) return false;
        if (ownerId != null && ownerId.equals(e.getUUID())) return true;
        SpellContext c = spellContext();
        return c != null && c.isAlly(e);
    }

    /**
     * Regra "o mais interno manda": {@code e} está dentro da barreira FECHADA de um domínio MENOR de outro dono —
     * os efeitos e o Acerto Garantido deste domo não o alcançam (o maior só ataca aquela barreira por fora).
     */
    public boolean shieldedFromThis(LivingEntity e) {
        if (LIVE.size() < 2) return false;
        float r = radius();
        for (DomainDomeEntity d : LIVE) {
            if (d == this || d.level() != level() || d.isRemoved() || !d.barrierUp() || d.radius() >= r) continue;
            if (d.ownerId != null && d.ownerId.equals(ownerId)) continue;
            if (d.contains(e, 0)) return true;
        }
        return false;
    }

    /** Este Domínio Simples protege {@code target} (dono ou aliado do dono)? */
    public boolean protects(ServerLevel sl, Entity target) {
        if (!isSimple() || state() != ACTIVE || !contains(target, 0.5)) return false;
        if (ownerId != null && ownerId.equals(target.getUUID())) return true;
        SpellContext c = context(sl);
        return c != null && c.isAlly(target);
    }

    /** {@code target} está dentro de um Domínio Simples que não é do dono do domínio atacante? */
    public static boolean protectedBySimpleDomain(ServerLevel sl, Entity target, UUID attackerOwner) {
        for (DomainDomeEntity d : around(sl, target.position(), 2.0)) {
            if (!d.isSimple() || (attackerOwner != null && attackerOwner.equals(d.ownerId))) continue;
            if (d.protects(sl, target)) return true;
        }
        return false;
    }

    /**
     * Domos (servidor) desta dimensão cuja esfera chega a até {@code margin} blocos de {@code pos}.
     * Devolve uma cópia: pode-se desfazer domos enquanto percorre.
     */
    public static List<DomainDomeEntity> around(Level level, Vec3 pos, double margin) {
        List<DomainDomeEntity> out = new ArrayList<>();
        for (DomainDomeEntity d : LIVE) {
            if (d.level() != level || d.isRemoved()) continue;
            double r = d.radius() + margin;
            if (d.position().distanceToSqr(pos) <= r * r) out.add(d);
        }
        return out;
    }

    /** Todos os domos (servidor) carregados nesta dimensão. */
    public static List<DomainDomeEntity> all(Level level) {
        List<DomainDomeEntity> out = new ArrayList<>();
        for (DomainDomeEntity d : LIVE) if (d.level() == level && !d.isRemoved()) out.add(d);
        return out;
    }

    /** Domo (servidor) pelo id de entidade, em qualquer dimensão; null se não estiver carregado. */
    @Nullable
    public static DomainDomeEntity byId(int id) {
        for (DomainDomeEntity d : LIVE) if (d.getId() == id && !d.isRemoved()) return d;
        return null;
    }

    /** A Lei Anulação deste domo cancela uma magia conjurada por {@code caster}? */
    public boolean nullifies(ServerLevel sl, LivingEntity caster) {
        if (!has(F_NULLIFY) || isSimple() || !isLive() || !contains(caster, 0)) return false;
        LivingEntity owner = owner(sl);
        SpellContext c = context(sl);
        return owner != null && c != null && isEnemy(c, owner, caster) && !shieldedFromThis(caster);
    }

    // =====================================================================================
    //  Fases
    // =====================================================================================
    private void expandFx(ServerLevel sl) {
        float t = age / (float) EXPAND_TICKS;
        float r = radius() * (1 - (1 - t) * (1 - t) * (1 - t));
        // Anel crescendo; magias multi-elemento alternam os elementos a cada anel
        Element el = elementAt(age / 2);
        if (age % 4 == 0) ElementFx.ring(sl, center().add(0, 0.15, 0), el, r, ringPoints(r, 3));
        if (age == 1) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            // v1.3: a cúpula se fecha do chão ao topo junto com a expansão; ao fechar, clarão de troca de paisagem
            if (!isSimple()) {
                List<Element> els = elements();
                ElementFx.domainSphere(sl, center(), el, color(), radius(), EXPAND_TICKS);
                if (els.size() > 1) ElementFx.domainSphere(sl, center(), els.get(1), els.get(1).color, radius() * 0.97F, EXPAND_TICKS + 4);
                ElementFx.gatherAt(sl, center().add(0, 1.2, 0), el, color(), 3.5F, 5, EXPAND_TICKS);
            } else {
                ElementFx.runeCircle(sl, center().add(0, 0.05, 0), color(), radius(), 40, 3);
            }
        }
    }

    private static int ringPoints(double r, double perBlock) {
        return Mth.clamp((int) (r * perBlock), 16, MAX_RING_POINTS);
    }

    private void onActivated(ServerLevel sl, LivingEntity owner, SpellContext c) {
        Element el = element();
        float r = radius();
        Domains.Info info = info(sl, owner, c);
        if (info != null) {
            lastInfo = info;
            Domains.opened(info);
            registered = true;
        }
        if (isSimple()) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
            return;
        }
        if (maxIntegrity <= 0) initBarrier(defaultIntegrity(owner, c));
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0F, 0.6F);
        sl.playSound(null, getX(), getY(), getZ(), ElementFx.castSoundOf(el), SoundSource.PLAYERS, 2.5F, 0.6F);
        List<Element> els = elements();
        // v1.3: explosão de ativação montada no cliente (preset gráfico) + chão do elemento por alguns segundos
        for (int i = 0; i < Math.min(2, els.size()); i++) {
            Element e = els.get(i);
            ElementFx.nova(sl, center(), e, i == 0 ? color() : e.color, r * (i == 0 ? 0.7F : 0.5F), 5, false);
            ElementFx.area(sl, center(), e, i == 0 ? color() : e.color, r * 0.9F, 3, 100);
            DomainFx.far(sl, ElementFx.signature(e), getX(), getY() + 1, getZ(), 24 / els.size(), r * 0.4, 0.5, r * 0.4, 0.05);
        }
        // Quem já está dentro fica preso (Sem Fuga) e, numa barreira invertida, é "de dentro"
        for (LivingEntity e : livingInside(sl, -1.5)) {
            insiders.add(e.getId());
            if (isEnemy(c, owner, e)) {
                trapped.add(e.getId());
                lastInside.put(e.getId(), e.position());
            }
        }
        if (isInverted()) {
            int neg = 0xFFFFFF ^ color();
            Announce.send(owner, Component.translatable("announce.isekai_mahou.inverted_barrier"), neg, Announce.STYLE_ULTIMATE);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 3.0F, 0.5F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.0F, 0.5F);
            for (int i = 0; i < 48; i++) {
                Vec3 p = center().add(upperDir(sl.random).scale(r));
                DomainFx.far(sl, Vfx.reversePortal(), p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (has(F_LANDSCAPE)) {
            if (MahouConfig.DOMAIN_GRIEFING.get()) {
                landscape.begin(sl, center(), r, landscapeElement(), has(F_ABSOLUTE) ? 2 : 1);
            } else if (owner instanceof ServerPlayer sp) {
                // O servidor desligou a troca de blocos: avisa em vez de a Lei "não fazer nada" em silêncio.
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_landscape_disabled").withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    /** Integridade de reserva (domos salvos antes da v0.8 ou de mobs sem forma): 60 + 20·Círculo + 0,5·custo base. */
    private float defaultIntegrity(LivingEntity owner, SpellContext c) {
        float v = 60F + 20F * circleOf(owner) + 0.5F * (c != null ? c.rootSpell().baseCost() : 120);
        return has(F_ABSOLUTE) ? v * 1.25F : v;
    }

    private void domainTick(ServerLevel sl, LivingEntity owner, SpellContext c, int active) {
        boolean abs = has(F_ABSOLUTE);
        if (active % 10 == 0) statusLaws(sl, owner, c, abs);
        boolean closed = !isOpen();
        if (closed) {
            barrierProjectiles(sl, owner, c);
            if (isInverted()) keepOutsidersOut(sl, owner, c);
            if (has(F_NO_ESCAPE)) barrier(sl, owner, c, abs);
            if (active % 10 == 5) tugOfWar(sl, owner);
        }
        if (has(F_SLOW)) slowProjectiles(sl, owner, c, abs);
        if (has(F_GRAVITY) && active % 2 == 0) gravityPull(sl, owner, c, abs);
        if (has(F_LANDSCAPE)) landscape.tick(sl, center(), radius(), landscapeElement());
        if (has(F_NULLIFY) && abs && active % 20 == 10) drainMana(sl, owner, c);
        int interval = has(F_SURE_HIT) && abs ? 15 : 20;
        if (active % interval == 0) pulse(sl, owner);
        if (active % 10 == 5) ambientFx(sl);
    }

    /** Pulso do domínio: os efeitos da magia em todos os alvos válidos lá dentro. */
    private void pulse(ServerLevel sl, LivingEntity owner) {
        SpellContext c = freshContext(sl);
        if (c == null) return;
        boolean sureHit = has(F_SURE_HIT);
        Element el = element();
        float[] charge = UltimateCharge.snapshot(owner);
        boolean heals = hasBeneficial(c);
        int struck = 0;
        for (LivingEntity e : enemiesFirst(c, livingInside(sl, 0))) {
            if (e == owner) continue;
            if (c.isAlly(e)) {
                // aliados recebem só os efeitos benéficos (cura, escudo...) — o SpellContext filtra.
                // Sem efeito benéfico nem chama: o Encadear pularia do aliado para inimigos fora do domo.
                if (heals) c.resolveOnEntity(e, null);
                continue;
            }
            if (!isEnemy(c, owner, e)) continue;
            // O mais interno manda: quem está protegido por uma barreira menor de outro dono não é alcançado
            if (shieldedFromThis(e)) continue;
            boolean guaranteed = sureHit && !protectedBySimpleDomain(sl, e, ownerId);
            if (!guaranteed && !owner.hasLineOfSight(e)) continue;
            c.resolveOnEntity(e, null);
            // Raio do domínio caindo no alvo; multi-elemento: cada alvo recebe a cor de um dos elementos
            Element fx = elementAt(struck++);
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            ElementFx.line(sl, p.add(0, 3.5, 0), p, fx);
            ElementFx.burst(sl, p, fx, 10, 0.3);
        }
        applyBeneficialToOwner(c, owner);
        UltimateCharge.restore(owner, charge);
        attackInnerBarriers(sl, owner, c);
        ElementFx.ring(sl, center().add(0, 0.2, 0), el, radius() * 0.97, ringPoints(radius(), 4));
        sl.playSound(null, getX(), getY(), getZ(), ElementFx.castSoundOf(el), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** Força de um pulso contra uma barreira: (3 + 1,5 por efeito) × poder; o golpe na barreira vale ×4. */
    private static float pulsePower(SpellContext c) {
        return (3F + 1.5F * c.spell().effects().size()) * Math.max(0.25F, c.powerMult()) * Math.max(0.25F, c.formStats().power());
    }

    /**
     * "Um domínio maior só ataca por fora": cada pulso deste domo bate na barreira dos domínios FECHADOS menores de
     * outros donos que ele envolve (ou, sendo aberto, que estão dentro dele). Invertida aguenta.
     */
    private void attackInnerBarriers(ServerLevel sl, LivingEntity owner, SpellContext c) {
        if (LIVE.size() < 2) return;
        List<DomainDomeEntity> inner = new ArrayList<>();
        for (DomainDomeEntity d : LIVE) {
            if (d == this || d.level() != sl || d.isRemoved() || !d.barrierUp()) continue;
            if (d.ownerId != null && d.ownerId.equals(ownerId)) continue;
            // Domínio de aliado: um não ataca o outro
            if (d.isFriendly(owner) || isFriendly(d.ownerLiving())) continue;
            boolean target = isOpen() ? d.radius() < radius() && contains(d.center(), 0) : envelops(d);
            if (target) inner.add(d);
        }
        float amount = 4F * pulsePower(c);
        for (DomainDomeEntity d : inner) {
            // Ponto da parede do menor virado para o centro deste (ou o topo, se forem concêntricos)
            Vec3 dir = center().subtract(d.center());
            dir = dir.lengthSqr() < 1.0E-3 ? new Vec3(0, 1, 0) : dir.normalize();
            if (dir.y < 0.2) dir = new Vec3(dir.x, 0.35, dir.z).normalize();
            Vec3 wall = d.center().add(dir.scale(d.radius()));
            ElementFx.line(sl, wall.add(dir.scale(4)), wall, element());
            if (d.isInverted()) {
                DomainBarrier.holdFx(sl, d, wall);
                continue;
            }
            d.damageBarrier(amount, false, owner, wall);
        }
    }

    /**
     * Efeitos benéficos (cura, escudo...) também valem para o dono — os nocivos, nunca. {@code applyEffect} cuida de
     * Atraso/Repetir e do elemento do glifo (magias multi-elemento).
     */
    private static void applyBeneficialToOwner(SpellContext c, LivingEntity owner) {
        for (ParsedSpell.EffectEntry entry : c.spell().effects()) {
            if (entry.effect().isBeneficial() && !entry.stats().inverted && !entry.stats().enemiesOnly) {
                c.applyEffect(entry, owner);
            }
        }
    }

    /**
     * Inimigos antes dos aliados: o 1º alvo resolvido num pulso é o que dispara Ao Acertar (o núcleo avisa por
     * formas não-programáveis), e numa magia de dano + cura ele deve sair de um inimigo, não de um amigo curado.
     */
    private static List<LivingEntity> enemiesFirst(SpellContext c, List<LivingEntity> list) {
        List<LivingEntity> out = new ArrayList<>(list);
        out.sort(Comparator.comparing(c::isAlly));
        return out;
    }

    private static boolean hasBeneficial(SpellContext c) {
        return c.spell().effects().stream().anyMatch(e -> e.effect().isBeneficial() && !e.stats().inverted);
    }

    /** A magia tem algum efeito que fere inimigos (golpe, maldição, empurrão...). */
    private static boolean hasHarmful(SpellContext c) {
        return c.spell().effects().stream().anyMatch(e -> !e.effect().isBeneficial() || e.stats().inverted);
    }

    // ---------------- Leis de status (a cada 10 ticks, duram 40) ----------------
    private void statusLaws(ServerLevel sl, LivingEntity owner, SpellContext c, boolean abs) {
        boolean territory = has(F_TERRITORY);
        boolean magma = has(F_LANDSCAPE) && landscape.usesMagma();
        for (LivingEntity e : livingInside(sl, 0.5)) {
            if (e == owner) {
                if (territory) {
                    give(e, MobEffects.DAMAGE_BOOST, abs ? 2 : 1, owner);
                    give(e, MobEffects.DAMAGE_RESISTANCE, abs ? 1 : 0, owner);
                }
                if (magma) give(e, MobEffects.FIRE_RESISTANCE, 0, owner);
                continue;
            }
            if (isEnemy(c, owner, e)) {
                if (shieldedFromThis(e)) continue;
                if (territory) {
                    give(e, CoreRegistries.EXPOSED, abs ? 1 : 0, owner);
                    give(e, MobEffects.WEAKNESS, 0, owner);
                }
                if (has(F_NULLIFY)) give(e, CoreRegistries.SILENCED, 0, owner);
                if (has(F_SLOW)) {
                    give(e, MobEffects.MOVEMENT_SLOWDOWN, abs ? 5 : 3, owner);
                    give(e, MobEffects.DIG_SLOWDOWN, abs ? 2 : 1, owner);
                }
                if (has(F_GRAVITY)) {
                    give(e, DomainRegistries.DOMAIN_GRAVITY, abs ? 1 : 0, owner);
                    e.removeEffect(MobEffects.LEVITATION);
                    e.removeEffect(MobEffects.SLOW_FALLING);
                }
            } else if (c.isAlly(e)) {
                if (territory) give(e, MobEffects.DAMAGE_BOOST, 0, owner);
                if (magma) give(e, MobEffects.FIRE_RESISTANCE, 0, owner);
            }
        }
    }

    private static void give(LivingEntity e, Holder<MobEffect> effect, int amp, Entity source) {
        e.addEffect(new MobEffectInstance(effect, 40, amp, true, true, true), source);
    }

    // ---------------- Sem Fuga ----------------
    private void barrier(ServerLevel sl, LivingEntity owner, SpellContext c, boolean abs) {
        double r = radius();
        Vec3 c0 = center();
        Element el = element();
        for (LivingEntity e : nearLiving) {
            if (!e.isAlive() || !isEnemy(c, owner, e)) continue;
            int id = e.getId();
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = p.distanceTo(c0);
            if (d < r - 1.5) {
                trapped.add(id);
                lastInside.put(id, e.position());
                continue;
            }
            if (!trapped.contains(id)) continue;
            if (d > r + 1.5) {
                // Escapou (teletransporte, empurrão muito forte): volta para o último ponto dentro
                Vec3 back = lastInside.get(id);
                if (back != null && d < r + 24) {
                    e.teleportTo(back.x, back.y, back.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.resetFallDistance();
                    e.hurtMarked = true;
                    sl.sendParticles(Vfx.reversePortal(), back.x, back.y + 1, back.z, 20, 0.3, 0.6, 0.3, 0.05);
                    sl.playSound(null, back.x, back.y, back.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
                } else {
                    trapped.remove(id);
                    lastInside.remove(id);
                }
                continue;
            }
            if (d > r - 1.5) {
                Vec3 in = c0.subtract(p).normalize();
                Vec3 v = e.getDeltaMovement();
                double outward = -v.dot(in);
                e.setDeltaMovement(v.add(in.scale(Math.max(0, outward) + (abs ? 0.55 : 0.4))));
                e.hurtMarked = true;
                if (tickCount % 4 == 0) {
                    Vec3 wall = c0.add(p.subtract(c0).normalize().scale(r));
                    sl.sendParticles(ElementFx.dust(el, 1.6F), wall.x, wall.y, wall.z, 8, 0.4, 0.4, 0.4, 0);
                    sl.sendParticles(Vfx.electric(), wall.x, wall.y, wall.z, 4, 0.3, 0.3, 0.3, 0.1);
                    sl.playSound(null, wall.x, wall.y, wall.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.5F);
                }
                if (abs && tickCount % 10 == 0) e.hurt(c.damageSource(this), 2.0F);
            }
        }
        if (trapped.size() > 128) trapped.removeIf(id -> sl.getEntity(id) == null);
        if (lastInside.size() > 128) lastInside.keySet().removeIf(id -> !trapped.contains(id));
    }

    // ---------------- Barreira invertida: quem está fora não entra ----------------
    private void keepOutsidersOut(ServerLevel sl, LivingEntity owner, SpellContext c) {
        double r = radius();
        Vec3 c0 = center();
        for (LivingEntity e : nearLiving) {
            if (!e.isAlive() || e == owner) continue;
            int id = e.getId();
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = p.distanceTo(c0);
            if (d < r - 1.5) {
                insiders.add(id);
                continue;
            }
            if (insiders.contains(id) || !isEnemy(c, owner, e) || d > r + 1.5) continue;
            // Encostou na parede invertida por fora: é repelido
            Vec3 out = p.subtract(c0);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
            Vec3 v = e.getDeltaMovement();
            double inward = -v.dot(out);
            e.setDeltaMovement(v.add(out.scale(Math.max(0, inward) + 0.45)));
            e.hurtMarked = true;
            if (tickCount % 4 == 0) {
                Vec3 wall = c0.add(out.scale(r));
                sl.sendParticles(Vfx.reversePortal(), wall.x, wall.y, wall.z, 8, 0.4, 0.4, 0.4, 0.05);
                sl.sendParticles(Vfx.electric(), wall.x, wall.y, wall.z, 4, 0.3, 0.3, 0.3, 0.1);
                sl.playSound(null, wall.x, wall.y, wall.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.35F);
            }
        }
        if (insiders.size() > 256) insiders.removeIf(id -> sl.getEntity(id) == null);
    }

    // ---------------- Projéteis cruzando a parede (toda barreira fechada) ----------------
    /**
     * Projétil de outro (não do dono nem de aliados) que vai atravessar a parede neste tick: bate na barreira. De um
     * lado ele tira integridade, do outro só faísca ({@link Domains#hitBarrier} aplica a regra). Flechas
     * ricocheteiam, o resto se desfaz.
     */
    private void barrierProjectiles(ServerLevel sl, LivingEntity owner, SpellContext c) {
        if (nearProjectiles.isEmpty()) return;
        double r = radius();
        Vec3 c0 = center();
        for (Projectile p : nearProjectiles) {
            if (p.isRemoved()) continue;
            Vec3 pos = p.position();
            Vec3 vel = p.getDeltaMovement();
            if (vel.lengthSqr() < 0.0025) continue;
            Vec3 next = pos.add(vel);
            double dPos = pos.distanceTo(c0), dNext = next.distanceTo(c0);
            boolean inward = dPos >= r && dNext < r;
            boolean outward = dPos <= r && dNext > r;
            if (!inward && !outward) continue;
            Entity shooter = p.getOwner();
            if (shooter != null && (shooter == owner || c.isAlly(shooter))) continue;
            Vec3 hit = DomainBarrier.wallPoint(this, pos, next);
            Vec3 normal = hit.subtract(c0);
            normal = normal.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : normal.normalize();
            // Lado do ataque: de onde o projétil veio
            Vec3 from = inward ? hit.add(normal.scale(0.6)) : hit.subtract(normal.scale(0.6));
            DomainBarrier.hitAt(sl, this, hit, from, p, DomainBarrier.projectileDamage(p));
            sl.sendParticles(Vfx.electric(), hit.x, hit.y, hit.z, 10, 0.2, 0.2, 0.2, 0.2);
            sl.sendParticles(ElementFx.dust(element(), 1.4F), hit.x, hit.y, hit.z, 8, 0.3, 0.3, 0.3, 0);
            if (p instanceof AbstractArrow) {
                // Ricochete para o lado de onde veio
                Vec3 back = inward ? normal : normal.scale(-1);
                p.setPos(hit.add(back.scale(0.8)));
                p.setDeltaMovement(back.scale(0.35).add(0, 0.1, 0));
                p.hasImpulse = true;
            } else {
                p.discard();
            }
        }
    }

    // ---------------- Cabo de guerra: domínios fechados que se sobrepõem em parte ----------------
    private void tugOfWar(ServerLevel sl, LivingEntity owner) {
        if (LIVE.size() < 2) return;
        List<DomainDomeEntity> rivals = new ArrayList<>();
        for (DomainDomeEntity d : LIVE) {
            if (d == this || d.level() != sl || d.isRemoved() || !d.barrierUp()) continue;
            if (d.ownerId != null && d.ownerId.equals(ownerId)) continue;
            if (!overlaps(d) || envelops(d) || d.envelops(this)) continue;
            if (d.isFriendly(owner) || isFriendly(d.ownerLiving())) continue;
            rivals.add(d);
        }
        float amount = 2F + score / 10F;
        for (DomainDomeEntity d : rivals) {
            // Ponto da disputa: no meio das duas paredes, na linha dos centros (um pouco acima do chão)
            Vec3 dir = center().subtract(d.center());
            dir = dir.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : dir.normalize();
            Vec3 wall = d.center().add(dir.scale(d.radius())).add(0, Math.min(d.radius(), radius()) * 0.3, 0);
            Vec3 mine = center().subtract(dir.scale(radius())).add(0, Math.min(d.radius(), radius()) * 0.3, 0);
            Vec3 mid = wall.add(mine).scale(0.5);
            DomainFx.far(sl, Vfx.electric(), mid.x, mid.y, mid.z, 16, 1.0, 1.0, 1.0, 0.25);
            DomainFx.far(sl, ElementFx.dust(element(), 2.0F), mid.x, mid.y, mid.z, 10, 1.2, 1.2, 1.2, 0);
            ElementFx.line(sl, mine, mid, element());
            if (tickCount % 20 == 5) sl.playSound(null, mid.x, mid.y, mid.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
            d.damageBarrier(amount, false, owner, wall);
        }
    }

    // ---------------- Tempo Lento ----------------
    private void slowProjectiles(ServerLevel sl, LivingEntity owner, SpellContext c, boolean abs) {
        double factor = abs ? 0.8 : 0.88;
        for (Projectile p : nearProjectiles) {
            if (p.isRemoved() || !contains(p, 0)) continue;
            Entity shooter = p.getOwner();
            if (shooter != null && (shooter == owner || c.isAlly(shooter))) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            p.setDeltaMovement(v.scale(factor));
            p.hasImpulse = true;
            if (tickCount % 3 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY(), p.getZ(), 2, 0.1, 0.1, 0.1, 0);
        }
    }

    // ---------------- Gravidade do Domínio ----------------
    private void gravityPull(ServerLevel sl, LivingEntity owner, SpellContext c, boolean abs) {
        double pull = abs ? 0.16 : 0.1;
        for (LivingEntity e : livingInside(sl, 0)) {
            if (!isEnemy(c, owner, e) || e.onGround() || e.isInWater() || shieldedFromThis(e)) continue;
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.9, Math.min(v.y, 0) - pull, v.z * 0.9);
            e.hurtMarked = true;
            if (tickCount % 6 == 0) {
                sl.sendParticles(ElementFx.dust(element(), 1.0F), e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ(), 0, 0, -1, 0, 0.4);
            }
        }
    }

    // ---------------- Anulação absoluta: drena mana ----------------
    private void drainMana(ServerLevel sl, LivingEntity owner, SpellContext c) {
        for (LivingEntity e : livingInside(sl, 0)) {
            if (e instanceof ServerPlayer sp && isEnemy(c, owner, sp) && !shieldedFromThis(sp)) {
                MagicApi.get(sp).addMana(-NULLIFY_DRAIN);
                sl.sendParticles(Vfx.enchant(), sp.getX(), sp.getY() + 1.2, sp.getZ(), 12, 0.4, 0.5, 0.4, 0.6);
            }
        }
    }

    private void ambientFx(ServerLevel sl) {
        Element el = element();
        double r = radius();
        int n = Mth.clamp((int) (r / 3), 6, 24);
        for (int i = 0; i < n; i++) {
            Vec3 dir = upperDir(sl.random);
            Vec3 p = center().add(dir.scale(r));
            sl.sendParticles(isInverted() ? Vfx.reversePortal() : ElementFx.dust(elementAt(i), 1.5F), p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0);
        }
        if (isOpen()) ElementFx.ring(sl, center().add(0, 0.1, 0), el, r, ringPoints(r, 3));
        // Barreira rachada: lascas caindo das rachaduras
        float f = integrityFraction();
        if (!isOpen() && f < 0.6F) {
            BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
            int k = (int) ((0.6F - f) * 12) + 1;
            for (int i = 0; i < k; i++) {
                Vec3 p = center().add(upperDir(sl.random).scale(r));
                sl.sendParticles(glass, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    /** Direção aleatória na metade de cima de uma esfera (partículas na casca do domo). */
    public static Vec3 upperDir(RandomSource rnd) {
        double y = rnd.nextDouble();
        double a = rnd.nextDouble() * Math.PI * 2;
        double h = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
    }

    // ---------------- Domínio Simples ----------------
    private void simpleTick(ServerLevel sl, LivingEntity owner, SpellContext c, int active) {
        if (active % 20 == 0) {
            c = freshContext(sl);
            if (c == null) return;
            applyBeneficialToOwner(c, owner);
        }
        if (active % 5 == 0) {
            long now = sl.getGameTime();
            boolean heals = hasBeneficial(c);
            boolean strikes = hasHarmful(c);
            for (LivingEntity e : enemiesFirst(c, livingInside(sl, 0))) {
                if (e == owner) continue;
                Long next = counterCooldown.get(e.getUUID());
                if (next != null && now < next) continue;
                if (c.isAlly(e)) {
                    if (heals) {
                        c.resolveOnEntity(e, null);
                        counterCooldown.put(e.getUUID(), now + 20);
                    }
                    continue;
                }
                // Contra-ataque automático em quem invade o Domínio Simples (só se a magia tem algo que fere)
                if (!strikes || !isEnemy(c, owner, e)) continue;
                c.resolveOnEntity(e, null);
                counterCooldown.put(e.getUUID(), now + 20);
                Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                ElementFx.line(sl, owner.position().add(0, owner.getBbHeight() * 0.6, 0), p, elementAt((int) (now / 5)));
                sl.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
            }
            if (counterCooldown.size() > 64) counterCooldown.entrySet().removeIf(en -> en.getValue() < now);
        }
        if (active % 10 == 0) {
            sl.sendParticles(ElementFx.dust(elementAt(active / 10), 1.0F), getX(), getY() + 0.1, getZ(), 12,
                    radius() * 0.5, 0.05, radius() * 0.5, 0);
        }
    }

    // =====================================================================================
    //  Barreira: integridade, golpes, barra de chefe
    // =====================================================================================
    /**
     * Tira integridade (só chamado para o lado que pode quebrar — a regra dos lados é do núcleo/{@link DomainBarrier}).
     * @return quanto foi tirado
     */
    public float damageBarrier(float amount, boolean fromInside, @Nullable Entity attacker, @Nullable Vec3 hitPos) {
        if (!barrierUp() || !(level() instanceof ServerLevel sl) || !(amount > 0F)) return 0F;
        if (maxIntegrity <= 0) {
            LivingEntity owner = owner(sl);
            initBarrier(owner != null ? defaultIntegrity(owner, context(sl)) : 160F);
        }
        float taken = Math.min(integrity, amount);
        integrity -= taken;
        entityData.set(INTEGRITY, Mth.clamp(integrity / maxIntegrity, 0F, 1F));
        entityData.set(HIT_TIME, sl.getGameTime());
        Vec3 at = hitPos != null ? hitPos : DomainBarrier.wallTowards(this, attacker);
        DomainBarrier.crackFx(sl, this, at, integrityFraction(), fromInside);
        if (bossBar != null) bossBar.setProgress(integrityFraction());
        if (integrity <= 0.01F) {
            LivingEntity breaker = attacker instanceof LivingEntity le ? le
                    : attacker instanceof Projectile p && p.getOwner() instanceof LivingEntity po ? po : null;
            shatter(breaker);
        }
        return taken;
    }

    private void bossBarTick(ServerLevel sl, int active) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(barName(sl), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            bossBar.setProgress(integrityFraction());
        }
        if (active % 10 != 0) return;
        bossBar.setProgress(integrityFraction());
        double reach = radius() + 16;
        Vec3 c0 = center();
        Set<ServerPlayer> want = new HashSet<>();
        for (ServerPlayer p : sl.players()) {
            if (p.position().distanceToSqr(c0) <= reach * reach) want.add(p);
        }
        for (ServerPlayer p : new ArrayList<>(bossBar.getPlayers())) {
            if (!want.contains(p)) bossBar.removePlayer(p);
        }
        for (ServerPlayer p : want) bossBar.addPlayer(p);
    }

    /** "Barreira — <nome da magia>" (ou "Domínio de <dono>"); invertida ganha "(Invertida)". */
    private Component barName(ServerLevel sl) {
        SpellContext c = context(sl);
        String name = c != null ? c.recipe().name() : "";
        Component domain;
        if (name != null && !name.isEmpty()) {
            domain = Component.literal(name);
        } else {
            LivingEntity owner = owner(sl);
            domain = Component.translatable("bossbar.isekai_mahou.domain_of", owner != null ? owner.getDisplayName() : Component.literal("?"));
        }
        return Component.translatable(isInverted() ? "bossbar.isekai_mahou.domain_barrier_inverted" : "bossbar.isekai_mahou.domain_barrier", domain);
    }

    /** Cor da barra mais perto da cor do elemento (o Minecraft só tem 7). */
    private BossEvent.BossBarColor barColor() {
        int rgb = isInverted() ? 0xFFFFFF ^ color() : color();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (max <= 0F || delta / max < 0.22F) return BossEvent.BossBarColor.WHITE;
        float h;
        if (max == r) h = 60F * (((g - b) / delta) % 6F);
        else if (max == g) h = 60F * ((b - r) / delta + 2F);
        else h = 60F * ((r - g) / delta + 4F);
        if (h < 0) h += 360F;
        if (h < 20 || h >= 335) return BossEvent.BossBarColor.RED;
        if (h < 70) return BossEvent.BossBarColor.YELLOW;
        if (h < 165) return BossEvent.BossBarColor.GREEN;
        if (h < 250) return BossEvent.BossBarColor.BLUE;
        if (h < 300) return BossEvent.BossBarColor.PURPLE;
        return BossEvent.BossBarColor.PINK;
    }

    private void clearBossBar() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    // =====================================================================================
    //  Fim: desmoronar / despedaçar
    // =====================================================================================
    /** Fim normal (tempo acabou, dono saiu/morreu). {@code exhaust}: o dono recebe Exaustão Mágica. */
    public void collapse(boolean exhaust) {
        if (state() != ACTIVE || !(level() instanceof ServerLevel sl)) return;
        endedForReal = exhaust;
        setState(COLLAPSING);
        clearBossBar();
        landscape.restore(sl);
        unregister(sl);
        if (exhaust) {
            exhaustOwner(sl, 100);
            stageEnd(sl);
        }
        List<Element> els = elements();
        if (isSimple()) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
            for (Element e : els) ElementFx.burst(sl, center().add(0, 1, 0), e, 16 / els.size() + 4, radius() * 0.4);
            return;
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0F, 0.6F);
        // v1.3: o domo se desfaz numa onda do elemento (montada no cliente) em vez de 80 partículas de pó
        ElementFx.nova(sl, center(), element(), color(), radius() * 0.5F, 4, false);
        ElementFx.shockwave(sl, center().add(0, radius() * 0.5, 0), color(), radius() * 1.1F, 18, null);
        for (Element e : els) {
            DomainFx.far(sl, ElementFx.dust(e, 1.8F), getX(), getY() + radius() * 0.4, getZ(), 24 / els.size(),
                    radius() * 0.5, radius() * 0.3, radius() * 0.5, 0);
        }
    }

    /**
     * A barreira quebrou (integridade zerou): despedaça como vidro gigante, o domínio acaba e o dono fica exausto.
     * @param breaker quem deu o último golpe (pode ser null)
     */
    public void shatter(@Nullable LivingEntity breaker) {
        if (state() != ACTIVE || !(level() instanceof ServerLevel sl)) return;
        endedForReal = true;
        setState(SHATTERED);
        entityData.set(INTEGRITY, 0F);
        clearBossBar();
        landscape.restore(sl);
        unregister(sl);
        exhaustOwner(sl, 160);
        stageEnd(sl);
        double r = radius();
        Element el = element();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            sl.playSound(null, getX() + Math.cos(a) * r * 0.7, getY() + r * 0.4, getZ() + Math.sin(a) * r * 0.7,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0F, 0.5F + sl.random.nextFloat() * 0.3F);
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 1.4F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 2.5F, 0.6F);
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        // Estilhaços: menos pacotes (eram até 140×9 partículas) + clarão e nova do elemento montados no cliente
        int shards = Mth.clamp((int) (r * 1.5), 24, 72);
        for (int i = 0; i < shards; i++) {
            Vec3 p = center().add(upperDir(sl.random).scale(r));
            DomainFx.far(sl, glass, p.x, p.y, p.z, 4, 0.6, 0.6, 0.6, 0.1);
            if (i % 2 == 0) DomainFx.far(sl, ElementFx.dust(i % 4 == 0 ? el : elementAt(i / 2), 1.8F), p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0);
        }
        ElementFx.flash(sl, center().add(0, r * 0.5, 0), 0xFFFFFF, (float) Math.min(20, r));
        ElementFx.nova(sl, center(), el, color(), (float) r * 0.6F, 5, false);
        DomainFx.shake(sl, center(), r * 2 + 24, 1.8F, 22);
        LivingEntity owner = owner(sl);
        if (owner != null) {
            Announce.send(owner, Component.translatable("announce.isekai_mahou.barrier_broken"), 0xFFFFFF, Announce.STYLE_SYSTEM);
        }
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_shattered").withStyle(ChatFormatting.RED), true);
        }
        if (breaker instanceof ServerPlayer bp && breaker != owner) {
            bp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_barrier_broken").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private void shatterDebris(ServerLevel sl) {
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        double r = radius();
        int n = Mth.clamp((int) (r / 4), 5, 16);
        for (int i = 0; i < n; i++) {
            Vec3 p = center().add(upperDir(sl.random).scale(r * (0.8 + 0.2 * sl.random.nextDouble())));
            sl.sendParticles(glass, p.x, p.y, p.z, 4, 0.4, 0.4, 0.4, 0.05);
        }
    }

    /**
     * Magia programável (Domínio → Ao Terminar → ...): o domínio acabou de verdade (tempo, dono saiu/morreu ou foi
     * despedaçado) e o próximo estágio nasce no centro. Substituir/limpar o domo não conta.
     */
    private void stageEnd(ServerLevel sl) {
        SpellContext c = context(sl);
        // Dono morto: o domínio caiu com ele; nenhuma magia nova sai de um conjurador morto
        if (c == null || c.caster() == null || !c.caster().isAlive()) return;
        c.end(center().add(0, 1.0, 0), c.look());
    }

    private void exhaustOwner(ServerLevel sl, int ticks) {
        if (isSimple()) return;
        LivingEntity owner = owner(sl);
        if (owner == null) return;
        owner.addEffect(new MobEffectInstance(CoreRegistries.MAGIC_EXHAUSTION, ticks, 0));
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_exhausted").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    private void setState(byte st) {
        entityData.set(STATE, st);
        entityData.set(STATE_TIME, level().getGameTime());
        stateTicks = 0;
    }

    // =====================================================================================
    //  Choque de Domínios (chamado pela forma ao erguer um domínio novo)
    // =====================================================================================
    /**
     * v0.8: o choque não despedaça mais na hora. Ao erguer um domínio que encosta em domínios de OUTROS donos, o
     * choque é anunciado (clarão, tremor, letreiro) e a disputa segue pelas regras da barreira: o maior que envolve o
     * menor ataca a barreira dele por fora a cada pulso; sobreposição parcial vira cabo de guerra a cada 10 ticks.
     */
    public void clashOnCreate(ServerLevel sl) {
        boolean any = false;
        for (DomainDomeEntity d : around(sl, center(), radius())) {
            if (d == this || d.isSimple() || d.state() != ACTIVE || d.ownerId == null || d.ownerId.equals(ownerId)) continue;
            any = true;
            Vec3 mid = center().add(d.center()).scale(0.5).add(0, Math.min(radius(), d.radius()) * 0.5, 0);
            ElementFx.line(sl, center().add(0, 2, 0), d.center().add(0, 2, 0), element());
            DomainFx.far(sl, ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
            DomainFx.far(sl, ParticleTypes.FLASH, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
            sl.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0F, 0.5F);
            DomainFx.shake(sl, mid, 48 + radius(), 1.6F, 25);
        }
        if (!any) return;
        LivingEntity owner = owner(sl);
        if (owner != null) {
            Announce.send(owner, Component.translatable("announce.isekai_mahou.domain_clash"), 0xFFFFFF, Announce.STYLE_SYSTEM);
        }
    }

    /** Desfaz domos antigos do mesmo dono (um domínio e um Domínio Simples por conjurador). */
    public static void replaceOld(ServerLevel sl, UUID owner, boolean simple) {
        if (owner == null) return;
        for (DomainDomeEntity d : all(sl)) {
            if (owner.equals(d.ownerId) && d.isSimple() == simple && d.state() == ACTIVE) d.collapse(false);
        }
    }

    // =====================================================================================
    //  Entidade "fantasma": não colide, não leva dano, não é empurrada
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean canBeHitByProjectile() { return false; }

    @Override
    public boolean ignoreExplosion(Explosion explosion) { return true; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(radius() + 2);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double r = radius() + 160;
        return distance < r * r;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) LIVE.add(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide) LIVE.remove(this);
    }

    /**
     * Removido (descartado, /kill ou chunk descarregado): tira a barra de chefe e avisa o núcleo. A paisagem só é
     * devolvida quando o domo some de verdade (descarregar o chunk mantém tudo salvo no NBT).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            clearBossBar();
            unregister(sl);
            if (reason.shouldDestroy()) landscape.restore(level());
        }
        super.remove(reason);
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        // Estado ATUAL do contexto (orçamento de estágios já gasto), não o da criação
        if (ctx != null) spellTag = ctx.save();
        tag.put("Spell", spellTag);
        tag.putInt("Duration", duration);
        tag.putInt("Age", age);
        tag.putInt("Score", score);
        tag.putFloat("Radius", radius());
        tag.putInt("Color", color());
        tag.putString("Element", element().id);
        tag.putInt("Flags", flags());
        tag.putInt("Elements", entityData.get(ELEMENTS));
        tag.putInt("Accent", accentColor());
        tag.putByte("State", state());
        if (maxIntegrity > 0) {
            tag.putFloat("Integrity", integrity);
            tag.putFloat("MaxIntegrity", maxIntegrity);
        }
        landscape.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        spellTag = tag.getCompound("Spell");
        duration = tag.getInt("Duration");
        age = tag.getInt("Age");
        score = tag.getInt("Score");
        entityData.set(RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : 10.0F);
        entityData.set(COLOR, tag.getInt("Color"));
        Element el = Element.byId(tag.getString("Element"));
        entityData.set(ELEMENT, (el != null ? el : Element.ARCANE).ordinal());
        entityData.set(FLAGS, tag.getInt("Flags"));
        // Domos salvos na v0.6 não têm estes campos: um elemento só e destaque = cor clareada
        entityData.set(ELEMENTS, tag.getInt("Elements"));
        entityData.set(ACCENT, tag.contains("Accent") ? tag.getInt("Accent") : lighten(tag.getInt("Color"), 0.35F));
        // Domos salvos antes da v0.8 não têm integridade: ganham a de reserva no próximo golpe/ativação
        if (tag.contains("MaxIntegrity")) {
            maxIntegrity = tag.getFloat("MaxIntegrity");
            integrity = tag.getFloat("Integrity");
            entityData.set(INTEGRITY, Mth.clamp(integrity / Math.max(1F, maxIntegrity), 0F, 1F));
        }
        landscape.load(tag, level());
        long now = level().getGameTime();
        entityData.set(BORN, now - age);
        byte st = tag.getByte("State");
        entityData.set(STATE, st);
        entityData.set(STATE_TIME, now);
        // Domo que já estava acabando quando o mundo salvou: termina logo (a paisagem já foi devolvida).
        if (st != ACTIVE) stateTicks = SHATTER_TICKS;
    }
}
