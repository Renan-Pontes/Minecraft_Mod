package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Ergue Las Noches aos poucos: coluna por coluna, com orçamento de tempo por tick (nada de travada de vários segundos).
 * Cada coluna pergunta à {@link LnLayout} o bloco de cada altura e só troca o que é diferente (rodar de novo é seguro).
 * Embaixo do piso, colunas de arenito até o chão (a cúpula nunca fica flutuando sobre um vale). No fim, os detalhes com
 * dados (baús com loot, o livro do trono, a placa).
 */
public final class LnBuilder {
    /** Tempo máximo de construção por tick (ms → ns). Um chunk começado sempre termina. */
    private static final long BUDGET_NS = 5_000_000L;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Quantos chunks à frente já ficam pedidos para geração. */
    private static final int PRELOAD = 6;
    /** Ticket que mantém/gera os chunks da obra (expira sozinho se a obra parar). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("isekai_mahou_las_noches", Comparator.comparingLong(ChunkPos::toLong), 300);
    private static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, IsekaiMahou.id("chests/hollow_palace"));
    private static List<ChunkPos> cachedChunks;
    private static BlockPos cachedFor;

    /** Chunks a construir (do sul para o norte: o corredor perto do palácio aparece primeiro). */
    static List<ChunkPos> chunks(BlockPos c) {
        if (c.equals(cachedFor) && cachedChunks != null) return cachedChunks;
        List<ChunkPos> out = new ArrayList<>();
        int cx0 = (c.getX() + LnLayout.MIN_X) >> 4, cx1 = (c.getX() + LnLayout.MAX_X) >> 4;
        int cz0 = (c.getZ() + LnLayout.MIN_Z) >> 4, cz1 = (c.getZ() + LnLayout.MAX_Z) >> 4;
        for (int cz = cz1; cz >= cz0; cz--) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (chunkUsed(c, cx, cz)) out.add(new ChunkPos(cx, cz));
            }
        }
        cachedFor = c;
        cachedChunks = List.copyOf(out);
        return cachedChunks;
    }

    private static boolean chunkUsed(BlockPos c, int cx, int cz) {
        for (int wx = cx << 4; wx < (cx << 4) + 16; wx++) {
            for (int wz = cz << 4; wz < (cz << 4) + 16; wz++) {
                if (LnLayout.usesColumn(wx - c.getX(), wz - c.getZ())) return true;
            }
        }
        return false;
    }

    /** Fração construída (0..1). */
    static float progress(LnData d) {
        if (d.center == null) return 0F;
        int n = chunks(d.center).size();
        return n == 0 ? 1F : Math.min(1F, d.chunkIndex / (float) n);
    }

    /**
     * Um passo da construção; true quando terminou (detalhes incluídos). Os chunks ainda não gerados são pedidos com um
     * ticket (geração fora da thread principal) e a construção espera por eles em vez de gerar na hora (travada).
     */
    static boolean step(ServerLevel level, LnData d) {
        if (d.center == null) return true;
        List<ChunkPos> list = chunks(d.center);
        long start = System.nanoTime();
        while (d.chunkIndex < list.size()) {
            ChunkPos cp = list.get(d.chunkIndex);
            for (int i = d.chunkIndex; i < Math.min(list.size(), d.chunkIndex + PRELOAD); i++) {
                ChunkPos q = list.get(i);
                level.getChunkSource().addRegionTicket(TICKET, q, 0, q);
            }
            if (!level.hasChunk(cp.x, cp.z)) return false;
            if (buildColumns(level, d, cp, start)) {
                d.chunkIndex++;
                d.columnIndex = 0;
            }
            d.setDirty();
            if (System.nanoTime() - start > BUDGET_NS) return false;
        }
        details(level, d.center);
        return true;
    }

    /** Constrói as colunas do chunk a partir de {@code d.columnIndex}; true se terminou o chunk. */
    private static boolean buildColumns(ServerLevel level, LnData d, ChunkPos cp, long start) {
        BlockPos c = d.center;
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState base = LnLayout.foundation();
        while (d.columnIndex < 256) {
            int wx = cp.getMinBlockX() + (d.columnIndex & 15), wz = cp.getMinBlockZ() + (d.columnIndex >> 4);
            d.columnIndex++;
            int x = wx - c.getX(), z = wz - c.getZ();
            int top = LnLayout.maxY(x, z);
            if (top < 0) continue;
            for (int y = 0; y <= top; y++) {
                int wy = c.getY() + y;
                if (wy < minY || wy > maxY) continue;
                BlockState want = LnLayout.at(x, y, z);
                if (want == null) continue;
                p.set(wx, wy, wz);
                if (level.getBlockState(p) != want) level.setBlock(p, want, FLAGS);
            }
            if (LnLayout.needsFoundation(x, z) && LnLayout.at(x, 0, z) != null) {
                // Fundação: desce até achar chão firme (máx. 40 blocos).
                for (int y = -1; y > LnLayout.MIN_Y; y--) {
                    int wy = c.getY() + y;
                    if (wy <= minY) break;
                    p.set(wx, wy, wz);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                    level.setBlock(p, base, FLAGS);
                }
            }
            if ((d.columnIndex & 7) == 0 && System.nanoTime() - start > BUDGET_NS) return d.columnIndex >= 256;
        }
        return true;
    }

    // ------------------------------------------------------------------ Detalhes com dados

    private static void details(ServerLevel level, BlockPos c) {
        for (BlockPos rel : LnLayout.AIZEN_CHESTS) chest(level, c.offset(rel), Direction.SOUTH);
        chest(level, c.offset(LnLayout.BARRAGAN_CHEST), Direction.EAST);
        lectern(level, c.offset(LnLayout.LECTERN));
        sign(level, c.offset(LnLayout.AIZEN_SIGN));
    }

    private static void chest(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity be) be.setLootTable(CHEST_LOOT, level.getRandom().nextLong());
    }

    /** O "registro do trono": livro escrito (páginas traduzidas) no púlpito diante do trono vazio. */
    private static void lectern(ServerLevel level, BlockPos pos) {
        BlockState state = LnLayout.lectern();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        LecternBlock.tryPlaceBook(null, level, pos, level.getBlockState(pos), throneBook());
    }

    public static ItemStack throneBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) pages.add(Filterable.passThrough(Component.translatable("book.isekai_mahou.las_noches.page" + i)));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Las Noches"), "???", 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.isekai_mahou.las_noches.throne_book"));
        return book;
    }

    private static void sign(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.BIRCH_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity be)) return;
        SignText text = new SignText();
        for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.translatable("sign.isekai_mahou.las_noches.throne." + (i + 1)));
        be.setText(text, true);
        be.setWaxed(true);
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_ALL);
    }

    private LnBuilder() {}
}
