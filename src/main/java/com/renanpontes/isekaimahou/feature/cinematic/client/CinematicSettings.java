package com.renanpontes.isekaimahou.feature.cinematic.client;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.cinematic.CinematicConfig;

import net.minecraft.client.Minecraft;
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

    /**
     * v1.1 — Quadros de impacto (cor invertida por 1–2 quadros): opção própria + acessibilidade do Minecraft
     * ("Esconder relâmpagos" ligado ou "Efeitos de distorção" em 0 desligam).
     */
    static boolean impactFrames() {
        if (!CinematicConfig.on(CinematicConfig.IMPACT_FRAMES)) return false;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.options.hideLightningFlash().get()) return false;
            return mc.options.screenEffectScale().get() > 0.01;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** v1.1 — Linhas de velocidade estilo mangá. */
    static boolean speedLines() {
        return CinematicConfig.on(CinematicConfig.SPEED_LINES);
    }

    /** Fator de partículas das cutscenes (metade com "Menos partículas"). */
    static float particleFactor() {
        return reducedParticles() ? 0.5F : 1F;
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
