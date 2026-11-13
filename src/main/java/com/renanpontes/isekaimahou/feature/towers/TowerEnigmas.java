package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaPlacer;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaRun;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaType;
import com.renanpontes.isekaimahou.feature.towers.block.RuneSwitchBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enigmas de raciocínio nos andares das Torres (v1.0 r9, "múltiplos puzzles"). Montados em tempo de jogo pelo Núcleo
 * do Andar quando o desafio desperta — vale também para torres de mundos já existentes.
 * <pre>
 * andar 2 (k=1, Salão das Runas): dois enigmas em corrente, nível 1 → nível 2 (o 1º dá uma pista do 2º)
 * andar 4 (k=3, tesouro)         : ondas e depois um enigma nível 2 (selado até as ondas acabarem)
 * andar 5 (k=4, Salão das Runas): dois enigmas em corrente, nível 2 → nível 3
 * andar 7 (k=6, elite)           : ondas de elite e depois um enigma nível 3
 * </pre>
 * Tipos: os 8 embaralhados pela semente da torre e usados em ordem (6 enigmas por torre, nenhum repetido).
 * Lugar: metade sul do andar (Selo ao lado do Núcleo, área de 9 × 8) e, para o segundo, a metade norte; se a área não
 * estiver livre (andar alagado da torre submersa…), tenta leste/oeste e, sem espaço, o andar fica com o desafio antigo.
 */
public final class TowerEnigmas {
    private TowerEnigmas() {}

    /** Níveis dos enigmas do andar {@code floor} (0 = térreo). Vazio = andar sem enigma. */
    public static int[] tiers(int floor) {
        return switch (floor) {
            case 1 -> new int[] {1, 2};
            case 3 -> new int[] {2};
            case 4 -> new int[] {2, 3};
            case 6 -> new int[] {3};
            default -> new int[0];
        };
    }

    private static int slotBase(int floor) {
        return switch (floor) {
            case 1 -> 0;
            case 3 -> 2;
            case 4 -> 3;
            default -> 5;
        };
    }

    /** Monta os enigmas do andar em volta do Núcleo. Devolve as posições dos Selos (vazio se não coube). */
    public static List<BlockPos> setup(ServerLevel sl, BlockPos floorCore, int floor, long towerKey, boolean locked) {
        int[] tiers = tiers(floor);
        List<BlockPos> out = new ArrayList<>();
        if (tiers.length == 0) return out;
        long key = towerKey == TowerData.NONE ? floorCore.asLong() : towerKey;
        EnigmaType[] order = EnigmaRun.order(key ^ sl.getSeed() ^ 0x70E5L);
        String chain = tiers.length > 1 ? "tower:" + floorCore.asLong() : "";
        List<Direction> prefs = new ArrayList<>(List.of(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST));
        for (int i = 0; i < tiers.length; i++) {
            for (Direction d : List.copyOf(prefs)) {
                BlockPos core = floorCore.relative(d);
                if (!EnigmaPlacer.fits(sl, core, d, 4)) continue;
                EnigmaType type = order[(slotBase(floor) + i) % order.length];
                long seed = key * 31 + floor * 7L + i + sl.getSeed();
                PuzzleCoreBlockEntity be = EnigmaPlacer.place(sl, core, d, 4, type, tiers[i], seed, chain, chain.isEmpty() ? 0 : i + 1, locked);
                if (be == null) continue;
                out.add(core.immutable());
                prefs.remove(d);
                // o segundo vai, de preferência, para o lado oposto
                prefs.remove(d.getOpposite());
                prefs.add(0, d.getOpposite());
                break;
            }
        }
        return out;
    }

    public static boolean allSolved(ServerLevel sl, List<BlockPos> cores) {
        for (BlockPos p : cores) {
            if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof PuzzleCoreBlockEntity be && be.enigma() != null && !be.solved()) return false;
        }
        return true;
    }

    public static int solvedCount(ServerLevel sl, List<BlockPos> cores) {
        int n = 0;
        for (BlockPos p : cores) {
            if (!(sl.getBlockEntity(p) instanceof PuzzleCoreBlockEntity be) || be.enigma() == null || be.solved()) n++;
        }
        return n;
    }

    public static void setLocked(ServerLevel sl, List<BlockPos> cores, boolean locked) {
        for (BlockPos p : cores) {
            if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof PuzzleCoreBlockEntity be && be.enigma() != null && !be.solved()) {
                be.enigma().setExternalLock(locked);
            }
        }
    }

    /**
     * Salão das Runas que ganhou enigmas: as Alavancas Rúnicas antigas viram parede (o bloco de cima delas) e a placa
     * "Acenda as runas" passa a falar dos enigmas.
     */
    public static void retireRuneHall(ServerLevel sl, BlockPos floorCore, List<BlockPos> switches) {
        for (BlockPos p : switches) {
            if (!(sl.getBlockState(p).getBlock() instanceof RuneSwitchBlock)) continue;
            BlockState above = sl.getBlockState(p.above());
            sl.setBlock(p, above.isAir() ? sl.getBlockState(p.below()) : above, Block.UPDATE_ALL);
        }
        for (BlockPos p : BlockPos.betweenClosed(floorCore.offset(-10, 0, -10), floorCore.offset(10, 3, 10))) {
            if (!(sl.getBlockEntity(p) instanceof SignBlockEntity sign)) continue;
            Component first = sign.getFrontText().getMessage(0, false);
            if (!(first.getContents() instanceof TranslatableContents tc) || !tc.getKey().equals("sign.isekai_mahou.towers.puzzle_1")) continue;
            SignText text = sign.getFrontText();
            text = text.setMessage(0, Component.translatable("sign.isekai_mahou.enigma.tower_1"))
                    .setMessage(1, Component.translatable("sign.isekai_mahou.enigma.tower_2"))
                    .setMessage(2, Component.translatable("sign.isekai_mahou.enigma.tower_3"));
            sign.setText(text, true);
            sign.setChanged();
            BlockState st = sl.getBlockState(p);
            sl.sendBlockUpdated(p, st, st, Block.UPDATE_ALL);
        }
    }
}
