package com.renanpontes.isekaimahou.feature.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.item.SoulBinding;
import com.renanpontes.isekaimahou.feature.market.MarketData.Box;
import com.renanpontes.isekaimahou.feature.market.MarketData.Listing;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras do <b>Mercado</b> (leilão entre jogadores) — "isso é legal, pode ser dentro do M[enu]".
 * <ul>
 *   <li>Anunciar: o item SAI do inventário e fica guardado no anúncio, com um preço em moedas da Guilda.</li>
 *   <li>Comprar: funciona com o dono offline. O comprador paga (saldo do Mercado primeiro, depois as moedas do
 *       inventário); o vendedor recebe o preço menos a taxa de {@value #FEE_PERCENT}% na caixa dele.</li>
 *   <li>Retirar: moedas de vendas e itens (cancelados, vencidos ou comprados com o inventário cheio) ficam na caixa
 *       até o jogador buscar na aba.</li>
 *   <li>Anúncio vence em {@value #DURATION_DAYS} dias reais e o item volta para a caixa do dono.</li>
 * </ul>
 * Itens de classe/vinculados não entram (são intransferíveis). Nada aqui cria moeda: só muda de mão (a taxa some).
 */
public final class MarketService {
    public static final int MAX_PER_PLAYER = 10;
    public static final int MAX_TOTAL = 300;
    public static final int MAX_PRICE = 9999;
    public static final int FEE_PERCENT = 5;
    public static final int DURATION_DAYS = 7;
    private static final long DURATION_MS = DURATION_DAYS * 24L * 60L * 60L * 1000L;
    /** Teto de itens esperando na caixa: cheia e sem espaço no inventário, comprar/cancelar é recusado até retirar. */
    private static final int MAX_BOX_ITEMS = 54;

    /** Último tick de ação por jogador (um clique nervoso não vira dez pacotes processados). */
    private static final Map<UUID, Integer> LAST_OP = new HashMap<>();

    private MarketService() {}

    // =====================================================================================
    //  Moeda
    // =====================================================================================
    /** Moeda do mod (a da Guilda; sem o módulo, esmeralda — nunca deve acontecer no mod completo). */
    public static Item coin() {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("guild_coin")).orElse(Items.EMERALD);
    }

    public static int coinsIn(Player p) {
        Item coin = coin();
        int n = 0;
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) if (s.is(coin)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(coin)) n += s.getCount();
        return n;
    }

    private static void takeCoins(ServerPlayer p, int amount) {
        Item coin = coin();
        int left = amount;
        Inventory inv = p.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.offhand)) {
            for (ItemStack s : part) {
                if (left <= 0) break;
                if (!s.is(coin)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }

    /** Taxa cobrada do vendedor quando vende (vendas miúdas não pagam, para o preço 1 não render 0). */
    public static int fee(int price) {
        return price < 5 ? 0 : Math.max(1, Math.round(price * FEE_PERCENT / 100F));
    }

    // =====================================================================================
    //  Abrir / sincronizar
    // =====================================================================================
    /** Abre a aba Mercado no cliente do jogador. */
    public static void open(ServerPlayer sp) {
        if (sp instanceof FakePlayer) return;
        PacketDistributor.sendToPlayer(sp, MarketNet.Open.INSTANCE);
    }

    static void onQuery(ServerPlayer sp, int knownVersion) {
        MinecraftServer server = sp.server;
        MarketData d = MarketData.get(server);
        Hints.show(sp, "market.open");
        if (knownVersion == d.version()) return;
        expire(server, d);
        sendAll(sp, d);
    }

    private static void sendAll(ServerPlayer sp, MarketData d) {
        long now = System.currentTimeMillis();
        List<MarketNet.Entry> part = new ArrayList<>(MarketNet.PART);
        boolean first = true;
        Iterator<Listing> it = d.listings.values().iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            int hours = (int) Math.max(1, (l.created + DURATION_MS - now + 3_599_999L) / 3_600_000L);
            part.add(new MarketNet.Entry(l.id, l.seller, l.sellerName, l.stack.copy(), l.price, hours));
            if (part.size() >= MarketNet.PART && it.hasNext()) {
                PacketDistributor.sendToPlayer(sp, new MarketNet.Listings(d.version(), first, false, part));
                part = new ArrayList<>(MarketNet.PART);
                first = false;
            }
        }
        PacketDistributor.sendToPlayer(sp, new MarketNet.Listings(d.version(), first, true, part));
        sendBox(sp, d);
    }

    private static void sendBox(ServerPlayer sp, MarketData d) {
        Box b = d.boxOrNull(sp.getUUID());
        List<ItemStack> items = new ArrayList<>();
        if (b != null) for (ItemStack s : b.items) items.add(s.copy());
        PacketDistributor.sendToPlayer(sp, new MarketNet.BoxSync(b == null ? 0 : b.coins, items));
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    static void onOp(ServerPlayer sp, MarketNet.Op op) {
        if (sp instanceof FakePlayer || sp.isSpectator() || !sp.isAlive()) return;
        int now = sp.server.getTickCount();
        Integer last = LAST_OP.put(sp.getUUID(), now);
        if (last != null && now - last < 3 && now >= last) return;
        MarketData d = MarketData.get(sp.server);
        Component fail = switch (op.op()) {
            case MarketNet.OP_SELL -> sellChecked(sp, d, op);
            case MarketNet.OP_BUY -> buy(sp, d, op.id(), op.a());
            case MarketNet.OP_CANCEL -> cancel(sp, d, op.id());
            case MarketNet.OP_COLLECT -> collect(sp, d, op.a());
            default -> msg("invalid");
        };
        if (fail != null) {
            PacketDistributor.sendToPlayer(sp, new MarketNet.Result(false, fail));
            // A lista do cliente pode estar velha (alguém comprou antes): manda a atual
            sendAll(sp, d);
        }
    }

    private static void ok(ServerPlayer sp, MarketData d, Component message) {
        d.changed();
        PacketDistributor.sendToPlayer(sp, new MarketNet.Result(true, message));
        sendAll(sp, d);
    }

    /** Pedido da aba: confere se o item do slot ainda é o que o jogador viu (o inventário pode ter mudado no caminho). */
    private static Component sellChecked(ServerPlayer sp, MarketData d, MarketNet.Op op) {
        int price = (int) (op.id() & 0xFFFFFFFFL), expected = (int) (op.id() >>> 32);
        Inventory inv = sp.getInventory();
        if (op.a() < 0 || op.a() >= inv.items.size()) return msg("invalid");
        ItemStack in = inv.items.get(op.a());
        if (in.isEmpty() || BuiltInRegistries.ITEM.getId(in.getItem()) != expected) return msg("moved");
        return sell(sp, d, op.a(), op.b(), price);
    }

    /** Anuncia {@code count} do item no {@code slot} (0–35) do inventário. Devolve o erro, ou null se deu certo. */
    static Component sell(ServerPlayer sp, MarketData d, int slot, int count, int price) {
        Inventory inv = sp.getInventory();
        if (slot < 0 || slot >= inv.items.size()) return msg("invalid");
        ItemStack in = inv.items.get(slot);
        if (in.isEmpty() || count <= 0 || count > in.getCount()) return msg("invalid");
        if (price < 1 || price > MAX_PRICE) return msg("bad_price", MAX_PRICE);
        if (in.is(coin())) return msg("no_coins_item");
        if (SoulBinding.isClassItem(in) || SoulBinding.owner(in) != null) return msg("bound");
        if (d.countOf(sp.getUUID()) >= MAX_PER_PLAYER) return msg("too_many", MAX_PER_PLAYER);
        if (d.listings.size() >= MAX_TOTAL) return msg("market_full");
        ItemStack sold = in.split(count);
        inv.setChanged();
        Listing l = new Listing(d.newId(), sp.getUUID(), sp.getGameProfile().getName(), sold, price, System.currentTimeMillis());
        d.listings.put(l.id, l);
        sp.playNotifySound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.6F, 1.2F);
        ok(sp, d, Component.translatable("market.isekai_mahou.msg.listed", name(sold), coins(price)));
        return null;
    }

    static Component buy(ServerPlayer sp, MarketData d, long id, int expectedPrice) {
        Listing l = d.listings.get(id);
        if (l == null) return msg("gone");
        if (l.seller.equals(sp.getUUID())) return msg("own");
        if (l.price != expectedPrice) return msg("gone");
        if (expired(l, System.currentTimeMillis())) {
            expire(sp.server, d);
            return msg("gone");
        }
        if (noRoom(sp, d)) return msg("box_full");
        Box mine = d.boxOrNull(sp.getUUID());
        int balance = mine == null ? 0 : mine.coins;
        int inHand = coinsIn(sp);
        if ((long) balance + inHand < l.price) return msg("no_money", coins(l.price), coins(balance + inHand));
        // Paga: primeiro o saldo do Mercado, depois as moedas do inventário
        int fromBalance = Math.min(balance, l.price);
        if (fromBalance > 0) mine.coins -= fromBalance;
        if (l.price > fromBalance) takeCoins(sp, l.price - fromBalance);
        d.listings.remove(id);

        int net = l.price - fee(l.price);
        Box theirs = d.box(l.seller);
        theirs.coins += net;
        ItemStack bought = l.stack;
        Component itemName = name(bought);
        deliver(sp, d, bought.copy());
        MagicApi.addCounter(sp, "market.bought", 1);

        ServerPlayer seller = sp.server.getPlayerList().getPlayer(l.seller);
        if (seller != null) {
            // Dono online: um aviso curto na barra de ação (o dinheiro fica na caixa para ele buscar)
            seller.displayClientMessage(Component.translatable("market.isekai_mahou.msg.sold", itemName, coins(net))
                    .withStyle(ChatFormatting.GOLD), true);
            seller.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F);
            MagicApi.addCounter(seller, "market.sold", 1);
            // Se ele estiver com a aba aberta, a caixa atualiza na hora
            sendBox(seller, d);
        } else {
            theirs.sold++;
            theirs.soldCoins += net;
        }
        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        ok(sp, d, Component.translatable("market.isekai_mahou.msg.bought", itemName, coins(l.price)));
        return null;
    }

    static Component cancel(ServerPlayer sp, MarketData d, long id) {
        Listing l = d.listings.get(id);
        if (l == null) return msg("gone");
        if (!l.seller.equals(sp.getUUID())) return msg("not_yours");
        if (noRoom(sp, d)) return msg("box_full");
        d.listings.remove(id);
        Component itemName = name(l.stack);
        deliver(sp, d, l.stack.copy());
        ok(sp, d, Component.translatable("market.isekai_mahou.msg.cancelled", itemName));
        return null;
    }

    static Component collect(ServerPlayer sp, MarketData d, int what) {
        Box b = d.boxOrNull(sp.getUUID());
        if (b == null || b.coins <= 0 && b.items.isEmpty()) return msg("nothing");
        Inventory inv = sp.getInventory();
        // No criativo o inventário "aceita" tudo e joga fora o que não cabe: sem espaço livre, nada sai da caixa
        if (sp.getAbilities().instabuild && inv.getFreeSlot() < 0) return msg("inventory_full");
        int gotCoins = 0, gotItems = 0;
        if (what != MarketNet.COLLECT_COINS) {
            Iterator<ItemStack> it = b.items.iterator();
            while (it.hasNext()) {
                ItemStack s = it.next();
                int before = s.getCount();
                inv.add(s);
                if (s.getCount() < before) gotItems++;
                if (s.isEmpty()) it.remove();
            }
        }
        if (what != MarketNet.COLLECT_ITEMS) {
            Item coin = coin();
            int max = Math.max(1, new ItemStack(coin).getMaxStackSize());
            while (b.coins > 0) {
                ItemStack s = new ItemStack(coin, Math.min(b.coins, max));
                int before = s.getCount();
                inv.add(s);
                int given = before - s.getCount();
                b.coins -= given;
                gotCoins += given;
                if (!s.isEmpty()) break;
            }
        }
        b.sold = 0;
        b.soldCoins = 0;
        if (gotCoins == 0 && gotItems == 0) return msg("inventory_full");
        sp.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.1F);
        boolean left = b.coins > 0 || !b.items.isEmpty();
        ok(sp, d, Component.translatable(left ? "market.isekai_mahou.msg.collected_part" : "market.isekai_mahou.msg.collected",
                coins(gotCoins), gotItems));
        return null;
    }

    /** Inventário cheio E caixa cheia: não há onde pôr mais um item. */
    private static boolean noRoom(ServerPlayer sp, MarketData d) {
        Box b = d.boxOrNull(sp.getUUID());
        return b != null && b.items.size() >= MAX_BOX_ITEMS && sp.getInventory().getFreeSlot() < 0;
    }

    /** Entrega no inventário; o que não couber espera na caixa do jogador (nunca cai no chão nem some). */
    private static void deliver(ServerPlayer sp, MarketData d, ItemStack stack) {
        // Criativo "aceita" tudo e joga fora o que não cabe: lá vai direto para a caixa
        if (!sp.getAbilities().instabuild) sp.getInventory().add(stack);
        if (!stack.isEmpty()) d.box(sp.getUUID()).items.add(stack);
    }

    // =====================================================================================
    //  Prazo, entrada no jogo
    // =====================================================================================
    private static boolean expired(Listing l, long now) {
        return now - l.created >= DURATION_MS;
    }

    /** Anúncios vencidos voltam para a caixa do dono. */
    static int expire(MinecraftServer server, MarketData d) {
        long now = System.currentTimeMillis();
        int n = 0;
        Iterator<Listing> it = d.listings.values().iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (!expired(l, now)) continue;
            it.remove();
            d.box(l.seller).items.add(l.stack);
            n++;
            ServerPlayer owner = server.getPlayerList().getPlayer(l.seller);
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("market.isekai_mahou.msg.expired", name(l.stack))
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (n > 0) d.changed();
        return n;
    }

    static void tick(MinecraftServer server) {
        // Uma conferida por minuto basta (o prazo é de dias); a lista é pequena (≤ MAX_TOTAL)
        if (server.getTickCount() % 1200 != 600) return;
        expire(server, MarketData.get(server));
    }

    /** Entrou no jogo: atualiza o nome nos anúncios e avisa, em UMA linha, se há algo para retirar. */
    static void onLogin(ServerPlayer sp) {
        MarketData d = MarketData.get(sp.server);
        String name = sp.getGameProfile().getName();
        boolean renamed = false;
        for (Listing l : d.listings.values()) {
            if (l.seller.equals(sp.getUUID()) && !name.equals(l.sellerName)) {
                l.sellerName = name;
                renamed = true;
            }
        }
        if (renamed) d.changed();
        Box b = d.boxOrNull(sp.getUUID());
        if (b == null || b.isEmpty()) return;
        MutableComponent line;
        if (b.sold > 0) {
            line = Component.translatable("market.isekai_mahou.msg.login_sold", b.sold, coins(b.soldCoins));
            MagicApi.addCounter(sp, "market.sold", b.sold);
            b.sold = 0;
            b.soldCoins = 0;
            d.setDirty();
        } else {
            line = Component.translatable("market.isekai_mahou.msg.login_waiting");
        }
        sp.sendSystemMessage(line.withStyle(ChatFormatting.GOLD));
    }

    static void onLogout(UUID id) {
        LAST_OP.remove(id);
    }

    static void clear() {
        LAST_OP.clear();
    }

    // =====================================================================================
    //  Texto
    // =====================================================================================
    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("market.isekai_mahou.err." + key, args);
    }

    static MutableComponent coins(int n) {
        return Component.translatable("market.isekai_mahou.coins", n);
    }

    static Component name(ItemStack stack) {
        MutableComponent c = Component.empty().append(stack.getHoverName());
        if (stack.getCount() > 1) c.append(Component.literal(" ×" + stack.getCount()));
        return c;
    }
}
