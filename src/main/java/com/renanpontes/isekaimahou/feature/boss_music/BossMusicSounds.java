package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * As músicas tema (geradas por {@code tools/gen/boss_music.py} em {@code sounds/music/<tema>.ogg}; chaves
 * {@code boss_music.<tema>} no {@code sounds.json}, todas em streaming). Ids estáveis — não renomear.
 */
public final class BossMusicSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IsekaiMahou.MODID);

    /** Vinheta curta tocada quando um chefe morre perto do jogador. */
    public static final String VICTORY = "boss_victory";
    /** Tema de qualquer chefe sem música própria. */
    public static final String GENERIC = "generic_boss";

    /** Temas em loop (a vinheta de vitória fica de fora: não é um tema). */
    public static final List<String> THEMES = List.of(
            "elemental_guardian", "abyss_sovereign", "seraph", "twilight_herald", "primordial_dragon", "lich", "beast",
            "mimic_king", "hollow_king", "ulquiorra", "ulquiorra_segunda_etapa", "barragan", "grimmjow", "las_noches",
            "flying_carpet",
            GENERIC);

    /**
     * Faixas OPCIONAIS por chefe (o id é o da entidade): só valem quando o Renan pôs uma música na pasta do chefe em
     * MUSICAS_DOS_CHEFES ({@link BossMusicCues#hasBossTrack}). Pasta vazia = o chefe usa o tema da família.
     */
    public static final List<String> BOSS_TRACKS = List.of(
            "ignis", "tide_guardian", "zephyr", "primordial_golem", "renegade_archmage", "corrupted_witch",
            "imprisoned_patron", "corrupted_ent", "trial_avatar", "swamp_hydra", "wyvern_king", "desert_basilisk",
            "ruin_colossus", "false_hydra", "manticore", "king_slime", "goblin_king", "orc_lord", "minotaur",
            "depths_worm_king", "star_devourer", "sand_empress", "thorn_queen", "demon_lord", "tower_kobold_lord");

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_THEME = new LinkedHashMap<>();
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> VICTORY_BY_THEME = new LinkedHashMap<>();

    static {
        for (String t : THEMES) BY_THEME.put(t, reg(t));
        for (String t : BOSS_TRACKS) BY_THEME.put(t, reg(t));
        BY_THEME.put(VICTORY, reg(VICTORY));
        // vinheta de vitória de cada tema: o FINAL da própria música (tools/music_import.py); sem arquivo = usa a padrão
        for (String t : THEMES) VICTORY_BY_THEME.put(t, reg("victory_" + t));
        for (String t : BOSS_TRACKS) VICTORY_BY_THEME.put(t, reg("victory_" + t));
    }

    /** O som da vinheta de vitória feita do final do tema (pode não ter arquivo: o cliente confere e cai na padrão). */
    @Nullable
    public static SoundEvent victoryOf(@Nullable String theme) {
        DeferredHolder<SoundEvent, SoundEvent> h = theme == null ? null : VICTORY_BY_THEME.get(theme);
        return h == null ? null : h.get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String theme) {
        String name = "boss_music." + theme;
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(IsekaiMahou.id(name)));
    }

    /** Tema tocável: um dos temas fixos, ou a faixa própria de um chefe que tem música importada. */
    public static boolean isTheme(@Nullable String theme) {
        if (theme == null || !BY_THEME.containsKey(theme) || VICTORY.equals(theme)) return false;
        return !BOSS_TRACKS.contains(theme) || BossMusicCues.hasBossTrack(theme);
    }

    /** É a faixa própria (importada) de um chefe? Ela ganha de qualquer tema de família ou de fase. */
    public static boolean isOwnBossTrack(@Nullable String theme) {
        return theme != null && BOSS_TRACKS.contains(theme) && BossMusicCues.hasBossTrack(theme);
    }

    /** O som do tema (ou da vinheta), ou null se o id não existe. */
    @Nullable
    public static SoundEvent sound(@Nullable String theme) {
        DeferredHolder<SoundEvent, SoundEvent> h = theme == null ? null : BY_THEME.get(theme);
        return h == null ? null : h.get();
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private BossMusicSounds() {}
}
