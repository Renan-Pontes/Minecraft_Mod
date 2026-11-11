package com.renanpontes.isekaimahou.feature.automation.block;

import net.minecraft.world.phys.Vec3;

/** Para onde o Pedestal Rúnico conjura (direção da magia). Guinada/arfagem no padrão do Minecraft (0 = sul). */
public enum PedestalDirection {
    DOWN("down", new Vec3(0, -1, 0), 0F, 90F),
    NORTH("north", new Vec3(0, 0, -1), 180F, 0F),
    SOUTH("south", new Vec3(0, 0, 1), 0F, 0F),
    EAST("east", new Vec3(1, 0, 0), -90F, 0F),
    WEST("west", new Vec3(-1, 0, 0), 90F, 0F),
    UP("up", new Vec3(0, 1, 0), 0F, -90F);

    public final String id;
    public final Vec3 dir;
    public final float yaw;
    public final float pitch;

    PedestalDirection(String id, Vec3 dir, float yaw, float pitch) {
        this.id = id;
        this.dir = dir;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String key() {
        return "gui.isekai_mahou.pedestal.direction." + id;
    }

    public PedestalDirection cycle(int step) {
        PedestalDirection[] v = values();
        return v[Math.floorMod(ordinal() + step, v.length)];
    }

    public static PedestalDirection byIndex(int i) {
        PedestalDirection[] v = values();
        return i >= 0 && i < v.length ? v[i] : DOWN;
    }
}
