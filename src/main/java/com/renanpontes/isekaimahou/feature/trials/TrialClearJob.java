package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Apaga os blocos de uma instância aos poucos (algumas seções de chunk por tick; seções vazias são puladas na hora)
 * e remove as entidades que sobraram. Itens jogados por jogadores voltam para o dono, se ele estiver online.
 */
final class TrialClearJob {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    final int index;
    /** Limpeza completa da instância (libera o índice no SavedData no fim). */
    final boolean full;
    private final List<BoundingBox> boxes;
    /** Dono dos tickets de chunk (liberados no fim); null = não mexe em tickets. */
    @Nullable private final UUID owner;
    /** Os tickets ainda precisam ser criados (limpeza no boot). */
    private final boolean forceOwn;
    private final List<int[]> work = new ArrayList<>();
    private int cursor;
    private boolean started;

    private TrialClearJob(int index, boolean full, List<BoundingBox> boxes, @Nullable UUID owner, boolean forceOwn) {
        this.index = index;
        this.full = full;
        this.boxes = boxes;
        this.owner = owner;
        this.forceOwn = forceOwn;
    }

    /** Instância inteira (recepção + arena). {@code alreadyForced}: os chunks já estão presos pelo {@code owner}. */
    static TrialClearJob full(int index, UUID owner, boolean alreadyForced) {
        return new TrialClearJob(index, true, List.of(TrialDimension.lobbyBox(index), TrialDimension.arenaBox(index)), owner, !alreadyForced);
    }

    /** Só a arena, dentro de uma instância aberta (os tickets continuam com ela). */
    static TrialClearJob arenaOnly(int index) {
        return new TrialClearJob(index, false, List.of(TrialDimension.arenaBox(index)), null, false);
    }

    /** Processa até {@code budget} seções com blocos. Devolve true quando terminou tudo. */
    boolean step(ServerLevel level, int budget) {
        if (!started) {
            started = true;
            if (forceOwn && owner != null) force(level, true);
            prepare(level);
        }
        int heavy = 0;
        while (cursor < work.size() && heavy < budget) {
            int[] w = work.get(cursor++);
            if (clearSection(level, boxes.get(w[0]), w[1], w[2], w[3])) heavy++;
        }
        if (cursor < work.size()) return false;
        clearEntities(level);
        if (owner != null) force(level, false);
        return true;
    }

    private void prepare(ServerLevel level) {
        for (int b = 0; b < boxes.size(); b++) {
            BoundingBox box = boxes.get(b);
            int s0 = level.getSectionIndex(box.minY()), s1 = level.getSectionIndex(box.maxY());
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    for (int s = s0; s <= s1; s++) work.add(new int[] {b, cx, cz, s});
                }
            }
        }
    }

    /** Apaga os blocos da seção dentro da caixa. Devolve true se a seção tinha blocos (custo "pesado"). */
    private static boolean clearSection(ServerLevel level, BoundingBox box, int cx, int cz, int sectionIndex) {
        LevelChunk chunk = level.getChunk(cx, cz);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) return false;
        LevelChunkSection sec = sections[sectionIndex];
        if (sec.hasOnlyAir()) return false;
        int baseY = level.getSectionYFromSectionIndex(sectionIndex) << 4;
        int x0 = Math.max(box.minX(), cx << 4), x1 = Math.min(box.maxX(), (cx << 4) + 15);
        int z0 = Math.max(box.minZ(), cz << 4), z1 = Math.min(box.maxZ(), (cz << 4) + 15);
        int y0 = Math.max(box.minY(), baseY), y1 = Math.min(box.maxY(), baseY + 15);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (sec.getBlockState(x & 15, y & 15, z & 15).isAir()) continue;
                    level.setBlock(pos.set(x, y, z), air, FLAGS);
                }
            }
        }
        return true;
    }

    private void clearEntities(ServerLevel level) {
        for (BoundingBox box : boxes) {
            for (Entity e : level.getEntities((Entity) null, TrialDimension.aabb(box), e -> !(e instanceof Player))) {
                if (e instanceof ItemEntity item && !item.getItem().isEmpty()) {
                    ServerPlayer owner = ownerOf(level, item);
                    if (owner != null) Rewards.give(owner, item.getItem());   // nada que o jogador largou se perde
                }
                e.discard();
            }
        }
    }

    /**
     * Dono de um item no chão: quem o largou (Thrower) ou para quem ele foi reservado (Target, ex.: recompensa que
     * não coube). Procurado na lista de jogadores do SERVIDOR — na limpeza final o dono já voltou para casa (outra
     * dimensão), e {@code ItemEntity.getOwner()} só encontra entidades do mesmo nível.
     */
    @Nullable
    private static ServerPlayer ownerOf(ServerLevel level, ItemEntity item) {
        UUID id = ownerId(item);
        if (id == null) return null;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
        return p == null || p.hasDisconnected() ? null : p;
    }

    /** UUID de quem largou o item (ou para quem ele foi reservado); null = espólio sem dono. */
    @Nullable
    static UUID ownerId(ItemEntity item) {
        CompoundTag tag = new CompoundTag();
        item.addAdditionalSaveData(tag);
        return tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : item.getTarget();
    }

    private void force(ServerLevel level, boolean add) {
        if (owner == null) return;
        for (ChunkPos cp : TrialDimension.chunks(index)) TrialManager.TICKETS.forceChunk(level, owner, cp.x, cp.z, add, true);
    }
}
