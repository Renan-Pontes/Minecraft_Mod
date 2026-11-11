package com.renanpontes.isekaimahou.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Classes (origens). Definem o kit inicial, o ritual/sistema próprio, os glifos de origem EXCLUSIVOS e a AFINIDADE
 * (quão rápido evoluem em cada elemento). Nada impede o multiclasse (Lições, pergaminhos, itens): a classe dá
 * DIRECIONAMENTO, não prisão.
 * <p>
 * v1.0: 15 classes em 3 categorias ({@link Category}: Mágicas, Físicas, Destreza). A categoria decide o NOME/COR do
 * recurso ({@link Resource}: Mana, Vigor, Foco — é o mesmo valor salvo de mana) e as técnicas iniciais. As 10 classes da
 * v0.9 (que nunca foi lançada) saíram e deram lugar às 10 novas (ordinais 6–15).
 * <b>A ordem das constantes é salva</b> (o pacote de escolha usa o ordinal; o save usa o id): novas classes só no FIM.
 */
public enum MagicPath {
    NONE("none", ChatFormatting.GRAY, 0xAAAAAA, Category.MAGIC, null, "mage_staff", null, Set.of(), Set.of()),
    // ---------------- Mágicas (v0.1 — ordem e ids fixos) ----------------
    MAGE("mage", ChatFormatting.BLUE, 0x5555FF, Category.MAGIC, Element.FIRE, "mage_staff", null,
            EnumSet.of(Element.FIRE, Element.WATER, Element.EARTH, Element.WIND), EnumSet.of(Element.SHADOW)),
    WITCH("witch", ChatFormatting.LIGHT_PURPLE, 0xFF55FF, Category.MAGIC, Element.WATER, "witch_wand", null,
            EnumSet.of(Element.WATER, Element.SHADOW), EnumSet.of(Element.LIGHT)),
    WARLOCK("warlock", ChatFormatting.DARK_RED, 0xAA0000, Category.MAGIC, Element.SHADOW, "pact_blade", null,
            EnumSet.of(Element.SHADOW, Element.FIRE), EnumSet.of(Element.LIGHT)),
    DRUID("druid", ChatFormatting.DARK_GREEN, 0x00AA00, Category.MAGIC, Element.EARTH, "druid_staff", null,
            EnumSet.of(Element.EARTH, Element.WATER, Element.LIGHT), EnumSet.of(Element.SHADOW)),
    NECROMANCER("necromancer", ChatFormatting.DARK_AQUA, 0x00AAAA, Category.MAGIC, Element.SHADOW, "reaper_scythe", null,
            EnumSet.of(Element.SHADOW, Element.EARTH), EnumSet.of(Element.LIGHT)),
    // ---------------- Físicas (v1.0) — recurso Vigor ----------------
    /** Ferreiro — Forja Arcana: monta e customiza armas como glifos (módulo class_smith). */
    SMITH("smith", ChatFormatting.GOLD, 0xE8742C, Category.PHYSICAL, Element.FIRE, "smith_hammer", "smith_hammer",
            EnumSet.of(Element.FIRE, Element.EARTH), EnumSet.of(Element.WATER)),
    /** Ladino — Roubo: itens, mana, buffs e até magias; crítico pelas costas (módulo class_rogue). */
    ROGUE("rogue", ChatFormatting.DARK_PURPLE, 0x8E5BD6, Category.PHYSICAL, Element.SHADOW, "rogue_dagger", "rogue_dagger",
            EnumSet.of(Element.SHADOW, Element.WIND), EnumSet.of(Element.LIGHT)),
    /** Cavaleiro Rúnico — Lâminas Rúnicas e espadas lendárias (módulo class_knight). */
    KNIGHT("knight", ChatFormatting.BLUE, 0x7FA8FF, Category.PHYSICAL, Element.LIGHT, "runic_blade", "runic_blade",
            EnumSet.of(Element.LIGHT, Element.EARTH), EnumSet.of(Element.SHADOW)),
    /**
     * Encantador — Encantamentos em si e nos aliados, com Anti-Magia e Restrição Celestial obrigatórios (módulo
     * class_enchanter). O Arcano é neutro para todos; a afinidade real é Luz e Vento.
     */
    ENCHANTER("enchanter", ChatFormatting.AQUA, 0x5FE3C0, Category.PHYSICAL, Element.LIGHT, "enchanter_gauntlets",
            "enchanter_gauntlets", EnumSet.of(Element.LIGHT, Element.WIND), EnumSet.of(Element.SHADOW)),
    /** Shinigami — Caminho da Zanpakutō, Mundo Interior, Shikai/Bankai, Kidō (módulo class_shinigami). */
    SHINIGAMI("shinigami", ChatFormatting.RED, 0xD9483B, Category.PHYSICAL, Element.SHADOW, "zanpakuto", "zanpakuto",
            EnumSet.of(Element.SHADOW, Element.WIND), EnumSet.of(Element.LIGHT)),
    // ---------------- Destreza (v1.0) — recurso Foco ----------------
    /** Quincy — arco espiritual, absorver e devolver, Letras e Auswählen (módulo class_quincy). */
    QUINCY("quincy", ChatFormatting.AQUA, 0xA8DFFF, Category.DEXTERITY, Element.LIGHT, "quincy_cross", "quincy_cross",
            EnumSet.of(Element.LIGHT, Element.WIND), EnumSet.of(Element.SHADOW)),
    /** Pistoleiro — armas mágicas feitas de mana; a magia sai pelas balas (módulo class_gunslinger). */
    GUNSLINGER("gunslinger", ChatFormatting.GOLD, 0xD9A441, Category.DEXTERITY, Element.FIRE, "mana_revolver", "mana_revolver",
            EnumSet.of(Element.FIRE, Element.WIND), EnumSet.of(Element.WATER)),
    /** Ranger — companheiros que evoluem e flechas montadas como glifos (módulo class_ranger). */
    RANGER("ranger", ChatFormatting.GREEN, 0x6BCB5A, Category.DEXTERITY, Element.WIND, "ranger_bow", "ranger_bow",
            EnumSet.of(Element.WIND, Element.EARTH), EnumSet.of(Element.FIRE)),
    /** Apostador — cada magia rola a sorte; jackpot, cartas, roleta e o domínio Cassino (módulo class_gambler). */
    GAMBLER("gambler", ChatFormatting.LIGHT_PURPLE, 0xE04A8C, Category.DEXTERITY, Element.LIGHT, "gambler_deck", "gambler_deck",
            EnumSet.of(Element.LIGHT, Element.SHADOW), Set.of()),
    /** Steel Ball — Rotação, lições do mestre e Partes do Cadáver Santo (módulo class_steelball). */
    STEELBALL("steelball", ChatFormatting.GRAY, 0xB4C0CC, Category.DEXTERITY, Element.EARTH, "steel_ball", "steel_ball",
            EnumSet.of(Element.EARTH, Element.WIND), EnumSet.of(Element.FIRE));

    /** As 15 classes escolhíveis, agrupadas por categoria (Mágicas, Físicas, Destreza), na ordem da tela. */
    public static final MagicPath[] PLAYABLE = {
            MAGE, WITCH, WARLOCK, DRUID, NECROMANCER,
            SMITH, ROGUE, KNIGHT, ENCHANTER, SHINIGAMI,
            QUINCY, GUNSLINGER, RANGER, GAMBLER, STEELBALL};

    private static final Map<Category, List<MagicPath>> BY_CATEGORY = new EnumMap<>(Category.class);

    static {
        for (Category c : Category.values()) {
            List<MagicPath> list = new ArrayList<>();
            for (MagicPath p : PLAYABLE) if (p.category == c) list.add(p);
            BY_CATEGORY.put(c, Collections.unmodifiableList(list));
        }
    }

    public final String id;
    /** Cor de chat mais próxima (compatibilidade). Para desenhar use {@link #argb()}/{@link #rgb()}. */
    public final ChatFormatting color;
    private final int rgb;
    private final Category category;
    @Nullable
    private final Element primary;
    private final String weaponPrefix;
    @Nullable
    private final String kitWeapon;
    private final Set<Element> affinity;
    private final Set<Element> hard;

    MagicPath(String id, ChatFormatting color, int rgb, Category category, @Nullable Element primary, String weaponPrefix,
              @Nullable String kitWeapon, Set<Element> affinity, Set<Element> hard) {
        this.id = id;
        this.color = color;
        this.rgb = rgb & 0xFFFFFF;
        this.category = category;
        this.primary = primary;
        this.weaponPrefix = weaponPrefix;
        this.kitWeapon = kitWeapon;
        this.affinity = affinity;
        this.hard = hard;
    }

    /** Afinidade da classe com um elemento. Fusões: afinidade se os 2 pais forem afinidade; difícil se algum for difícil. */
    public Affinity affinity(Element e) {
        if (e == Element.ARCANE || this == NONE) return Affinity.NEUTRAL;
        if (e.isFusion()) {
            Affinity a = affinity(e.parentA);
            Affinity b = affinity(e.parentB);
            if (a == Affinity.HARD || b == Affinity.HARD) return Affinity.HARD;
            if (a == Affinity.AFFINITY && b == Affinity.AFFINITY) return Affinity.AFFINITY;
            return Affinity.NEUTRAL;
        }
        if (affinity.contains(e)) return Affinity.AFFINITY;
        if (hard.contains(e)) return Affinity.HARD;
        return Affinity.NEUTRAL;
    }

    /** Elementos base de afinidade, com o principal primeiro. */
    public List<Element> affinityElements() {
        List<Element> out = new ArrayList<>();
        if (primary != null) out.add(primary);
        for (Element e : Element.BASE) if (e != primary && affinity.contains(e)) out.add(e);
        return out;
    }

    /** Elementos base difíceis para a classe. */
    public List<Element> hardElements() {
        List<Element> out = new ArrayList<>();
        for (Element e : Element.BASE) if (hard.contains(e)) out.add(e);
        return out;
    }

    /**
     * Afinidade PRINCIPAL: o 1º elemento despertado ao escolher a classe (o módulo Caminhos usa).
     * {@code null} só para {@link #NONE}.
     */
    @Nullable
    public Element primaryAffinity() { return primary; }

    public Category category() { return category; }

    /** Recurso da classe (Mana, Vigor ou Foco) — mesmo valor salvo, nome/cor/regras por categoria. */
    public Resource resource() { return category.resource; }

    /**
     * Prefixo da arma da classe ({@code <prefixo>_t1..t4} quando o item tem tiers), ex.: {@code mage_staff}. Nas classes
     * Físicas/Destreza é o id da arma do kit (o módulo da classe pode registrar {@code <prefixo>_t2..t4} para a Guilda
     * e as Provações entregarem — quem usa SEMPRE tolera o item não existir). {@link #NONE} usa o do Mago.
     */
    public String weaponPrefix() { return weaponPrefix; }

    /**
     * Id da arma do kit inicial (Físicas/Destreza; ver a tabela em {@code docs/v10/API_spells.md}); null para as
     * Mágicas, cujo kit vem dos rituais. O MÓDULO da classe ({@code class_<id>}) registra o item com esse id; ele pode não
     * existir (módulo ausente): resolva com {@code BuiltInRegistries.ITEM.getOptional}.
     */
    @Nullable
    public ResourceLocation kitWeapon() { return kitWeapon == null ? null : IsekaiMahou.id(kitWeapon); }

    /** Módulo que implementa o sistema próprio da classe ({@code class_<id>} nas 10 novas; ritual_* nas Mágicas). */
    public String moduleId() {
        return switch (category) {
            case MAGIC -> this == NONE ? "" : "ritual_" + (this == NECROMANCER ? "necro" : id);
            default -> "class_" + id;
        };
    }

    /** Textura do ícone da classe ({@code textures/gui/class/<id>.png}; pode não existir — tenha reserva). */
    public ResourceLocation iconTexture() { return IsekaiMahou.id("textures/gui/class/" + id + ".png"); }

    public boolean isPlayable() { return this != NONE; }

    public MutableComponent displayName() {
        return Component.translatable("path.isekai_mahou." + id).withColor(rgb);
    }

    /** "Caminho do Estudo", "Lâmina e Chama"... */
    public MutableComponent styleName() {
        return Component.translatable("path.isekai_mahou." + id + ".style");
    }

    public MutableComponent description() {
        return Component.translatable("path.isekai_mahou." + id + ".desc");
    }

    /** Nome do sistema próprio da classe ("Forja Arcana", "Roubo", "Encantamentos"...). */
    public MutableComponent systemName() {
        return Component.translatable("path.isekai_mahou." + id + ".system");
    }

    /** Explicação curta do sistema próprio (o que a classe faz que nenhuma outra faz). */
    public MutableComponent systemDescription() {
        return Component.translatable("path.isekai_mahou." + id + ".system.desc");
    }

    /** Bônus de classe (passivos fortes ligados ao estilo; o módulo da classe os aplica). */
    public MutableComponent bonusDescription() {
        return Component.translatable("path.isekai_mahou." + id + ".bonus");
    }

    public int rgb() { return rgb; }

    public int argb() { return 0xFF000000 | rgb; }

    public static MagicPath byId(String id) {
        for (MagicPath p : values()) if (p.id.equals(id)) return p;
        return NONE;
    }

    public static MagicPath byIndex(int i) {
        MagicPath[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }

    /** As 5 classes de uma categoria, na ordem da tela de escolha. */
    public static List<MagicPath> byCategory(Category c) {
        return BY_CATEGORY.getOrDefault(c, List.of());
    }

    // =====================================================================================
    //  Categoria e recurso (v0.9; v1.0: Físicas e Destreza)
    // =====================================================================================

    /** Categoria da classe. Decide o recurso e as técnicas iniciais. */
    public enum Category {
        MAGIC("magic", "magic", Resource.MANA),
        /** Físicas (ex-"Guerreiras" da v0.9): Vigor. */
        PHYSICAL("physical", "martial", Resource.VIGOR),
        /** Destreza (ex-"À Distância" da v0.9): Foco. */
        DEXTERITY("dexterity", "ranged", Resource.FOCUS);

        public final String id;
        /**
         * Id da v0.9 ({@code magic}/{@code martial}/{@code ranged}) — só para chaves de tradução/arquivos antigos de
         * módulos que ainda não migraram (ex.: graus dos trajes). Código novo usa {@link #id}.
         */
        public final String legacyId;
        public final Resource resource;

        Category(String id, String legacyId, Resource resource) {
            this.id = id;
            this.legacyId = legacyId;
            this.resource = resource;
        }

        public MutableComponent displayName() {
            return Component.translatable("category.isekai_mahou." + id);
        }

        /** Dica curta da categoria (tela de classe). */
        public MutableComponent hint() {
            return Component.translatable("screen.isekai_mahou.classes.category_hint." + id);
        }

        /** Textura do ícone da aba ({@code textures/gui/category/<id>.png}; pode não existir). */
        public ResourceLocation iconTexture() {
            return IsekaiMahou.id("textures/gui/category/" + id + ".png");
        }

        /** Pelo id (aceita também os ids da v0.9: martial → PHYSICAL, ranged → DEXTERITY). */
        public static Category byId(String id) {
            for (Category c : values()) if (c.id.equals(id) || c.legacyId.equals(id)) return c;
            return MAGIC;
        }
    }

    /**
     * Recurso: nome e cor da "mana" de cada categoria. O valor salvo é o mesmo ({@code MagicData.mana()}); só as
     * regras de regeneração mudam (ver {@code core.classes.ClassResources}).
     */
    public enum Resource {
        MANA("mana", 0xFF7A5CFF, 1.0F),
        VIGOR("vigor", 0xFFE0563B, 0.35F),
        FOCUS("focus", 0xFF3BC9A8, 0.5F);

        public final String id;
        private final int argb;
        /** Multiplicador da regeneração passiva. */
        public final float passiveRegen;

        Resource(String id, int argb, float passiveRegen) {
            this.id = id;
            this.argb = argb;
            this.passiveRegen = passiveRegen;
        }

        /** Cor ARGB da barra/texto. */
        public int argb() { return argb; }

        /** Cor RGB (sem alfa). */
        public int rgb() { return argb & 0xFFFFFF; }

        /** "Mana" / "Vigor" / "Foco", já na cor do recurso. */
        public MutableComponent displayName() {
            return Component.translatable("resource.isekai_mahou." + id).withColor(argb & 0xFFFFFF);
        }

        /** Explicação curta de como o recurso volta. */
        public MutableComponent description() {
            return Component.translatable("resource.isekai_mahou." + id + ".desc");
        }

        /** Textura do ícone ({@code textures/gui/resource/<id>.png}; pode não existir). */
        public ResourceLocation iconTexture() {
            return IsekaiMahou.id("textures/gui/resource/" + id + ".png");
        }
    }

    /** Afinidade: multiplica o XP do elemento e reduz/aumenta os feitos pedidos nos caminhos. */
    public enum Affinity {
        AFFINITY(1.5F, 0.5F),
        NEUTRAL(1.0F, 1.0F),
        HARD(0.5F, 2.0F);

        public final float xpMult;
        /** Multiplicador da quantidade pedida nos feitos dos caminhos. */
        public final float featMult;

        Affinity(float xpMult, float featMult) {
            this.xpMult = xpMult;
            this.featMult = featMult;
        }

        public MutableComponent displayName() {
            return Component.translatable("affinity.isekai_mahou." + name().toLowerCase());
        }
    }
}
