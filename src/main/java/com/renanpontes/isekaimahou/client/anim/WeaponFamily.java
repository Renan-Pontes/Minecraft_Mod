package com.renanpontes.isekaimahou.client.anim;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Família da arma na mão (decide o combo). Descoberta pelo id do item (katana, zanpakuto, adaga, martelo, lança...)
 * e pela classe/tag (SwordItem, AxeItem, TridentItem...). Itens que não são armas (picareta, arco, arma de fogo,
 * varinha) ficam com a animação do Minecraft. O resultado é guardado por item (sem custo por clique).
 */
enum WeaponFamily {
    SWORD(5, MahouAnims.SWORD_1, MahouAnims.SWORD_2, MahouAnims.SWORD_3),
    HEAVY(9, MahouAnims.HEAVY_1, MahouAnims.HEAVY_2),
    DAGGER(3, MahouAnims.DAGGER_1, MahouAnims.DAGGER_2),
    SPEAR(6, MahouAnims.SPEAR_1, MahouAnims.SPEAR_2),
    FIST(4, MahouAnims.FIST_1, MahouAnims.FIST_2, MahouAnims.FIST_3);

    /** Ticks mínimos entre dois golpes animados (cliques mais rápidos não reiniciam a animação). */
    final int minGap;
    final String[] combo;

    WeaponFamily(int minGap, String... combo) {
        this.minGap = minGap;
        this.combo = combo;
    }

    private static final String[] FIST_KEYS = {"gauntlet", "fist", "knuckle", "cestus", "glove"};
    private static final String[] DAGGER_KEYS = {"dagger", "knife", "kunai", "tanto", "dirk", "shiv", "stiletto", "stinger"};
    private static final String[] SPEAR_KEYS = {"spear", "lance", "trident", "halberd", "glaive", "naginata", "pike", "javelin",
            "partisan", "polearm", "yari"};
    private static final String[] HEAVY_KEYS = {"hammer", "mace", "greataxe", "great_axe", "battleaxe", "battle_axe", "greatsword",
            "great_sword", "claymore", "zweihander", "maul", "club", "scythe", "cleaver", "buster", "warhammer", "flail", "anvil"};
    private static final String[] SWORD_KEYS = {"sword", "katana", "zanpakuto", "blade", "saber", "sabre", "rapier", "falchion",
            "scimitar", "cutlass", "odachi", "wakizashi", "schneider", "longsword", "staff"};

    /** Sem família (null) = item que não é arma. */
    private static final Object NONE = new Object();
    private static final Map<Item, Object> CACHE = new IdentityHashMap<>();

    @Nullable
    static WeaponFamily of(ItemStack stack) {
        if (stack.isEmpty()) return FIST;
        Item item = stack.getItem();
        Object c = CACHE.get(item);
        if (c == null) {
            WeaponFamily f = detect(stack);
            c = f == null ? NONE : f;
            CACHE.put(item, c);
        }
        return c == NONE ? null : (WeaponFamily) c;
    }

    @Nullable
    private static WeaponFamily detect(ItemStack stack) {
        Item item = stack.getItem();
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.contains("pickaxe") || path.contains("shovel") || path.contains("hoe") && !path.contains("shoe")) return null;
        if (path.startsWith("mana_gun") || path.contains("pistol") || path.contains("rifle") || path.contains("bow")) return null;
        if (any(path, FIST_KEYS)) return FIST;
        if (any(path, DAGGER_KEYS)) return DAGGER;
        if (any(path, SPEAR_KEYS) || item instanceof TridentItem) return SPEAR;
        if (any(path, HEAVY_KEYS) || item instanceof MaceItem) return HEAVY;
        if (item instanceof AxeItem || stack.is(ItemTags.AXES) || path.endsWith("_axe") || path.equals("axe")) return HEAVY;
        if (item instanceof SwordItem || stack.is(ItemTags.SWORDS) || any(path, SWORD_KEYS)) return SWORD;
        return null;
    }

    private static boolean any(String path, String[] keys) {
        for (String k : keys) {
            if (path.contains(k)) return true;
        }
        return false;
    }
}
