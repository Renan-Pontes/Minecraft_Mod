package com.renanpontes.isekaimahou.feature.city_arcana;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * O que o módulo guarda no mundo: o modo do Obelisco de Nulificação de cada cidade (id do lugar → modo). Cidade sem
 * entrada usa o padrão ({@link NullObelisk#MODE_OUTSIDERS}). Arquivo próprio — não mexe no save das cidades.
 * <p>v1.0 (city_magic): também a RECEITA gravada na Barreira Arcana e a mana do Núcleo de cada cidade ({@link WardRecipe})
 * — chave nova e opcional {@code Wards}; mundos antigos carregam sem ela.
 */
public final class CityArcanaData extends SavedData {
    private static final String NAME = "isekai_mahou_city_arcana";

    private final Map<String, Integer> modes = new HashMap<>();
    /** Cidade → {Recipe (receita), Element, By (quem gravou), Mana (mana do Núcleo)}. */
    private final Map<String, CompoundTag> wards = new HashMap<>();

    public static CityArcanaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityArcanaData::new, CityArcanaData::load, null), NAME);
    }

    public int mode(String city) {
        int m = modes.getOrDefault(city, NullObelisk.MODE_OUTSIDERS);
        return Math.max(NullObelisk.MODE_OFF, Math.min(NullObelisk.MODE_ALL, m));
    }

    public void setMode(String city, int mode) {
        modes.put(city, mode);
        setDirty();
    }

    /** Dados da Barreira da cidade (vazio se nunca gravaram nada). Só leitura — escreva pelos métodos abaixo. */
    public CompoundTag ward(String city) {
        CompoundTag t = wards.get(city);
        return t == null ? new CompoundTag() : t;
    }

    /** Grava (ou troca) a receita da Barreira. {@code recipe} = a receita já codificada (SpellRecipe.CODEC). */
    public void setWardRecipe(String city, net.minecraft.nbt.Tag recipe, String element, String by) {
        CompoundTag t = wards.computeIfAbsent(city, k -> new CompoundTag());
        t.put("Recipe", recipe);
        t.putString("Element", element);
        t.putString("By", by);
        setDirty();
    }

    public void clearWardRecipe(String city) {
        CompoundTag t = wards.get(city);
        if (t == null) return;
        t.remove("Recipe");
        t.remove("Element");
        t.remove("By");
        setDirty();
    }

    public float wardMana(String city) {
        CompoundTag t = wards.get(city);
        return t == null ? 0F : t.getFloat("Mana");
    }

    public void setWardMana(String city, float mana) {
        wards.computeIfAbsent(city, k -> new CompoundTag()).putFloat("Mana", Math.max(0F, mana));
        setDirty();
    }

    private static CityArcanaData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityArcanaData d = new CityArcanaData();
        CompoundTag m = tag.getCompound("ObeliskModes");
        for (String k : m.getAllKeys()) d.modes.put(k, m.getInt(k));
        CompoundTag w = tag.getCompound("Wards");
        for (String k : w.getAllKeys()) d.wards.put(k, w.getCompound(k).copy());
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag m = new CompoundTag();
        modes.forEach(m::putInt);
        tag.put("ObeliskModes", m);
        if (!wards.isEmpty()) {
            CompoundTag w = new CompoundTag();
            wards.forEach((k, v) -> w.put(k, v.copy()));
            tag.put("Wards", w);
        }
        return tag;
    }
}
