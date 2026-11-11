package com.renanpontes.isekaimahou.feature.world_v09;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * {@code /mahou sites [estrutura]} (cheats): procura a estrutura do world_v09 mais próxima de cada tipo (ou só a
 * pedida), como o {@code /locate}, e mostra coordenadas clicáveis para teleportar.
 */
public final class V09Commands {
    /** Raio de busca em "anéis" da grade (o /locate do vanilla usa 100). */
    private static final int SEARCH_RADIUS = 64;

    public static void init() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("sites")
                .executes(c -> run(c, null))
                .then(Commands.argument("site", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(V09Site.values()).map(s -> s.id), b))
                        .executes(c -> run(c, StringArgumentType.getString(c, "site"))))));
    }

    private static int run(CommandContext<CommandSourceStack> c, String only) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        List<V09Site> sites;
        if (only == null) {
            sites = List.of(V09Site.values());
        } else {
            V09Site s = V09Site.byId(only);
            if (s == null) {
                src.sendFailure(Component.translatable("command.isekai_mahou.world_v09.sites.unknown", only));
                return 0;
            }
            sites = List.of(s);
        }
        BlockPos origin = BlockPos.containing(src.getPosition());
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.world_v09.sites.header").withStyle(ChatFormatting.GOLD), false);
        int found = 0;
        for (V09Site site : sites) {
            Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(site.key()).orElse(null);
            Pair<BlockPos, Holder<Structure>> hit = holder == null ? null : level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(holder), origin, SEARCH_RADIUS, false);
            MutableComponent line = Component.literal(" • ").append(site.displayName().copy().withStyle(s -> s.withColor(site.color)));
            if (hit == null) {
                line.append(Component.translatable("command.isekai_mahou.world_v09.sites.none").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                found++;
                BlockPos p = hit.getFirst();
                int dist = (int) Math.sqrt(p.distSqr(new BlockPos(origin.getX(), p.getY(), origin.getZ())));
                String tp = "/tp @s " + p.getX() + " ~ " + p.getZ();
                line.append(Component.translatable("command.isekai_mahou.world_v09.sites.found", p.getX(), p.getZ(), dist)
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp)))));
            }
            src.sendSuccess(() -> line, false);
        }
        return found;
    }

    private V09Commands() {}
}
