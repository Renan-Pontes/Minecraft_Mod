package com.renanpontes.isekaimahou.feature.dwarf_hold;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.dwarf_hold.entity.DwarfEntity;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Rune;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Runes;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * {@code /mahou dwarf_hold ...} (cheats): {@code give} (kit de Mithril + tesouros + forja), {@code locate} (Cidade Anã mais
 * próxima, clicável), {@code tp} (vai para o Grande Salão da mais próxima), {@code spawn merchant|smith} e
 * {@code rune <id>} (grava a runa no item da mão).
 */
public final class DwarfCommands {
    private static final String CMD = "command.isekai_mahou.dwarf_hold.";
    /** Altura do chão do Grande Salão acima da base do molde (igual ao FLOOR do tools/gen/dwarf_hold.py). */
    public static final int HALL_FLOOR = 2;

    public static void init() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("dwarf_hold")
                .then(Commands.literal("give").executes(DwarfCommands::give))
                .then(Commands.literal("locate").executes(c -> locate(c, false)))
                .then(Commands.literal("tp").executes(c -> locate(c, true)))
                .then(Commands.literal("spawn").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"merchant", "smith"}, b))
                        .executes(DwarfCommands::spawn)))
                .then(Commands.literal("rune").then(Commands.argument("rune", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Rune.values()).map(r -> r.id), b))
                        .executes(DwarfCommands::rune)))));
    }

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        for (var holder : DwarfRegistry.worldTab()) p.getInventory().placeItemBackInInventory(new ItemStack(holder.get()));
        p.getInventory().placeItemBackInInventory(new ItemStack(DwarfRegistry.MITHRIL_INGOT.get(), 16));
        c.getSource().sendSuccess(() -> Component.translatable(CMD + "give"), false);
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> c, boolean teleport) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getServer().overworld();
        Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(DwarfHoldEvents.CITY).orElse(null);
        BlockPos origin = src.getLevel() == level ? BlockPos.containing(src.getPosition()) : level.getSharedSpawnPos();
        Pair<BlockPos, Holder<Structure>> hit = holder == null ? null
                : level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
        if (hit == null) {
            src.sendFailure(Component.translatable(CMD + "none"));
            return 0;
        }
        BlockPos p = hit.getFirst();
        StructureStart start = level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS)
                .getStartForStructure(holder.value());
        BlockPos hall = p;
        if (start != null && start.isValid()) {
            BoundingBox bb = start.getBoundingBox();
            hall = new BlockPos(bb.getCenter().getX(), bb.minY() + HALL_FLOOR, bb.getCenter().getZ());
        }
        if (teleport) {
            ServerPlayer sp = src.getPlayerOrException();
            sp.teleportTo(level, hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            src.sendSuccess(() -> Component.translatable(CMD + "tp"), false);
            return 1;
        }
        String tp = "/execute in minecraft:overworld run tp @s " + hall.getX() + " " + hall.getY() + " " + hall.getZ();
        int dist = (int) Math.sqrt(hall.distSqr(new BlockPos(origin.getX(), hall.getY(), origin.getZ())));
        BlockPos fh = hall;
        src.sendSuccess(() -> Component.translatable(CMD + "found", fh.getX(), fh.getY(), fh.getZ(), dist)
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp)))), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        EntityType<DwarfEntity> type = "smith".equals(kind) ? DwarfRegistry.DWARF_SMITH.get() : DwarfRegistry.DWARF_MERCHANT.get();
        DwarfEntity d = type.spawn(p.serverLevel(), p.blockPosition().relative(p.getDirection(), 2), MobSpawnType.COMMAND);
        return d == null ? 0 : 1;
    }

    private static int rune(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Rune r = Rune.byId(StringArgumentType.getString(c, "rune"));
        ItemStack held = p.getMainHandItem();
        if (r == null || Runes.target(held) == null) {
            c.getSource().sendFailure(Component.translatable(CMD + "rune.fail"));
            return 0;
        }
        held.set(DwarfRegistry.RUNE.get(), r.id);
        c.getSource().sendSuccess(() -> Component.translatable(CMD + "rune", r.displayName(), held.getHoverName()), false);
        return 1;
    }

    private DwarfCommands() {}
}
