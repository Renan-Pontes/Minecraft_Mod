package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Leitura/escrita dos dados de lore de um jogador — um {@link CompoundTag} guardado num attachment próprio
 * ({@link LoreContent#PLAYER_DATA}, sobrevive à morte) e copiado inteiro para o cliente (é pequeno). Formato:
 * <pre>
 * frags:  { &lt;id do fragmento&gt;: { st: 0 sem lugar | 1 lugar revelado | 2 visitado, dim, x, y, z, ey (altura certa) } }
 * steles: [posições (long) das Estelas já lidas]
 * track:  id do fragmento seguido no rastreador ("" = nenhum)
 * </pre>
 * Os mesmos métodos servem no servidor (dados reais) e no cliente (cópia). Tudo opcional: jogador antigo = vazio.
 */
public final class LoreData {
    public static final int NO_PLACE = 0, REVEALED = 1, VISITED = 2;
    /** Teto de Estelas lembradas por jogador (as mais antigas saem). */
    private static final int MAX_STELES = 256;

    /** Lugar revelado por um fragmento. */
    public record Spot(int state, ResourceKey<Level> dim, BlockPos pos, boolean exactY) {}

    private static CompoundTag frags(CompoundTag root) {
        return root.getCompound("frags");
    }

    public static boolean has(CompoundTag root, String id) {
        return frags(root).contains(id, Tag.TAG_COMPOUND);
    }

    public static int count(CompoundTag root) {
        int n = 0;
        CompoundTag f = frags(root);
        for (LoreFragments.Fragment frag : LoreFragments.all()) if (f.contains(frag.id(), Tag.TAG_COMPOUND)) n++;
        return n;
    }

    public static int countInSet(CompoundTag root, LoreFragments.LoreSet set) {
        int n = 0;
        CompoundTag f = frags(root);
        for (LoreFragments.Fragment frag : set.fragments()) if (f.contains(frag.id(), Tag.TAG_COMPOUND)) n++;
        return n;
    }

    public static boolean setComplete(CompoundTag root, LoreFragments.LoreSet set) {
        return countInSet(root, set) >= set.fragments().size();
    }

    /** A entrada da Enciclopédia está aberta? (só as Verdades Ocultas fecham.) */
    public static boolean entryOpen(CompoundTag root, LoreBook.Entry entry) {
        if (!entry.secret()) return true;
        LoreFragments.LoreSet set = LoreFragments.setOfTruth(entry.id());
        return set == null || setComplete(root, set);
    }

    /** Lugar revelado pelo fragmento (null = não tem o fragmento ou ele não revelou nada). */
    @Nullable
    public static Spot spot(CompoundTag root, String id) {
        if (!has(root, id)) return null;
        CompoundTag t = frags(root).getCompound(id);
        int st = t.getByte("st");
        if (st == NO_PLACE) return null;
        ResourceKey<Level> dim = LoreFragments.dim(t.getString("dim"));
        if (dim == null) return null;
        return new Spot(st, dim, new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")), t.getBoolean("ey"));
    }

    public static String tracked(CompoundTag root) {
        return root.getString("track");
    }

    // ------------------------------------------------------------------ escrita (servidor)
    static void add(CompoundTag root, String id) {
        CompoundTag f = frags(root);
        if (!f.contains(id, Tag.TAG_COMPOUND)) f.put(id, new CompoundTag());
        root.put("frags", f);
    }

    static void setSpot(CompoundTag root, String id, int state, ResourceKey<Level> dim, BlockPos pos, boolean exactY) {
        CompoundTag f = frags(root);
        CompoundTag t = f.getCompound(id);
        t.putByte("st", (byte) state);
        t.putString("dim", dim.location().toString());
        t.putInt("x", pos.getX());
        t.putInt("y", pos.getY());
        t.putInt("z", pos.getZ());
        t.putBoolean("ey", exactY);
        f.put(id, t);
        root.put("frags", f);
    }

    static void clearSpot(CompoundTag root, String id) {
        CompoundTag f = frags(root);
        if (!f.contains(id, Tag.TAG_COMPOUND)) return;
        f.put(id, new CompoundTag());
        root.put("frags", f);
    }

    static void setState(CompoundTag root, String id, int state) {
        CompoundTag f = frags(root);
        if (!f.contains(id, Tag.TAG_COMPOUND)) return;
        CompoundTag t = f.getCompound(id);
        t.putByte("st", (byte) state);
        f.put(id, t);
        root.put("frags", f);
    }

    static void setTracked(CompoundTag root, String id) {
        if (id == null || id.isEmpty()) root.remove("track");
        else root.putString("track", id);
    }

    static boolean steleRead(CompoundTag root, BlockPos pos) {
        long key = pos.asLong();
        for (long l : root.getLongArray("steles")) if (l == key) return true;
        return false;
    }

    static void addStele(CompoundTag root, BlockPos pos) {
        List<Long> list = new ArrayList<>();
        for (long l : root.getLongArray("steles")) list.add(l);
        list.add(pos.asLong());
        while (list.size() > MAX_STELES) list.remove(0);
        root.putLongArray("steles", list);
    }

    static int steleCount(CompoundTag root) {
        return root.getLongArray("steles").length;
    }

    private LoreData() {}
}
