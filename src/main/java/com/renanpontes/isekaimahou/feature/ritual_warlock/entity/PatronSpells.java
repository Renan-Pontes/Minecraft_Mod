package com.renanpontes.isekaimahou.feature.ritual_warlock.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Magias que cada Patrono conjura via {@code SpellCaster.castAsMob}. Os glifos vêm de vários módulos;
 * cada candidato só entra se TODOS os glifos existirem (validado pelo SpellParser). O último candidato
 * usa só glifos do núcleo, então sempre há pelo menos uma magia.
 */
public final class PatronSpells {
    private static final Map<PatronKind, String[][]> CANDIDATES = new EnumMap<>(PatronKind.class);
    private static final Map<PatronKind, List<SpellRecipe>> RESOLVED = new EnumMap<>(PatronKind.class);

    static {
        CANDIDATES.put(PatronKind.EMBER_LORD, new String[][]{
                {"projectile", "ignite", "explosion"},
                {"projectile", "split", "ignite"},
                {"projectile", "black_flame"},
                {"projectile", "infernal_chain"},
                {"projectile", "combustion"},
                {"nova", "ember"},
                {"cone", "ignite"},
                {"fall", "explosion", "ignite"},
                {"projectile", "amplify", "arcane_damage"}});
        CANDIDATES.put(PatronKind.VOID_LADY, new String[][]{
                {"projectile", "homing", "life_drain"},
                {"projectile", "curse"},
                {"projectile", "split", "darkness"},
                {"projectile", "chains"},
                {"projectile", "tentacles"},
                {"nova", "fear"},
                {"beam", "weaken"},
                {"projectile", "split", "arcane_damage"}});
        CANDIDATES.put(PatronKind.DROWNED_KING, new String[][]{
                {"wave", "soak"},
                {"wave", "tide"},
                {"projectile", "water_blade"},
                {"projectile", "current"},
                {"projectile", "scald"},
                {"projectile", "bubble"},
                {"cone", "push", "arcane_damage"},
                {"projectile", "push", "arcane_damage"}});
    }

    /** Magia sorteada para o Patrono, ou null se nenhuma receita for válida. */
    public static synchronized SpellRecipe pick(PatronKind kind, RandomSource random) {
        List<SpellRecipe> list = RESOLVED.computeIfAbsent(kind, PatronSpells::resolve);
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private static List<SpellRecipe> resolve(PatronKind kind) {
        List<SpellRecipe> out = new ArrayList<>();
        for (String[] ids : CANDIDATES.get(kind)) {
            List<ResourceLocation> glyphs = new ArrayList<>();
            for (String id : ids) glyphs.add(IsekaiMahou.id(id));
            SpellRecipe r = new SpellRecipe(glyphs, "", -1, "");
            if (SpellParser.parse(r, null, null).ok()) out.add(r);
        }
        return out;
    }

    private PatronSpells() {}
}
