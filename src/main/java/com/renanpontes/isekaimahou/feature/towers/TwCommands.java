package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.towers.block.FloorCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou towers ...} (só op):
 * <ul>
 *   <li>{@code give} · {@code tp [stone|frost|sand|forest|sunken|floating]} · {@code island} (Ilha Voadora mais próxima)</li>
 *   <li>{@code boss} · {@code floor} (vence à força o andar onde você está) · {@code reset} (esquece a torre)</li>
 *   <li>{@code affix list} · {@code affix set <a,b,...>} · {@code affix none} · {@code affix random} — afixos do PRÓXIMO
 *       despertar do núcleo mais perto (saia do andar 1 min ou use um andar adormecido)</li>
 *   <li>{@code records} · {@code records clear}</li>
 * </ul>
 * E {@code /mahou spawnboss tower_kobold_lord}.
 */
public final class TwCommands {
    private static final String[] VARIANTS = {"stone", "frost", "sand", "forest", "sunken", "floating"};

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("towers")
                    .then(Commands.literal("give").executes(c -> give(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("tp").executes(c -> tp(c.getSource(), c.getSource().getPlayerOrException(), null))
                            .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests((c, b) -> {
                                        for (String v : VARIANTS) b.suggest(v);
                                        return b.buildFuture();
                                    })
                                    .executes(c -> tp(c.getSource(), c.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(c, "variant")))))
                    .then(Commands.literal("island").executes(c -> island(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("boss").executes(c -> spawnBoss(c.getSource())))
                    .then(Commands.literal("floor").executes(c -> clearFloor(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("reset").executes(c -> reset(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("affix")
                            .then(Commands.literal("list").executes(c -> affixList(c.getSource(), c.getSource().getPlayerOrException())))
                            .then(Commands.literal("none").executes(c -> affixSet(c.getSource(), c.getSource().getPlayerOrException(), "")))
                            .then(Commands.literal("random").executes(c -> affixSet(c.getSource(), c.getSource().getPlayerOrException(), null)))
                            .then(Commands.literal("set").then(Commands.argument("ids", StringArgumentType.greedyString())
                                    .suggests((c, b) -> {
                                        for (TowerAffix a : TowerAffix.values()) b.suggest(a.id());
                                        return b.buildFuture();
                                    })
                                    .executes(c -> affixSet(c.getSource(), c.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(c, "ids"))))))
                    .then(Commands.literal("records").executes(c -> records(c.getSource()))
                            .then(Commands.literal("clear").executes(c -> recordsClear(c.getSource())))));
            root.then(MahouCommands.op("spawnboss").then(Commands.literal("tower_kobold_lord").executes(c -> spawnBoss(c.getSource()))));
        });
    }

    private static int give(ServerPlayer sp) {
        sp.getInventory().placeItemBackInInventory(new ItemStack(TwRegistries.TELEPORT_CRYSTAL.get(), 8));
        sp.getInventory().placeItemBackInInventory(new ItemStack(TwRegistries.TWIN_NOIR.get()));
        sp.getInventory().placeItemBackInInventory(new ItemStack(TwRegistries.TWIN_AZURE.get()));
        sp.getInventory().placeItemBackInInventory(new ItemStack(TwRegistries.KOBOLD_LORD_EGG.get()));
        for (var item : TwRegistries.worldItems()) sp.getInventory().placeItemBackInInventory(new ItemStack(item.get()));
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.towers.given").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /** Estrutura mais próxima do conjunto (procura a partir do jogador, ou do spawn se ele está noutra dimensão). */
    private static StructureStart nearest(ServerLevel ow, ServerPlayer sp, Optional<? extends HolderSet<Structure>> set, BlockPos[] atOut) {
        BlockPos origin = sp.level() == ow ? sp.blockPosition() : ow.getSharedSpawnPos();
        Pair<BlockPos, Holder<Structure>> found = set.isEmpty() ? null
                : ow.getChunkSource().getGenerator().findNearestMapStructure(ow, set.get(), origin, 100, false);
        if (found == null) return null;
        BlockPos at = found.getFirst();
        atOut[0] = at;
        return ow.structureManager().getStartForStructure(SectionPos.of(at), found.getSecond().value(),
                ow.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
    }

    private static int tp(CommandSourceStack source, ServerPlayer sp, String variant) {
        ServerLevel ow = source.getServer().getLevel(Level.OVERWORLD);
        if (ow == null) return 0;
        Registry<Structure> reg = ow.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<? extends HolderSet<Structure>> set;
        if (variant == null) {
            set = reg.getTag(TowerData.TOWERS);
        } else {
            set = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("tower_" + variant))).map(HolderSet::direct);
        }
        BlockPos[] at = new BlockPos[1];
        StructureStart start = nearest(ow, sp, set, at);
        if (at[0] == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.towers.not_found"));
            return 0;
        }
        Vec3 dest;
        if (start != null && start.isValid()) {
            dest = Vec3.atBottomCenterOf(TowerData.entrance(ow, start));
        } else {
            dest = Vec3.atBottomCenterOf(ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at[0]));
        }
        sp.teleportTo(ow, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());
        Vec3 d = dest;
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.tp", (int) d.x, (int) d.y, (int) d.z), true);
        return 1;
    }

    /** Leva para a borda do poço da Ilha Voadora mais próxima. */
    private static int island(CommandSourceStack source, ServerPlayer sp) {
        ServerLevel ow = source.getServer().getLevel(Level.OVERWORLD);
        if (ow == null) return 0;
        BlockPos[] at = new BlockPos[1];
        StructureStart start = nearest(ow, sp, ow.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(SkySites.ISLANDS), at);
        SkySites.Site site = start != null && start.isValid() ? SkySites.siteOf(TowerData.pathOf(ow, start), start) : null;
        if (site == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.towers.island_not_found"));
            return 0;
        }
        sp.teleportTo(ow, site.wellX() + 2.5, site.feetY(), site.wellZ() + 0.5, sp.getYRot(), sp.getXRot());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.island_tp", site.wellX(), site.feetY(), site.wellZ()), true);
        return 1;
    }

    private static int spawnBoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) pos = viewer.position().add(look.normalize().scale(6.0));
        }
        Entity boss = TwRegistries.KOBOLD_LORD.get().spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.towers.spawn_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.spawn_done", name), true);
        return 1;
    }

    /** Núcleo mais perto (até 16 blocos, chunks carregados). */
    private static FloorCoreBlockEntity nearestCore(ServerLevel sl, BlockPos pos) {
        FloorCoreBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (int cx = (pos.getX() >> 4) - 1; cx <= (pos.getX() >> 4) + 1; cx++) {
            for (int cz = (pos.getZ() >> 4) - 1; cz <= (pos.getZ() >> 4) + 1; cz++) {
                if (!sl.hasChunk(cx, cz)) continue;
                for (BlockEntity be : sl.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof FloorCoreBlockEntity core)) continue;
                    BlockPos p = be.getBlockPos();
                    if (p.distSqr(pos) > 16 * 16) continue;
                    // Prefere o núcleo do mesmo andar (altura pesa mais)
                    int dy = p.getY() - pos.getY();
                    double d = p.distSqr(pos) + 15.0 * dy * dy;
                    if (d < bd) {
                        bd = d;
                        best = core;
                    }
                }
            }
        }
        return best;
    }

    private static FloorCoreBlockEntity coreOrFail(CommandSourceStack source, ServerPlayer sp) {
        FloorCoreBlockEntity core = nearestCore(sp.serverLevel(), sp.blockPosition());
        if (core == null) source.sendFailure(Component.translatable("command.isekai_mahou.towers.no_core"));
        return core;
    }

    private static int clearFloor(CommandSourceStack source, ServerPlayer sp) {
        FloorCoreBlockEntity core = coreOrFail(source, sp);
        if (core == null) return 0;
        core.clear(sp.serverLevel(), true);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.floor_cleared", core.floorLabel()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer sp) {
        StructureStart s = TowerData.towerAt(sp.serverLevel(), sp.blockPosition());
        if (!s.isValid()) {
            source.sendFailure(Component.translatable("command.isekai_mahou.towers.not_found"));
            return 0;
        }
        TowerData.get(sp.serverLevel()).forget(TowerData.keyOf(s));
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.reset"), true);
        return 1;
    }

    // ------------------------------------------------------------------ afixos

    private static int affixList(CommandSourceStack source, ServerPlayer sp) {
        List<TowerAffix> all = List.of(TowerAffix.values());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.affix_all", TowerAffix.list(all)), false);
        FloorCoreBlockEntity core = nearestCore(sp.serverLevel(), sp.blockPosition());
        if (core != null) {
            List<TowerAffix> now = core.affixes();
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.affix_now", core.floorLabel(),
                    now.isEmpty() ? Component.literal("—") : TowerAffix.list(now)), false);
        }
        return 1;
    }

    /** {@code ids}: null = voltar a sortear; "" = nenhum; "a,b c" = esses. */
    private static int affixSet(CommandSourceStack source, ServerPlayer sp, String ids) {
        FloorCoreBlockEntity core = coreOrFail(source, sp);
        if (core == null) return 0;
        if (ids == null) {
            core.forceAffixes(null);
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.affix_random", core.floorLabel()), true);
            return 1;
        }
        List<TowerAffix> list = new ArrayList<>();
        for (String part : ids.split("[,\\s]+")) {
            if (part.isEmpty()) continue;
            TowerAffix a = TowerAffix.byId(part.toLowerCase(java.util.Locale.ROOT));
            if (a == null) {
                source.sendFailure(Component.translatable("command.isekai_mahou.towers.affix_unknown", part));
                return 0;
            }
            if (!list.contains(a)) list.add(a);
        }
        core.forceAffixes(list);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.affix_set", core.floorLabel(),
                list.isEmpty() ? Component.literal("—") : TowerAffix.list(list)), true);
        return 1;
    }

    // ------------------------------------------------------------------ recordes

    private static int records(CommandSourceStack source) {
        TowerRecords data = TowerRecords.get(source.getServer());
        List<String> keys = data.keys();
        if (keys.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.records_empty"), false);
            return 0;
        }
        for (String key : keys) {
            TowerRecords.Entry e = data.bestEntry(key);
            if (e == null) continue;
            int slash = key.lastIndexOf('/');
            String variant = slash < 0 ? key : key.substring(0, slash);
            int floor = slash < 0 ? 0 : parse(key.substring(slash + 1));
            Component label = floor >= TowerData.FLOORS ? Component.translatable("tower.isekai_mahou.summit_title")
                    : Component.translatable("tower.isekai_mahou.floor_title", floor + 1);
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.records_line",
                    Component.translatable("tower.isekai_mahou.variant." + variant), label, TowerRecords.format(e.ticks()), e.name(),
                    e.affixes()), false);
        }
        return keys.size();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int recordsClear(CommandSourceStack source) {
        TowerRecords.get(source.getServer()).clear();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.towers.records_cleared"), true);
        return 1;
    }

    private TwCommands() {}
}
