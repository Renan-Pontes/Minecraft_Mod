package com.renanpontes.isekaimahou.feature.gear_plus;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Depuração (op): {@code /mahou gear_plus ...}
 * <ul>
 *   <li>{@code status} — conjunto vestido, Vínculo da arma e recarga do kit;</li>
 *   <li>{@code gems} — dá o Cinzel e 2 de cada gema (as 3 lapidações);</li>
 *   <li>{@code bond add <n>} · {@code bond awaken} · {@code bond reset} · {@code bond charge} (enche a Ressonância);</li>
 *   <li>{@code kit recover} (ignora recarga/custo) · {@code kit reset} (zera a recarga) · {@code kit best} (mostra, vaga
 *       a vaga, a peça que a recuperação devolve e o que volta nela) · {@code kit forget} (esquece as melhores peças da
 *       classe atual: a recuperação volta a dar o kit básico).</li>
 * </ul>
 */
final class GearCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("gear_plus")
                .then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException())))
                .then(Commands.literal("gems").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    give(sp, new ItemStack(GearRegistry.CHISEL.get()));
                    for (GemType type : GemType.values()) {
                        for (int grade = 1; grade <= 3; grade++) give(sp, new ItemStack(GearRegistry.gem(type, grade), 2));
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gear_plus.gems"), false);
                    return 1;
                }))
                .then(Commands.literal("bond")
                        .then(Commands.literal("add").then(Commands.argument("kills", IntegerArgumentType.integer(-10000, 10000))
                                .executes(c -> {
                                    ServerPlayer sp = c.getSource().getPlayerOrException();
                                    MagicPath path = MagicApi.get(sp).path();
                                    if (!WeaponBond.hasBond(path)) return noBond(c.getSource());
                                    WeaponBond.addKills(sp, path, IntegerArgumentType.getInteger(c, "kills"));
                                    return status(sp);
                                })))
                        .then(Commands.literal("awaken").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            MagicPath path = MagicApi.get(sp).path();
                            if (!WeaponBond.hasBond(path)) return noBond(c.getSource());
                            int need = WeaponBond.killsFor(WeaponBond.MAX_LEVEL) - GearData.bondKills(sp, path);
                            if (need > 0) WeaponBond.addKills(sp, path, need);
                            return status(sp);
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            MagicPath path = MagicApi.get(sp).path();
                            GearData.setBondKills(sp, path, 0);
                            GearData.setAwakeAnnounced(sp, path, false);
                            GearNet.sync(sp);
                            return status(sp);
                        }))
                        .then(Commands.literal("charge").executes(c -> {
                            WeaponBond.fillResonance(c.getSource().getPlayerOrException());
                            return 1;
                        })))
                .then(Commands.literal("kit")
                        .then(Commands.literal("recover").executes(c ->
                                KitRecovery.recover(c.getSource().getPlayerOrException(), false, true) == KitRecovery.Result.OK ? 1 : 0))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            GearData.setKitLast(sp, -1);
                            GearNet.sync(sp);
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gear_plus.kit_reset"), false);
                            return 1;
                        }))
                        .then(Commands.literal("best").executes(c -> best(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("forget").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            GearData.clearKitBest(sp, MagicApi.get(sp).path());
                            GearNet.sync(sp);
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gear_plus.kit_forget"), false);
                            return 1;
                        })))));
    }

    private static int noBond(net.minecraft.commands.CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.isekai_mahou.gear_plus.no_bond"));
        return 0;
    }

    private static void give(ServerPlayer sp, ItemStack stack) {
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
    }

    /** Vaga a vaga: a peça que a recuperação devolve agora (registrada ou básica), o grau e o que volta nela. */
    private static int best(ServerPlayer sp) {
        MagicPath path = MagicApi.get(sp).path();
        // registra já o que ele carrega (sem esperar os 2 s do tick): o teste vê o estado atual
        if (KitBest.scan(sp, path)) GearNet.sync(sp);
        java.util.List<KitBest.Entry> entries = KitBest.entries(sp, path);
        if (entries.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gear_plus.kit.no_kit"));
            return 0;
        }
        for (KitBest.Entry e : entries) {
            Component name = e.stack().isEmpty() ? Component.literal("-") : e.stack().getHoverName();
            boolean missing = !e.stack().isEmpty() && !KitRecovery.carries(sp, path, e.stack());
            sp.sendSystemMessage(Component.translatable(e.recorded() ? "command.isekai_mahou.gear_plus.kit_best.recorded"
                    : "command.isekai_mahou.gear_plus.kit_best.basic", e.slot().key(), name, e.rank(),
                    e.kept().isEmpty() ? "-" : String.join(", ", e.kept()))
                    .withStyle(missing ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.WHITE));
        }
        return entries.size();
    }

    private static int status(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        GearBonuses.refresh(sp);
        SetBonuses.Active set = SetBonuses.active(sp);
        sp.sendSystemMessage(set == null
                ? Component.translatable("command.isekai_mahou.gear_plus.status.no_set")
                : Component.translatable("command.isekai_mahou.gear_plus.status.set", SetBonuses.familyName(set.family()), set.rank()));
        if (WeaponBond.hasBond(md.path())) {
            int kills = GearData.bondKills(sp, md.path());
            sp.sendSystemMessage(Component.translatable("command.isekai_mahou.gear_plus.status.bond",
                    WeaponBond.level(kills, md.level()), kills));
        }
        int[] gems = {0};
        Gems.forEachActive(sp, md, (type, grade) -> gems[0]++);
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.gear_plus.status.gems", gems[0]));
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.gear_plus.status.kit", GearText.time(KitRecovery.waitTicks(sp))));
        return 1;
    }

    private GearCommands() {}
}
