package com.renanpontes.isekaimahou.feature.forms_b.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellFormEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Limite de magias persistentes (zonas, pulsos, runas, torres) por conjurador:
 * {@code MahouConfig.MAX_PERSISTENT_SPELLS}. Ao passar do limite, a mais antiga se desfaz.
 * Só conta as que estão carregadas no mundo (servidor).
 */
public final class PersistentSpells {
    private static final Map<UUID, List<SpellFormEntity>> BY_OWNER = new HashMap<>();

    /** Registra uma magia persistente ativa (chamado no primeiro tick dela no servidor). */
    public static void track(SpellFormEntity e) {
        UUID owner = e.ownerId();
        if (owner == null) return;
        List<SpellFormEntity> list = BY_OWNER.computeIfAbsent(owner, k -> new ArrayList<>());
        if (!list.contains(e)) list.add(e);
        list.sort(Comparator.comparingLong(SpellFormEntity::createdTime));
        int max = FormsBUtil.maxPersistent();
        boolean evicted = false;
        while (list.size() > max) {
            SpellFormEntity old = list.remove(0);
            old.fizzle();
            evicted = true;
        }
        if (list.isEmpty()) BY_OWNER.remove(owner);
        if (evicted) {
            MinecraftServer server = e.getServer();
            ServerPlayer sp = server != null ? server.getPlayerList().getPlayer(owner) : null;
            if (sp != null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.persistent_limit", max)
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Remove do controle (a entidade sumiu ou o chunk descarregou). */
    public static void untrack(SpellFormEntity e) {
        UUID owner = e.ownerId();
        if (owner == null) return;
        List<SpellFormEntity> list = BY_OWNER.get(owner);
        if (list == null) return;
        list.remove(e);
        if (list.isEmpty()) BY_OWNER.remove(owner);
    }

    /** Servidor fechou: esquece tudo. */
    public static void clear() {
        BY_OWNER.clear();
    }

    private PersistentSpells() {}
}
