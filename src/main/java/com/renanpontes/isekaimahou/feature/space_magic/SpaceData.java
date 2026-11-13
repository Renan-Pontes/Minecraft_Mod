package com.renanpontes.isekaimahou.feature.space_magic;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Leitura/escrita dos dados do jogador ({@link SpaceContent#PLAYER_DATA}, um CompoundTag que sobrevive à morte):
 * <ul>
 *   <li>{@code box}: os itens do Inventário Dimensional (lista com o índice do espaço);</li>
 *   <li>{@code mark}: a Marca de Retorno (dimensão, posição, rotação);</li>
 *   <li>{@code last}: o último chão firme visto em cada dimensão (chegada do Abrir Portal quando não há hub).</li>
 * </ul>
 */
public final class SpaceData {
    /** Tamanho máximo do Item Box (6 fileiras, Círculo 9). */
    public static final int BOX_MAX = 54;

    public static CompoundTag tag(Player p) {
        return p.getData(SpaceContent.PLAYER_DATA);
    }

    // ------------------------------------------------------------------ Inventário Dimensional
    /** Fileiras do Item Box pelo Círculo: 1 fileira (9 espaços) no C1 até 6 fileiras (54) no C9. */
    public static int boxRows(int circle) {
        return Mth.clamp(1 + (Math.max(1, circle) - 1) * 5 / 8, 1, 6);
    }

    public static int boxSlots(int circle) {
        return boxRows(circle) * 9;
    }

    /** Todos os 54 espaços guardados (os que passam do tamanho atual continuam guardados, só ficam ocultos). */
    public static NonNullList<ItemStack> loadBox(Player p, HolderLookup.Provider regs) {
        NonNullList<ItemStack> items = NonNullList.withSize(BOX_MAX, ItemStack.EMPTY);
        ListTag list = tag(p).getList("box", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int slot = e.getInt("Slot");
            if (slot < 0 || slot >= BOX_MAX) continue;
            items.set(slot, ItemStack.parseOptional(regs, e.getCompound("Item")));
        }
        return items;
    }

    public static void saveBox(Player p, NonNullList<ItemStack> items, HolderLookup.Provider regs) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size() && i < BOX_MAX; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            e.put("Item", s.save(regs));
            list.add(e);
        }
        tag(p).put("box", list);
    }

    /** Quantos espaços ocupados (para o comando de teste e a mensagem de abertura). */
    public static int boxUsed(Player p) {
        return tag(p).getList("box", Tag.TAG_COMPOUND).size();
    }

    // ------------------------------------------------------------------ Marca de Retorno
    public record Mark(ResourceKey<Level> dim, Vec3 pos, float yaw) {}

    @Nullable
    public static Mark mark(Player p) {
        CompoundTag t = tag(p);
        if (!t.contains("mark", Tag.TAG_COMPOUND)) return null;
        CompoundTag m = t.getCompound("mark");
        ResourceLocation dim = ResourceLocation.tryParse(m.getString("dim"));
        if (dim == null) return null;
        return new Mark(ResourceKey.create(Registries.DIMENSION, dim), new Vec3(m.getDouble("x"), m.getDouble("y"), m.getDouble("z")),
                m.getFloat("yaw"));
    }

    public static void setMark(Player p, @Nullable Mark mark) {
        CompoundTag t = tag(p);
        if (mark == null) {
            t.remove("mark");
            return;
        }
        CompoundTag m = new CompoundTag();
        m.putString("dim", mark.dim().location().toString());
        m.putDouble("x", mark.pos().x);
        m.putDouble("y", mark.pos().y);
        m.putDouble("z", mark.pos().z);
        m.putFloat("yaw", mark.yaw());
        t.put("mark", m);
    }

    // ------------------------------------------------------------------ último chão firme por dimensão
    @Nullable
    public static BlockPos lastPos(Player p, ResourceKey<Level> dim) {
        CompoundTag last = tag(p).getCompound("last");
        String k = dim.location().toString();
        return last.contains(k, Tag.TAG_LONG) ? BlockPos.of(last.getLong(k)) : null;
    }

    public static void setLastPos(Player p, ResourceKey<Level> dim, BlockPos pos) {
        CompoundTag t = tag(p);
        CompoundTag last = t.getCompound("last");
        last.putLong(dim.location().toString(), pos.asLong());
        t.put("last", last);
    }

    private SpaceData() {}
}
