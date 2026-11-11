package com.renanpontes.isekaimahou.feature.structures_b.world;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * As estruturas deste módulo (worldgen em data/isekai_mahou/worldgen/structure/&lt;id&gt;.json).
 * {@code classStructure}: estrutura de classe com minichefe (rara, mais XP na descoberta).
 * Tradução: "structure.isekai_mahou.&lt;id&gt;" (nome) e "message.isekai_mahou.structure_hint.&lt;id&gt;" (dica).
 */
public enum ModStructure {
    MAGE_TOWER("mage_tower", true, 0x8E6BFF, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F),
    WITCH_HUT_ANCIENT("witch_hut_ancient", true, 0x6FBF4A, SoundEvents.WITCH_AMBIENT, 0.7F),
    PROFANE_ALTAR("profane_altar", true, 0xC0303A, SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F),
    DRUID_CIRCLE("druid_circle", true, 0x4FB86A, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F),
    FORGOTTEN_CRYPT("forgotten_crypt", true, 0x5FC8D8, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F),
    ARCANE_RUINS("arcane_ruins", false, 0xB48CFF, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F),
    WANDERING_MAGE_CAMP("wandering_mage_camp", false, 0xE0A040, SoundEvents.CAMPFIRE_CRACKLE, 1.0F),
    FAIRY_LAKE("fairy_lake", false, 0xFFB6F0, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.1F);

    public final String path;
    public final boolean classStructure;
    public final int rgb;
    public final SoundEvent sound;
    public final float pitch;

    ModStructure(String path, boolean classStructure, int rgb, SoundEvent sound, float pitch) {
        this.path = path;
        this.classStructure = classStructure;
        this.rgb = rgb;
        this.sound = sound;
        this.pitch = pitch;
    }

    private static final String NAME_KEY = "structure.isekai_mahou.";
    private static final String HINT_KEY = "message.isekai_mahou.structure_hint.";

    public MutableComponent displayName() {
        return Component.translatable(NAME_KEY + path);
    }

    public MutableComponent hint() {
        return Component.translatable(HINT_KEY + path);
    }

    /** XP de nível na primeira visita a este TIPO de estrutura / em cada nova instância depois. */
    public int firstXp() {
        return classStructure ? 40 : 15;
    }

    public int repeatXp() {
        return classStructure ? 10 : 3;
    }

    @Nullable
    public static ModStructure byId(@Nullable ResourceLocation id) {
        if (id == null || !id.getNamespace().equals(IsekaiMahou.MODID)) return null;
        for (ModStructure s : values()) if (s.path.equals(id.getPath())) return s;
        return null;
    }
}
