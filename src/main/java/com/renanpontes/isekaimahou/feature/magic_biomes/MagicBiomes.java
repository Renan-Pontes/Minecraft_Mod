package com.renanpontes.isekaimahou.feature.magic_biomes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Os biomas mágicos e as regras de ONDE cada um entra nos geradores do vanilla (sem TerraBlender; só chunks NOVOS).
 * <ul>
 *   <li><b>Overworld, superfície</b> — mesmo método do flat_lands: o mixin {@code OverworldBiomeBuilderFlatMixin} chama
 *       {@link #replaceSurface} em cada {@code addSurfaceBiome}; uma FAIXA de umidade de certas entradas do vanilla vira
 *       o bioma mágico e o resto continua como era. Planície e savana não são tocadas (são das cidades).</li>
 *   <li><b>Overworld, subterrâneo</b> — {@link #addUnderground} acrescenta a Caverna de Cristal Mágico ao lado das
 *       cavernas de estalactite/exuberantes do vanilla (debaixo de terras muito secas).</li>
 *   <li><b>Nether</b> — {@link #extendNether} acrescenta um 6º ponto na lista de clima do Nether
 *       (mixin {@code MultiNoiseBiomeSourceParameterListMixin}).</li>
 *   <li><b>End</b> — {@link #astralRegion} diz quais regiões das ilhas de fora viram Jardins Astrais
 *       (mixin {@code TheEndBiomeSourceMixin}).</li>
 * </ul>
 */
public final class MagicBiomes {
    public static final ResourceKey<Biome> CRYSTAL_FOREST = key("crystal_forest");
    public static final ResourceKey<Biome> HAUNTED_SWAMP = key("haunted_swamp");
    public static final ResourceKey<Biome> TWILIGHT_GROVE = key("twilight_grove");
    public static final ResourceKey<Biome> CRYSTAL_CAVERN = key("crystal_cavern");
    public static final ResourceKey<Biome> EMBER_WASTES = key("ember_crystal_wastes");
    public static final ResourceKey<Biome> ASTRAL_GARDENS = key("astral_gardens");

    /** Todos, na ordem do Diário. */
    public static final List<ResourceKey<Biome>> ALL = List.of(CRYSTAL_FOREST, HAUNTED_SWAMP, TWILIGHT_GROVE,
            CRYSTAL_CAVERN, EMBER_WASTES, ASTRAL_GARDENS);

    /** Tag {@code #isekai_mahou:magic_biomes} com os 6. */
    public static final TagKey<Biome> TAG = TagKey.create(Registries.BIOME, IsekaiMahou.id("magic_biomes"));

    // Limites já quantizados (×10000), como o Climate guarda. Com folga de ±100 para não depender de arredondamento.
    private static final long TEMP_COOL_MIN = -4600, TEMP_COOL_MAX = -1400;   // temperatures[1] do vanilla (−0,45..−0,15)
    /** Floresta de Cristal: pega o lado úmido da Floresta fria e o lado seco da Taiga (faixa contínua). */
    private static final long CRYSTAL_FROM = 200, CRYSTAL_TO = 1400;
    /** Pântano Assombrado: a parte mais úmida dos pântanos. */
    private static final long HAUNTED_FROM = 2000, HAUNTED_TO = 5500;
    /** Bosque Crepuscular: o miolo das Florestas Escuras. */
    private static final long TWILIGHT_FROM = 5000, TWILIGHT_TO = 6300;
    /** Menor pedaço de umidade que vale a pena separar (evita lascas de bioma). */
    private static final long MIN_SLICE = 150;

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, IsekaiMahou.id(path));
    }

    // ---------------------------------------------------------------------------------------------
    //  Overworld: superfície
    // ---------------------------------------------------------------------------------------------

    /** Faixa de umidade [de, até] que vira {@code biome}. */
    private record Band(ResourceKey<Biome> biome, long from, long to) {}

    private static Band pick(Climate.Parameter temp, ResourceKey<Biome> original) {
        if (original == Biomes.SWAMP) return new Band(HAUNTED_SWAMP, HAUNTED_FROM, HAUNTED_TO);
        if (original == Biomes.DARK_FOREST) return new Band(TWILIGHT_GROVE, TWILIGHT_FROM, TWILIGHT_TO);
        if ((original == Biomes.FOREST || original == Biomes.TAIGA)
                && temp.min() >= TEMP_COOL_MIN && temp.max() <= TEMP_COOL_MAX) {
            return new Band(CRYSTAL_FOREST, CRYSTAL_FROM, CRYSTAL_TO);
        }
        return null;
    }

    /**
     * Chamado pelo mixin no início de {@code addSurfaceBiome} (depois do flat_lands). Se a entrada for elegível, emite
     * até três pedaços (umidade abaixo da faixa = original, faixa = bioma mágico, acima = original) com as mesmas duas
     * profundidades do vanilla e devolve true (o mixin cancela a chamada original). Senão devolve false e nada muda.
     */
    public static boolean replaceSurface(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out,
                                         Climate.Parameter temp, Climate.Parameter humidity, Climate.Parameter continent,
                                         Climate.Parameter erosion, Climate.Parameter weird, float offset,
                                         ResourceKey<Biome> original) {
        Band band = pick(temp, original);
        if (band == null) return false;
        long from = Math.max(humidity.min(), band.from());
        long to = Math.min(humidity.max(), band.to());
        if (to - from < MIN_SLICE || vanillaBootstrap()) return false;
        // sobras muito finas ficam com o bioma mágico (não vale criar uma lasca do original)
        if (from - humidity.min() < MIN_SLICE) from = humidity.min();
        if (humidity.max() - to < MIN_SLICE) to = humidity.max();
        if (from > humidity.min()) emit(out, temp, new Climate.Parameter(humidity.min(), from), continent, erosion, weird, offset, original);
        emit(out, temp, new Climate.Parameter(from, to), continent, erosion, weird, offset, band.biome());
        if (to < humidity.max()) emit(out, temp, new Climate.Parameter(to, humidity.max()), continent, erosion, weird, offset, original);
        return true;
    }

    /** Igual ao addSurfaceBiome do vanilla: uma entrada na superfície (profundidade 0) e outra logo abaixo (1). */
    private static void emit(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out, Climate.Parameter temp,
                             Climate.Parameter humidity, Climate.Parameter continent, Climate.Parameter erosion,
                             Climate.Parameter weird, float offset, ResourceKey<Biome> biome) {
        out.accept(Pair.of(Climate.parameters(temp, humidity, continent, erosion, Climate.Parameter.point(0.0F), weird, offset), biome));
        out.accept(Pair.of(Climate.parameters(temp, humidity, continent, erosion, Climate.Parameter.point(1.0F), weird, offset), biome));
    }

    // ---------------------------------------------------------------------------------------------
    //  Overworld: subterrâneo
    // ---------------------------------------------------------------------------------------------

    /**
     * Chamado pelo mixin no fim de {@code addUndergroundBiomes}. A Caverna de Cristal fica debaixo de terras MUITO secas
     * (as Cavernas Exuberantes ficam nas muito úmidas), nas mesmas profundidades das cavernas do vanilla (0,2–0,9).
     */
    public static void addUnderground(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
        if (vanillaBootstrap()) return;
        Climate.Parameter full = Climate.Parameter.span(-1.0F, 1.0F);
        out.accept(Pair.of(Climate.parameters(full, Climate.Parameter.span(-1.0F, -0.95F), full, full,
                Climate.Parameter.span(0.2F, 0.9F), full, 0.0F), CRYSTAL_CAVERN));
    }

    // ---------------------------------------------------------------------------------------------
    //  Nether
    // ---------------------------------------------------------------------------------------------

    /**
     * Devolve a lista de clima do Nether com o Ermo de Cristal Ígneo acrescentado (quente e seco: no canto entre a
     * Floresta Carmesim e o Vale das Almas). Outras listas (Overworld) e o registro "só vanilla" voltam como vieram.
     */
    public static Climate.ParameterList<Holder<Biome>> extendNether(MultiNoiseBiomeSourceParameterList.Preset preset,
                                                                    Climate.ParameterList<Holder<Biome>> original,
                                                                    HolderGetter<Biome> biomes) {
        if (preset != MultiNoiseBiomeSourceParameterList.Preset.NETHER || vanillaBootstrap()) return original;
        Optional<Holder.Reference<Biome>> ember = biomes.get(EMBER_WASTES);
        if (ember.isEmpty()) return original;
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> list = new ArrayList<>(original.values());
        list.add(Pair.of(Climate.parameters(0.35F, -0.35F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), ember.get()));
        return new Climate.ParameterList<>(list);
    }

    // ---------------------------------------------------------------------------------------------
    //  End
    // ---------------------------------------------------------------------------------------------

    /** Tamanho (em quartos de bloco) da grade do ruído das regiões astrais: 96 quartos = 384 blocos. */
    private static final double ASTRAL_CELL = 96.0;
    /** Quanto maior, mais raro (0..1). 0,70 dá perto de um quinto das terras altas/médias das ilhas de fora. */
    private static final double ASTRAL_THRESHOLD = 0.70;

    /**
     * A coluna (em coordenadas de quarto de bloco, como o gerador de biomas usa) fica numa região de Jardins Astrais?
     * Ruído de valor suave e fixo (o gerador de biomas do End não conhece a semente): manchas de algumas centenas de
     * blocos. O mixin só pergunta para terras altas/médias das ilhas de fora.
     */
    public static boolean astralRegion(int quartX, int quartZ) {
        double x = quartX / ASTRAL_CELL, z = quartZ / ASTRAL_CELL;
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double a = Mth.lerp(fx, corner(x0, z0), corner(x0 + 1, z0));
        double b = Mth.lerp(fx, corner(x0, z0 + 1), corner(x0 + 1, z0 + 1));
        return Mth.lerp(fz, a, b) > ASTRAL_THRESHOLD;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Valor fixo 0..1 de um canto da grade. */
    private static double corner(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L + 0x1A57EA1L;
        h = (h ^ (h >>> 31)) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * O vanilla também monta as listas de biomas no registro "só vanilla" ({@code VanillaRegistries} /
     * {@code RegistrySetBuilder}: validação de comandos e datagen), onde os nossos biomas não existem — lá qualquer
     * referência a eles quebra o boot ("Unreferenced key"). Só é chamado nas poucas entradas elegíveis.
     */
    private static boolean vanillaBootstrap() {
        return StackWalker.getInstance().walk(s -> s.anyMatch(f -> {
            String c = f.getClassName();
            return c.endsWith(".VanillaRegistries") || c.contains(".RegistrySetBuilder");
        }));
    }

    private MagicBiomes() {}
}
