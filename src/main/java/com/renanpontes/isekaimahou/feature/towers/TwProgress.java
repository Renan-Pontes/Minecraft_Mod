package com.renanpontes.isekaimahou.feature.towers;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.server.level.ServerPlayer;

/** Flags e contadores do capítulo do diário "towers". */
public final class TwProgress {
    public static final String FLAG_ENTERED = "towers.entered";
    public static final String FLAG_PUZZLE = "towers.puzzle";
    public static final String FLAG_CRYSTAL = "towers.crystal";
    public static final String FLAG_TWIN = "towers.twin";
    /** +1 por andar vencido (qualquer torre). */
    public static final String COUNTER_FLOOR = "towers.floor";
    /** +1 por pináculo conquistado. */
    public static final String COUNTER_CLEARED = "towers.cleared";

    /** Marca a flag uma vez; devolve true se foi a primeira vez. */
    public static boolean flag(ServerPlayer sp, String key) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(key)) return false;
        d.setFlag(key, true);
        d.markDirty();
        return true;
    }

    private TwProgress() {}
}
