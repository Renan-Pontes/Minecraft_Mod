package com.renanpontes.isekaimahou.feature.armory;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Rarity;

/**
 * As 15 armas do Arsenal (cada uma com 4 tiers: {@code <id>_t1..t4}): 5 focos das classes Mágicas (v0.7) e 10 armas
 * das classes Físicas/Destreza da v0.9. Os números dos tiers ficam aqui para a progressão ser fácil de balancear num
 * lugar só (índice 0 = tier I).
 * <p>
 * v1.0: as classes da v0.9 saíram e as 10 classes novas têm a arma no PRÓPRIO módulo ({@code class_<id>}, itens
 * {@code <MagicPath.weaponPrefix()>_t1..t4}). As 10 armas da v0.9 — inclusive Lâmina do Cavaleiro, Arco Longo e Adagas
 * Gêmeas, que só lembravam as classes de mesmo id — ficaram GENÉRICAS (sem classe, {@link MagicPath#NONE}, com a
 * categoria guardada em {@link #category()}): continuam no criativo, na Guilda e nos saves, e {@link #forPath} só as usa
 * como reserva da categoria quando o módulo de uma classe não registrou arma de tier.
 * <p>
 * Inflação da v1.0 (V10_DESIGN §0.1): armadura na mão ×1,5 e o mana máximo das tabelas (escala da v0.8) ×
 * {@link ResourceTraining#MANA_SCALE} em {@link #maxMana}.
 * <p>
 * A ORDEM das constantes não é salva em lugar nenhum (os itens usam o id), mas as 5 primeiras ficam como na v0.7.
 */
public enum WeaponKind {
    /** Cajado do Mago: foco puro em magia. */
    MAGE_STAFF("mage_staff", MagicPath.MAGE, Style.STAFF, 0x6FA8FF),
    /** Varinha da Bruxa: curta, uma mão. */
    WITCH_WAND("witch_wand", MagicPath.WITCH, Style.WAND, 0xE070FF),
    /** Lâmina do Pacto do Warlock: espada (corpo a corpo + magia). */
    PACT_BLADE("pact_blade", MagicPath.WARLOCK, Style.BLADE, 0xE0303A),
    /** Cajado de Galhos do Druida. */
    DRUID_STAFF("druid_staff", MagicPath.DRUID, Style.STAFF, 0x6BD66B),
    /** Foice do Necromante: corpo a corpo pesado, ceifa almas. */
    REAPER_SCYTHE("reaper_scythe", MagicPath.NECROMANCER, Style.SCYTHE, 0x4FE0D0),

    // ---------------- Guerreiras (v0.9) ----------------
    /** Espada Longa do Guerreiro: equilibrada, varre. */
    LONGSWORD("longsword", MagicPath.Category.PHYSICAL, Style.BLADE, 0xFF8A3A),
    /** Maça Sagrada do Paladino: pesada, abençoada contra mortos-vivos, dá armadura. */
    HOLY_MACE("holy_mace", MagicPath.Category.PHYSICAL, Style.MACE, 0xFFD966),
    /** Machado Grande do Bárbaro: o golpe mais forte e lento; quebra escudos. */
    GREATAXE("greataxe", MagicPath.Category.PHYSICAL, Style.AXE, 0xE0482E),
    /** Manoplas de Ki do Monge: socos rápidos, mais velocidade de movimento. */
    KI_GAUNTLETS("ki_gauntlets", MagicPath.Category.PHYSICAL, Style.FIST, 0x7FE8D8),
    /** Lâmina do Cavaleiro: espada de guarda, armadura e firmeza (lute com o Escudo do Cavaleiro). Genérica na v1.0. */
    KNIGHT_BLADE("knight_blade", MagicPath.Category.PHYSICAL, Style.BLADE, 0xC8D6F0),

    // ---------------- À Distância (v0.9) ----------------
    /** Arco Longo do Patrulheiro: puxada mais longa, flechas mais rápidas e fortes. Genérico na v1.0. */
    LONGBOW("longbow", MagicPath.Category.DEXTERITY, Style.BOW, 0x9BE070),
    /** Adagas Gêmeas do Ladino: golpes cheios acertam duas vezes. Genéricas na v1.0. */
    TWIN_DAGGERS("twin_daggers", MagicPath.Category.DEXTERITY, Style.DAGGER, 0xA070E8),
    /** Alaúde do Bardo: instrumento (segurar = tocar) e foco mágico. */
    LUTE("lute", MagicPath.Category.DEXTERITY, Style.LUTE, 0xFFC860),
    /** Besta Arcana do Artífice: carrega rápido, virotes escalam com o Poder Mágico. */
    ARCANE_CROSSBOW("arcane_crossbow", MagicPath.Category.DEXTERITY, Style.CROSSBOW, 0xB48CFF),
    /** Besta de Caçador: virotes pesados que deixam a presa lenta. */
    HUNTER_CROSSBOW("hunter_crossbow", MagicPath.Category.DEXTERITY, Style.CROSSBOW, 0xC89A5A);

    /** Formato da arma (decide o tipo de item, o modelo e o brilho no cliente). */
    public enum Style { STAFF, WAND, BLADE, SCYTHE, MACE, AXE, FIST, DAGGER, BOW, CROSSBOW, LUTE }

    public static final int MAX_TIER = 4;

    // ---------------- Atributos mágicos na mão principal, por tier ----------------
    /** Poder Mágico (+% sobre a base 1.0) dos focos das Mágicas (e do alaúde, foco do Bardo). */
    public static final double[] SPELL_POWER = {0.08, 0.16, 0.25, 0.35};
    /** Mana Máximo (soma) dos focos das Mágicas. */
    public static final double[] MAX_MANA = {20, 50, 90, 150};
    /** Regeneração de Mana (+% sobre a base 1.0) dos focos das Mágicas. */
    public static final double[] MANA_REGEN = {0.10, 0.20, 0.30, 0.40};
    /** Guerreiras e À Distância: as técnicas também escalam com o Poder Mágico, mas a arma é o forte delas. */
    public static final double[] MARTIAL_SPELL_POWER = {0.04, 0.08, 0.12, 0.16};
    /** Vigor/Foco máximo (o mesmo atributo de Mana Máximo, com outro nome por categoria). */
    public static final double[] MARTIAL_MAX_MANA = {15, 35, 60, 100};
    /** À Distância: Foco também volta um pouco mais rápido. */
    public static final double[] RANGED_REGEN = {0.05, 0.10, 0.15, 0.20};
    /** Alaúde (foco do Bardo). */
    public static final double[] LUTE_SPELL_POWER = {0.07, 0.14, 0.22, 0.30};
    public static final double[] LUTE_MAX_MANA = {20, 45, 80, 130};

    // ---------------- Corpo a corpo ----------------
    /** Modificador de dano da Lâmina do Pacto (o jogador já tem 1 de base: 6/7/8/9 no total). */
    public static final float[] BLADE_DAMAGE = {5.0F, 6.0F, 7.0F, 8.0F};
    /** Velocidade de ataque da lâmina (4 + valor = 1.6 ataques/s, como espada). */
    public static final float BLADE_SPEED = -2.4F;
    /** Modificador de dano da Foice (7 / 8.5 / 10 / 11.5 no total). */
    public static final float[] SCYTHE_DAMAGE = {6.0F, 7.5F, 9.0F, 10.5F};
    /** Foice é pesada: 1.2 ataques/s. */
    public static final float SCYTHE_SPEED = -2.8F;
    /** Espada Longa: 6.5 / 7.5 / 8.5 / 9.5 no total, 1.6 ataques/s. */
    public static final float[] LONGSWORD_DAMAGE = {5.5F, 6.5F, 7.5F, 8.5F};
    /** Maça Sagrada: 7 / 8 / 9 / 10, 1.2 ataques/s. */
    public static final float[] MACE_DAMAGE = {6.0F, 7.0F, 8.0F, 9.0F};
    public static final float MACE_SPEED = -2.8F;
    /** Machado Grande: 9 / 10.5 / 12 / 13.5, 1.0 ataque/s. */
    public static final float[] AXE_DAMAGE = {8.0F, 9.5F, 11.0F, 12.5F};
    public static final float AXE_SPEED = -3.0F;
    /**
     * Manoplas: 4.5 / 5.25 / 6 / 6.75, 2.0 ataques/s. Mais rápido que isso seria desperdiçado: a invencibilidade de
     * 10 ticks depois de cada golpe engoliria os socos extras.
     */
    public static final float[] FIST_DAMAGE = {3.5F, 4.25F, 5.0F, 5.75F};
    public static final float FIST_SPEED = -2.0F;
    /** Lâmina do Cavaleiro: 6 / 7 / 8 / 9, 1.6 ataques/s. */
    public static final float[] KNIGHT_DAMAGE = {5.0F, 6.0F, 7.0F, 8.0F};
    /** Adagas Gêmeas: 3.5 / 4.25 / 5 / 5.75 por golpe (+35% no golpe duplo), 2.0 ataques/s. */
    public static final float[] DAGGER_DAMAGE = {2.5F, 3.25F, 4.0F, 4.75F};
    public static final float DAGGER_SPEED = -2.0F;
    /** Armadura na mão principal: Maça Sagrada e Lâmina do Cavaleiro (v1.0: ×1,5 da v0.8, que era 1/1/2/2 e 1/2/2/3). */
    public static final double[] MACE_ARMOR = {1.5, 1.5, 3, 3};
    public static final double[] KNIGHT_ARMOR = {1.5, 3, 3, 4.5};
    /** Resistência a empurrão da Lâmina do Cavaleiro. */
    public static final double[] KNIGHT_KNOCKBACK = {0.05, 0.10, 0.15, 0.20};
    /** Velocidade de movimento (+% da base): Manoplas e Adagas. */
    public static final double[] FIST_MOVE = {0.04, 0.06, 0.08, 0.10};
    public static final double[] DAGGER_MOVE = {0.03, 0.04, 0.05, 0.06};
    /** Durabilidade das armas por tier (corpo a corpo, arcos e bestas). */
    public static final int[] DURABILITY = {600, 1000, 1700, 2500};

    // ---------------- À distância ----------------
    /** Arco Longo: puxada completa (ticks; o arco comum é 20), velocidade e dano das flechas. */
    public static final int[] LONGBOW_DRAW = {26, 25, 24, 22};
    public static final float[] LONGBOW_SPEED = {1.15F, 1.20F, 1.25F, 1.30F};
    public static final float[] LONGBOW_DAMAGE = {1.10F, 1.18F, 1.26F, 1.35F};
    /** Besta Arcana: recarga (s; a besta comum é 1.25), velocidade e dano (também × Poder Mágico do atirador). */
    public static final float[] ARCANE_CHARGE = {1.15F, 1.05F, 0.95F, 0.85F};
    public static final float[] ARCANE_SPEED = {1.00F, 1.05F, 1.10F, 1.15F};
    public static final float[] ARCANE_DAMAGE = {1.00F, 1.08F, 1.16F, 1.25F};
    /** Besta de Caçador: recarga mais lenta, virotes mais pesados. */
    public static final float[] HUNTER_CHARGE = {1.35F, 1.30F, 1.25F, 1.20F};
    public static final float[] HUNTER_SPEED = {1.05F, 1.10F, 1.15F, 1.20F};
    public static final float[] HUNTER_DAMAGE = {1.20F, 1.30F, 1.40F, 1.50F};

    public final String id;
    /** Classe dona (v1.0: {@link MagicPath#NONE} nas armas genéricas da v0.9). */
    public final MagicPath path;
    private final MagicPath.Category category;
    public final Style style;
    /** Cor de destaque (gemas, brilho, mensagens). */
    public final int color;

    WeaponKind(String id, MagicPath path, Style style, int color) {
        this(id, path, path.category(), style, color);
    }

    /** Arma genérica (sem classe) da categoria informada. */
    WeaponKind(String id, MagicPath.Category category, Style style, int color) {
        this(id, MagicPath.NONE, category, style, color);
    }

    WeaponKind(String id, MagicPath path, MagicPath.Category category, Style style, int color) {
        this.id = id;
        this.path = path;
        this.category = category;
        this.style = style;
        this.color = color;
    }

    /** Categoria da arma (decide os atributos mágicos); nas genéricas é a categoria da classe v0.9 de origem. */
    public MagicPath.Category category() {
        return category;
    }

    /** Arma avulsa (v1.0): não pertence a nenhuma classe. */
    public boolean isGeneric() {
        return path == MagicPath.NONE;
    }

    /** "Mago" nas armas de classe; "Físicas"/"Destreza" nas avulsas. */
    public MutableComponent ownerName() {
        return isGeneric() ? category.displayName() : path.displayName();
    }

    /** Id do item do tier (1..4), ex.: {@code mage_staff_t2}. */
    public String itemId(int tier) {
        return id + "_t" + tier;
    }

    private static int idx(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier)) - 1;
    }

    /** Arma de golpe (espada, foice, maça, machado, manoplas, adagas). */
    public boolean isMelee() {
        return switch (style) {
            case BLADE, SCYTHE, MACE, AXE, FIST, DAGGER -> true;
            default -> false;
        };
    }

    /** Arco ou besta de verdade (dispara flechas). */
    public boolean isRanged() {
        return style == Style.BOW || style == Style.CROSSBOW;
    }

    /** Foco mágico (cajados, varinha e o alaúde do Bardo): atributos de mago. */
    public boolean isFocus() {
        return style == Style.STAFF || style == Style.WAND || style == Style.LUTE;
    }

    /** Espadas varrem (golpe em área); maças, machados, punhos e adagas não. */
    public boolean sweeps() {
        return style == Style.BLADE || style == Style.SCYTHE;
    }

    /** Modificador de dano corpo a corpo do tier (0 = não é arma de golpe). */
    public float meleeDamage(int tier) {
        int i = idx(tier);
        return switch (this) {
            case PACT_BLADE -> BLADE_DAMAGE[i];
            case REAPER_SCYTHE -> SCYTHE_DAMAGE[i];
            case LONGSWORD -> LONGSWORD_DAMAGE[i];
            case HOLY_MACE -> MACE_DAMAGE[i];
            case GREATAXE -> AXE_DAMAGE[i];
            case KI_GAUNTLETS -> FIST_DAMAGE[i];
            case KNIGHT_BLADE -> KNIGHT_DAMAGE[i];
            case TWIN_DAGGERS -> DAGGER_DAMAGE[i];
            default -> 0F;
        };
    }

    /** Modificador de velocidade de ataque (somado aos 4 ataques/s da base). */
    public float attackSpeed() {
        return switch (style) {
            case SCYTHE, MACE -> SCYTHE_SPEED;
            case AXE -> AXE_SPEED;
            case FIST -> FIST_SPEED;
            case DAGGER -> DAGGER_SPEED;
            default -> BLADE_SPEED;
        };
    }

    public double armor(int tier) {
        return switch (this) {
            case HOLY_MACE -> MACE_ARMOR[idx(tier)];
            case KNIGHT_BLADE -> KNIGHT_ARMOR[idx(tier)];
            default -> 0;
        };
    }

    public double knockbackResistance(int tier) {
        return this == KNIGHT_BLADE ? KNIGHT_KNOCKBACK[idx(tier)] : 0;
    }

    public double moveSpeed(int tier) {
        return switch (this) {
            case KI_GAUNTLETS -> FIST_MOVE[idx(tier)];
            case TWIN_DAGGERS -> DAGGER_MOVE[idx(tier)];
            default -> 0;
        };
    }

    public double spellPower(int tier) {
        if (this == LUTE) return LUTE_SPELL_POWER[idx(tier)];
        return category == MagicPath.Category.MAGIC ? SPELL_POWER[idx(tier)] : MARTIAL_SPELL_POWER[idx(tier)];
    }

    /** Mana/Vigor/Foco máximo (soma), já com a inflação do recurso da v1.0 (tabelas ×{@link ResourceTraining#MANA_SCALE}). */
    public double maxMana(int tier) {
        double base;
        if (this == LUTE) base = LUTE_MAX_MANA[idx(tier)];
        else base = category == MagicPath.Category.MAGIC ? MAX_MANA[idx(tier)] : MARTIAL_MAX_MANA[idx(tier)];
        return base * ResourceTraining.MANA_SCALE;
    }

    /** Regen de mana (+%); as Guerreiras não ganham (o Vigor vem dos golpes). */
    public double manaRegen(int tier) {
        if (this == LUTE || category == MagicPath.Category.MAGIC) return MANA_REGEN[idx(tier)];
        return category == MagicPath.Category.DEXTERITY ? RANGED_REGEN[idx(tier)] : 0;
    }

    /** Multiplicador do dano das flechas/virotes disparados por esta arma (1 = normal). */
    public float projectileDamage(int tier) {
        int i = idx(tier);
        return switch (this) {
            case LONGBOW -> LONGBOW_DAMAGE[i];
            case ARCANE_CROSSBOW -> ARCANE_DAMAGE[i];
            case HUNTER_CROSSBOW -> HUNTER_DAMAGE[i];
            default -> 1F;
        };
    }

    /** Multiplicador da velocidade do disparo (flecha mais rápida = mais alcance e mais dano). */
    public float projectileSpeed(int tier) {
        int i = idx(tier);
        return switch (this) {
            case LONGBOW -> LONGBOW_SPEED[i];
            case ARCANE_CROSSBOW -> ARCANE_SPEED[i];
            case HUNTER_CROSSBOW -> HUNTER_SPEED[i];
            default -> 1F;
        };
    }

    /** Ticks da puxada completa do arco. */
    public int drawTicks(int tier) {
        return this == LONGBOW ? LONGBOW_DRAW[idx(tier)] : 20;
    }

    /** Segundos de recarga da besta (antes dos encantamentos de Carga Rápida). */
    public float chargeSeconds(int tier) {
        return switch (this) {
            case ARCANE_CROSSBOW -> ARCANE_CHARGE[idx(tier)];
            case HUNTER_CROSSBOW -> HUNTER_CHARGE[idx(tier)];
            default -> 1.25F;
        };
    }

    /**
     * Arma do Arsenal para a classe: o foco das 5 Mágicas; as classes novas não têm arma aqui (a delas fica no módulo
     * {@code class_<id>} — {@code LevelMilestones} procura por id primeiro), então cai na genérica da categoria — Espada
     * Longa (Físicas), Besta Arcana (Destreza); sem classe, o Cajado do Mago.
     */
    public static WeaponKind forPath(MagicPath path) {
        if (path == null || path == MagicPath.NONE) return MAGE_STAFF;
        for (WeaponKind k : values()) if (k.path == path) return k;
        return switch (path.category()) {
            case PHYSICAL -> LONGSWORD;
            case DEXTERITY -> ARCANE_CROSSBOW;
            default -> MAGE_STAFF;
        };
    }

    /** Pelo id da arma (ex.: {@code longsword}), ou null. */
    public static WeaponKind byId(String id) {
        for (WeaponKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Raridade por tier: comum, incomum, raro, épico. */
    public static Rarity rarity(int tier) {
        return switch (tier) {
            case 2 -> Rarity.UNCOMMON;
            case 3 -> Rarity.RARE;
            case 4 -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    /** Algarismo romano do tier (I..IV) para os textos. */
    public static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "I";
        };
    }
}
