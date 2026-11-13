package com.renanpontes.isekaimahou.feature.travel.guide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * v1.0 — o Xaero's World Map tem um "perfil de servidor" ({@code config/xaero/world-map/server_profiles/default.cfg}) que
 * o servidor IMPÕE aos clientes. Aqui a opção do teleporte pelo mapa entra nele como desligada: quem joga no servidor
 * (ou no mundo local) nem vê o botão funcionando, mesmo que mexa na própria config. Roda nos dois lados na construção
 * do mod; só acrescenta/troca essa linha. O bloqueio de verdade continua sendo o {@code core/ux/MapTeleportGuard}.
 */
public final class XaeroServerProfile {
    private static final String KEY = "map_teleport_allowed";

    public static void seed() {
        try {
            ModList mods = ModList.get();
            if (mods == null || !mods.isLoaded("xaeroworldmap")) return;
            Path file = FMLPaths.CONFIGDIR.get().resolve("xaero").resolve("world-map").resolve("server_profiles").resolve("default.cfg");
            List<String> lines = new ArrayList<>();
            if (Files.exists(file)) {
                lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            } else {
                Files.createDirectories(file.getParent());
                lines.addAll(List.of("############", "## Server Config Profile \"default\"", "##", "", "profile_name = Default"));
            }
            boolean found = false, changed = !Files.exists(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.startsWith(KEY)) continue;
                String rest = line.substring(KEY.length()).trim();
                if (!rest.startsWith("=")) continue;
                found = true;
                if (!"false".equalsIgnoreCase(rest.substring(1).trim())) {
                    lines.set(i, KEY + " = false");
                    changed = true;
                }
            }
            if (!found) {
                lines.add(KEY + " = false");
                changed = true;
            }
            if (changed) {
                Files.write(file, lines, StandardCharsets.UTF_8);
                IsekaiMahou.LOGGER.info("Isekai Mahou: perfil de servidor do Xaero's World Map com o teleporte pelo mapa desligado ({})", file);
            }
        } catch (IOException | RuntimeException e) {
            IsekaiMahou.LOGGER.warn("Não consegui ajustar o perfil de servidor do Xaero's World Map", e);
        }
    }

    private XaeroServerProfile() {}
}
