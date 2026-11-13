package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Release 1.0 (costura) — <b>blocos de serviço das ondas 1–2 nas cidades do mundo</b>, sem mexer nos moldes:
 * <ul>
 *   <li>{@code isekai_mahou:market_board} (Quadro do Mercado) na praça de toda cidade, ao lado de uma avenida;</li>
 *   <li>{@code isekai_mahou:new_beginnings_altar} (resetar talentos) e {@code isekai_mahou:reincarnation_altar} (trocar de
 *       classe e raça) SÓ nas capitais — palavra do Renan: "um local pra resetar e outro pra trocar de classe e Raça":
 *       dois lugares distintos, em lados opostos da praça (nunca a menos de {@value #ALTAR_GAP} blocos um do outro).</li>
 * </ul>
 * Quem põe é o coração da cidade ({@link CityHeartBlockEntity}), então vale para mundos novos E para cidades que já
 * existiam: na primeira vez que a praça está carregada, cada bloco aparece num ponto livre. O mesmo caminho do Quadro de
 * Renome ({@code feature/renown/BoardPlacer}) — "cidades são sagradas": só em AR (ou neve do clima), sobre chão firme, com a frente/volta
 * livre, longe de portas e fora das avenidas; nada é quebrado nem trocado. Uma vez por cidade: se alguém tirar o bloco
 * de lá, ele não volta. Os blocos são buscados pelo id (módulo ausente = nada acontece).
 */
public final class CityFixtures {
    /** Os blocos de serviço. A ordem importa: o Altar da Reencarnação espera o do Recomeço para ficar longe dele. */
    public enum Kind {
        MARKET_BOARD("market_board", "FixMarket", false),
        NEW_BEGINNINGS("new_beginnings_altar", "FixRenewal", true),
        REINCARNATION("reincarnation_altar", "FixReinc", true);

        public final String blockId;
        /** Chave NBT (no coração) da posição onde o bloco foi posto. */
        public final String nbtKey;
        public final boolean capitalOnly;

        Kind(String blockId, String nbtKey, boolean capitalOnly) {
            this.blockId = blockId;
            this.nbtKey = nbtKey;
            this.capitalOnly = capitalOnly;
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    /** Distância mínima entre os dois altares (lados opostos da praça de 51 blocos). */
    static final int ALTAR_GAP = 24;
    /** Distância preferida entre os altares: o outro lado da praça. */
    private static final int ALTAR_FAR = 34;
    /** Distância mínima entre dois blocos de serviço quaisquer. */
    private static final int ANY_GAP = 6;
    /** Tentativas com tudo carregado e nenhum ponto livre antes de desistir (uma a cada 10 s ≈ 20 min de cidade carregada). */
    private static final int MAX_TRIES = 120;
    private static final String HINT_MARKET = "cities.market_board", HINT_ALTARS = "cities.capital_altars";

    /** Um ponto candidato: deslocamento do coração e (para o quadro) para onde a frente olha. */
    private record Spot(int dx, int dz, Direction facing) {}

    private static final List<Spot> BOARD_SPOTS = new ArrayList<>(), ALTAR_SPOTS = new ArrayList<>();
    /** Quantos pontos de cada lista são os "bons" (o resto é reserva para praças diferentes do molde atual). */
    private static final int BOARD_PRIMARY = 8, ALTAR_PRIMARY = 8;

    static {
        int[] s = {1, -1};
        // Quadro: no mosaico entre as duas floreiras de cada avenida (x=±5, z=±12), de frente para a avenida.
        for (int sx : s) for (int sz : s) BOARD_SPOTS.add(new Spot(sx * 5, sz * 12, sx > 0 ? Direction.WEST : Direction.EAST));
        for (int sx : s) for (int sz : s) BOARD_SPOTS.add(new Spot(sx * 12, sz * 5, sz > 0 ? Direction.NORTH : Direction.SOUTH));
        // Altares: na faixa de mosaico entre os jardins dos quadrantes e a borda da praça.
        for (int sx : s) for (int sz : s) ALTAR_SPOTS.add(new Spot(sx * 21, sz * 14, Direction.NORTH));
        for (int sx : s) for (int sz : s) ALTAR_SPOTS.add(new Spot(sx * 14, sz * 21, Direction.NORTH));
        // Reserva (praça mexida, cidade antiga, coração posto por comando): anéis em volta do chafariz, fora das avenidas.
        for (int radius : new int[] {12, 16, 20, 23}) {
            for (int i = 0; i < 16; i++) {
                double a = (i + 0.5) * Math.PI / 8;
                int dx = (int) Math.round(Math.cos(a) * radius), dz = (int) Math.round(Math.sin(a) * radius);
                if (Math.abs(dx) <= 4 || Math.abs(dz) <= 4) continue;
                Direction toHeart = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST)
                        : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                BOARD_SPOTS.add(new Spot(dx, dz, toHeart));
                ALTAR_SPOTS.add(new Spot(dx, dz, toHeart));
            }
        }
    }

    private CityFixtures() {}

    // =====================================================================================
    //  Colocação (chamado pelo coração, de tempos em tempos, até resolver os três)
    // =====================================================================================
    /** @return quantos blocos foram postos nesta chamada */
    public static int tick(ServerLevel sl, CityHeartBlockEntity heart) {
        if (heart.fixturesSettled() || "home_realm".equals(sl.dimension().location().getPath())) return 0;
        Places.Place place = Places.get(sl, heart.placeId());
        if (place == null) return 0;
        int placed = 0;
        boolean blocked = false;
        for (Kind kind : Kind.values()) {
            if (heart.fixtureDone(kind)) continue;
            if (kind.capitalOnly && !heart.capital()) {
                heart.settleFixture(kind, null);
                continue;
            }
            // O segundo altar só escolhe o lugar depois que o primeiro foi resolvido (para ficar do outro lado da praça)
            if (kind == Kind.REINCARNATION && !heart.fixtureDone(Kind.NEW_BEGINNINGS)) continue;
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id(kind.blockId));
            if (block.isEmpty()) continue;   // módulo ausente: nada a pôr (e nada gravado; se o módulo voltar, o bloco vem)
            int result = tryPlace(sl, heart, place, kind, block.get());
            if (result == PLACED) placed++;
            else if (result == BLOCKED) blocked = true;
        }
        if (blocked && heart.addFixtureTry() >= MAX_TRIES) {
            // Praça sem um canto livre (cidade muito mexida): desiste em vez de tentar para sempre
            for (Kind kind : Kind.values()) {
                if (!heart.fixtureDone(kind) && BuiltInRegistries.BLOCK.containsKey(IsekaiMahou.id(kind.blockId))) {
                    heart.settleFixture(kind, null);
                    IsekaiMahou.LOGGER.info("Cidade {}: sem lugar livre na praça para {} — desisti", heart.placeId(), kind.blockId);
                }
            }
        }
        return placed;
    }

    private static final int PLACED = 0, BLOCKED = 1, UNLOADED = 2;

    /** Primeiro tenta o altar bem do outro lado da praça; se não der, aceita a distância mínima. */
    private static int tryPlace(ServerLevel sl, CityHeartBlockEntity heart, Places.Place place, Kind kind, Block block) {
        if (!kind.capitalOnly) return tryPlace(sl, heart, place, kind, block, ALTAR_GAP);
        int far = tryPlace(sl, heart, place, kind, block, ALTAR_FAR);
        return far == BLOCKED ? tryPlace(sl, heart, place, kind, block, ALTAR_GAP) : far;
    }

    private static int tryPlace(ServerLevel sl, CityHeartBlockEntity heart, Places.Place place, Kind kind, Block block, int altarGap) {
        BlockPos c = heart.getBlockPos();
        int y = heart.floorY() + 1;
        boolean board = kind == Kind.MARKET_BOARD;
        List<Spot> spots = board ? BOARD_SPOTS : ALTAR_SPOTS;
        int primary = board ? BOARD_PRIMARY : ALTAR_PRIMARY;
        // Cada cidade começa por um canto diferente (mas sempre o mesmo para a mesma cidade)
        int start = Math.floorMod(c.getX() * 31 + c.getZ() * 17 + kind.ordinal() * 5, primary);
        boolean unloaded = false;
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i < primary ? (i + start) % primary : i);
            BlockPos p = new BlockPos(c.getX() + s.dx(), y, c.getZ() + s.dz());
            if (!place.contains(p)) continue;
            if (!sl.hasChunk((p.getX() - 1) >> 4, (p.getZ() - 1) >> 4) || !sl.hasChunk((p.getX() + 1) >> 4, (p.getZ() + 1) >> 4)
                    || !sl.hasChunk((p.getX() - 1) >> 4, (p.getZ() + 1) >> 4) || !sl.hasChunk((p.getX() + 1) >> 4, (p.getZ() - 1) >> 4)) {
                unloaded = true;
                continue;
            }
            if (tooClose(heart, kind, p, altarGap)) continue;
            if (board ? !boardFits(sl, p, s.facing()) : !altarFits(sl, p)) continue;
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) state = state.setValue(HorizontalDirectionalBlock.FACING, s.facing());
            if (!sl.setBlock(p, state, Block.UPDATE_ALL)) continue;
            heart.settleFixture(kind, p);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 10, 0.35, 0.4, 0.35, 0.0);
            sl.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.2F);
            IsekaiMahou.LOGGER.info("Cidade {}: {} posto em {}", heart.placeId(), kind.blockId, p.toShortString());
            return PLACED;
        }
        return unloaded ? UNLOADED : BLOCKED;
    }

    /** Perto demais de outro bloco de serviço já posto? (Os dois altares ficam em lados opostos da praça.) */
    private static boolean tooClose(CityHeartBlockEntity heart, Kind kind, BlockPos p, int altarGap) {
        for (Kind other : Kind.values()) {
            if (other == kind) continue;
            BlockPos o = heart.fixturePos(other);
            if (o == null) continue;
            int gap = kind.capitalOnly && other.capitalOnly ? altarGap : ANY_GAP;
            if (o.distSqr(p) < (double) gap * gap) return true;
        }
        return false;
    }

    private static boolean floor(ServerLevel sl, BlockPos below) {
        return sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP);
    }

    /** Livre: ar (ou a camada de neve que o clima deixa no piso das cidades do norte — não é bloco da cidade). */
    private static boolean open(ServerLevel sl, BlockPos p) {
        BlockState st = sl.getBlockState(p);
        return (st.isAir() || st.is(Blocks.SNOW)) && sl.getBlockState(p.above()).isAir();
    }

    /** Altar solto no meio do piso: o bloco e os 8 vizinhos são ar (não encosta em banco, muro, porta nem chafariz). */
    private static boolean altarFits(ServerLevel sl, BlockPos p) {
        if (!open(sl, p) || !floor(sl, p.below())) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, 0, dz);
                if (!open(sl, n)) return false;
                // Em volta dá para andar (nada de borda de água ou buraco)
                if ((dx == 0 || dz == 0) && !floor(sl, n.below())) return false;
            }
        }
        return true;
    }

    /** Quadro em pé: frente e lados livres, chão firme embaixo e na frente, nenhuma porta encostada. */
    private static boolean boardFits(ServerLevel sl, BlockPos p, Direction facing) {
        if (!open(sl, p) || !floor(sl, p.below())) return false;
        BlockPos front = p.relative(facing);
        if (!open(sl, front) || !floor(sl, front.below())) return false;
        if (!open(sl, p.relative(facing.getClockWise())) || !open(sl, p.relative(facing.getCounterClockWise()))) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (sl.getBlockState(p.relative(d)).getBlock() instanceof DoorBlock) return false;
        }
        return true;
    }

    // =====================================================================================
    //  Dicas de primeira vez e comando de teste
    // =====================================================================================
    /** Dica (uma vez por jogador) quando ele está numa cidade que já tem o Quadro do Mercado / os altares da capital. */
    static void hints(ServerPlayer sp) {
        boolean market = Hints.seen(sp, HINT_MARKET), altars = Hints.seen(sp, HINT_ALTARS);
        if (market && altars) return;
        CityHeartBlockEntity heart = CityCommands.heartAt(sp.serverLevel(), sp.blockPosition());
        if (heart == null) return;
        if (!market && heart.fixturePos(Kind.MARKET_BOARD) != null) Hints.show(sp, HINT_MARKET);
        if (!altars && heart.fixturePos(Kind.NEW_BEGINNINGS) != null && heart.fixturePos(Kind.REINCARNATION) != null) {
            Hints.show(sp, HINT_ALTARS);
        }
    }

    /**
     * Comando de teste: esquece o que não está mais no lugar (bloco tirado, ou "desisti") e tenta pôr de novo agora.
     *
     * @return quantos blocos foram postos
     */
    static int retry(ServerLevel sl, CityHeartBlockEntity heart) {
        for (Kind kind : Kind.values()) {
            BlockPos p = heart.fixturePos(kind);
            // Posto e ainda lá (ou num chunk descarregado: não dá para saber — não arrisca pôr outro)
            if (p != null && (!sl.isLoaded(p) || present(sl, kind, p))) continue;
            heart.forgetFixture(kind);
        }
        return tick(sl, heart);
    }

    /** O bloco de serviço ainda está onde foi posto? */
    static boolean present(ServerLevel sl, Kind kind, @Nullable BlockPos p) {
        if (p == null || !sl.isLoaded(p)) return false;
        return BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id(kind.blockId)).map(b -> sl.getBlockState(p).is(b)).orElse(false);
    }
}
