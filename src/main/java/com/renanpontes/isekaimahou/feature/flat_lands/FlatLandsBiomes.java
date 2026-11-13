package com.renanpontes.isekaimahou.feature.flat_lands;

import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Os 3 biomas planos do Overworld e a regra de onde eles entram no gerador de biomas do vanilla.
 * <p>Sem TerraBlender: o mixin {@code OverworldBiomeBuilderFlatMixin} chama {@link #replaceSurface} em cada
 * {@code addSurfaceBiome} do {@code OverworldBiomeBuilder}. Só fatias de ruído JÁ planas são tocadas
 * (erosão alta, terra firme longe da costa, "weirdness" baixa = vales e encostas suaves), e só a faixa do MEIO da
 * umidade de cada fatia vira o bioma novo — o resto continua planície/savana. Por isso eles são raros e sempre
 * cercados pelos biomas originais. Vale só para chunks novos.
 */
public final class FlatLandsBiomes {
    public static final ResourceKey<Biome> SAKURA_FIELDS = key("sakura_fields");
    public static final ResourceKey<Biome> GOLDEN_STEPPE = key("flat_lands_golden_steppe");
    public static final ResourceKey<Biome> KINGDOM_MEADOWS = key("flat_lands_kingdom_meadows");

    /** Tag com os 3 biomas (as cidades/capitais incluem esta tag pelo pacote interno do módulo). */
    public static final TagKey<Biome> TAG = TagKey.create(Registries.BIOME, IsekaiMahou.id("flat_lands"));

    // Limites (já quantizados, ×10000) das fatias do OverworldBiomeBuilder do 1.21.1.
    private static final long TEMP_COOL_MIN = -4500, TEMP_COOL_MAX = -1500;     // temperatures[1]
    private static final long TEMP_MILD_MIN = -1500, TEMP_MILD_MAX = 2000;      // temperatures[2]
    private static final long TEMP_WARM_MIN = 2000, TEMP_WARM_MAX = 5500;       // temperatures[3]
    private static final long EROSION_FLAT_MIN = -2225, EROSION_FLAT_MAX = 5500; // erosions[3]..erosions[5]
    private static final long INLAND_MIN = -1100;                               // nearInland em diante (sem costa)
    private static final long WEIRD_LIMIT = 2700;                               // vales + fatias baixas (±0,2667 quantizado = ±2666)
    /** Parte da faixa de umidade (do meio) que vira bioma novo. */
    private static final float BAND_FROM = 0.33F, BAND_TO = 0.68F;

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, IsekaiMahou.id(path));
    }

    /** Qual bioma plano substitui esta entrada (ou null = deixa o vanilla como está). */
    static ResourceKey<Biome> pick(Climate.Parameter temp, Climate.Parameter continent, Climate.Parameter erosion,
                                   Climate.Parameter weird, ResourceKey<Biome> original) {
        if (erosion.min() < EROSION_FLAT_MIN || erosion.max() > EROSION_FLAT_MAX) return null;
        if (continent.min() < INLAND_MIN) return null;
        if (weird.min() < -WEIRD_LIMIT || weird.max() > WEIRD_LIMIT) return null;
        boolean plains = original == Biomes.PLAINS || original == Biomes.SUNFLOWER_PLAINS;
        if (plains && temp.min() == TEMP_MILD_MIN && temp.max() == TEMP_MILD_MAX) return SAKURA_FIELDS;
        if (original == Biomes.PLAINS && temp.min() == TEMP_COOL_MIN && temp.max() == TEMP_COOL_MAX) return KINGDOM_MEADOWS;
        if (original == Biomes.SAVANNA && temp.min() == TEMP_WARM_MIN && temp.max() == TEMP_WARM_MAX) return GOLDEN_STEPPE;
        return null;
    }

    /**
     * Chamado pelo mixin no início de {@code addSurfaceBiome}. Se a entrada for elegível, emite três pedaços (umidade
     * baixa = original, meio = bioma plano, alta = original) com as mesmas duas profundidades do vanilla e devolve true
     * (o mixin cancela a chamada original). Senão devolve false e nada muda.
     */
    public static boolean replaceSurface(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out,
                                         Climate.Parameter temp, Climate.Parameter humidity, Climate.Parameter continent,
                                         Climate.Parameter erosion, Climate.Parameter weird, float offset,
                                         ResourceKey<Biome> original) {
        ResourceKey<Biome> flat = pick(temp, continent, erosion, weird, original);
        if (flat == null || vanillaBootstrap()) return false;
        long span = humidity.max() - humidity.min();
        long a = humidity.min() + (long) (span * BAND_FROM);
        long b = humidity.min() + (long) (span * BAND_TO);
        if (a <= humidity.min() || b >= humidity.max() || a >= b) return false;
        emit(out, temp, new Climate.Parameter(humidity.min(), a), continent, erosion, weird, offset, original);
        emit(out, temp, new Climate.Parameter(a, b), continent, erosion, weird, offset, flat);
        emit(out, temp, new Climate.Parameter(b, humidity.max()), continent, erosion, weird, offset, original);
        return true;
    }

    /**
     * O vanilla também monta a lista de biomas no registro "só vanilla" ({@code VanillaRegistries.createLookup}, na
     * validação de comandos e no datagen), onde os nossos biomas não existem — lá a troca quebraria o boot
     * ("Unreferenced key"). Só é chamado nas poucas entradas elegíveis, então olhar a pilha é barato.
     */
    private static boolean vanillaBootstrap() {
        return StackWalker.getInstance().walk(s -> s.anyMatch(f -> f.getClassName().endsWith(".VanillaRegistries")));
    }

    /** Igual ao addSurfaceBiome do vanilla: uma entrada na superfície (profundidade 0) e outra logo abaixo (1). */
    private static void emit(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out, Climate.Parameter temp,
                             Climate.Parameter humidity, Climate.Parameter continent, Climate.Parameter erosion,
                             Climate.Parameter weird, float offset, ResourceKey<Biome> biome) {
        out.accept(Pair.of(Climate.parameters(temp, humidity, continent, erosion, Climate.Parameter.point(0.0F), weird, offset), biome));
        out.accept(Pair.of(Climate.parameters(temp, humidity, continent, erosion, Climate.Parameter.point(1.0F), weird, offset), biome));
    }

    private FlatLandsBiomes() {}
}
