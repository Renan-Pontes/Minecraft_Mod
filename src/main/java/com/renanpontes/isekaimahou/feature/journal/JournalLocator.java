package com.renanpontes.isekaimahou.feature.journal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalLocate;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Localizar" do Diário (servidor): quando o objetivo atual tem {@code locate}, procura a estrutura (ou o bioma) mais
 * próximo do jogador e manda a posição ao cliente (seta + distância no rastreador; o Espírito Guia voa para lá).
 * <ul>
 *   <li>Raio de ~{@value #SEARCH_CHUNKS} chunks (convertido em "anéis" do espaçamento de cada estrutura).</li>
 *   <li>Cache por jogador: só procura de novo se o objetivo mudar, as candidatas mudarem (santuário vencido...),
 *       o jogador trocar de dimensão ou andar mais de {@value #MOVE_RESEARCH} blocos desde a última busca.</li>
 *   <li>No máximo UMA busca por tick do servidor (fila), para não travar o jogo com vários jogadores.</li>
 *   <li>v1.0: duas trilhas por jogador ({@link JournalTargets#MAIN} = história, {@link JournalTargets#CLASS} = rota da
 *       classe), cada uma com o seu alvo, cache e pacote. Alvos do tipo {@code place} (lugares do {@code Places}, ex.: o
 *       Palácio do Mundo Oco) não precisam de busca no mundo.</li>
 * </ul>
 */
public final class JournalLocator {
    /** Raio de busca em chunks (~1600 blocos). */
    static final int SEARCH_CHUNKS = 100;
    /** Distância andada (blocos, no plano) que pede uma busca nova. */
    static final int MOVE_RESEARCH = 500;
    /**
     * Busca de bioma (chefes sem covil, ex.: Rei Slime nos pântanos): raio e passo da amostragem, em blocos. Orçamento:
     * (2·1600/64 + 1)² ≈ 2,6 mil amostras da fonte de biomas no pior caso (nada no raio), numa busca da fila (1 por tick).
     */
    static final int BIOME_RADIUS = 1600;
    static final int BIOME_STEP = 64;

    /** Jogador + trilha (história ou rota da classe). */
    private record Key(UUID id, int track) {}

    /** Resultado de uma busca para um jogador. {@code found} null = nada por perto. */
    private record Cache(String step, String signature, ResourceKey<Level> dim, BlockPos origin,
                         @Nullable BlockPos found, boolean yKnown) {}

    /** Pedido na fila (o que procurar e para qual passo). */
    private record Request(String step, String signature, List<JournalLocate.Target> targets) {}

    private static final Map<Key, Cache> CACHE = new HashMap<>();
    private static final Map<Key, Request> PENDING = new HashMap<>();
    private static final Deque<Key> QUEUE = new ArrayDeque<>();
    /** Último pacote mandado a cada jogador/trilha (evita reenviar o mesmo alvo a cada checagem). */
    private static final Map<Key, JournalNetwork.Target> SENT = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    // =====================================================================================
    //  Chamado pelo JournalService a cada avaliação do capítulo em andamento
    // =====================================================================================
    /** Atualiza o alvo da história para o passo atual (null = sem objetivo). */
    static void update(ServerPlayer player, @Nullable JournalChapter ch, @Nullable JournalStep step) {
        update(player, JournalTargets.MAIN, ch, step);
    }

    /** Atualiza o alvo da trilha informada para o passo atual (null = sem objetivo). */
    static void update(ServerPlayer player, int track, @Nullable JournalChapter ch, @Nullable JournalStep step) {
        Key id = new Key(player.getUUID(), track);
        if (ch == null || step == null || step.locate() == null) {
            clear(player, track);
            return;
        }
        JournalLocate loc = step.locate();
        ServerLevel level = player.serverLevel();
        String uid = step.uid(ch);
        if (loc.dimension() != null && !loc.dimension().equals(level.dimension().location())) {
            // Estrutura de outra dimensão: nada para apontar daqui (a dica explica como chegar lá).
            forget(id);
            send(player, JournalNetwork.Target.none(track));
            return;
        }
        List<JournalLocate.Target> targets = loc.active(MagicApi.get(player), player);
        if (targets.isEmpty()) {
            forget(id);
            send(player, JournalNetwork.Target.none(track));
            return;
        }
        String signature = signature(targets);
        Cache c = CACHE.get(id);
        boolean stale = c == null || !c.step().equals(uid) || !c.signature().equals(signature)
                || !c.dim().equals(level.dimension()) || horizontal(c.origin(), player.blockPosition()) > MOVE_RESEARCH
                // Lugar do Places ainda não registrado (o reino monta o palácio na 1ª visita): consulta barata, tenta de novo.
                || c.found() == null && targets.stream().allMatch(JournalLocate.Target::place);
        if (stale) {
            Request old = PENDING.put(id, new Request(uid, signature, targets));
            if (old == null) QUEUE.add(id);
            return;
        }
        // Altura desconhecida e o chunk do alvo carregou: completa com a superfície.
        if (c.found() != null && !c.yKnown() && columnLoaded(level, c.found())) {
            BlockPos surface = surface(level, c.found());
            c = new Cache(c.step(), c.signature(), c.dim(), c.origin(), surface, true);
            CACHE.put(id, c);
        }
        send(player, packet(id, c));
    }

    /** Sem objetivo com alvo na história: esquece a busca e avisa o cliente (uma vez). */
    static void clear(ServerPlayer player) {
        clear(player, JournalTargets.MAIN);
    }

    /** Sem objetivo com alvo na trilha: esquece a busca e avisa o cliente (uma vez). */
    static void clear(ServerPlayer player, int track) {
        Key id = new Key(player.getUUID(), track);
        forget(id);
        send(player, JournalNetwork.Target.none(track));
    }

    /** Saída do jogador: apaga tudo dele (as duas trilhas). */
    static void forgetAll(UUID player) {
        for (int t = 0; t < JournalTargets.TRACKS; t++) {
            Key id = new Key(player, t);
            forget(id);
            SENT.remove(id);
        }
    }

    static void clearAll() {
        CACHE.clear();
        PENDING.clear();
        QUEUE.clear();
        SENT.clear();
        FAILED.clear();
    }

    private static void forget(Key id) {
        CACHE.remove(id);
        if (PENDING.remove(id) != null) QUEUE.remove(id);
    }

    /** Fila: uma busca por tick do servidor. */
    static void tick(MinecraftServer server) {
        Key id = QUEUE.pollFirst();
        if (id == null) return;
        Request req = PENDING.remove(id);
        ServerPlayer player = server.getPlayerList().getPlayer(id.id());
        if (req == null || player == null) return;
        search(player, id, req);
    }

    /**
     * Alvo atual do jogador (para o Espírito Guia). Se a busca está na fila, faz agora.
     * @return a posição (na dimensão atual do jogador), ou null se não há alvo
     */
    @Nullable
    static BlockPos targetNow(ServerPlayer player) {
        return targetNow(player, JournalTargets.MAIN);
    }

    /** Alvo atual da trilha (busca agora se estava na fila); null sem alvo nesta dimensão. */
    @Nullable
    static BlockPos targetNow(ServerPlayer player, int track) {
        Key id = new Key(player.getUUID(), track);
        Request req = PENDING.remove(id);
        if (req != null) {
            QUEUE.remove(id);
            search(player, id, req);
        }
        Cache c = CACHE.get(id);
        if (c == null || c.found() == null || !c.dim().equals(player.level().dimension())) return null;
        return c.found();
    }

    /** A última busca (nesta dimensão) não achou nada no raio? (o Espírito Guia pede para explorar mais longe). */
    static boolean nothingNearby(ServerPlayer player) {
        return nothingNearby(player, JournalTargets.MAIN);
    }

    static boolean nothingNearby(ServerPlayer player, int track) {
        Cache c = CACHE.get(new Key(player.getUUID(), track));
        return c != null && c.found() == null && c.dim().equals(player.level().dimension());
    }

    /** O passo atual tem alvo em OUTRA dimensão? (o Espírito Guia avisa em vez de voar). */
    static boolean targetElsewhere(ServerPlayer player, @Nullable JournalChapter ch, @Nullable JournalStep step) {
        if (ch == null || step == null || step.locate() == null || step.locate().dimension() == null) return false;
        return !step.locate().dimension().equals(player.level().dimension().location());
    }

    /** Busca forçada (comando de teste): ignora o cache. */
    @Nullable
    static BlockPos forceSearch(ServerPlayer player, JournalChapter ch, JournalStep step) {
        int track = ch.isClassRoute() ? JournalTargets.CLASS : JournalTargets.MAIN;
        forget(new Key(player.getUUID(), track));
        update(player, track, ch, step);
        return targetNow(player, track);
    }

    // =====================================================================================
    //  Busca
    // =====================================================================================
    private static void search(ServerPlayer player, Key key, Request req) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        BlockPos found = null;
        boolean yKnown = false;
        try {
            found = nearest(level, req.targets(), origin);
            if (found != null && req.targets().stream().allMatch(JournalLocate.Target::place)) {
                // Lugares do Places têm a altura certa (o centro do lugar): nada de "superfície" (o teto do palácio).
                yKnown = true;
            } else if (found != null && columnLoaded(level, found)) {
                found = surface(level, found);
                yKnown = true;
            }
        } catch (RuntimeException e) {
            // Busca de estrutura nunca pode derrubar o servidor: registra uma vez por passo e segue sem seta.
            if (FAILED.add(req.step())) IsekaiMahou.LOGGER.warn("Diario: falha ao localizar o alvo de {}", req.step(), e);
        }
        Cache c = new Cache(req.step(), req.signature(), level.dimension(), origin, found, yKnown);
        CACHE.put(key, c);
        send(player, packet(key, c));
    }

    /**
     * Candidata mais próxima de {@code origin}: estrutura (pelo posicionamento do mundo) ou bioma (amostragem da fonte de
     * biomas na altura do mar). Null = nenhuma no raio, ou nenhuma existe nesta dimensão.
     */
    @Nullable
    private static BlockPos nearest(ServerLevel level, List<JournalLocate.Target> targets, BlockPos origin) {
        List<JournalLocate.Target> structures = new ArrayList<>(targets.size());
        List<JournalLocate.Target> biomes = new ArrayList<>(2);
        BlockPos best = null;
        for (JournalLocate.Target t : targets) {
            if (t.place()) {
                // Lugar registrado no Places (palácio, corte, hub): posição exata, sem busca no mundo.
                Places.Place p = Places.get(level, t.id().getPath());
                if (p != null && p.dim().equals(level.dimension())
                        && (best == null || horizontal(origin, p.center()) < horizontal(origin, best))) best = p.center();
                continue;
            }
            (t.biome() ? biomes : structures).add(t);
        }
        if (!structures.isEmpty()) {
            BlockPos st = nearestStructure(level, structures, origin);
            if (st != null && (best == null || horizontal(origin, st) < horizontal(origin, best))) best = st;
        }
        if (!biomes.isEmpty()) {
            BlockPos b = nearestBiome(level, biomes, origin);
            if (b != null && (best == null || horizontal(origin, b) < horizontal(origin, best))) best = b;
        }
        return best;
    }

    @Nullable
    private static BlockPos nearestStructure(ServerLevel level, List<JournalLocate.Target> targets, BlockPos origin) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        for (JournalLocate.Target t : targets) {
            if (t.tag()) {
                registry.getTag(TagKey.create(Registries.STRUCTURE, t.id())).ifPresent(set -> set.forEach(h -> {
                    if (!holders.contains(h)) holders.add(h);
                }));
            } else {
                registry.getHolder(ResourceKey.create(Registries.STRUCTURE, t.id())).ifPresent(h -> {
                    if (!holders.contains(h)) holders.add(h);
                });
            }
        }
        if (holders.isEmpty()) return null;
        // Raio em "anéis": cada anel de uma estrutura espalhada anda {spacing} chunks. Sem posicionamento nesta
        // dimensão (estrutura de outro mundo) nem vale a pena chamar a busca.
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        int minSpacing = Integer.MAX_VALUE;
        boolean any = false;
        for (Holder<Structure> h : holders) {
            for (StructurePlacement p : state.getPlacementsForStructure(h)) {
                any = true;
                if (p instanceof RandomSpreadStructurePlacement spread) minSpacing = Math.min(minSpacing, Math.max(1, spread.spacing()));
            }
        }
        if (!any) return null;
        int radius = minSpacing == Integer.MAX_VALUE ? SEARCH_CHUNKS : Mth.clamp(Mth.ceil(SEARCH_CHUNKS / (float) minSpacing), 1, SEARCH_CHUNKS);
        Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holders), origin, radius, false);
        return result == null ? null : result.getFirst();
    }

    /**
     * Bioma mais próximo (espiral a partir do jogador, uma amostra a cada {@value #BIOME_STEP} blocos, só na altura do mar:
     * ~2,6 mil consultas à fonte de biomas no pior caso, sem gerar chunks).
     */
    @Nullable
    private static BlockPos nearestBiome(ServerLevel level, List<JournalLocate.Target> targets, BlockPos origin) {
        List<TagKey<Biome>> tags = new ArrayList<>();
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        for (JournalLocate.Target t : targets) {
            if (t.tag()) tags.add(TagKey.create(Registries.BIOME, t.id()));
            else keys.add(ResourceKey.create(Registries.BIOME, t.id()));
        }
        Predicate<Holder<Biome>> match = h -> {
            for (TagKey<Biome> tag : tags) if (h.is(tag)) return true;
            for (ResourceKey<Biome> key : keys) if (h.is(key)) return true;
            return false;
        };
        int y = Mth.clamp(level.getChunkSource().getGenerator().getSeaLevel(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 1);
        // Passo vertical maior que o mundo: só a altura do mar (onde ficam pântanos, desertos etc. da superfície).
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(match, new BlockPos(origin.getX(), y, origin.getZ()),
                BIOME_RADIUS, BIOME_STEP, 4096);
        return found == null ? null : found.getFirst();
    }

    /** O chunk da coluna está carregado? (a altura do ponto achado pela busca é 0, que pode ficar fora do mundo). */
    private static boolean columnLoaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(new BlockPos(pos.getX(), level.getMinBuildHeight(), pos.getZ()));
    }

    private static BlockPos surface(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (y <= level.getMinBuildHeight()) y = pos.getY();
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    private static JournalNetwork.Target packet(Key key, Cache c) {
        String dim = c.dim().location().toString();
        return c.found() == null
                ? new JournalNetwork.Target(key.track(), c.step(), false, BlockPos.ZERO, false, dim)
                : new JournalNetwork.Target(key.track(), c.step(), true, c.found(), c.yKnown(), dim);
    }

    private static void send(ServerPlayer player, JournalNetwork.Target packet) {
        Key key = new Key(player.getUUID(), packet.track());
        if (packet.equals(SENT.get(key))) return;
        SENT.put(key, packet);
        PacketDistributor.sendToPlayer(player, packet);
    }

    private static String signature(List<JournalLocate.Target> targets) {
        StringBuilder sb = new StringBuilder();
        for (JournalLocate.Target t : targets) sb.append(t.signature()).append(';');
        return sb.toString();
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private JournalLocator() {}
}
