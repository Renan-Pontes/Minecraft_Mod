package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.Map;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;

import net.minecraft.server.level.ServerPlayer;

/**
 * Contadores e marcas que alimentam as Provas da Forja (lado comum: a aba do cliente lê os mesmos dados sincronizados).
 * Só contam para quem é Ferreiro — um multiclasse pode forjar, mas não sobe as Provas de outra classe.
 */
public final class SmithProgress {
    public static final String RANK = "smith.rank";
    public static final String FORGED = "smith.forged";
    public static final String RESHAPED = "smith.reshaped";
    public static final String BLADE_KILLS = "smith.blade_kills";
    public static final String ELITE_KILLS = "smith.elite_kills";
    public static final String ORES = "smith.ores";
    public static final String RUNES = "smith.runes";
    public static final String CASTS = "smith.casts";
    public static final String FAMILY_FLAG = "smith.family.";
    public static final String MATERIAL_FLAG = "smith.material.";
    public static final String MASTERWORK_FLAG = "smith.masterwork";
    /** Magias prontas e técnicas iniciais já entregues. */
    public static final String STARTER_FLAG = "smith.starter";

    public static int rank(MagicData d) {
        return d == null ? 0 : d.counter(RANK);
    }

    private static boolean smith(ServerPlayer sp) {
        return MagicApi.get(sp).path() == MagicPath.SMITH;
    }

    public static void onReshaped(ServerPlayer sp, ForgedBlade before, ForgedBlade after) {
        if (!smith(sp)) return;
        MagicApi.addCounter(sp, RESHAPED, 1);
        mark(sp, after);
    }

    public static void onForged(ServerPlayer sp, ForgedBlade blade) {
        if (!smith(sp)) return;
        MagicApi.addCounter(sp, FORGED, 1);
        mark(sp, blade);
    }

    public static void onRune(ServerPlayer sp) {
        if (smith(sp)) MagicApi.addCounter(sp, RUNES, 1);
    }

    /** Marca família, material e obra-prima já alcançados (tarefas dos ranks III–V). */
    public static void mark(ServerPlayer sp, ForgedBlade b) {
        MagicData d = MagicApi.get(sp);
        boolean dirty = false;
        if (!d.hasFlag(FAMILY_FLAG + b.family().id)) {
            d.setFlag(FAMILY_FLAG + b.family().id, true);
            dirty = true;
        }
        if (!d.hasFlag(MATERIAL_FLAG + b.material().id)) {
            d.setFlag(MATERIAL_FLAG + b.material().id, true);
            dirty = true;
        }
        if (b.masterwork() && !d.hasFlag(MASTERWORK_FLAG)) {
            d.setFlag(MASTERWORK_FLAG, true);
            dirty = true;
        }
        if (dirty) d.markDirty();
    }

    /** Quantas famílias diferentes o Ferreiro já forjou. */
    public static int families(MagicData d) {
        int n = 0;
        for (BladeFamily f : BladeFamily.VALUES) if (d.hasFlag(FAMILY_FLAG + f.id)) n++;
        return n;
    }

    public static boolean hasMaterial(MagicData d, BladeMaterial m) {
        return d.hasFlag(MATERIAL_FLAG + m.id);
    }

    /** Chefes diferentes derrotados por perto (contadores {@code boss.<mob>} do núcleo). */
    public static int bosses(MagicData d) {
        int n = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("boss.") && e.getValue() > 0) n++;
        }
        return n;
    }

    private SmithProgress() {}
}
