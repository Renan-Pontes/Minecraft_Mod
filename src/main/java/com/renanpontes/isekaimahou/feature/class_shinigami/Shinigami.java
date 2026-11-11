package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Constantes e estado da classe Shinigami (homenagem a Bleach, com nomes e visuais próprios). Todo o progresso vive nos
 * contadores/flags do {@link MagicData} (salvos e sincronizados com o cliente — a aba do menu e o HUD leem a cópia local).
 * <ul>
 *   <li>Rank do Caminho da Zanpakutō: {@link #RANK} (0..5) e a convenção global {@link #GLOBAL_RANK} (+1 por rank);</li>
 *   <li>Arquétipo da Shikai escolhido no Mundo Interior: {@link #ARCHETYPE} (índice de {@link Archetype} + 1; 0 = nenhum);</li>
 *   <li>feitos que contam para os ranks: abates, Kidō, meditações, cortes com a Shikai, liberações.</li>
 * </ul>
 */
public final class Shinigami {
    public static final String MODULE = "class_shinigami";
    /** Cor da classe (vermelho do Reiatsu) e tons auxiliares. */
    public static final int RGB = 0xD9483B;
    public static final int REIATSU_RGB = 0x8FE0FF;
    public static final int INK_RGB = 0x1A1622;

    public static final String RANK = "ritual_rank.shinigami";
    public static final String GLOBAL_RANK = "ritual_rank";
    public static final String ARCHETYPE = "shinigami.archetype";
    public static final String KILLS = "shinigami.kills";
    public static final String KIDO = "shinigami.kido";
    public static final String MEDITATIONS = "shinigami.meditations";
    public static final String SHIKAI_HITS = "shinigami.shikai_hits";
    public static final String RELEASES = "shinigami.releases";
    public static final int MAX_RANK = 5;

    /** Prefixos das traduções. */
    static final String MSG_KEY = "message.isekai_mahou.shinigami.";
    static final String RANK_KEY = "shinigami.isekai_mahou.rank.";
    static final String REQ_KEY = "shinigami.isekai_mahou.req.";
    static final String TIER_KEY = "shinigami.isekai_mahou.outfit_tier.";

    // ------------------------------------------------------------------ ids dos glifos de origem
    public static final String ZANJUTSU = "zanpakuto_zanjutsu";
    public static final String SHUNPO = "shinigami_shunpo";
    public static final String MOON_FANG = "zanpakuto_moon_fang";
    public static final String HADO_4 = "kido_hado_4";
    public static final String HADO_31 = "kido_hado_31";
    public static final String BAKUDO_61 = "kido_bakudo_61";
    public static final String BAKUDO_81 = "kido_bakudo_81";
    public static final String BANKAI = "zanpakuto_bankai";
    // técnicas avançadas (pacote de técnicas icônicas — ver ShinigamiTechniques)
    public static final String GETSUGA = "zanpakuto_getsuga";
    public static final String HADO_90 = "kido_hado_90";
    public static final String VISORED = "shinigami_visored";
    public static final String CERO = "shinigami_cero";
    public static final String VASTO_LORDE = "shinigami_vasto_lorde";
    public static final String MUGETSU = "zanpakuto_mugetsu";

    /** Feitos das técnicas avançadas. */
    public static final String GETSUGA_USES = "shinigami.getsuga";
    public static final String VISORED_USES = "shinigami.visored_uses";
    /** Flag: o Vazio Interior foi domado no Mundo Interior. */
    public static final String HOLLOW_TAMED = "shinigami.hollow_tamed";
    /** Segundos restantes sem poder espiritual (depois do Mugetsu). */
    public static final String POWERLESS = "shinigami.powerless";

    /** Glifos de Kidō (contam para o feito "Kidō"). */
    public static final String[] KIDO_GLYPHS = {HADO_4, HADO_31, BAKUDO_61, BAKUDO_81, HADO_90};
    /** Glifos ensinados ao escolher a classe (o resto vem dos ranks e do Mundo Interior). */
    public static final String[] STARTER_GLYPHS = {ZANJUTSU, SHUNPO, HADO_4};

    public static boolean isShinigami(@Nullable Player p) {
        return p != null && MagicApi.get(p).path() == MagicPath.SHINIGAMI;
    }

    public static int rank(MagicData d) {
        return Math.max(0, Math.min(MAX_RANK, d.counter(RANK)));
    }

    @Nullable
    public static Archetype archetype(MagicData d) {
        return Archetype.byIndex(d.counter(ARCHETYPE) - 1);
    }

    /** Shikai liberada (rank III ou mais). */
    public static boolean hasShikai(MagicData d) {
        return rank(d) >= 3 && archetype(d) != null;
    }

    /** Bankai aprendida (rank V). */
    public static boolean hasBankai(MagicData d) {
        return rank(d) >= 5 && archetype(d) != null;
    }

    /** Bônus de poder das técnicas Shinigami por rank (+4% por rank: +20% no V). */
    public static float rankPower(MagicData d) {
        return 1.0F + 0.04F * rank(d);
    }

    /** Recupera uma FRAÇÃO do Vigor máximo (nunca número fixo — a v1.0 tem ~3× mais recurso). */
    public static void gainVigor(ServerPlayer sp, float fraction) {
        if (fraction <= 0) return;
        ClassResources.gain(sp, MagicApi.get(sp).maxMana() * fraction);
    }

    /** Gasta uma FRAÇÃO do Vigor máximo; false (sem gastar) se não houver o bastante. Criativo: grátis. */
    public static boolean spendVigor(ServerPlayer sp, float fraction) {
        if (sp.getAbilities().instabuild) return true;
        MagicData d = MagicApi.get(sp);
        return d.consumeMana(d.maxMana() * fraction);
    }

    /** A receita tem algum glifo de origem Shinigami? */
    public static boolean hasShinigamiGlyph(@Nullable SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.origin() == MagicPath.SHINIGAMI) return true;
        }
        return false;
    }

    public static boolean hasGlyph(@Nullable SpellRecipe recipe, String path) {
        if (recipe == null) return false;
        ResourceLocation id = IsekaiMahou.id(path);
        for (ResourceLocation g : recipe.glyphs()) if (g.equals(id)) return true;
        return false;
    }

    public static boolean hasKido(@Nullable SpellRecipe recipe) {
        if (recipe == null) return false;
        for (String k : KIDO_GLYPHS) if (hasGlyph(recipe, k)) return true;
        return false;
    }

    /** Soma dos contadores {@code boss.*} (chefes derrotados por perto — convenção dos módulos de chefes). */
    public static int bossKills(MagicData d) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("boss.") && e.getValue() > 0) sum += e.getValue();
        }
        return sum;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(MSG_KEY + key, args);
    }

    public static MutableComponent rankTitle(int rank) {
        return Component.translatable(RANK_KEY + Math.max(0, Math.min(MAX_RANK, rank)));
    }

    /** Nome do espírito da espada do jogador (o do arquétipo, ou "sem nome" antes do rank II). */
    public static MutableComponent spiritName(MagicData d) {
        Archetype a = archetype(d);
        return a != null ? a.spiritName() : Component.translatable("shinigami.isekai_mahou.spirit.unnamed");
    }

    public static int spiritRgb(MagicData d) {
        Archetype a = archetype(d);
        return a != null ? a.rgb : 0xB8B8C8;
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "0";
        };
    }

    private Shinigami() {}
}
