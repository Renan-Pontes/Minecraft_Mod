package com.renanpontes.isekaimahou.client.objectives;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Rastreador de objetivos na tela (cliente): canto superior direito — v1.0: logo abaixo do MINIMAPA (Xaero/JourneyMap,
 * tamanho lido/estimado pelo {@link HudLayout}) ou, sem minimapa, abaixo dos ícones de efeito.
 * Cada módulo registra um PROVEDOR que devolve as suas linhas (lidas a cada tick do cliente, então pode
 * calcular a partir dos dados sincronizados). Provedores de prioridade MAIOR aparecem primeiro; no total
 * cabem {@link #MAX_LINES} linhas.
 *
 * Prioridades sugeridas: Provação/chefe em andamento 1000 · Ascensão (núcleo) 600 · história do Diário 500 ·
 * missões da Guilda 300 · dicas 100.
 *
 * Tecla {@link CoreKeys#TRACKER} (J) mostra/esconde (salvo na config {@link MahouConfig#SHOW_OBJECTIVES}).
 * Some com F1, F3 e com telas abertas (menos o chat).
 */
public final class ObjectiveTracker {
    public static final int MAX_LINES = 6;
    /**
     * Y do topo do painel SEM minimapa (logo abaixo das 2 fileiras de ícones de efeito do vanilla). Com minimapa o
     * rastreador desce: use {@link #top()} / {@link HudLayout#tracker()}.
     */
    public static final int TOP = HudLayout.RIGHT_COLUMN_TOP;
    public static final int WIDTH = HudLayout.TRACKER_MAX_WIDTH;
    /** Reserva antiga do minimapa do Xaero (antes da v1.0): o {@link HudLayout} já mede o minimapa, então é ignorada. */
    private static final String LEGACY_XAERO_RESERVE = "xaero_minimap";

    private record Provider(String id, int priority, int order, Supplier<List<ObjectiveLine>> supplier) {}

    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int registrations;
    private static volatile List<ObjectiveLine> current = List.of();

    /**
     * Registra (ou substitui) um provedor. {@code id} único (ex.: "trials", "journal").
     * O provedor é chamado a cada 4 ticks do cliente (v1.3) e deve ser barato; devolva lista vazia quando não há nada.
     */
    public static synchronized void register(String id, int priority, Supplier<List<ObjectiveLine>> provider) {
        Provider old = PROVIDERS.get(id);
        PROVIDERS.put(id, new Provider(id, priority, old != null ? old.order() : registrations++, provider));
        FAILED.remove(id);
        forceRefresh = true;
    }

    public static synchronized void unregister(String id) {
        PROVIDERS.remove(id);
        forceRefresh = true;
    }

    /**
     * Outro elemento de HUD no mesmo canto (ex.: painel do pacto do Warlock) pode empurrar o rastreador para baixo:
     * {@code height} devolve quantos pixels ele ocupa agora (0 quando escondido). Atalho de
     * {@code HudLayout.reserve(Region.TRACKER, id, height)} — o painel deve desenhar em
     * {@code HudLayout.rightColumnTop() + HudLayout.reservedBefore(Region.TRACKER, id)} (abaixo do minimapa).
     */
    public static void reserveTop(String id, IntSupplier height) {
        if (LEGACY_XAERO_RESERVE.equals(id)) return;
        HudLayout.reserve(HudLayout.Region.TRACKER, id, height);
    }

    public static void releaseTop(String id) {
        HudLayout.release(HudLayout.Region.TRACKER, id);
    }

    /** Soma das reservas atuais (pixels) acima do rastreador. */
    public static int reservedTop() {
        return HudLayout.reserved(HudLayout.Region.TRACKER);
    }

    /** Y do topo do painel agora (abaixo do minimapa e das reservas). */
    public static int top() {
        return HudLayout.tracker().y();
    }

    /** Linhas do último tick (já ordenadas e limitadas a {@link #MAX_LINES}). */
    public static List<ObjectiveLine> lines() {
        return current;
    }

    /** Rastreador ligado na config do cliente. */
    public static boolean isEnabled() {
        try {
            return MahouConfig.SHOW_OBJECTIVES.get();
        } catch (RuntimeException e) {
            return false; // config ainda não carregada (padrão: fechado)
        }
    }

    /** Liga/desliga e salva na config do cliente. */
    public static void setEnabled(boolean on) {
        try {
            MahouConfig.SHOW_OBJECTIVES.set(on);
            MahouConfig.SHOW_OBJECTIVES.save();
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("Não foi possível salvar showObjectives", e);
        }
    }

    /** Rastreador visível (mesmo que {@link #isEnabled()}). */
    public static boolean isVisible() {
        return isEnabled();
    }

    /**
     * Mostra/esconde sem aviso na tela (salvo na config). Usado pela entrada no jogo: personagem novo começa com o
     * rastreador fechado e o tutorial do Espírito Guia o abre quando o jogador aperta J.
     */
    public static void setVisible(boolean on) {
        if (isEnabled() != on) setEnabled(on);
        if (!on) current = List.of();
        forceRefresh = true;
    }

    /** Páginas do rastreador: J cicla fechado → Objetivos → Missões da Guilda → fechado. */
    public enum Page { OBJECTIVES, GUILD }

    /** Provedores da página da Guilda (missões, prova de rank, companheiros — com seta de para onde ir). */
    private static final Set<String> GUILD_PROVIDERS = Set.of("guild", "guild_companions");
    /** Urgentes (Provação/chefe, confronto e Procurado na cidade) aparecem nas duas páginas. */
    private static final int URGENT_PRIORITY = 900;

    private static Page page = Page.OBJECTIVES;

    public static Page page() {
        return page;
    }

    /** J: fechado → Objetivos → Missões da Guilda → fechado. Avisa na barra de ação. */
    public static void toggle() {
        Component msg;
        if (!isEnabled()) {
            page = Page.OBJECTIVES;
            setEnabled(true);
            msg = Component.translatable("objectives.isekai_mahou.shown");
        } else if (page == Page.OBJECTIVES) {
            page = Page.GUILD;
            msg = Component.translatable("objectives.isekai_mahou.shown_guild");
        } else {
            page = Page.OBJECTIVES;
            setEnabled(false);
            msg = Component.translatable("objectives.isekai_mahou.hidden", CoreKeys.TRACKER.getTranslatedKeyMessage());
        }
        refresh();
        Minecraft.getInstance().gui.setOverlayMessage(msg.copy().withStyle(ChatFormatting.GOLD), false);
    }

    private static boolean onPage(Provider p) {
        if (p.priority() >= URGENT_PRIORITY) return true;
        return GUILD_PROVIDERS.contains(p.id()) == (page == Page.GUILD);
    }

    /** v1.3: recalcula a cada 4 ticks (0,2 s) — 15 provedores por tick pesavam à toa; J/registro forçam na hora. */
    private static final int REFRESH_TICKS = 4;
    private static int refreshTimer;
    private static volatile boolean forceRefresh = true;

    /** Chamado a cada tick do cliente pelo núcleo. */
    public static void tick() {
        if (!forceRefresh && ++refreshTimer < REFRESH_TICKS) return;
        refresh();
    }

    /** Recalcula as linhas agora. */
    public static void refresh() {
        refreshTimer = 0;
        forceRefresh = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isEnabled()) {
            current = List.of();
            return;
        }
        List<Provider> providers;
        synchronized (ObjectiveTracker.class) {
            providers = new ArrayList<>(PROVIDERS.values());
        }
        providers.sort(Comparator.comparingInt(Provider::priority).reversed().thenComparingInt(Provider::order));
        List<ObjectiveLine> out = new ArrayList<>();
        for (Provider p : providers) {
            if (out.size() >= MAX_LINES) break;
            if (!onPage(p)) continue;
            List<ObjectiveLine> lines;
            try {
                lines = p.supplier().get();
            } catch (RuntimeException e) {
                synchronized (ObjectiveTracker.class) {
                    if (FAILED.add(p.id())) IsekaiMahou.LOGGER.error("Provedor de objetivos '{}' falhou", p.id(), e);
                }
                continue;
            }
            if (lines == null) continue;
            for (ObjectiveLine l : lines) {
                if (l == null) continue;
                out.add(l);
                if (out.size() >= MAX_LINES) break;
            }
        }
        if (out.isEmpty() && page == Page.GUILD) {
            out.add(ObjectiveLine.of(Component.translatable("objectives.isekai_mahou.guild_empty"),
                    Component.translatable("objectives.isekai_mahou.guild_empty.hint"), 0xC9A86A,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK)));
        }
        current = List.copyOf(out);
    }

    /** Limpa o cache (saída do mundo). */
    public static void clear() {
        current = List.of();
        forceRefresh = true;
    }

    private ObjectiveTracker() {}
}
