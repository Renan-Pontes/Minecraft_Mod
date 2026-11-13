package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.MilitiaNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Onda 7 (sky_access) — a <b>Ilha Celeste</b> vivível: subir, descer e ninguém se perder lá em cima. Tudo aqui só vale
 * para cidade do céu ({@code s.sky}); cidade no chão sai na primeira linha de cada método.
 * <ul>
 *   <li><b>Corrente Ascendente</b> da cidade (mesma sensação da das Ilhas Voadoras, {@code feature/towers/SkySites}):
 *       uma coluna 3×3 de ar logo além da ponta do cais. Entrou na coluna → sobe (Levitação) e é posto no cais;
 *       agachado → desce devagar (Queda Lenta). Obedece ao decreto dos portões: fechados (ou de noite, no decreto
 *       "à noite"), só quem é da cidade sobe — descer sempre pode.</li>
 *   <li><b>Cais Celeste</b>: deque pequeno na borda da ilha, do lado da frente da praça ({@link SkyCity#pierSide}),
 *       com os blocos do estilo da cidade. Só entra em ar; os blocos postos ficam anotados e saem limpos quando a
 *       ilha cresce (o cais vai para a borda nova) ou o estilo muda.</li>
 *   <li><b>Marco no chão</b>: um anel discreto de lajes debaixo da coluna (posto POR CIMA do terreno — nada é
 *       trocado nem cavado), com brilho de partículas só para quem está perto. Cais e marco são protegidos.</li>
 *   <li><b>Cair não mata</b>: quem despenca da ilha ganha Queda Lenta. Traço da cidade do céu: quem é da cidade não
 *       leva dano de queda no território da ilha (do topo até o chão embaixo dela).</li>
 *   <li><b>NPCs nunca se perdem</b> ({@link #rescue}, chamado no sinal de vida): quem caiu ou ficou fora da ilha volta
 *       para casa; NPC de cidade do céu não leva dano de queda. Pontos de chegada sempre em terra ({@link #onLand}).</li>
 *   <li><b>Invasões</b> ({@link #raidSpot}): a horda chega "pelo ar" — nasce em terra da ilha, na faixa fora da
 *       muralha, numa nuvem; atacante que cair volta para a borda.</li>
 * </ul>
 * Estado salvo em {@code Settlement.skyAccess} (opcional): {@code Shape}, {@code Rim}, {@code ColX}, {@code ColZ},
 * {@code Ground}, {@code Look}, {@code Pier}, {@code PierDone}, {@code Mark}, {@code MarkDone}, {@code Gone}.
 * <p>Custo: a manutenção roda a cada 2 s por cidade do céu e quase sempre só confere duas marcas; o tick do jogador é
 * uma comparação de caixa por cidade do céu (lista vazia = nada). Blocos só em chunk carregado.
 */
public final class SkyAccess {
    // ---- geometria (tudo contado a partir da borda da ilha, para fora, no lado do cais)
    /** Blocos de deque para fora da borda. */
    private static final int PIER_LEN = 6;
    /** Meia-largura do deque (5 de largura: 3 de passagem e o guarda-corpo de cada lado). */
    private static final int PIER_HALF = 2;
    /** O deque entra este tanto ilha adentro, para fechar os recortes da borda. */
    private static final int PIER_INSET = 6;
    /** Centro da coluna da corrente, além da borda (a coluna 3×3 começa logo depois da ponta do cais). */
    private static final int COLUMN_OUT = PIER_LEN + 2;
    /** Faixas vizinhas conferidas para achar a borda mais saliente (a barriga da ilha nunca entra na coluna). */
    private static final int LANES = 3;
    /** Sem altura do chão conhecida. */
    private static final int NO_GROUND = Integer.MIN_VALUE;

    /** Blocos do cais e do marco por estilo: deque, guarda-corpo, lanterna e a laje do anel no chão. */
    private record Look(BlockState deck, BlockState rail, BlockState lamp, BlockState ring) {}

    private static final Map<SettlementStyle, Look> LOOKS = new EnumMap<>(SettlementStyle.class);
    /** Todo bloco que o cais/marco de algum estilo usa: só estes são retirados na hora de desmontar. */
    private static final Set<Block> OWN = new HashSet<>();

    private static void look(SettlementStyle style, Block deck, Block rail, Block lamp, Block ring) {
        LOOKS.put(style, new Look(deck.defaultBlockState(), rail.defaultBlockState(), lamp.defaultBlockState(), ring.defaultBlockState()));
        OWN.addAll(List.of(deck, rail, lamp, ring));
    }

    static {
        look(SettlementStyle.KINGDOM, Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.LANTERN, Blocks.STONE_BRICK_SLAB);
        look(SettlementStyle.DESERT, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_WALL, Blocks.LANTERN, Blocks.SMOOTH_SANDSTONE_SLAB);
        look(SettlementStyle.NORDIC, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.LANTERN, Blocks.COBBLESTONE_SLAB);
        look(SettlementStyle.ORIENTAL, Blocks.MANGROVE_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.LANTERN, Blocks.POLISHED_ANDESITE_SLAB);
        look(SettlementStyle.ELVEN, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE, Blocks.LANTERN, Blocks.MOSSY_STONE_BRICK_SLAB);
        look(SettlementStyle.GOTHIC, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_WALL, Blocks.SOUL_LANTERN, Blocks.DEEPSLATE_BRICK_SLAB);
        look(SettlementStyle.DWARVEN, Blocks.WAXED_CUT_COPPER, Blocks.STONE_BRICK_WALL, Blocks.LANTERN, Blocks.DEEPSLATE_TILE_SLAB);
    }

    private static Look look(SettlementStyle style) {
        Look l = LOOKS.get(style);
        return l != null ? l : LOOKS.get(SettlementStyle.KINGDOM);
    }

    /** Onda 9 (isles): guarda-corpo e luz das pontes do arquipélago (os mesmos do cais, por estilo). */
    static BlockState bridgeRail(SettlementStyle style) {
        return look(style).rail();
    }

    static BlockState bridgeLamp(SettlementStyle style) {
        return look(style).lamp();
    }

    /** Bloco que o cais/marco/ponte de algum estilo usa? */
    static boolean ownBlock(BlockState st) {
        return OWN.contains(st.getBlock());
    }

    // =====================================================================================
    //  Lista das cidades do céu (só leitura no tick do jogador; trocada inteira)
    // =====================================================================================
    /**
     * Uma cidade do céu para o tick do jogador: caixa (coração ± {@code span}), topo da ilha, a coluna da corrente
     * (se já tem lugar), altura do chão debaixo dela (ou {@link #NO_GROUND}) e onde quem sobe é posto (null = a ilha
     * ainda não tem onde pousar).
     */
    private record Site(String city, ResourceKey<Level> dim, int hx, int hz, int topY, int span, boolean column, int colX, int colZ,
                        int groundY, @Nullable BlockPos land) {
        boolean near(double x, double z) {
            return Math.abs(x - hx) <= span && Math.abs(z - hz) <= span;
        }

        boolean inColumn(double x, double z) {
            return column && Math.abs(x - (colX + 0.5)) <= 1.5 && Math.abs(z - (colZ + 0.5)) <= 1.5;
        }

        /** Altura dos pés de quem chega ao fim da subida. */
        int feetY() {
            return topY + 1;
        }
    }

    private static volatile Site[] sites = new Site[0];

    /** Quem está subindo pela corrente: cidade, última altura e há quantos ticks não sobe (algo no caminho). */
    private static final class Ride {
        final String city;
        double lastY;
        int stuck;

        Ride(String city, double y) {
            this.city = city;
            this.lastY = y;
        }
    }

    private static final Map<UUID, Ride> RIDING = new HashMap<>();
    /** Quem despencou da ilha e está sendo amortecido até tocar o chão. */
    private static final Set<UUID> CUSHION = new HashSet<>();
    /** Último aviso curto por jogador (tempo do jogo): portões fechados, cais protegido. */
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkyAccess::onServerTick);
        NeoForge.EVENT_BUS.addListener(SkyAccess::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SkyAccess::onFall);
        NeoForge.EVENT_BUS.addListener(SkyAccess::onBreak);
        NeoForge.EVENT_BUS.addListener(SkyAccess::onExplosion);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> clear());
        // a ilha começou a crescer (o cais velho sai antes de a terra nova chegar nele) ou a terra ficou pronta (cais novo)
        SkyCity.onLand((sl, s, oldRadius, newRadius, finished) -> maintain(sl, s, false));
    }

    public static void forget(UUID player) {
        RIDING.remove(player);
        CUSHION.remove(player);
        WARNED.remove(player);
    }

    public static void clear() {
        sites = new Site[0];
        RIDING.clear();
        CUSHION.clear();
        WARNED.clear();
    }

    /** A cidade do céu desta posição: a dona da coluna em que se está, senão a de coração mais perto (ilhas grandes vizinhas se cruzam). */
    @Nullable
    private static Site find(Site[] list, ResourceKey<Level> dim, double x, double z) {
        Site best = null;
        double bd = Double.MAX_VALUE;
        for (Site s : list) {
            if (s.dim() != dim || !s.near(x, z)) continue;
            if (s.inColumn(x, z)) return s;
            double d = (x - s.hx()) * (x - s.hx()) + (z - s.hz()) * (z - s.hz());
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** Refaz a lista (a cada 10 s e sempre que o cais ou a coluna de alguma cidade muda). */
    public static void refreshSites(MinecraftServer server) {
        List<Site> out = new ArrayList<>();
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.sky) continue;
            int shape = SkyCity.shapeRadius(s);
            if (shape <= 0) continue;
            CompoundTag a = s.skyAccess;
            boolean col = a.contains("ColX");
            // onda 9 (isles): o arquipélago cabe no território dele (a coluna fica no meio, dentro da ilha do centro)
            int span = s.isles ? CityLayout.half(s) + 24 : SkyCity.reach(shape) + COLUMN_OUT + 8;
            out.add(new Site(s.id, s.dim, s.heart.getX(), s.heart.getZ(), SkyCity.topY(s), span, col,
                    a.getInt("ColX"), a.getInt("ColZ"), a.contains("Ground") ? a.getInt("Ground") : NO_GROUND, landing(s)));
        }
        sites = out.toArray(new Site[0]);
    }

    // =====================================================================================
    //  Consultas (para os outros sistemas da cidade)
    // =====================================================================================
    /** Centro da coluna da Corrente Ascendente, na altura do chão quando ela é conhecida (senão, na do cais); null = ainda sem coluna. */
    @Nullable
    public static BlockPos column(Settlement s) {
        CompoundTag a = s.skyAccess;
        if (!s.sky || !a.contains("ColX")) return null;
        return new BlockPos(a.getInt("ColX"), a.contains("Ground") ? a.getInt("Ground") : SkyCity.topY(s) + 1, a.getInt("ColZ"));
    }

    /** O Cais Celeste já está de pé? */
    public static boolean pierBuilt(Settlement s) {
        return s.sky && s.skyAccess.getBoolean("PierDone");
    }

    /**
     * A coluna (x, z) é do cais ou da corrente? Enfeites da ilha (cachoeiras, ilhotas, cipós) devem pular este trecho
     * para a subida ficar livre.
     */
    public static boolean reserved(Settlement s, int x, int z) {
        CompoundTag a = s.skyAccess;
        if (!s.sky || !a.contains("ColX")) return false;
        // onda 9 (isles): o poço no meio da ilha do centro e a volta dele
        if (s.isles) return Math.abs(x - a.getInt("ColX")) <= 4 && Math.abs(z - a.getInt("ColZ")) <= 4;
        Direction side = SkyCity.pierSide(s);
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        int along = dx * side.getStepX() + dz * side.getStepZ(), across = Math.abs(-dx * side.getStepZ() + dz * side.getStepX());
        int rim = a.getInt("Rim");
        return across <= PIER_HALF + 4 && along >= rim - PIER_INSET && along <= rim + COLUMN_OUT + 6;
    }

    /** Os portões da cidade estão fechados AGORA pelo decreto (fechados, ou "à noite" e é noite)? */
    private static boolean gatesClosed(ServerLevel sl, Settlement s) {
        return s.gates == Settlement.GATES_CLOSED || s.gates == Settlement.GATES_NIGHT && sl.isNight();
    }

    /** A corrente leva este jogador para cima? (descer sempre pode) */
    public static boolean mayRide(ServerLevel sl, Settlement s, ServerPlayer sp) {
        return sp.isCreative() || !gatesClosed(sl, s) || CityEconomy.isMember(sl.getServer(), s, sp);
    }

    /**
     * O ponto pedido, ou — numa cidade do céu, se ali ainda não há terra — o ponto com terra mais perto dele no caminho
     * até o coração. Usado pelos pontos de chegada de moradores, comerciantes e atacantes.
     */
    public static BlockPos onLand(Settlement s, BlockPos p) {
        if (!s.sky || walkable(s, p.getX(), p.getZ())) return p;
        int hx = s.heart.getX(), hz = s.heart.getZ();
        int dx = p.getX() - hx, dz = p.getZ() - hz;
        int n = Math.max(Math.abs(dx), Math.abs(dz));
        for (int i = n - 2; i > 3; i -= 2) {
            int x = hx + Math.round(dx * (float) i / n), z = hz + Math.round(dz * (float) i / n);
            if (walkable(s, x, z)) return new BlockPos(x, p.getY(), z);
        }
        return s.heart.offset(2, 0, 2);
    }

    /** Terra onde se chega andando (onda 9: no arquipélago as ilhotas de vigia das muralhas, soltas no céu, não contam). */
    private static boolean walkable(Settlement s, int x, int z) {
        return s.isles ? SkyIsles.hasTownLand(s, x, z) : SkyCity.hasLand(s, x, z);
    }

    // =====================================================================================
    //  Manutenção: lugar da coluna, cais, marco e o que ficou para desmontar
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        int t = server.getTickCount();
        if (t % 40 != 17) return;
        boolean verify = t % 1200 == 17, any = false;
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.sky) continue;
            any = true;
            ServerLevel sl = server.getLevel(s.dim);
            if (sl != null) maintain(sl, s, verify);
        }
        if (t % 200 == 17 || any != sites.length > 0) refreshSites(server);
    }

    /** Posição a {@code along} blocos do coração no lado do cais e {@code across} para o lado, na altura {@code y}. */
    private static BlockPos at(Settlement s, Direction side, int along, int across, int y) {
        return new BlockPos(s.heart.getX() + side.getStepX() * along - side.getStepZ() * across, y,
                s.heart.getZ() + side.getStepZ() * along + side.getStepX() * across);
    }

    /** Distância do coração até a borda mais saliente da ilha nas faixas do cais (conta pura, pela forma do raio). */
    private static int rimDistance(Settlement s, int shape) {
        Direction side = SkyCity.pierSide(s);
        int best = 0;
        for (int w = -LANES; w <= LANES; w++) {
            for (int d = SkyCity.reach(shape); d > best; d--) {
                if (SkyCity.inside(s.heart, shape, side.getStepX() * d - side.getStepZ() * w, side.getStepZ() * d + side.getStepX() * w)) {
                    best = d;
                    break;
                }
            }
        }
        return best;
    }

    /** Onde quem sobe é posto (pés): a ponta do cais; sem cais, a borda de terra que já existe; null = sem terra. */
    @Nullable
    private static BlockPos landing(Settlement s) {
        // onda 9 (isles): ao lado do poço da corrente, no meio da ilha do centro
        if (s.isles) return SkyIsles.landing(s);
        CompoundTag a = s.skyAccess;
        if (a.getBoolean("PierDone")) return at(s, SkyCity.pierSide(s), a.getInt("Rim") + PIER_LEN - 1, 0, SkyCity.topY(s) + 1);
        return SkyCity.pierPoint(s);
    }

    /**
     * Mantém o acesso de uma cidade do céu. Quase sempre só confere marcas: recalcula o lugar da coluna quando a forma
     * da ilha muda, desmonta o que ficou para trás, põe o marco e — com a terra completa — o cais. {@code verify} =
     * a conferência de minuto em minuto (repõe bloco que sumiu).
     */
    static void maintain(ServerLevel sl, Settlement s, boolean verify) {
        if (!s.sky) return;
        int shape = SkyCity.shapeRadius(s);
        if (shape <= 0) return;
        CompoundTag a = s.skyAccess;
        boolean changed = false;
        if (s.isles) {
            // onda 9 (isles): a coluna é o poço no meio da ilha do centro — o lugar nunca muda (Shape -1 = arquipélago)
            BlockPos c = SkyIsles.shaft(s);
            if (!a.contains("ColX") || a.getInt("Shape") != -1 || a.getInt("ColX") != c.getX() || a.getInt("ColZ") != c.getZ()) {
                retire(a, true);
                a.putInt("Shape", -1);
                a.putInt("Rim", 0);
                a.putInt("ColX", c.getX());
                a.putInt("ColZ", c.getZ());
                a.remove("Ground");
                changed = true;
            }
        } else if (!a.contains("ColX") || a.getInt("Shape") != shape) {
            int rim = rimDistance(s, shape);
            if (!a.contains("ColX") || a.getInt("Rim") != rim) {
                // a borda mudou de lugar (ilha nova ou crescendo): cais e marco velhos saem; a coluna já vale na borda nova
                retire(a, true);
                Direction side = SkyCity.pierSide(s);
                a.putInt("Rim", rim);
                a.putInt("ColX", s.heart.getX() + side.getStepX() * (rim + COLUMN_OUT));
                a.putInt("ColZ", s.heart.getZ() + side.getStepZ() * (rim + COLUMN_OUT));
                a.remove("Ground");
            }
            a.putInt("Shape", shape);
            changed = true;
        }
        if (!s.style.id.equals(a.getString("Look"))) {
            // a cidade trocou de estilo: cais e marco são refeitos com os blocos novos
            if (a.contains("Look")) retire(a, true);
            a.putString("Look", s.style.id);
            changed = true;
        }
        if (a.contains("Gone")) changed |= sweep(sl, s, a);
        if (!a.getBoolean("MarkDone") || verify) changed |= marker(sl, s, a);
        if (s.isles) {
            // arquipélago: em vez do cais, o guarda-corpo em volta do poço (com a boca do lado da rua)
            if (SkyIsles.coreDone(s) && (!a.getBoolean("PierDone") || verify)) changed |= shaftRing(sl, s, a);
        } else if (!SkyCity.generating(s) && (!a.getBoolean("PierDone") || verify)) {
            changed |= pier(sl, s, a);
        }
        if (changed) {
            SettlementData.get(sl.getServer()).setDirty();
            refreshSites(sl.getServer());
        }
    }

    /** Passa os blocos anotados do cais (e do marco) para a lista "a desmontar". */
    private static void retire(CompoundTag a, boolean pierToo) {
        LongArrayList gone = new LongArrayList(a.getLongArray("Gone"));
        gone.addElements(gone.size(), a.getLongArray("Mark"));
        a.remove("Mark");
        a.remove("MarkDone");
        if (pierToo) {
            gone.addElements(gone.size(), a.getLongArray("Pier"));
            a.remove("Pier");
            a.remove("PierDone");
        }
        if (gone.isEmpty()) a.remove("Gone");
        else a.putLongArray("Gone", gone.toLongArray());
    }

    /** Desmonta o que está em chunk carregado; o resto espera. */
    private static boolean sweep(ServerLevel sl, Settlement s, CompoundTag a) {
        long[] gone = a.getLongArray("Gone");
        LongArrayList keep = new LongArrayList();
        for (long l : gone) {
            BlockPos p = BlockPos.of(l);
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) keep.add(l);
            else removeOwn(sl, s, p);
        }
        if (keep.isEmpty()) a.remove("Gone");
        else a.putLongArray("Gone", keep.toLongArray());
        return keep.size() != gone.length;
    }

    /**
     * Tira um bloco do cais/marco — só se ainda for um dos nossos (o que o jogador pôs no lugar fica). Se a ilha
     * cresceu em volta do deque velho, o buraco no chão é fechado com o mesmo bloco do topo vizinho.
     */
    private static void removeOwn(ServerLevel sl, Settlement s, BlockPos p) {
        BlockState cur = sl.getBlockState(p);
        if (!OWN.contains(cur.getBlock())) return;
        BlockState fill = Blocks.AIR.defaultBlockState();
        if (p.getY() == SkyCity.topY(s) && SkyCity.hasLand(s, p.getX(), p.getZ()) && sl.getBlockState(p.below()).isSolid()) {
            fill = Blocks.DIRT.defaultBlockState();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                BlockState st = sl.getBlockState(n);
                if (!OWN.contains(st.getBlock()) && st.isSolid() && st.isCollisionShapeFullBlock(sl, n)) {
                    fill = st.getBlock().defaultBlockState();
                    break;
                }
            }
        }
        sl.setBlock(p, fill, 3);
    }

    /** Põe (ou reconhece) um bloco nosso: só em ar/mato solto. Devolve true se o bloco está lá. */
    private static boolean put(ServerLevel sl, BlockPos p, BlockState state, LongArrayList placed) {
        BlockState cur = sl.getBlockState(p);
        if (cur.is(state.getBlock())) {
            placed.add(p.asLong());
            return true;
        }
        // construção de alguém no caminho: contorna
        if (!(cur.isAir() || cur.canBeReplaced())) return false;
        sl.setBlock(p, Block.updateFromNeighbourShapes(state, sl, p), 3);
        placed.add(p.asLong());
        return true;
    }

    /** Ergue (ou confere) o Cais Celeste na borda. Só com os quatro cantos carregados. */
    private static boolean pier(ServerLevel sl, Settlement s, CompoundTag a) {
        Direction side = SkyCity.pierSide(s);
        int rim = a.getInt("Rim"), top = SkyCity.topY(s);
        for (int along : new int[] {rim - PIER_INSET, rim + PIER_LEN}) {
            for (int w = -PIER_HALF; w <= PIER_HALF; w += 2 * PIER_HALF) {
                BlockPos c = at(s, side, along, w, top);
                if (!sl.hasChunk(c.getX() >> 4, c.getZ() >> 4)) return false;
            }
        }
        Look look = look(s.style);
        LongArrayList placed = new LongArrayList();
        int end = rim + PIER_LEN;
        for (int along = rim - PIER_INSET; along <= end; along++) {
            for (int w = -PIER_HALF; w <= PIER_HALF; w++) {
                boolean deck = put(sl, at(s, side, along, w, top), look.deck(), placed);
                // guarda-corpo dos dois lados (a ponta fica aberta: é por ali que se entra na corrente)
                if (along > rim && Math.abs(w) == PIER_HALF && (deck || sl.getBlockState(at(s, side, along, w, top)).isSolid())) {
                    boolean rail = put(sl, at(s, side, along, w, top + 1), look.rail(), placed);
                    if (rail && (along == end || along == rim + 1)) put(sl, at(s, side, along, w, top + 2), look.lamp(), placed);
                }
            }
        }
        // estacas penduradas debaixo da ponta (só enfeite: dá cara de cais)
        for (int w = -PIER_HALF; w <= PIER_HALF; w += 2 * PIER_HALF) {
            if (put(sl, at(s, side, end, w, top - 1), look.rail(), placed)) put(sl, at(s, side, end, w, top - 2), look.rail(), placed);
        }
        long[] now = placed.toLongArray();
        boolean first = !a.getBoolean("PierDone");
        boolean changed = first || !Arrays.equals(now, a.getLongArray("Pier"));
        a.putLongArray("Pier", now);
        a.putBoolean("PierDone", true);
        if (first) announce(sl, s, a);
        return changed;
    }

    /**
     * Onda 9 (isles): o guarda-corpo do poço da Corrente Ascendente no meio da ilha do centro — a volta 5×5 do buraco,
     * menos a boca (do lado da rua principal, onde quem sobe é posto), com luz nos quatro cantos. Só em ar; os blocos
     * ficam anotados como os do cais (saem limpos se o estilo mudar).
     */
    private static boolean shaftRing(ServerLevel sl, Settlement s, CompoundTag a) {
        int top = SkyCity.topY(s), hx = s.heart.getX(), hz = s.heart.getZ();
        BlockPos c = SkyIsles.shaft(s);
        for (int ox = -3; ox <= 3; ox += 6) {
            for (int oz = -3; oz <= 3; oz += 6) {
                if (!sl.hasChunk(c.getX() + ox >> 4, c.getZ() + oz >> 4)) return false;
            }
        }
        Look look = look(s.style);
        LongArrayList placed = new LongArrayList();
        for (int[] r : SkyIsles.shaftRing(s)) {
            BlockPos floor = new BlockPos(hx + r[0], top, hz + r[1]);
            if (!sl.getBlockState(floor).isSolid()) continue;
            if (put(sl, floor.above(), look.rail(), placed) && r[2] == 1) put(sl, floor.above(2), look.lamp(), placed);
        }
        long[] now = placed.toLongArray();
        boolean first = !a.getBoolean("PierDone");
        boolean changed = first || !Arrays.equals(now, a.getLongArray("Pier"));
        a.putLongArray("Pier", now);
        a.putBoolean("PierDone", true);
        if (first) announce(sl, s, a);
        return changed;
    }

    /** O cais ficou pronto: uma linha no Diário e um aviso curto a quem governa, com o lugar do marco no chão. */
    private static void announce(ServerLevel sl, Settlement s, CompoundTag a) {
        MinecraftServer server = sl.getServer();
        BlockPos land = s.isles ? SkyIsles.landing(s) : null;
        BlockPos end = land != null ? land : at(s, SkyCity.pierSide(s), a.getInt("Rim") + PIER_LEN, 0, SkyCity.topY(s) + 1);
        sl.playSound(null, end, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.2F);
        sl.sendParticles(ParticleTypes.CLOUD, end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5, 18, 1.2, 0.3, 1.2, 0.02);
        CityPace.diary(server, s, SettlementsLang.tr(s.isles ? "isle.diary.shaft" : "sky.diary.pier", a.getInt("ColX"), a.getInt("ColZ")));
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            g.displayClientMessage(SettlementsLang.tr(s.isles ? "isle.shaft.built" : "sky.pier.built", a.getInt("ColX"), a.getInt("ColZ")).withColor(s.style.color), true);
        }
    }

    /**
     * Altura dos pés de quem está no chão da coluna (x, z), olhando só abaixo da ilha; {@link #NO_GROUND} = nada.
     * Folha de árvore não conta como chão.
     */
    private static int ground(ServerLevel sl, int x, int z, int islandTop) {
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int limit = islandTop - SkyCity.MAX_THICK - 4;
        if (y > limit) {
            // algo alto nesta coluna (a ilha, uma construção): procura o chão de verdade por baixo
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (y = limit; y > sl.getMinBuildHeight(); y--) {
                BlockState st = sl.getBlockState(m.set(x, y - 1, z));
                if (st.blocksMotion() || !st.getFluidState().isEmpty()) break;
            }
        }
        // tronco de árvore bem na coluna: o chão é o pé dela, não a ponta do tronco
        BlockPos.MutableBlockPos t = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 40 && y > sl.getMinBuildHeight() && sl.getBlockState(t.set(x, y - 1, z)).is(net.minecraft.tags.BlockTags.LOGS); i++) y--;
        return y <= sl.getMinBuildHeight() ? NO_GROUND : y;
    }

    /**
     * Põe o marco no chão debaixo da coluna: um anel de 12 lajes, cada uma POR CIMA do terreno (nada é trocado). Em
     * água, em terreno muito torto ou dentro de outra cidade o anel fica incompleto ou não existe — o brilho da
     * coluna continua mostrando o lugar. Depois de pronto, a conferência só repõe laje que sumiu.
     */
    private static boolean marker(ServerLevel sl, Settlement s, CompoundTag a) {
        int cx = a.getInt("ColX"), cz = a.getInt("ColZ");
        for (int ox = -2; ox <= 2; ox += 4) {
            for (int oz = -2; oz <= 2; oz += 4) {
                if (!sl.hasChunk(cx + ox >> 4, cz + oz >> 4)) return false;
            }
        }
        Look look = look(s.style);
        if (a.getBoolean("MarkDone")) {
            boolean fixed = false;
            for (long l : a.getLongArray("Mark")) {
                BlockPos p = BlockPos.of(l);
                BlockState cur = sl.getBlockState(p);
                if ((cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty()) && sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) {
                    sl.setBlock(p, look.ring(), 3);
                    fixed = true;
                }
            }
            return fixed;
        }
        int top = SkyCity.topY(s);
        int gy = ground(sl, cx, cz, top);
        if (gy == NO_GROUND) return false;
        LongArrayList placed = new LongArrayList();
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                int d = ox * ox + oz * oz;
                if (d != 4 && d != 5) continue;
                int y = ground(sl, cx + ox, cz + oz, top);
                if (y == NO_GROUND || Math.abs(y - gy) > 1) continue;
                BlockPos p = new BlockPos(cx + ox, y, cz + oz);
                BlockState cur = sl.getBlockState(p);
                if (!(cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty())) continue;
                if (!sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) continue;
                // cidades são sagradas: o marco não entra no território de outro lugar
                Places.Place here = Places.at(sl, p);
                if (here != null && !here.id().equals(s.id)) continue;
                sl.setBlock(p, look.ring(), 3);
                placed.add(p.asLong());
            }
        }
        a.putInt("Ground", gy);
        a.putLongArray("Mark", placed.toLongArray());
        a.putBoolean("MarkDone", true);
        return true;
    }

    /** A cidade acabou (dissolvida): a ilha e o cais ficam como ruína; o marco no chão sai, porque a corrente acabou. */
    static void dissolved(ServerLevel sl, Settlement s) {
        if (!s.sky) return;
        retire(s.skyAccess, false);
        if (s.skyAccess.contains("Gone")) sweep(sl, s, s.skyAccess);
        // a cidade ainda está nos dados neste instante: a lista é refeita no próximo ciclo (até lá a coluna não acha a cidade)
    }

    // =====================================================================================
    //  Jogador: corrente, queda amortecida, brilho da coluna
    // =====================================================================================
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        Site[] list = sites;
        if (list.length == 0 || !(e.getEntity() instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        Site site = sp.isSpectator() ? null : find(list, sp.level().dimension(), sp.getX(), sp.getZ());
        if (site == null) {
            if (!RIDING.isEmpty() && RIDING.remove(id) != null) sp.removeEffect(MobEffects.LEVITATION);
            if (!CUSHION.isEmpty()) CUSHION.remove(id);
            return;
        }
        ServerLevel sl = sp.serverLevel();
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        boolean column = site.inColumn(x, z);

        // Chegada: todo tick para quem está subindo (a 10 blocos/s não dá para esperar o próximo ciclo)
        Ride ride = RIDING.get(id);
        if (ride != null) {
            if (!column || sp.isShiftKeyDown() || !ride.city.equals(site.city())) {
                RIDING.remove(id);
                sp.removeEffect(MobEffects.LEVITATION);
                // saiu da coluna no meio da subida: a corrente não larga ninguém para morrer na queda
                if (!column) CUSHION.add(id);
            } else if (y >= site.feetY() - 0.2) {
                RIDING.remove(id);
                land(sl, sp, site);
                return;
            } else if (y > ride.lastY + 0.05) {
                ride.lastY = y;
                ride.stuck = 0;
            } else if (++ride.stuck > 30) {
                // algo no caminho (enfeite, construção): acima do chão a corrente entrega no cais; numa caverna, solta
                RIDING.remove(id);
                if (site.groundY() != NO_GROUND && y >= site.groundY() - 1) {
                    land(sl, sp, site);
                    return;
                }
                sp.removeEffect(MobEffects.LEVITATION);
                WARNED.put(id, sl.getGameTime() + 100);
            }
        }
        if (sp.tickCount % 5 != 0) return;

        boolean free = !sp.getAbilities().flying && !sp.isPassenger() && !sp.isFallFlying();
        boolean above = site.groundY() == NO_GROUND || y > site.groundY() - 10;
        if (column && y < site.feetY() - 0.2 && above) {
            CUSHION.remove(id);
            if (sp.isShiftKeyDown()) {
                // agachado: desce devagar pela mesma coluna
                if (free) sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, true, false));
            } else if (free && !lift(sl, sp, site, id) && sp.fallDistance > 3F) {
                // a corrente não leva este jogador (portões fechados, ilha sem pouso), mas também não o deixa despencar
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, true, false));
            }
        } else if (free && !sp.onGround() && !sp.isInWater()) {
            boolean falling = CUSHION.contains(id);
            // despencou da ilha (a queda começou lá em cima): a magia dela amortece até o chão
            if (!falling && y < site.topY() - 2 && sp.fallDistance > 6F && y + sp.fallDistance >= site.topY() - 8) {
                CUSHION.add(id);
                falling = true;
                sp.displayClientMessage(SettlementsLang.tr("sky.cushion").withColor(0xBFE8FF), true);
                Hints.show(sp, "city_sky_fall");
            }
            if (falling && !sp.hasEffect(MobEffects.SLOW_FALLING)) sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, true, false));
        } else if (!CUSHION.isEmpty()) {
            CUSHION.remove(id);
        }

        if (sp.tickCount % 10 == 0 && site.column() && y < site.feetY() + 4) beacon(sl, sp, site);
    }

    /** Um passo da subida: confere os portões e se a ilha tem onde pousar, e empurra para cima. Falso = não levou. */
    private static boolean lift(ServerLevel sl, ServerPlayer sp, Site site, UUID id) {
        long now = sl.getGameTime();
        Settlement s = SettlementService.city(sl.getServer(), site.city());
        if (s == null) return false;
        if (site.land() == null || !mayRide(sl, s, sp)) {
            Long last = WARNED.get(id);
            if (last == null || now - last > 60) {
                WARNED.put(id, now);
                Component name = SettlementService.cityName(sl.getServer(), s);
                sp.displayClientMessage(SettlementsLang.tr(site.land() == null ? "sky.updraft.no_land" : "sky.updraft.closed", name)
                        .withColor(0xE8A440), true);
            }
            return false;
        }
        // acabou de ser solto por algo no caminho: espera um pouco antes de tentar de novo
        Long hold = WARNED.get(id);
        if (hold != null && hold > now) return false;
        sp.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 12, 9, true, false));
        sp.resetFallDistance();
        if (!RIDING.containsKey(id)) {
            RIDING.put(id, new Ride(site.city(), sp.getY()));
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 1.6F);
            Hints.show(sp, "city_sky_updraft");
        }
        return true;
    }

    /** Fim da subida: pousa na ponta do cais (ou na borda de terra, enquanto o cais não existe). */
    private static void land(ServerLevel sl, ServerPlayer sp, Site site) {
        sp.removeEffect(MobEffects.LEVITATION);
        BlockPos to = site.land();
        if (to == null) return;
        // alguém construiu em cima do ponto de pouso: sobe até caber
        BlockPos.MutableBlockPos m = to.mutable();
        for (int i = 0; i < 5; i++) {
            if (sl.getBlockState(m).getCollisionShape(sl, m).isEmpty() && sl.getBlockState(m.above()).getCollisionShape(sl, m.above()).isEmpty()) break;
            m.move(Direction.UP);
        }
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
        sp.teleportTo(sl, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        sl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.2, sp.getZ(), 14, 0.4, 0.1, 0.4, 0.02);
        sl.playSound(null, sp.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** Brilho da coluna, só para este jogador e só de perto: faíscas no trecho perto da altura dele e névoa no marco. */
    private static void beacon(ServerLevel sl, ServerPlayer sp, Site site) {
        double cx = site.colX() + 0.5, cz = site.colZ() + 0.5;
        double dx = sp.getX() - cx, dz = sp.getZ() - cz;
        if (dx * dx + dz * dz > 56 * 56) return;
        int ground = site.groundY() == NO_GROUND ? sl.getMinBuildHeight() : site.groundY();
        double lo = Math.max(ground, sp.getY() - 24), hi = Math.min(site.feetY(), sp.getY() + 40);
        if (hi <= lo) return;
        // um pacote só: o espalhamento vertical estica as faíscas pelo trecho da coluna perto do jogador
        sl.sendParticles(sp, ParticleTypes.END_ROD, true, cx, (lo + hi) * 0.5, cz, 8, 0.5, (hi - lo) * 0.28, 0.5, 0.01);
        if (site.groundY() != NO_GROUND && sp.getY() < ground + 12) {
            sl.sendParticles(sp, ParticleTypes.CLOUD, true, cx, ground + 0.5, cz, 2, 0.6, 0.2, 0.6, 0.02);
        }
    }

    /**
     * Traço da cidade do céu: quem é da cidade não leva dano de queda no território da ilha (do topo ao chão embaixo
     * dela). E NPC de cidade do céu nunca morre de queda — quem cai é devolvido para casa no sinal de vida.
     */
    private static void onFall(LivingFallEvent e) {
        Site[] list = sites;
        if (list.length == 0) return;
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide()) return;
        if (le instanceof SettlementNpc npc) {
            Settlement s = npc.settlement();
            if (s != null && s.sky) e.setDamageMultiplier(0);
            return;
        }
        if (!(le instanceof ServerPlayer sp) || e.getDistance() <= 3F) return;
        Site site = find(list, sp.level().dimension(), sp.getX(), sp.getZ());
        Settlement s = site == null ? null : SettlementService.city(sp.server, site.city());
        if (s != null && CityEconomy.isMember(sp.server, s, sp)) e.setDamageMultiplier(0);
    }

    // =====================================================================================
    //  Proteção do cais e do marco
    // =====================================================================================
    /** A posição é um bloco anotado do cais ou do marco de alguma cidade do céu? */
    private static boolean guarded(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        long key = pos.asLong();
        for (Site site : sites) {
            // cais e marco ficam a poucos blocos da coluna: longe dela nem olha as listas
            if (site.dim() != dim || !site.column() || Math.abs(pos.getX() - site.colX()) > 24 || Math.abs(pos.getZ() - site.colZ()) > 24) continue;
            Settlement s = SettlementService.city(server, site.city());
            if (s == null) continue;
            for (long l : s.skyAccess.getLongArray("Pier")) if (l == key) return true;
            for (long l : s.skyAccess.getLongArray("Mark")) if (l == key) return true;
        }
        return false;
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (sites.length == 0 || !(e.getLevel() instanceof ServerLevel sl) || e.getPlayer().isCreative()) return;
        if (!OWN.contains(e.getState().getBlock()) || !guarded(sl.getServer(), sl.dimension(), e.getPos())) return;
        e.setCanceled(true);
        long now = sl.getGameTime();
        Long last = WARNED.get(e.getPlayer().getUUID());
        if (last == null || now - last > 40) {
            WARNED.put(e.getPlayer().getUUID(), now);
            e.getPlayer().displayClientMessage(SettlementsLang.tr("sky.pier.protected").withColor(0xBFE8FF), true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (sites.length == 0 || !(e.getLevel() instanceof ServerLevel sl)) return;
        Vec3 c = e.getExplosion().center();
        for (Site site : sites) {
            if (site.dim() != sl.dimension() || !site.column() || Math.abs(c.x - site.colX()) > 40 || Math.abs(c.z - site.colZ()) > 40) continue;
            e.getAffectedBlocks().removeIf(p -> guarded(sl.getServer(), sl.dimension(), p));
            return;
        }
    }

    // =====================================================================================
    //  NPCs: ninguém se perde
    // =====================================================================================
    /**
     * Sinal de vida de um NPC de cidade do céu: quem caiu (está abaixo da ilha) ou ficou fora dela sem ter como
     * voltar (caindo, ou em pé longe da borda — ilhota, pedaço solto) é devolvido para casa. Miliciano seguindo o
     * Senhor fica com ele, onde for.
     */
    public static void rescue(ServerLevel sl, Settlement s, SettlementNpc npc) {
        if (!s.sky || !npc.isAlive() || npc.isPassenger()) return;
        int top = SkyCity.topY(s);
        int x = npc.getBlockX(), z = npc.getBlockZ();
        boolean below = npc.getY() < top - SkyCity.MAX_THICK - 2;
        if (!below) {
            if (SkyCity.hasLand(s, x, z)) return;
            // onda 9 (isles): em pé numa ponte entre as ilhas (na altura do topo) também está tudo bem
            if (s.isles && npc.onGround() && npc.getY() >= top - 1) return;
            // fora da terra, mas em pé perto da borda (cais, ponte que o jogador fez): está tudo bem
            BlockPos in = toward(s, x, z, 8);
            if (npc.onGround() && SkyCity.hasLand(s, in.getX(), in.getZ())) return;
        }
        if (npc instanceof MilitiaNpc && s.militiaOrder == Settlement.ORDER_FOLLOW && s.owner != null) {
            ServerPlayer lord = sl.getServer().getPlayerList().getPlayer(s.owner);
            if (lord != null && lord.level() == sl && lord.isAlive() && !lord.isSpectator()) return;
        }
        BlockPos to = home(sl, s, npc);
        if (to == null) return;
        sl.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.8, npc.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
        npc.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        npc.resetFallDistance();
        sl.sendParticles(ParticleTypes.CLOUD, to.getX() + 0.5, to.getY() + 0.8, to.getZ() + 0.5, 10, 0.3, 0.5, 0.3, 0.02);
    }

    /** O ponto (x, z) andado {@code steps} blocos na direção do coração. */
    private static BlockPos toward(Settlement s, int x, int z, int steps) {
        int dx = s.heart.getX() - x, dz = s.heart.getZ() - z;
        int n = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        int k = Math.min(steps, n);
        return new BlockPos(x + Math.round(dx * (float) k / n), s.heart.getY(), z + Math.round(dz * (float) k / n));
    }

    /** Onde devolver um NPC: a casa dele, a praça ou — se estão longe demais para estarem carregadas — a terra mais perto. */
    @Nullable
    private static BlockPos home(ServerLevel sl, Settlement s, SettlementNpc npc) {
        BlockPos h = npc.home();
        if (h != null && (h.getX() != s.heart.getX() || h.getZ() != s.heart.getZ())) {
            BlockPos spot = stand(sl, s, h.getX(), h.getZ(), 2);
            if (spot != null) return spot;
        }
        for (int[] o : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            BlockPos spot = stand(sl, s, s.heart.getX() + o[0], s.heart.getZ() + o[1], 2);
            if (spot != null) return spot;
        }
        BlockPos land = onLand(s, new BlockPos(npc.getBlockX(), s.heart.getY(), npc.getBlockZ()));
        BlockPos in = toward(s, land.getX(), land.getZ(), 5);
        return stand(sl, s, in.getX(), in.getZ(), 10);
    }

    /**
     * Lugar para ficar em pé perto da coluna (x, z) da ilha (ela e as vizinhas, até 2 blocos): piso firme com dois
     * blocos livres, no máximo {@code up} blocos acima do topo da ilha — com {@code up} pequeno o NPC volta para o
     * chão da casa ou da rua, nunca para cima de um telhado.
     */
    @Nullable
    private static BlockPos stand(ServerLevel sl, Settlement s, int x, int z, int up) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int top = SkyCity.topY(s);
        for (int r = 0; r <= 2; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int px = x + ox, pz = z + oz;
                    if (!SkyCity.hasLand(s, px, pz) || !sl.hasChunk(px >> 4, pz >> 4)) continue;
                    for (int y = top + 1; y <= top + up; y++) {
                        m.set(px, y - 1, pz);
                        if (!sl.getBlockState(m).isFaceSturdy(sl, m, Direction.UP)) continue;
                        m.set(px, y, pz);
                        if (!sl.getBlockState(m).getCollisionShape(sl, m).isEmpty()) continue;
                        m.set(px, y + 1, pz);
                        if (sl.getBlockState(m).getCollisionShape(sl, m).isEmpty()) return new BlockPos(px, y, pz);
                    }
                }
            }
        }
        return null;
    }

    // =====================================================================================
    //  Invasões: a horda chega pelo ar
    // =====================================================================================
    /**
     * Onde um atacante nasce numa cidade do céu: em terra da ilha, o mais perto possível do ponto pedido (a faixa fora
     * da muralha). null = sem terra carregada ali (o chamador pula este atacante). Cidade no chão: nunca é chamada.
     */
    @Nullable
    public static BlockPos raidSpot(ServerLevel sl, Settlement s, int x, int z) {
        BlockPos p = onLand(s, new BlockPos(x, s.heart.getY(), z));
        if (!walkable(s, p.getX(), p.getZ()) || !sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        // buraco cavado até o vazio bem neste ponto: nada de nascer no chão lá embaixo
        if (y < SkyCity.topY(s) - SkyCity.MAX_THICK) return null;
        return new BlockPos(p.getX(), y, p.getZ());
    }

    /** A chegada "pelo ar": uma nuvem curta onde o atacante aparece. */
    public static void raidFx(ServerLevel sl, double x, double y, double z) {
        sl.sendParticles(ParticleTypes.CLOUD, x, y + 1.0, z, 14, 0.5, 0.7, 0.5, 0.03);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 2.2, z, 5, 0.3, 0.8, 0.3, 0.01);
    }

    /** Atacante de invasão que caiu da ilha: volta para a borda, pelo ar. Devolve true se foi trazido de volta. */
    public static boolean raidRecover(ServerLevel sl, Settlement s, Mob m) {
        if (!s.sky || m.getY() >= SkyCity.topY(s) - SkyCity.MAX_THICK - 2) return false;
        int d = CityLayout.half(s) + 8;
        int x = Math.max(s.heart.getX() - d, Math.min(s.heart.getX() + d, m.getBlockX()));
        int z = Math.max(s.heart.getZ() - d, Math.min(s.heart.getZ() + d, m.getBlockZ()));
        BlockPos spot = raidSpot(sl, s, x, z);
        if (spot == null) return false;
        m.getNavigation().stop();
        m.setDeltaMovement(Vec3.ZERO);
        m.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        m.resetFallDistance();
        raidFx(sl, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        return true;
    }

    // =====================================================================================
    //  Tela e comandos de teste
    // =====================================================================================
    /** Para o cliente (dentro do NBT da cidade): onde a corrente toca o chão e se o cais já existe. */
    static void view(CompoundTag t, Settlement s) {
        CompoundTag a = s.skyAccess;
        if (!s.sky || !a.contains("ColX")) return;
        t.putInt("SkyColX", a.getInt("ColX"));
        t.putInt("SkyColZ", a.getInt("ColZ"));
        if (a.contains("Ground")) t.putInt("SkyColY", a.getInt("Ground"));
        t.putBoolean("SkyPier", a.getBoolean("PierDone"));
    }

    /**
     * Comandos de teste ({@code /mahou city sky pier|down|drop}): 0 = refaz cais e marco agora e mostra o estado;
     * 1 = leva o jogador ao marco no chão; 2 = derruba da ilha o NPC da cidade mais perto (ele volta no sinal de vida).
     */
    static int debug(CommandSourceStack src, ServerLevel sl, Settlement s, int mode) {
        MinecraftServer server = sl.getServer();
        CompoundTag a = s.skyAccess;
        ServerPlayer sp = src.getPlayer();
        switch (mode) {
            case 1 -> {
                if (sp == null || !a.contains("ColX")) {
                    src.sendFailure(SettlementsLang.tr("cmd.sky.pier.none"));
                    return 0;
                }
                int cx = a.getInt("ColX"), cz = a.getInt("ColZ");
                // comando de teste: carrega o chunk do marco para poder medir o chão e pôr o anel
                sl.getChunk(cx >> 4, cz >> 4);
                maintain(sl, s, false);
                int gy = a.contains("Ground") ? a.getInt("Ground") : ground(sl, cx, cz, SkyCity.topY(s));
                if (gy == NO_GROUND) {
                    src.sendFailure(SettlementsLang.tr("cmd.sky.pier.none"));
                    return 0;
                }
                sp.stopRiding();
                sp.teleportTo(sl, cx + 4.5, gy + 1, cz + 0.5, sp.getYRot(), sp.getXRot());
                sp.resetFallDistance();
            }
            case 2 -> {
                if (sp == null) return 0;
                SettlementNpc best = null;
                double bd = 48 * 48;
                for (SettlementNpc n : sl.getEntitiesOfClass(SettlementNpc.class, sp.getBoundingBox().inflate(48, 24, 48),
                        n -> n.isAlive() && s.id.equals(n.cityId()))) {
                    double d = n.distanceToSqr(sp);
                    if (d < bd) {
                        bd = d;
                        best = n;
                    }
                }
                if (best == null) {
                    src.sendFailure(SettlementsLang.tr("cmd.sky.drop.none"));
                    return 0;
                }
                best.teleportTo(best.getX(), SkyCity.topY(s) - SkyCity.MAX_THICK - 20, best.getZ());
                Component who = best.getDisplayName();
                src.sendSuccess(() -> SettlementsLang.tr("cmd.sky.drop", who), false);
                return 1;
            }
            default -> {
                retire(a, true);
                a.remove("Ground");
                maintain(sl, s, true);
                SettlementData.get(server).setDirty();
                refreshSites(server);
            }
        }
        Component name = SettlementService.cityName(server, s);
        Component line = !a.contains("ColX") ? SettlementsLang.tr("cmd.sky.pier.none")
                : SettlementsLang.tr("cmd.sky.pier", name, a.getInt("ColX"), a.getInt("ColZ"),
                        a.contains("Ground") ? Integer.toString(a.getInt("Ground")) : "?", a.getLongArray("Pier").length,
                        a.getLongArray("Mark").length, a.getLongArray("Gone").length,
                        SettlementsLang.tr(gatesClosed(sl, s) ? "cmd.sky.pier.closed" : "cmd.sky.pier.open"));
        src.sendSuccess(() -> line, false);
        return 1;
    }

    private SkyAccess() {}
}
