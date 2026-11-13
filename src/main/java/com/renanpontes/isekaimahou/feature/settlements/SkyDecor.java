package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.network.SkyPackets;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Onda 7 (sky_ui) — <b>a beleza da Ilha Celeste</b>: os enfeites que entram POR CIMA do gerador de {@link SkyCity}
 * (que não é tocado), quando a terra de um raio termina de se formar:
 * <ul>
 *   <li><b>Cachoeiras</b> caindo da borda: uma bica de pedra encostada na beirada e um laguinho próprio, flutuando
 *       logo abaixo. A água NÃO inunda nada: a nascente fica cercada pelos quatro lados (só escorre para baixo, numa
 *       coluna só) e o laguinho tem borda — nada chega ao chão. No estilo Nórdico (e em bioma frio) a queda é de gelo.</li>
 *   <li><b>Ilhotas-satélite</b> (2 a 4) em volta da ilha, cada uma com a árvore do estilo (cerejeira no Oriental,
 *       pinheiro nevado no Nórdico, acácia no Deserto...).</li>
 *   <li><b>Barriga</b>: cristais luminosos pendurados e raízes, cipós de frutas-brilhantes, estalactites ou flores de
 *       esporos, conforme o estilo.</li>
 *   <li><b>Névoa de nuvem</b> na borda e no pé das cachoeiras: só no cliente ({@code client/SkyFx}, pelo
 *       {@code FxQuality}); daqui sai, a cada 5 s, um pacote pequeno com a ilha para quem está perto.</li>
 * </ul>
 * <b>Regras</b>: só entra em ar (ou mato solto) — nada do jogador é trocado; tudo o que é colocado fica RASTREADO
 * ({@code data/isekai_mahou_sky_decor.dat}); quando a ilha começa a crescer para uma forma nova, os enfeites antigos
 * saem (só os blocos que ainda são os que este código pôs) e, quando a terra nova termina, são refeitos na borda nova.
 * Poucos blocos por tick, do orçamento comum das obras ({@link ConstructionEngine#claim}), só em chunk carregado; o
 * que ficou em chunk descarregado espera. Cidade no chão nunca entra aqui.
 */
public final class SkyDecor {
    private static final String NAME = "isekai_mahou_sky_decor";
    /** Tipos de enfeite da lista ({@link #plan}). */
    private static final int FALL = 0, ISLET = 1, CRYSTAL = 2, HANG = 3;
    /** O que pende da barriga. */
    private static final int ROOTS = 0, GLOW_VINE = 1, DRIP = 2, WEEPING = 3, SPORE = 4;
    /** Resultado de {@link #build}: a área não está carregada — fica para depois. */
    private static final int RETRY = -1;
    /** Blocos por tick (do orçamento das obras) e enfeites removidos por tick. */
    private static final int BLOCKS_PER_TICK = 48, REMOVE_PER_TICK = 200;
    /** Sem vizinhos nem formas atualizados: nada "cai" nem dá item ao mexer nos enfeites. */
    private static final int QUIET = 2 | 16;

    private record Feat(int type, int x, int z, int a, int b) {}

    /** Árvore de uma ilhota: tronco, folhas e formato da copa (0 = redonda, 1 = cone, 2 = chata). */
    private record Tree(Block log, Block leaves, @Nullable Block leaves2, int shape) {}

    /** Materiais dos enfeites por estilo. */
    private record Look(Block top, Block soil, Block stone, Block stone2, Block lip, Block body, @Nullable Block glow, boolean cluster,
                        boolean frozen, Tree tree, @Nullable Block plant, int[] hangs) {}

    private static final Map<SettlementStyle, Look> LOOKS = new java.util.EnumMap<>(SettlementStyle.class);

    static {
        LOOKS.put(SettlementStyle.KINGDOM, new Look(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE,
                Blocks.AMETHYST_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, true, false, new Tree(Blocks.OAK_LOG, Blocks.OAK_LEAVES, null, 0),
                Blocks.SHORT_GRASS, new int[] {ROOTS, ROOTS, GLOW_VINE}));
        LOOKS.put(SettlementStyle.DESERT, new Look(Blocks.SAND, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                Blocks.AMETHYST_BLOCK, Blocks.OCHRE_FROGLIGHT, true, false, new Tree(Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, null, 2),
                Blocks.DEAD_BUSH, new int[] {ROOTS, DRIP, DRIP}));
        LOOKS.put(SettlementStyle.NORDIC, new Look(Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE,
                Blocks.BLUE_ICE, Blocks.SEA_LANTERN, false, true, new Tree(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, null, 1),
                Blocks.SNOW, new int[] {ROOTS, DRIP, ROOTS}));
        LOOKS.put(SettlementStyle.ORIENTAL, new Look(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.CALCITE, Blocks.MOSSY_STONE_BRICKS,
                Blocks.AMETHYST_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, true, false, new Tree(Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, null, 0),
                Blocks.PINK_PETALS, new int[] {GLOW_VINE, GLOW_VINE, ROOTS}));
        LOOKS.put(SettlementStyle.ELVEN, new Look(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                Blocks.AMETHYST_BLOCK, Blocks.VERDANT_FROGLIGHT, true, false,
                new Tree(Blocks.OAK_LOG, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, 0),
                Blocks.FERN, new int[] {GLOW_VINE, GLOW_VINE, SPORE, ROOTS}));
        LOOKS.put(SettlementStyle.GOTHIC, new Look(Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
                Blocks.AMETHYST_BLOCK, Blocks.CRYING_OBSIDIAN, true, false, new Tree(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, null, 0),
                Blocks.FERN, new int[] {WEEPING, ROOTS, WEEPING}));
        LOOKS.put(SettlementStyle.DWARVEN, new Look(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.POLISHED_DEEPSLATE,
                Blocks.AMETHYST_BLOCK, Blocks.OCHRE_FROGLIGHT, true, false, new Tree(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, null, 1),
                null, new int[] {DRIP, DRIP, ROOTS}));
    }

    private static Look look(SettlementStyle style) {
        Look l = LOOKS.get(style);
        return l != null ? l : LOOKS.get(SettlementStyle.KINGDOM);
    }

    // =====================================================================================
    //  O que foi colocado (salvo)
    // =====================================================================================
    /** Enfeites de uma ilha: o que está de pé, o que falta construir e o que ficou para remover. */
    private static final class Rec {
        /** {@link CityLayout#half} do raio enfeitado (0 = ainda sem enfeites) e o raio. */
        int half, radius;
        /** Índices da lista de enfeites que ainda faltam (os de chunk descarregado voltam para o fim). */
        final IntArrayList pending = new IntArrayList();
        /** Blocos de pé: posição + índice do bloco em {@link Data#palette}. */
        final LongArrayList pos = new LongArrayList();
        final IntArrayList kind = new IntArrayList();
        /** Cachoeiras: x, z, y da nascente, y do piso do laguinho, 1 = de gelo. */
        final List<int[]> falls = new ArrayList<>();
        /** Enfeites de uma forma antiga da ilha, esperando sair (chunk descarregado). */
        final LongArrayList gonePos = new LongArrayList();
        final IntArrayList goneKind = new IntArrayList();
        final List<int[]> goneFalls = new ArrayList<>();
        // ---- não salvo
        @Nullable List<Feat> plan;
        int planHalf, wait, goneWait;
    }

    /** {@code data/isekai_mahou_sky_decor.dat}: por id de cidade. Cidade no chão nunca ganha registro. */
    private static final class Data extends SavedData {
        final Map<String, Rec> recs = new HashMap<>();
        /** Ids dos blocos usados (as listas guardam só o índice). */
        final List<String> palette = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        int index(Block b) {
            int i = blocks.indexOf(b);
            if (i >= 0) return i;
            blocks.add(b);
            palette.add(BuiltInRegistries.BLOCK.getKey(b).toString());
            return blocks.size() - 1;
        }

        @Nullable
        Block block(int i) {
            return i >= 0 && i < blocks.size() ? blocks.get(i) : null;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag pal = new ListTag();
            for (String id : palette) pal.add(StringTag.valueOf(id));
            tag.put("Palette", pal);
            CompoundTag all = new CompoundTag();
            for (Map.Entry<String, Rec> e : recs.entrySet()) {
                Rec r = e.getValue();
                CompoundTag t = new CompoundTag();
                t.putInt("Half", r.half);
                t.putInt("Radius", r.radius);
                t.putIntArray("Pending", r.pending.toIntArray());
                t.putLongArray("Pos", r.pos.toLongArray());
                t.putIntArray("Kind", r.kind.toIntArray());
                t.putIntArray("Falls", flat(r.falls));
                t.putLongArray("GonePos", r.gonePos.toLongArray());
                t.putIntArray("GoneKind", r.goneKind.toIntArray());
                t.putIntArray("GoneFalls", flat(r.goneFalls));
                all.put(e.getKey(), t);
            }
            tag.put("Cities", all);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag pal = tag.getList("Palette", Tag.TAG_STRING);
            for (int i = 0; i < pal.size(); i++) {
                d.palette.add(pal.getString(i));
                ResourceLocation id = ResourceLocation.tryParse(pal.getString(i));
                // bloco que não existe mais vira "ar": a remoção nunca casa com ele e o bloco fica onde está
                d.blocks.add(id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id));
            }
            CompoundTag all = tag.getCompound("Cities");
            for (String key : all.getAllKeys()) {
                CompoundTag t = all.getCompound(key);
                Rec r = new Rec();
                r.half = t.getInt("Half");
                r.radius = t.getInt("Radius");
                r.pending.addElements(0, t.getIntArray("Pending"));
                fill(r.pos, r.kind, t.getLongArray("Pos"), t.getIntArray("Kind"));
                unflat(r.falls, t.getIntArray("Falls"));
                fill(r.gonePos, r.goneKind, t.getLongArray("GonePos"), t.getIntArray("GoneKind"));
                unflat(r.goneFalls, t.getIntArray("GoneFalls"));
                d.recs.put(key, r);
            }
            return d;
        }

        private static void fill(LongArrayList pos, IntArrayList kind, long[] p, int[] k) {
            int n = Math.min(p.length, k.length);
            for (int i = 0; i < n; i++) {
                pos.add(p[i]);
                kind.add(k[i]);
            }
        }

        private static int[] flat(List<int[]> falls) {
            int[] out = new int[falls.size() * 5];
            for (int i = 0; i < falls.size(); i++) System.arraycopy(falls.get(i), 0, out, i * 5, 5);
            return out;
        }

        private static void unflat(List<int[]> falls, int[] raw) {
            for (int i = 0; i + 4 < raw.length; i += 5) falls.add(new int[] {raw[i], raw[i + 1], raw[i + 2], raw[i + 3], raw[i + 4]});
        }
    }

    // =====================================================================================
    //  Tick (chamado pelo Coração, logo depois de SkyCity.tick)
    // =====================================================================================
    public static void tick(ServerLevel sl, Settlement s, int age) {
        // onda 9 (isles): o arquipélago não usa estes enfeites (feitos para a borda da ilha única)
        if (!s.sky || s.isles) return;
        Data data = Data.get(sl.getServer());
        Rec rec = data.recs.get(s.id);
        if (rec == null) {
            if (s.skyLand <= 0) return;
            rec = new Rec();
            data.recs.put(s.id, rec);
            data.setDirty();
        }
        if (age % 100 == 37) broadcast(sl, s, rec);
        // a ilha começou a crescer para uma forma nova: os enfeites da borda antiga saem antes de a terra chegar
        if (rec.half != 0 && s.skyGoal > 0 && CityLayout.half(s.skyGoal) != rec.half) {
            retire(rec);
            data.setDirty();
            remove(sl, data, rec, 4 * REMOVE_PER_TICK);
        }
        if (!rec.gonePos.isEmpty() || !rec.goneFalls.isEmpty()) {
            if (rec.goneWait > 0) rec.goneWait--;
            else if (remove(sl, data, rec, REMOVE_PER_TICK) == 0) rec.goneWait = 100;
        }
        if (s.skyGoal > 0 || s.skyLand <= 0) return;
        int half = CityLayout.half(s.skyLand);
        if (rec.half != half) {
            // terra nova completa: monta a lista dos enfeites desta forma e começa
            if (rec.half != 0) retire(rec);
            rec.half = half;
            rec.radius = s.skyLand;
            rec.plan = plan(s, rec.radius);
            rec.planHalf = half;
            rec.pending.clear();
            for (int i = 0; i < rec.plan.size(); i++) rec.pending.add(i);
            rec.wait = 40;
            data.setDirty();
        }
        if (rec.pending.isEmpty()) return;
        if (rec.wait > 0) {
            rec.wait--;
            return;
        }
        if ((age & 1) == 0) step(sl, s, data, rec, ConstructionEngine.claim(sl, BLOCKS_PER_TICK));
    }

    /** A lista dos enfeites sai sempre igual para a mesma ilha (semente = coração + forma): só o índice é salvo. */
    private static List<Feat> planOf(Settlement s, Rec rec) {
        if (rec.plan == null || rec.planHalf != rec.half) {
            rec.plan = plan(s, rec.radius);
            rec.planHalf = rec.half;
        }
        return rec.plan;
    }

    private static void step(ServerLevel sl, Settlement s, Data data, Rec rec, int budget) {
        if (budget <= 0) return;
        List<Feat> plan = planOf(s, rec);
        int tries = Math.min(rec.pending.size(), 24), built = 0;
        while (budget > 0 && tries-- > 0 && !rec.pending.isEmpty()) {
            int idx = rec.pending.removeInt(0);
            if (idx < 0 || idx >= plan.size()) continue;
            int placed = build(sl, s, data, rec, plan.get(idx));
            if (placed == RETRY) {
                rec.pending.add(idx);
                continue;
            }
            built++;
            budget -= Math.max(1, placed);
        }
        // só sobrou enfeite em chunk descarregado: tenta de novo daqui a pouco
        if (built == 0) rec.wait = 100;
        data.setDirty();
    }

    /** Os enfeites de pé passam para a fila de remoção (a ilha mudou de forma, ou o comando de refazer). */
    private static void retire(Rec rec) {
        rec.gonePos.addAll(rec.pos);
        rec.goneKind.addAll(rec.kind);
        rec.goneFalls.addAll(rec.falls);
        rec.pos.clear();
        rec.kind.clear();
        rec.falls.clear();
        rec.pending.clear();
        rec.half = 0;
        rec.plan = null;
        rec.goneWait = 0;
    }

    // =====================================================================================
    //  Remoção (só o que ainda é o bloco que este código pôs)
    // =====================================================================================
    private static int remove(ServerLevel sl, Data data, Rec rec, int max) {
        int done = 0;
        // primeiro a água: a coluna da queda e o laguinho somem de uma vez, antes de a pedra em volta sair
        for (int i = rec.goneFalls.size() - 1; i >= 0; i--) {
            int[] f = rec.goneFalls.get(i);
            if (!loaded(sl, f[0], f[1], 3)) continue;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = f[2]; y > f[3]; y--) {
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        // a coluna da queda é só a do meio; o laguinho (3×3) fica nas duas camadas de baixo
                        if ((ox != 0 || oz != 0) && y > f[3] + 2) continue;
                        if (sl.getBlockState(m.set(f[0] + ox, y, f[1] + oz)).is(Blocks.WATER)) sl.setBlock(m, Blocks.AIR.defaultBlockState(), QUIET);
                    }
                }
            }
            rec.goneFalls.remove(i);
            done++;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // do fim para o começo: o que foi posto por último (em cima, pendurado) sai antes do apoio
        for (int i = rec.gonePos.size() - 1; i >= 0 && done < max; i--) {
            m.set(rec.gonePos.getLong(i));
            if (!sl.hasChunk(m.getX() >> 4, m.getZ() >> 4)) continue;
            Block expected = data.block(rec.goneKind.getInt(i));
            BlockState cur = sl.getBlockState(m);
            if (expected != null && expected != Blocks.AIR && cur.is(expected)) sl.setBlock(m, Blocks.AIR.defaultBlockState(), QUIET);
            rec.gonePos.removeLong(i);
            rec.goneKind.removeInt(i);
            done++;
        }
        if (done > 0) data.setDirty();
        return done;
    }

    // =====================================================================================
    //  A lista dos enfeites de uma ilha
    // =====================================================================================
    private static List<Feat> plan(Settlement s, int landRadius) {
        List<Feat> out = new ArrayList<>();
        // o raio guardado quando os enfeites começaram: a lista (e os índices salvos) não muda se a cidade ganhar um anel par
        int radius = Math.max(1, landRadius);
        BlockPos h = s.heart;
        Random rnd = new Random(h.getX() * 341873128712L + h.getZ() * 132897987541L + CityLayout.half(radius) * 31L);
        Direction pierSide = SkyCity.pierSide(s);
        double pier = Math.atan2(pierSide.getStepZ(), pierSide.getStepX());
        List<Double> used = new ArrayList<>();
        int top = SkyCity.topY(s);

        // cachoeiras: longe do lado do cais e umas das outras
        // duas candidatas a mais do que o limite: uma queda que não couber (borda recortada demais) tem substituta
        int falls = maxFalls(radius) + 2;
        for (int tries = 0, made = 0; tries < 80 && made < falls; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            if (near(a, pier, 0.75) || near(a, used, 0.55)) continue;
            int[] rim = rimLand(s, a);
            if (rim == null) continue;
            Direction dir = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? Direction.EAST : Direction.WEST)
                    : (Math.sin(a) > 0 ? Direction.SOUTH : Direction.NORTH);
            int x = rim[0], z = rim[1], guard = 0;
            while (SkyCity.hasLand(s, x, z) && guard++ < 32) {
                x += dir.getStepX();
                z += dir.getStepZ();
            }
            // a bica fica na primeira coluna SEM terra; a queda desce por fora da ilha (2 colunas de folga para o laguinho)
            if (SkyCity.hasLand(s, x, z) || SkyCity.hasLand(s, x + dir.getStepX() * 2, z + dir.getStepZ() * 2)) continue;
            // queda de 12 a 16 blocos: o laguinho fica abaixo da parte grossa da beirada
            out.add(new Feat(FALL, x, z, dir.get2DDataValue(), 12 + rnd.nextInt(5)));
            used.add(a);
            made++;
        }
        // ilhotas-satélite: 2 a 4, um pouco afastadas da borda, em alturas diferentes
        int islets = 2 + rnd.nextInt(3);
        // as ilhotas ficam bem mais para fora que as cachoeiras: só precisam se afastar umas das outras (e do cais)
        used.clear();
        for (int tries = 0, made = 0; tries < 80 && made < islets; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            if (near(a, pier, 0.9) || near(a, used, 0.7)) continue;
            int[] rim = rimLand(s, a);
            if (rim == null) continue;
            double dist = Math.hypot(rim[0] - h.getX(), rim[1] - h.getZ()) + 16 + rnd.nextInt(14);
            int x = h.getX() + (int) Math.round(Math.cos(a) * dist), z = h.getZ() + (int) Math.round(Math.sin(a) * dist);
            out.add(new Feat(ISLET, x, z, top - 8 + rnd.nextInt(13), 3 + rnd.nextInt(3) | rnd.nextInt(256) << 8));
            used.add(a);
            made++;
        }
        // barriga: cristais luminosos (onde a ilha é grossa) e o que pende (raízes, cipós, estalactites)
        int reach = SkyCity.reach(radius), span = 2 * reach + 1;
        long area = (long) span * span;
        int crystals = (int) Math.max(18, Math.min(120, area / 1400)), hangs = (int) Math.max(60, Math.min(420, area / 320));
        Look look = look(s.style);
        for (int tries = 0, made = 0; tries < crystals * 8 && made < crystals; tries++) {
            int dx = rnd.nextInt(span) - reach, dz = rnd.nextInt(span) - reach;
            if (SkyCity.thickness(h, radius, dx, dz) < 7) continue;
            out.add(new Feat(CRYSTAL, h.getX() + dx, h.getZ() + dz, 1 + rnd.nextInt(3), rnd.nextInt(5) < 3 ? 1 : 0));
            made++;
        }
        for (int tries = 0, made = 0; tries < hangs * 8 && made < hangs; tries++) {
            int dx = rnd.nextInt(span) - reach, dz = rnd.nextInt(span) - reach;
            if (SkyCity.thickness(h, radius, dx, dz) < 3) continue;
            out.add(new Feat(HANG, h.getX() + dx, h.getZ() + dz, look.hangs()[rnd.nextInt(look.hangs().length)], rnd.nextInt(1 << 16)));
            made++;
        }
        return out;
    }

    /** Cachoeiras de uma ilha: 2 no começo, 3 e 4 nas ilhas grandes. */
    private static int maxFalls(int radius) {
        return 2 + (radius >= 4 ? 1 : 0) + (radius >= 8 ? 1 : 0);
    }

    private static boolean near(double a, double b, double within) {
        double d = Math.abs(a - b) % (Math.PI * 2);
        return Math.min(d, Math.PI * 2 - d) < within;
    }

    private static boolean near(double a, List<Double> others, double within) {
        for (double o : others) if (near(a, o, within)) return true;
        return false;
    }

    /** Última coluna com terra indo do coração no ângulo {@code a} (de fora para dentro), ou null. */
    @Nullable
    private static int[] rimLand(Settlement s, double a) {
        double cos = Math.cos(a), sin = Math.sin(a);
        for (int d = (int) (SkyCity.reach(SkyCity.shapeRadius(s)) * 1.45) + 2; d > 8; d--) {
            int x = s.heart.getX() + (int) Math.round(cos * d), z = s.heart.getZ() + (int) Math.round(sin * d);
            if (SkyCity.hasLand(s, x, z)) return new int[] {x, z};
        }
        return null;
    }

    // =====================================================================================
    //  Construção de um enfeite
    // =====================================================================================
    private static boolean loaded(ServerLevel sl, int x, int z, int r) {
        return sl.hasChunk(x - r >> 4, z - r >> 4) && sl.hasChunk(x + r >> 4, z - r >> 4) && sl.hasChunk(x - r >> 4, z + r >> 4)
                && sl.hasChunk(x + r >> 4, z + r >> 4);
    }

    /** Livre para um enfeite entrar: ar ou mato solto (nunca fluido, nunca bloco de alguém). */
    private static boolean free(BlockState st) {
        return st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty();
    }

    /** Coloca (se o lugar está livre) e rastreia. */
    private static boolean put(ServerLevel sl, Data data, Rec rec, BlockPos p, BlockState st, int flags) {
        if (p.getY() <= sl.getMinBuildHeight() || p.getY() >= sl.getMaxBuildHeight() || !free(sl.getBlockState(p))) return false;
        sl.setBlock(p, st, flags);
        rec.pos.add(p.asLong());
        rec.kind.add(data.index(st.getBlock()));
        return true;
    }

    private static boolean put(ServerLevel sl, Data data, Rec rec, BlockPos p, Block b) {
        return put(sl, data, rec, p, b.defaultBlockState(), QUIET);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 1103515245 + z * 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        return h ^ h >>> 16;
    }

    /** Devolve os blocos colocados, 0 se o enfeite não cabe ali (é pulado) ou {@link #RETRY}. */
    private static int build(ServerLevel sl, Settlement s, Data data, Rec rec, Feat f) {
        Look look = look(s.style);
        return switch (f.type()) {
            case FALL -> fall(sl, s, data, rec, look, f);
            case ISLET -> islet(sl, data, rec, look, f);
            case CRYSTAL -> crystal(sl, s, data, rec, look, f);
            default -> hang(sl, s, data, rec, f);
        };
    }

    // ---- cachoeira
    /**
     * Bica + queda + laguinho. A nascente fica na primeira coluna fora da borda, na altura do topo, CERCADA pelos
     * quatro lados (a beirada da ilha e três pedras): água de nascente com água embaixo espalharia para os lados — com
     * a cerca, desce uma coluna só. Ela cai dentro de um laguinho 3×3 cheio (piso + borda), que não transborda.
     */
    private static int fall(ServerLevel sl, Settlement s, Data data, Rec rec, Look look, Feat f) {
        int x = f.x(), z = f.z(), top = SkyCity.topY(s), y0 = top - f.b();
        if (rec.falls.size() >= maxFalls(rec.radius)) return 0;
        if (!loaded(sl, x, z, 3)) return RETRY;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // confere tudo antes de pôr o primeiro bloco: coluna livre, cerca possível, laguinho possível
        for (int y = y0 + 1; y <= top; y++) if (!sl.getBlockState(m.set(x, y, z)).isAir()) return 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState st = sl.getBlockState(m.set(x + d.getStepX(), top, z + d.getStepZ()));
            if (!free(st) && !st.isCollisionShapeFullBlock(sl, m)) return 0;
        }
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                int ax = Math.abs(ox), az = Math.abs(oz);
                if (ax == 2 && az == 2) continue;
                boolean inner = ax <= 1 && az <= 1;
                BlockState st = sl.getBlockState(m.set(x + ox, y0 + 1, z + oz));
                // dentro: tem de estar vazio; borda: vazia ou já sólida (a própria ilha serve de borda)
                if (inner ? !st.isAir() : !free(st) && !st.isCollisionShapeFullBlock(sl, m)) return 0;
                if (inner) {
                    BlockState below = sl.getBlockState(m.set(x + ox, y0, z + oz));
                    if (!free(below) && !below.isCollisionShapeFullBlock(sl, m)) return 0;
                }
            }
        }
        boolean frozen = look.frozen() || sl.getBiome(m.set(x, top, z)).value().coldEnoughToSnow(m);
        int n = 0;
        // laguinho: piso 3×3, borda de pedra, um pouco de rocha embaixo
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                int ax = Math.abs(ox), az = Math.abs(oz);
                if (ax == 2 && az == 2) continue;
                if (ax <= 1 && az <= 1) {
                    if (put(sl, data, rec, m.set(x + ox, y0, z + oz), (hash(x + ox, y0, z + oz) & 3) == 0 ? look.stone2() : look.stone())) n++;
                    if (ax + az <= 1 && put(sl, data, rec, m.set(x + ox, y0 - 1, z + oz), look.stone())) n++;
                } else if (put(sl, data, rec, m.set(x + ox, y0 + 1, z + oz), (hash(x + ox, y0, z + oz) & 1) == 0 ? look.lip() : look.stone())) {
                    n++;
                }
            }
        }
        if (put(sl, data, rec, m.set(x, y0 - 2, z), look.stone2())) n++;
        // a borda tem de ter ficado inteira (alguém pode ter posto algo entre a conferência e agora): senão, sem água
        boolean sealed = true;
        for (int ox = -2; ox <= 2 && sealed; ox++) {
            for (int oz = -2; oz <= 2 && sealed; oz++) {
                int ax = Math.abs(ox), az = Math.abs(oz);
                if (ax == 2 && az == 2) continue;
                if (ax <= 1 && az <= 1) sealed = sl.getBlockState(m.set(x + ox, y0, z + oz)).isCollisionShapeFullBlock(sl, m);
                else sealed = sl.getBlockState(m.set(x + ox, y0 + 1, z + oz)).isCollisionShapeFullBlock(sl, m);
            }
        }
        // a bica: três pedras (ou a ilha) em volta da nascente
        for (Direction d : Direction.Plane.HORIZONTAL) {
            m.set(x + d.getStepX(), top, z + d.getStepZ());
            if (free(sl.getBlockState(m)) && put(sl, data, rec, m, look.lip())) n++;
            sealed &= sl.getBlockState(m.set(x + d.getStepX(), top, z + d.getStepZ())).isCollisionShapeFullBlock(sl, m);
        }
        if (!sealed) return Math.max(1, n);
        if (frozen) {
            // queda congelada: coluna de gelo azul, pé mais largo de gelo compactado
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) if (put(sl, data, rec, m.set(x + ox, y0 + 1, z + oz), Blocks.PACKED_ICE)) n++;
            }
            for (int y = y0 + 2; y <= top; y++) if (put(sl, data, rec, m.set(x, y, z), Blocks.BLUE_ICE)) n++;
        } else {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) if (put(sl, data, rec, m.set(x + ox, y0 + 1, z + oz), Blocks.WATER.defaultBlockState(), 2)) n++;
            }
            // a nascente por último (com atualização: é ela que começa a escorrer)
            if (put(sl, data, rec, m.set(x, top, z), Blocks.WATER.defaultBlockState(), 3)) n++;
        }
        rec.falls.add(new int[] {x, z, top, y0, frozen ? 1 : 0});
        return Math.max(1, n);
    }

    // ---- ilhota-satélite
    private static int islet(ServerLevel sl, Data data, Rec rec, Look look, Feat f) {
        int x = f.x(), z = f.z(), top = f.a(), r = f.b() & 255, seed = f.b() >> 8;
        if (!loaded(sl, x, z, r + 4)) return RETRY;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // céu livre no miolo (chão, copa e um pouco para baixo): senão a ilhota não nasce
        for (int y = top - 4; y <= top + 9; y++) if (!sl.getBlockState(m.set(x, y, z)).isAir()) return 0;
        int n = 0;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int hsh = hash(x + dx, seed, z + dz);
                double d = Math.sqrt(dx * dx + dz * dz) + ((hsh & 255) / 255.0 - 0.5) * 1.3;
                if (d > r) continue;
                int t = Math.max(1, (int) ((r - d) * 1.7 + 1.5) + (hsh >> 8 & 1));
                boolean supported = false;
                // de baixo para cima (areia nunca fica no ar)
                for (int k = t - 1; k >= 0; k--) {
                    Block b = k == 0 ? look.top() : k <= 2 ? look.soil() : (hash(x + dx, top - k, z + dz) & 3) == 0 ? look.stone2() : look.stone();
                    if (b instanceof FallingBlock && !supported) b = look.soil();
                    if (put(sl, data, rec, m.set(x + dx, top - k, z + dz), b)) n++;
                    supported = true;
                }
                // matinho/neve/pétalas em parte do topo (longe do tronco)
                if (look.plant() != null && (dx != 0 || dz != 0) && (hsh >> 9 & 3) == 0 && sl.getBlockState(m.set(x + dx, top, z + dz)).is(look.top())
                        && put(sl, data, rec, m.set(x + dx, top + 1, z + dz), look.plant())) n++;
            }
        }
        n += tree(sl, data, rec, look, x, top + 1, z, seed);
        return Math.max(1, n);
    }

    private static int tree(ServerLevel sl, Data data, Rec rec, Look look, int x, int y, int z, int seed) {
        Tree tree = look.tree();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = 0, h = 4 + (seed & 1) + (tree.shape() == 1 ? 1 : 0);
        for (int i = 0; i < h; i++) if (put(sl, data, rec, m.set(x, y + i, z), tree.log())) n++;
        // camadas da copa: (altura a partir do pé, raio)
        int[][] layers = switch (tree.shape()) {
            case 1 -> new int[][] {{h - 3, 2}, {h - 2, 1}, {h - 1, 2}, {h, 1}, {h + 1, 0}};
            case 2 -> new int[][] {{h - 1, 3}, {h, 2}};
            default -> new int[][] {{h - 2, 2}, {h - 1, 2}, {h, 1}, {h + 1, 1}};
        };
        for (int[] layer : layers) {
            int r = layer[1];
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // cantos comidos (copa arredondada); o topo do cone é só o bloco do meio
                    if (r > 0 && Math.abs(dx) == r && Math.abs(dz) == r && (r > 1 || (hash(x + dx, y + layer[0], z + dz) & 1) == 0)) continue;
                    Block leaf = tree.leaves2() != null && (hash(x + dx, y + layer[0], z + dz) & 3) == 0 ? tree.leaves2() : tree.leaves();
                    BlockState st = leaf.defaultBlockState();
                    if (st.hasProperty(LeavesBlock.PERSISTENT)) st = st.setValue(LeavesBlock.PERSISTENT, true);
                    if (put(sl, data, rec, m.set(x + dx, y + layer[0], z + dz), st, QUIET)) n++;
                }
            }
        }
        // pinheiro nevado no Nórdico
        if (look.frozen()) {
            int[] crown = layers[layers.length - 1];
            if (put(sl, data, rec, m.set(x, y + crown[0] + 1, z), Blocks.SNOW)) n++;
        }
        return n;
    }

    // ---- barriga
    /** Primeiro bloco de AR debaixo da ilha na coluna (o de cima é rocha inteira), ou {@link Integer#MIN_VALUE}. */
    private static int under(ServerLevel sl, Settlement s, int x, int z) {
        int fy = SkyCity.floorY(s, x, z);
        if (fy == Integer.MAX_VALUE) return Integer.MIN_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // a conta da forma dá o fundo; confere no mundo (2 para cima, 3 para baixo: o gerador contorna o que já havia)
        for (int y = fy + 2; y >= fy - 3; y--) {
            if (sl.getBlockState(m.set(x, y, z)).isCollisionShapeFullBlock(sl, m) && sl.getBlockState(m.set(x, y - 1, z)).isAir()) return y - 1;
        }
        return Integer.MIN_VALUE;
    }

    private static int crystal(ServerLevel sl, Settlement s, Data data, Rec rec, Look look, Feat f) {
        if (!sl.hasChunk(f.x() >> 4, f.z() >> 4)) return RETRY;
        int y = under(sl, s, f.x(), f.z());
        if (y == Integer.MIN_VALUE) return 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = 0, len = f.a();
        for (int i = 0; i < len; i++) {
            Block b = i == 0 && f.b() == 1 && look.glow() != null ? look.glow() : look.body();
            if (!put(sl, data, rec, m.set(f.x(), y - i, f.z()), b)) return Math.max(1, n);
            n++;
        }
        if (look.cluster()) {
            BlockState tip = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN);
            if (put(sl, data, rec, m.set(f.x(), y - len, f.z()), tip, QUIET)) n++;
        }
        return Math.max(1, n);
    }

    private static int hang(ServerLevel sl, Settlement s, Data data, Rec rec, Feat f) {
        if (!sl.hasChunk(f.x() >> 4, f.z() >> 4)) return RETRY;
        int y = under(sl, s, f.x(), f.z());
        if (y == Integer.MIN_VALUE) return 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bits = f.b(), len = 1 + (bits & 3), n = 0;
        switch (f.a()) {
            case GLOW_VINE -> {
                // cipó de frutas-brilhantes: a ponta já "adulta" (não cresce mais), uma ou outra fruta acesa
                for (int i = 0; i < len; i++) {
                    boolean berries = (bits >> 4 + i & 3) == 0;
                    BlockState st = i == len - 1
                            ? Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE)
                                    .setValue(BlockStateProperties.BERRIES, berries)
                            : Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, berries);
                    if (!put(sl, data, rec, m.set(f.x(), y - i, f.z()), st, QUIET)) break;
                    n++;
                }
            }
            case WEEPING -> {
                for (int i = 0; i < len; i++) {
                    BlockState st = i == len - 1
                            ? Blocks.WEEPING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE)
                            : Blocks.WEEPING_VINES_PLANT.defaultBlockState();
                    if (!put(sl, data, rec, m.set(f.x(), y - i, f.z()), st, QUIET)) break;
                    n++;
                }
            }
            case DRIP -> {
                // estalactite de 1 ou 2 peças
                boolean two = (bits & 1) == 1;
                BlockState tip = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                        .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
                if (two && put(sl, data, rec, m.set(f.x(), y, f.z()), tip.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.FRUSTUM), QUIET)) {
                    n++;
                    if (put(sl, data, rec, m.set(f.x(), y - 1, f.z()), tip, QUIET)) n++;
                } else if (!two && put(sl, data, rec, m.set(f.x(), y, f.z()), tip, QUIET)) {
                    n++;
                }
            }
            case SPORE -> {
                if (put(sl, data, rec, m.set(f.x(), y, f.z()), Blocks.SPORE_BLOSSOM)) n++;
            }
            default -> {
                if (put(sl, data, rec, m.set(f.x(), y, f.z()), Blocks.HANGING_ROOTS)) n++;
            }
        }
        return Math.max(1, n);
    }

    // =====================================================================================
    //  Cliente (névoa) e comandos
    // =====================================================================================
    /** A cada 5 s: a ilha (coração, raio da terra, estilo, cachoeiras) para quem está perto — a névoa é toda do cliente. */
    private static void broadcast(ServerLevel sl, Settlement s, Rec rec) {
        if (s.skyLand <= 0) return;
        int range = SkyCity.reach(s.skyLand) + 96;
        CompoundTag t = null;
        for (ServerPlayer sp : sl.players()) {
            if (Math.abs(sp.getX() - s.heart.getX()) > range || Math.abs(sp.getZ() - s.heart.getZ()) > range) continue;
            if (t == null) {
                t = new CompoundTag();
                t.putString("Dim", s.dim.location().toString());
                t.putInt("X", s.heart.getX());
                t.putInt("Y", s.heart.getY());
                t.putInt("Z", s.heart.getZ());
                t.putInt("R", s.skyLand);
                t.putString("Style", s.style.id);
                t.putIntArray("Falls", Data.flat(rec.falls));
            }
            PacketDistributor.sendToPlayer(sp, new SkyPackets.Near(t));
        }
    }

    /** {@code /mahou city sky decor}: estado dos enfeites; com {@code now}, constrói já o que falta (chunks carregados). */
    static Component debug(ServerLevel sl, Settlement s, boolean now) {
        if (!s.sky) return SettlementsLang.tr("cmd.sky.only");
        Data data = Data.get(sl.getServer());
        Rec rec = data.recs.get(s.id);
        if (rec == null || rec.half == 0) return SettlementsLang.tr("sky.decor.cmd.none");
        if (now) {
            // algumas passadas sem orçamento: o que estiver em chunk descarregado continua na fila
            for (int pass = 0; pass < 40 && !rec.pending.isEmpty(); pass++) step(sl, s, data, rec, 100000);
            rec.wait = 0;
        }
        return SettlementsLang.tr("sky.decor.cmd.state", rec.falls.size(), rec.pos.size(), rec.pending.size(), rec.gonePos.size());
    }

    /** {@code /mahou city sky decor redo}: tira os enfeites (o que é deste código) e refaz a lista do zero. */
    static Component redo(ServerLevel sl, Settlement s) {
        if (!s.sky) return SettlementsLang.tr("cmd.sky.only");
        Data data = Data.get(sl.getServer());
        Rec rec = data.recs.get(s.id);
        if (rec != null) {
            retire(rec);
            remove(sl, data, rec, Integer.MAX_VALUE);
            data.setDirty();
        }
        return SettlementsLang.tr("sky.decor.cmd.redo");
    }

    private SkyDecor() {}
}
