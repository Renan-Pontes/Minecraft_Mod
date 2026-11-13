package com.renanpontes.isekaimahou.feature.end_library;

import java.util.Optional;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de teste (op):
 * <ul>
 *   <li>{@code /mahou end_library give} — fragmentos, lingotes, molde, tomos, os dois tesouros e o conjunto estelar;</li>
 *   <li>{@code /mahou end_library locate} — mostra a Biblioteca mais próxima no End;</li>
 *   <li>{@code /mahou end_library tp} — leva você até a porta da Biblioteca mais próxima (no End);</li>
 *   <li>{@code /mahou end_library place} — gera a Biblioteca onde você está (como o /place structure);</li>
 *   <li>{@code /mahou spawnboss star_devourer} — invoca o Devorador de Estrelas à frente.</li>
 * </ul>
 */
public final class ElCommands {
    private static final TagKey<Structure> LIBRARY_TAG = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("lair/end_library"));

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("end_library")
                    .then(Commands.literal("give").executes(ElCommands::give))
                    .then(Commands.literal("locate").executes(c -> locate(c, false)))
                    .then(Commands.literal("tp").executes(c -> locate(c, true)))
                    .then(Commands.literal("place").executes(ElCommands::place)));
            root.then(MahouCommands.op("spawnboss").then(Commands.literal("star_devourer").executes(ElCommands::spawnBoss)));
        });
    }

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack[] kit = {new ItemStack(ElRegistries.STAR_FRAGMENT.get(), 16), new ItemStack(ElRegistries.STAR_INGOT.get(), 4),
                new ItemStack(ElRegistries.STAR_UPGRADE_TEMPLATE.get(), 2), new ItemStack(ElRegistries.VOID_TOME.get(), 3),
                new ItemStack(ElRegistries.VOID_TREASURE_GATE.get()), new ItemStack(ElRegistries.VOID_OVERLORD_STAFF.get()),
                new ItemStack(ElRegistries.STAR_SWORD.get()), new ItemStack(ElRegistries.STAR_PICKAXE.get()),
                new ItemStack(ElRegistries.STAR_HELMET.get()), new ItemStack(ElRegistries.STAR_CHESTPLATE.get()),
                new ItemStack(ElRegistries.STAR_LEGGINGS.get()), new ItemStack(ElRegistries.STAR_BOOTS.get()),
                new ItemStack(ElRegistries.STAR_FRAGMENT_ORE_ITEM.get(), 4), new ItemStack(ElRegistries.VOID_BOOKSHELF_ITEM.get(), 8)};
        for (ItemStack s : kit) {
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.end_library.given"), false);
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> c, boolean teleport) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel end = c.getSource().getServer().getLevel(Level.END);
        if (end == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.end_library.no_end"));
            return 0;
        }
        // Fora do End, procura a partir do começo das ilhas externas.
        BlockPos from = p.level() == end ? p.blockPosition() : new BlockPos(1100, 64, 0);
        BlockPos found = end.findNearestMapStructure(LIBRARY_TAG, from, 100, false);
        if (found == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.end_library.not_found"));
            return 0;
        }
        if (teleport) {
            int x = found.getX(), z = found.getZ() + 24;
            end.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= end.getMinBuildHeight() + 1) {
                end.getChunk(SectionPos.blockToSectionCoord(found.getX()), SectionPos.blockToSectionCoord(found.getZ()));
                x = found.getX() + 3;
                z = found.getZ() + 12;
                y = end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            }
            p.teleportTo(end, x + 0.5, y + 1, z + 0.5, 180F, 0F);
        }
        BlockPos at = found;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.end_library.located", at.getX(), at.getZ())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    /** Gera a Biblioteca na posição do jogador (forçado: aceita qualquer bioma/terreno), carregando os chunks. */
    private static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        Optional<Holder.Reference<Structure>> holder = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ElEvents.STRUCTURE);
        if (holder.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.end_library.place_failed"));
            return 0;
        }
        ChunkGenerator gen = sl.getChunkSource().getGenerator();
        StructureStart start = holder.get().value().generate(sl.registryAccess(), gen, gen.getBiomeSource(),
                sl.getChunkSource().randomState(), sl.getStructureManager(), sl.getSeed(), new ChunkPos(p.blockPosition()), 0, sl, b -> true);
        if (!start.isValid()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.end_library.place_failed"));
            return 0;
        }
        BoundingBox bb = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(cp -> sl.getChunk(cp.x, cp.z));
        ChunkPos.rangeClosed(min, max).forEach(cp -> start.placeInChunk(sl, sl.structureManager(), gen, sl.getRandom(),
                new BoundingBox(cp.getMinBlockX(), sl.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), sl.getMaxBuildHeight(),
                        cp.getMaxBlockZ()), cp));
        int cx = (bb.minX() + bb.maxX()) / 2, cz = (bb.minZ() + bb.maxZ()) / 2;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.end_library.placed", cx, cz), true);
        return 1;
    }

    private static int spawnBoss(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) pos = viewer.position().add(look.normalize().scale(7.0));
        }
        Entity boss = ElRegistries.STAR_DEVOURER.get().spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.end_library.place_failed"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.end_library.spawned", name), true);
        return 1;
    }

    private ElCommands() {}
}
