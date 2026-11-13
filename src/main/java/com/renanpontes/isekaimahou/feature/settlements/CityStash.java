package com.renanpontes.isekaimahou.feature.settlements;

import java.util.Map;

import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Armazém de MERCADORIAS da cidade (v1.0, city_life — "coleta fácil de impostos/produção"): o que os prédios de
 * produção rendem por dia (mina, serraria, fazenda, porto) e o que as caravanas trazem fica guardado aqui, e o
 * governante/conselheiro pega tudo com um botão, de qualquer lugar dentro da cidade.
 * <p>Quantias modestas e com teto: a cidade ajuda, não substitui a aventura.
 */
public final class CityStash {
    /** Teto por tipo de item e de tipos diferentes guardados. */
    public static final int MAX_PER_ITEM = 256, MAX_KINDS = 18;
    /** Teto do que UM tipo de prédio rende por dia, somando todos os prédios dele. */
    private static final int DAILY_CAP = 24;

    public static void add(Settlement s, Item item, int count) {
        if (count <= 0 || item == Items.AIR) return;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        for (CompoundTag t : s.stash) {
            if (t.getString("Item").equals(id)) {
                t.putInt("Count", Math.min(MAX_PER_ITEM, t.getInt("Count") + count));
                return;
            }
        }
        if (s.stash.size() >= MAX_KINDS) return;
        CompoundTag t = new CompoundTag();
        t.putString("Item", id);
        t.putInt("Count", Math.min(MAX_PER_ITEM, count));
        s.stash.add(t);
    }

    public static int total(Settlement s) {
        int n = 0;
        for (CompoundTag t : s.stash) n += t.getInt("Count");
        return n;
    }

    /** Entrega tudo que está guardado (o que não couber cai aos pés do jogador). */
    public static void collect(ServerPlayer sp, Settlement s) {
        if (s.stash.isEmpty()) return;
        int n = 0;
        for (CompoundTag t : s.stash) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("Item"));
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            int left = t.getInt("Count");
            if (item == Items.AIR) continue;
            n += left;
            while (left > 0) {
                int c = Math.min(left, item.getDefaultMaxStackSize());
                Rewards.give(sp, new ItemStack(item, c));
                left -= c;
            }
        }
        s.stash.clear();
        if (n > 0) {
            sp.displayClientMessage(SettlementsLang.tr("stash.collected", n).withColor(0x7DDA6B), true);
            sp.playNotifySound(SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * Produção do dia dos prédios (só cidade fundada): mina = ferro bruto e carvão; serraria = toras do estilo; fazenda =
     * pão; porto = peixe. Cresce com o nível do prédio e com a felicidade; prédio danificado não rende.
     */
    public static void dailyProduction(MinecraftServer server, Settlement s, CityEconomy.Stats st) {
        if (!s.own()) return;
        int mine = 0, saw = 0, farm = 0, port = 0;
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (s.damaged.contains(e.getKey())) continue;
            int lvl = s.levelOf(e.getKey());
            switch (e.getValue()) {
                case "mine" -> mine += lvl;
                case "sawmill" -> saw += lvl;
                case "farm" -> farm += lvl;
                case "port" -> port += lvl;
                default -> { }
            }
        }
        float m = st.prodMult();
        // onda 5: as minas dos anões rendem mais (o ponto forte do estilo)
        float mm = m * s.style.mineMult();
        add(s, Items.RAW_IRON, amount(mine, mm));
        add(s, Items.COAL, amount(mine * 2, mm));
        if (s.style == SettlementStyle.DWARVEN) add(s, Items.RAW_COPPER, amount(mine, m));
        Item log = switch (s.style) {
            case DESERT -> Items.ACACIA_LOG;
            case NORDIC, GOTHIC -> Items.SPRUCE_LOG;
            case ORIENTAL -> Items.CHERRY_LOG;
            case ELVEN -> Items.BIRCH_LOG;
            case DWARVEN -> Items.DARK_OAK_LOG;
            default -> Items.OAK_LOG;
        };
        add(s, log, amount(saw * 3, m));
        add(s, Items.BREAD, amount(farm, m));
        add(s, Items.COD, amount(port * 2, m));
    }

    private static int amount(int base, float mult) {
        return Math.min(DAILY_CAP, Math.round(base * mult));
    }

    private CityStash() {}
}
