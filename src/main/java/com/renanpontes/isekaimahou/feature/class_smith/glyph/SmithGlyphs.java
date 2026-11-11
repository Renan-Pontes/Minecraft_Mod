package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;

/**
 * Os glifos de ORIGEM do Ferreiro ({@code origin(MagicPath.SMITH)}): usáveis em magia de qualquer elemento, ensinados
 * pelas Provas da Forja e, para outras classes, só por Lição. Todos montáveis no Grimório com o resto do sistema
 * (estágios, ligações, aumentos, multi-elemento).
 * <ul>
 *   <li>Formas: Martelada (onda no chão), Chuva de Fagulhas (leque de fagulhas — várias cópias), Forja Estelar (G).</li>
 *   <li>Efeitos de combate: Metal Fundido.</li>
 *   <li>Efeitos de FORJA (Alongar, Alargar, Afiar Ponta, Serrilhar, Fosquear, Gravar Runa, Reforjar): em você mesmo
 *   (forma Si Mesmo) mexem na Lâmina Forjada da mão; num inimigo viram um golpe com a mesma ideia.</li>
 * </ul>
 * Herdados do {@code legends_a} (agora de origem do Ferreiro): Campo de Lâminas, Arma Conjurada e Portão do Arsenal.
 */
public final class SmithGlyphs {
    public static final String HAMMER_SLAM = "smith_hammer_slam";
    public static final String SPARK_SHOWER = "smith_spark_shower";
    public static final String STARFORGE = "smith_starforge";
    public static final String MOLTEN = "smith_molten";
    public static final String LENGTHEN = "forge_lengthen";
    public static final String WIDEN = "forge_widen";
    public static final String SHARPEN = "forge_sharpen";
    public static final String SERRATE = "forge_serrate";
    public static final String MATTE = "forge_matte";
    public static final String RUNE = "forge_rune";
    public static final String REFORGE = "forge_reforge";
    // pacote de técnicas de anime (arqueiro de lâminas e ferreiros lendários)
    public static final String TRACE_ON = "smith_trace_on";
    public static final String TWIN_FALCHIONS = "smith_twin_falchions";
    public static final String RHO_AIAS = "smith_rho_aias";
    public static final String EPHEMERAL_SWORD = "smith_ephemeral_sword";
    public static final String CALADBOLG = "smith_caladbolg";
    public static final String BLADE_WORLD = "smith_blade_world";

    /** Todos os glifos do módulo + os três herdados das lâminas (para o contador de técnicas do Ferreiro). */
    public static final List<String> ALL = List.of(HAMMER_SLAM, SPARK_SHOWER, STARFORGE, MOLTEN, LENGTHEN, WIDEN, SHARPEN,
            SERRATE, MATTE, RUNE, REFORGE, "blade_field", "conjured_weapon", "arsenal_gate",
            TRACE_ON, TWIN_FALCHIONS, RHO_AIAS, EPHEMERAL_SWORD, CALADBOLG, BLADE_WORLD);
    private static final Set<ResourceLocation> IDS = Set.copyOf(ALL.stream().map(IsekaiMahou::id).toList());

    public static void register() {
        GlyphRegistry.register(new HammerSlamForm());
        GlyphRegistry.register(new SparkShowerForm());
        GlyphRegistry.register(new StarforgeForm());
        GlyphRegistry.register(new MoltenEffect());
        GlyphRegistry.register(new ForgeEffects.Lengthen());
        GlyphRegistry.register(new ForgeEffects.Widen());
        GlyphRegistry.register(new ForgeEffects.Sharpen());
        GlyphRegistry.register(new ForgeEffects.Serrate());
        GlyphRegistry.register(new ForgeEffects.Matte());
        GlyphRegistry.register(new ForgeEffects.Rune());
        GlyphRegistry.register(new ForgeEffects.Reforge());
        GlyphRegistry.register(new TraceOnEffect());
        GlyphRegistry.register(new TwinFalchionsForm());
        GlyphRegistry.register(new RhoAiasEffect());
        GlyphRegistry.register(new EphemeralSwordEffect());
        GlyphRegistry.register(new CaladbolgForm());
        GlyphRegistry.register(new BladeWorldForm());
    }

    /** A receita usa alguma técnica do Ferreiro? (tarefa "conjurar técnicas" das Provas). */
    public static boolean usesSmithGlyph(SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) if (IDS.contains(id)) return true;
        return false;
    }

    private SmithGlyphs() {}
}
