package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "vehicles" — v1.0: veículos mágicos ({@link VehicleKind}), cada um bom para um terreno, com controles simples
 * e energia = mana do piloto ou Cristal de Mana ({@link MagicVehicle}).
 * <p>Onda 10 ("pra que sejam vendidos na cidade por um mecânico"): os veículos são VENDIDOS pelo Mecânico — na Oficina
 * Mecânica da cidade do jogador e nas cidades do mundo ({@link VehicleMechanic}); só o Barco Arcano continua na bancada.
 * Ganharam vida, conserto, melhorias ({@link VehicleUpgrades}), resgate e reconstrução ({@link VehicleRegistry}),
 * faróis, fumaça de avaria e som de motor, e três tipos novos (Triciclo de Mana, Lancha das Marés, Galeão Celeste).
 * <p>Teste: {@code /mahou vehicles give <tipo|all|parts>}, {@code fuel <0-100>}, {@code hp <0-100>},
 * {@code upgrade <peça> <0-3>}, {@code list}.
 */
public class VehiclesModule implements Module {
    @Override
    public String id() {
        return "vehicles";
    }

    @Override
    public void init(IEventBus modBus) {
        VehiclesRegistries.register(modBus);
        modBus.addListener(VehiclesModule::creativeTab);
        MahouCommands.extend((root, ctx) -> root.then(commands()));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            for (VehicleKind kind : VehicleKind.values()) {
                e.accept(VehiclesRegistries.stackWithFuel(kind, MagicVehicle.MAX_FUEL));
            }
            for (VehicleUpgrades.Part part : VehicleUpgrades.Part.values()) e.accept(new ItemStack(VehiclesRegistries.part(part)));
            e.accept(new ItemStack(VehiclesRegistries.REPAIR_KIT.get()));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commands() {
        LiteralArgumentBuilder<CommandSourceStack> v = MahouCommands.op("vehicles");
        // /mahou vehicles give <tipo|all|parts>: o item do veículo com o tanque cheio (parts = peças e kits)
        v.then(Commands.literal("give").then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all", "parts"),
                        Arrays.stream(VehicleKind.values()).map(k -> k.id)), b))
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(c, "type");
                    int n = 0;
                    if (id.equals("parts")) {
                        for (VehicleUpgrades.Part part : VehicleUpgrades.Part.values()) give(sp, new ItemStack(VehiclesRegistries.part(part), 3));
                        give(sp, new ItemStack(VehiclesRegistries.REPAIR_KIT.get(), 4));
                        n = 4;
                    }
                    for (VehicleKind kind : VehicleKind.values()) {
                        if (!id.equals("all") && !id.equals(kind.id)) continue;
                        give(sp, VehiclesRegistries.stackWithFuel(kind, MagicVehicle.MAX_FUEL));
                        n++;
                    }
                    if (n == 0) {
                        c.getSource().sendFailure(Component.literal("Veículo desconhecido: " + id));
                        return 0;
                    }
                    int given = n;
                    c.getSource().sendSuccess(() -> Component.literal("Veículos/peças entregues: " + given), false);
                    return n;
                })));
        // /mahou vehicles fuel <0-100>: carga do tanque do veículo em que você está
        v.then(Commands.literal("fuel").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
            MagicVehicle vehicle = riding(c.getSource());
            if (vehicle == null) return 0;
            int pct = IntegerArgumentType.getInteger(c, "percent");
            vehicle.setFuel(MagicVehicle.MAX_FUEL * pct / 100);
            c.getSource().sendSuccess(() -> Component.literal("Tanque: " + pct + "%"), false);
            return 1;
        })));
        // /mahou vehicles hp <0-100>: vida do veículo em que você está (0 = destruído, para testar a reconstrução)
        v.then(Commands.literal("hp").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
            MagicVehicle vehicle = riding(c.getSource());
            if (vehicle == null) return 0;
            int pct = IntegerArgumentType.getInteger(c, "percent");
            if (pct <= 0) {
                vehicle.setHealth(1F);
                vehicle.hurt(vehicle.damageSources().explosion(null, null), 1000F);
            } else {
                vehicle.setHealth(vehicle.getMaxHealth() * pct / 100F);
            }
            c.getSource().sendSuccess(() -> Component.literal("Vida: " + pct + "%"), false);
            return 1;
        })));
        // /mahou vehicles upgrade <peça> <0-3>: melhoria do veículo em que você está
        v.then(Commands.literal("upgrade").then(Commands.argument("part", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(VehicleUpgrades.Part.values()).map(p -> p.id), b))
                .then(Commands.argument("level", IntegerArgumentType.integer(0, VehicleUpgrades.MAX)).executes(c -> {
                    MagicVehicle vehicle = riding(c.getSource());
                    if (vehicle == null) return 0;
                    VehicleUpgrades.Part part = VehicleUpgrades.Part.byId(StringArgumentType.getString(c, "part"));
                    if (part == null) {
                        c.getSource().sendFailure(Component.literal("Peça desconhecida"));
                        return 0;
                    }
                    int lv = IntegerArgumentType.getInteger(c, "level");
                    vehicle.setUpgrades(VehicleUpgrades.with(vehicle.upgrades(), part, lv));
                    c.getSource().sendSuccess(() -> Component.literal(part.id + " = " + lv), false);
                    return 1;
                }))));
        // /mahou vehicles list: os seus veículos no caderno do Mecânico
        v.then(Commands.literal("list").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            c.getSource().sendSuccess(() -> VehicleMechanic.summary(sp), false);
            for (Component line : VehicleMechanic.lines(sp)) c.getSource().sendSuccess(() -> line, false);
            return 1;
        }));
        return v;
    }

    private static MagicVehicle riding(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        if (!(sp.getVehicle() instanceof MagicVehicle vehicle)) {
            src.sendFailure(Component.literal("Suba num veículo mágico primeiro."));
            return null;
        }
        return vehicle;
    }

    private static void give(ServerPlayer sp, ItemStack s) {
        if (!sp.getInventory().add(s)) sp.drop(s, false);
    }
}
