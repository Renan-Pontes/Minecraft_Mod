package com.renanpontes.isekaimahou.feature.class_quincy;

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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Constantes e estado da classe Quincy (tudo guardado nos contadores/flags do {@link MagicData}, que já são salvos e
 * sincronizados com o cliente — a aba do menu lê direto da cópia do cliente).
 * <ul>
 *   <li>Rank do Auswählen: {@link #RANK} (0..5) e a convenção global {@link #GLOBAL_RANK} (+1 por rank);</li>
 *   <li>Reishi armazenado (absorvido de projéteis, magias e do ambiente): {@link #REISHI}, até {@link #reishiCap};</li>
 *   <li>feitos que contam para os ranks: abates com técnicas Quincy, absorções, Auswählen, usos da Letra;</li>
 *   <li>a Letra (Schrift) escolhida no rank III: {@link #LETTER} (índice de {@link Letter} + 1, 0 = nenhuma).</li>
 * </ul>
 */
public final class Quincy {
    public static final String MODULE = "class_quincy";
    /** Cor da classe (azul-gelo) e do Reishi. */
    public static final int RGB = 0xA8DFFF;
    public static final int REISHI_RGB = 0x8FD0FF;
    public static final int DEEP_RGB = 0x3576D0;

    public static final String RANK = "ritual_rank.quincy";
    public static final String GLOBAL_RANK = "ritual_rank";
    public static final String REISHI = "quincy.reishi";
    public static final String ABSORBED = "quincy.absorbed";
    public static final String KILLS = "quincy.kills";
    public static final String AUSWAHLEN = "quincy.auswahlen";
    public static final String SCHRIFT_USES = "quincy.schrift_uses";
    public static final String LETTER = "quincy.letter";
    /** Uso diário de quem DOA o poder num Auswählen entre jogadores (1 por dia do mundo). */
    public static final String DAILY_GIVEN = "quincy_auswahlen_given";
    public static final int MAX_RANK = 5;

    /** Prefixos das traduções (mensagens, títulos de rank e Letras). */
    static final String MSG_KEY = "message.isekai_mahou.quincy.";
    static final String RANK_KEY = "quincy.isekai_mahou.rank.";
    static final String LETTER_KEY = "quincy.isekai_mahou.letter.";
    static final String REQ_KEY = "quincy.isekai_mahou.req.";
    static final String TIER_KEY = "quincy.isekai_mahou.outfit_tier.";

    /** Ids dos glifos de origem (prefixo da classe). */
    public static final String BOW = "quincy_spirit_bow";
    public static final String LIGHT_RAIN = "quincy_light_rain";
    public static final String SEELE_BLADE = "quincy_seele_blade";
    public static final String ABSORB = "quincy_absorb";
    public static final String HIRENKYAKU = "quincy_hirenkyaku";
    public static final String VOLLSTANDIG = "quincy_vollstandig";
    public static final String DRAIN = "quincy_drain_reishi";
    public static final String RELEASE = "quincy_release";
    public static final String BLUT = "quincy_blut";
    public static final String SCHRIFT = "quincy_schrift";
    public static final String SILVER_TUBE = "quincy_silver_tube";
    // técnicas avançadas (pacote de técnicas icônicas — ver QuincyTechniques)
    public static final String HEILIG_BOGEN = "quincy_heilig_bogen";
    public static final String SANKT_ZWINGER = "quincy_sankt_zwinger";
    public static final String ANTITHESIS = "quincy_antithesis";
    public static final String LETZT_STIL = "quincy_letzt_stil";
    public static final String ALMIGHTY = "quincy_almighty";
    /** Abates com técnicas Quincy segurando a Luva Sanrei (treino da Letzt Stil). */
    public static final String SANREI = "quincy.sanrei";
    /** Segundos restantes sem poder Quincy (depois da Letzt Stil). */
    public static final String POWERLESS = "quincy.powerless";
    public static final ResourceLocation SILVER_TUBE_ID = IsekaiMahou.id(SILVER_TUBE);

    /** Glifos ensinados ao escolher a classe (o resto vem dos ranks). */
    public static final String[] STARTER_GLYPHS = {BOW, HIRENKYAKU, DRAIN};

    /** As Letras (Schrift): poderes modestos, um por Quincy (homenagens com nomes próprios). */
    public enum Letter {
        EXPLODE("explode", 'E', 0xFF9A4A),
        IRON("iron", 'I', 0xB8C2D0),
        THUNDER("thunder", 'T', 0xFFE45A),
        WIND("wind", 'W', 0x8FF0C8),
        FEAR("fear", 'F', 0xB08AFF),
        // Letras novas (modestas): ordem salva = índice + 1 — novas só no FIM
        BURNT("burnt", 'H', 0xFF5A1E),
        JAIL("jail", 'J', 0xC8B070),
        XAXIS("xaxis", 'X', 0xE8F0FF),
        BALANCE("balance", 'B', 0x9AD0A8),
        DEATHDEALING("deathdealing", 'D', 0x7AC040);

        public final String id;
        public final char symbol;
        public final int rgb;

        Letter(String id, char symbol, int rgb) {
            this.id = id;
            this.symbol = symbol;
            this.rgb = rgb;
        }

        public MutableComponent displayName() {
            return Component.translatable(LETTER_KEY + id).withColor(rgb);
        }

        public MutableComponent description() {
            return Component.translatable(LETTER_KEY + id + ".desc");
        }

        public MutableComponent passive() {
            return Component.translatable(LETTER_KEY + id + ".passive");
        }

        @Nullable
        public static Letter byId(String id) {
            for (Letter l : values()) if (l.id.equalsIgnoreCase(id)) return l;
            return null;
        }

        @Nullable
        public static Letter byIndex(int i) {
            Letter[] v = values();
            return i >= 0 && i < v.length ? v[i] : null;
        }
    }

    public static boolean isQuincy(@Nullable Player p) {
        return p != null && MagicApi.get(p).path() == MagicPath.QUINCY;
    }

    public static int rank(MagicData d) {
        return Math.max(0, Math.min(MAX_RANK, d.counter(RANK)));
    }

    @Nullable
    public static Letter letter(MagicData d) {
        return Letter.byIndex(d.counter(LETTER) - 1);
    }

    /** Quanto Reishi cabe guardado: 10 + 2 por rank (20 no rank V); a Letra J (A Prisão) guarda +4. */
    public static int reishiCap(MagicData d) {
        return 10 + 2 * rank(d) + (letter(d) == Letter.JAIL ? 4 : 0);
    }

    public static int reishi(MagicData d) {
        return Math.max(0, d.counter(REISHI));
    }

    /** Soma Reishi armazenado (corta no limite). Devolve quanto entrou de fato. */
    public static int addReishi(ServerPlayer sp, int amount) {
        MagicData d = MagicApi.get(sp);
        int cur = reishi(d);
        int add = Math.max(0, Math.min(amount, reishiCap(d) - cur));
        if (add > 0) MagicApi.addCounter(sp, REISHI, add);
        return add;
    }

    /** Gasta TODO o Reishi armazenado e devolve quanto havia. */
    public static int takeAllReishi(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        int cur = reishi(d);
        if (cur != 0) MagicApi.addCounter(sp, REISHI, -d.counter(REISHI));
        return Math.max(0, cur);
    }

    /** Recupera uma FRAÇÃO do recurso máximo (Foco) — nunca número fixo (a mana da v1.0 é ~3× maior). */
    public static void gainFocus(ServerPlayer sp, float fraction) {
        if (fraction <= 0) return;
        ClassResources.gain(sp, MagicApi.get(sp).maxMana() * fraction);
    }

    /** Bônus de poder das técnicas Quincy por rank (+4% por rank: +20% no V). */
    public static float rankPower(MagicData d) {
        return 1.0F + 0.04F * rank(d);
    }

    /** A receita tem algum glifo de origem Quincy? */
    public static boolean hasQuincyGlyph(@Nullable SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.origin() == MagicPath.QUINCY) return true;
        }
        return false;
    }

    public static boolean hasGlyph(@Nullable SpellRecipe recipe, String path) {
        if (recipe == null) return false;
        ResourceLocation id = IsekaiMahou.id(path);
        for (ResourceLocation g : recipe.glyphs()) if (g.equals(id)) return true;
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

    /** "Reishi 7/16" para a barra de ação. */
    public static MutableComponent reishiLine(MagicData d) {
        return Component.translatable("message.isekai_mahou.quincy.reishi", reishi(d), reishiCap(d)).withColor(REISHI_RGB);
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
            case 5 -> "V";
            default -> "0";
        };
    }

    public static ChatFormatting chat() {
        return ChatFormatting.AQUA;
    }

    private Quincy() {}
}
