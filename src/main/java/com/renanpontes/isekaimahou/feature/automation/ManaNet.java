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

    // =====================================================================================
    //  v1.5: a "bolsa" de um pedestal = Núcleo de Mana ligado (com a perda da distância) + baterias por perto
    // =====================================================================================

    /** Núcleo ligado ao pedestal em {@code pedestal}, se der para usar agora (chunk carregado, no alcance). */
    @Nullable
    private static WirelessMana linkedCore(Level level, BlockPos pedestal) {
        if (!(level.getBlockEntity(pedestal) instanceof RunicPedestalBlockEntity p)) return null;
        BlockPos core = p.corePos();
        if (core == null || !WirelessMana.inRange(pedestal, core)) return null;
        return WirelessMana.at(level, core);
    }

    /** Quanto mana o pedestal consegue pagar agora (núcleo já descontada a perda + baterias a até 8 blocos). */
    public static int availableFor(Level level, BlockPos pedestal) {
        long sum = total(batteries(level, pedestal));
        WirelessMana core = linkedCore(level, pedestal);
        if (core != null) {
            int loss = WirelessMana.lossPercent(pedestal, core instanceof BlockEntity be ? be.getBlockPos() : pedestal);
            sum += (long) core.mana() * 100 / (100 + loss);
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * Cobra {@code amount} da mesma bolsa que o pedestal usa para conjurar: primeiro o Núcleo ligado (com a perda), o
     * resto das baterias. Só cobra se der para pagar tudo.
     */
    public static boolean drainFor(Level level, BlockPos pedestal, int amount) {
        if (amount <= 0) return true;
        List<ManaBatteryBlockEntity> list = batteries(level, pedestal);
        WirelessMana core = linkedCore(level, pedestal);
        int covered = 0, coreCost = 0;
        if (core != null) {
            int loss = WirelessMana.lossPercent(pedestal, core instanceof BlockEntity be ? be.getBlockPos() : pedestal);
            int coreCan = (int) Math.min(Integer.MAX_VALUE, (long) core.mana() * 100 / (100 + loss));
            covered = Math.min(amount, coreCan);
            coreCost = Math.min(core.mana(), WirelessMana.withLoss(covered, loss));
        }
        if ((long) covered + total(list) < amount) return false;
        if (covered < amount && !drain(list, amount - covered)) return false;
        if (coreCost > 0) {
            core.extract(coreCost);
            core.noteUse(pedestal, coreCost);
        }
        return true;
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
