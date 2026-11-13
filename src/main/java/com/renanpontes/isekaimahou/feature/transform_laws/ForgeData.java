package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.LinkedHashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Receitas de transformação escolhidas pelo jogador, uma por ESPAÇO ({@link TransformForge.Slot}: forma racial,
 * Resurrección, Bankai...). Attachment próprio do módulo (sobrevive à morte); o cliente recebe uma cópia
 * ({@link ForgeNetwork.SyncRecipes}) para a aba Votos. Jogador sem nada salvo = nenhuma receita (igual a antes).
 */
public final class ForgeData implements INBTSerializable<CompoundTag> {
    static final int MAX_SLOTS = 32;

    private final Map<String, SpellRecipe> recipes = new LinkedHashMap<>();

    /**
     * Onda 5 — sessão guardada na saída do jogo para voltar no próximo login (ver {@code ForgeSessions.login}).
     * Fica na chave {@value #RESUME_KEY} do mesmo attachment (ids de espaço são {@code a-z0-9_}: nunca colide).
     */
    record Resume(String source, SpellRecipe recipe, String probe, boolean ownsProbe) {}

    static final String RESUME_KEY = "#resume";
    private final java.util.List<Resume> resume = new java.util.ArrayList<>();

    static void saveResume(ServerPlayer player, java.util.List<Resume> list) {
        ForgeData d = player.getData(TlawContent.FORGE_DATA.get());
        d.resume.clear();
        d.resume.addAll(list);
    }

    /** Devolve e esvazia as sessões guardadas. */
    static java.util.List<Resume> takeResume(ServerPlayer player) {
        ForgeData d = player.getData(TlawContent.FORGE_DATA.get());
        java.util.List<Resume> out = java.util.List.copyOf(d.resume);
        d.resume.clear();
        return out;
    }

    /** Cliente: espelho das receitas do jogador local. */
    private static volatile Map<String, SpellRecipe> client = Map.of();

    static void setClient(Map<String, SpellRecipe> map) {
        client = Map.copyOf(map);
    }

    /** Receita do espaço ({@link SpellRecipe#EMPTY} = nenhuma). Servidor: do attachment; cliente: do espelho. */
    static SpellRecipe recipe(Player player, String slot) {
        if (player.level().isClientSide) return client.getOrDefault(slot, SpellRecipe.EMPTY);
        return player.getData(TlawContent.FORGE_DATA.get()).recipes.getOrDefault(slot, SpellRecipe.EMPTY);
    }

    /** Servidor: grava (ou tira, com receita vazia) e manda a cópia ao cliente. */
    static void set(ServerPlayer player, String slot, SpellRecipe recipe) {
        ForgeData d = player.getData(TlawContent.FORGE_DATA.get());
        if (recipe == null || recipe.isEmpty()) d.recipes.remove(slot);
        else if (d.recipes.containsKey(slot) || d.recipes.size() < MAX_SLOTS) d.recipes.put(slot, recipe);
        sync(player);
    }

    static void sync(ServerPlayer player) {
        ForgeData d = player.getData(TlawContent.FORGE_DATA.get());
        PacketDistributor.sendToPlayer(player, new ForgeNetwork.SyncRecipes(new LinkedHashMap<>(d.recipes)));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, SpellRecipe> e : recipes.entrySet()) {
            SpellRecipe.CODEC.encodeStart(NbtOps.INSTANCE, e.getValue()).result().ifPresent(tag -> t.put(e.getKey(), tag));
        }
        if (!resume.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Resume r : resume) {
                CompoundTag one = new CompoundTag();
                one.putString("source", r.source());
                one.putString("probe", r.probe());
                one.putBoolean("own", r.ownsProbe());
                SpellRecipe.CODEC.encodeStart(NbtOps.INSTANCE, r.recipe()).result().ifPresent(tag -> one.put("recipe", tag));
                list.add(one);
            }
            t.put(RESUME_KEY, list);
        }
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        recipes.clear();
        resume.clear();
        if (t.contains(RESUME_KEY, Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = t.getList(RESUME_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < 8; i++) {
                CompoundTag one = list.getCompound(i);
                Tag rt = one.get("recipe");
                if (rt == null) continue;
                SpellRecipe.CODEC.parse(NbtOps.INSTANCE, rt).result().ifPresent(r ->
                        resume.add(new Resume(one.getString("source"), r, one.getString("probe"), one.getBoolean("own"))));
            }
        }
        for (String key : t.getAllKeys()) {
            if (RESUME_KEY.equals(key)) continue;
            Tag tag = t.get(key);
            if (tag == null || recipes.size() >= MAX_SLOTS) continue;
            SpellRecipe.CODEC.parse(NbtOps.INSTANCE, tag).result().ifPresent(r -> {
                if (!r.isEmpty()) recipes.put(key, r);
            });
        }
    }
}
