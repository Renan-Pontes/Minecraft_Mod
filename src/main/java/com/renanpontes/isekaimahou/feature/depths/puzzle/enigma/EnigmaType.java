package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import java.util.Locale;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

/** Os oito Enigmas de raciocínio (v1.0 r9). Todos têm níveis 1 a 3. */
public enum EnigmaType {
    LIGHTS(LightsEnigma::new),
    PATTERN(PatternEnigma::new),
    PATH(PathEnigma::new),
    CRATES(CratesEnigma::new),
    STATUES(StatuesEnigma::new),
    CIPHER(CipherEnigma::new),
    BINARY(BinaryEnigma::new),
    BEAM(BeamEnigma::new);

    private final BiFunction<Integer, Long, Enigma> factory;

    EnigmaType(BiFunction<Integer, Long, Enigma> factory) {
        this.factory = factory;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Enigma create(int tier, long seed) {
        return factory.apply(Math.max(1, Math.min(3, tier)), seed);
    }

    @Nullable
    public static EnigmaType byId(String id) {
        for (EnigmaType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
