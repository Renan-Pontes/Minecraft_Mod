package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.cities.CityLines;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

/**
 * Profissões dos mercadores da cidade (valor "Profession" do NBT dos templates). Cada uma tem facção (null = Reino),
 * peças de roupa/acessórios do modelo e cor da interface.
 */
public enum Profession {
    BLACKSMITH("blacksmith", null, 0xC7743A, Set.of("apron", "hammer")),
    ALCHEMIST("alchemist", null, 0x5FD08A, Set.of("goggles", "flask", "satchel")),
    SCRIBE("scribe", "academy", 0xB98CFF, Set.of("mortarboard", "glasses", "book", "skirt")),
    JEWELER("jeweler", null, 0x5FD8E8, Set.of("glasses", "satchel", "cape")),
    INNKEEPER("innkeeper", null, 0xE0A050, Set.of("apron", "mug")),
    PRIEST("priest", "church", 0xFFE9A8, Set.of("hood", "book", "skirt")),
    FENCE("fence", "underworld", 0x8A2B3A, Set.of("hood", "mask", "dagger")),
    GROCER("grocer", null, 0x8CC84B, Set.of("cap", "apron", "basket")),
    TAILOR("tailor", null, 0xE070A8, Set.of("cap", "satchel", "cape"));

    private static final Profession[] VALUES = values();

    public final String id;
    /** Facção da profissão; null = a da tag (Reino). */
    @Nullable public final String faction;
    public final int color;
    /** Peças opcionais do modelo mostradas para esta profissão. */
    public final Set<String> bones;

    Profession(String id, @Nullable String faction, int color, Set<String> bones) {
        this.id = id;
        this.faction = faction;
        this.color = color;
        this.bones = bones;
    }

    /** Facção efetiva (Reino quando a profissão não tem uma própria). */
    public String factionOrKingdom() {
        return faction != null ? faction : "kingdom";
    }

    public MutableComponent displayName() {
        return CityLines.tr("profession.isekai_mahou.cities." + id);
    }

    public MutableComponent displayName(boolean female) {
        return CityLines.tr("profession.isekai_mahou.cities." + id + (female ? ".f" : ""));
    }

    @Nullable
    public static Profession byId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (Profession p : VALUES) if (p.id.equals(s)) return p;
        return null;
    }

    public static Profession random(RandomSource r) {
        return VALUES[r.nextInt(VALUES.length)];
    }
}
