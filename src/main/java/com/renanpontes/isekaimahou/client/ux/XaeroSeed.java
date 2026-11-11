package com.renanpontes.isekaimahou.client.ux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Pré-semeia a config do Xaero's World Map com o teleporte pelo mapa DESLIGADO ({@code map_teleport_allowed = false}),
 * só quando o arquivo do perfil ainda não existe (instalação nova): quem já mexeu na config do Xaero não é tocado.
 * O bloqueio de verdade é no servidor ({@code core/ux/MapTeleportGuard}: /tp de jogador fora do criativo é cancelado);
 * isto só tira o botão "Teleportar" do mapa para ninguém clicar e levar a mensagem. Roda na construção do mod (antes
 * do Xaero ler a config na primeira tela).
 */
final class XaeroSeed {
    static void run() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("xaeroworldmap")) return;
        Path file = FMLPaths.CONFIGDIR.get().resolve("xaero").resolve("world-map").resolve("profiles").resolve("default.cfg");
        if (Files.exists(file)) return;
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, List.of(
                    "############",
                    "## Client Config Profile \"default\"",
                    "##",
                    "",
                    "profile_name = Default",
                    "map_teleport_allowed = false"), StandardCharsets.UTF_8);
            IsekaiMahou.LOGGER.info("Isekai Mahou: config do Xaero's World Map criada com o teleporte pelo mapa desligado ({})", file);
        } catch (IOException | RuntimeException e) {
            IsekaiMahou.LOGGER.warn("Não consegui pré-semear a config do Xaero's World Map em {}", file, e);
        }
    }

    private XaeroSeed() {}
}
