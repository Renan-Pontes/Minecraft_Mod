package com.renanpontes.isekaimahou.feature.nether_citadel;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Flags do capítulo do diário (nether_citadel.*) e consultas simples ao progresso do jogador. */
public final class NcProgress {
    public static final String FLAG_ENTERED = "nether_citadel.entered";
    public static final String FLAG_ADAMANTITE = "nether_citadel.adamantite";
    public static final String FLAG_PACT = "nether_citadel.pact";
    public static final String FLAG_FORGE = "nether_citadel.forge";

    /** Marca a flag uma vez; devolve true se foi a primeira vez. */
    public static boolean flag(ServerPlayer sp, String key) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(key)) return false;
        d.setFlag(key, true);
        d.markDirty();
        return true;
    }

    public static boolean isWarlock(Player p) {
        return MagicApi.get(p).path() == MagicPath.WARLOCK;
    }

    private NcProgress() {}
}
