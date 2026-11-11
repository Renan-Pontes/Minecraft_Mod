package com.renanpontes.isekaimahou.glyph;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

/**
 * Um slot (Z, X, C ou G) de um loadout: até 5 versões da magia, uma por {@link MoveTrigger}.
 * A versão NORMAL é a principal; as outras são opcionais.
 */
public final class SpellSlot {
    public static final Codec<SpellSlot> CODEC = Codec.unboundedMap(Codec.STRING, SpellRecipe.CODEC)
            .xmap(SpellSlot::fromMap, SpellSlot::toMap);

    private final EnumMap<MoveTrigger, SpellRecipe> variants = new EnumMap<>(MoveTrigger.class);

    public SpellRecipe get(MoveTrigger t) {
        return variants.get(t);
    }

    public void set(MoveTrigger t, SpellRecipe recipe) {
        if (recipe == null || recipe.isEmpty()) variants.remove(t);
        else variants.put(t, recipe);
    }

    public boolean isEmpty() {
        return variants.isEmpty();
    }

    public SpellRecipe main() {
        return variants.get(MoveTrigger.NORMAL);
    }

    public Map<MoveTrigger, SpellRecipe> all() {
        return variants;
    }

    private static SpellSlot fromMap(Map<String, SpellRecipe> map) {
        SpellSlot s = new SpellSlot();
        map.forEach((k, v) -> {
            for (MoveTrigger t : MoveTrigger.values()) if (t.id.equals(k)) s.set(t, v);
        });
        return s;
    }

    private Map<String, SpellRecipe> toMap() {
        Map<String, SpellRecipe> m = new HashMap<>();
        variants.forEach((t, r) -> m.put(t.id, r));
        return m;
    }
}
