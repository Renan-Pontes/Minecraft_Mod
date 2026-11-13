package com.renanpontes.isekaimahou.core.classes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Fachada do núcleo para os TALENTOS e ESPECIALIZAÇÕES (v1.0). Quem implementa é o módulo {@code talents}
 * ({@code feature.talents.Talents}); o núcleo não pode depender de módulo, então a ligação é feita por nome, uma única
 * vez, e guardada. Serve para qualquer módulo ler a especialização do jogador, somar uma passiva ou resetar os talentos
 * sem depender das classes do módulo. Sem o módulo tudo devolve o padrão (sem especialização, 0).
 * <pre>
 *   if (ClassTalents.hasSubclass(player, "rogue_assassin")) { ... }     // ids: &lt;classe&gt;_&lt;especialização&gt;
 *   ClassTalents.reset(serverPlayer);                                   // Altar do Recomeço
 * </pre>
 */
public final class ClassTalents {
    private static final String IMPL = "com.renanpontes.isekaimahou.feature.talents.Talents";

    private static boolean looked;
    private static MethodHandle reset, subclass, stat, unspent;

    private static synchronized boolean ready() {
        if (!looked) {
            looked = true;
            try {
                Class<?> impl = Class.forName(IMPL);
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                reset = l.findStatic(impl, "reset", MethodType.methodType(void.class, ServerPlayer.class, boolean.class));
                subclass = l.findStatic(impl, "subclass", MethodType.methodType(String.class, Player.class));
                stat = l.findStatic(impl, "stat", MethodType.methodType(double.class, Player.class, String.class));
                unspent = l.findStatic(impl, "unspent", MethodType.methodType(int.class, Player.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                reset = subclass = stat = unspent = null;
                IsekaiMahou.LOGGER.debug("Módulo de talentos ausente: ClassTalents fica no padrão", e);
            }
        }
        return reset != null;
    }

    /** O módulo de talentos está presente? */
    public static boolean available() {
        return ready();
    }

    /** Recomeço completo: pontos devolvidos e especialização desfeita (o jogador escolhe de novo). */
    public static void reset(ServerPlayer player) {
        reset(player, true);
    }

    /** Devolve os pontos da classe atual; com {@code includeSubclass}, também desfaz a especialização. */
    public static void reset(ServerPlayer player, boolean includeSubclass) {
        if (player == null || !ready()) return;
        try {
            reset.invoke(player, includeSubclass);
        } catch (Throwable t) {
            IsekaiMahou.LOGGER.error("Falha ao resetar talentos", t);
        }
    }

    /** Especialização da classe atual ("" = nenhuma ou módulo ausente). No cliente vale para o jogador local. */
    public static String subclass(Player player) {
        if (player == null || !ready()) return "";
        try {
            return (String) subclass.invoke(player);
        } catch (Throwable t) {
            return "";
        }
    }

    public static boolean hasSubclass(Player player, String id) {
        return id != null && !id.isEmpty() && id.equals(subclass(player));
    }

    /** Soma de uma passiva de talento (chaves em {@code feature.talents.TalentStats}); 0 se não tem. Só no servidor. */
    public static double stat(Player player, String key) {
        if (player == null || !ready()) return 0.0;
        try {
            return (double) stat.invoke(player, key);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** Pontos de talento livres. */
    public static int unspent(Player player) {
        if (player == null || !ready()) return 0;
        try {
            return (int) unspent.invoke(player);
        } catch (Throwable t) {
            return 0;
        }
    }

    private ClassTalents() {}
}
