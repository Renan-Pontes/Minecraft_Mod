package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Dados salvos da Dimensão Casa (guardados no Overworld, arquivo {@code isekai_mahou_home_realm}):
 * <ul>
 *   <li><b>lares</b>: um por jogador que usou a Chave do Lar — número do terreno, se a ilha já foi construída, quem foi
 *       convidado e se os convidados podem mexer nas coisas;</li>
 *   <li><b>retornos</b>: de onde cada jogador entrou, para "voltar para onde estava" (mesmo depois de reiniciar).</li>
 * </ul>
 * Tudo opcional na leitura: mundos sem o arquivo começam vazios.
 */
public class HomeData extends SavedData {
    private static final String NAME = "isekai_mahou_home_realm";

    /** O lar de um jogador. */
    public static final class Home {
        public final UUID owner;
        public final int index;
        public String ownerName = "";
        /** A ilha inicial já foi construída (nunca é refeita: o que está lá é do jogador). */
        public boolean built;
        /** Convidados podem quebrar/colocar blocos e abrir baús. */
        public boolean guestsBuild;
        /** Convidados (uuid → último nome conhecido), na ordem do convite. */
        public final Map<UUID, String> invited = new LinkedHashMap<>();
        /** Ponto de chegada escolhido pelo dono (null = o círculo de pedra na frente da casa). */
        @Nullable public Vec3 arrival;
        public float arrivalYaw;

        Home(UUID owner, int index) {
            this.owner = owner;
            this.index = index;
        }
    }

    /** Ponto de retorno do jogador. */
    public record ReturnPoint(ResourceKey<Level> dim, Vec3 pos, float yaw) {}

    private final Map<UUID, Home> homes = new LinkedHashMap<>();
    private final Map<Integer, Home> byIndex = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private int nextIndex;

    public static HomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HomeData::new, HomeData::load), NAME);
    }

    // ------------------------------------------------------------------------------------------------ lares
    @Nullable
    public Home home(UUID owner) {
        return homes.get(owner);
    }

    @Nullable
    public Home byIndex(int index) {
        return byIndex.get(index);
    }

    public List<Home> all() {
        return new ArrayList<>(homes.values());
    }

    /** Cria o lar do jogador (ou devolve o que já existe). */
    public Home create(UUID owner, String name) {
        Home h = homes.get(owner);
        if (h == null) {
            h = new Home(owner, nextIndex++);
            homes.put(owner, h);
            byIndex.put(h.index, h);
        }
        h.ownerName = name;
        setDirty();
        return h;
    }

    /** Lares para os quais o jogador foi convidado. */
    public List<Home> invitedTo(UUID guest) {
        List<Home> out = new ArrayList<>();
        for (Home h : homes.values()) if (h.invited.containsKey(guest)) out.add(h);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ retornos
    public void putReturn(UUID player, ReturnPoint point) {
        returns.put(player, point);
        setDirty();
    }

    @Nullable
    public ReturnPoint takeReturn(UUID player) {
        ReturnPoint p = returns.remove(player);
        if (p != null) setDirty();
        return p;
    }

    public boolean hasReturn(UUID player) {
        return returns.containsKey(player);
    }

    /** Avisa que um lar mudou (convites, permissão, chegada) e precisa ser salvo. */
    public void touch() {
        setDirty();
    }

    // ------------------------------------------------------------------------------------------------ NBT
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Home h : homes.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", h.owner);
            t.putInt("index", h.index);
            t.putString("name", h.ownerName);
            t.putBoolean("built", h.built);
            t.putBoolean("guests_build", h.guestsBuild);
            ListTag inv = new ListTag();
            h.invited.forEach((id, name) -> {
                CompoundTag g = new CompoundTag();
                g.putUUID("id", id);
                g.putString("name", name);
                inv.add(g);
            });
            t.put("invited", inv);
            if (h.arrival != null) {
                t.putDouble("ax", h.arrival.x);
                t.putDouble("ay", h.arrival.y);
                t.putDouble("az", h.arrival.z);
                t.putFloat("ayaw", h.arrivalYaw);
            }
            list.add(t);
        }
        tag.put("homes", list);
        tag.putInt("next_index", nextIndex);
        ListTag ret = new ListTag();
        returns.forEach((id, p) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", id);
            t.putString("dim", p.dim().location().toString());
            t.putDouble("x", p.pos().x);
            t.putDouble("y", p.pos().y);
            t.putDouble("z", p.pos().z);
            t.putFloat("yaw", p.yaw());
            ret.add(t);
        });
        tag.put("returns", ret);
        return tag;
    }

    private static HomeData load(CompoundTag tag, HolderLookup.Provider registries) {
        HomeData d = new HomeData();
        ListTag list = tag.getList("homes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("owner")) continue;
            Home h = new Home(t.getUUID("owner"), t.getInt("index"));
            if (d.byIndex.containsKey(h.index)) continue; // arquivo estragado: dois donos no mesmo terreno
            h.ownerName = t.getString("name");
            h.built = t.getBoolean("built");
            h.guestsBuild = t.getBoolean("guests_build");
            ListTag inv = t.getList("invited", Tag.TAG_COMPOUND);
            for (int k = 0; k < inv.size(); k++) {
                CompoundTag g = inv.getCompound(k);
                if (g.hasUUID("id")) h.invited.put(g.getUUID("id"), g.getString("name"));
            }
            if (t.contains("ax") && t.contains("ay") && t.contains("az")) {
                h.arrival = new Vec3(t.getDouble("ax"), t.getDouble("ay"), t.getDouble("az"));
                h.arrivalYaw = t.getFloat("ayaw");
            }
            d.homes.put(h.owner, h);
            d.byIndex.put(h.index, h);
            d.nextIndex = Math.max(d.nextIndex, h.index + 1);
        }
        d.nextIndex = Math.max(d.nextIndex, tag.getInt("next_index"));
        ListTag ret = tag.getList("returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < ret.size(); i++) {
            CompoundTag t = ret.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (!t.hasUUID("player") || dim == null) continue;
            d.returns.put(t.getUUID("player"), new ReturnPoint(ResourceKey.create(Registries.DIMENSION, dim),
                    new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")), t.getFloat("yaw")));
        }
        return d;
    }
}
