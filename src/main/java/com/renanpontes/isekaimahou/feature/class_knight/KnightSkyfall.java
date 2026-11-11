package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.renanpontes.isekaimahou.feature.class_knight.entity.SpectralSwordEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Espadas espectrais caindo do céu (Chuva de Espadas, Sentença do Juramento): cria a entidade visual e chama
 * {@code onLand} no tick em que ela crava no chão. Limite global para não lotar o mundo.
 */
public final class KnightSkyfall {
    private static final int CAP = 64;

    private static final class Fall {
        final SpectralSwordEntity sword;
        final Runnable onLand;

        Fall(SpectralSwordEntity sword, Runnable onLand) {
            this.sword = sword;
            this.onLand = onLand;
        }
    }

    private static final List<Fall> FALLS = new ArrayList<>();
    private static final List<Fall> PENDING = new ArrayList<>();

    /**
     * Solta uma espada de {@code from} até a altura {@code groundY}.
     * @return false se o limite global foi atingido (o chamador aplica o efeito na hora)
     */
    public static boolean drop(ServerLevel level, Vec3 from, double groundY, float speed, ItemStack sword, float scale, int rgb,
                               Runnable onLand) {
        if (FALLS.size() + PENDING.size() >= CAP) return false;
        SpectralSwordEntity e = SpectralSwordEntity.create(level, from, groundY, speed, sword, scale, rgb);
        if (!level.addFreshEntity(e)) return false;
        PENDING.add(new Fall(e, onLand));
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            FALLS.addAll(PENDING);
            PENDING.clear();
        }
        if (FALLS.isEmpty()) return;
        List<Runnable> landed = new ArrayList<>();
        Iterator<Fall> it = FALLS.iterator();
        while (it.hasNext()) {
            Fall f = it.next();
            if (f.sword.isRemoved()) {
                it.remove();
                continue;
            }
            if (f.sword.landed()) {
                it.remove();
                landed.add(f.onLand);
            }
        }
        for (Runnable r : landed) r.run();
    }

    public static void clear() {
        FALLS.clear();
        PENDING.clear();
    }

    private KnightSkyfall() {}
}
