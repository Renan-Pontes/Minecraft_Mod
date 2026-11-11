package com.renanpontes.isekaimahou.core.stack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.SpellStack.CounterResult;
import com.renanpontes.isekaimahou.core.api.SpellStack.Tier;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.event.PendingCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCounteredEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Implementação da Pilha ({@link SpellStack}) — só servidor, thread do servidor. Guarda as pendentes ativas, junta as
 * formas telegrafadas do mesmo dono/tick num golpe só, acompanha a conjuração dos jogadores, aplica as regras da
 * contramágica e sincroniza o HUD da Pilha com cada jogador (só o que mudou).
 */
public final class StackManager {
    /** Pendentes de módulo resolvem sozinhas este tanto depois do fim, se o módulo não chamou resolve. */
    private static final int CUSTOM_GRACE = 5;
    /** Segurança: golpe telegrafado que nunca disparou some depois do fim + isto. */
    private static final int TELEGRAPH_SAFETY = 200;
    private static final int CHANNEL_SAFETY = 40;
    private static final int SYNC_INTERVAL = 2;
    /** Máximo de linhas enviadas a cada jogador (o HUD mostra 5, juntando repetidas). */
    private static final int MAX_SYNC_ROWS = 8;
    private static final int RECENT_KEEP_TICKS = 600;
    private static final int RECENT_MAX = 64;
    private static final int MAX_FX_SHAPES = 12;

    private static final Map<Integer, PendingState> ACTIVE = new LinkedHashMap<>();
    /** Último grupo telegrafado de cada dono (vale só no tick em que nasceu). */
    private static final Map<UUID, PendingState> GROUPS = new HashMap<>();
    private static final Map<Integer, PendingState> BY_TELEGRAPH = new HashMap<>();
    private static final Map<UUID, PendingState> CHANNELS = new HashMap<>();
    /** Chefe → tempo de jogo da última contramágica sofrida. */
    private static final Map<UUID, Long> BOSS_COUNTERED = new HashMap<>();
    private static final Map<ResourceKey<Level>, ArrayDeque<SpellStack.Recent>> RECENT = new HashMap<>();
    /** Jogador → (id da pendente → versão enviada). */
    private static final Map<UUID, Map<Integer, Integer>> SHOWN = new HashMap<>();
    /**
     * Formas de golpes contrariados neste tick. O agendador do Telegraph junta os callbacks do tick numa fila antes de
     * rodá-los: se um callback (ex.: dano que dispara um gatilho de contramágica) contrariar outro golpe cujo callback
     * já está na fila, este conjunto impede que ele acerte mesmo assim. Limpo a cada tick.
     */
    private static final Set<Integer> COUNTERED_SHAPES = new HashSet<>();
    private static int nextId = 1;
    private static long serverTicks;

    // =====================================================================================
    //  Criação / saída
    // =====================================================================================

    public static PendingState push(ServerLevel level, LivingEntity caster, Vec3 pos, float radius, int ticks, Tier tier,
                                    Component name, @Nullable Element element, @Nullable Runnable onCountered, boolean counterable) {
        PendingState s = create(level, caster, PendingState.Source.CUSTOM, pos, radius, ticks, tier, name, element, counterable, onCountered);
        announce(level, s);
        return s;
    }

    private static PendingState create(ServerLevel level, LivingEntity caster, PendingState.Source source, Vec3 pos, float radius,
                                       int ticks, @Nullable Tier tier, @Nullable Component castName, @Nullable Element element,
                                       boolean counterable, @Nullable Runnable onCountered) {
        long now = level.getGameTime();
        PendingState s = new PendingState(nextId(), caster, level.dimension(), source, now, BossKit.isBossLike(caster));
        s.pos = pos != null ? pos : caster.position();
        s.radius = Math.max(0.5F, radius);
        s.endTick = now + Math.max(1, ticks);
        s.tier = tier != null ? tier : Tier.MINOR;
        s.castName = castName;
        s.name = castName != null ? castName : caster.getDisplayName();
        s.element = element;
        s.counterable = counterable;
        s.onCountered = onCountered;
        ACTIVE.put(s.id, s);
        return s;
    }

    private static int nextId() {
        int id = nextId++;
        if (nextId == Integer.MAX_VALUE) nextId = 1;
        return id;
    }

    /** Posta o PendingCastEvent uma vez (o HUD só mostra pendentes anunciadas). */
    private static void announce(ServerLevel level, PendingState s) {
        if (s.done || s.announced) return;
        s.announced = true;
        try {
            NeoForge.EVENT_BUS.post(new PendingCastEvent(level, s.view()));
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("Erro num ouvinte de PendingCastEvent", ex);
        }
    }

    public static void resolve(PendingState s) {
        finish(s, StackPayloads.REASON_RESOLVED, false);
    }

    public static void cancel(PendingState s) {
        finish(s, StackPayloads.REASON_CANCELLED, true);
    }

    @Nullable
    public static PendingState get(int id) {
        PendingState s = ACTIVE.get(id);
        return s == null || s.done ? null : s;
    }

    private static void finish(PendingState s, byte reason, boolean cancelTelegraphs) {
        if (s.done) return;
        s.done = true;
        ACTIVE.remove(s.id);
        if (s.source == PendingState.Source.CHANNEL) CHANNELS.remove(s.casterId, s);
        if (s.source == PendingState.Source.TELEGRAPH) {
            // Contrariado: o grupo continua registrado até o fim do tick, para as formas que o dono ainda criar neste
            // mesmo tick (o resto do golpe) sumirem junto (joinTelegraph)
            if (!s.countered) GROUPS.remove(s.casterId, s);
            List<Integer> ids = new ArrayList<>(s.telegraphs);
            s.telegraphs.clear();
            for (Integer id : ids) BY_TELEGRAPH.remove(id);
            if (cancelTelegraphs) {
                ServerLevel level = level(s);
                // Telegraph.cancel volta em onTelegraphGone, que já não acha o id: sem recursão
                if (level != null) for (Integer id : ids) Telegraph.cancel(level, id);
            }
        }
        notifyRemoved(s, reason);
    }

    // =====================================================================================
    //  Telegraph (golpes de chefe)
    // =====================================================================================

    /**
     * Uma forma {@code delayed} com dono vivo foi criada: entra no golpe (pendente) do dono neste tick, ou começa um.
     * Formas criadas no mesmo tick de um golpe já contrariado fazem parte dele e somem na hora.
     */
    public static void joinTelegraph(ServerLevel level, int telegraphId, LivingEntity owner, Vec3 center, double extent, int warmup,
                                     @Nullable Component castName, @Nullable Tier tier, boolean uncounterable, @Nullable Element element) {
        long now = level.getGameTime();
        PendingState g = GROUPS.get(owner.getUUID());
        if (g != null && (g.groupTick != now || g.dim != level.dimension())) g = null;
        if (g != null && g.done) {
            if (g.countered) {
                Telegraph.cancel(level, telegraphId);
                return;
            }
            g = null;
        }
        int w = Math.max(1, warmup);
        if (g == null) {
            g = create(level, owner, PendingState.Source.TELEGRAPH, center, (float) extent, w,
                    tier != null ? tier : Tier.byWarmup(w), castName, element, !uncounterable, null);
            g.groupTick = now;
            g.maxWarmup = w;
            g.explicitTier = tier;
            GROUPS.put(owner.getUUID(), g);
        } else {
            g.endTick = Math.max(g.endTick, now + w);
            g.maxWarmup = Math.max(g.maxWarmup, w);
            g.radius = (float) Math.max(g.radius, horizontal(g.pos, center) + extent);
            if (tier != null) g.explicitTier = g.explicitTier == null ? tier : Tier.max(g.explicitTier, tier);
            g.tier = g.explicitTier != null ? g.explicitTier : Tier.byWarmup(g.maxWarmup);
            if (g.castName == null && castName != null) {
                g.castName = castName;
                g.name = castName;
            }
            if (g.element == null) g.element = element;
            if (uncounterable) g.counterable = false;
            g.version++;
        }
        g.telegraphs.add(telegraphId);
        BY_TELEGRAPH.put(telegraphId, g);
    }

    /**
     * O callback de uma forma do golpe vai rodar agora (acertou).
     * @return false se o golpe foi contrariado depois que o callback entrou na fila do tick (não deve acertar)
     */
    public static boolean onTelegraphFired(int telegraphId) {
        if (COUNTERED_SHAPES.remove(telegraphId)) return false;
        PendingState g = BY_TELEGRAPH.remove(telegraphId);
        if (g == null) return true;
        g.telegraphs.remove((Integer) telegraphId);
        g.firedAny = true;
        if (g.telegraphs.isEmpty()) finish(g, StackPayloads.REASON_RESOLVED, false);
        return true;
    }

    /** A forma foi cancelada ({@code hit=false}) ou terminada com flash ({@code hit=true}) pelo dono. */
    public static void onTelegraphGone(int telegraphId, boolean hit) {
        PendingState g = BY_TELEGRAPH.remove(telegraphId);
        if (g == null) return;
        g.telegraphs.remove((Integer) telegraphId);
        if (hit) g.firedAny = true;
        if (g.telegraphs.isEmpty()) finish(g, g.firedAny ? StackPayloads.REASON_RESOLVED : StackPayloads.REASON_CANCELLED, false);
    }

    /** {@code Telegraph.cancelAll(owner)}: confere os golpes desse dono. */
    public static void onOwnerTelegraphsCancelled(Entity owner) {
        if (owner == null) return;
        List<PendingState> mine = new ArrayList<>();
        for (PendingState s : ACTIVE.values()) {
            if (s.source == PendingState.Source.TELEGRAPH && s.casterId.equals(owner.getUUID())) mine.add(s);
        }
        for (PendingState s : mine) refreshGroup(s);
    }

    /** Tira do golpe as formas que sumiram sem disparar; sem formas, o golpe sai da Pilha. */
    private static void refreshGroup(PendingState g) {
        if (g.done) return;
        Iterator<Integer> it = g.telegraphs.iterator();
        while (it.hasNext()) {
            Integer id = it.next();
            if (!Telegraph.isActive(id)) {
                it.remove();
                BY_TELEGRAPH.remove(id);
            }
        }
        if (g.telegraphs.isEmpty()) finish(g, g.firedAny ? StackPayloads.REASON_RESOLVED : StackPayloads.REASON_CANCELLED, false);
    }

    // =====================================================================================
    //  Conjuração de jogador
    // =====================================================================================

    /** Começou a conjurar (tecla segurada) uma magia com tempo de conjuração. */
    public static void channelStarted(ServerPlayer p, ParsedSpell spell, SpellRecipe recipe, Element element, int ticks, long channelStart) {
        channelEnded(p, false);
        if (ticks < SpellStack.MIN_PLAYER_CAST_TICKS) return;
        boolean ultimate = spell.isUltimate() || spell.form().acceptsLaws();
        Component name = recipe.name().isEmpty()
                ? Component.translatable("announce.isekai_mahou.unnamed", element.displayName())
                : Component.literal(recipe.name());
        ServerLevel level = p.serverLevel();
        PendingState s = create(level, p, PendingState.Source.CHANNEL, p.position(), 2.0F, ticks,
                Tier.byCost(spell.baseCost(), ultimate), name, element, true, null);
        s.channelStart = channelStart;
        CHANNELS.put(p.getUUID(), s);
        announce(level, s);
    }

    /** A conjuração terminou: {@code cast=true} saiu a magia; false = soltou/foi interrompida/falhou. */
    public static void channelEnded(ServerPlayer p, boolean cast) {
        PendingState s = CHANNELS.remove(p.getUUID());
        if (s != null && !s.done) finish(s, cast ? StackPayloads.REASON_RESOLVED : StackPayloads.REASON_CANCELLED, false);
    }

    // =====================================================================================
    //  Contramágica
    // =====================================================================================

    public static CounterResult counter(ServerLevel requested, PendingState s, @Nullable LivingEntity by, float power, boolean feedback) {
        return counter(requested, s, by, power, feedback, null);
    }

    /**
     * @param sameAction chefes já contrariados NESTA mesma ação (um {@code counterNear}): os outros golpes pendentes deles
     *                   apanhados na mesma resposta também caem (a recarga de 12 s vale para as PRÓXIMAS contramágicas)
     */
    private static CounterResult counter(ServerLevel requested, PendingState s, @Nullable LivingEntity by, float power, boolean feedback,
                                         @Nullable Set<UUID> sameAction) {
        if (s.done || s.countered) return CounterResult.NOT_COUNTERABLE;
        if (by != null && by.getUUID().equals(s.casterId)) return CounterResult.NOT_COUNTERABLE;
        // Efeitos e tempo sempre no nível da pendente (mesmo se quem chamou passou outro)
        ServerLevel level = requested.dimension() == s.dim ? requested : level(s);
        if (level == null) return CounterResult.NOT_COUNTERABLE;
        LivingEntity caster = s.caster();
        if (!s.counterable) {
            if (feedback) tell(by, Component.translatable("message.isekai_mahou.stack.not_counterable", s.name));
            StackFx.warded(level, s, caster);
            return CounterResult.NOT_COUNTERABLE;
        }
        boolean blocked;
        try {
            blocked = NeoForge.EVENT_BUS.post(new SpellCounteredEvent.Attempt(level, s.view(), by, power)).isCanceled();
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("Erro num ouvinte de SpellCounteredEvent.Attempt", ex);
            blocked = false;
        }
        if (s.done) return CounterResult.NOT_COUNTERABLE;
        if (blocked) {
            if (feedback) tell(by, Component.translatable("message.isekai_mahou.stack.warded", s.name));
            StackFx.warded(level, s, caster);
            return CounterResult.NOT_COUNTERABLE;
        }
        long now = level.getGameTime();
        if (s.boss && (sameAction == null || !sameAction.contains(s.casterId))) {
            Long last = BOSS_COUNTERED.get(s.casterId);
            if (last != null && now - last < SpellStack.BOSS_COUNTER_COOLDOWN) {
                if (feedback) tell(by, bossResists(now - last));
                StackFx.bossResists(level, s, caster);
                return CounterResult.COOLDOWN;
            }
        }
        if (power + 1.0E-4F < s.tier.requiredPower()) {
            if (feedback) tell(by, resisted(s, power));
            StackFx.resisted(level, s, caster);
            return CounterResult.RESISTED;
        }

        // Contrariada: guarda onde estavam as formas (para o estilhaço) antes de cancelar
        List<Vec3> shapes = new ArrayList<>();
        for (Integer id : s.telegraphs) {
            if (shapes.size() >= MAX_FX_SHAPES) break;
            Vec3 c = Telegraph.centerOf(id);
            if (c != null) shapes.add(c);
        }
        COUNTERED_SHAPES.addAll(s.telegraphs);
        s.countered = true;
        finish(s, StackPayloads.REASON_COUNTERED, true);
        if (s.boss) BOSS_COUNTERED.put(s.casterId, now);
        boolean stunned = punish(s, caster, by);
        StackFx.countered(level, s, caster, by, shapes, stunned);
        if (s.onCountered != null) {
            try {
                s.onCountered.run();
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Erro no onCountered de uma pendente da Pilha", ex);
            }
        }
        if (by instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) {
            MagicApi.addCounter(sp, "counter.success", 1);
            if (s.boss) MagicApi.addCounter(sp, "counter.boss", 1);
        }
        try {
            NeoForge.EVENT_BUS.post(new SpellCounteredEvent(level, s.view(), by, power));
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("Erro num ouvinte de SpellCounteredEvent", ex);
        }
        return CounterResult.COUNTERED;
    }

    public static int counterNear(ServerLevel level, Vec3 pos, double radius, @Nullable LivingEntity by, float power) {
        List<PendingState> found = new ArrayList<>();
        double r2 = radius * radius;
        for (PendingState s : ACTIVE.values()) {
            if (s.done || s.dim != level.dimension() || !hostile(s, by)) continue;
            LivingEntity c = s.caster();
            if (s.pos.distanceToSqr(pos) <= r2 || (c != null && c.distanceToSqr(pos) <= r2)) found.add(s);
        }
        // Mais perigosas primeiro (se algo falhar no meio, as grandes já foram)
        found.sort(Comparator.comparingInt((PendingState s) -> -s.tier.ordinal()).thenComparingLong(s -> s.endTick));
        int countered = 0;
        CounterResult failure = null;
        PendingState failed = null;
        long now = level.getGameTime();
        Set<UUID> bossesHit = new HashSet<>();
        for (PendingState s : found) {
            CounterResult r = counter(level, s, by, power, false, bossesHit);
            if (r.success()) {
                countered++;
                if (s.boss) bossesHit.add(s.casterId);
            } else if (failure == null || r.ordinal() < failure.ordinal()) {
                // RESISTED (mais informativo) > COOLDOWN > NOT_COUNTERABLE
                failure = r;
                failed = s;
            }
        }
        if (countered == 0 && failure != null && failed != null) {
            switch (failure) {
                case RESISTED -> tell(by, resisted(failed, power));
                case COOLDOWN -> {
                    Long last = BOSS_COUNTERED.get(failed.casterId);
                    tell(by, bossResists(last == null ? 0 : now - last));
                }
                default -> tell(by, Component.translatable("message.isekai_mahou.stack.not_counterable", failed.name));
            }
        }
        return countered;
    }

    /**
     * Pune quem foi contrariado. Jogador: interrompido + Silenciado 2 s. Chefe: MAJOR Atordoado 1,5 s + Exposto I 5 s,
     * ULTIMATE Atordoado 3 s + Exposto II 8 s. Mob comum: Silenciado 3 s (+ Atordoado 1,5 s se não for MINOR).
     * @return se o conjurador ficou atordoado (o letreiro avisa)
     */
    private static boolean punish(PendingState s, @Nullable LivingEntity caster, @Nullable LivingEntity by) {
        if (caster == null || !caster.isAlive()) return false;
        if (caster instanceof ServerPlayer sp) {
            if (s.source == PendingState.Source.CHANNEL) {
                SpellCaster.counterInterrupt(sp, by);
            } else {
                sp.displayClientMessage(counteredMessage(by), true);
            }
            effect(sp, CoreRegistries.SILENCED, 40, 0, by, false);
            return false;
        }
        boolean stunned = false;
        if (s.boss) {
            if (s.tier == Tier.MAJOR) {
                effect(caster, CoreRegistries.STUNNED, 30, 0, by, true);
                effect(caster, CoreRegistries.EXPOSED, 100, 0, by, true);
                stunned = true;
            } else if (s.tier == Tier.ULTIMATE) {
                effect(caster, CoreRegistries.STUNNED, 60, 0, by, true);
                effect(caster, CoreRegistries.EXPOSED, 160, 1, by, true);
                stunned = true;
            }
        } else {
            effect(caster, CoreRegistries.SILENCED, 60, 0, by, false);
            if (s.tier != Tier.MINOR) {
                effect(caster, CoreRegistries.STUNNED, 30, 0, by, false);
                stunned = true;
            }
        }
        if (stunned && caster instanceof Mob mob) mob.getNavigation().stop();
        return stunned;
    }

    private static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp, @Nullable Entity source, boolean force) {
        if (!e.addEffect(new MobEffectInstance(effect, ticks, amp), source) && force) {
            // Chefes com imunidades próprias: a punição da contramágica vale mesmo assim
            e.forceAddEffect(new MobEffectInstance(effect, ticks, amp), source);
        }
    }

    public static Component counteredMessage(@Nullable LivingEntity by) {
        return (by != null
                ? Component.translatable("message.isekai_mahou.stack.countered_by", by.getDisplayName())
                : Component.translatable("message.isekai_mahou.stack.your_spell_countered"))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static Component bossResists(long since) {
        long left = Math.max(1, (SpellStack.BOSS_COUNTER_COOLDOWN - since + 19) / 20);
        return Component.translatable("message.isekai_mahou.stack.boss_resists", left);
    }

    private static Component resisted(PendingState s, float power) {
        return Component.translatable("message.isekai_mahou.stack.resisted", s.tier.displayName(),
                String.format(java.util.Locale.ROOT, "%.1f", s.tier.requiredPower()),
                String.format(java.util.Locale.ROOT, "%.1f", power));
    }

    private static void tell(@Nullable LivingEntity by, Component msg) {
        if (by instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) {
            sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * {@code s} é hostil a {@code e}? Não: o próprio conjurador, bichos/invocações do mesmo dono, aliados
     * ({@code isAlliedTo}) e — com PvP desligado — outros jogadores. {@code e == null} = sem dono: hostil.
     */
    public static boolean hostile(PendingState s, @Nullable LivingEntity e) {
        if (e == null) return true;
        if (e.getUUID().equals(s.casterId)) return false;
        LivingEntity c = s.caster();
        if (c == null) return true;
        UUID co = c instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        UUID eo = e instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        if (e.getUUID().equals(co) || c.getUUID().equals(eo) || (co != null && co.equals(eo))) return false;
        if (c.isAlliedTo(e)) return false;
        if (c instanceof Player && e instanceof Player) {
            MinecraftServer server = e.getServer();
            return server != null && server.isPvpAllowed();
        }
        return true;
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================

    public static List<SpellStack.Pending> near(ServerLevel level, @Nullable Vec3 pos, double range) {
        List<SpellStack.Pending> out = new ArrayList<>();
        double r2 = range * range;
        for (PendingState s : ACTIVE.values()) {
            if (s.done || s.dim != level.dimension()) continue;
            if (pos != null && range >= 0) {
                LivingEntity c = s.caster();
                if (s.pos.distanceToSqr(pos) > r2 && (c == null || c.distanceToSqr(pos) > r2)) continue;
            }
            out.add(s.view());
        }
        return out;
    }

    // =====================================================================================
    //  Conjurações recentes ("Copiar Magia")
    // =====================================================================================

    public static void recordCast(LivingEntity caster, SpellRecipe recipe, Element element, float power) {
        if (caster == null || recipe == null || recipe.isEmpty() || !(caster.level() instanceof ServerLevel level)) return;
        ArrayDeque<SpellStack.Recent> q = RECENT.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
        q.addFirst(new SpellStack.Recent(caster.getUUID(), caster.getName().getString(), recipe,
                element != null ? element : Element.ARCANE, power, level.getGameTime(), caster.position()));
        while (q.size() > RECENT_MAX) q.removeLast();
    }

    public static List<SpellStack.Recent> recent(ServerLevel level, Vec3 pos, double range, int maxAgeTicks) {
        List<SpellStack.Recent> out = new ArrayList<>();
        ArrayDeque<SpellStack.Recent> q = RECENT.get(level.dimension());
        if (q == null) return out;
        long now = level.getGameTime();
        double r2 = range * range;
        for (SpellStack.Recent r : q) {
            if (now - r.tick() > maxAgeTicks) break;   // a fila está da mais nova para a mais velha
            if (r.pos().distanceToSqr(pos) <= r2) out.add(r);
        }
        return out;
    }

    // =====================================================================================
    //  Tick, HUD e limpeza
    // =====================================================================================

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        serverTicks++;
        COUNTERED_SHAPES.clear();
        if (!ACTIVE.isEmpty()) sweep(server);
        if (serverTicks % SYNC_INTERVAL == 0 && (!ACTIVE.isEmpty() || anyShown())) sync(server);
        if (serverTicks % 20 == 0) prune(server);
    }

    private static void sweep(MinecraftServer server) {
        for (PendingState s : new ArrayList<>(ACTIVE.values())) {
            if (s.done) continue;
            ServerLevel level = server.getLevel(s.dim);
            if (level == null) {
                finish(s, StackPayloads.REASON_CANCELLED, false);
                continue;
            }
            LivingEntity c = s.caster;
            if (c == null || c.isRemoved() || !c.isAlive() || c.level() != level) {
                // Só sai da Pilha: as formas seguem a regra do Telegraph (somem sozinhas com o dono morto)
                finish(s, StackPayloads.REASON_CANCELLED, false);
                continue;
            }
            long now = level.getGameTime();
            switch (s.source) {
                case TELEGRAPH -> {
                    refreshGroup(s);
                    if (s.done) continue;
                    Vec3 at = Telegraph.centerOf(s.telegraphs.get(0));
                    if (at != null) s.pos = at;
                    if (now > s.endTick + TELEGRAPH_SAFETY) finish(s, StackPayloads.REASON_RESOLVED, false);
                    else announce(level, s);
                }
                case CHANNEL -> {
                    if (c instanceof ServerPlayer sp) {
                        MagicData d = MagicApi.get(sp);
                        if (d.channelSlot < 0 || d.channelStart != s.channelStart) {
                            finish(s, StackPayloads.REASON_CANCELLED, false);
                            continue;
                        }
                        // O tempo de conjuração pode mudar depois de começar (Gelado: +15%/acúmulo): a pendente acompanha
                        long end = d.channelStart + Math.max(1, d.channelTicks);
                        if (end != s.endTick) {
                            s.endTick = end;
                            s.version++;
                        }
                    }
                    s.pos = c.position();
                    if (now > s.endTick + CHANNEL_SAFETY) finish(s, StackPayloads.REASON_CANCELLED, false);
                }
                case CUSTOM -> {
                    if (now >= s.endTick + CUSTOM_GRACE) finish(s, StackPayloads.REASON_RESOLVED, false);
                }
            }
        }
    }

    private static boolean anyShown() {
        for (Map<Integer, Integer> m : SHOWN.values()) if (!m.isEmpty()) return true;
        return false;
    }

    /** Manda a cada jogador só o que entrou, mudou ou saiu do alcance dele. */
    private static void sync(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Map<Integer, Integer> shown = SHOWN.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
            List<PendingState> visible = visibleTo(sp);
            if (visible.isEmpty() && shown.isEmpty()) continue;
            long now = sp.serverLevel().getGameTime();
            Set<Integer> ids = new HashSet<>();
            for (PendingState s : visible) {
                ids.add(s.id);
                Integer v = shown.get(s.id);
                if (v == null || v != s.version) {
                    PacketDistributor.sendToPlayer(sp, addPacket(s, now));
                    shown.put(s.id, s.version);
                }
            }
            if (shown.size() > ids.size()) {
                Iterator<Integer> it = shown.keySet().iterator();
                while (it.hasNext()) {
                    Integer id = it.next();
                    if (ids.contains(id)) continue;
                    it.remove();
                    PacketDistributor.sendToPlayer(sp, new StackPayloads.Remove(id, StackPayloads.REASON_OUT_OF_RANGE));
                }
            }
        }
    }

    private static List<PendingState> visibleTo(ServerPlayer sp) {
        List<PendingState> out = new ArrayList<>();
        if (ACTIVE.isEmpty()) return out;
        double r2 = SpellStack.VIEW_RANGE * SpellStack.VIEW_RANGE;
        ResourceKey<Level> dim = sp.level().dimension();
        for (PendingState s : ACTIVE.values()) {
            if (s.done || !s.announced || s.dim != dim || !hostile(s, sp)) continue;
            LivingEntity c = s.caster();
            if (s.pos.distanceToSqr(sp.position()) > r2 && (c == null || c.distanceToSqr(sp) > r2)) continue;
            out.add(s);
        }
        if (out.size() > MAX_SYNC_ROWS) {
            out.sort(Comparator.comparingLong(s -> s.endTick));
            return new ArrayList<>(out.subList(0, MAX_SYNC_ROWS));
        }
        return out;
    }

    private static StackPayloads.Add addPacket(PendingState s, long now) {
        LivingEntity c = s.caster();
        int flags = (s.counterable ? StackPayloads.FLAG_COUNTERABLE : 0) | (s.boss ? StackPayloads.FLAG_BOSS : 0)
                | (c instanceof Player ? StackPayloads.FLAG_PLAYER : 0);
        return new StackPayloads.Add(s.id, c != null ? c.getId() : -1, c != null ? c.getDisplayName() : s.name,
                s.castName != null ? s.castName : Component.empty(), s.element != null ? s.element.ordinal() : -1,
                s.tier.ordinal(), (int) Math.max(1, s.endTick - s.startTick), (int) Math.max(0, s.endTick - now), flags);
    }

    private static void notifyRemoved(PendingState s, byte reason) {
        if (SHOWN.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, Map<Integer, Integer>> e : SHOWN.entrySet()) {
            if (e.getValue().remove(s.id) == null || server == null) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp != null) PacketDistributor.sendToPlayer(sp, new StackPayloads.Remove(s.id, reason));
        }
    }

    private static void prune(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        BOSS_COUNTERED.values().removeIf(t -> now - t >= SpellStack.BOSS_COUNTER_COOLDOWN);
        for (Map.Entry<ResourceKey<Level>, ArrayDeque<SpellStack.Recent>> e : RECENT.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            long t = level != null ? level.getGameTime() : now;
            ArrayDeque<SpellStack.Recent> q = e.getValue();
            while (!q.isEmpty() && t - q.peekLast().tick() > RECENT_KEEP_TICKS) q.removeLast();
        }
        RECENT.values().removeIf(ArrayDeque::isEmpty);
        // Grupos de ticks passados não juntam mais nada (os do tick atual ficam: o dono pode criar mais formas)
        GROUPS.values().removeIf(g -> g.groupTick < now);
    }

    @Nullable
    private static ServerLevel level(PendingState s) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(s.dim);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) channelEnded(sp, false);
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceKey<Level> dim = level.dimension();
        for (PendingState s : new ArrayList<>(ACTIVE.values())) {
            if (s.dim == dim) finish(s, StackPayloads.REASON_CANCELLED, false);
        }
        RECENT.remove(dim);
    }

    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        GROUPS.clear();
        BY_TELEGRAPH.clear();
        CHANNELS.clear();
        COUNTERED_SHAPES.clear();
        BOSS_COUNTERED.clear();
        RECENT.clear();
        SHOWN.clear();
        nextId = 1;
        serverTicks = 0;
    }

    private StackManager() {}
}
