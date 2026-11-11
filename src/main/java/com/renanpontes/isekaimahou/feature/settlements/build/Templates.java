package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Moldes (.nbt) lidos pelo gerenciador de estruturas do servidor e convertidos numa lista ORDENADA de blocos para os
 * construtores: primeiro os blocos cheios (de baixo para cima), depois os "apoiados" (tochas, portas, camas, placas,
 * lanternas...) — assim nada fica pendurado no ar durante a obra. Cache por servidor ({@link #clear} ao parar).
 */
public final class Templates {
    /** Um bloco do molde (posição local, estado, NBT do BlockEntity ou null). */
    public record Entry(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {}

    /** Molde pronto: tamanho e blocos na ordem de construção. */
    public record Tpl(int sx, int sy, int sz, List<Entry> entries) {
        /** Largura/profundidade depois da rotação. */
        public int width(Rotation r) {
            return r == Rotation.CLOCKWISE_90 || r == Rotation.COUNTERCLOCKWISE_90 ? sz : sx;
        }

        public int depth(Rotation r) {
            return r == Rotation.CLOCKWISE_90 || r == Rotation.COUNTERCLOCKWISE_90 ? sx : sz;
        }

        /** Posição local (x, y, z) girada dentro da caixa do molde (canto mínimo continua em 0,0). */
        public BlockPos rotate(int x, int y, int z, Rotation r) {
            return switch (r) {
                case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, y, x);
                case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, y, sz - 1 - z);
                case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sx - 1 - x);
                default -> new BlockPos(x, y, z);
            };
        }
    }

    private static final Map<ResourceLocation, Optional<Tpl>> CACHE = new HashMap<>();

    @Nullable
    public static synchronized Tpl get(ServerLevel level, ResourceLocation id) {
        return CACHE.computeIfAbsent(id, k -> Optional.ofNullable(load(level, k))).orElse(null);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    @Nullable
    private static Tpl load(ServerLevel level, ResourceLocation id) {
        Optional<StructureTemplate> opt;
        try {
            opt = level.getStructureManager().get(id);
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("[settlements] molde {} ilegível: {}", id, e.toString());
            return null;
        }
        if (opt.isEmpty()) {
            IsekaiMahou.LOGGER.warn("[settlements] molde {} não existe", id);
            return null;
        }
        CompoundTag tag = opt.get().save(new CompoundTag());
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() < 3) return null;
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(i)));
        List<Entry> solid = new ArrayList<>();
        List<Entry> attached = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int idx = b.getInt("state");
            if (idx < 0 || idx >= palette.size()) continue;
            BlockState st = palette.get(idx);
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW)) continue;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            Entry e = new Entry(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), st,
                    b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null);
            if (Block.isShapeFullBlock(st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) && e.nbt() == null) {
                solid.add(e);
            } else {
                attached.add(e);
            }
        }
        Comparator<Entry> order = Comparator.<Entry>comparingInt(e -> e.pos().getY())
                .thenComparingInt(e -> e.pos().getX()).thenComparingInt(e -> e.pos().getZ());
        solid.sort(order);
        attached.sort(order);
        List<Entry> all = new ArrayList<>(solid.size() + attached.size());
        all.addAll(solid);
        all.addAll(attached);
        return new Tpl(size.getInt(0), size.getInt(1), size.getInt(2), List.copyOf(all));
    }

    private Templates() {}
}
