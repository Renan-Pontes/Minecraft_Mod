package com.renanpontes.isekaimahou.feature.familiars;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarShaping.Feat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As 10 formas base do Familiar. Cada uma "combina" com um jeito de jogar (classes, raças, elemento e feitos — pesos
 * usados por {@link FamiliarShaping}) e dá UMA ajuda pequena e temática ({@link Help}); nenhuma luta pelo jogador.
 * <p>
 * <b>O id é salvo</b> (dados do jogador) e dá nome aos arquivos ({@code geo/entity/fam_<id>.geo.json},
 * {@code textures/entity/fam_<id>_<estágio>.png}): nunca renomeie; formas novas só no FIM.
 * Textos: {@code familiars.isekai_mahou.form.<id>.<1|2|3>} (nome por estágio) e {@code ...form.<id>.desc}.
 */
public enum FamiliarForm {
    EMBER_FOX("ember_fox", 0xFF8A3C, false, Help.FETCH, Element.FIRE, null,
            EnumSet.of(MagicPath.MAGE, MagicPath.WARLOCK, MagicPath.GUNSLINGER, MagicPath.SMITH),
            Set.of("demon", "dragonborn", "beastfolk"),
            Map.of(Feat.KILLS, 0.6F, Feat.EXPLORE, 0.4F)),
    TIDE_OTTER("tide_otter", 0x4FB6E8, false, Help.SOOTHE, Element.WATER, null,
            EnumSet.of(MagicPath.WITCH, MagicPath.DRUID),
            Set.of("slime", "beastfolk"),
            Map.of(Feat.FISH, 0.4F, Feat.SWIM, 0.35F, Feat.BREW, 0.25F)),
    STONE_TORTOISE("stone_tortoise", 0x9A8F6A, false, Help.ORE, Element.EARTH, null,
            EnumSet.of(MagicPath.SMITH, MagicPath.STEELBALL, MagicPath.KNIGHT),
            Set.of("dwarf", "half_orc"),
            Map.of(Feat.MINING, 0.65F, Feat.TANK, 0.35F)),
    GALE_OWL("gale_owl", 0xBFE8D8, true, Help.WATCH, Element.WIND, null,
            EnumSet.of(MagicPath.RANGER, MagicPath.QUINCY, MagicPath.STEELBALL),
            Set.of("elf", "angel"),
            Map.of(Feat.FLIGHT, 0.5F, Feat.EXPLORE, 0.5F)),
    DAWN_HARE("dawn_hare", 0xFFE9A8, false, Help.MEND, Element.LIGHT, null,
            EnumSet.of(MagicPath.KNIGHT, MagicPath.ENCHANTER, MagicPath.QUINCY),
            Set.of("angel", "human"),
            Map.of(Feat.MEDITATE, 0.4F, Feat.TRADES, 0.3F, Feat.BREED, 0.3F)),
    SHADE_CAT("shade_cat", 0x7A5CC8, false, Help.AMBUSH, Element.SHADOW, null,
            EnumSet.of(MagicPath.ROGUE, MagicPath.WITCH, MagicPath.SHINIGAMI, MagicPath.HOLLOW),
            Set.of("phantom", "arachnid"),
            Map.of(Feat.SNEAK, 0.6F, Feat.KILLS, 0.4F)),
    RUNE_WISP("rune_wisp", 0xB98CFF, true, Help.MANA, Element.ARCANE, null,
            EnumSet.of(MagicPath.MAGE, MagicPath.ENCHANTER, MagicPath.GAMBLER),
            Set.of("human", "elf"),
            Map.of(Feat.STUDY, 0.35F, Feat.GLYPHS, 0.35F, Feat.ENCHANT, 0.15F, Feat.MEDITATE, 0.15F)),
    VERDANT_SPRITE("verdant_sprite", 0x7BD86A, true, Help.FORAGE, Element.EARTH, Element.WATER,
            EnumSet.of(MagicPath.DRUID, MagicPath.RANGER),
            Set.of("elf", "beastfolk", "slime"),
            Map.of(Feat.HARVEST, 0.6F, Feat.BREED, 0.4F)),
    BONE_RAVEN("bone_raven", 0xD8D2C0, true, Help.DEATH_GUIDE, Element.SHADOW, null,
            EnumSet.of(MagicPath.NECROMANCER, MagicPath.SHINIGAMI, MagicPath.WARLOCK, MagicPath.HOLLOW),
            Set.of("phantom", "demon"),
            Map.of(Feat.DEATHS, 0.45F, Feat.UNDEAD, 0.55F)),
    FORTUNE_DRAKE("fortune_drake", 0xF2C84B, true, Help.TREASURE, Element.FIRE, Element.LIGHT,
            EnumSet.of(MagicPath.GAMBLER, MagicPath.ROGUE, MagicPath.GUNSLINGER),
            Set.of("dragonborn", "dwarf"),
            Map.of(Feat.BOSSES, 0.5F, Feat.CHESTS, 0.3F, Feat.TRADES, 0.2F));

    /** O que o familiar faz por você (uma coisa só, pequena). Texto: {@code familiars.isekai_mahou.help.<id>}. */
    public enum Help {
        /** Traz para você os itens caídos por perto. */
        FETCH,
        /** Apaga o fogo, devolve fôlego debaixo d'água e cura bem de leve fora de combate. */
        SOOTHE,
        /** Aponta o minério mais próximo. */
        ORE,
        /** Avisa de longe quem está caçando você. */
        WATCH,
        /** Cura leve fora de combate. */
        MEND,
        /** Avisa de emboscadas de perto (creepers, invisíveis, pelas costas). */
        AMBUSH,
        /** Devolve um pouco do recurso (mana/vigor/foco) fora de combate. */
        MANA,
        /** Mata a fome aos poucos. */
        FORAGE,
        /** Guia até o lugar da sua última morte e avisa de mortos-vivos. */
        DEATH_GUIDE,
        /** Aponta baús de tesouro ainda fechados. */
        TREASURE;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);

        public MutableComponent description() {
            return Component.translatable("familiars.isekai_mahou.help." + id);
        }
    }

    public static final FamiliarForm[] ALL = values();
    public static final int MAX_STAGE = 3;

    public final String id;
    public final int rgb;
    /** Voa/flutua ao lado do dono (os outros andam atrás dele). */
    public final boolean flying;
    public final Help help;
    public final Element element;
    /** Segundo elemento (conta metade), ou null. */
    @Nullable
    public final Element element2;
    public final Set<MagicPath> classes;
    /** Ids de raça (texto: o módulo character é lido só por id). */
    public final Set<String> races;
    /** Feitos que puxam para esta forma (pesos somam 1). */
    public final Map<Feat, Float> feats;

    FamiliarForm(String id, int rgb, boolean flying, Help help, Element element, @Nullable Element element2,
                 Set<MagicPath> classes, Set<String> races, Map<Feat, Float> feats) {
        this.id = id;
        this.rgb = rgb;
        this.flying = flying;
        this.help = help;
        this.element = element;
        this.element2 = element2;
        this.classes = classes;
        this.races = races;
        this.feats = feats;
    }

    /** Nome da forma no estágio (1..3). */
    public MutableComponent displayName(int stage) {
        int s = Math.max(1, Math.min(MAX_STAGE, stage));
        return Component.translatable("familiars.isekai_mahou.form." + id + "." + s);
    }

    public MutableComponent description() {
        return Component.translatable("familiars.isekai_mahou.form." + id + ".desc");
    }

    /** Linhas (de 9 espaços) da bolsa do familiar: cresce no último estágio; a Tartaruga carrega mais. */
    public int stashRows(int stage) {
        int rows = stage >= MAX_STAGE ? 2 : 1;
        return this == STONE_TORTOISE ? rows + 1 : rows;
    }

    @Nullable
    public static FamiliarForm byId(String id) {
        for (FamiliarForm f : ALL) if (f.id.equals(id)) return f;
        return null;
    }
}
