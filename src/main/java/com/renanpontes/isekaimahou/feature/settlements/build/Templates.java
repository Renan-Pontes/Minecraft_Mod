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
        // sem computeIfAbsent: o molde com enfeites carrega o molde base (chamada aninhada no mesmo mapa)
        Optional<Tpl> hit = CACHE.get(id);
        if (hit == null) {
            hit = Optional.ofNullable(load(level, id));
            CACHE.put(id, hit);
        }
        return hit.orElse(null);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    public static final String PROC = "settlements_proc/";
    public static final String LEVELED = "settlements_lvl/";

    /** Molde procedural ({@link ProcBuildings}) de uma planta num estilo e nível. */
    public static ResourceLocation procId(com.renanpontes.isekaimahou.feature.settlements.SettlementStyle style, String key, int level) {
        return com.renanpontes.isekaimahou.IsekaiMahou.id(PROC + style.id + "_" + key + "_l" + Math.max(1, Math.min(5, level)));
    }

    /** Molde .nbt com os enfeites do nível do prédio em volta (nível 1 = o próprio molde). */
    public static ResourceLocation leveledId(ResourceLocation base, com.renanpontes.isekaimahou.feature.settlements.SettlementStyle style, int level) {
        if (level <= 1) return base;
        return com.renanpontes.isekaimahou.IsekaiMahou.id(LEVELED + Math.min(5, level) + "_" + style.id + "/" + base.getNamespace() + "/" + base.getPath());
    }

    @Nullable
    private static Tpl load(ServerLevel level, ResourceLocation id) {
        String path = id.getPath();
        try {
            // onda 5: muralha por degrau (gerada) e moldes de troca da reforma
            if (path.startsWith(TierWalls.PREFIX) || path.startsWith(TierWalls.SWAP)) return TierWalls.load(level, id);
            // onda 8 (city_looks): prédios no visual novo (um molde por nível, estilo e variante)
            if (path.startsWith(LookBuildings.PREFIX)) {
                Tpl look = LookBuildings.load(id);
                if (look != null) return look;
                // o gerador falhou (nunca deveria): a obra usa o molde antigo da planta em vez de ficar sem prédio
                ResourceLocation old = LookBuildings.oldId(id);
                return old == null ? null : get(level, old);
            }
            if (path.startsWith(PROC)) {
                String rest = path.substring(PROC.length());
                int us = rest.indexOf('_');
                int lv = rest.lastIndexOf("_l");
                if (us < 0 || lv <= us) return null;
                var style = com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.byId(rest.substring(0, us));
                return ProcBuildings.build(rest.substring(us + 1, lv), style, Integer.parseInt(rest.substring(lv + 2)));
            }
            if (path.startsWith(LEVELED)) {
                String rest = path.substring(LEVELED.length());
                String[] parts = rest.split("/", 3);
                if (parts.length < 3) return null;
                int us = parts[0].indexOf('_');
                int lvl = Integer.parseInt(parts[0].substring(0, us));
                var style = com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.byId(parts[0].substring(us + 1));
                ResourceLocation base = ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]);
                Tpl b = get(level, base);
                if (b == null) return null;
                List<Entry> all = new ArrayList<>(b.entries());
                // onda 5: do Burgo em diante as fontes da praça ficam no lugar dos canteiros das diagonais — a reforma
                // do centro não os recoloca (senão cada subida de degrau poria o canteiro por cima da fonte)
                if (lvl >= 3 && parts[2].endsWith("_" + Blueprint.PLAZA)) all.removeIf(e -> plazaCorner(b, e));
                all.addAll(ProcBuildings.ornaments(style == null
                        ? com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.KINGDOM : style, lvl, b.sx(), b.sz()));
                return fromEntries(b.sx(), b.sy(), b.sz(), all);
            }
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("[settlements] molde {} inválido: {}", id, e.toString());
            return null;
        }
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
        List<Entry> raw = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int idx = b.getInt("state");
            if (idx < 0 || idx >= palette.size()) continue;
            BlockState st = palette.get(idx);
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW) || st.is(Blocks.STRUCTURE_BLOCK)) continue;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            raw.add(new Entry(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), st,
                    b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null));
        }
        return fromEntries(size.getInt(0), size.getInt(1), size.getInt(2), raw);
    }

    /**
     * Bloco de um dos 4 canteiros das diagonais da praça: a caixa 3×3 a 3 blocos do coração (acima do piso) e a copa
     * das árvores plantadas neles (folhas altas — as palmeiras do deserto passam da caixa).
     */
    public static boolean plazaCorner(Tpl plaza, Entry e) {
        BlockPos p = e.pos();
        int dx = Math.abs(p.getX() - plaza.sx() / 2), dz = Math.abs(p.getZ() - plaza.sz() / 2);
        if (p.getY() >= 1 && dx >= 2 && dx <= 4 && dz >= 2 && dz <= 4) return true;
        return p.getY() >= 2 && e.state().is(net.minecraft.tags.BlockTags.LEAVES);
    }

    private static List<Entry> all(List<Entry> a, List<Entry> b) {
        List<Entry> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /**
     * Molde a partir de uma lista de blocos (moldes procedurais, {@link ProcBuildings}): ordena na ordem de obra
     * (cheios de baixo para cima, depois os apoiados) e tira o conteúdo/saque dos baús (nada de itens de graça a
     * cada reconstrução).
     */
    public static Tpl fromEntries(int sx, int sy, int sz, List<Entry> entries) {
        List<Entry> solid = new ArrayList<>();
        List<Entry> attached = new ArrayList<>();
        for (Entry e : entries) {
            if (e.state().isAir()) continue;
            Entry clean = e.nbt() == null ? e : new Entry(e.pos(), e.state(), sanitize(e.nbt()));
            if (Block.isShapeFullBlock(e.state().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) && clean.nbt() == null) {
                solid.add(clean);
            } else {
                attached.add(clean);
            }
        }
        Comparator<Entry> order = Comparator.<Entry>comparingInt(e -> e.pos().getY())
                .thenComparingInt(e -> e.pos().getX()).thenComparingInt(e -> e.pos().getZ());
        solid.sort(order);
        attached.sort(order);
        return new Tpl(sx, sy, sz, List.copyOf(all(solid, attached)));
    }

    /** Baús/barris dos moldes chegam vazios e sem tabela de saque (reconstruir não pode virar fazenda de itens). */
    private static CompoundTag sanitize(CompoundTag nbt) {
        if (!nbt.contains("Items") && !nbt.contains("LootTable") && !nbt.contains("item")) return nbt;
        CompoundTag c = nbt.copy();
        c.remove("Items");
        c.remove("LootTable");
        c.remove("LootTableSeed");
        c.remove("item");
        return c;
    }

    private Templates() {}
}
