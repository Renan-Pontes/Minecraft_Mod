package com.renanpontes.isekaimahou.feature.cinematic.client;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Leitura segura das opções de cliente (a config pode ainda não ter carregado nos primeiros instantes). */
final class CinematicSettings {

    static boolean cutscenes() {
        return get(MahouConfig.CUTSCENES, true);
    }

    static boolean ownCutscenesOnly() {
        return get(MahouConfig.OWN_CUTSCENES_ONLY, false);
    }

    static boolean othersAnnouncements() {
        return get(MahouConfig.OTHERS_ANNOUNCEMENTS, true);
    }

    static boolean reducedParticles() {
        return get(MahouConfig.REDUCED_PARTICLES, false);
    }

    private static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private CinematicSettings() {}
}
