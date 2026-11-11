package com.renanpontes.isekaimahou.feature.bosses_d.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Magias de glifo do Avatar (inclusive PROGRAMÁVEIS, com ligações Ao Acertar / Saltar / Insaciável).
 * Cada elemento tem uma lista de receitas em ordem de preferência: se um glifo não existe (módulo ausente) ou a
 * receita não é válida (ex.: forma ainda não "stageable" num estágio 2+), tenta a próxima. A última reserva é sempre
 * {@code projectile, arcane_damage} (glifos do núcleo).
 */
public final class AvatarSpells {
    /** Uma receita com chave de anúncio ({@code announce.isekai_mahou.trial_avatar.spell.<key>}). */
    public record Spell(String key, List<ResourceLocation> glyphs) {
        boolean programmable() {
            for (ResourceLocation id : glyphs) if (id.getPath().startsWith("link_") || id.getPath().equals("relentless")) return true;
            return false;
        }
    }

    private static final Map<Element, List<Spell>> PRIMARY = new EnumMap<>(Element.class);
    private static final Map<Element, List<Spell>> SECONDARY = new EnumMap<>(Element.class);
    private static final Spell FALLBACK = spell("arcane_bolt", "projectile, arcane_damage");

    static {
        // ---------------- elementos base ----------------
        put(PRIMARY, Element.FIRE,
                spell("fire_burst", "projectile, accelerate, ignite, link_hit, nova, explosion"),
                spell("fire_shatter", "projectile, accelerate, ignite, link_hit, projectile, split, split, ember"),
                spell("fire_bolt", "projectile, ignite, amplify"));
        put(SECONDARY, Element.FIRE,
                spell("fire_leap", "projectile, explosion, link_leap"),
                spell("fire_twin", "projectile, split, ignite"));
        put(PRIMARY, Element.WATER,
                spell("water_torrent", "beam, water_blade, link_hit, zone, soak"),
                spell("water_split", "projectile, water_blade, link_hit, projectile, split, split, tide"),
                spell("water_blade", "projectile, water_blade"));
        put(SECONDARY, Element.WATER,
                spell("water_leap", "projectile, tide, link_leap"),
                spell("water_twin", "projectile, split, soak"));
        put(PRIMARY, Element.EARTH,
                spell("earth_fall", "projectile, rock, link_hit, fall, stalagmite"),
                spell("earth_shrapnel", "projectile, rock, amplify, link_hit, projectile, split, split, rock"),
                spell("earth_rock", "projectile, rock, amplify"));
        put(SECONDARY, Element.EARTH,
                spell("earth_snare", "projectile, decelerate, stalagmite, link_hit, touch, expand, root"),
                spell("earth_twin", "projectile, split, rock"));
        put(PRIMARY, Element.WIND,
                spell("wind_leap", "projectile, accelerate, wind_blade, link_leap"),
                spell("wind_fan", "projectile, split, split, wind_blade"),
                spell("wind_blade", "projectile, wind_blade"));
        put(SECONDARY, Element.WIND,
                spell("wind_chain", "projectile, chain, relentless, wind_blade"),
                spell("wind_gust", "projectile, accelerate, gust"));
        put(PRIMARY, Element.LIGHT,
                spell("light_prism", "beam, holy_ray, link_hit, nova, blind"),
                spell("light_burst", "beam, holy_ray, link_hit, projectile, split, split, holy_ray"),
                spell("light_ray", "projectile, holy_ray"));
        put(SECONDARY, Element.LIGHT,
                spell("light_leap", "projectile, holy_ray, link_leap"),
                spell("light_twin", "projectile, split, holy_ray"));
        put(PRIMARY, Element.SHADOW,
                spell("shadow_drain", "projectile, life_drain, link_leap"),
                spell("shadow_curse", "projectile, weaken, link_hit, projectile, split, split, life_drain"),
                spell("shadow_bolt", "projectile, life_drain"));
        put(SECONDARY, Element.SHADOW,
                spell("shadow_chain", "beam, chains, link_hit, nova, darkness"),
                spell("shadow_dark", "projectile, darkness"));
        put(PRIMARY, Element.ARCANE,
                spell("arcane_cascade", "projectile, arcane_damage, link_hit, projectile, split, split, arcane_damage"),
                spell("arcane_missiles", "projectile, split, arcane_damage"));
        put(SECONDARY, Element.ARCANE,
                spell("arcane_leap", "projectile, arcane_damage, link_leap"),
                spell("arcane_push", "projectile, accelerate, arcane_damage, push"));

        // ---------------- fusões (antes das listas dos pais) ----------------
        put(PRIMARY, Element.MAGMA, spell("magma_rain", "projectile, molten_rock, link_hit, projectile, split, split, molten_rock"));
        put(PRIMARY, Element.STEAM, spell("steam_leap", "projectile, scald, link_leap"));
        put(PRIMARY, Element.LIGHTNING, spell("lightning_chain", "projectile, chain, relentless, lightning_bolt"));
        put(PRIMARY, Element.SUN, spell("sun_flare", "beam, solar_ray, link_hit, nova, holy_flame"), spell("sun_ray", "beam, solar_ray"));
        put(PRIMARY, Element.HELLFIRE, spell("hell_shatter", "projectile, black_flame, link_hit, projectile, split, split, black_flame"));
        put(PRIMARY, Element.NATURE, spell("nature_bloom", "projectile, thorns, link_hit, zone, spores"), spell("nature_leap", "projectile, thorns, link_leap"));
        put(PRIMARY, Element.ICE, spell("ice_shatter", "projectile, ice_lance, link_hit, projectile, split, split, freeze"));
        put(PRIMARY, Element.PURITY, spell("purity_leap", "projectile, holy_water, link_leap"));
        put(PRIMARY, Element.ABYSS, spell("abyss_venom", "projectile, venom, link_leap"));
        put(PRIMARY, Element.SAND, spell("sand_burst", "projectile, dune, link_hit, nova, sandstorm"), spell("sand_dune", "projectile, dune"));
        put(PRIMARY, Element.CRYSTAL, spell("crystal_shatter", "projectile, shards, link_hit, projectile, split, split, shards"));
        put(PRIMARY, Element.GRAVITY, spell("gravity_crush", "projectile, crush, link_hit, nova, crush"), spell("gravity_bolt", "projectile, crush"));
        put(PRIMARY, Element.ASTRAL, spell("astral_star", "projectile, falling_star, link_leap"));
        put(PRIMARY, Element.VOID, spell("void_erase", "beam, pierce, erase, link_hit, nova, erase"), spell("void_ray", "beam, pierce, erase"));
        put(PRIMARY, Element.TWILIGHT, spell("twilight_duality", "projectile, duality, link_hit, projectile, split, split, life_drain"),
                spell("twilight_bolt", "projectile, duality"));
    }

    private static Spell spell(String key, String recipe) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String s : recipe.split(",")) ids.add(IsekaiMahou.id(s.trim()));
        return new Spell(key, List.copyOf(ids));
    }

    private static void put(Map<Element, List<Spell>> map, Element e, Spell... spells) {
        map.put(e, List.of(spells));
    }

    /** Lista de tentativas para o elemento: própria da fusão → pai A → pai B → reserva do núcleo. */
    static List<Spell> options(Element e, boolean secondary) {
        Map<Element, List<Spell>> map = secondary ? SECONDARY : PRIMARY;
        List<Spell> out = new ArrayList<>();
        if (e.isFusion()) {
            out.addAll(PRIMARY.getOrDefault(e, List.of()));
            out.addAll(map.getOrDefault(secondary ? e.parentB : e.parentA, List.of()));
            out.addAll(map.getOrDefault(secondary ? e.parentA : e.parentB, List.of()));
        } else {
            out.addAll(map.getOrDefault(e, List.of()));
            if (secondary) out.addAll(PRIMARY.getOrDefault(e, List.of()));
        }
        out.add(FALLBACK);
        return out;
    }

    private static boolean available(Spell s) {
        for (ResourceLocation id : s.glyphs) if (GlyphRegistry.get(id) == null) return false;
        return true;
    }

    /**
     * Conjura a primeira receita válida a partir de {@code origin} na direção {@code dir} (castFrom: sem mana, poder fixo).
     * @return a magia conjurada, ou null se nenhuma deu certo (evento cancelado por um Domínio, por exemplo)
     */
    @Nullable
    public static Spell cast(LivingEntity caster, Element element, boolean secondary, float power, Vec3 origin, Vec3 dir) {
        for (Spell s : options(element, secondary)) {
            if (!available(s)) continue;
            SpellRecipe recipe = new SpellRecipe(s.glyphs, "", element.color, "");
            SpellContext ctx = SpellCaster.castFrom(caster, element, recipe, power, origin, dir, true);
            if (ctx != null) return s;
        }
        return null;
    }

    private AvatarSpells() {}
}
