package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.DynamicOps;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.core.stack.DamageCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Contexto de UMA conjuração (servidor). Formas usam para aplicar os efeitos nos alvos;
 * efeitos usam os helpers de dano/cura/status.
 *
 * Contextos podem ser salvos ({@link #save()}) e recarregados ({@link #load}) por entidades que
 * guardam a magia (projéteis, zonas, torres, runas...).
 *
 * <h3>v0.7 — magias programáveis</h3>
 * Cada contexto representa UMA instância de um estágio ({@link #stage()}). Estágios 2+ nascem num ponto
 * ({@link #eyePos()}/{@link #look()}/{@link #anchor()}) quando o estágio anterior chama {@link #impact} ou
 * {@link #end}, ou quando um alvo morre pelo dano dele (Ao Abater). Todos os contextos derivados de uma mesma
 * conjuração (estágios, saltos, visões por elemento, cópias) compartilham o conjunto de alvos atingidos e um
 * orçamento (24 estágios-filho, 64 saltos, 8 pulos pagos para mobs).
 */
public class SpellContext {
    /** Máximo de estágios-filho criados por uma conjuração raiz. */
    public static final int MAX_CHILD_STAGES = 24;
    /** Máximo de saltos de {@code link_leap} por conjuração raiz. */
    public static final int MAX_LEAPS = 64;
    /** Alcance da busca do próximo alvo de {@code link_leap}. */
    public static final double LEAP_RANGE = 16.0;
    /** Insaciável: alcance, intervalo, queda de poder por pulo e teto de pulos por corrente. */
    public static final double RELENTLESS_RANGE = 8.0;
    public static final int RELENTLESS_INTERVAL = 3;
    public static final float RELENTLESS_FALLOFF = 0.92F;
    public static final int RELENTLESS_MAX_HOPS = 64;
    /** Pulos pagos (Saltar/Insaciável) que um mob (ou castFrom) pode dar por conjuração. */
    public static final int MOB_HOP_CAP = 8;
    /** Ticks (além do Atraso/Eco da forma-filho) em que o alvo do estágio anterior fica ignorado. */
    public static final int PREVIOUS_TARGET_TICKS = 10;
    /** Alcance do Encadear clássico (v0.6). */
    private static final double CHAIN_RANGE = 6.0;

    private final ServerLevel level;
    private final LivingEntity caster;
    private final UUID casterId;
    private final Element element;
    private final SpellRecipe recipe;
    private final ParsedSpell spell;
    private float powerMult = 1.0F;
    private boolean selfCast;
    /** Entidades já atingidas nesta conjuração (evita encadear/saltar no mesmo alvo). Compartilhado entre derivados. */
    private final Set<UUID> chained;

    // ---------- v0.7 ----------
    /** Magia inteira (estágio 0). null = descobrir pela receita (contexto criado à mão com um estágio-filho). */
    private ParsedSpell root;
    /** Índice do estágio; -1 = descobrir pela receita. */
    private int stage;
    private Vec3 origin;
    private Vec3 dir;
    private Vec3 anchor;
    /** Entidade atingida pelo estágio anterior (o novo estágio a ignora para não acertá-la de novo na hora). */
    private UUID lastHit;
    /**
     * Até quando (tempo de jogo) {@link #lastHit} é ignorado. Só "na hora" em que o estágio nasce: uma torre ou
     * zona criada como estágio não pode ignorar para sempre o inimigo que estava no ponto de impacto.
     */
    private long ignoreUntil;
    /** Conjuração de JOGADOR (performCast): liga as visões por elemento e paga saltos com mana. */
    private boolean playerCast;
    private final Budget budget;
    private final StageState state;
    /** != null: este contexto é uma visão por elemento de {@code viewOf}. */
    private final SpellContext viewOf;
    private Map<Element, SpellContext> views;

    public SpellContext(ServerLevel level, LivingEntity caster, Element element, SpellRecipe recipe, ParsedSpell spell) {
        this(level, caster, caster != null ? caster.getUUID() : null, element, recipe, spell);
    }

    private SpellContext(ServerLevel level, LivingEntity caster, UUID casterId, Element element, SpellRecipe recipe, ParsedSpell spell) {
        this.level = level;
        this.caster = caster;
        this.casterId = casterId;
        this.element = element;
        this.recipe = recipe;
        this.spell = spell;
        this.chained = new HashSet<>();
        this.budget = new Budget();
        this.state = new StageState();
        this.viewOf = null;
        // A raiz sempre custa ≥ 1 e os estágios-filho custam 0: dá para saber se recebemos a raiz
        if (spell.manaCost() > 0) {
            this.root = spell;
            this.stage = 0;
        } else {
            this.stage = -1;
        }
    }

    /** Derivado de outro contexto: compartilha conjurador, receita, alvos atingidos e orçamento. */
    private SpellContext(SpellContext from, ParsedSpell spell, int stage, Element element, StageState state, SpellContext viewOf) {
        this.level = from.level;
        this.caster = from.caster;
        this.casterId = from.casterId;
        this.recipe = from.recipe;
        this.element = element;
        this.spell = spell;
        this.chained = from.chained;
        this.budget = from.budget;
        this.state = state;
        this.viewOf = viewOf;
        this.root = from.rootSpell();
        this.stage = stage;
        this.powerMult = from.powerMult;
        this.playerCast = from.playerCast;
        this.origin = from.origin;
        this.dir = from.dir;
        this.anchor = from.anchor;
        this.lastHit = from.lastHit;
        this.ignoreUntil = from.ignoreUntil;
    }

    // =====================================================================================
    //  Getters
    // =====================================================================================
    public ServerLevel level() { return level; }
    /** Quem conjurou (pode ser null num contexto recarregado se o conjurador saiu do mundo). */
    public LivingEntity caster() { return caster; }
    public UUID casterId() { return casterId; }
    public ServerPlayer casterPlayer() { return caster instanceof ServerPlayer sp ? sp : null; }
    /** Elemento deste contexto (numa visão por elemento, o elemento do glifo). */
    public Element element() { return element; }
    public SpellRecipe recipe() { return recipe; }
    /** O ESTÁGIO que este contexto executa (no estágio 0, a magia raiz). */
    public ParsedSpell spell() { return spell; }
    public SpellStats formStats() { return spell.formStats(); }
    public float powerMult() { return powerMult; }
    public void setPowerMult(float m) { powerMult = m; }
    public RandomSource random() { return level.random; }

    /** Cor da magia (a escolhida pelo jogador, ou a do elemento). ARGB. */
    public int color() {
        return recipe.color() >= 0 ? 0xFF000000 | recipe.color() : element.argb();
    }

    /** Ponto de saída: a origem do estágio, se houver; senão os olhos do conjurador. */
    public Vec3 eyePos() {
        if (origin != null) return origin;
        return caster != null ? caster.getEyePosition() : Vec3.ZERO;
    }

    /** Direção: a do estágio, se houver; senão para onde o conjurador olha. */
    public Vec3 look() {
        if (dir != null) return dir;
        return caster != null ? caster.getLookAngle() : new Vec3(0, 0, 1);
    }

    // ---------- v0.7 ----------
    /** A magia inteira (estágio 0), com os custos totais. */
    public ParsedSpell rootSpell() {
        if (root == null) {
            SpellParser.Result r = SpellParser.parse(recipe, element, null);
            root = r.ok() && r.spell().stageCount() >= spell.stageCount() ? r.spell() : spell;
        }
        return root;
    }

    /** Índice do estágio que este contexto executa (0 = primeiro). */
    public int stage() {
        if (stage < 0) stage = Math.max(0, rootSpell().stageCount() - spell.stageCount());
        return stage;
    }

    /** Este estágio tem origem/direção próprias (nasceu de uma ligação, de castFrom ou de withOrigin). */
    public boolean hasOrigin() { return origin != null; }

    /** Ponto de impacto que gerou este estágio (para formas de ÁREA nascerem ali). Pode ser null. */
    @Nullable
    public Vec3 anchor() { return anchor; }

    /**
     * Entidade que o estágio anterior ACABOU de atingir (formas de disparo devem ignorá-la), ou null.
     * Vale só enquanto o estágio nasce: {@link #PREVIOUS_TARGET_TICKS} ticks depois do momento em que a forma
     * roda (Atraso/Eco da forma inclusos). Depois disso volta a null.
     */
    @Nullable
    public UUID previousTarget() {
        return lastHit != null && level.getGameTime() <= ignoreUntil ? lastHit : null;
    }

    /** A forma deve ignorar esta entidade (acabou de ser atingida pelo estágio anterior). */
    public boolean ignores(Entity e) {
        UUID prev = previousTarget();
        return e != null && prev != null && prev.equals(e.getUUID());
    }

    /** Conjuração de jogador (tecla/gatilho). Mobs, castAsMob e castFrom retornam false. */
    public boolean isPlayerCast() { return playerCast; }

    /** Este contexto é uma visão por elemento (efeito de outro elemento numa magia multi-elemento). */
    public boolean isView() { return viewOf != null; }

    /** Elemento primário da magia (o do slot). Igual a {@link #element()} fora das visões. */
    public Element primaryElement() { return viewOf != null ? viewOf.element : element; }

    /**
     * Custo de mana de um salto (Saltar/Insaciável): 15% do custo da magia, no mínimo 2 × {@link SpellCost#COST_SCALE}.
     * v1.0: o salto continua pago na hora (não entra no custo por cópia).
     */
    public float hopCost() {
        return Math.max(2.0F * SpellCost.COST_SCALE, rootSpell().manaCost() * 0.15F);
    }

    /**
     * Marca como conjuração de JOGADOR (visões por elemento; Saltar/Insaciável pagos com mana). O núcleo faz isso em
     * {@code performCast}; use só em contextos que um módulo recria para o jogador (ex.: Selo na arma).
     * Sem efeito se o conjurador não for jogador.
     */
    public void markPlayerCast() {
        if (caster instanceof Player || caster == null) playerCast = true;
    }

    void setOrigin(Vec3 origin, Vec3 dir) {
        this.origin = origin;
        this.dir = dir == null || dir.lengthSqr() < 1.0E-8 ? null : dir.normalize();
    }

    // =====================================================================================
    //  Derivados públicos
    // =====================================================================================

    /**
     * Cópia do mesmo estágio com outro multiplicador de poder (valor absoluto). É uma NOVA instância
     * (conta seus próprios disparos de ligação), mas compartilha alvos atingidos e orçamento.
     */
    public SpellContext copy(float powerMult) {
        SpellContext base = owner();
        SpellContext c = new SpellContext(base, base.spell, base.stage(), base.element, new StageState(), null);
        c.powerMult = powerMult;
        return viewOf != null ? c.forElement(element) : c;
    }

    /**
     * Cópia que sai de outro ponto/direção (torres, portais, chefes...). Mesmo estágio e poder.
     * A âncora é descartada: formas de área passam a mirar a partir da nova origem.
     */
    public SpellContext withOrigin(Vec3 origin, Vec3 dir) {
        SpellContext base = owner();
        SpellContext c = new SpellContext(base, base.spell, base.stage(), base.element, new StageState(), null);
        c.powerMult = powerMult;
        c.anchor = null;
        c.setOrigin(origin, dir);
        return viewOf != null ? c.forElement(element) : c;
    }

    /**
     * Visão deste contexto com outro elemento: dano, reações, cor e visual passam a ser do elemento informado,
     * mas alvos atingidos, orçamento e disparos de ligação são os mesmos. O núcleo usa automaticamente
     * nos efeitos de outro elemento em conjurações de jogador.
     */
    public SpellContext forElement(Element e) {
        SpellContext base = owner();
        if (e == null || e == base.element) return base;
        if (base.views == null) base.views = new EnumMap<>(Element.class);
        SpellContext v = base.views.computeIfAbsent(e, k -> new SpellContext(base, base.spell, base.stage(), k, base.state, base));
        v.powerMult = base.powerMult;
        v.selfCast = base.selfCast;
        return v;
    }

    /** O contexto "dono" do estado do estágio (a base, se este for uma visão). */
    private SpellContext owner() {
        return viewOf != null ? viewOf : this;
    }

    /** Contexto em que o efeito roda: a visão do elemento do glifo, se ele não é do elemento primário. */
    private SpellContext contextFor(Glyph g) {
        SpellContext base = owner();
        if (!playerCast || g.origin() != null || base.element.canUseGlyphOf(g.element())) return base;
        return base.forElement(g.element());
    }

    // =====================================================================================
    //  Resolução: aplicar os efeitos
    // =====================================================================================

    /** Aplica todos os efeitos em você mesmo (forma Si Mesmo, Aura...). */
    public void resolveOnSelf() {
        if (caster == null) return;
        boolean old = selfCast;
        selfCast = true;
        resolveOnEntity(caster, null);
        selfCast = old;
    }

    /** Aplica todos os efeitos numa entidade (respeitando aliados/inimigos e os aumentos de Atraso/Repetir/Encadear). */
    public void resolveOnEntity(Entity target, Entity direct) {
        resolveEntityInternal(target, direct);
        if (target == caster) return;
        chained.add(target.getUUID());
        // Forma que ainda não avisa os próprios impactos: o núcleo avisa por ela (Ao Acertar no estágio 0)
        if (!spell.form().stageable()) impact(centerOf(target), look(), target);
        chainFrom(target, direct);
    }

    /** Encadear (da forma ou de um efeito) e Insaciável, a partir do alvo atingido. */
    private void chainFrom(Entity target, Entity direct) {
        SpellStats fs = spell.formStats();
        if (fs.relentless()) {
            relentlessHop(target, direct, null, Math.max(0, fs.chain), 0);
        } else if (fs.chain > 0) {
            // Encadear clássico (igual à v0.6): pulos instantâneos para inimigos ainda não atingidos
            Entity from = target;
            for (int i = 0; i < fs.chain; i++) {
                Entity next = nearestEnemy(from, CHAIN_RANGE, chained);
                if (next == null) break;
                ElementFx.line(level, centerOf(from), centerOf(next), element);
                chained.add(next.getUUID());
                resolveEntityInternal(next, direct);
                from = next;
            }
        }
        // Encadear/Insaciável colocados depois de um EFEITO: só aquele efeito pula
        for (ParsedSpell.EffectEntry entry : spell.effects()) {
            SpellStats st = entry.stats();
            if (st.relentless()) {
                relentlessHop(target, direct, entry, Math.max(0, st.chain), 0);
            } else if (st.chain > 0) {
                Set<UUID> seen = new HashSet<>();
                seen.add(target.getUUID());
                Entity from = target;
                Element fx = contextFor(entry.effect()).element;
                for (int i = 0; i < st.chain; i++) {
                    Entity next = nearestEnemy(from, CHAIN_RANGE, seen);
                    if (next == null) break;
                    ElementFx.line(level, centerOf(from), centerOf(next), fx);
                    seen.add(next.getUUID());
                    chained.add(next.getUUID());
                    applyIfValid(entry, next);
                    from = next;
                }
            }
        }
    }

    /**
     * Um pulo do Insaciável (agendado). Os primeiros {@code freeHops} pulos (os do Encadear) são grátis; depois
     * cada pulo paga {@link #hopCost()}. Prefere inimigos ainda não atingidos; sem eles, volta a qualquer um
     * que não seja o atual. Para quando não há alvo, o mana acaba ou chega a {@link #RELENTLESS_MAX_HOPS}.
     */
    private void relentlessHop(Entity from, Entity direct, @Nullable ParsedSpell.EffectEntry only, int freeHops, int hop) {
        SpellScheduler.schedule(RELENTLESS_INTERVAL, () -> {
            if (hop >= RELENTLESS_MAX_HOPS || from.level() != level) return;
            LivingEntity next = nearestEnemy(from, RELENTLESS_RANGE, chained);
            if (next == null) next = nearestEnemy(from, RELENTLESS_RANGE, null);
            if (next == null) return;
            if (hop >= freeHops && !payHop(hopCost())) return;
            SpellContext hc = sharedWithPower(powerMult * (float) Math.pow(RELENTLESS_FALLOFF, hop + 1));
            Element fx = only != null ? hc.contextFor(only.effect()).element : hc.element;
            ElementFx.line(level, centerOf(from), centerOf(next), fx);
            chained.add(next.getUUID());
            if (only == null) hc.resolveEntityInternal(next, direct);
            else hc.applyIfValid(only, next);
            relentlessHop(next, direct, only, freeHops, hop + 1);
        });
    }

    /**
     * Mesmo estágio e mesma instância (disparos compartilhados), com outro poder. Sempre derivado da base
     * (não de uma visão), para as visões dos efeitos herdarem o poder reduzido.
     */
    private SpellContext sharedWithPower(float pm) {
        SpellContext base = owner();
        SpellContext c = new SpellContext(base, base.spell, base.stage(), base.element, base.state, null);
        c.powerMult = pm;
        return c;
    }

    /** Inimigo vivo mais próximo de {@code from} (≠ from, ≠ conjurador, ≠ aliado), fora de {@code exclude}. */
    @Nullable
    private LivingEntity nearestEnemy(Entity from, double range, @Nullable Set<UUID> exclude) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                e -> e.isAlive() && e != caster && e != from && !e.isSpectator()
                        && (exclude == null || !exclude.contains(e.getUUID())) && !isAlly(e))) {
            double d = e.distanceToSqr(from);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private void resolveEntityInternal(Entity target, Entity direct) {
        for (ParsedSpell.EffectEntry entry : spell.effects()) applyIfValid(entry, target);
    }

    private void applyIfValid(ParsedSpell.EffectEntry entry, Entity target) {
        if (isValidTarget(entry.effect(), entry.stats(), target)) applyEffect(entry, target);
    }

    /**
     * Aplica UM efeito numa entidade, SEM checar aliado/inimigo (quem chama decide), respeitando Atraso/Repetir
     * do efeito e a visão por elemento. Use em vez de {@code entry.effect().onEntity(ctx, ...)} direto.
     */
    public void applyEffect(ParsedSpell.EffectEntry entry, Entity target) {
        SpellContext c = contextFor(entry.effect());
        schedule(entry.stats(), () -> {
            if (!target.isAlive()) return;
            entry.effect().onEntity(c, target, entry.stats());
            // Maestria: uso real (o efeito caiu num alvo); o GlyphMastery decide se e quanto vale
            GlyphMastery.onEntity(c, target);
        });
    }

    /** Aplica os efeitos que mexem em blocos (os que pedem blocos, ou todos se houver o aumento Sensível). */
    public void resolveOnBlock(BlockHitResult hit, Entity direct) {
        boolean sensitive = spell.formStats().sensitive;
        for (ParsedSpell.EffectEntry entry : spell.effects()) {
            if (!(entry.effect().wantsBlocks() || sensitive || entry.stats().sensitive)) continue;
            SpellContext c = contextFor(entry.effect());
            // Maestria: só efeitos FEITOS para blocos contam como trabalho (Sensível sozinho não)
            boolean work = entry.effect().wantsBlocks();
            schedule(entry.stats(), () -> {
                // foto dos blocos antes: só vale como trabalho se o efeito MUDOU alguma coisa ali (null = nem confere)
                net.minecraft.world.level.block.state.BlockState[] before =
                        work ? GlyphMastery.blockSnapshot(c, hit.getBlockPos()) : null;
                entry.effect().onBlock(c, hit, entry.stats());
                if (before != null) GlyphMastery.onBlock(c, hit.getBlockPos(), before);
            });
        }
    }

    public void resolveHit(HitResult hit, Entity direct) {
        if (hit instanceof EntityHitResult ehr) resolveOnEntity(ehr.getEntity(), direct);
        else if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) resolveOnBlock(bhr, direct);
    }

    /** Aplica em todas as entidades numa esfera. Se {@code centerBlock}, também no bloco embaixo do centro. */
    public void resolveArea(Vec3 center, float radius, Entity direct, boolean centerBlock) {
        AABB box = new AABB(center, center).inflate(radius);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator()
                && e.distanceToSqr(center) <= radius * radius && (e instanceof LivingEntity))) {
            resolveEntityInternal(e, direct);
        }
        if (centerBlock) {
            BlockPos pos = BlockPos.containing(center);
            if (level.getBlockState(pos).isAir()) pos = pos.below();
            resolveOnBlock(new BlockHitResult(center, Direction.UP, pos, false), direct);
        }
    }

    /** Lista de entidades vivas numa esfera que seriam alvos válidos de dano (útil para formas). */
    public List<LivingEntity> enemiesAround(Vec3 center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e != caster && !isAlly(e) && e.distanceToSqr(center) <= radius * radius)) {
            list.add(e);
        }
        return list;
    }

    private void schedule(SpellStats stats, Runnable r) {
        int delay = stats.delayTicks();
        if (delay > 0) SpellScheduler.schedule(delay, r);
        else r.run();
        for (int i = 1; i <= Math.max(0, stats.repeat); i++) {
            SpellScheduler.schedule(delay + 20 * i, r);
        }
    }

    // =====================================================================================
    //  Estágios (v0.7): impacto, fim, abate, salto
    // =====================================================================================

    /**
     * A forma atingiu algo DE VERDADE neste ponto (entidade ou bloco). Dispara {@code link_hit} (o próximo estágio
     * nasce aqui, na direção {@code dir}) ou {@code link_leap} (repete este estágio a partir de {@code hit} em
     * direção ao próximo inimigo). Sem ligação, não faz nada — pode chamar sempre.
     *
     * @param hit entidade atingida, ou null se foi um bloco
     */
    public void impact(Vec3 pos, Vec3 dir, @Nullable Entity hit) {
        SpellContext o = owner();
        if (o != this) { o.impact(pos, dir, hit); return; }
        LinkGlyph.LinkKind kind = spell.linkKind();
        if (kind != LinkGlyph.LinkKind.HIT && kind != LinkGlyph.LinkKind.LEAP) return;
        if (hit != null) {
            if (hit == caster) return;
            // O mesmo alvo no mesmo tick conta uma vez (ex.: a forma avisa e o núcleo também)
            long now = level.getGameTime();
            if (hit.getUUID().equals(state.lastImpact) && state.lastImpactTick == now) return;
            state.lastImpact = hit.getUUID();
            state.lastImpactTick = now;
        }
        Vec3 d = safeDir(dir);
        if (kind == LinkGlyph.LinkKind.LEAP) {
            if (hit != null) leapFrom(hit, d);
            return;
        }
        // Num bloco, recua um pouco para o próximo estágio não nascer dentro dele
        Vec3 start = hit == null ? pos.subtract(d.scale(0.3)) : pos;
        fireNext(start, d, pos, hit);
    }

    /**
     * O estágio terminou neste ponto (projétil expirou, zona acabou, feixe chegou ao fim). Dispara {@code link_end}.
     * Sem ligação, não faz nada.
     */
    public void end(Vec3 pos, Vec3 dir) {
        SpellContext o = owner();
        if (o != this) { o.end(pos, dir); return; }
        if (spell.linkKind() != LinkGlyph.LinkKind.END) return;
        fireNext(pos, safeDir(dir), pos, null);
    }

    /** Um alvo morreu pelo dano deste estágio: dispara {@code link_kill}. */
    private void onKilled(LivingEntity victim) {
        SpellContext o = owner();
        if (o.spell.linkKind() != LinkGlyph.LinkKind.KILL) return;
        o.fireNext(centerOf(victim), o.look(), victim.position(), victim);
    }

    /** Agenda o próximo estágio (1 tick depois, sem SpellCastEvent), respeitando os limites. */
    private void fireNext(Vec3 start, Vec3 d, Vec3 at, @Nullable Entity hit) {
        if (!spell.hasNext()) return;
        if (state.fires >= Math.max(1, spell.formStats().count())) return;
        if (budget.children >= MAX_CHILD_STAGES) return;
        state.fires++;
        budget.children++;
        SpellContext child = new SpellContext(this, spell.next().spell(), stage() + 1, element, new StageState(), null);
        child.born(start, d, at, hit);
        SpellScheduler.schedule(1, () -> SpellCaster.runForm(child));
    }

    /** Posiciona um estágio recém-criado (que roda 1 tick depois) e marca por quanto tempo ignora o alvo anterior. */
    private void born(Vec3 start, Vec3 d, Vec3 at, @Nullable Entity hit) {
        origin = start;
        dir = d;
        anchor = at;
        lastHit = hit != null ? hit.getUUID() : null;
        SpellStats fs = spell.formStats();
        ignoreUntil = level.getGameTime() + 1 + fs.delayTicks() + 20L * Math.max(0, fs.repeat) + PREVIOUS_TARGET_TICKS;
    }

    /**
     * Saltar: repete este estágio a partir de {@code from} em direção ao inimigo mais próximo ainda não atingido.
     * Origem = centro de {@code from}; direção = rumo ao próximo; âncora = pés do próximo (formas de área caem nele).
     */
    private void leapFrom(Entity from, Vec3 d) {
        if (state.fires >= Math.max(1, spell.formStats().count())) return;
        if (budget.leaps >= MAX_LEAPS) return;
        chained.add(from.getUUID());
        LivingEntity next = nearestEnemy(from, LEAP_RANGE, chained);
        if (next == null || !payHop(hopCost())) return;
        state.fires++;
        budget.leaps++;
        Vec3 start = centerOf(from);
        Vec3 to = centerOf(next).subtract(start);
        SpellContext child = new SpellContext(this, spell, stage(), element, new StageState(), null);
        child.born(start, to.lengthSqr() < 1.0E-6 ? d : to.normalize(), next.position(), from);
        SpellScheduler.schedule(1, () -> SpellCaster.runForm(child));
    }

    /**
     * Paga um salto/pulo. Jogador: gasta mana (criativo: grátis; offline: falha). Mobs e castFrom: contador
     * limitado a {@link #MOB_HOP_CAP} por conjuração.
     * @return true se pagou (pode pular)
     */
    public boolean payHop(float cost) {
        if (playerCast) {
            ServerPlayer sp = casterId == null ? null : level.getServer().getPlayerList().getPlayer(casterId);
            if (sp == null || !sp.isAlive()) return false;
            if (sp.getAbilities().instabuild) return true;
            return MagicApi.get(sp).consumeMana(cost);
        }
        if (budget.mobHops >= MOB_HOP_CAP) return false;
        budget.mobHops++;
        return true;
    }

    private Vec3 safeDir(Vec3 d) {
        return d == null || d.lengthSqr() < 1.0E-8 ? look() : d.normalize();
    }

    private static Vec3 centerOf(Entity e) {
        return e.position().add(0, e.getBbHeight() / 2, 0);
    }

    // =====================================================================================
    //  Aliados e alvos
    // =====================================================================================
    public boolean isAlly(Entity e) {
        if (e == null) return false;
        if (casterId != null && e.getUUID().equals(casterId)) return true;
        if (e instanceof OwnableEntity own && casterId != null && casterId.equals(own.getOwnerUUID())) return true;
        // v1.0 (A2): grupo/facção aliada = aliado; duelo/guerra de cidades = inimigo mesmo com PvP desligado
        Boolean social = com.renanpontes.isekaimahou.core.api.Party.allyOverride(caster, e);
        if (social != null) return social;
        if (caster != null && caster.isAlliedTo(e)) return true;
        // Outros jogadores são aliados se o PvP do servidor estiver desligado
        return caster instanceof Player && e instanceof Player && !level.getServer().isPvpAllowed();
    }

    /** Regra de alvo de cada efeito. O próprio conjurador só é alvo quando a forma é "em si mesmo". */
    public boolean isValidTarget(EffectGlyph effect, SpellStats stats, Entity target) {
        if (target == caster || (casterId != null && target.getUUID().equals(casterId))) return selfCast;
        boolean ally = isAlly(target);
        if (stats.alliesOnly || spell.formStats().alliesOnly) return ally;
        if (stats.enemiesOnly || spell.formStats().enemiesOnly) return !ally;
        if (effect.isBeneficial()) return ally != stats.inverted;
        return !ally;
    }

    // =====================================================================================
    //  Helpers para efeitos
    // =====================================================================================
    public DamageSource damageSource(Entity direct) {
        Holder<net.minecraft.world.damagesource.DamageType> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        Entity causing = caster;
        return new DamageSource(type, direct != null ? direct : causing, causing);
    }

    /**
     * Causa dano mágico do elemento da magia. Aplica: poder dos aumentos e da conjuração, crítico,
     * reações elementais, resistência/fraqueza do alvo, Vampírico, carga de ultimate e marcação para XP.
     * Se o alvo morrer, dispara a ligação Ao Abater do estágio.
     * <p>
     * v0.8: em criaturas posta {@link SpellDamageEvent.Pre} (depois das reações; cancelável, muda o valor) e
     * {@link SpellDamageEvent.Post} (dano real, overkill, abate) em volta do {@code hurt}.
     * @return dano final (0 se um {@code SpellDamageEvent.Pre} anulou)
     */
    public float damage(Entity target, float base, SpellStats stats, Entity direct) {
        return damage(target, base, stats, direct, element);
    }

    /** Igual ao outro, mas com elemento explícito (ex.: efeito de fusão que causa dano de um dos pais). */
    public float damage(Entity target, float base, SpellStats stats, Entity direct, Element el) {
        // v1.0: inflação de números — todo dano de magia/técnica ×DAMAGE_SCALE (vida do jogador também cresceu)
        float amount = base * stats.power() * powerMult * casterSpellPower() * SpellCost.damageScale();
        if (stats.crit && level.random.nextFloat() < 0.25F) {
            amount *= 1.75F;
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2,
                    target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
        DamageSource src = damageSource(direct);
        if (target instanceof LivingEntity le) {
            amount = ElementReactions.onSpellHit(this, le, el, amount);
            ElementReactions.mark(le, el, casterId, level.getGameTime());
            float requested = amount;
            SpellDamageEvent.Pre pre = new SpellDamageEvent.Pre(this, le, el, stats, direct, src, amount);
            if (NeoForge.EVENT_BUS.post(pre).isCanceled()) return 0F;
            amount = pre.getAmount();
            // Um ouvinte zerou o dano: nada acontece (sem ninguém mexer, segue igual à v0.7, mesmo com dano 0)
            if (amount != requested && !(amount > 0F)) return 0F;
            float before = le.getHealth();
            boolean wasAlive = le.isAlive() && !le.isDeadOrDying();
            boolean hurt;
            float applied;
            // Mede o dano real (depois de armadura/resistências) para o overkill do evento Post
            DamageCapture.begin(le);
            try {
                hurt = le.hurt(src, amount);
            } finally {
                applied = DamageCapture.end(le);
            }
            float dealt = 0F, overkill = 0F;
            boolean killed = false;
            if (hurt) {
                dealt = Math.max(0, before - le.getHealth());
                killed = wasAlive && le.isDeadOrDying();
                if (killed) overkill = Math.max(0F, (applied >= 0F ? applied : dealt) - before);
                if (stats.vampiric && caster != null) caster.heal(dealt * 0.25F);
                ServerPlayer sp = casterPlayer();
                // A carga de ultimate é do elemento primário (slot), mesmo quando o efeito é de outro elemento
                if (sp != null) MagicApi.onSpellDamage(sp, viewOf != null ? viewOf.element : el, dealt);
                if (killed) onKilled(le);
            }
            NeoForge.EVENT_BUS.post(new SpellDamageEvent.Post(this, le, el, stats, direct, src, amount, hurt, dealt, overkill, killed));
        } else {
            target.hurt(src, amount);
        }
        return amount;
    }

    /** Atributo isekai_mahou:spell_power do conjurador (equipamentos), 1.0 se não tiver. */
    public float casterSpellPower() {
        if (caster == null) return 1.0F;
        var inst = caster.getAttribute(CoreRegistries.SPELL_POWER);
        return inst == null ? 1.0F : (float) inst.getValue();
    }

    public void heal(LivingEntity target, float base, SpellStats stats) {
        target.heal(base * stats.power() * powerMult * casterSpellPower() * com.renanpontes.isekaimahou.core.Progression.HEAL_SCALE);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3,
                target.getZ(), 2, 0.3, 0.1, 0.3, 0);
    }

    /** Aplica um efeito de status com a duração ajustada pelos aumentos de duração. */
    public void status(LivingEntity target, Holder<MobEffect> effect, int baseTicks, int amplifier, SpellStats stats) {
        target.addEffect(new MobEffectInstance(effect, stats.duration(baseTicks), amplifier), caster);
    }

    // =====================================================================================
    //  Salvar/carregar (para entidades que guardam a magia)
    // =====================================================================================

    /**
     * Salva o contexto. v0.7 também guarda estágio, origem, direção, âncora, alvo anterior, disparos e orçamento
     * (chaves opcionais: NBT antigo sem elas carrega como estágio 0). Salve de novo quando a entidade for salva
     * para não perder os disparos já feitos.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (casterId != null) tag.putUUID("caster", casterId);
        // "element" continua sendo o elemento DESTE contexto (entidades leem para o visual); numa visão,
        // "primary" guarda o elemento do slot para o load recriar a visão por cima do contexto certo
        tag.putString("element", element.id);
        if (viewOf != null) tag.putString("primary", viewOf.element.id);
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        SpellRecipe.CODEC.encodeStart(ops, recipe).result().ifPresent(t -> tag.put("recipe", t));
        tag.putFloat("power", powerMult);
        int st = stage();
        if (st > 0) tag.putInt("stage", st);
        if (origin != null) tag.put("origin", vec(origin));
        if (dir != null) tag.put("dir", vec(dir));
        if (anchor != null) tag.put("anchor", vec(anchor));
        if (lastHit != null) {
            tag.putUUID("lastHit", lastHit);
            tag.putLong("ignoreUntil", ignoreUntil);
        }
        if (state.fires > 0) tag.putInt("fires", state.fires);
        if (playerCast) tag.putBoolean("player", true);
        if (budget.children > 0 || budget.leaps > 0 || budget.mobHops > 0) {
            CompoundTag b = new CompoundTag();
            b.putInt("children", budget.children);
            b.putInt("leaps", budget.leaps);
            b.putInt("mobHops", budget.mobHops);
            tag.put("budget", b);
        }
        return tag;
    }

    /**
     * Recria o contexto (sem checar conhecimento de glifos). Retorna null se a magia ficou inválida.
     * Um contexto salvo a partir de uma visão por elemento volta como visão (mesmo {@link #element()}).
     */
    public static SpellContext load(ServerLevel level, CompoundTag tag) {
        if (tag == null || !tag.contains("recipe")) return null;
        Element el = Element.byId(tag.getString("element"));
        if (el == null) return null;
        Element primary = tag.contains("primary", Tag.TAG_STRING) ? Element.byId(tag.getString("primary")) : null;
        Element base = primary != null ? primary : el;
        SpellRecipe recipe = SpellRecipe.CODEC.parse(NbtOps.INSTANCE, tag.get("recipe")).result().orElse(null);
        if (recipe == null) return null;
        UUID id = tag.hasUUID("caster") ? tag.getUUID("caster") : null;
        LivingEntity caster = id != null && level.getEntity(id) instanceof LivingEntity le ? le : null;
        // Magia de jogador guardada numa entidade (zona, torre...): mantém a maestria de quem conjurou, se ele está aqui
        com.renanpontes.isekaimahou.core.MagicData mastery = tag.getBoolean("player") && caster instanceof ServerPlayer sp
                && !(sp instanceof net.neoforged.neoforge.common.util.FakePlayer) ? MagicApi.get(sp) : null;
        // Com data null o elemento só serve para o custo (acréscimo multi-elemento): nunca invalida a magia
        SpellParser.Result r = SpellParser.parse(recipe, base, null, mastery);
        if (!r.ok()) return null;
        int st = Math.max(0, tag.getInt("stage"));
        ParsedSpell spell = r.spell().stage(st);
        if (spell == null) return null;
        SpellContext ctx = new SpellContext(level, caster, id, base, recipe, spell);
        ctx.root = r.spell();
        ctx.stage = st;
        ctx.powerMult = tag.contains("power") ? tag.getFloat("power") : 1.0F;
        ctx.origin = readVec(tag, "origin");
        ctx.dir = readVec(tag, "dir");
        ctx.anchor = readVec(tag, "anchor");
        ctx.lastHit = tag.hasUUID("lastHit") ? tag.getUUID("lastHit") : null;
        ctx.ignoreUntil = tag.getLong("ignoreUntil");
        ctx.state.fires = tag.getInt("fires");
        ctx.playerCast = tag.getBoolean("player");
        if (tag.contains("budget", Tag.TAG_COMPOUND)) {
            CompoundTag b = tag.getCompound("budget");
            ctx.budget.children = b.getInt("children");
            ctx.budget.leaps = b.getInt("leaps");
            ctx.budget.mobHops = b.getInt("mobHops");
        }
        return base != el ? ctx.forElement(el) : ctx;
    }

    private static ListTag vec(Vec3 v) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(v.x));
        list.add(DoubleTag.valueOf(v.y));
        list.add(DoubleTag.valueOf(v.z));
        return list;
    }

    @Nullable
    private static Vec3 readVec(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) return null;
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        return list.size() == 3 ? new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2)) : null;
    }

    // =====================================================================================
    //  Estado compartilhado
    // =====================================================================================

    /** Orçamento de TODA a árvore de uma conjuração (raiz, estágios, saltos, visões, cópias). */
    private static final class Budget {
        int children;
        int leaps;
        int mobHops;
    }

    /** Estado de UMA instância de estágio (compartilhado com suas visões por elemento). */
    private static final class StageState {
        int fires;
        UUID lastImpact;
        long lastImpactTick = Long.MIN_VALUE;
    }
}
