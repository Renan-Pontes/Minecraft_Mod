package com.renanpontes.isekaimahou.client.perf;

import java.util.Locale;

import com.mojang.blaze3d.platform.GlUtil;

import net.minecraft.network.chat.Component;

/**
 * SÓ CLIENTE. Olha o hardware (memória do Java, núcleos, nome da placa de vídeo) e sugere um {@link PcPreset}.
 * Heurística simples e conservadora: placa integrada (Intel UHD/Iris/HD, AMD Vega/Radeon Graphics de APU) nunca passa de
 * "OK"; renderizador por software → "Fraco". Ler a GPU exige a thread de render (chamar só do cliente).
 */
public final class HardwareProbe {
    public record Info(long maxMemMb, int cores, String gpu, boolean integrated, boolean software, PcPreset suggestion) {
        /** "6 GB para o jogo · 8 núcleos · Intel(R) UHD Graphics 620". */
        public Component summary() {
            String mem = maxMemMb >= 1024 ? String.format(Locale.ROOT, "%.1f", maxMemMb / 1024.0) : String.valueOf(maxMemMb);
            return Component.translatable(maxMemMb >= 1024 ? "screen.isekai_mahou.perf.hw.gb" : "screen.isekai_mahou.perf.hw.mb",
                    mem, cores, gpu.isBlank() ? "?" : gpu);
        }
    }

    private static Info cached;

    public static Info info() {
        if (cached == null) cached = probe();
        return cached;
    }

    private static Info probe() {
        long mem = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        int cores = Runtime.getRuntime().availableProcessors();
        String gpu = "";
        try {
            String r = GlUtil.getRenderer();
            if (r != null) gpu = r.strip();
        } catch (RuntimeException | LinkageError e) {
            gpu = "";
        }
        String g = gpu.toLowerCase(Locale.ROOT);
        boolean software = g.contains("llvmpipe") || g.contains("softpipe") || g.contains("swiftshader") || g.contains("gdi generic")
                || g.contains("microsoft basic");
        boolean integrated = software || isIntegrated(g);
        boolean strongGpu = !integrated && (g.matches(".*rtx\\s*[2-5]0[6-9]0.*") || g.matches(".*rx\\s*[6-9][7-9]\\d\\d.*")
                || g.contains("rtx 4") || g.contains("rtx 5") || g.matches(".*rx\\s*7[89]\\d\\d.*") || g.matches(".*rx\\s*9\\d\\d\\d.*"));

        PcPreset s;
        if (software || mem < 2600 || cores <= 2) s = PcPreset.LOW;
        else if (integrated) s = (mem < 3500 || cores <= 4) ? PcPreset.LOW : PcPreset.OK;
        else if (mem < 4000 || cores <= 4) s = PcPreset.OK;
        else if (strongGpu && mem >= 6000 && cores >= 8) s = PcPreset.ULTRA;
        else s = PcPreset.GOOD;
        return new Info(mem, cores, gpu, integrated, software, s);
    }

    /** Placas de vídeo integradas comuns (o nome do renderizador OpenGL). */
    private static boolean isIntegrated(String g) {
        if (g.contains("intel")) {
            // Intel Arc (A3xx/A7xx, B5xx) é dedicada; o resto da Intel é integrada (UHD, Iris, HD Graphics)
            return !(g.contains("arc") && (g.matches(".*\\ba\\d{3}.*") || g.matches(".*\\bb\\d{3}.*")));
        }
        if (g.contains("vega") && !g.contains("rx vega")) return true;
        // APUs AMD: "AMD Radeon(TM) Graphics", "Radeon 680M/780M/890M" (sem "RX")
        if ((g.contains("radeon(tm) graphics") || g.equals("amd radeon graphics") || g.matches(".*radeon\\s*\\d{3}m.*"))
                && !g.contains(" rx ")) return true;
        return g.contains("mali") || g.contains("adreno") || g.contains("apple m") || g.contains("uhd graphics");
    }

    private HardwareProbe() {}
}
