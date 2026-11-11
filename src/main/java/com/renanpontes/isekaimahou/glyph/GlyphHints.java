package com.renanpontes.isekaimahou.glyph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.0 — Dicas de números dos glifos para o DETALHAMENTO da magia ({@link ParsedSpell#breakdown()}): dano/cura base,
 * duração base (ticks), raio base e alcance (blocos). São só ESTIMATIVAS para o Grimório; a magia de verdade continua
 * calculando tudo no {@code onEntity}/{@code cast} de cada glifo.
 * <p>
 * Ordem de prioridade: o glifo pode sobrescrever {@link EffectGlyph#baseDamage()}, {@link EffectGlyph#baseHeal()},
 * {@link FormGlyph#hintRange()}... (recomendado para glifos novos da fase B); senão vale a dica registrada aqui
 * ({@link #set}), por id. A tabela inicial foi extraída do código dos módulos da v0.8/v0.9 (primeiro número passado ao
 * dano/cura, ao {@code stats.duration(..)} e ao {@code stats.radius(..)} de cada glifo). Valor &lt; 0 = desconhecido;
 * dano 0 = o glifo não causa dano direto.
 */
public final class GlyphHints {
    /**
     * @param damage   dano base por acerto (antes de poder/aumentos/{@code DAMAGE_SCALE}); 0 = sem dano direto
     * @param heal     cura base por acerto; 0 = não cura
     * @param duration duração base em ticks (antes de Estender/Persistente); &lt; 0 = sem duração
     * @param radius   raio base em blocos (antes de Expandir/Concentrar); &lt; 0 = sem área
     * @param range    alcance em blocos (formas); &lt; 0 = desconhecido
     */
    public record Hint(float damage, float heal, int duration, float radius, float range) {
        public static final Hint NONE = new Hint(0F, 0F, -1, -1F, -1F);
    }

    private static final Map<ResourceLocation, Hint> HINTS = new ConcurrentHashMap<>();

    static {
        // Formas do núcleo (glyph/CoreGlyphs, glyph/weapon)
        h("projectile", 0F, 0F, -1, -1F, 48.0F);
        h("touch", 0F, 0F, -1, -1F, 4.5F);
        h("self", 0F, 0F, -1, -1F, 0F);
        h("push", 0F, 0F, -1, -1F, -1F);
        h("strike", 0F, 0F, -1, -1F, 3.5F);
        h("arrow", 0F, 0F, -1, -1F, 64.0F);
        // Extraído automaticamente do código dos módulos (v0.8/v0.9)
        h("absolute_zero", 0F, 0F, 80, 10.0F, -1F);
        h("abyssal_drown", 1.5F, 0F, 120, 2.5F, -1F);
        h("abyssal_pressure", 4.0F, 0F, -1, 3.0F, -1F);
        h("annihilate", 0F, 0F, -1, 1.8F, -1F);
        h("arcane_damage", 6.0F, 0F, -1, -1F, -1F);
        h("astral_projection", 0F, 0F, 200, -1F, -1F);
        h("attraction_point", 0F, 0F, 80, 7.0F, -1F);
        h("aura", 0F, 0F, 200, 3.0F, -1F);
        h("banish", 2.0F, 0F, -1, 3.0F, -1F);
        h("beam", 0F, 0F, -1, -1F, 24.0F);
        h("black_flame", 2.0F, 0F, 100, 2.5F, -1F);
        h("blizzard", 0F, 0F, 160, 5.0F, -1F);
        h("blood_pact", 4.0F, 0F, 400, -1F, -1F);
        h("bloom", 4.0F, 6.0F, 80, 4.0F, -1F);
        h("blue_flame", 8.0F, 0F, 120, 3.0F, -1F);
        h("bone_army", 0F, 0F, 800, -1F, -1F);
        h("bone_shield", 0F, 0F, 600, -1F, -1F);
        h("bubble", 0F, 0F, 60, 3.0F, -1F);
        h("cataclysm", 0F, 0F, -1, 8.0F, 48.0F);
        h("chains", 2.0F, 0F, -1, -1F, -1F);
        h("cleanse", 2.0F, 0F, -1, -1F, -1F);
        h("cleave", 0F, 0F, 120, -1F, -1F);
        h("colossus", 0F, 0F, 600, -1F, -1F);
        h("combustion", 2.0F, 0F, -1, 6.0F, -1F);
        h("conjure", 0F, 0F, 600, -1F, -1F);
        h("conjure_light", 0F, 0F, 200, 4.0F, -1F);
        h("constellation", 5.0F, 0F, 50, 10.0F, -1F);
        h("converge", 0F, 0F, -1, 1.9F, -1F);
        h("corpse_explosion", 0F, 0F, -1, 4.0F, -1F);
        h("counterspell", 0F, 0F, 60, 8.0F, -1F);
        h("crush", 7.5F, 0F, 40, 2.5F, -1F);
        h("crystal_armor", 0F, 0F, 200, -1F, -1F);
        h("crystallize", 2.0F, 0F, 60, 3.0F, -1F);
        h("current", 2.0F, 0F, 60, -1F, -1F);
        h("dawn", 10.0F, 0F, 60, 8.0F, -1F);
        h("death_contract", 0F, 0F, 300, -1F, -1F);
        h("debris_orbit", 0F, 0F, 300, -1F, -1F);
        h("deluge", 0F, 0F, 240, 9.0F, -1F);
        h("demon_wings", 0F, 0F, 400, -1F, -1F);
        h("detect", 0F, 0F, 200, 24.0F, -1F);
        h("devour", 0F, 0.5F, -1, -1F, -1F);
        h("disarm", 0F, 0F, 60, -1F, -1F);
        h("disintegration", 0F, 0F, -1, 3.0F, -1F);
        h("dismantle", 0F, 0F, 120, -1F, -1F);
        h("dispel", 0F, 0F, -1, 4.0F, -1F);
        h("drown", 2.0F, 0F, 120, 3.0F, -1F);
        h("duality", 0F, 0F, 400, -1F, -1F);
        h("dune", 4.0F, 0F, 40, -1F, -1F);
        h("earthsplitter", 12.0F, 0F, 120, -1F, -1F);
        h("eclipse", 0F, 0F, 200, 6.0F, -1F);
        h("ember", 2.0F, 0F, 160, 2.5F, -1F);
        h("equilibrium", 3.0F, 3.0F, -1, -1F, -1F);
        h("erase", 4.5F, 0F, -1, 2.5F, -1F);
        h("eruption", 0F, 0F, 80, 5.0F, -1F);
        h("exile", 6.0F, 0F, 120, -1F, -1F);
        h("exorcise", 20.0F, 0F, -1, -1F, -1F);
        h("explosion", 6.0F, 0F, -1, 2.5F, -1F);
        h("extinguish", 5.0F, 0F, -1, 2.5F, -1F);
        h("fall", 0F, 0F, -1, -1F, 48.0F);
        h("falling_star", 0F, 0F, -1, 2.5F, -1F);
        h("familiar", 0F, 0F, 12000, -1F, -1F);
        h("fear", 0F, 0F, -1, 6.0F, -1F);
        h("fertilize", 0F, 0F, -1, 2.5F, -1F);
        h("final_judgment", 18.0F, 0F, -1, 8.0F, -1F);
        h("fire_wall", 0F, 0F, 160, 3.5F, -1F);
        h("fortress", 0F, 0F, 300, 3.5F, -1F);
        h("freeze", 2.0F, 0F, 300, 2.5F, -1F);
        h("frogify", 0F, 0F, 400, -1F, -1F);
        h("geyser", 4.0F, 0F, -1, -1F, -1F);
        h("goatify", 0F, 0F, 120, -1F, -1F);
        h("greater_heal", 10.0F, 10.0F, 80, -1F, -1F);
        h("grow", 0F, 0F, 300, -1F, -1F);
        h("growth", 0F, 0F, -1, 2.5F, -1F);
        h("harvest", 0F, 0F, -1, 2.0F, -1F);
        h("heal", 8.0F, 6.0F, -1, -1F, -1F);
        h("hell_portal", 0F, 0F, 400, -1F, -1F);
        h("hexproof", 0F, 0F, 160, -1F, -1F);
        h("holy_flame", 8.0F, 0F, 100, -1F, -1F);
        h("holy_water", 9.0F, 4.0F, 100, 3.0F, -1F);
        h("hot_mist", 0F, 0F, 160, 4.0F, -1F);
        h("hydrate", 0F, 0F, -1, 2.0F, -1F);
        h("ice_armor", 2.0F, 0F, 600, -1F, -1F);
        h("ice_bridge", 0F, 0F, 300, 14.0F, -1F);
        h("ice_coffin", 0F, 0F, -1, 6.0F, -1F);
        h("ice_lance", 7.0F, 0F, 40, -1F, -1F);
        h("ice_statue", 3.0F, 0F, 60, -1F, -1F);
        h("ignite", 3.0F, 0F, 80, 2.5F, -1F);
        h("illusion", 0F, 0F, 200, -1F, -1F);
        h("indestructible", 0F, 0F, 80, -1F, -1F);
        h("infernal_chain", 2.0F, 0F, 80, 8.0F, -1F);
        h("ink", 0F, 0F, 100, 3.5F, -1F);
        h("invert_gravity", 2.0F, 0F, 50, 3.0F, -1F);
        h("kraken_grasp", 0F, 0F, 140, 3.4F, -1F);
        h("launch", 3.0F, 0F, -1, -1F, -1F);
        h("lava_geyser", 0F, 0F, -1, 8.0F, -1F);
        h("lava_pool", 0F, 0F, 120, 1.8F, -1F);
        h("lich_form", 0F, 0F, 600, -1F, -1F);
        h("life_drain", 4.0F, 0F, -1, -1F, -1F);
        h("lightning_bolt", 7.0F, 0F, -1, -1F, -1F);
        h("living_shadow", 0F, 0F, 600, -1F, -1F);
        h("local_rain", 0F, 0F, 300, 5.0F, -1F);
        h("magma_armor", 2.0F, 0F, 100, -1F, -1F);
        h("magnet", 9.0F, 0F, -1, 8.0F, -1F);
        h("magnetism", 0F, 0F, 60, 10.0F, -1F);
        h("mana_drain", 3.0F, 0F, 40, -1F, -1F);
        h("mana_gift", 0F, 3.0F, -1, -1F, -1F);
        h("mana_leak", 0F, 0F, -1, 5.0F, -1F);
        h("mark", 0F, 0F, -1, 1.5F, 32.0F);
        h("mark_target", 0F, 0F, 160, -1F, -1F);
        h("mass_levitate", 0F, 0F, 60, 4.0F, -1F);
        h("melt", 7.5F, 0F, -1, 3.0F, -1F);
        h("mend", 4.0F, 5.0F, 80, -1F, -1F);
        h("meteor", 0F, 0F, -1, 4.0F, 48.0F);
        h("meteor_shower", 0F, 0F, 300, -1F, 56.0F);
        h("mirage", 0F, 0F, 120, 5.0F, -1F);
        h("mist", 0F, 0F, 200, 4.0F, -1F);
        h("molten_rock", 6.0F, 0F, 80, -1F, -1F);
        h("obsidian", 3.0F, 0F, 70, 1.6F, -1F);
        h("orbit", 0F, 0F, 300, 2.0F, -1F);
        h("overcharge", 3.0F, 0F, 30, -1F, -1F);
        h("overheat", 6.0F, 0F, 160, 3.0F, -1F);
        h("paralyze", 2.0F, 0F, -1, -1F, -1F);
        h("phoenix_rebirth", 0F, 0F, 400, -1F, -1F);
        h("photosynthesis", 0F, 0F, 1200, -1F, -1F);
        h("place_block", 0F, 0F, 600, -1F, -1F);
        h("portal", 0F, 0F, 300, -1F, 64.0F);
        h("possession", 0F, 0F, 600, -1F, -1F);
        h("prism", 6.0F, 0F, -1, 10.0F, -1F);
        h("protective_bubble", 0F, 0F, 40, -1F, -1F);
        h("pulse", 0F, 0F, 100, 3.5F, -1F);
        h("purge", 1.25F, 2.0F, -1, 8.0F, -1F);
        h("purify", 4.0F, 0F, -1, -1F, -1F);
        h("quicksand", 0F, 0F, 160, 1.6F, -1F);
        h("ragnarok", 10.0F, 0F, -1, 10.0F, -1F);
        h("railgun", 18.0F, 0F, -1, -1F, -1F);
        h("rain", 0F, 0F, 60, -1F, 48.0F);
        h("raise_dead", 0F, 0F, 1200, -1F, -1F);
        h("raise_pillar", 0F, 0F, 200, -1F, -1F);
        h("redirect", 0F, 0F, -1, 5.0F, -1F);
        h("reflecting_prism", 0F, 0F, 40, -1F, -1F);
        h("resurrection", 30.0F, 0F, -1, -1F, -1F);
        h("reveal", 0F, 0F, -1, 12.0F, -1F);
        h("rift", 0F, 0F, 120, 2.5F, -1F);
        h("rock", 7.5F, 0F, -1, -1F, -1F);
        h("root", 2.0F, 0F, 80, -1F, -1F);
        h("rune", 0F, 0F, 1200, 2.0F, -1F);
        h("sacred_barrier", 0F, 0F, 400, 5.0F, -1F);
        h("sanctuary", 0F, 0F, 300, 4.5F, -1F);
        h("sand_coffin", 0F, 0F, 50, 6.0F, -1F);
        h("sand_tsunami", 0F, 0F, -1, 9.0F, -1F);
        h("sandstorm", 0F, 0F, 160, 5.0F, -1F);
        h("scald", 5.0F, 0F, 80, -1F, -1F);
        h("shadow_step", 0F, 0F, -1, 16.0F, -1F);
        h("shadow_weapon", 0F, 0F, 900, -1F, -1F);
        h("shards", 3.0F, 0F, -1, -1F, -1F);
        h("shield", 0F, 0F, 400, -1F, -1F);
        h("silent_steps", 0F, 0F, 200, -1F, -1F);
        h("sinkhole", 0F, 0F, 120, 2.5F, -1F);
        h("sky_lances", 0F, 0F, 60, 6.0F, 48.0F);
        h("slash", 0F, 0F, -1, -1F, 3.5F);
        h("slash_storm", 0F, 0F, -1, 1.5F, -1F);
        h("slippery_ground", 0F, 0F, 200, 3.0F, -1F);
        h("slow_time", 0F, 0F, -1, 4.0F, -1F);
        h("smelt", 4.0F, 0F, -1, -1F, -1F);
        h("smelt_items", 0F, 0F, 60, -1F, -1F);
        h("smoke", 0F, 0F, 120, 3.0F, -1F);
        h("soak", 2.0F, 0F, -1, -1F, -1F);
        h("solar_crown", 3.0F, 0F, 80, 4.0F, -1F);
        h("solar_flare", 0F, 0F, 50, 0.7F, -1F);
        h("solar_ray", 0F, 0F, 60, -1F, -1F);
        h("sonar", 0F, 0F, 200, 32.0F, -1F);
        h("soul_burn", 3.0F, 0F, 120, 4.5F, -1F);
        h("soul_drain", 5.0F, 0F, -1, -1F, -1F);
        h("spiral_sphere", 0F, 0F, 80, 0.55F, -1F);
        h("spores", 1.5F, 0F, 80, 2.5F, -1F);
        h("stalagmite", 7.0F, 0F, -1, -1F, -1F);
        h("starfall", 0F, 0F, -1, 7.0F, -1F);
        h("steal_blessing", 2.0F, 0F, -1, 8.0F, -1F);
        h("steam_cannon", 0F, 0F, 30, 1.2F, -1F);
        h("steam_explosion", 6.0F, 0F, -1, 3.5F, -1F);
        h("stone_wall", 0F, 0F, 240, 2.0F, -1F);
        h("stun", 0F, 0F, 50, -1F, -1F);
        h("summon_demon", 0F, 0F, 900, -1F, -1F);
        h("summon_ent", 0F, 0F, 900, -1F, -1F);
        h("summon_golem", 0F, 0F, 900, -1F, -1F);
        h("summon_guardian_fairy", 0F, 0F, 1200, -1F, -1F);
        h("summon_imp", 0F, 0F, 1200, -1F, -1F);
        h("summon_salamander", 0F, 0F, 600, -1F, -1F);
        h("summon_sylph", 0F, 0F, 900, -1F, -1F);
        h("summon_undine", 0F, 0F, 600, -1F, -1F);
        h("sunlight", 4.0F, 0F, 160, 2.0F, -1F);
        h("telekinesis", 0F, 0F, 80, 3.0F, -1F);
        h("temp_enchant", 0F, 0F, 6000, -1F, -1F);
        h("tempest", 0F, 0F, 300, 10.0F, -1F);
        h("tentacles", 2.0F, 0F, 80, 4.0F, -1F);
        h("thorns", 5.0F, 0F, 60, -1F, -1F);
        h("thunderstorm", 0F, 0F, 200, 6.0F, -1F);
        h("tide", 1.5F, 0F, -1, 4.0F, -1F);
        h("tornado", 0F, 0F, 200, 2.5F, -1F);
        h("transcend", 0F, 0F, 600, -1F, -1F);
        h("tremor", 5.0F, 0F, -1, 6.0F, -1F);
        h("turret", 0F, 0F, 400, -1F, -1F);
        h("vacuum", 3.0F, 0F, 80, 6.0F, -1F);
        h("venom", 1.5F, 0F, 140, 2.5F, -1F);
        h("vines", 2.0F, 0F, 60, -1F, -1F);
        h("vitrify", 4.0F, 0F, 50, 2.0F, -1F);
        h("voodoo_doll", 0F, 0F, 1200, -1F, -1F);
        h("wall", 0F, 0F, 200, -1F, -1F);
        h("water_blade", 0F, 0F, -1, 3.0F, -1F);
        h("water_breathing", 0F, 0F, 40, -1F, -1F);
        h("water_mirror", 0F, 0F, 300, -1F, -1F);
        h("weaken", 1.5F, 0F, -1, -1F, -1F);
        h("whirlwind", 0F, 0F, 60, 5.0F, -1F);
        h("wind_blade", 5.5F, 0F, -1, -1F, -1F);
        h("world_roots", 0F, 0F, 60, 6.0F, -1F);
        h("wrath", 0F, 0F, -1, 14.0F, -1F);
        h("zero_gravity", 0F, 0F, 200, -1F, -1F);
        h("zone", 0F, 0F, 100, 3.0F, -1F);
    }

    /** Registra/troca a dica de um glifo (módulos da fase B podem chamar no init). */
    public static void set(ResourceLocation id, Hint hint) {
        if (id != null && hint != null) HINTS.put(id, hint);
    }

    /** Dica do glifo, ou null se não há. */
    @Nullable
    public static Hint get(ResourceLocation id) {
        return id == null ? null : HINTS.get(id);
    }

    /** Dica do glifo ou {@link Hint#NONE}. */
    public static Hint getOrNone(ResourceLocation id) {
        Hint h = get(id);
        return h != null ? h : Hint.NONE;
    }

    private static void h(String path, float damage, float heal, int duration, float radius, float range) {
        HINTS.put(IsekaiMahou.id(path), new Hint(damage, heal, duration, radius, range));
    }

    private GlyphHints() {}
}
