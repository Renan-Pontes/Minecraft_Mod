package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Convivência com o Jade (sem depender dele) — v1.1.
 * <p>
 * Antes o Olho Analítico "desviava" da caixa do Jade e acabava descendo para perto do meio da tela, e as criaturas
 * tinham DUAS barras de vida (os corações do Jade em cima + o nosso painel). Agora, uma única vez por instalação,
 * desligamos as dicas de CRIATURAS do Jade ({@code general.displayEntities = false} — as de blocos continuam) e o nosso
 * painel fica fixo no topo central, logo abaixo das barras de chefe. Um arquivo-marcador
 * ({@code config/isekai_mahou-jade-entities.done}) garante que isso só acontece uma vez: quem quiser os corações do
 * Jade de volta religa nas opções do Jade e nós não mexemos de novo.
 * <p>
 * Faz pelo próprio Jade (reflexão: {@code IWailaConfig.get().getGeneral().setDisplayEntities(false)} e
 * {@code Jade.CONFIG.save()}); se a API mudar, edita {@code config/jade/jade.json} direto.
 */
final class JadeCompat {
    private static final String MARKER = "isekai_mahou-jade-entities.done";
    private static Boolean loaded;
    private static boolean tried;

    private JadeCompat() {}

    /** O Jade está instalado? */
    static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("jade");
        return loaded;
    }

    /** Ao entrar num mundo (a config do Jade já foi lida): desliga as dicas de criaturas do Jade, uma vez só. */
    static void disableEntityTooltipsOnce() {
        if (tried || !loaded()) return;
        tried = true;
        Path marker = FMLPaths.CONFIGDIR.get().resolve(MARKER);
        if (Files.exists(marker)) return;
        boolean ok = viaApi() || viaFile();
        if (!ok) return;
        try {
            Files.writeString(marker, "O Isekai Mahou desligou as dicas de CRIATURAS do Jade (o Olho Analitico mostra a vida do alvo).\n"
                    + "Para ter os coracoes do Jade de volta, religue \"Mostrar entidades\" nas opcoes do Jade. Este arquivo impede\n"
                    + "que o mod mexa nisso de novo.\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            IsekaiMahou.LOGGER.debug("Não foi possível gravar {}", MARKER, e);
        }
        IsekaiMahou.LOGGER.info("Jade: dicas de criaturas desligadas (o Olho Analítico do Isekai Mahou mostra o alvo). Religue nas opções do Jade se quiser.");
    }

    private static boolean viaApi() {
        try {
            Class<?> api = Class.forName("snownee.jade.api.config.IWailaConfig");
            Object cfg = api.getMethod("get").invoke(null);
            Object general = api.getMethod("getGeneral").invoke(cfg);
            // pela interface pública da API (a classe de implementação pode não ser acessível)
            Method set = Class.forName("snownee.jade.api.config.IWailaConfig$IConfigGeneral").getMethod("setDisplayEntities", boolean.class);
            set.invoke(general, false);
            Class<?> jade = Class.forName("snownee.jade.Jade");
            Object json = jade.getField("CONFIG").get(null);
            json.getClass().getMethod("save").invoke(json);
            return true;
        } catch (Throwable t) {
            IsekaiMahou.LOGGER.debug("Jade: API de config indisponível, editando o arquivo", t);
            return false;
        }
    }

    private static boolean viaFile() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("jade").resolve("jade.json");
        try {
            JsonObject root;
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement el = JsonParser.parseReader(r);
                    root = el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
                }
            } else {
                Files.createDirectories(file.getParent());
                root = new JsonObject();
            }
            JsonObject general = root.has("general") && root.get("general").isJsonObject() ? root.getAsJsonObject("general") : new JsonObject();
            general.addProperty("displayEntities", false);
            root.add("general", general);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
            // Pede para o Jade reler o arquivo (se a API existir)
            try {
                Class<?> jade = Class.forName("snownee.jade.Jade");
                Object json = jade.getField("CONFIG").get(null);
                json.getClass().getMethod("invalidate").invoke(json);
            } catch (Throwable ignored) {
                // vale a partir do próximo início do jogo
            }
            return true;
        } catch (Exception e) {
            IsekaiMahou.LOGGER.debug("Jade: não foi possível editar {}", file, e);
            return false;
        }
    }

    static void clear() {
        // nada a esquecer entre mundos (a troca é feita uma vez só)
    }
}
