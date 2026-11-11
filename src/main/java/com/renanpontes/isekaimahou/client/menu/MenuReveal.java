package com.renanpontes.isekaimahou.client.menu;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.notify.ChatRouter;
import com.renanpontes.isekaimahou.client.notify.NoticeLog;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Revelação PROGRESSIVA do menu B (cliente, v1.1). No começo o menu mostra só o essencial — Status, Raça, a aba da
 * sua classe, Grimório, Loadouts, Missões (Guia de Viagem) e Avisos. As outras abas aparecem quando fazem sentido,
 * lendo contadores/flags que já vêm sincronizados no {@link MagicData} (com folga: quem já tem progresso vê tudo o que
 * já liberou; nível/Círculo altos liberam por segurança):
 * <ul>
 *   <li>Glifos: 2º elemento, pergaminho de lenda no inventário, 12+ glifos, nível 15 ou Círculo 2;</li>
 *   <li>Caminhos: 1º nó de caminho, 2º elemento, nível 10 ou Círculo 2;</li>
 *   <li>Provações: 1ª Provação disponível/concluída ou Círculo 2;</li>
 *   <li>Guilda: registrado na Guilda ou visitou uma cidade; Reputação/Cidade: visitou uma cidade/vila;</li>
 *   <li>Grupo: está num grupo ou recebeu um pedido;</li>
 *   <li>Lugares (Viagem): descobriu uma Pedra de Teleporte/um destino; Bestiário: 1ª criatura registrada.</li>
 * </ul>
 * Uma aba revelada continua revelada (guardado por mundo em {@code config/isekai_mahou-menu.properties}). Na primeira
 * vez que aparece, ganha o selo "Nova!" até ser aberta, e um aviso vai para a hotbar/registro. Config
 * {@code progressiveMenu = false} mostra tudo desde o começo.
 */
public final class MenuReveal {
    private static final Map<String, Predicate<MagicData>> RULES = new HashMap<>();
    private static final String FILE = "isekai_mahou-menu.properties";

    /** Mundo atual (chave do arquivo) e o que já foi revelado/visto nele. */
    private static String world;
    private static final Set<String> REVEALED = new LinkedHashSet<>();
    private static final Set<String> SEEN = new LinkedHashSet<>();
    /** Primeira avaliação neste mundo: o que já está liberado não ganha selo "Nova!" (jogadores antigos). */
    private static boolean firstPass;
    private static boolean dirty;
    private static int lastSync = -1;
    private static final Map<String, long[]> NEGATIVE = new HashMap<>();

    static {
        RULES.put("glyphs", d -> elements(d) >= 2 || d.glyphs().size() >= 12 || d.level() >= 15 || d.circle() >= 2
                || hasLegendScroll());
        RULES.put("legends", RULES.get("glyphs"));
        RULES.put("paths", d -> nodes(d) >= 1 || elements(d) >= 2 || d.level() >= 10 || d.circle() >= 2);
        RULES.put("trials", d -> anyFlag(d, "trials.notified.") || d.counter("trial.clear") > 0 || anyFlag(d, "trial.")
                || nodes(d) >= 1 || d.circle() >= 2);
        RULES.put("guild", d -> d.counter("guild_rank") > 0 || visitedTown(d) || d.circle() >= 2);
        RULES.put("reputation", d -> visitedTown(d) || d.counter("guild_rank") > 0 || d.circle() >= 2);
        RULES.put("settlement", d -> visitedTown(d) || d.circle() >= 2 || hasAllegiance());
        RULES.put("party", d -> ClientSocialView.inParty() || hasRequests() || d.counter("duel.win") + d.counter("duel.loss") > 0);
        RULES.put("travel", d -> anyFlag(d, "travel.") || d.counter("travel.count") > 0 || d.counter("visit.waystone") > 0
                || d.circle() >= 2);
        RULES.put("bestiary", d -> anyCounter(d, "bestiary.") || anyCounter(d, "kill.") || anyCounter(d, "boss."));
    }

    private MenuReveal() {}

    /** A aba está liberada? (abas sem regra — essenciais e de módulos novos — sempre.) */
    public static boolean revealed(String id) {
        Predicate<MagicData> rule = RULES.get(id);
        if (rule == null || !MahouConfig.safe(MahouConfig.PROGRESSIVE_MENU)) return true;
        ensureWorld();
        if (REVEALED.contains(id)) return true;
        // Resposta negativa recente (mesma sincronização, < 1 s): não reavalia a cada quadro
        long[] neg = NEGATIVE.get(id);
        long ms = net.minecraft.Util.getMillis();
        if (neg != null && neg[0] == ClientMagic.syncVersion && ms - neg[1] < 1000L) return false;
        boolean now;
        try {
            now = rule.test(ClientMagic.data());
        } catch (RuntimeException e) {
            now = true;
        }
        if (now) reveal(id);
        else NEGATIVE.put(id, new long[] {ClientMagic.syncVersion, ms});
        return now;
    }

    /** Regra extra (ou troca) para um id de aba: a aba só aparece quando o predicado for verdadeiro. */
    public static void addRule(String id, Predicate<MagicData> rule) {
        RULES.put(id, rule);
    }

    /**
     * Libera a aba na hora (ex.: o NPC da Guilda abre a aba "guild" antes da regra valer): quem abre algo pelo jogo
     * nunca cai numa aba escondida.
     */
    public static void force(String id) {
        if (!RULES.containsKey(id)) return;
        ensureWorld();
        if (REVEALED.add(id)) {
            NEGATIVE.remove(id);
            save();
        }
    }

    /** A aba ainda tem o selo "Nova!" (revelada mas nunca aberta)? */
    public static boolean isNew(String id) {
        if (!RULES.containsKey(id) || !MahouConfig.safe(MahouConfig.PROGRESSIVE_MENU)) return false;
        ensureWorld();
        return REVEALED.contains(id) && !SEEN.contains(id);
    }

    /** O jogador abriu a aba: tira o selo "Nova!". */
    public static void markSeen(String id) {
        ensureWorld();
        if (REVEALED.contains(id) && SEEN.add(id)) {
            dirty = true;
            save();
        }
    }

    /**
     * A cada tick (cliente, no mundo): quando os dados sincronizam, reavalia as regras — uma aba que acabou de aparecer
     * gera um aviso curto ("Nova aba no menu (B): Provações").
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (ClientMagic.syncVersion == lastSync && mc.player.tickCount % 40 != 0) return;
        lastSync = ClientMagic.syncVersion;
        if (ClientMagic.syncVersion == 0) return;   // ainda sem dados do servidor
        ensureWorld();
        boolean announce = !firstPass;
        for (String id : RULES.keySet()) {
            if (REVEALED.contains(id) || MenuTabs.get(id) == null) continue;
            if (!revealed(id)) continue;
            if (announce && MenuTabs.visible(id)) {
                MenuTabs.Entry e = MenuTabs.get(id);
                Component msg = Component.translatable("hud.isekai_mahou.menu.new_tab", e.title()).withStyle(ChatFormatting.GOLD);
                NoticeLog.add(msg, NoticeLog.Kind.MENU);
                ChatRouter.offerBar(msg);
            }
        }
        if (firstPass) {
            // Primeira vez neste mundo: o que já estava liberado não é "novo"
            SEEN.addAll(REVEALED);
            firstPass = false;
            dirty = true;
        }
        if (dirty) save();
    }

    public static void logout() {
        if (dirty) save();
        world = null;
        REVEALED.clear();
        SEEN.clear();
        NEGATIVE.clear();
        lastSync = -1;
    }

    private static void reveal(String id) {
        if (REVEALED.add(id)) dirty = true;
    }

    // ---------------------------------------------------------------- regras
    /** Elementos despertados sem contar o Arcano (todo mundo tem). */
    private static int elements(MagicData d) {
        int n = 0;
        for (Element e : d.awakenedElements()) if (e != Element.ARCANE) n++;
        return n;
    }

    private static int nodes(MagicData d) {
        int n = 0;
        for (MagicData.ElementProgress p : d.elements().values()) n += p.nodes.size();
        return n;
    }

    private static boolean visitedTown(MagicData d) {
        return d.counter("visit.city") + d.counter("visit.capital") + d.counter("visit.village") + d.counter("visit.town") > 0;
    }

    private static boolean anyFlag(MagicData d, String prefix) {
        for (String f : d.flags()) if (f.startsWith(prefix)) return true;
        return false;
    }

    private static boolean anyCounter(MagicData d, String prefix) {
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getValue() > 0 && e.getKey().startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean hasRequests() {
        try {
            return !ClientSocialView.state().requests().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasAllegiance() {
        try {
            return !ClientSocialView.state().allegiance().isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasLegendScroll() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty()) continue;
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (path.contains("legend")) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- arquivo por mundo
    private static void ensureWorld() {
        String key = worldKey();
        if (key.equals(world)) return;
        world = key;
        REVEALED.clear();
        SEEN.clear();
        Properties props = load();
        String rev = props.getProperty(key + ".revealed");
        firstPass = rev == null;
        if (rev != null) REVEALED.addAll(split(rev));
        String seen = props.getProperty(key + ".seen");
        if (seen != null) SEEN.addAll(split(seen));
        dirty = false;
    }

    private static Set<String> split(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : Arrays.asList(s.split(","))) if (!p.isBlank()) out.add(p.strip());
        return out;
    }

    /** Mundo do singleplayer (nome da pasta) ou endereço do servidor + o jogador. */
    private static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        String where;
        if (mc.getSingleplayerServer() != null) {
            where = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData sd = mc.getCurrentServer();
            where = "mp_" + (sd != null ? sd.ip : "unknown");
        }
        String who = mc.player != null ? mc.player.getUUID().toString().substring(0, 8) : "anon";
        return (where + "_" + who).replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static Properties load() {
        Properties props = new Properties();
        Path f = file();
        if (!Files.exists(f)) return props;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (Exception e) {
            IsekaiMahou.LOGGER.debug("Não foi possível ler {}", FILE, e);
        }
        return props;
    }

    private static void save() {
        if (world == null) return;
        dirty = false;
        Properties props = load();
        props.setProperty(world + ".revealed", String.join(",", REVEALED));
        props.setProperty(world + ".seen", String.join(",", SEEN));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            props.store(w, "Isekai Mahou - abas do menu (B) ja liberadas/vistas por mundo. Apagar = recomecar os selos \"Nova!\".");
        } catch (Exception e) {
            IsekaiMahou.LOGGER.debug("Não foi possível salvar {}", FILE, e);
        }
    }
}
