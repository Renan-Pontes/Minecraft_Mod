package com.renanpontes.isekaimahou.feature.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.automation.block.ArcaneCollectorBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * "Rede" de mana da Automação: acha baterias, coletores e pedestais por perto varrendo só os chunks JÁ carregados
 * (nunca carrega chunk). Distância = cubo (até N blocos em qualquer eixo), fácil de entender no jogo.
 */
public final class ManaNet {
    /** Alcance das baterias para pedestais e condensadores. */
    public static final int BATTERY_RANGE = 8;
    /** Alcance em que um pedestal com Recolher procura um Coletor Arcano. */
    public static final int COLLECTOR_RANGE = 16;

    /** Block entities do tipo pedido num cubo de raio {@code radius} em volta de {@code center}, do mais perto ao mais longe. */
    public static <T extends BlockEntity> List<T> scan(Level level, BlockPos center, int radius, Class<T> type) {
        List<T> out = new ArrayList<>();
        int minCx = (center.getX() - radius) >> 4, maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4, maxCz = (center.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!type.isInstance(be) || be.isRemoved()) continue;
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) > radius || Math.abs(p.getY() - center.getY()) > radius
                            || Math.abs(p.getZ() - center.getZ()) > radius) continue;
                    out.add(type.cast(be));
                }
            }
        }
        out.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));
        return out;
    }

    public static List<ManaBatteryBlockEntity> batteries(Level level, BlockPos center) {
        return scan(level, center, BATTERY_RANGE, ManaBatteryBlockEntity.class);
    }

    public static int total(List<ManaBatteryBlockEntity> batteries) {
        long sum = 0;
        for (ManaBatteryBlockEntity b : batteries) sum += b.mana();
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Tira {@code amount} de mana das baterias (das mais cheias primeiro). Só tira se der para pagar tudo. */
    public static boolean drain(List<ManaBatteryBlockEntity> batteries, int amount) {
        if (amount <= 0) return true;
        if (total(batteries) < amount) return false;
        List<ManaBatteryBlockEntity> sorted = new ArrayList<>(batteries);
        sorted.sort(Comparator.comparingInt(ManaBatteryBlockEntity::mana).reversed());
        int left = amount;
        for (ManaBatteryBlockEntity b : sorted) {
            left -= b.extract(left);
            if (left <= 0) break;
        }
        return true;
    }

    /** Devolve mana (ex.: a magia não saiu) para a bateria com mais espaço. */
    public static void refund(List<ManaBatteryBlockEntity> batteries, int amount) {
        int left = amount;
        List<ManaBatteryBlockEntity> sorted = new ArrayList<>(batteries);
        sorted.sort(Comparator.comparingInt(ManaBatteryBlockEntity::mana));
        for (ManaBatteryBlockEntity b : sorted) {
            left -= b.insert(left);
            if (left <= 0) break;
        }
    }

    @Nullable
    public static ArcaneCollectorBlockEntity nearestCollector(Level level, BlockPos center) {
        List<ArcaneCollectorBlockEntity> list = scan(level, center, COLLECTOR_RANGE, ArcaneCollectorBlockEntity.class);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Pedestais no mesmo chunk de {@code pos}, ordenados pela posição (os primeiros N são os que valem). */
    public static List<RunicPedestalBlockEntity> pedestalsInChunk(Level level, BlockPos pos) {
        List<RunicPedestalBlockEntity> out = new ArrayList<>();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return out;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof RunicPedestalBlockEntity p && !p.isRemoved()) out.add(p);
        }
        out.sort(Comparator.comparingLong(p -> p.getBlockPos().asLong()));
        return out;
    }

    private ManaNet() {}
}
