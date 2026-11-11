package com.renanpontes.isekaimahou.client.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Filtro do CHAT do mod (cliente, v1.1). Toda mensagem de sistema que tem uma chave de tradução do mod
 * ({@code ...isekai_mahou...}) em qualquer parte (texto, irmãos ou argumentos) passa por aqui e é decidida pela config
 * {@link MahouConfig#CHAT_MESSAGES}:
 * <ul>
 *   <li><b>MINIMAL</b> (padrão): fica no chat só o essencial — erros e recusas, convites (grupo, duelo, Provação),
 *       falas de NPC, subir de nível/Círculo/rank, chefe derrotado, respostas a comandos e mensagens clicáveis. O resto
 *       vai para o registro "Avisos" do menu (B) e aparece numa linha curta acima da hotbar.</li>
 *   <li><b>NORMAL</b>: quase tudo no chat; dicas, progresso e recompensas vão para os Avisos.</li>
 *   <li><b>ALL</b>: tudo no chat (como antes).</li>
 * </ul>
 * O que o jogador acabou de PEDIR (até 1,5 s depois de clicar com o botão direito, ou 4 s depois de usar o chat/um
 * comando) sempre fica no chat — exceto dicas. A decisão é por padrões na chave (tabela abaixo).
 */
public final class ChatRouter {
    public enum Route { KEEP, LOG }

    // ---------------------------------------------------------------- tabela de padrões (chave em minúsculas)
    /** Dicas e textos de ambientação: sempre vão para os Avisos (MINIMAL/NORMAL). */
    private static final String[] HINT = {
            "hint", ".tip", "_tip", "tip.", "tips", "tutorial", "_note", ".note", "flavor", "lore", "welcome", "_chat"};
    /** Progresso, descobertas e recompensas: vão para os Avisos (MINIMAL/NORMAL). */
    private static final String[] NOISE = {
            "reward", "loot", "drop", "toast", "progress", "feat", "counter", "explored", "discover", "learned", "skill",
            "xp", "found", "visit", "entered", "entering", "leaving", "summary", "step", "tech_", "status_", "unlock",
            "gain", "earned", "harvest", "stats", "granted", "received", "obtained", "collected", "wave"};
    /** Erros e recusas (resposta a algo que o jogador tentou): ficam no chat mesmo se a chave também parecer "progresso". */
    private static final String[] REFUSAL = {
            "error", "deny", "denied", "refuse", "cannot", "cant_", "not_", "no_mana", "too_", "need", "already",
            "cooldown", "busy", "blocked", "forbidden", "invalid", "missing", "unavailable", "insufficient", "wrong", "fail"};
    /** Essenciais: ficam no chat (MINIMAL). Verificado depois de HINT e NOISE. */
    private static final String[] ESSENTIAL = {
            // comandos e falas
            "command.", ".cmd", "cmd_", "dialogue.", "npc.", "shout", ".say", "speech",
            // convites e PvP
            "invite", "invited", ".request", "duel", "pvp", "war.", "_war", "trade", "party",
            // erros e recusas
            "error", "deny", "denied", "refuse", "cannot", "cant_", "not_", ".no_", "no_mana", "need", "too_", "already",
            "cooldown", "busy", "blocked", "forbidden", "protected", "invalid", "missing", "locked", "full", "fail",
            "require", "wrong", "unavailable", "offline", "insufficient", "only", "outside", "expired", "lost",
            // marcos
            "levelup", "level_up", "circle", "rank_up", "boss_defeated", "defeated", "slain", "victory", "died", "death"};

    /** Janela "o jogador pediu" depois de um clique direito / do chat. */
    private static final long USE_WINDOW_MS = 1500L;
    private static final long CHAT_WINDOW_MS = 4000L;
    /** Linha da hotbar: intervalo mínimo e janela sem repetir. */
    private static final long BAR_INTERVAL_MS = 1500L;
    private static final long BAR_REPEAT_MS = 2000L;
    private static final int BAR_SUMMARY_AT = 3;
    private static final int BAR_MAX_CHARS = 72;

    private static long lastUse = Long.MIN_VALUE / 2;
    private static long lastChat = Long.MIN_VALUE / 2;
    private static final Deque<Component> BAR = new ArrayDeque<>();
    private static long lastBar;
    private static String lastBarText = "";

    private ChatRouter() {}

    // ---------------------------------------------------------------- eventos
    /** {@link ClientChatReceivedEvent.System}: decide se a mensagem do mod fica no chat ou vai para os Avisos. */
    public static void onChat(ClientChatReceivedEvent.System e) {
        if (e.isOverlay() || e.isCanceled()) return;
        Component msg = e.getMessage();
        if (msg == null) return;
        try {
            Scan scan = scan(msg);
            if (!scan.mod) return;
            Route route = route(scan);
            if (route == Route.LOG) {
                e.setCanceled(true);
                NoticeLog.add(msg, scan.reward ? NoticeLog.Kind.REWARD : NoticeLog.Kind.INFO);
                offerBar(msg);
            } else if (!scan.command) {
                NoticeLog.add(msg, scan.essential ? NoticeLog.Kind.WARNING : NoticeLog.Kind.IMPORTANT);
                NoticeLog.markOneRead();
            }
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.debug("Filtro de chat: mensagem ignorada", ex);
        }
    }

    /** Botão direito (usar item/bloco/criatura): o que chegar logo depois é resposta ao jogador. */
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (e.isUseItem()) lastUse = Util.getMillis();
    }

    /** A cada tick do cliente: lembra se o chat está aberto e solta a próxima linha da hotbar. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (mc.screen instanceof ChatScreen) lastChat = now;
        if (BAR.isEmpty() || now - lastBar < BAR_INTERVAL_MS) return;
        if (mc.player == null) {
            BAR.clear();
            return;
        }
        Component next;
        if (BAR.size() >= BAR_SUMMARY_AT) {
            next = Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("hud.isekai_mahou.notice.many", BAR.size()).withStyle(ChatFormatting.GRAY));
            BAR.clear();
        } else {
            next = BAR.pollFirst();
        }
        lastBar = now;
        lastBarText = next.getString();
        mc.gui.setOverlayMessage(next, false);
    }

    public static void clear() {
        BAR.clear();
        lastBarText = "";
    }

    // ---------------------------------------------------------------- linha acima da hotbar
    /** Mostra uma versão curta do aviso acima da hotbar (fila curta, sem repetir em 2 s). */
    public static void offerBar(Component msg) {
        if (!MahouConfig.safe(MahouConfig.NOTICE_ACTION_BAR)) return;
        Component line = shorten(msg);
        String plain = line.getString();
        if (plain.isBlank()) return;
        long now = Util.getMillis();
        if (plain.equals(lastBarText) && now - lastBar < BAR_REPEAT_MS) return;
        for (Component q : BAR) if (q.getString().equals(plain)) return;
        BAR.addLast(line);
        while (BAR.size() > 8) BAR.pollFirst();
    }

    /** Primeira linha, sem espaços nas pontas, cortada em ~72 letras; cor do texto original. */
    private static Component shorten(Component msg) {
        String plain = msg.getString();
        int nl = plain.indexOf('\n');
        if (nl >= 0) plain = plain.substring(0, nl);
        plain = plain.strip();
        while (plain.startsWith("✦") || plain.startsWith("•") || plain.startsWith("★")) plain = plain.substring(1).strip();
        boolean cut = plain.length() > BAR_MAX_CHARS;
        if (!cut && nl < 0 && !plain.isEmpty() && msg.getString().strip().equals(plain)) {
            return Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(msg.copy());
        }
        if (cut) plain = plain.substring(0, BAR_MAX_CHARS).strip() + "…";
        MutableComponent text = Component.literal(plain);
        TextColor color = firstColor(msg);
        text = color != null ? text.withStyle(s -> s.withColor(color)) : text.withStyle(ChatFormatting.GRAY);
        return Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(text);
    }

    private static TextColor firstColor(Component c) {
        if (c.getStyle().getColor() != null) return c.getStyle().getColor();
        for (Component s : c.getSiblings()) {
            TextColor t = firstColor(s);
            if (t != null) return t;
        }
        return null;
    }

    // ---------------------------------------------------------------- classificação
    /** O que se sabe da mensagem: chaves do mod (no "corpo" e nos argumentos) e se tem clique. */
    static final class Scan {
        final List<String> topKeys = new ArrayList<>();
        final List<String> argKeys = new ArrayList<>();
        boolean mod;
        boolean clickable;
        boolean command;
        boolean essential;
        boolean reward;
    }

    static Scan scan(Component msg) {
        Scan s = new Scan();
        walk(msg, s, false, 0);
        s.mod = !s.topKeys.isEmpty() || !s.argKeys.isEmpty();
        return s;
    }

    private static void walk(Component c, Scan s, boolean arg, int depth) {
        if (c == null || depth > 12) return;
        if (c.getStyle().getClickEvent() != null) s.clickable = true;
        if (c.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key != null && key.contains(IsekaiMahou.MODID)) (arg ? s.argKeys : s.topKeys).add(normalize(key));
            for (Object a : tc.getArgs()) if (a instanceof Component ac) walk(ac, s, true, depth + 1);
        }
        for (Component sib : c.getSiblings()) walk(sib, s, arg, depth + 1);
    }

    /** Decide o destino (já considerando a config e a janela "o jogador pediu"). */
    static Route route(Scan s) {
        List<String> keys = s.topKeys.isEmpty() ? s.argKeys : s.topKeys;
        boolean hint = any(keys, HINT);
        boolean noise = any(keys, NOISE);
        s.essential = any(keys, ESSENTIAL);
        s.reward = any(keys, new String[] {"reward", "loot", "drop", "xp", "granted", "received", "obtained"});
        s.command = any(keys, new String[] {"command.", ".cmd", "cmd_"});
        MahouConfig.ChatMode mode = MahouConfig.safe(MahouConfig.CHAT_MESSAGES);
        if (mode == MahouConfig.ChatMode.ALL) return Route.KEEP;
        if (s.command || s.clickable) return Route.KEEP;
        long now = Util.getMillis();
        boolean asked = now - lastUse <= USE_WINDOW_MS || now - lastChat <= CHAT_WINDOW_MS;
        if (hint) return Route.LOG;
        if (asked || any(keys, REFUSAL)) return Route.KEEP;
        if (noise) return Route.LOG;
        if (s.essential) return Route.KEEP;
        return mode == MahouConfig.ChatMode.NORMAL ? Route.KEEP : Route.LOG;
    }

    /** Chave sem o "tipo.isekai_mahou." do começo; nomes de módulo que enganariam a tabela são trocados. */
    private static String normalize(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        int i = k.indexOf(IsekaiMahou.MODID + ".");
        String head = i > 0 ? k.substring(0, i) : "";
        if (i >= 0) k = k.substring(i + IsekaiMahou.MODID.length() + 1);
        // "arcane_duel" (módulo de magias de resposta) não é um convite de duelo
        k = k.replace("arcane_duel", "arcane_d_");
        // o tipo da chave ("command.", "dialogue.") continua valendo para a tabela
        return head + k;
    }

    private static boolean any(List<String> keys, String[] patterns) {
        for (String k : keys) {
            for (String p : patterns) if (k.contains(p)) return true;
        }
        return false;
    }
}
