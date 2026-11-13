package com.renanpontes.isekaimahou.feature.vehicles;

import javax.annotation.Nullable;

/**
 * Onda 10: MELHORIAS dos veículos, compradas no Mecânico e aplicadas clicando no veículo com a peça (só o dono).
 * Três peças, cada uma até o nível {@link #MAX}; guardadas num inteiro só (4 bits por peça) na entidade e no item:
 * <ul>
 *   <li><b>Motor Rúnico</b> ({@link Part#ENGINE}): +10% de velocidade máxima e de aceleração por nível;</li>
 *   <li><b>Blindagem Arcana</b> ({@link Part#ARMOR}): +40% de vida e −15% do dano recebido por nível;</li>
 *   <li><b>Porão Expandido</b> ({@link Part#STORAGE}): +9 espaços de carga por nível (veículo sem baú ganha um).</li>
 * </ul>
 */
public final class VehicleUpgrades {
    public static final int MAX = 3;

    /** As peças. O id é salvo no item ({@code vehicle_part_<id>}): só ACRESCENTE. */
    public enum Part {
        ENGINE("engine", 0),
        ARMOR("armor", 4),
        STORAGE("storage", 8);

        public final String id;
        final int shift;

        Part(String id, int shift) {
            this.id = id;
            this.shift = shift;
        }

        public String itemId() {
            return "vehicle_part_" + id;
        }

        @Nullable
        public static Part byId(String id) {
            for (Part p : values()) if (p.id.equals(id)) return p;
            return null;
        }
    }

    public static int level(int packed, Part part) {
        return Math.min(MAX, (packed >> part.shift) & 0xF);
    }

    public static int with(int packed, Part part, int level) {
        int lv = Math.max(0, Math.min(MAX, level));
        return (packed & ~(0xF << part.shift)) | (lv << part.shift);
    }

    public static float speedMult(int packed) {
        return 1F + 0.1F * level(packed, Part.ENGINE);
    }

    public static float healthMult(int packed) {
        return 1F + 0.4F * level(packed, Part.ARMOR);
    }

    public static float damageMult(int packed) {
        return 1F - 0.15F * level(packed, Part.ARMOR);
    }

    public static int total(int packed) {
        int n = 0;
        for (Part p : Part.values()) n += level(packed, p);
        return n;
    }

    private VehicleUpgrades() {}
}
