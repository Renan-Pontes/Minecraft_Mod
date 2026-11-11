package com.renanpontes.isekaimahou.feature.farm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Ligação com as Baterias de Mana do módulo Automação SEM depender da classe dele: acha as block entities pelo id
 * {@code isekai_mahou:mana_battery} e chama os métodos públicos {@code mana()}, {@code extract(int)} e {@code insert(int)}
 * por reflexão (guardada por classe). Sem o módulo (ou se a assinatura mudar) tudo devolve 0 — nada quebra.
 */
public final class ManaLink {
    public static final ResourceLocation BATTERY = IsekaiMahou.id("mana_battery");
    /** Alcance (cubo) em que as fazendas e os canteiros enxergam baterias — o mesmo da Automação. */
    public static final int RANGE = 8;

    private record Api(Method mana, Method extract, Method insert) {}

    private static final Map<Class<?>, Api> APIS = new ConcurrentHashMap<>();
    private static final Api NONE = new Api(null, null, null);
    private static boolean warned;

    public static boolean isBattery(BlockEntity be) {
        return BATTERY.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
    }

    /** Baterias carregadas num cubo de raio {@link #RANGE}, da mais perto para a mais longe (nunca carrega chunk). */
    public static List<BlockEntity> batteries(Level level, BlockPos center) {
        List<BlockEntity> out = new ArrayList<>();
        int r = RANGE;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || !isBattery(be)) continue;
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) > r || Math.abs(p.getY() - center.getY()) > r
                            || Math.abs(p.getZ() - center.getZ()) > r) continue;
                    out.add(be);
                }
            }
        }
        out.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));
        return out;
    }

    public static int mana(BlockEntity be) {
        Api api = api(be);
        if (api.mana == null) return 0;
        try {
            return (Integer) api.mana.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    public static int total(List<BlockEntity> batteries) {
        long sum = 0;
        for (BlockEntity b : batteries) sum += mana(b);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Tira até {@code amount} das baterias (das mais cheias primeiro); devolve quanto saiu. */
    public static int drain(List<BlockEntity> batteries, int amount) {
        if (amount <= 0 || batteries.isEmpty()) return 0;
        List<BlockEntity> sorted = new ArrayList<>(batteries);
        sorted.sort(Comparator.comparingInt(ManaLink::mana).reversed());
        int got = 0;
        for (BlockEntity b : sorted) {
            Api api = api(b);
            if (api.extract == null) continue;
            try {
                got += (Integer) api.extract.invoke(b, amount - got);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            if (got >= amount) break;
        }
        return got;
    }

    /** Põe mana de volta (ex.: sobrou); devolve quanto coube. */
    public static int refund(List<BlockEntity> batteries, int amount) {
        int left = amount;
        for (BlockEntity b : batteries) {
            Api api = api(b);
            if (api.insert == null) continue;
            try {
                left -= (Integer) api.insert.invoke(b, left);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            if (left <= 0) break;
        }
        return amount - Math.max(0, left);
    }

    private static Api api(BlockEntity be) {
        return APIS.computeIfAbsent(be.getClass(), c -> {
            try {
                return new Api(c.getMethod("mana"), c.getMethod("extract", int.class), c.getMethod("insert", int.class));
            } catch (NoSuchMethodException e) {
                if (!warned) {
                    warned = true;
                    IsekaiMahou.LOGGER.warn("Fazendas: a Bateria de Mana ({}) não tem a API esperada; ignorando baterias", c.getName());
                }
                return NONE;
            }
        });
    }

    private ManaLink() {}
}
