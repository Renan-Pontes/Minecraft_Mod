package com.renanpontes.isekaimahou.feature.ambience;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons do módulo, todos gerados por código ({@code tools/gen/ambience_sfx.py} e {@code tools/gen/ambience_music.py}):
 * <ul>
 * <li>{@code ambience.spell.<família>.<cast_s|cast_m|cast_l|impact_s|impact_l>} — conjuração/impacto por família de
 * elemento e tamanho (mono, posicional, alcance fixo por tamanho);</li>
 * <li>{@code ambience.music.<zona>} — faixas ambientes em streaming.</li>
 * </ul>
 * Ids estáveis — não renomear.
 */
public final class AmbienceSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IsekaiMahou.MODID);

    /** Tipo de som de magia. */
    public static final byte KIND_CAST = 0;
    public static final byte KIND_IMPACT = 1;

    /** Famílias de timbre: os 21 elementos caem numa delas (as fusões mudam só a altura do som). */
    public enum Family {
        FIRE, WATER, EARTH, WIND, LIGHT, SHADOW, LIGHTNING, ICE, NATURE, ARCANE;

        public final String id = name().toLowerCase(Locale.ROOT);

        public static Family byIndex(int i) {
            Family[] v = values();
            return v[Math.floorMod(i, v.length)];
        }

        @Nullable
        public static Family byId(String id) {
            for (Family f : values()) if (f.id.equals(id)) return f;
            return null;
        }
    }

    /** Zonas com música própria (o id vai no pacote e no nome do som). */
    public static final List<String> ZONES = List.of("dungeon", "citadel", "library", "tower", "city", "abyss", "home");

    private static final String[] CAST_KEYS = {"cast_s", "cast_m", "cast_l"};
    private static final String[] IMPACT_KEYS = {"impact_s", "impact_l"};
    /** Alcance (blocos) em que cada tamanho é ouvido. */
    private static final float[] CAST_RANGE = {20F, 32F, 56F};
    private static final float[] IMPACT_RANGE = {24F, 48F};

    private static final Map<Family, List<DeferredHolder<SoundEvent, SoundEvent>>> CAST = new EnumMap<>(Family.class);
    private static final Map<Family, List<DeferredHolder<SoundEvent, SoundEvent>>> IMPACT = new EnumMap<>(Family.class);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> MUSIC = new LinkedHashMap<>();

    static {
        for (Family f : Family.values()) {
            CAST.put(f, group(f, CAST_KEYS, CAST_RANGE));
            IMPACT.put(f, group(f, IMPACT_KEYS, IMPACT_RANGE));
        }
        for (String z : ZONES) {
            String name = "ambience.music." + z;
            MUSIC.put(z, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(IsekaiMahou.id(name))));
        }
    }

    private static List<DeferredHolder<SoundEvent, SoundEvent>> group(Family f, String[] keys, float[] range) {
        List<DeferredHolder<SoundEvent, SoundEvent>> out = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            String name = "ambience.spell." + f.id + "." + keys[i];
            float r = range[i];
            out.add(SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(IsekaiMahou.id(name), r)));
        }
        return List.copyOf(out);
    }

    /** Som de conjuração (size 0..2) ou de impacto (size 0 = pequeno, 1+ = grande). */
    public static SoundEvent spell(byte kind, Family family, int size) {
        if (kind == KIND_IMPACT) return IMPACT.get(family).get(size <= 0 ? 0 : 1).get();
        return CAST.get(family).get(Math.max(0, Math.min(2, size))).get();
    }

    /** A faixa da zona, ou null se a zona não tem música. */
    @Nullable
    public static SoundEvent music(@Nullable String zone) {
        DeferredHolder<SoundEvent, SoundEvent> h = zone == null ? null : MUSIC.get(zone);
        return h == null ? null : h.get();
    }

    public static boolean isZone(@Nullable String zone) {
        return zone != null && MUSIC.containsKey(zone);
    }

    /** Família de timbre do elemento. */
    public static Family familyOf(@Nullable Element e) {
        if (e == null) return Family.ARCANE;
        return switch (e) {
            case FIRE, MAGMA, SUN, HELLFIRE -> Family.FIRE;
            case WATER, STEAM, PURITY, ABYSS -> Family.WATER;
            case EARTH, GRAVITY -> Family.EARTH;
            case WIND, SAND -> Family.WIND;
            case LIGHT, ASTRAL -> Family.LIGHT;
            case SHADOW, VOID -> Family.SHADOW;
            case LIGHTNING -> Family.LIGHTNING;
            case ICE, CRYSTAL -> Family.ICE;
            case NATURE -> Family.NATURE;
            default -> Family.ARCANE;
        };
    }

    /** Altura do som: as fusões soam como "parentes" do elemento base (mais graves ou mais agudas). */
    public static float pitchOf(@Nullable Element e) {
        if (e == null) return 1F;
        return switch (e) {
            case MAGMA -> 0.84F;
            case SUN -> 1.12F;
            case HELLFIRE -> 0.76F;
            case STEAM -> 1.18F;
            case PURITY -> 1.26F;
            case ABYSS -> 0.72F;
            case GRAVITY -> 0.74F;
            case SAND -> 0.86F;
            case ASTRAL -> 0.9F;
            case VOID -> 0.8F;
            case CRYSTAL -> 0.86F;
            case TWILIGHT -> 0.85F;
            default -> 1F;
        };
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private AmbienceSounds() {}
}
