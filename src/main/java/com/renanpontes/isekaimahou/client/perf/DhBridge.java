package com.renanpontes.isekaimahou.client.perf;

import java.lang.reflect.Method;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.fml.ModList;

/**
 * SÓ CLIENTE. Ponte opcional com o Distant Horizons (terreno distante em LOD), por reflexão — o mod não é dependência
 * de compilação. Usa a API pública dele ({@code DhApi.Delayed.configs.graphics().chunkRenderDistance()}). Valores postos
 * pela API valem enquanto o jogo está aberto (não mexem no arquivo do DH), por isso {@link PerfClient} reaplica ao
 * entrar no jogo quando há um Modo do PC escolhido; o modo "NONE" limpa o valor e o DH volta ao que o jogador ajustou.
 */
public final class DhBridge {
    private static Boolean present;

    public static boolean present() {
        if (present == null) {
            try {
                present = ModList.get() != null && ModList.get().isLoaded("distanthorizons");
            } catch (RuntimeException e) {
                present = false;
            }
        }
        return present;
    }

    /** A API do DH já iniciou? (só depois do carregamento do jogo) */
    public static boolean ready() {
        return present() && configValue() != null;
    }

    /** Alcance atual do terreno distante (chunks), ou -1 se não der para ler. */
    public static int chunkRenderDistance() {
        Object v = configValue();
        if (v == null) return -1;
        try {
            Object r = valueMethod("getValue").invoke(v);
            return r instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Põe o alcance do preset. Devolve false se o DH não está pronto (chamar de novo depois). */
    public static boolean apply(PcPreset preset) {
        Object v = configValue();
        if (v == null) return false;
        try {
            Method set = valueMethod("setValue", Object.class);
            int max = intOr(v, "getMaxValue", Integer.MAX_VALUE);
            int min = intOr(v, "getMinValue", 0);
            set.invoke(v, Math.max(min, Math.min(max, preset.dhChunks)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Distant Horizons recusou o alcance do preset", e);
            return false;
        }
    }

    /** Devolve o controle ao jogador (o valor do arquivo de config do DH volta a valer). */
    public static void clear() {
        Object v = configValue();
        if (v == null) return;
        try {
            valueMethod("clearValue").invoke(v);
        } catch (ReflectiveOperationException | RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Não foi possível limpar o alcance do Distant Horizons", e);
        }
    }

    private static int intOr(Object v, String method, int def) {
        try {
            Object r = valueMethod(method).invoke(v);
            return r instanceof Integer i ? i : def;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return def;
        }
    }

    private static final String API = "com.seibel.distanthorizons.api.interfaces.config.";

    /** {@code DhApi.Delayed.configs.graphics().chunkRenderDistance()} ou null (métodos pelas INTERFACES públicas). */
    private static Object configValue() {
        if (!present()) return null;
        try {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) return null;
            Object graphics = Class.forName(API + "IDhApiConfig").getMethod("graphics").invoke(configs);
            if (graphics == null) return null;
            return Class.forName(API + "client.IDhApiGraphicsConfig").getMethod("chunkRenderDistance").invoke(graphics);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Método da interface {@code IDhApiConfigValue} (a classe concreta pode não ser pública). */
    private static Method valueMethod(String name, Class<?>... params) throws ReflectiveOperationException {
        return Class.forName(API + "IDhApiConfigValue").getMethod(name, params);
    }

    private DhBridge() {}
}
