package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossThemed;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Qual tema toca para cada chefe (comum: o cliente escolhe a música e o servidor a duração da ENTRADA pelo mesmo tema).
 * Ordem: {@link BossThemed#musicTheme()} → faixa com o id da entidade → família (bestas, guardiões...) → genérico.
 */
public final class BossThemes {
    /** Família de cada chefe sem faixa própria (id do tipo → tema). */
    static final Map<String, String> FAMILY = new HashMap<>();
    /** Temas "de família": se o chefe tiver faixa com o próprio id, ela ganha. */
    private static final Set<String> FAMILY_THEMES = Set.of("beast", "elemental_guardian", BossMusicSounds.GENERIC);

    static {
        for (String s : new String[] {"ignis", "tide_guardian", "zephyr", "primordial_golem"}) FAMILY.put(s, "elemental_guardian");
        for (String s : new String[] {"swamp_hydra", "false_hydra", "false_hydra_head", "desert_basilisk", "wyvern_king", "manticore",
                "ruin_colossus", "corrupted_ent", "minotaur", "orc_lord", "goblin_king", "young_kraken", "storm_wyvern"}) FAMILY.put(s, "beast");
        for (String s : new String[] {"corrupted_witch", "imprisoned_patron", "renegade_archmage"}) FAMILY.put(s, "lich");
        for (String s : new String[] {"sand_empress", "thorn_queen", "menos_giant"}) FAMILY.put(s, "hollow_king");
        for (String s : new String[] {"primordial_shade", "primordial_chain"}) FAMILY.put(s, "primordial_dragon");
        // a Sentinela da provação é um lacaio (90 de vida): não liga música de chefe (antes tocava o genérico na sala toda)
        FAMILY.put("trial_avatar", BossMusicSounds.GENERIC);
        FAMILY.put("king_slime", "mimic_king");
        FAMILY.put("vault_mimic", "mimic_king");
        FAMILY.put("minecraft:wither", BossMusicSounds.GENERIC);
    }

    /** O id tem tema conhecido (faixa própria ou família)? */
    public static boolean known(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (IsekaiMahou.MODID.equals(id.getNamespace())) {
            return BossMusicSounds.isTheme(id.getPath()) || FAMILY.containsKey(id.getPath());
        }
        return FAMILY.containsKey(id.toString());
    }

    public static String themeOf(LivingEntity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        String own = IsekaiMahou.MODID.equals(id.getNamespace()) && BossMusicSounds.isTheme(id.getPath()) ? id.getPath() : null;
        // música escolhida pelo Renan para ESTE chefe (pasta própria em MUSICAS_DOS_CHEFES): ganha de tudo
        if (BossMusicSounds.isOwnBossTrack(own)) return own;
        if (e instanceof BossThemed bt) {
            String t;
            try {
                t = bt.musicTheme();
            } catch (RuntimeException ex) {
                t = null;
            }
            if (BossMusicSounds.isTheme(t)) return own != null && FAMILY_THEMES.contains(t) ? own : t;
        }
        if (own != null) return own;
        String fam = FAMILY.get(IsekaiMahou.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString());
        return fam != null ? fam : BossMusicSounds.GENERIC;
    }

    private BossThemes() {}
}
