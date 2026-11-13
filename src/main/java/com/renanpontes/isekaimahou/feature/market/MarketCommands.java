package com.renanpontes.isekaimahou.feature.market;

import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.market.MarketData.Box;
import com.renanpontes.isekaimahou.feature.market.MarketData.Listing;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos de teste (op): {@code /mahou market open | coins <n> | sellhand <preço> | fake <preço> | list | box | expire |
 * clear}. "fake" cria um anúncio de um vendedor fantasma com uma cópia do item na mão — para testar a compra sozinho.
 */
final class MarketCommands {
    private static final UUID GHOST = new UUID(0x4D41524B45540000L, 1L);

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("market")
                .then(Commands.literal("open").executes(c -> {
                    MarketService.open(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("coins").then(Commands.argument("n", IntegerArgumentType.integer(1, 6400)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int left = IntegerArgumentType.getInteger(c, "n");
                    while (left > 0) {
                        ItemStack s = new ItemStack(MarketService.coin(), Math.min(64, left));
                        left -= s.getCount();
                        if (!p.getInventory().add(s)) p.drop(s, false);
                    }
                    return 1;
                })))
                .then(Commands.literal("sellhand").then(Commands.argument("price", IntegerArgumentType.integer(1, MarketService.MAX_PRICE))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int slot = p.getInventory().selected;
                            Component err = MarketService.sell(p, MarketData.get(p.server), slot, p.getInventory().getItem(slot).getCount(),
                                    IntegerArgumentType.getInteger(c, "price"));
                            if (err != null) c.getSource().sendFailure(err);
                            return err == null ? 1 : 0;
                        })))
                .then(Commands.literal("fake").then(Commands.argument("price", IntegerArgumentType.integer(1, MarketService.MAX_PRICE))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack hand = p.getMainHandItem();
                            if (hand.isEmpty()) {
                                c.getSource().sendFailure(Component.translatable("market.isekai_mahou.err.invalid"));
                                return 0;
                            }
                            MarketData d = MarketData.get(p.server);
                            Listing l = new Listing(d.newId(), GHOST, "Mercador Fantasma", hand.copy(),
                                    IntegerArgumentType.getInteger(c, "price"), System.currentTimeMillis());
                            d.listings.put(l.id, l);
                            d.changed();
                            c.getSource().sendSuccess(() -> Component.literal("Anúncio de teste #" + l.id + " criado."), false);
                            return 1;
                        })))
                .then(Commands.literal("list").executes(c -> {
                    MarketData d = MarketData.get(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Mercado: " + d.listings.size() + " anúncio(s), "
                            + d.boxes.size() + " caixa(s)"), false);
                    for (Listing l : d.listings.values()) {
                        c.getSource().sendSuccess(() -> Component.literal(" #" + l.id + " ").append(MarketService.name(l.stack))
                                .append(" — " + l.price + " — " + l.sellerName), false);
                    }
                    return d.listings.size();
                }))
                .then(Commands.literal("box").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Box b = MarketData.get(p.server).boxOrNull(p.getUUID());
                    int coins = b == null ? 0 : b.coins, items = b == null ? 0 : b.items.size();
                    c.getSource().sendSuccess(() -> Component.literal("Caixa: " + coins + " moeda(s), " + items + " item(ns)"), false);
                    return 1;
                }))
                .then(Commands.literal("expire").executes(c -> {
                    // Vence TODOS os anúncios agora (os itens voltam para as caixas dos donos)
                    MarketData d = MarketData.get(c.getSource().getServer());
                    int n = d.listings.size();
                    for (Listing l : d.listings.values()) d.box(l.seller).items.add(l.stack);
                    d.listings.clear();
                    d.changed();
                    c.getSource().sendSuccess(() -> Component.literal(n + " anúncio(s) vencido(s)."), true);
                    return n;
                }))
                .then(Commands.literal("clear").executes(c -> {
                    MarketData d = MarketData.get(c.getSource().getServer());
                    d.listings.clear();
                    d.boxes.clear();
                    d.changed();
                    c.getSource().sendSuccess(() -> Component.literal("Mercado esvaziado (anúncios e caixas)."), true);
                    return 1;
                }))));
    }

    private MarketCommands() {}
}
