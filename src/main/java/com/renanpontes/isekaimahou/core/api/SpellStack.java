package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.stack.PendingState;
import com.renanpontes.isekaimahou.core.stack.StackManager;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Pilha (inspirada em Magic: The Gathering): tudo que está PARA acontecer — golpe telegrafado de chefe, magia sendo
 * conjurada por um jogador, ultimate carregando — vira uma <b>conjuração pendente</b> que pode ser respondida com
 * contramágica antes de resolver.
 * <p>
 * O núcleo já cria pendentes sozinho para: {@code Telegraph.delayed*} com dono ({@code .owner(e)}) e conjurações de
 * jogador com tempo de conjuração ≥ {@link #MIN_PLAYER_CAST_TICKS}. Módulos criam as próprias com {@link #push}.
 * Tudo aqui é do servidor (thread do servidor). Ver {@code docs/v08/API_stack.md}.
 */
public final class SpellStack {
    /** Distância (blocos) em que os jogadores veem as pendentes HOSTIS no HUD da Pilha e o letreiro de contramágica. */
    public static final double VIEW_RANGE = 48.0;
    /** Ticks em que o MESMO chefe não pode ser contrariado de novo (12 s). */
    public static final int BOSS_COUNTER_COOLDOWN = 240;
    /** Conjurações de jogador com pelo menos este tempo de conjuração entram na Pilha. */
    public static final int MIN_PLAYER_CAST_TICKS = 10;
    /** Glifos de contramágica (o HUD mostra "✦ pode ser contrariado" para quem conhece um deles). */
    public static final ResourceLocation COUNTERSPELL = IsekaiMahou.id("counterspell");
    public static final ResourceLocation MANA_LEAK = IsekaiMahou.id("mana_leak");

    /** Peso da conjuração: define o poder necessário para contrariar. */
    public enum Tier {
        MINOR(0.5F), MAJOR(1.0F), ULTIMATE(2.0F);

        private final float required;

        Tier(float required) {
            this.required = required;
        }

        /** Poder mínimo da contramágica ({@code stats.power() × ctx.powerMult()}) para contrariar. */
        public float requiredPower() { return required; }

        /** Golpes de chefe (Telegraph): &lt;20 ticks MINOR, 20–59 MAJOR, ≥60 ULTIMATE. */
        public static Tier byWarmup(int ticks) {
            return ticks >= 60 ? ULTIMATE : ticks >= 20 ? MAJOR : MINOR;
        }

        /** Magias de jogador: custo &lt;40 MINOR, &lt;120 MAJOR, senão (ou ultimate/Domínio) ULTIMATE. */
        public static Tier byCost(int manaCost, boolean ultimate) {
            if (ultimate) return ULTIMATE;
            return manaCost < 40 ? MINOR : manaCost < 120 ? MAJOR : ULTIMATE;
        }

        public static Tier max(Tier a, Tier b) {
            return a.ordinal() >= b.ordinal() ? a : b;
        }

        public static Tier byOrdinal(int i) {
            Tier[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }

        /** Nome traduzido ("Menor", "Maior", "Suprema"). */
        public Component displayName() {
            return Component.translatable("stack.isekai_mahou.tier." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Resultado de {@link #counter}. */
    public enum CounterResult {
        /** Contrariada: saiu da Pilha sem acontecer. */
        COUNTERED,
        /** Poder da contramágica abaixo do necessário para o {@link Tier}. */
        RESISTED,
        /** Chefe contrariado há menos de 12 s ("O chefe resiste!"). */
        COOLDOWN,
        /** Não pode ser contrariada (marcada assim, protegida, do próprio conjurador, ou já saiu da Pilha). */
        NOT_COUNTERABLE;

        public boolean success() { return this == COUNTERED; }
    }

    /**
     * Uma conjuração pendente (somente leitura para os módulos). O estado muda sozinho: use {@link #active()} para
     * saber se ainda está na Pilha.
     */
    public static final class Pending {
        private final PendingState state;

        /** Interno: só o núcleo cria pendentes ({@link #push}). */
        public Pending(PendingState state) {
            this.state = state;
        }

        public int id() { return state.id(); }
        /** Quem conjura; null se a entidade sumiu do mundo (descarregou/morreu). */
        @Nullable public LivingEntity caster() { return state.caster(); }
        public UUID casterId() { return state.casterId(); }
        public ResourceKey<Level> dim() { return state.dim(); }
        /** Onde vai acontecer (golpe: centro da 1ª forma; magia de jogador: posição atual do conjurador). */
        public Vec3 pos() { return state.pos(); }
        /** Alcance aproximado em volta de {@link #pos()} (cobre todas as formas do golpe). */
        public float radius() { return state.radius(); }
        public long startTick() { return state.startTick(); }
        /** Tempo de jogo em que resolve. */
        public long endTick() { return state.endTick(); }
        public Tier tier() { return state.tier(); }
        /** Nome do golpe/magia; sem nome próprio = nome do conjurador. */
        public Component name() { return state.name(); }
        /** Nome dado explicitamente (castName / nome da magia), ou null. */
        @Nullable public Component castName() { return state.castName(); }
        @Nullable public Element element() { return state.element(); }
        public boolean counterable() { return state.counterable(); }
        public boolean countered() { return state.countered(); }
        /** Ainda está na Pilha (não resolveu, não foi cancelada nem contrariada). */
        public boolean active() { return !state.done(); }
        /** Veio de um golpe telegrafado ({@code Telegraph.delayed*} com dono). */
        public boolean fromTelegraph() { return state.source() == PendingState.Source.TELEGRAPH; }
        /** É a conjuração (tecla segurada) de um jogador. */
        public boolean fromPlayerCast() { return state.source() == PendingState.Source.CHANNEL; }
        /** O conjurador é chefe ({@code BossKit.isBossLike}). */
        public boolean bossCaster() { return state.boss(); }

        /** Ticks até resolver (0 se já passou). */
        public int ticksLeft(ServerLevel level) {
            return (int) Math.max(0, state.endTick() - level.getGameTime());
        }

        /** Esta pendente é hostil a {@code e} (pode ser contrariada por ele / aparece no HUD dele)? */
        public boolean hostileTo(@Nullable LivingEntity e) {
            return StackManager.hostile(state, e);
        }

        @Override
        public String toString() {
            return "Pending#" + id() + "[" + tier() + " " + name().getString() + "]";
        }
    }

    /** Uma conjuração recente (para "Copiar Magia"). {@code pos} = onde o conjurador estava. */
    public record Recent(UUID caster, String casterName, SpellRecipe recipe, Element element, float power, long tick, Vec3 pos) {}

    // =====================================================================================
    //  API
    // =====================================================================================

    /**
     * Põe uma conjuração na Pilha e posta {@code PendingCastEvent}. Chame {@link #resolve} quando acontecer (se não
     * chamar, ela resolve sozinha 5 ticks depois de {@code endTick}). Some sozinha se o conjurador morrer/sumir.
     *
     * @param ticks       ticks até resolver (mín. 1)
     * @param onCountered roda se for contrariada (aborte o seu efeito aqui); null = nada
     */
    public static Pending push(ServerLevel l, LivingEntity caster, Vec3 pos, float radius, int ticks, Tier tier, Component name,
                               @Nullable Element element, @Nullable Runnable onCountered) {
        return push(l, caster, pos, radius, ticks, tier, name, element, onCountered, true);
    }

    /** Igual ao outro; {@code counterable=false} mostra no HUD mas não pode ser contrariada. */
    public static Pending push(ServerLevel l, LivingEntity caster, Vec3 pos, float radius, int ticks, Tier tier, Component name,
                               @Nullable Element element, @Nullable Runnable onCountered, boolean counterable) {
        return StackManager.push(l, caster, pos, radius, ticks, tier, name, element, onCountered, counterable).view();
    }

    /** Aconteceu: sai da Pilha (sem contramágica). Pendentes que já saíram são ignoradas. */
    public static void resolve(Pending p) {
        if (p != null) StackManager.resolve(p.state);
    }

    /** Sumiu sem acontecer (dono morreu, golpe abortado...). Golpes de Telegraph: as formas do grupo também somem. */
    public static void cancel(Pending p) {
        if (p != null) StackManager.cancel(p.state);
    }

    /** Pendentes ATIVAS da dimensão cujo {@code pos} ou conjurador está a até {@code range} de {@code pos}. */
    public static List<Pending> near(ServerLevel l, Vec3 pos, double range) {
        return StackManager.near(l, pos, range);
    }

    /** Todas as pendentes ativas da dimensão. */
    public static List<Pending> all(ServerLevel l) {
        return StackManager.near(l, null, -1);
    }

    /** A pendente com este id, se ainda estiver na Pilha. */
    @Nullable
    public static Pending get(int id) {
        PendingState s = StackManager.get(id);
        return s == null ? null : s.view();
    }

    /**
     * Tenta contrariar {@code p}. Regras: poder ≥ {@link Tier#requiredPower()}; o mesmo chefe só uma vez a cada 12 s;
     * chefe contrariado fica Atordoado + Exposto (MAJOR/ULTIMATE); jogador é interrompido e Silenciado 2 s.
     * Efeitos visuais/sonoros, letreiro "CONTRAMÁGICA!", eventos e contadores ({@code counter.success},
     * {@code counter.boss}) já são feitos aqui. Dá aviso na barra de ação de {@code by} (jogador) quando falha.
     *
     * @param power poder da contramágica: {@code stats.power() × ctx.powerMult()}
     */
    public static CounterResult counter(ServerLevel l, Pending p, @Nullable LivingEntity by, float power) {
        if (p == null) return CounterResult.NOT_COUNTERABLE;
        return StackManager.counter(l, p.state, by, power, true);
    }

    /**
     * Contraria todas as pendentes HOSTIS a {@code by} cujo {@code pos} OU conjurador esteja a até {@code radius} de
     * {@code pos} (as mais pesadas primeiro). Devolve quantas foram contrariadas (um aviso só na barra de ação se
     * nenhuma foi). Um chefe contrariado aqui perde TODOS os golpes pendentes apanhados nesta mesma resposta; a recarga
     * de 12 s vale para as próximas contramágicas.
     */
    public static int counterNear(ServerLevel l, Vec3 pos, double radius, @Nullable LivingEntity by, float power) {
        return StackManager.counterNear(l, pos, radius, by, power);
    }

    /** {@code p} é hostil a {@code e}? (mesma regra do HUD e do {@link #counterNear}). */
    public static boolean isHostile(Pending p, @Nullable LivingEntity e) {
        return p != null && StackManager.hostile(p.state, e);
    }

    /** O núcleo chama em toda conjuração resolvida (jogador, mobs e {@code castFrom}). */
    public static void recordCast(LivingEntity caster, SpellRecipe recipe, Element element, float power) {
        StackManager.recordCast(caster, recipe, element, power);
    }

    /**
     * Conjurações a até {@code range} de {@code pos} feitas nos últimos {@code maxAgeTicks} ticks, da MAIS NOVA para
     * a mais velha (guarda até 30 s / 64 por dimensão). Filtre o próprio conjurador por {@link Recent#caster()}.
     */
    public static List<Recent> recent(ServerLevel l, Vec3 pos, double range, int maxAgeTicks) {
        return StackManager.recent(l, pos, range, maxAgeTicks);
    }

    private SpellStack() {}
}
