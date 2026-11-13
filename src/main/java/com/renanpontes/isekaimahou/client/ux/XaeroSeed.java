package com.renanpontes.isekaimahou.client.ux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Config do Xaero's World Map com o teleporte pelo mapa DESLIGADO ({@code map_teleport_allowed = false}).
 * <p>
 * v1.0 ("LEMBRE DE TIRAR O TP DO XAEROSMAP"): antes isto só criava o arquivo em instalação nova — quem já tinha o perfil
 * do Xaero (com o teleporte ligado, que é o padrão dele) continuava com o botão. Agora a opção é FORÇADA para desligada
 * em todos os perfis do mapa, toda vez que o jogo abre e de novo quando ele fecha (o Xaero regrava o arquivo com o que
 * estava na memória; assim a próxima abertura sempre lê "desligado"). O resto da config do jogador não é tocado.
 * <p>
 * O bloqueio de verdade é no servidor ({@code core/ux/MapTeleportGuard}: comando de teleporte de jogador é cancelado,
 * menos para o admin logado); isto só tira o botão "Teleportar" do mapa. Roda na construção do mod.
 */
final class XaeroSeed {
    private static final String KEY = "map_teleport_allowed";

    static void run() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("xaeroworldmap")) return;
        enforce(true);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> enforce(false), "isekai-mahou-xaero-seed"));
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Não consegui registrar o gancho de saída da config do Xaero", e);
        }
    }

    /** Força a opção em todos os perfis; {@code create} = cria o perfil padrão se ainda não existe. */
    private static void enforce(boolean create) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("xaero").resolve("world-map").resolve("profiles");
        try {
            List<Path> files = new ArrayList<>();
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.cfg")) {
                    for (Path p : stream) files.add(p);
                }
            }
            if (files.isEmpty()) {
                if (!create) return;
                Files.createDirectories(dir);
                Path file = dir.resolve("default.cfg");
                Files.write(file, List.of(
                        "############",
                        "## Client Config Profile \"default\"",
                        "##",
                        "",
                        "profile_name = Default",
                        KEY + " = false"), StandardCharsets.UTF_8);
                if (create) IsekaiMahou.LOGGER.info("Isekai Mahou: config do Xaero's World Map criada com o teleporte pelo mapa desligado ({})", file);
                return;
            }
            for (Path file : files) {
                if (forceOff(file) && create) {
                    IsekaiMahou.LOGGER.info("Isekai Mahou: teleporte pelo mapa do Xaero desligado em {}", file);
                }
            }
        } catch (IOException | RuntimeException e) {
            // No gancho de saída o logger pode já ter fechado: nunca deixa a exceção escapar.
            if (create) IsekaiMahou.LOGGER.warn("Não consegui ajustar a config do Xaero's World Map em {}", dir, e);
        }
    }

    /** Troca (ou acrescenta) a linha da opção; devolve se o arquivo mudou. */
    private static boolean forceOff(Path file) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        boolean found = false, changed = false;
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
        if (changed) Files.write(file, lines, StandardCharsets.UTF_8);
        return changed;
    }

    private XaeroSeed() {}
}
