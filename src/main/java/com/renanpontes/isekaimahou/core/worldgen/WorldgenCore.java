package com.renanpontes.isekaimahou.core.worldgen;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Inicialização do núcleo v0.9 — A3 (docs/V09_DESIGN.md §4, docs/v09/API_worldgen.md): os tipos de posicionamento
 * de estrutura {@code isekai_mahou:random_spread_multi} (várias zonas de exclusão) e {@code isekai_mahou:kingdom_ring}
 * (capital de reino num anel em volta do spawn), e o comando {@code /mahou worldgen kingdoms}.
 * A barreira "nada do vanilla dentro das cidades" é o mixin {@code StructurePlacementMixin} + {@link CityExclusion}.
 */
public final class WorldgenCore {
    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, IsekaiMahou.MODID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<RandomSpreadMultiPlacement>>
            RANDOM_SPREAD_MULTI = PLACEMENTS.register("random_spread_multi", () -> () -> RandomSpreadMultiPlacement.CODEC);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<KingdomRingPlacement>>
            KINGDOM_RING = PLACEMENTS.register("kingdom_ring", () -> () -> KingdomRingPlacement.CODEC);

    /** Os três reinos: id do reino, conjunto de estruturas da capital e cor do nome. */
    private record Kingdom(String id, String set, int color) {}

    private static final List<Kingdom> KINGDOMS = List.of(
            new Kingdom("aldoria", "capital_city", 0x3F6FD8),
            new Kingdom("sahra", "capital_sahra", 0xE0A030),
            new Kingdom("nordheim", "capital_nordheim", 0x8FD3FF));

    public static void init(IEventBus modBus) {
        PLACEMENTS.register(modBus);
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("worldgen")
                .then(Commands.literal("kingdoms").executes(c -> listKingdoms(c.getSource())))));
    }

    /** Lista onde as capitais dos reinos vão nascer neste mundo (sem carregar chunks). */
    private static int listKingdoms(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.isekai_mahou.worldgen.kingdoms.wrong_dim"));
            return 0;
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        Vec3 from = source.getPosition();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.worldgen.kingdoms.header")
                .withStyle(ChatFormatting.GOLD), false);
        int found = 0;
        for (Kingdom k : KINGDOMS) {
            MutableComponent name = Component.translatable("worldgen.isekai_mahou.kingdom." + k.id()).withColor(k.color());
            ChunkPos pos = sets.getHolder(ResourceKey.create(Registries.STRUCTURE_SET, IsekaiMahou.id(k.set())))
                    .filter(Holder::isBound)
                    .filter(h -> state.possibleStructureSets().contains(h))
                    .map(h -> capitalChunk(state, h.value().placement()))
                    .orElse(null);
            if (pos == null) {
                source.sendSuccess(() -> Component.translatable("command.isekai_mahou.worldgen.kingdoms.missing", name)
                        .withStyle(ChatFormatting.GRAY), false);
                continue;
            }
            found++;
            int x = pos.getMiddleBlockX();
            int z = pos.getMiddleBlockZ();
            int dist = Mth.floor(Math.sqrt(Mth.square(x - from.x) + Mth.square(z - from.z)));
            Component coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", x, "~", z))
                    .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " ~ " + z))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("chat.coordinates.tooltip"))));
            source.sendSuccess(() -> Component.translatable("command.isekai_mahou.worldgen.kingdoms.entry", name, coords,
                    dist), false);
        }
        return found;
    }

    private static ChunkPos capitalChunk(ChunkGeneratorStructureState state, StructurePlacement placement) {
        if (placement instanceof KingdomRingPlacement kingdom) return kingdom.chosen(state);
        if (placement instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> ring = state.getRingPositionsFor(rings);
            return ring == null || ring.isEmpty() ? null : ring.get(0);
        }
        return null;
    }

    private WorldgenCore() {}
}
