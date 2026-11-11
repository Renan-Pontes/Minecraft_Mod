package com.renanpontes.isekaimahou.feature.ascension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * "Altar público mais próximo" para o rastreador de objetivos. Só para quem está no LIMITE do Círculo (pronto para
 * ascender). Ordem de busca:
 * <ol>
 *   <li>altares públicos já conhecidos ({@link AscensionAltars} — Núcleos com {@code PublicAltar} que já carregaram);</li>
 *   <li>se não há um conhecido a menos de {@value #KNOWN_GOOD} blocos: busca de ESTRUTURA (cidades {@code #isekai_mahou:city},
 *       que têm o altar na Igreja/Academia, e o {@code isekai_mahou:ascension_sanctum}) — uma busca por vez no servidor,
 *       no máximo 1 por segundo, com cache por jogador. Ao chegar perto, o Núcleo de verdade carrega, se registra e a
 *       seta passa a apontar para ele; cidade antiga sem altar vira "sem altar" e é ignorada.</li>
 * </ol>
 * O nome vem do {@link Places} (a cidade onde o altar está) ou do tipo de estrutura.
 */
public final class AscensionAltarFinder {
    /** Um altar conhecido a menos disso dispensa a busca de estrutura. */
    static final int KNOWN_GOOD = 1200;
    private static final int UPDATE_EVERY = 40;
    private static final int SEARCH_CHUNKS = 64;
    /** Refaz a busca depois de andar isso (blocos) ou depois de 10 min. */
    private static final int MOVE_RESEARCH = 600;
    private static final long RESEARCH_TICKS = 12000;
    private static final TagKey<Structure> CITY_TAG = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("city"));
    private static final ResourceKey<Structure> SANCTUM = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("ascension_sanctum"));

    /** O que foi mandado ao cliente por último (null = "nenhum"). */
    public record Hint(ResourceKey<Level> dim, BlockPos pos, Component name, boolean exact) {}

    private record Located(ResourceKey<Level> dim, BlockPos origin, long time, @Nullable BlockPos pos, boolean sanctum) {}

    private static final Map<UUID, Hint> SENT = new HashMap<>();
    private static final Set<UUID> SENT_NONE = new HashSet<>();
    private static final Map<UUID, Located> LOCATED = new HashMap<>();
    private static final Map<UUID, Integer> ARRIVED = new HashMap<>();
    private static final Deque<UUID> QUEUE = new ArrayDeque<>();
    /** Pontos de estrutura onde o jogador chegou e não havia altar (cidades de mundos antigos). */
    private static final Set<Long> DUDS = new HashSet<>();

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 20 == 5) {
            UUID id = QUEUE.pollFirst();
            if (id != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) locate(p);
            }
        }
        if (now % UPDATE_EVERY != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) update(p);
    }

    /** Pede uma atualização já (ex.: chegou ao limite do Círculo, mudou de dimensão). */
    public static void refresh(ServerPlayer p) {
        update(p);
    }

    public static void forget(UUID id) {
        SENT.remove(id);
        SENT_NONE.remove(id);
        LOCATED.remove(id);
        ARRIVED.remove(id);
        QUEUE.remove(id);
    }

    public static void clearAll() {
        SENT.clear();
        SENT_NONE.clear();
        LOCATED.clear();
        ARRIVED.clear();
        QUEUE.clear();
        DUDS.clear();
    }

    /** Altar mais próximo agora (para o comando), ou null. */
    @Nullable
    public static Hint current(ServerPlayer p) {
        return SENT.get(p.getUUID());
    }

    /**
     * Comando {@code /mahou ascension altar}: atualiza e, se ainda não há nada e a busca de estrutura deste jogador
     * nunca rodou nesta dimensão, roda-a já (uma busca por pedido do jogador, igual a um /locate).
     */
    @Nullable
    public static Hint lookup(ServerPlayer p) {
        update(p);
        Hint hint = SENT.get(p.getUUID());
        if (hint != null || !eligible(p)) return hint;
        Located loc = LOCATED.get(p.getUUID());
        if (loc == null || !loc.dim().equals(p.level().dimension())) {
            QUEUE.remove(p.getUUID());
            locate(p);
        }
        return SENT.get(p.getUUID());
    }

    private static boolean eligible(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        return d.path() != MagicPath.NONE && d.atLevelCap() && d.circle() < Progression.MAX_CIRCLE;
    }

    private static void update(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!eligible(p) || p.isSpectator()) {
            send(p, null);
            return;
        }
        ServerLevel level = p.serverLevel();
        BlockPos here = p.blockPosition();
        BlockPos known = nearestValid(level, here);
        double knownDist = known == null ? Double.MAX_VALUE : horizontal(known, here);

        Located loc = LOCATED.get(id);
        if (loc != null && !loc.dim().equals(level.dimension())) loc = null;
        if (knownDist > KNOWN_GOOD) {
            boolean stale = loc == null || horizontal(loc.origin(), here) > MOVE_RESEARCH
                    || level.getGameTime() - loc.time() > RESEARCH_TICKS;
            if (stale && !QUEUE.contains(id)) QUEUE.addLast(id);
        }
        BlockPos located = loc == null ? null : loc.pos();
        if (located != null && DUDS.contains(located.asLong())) located = null;

        // Chegou ao ponto da estrutura e nenhum altar apareceu por perto: cidade sem altar (mundo antigo).
        if (located != null) {
            boolean altarThere = known != null && horizontal(known, located) <= 200;
            if (!altarThere && horizontal(located, here) <= 48) {
                int n = ARRIVED.merge(id, 1, Integer::sum);
                if (n >= 4) {
                    DUDS.add(located.asLong());
                    ARRIVED.remove(id);
                    located = null;
                }
            } else {
                ARRIVED.remove(id);
            }
        }

        Hint hint = null;
        if (known != null && (located == null || knownDist <= horizontal(located, here) + 32)) {
            hint = new Hint(level.dimension(), known, nameAt(level, known), true);
        } else if (located != null) {
            Component name = Component.translatable(loc.sanctum() ? "ascension.isekai_mahou.altar.sanctum"
                    : "ascension.isekai_mahou.altar.city");
            hint = new Hint(level.dimension(), located, name, false);
        }
        send(p, hint);
    }

    /** Altar conhecido mais próximo, esquecendo os que sumiram (chunk carregado e o Núcleo público não está mais lá). */
    @Nullable
    private static BlockPos nearestValid(ServerLevel level, BlockPos from) {
        List<BlockPos> list = AscensionAltars.known(level.dimension());
        if (list.isEmpty()) return null;
        List<BlockPos> gone = new ArrayList<>();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : list) {
            if (level.isLoaded(p) && !AscensionAltarGuard.isPublicCore(level, p)) {
                gone.add(p);
                continue;
            }
            double d = horizontal(p, from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        for (BlockPos p : gone) AscensionAltars.remove(level, p);
        return best;
    }

    private static Component nameAt(ServerLevel level, BlockPos pos) {
        Places.Place place = Places.at(level, pos);
        if (place != null) return Component.translatable("ascension.isekai_mahou.altar.in", place.name());
        return Component.translatable("ascension.isekai_mahou.altar.public");
    }

    // =====================================================================================
    //  Busca de estrutura (uma por vez)
    // =====================================================================================
    private static void locate(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos origin = p.blockPosition();
        BlockPos found = null;
        boolean sanctum = false;
        try {
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            List<Holder<Structure>> holders = new ArrayList<>();
            registry.getTag(CITY_TAG).ifPresent(set -> set.forEach(holders::add));
            registry.getHolder(SANCTUM).ifPresent(h -> {
                if (!holders.contains(h)) holders.add(h);
            });
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            int minSpacing = Integer.MAX_VALUE;
            List<Holder<Structure>> usable = new ArrayList<>();
            for (Holder<Structure> h : holders) {
                List<StructurePlacement> placements = state.getPlacementsForStructure(h);
                if (placements.isEmpty()) continue;
                usable.add(h);
                for (StructurePlacement sp : placements) {
                    if (sp instanceof RandomSpreadStructurePlacement spread) minSpacing = Math.min(minSpacing, Math.max(1, spread.spacing()));
                }
            }
            if (!usable.isEmpty()) {
                int radius = minSpacing == Integer.MAX_VALUE ? 8 : Mth.clamp(Mth.ceil(SEARCH_CHUNKS / (float) minSpacing), 1, 8);
                Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(usable), origin, radius, false);
                if (result != null) {
                    found = result.getFirst();
                    sanctum = result.getSecond().is(SANCTUM);
                    if (level.isLoaded(found)) {
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
                        found = new BlockPos(found.getX(), Math.max(level.getMinBuildHeight() + 1, y), found.getZ());
                    } else {
                        found = new BlockPos(found.getX(), origin.getY(), found.getZ());
                    }
                }
            }
        } catch (RuntimeException e) {
            // Busca de estrutura nunca derruba o servidor: segue sem seta.
            IsekaiMahou.LOGGER.debug("Ascensão: falha ao procurar altar público", e);
            found = null;
        }
        LOCATED.put(p.getUUID(), new Located(level.dimension(), origin, level.getGameTime(), found, sanctum));
        update(p);
    }

    // =====================================================================================
    //  Envio
    // =====================================================================================
    private static void send(ServerPlayer p, @Nullable Hint hint) {
        UUID id = p.getUUID();
        if (hint == null) {
            if (SENT_NONE.add(id)) {
                SENT.remove(id);
                AscensionNetwork.sendAltar(p, null);
            }
            return;
        }
        Hint old = SENT.get(id);
        if (old != null && old.exact() == hint.exact() && old.pos().equals(hint.pos()) && old.dim().equals(hint.dim())
                && Objects.equals(old.name(), hint.name())) return;
        SENT.put(id, hint);
        SENT_NONE.remove(id);
        AscensionNetwork.sendAltar(p, hint);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private AscensionAltarFinder() {}
}
