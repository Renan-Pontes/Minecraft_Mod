package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Cidade inicial (release 1.0 — "faz os players nascerem numa cidade inicial").
 * <ul>
 *   <li>Na primeira vez que o servidor sobe com esta versão, procura a cidade/capital do mod mais perto do ponto de
 *       nascimento original (mesma busca do /locate), gera a praça, acha um lugar livre ao lado do chafariz e faz dele o
 *       ponto de nascimento DO MUNDO. O resultado fica salvo ({@link CityWorld}); a busca nunca se repete sozinha.</li>
 *   <li>Jogador que entra pela primeira vez é posto exatamente nesse lugar (o vanilla sorteia até 10 blocos em volta
 *       do ponto do mundo e poderia deixá-lo em cima do chafariz ou de um telhado).</li>
 *   <li>Quem morre SEM cama/quarto renasce ali também. Quem tem cama, âncora ou quarto alugado não é tocado — só o
 *       ponto do mundo muda, nunca o ponto de renascimento de um jogador. Jogadores antigos não são teleportados.</li>
 * </ul>
 * A praça fica dentro do lugar nomeado da cidade (zona segura: sem monstros naturais, sem destruição, Modo Paz).
 */
public final class CityStart {
    private static final String[] CITY_STRUCTURES = {"guild_city", "capital_city", "capital_sahra", "capital_nordheim"};
    /** Alcance da busca (em células da grade de cada estrutura, como o /locate). Limitado para não travar a subida. */
    private static final int SEARCH_RADIUS = 24;
    private static final String DONE_FLAG = "StartCityDone";

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            try {
                CityWorld w = CityWorld.get(e.getServer());
                if (w.startState == CityWorld.START_UNDECIDED) choose(e.getServer(), e.getServer().overworld().getSharedSpawnPos());
            } catch (RuntimeException ex) {
                // Nunca impedir o mundo de abrir por causa da cidade inicial.
                IsekaiMahou.LOGGER.error("Cidade inicial: a busca falhou; o ponto de nascimento fica como está", ex);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) onLogin(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (!e.isEndConquered() && e.getEntity() instanceof ServerPlayer sp) onRespawn(sp);
        });
    }

    // =====================================================================================
    //  Escolha da cidade
    // =====================================================================================
    /**
     * Procura a cidade mais perto de {@code from}, acha o lugar na praça e muda o ponto de nascimento do mundo.
     * @return true se a cidade inicial ficou definida
     */
    public static boolean choose(MinecraftServer server, BlockPos from) {
        ServerLevel sl = server.overworld();
        CityWorld w = CityWorld.get(server);
        if (w.originalSpawn == null) w.originalSpawn = sl.getSharedSpawnPos();
        Registry<Structure> reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> cities = new ArrayList<>();
        for (String id : CITY_STRUCTURES) {
            reg.getHolder(ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id(id))).ifPresent(h -> {
                if (!sl.getChunkSource().getGeneratorState().getPlacementsForStructure(h).isEmpty()) cities.add(h);
            });
        }
        Pair<BlockPos, Holder<Structure>> found = cities.isEmpty() ? null : sl.getChunkSource().getGenerator()
                .findNearestMapStructure(sl, HolderSet.direct(cities), from, SEARCH_RADIUS, false);
        if (found == null) {
            w.startState = CityWorld.START_NONE;
            w.setDirty();
            IsekaiMahou.LOGGER.info("Cidade inicial: nenhuma cidade do mod perto de {} — o ponto de nascimento fica como está", from);
            return false;
        }
        // O /locate devolve o canto do chunk de início; o centro de verdade é o meio da caixa da estrutura.
        ChunkPos cp = new ChunkPos(found.getFirst());
        ChunkAccess startChunk = sl.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = sl.structureManager().getStartForStructure(SectionPos.bottomOf(startChunk), found.getSecond().value(), startChunk);
        BlockPos center = start != null && start.isValid() ? start.getBoundingBox().getCenter() : found.getFirst();
        CityHeartBlockEntity heart = findHeart(sl, center);
        BlockPos feet;
        BlockPos look;
        if (heart != null) {
            look = heart.getBlockPos();
            feet = findSpot(sl, look, heart.floorY());
            heart.register(sl);   // o lugar nomeado (zona segura) já existe quando o primeiro jogador chega
        } else {
            look = center;
            feet = null;
        }
        if (feet == null) {
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + 7, center.getZ());
            feet = new BlockPos(center.getX() + 7, y, center.getZ());
        }
        float angle = (float) (Mth.atan2(look.getZ() - feet.getZ(), look.getX() - feet.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        sl.setDefaultSpawnPos(feet, angle);
        w.startState = CityWorld.START_SET;
        w.startPos = feet;
        w.startHeart = heart != null ? heart.getBlockPos() : null;
        w.startAngle = angle;
        w.setDirty();
        IsekaiMahou.LOGGER.info("Cidade inicial: {} em {} — jogadores novos nascem em {}",
                found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?"), center, feet);
        return true;
    }

    /** Coração da cidade perto do centro (gera o chunk do centro; se não estiver lá, olha os vizinhos). */
    @Nullable
    private static CityHeartBlockEntity findHeart(ServerLevel sl, BlockPos center) {
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int ring = 0; ring <= 1; ring++) {
            CityHeartBlockEntity best = null;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    LevelChunk chunk = sl.getChunk(cx + dx, cz + dz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof CityHeartBlockEntity h
                                && (best == null || h.getBlockPos().distSqr(center) < best.getBlockPos().distSqr(center))) best = h;
                    }
                }
            }
            if (best != null) return best;
        }
        // Rede de segurança: o bloco do coração na coluna do centro (e vizinhas), mesmo que o BlockEntity ainda esteja
        // "pendente" no chunk recém-gerado (pedir o BlockEntity pela posição o cria).
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = center.getY() + 48; y >= center.getY() - 48; y--) {
                    p.set(center.getX() + dx, y, center.getZ() + dz);
                    if (sl.getBlockState(p).is(CitiesContent.CITY_HEART.get()) && sl.getBlockEntity(p) instanceof CityHeartBlockEntity h) return h;
                }
            }
        }
        return null;
    }

    /** Um lugar livre na praça, a alguns passos do chafariz (de preferência a céu aberto). */
    @Nullable
    private static BlockPos findSpot(ServerLevel sl, BlockPos heart, int floorY) {
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        BlockPos roofed = null;
        for (int r = 8; r <= 16; r++) {
            for (int[] d : dirs) {
                int x = heart.getX() + d[0] * r, z = heart.getZ() + d[1] * r;
                for (int y = floorY + 3; y >= floorY - 1; y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!standable(sl, feet)) continue;
                    if (sl.canSeeSky(feet)) return feet;
                    if (roofed == null) roofed = feet;
                    break;
                }
            }
        }
        return roofed;
    }

    /** Pés em {@code feet}: chão firme embaixo e dois blocos livres, sem líquido. */
    static boolean standable(ServerLevel sl, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = sl.getBlockState(below);
        if (!floor.isFaceSturdy(sl, below, Direction.UP) || !floor.getFluidState().isEmpty()) return false;
        for (int i = 0; i < 2; i++) {
            BlockPos p = feet.above(i);
            BlockState s = sl.getBlockState(p);
            if (!s.getCollisionShape(sl, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** Desfaz: volta o ponto de nascimento do mundo ao original e desliga a cidade inicial. */
    public static void clear(MinecraftServer server) {
        CityWorld w = CityWorld.get(server);
        if (w.originalSpawn != null) server.overworld().setDefaultSpawnPos(w.originalSpawn, 0.0F);
        w.startState = CityWorld.START_OFF;
        w.startPos = null;
        w.startHeart = null;
        w.setDirty();
    }

    // =====================================================================================
    //  Jogadores
    // =====================================================================================
    /** Primeira entrada no mundo: nasce exatamente no lugar da praça. Quem já jogava só ganha a marca. */
    private static void onLogin(ServerPlayer sp) {
        CompoundTag t = CityPlayer.tag(sp);
        if (t.getBoolean(DONE_FLAG)) return;
        t.putBoolean(DONE_FLAG, true);
        if (sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 40) return;   // personagem antigo: não mexe
        if (sp.getRespawnPosition() != null) return;
        place(sp);
    }

    /** Renasceu sem cama/âncora/quarto (o vanilla o deixou perto do ponto do mundo): vai para o lugar exato. */
    private static void onRespawn(ServerPlayer sp) {
        if (sp.getRespawnPosition() == null) place(sp);
    }

    /** Leva ao lugar da praça se o jogador está no Overworld, perto do ponto de nascimento do mundo. */
    private static void place(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null || sp.level().dimension() != Level.OVERWORLD) return;
        CityWorld w = CityWorld.get(server);
        if (w.startState != CityWorld.START_SET || w.startPos == null) return;
        BlockPos pos = w.startPos;
        ServerLevel sl = server.overworld();
        // Outro módulo já levou o jogador para outro lugar (ex.: sala de criação, alma): não briga com ele.
        double dx = sp.getX() - pos.getX(), dz = sp.getZ() - pos.getZ();
        if (dx * dx + dz * dz > 48.0 * 48.0) return;
        // A praça mudou (alguém construiu no lugar): fica onde o vanilla o pôs.
        if (!standable(sl, pos)) return;
        sp.teleportTo(sl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, w.startAngle, 0.0F);
    }

    /** Teleporte de teste (comando). */
    public static boolean teleport(ServerPlayer sp) {
        CityWorld w = CityWorld.get(sp.server);
        if (w.startState != CityWorld.START_SET || w.startPos == null) return false;
        sp.teleportTo(sp.server.overworld(), w.startPos.getX() + 0.5, w.startPos.getY(), w.startPos.getZ() + 0.5, w.startAngle, 0.0F);
        return true;
    }

    private CityStart() {}
}
