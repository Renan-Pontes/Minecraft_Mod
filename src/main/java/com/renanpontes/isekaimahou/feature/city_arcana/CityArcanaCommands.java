package com.renanpontes.isekaimahou.feature.city_arcana;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos do módulo:
 * <pre>
 * /mahou city_arcana info                          estado dos prédios mágicos da cidade onde você está (qualquer um)
 * /mahou city_arcana obelisk off|outsiders|all     modo do Obelisco (o Senhor da cidade; op em qualquer cidade)
 * /mahou city_arcana ward                          (v1.0) Núcleo de Mana e a receita gravada na Barreira da cidade onde você está
 * /mahou city_arcana ward record &lt;1-4&gt;             (v1.0) o Senhor grava na Barreira a magia do slot Z/X/C/G do loadout ativo
 * /mahou city_arcana ward clear | pour             (v1.0) apaga a receita / doa a sua mana ao Núcleo
 * --- op ---
 * /mahou city_arcana debug outsider                você passa a contar como forasteiro INIMIGO (de novo = volta)
 * /mahou city_arcana debug refresh                 recalcula o cache das cidades agora
 * /mahou city_arcana debug dome                    mostra o domo da barreira da cidade onde você está
 * /mahou city_arcana debug grant &lt;chave&gt; &lt;1-5&gt;    põe o prédio (só os dados, sem obra) no terreno vazio onde você está
 * /mahou city_arcana debug mana &lt;n&gt;                (v1.0) põe n de mana no Núcleo da Barreira
 * /mahou city_arcana debug pulse                   (v1.0) dispara agora a receita da Barreira em qualquer inimigo (sem cerco)
 * </pre>
 */
public final class CityArcanaCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("city_arcana")
                .then(Commands.literal("info").executes(CityArcanaCommands::info))
                .then(Commands.literal("obelisk")
                        .then(Commands.literal("off").executes(c -> obelisk(c, NullObelisk.MODE_OFF)))
                        .then(Commands.literal("outsiders").executes(c -> obelisk(c, NullObelisk.MODE_OUTSIDERS)))
                        .then(Commands.literal("all").executes(c -> obelisk(c, NullObelisk.MODE_ALL))))
                .then(Commands.literal("ward").executes(c -> ward(c, 0))
                        .then(Commands.literal("record").then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
                                .executes(c -> ward(c, IntegerArgumentType.getInteger(c, "slot")))))
                        .then(Commands.literal("clear").executes(c -> ward(c, -1)))
                        .then(Commands.literal("pour").executes(c -> ward(c, -2))))
                .then(MahouCommands.op("debug")
                        .then(Commands.literal("mana").then(Commands.argument("n", IntegerArgumentType.integer(0, 99999)).executes(c -> {
                            CityArcana.City city = here(c);
                            if (city == null) return 0;
                            int n = Math.min(IntegerArgumentType.getInteger(c, "n"), Math.round(WardRecipe.capacity(city)));
                            CityArcanaData.get(c.getSource().getServer()).setWardMana(city.s.id, n);
                            c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.ward.mana", n,
                                    Math.round(WardRecipe.capacity(city))), true);
                            return 1;
                        })))
                        .then(Commands.literal("pulse").executes(c -> {
                            CityArcana.City city = here(c);
                            if (city == null) return 0;
                            var server = c.getSource().getServer();
                            int n = WardRecipe.fire(server, CityArcanaData.get(server), city, true, true);
                            c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.ward.pulse", n), true);
                            return n;
                        }))
                        .then(Commands.literal("outsider").executes(CityArcanaCommands::outsider))
                        .then(Commands.literal("refresh").executes(c -> {
                            CityArcana.rebuild(c.getSource().getServer());
                            int n = CityArcana.cities().size();
                            c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.refresh", n), true);
                            return n;
                        }))
                        .then(Commands.literal("dome").executes(CityArcanaCommands::dome))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CityArcana.KEYS, b))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, Settlement.MAX_BUILDING_LEVEL))
                                                .executes(CityArcanaCommands::grant)))))));
    }

    @Nullable
    private static CityArcana.City here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CityArcana.City city = CityArcana.at(p);
        if (city == null) c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.cmd.none"));
        return city;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CityArcana.City city = here(c);
        if (city == null) return 0;
        var server = c.getSource().getServer();
        int beacons = 0;
        for (CityArcana.Site st : city.sites) if (st.key().equals(CityArcana.BEACON)) beacons++;
        int ward = city.ward, ob = city.obelisk, spire = city.spireTotal, b = beacons;
        c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.info", SettlementService.cityName(server, city.s),
                ward, Math.round(ArcaneWard.reduction(ward) * 100), Math.round(ArcaneWard.fizzleChance(ward) * 100),
                ob, NullObelisk.modeName(city.mode), Math.round(ArcanaTowers.SPIRE_PCT * 1000 * spire) / 10.0, b), false);
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean nul = NullObelisk.targets(city, p, city.mode);
        boolean hostile = CityArcana.hostile(city, p);
        c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.info.you",
                Component.translatable(CityArcana.friendly(city, p) ? "city_arcana.isekai_mahou.cmd.friend"
                        : hostile ? "city_arcana.isekai_mahou.cmd.enemy" : "city_arcana.isekai_mahou.cmd.visitor"),
                Component.translatable(nul ? "city_arcana.isekai_mahou.cmd.yes" : "city_arcana.isekai_mahou.cmd.no"),
                ArcaneWard.tracked()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int obelisk(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        CityArcana.City city = here(c);
        if (city == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!p.getUUID().equals(city.s.owner) && !c.getSource().hasPermission(2)) {
            c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.obelisk.not_governor",
                    SettlementService.cityName(p.server, city.s)));
            return 0;
        }
        if (city.obelisk <= 0) {
            c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.cmd.no_obelisk"));
            return 0;
        }
        NullObelisk.setMode(p.serverLevel(), city, mode, p);
        return 1;
    }

    /** {@code what}: 0 = estado, 1..4 = gravar o slot, -1 = apagar, -2 = doar mana. */
    private static int ward(CommandContext<CommandSourceStack> c, int what) throws CommandSyntaxException {
        CityArcana.City city = here(c);
        if (city == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (city.ward <= 0) {
            c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.cmd.no_ward"));
            return 0;
        }
        if (what == 0) {
            WardRecipe.status(p, city);
        } else if (what == -1) {
            WardRecipe.erase(p, city);
        } else if (what == -2) {
            if (CityArcana.friendly(city, p)) WardRecipe.pour(p, city);
        } else {
            var slot = com.renanpontes.isekaimahou.core.MagicApi.get(p).activeSlot(what - 1);
            return WardRecipe.record(p, city, slot == null ? null : slot.main()) ? 1 : 0;
        }
        return 1;
    }

    private static int outsider(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean on = CityArcana.DEBUG_OUTSIDER.add(p.getUUID());
        if (!on) CityArcana.DEBUG_OUTSIDER.remove(p.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable(on ? "city_arcana.isekai_mahou.cmd.outsider.on"
                : "city_arcana.isekai_mahou.cmd.outsider.off"), false);
        return 1;
    }

    private static int dome(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CityArcana.City city = here(c);
        if (city == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        Vec3 center = Vec3.atBottomCenterOf(city.s.heart);
        CityArcanaNet.send(p.serverLevel(), CityArcanaNet.DOME, center, Vec3.ZERO, city.halfSize(), CityArcana.wardColor(city));
        // e um pedaço do domo bem na frente de quem pediu
        Vec3 look = p.getLookAngle();
        CityArcanaNet.send(p.serverLevel(), CityArcanaNet.WARD_HIT, p.getEyePosition().add(look.scale(4)), look.scale(-1), 2.5F,
                CityArcana.wardColor(city));
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(c, "key");
        int level = IntegerArgumentType.getInteger(c, "level");
        var at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s == null || !s.own() || !CityArcana.KEYS.contains(key)) {
            c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.cmd.grant.bad"));
            return 0;
        }
        int dx = CityLayout.indexAt(p.getBlockX() - s.heart.getX());
        int dz = CityLayout.indexAt(p.getBlockZ() - s.heart.getZ());
        int cell = dx == Integer.MIN_VALUE || dz == Integer.MIN_VALUE ? -1 : CityLayout.cellIndex(dx, dz);
        if (cell < 0 || cell == CityLayout.CENTER_CELL || !CityLayout.valid(cell) || s.buildings.containsKey(cell) || s.projectAt(cell) != null) {
            c.getSource().sendFailure(Component.translatable("city_arcana.isekai_mahou.cmd.grant.bad"));
            return 0;
        }
        s.buildings.put(cell, key);
        s.bLevels.put(cell, level);
        CityArcana.rebuild(p.server);
        SettlementService.refreshViewers(p.server, s);
        c.getSource().sendSuccess(() -> Component.translatable("city_arcana.isekai_mahou.cmd.grant.ok", key, level), true);
        return 1;
    }

    private CityArcanaCommands() {}
}
