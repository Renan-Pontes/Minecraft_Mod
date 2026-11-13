package com.renanpontes.isekaimahou.feature.light_shadow.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ajuda para invocações: achar um lugar livre e limitar quantas cada dono pode ter. */
public final class SummonUtil {
    /** Chave do persistentData onde a Forja de Invocações (módulo forms_b) guarda a ordem da criatura. Lida por texto. */
    private static final String FORGE_KEY = "isekai_mahou_forge";

    /**
     * Posto da criatura quando a ordem da Forja de Invocações é Guardar (1) ou Ficar (3); null = sem posto (seguir,
     * caçar ou invocação comum, que acompanha o dono como sempre).
     */
    public static Vec3 forgePost(net.minecraft.world.entity.Entity e) {
        net.minecraft.nbt.CompoundTag pd = e.getPersistentData();
        if (!pd.contains(FORGE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
        net.minecraft.nbt.CompoundTag t = pd.getCompound(FORGE_KEY);
        int order = t.getInt("order");
        if ((order != 1 && order != 3) || !t.contains("gx")) return null;
        return new Vec3(t.getDouble("gx"), t.getDouble("gy"), t.getDouble("gz"));
    }

    /** Um ponto perto de {@code near} onde a entidade cabe (tenta subir e espalhar um pouco). */
    public static Vec3 freeSpot(ServerLevel level, EntityType<?> type, Vec3 near) {
        for (int i = 0; i < 12; i++) {
            double ox = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * 3;
            double oz = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * 3;
            for (int dy = 0; dy <= 2; dy++) {
                Vec3 p = near.add(ox, dy, oz);
                AABB box = type.getSpawnAABB(p.x, p.y, p.z);
                if (level.noCollision(box)) return p;
            }
        }
        return near;
    }

    /** Se o dono já tem {@code max} invocações deste tipo, as mais antigas se desfazem. */
    public static <T extends Mob & OwnableEntity> void limit(ServerLevel level, Class<T> cls, UUID owner, Vec3 near, int max) {
        if (owner == null) return;
        List<T> mine = level.getEntitiesOfClass(cls, new AABB(near, near).inflate(96), e -> owner.equals(e.getOwnerUUID()) && e.isAlive());
        if (mine.size() < max) return;
        mine.sort(Comparator.comparingInt((T e) -> e.tickCount).reversed());
        for (int i = 0; i <= mine.size() - max; i++) {
            T e = mine.get(i);
            LsFx.shadowPuff(level, e.position(), e.getBbHeight());
            e.discard();
        }
    }

    private SummonUtil() {}
}
