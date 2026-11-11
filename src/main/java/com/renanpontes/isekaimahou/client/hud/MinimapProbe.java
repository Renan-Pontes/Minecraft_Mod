package com.renanpontes.isekaimahou.client.hud;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Descobre onde o minimapa está na tela (GUI escalada), para o {@link HudLayout} desviar dele.
 * <ul>
 * <li><b>Xaero's Minimap</b>: pergunta ao próprio mod por reflexão (sessão do módulo "minimap": ativo, posição
 * efetiva, largura/altura na escala da GUI) — sem depender das classes dele para compilar. Se a API mudar, cai na
 * ESTIMATIVA pela config do Xaero ({@code config/xaero/minimap/profiles/*.cfg} + {@code config/xaerohud.txt}) com as
 * mesmas fórmulas dele (tamanho automático ≈ 24% do menor lado da janela; escala de interface automática).</li>
 * <li><b>JourneyMap</b>: estimativa (quadrado de ~28% da altura no canto superior direito).</li>
 * </ul>
 * Em ambos soma as linhas de informação embaixo do mapa (coordenadas etc.) e uma margem. Resultado em cache por 100 ms.
 */
final class MinimapProbe {
    private static final long CACHE_MS = 100;
    private static final long FILE_CACHE_MS = 5000;
    /** Margem em volta do mapa (moldura, sombra, bússola). */
    private static final int PAD = 3;

    private static HudLayout.Rect cached = HudLayout.Rect.EMPTY;
    private static long cachedAt;
    private static int cachedW, cachedH;

    // --- reflexão (Xaero) ---
    private static boolean reflectTried, reflectBroken;
    @Nullable private static Object minimapModule;
    @Nullable private static Method getSession, isActive, effX, effY, getWidth, getHeight, configuredWidth;

    // --- config do Xaero (reserva) ---
    @Nullable private static XaeroFiles files;
    private static long filesAt;

    private record XaeroFiles(boolean display, int size, int uiScale, int infoLines, boolean fromRight, boolean fromBottom,
                              boolean centered, int offX, int offY) {}

    static void invalidate() {
        cachedAt = 0;
        files = null;
    }

    static HudLayout.Rect rect(Minecraft mc, int gw, int gh) {
        long now = Util.getMillis();
        if (now - cachedAt < CACHE_MS && gw == cachedW && gh == cachedH) return cached;
        HudLayout.Rect r;
        try {
            r = compute(mc, gw, gh);
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Leitura do minimapa falhou", e);
            r = HudLayout.Rect.EMPTY;
        }
        cached = r;
        cachedAt = now;
        cachedW = gw;
        cachedH = gh;
        return r;
    }

    private static HudLayout.Rect compute(Minecraft mc, int gw, int gh) {
        ModList mods = ModList.get();
        if (mods == null) return HudLayout.Rect.EMPTY;
        if (mods.isLoaded("xaerominimap") || mods.isLoaded("xaerominimapfair")) {
            HudLayout.Rect r = xaeroReflect(mc, gw, gh);
            return r != null ? r : xaeroEstimate(mc, gw, gh);
        }
        if (mods.isLoaded("journeymap")) {
            int side = Math.round(gh * 0.28F);
            return new HudLayout.Rect(gw - side - PAD * 2, 0, side + PAD * 2, side + 12 + PAD * 2);
        }
        return HudLayout.Rect.EMPTY;
    }

    // =====================================================================================
    //  Xaero por reflexão
    // =====================================================================================
    @Nullable
    private static HudLayout.Rect xaeroReflect(Minecraft mc, int gw, int gh) {
        if (reflectBroken) return null;
        try {
            if (!reflectTried) {
                reflectTried = true;
                Class<?> builtIn = Class.forName("xaero.hud.minimap.BuiltInHudModules");
                minimapModule = builtIn.getField("MINIMAP").get(null);
                getSession = minimapModule.getClass().getMethod("getCurrentSession");
                Class<?> session = Class.forName("xaero.hud.module.ModuleSession");
                isActive = session.getMethod("isActive");
                effX = session.getMethod("getEffectiveX", int.class, double.class);
                effY = session.getMethod("getEffectiveY", int.class, double.class);
                getWidth = session.getMethod("getWidth", double.class);
                getHeight = session.getMethod("getHeight", double.class);
                try {
                    configuredWidth = Class.forName("xaero.hud.minimap.module.MinimapSession").getMethod("getConfiguredWidth");
                } catch (ReflectiveOperationException e) {
                    configuredWidth = null; // só serve para estimar as linhas de texto
                }
            }
            if (minimapModule == null || getSession == null) return null;
            Object s = getSession.invoke(minimapModule);
            if (s == null) {
                // Fora do mundo (sem sessão): estimativa pela config
                return null;
            }
            if (!(Boolean) isActive.invoke(s)) return HudLayout.Rect.EMPTY;
            double scale = mc.getWindow().getGuiScale();
            int x = (Integer) effX.invoke(s, gw, scale);
            int y = (Integer) effY.invoke(s, gh, scale);
            int w = (Integer) getWidth.invoke(s, scale);
            int h = (Integer) getHeight.invoke(s, scale);
            if (w <= 0 || h <= 0) return HudLayout.Rect.EMPTY;
            // Linhas de informação embaixo do mapa, na escala do minimapa
            float textScale = 1F;
            if (configuredWidth != null && configuredWidth.getDeclaringClass().isInstance(s)) {
                int cw = (Integer) configuredWidth.invoke(s);
                if (cw > 0) textScale = Mth.clamp(w / (float) cw, 0.25F, 4F);
            }
            int info = Math.round(infoLines() * 10 * textScale) + 2;
            return pad(x, y, w, h + info, gw, gh);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException | LinkageError e) {
            reflectBroken = true;
            IsekaiMahou.LOGGER.info("Isekai Mahou: API do Xaero's Minimap diferente da esperada; usando a estimativa pela config ({})",
                    e.toString());
            return null;
        }
    }

    // =====================================================================================
    //  Xaero pela config (reserva)
    // =====================================================================================
    private static HudLayout.Rect xaeroEstimate(Minecraft mc, int gw, int gh) {
        XaeroFiles f = files();
        if (!f.display()) return HudLayout.Rect.EMPTY;
        int winW = mc.getWindow().getWidth(), winH = mc.getWindow().getHeight();
        int minDim = Math.max(1, Math.min(winW, winH));
        double guiScale = Math.max(1, mc.getWindow().getGuiScale());
        // Escala de interface do minimapa: 1 = automática, 11 = a da GUI, senão o valor
        double mmScale;
        if (f.uiScale() <= 1) mmScale = minDim >= 1500 ? minDim / 500 : 2;
        else if (f.uiScale() >= 11) mmScale = guiScale;
        else mmScale = f.uiScale();
        int size;
        if (f.size() <= 0) size = Mth.clamp(2 * (int) (minDim / mmScale) * 130 / 1080, 55, 250);
        else size = f.size() < 55 ? f.size() + 54 : f.size();
        int w = (int) Math.ceil((size + 6) * mmScale / guiScale);
        int info = (int) Math.ceil(f.infoLines() * 10 * mmScale / guiScale) + 2;
        int h = w + info;
        int ox = (int) Math.round(f.offX() * mmScale / guiScale), oy = (int) Math.round(f.offY() * mmScale / guiScale);
        int x = f.centered() ? gw / 2 - w / 2 : f.fromRight() ? gw - ox - w : ox;
        int y = f.fromBottom() ? gh - oy - h : oy;
        return pad(x, y, w, h, gw, gh);
    }

    private static HudLayout.Rect pad(int x, int y, int w, int h, int gw, int gh) {
        int x0 = Mth.clamp(x - PAD, 0, gw), y0 = Mth.clamp(y - PAD, 0, gh);
        int x1 = Mth.clamp(x + w + PAD, 0, gw), y1 = Mth.clamp(y + h + PAD, 0, gh);
        if (x1 <= x0 || y1 <= y0) return HudLayout.Rect.EMPTY;
        return new HudLayout.Rect(x0, y0, x1 - x0, y1 - y0);
    }

    private static int infoLines() {
        return files().infoLines();
    }

    /** Config do Xaero (relida no máximo a cada 5 s). */
    private static XaeroFiles files() {
        long now = Util.getMillis();
        XaeroFiles f = files;
        if (f != null && now - filesAt < FILE_CACHE_MS) return f;
        f = readFiles();
        files = f;
        filesAt = now;
        return f;
    }

    private static XaeroFiles readFiles() {
        Path cfg = FMLPaths.CONFIGDIR.get();
        Path dir = cfg.resolve("xaero").resolve("minimap");
        Map<String, String> client = keyValues(dir.resolve("client.cfg"));
        String profile = client.getOrDefault("current_profile", "default");
        if (!profile.matches("[A-Za-z0-9_\\-]{1,64}")) profile = "default";
        Map<String, String> p = keyValues(dir.resolve("profiles").resolve(profile + ".cfg"));
        boolean display = !"false".equalsIgnoreCase(p.getOrDefault("display_minimap", "true"));
        int size = parseInt(p.get("minimap_size"), 0);
        int ui = parseInt(p.get("minimap_ui_scale"), 1);
        int info = countInfoLines(dir.resolve("profiles").resolve("info_display_config").resolve(profile + ".cfg.txt"));

        boolean fromRight = true, fromBottom = false, centered = false;
        int ox = 0, oy = 0;
        for (String line : lines(cfg.resolve("xaerohud.txt"))) {
            if (!line.contains("id=xaerominimap:minimap")) continue;
            Map<String, String> m = new HashMap<>();
            for (String part : line.split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) m.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
            fromRight = "true".equalsIgnoreCase(m.getOrDefault("fromRight", "true"));
            fromBottom = "true".equalsIgnoreCase(m.getOrDefault("fromBottom", "false"));
            centered = "true".equalsIgnoreCase(m.getOrDefault("centered", "false"));
            ox = parseInt(m.get("x"), 0);
            oy = parseInt(m.get("y"), 0);
        }
        return new XaeroFiles(display, size, ui, info, fromRight, fromBottom, centered, ox, oy);
    }

    /** Linhas fixas embaixo do mapa (coordenadas, ângulos, bioma...). Sem o arquivo: só as coordenadas. */
    private static int countInfoLines(Path file) {
        List<String> lines = lines(file);
        if (lines.isEmpty()) return 1;
        int n = 0;
        for (String l : lines) {
            if (!l.startsWith("infoDisplay:")) continue;
            String[] p = l.split(":");
            if (p.length < 3) continue;
            String id = p[1], v = p[2];
            // indicadores que só aparecem em situações especiais não contam
            if (id.equals("highlights") || id.endsWith("_indicator") || id.equals("custom_sub_world") || id.equals("overworld_coords")) continue;
            if (v.equalsIgnoreCase("true") || (!v.equalsIgnoreCase("false") && !v.equals("0"))) n++;
        }
        return Mth.clamp(n, 0, 8);
    }

    private static Map<String, String> keyValues(Path file) {
        Map<String, String> out = new HashMap<>();
        for (String l : lines(file)) {
            if (l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq > 0) out.put(l.substring(0, eq).trim(), l.substring(eq + 1).trim());
        }
        return out;
    }

    private static List<String> lines(Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static int parseInt(@Nullable String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private MinimapProbe() {}
}
