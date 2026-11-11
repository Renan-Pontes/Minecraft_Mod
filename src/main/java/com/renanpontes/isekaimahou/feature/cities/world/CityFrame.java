package com.renanpontes.isekaimahou.feature.cities.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * Onde a cidade está no mundo: centro (x,z), altura do piso das ruas (floorY = bloco do calçamento; anda-se em
 * floorY+1) e a rotação aplicada ao plano canônico (mesma convenção de StructureTemplate.transform).
 */
public record CityFrame(int centerX, int centerZ, int floorY, Rotation rotation) {

    public int worldX(int x, int z) {
        return centerX + switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    public int worldZ(int x, int z) {
        return centerZ + switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    /** Inverso de worldX/worldZ: do mundo para o plano canônico. */
    public int localX(int wx, int wz) {
        int dx = wx - centerX;
        int dz = wz - centerZ;
        return switch (rotation) {
            case NONE -> dx;
            case CLOCKWISE_90 -> dz;
            case CLOCKWISE_180 -> -dx;
            case COUNTERCLOCKWISE_90 -> -dz;
        };
    }

    public int localZ(int wx, int wz) {
        int dx = wx - centerX;
        int dz = wz - centerZ;
        return switch (rotation) {
            case NONE -> dz;
            case CLOCKWISE_90 -> -dx;
            case CLOCKWISE_180 -> -dz;
            case COUNTERCLOCKWISE_90 -> dx;
        };
    }

    public Direction toWorld(Direction canonical) {
        return rotation.rotate(canonical);
    }
}
