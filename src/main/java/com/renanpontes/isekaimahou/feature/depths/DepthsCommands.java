package com.renanpontes.isekaimahou.feature.depths;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaPlacer;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaRun;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou depths give|tp [andar]|boss|geode|where|affixes|record|puzzle demo <tipo>|puzzle solve|puzzle tp} e {@code /mahou spawnboss depths_worm_king} (só op).
 * v1.0 r9: {@code puzzle enigma <tipo> [nível]|all [nível]|chain|reset|hint} monta/controla os Enigmas de raciocínio.
 * {@code tp} leva à Masmorra em Camadas mais próxima (0 = entrada, 1–4 = andares, 5 = ninho do Verme-Rei).
 */
public final class DepthsCommands {
    private static final ResourceKey<ConfiguredFeature<?, ?>> GEODE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, IsekaiMahou.id("depths_mana_geode"));

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("depths")
                    .then(Commands.literal("give").executes(c -> give(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("tp")
                            .executes(c -> tp(c.getSource(), c.getSource().getPlayerOrException(), 0))
                            .then(Commands.argument("floor", IntegerArgumentType.integer(0, DepthsDungeon.LAIR))
                                    .executes(c -> tp(c.getSource(), c.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(c, "floor")))))
                    .then(Commands.literal("boss").executes(c -> spawnBoss(c.getSource())))
                    .then(Commands.literal("geode").executes(c -> geode(c.getSource())))
                    .then(Commands.literal("where").executes(c -> where(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("affixes").executes(c -> affixes(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("record").executes(c -> record(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(puzzleCommands()));
            root.then(MahouCommands.op("spawnboss").then(Commands.literal("depths_worm_king").executes(c -> spawnBoss(c.getSource()))));
        });
    }

    private static int give(ServerPlayer sp) {
        for (var item : DepthsRegistries.worldItems()) sp.getInventory().placeItemBackInInventory(new ItemStack(item.get(), 8));
        for (var item : DepthsRegistries.gearItems()) sp.getInventory().placeItemBackInInventory(new ItemStack(item.get()));
        for (var egg : DepthsRegistries.eggs()) sp.getInventory().placeItemBackInInventory(new ItemStack(egg.get()));
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.depths.given").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int tp(CommandSourceStack source, ServerPlayer sp, int floor) {
        ServerLevel ow = source.getServer().getLevel(Level.OVERWORLD);
        if (ow == null) return 0;
        BlockPos origin = sp.level() == ow ? sp.blockPosition() : BlockPos.containing(sp.getX(), 64, sp.getZ());
        BlockPos found = ow.findNearestMapStructure(DepthsDungeon.TAG, origin, 100, false);
        StructureStart start = found == null ? null : DepthsDungeon.startAt(ow, found);
        if (start == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.not_found"));
            return 0;
        }
        List<StructurePiece> pieces = DepthsDungeon.sorted(start);
        StructurePiece piece = pieces.get(Math.min(floor, pieces.size() - 1));
        BoundingBox bb = piece.getBoundingBox();
        BlockPos c = bb.getCenter();
        Vec3 dest;
        if (pieces.indexOf(piece) == 0) {
            // Entrada: plataforma da ruína, ao lado do poço (o chão fica 20 blocos acima da base do molde).
            dest = new Vec3(c.getX() + 3.5, bb.minY() + 21, c.getZ() + 0.5);
        } else if (pieces.indexOf(piece) >= DepthsDungeon.LAIR) {
            dest = new Vec3(c.getX() + 9.5, bb.minY() + 2, c.getZ() + 0.5);
        } else {
            BlockPos f = DepthsDungeon.floorCenter(piece);
            dest = new Vec3(f.getX() + 0.5, f.getY(), f.getZ() + 0.5);
        }
        sp.teleportTo(ow, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());
        Vec3 at = dest;
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.tp", (int) at.x, (int) at.y, (int) at.z), true);
        return 1;
    }

    // ------------------------------------------------------------------ v1.0: afixos, recorde e quebra-cabeças

    /** Afixos de todos os andares da masmorra mais próxima (ou da que o jogador está). */
    private static int affixes(CommandSourceStack source, ServerPlayer sp) {
        ServerLevel ow = source.getServer().getLevel(Level.OVERWORLD);
        if (ow == null) return 0;
        StructureStart start = sp.level() == ow ? DepthsDungeon.startWithPieceAt(ow, sp.blockPosition()) : null;
        if (start == null) {
            BlockPos origin = sp.level() == ow ? sp.blockPosition() : BlockPos.containing(sp.getX(), 64, sp.getZ());
            BlockPos found = ow.findNearestMapStructure(DepthsDungeon.TAG, origin, 100, false);
            start = found == null ? null : DepthsDungeon.startAt(ow, found);
        }
        if (start == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.not_found"));
            return 0;
        }
        for (int floor = 1; floor <= DepthsDungeon.LAIR; floor++) {
            int f = floor;
            List<DepthsAffixes.Affix> list = DepthsAffixes.roll(ow, start.getChunkPos(), floor);
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.affix_floor", f, DepthsAffixes.line(list)), false);
            for (DepthsAffixes.Affix a : list) {
                source.sendSuccess(() -> Component.literal("  ").append(a.label()).append(Component.literal(" — ")
                        .withStyle(ChatFormatting.DARK_GRAY)).append(a.desc().withStyle(ChatFormatting.GRAY)), false);
            }
        }
        return 1;
    }

    private static int record(CommandSourceStack source, ServerPlayer sp) {
        var data = com.renanpontes.isekaimahou.core.MagicApi.get(sp);
        int best = data.counter(DepthsAffixes.COUNTER_BEST);
        int now = DepthsAffixes.runSeconds(sp);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.record",
                best > 0 ? DepthsAffixes.clock(best) : "—", data.counter(DepthsAffixes.COUNTER_CLEARS),
                now >= 0 ? DepthsAffixes.clock(now) : "—"), false);
        return 1;
    }

    /** {@code puzzle demo <tipo>} monta uma sala de teste; {@code puzzle solve} resolve o Selo mais próximo; {@code puzzle tp} leva até ele. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> puzzleCommands() {
        var demo = Commands.literal("demo");
        for (String kind : new String[] {"levers", "tiles", "light", "sequence"}) {
            demo.then(Commands.literal(kind).executes(c -> puzzleDemo(c.getSource(), c.getSource().getPlayerOrException(), kind)));
        }
        return Commands.literal("puzzle").then(demo)
                .then(Commands.literal("solve").executes(c -> puzzleSolve(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("tp").executes(c -> puzzleTp(c.getSource(), c.getSource().getPlayerOrException())))
                .then(enigmaCommands());
    }

    // ------------------------------------------------------------------ v1.0 r9: Enigmas de raciocínio

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enigmaCommands() {
        var root = Commands.literal("enigma");
        for (EnigmaType type : EnigmaType.values()) {
            root.then(Commands.literal(type.id())
                    .executes(c -> enigmaDemo(c.getSource(), c.getSource().getPlayerOrException(), type, 2))
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                            .executes(c -> enigmaDemo(c.getSource(), c.getSource().getPlayerOrException(), type,
                                    IntegerArgumentType.getInteger(c, "tier")))));
        }
        root.then(Commands.literal("all")
                .executes(c -> enigmaAll(c.getSource(), c.getSource().getPlayerOrException(), 2))
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                        .executes(c -> enigmaAll(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "tier")))));
        root.then(Commands.literal("chain").executes(c -> enigmaChain(c.getSource(), c.getSource().getPlayerOrException())));
        root.then(Commands.literal("reset").executes(c -> enigmaNearest(c.getSource(), c.getSource().getPlayerOrException(), false)));
        root.then(Commands.literal("hint").executes(c -> enigmaNearest(c.getSource(), c.getSource().getPlayerOrException(), true)));
        return root;
    }

    private static int enigmaDemo(CommandSourceStack source, ServerPlayer sp, EnigmaType type, int tier) {
        var be = EnigmaPlacer.demo(sp.serverLevel(), sp.blockPosition(), sp.getDirection(), type, tier, sp.getRandom().nextLong(), "", 0);
        if (be == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.puzzle_none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.enigma.demo", type.id(), tier), true);
        return 1;
    }

    /** Os 8 enigmas lado a lado (10 blocos entre eles), para testar tudo de uma vez. */
    private static int enigmaAll(CommandSourceStack source, ServerPlayer sp, int tier) {
        var dir = sp.getDirection();
        var right = dir.getClockWise();
        EnigmaType[] all = EnigmaType.values();
        for (int i = 0; i < all.length; i++) {
            BlockPos feet = sp.blockPosition().relative(right, (i - all.length / 2) * 10);
            EnigmaPlacer.demo(sp.serverLevel(), feet, dir, all[i], tier, sp.getRandom().nextLong(), "", 0);
        }
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.enigma.all", tier), true);
        return 1;
    }

    /** Dois enigmas em corrente: o da direita fica selado até o da esquerda ser resolvido (e ganha uma pista). */
    private static int enigmaChain(CommandSourceStack source, ServerPlayer sp) {
        var dir = sp.getDirection();
        var right = dir.getClockWise();
        String chain = "demo" + sp.getRandom().nextInt(1_000_000);
        EnigmaType[] order = EnigmaRun.order(sp.getRandom().nextLong());
        EnigmaPlacer.demo(sp.serverLevel(), sp.blockPosition().relative(right, -5), dir, order[0], 1, sp.getRandom().nextLong(), chain, 1);
        EnigmaPlacer.demo(sp.serverLevel(), sp.blockPosition().relative(right, 6), dir, order[1], 2, sp.getRandom().nextLong(), chain, 2);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.enigma.chain", order[0].id(), order[1].id()), true);
        return 1;
    }

    private static int enigmaNearest(CommandSourceStack source, ServerPlayer sp, boolean hint) {
        final PuzzleCoreBlockEntity[] found = {null};
        final double[] d = {48 * 48};
        PuzzleCoreBlockEntity.forEachLoaded(sp.level(), be -> {
            if (be.enigma() == null) return;
            double dist = be.getBlockPos().distSqr(sp.blockPosition());
            if (dist < d[0]) {
                d[0] = dist;
                found[0] = be;
            }
        });
        PuzzleCoreBlockEntity best = found[0];
        if (best == null || best.enigma() == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.enigma.none"));
            return 0;
        }
        if (hint) best.enigma().giftClueForTest(sp.serverLevel());
        else best.enigma().forceReset(sp.serverLevel());
        BlockPos p = best.getBlockPos();
        String label = best.label();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.puzzle_at", label, p.getX(), p.getY(), p.getZ()), true);
        return 1;
    }

    private static int puzzleDemo(CommandSourceStack source, ServerPlayer sp, String kind) {
        ServerLevel level = sp.serverLevel();
        var template = level.getStructureManager().get(IsekaiMahou.id("depths_dungeon/puzzle_" + kind));
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.puzzle_none"));
            return 0;
        }
        // a sala (11x9x11) nasce com o piso na altura dos pés, 3 blocos à frente (sul) do jogador
        BlockPos at = sp.blockPosition().offset(-5, -1, 3);
        template.get().placeInWorld(level, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.random, 2);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.puzzle_demo", kind), true);
        return 1;
    }

    private static int puzzleSolve(CommandSourceStack source, ServerPlayer sp) {
        PuzzleCoreBlockEntity core = PuzzleCoreBlockEntity.nearest(sp.level(), sp.blockPosition(), 32);
        if (core == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.puzzle_none"));
            return 0;
        }
        core.solve(sp.serverLevel());
        BlockPos p = core.getBlockPos();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.puzzle_at", core.label(), p.getX(), p.getY(), p.getZ()), true);
        return 1;
    }

    private static int puzzleTp(CommandSourceStack source, ServerPlayer sp) {
        PuzzleCoreBlockEntity core = PuzzleCoreBlockEntity.nearest(sp.level(), sp.blockPosition(), 160);
        if (core == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.puzzle_none"));
            return 0;
        }
        BlockPos p = core.getBlockPos();
        sp.teleportTo(sp.serverLevel(), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.puzzle_at", core.label(), p.getX(), p.getY(), p.getZ()), true);
        return 1;
    }

    private static int where(CommandSourceStack source, ServerPlayer sp) {
        int floor = DepthsDungeon.floorAt(sp.serverLevel(), sp.blockPosition());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.where", floor), false);
        return 1;
    }

    private static int spawnBoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = ahead(source, 7.0);
        EntityType<?> type = DepthsRegistries.WORM_KING.get();
        Entity boss = type.spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.spawn_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.spawn_done", name), true);
        return 1;
    }

    /** Gera um Geodo de Mana na frente do jogador (teste da feature). */
    private static int geode(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(ahead(source, 14.0));
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(GEODE);
        boolean ok = feature != null && feature.place(level, level.getChunkSource().getGenerator(), level.random, at);
        if (!ok) {
            source.sendFailure(Component.translatable("command.isekai_mahou.depths.geode_fail"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.depths.geode_done", at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    private static Vec3 ahead(CommandSourceStack source, double dist) {
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) pos = viewer.position().add(look.normalize().scale(dist));
        }
        return pos;
    }

    private DepthsCommands() {}
}
