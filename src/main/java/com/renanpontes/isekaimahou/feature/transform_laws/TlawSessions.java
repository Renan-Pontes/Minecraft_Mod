package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformLaws;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Sessões de Leis de Transformação (o {@link TransformLaws.Backend} do módulo). Uma SESSÃO = uma transformação em curso
 * (fonte: o glifo da transformação, a forma racial ou outro módulo) com as leis escolhidas para ela. Enquanto existir,
 * as leis valem ({@link #active}); acaba quando o efeito-sonda some, quando o tempo acaba ou por {@link #end}.
 * <p>
 * Durações: Último Suspiro ×1,5 (o efeito da transformação é esticado na hora) e Chama Breve ×0,5 (a forma é desfeita
 * na metade). Tudo roda a cada 5 ticks no tick do jogador; os mapas são limpos ao sair.
 */
public final class TlawSessions implements TransformLaws.Backend {
    static final TlawSessions INSTANCE = new TlawSessions();
    /** Fonte da Transformação Racial (tecla V). */
    public static final ResourceLocation RACE_SOURCE = IsekaiMahou.id("race_form");
    /** Fonte da Resurrección do Hollow (HollowRelease). */
    public static final ResourceLocation HOLLOW_SOURCE = IsekaiMahou.id("hollow_resurreccion");
    /** Tempo de espera pelo efeito-sonda (ex.: os 3 s de meditação do Modo Sábio, Atraso da magia). */
    private static final int GRACE = 160;
    /** Teto de segurança de uma sessão (20 min). */
    private static final long MAX_TICKS = 20L * 60 * 20;
    static final String ACTIVE_FLAG = "tlaw.on.";

    enum Reason { NORMAL, LOGOUT, DEATH, REPLACED, FAILED }

    static final class Session {
        final ResourceLocation source;
        final List<TransformLawGlyph> laws;
        final long start;
        long endAt;
        @Nullable final Holder<MobEffect> probe;
        boolean seen;
        /** Onda 5: multiplicador de duração vindo da RECEITA do espaço (aumentos no corpo); 1 = nenhum. */
        float extra = 1F;
        @Nullable Vec3 anchor;
        @Nullable ResourceKey<Level> anchorDim;

        Session(ResourceLocation source, List<TransformLawGlyph> laws, long start, long endAt, @Nullable Holder<MobEffect> probe) {
            this.source = source;
            this.laws = laws;
            this.start = start;
            this.endAt = endAt;
            this.probe = probe;
        }

        boolean has(String path) {
            for (TransformLawGlyph l : laws) if (l.id().getPath().equals(path)) return true;
            return false;
        }
    }

    /** Estado por jogador: sessões + contadores das regras (ritmo, recargas de avisos). */
    static final class State {
        final List<Session> sessions = new ArrayList<>();
        List<TransformLawGlyph> active = List.of();
        int rhythm;
        long lastHit;
        long lastBurst;
        long lastWarn;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private TlawSessions() {}

    // =====================================================================================
    //  API (Backend)
    // =====================================================================================
    @Override
    public List<TransformLawGlyph> active(LivingEntity entity) {
        State s = STATES.get(entity.getUUID());
        return s == null ? List.of() : s.active;
    }

    @Override
    public void begin(ServerPlayer player, ResourceLocation source, Collection<TransformLawGlyph> laws, int ticks,
                      @Nullable Holder<MobEffect> probe) {
        if (player instanceof FakePlayer || !player.isAlive()) return;
        // Release 1.0: a fonte é um espaço de receita (forma racial, liberação de classe) com receita escolhida → a
        // receita entra por cima; as leis dela vêm primeiro e as soltas completam até o limite do jogador
        ForgeSessions.Plan plan = ForgeSessions.plan(player, source);
        // Onda 5: os aumentos no corpo da receita esticam/encurtam a transformação base (não de novo ao voltar do relog)
        float rm = plan == null || plan.resumed() ? 1F : TransformForge.durationMult(plan.spell().formStats());
        List<TransformLawGlyph> list = new ArrayList<>();
        if (plan != null) list.addAll(TransformLaws.lawsOf(plan.spell()));
        for (TransformLawGlyph l : laws) if (l != null && !list.contains(l)) list.add(l);
        if (plan != null) {
            MagicData pd = MagicApi.get(player);
            int max = TransformTargets.maxLaws(pd.level(), pd.circle());
            while (list.size() > max) list.remove(list.size() - 1);
        }
        if (list.isEmpty()) {
            if (plan != null) ForgeSessions.startPlan(player, source, plan, ticks, probe, rm, true);
            return;
        }
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        Session old = find(st, source);
        if (old != null) {
            // Reconjurar a mesma transformação: se ela ACABOU com a reconjuração (formas que alternam, como as de
            // Druida), é um fim normal (paga os preços); se continua, os votos são trocados — e os votos que saem
            // ainda cobram o preço de fim (não dá para fugir do custo de Transcender/Último Suspiro reconjurando)
            boolean ended = old.seen && old.probe != null && !player.hasEffect(old.probe);
            finish(player, st, old, ended ? Reason.NORMAL : Reason.REPLACED);
            if (!ended && old.seen) {
                List<TransformLawGlyph> dropped = new ArrayList<>(old.laws);
                dropped.removeAll(list);
                if (!dropped.isEmpty()) TlawRules.onEnd(player, dropped);
            }
            st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        }
        long now = player.serverLevel().getGameTime();
        float mult = durationMult(list) * rm;
        long endAt = probe == null ? now + Math.max(20, Math.round(Math.max(20, ticks) * mult)) : Long.MAX_VALUE;
        Session s = new Session(source, List.copyOf(list), now, endAt, probe);
        s.extra = rm;
        st.sessions.add(s);
        if (probe == null) start(player, s, now, 1F);
        else if (player.hasEffect(probe)) start(player, s, now, mult);
        recompute(player, st);
        if (plan != null) ForgeSessions.startPlan(player, source, plan, ticks, probe, mult, false);
    }

    @Override
    public void end(ServerPlayer player, ResourceLocation source) {
        ForgeSessions.stop(player, source);
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        Session s = find(st, source);
        if (s != null) finish(player, st, s, Reason.NORMAL);
    }

    // =====================================================================================
    //  Consultas internas
    // =====================================================================================
    @Nullable
    static State state(LivingEntity e) {
        return STATES.get(e.getUUID());
    }

    static boolean has(LivingEntity e, String path) {
        State s = STATES.get(e.getUUID());
        if (s == null) return false;
        for (TransformLawGlyph l : s.active) if (l.id().getPath().equals(path)) return true;
        return false;
    }

    static boolean hasSource(LivingEntity e, ResourceLocation source) {
        State s = STATES.get(e.getUUID());
        return s != null && find(s, source) != null;
    }

    /** Sessões (para o comando de status). */
    static List<Session> sessions(LivingEntity e) {
        State s = STATES.get(e.getUUID());
        return s == null ? List.of() : List.copyOf(s.sessions);
    }

    /** Leis da sessão da fonte (vazio se não há sessão). */
    static List<TransformLawGlyph> lawsOf(LivingEntity e, ResourceLocation source) {
        State s = STATES.get(e.getUUID());
        Session x = s == null ? null : find(s, source);
        return x == null ? List.of() : x.laws;
    }

    @Nullable
    private static Session find(State st, ResourceLocation source) {
        for (Session s : st.sessions) if (s.source.equals(source)) return s;
        return null;
    }

    static float durationMult(List<TransformLawGlyph> laws) {
        float m = 1F;
        for (TransformLawGlyph l : laws) {
            String p = l.id().getPath();
            if (p.equals(TlawGlyphs.LAST_BREATH)) m *= 1.5F;
            if (p.equals(TlawGlyphs.BRIEF_FLAME)) m *= 0.5F;
        }
        return m;
    }

    // =====================================================================================
    //  Ciclo de vida
    // =====================================================================================
    /** A transformação entrou de fato: ajusta a duração, guarda a âncora (Juramento) e mostra os votos. */
    private static void start(ServerPlayer player, Session s, long now, float mult) {
        s.seen = true;
        s.anchor = player.position();
        s.anchorDim = player.level().dimension();
        if (s.probe != null && mult != 1F) {
            MobEffectInstance inst = player.getEffect(s.probe);
            if (inst != null && !inst.isInfiniteDuration()) {
                int rem = inst.getDuration();
                int scaled = Math.max(20, Math.round(rem * mult));
                if (mult > 1F) {
                    // Esticar: mesmo nível, mais tempo (o vanilla aceita a atualização sem tirar o efeito)
                    player.addEffect(new MobEffectInstance(s.probe, scaled, inst.getAmplifier(), inst.isAmbient(), inst.isVisible(),
                            inst.showIcon()));
                } else {
                    s.endAt = now + scaled; // encurtar: a forma é desfeita quando chegar a hora
                }
            }
        }
        TlawFx.sealed(player, s.laws);
        TlawRules.onStart(player, s.laws);
    }

    private static void finish(ServerPlayer player, State st, Session s, Reason why) {
        st.sessions.remove(s);
        recompute(player, st);
        if (!s.seen || why == Reason.REPLACED || why == Reason.FAILED) return;
        if (why == Reason.DEATH || !player.isAlive()) return;
        TlawFx.broken(player, why == Reason.LOGOUT);
        TlawRules.onEnd(player, s.laws);
    }

    /** Recalcula as leis em vigor (união das sessões já iniciadas), as marcas sincronizadas e os atributos. */
    private static void recompute(ServerPlayer player, State st) {
        List<TransformLawGlyph> act = new ArrayList<>();
        for (TransformLawGlyph l : TlawGlyphs.all()) {
            for (Session s : st.sessions) {
                if (s.seen && s.laws.contains(l)) {
                    act.add(l);
                    break;
                }
            }
        }
        // Leis de outros módulos (subclasses registradas fora deste módulo) entram no fim
        for (Session s : st.sessions) {
            if (!s.seen) continue;
            for (TransformLawGlyph l : s.laws) if (!act.contains(l)) act.add(l);
        }
        st.active = List.copyOf(act);
        syncFlags(player, act);
        TlawRules.applyModifiers(player, act);
        if (st.sessions.isEmpty()) STATES.remove(player.getUUID());
    }

    /** Marca {@code tlaw.on.<lei>} nos dados (sincronizados) para a aba Votos mostrar o que está valendo. */
    private static void syncFlags(ServerPlayer player, List<TransformLawGlyph> act) {
        MagicData d = MagicApi.get(player);
        List<String> stale = new ArrayList<>();
        for (String f : d.flags()) if (f.startsWith(ACTIVE_FLAG)) stale.add(f);
        Set<String> want = new java.util.HashSet<>();
        for (TransformLawGlyph l : act) want.add(ACTIVE_FLAG + l.id().getPath());
        for (String f : stale) if (!want.contains(f)) d.setFlag(f, false);
        for (String f : want) d.setFlag(f, true);
    }

    // =====================================================================================
    //  Tick (a cada 5 ticks do jogador)
    // =====================================================================================
    static void tick(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        long now = player.serverLevel().getGameTime();
        for (Session s : List.copyOf(st.sessions)) {
            if (!player.isAlive()) {
                finish(player, st, s, Reason.DEATH);
                continue;
            }
            if (s.probe != null) {
                boolean present = player.hasEffect(s.probe);
                if (!s.seen) {
                    if (present) {
                        start(player, s, now, durationMult(s.laws) * s.extra);
                        recompute(player, st);
                    } else if (now - s.start > GRACE) {
                        finish(player, st, s, Reason.FAILED);
                    }
                    continue;
                }
                if (!present) {
                    finish(player, st, s, Reason.NORMAL);
                    continue;
                }
                if (now >= s.endAt) {
                    // Chama Breve: a forma se apaga na metade do tempo
                    player.removeEffect(s.probe);
                    finish(player, st, s, Reason.NORMAL);
                    continue;
                }
            } else if (now >= s.endAt) {
                finish(player, st, s, Reason.NORMAL);
                continue;
            }
            if (now - s.start > MAX_TICKS) finish(player, st, s, Reason.NORMAL);
        }
        State cur = STATES.get(player.getUUID());
        if (cur != null && !cur.active.isEmpty() && player.isAlive()) TlawRules.tickActive(player, cur, now);
    }

    /** Saída do jogador: os preços de fim valem (não dá para fugir do custo de Transcender deslogando). */
    static void logout(ServerPlayer player) {
        // Onda 5: as receitas presas a um efeito ficam guardadas para voltar no próximo login
        ForgeSessions.logout(player);
        State st = STATES.get(player.getUUID());
        if (st != null) {
            for (Session s : List.copyOf(st.sessions)) finish(player, st, s, Reason.LOGOUT);
        }
        STATES.remove(player.getUUID());
        clearFlags(player);
    }

    /** Entrada: limpa marcas velhas (sessões não sobrevivem ao relog). */
    static void clearFlags(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        List<String> stale = new ArrayList<>();
        for (String f : d.flags()) if (f.startsWith(ACTIVE_FLAG)) stale.add(f);
        for (String f : stale) d.setFlag(f, false);
        TlawRules.applyModifiers(player, List.of());
    }

    /** Encerra a sessão da fonte SEM cobrar os preços (fim "não natural": morte, troca de classe...). */
    static void drop(ServerPlayer player, ResourceLocation source) {
        ForgeSessions.stop(player, source);
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        Session s = find(st, source);
        if (s != null) finish(player, st, s, Reason.REPLACED);
    }

    /** Comando: encerra tudo sem cobrar preços. */
    static void clear(ServerPlayer player) {
        ForgeSessions.stopAll(player, true);
        State st = STATES.get(player.getUUID());
        if (st != null) {
            for (Session s : List.copyOf(st.sessions)) finish(player, st, s, Reason.REPLACED);
        }
        STATES.remove(player.getUUID());
        clearFlags(player);
    }

    static void clearAll() {
        STATES.clear();
    }

    static Set<UUID> players() {
        return STATES.keySet();
    }
}
