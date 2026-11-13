package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.Map;

/**
 * GERADO por {@code tools/music_import.py} — não editar à mão. Para cada tema: ticks do início da faixa até o "drop"
 * (onde a música explode). A entrada do chefe ({@link BossEntrance}) dura isso e ele acorda no drop.
 */
public final class BossMusicCues {
    private static final Map<String, Integer> INTRO = Map.ofEntries(
            Map.entry("abyss_sovereign", 234),
            Map.entry("barragan", 243),
            Map.entry("beast", 0),
            Map.entry("generic_boss", 20),
            Map.entry("grimmjow", 0),
            Map.entry("hollow_king", 232),
            Map.entry("lich", 251),
            Map.entry("mimic_king", 21),
            Map.entry("primordial_dragon", 240),
            Map.entry("seraph", 236),
            Map.entry("twilight_herald", 177),
            Map.entry("ulquiorra", 229));

    /** Chefes com faixa PRÓPRIA importada (pasta do chefe com música). */
    private static final java.util.Set<String> BOSS_TRACKS = java.util.Set.of();

    public static boolean hasBossTrack(String boss) {
        return boss != null && BOSS_TRACKS.contains(boss);
    }

    /** Ticks até o drop (0 = a música já começa forte). */
    public static int introTicks(String theme) {
        return theme == null ? 0 : INTRO.getOrDefault(theme, 0);
    }

    private BossMusicCues() {}
}
