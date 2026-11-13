package com.renanpontes.isekaimahou.feature.travel.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Servidor do Guia de Portais: "rastrear o mais próximo". Ordem: (1) estruturas de portal que já carregaram (lugares do
 * {@code Places}, posição exata); (2) busca de estrutura no mapa, igual ao {@code /locate}, num raio limitado — só
 * quando o jogador clica, com recarga curta. O alvo fica guardado no jogador (sobrevive a morte e a sair do jogo) e o
 * cliente mostra a seta no rastreador.
 */
public final class GuideService {
    private static final String TAG = "isekai_mahou_portal_track";
    /** Recarga entre buscas do mesmo jogador (ticks). */
    private static final int COOLDOWN = 40;
    /** Um portal conhecido mais perto que isto dispensa a busca no mapa. */
    private static final int KNOWN_GOOD = 320;
    /** Alcance desejado da busca no mapa, em chunks (o número de "anéis" sai do espaçamento da estrutura). */
    private static final int SEARCH_CHUNKS = 160;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** O que a busca achou. */
    public record Found(BlockPos pos, boolean exact, int reach) {}

    public static void track(ServerPlayer p, String id) {
        if (id == null || id.isEmpty()) {
            clear(p);
            send(p, new GuideNet.PortalTarget("", GuideNet.CLEARED, p.level().dimension().location(), BlockPos.ZERO, false, 0));
            return;
        }
        PortalGuide.Entry entry = PortalGuide.get(id);
        ResourceLocation here = p.level().dimension().location();
        if (entry == null || !entry.trackable()) {
            send(p, new GuideNet.PortalTarget(id, GuideNet.NONE, here, BlockPos.ZERO, false, 0));
            return;
        }
        if (!p.level().dimension().equals(entry.siteDim())) {
            send(p, new GuideNet.PortalTarget(id, GuideNet.WRONG_DIM, entry.siteDim().location(), BlockPos.ZERO, false, 0));
            return;
        }
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now >= last && now - last < COOLDOWN) {
            send(p, new GuideNet.PortalTarget(id, GuideNet.COOLDOWN, here, BlockPos.ZERO, false, 0));
            return;
        }
        LAST.put(p.getUUID(), now);
        Found found = find(p.serverLevel(), entry, p.blockPosition());
        if (found.pos() == null) {
            send(p, new GuideNet.PortalTarget(id, GuideNet.NONE, here, BlockPos.ZERO, false, found.reach()));
            return;
        }
        save(p, id, p.level().dimension(), found.pos(), found.exact());
        send(p, new GuideNet.PortalTarget(id, GuideNet.FOUND, here, found.pos(), found.exact(), found.reach()));
    }

    /** Portal mais próximo de {@code from} (pos null = nada no alcance). Também usado pelo comando de teste. */
    public static Found find(ServerLevel level, PortalGuide.Entry entry, BlockPos from) {
        BlockPos known = nearestKnown(level, entry, from);
        if (known != null && horizontal(known, from) <= KNOWN_GOOD) return new Found(known, true, 0);
        int[] reach = new int[1];
        BlockPos located = locate(level, entry, from, reach);
        if (located != null && (known == null || horizontal(located, from) + 48 < horizontal(known, from))) {
            return new Found(located, false, reach[0]);
        }
        return new Found(known, known != null, reach[0]);
    }

    /** Estrutura de portal já registrada no {@code Places} (id {@code <estrutura>_<chunkX>_<chunkZ>}), a mais perto. */
    @Nullable
    private static BlockPos nearestKnown(ServerLevel level, PortalGuide.Entry entry, BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Places.Place place : Places.all(level)) {
            if (Travel.WAYSTONE_TYPE.equals(place.type()) || Travel.HUB_TYPE.equals(place.type())) continue;
            boolean match = false;
            for (String s : entry.structures()) {
                if (place.id().startsWith(s + "_")) {
                    match = true;
                    break;
                }
            }
            if (!match) continue;
            double d = horizontal(place.center(), from);
            if (d < bestD) {
                bestD = d;
                best = place.center();
            }
        }
        return best;
    }

    /** Busca no mapa (como o /locate), limitada a ~{@value #SEARCH_CHUNKS} chunks. Nunca derruba o servidor. */
    @Nullable
    private static BlockPos locate(ServerLevel level, PortalGuide.Entry entry, BlockPos from, int[] reachOut) {
        try {
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            List<Holder<Structure>> usable = new ArrayList<>();
            int minSpacing = Integer.MAX_VALUE;
            for (String s : entry.structures()) {
                Holder<Structure> h = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id(s))).orElse(null);
                if (h == null) continue;
                List<StructurePlacement> placements = state.getPlacementsForStructure(h);
                if (placements.isEmpty()) continue;
                usable.add(h);
                for (StructurePlacement sp : placements) {
                    if (sp instanceof RandomSpreadStructurePlacement spread) minSpacing = Math.min(minSpacing, Math.max(1, spread.spacing()));
                }
            }
            if (usable.isEmpty()) return null;
            int spacing = minSpacing == Integer.MAX_VALUE ? 32 : minSpacing;
            int rings = Mth.clamp(Mth.ceil(SEARCH_CHUNKS / (float) spacing), 2, 8);
            reachOut[0] = rings * spacing * 16;
            Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(usable), from, rings, false);
            if (result == null) return null;
            BlockPos pos = result.getFirst();
            if (level.isLoaded(pos)) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                return new BlockPos(pos.getX(), Math.max(level.getMinBuildHeight() + 1, y), pos.getZ());
            }
            return new BlockPos(pos.getX(), from.getY(), pos.getZ());
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Guia de Portais: falha ao procurar {}", entry.id(), e);
            return null;
        }
    }

    // ------------------------------------------------------------------ alvo guardado no jogador
    private static void save(ServerPlayer p, String id, ResourceKey<Level> dim, BlockPos pos, boolean exact) {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Dim", dim.location().toString());
        t.putInt("X", pos.getX());
        t.putInt("Y", pos.getY());
        t.putInt("Z", pos.getZ());
        t.putBoolean("Exact", exact);
        persisted(p).put(TAG, t);
    }

    private static void clear(ServerPlayer p) {
        persisted(p).remove(TAG);
    }

    /** Dados que sobrevivem à morte (convenção do NeoForge: {@code PlayerPersisted}). */
    static CompoundTag persisted(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Entrou no jogo: devolve ao cliente o alvo que ele estava seguindo. */
    public static void onLogin(ServerPlayer p) {
        CompoundTag root = persisted(p);
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) return;
        CompoundTag t = root.getCompound(TAG);
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
        if (dim == null || PortalGuide.get(t.getString("Id")) == null) {
            root.remove(TAG);
            return;
        }
        send(p, new GuideNet.PortalTarget(t.getString("Id"), GuideNet.FOUND, dim,
                new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")), t.getBoolean("Exact"), 0));
    }

    /**
     * Subiu de Círculo e com isso alcançou o requisito de algum portal: UMA linha no chat apontando o Guia (sem isso o
     * jogador não fica sabendo que um caminho novo abriu).
     */
    public static void onCircle(ServerPlayer p, int oldCircle, int newCircle) {
        MutableComponent names = null;
        for (PortalGuide.Entry e : PortalGuide.entries()) {
            if (!e.trackable() || e.minCircle() <= oldCircle || e.minCircle() > newCircle) continue;
            names = names == null ? e.name() : names.append(Component.literal(", ").withStyle(ChatFormatting.GRAY)).append(e.name());
        }
        if (names == null) return;
        p.sendSystemMessage(Component.translatable("guide.isekai_mahou.portals.unlocked", newCircle, names)
                .withStyle(ChatFormatting.GRAY));
    }

    public static void onLogout(UUID id) {
        LAST.remove(id);
    }

    public static void clearAll() {
        LAST.clear();
    }

    private static void send(ServerPlayer p, GuideNet.PortalTarget payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private GuideService() {}
}
