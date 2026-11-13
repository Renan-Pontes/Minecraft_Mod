package com.renanpontes.isekaimahou.feature.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados salvos do Mercado (um por mundo, guardado com o Overworld): os anúncios e a "caixa" de cada jogador — moedas de
 * vendas e itens devolvidos/comprados que esperam retirada. É isto que deixa a venda acontecer com o dono offline.
 * <p>
 * Chaves NBT (não renomear): {@code Next}, {@code Listings[Id,Seller,Name,Item,Price,Created]},
 * {@code Boxes[Owner,Coins,Items[],Sold,SoldCoins]}.
 */
public final class MarketData extends SavedData {
    private static final String FILE = "isekai_mahou_market";

    /** Um anúncio. O item fica guardado aqui (saiu do inventário do vendedor). */
    public static final class Listing {
        public final long id;
        public final UUID seller;
        public String sellerName;
        public final ItemStack stack;
        public final int price;
        /** Relógio real (ms) da criação: o prazo corre mesmo com o servidor desligado. */
        public final long created;

        Listing(long id, UUID seller, String sellerName, ItemStack stack, int price, long created) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.stack = stack;
            this.price = price;
            this.created = created;
        }
    }

    /** Caixa de retirada de um jogador. */
    public static final class Box {
        public int coins;
        public final List<ItemStack> items = new ArrayList<>();
        /** Vendas feitas desde o último aviso (para avisar quem estava offline). */
        public int sold, soldCoins;

        public boolean isEmpty() {
            return coins <= 0 && items.isEmpty() && sold <= 0;
        }
    }

    final Map<Long, Listing> listings = new LinkedHashMap<>();
    final Map<UUID, Box> boxes = new HashMap<>();
    private long nextId = 1;
    /** Sobe a cada mudança (não é salvo): o cliente só recebe a lista de novo quando ela mudou. */
    private int version = 1;

    public static MarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MarketData::new, MarketData::load), FILE);
    }

    public int version() {
        return version;
    }

    /** Marca para salvar e avisa os clientes com o Mercado aberto. */
    void changed() {
        version++;
        setDirty();
    }

    long newId() {
        return nextId++;
    }

    Box box(UUID owner) {
        return boxes.computeIfAbsent(owner, u -> new Box());
    }

    @Nullable
    Box boxOrNull(UUID owner) {
        return boxes.get(owner);
    }

    int countOf(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller.equals(seller)) n++;
        return n;
    }

    // ------------------------------------------------------------------ NBT
    private static MarketData load(CompoundTag tag, HolderLookup.Provider reg) {
        MarketData d = new MarketData();
        d.nextId = Math.max(1, tag.getLong("Next"));
        ListTag list = tag.getList("Listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Seller")) continue;
            // Item de um mod que saiu do mundo: o anúncio some sem derrubar o carregamento
            ItemStack stack = ItemStack.parse(reg, t.getCompound("Item")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) continue;
            long id = t.getLong("Id");
            d.listings.put(id, new Listing(id, t.getUUID("Seller"), t.getString("Name"), stack, Math.max(1, t.getInt("Price")),
                    t.getLong("Created")));
            d.nextId = Math.max(d.nextId, id + 1);
        }
        ListTag boxes = tag.getList("Boxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            CompoundTag t = boxes.getCompound(i);
            if (!t.hasUUID("Owner")) continue;
            Box b = new Box();
            b.coins = Math.max(0, t.getInt("Coins"));
            b.sold = Math.max(0, t.getInt("Sold"));
            b.soldCoins = Math.max(0, t.getInt("SoldCoins"));
            ListTag items = t.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                ItemStack.parse(reg, items.getCompound(j)).filter(s -> !s.isEmpty()).ifPresent(b.items::add);
            }
            if (!b.isEmpty()) d.boxes.put(t.getUUID("Owner"), b);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        tag.putLong("Next", nextId);
        ListTag list = new ListTag();
        for (Listing l : listings.values()) {
            if (l.stack.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putLong("Id", l.id);
            t.putUUID("Seller", l.seller);
            t.putString("Name", l.sellerName);
            t.put("Item", l.stack.save(reg));
            t.putInt("Price", l.price);
            t.putLong("Created", l.created);
            list.add(t);
        }
        tag.put("Listings", list);
        ListTag out = new ListTag();
        for (Map.Entry<UUID, Box> e : boxes.entrySet()) {
            Box b = e.getValue();
            if (b.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", e.getKey());
            t.putInt("Coins", b.coins);
            t.putInt("Sold", b.sold);
            t.putInt("SoldCoins", b.soldCoins);
            ListTag items = new ListTag();
            for (ItemStack s : b.items) if (!s.isEmpty()) items.add(s.save(reg));
            t.put("Items", items);
            out.add(t);
        }
        tag.put("Boxes", out);
        return tag;
    }
}
