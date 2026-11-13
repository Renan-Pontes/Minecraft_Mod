package com.renanpontes.isekaimahou.feature.towers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * <b>Lugares no céu</b> (v1.0): as Ilhas Voadoras ({@code tower_sky_island_*}) e a Torre Flutuante ({@code tower_floating}).
 * <ul>
 *   <li><b>Corrente Ascendente</b>: cada lugar tem um poço 3×3 que atravessa a ilha. Quem entra na coluna embaixo dela
 *       sobe (Levitação); chegando à borda do poço é posto em terra firme. Agachado, desce devagar (Queda Lenta).
 *       A coluna brilha (partículas só para quem está perto) para ser achada do chão.</li>
 *   <li><b>Queda amortecida</b>: quem despenca da ilha ganha Queda Lenta — cair da borda não mata.</li>
 * </ul>
 * Custo: nada de varredura. O lugar do jogador vem das referências de estrutura do chunk onde ele está (dado que o
 * chunk já tem), relido só quando ele troca de chunk; o resto é conta com a posição. Só no Overworld.
 * <p>As posições do poço são as dos moldes de {@code tools/gen/towers_sky.py} (tabela {@link #LAYOUTS}).
 */
public final class SkySites {
    /** As Ilhas Voadoras (sem a torre flutuante, que está na tag das torres). */
    public static final TagKey<Structure> ISLANDS = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("sky_islands"));
    public static final String FLAG_ISLAND = "towers.sky_island";
    /** +1 por ilha voadora diferente visitada (até o limite guardado por jogador). */
    public static final String COUNTER_ISLANDS = "towers.sky_islands";
    private static final String VISITED_KEY = "isekai_mahou_sky_islands";
    private static final int MAX_VISITED = 128;

    /** Poço no molde: x/z locais do centro e altura (local) dos pés de quem está em pé na ilha. */
    private record Layout(int wellX, int wellZ, int feetY) {}

    /** Tem que bater com tools/gen/towers_sky.py e towers_struct.py (ISLAND_H). */
    private static final Map<String, Layout> LAYOUTS = Map.of(
            "tower_floating", new Layout(25, 41, 16),
            "tower_sky_island_shrine", new Layout(11, 11, 11),
            "tower_sky_island_ruins", new Layout(16, 16, 14),
            "tower_sky_island_keep", new Layout(21, 21, 17));

    /** Um lugar no mundo: caixa da estrutura, centro do poço (x/z) e altura dos pés na borda. */
    public record Site(String path, long key, BoundingBox box, int wellX, int wellZ, int feetY) {
        boolean inColumn(double x, double z) {
            return Math.abs(x - (wellX + 0.5)) <= 1.5 && Math.abs(z - (wellZ + 0.5)) <= 1.5;
        }

        /** Longe do meio do poço: na beirada, a cabeça de quem sobe bate na pedra da barriga da ilha. */
        boolean offCenter(double x, double z) {
            return Math.abs(x - (wellX + 0.5)) > 0.8 || Math.abs(z - (wellZ + 0.5)) > 0.8;
        }

        public boolean isIsland() {
            return path.contains("sky_island");
        }
    }

    private static final Site NONE = new Site("", 0L, new BoundingBox(0, 0, 0, 0, 0, 0), 0, 0, 0);
    /** Por jogador: lugar do chunk atual (NONE = nenhum) e o chunk em que isso foi lido. */
    private static final Map<UUID, Site> CURRENT = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHUNK = new HashMap<>();
    /** Quem está subindo pela corrente (para pousar na borda no tick certo). */
    private static final Set<UUID> RIDING = new HashSet<>();
    /** Estrutura → caminho do id ("" = não é lugar do céu), resolvido uma vez por servidor. */
    private static final Map<Structure, String> PATHS = new java.util.IdentityHashMap<>();

    public static void clearAll() {
        CURRENT.clear();
        LAST_CHUNK.clear();
        RIDING.clear();
        PATHS.clear();
    }

    public static void forget(UUID id) {
        CURRENT.remove(id);
        LAST_CHUNK.remove(id);
        RIDING.remove(id);
    }

    // ------------------------------------------------------------------ tick do jogador

    public static void tick(ServerPlayer sp) {
        if (sp.level().dimension() != Level.OVERWORLD || sp.isSpectator()) {
            if (!RIDING.isEmpty()) RIDING.remove(sp.getUUID());
            return;
        }
        ServerLevel sl = sp.serverLevel();
        UUID id = sp.getUUID();
        if (sp.tickCount % 10 == 0) refresh(sl, sp);
        Site site = CURRENT.getOrDefault(id, NONE);
        if (site == NONE) {
            if (!RIDING.isEmpty()) RIDING.remove(id);
            return;
        }
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        boolean column = site.inColumn(x, z);

        // Chegada: todo tick para quem está subindo (a 10 blocos/s não dá para esperar o próximo ciclo)
        if (RIDING.contains(id)) {
            if (!column || sp.isShiftKeyDown()) {
                RIDING.remove(id);
                sp.removeEffect(MobEffects.LEVITATION);
            } else if (y >= site.feetY() - 0.2) {
                RIDING.remove(id);
                land(sl, sp, site);
                return;
            } else if (y >= site.box().minY() - 4 && site.offCenter(x, z)) {
                // Chegando à ilha pela beirada da coluna: a corrente puxa para o meio do poço (senão trava no teto)
                sp.teleportTo(sl, site.wellX() + 0.5, y, site.wellZ() + 0.5, sp.getYRot(), sp.getXRot());
            }
        }
        if (sp.tickCount % 5 != 0) return;

        if (column && y < site.feetY() - 0.2 && y > site.box().minY() - 220) {
            if (sp.isShiftKeyDown()) {
                // Agachado: desce devagar pelo mesmo poço
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, true, false));
            } else if (!sp.getAbilities().flying && !sp.isPassenger() && !sp.isFallFlying()) {
                sp.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 12, 9, true, false));
                sp.resetFallDistance();
                if (RIDING.add(id)) {
                    sl.playSound(null, x, y, z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 1.6F);
                }
            }
        } else if (!column && y < site.box().minY() && sp.getDeltaMovement().y < -0.7
                && !sp.hasEffect(MobEffects.SLOW_FALLING) && !sp.getAbilities().flying && !sp.isFallFlying()) {
            // Despencou da ilha: a magia dela amortece
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, true, false));
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.towers.sky_cushion").withStyle(ChatFormatting.AQUA), true);
        }

        if (sp.tickCount % 10 == 0 && y < site.feetY() + 4) beacon(sl, sp, site);
        if (sp.tickCount % 40 == 0) {
            if (y < site.box().minY()) Hints.show(sp, "towers.updraft");
            if (site.isIsland() && y >= site.feetY() - 2 && site.box().isInside(sp.blockPosition())) visit(sp, site);
        }
    }

    /** Fim da subida: pousa na borda do poço (terra firme em toda a volta nos moldes). */
    private static void land(ServerLevel sl, ServerPlayer sp, Site site) {
        sp.removeEffect(MobEffects.LEVITATION);
        sp.resetFallDistance();
        sp.setDeltaMovement(0, 0, 0);
        sp.teleportTo(sl, site.wellX() + 2.5, site.feetY(), site.wellZ() + 0.5, sp.getYRot(), sp.getXRot());
        sl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.2, sp.getZ(), 14, 0.4, 0.1, 0.4, 0.02);
        sl.playSound(null, sp.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** Brilho da coluna, só para este jogador: algumas partículas entre o chão e a ilha, perto da altura dele. */
    private static void beacon(ServerLevel sl, ServerPlayer sp, Site site) {
        double cx = site.wellX() + 0.5, cz = site.wellZ() + 0.5;
        int top = site.feetY();
        int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, site.wellX(), site.wellZ());
        if (ground >= site.box().minY()) ground = sl.getMinBuildHeight();
        double lo = Math.max(ground, sp.getY() - 24), hi = Math.min(top, sp.getY() + 40);
        if (hi <= lo) return;
        // Um pacote só: o espalhamento vertical (gaussiano) estica as faíscas pelo trecho da coluna perto do jogador
        sl.sendParticles(sp, ParticleTypes.END_ROD, true, cx, (lo + hi) * 0.5, cz, 8, 0.5, (hi - lo) * 0.28, 0.5, 0.01);
        if (sp.getY() < ground + 12) {
            sl.sendParticles(sp, ParticleTypes.CLOUD, true, cx, ground + 0.5, cz, 2, 0.6, 0.2, 0.6, 0.02);
        }
    }

    /** Primeira vez nesta ilha: diário + contador (uma vez por ilha). */
    private static void visit(ServerPlayer sp, Site site) {
        net.minecraft.nbt.CompoundTag root = sp.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        long[] seen = root.getLongArray(VISITED_KEY);
        for (long k : seen) {
            if (k == site.key()) return;
        }
        if (seen.length < MAX_VISITED) {
            long[] next = java.util.Arrays.copyOf(seen, seen.length + 1);
            next[seen.length] = site.key();
            root.putLongArray(VISITED_KEY, next);
            sp.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, root);
            MagicApi.addCounter(sp, COUNTER_ISLANDS, 1);
        }
        if (TwProgress.flag(sp, FLAG_ISLAND)) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.towers.sky_island").withColor(0xBFE8FF));
        }
    }

    // ------------------------------------------------------------------ achar o lugar

    private static void refresh(ServerLevel sl, ServerPlayer sp) {
        long chunk = ChunkPos.asLong(sp.getBlockX() >> 4, sp.getBlockZ() >> 4);
        Long last = LAST_CHUNK.get(sp.getUUID());
        if (last != null && last == chunk) return;
        LAST_CHUNK.put(sp.getUUID(), chunk);
        Site found = NONE;
        try {
            found = lookup(sl, sp.blockPosition());
        } catch (RuntimeException e) {
            // Ler referências de estrutura nunca derruba o servidor: sem corrente desta vez.
            IsekaiMahou.LOGGER.debug("Lugares do céu: falha ao conferir estruturas", e);
        }
        CURRENT.put(sp.getUUID(), found);
    }

    private static String pathOf(ServerLevel sl, Structure s) {
        return PATHS.computeIfAbsent(s, st -> {
            ResourceLocation key = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(st);
            return key != null && IsekaiMahou.MODID.equals(key.getNamespace()) && LAYOUTS.containsKey(key.getPath()) ? key.getPath() : "";
        });
    }

    /** Lugar do céu que tem referência no chunk da posição (o mais perto na horizontal), ou NONE. */
    private static Site lookup(ServerLevel sl, BlockPos pos) {
        if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return NONE;
        Site best = NONE;
        double bd = Double.MAX_VALUE;
        for (Map.Entry<Structure, LongSet> e : sl.structureManager().getAllStructuresAt(pos).entrySet()) {
            String path = pathOf(sl, e.getKey());
            if (path.isEmpty()) continue;
            for (long startChunk : e.getValue()) {
                Site site = siteOf(sl, e.getKey(), path, startChunk);
                if (site == null) continue;
                double dx = site.wellX() - pos.getX(), dz = site.wellZ() - pos.getZ();
                if (dx * dx + dz * dz < bd) {
                    bd = dx * dx + dz * dz;
                    best = site;
                }
            }
        }
        return best;
    }

    @Nullable
    private static Site siteOf(ServerLevel sl, Structure structure, String path, long startChunk) {
        ChunkPos cp = new ChunkPos(startChunk);
        StructureStart start = sl.structureManager().getStartForStructure(SectionPos.of(cp, 0), structure,
                sl.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS));
        return start == null || !start.isValid() ? null : siteOf(path, start);
    }

    /** Lugar do céu desta estrutura (null se não for um, ou se o molde não for o esperado). */
    @Nullable
    public static Site siteOf(String path, StructureStart start) {
        Layout layout = LAYOUTS.get(path);
        if (layout == null || start.getPieces().isEmpty()) return null;
        StructurePiece first = start.getPieces().get(0);
        if (!(first instanceof PoolElementStructurePiece piece)) return null;
        BlockPos well = piece.getPosition().offset(StructureTemplate.transform(new BlockPos(layout.wellX(), 0, layout.wellZ()),
                Mirror.NONE, piece.getRotation(), BlockPos.ZERO));
        return new Site(path, start.getChunkPos().toLong(), start.getBoundingBox(), well.getX(), well.getZ(),
                piece.getPosition().getY() + layout.feetY());
    }

    private SkySites() {}
}
