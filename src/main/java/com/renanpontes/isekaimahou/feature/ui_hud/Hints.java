package com.renanpontes.isekaimahou.feature.ui_hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Dicas de primeira vez</b> (v1.0 RELEASE) — "tem que ser simples pra não explodir na tela do cara".
 * Uma linha discreta acima da barra de itens, UMA por vez, que some sozinha e NUNCA repete para o mesmo jogador.
 *
 * <pre>
 * Hints.show(player, "city_founded");                                   // texto: hint.isekai_mahou.city_founded
 * Hints.show(player, "safe_zone", Component.keybind("key.isekai_mahou.peace_mode"));   // com %s no texto
 * </pre>
 * Regras:
 * <ul>
 *   <li>Chame no SERVIDOR (no cliente a chamada é ignorada). Pode chamar à vontade (todo tick, até): se o jogador já
 *       viu, não faz nada e custa uma consulta num mapa.</li>
 *   <li>O texto vem da tradução {@code hint.isekai_mahou.<id>} (pt_br + en_us no lang do SEU módulo). Curto: uma frase.</li>
 *   <li>A dica só conta como vista quando o cliente realmente a mostrou. Com as dicas desligadas (opção "Dicas de
 *       primeira vez") ou a fila cheia, ela é descartada e volta numa próxima sessão.</li>
 *   <li>Ids: {@code a-z 0-9 _ .}, até 64 letras. Use um prefixo do seu módulo se o nome for genérico.</li>
 * </ul>
 */
public final class Hints {
    static final int MAX_ID = 64;
    /** Teto de dicas guardadas por jogador (ninguém chega perto; só evita crescer sem limite). */
    private static final int MAX_STORED = 512;

    /** Dicas mandadas nesta sessão e ainda não confirmadas pelo cliente (não reenviar a cada chamada). */
    private static final Map<UUID, Set<String>> SENT = new ConcurrentHashMap<>();

    private Hints() {}

    /** Mostra a dica {@code id} para o jogador se ele nunca a viu. Os {@code args} preenchem os {@code %s} do texto. */
    public static void show(Player player, String id, Object... args) {
        if (!(player instanceof ServerPlayer sp) || !validId(id)) return;
        Set<String> sent = SENT.get(sp.getUUID());
        if (sent != null && sent.contains(id)) return;
        if (stored(sp).contains(id)) return;
        SENT.computeIfAbsent(sp.getUUID(), u -> ConcurrentHashMap.newKeySet()).add(id);
        PacketDistributor.sendToPlayer(sp, new HintPayloads.Show(id, components(args), false));
    }

    /** O jogador já viu (ou já recebeu nesta sessão) a dica? */
    public static boolean seen(Player player, String id) {
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        Set<String> sent = SENT.get(sp.getUUID());
        return sent != null && sent.contains(id) || stored(sp).contains(id);
    }

    /** Depuração: mostra a dica mesmo que já tenha sido vista (e com as dicas desligadas). */
    public static void force(ServerPlayer sp, String id, Object... args) {
        if (!validId(id)) return;
        PacketDistributor.sendToPlayer(sp, new HintPayloads.Show(id, components(args), true));
    }

    /** Depuração: o jogador volta a ver todas as dicas. */
    public static void reset(ServerPlayer sp) {
        sp.setData(UiHudModule.HINTS.get(), new CompoundTag());
        SENT.remove(sp.getUUID());
    }

    /** O cliente mostrou a dica: agora ela fica guardada com o jogador (sobrevive à morte e ao reinício). */
    static void confirm(ServerPlayer sp, String id) {
        if (!validId(id)) return;
        Set<String> sent = SENT.get(sp.getUUID());
        // Só confirma o que o servidor mandou nesta sessão (um cliente não enche os dados do jogador com lixo)
        if (sent == null || !sent.contains(id)) return;
        CompoundTag tag = stored(sp);
        if (tag.contains(id) || tag.size() >= MAX_STORED) return;
        tag.putBoolean(id, true);
        sp.setData(UiHudModule.HINTS.get(), tag);
    }

    static void forget(UUID player) {
        SENT.remove(player);
    }

    private static CompoundTag stored(ServerPlayer sp) {
        return sp.getData(UiHudModule.HINTS.get());
    }

    static boolean validId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.')) return false;
        }
        return true;
    }

    private static List<Component> components(Object[] args) {
        List<Component> out = new ArrayList<>();
        if (args == null) return out;
        for (Object a : args) {
            if (out.size() >= 4) break;
            out.add(a instanceof Component c ? c : Component.literal(String.valueOf(a)));
        }
        return out;
    }
}
