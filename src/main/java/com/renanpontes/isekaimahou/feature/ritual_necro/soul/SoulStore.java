package com.renanpontes.isekaimahou.feature.ritual_necro.soul;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Almas guardadas num Filactério: tipo de mob (id da entidade) → quantidade.
 * Valor imutável de componente de item (equals/hashCode do record cuidam da comparação de stacks).
 */
public record SoulStore(Map<ResourceLocation, Integer> souls) {
    /** Limite por tipo de alma (evita acumular centenas de almas de galinha). */
    public static final int MAX_PER_TYPE = 99;
    public static final SoulStore EMPTY = new SoulStore(Map.of());

    public static final Codec<SoulStore> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
            .xmap(SoulStore::new, SoulStore::souls);
    public static final StreamCodec<ByteBuf, SoulStore> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, ResourceLocation, Integer, Map<ResourceLocation, Integer>>map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT)
            .map(SoulStore::new, SoulStore::souls);

    public SoulStore {
        Map<ResourceLocation, Integer> clean = new HashMap<>();
        souls.forEach((id, n) -> {
            if (n != null && n > 0) clean.put(id, Math.min(MAX_PER_TYPE, n));
        });
        souls = Map.copyOf(clean);
    }

    /** Almas do Filactério (vazio se o componente não existir). */
    public static SoulStore of(ItemStack stack) {
        return stack.getOrDefault(NecroContent.SOULS.get(), EMPTY);
    }

    public int count(ResourceLocation type) {
        return souls.getOrDefault(type, 0);
    }

    public int total() {
        int t = 0;
        for (int n : souls.values()) t += n;
        return t;
    }

    public boolean isEmpty() {
        return souls.isEmpty();
    }

    public boolean isFull(ResourceLocation type) {
        return count(type) >= MAX_PER_TYPE;
    }

    public SoulStore add(ResourceLocation type, int amount) {
        Map<ResourceLocation, Integer> m = new HashMap<>(souls);
        m.merge(type, amount, Integer::sum);
        return new SoulStore(m);
    }

    /** true se tiver pelo menos as quantidades pedidas. */
    public boolean has(Map<ResourceLocation, Integer> cost) {
        for (Map.Entry<ResourceLocation, Integer> e : cost.entrySet()) {
            if (count(e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    /** Remove as quantidades pedidas (o que faltar vira zero). */
    public SoulStore remove(Map<ResourceLocation, Integer> cost) {
        Map<ResourceLocation, Integer> m = new HashMap<>(souls);
        cost.forEach((id, n) -> m.computeIfPresent(id, (k, v) -> v - n));
        return new SoulStore(m);
    }

    /** Entradas ordenadas: mais almas primeiro, depois por id (ordem estável na dica). */
    public List<Map.Entry<ResourceLocation, Integer>> sorted() {
        List<Map.Entry<ResourceLocation, Integer>> list = new ArrayList<>(souls.entrySet());
        list.sort(Map.Entry.<ResourceLocation, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        return list;
    }

    /** Estágio de brilho do Filactério (0 = apagado .. 3 = transbordando). */
    public int stage() {
        int t = total();
        if (t <= 0) return 0;
        if (t < 10) return 1;
        if (t < 30) return 2;
        return 3;
    }
}
