package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /mahou lore ...}
 * <ul>
 *   <li>qualquer jogador: {@code status} (quantos fragmentos, por coleção);</li>
 *   <li>op (teste): {@code page [fragmento]} (dá uma Página Perdida, comum ou de um fragmento certo);
 *       {@code unlock <fragmento|all>} ({@code all} não procura os lugares: use "Procurar de novo"); {@code reset}; {@code ruin} (teleporta para a Ruína de Velmora mais próxima);
 *       {@code goto <fragmento>} (teleporta para o lugar revelado por um fragmento que você tem).</li>
 * </ul>
 */
final class LoreCommands {
    private LoreCommands() {}

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("lore")
                .then(Commands.literal("status").executes(LoreCommands::status))
                .then(MahouCommands.op("page")
                        .executes(c -> page(c, ""))
                        .then(Commands.argument("fragment", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(false), b))
                                .executes(c -> page(c, StringArgumentType.getString(c, "fragment")))))
                .then(MahouCommands.op("unlock").then(Commands.argument("fragment", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(true), b))
                        .executes(LoreCommands::unlock)))
                .then(MahouCommands.op("reset").executes(c -> {
                    LoreService.reset(c.getSource().getPlayerOrException());
                    return done(c, "command.isekai_mahou.lore.reset");
                }))
                .then(MahouCommands.op("ruin").executes(LoreCommands::ruin))
                .then(MahouCommands.op("goto").then(Commands.argument("fragment", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(false), b))
                        .executes(LoreCommands::goTo)))));
    }

    private static List<String> ids(boolean withAll) {
        List<String> out = new ArrayList<>();
        if (withAll) out.add("all");
        for (LoreFragments.Fragment f : LoreFragments.all()) out.add(f.id());
        return out;
    }

    private static int done(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(key, args), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        CompoundTag d = LoreService.data(sp);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.lore.status",
                LoreData.count(d), LoreFragments.total(), LoreData.steleCount(d)).withStyle(ChatFormatting.GOLD), false);
        for (LoreFragments.LoreSet set : LoreFragments.sets()) {
            int have = LoreData.countInSet(d, set);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.lore.status_set",
                    set.title(), have, set.fragments().size()).withStyle(have >= set.fragments().size() ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int page(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (!id.isEmpty() && LoreFragments.get(id) == null) return fail(c, "command.isekai_mahou.lore.unknown", id);
        ItemStack stack = id.isEmpty() ? LoreApi.page() : LoreApi.page(id);
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        return done(c, "command.isekai_mahou.lore.page_given");
    }

    private static int unlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "fragment");
        int n = 0;
        if (id.equals("all")) {
            // Sem procurar os 37 lugares de uma vez (travaria o servidor): cada um tem "Procurar de novo" na Enciclopédia
            for (LoreFragments.Fragment f : LoreFragments.all()) if (LoreService.discover(sp, f, false)) n++;
        } else {
            LoreFragments.Fragment f = LoreFragments.get(id);
            if (f == null) return fail(c, "command.isekai_mahou.lore.unknown", id);
            if (LoreService.discover(sp, f)) n++;
        }
        return done(c, "command.isekai_mahou.lore.unlocked", n);
    }

    private static int ruin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.serverLevel();
        LoreLocator.Found found = LoreLocator.structure(level, List.of("lore_ruin", "lore_ruin_sand"), sp.blockPosition());
        if (found == null) return fail(c, "command.isekai_mahou.lore.ruin_none");
        teleport(sp, level, found.pos());
        return done(c, "command.isekai_mahou.lore.teleported", found.pos().getX(), found.pos().getZ());
    }

    private static int goTo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "fragment");
        LoreData.Spot spot = LoreData.spot(LoreService.data(sp), id);
        ServerLevel level = spot == null ? null : sp.server.getLevel(spot.dim());
        if (spot == null || level == null) return fail(c, "command.isekai_mahou.lore.no_place", id);
        teleport(sp, level, spot.pos());
        return done(c, "command.isekai_mahou.lore.teleported", spot.pos().getX(), spot.pos().getZ());
    }

    /** Teleporta para a superfície daquele X/Z (carrega o chunk para saber a altura). */
    private static void teleport(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (y <= level.getMinBuildHeight() + 1) y = Math.max(pos.getY(), level.getSeaLevel()) + 1;
        sp.teleportTo(level, pos.getX() + 0.5, y + 1, pos.getZ() + 0.5, sp.getYRot(), sp.getXRot());
    }
}
