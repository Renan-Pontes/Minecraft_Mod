package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * CARAVANAS (v1.0, city_life) — palavras do dono: <i>"mandam um 'comerciante' que vai como % até o local, e negocia
 * lá"</i>. Pelo painel, a cidade com Mercado manda um comerciante a outra cidade (de reino, vila ou de outro jogador).
 * Ele NÃO anda de verdade: a viagem é uma porcentagem (ida → negociando → volta) contada pelo tempo do jogo, e funciona
 * mesmo com a cidade descarregada.
 * <ul>
 *   <li>Missões: vender comida ou lenha do celeiro, comprar comida ou lenha com moedas do cofre, ou negociar
 *       mercadorias (investe moedas, volta com lucro e às vezes itens para o armazém);</li>
 *   <li>cada cidade tem os SEUS preços (fixos pelo lugar, oscilando um pouco a cada 3 dias); longe paga melhor;
 *       mandar várias caravanas no mesmo dia para a MESMA cidade satura o mercado de lá (vale mais espalhar);</li>
 *   <li>riscos pequenos: salteadores levam uma parte (menos com segurança e estábulo), e às vezes sai um bom negócio.
 *       O comerciante sempre volta e nunca se perde tudo;</li>
 *   <li>festival em casa: +20% no negócio. Cidade de outro jogador como destino ganha movimento e uma taxa.</li>
 * </ul>
 */
public final class CityCaravans {
    public static final String SELL_FOOD = "sell_food", SELL_FUEL = "sell_fuel", BUY_FOOD = "buy_food", BUY_FUEL = "buy_fuel",
            GOODS = "goods";
    public static final List<String> KINDS = List.of(SELL_FOOD, SELL_FUEL, BUY_FOOD, BUY_FUEL, GOODS);
    /** Tamanhos da carga: unidades (comida/lenha) e moedas (compras e mercadorias). */
    public static final int[] CARGO_UNITS = {40, 80, 160};
    public static final int[] CARGO_COINS = {30, 60, 120};
    public static final int MAX_SLOTS = 5;
    private static final int TRADE_TICKS = 600;
    private static final int MAX_DEST = 10;

    /** Itens que uma caravana de mercadorias pode trazer: item, quantidade para cada 30 moedas investidas. */
    private static final Object[][] WARES = {
            {Items.IRON_INGOT, 2}, {Items.GOLD_INGOT, 1}, {Items.EMERALD, 1}, {Items.BOOK, 2}, {Items.LEATHER, 3},
            {Items.REDSTONE, 4}, {Items.LAPIS_LAZULI, 3}, {Items.AMETHYST_SHARD, 2}, {Items.GLASS, 6}, {Items.STRING, 4},
            {Items.PAPER, 6}, {Items.HONEY_BOTTLE, 1}};

    /** Um destino possível. */
    public record Dest(Places.Place place, int dist, boolean war, boolean playerCity) {}

    // =====================================================================================
    //  Preços (por cidade)
    // =====================================================================================
    private static int hash(String s) {
        int h = s.hashCode();
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Preço de 100 unidades de comida ({@code food}) ou lenha ({@code fuel}) numa cidade, em moedas. */
    public static int price(Places.Place p, String good, long day) {
        int base = good.equals("food") ? 30 : 24;
        // gosto fixo da cidade (70%..140%) + oscilação de ±15% a cada 3 dias (comprar custa 30% a mais que vender)
        float taste = 0.7F + (hash(p.id() + "/" + good) % 71) / 100F;
        float drift = 0.85F + (hash(p.id() + "/" + good + "/" + day / 3) % 31) / 100F;
        float type = "capital".equals(p.type()) ? 1.1F : "village".equals(p.type()) ? 0.9F : 1F;
        return Math.max(8, Math.round(base * taste * drift * type));
    }

    /** Lucro (%) das mercadorias naquela cidade. */
    public static int margin(Places.Place p, long day) {
        return 8 + hash(p.id() + "/goods/" + day / 3) % 28 + ("capital".equals(p.type()) ? 6 : 0);
    }

    /** Caravanas que esta cidade já mandou HOJE para aquele destino (o mercado de lá satura). */
    public static int sentToday(MinecraftServer server, Settlement s, String destId) {
        if (s.caravanMarket.getLong("Day") != SettlementService.day(server)) return 0;
        return s.caravanMarket.getCompound("Sent").getInt(destId);
    }

    private static void countSent(MinecraftServer server, Settlement s, String destId) {
        long day = SettlementService.day(server);
        if (s.caravanMarket.getLong("Day") != day) {
            s.caravanMarket = new CompoundTag();
            s.caravanMarket.putLong("Day", day);
        }
        CompoundTag sent = s.caravanMarket.getCompound("Sent");
        sent.putInt(destId, sent.getInt(destId) + 1);
        s.caravanMarket.put("Sent", sent);
    }

    /**
     * Cotação de uma missão naquele destino AGORA: moedas por 100 unidades (vender: o que pagam; comprar: o que cobram,
     * 30% mais caro) ou o lucro em % (mercadorias). Cada caravana já mandada hoje para lá piora a cotação.
     */
    public static int quote(MinecraftServer server, Settlement s, Places.Place p, String kind) {
        long day = SettlementService.day(server);
        int n = sentToday(server, s, p.id());
        float glutSell = Math.max(0.4F, 1F - 0.15F * n), glutBuy = 1F + 0.15F * n;
        return switch (kind) {
            case SELL_FOOD -> Math.max(4, Math.round(price(p, "food", day) * glutSell));
            case SELL_FUEL -> Math.max(4, Math.round(price(p, "fuel", day) * glutSell));
            case BUY_FOOD -> Math.round(price(p, "food", day) * 1.3F * glutBuy);
            case BUY_FUEL -> Math.round(price(p, "fuel", day) * 1.3F * glutBuy);
            default -> Math.round(margin(p, day) * Math.max(0F, 1F - 0.34F * n));
        };
    }

    /** Longe paga melhor: +1% a cada 100 blocos, até +30%. */
    public static int distanceBonus(int dist) {
        return Math.min(30, dist / 100);
    }

    /** Tempo de ida (ticks): 2 a 10 minutos conforme a distância; o Estábulo encurta 25%. */
    public static int travelTicks(Settlement s, int dist) {
        int t = Math.max(2400, Math.min(12000, dist * 5));
        return s.has("stable") ? t * 3 / 4 : t;
    }

    public static int totalTicks(Settlement s, int dist) {
        return 2 * travelTicks(s, dist) + TRADE_TICKS;
    }

    /** Chance (%) de salteadores: 6–20 pela distância; segurança e estábulo diminuem. */
    public static int risk(MinecraftServer server, Settlement s, int dist) {
        float r = 6 + Math.min(14, dist / 200F);
        r *= 1F - 0.4F * Math.min(1F, CityEconomy.stats(server, s).securityRatio());
        if (s.has("stable")) r *= 0.75F;
        return Math.max(2, Math.round(r));
    }

    // =====================================================================================
    //  Vagas e destinos
    // =====================================================================================
    /** Caravanas ao mesmo tempo: 1 por Mercado, +1 com Porto, +1 se a cidade está feliz (0 sem Mercado). */
    public static int slots(Settlement s) {
        if (!s.own() || !s.has("market")) return 0;
        return Math.min(MAX_SLOTS, s.count("market") + (s.has("port") ? 1 : 0) + (s.affinity >= CityHappiness.HAPPY ? 1 : 0)
                // onda 5: Cidade e Capital mandam uma caravana a mais
                + (s.level >= 4 ? 1 : 0));
    }

    /** As cidades e vilas mais perto (mesma dimensão), da mais próxima para a mais distante. */
    public static List<Dest> destinations(MinecraftServer server, Settlement s) {
        List<Dest> out = new ArrayList<>();
        ServerLevel sl = SettlementService.level(server, s);
        if (sl == null) return out;
        SettlementData data = SettlementData.get(server);
        for (Places.Place p : Places.all(sl)) {
            if (p.dim() != s.dim || p.id().equals(s.id)) continue;
            if (!SettlementService.isCity(p) && !"village".equals(p.type())) continue;
            int dist = (int) Math.sqrt(p.center().distSqr(s.heart));
            Settlement other = data.city(p.id());
            out.add(new Dest(p, dist, Allegiance.areEnemies(server, s.id, p.id()), other != null && other.owner != null));
        }
        out.sort(Comparator.comparingInt(Dest::dist));
        return out.size() > MAX_DEST ? new ArrayList<>(out.subList(0, MAX_DEST)) : out;
    }

    @Nullable
    private static Dest dest(MinecraftServer server, Settlement s, String id) {
        for (Dest d : destinations(server, s)) if (d.place().id().equals(id)) return d;
        return null;
    }

    // =====================================================================================
    //  Mandar
    // =====================================================================================
    private static boolean fail(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
        return false;
    }

    /** Manda uma caravana (ação da tela). {@code size} = 0..2 (pequena, média, grande). */
    public static boolean send(ServerPlayer sp, Settlement s, String targetId, String kind, int size) {
        MinecraftServer server = sp.server;
        if (!KINDS.contains(kind)) return false;
        int slots = slots(s);
        if (slots <= 0) return fail(sp, "caravan.need_market");
        if (s.caravans.size() >= slots) return fail(sp, "caravan.no_slot", slots);
        Dest d = dest(server, s, targetId);
        if (d == null) return fail(sp, "caravan.no_dest");
        if (d.war()) return fail(sp, "caravan.war");
        int sz = Math.max(0, Math.min(2, size));
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        int cargo;
        switch (kind) {
            case SELL_FOOD -> {
                // nunca deixa a cidade com fome: guarda 2 dias de comida
                cargo = Math.min(CARGO_UNITS[sz], s.food - 2 * st.foodUse());
                if (cargo < 10) return fail(sp, "caravan.no_food");
                s.food -= cargo;
            }
            case SELL_FUEL -> {
                cargo = Math.min(CARGO_UNITS[sz], s.fuel - 2 * st.fuelUse());
                if (cargo < 10) return fail(sp, "caravan.no_fuel");
                s.fuel -= cargo;
            }
            default -> {
                cargo = Math.min(CARGO_COINS[sz], s.treasury);
                if (cargo < 10) return fail(sp, "caravan.no_coins");
                s.treasury -= cargo;
            }
        }
        CompoundTag c = new CompoundTag();
        c.putString("Target", d.place().id());
        c.putString("TName", d.place().name().getString());
        c.putString("Kind", kind);
        c.putInt("Cargo", cargo);
        c.putInt("Dist", d.dist());
        c.putLong("Start", server.overworld().getGameTime());
        c.putInt("Travel", travelTicks(s, d.dist()));
        // a cotação é a combinada na saída (a tela mostrou esta)
        c.putInt("Price", quote(server, s, d.place(), kind));
        countSent(server, s, d.place().id());
        c.putBoolean("Feast", Lordship.festivalActive(server, s));
        s.caravans.add(c);
        s.caravansSent++;
        sp.displayClientMessage(SettlementsLang.tr("caravan.sent", d.place().name(), seconds(totalTicks(s, d.dist()))).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static String seconds(int ticks) {
        int sec = ticks / 20;
        return sec >= 60 ? (sec / 60) + "min" + (sec % 60 >= 10 ? " " + (sec % 60) + "s" : "") : sec + "s";
    }

    // =====================================================================================
    //  Viagem (porcentagem) e volta
    // =====================================================================================
    /** Fase agora: 0 indo, 1 negociando, 2 voltando, 3 chegou. */
    public static int phase(CompoundTag c, long now) {
        long e = now - c.getLong("Start");
        int travel = Math.max(1, c.getInt("Travel"));
        if (e < travel) return 0;
        if (e < travel + TRADE_TICKS) return 1;
        return e < 2L * travel + TRADE_TICKS ? 2 : 3;
    }

    /** Porcentagem da viagem inteira (0..100). */
    public static int percent(CompoundTag c, long now) {
        long total = 2L * Math.max(1, c.getInt("Travel")) + TRADE_TICKS;
        return (int) Math.max(0, Math.min(100, (now - c.getLong("Start")) * 100 / total));
    }

    public static int secondsLeft(CompoundTag c, long now) {
        long total = 2L * Math.max(1, c.getInt("Travel")) + TRADE_TICKS;
        return (int) Math.max(0, (c.getLong("Start") + total - now) / 20);
    }

    /** A cada 5 s: caravanas que voltaram fecham o negócio. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Settlement s : SettlementData.get(server).all()) {
            if (s.caravans.isEmpty()) continue;
            boolean any = false;
            for (Iterator<CompoundTag> it = s.caravans.iterator(); it.hasNext();) {
                CompoundTag c = it.next();
                // relógio do mundo voltou (comando /time não mexe no gameTime, mas por segurança): recomeça a viagem
                if (c.getLong("Start") > now) c.putLong("Start", now);
                if (phase(c, now) < 3) continue;
                it.remove();
                resolve(server, s, c);
                any = true;
            }
            if (any) SettlementService.refreshViewers(server, s);
        }
    }

    /** Fecha o negócio de uma caravana que voltou. */
    static void resolve(MinecraftServer server, Settlement s, CompoundTag c) {
        RandomSource r = server.overworld().random;
        String kind = c.getString("Kind");
        int cargo = c.getInt("Cargo"), dist = c.getInt("Dist"), price = Math.max(1, c.getInt("Price"));
        float bonus = 1F + distanceBonus(dist) / 100F;
        if (c.getBoolean("Feast") || Lordship.festivalActive(server, s)) bonus += 0.2F;
        // onda 6 (city_monuments): Arco e Grande Farol aumentam o lucro das caravanas
        bonus += CityMonuments.cityStat(s, CityMonuments.Stat.CARAVAN) / 100F;
        String event = "";
        int roll = r.nextInt(100);
        float luck = 1F;
        if (roll < risk(server, s, dist)) {
            event = "bandits";
            luck = 0.55F + r.nextInt(21) / 100F;   // perde 25–45%
        } else if (roll >= 92) {
            event = "deal";
            luck = 1.2F;
        }
        int coins = 0, food = 0, fuel = 0;
        ItemStack ware = ItemStack.EMPTY;
        switch (kind) {
            case SELL_FOOD, SELL_FUEL -> coins = Math.max(1, Math.round(cargo * price / 100F * bonus * luck));
            case BUY_FOOD -> food = Math.round(cargo * 100F / price * (1F + (bonus - 1F) / 2F) * luck);
            case BUY_FUEL -> fuel = Math.round(cargo * 100F / price * (1F + (bonus - 1F) / 2F) * luck);
            default -> {
                // mercadorias: o investimento volta com o lucro da cidade (a rota e a festa aumentam só o LUCRO);
                // salteadores levam parte, mas nunca tudo; um ótimo negócio dobra o lucro
                float profit = cargo * c.getInt("Price") / 100F * bonus * (event.equals("deal") ? 2F : 1F);
                coins = event.equals("bandits") ? Math.max(Math.round(cargo * 0.6F), Math.round((cargo + profit) * luck))
                        : Math.round(cargo + profit);
                // itens só quando o negócio foi bom de verdade (mercado saturado = sem brinde)
                if (!event.equals("bandits") && r.nextInt(100) < Math.min(40, 2 * c.getInt("Price"))) {
                    Object[] w = WARES[r.nextInt(WARES.length)];
                    ware = new ItemStack((Item) w[0], Math.max(1, (int) w[1] * cargo / 30));
                }
            }
        }
        // o que não cabe no celeiro é vendido na porta pela metade
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        if (food > 0) {
            int room = Math.max(0, st.foodCap() - s.food);
            coins += Math.max(0, food - room) * price / 200;
            s.food += Math.min(food, room);
        }
        if (fuel > 0) {
            int room = Math.max(0, st.fuelCap() - s.fuel);
            coins += Math.max(0, fuel - room) * price / 200;
            s.fuel += Math.min(fuel, room);
        }
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + coins);
        if (!ware.isEmpty()) CityStash.add(s, ware.getItem(), ware.getCount());
        int spent = kind.equals(SELL_FOOD) || kind.equals(SELL_FUEL) ? 0 : cargo;
        s.caravanProfit += coins - (kind.equals(GOODS) ? spent : 0);
        // cidade de outro jogador: o negócio movimenta o comércio de lá (e deixa uma taxa no cofre)
        Settlement other = SettlementData.get(server).city(c.getString("Target"));
        if (other != null && other != s) {
            other.tradesToday += 2;
            other.treasury = Math.min(Settlement.MAX_TREASURY, other.treasury + Math.max(1, Math.max(coins, cargo) / 20));
        }
        CompoundTag last = new CompoundTag();
        last.putString("TName", c.getString("TName"));
        last.putString("Kind", kind);
        last.putInt("Coins", coins);
        last.putInt("Food", food);
        last.putInt("Fuel", fuel);
        last.putString("Event", event);
        if (!ware.isEmpty()) {
            last.putString("Ware", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ware.getItem()).toString());
            last.putInt("WareCount", ware.getCount());
        }
        s.caravanLast = last;
        Component summary = summary(last);
        for (ServerPlayer p : CityEconomy.governors(server, s)) {
            Rewards.toast(p, SettlementsLang.tr("caravan.back.title", c.getString("TName")), summary,
                    new ItemStack(event.equals("bandits") ? Items.IRON_SWORD : Items.EMERALD), event.equals("bandits") ? 0xE8735E : 0x7DDA6B);
        }
    }

    /** "+34 moedas · +2 Livro" / "+60 comida · salteadores levaram parte". */
    public static Component summary(CompoundTag last) {
        var out = Component.empty();
        boolean any = false;
        int[] vals = {last.getInt("Coins"), last.getInt("Food"), last.getInt("Fuel")};
        String[] keys = {"coins", "food", "fuel"};
        for (int i = 0; i < 3; i++) {
            if (vals[i] <= 0) continue;
            if (any) out.append(" · ");
            out.append(SettlementsLang.tr("caravan.got." + keys[i], vals[i]));
            any = true;
        }
        if (last.contains("Ware")) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(last.getString("Ware"));
            if (id != null) {
                if (any) out.append(" · ");
                out.append(Component.literal("+" + last.getInt("WareCount") + " "))
                        .append(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).getHoverName());
                any = true;
            }
        }
        String event = last.getString("Event");
        if (!event.isEmpty()) {
            if (any) out.append(" · ");
            out.append(SettlementsLang.tr("caravan.event." + event));
        }
        return out;
    }

    /** Comando de teste: todas as caravanas chegam agora. */
    public static int finishAll(MinecraftServer server, Settlement s) {
        int n = s.caravans.size();
        for (CompoundTag c : new ArrayList<>(s.caravans)) resolve(server, s, c);
        s.caravans.clear();
        return n;
    }

    private CityCaravans() {}
}
