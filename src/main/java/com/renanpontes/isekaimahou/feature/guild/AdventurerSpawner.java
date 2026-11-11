package com.renanpontes.isekaimahou.feature.guild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.guild.block.FactionBoardBlock;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Faz grupos de aventureiros aparecerem pelo Overworld:
 * <ul>
 *   <li><b>cidades</b> ({@link Places} tipo city/capital): 2–4 juntos no salão da Guilda/taverna — o lugar é achado uma vez
 *       por cidade procurando o Balcão, o Quadro de Missões ou o Quadro da Guilda perto do centro (só nas seções de chunk
 *       que têm esses blocos, via paleta), senão na praça central;</li>
 *   <li><b>vilas</b>: perto do sino;</li>
 *   <li><b>estradas</b>: 2–4 viajantes longe da vista do jogador.</li>
 * </ul>
 * Os errantes vão embora depois de ~10–15 min (os dos moldes das cidades ficam, são persistentes).
 */
final class AdventurerSpawner {
    private static final int INTERVAL = 1200;
    private static final float CHANCE_PER_PLAYER = 0.25F;
    private static final int MAX_NEARBY = 4;
    /** Aventureiros (contando os dos moldes) a partir dos quais a cidade não recebe mais grupos. */
    private static final int CITY_CAP = 8, CAPITAL_CAP = 12;
    /** Salão da Guilda/taverna achado por cidade (id do lugar → posição; vazio = procurou e não achou). */
    private static final Map<String, Optional<BlockPos>> HALLS = new HashMap<>();
    /** Quando procurar de novo o salão de uma cidade em que não foi achado (tempo de jogo). */
    private static final Map<String, Long> HALL_MISS = new HashMap<>();
    private static final long HALL_RETRY = 6000L;
    private static int timer = 600;

    static void tick(MinecraftServer server) {
        if (--timer > 0) return;
        timer = INTERVAL;
        ServerLevel level = server.overworld();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || level.random.nextFloat() >= CHANCE_PER_PLAYER) continue;
            Places.Place place = Places.at(level, p.blockPosition());
            if (place != null && isCity(place)) trySpawnCity(level, place);
            else trySpawn(level, p);
        }
    }

    static void clear() {
        HALLS.clear();
        HALL_MISS.clear();
        timer = 600;
    }

    private static boolean isCity(Places.Place p) {
        return "city".equals(p.type()) || "capital".equals(p.type());
    }

    // =====================================================================================
    //  Cidades
    // =====================================================================================
    private static void trySpawnCity(ServerLevel level, Places.Place place) {
        int cap = "capital".equals(place.type()) ? CAPITAL_CAP : CITY_CAP;
        AABB box = AABB.of(place.box());
        if (level.getEntitiesOfClass(AdventurerEntity.class, box).size() >= cap) return;
        RandomSource rnd = level.random;
        BlockPos hall = hallOf(level, place);
        BlockPos center = hall != null ? hall : place.center();
        int radius = hall != null ? 5 : 9;
        int party = 2 + rnd.nextInt(3);
        int spawned = 0;
        boolean floor = false;
        for (int attempt = 0; attempt < 20 && spawned < party; attempt++) {
            BlockPos at = floorNear(level, center.offset(rnd.nextInt(radius * 2 + 1) - radius, 0, rnd.nextInt(radius * 2 + 1) - radius));
            if (at == null) continue;
            floor = true;
            // Não "brota" na cara do jogador: com alguém a menos de 10 blocos e vendo o lugar, tenta outro ponto.
            if (seenUpClose(level, at)) continue;
            if (spawnOne(level, at, rnd)) spawned++;
        }
        // Salão guardado que virou parede/sumiu (nenhum chão livre perto dele): procura de novo na próxima vez.
        if (!floor && hall != null) HALLS.remove(place.id());
    }

    /**
     * Salão da cidade: guardado quando achado; quando não acha (chunks ainda carregando, cidade sem salão), tenta de novo
     * depois de {@link #HALL_RETRY} ticks em vez de desistir para sempre.
     */
    @Nullable
    private static BlockPos hallOf(ServerLevel level, Places.Place place) {
        Optional<BlockPos> cached = HALLS.get(place.id());
        long now = level.getGameTime();
        if (cached != null && (cached.isPresent() || now < HALL_MISS.getOrDefault(place.id(), 0L))) return cached.orElse(null);
        Optional<BlockPos> found = findHall(level, place);
        HALLS.put(place.id(), found);
        if (found.isEmpty()) HALL_MISS.put(place.id(), now + HALL_RETRY);
        else HALL_MISS.remove(place.id());
        return found.orElse(null);
    }

    /**
     * Procura (uma vez por cidade) o Balcão da Guilda, um Quadro de Missões ou um Quadro da Guilda perto do centro, só em
     * chunks carregados e só nas seções cuja paleta tem um desses blocos (barato).
     */
    private static Optional<BlockPos> findHall(ServerLevel level, Places.Place place) {
        BlockPos c = place.center();
        int r = Math.min(80, Math.max(place.box().getXSpan(), place.box().getZSpan()) / 2);
        int minY = c.getY() - 12, maxY = c.getY() + 24;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = SectionPos.blockToSectionCoord(c.getX() - r); cx <= SectionPos.blockToSectionCoord(c.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(c.getZ() - r); cz <= SectionPos.blockToSectionCoord(c.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(AdventurerSpawner::isHallBlock)) continue;
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (baseY + 15 < minY || baseY > maxY) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!isHallBlock(sec.getBlockState(x, y, z))) continue;
                                BlockPos p = new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, baseY + y, SectionPos.sectionToBlockCoord(cz) + z);
                                double d = p.distSqr(c);
                                if (d < bestD) {
                                    bestD = d;
                                    best = p;
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isHallBlock(BlockState s) {
        return s.is(GuildContent.GUILD_COUNTER.get()) || s.is(GuildContent.QUEST_BOARD.get())
                || (s.is(GuildContent.FACTION_BOARD.get()) && s.getValue(FactionBoardBlock.FACTION) == GuildFaction.GUILD);
    }

    /** Algum jogador a menos de 10 blocos com linha de visão até o ponto? */
    private static boolean seenUpClose(ServerLevel level, BlockPos at) {
        net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atCenterOf(at.above());
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(eye) > 10 * 10) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), eye, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(at.above())) return true;
        }
        return false;
    }

    /** Chão livre perto de {@code p} no MESMO andar (dentro de prédios: sem heightmap). */
    @Nullable
    private static BlockPos floorNear(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos at = p.offset(0, dy, 0);
            if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                    && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                    && level.getFluidState(at).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    // =====================================================================================
    //  Vilas e estradas
    // =====================================================================================
    private static void trySpawn(ServerLevel level, ServerPlayer player) {
        BlockPos pp = player.blockPosition();
        if (level.getEntitiesOfClass(AdventurerEntity.class, new AABB(pp).inflate(64)).size() >= MAX_NEARBY) return;
        RandomSource rnd = level.random;
        Optional<BlockPos> bell = level.getPoiManager().find(h -> h.is(PoiTypes.MEETING), p -> true, pp, 64, PoiManager.Occupancy.ANY);
        // Sem vila por perto: só às vezes aparecem "na estrada".
        if (bell.isEmpty() && rnd.nextFloat() > 0.45F) return;
        BlockPos center = bell.orElse(pp);
        int radius = bell.isPresent() ? 10 : 40;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos col = center.offset(rnd.nextInt(radius * 2 + 1) - radius, 0, rnd.nextInt(radius * 2 + 1) - radius);
            if (bell.isEmpty() && col.distSqr(pp) < 24 * 24) continue;
            if (!level.isLoaded(col)) continue;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            if (!level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP)) continue;
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.below()).isEmpty()) continue;
            // Na estrada viajam em grupo (2–4); na vila, 1–3.
            int party = bell.isPresent() ? 1 + rnd.nextInt(3) : 2 + rnd.nextInt(3);
            List<BlockPos> spots = new ArrayList<>();
            for (int i = 0; i < party; i++) {
                spots.add(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.offset(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1)));
            }
            int spawned = 0;
            for (BlockPos s : spots) if (spawnOne(level, s, rnd)) spawned++;
            if (spawned > 0) return;
        }
    }

    private static boolean spawnOne(ServerLevel level, BlockPos at, RandomSource rnd) {
        AdventurerEntity a = GuildContent.ADVENTURER.get().create(level);
        if (a == null) return false;
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(a)) {
            a.discard();
            return false;
        }
        EventHooks.finalizeMobSpawn(a, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        a.setDespawnDelay(12000 + rnd.nextInt(6000));
        a.setHome(at);
        return level.addFreshEntity(a);
    }

    private AdventurerSpawner() {}
}
