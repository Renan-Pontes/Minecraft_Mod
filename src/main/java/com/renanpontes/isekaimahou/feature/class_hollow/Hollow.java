package com.renanpontes.isekaimahou.feature.class_hollow;

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
 * Constantes e estado da classe HOLLOW (v1.3; homenagem a Bleach, com nomes e visuais próprios). Tudo vive nos
 * contadores/flags do {@link MagicData} (salvos e sincronizados — a aba e o HUD leem a cópia do cliente):
 * <ul>
 *   <li>Evolução: {@link #RANK} 0..4 = Hollow, Gillian, Adjuchas, Vasto Lorde, Arrancar (+1 na convenção {@code ritual_rank});</li>
 *   <li>Almas: {@link #SOULS} (as que ainda tem, gastas na Regeneração) e {@link #SOULS_TOTAL} (devoradas na vida toda,
 *   pedidas pela evolução);</li>
 *   <li>Fome de almas: {@link #LAST_FEED} = segundo do mundo em que devorou pela última vez (só escreve ao devorar);</li>
 *   <li>Resurrección: {@link #RELEASE} (1 Pantera, 2 Murciélago, 3 Serpiente; 0 = ainda não escolhida);</li>
 *   <li>Origem (por que virou Hollow): {@link #ORIGIN} (1 derrotado pelo Vazio Interior, 2 aceitou, 3 comando), o rank
 *   de Shinigami que tinha ({@link #SHINIGAMI_RANK}) e o dia ({@link #ORIGIN_DAY}).</li>
 * </ul>
 */
public final class Hollow {
    public static final String MODULE = "class_hollow";
    /** Cor da classe (osso), do Reiatsu (rubro) e do negro do Vazio. */
    public static final int RGB = 0xE6DFCF;
    public static final int REIATSU_RGB = 0xD0142C;
    public static final int DARK_RGB = 0x1A0608;
    public static final int BONE_RGB = 0xF2EEE6;

    public static final String RANK = "ritual_rank.hollowfied";
    public static final String GLOBAL_RANK = "ritual_rank";
    public static final String SOULS = "hollow.souls";
    public static final String SOULS_TOTAL = "hollow.souls_total";
    public static final String LAST_FEED = "hollow.last_feed";
    public static final String RELEASE = "hollow.release";
    public static final String ORIGIN = "hollow.origin";
    public static final String ORIGIN_DAY = "hollow.origin_day";
    public static final String SHINIGAMI_RANK = "hollow.shinigami_rank";
    public static final String DEVOURED_PLAYERS = "hollow.devoured_players";
    public static final String DEVOURED_HOLLOWS = "hollow.devoured_hollows";
    public static final String CEROS = "hollow.ceros";
    /** Flags da origem (leitura fácil por outros módulos e comandos). */
    public static final String FLAG_DEFEATED = "hollow.origin.defeated";
    public static final String FLAG_ACCEPTED = "hollow.origin.accepted";
    /** O jogador desligou o Hierro passivo (ligado por padrão). */
    public static final String FLAG_HIERRO_OFF = "hollow.hierro_off";

    public static final int ORIGIN_DEFEATED = 1;
    public static final int ORIGIN_ACCEPTED = 2;
    public static final int ORIGIN_COMMAND = 3;

    public static final int MAX_RANK = 4;
    public static final int GILLIAN = 1, ADJUCHAS = 2, VASTO_LORDE = 3, ARRANCAR = 4;

    // ------------------------------------------------------------------ ids dos glifos de origem
    public static final String CERO = "hollowfied_cero";
    public static final String BALA = "hollowfied_bala";
    public static final String PESQUISA = "hollowfied_pesquisa";
    public static final String REGEN = "hollowfied_regen";
    public static final String HIERRO = "hollowfied_hierro";
    public static final String SONIDO = "hollowfied_sonido";
    public static final String GARGANTA = "hollowfied_garganta";
    public static final String GRAN_REY_CERO = "hollowfied_gran_rey_cero";
    public static final String DESCORRER = "hollowfied_descorrer";
    public static final String RESURRECCION = "hollowfied_resurreccion";

    /** Prefixos das traduções. */
    static final String MSG_KEY = "message.isekai_mahou.hollow.";
    public static final String RANK_KEY = "hollow.isekai_mahou.rank.";
    public static final String REQ_KEY = "hollow.isekai_mahou.req.";

    public static boolean isHollow(@Nullable Player p) {
        return p != null && MagicApi.get(p).path() == MagicPath.HOLLOW;
    }

    public static int rank(MagicData d) {
        return Math.max(0, Math.min(MAX_RANK, d.counter(RANK)));
    }

    public static int souls(MagicData d) {
        return Math.max(0, d.counter(SOULS));
    }

    public static int soulsTotal(MagicData d) {
        return Math.max(0, d.counter(SOULS_TOTAL));
    }

    @Nullable
    public static Release release(MagicData d) {
        return Release.byIndex(d.counter(RELEASE) - 1);
    }

    /** Cor do Cero pelo rank: rubro (Hollow/Gillian), carmim (Adjuchas), negro-rubro (Vasto Lorde), a do Arrancar. */
    public static int ceroRgb(MagicData d) {
        Release r = release(d);
        return switch (rank(d)) {
            case 0, 1 -> 0xE01030;
            case 2 -> 0xB0102A;
            case 3 -> 0x6A0010;
            default -> r != null ? r.ceroRgb : 0x2EE07A;
        };
    }

    /** Bônus de poder das técnicas Hollow por rank (+6% por rank: +24% no Arrancar). */
    public static float rankPower(MagicData d) {
        return 1.0F + 0.06F * rank(d);
    }

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

    /**
     * A receita tem alguma TÉCNICA Hollow (forma de origem Hollow)? v1.4: o aumento e o gatilho da classe não contam —
     * eles não fazem a magia inteira virar técnica Hollow (sem o bônus do rank na magia toda).
     */
    public static boolean hasHollowGlyph(@Nullable SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g instanceof com.renanpontes.isekaimahou.glyph.FormGlyph && g.origin() == MagicPath.HOLLOW) return true;
        }
        return false;
    }

    public static boolean hasGlyph(@Nullable SpellRecipe recipe, String path) {
        if (recipe == null) return false;
        ResourceLocation id = IsekaiMahou.id(path);
        for (ResourceLocation g : recipe.glyphs()) if (g.equals(id)) return true;
        return false;
    }

    /** Segundo do mundo (para a fome de almas; cabe num int por ~3 anos de jogo). */
    public static int nowSeconds(ServerPlayer sp) {
        return (int) Math.min(Integer.MAX_VALUE, sp.serverLevel().getGameTime() / 20L);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(MSG_KEY + key, args);
    }

    public static MutableComponent rankTitle(int rank) {
        return Component.translatable(RANK_KEY + Math.max(0, Math.min(MAX_RANK, rank)));
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "0";
        };
    }

    // =====================================================================================
    //  Resurrección: os 3 arquétipos (escolhidos UMA vez, ao virar Arrancar)
    // =====================================================================================
    /** A forma liberada do Arrancar. */
    public enum Release {
        /** Pantera: fera de caça — velocidade, garras que rasgam, saltos. */
        PANTERA("pantera", 0x3FA9F5, 0x2EA0FF),
        /** Murciélago: asas negras, voo, a Lança do Relâmpago. */
        MURCIELAGO("murcielago", 0x2EE07A, 0x2EE07A),
        /** Serpiente: escamas, veneno, regeneração e cauda que alcança longe. */
        SERPIENTE("serpiente", 0x9A6AD8, 0xB060FF);

        public final String id;
        public final int rgb;
        public final int ceroRgb;

        Release(String id, int rgb, int ceroRgb) {
            this.id = id;
            this.rgb = rgb;
            this.ceroRgb = ceroRgb;
        }

        public int counterValue() {
            return ordinal() + 1;
        }

        public MutableComponent displayName() {
            return Component.translatable("hollow.isekai_mahou.release." + id).withColor(rgb);
        }

        /** O comando de liberação ("Desgarra, Pantera!"...). */
        public MutableComponent command() {
            return Component.translatable("hollow.isekai_mahou.release." + id + ".command");
        }

        public MutableComponent description() {
            return Component.translatable("hollow.isekai_mahou.release." + id + ".desc");
        }

        @Nullable
        public static Release byIndex(int i) {
            Release[] v = values();
            return i >= 0 && i < v.length ? v[i] : null;
        }
    }

    private Hollow() {}
}
