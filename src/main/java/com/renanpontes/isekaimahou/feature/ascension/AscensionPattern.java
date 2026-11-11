package com.renanpontes.isekaimahou.feature.ascension;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Formato do multibloco do Círculo de Ascensão. v0.7: cada tamanho CONTÉM o anterior — o jogador só aumenta o
 * círculo que já construiu, nunca desmancha:
 * <ul>
 *   <li>alvo 2–4 (5×5): 4 pedestais em cruz a 2 blocos do centro + 4 Pedras Rúnicas nas diagonais;</li>
 *   <li>alvo 5–7 (7×7): o de cima + 4 pedestais nas diagonais externas (3,3) + 4 Pedras Rúnicas em cruz a 3 blocos
 *       (8 pedestais: cabem os tributos com Núcleo de Guardião, 4 essências, Arcana e diamantes);</li>
 *   <li>alvo 8–10 (9×9): o de cima + 8 Pedras Rúnicas no anel externo. v0.9: os cantos (±4,±4) ficam LIVRES — o
 *       anel não pede mais Blocos de Cristal de Mana (os altares prontos das cidades põem colunas ali).</li>
 * </ul>
 * Todas as peças ficam na MESMA altura do Núcleo. {@link Part#MANA_CRYSTAL} continua no enum só para que prévias
 * fantasmas gravadas em mundos antigos (ordinal no NBT) sejam lidas sem erro.
 */
public final class AscensionPattern {

    public enum Part {
        PEDESTAL(0xFFC857), RUNE_STONE(0xB98CFF), MANA_CRYSTAL(0x7FE3FF);

        public final int color;

        Part(int color) { this.color = color; }

        public Block block() {
            return switch (this) {
                case PEDESTAL -> AscensionRegistry.PEDESTAL.get();
                case RUNE_STONE -> AscensionRegistry.RUNE_STONE.get();
                case MANA_CRYSTAL -> AscensionRegistry.MANA_CRYSTAL_BLOCK.get();
            };
        }

        public static Part byOrdinal(int i) {
            Part[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    /** Uma posição do padrão, relativa ao Núcleo. */
    public record Slot(int dx, int dz, Part part) {
        public BlockPos at(BlockPos core) { return core.offset(dx, 0, dz); }
    }

    private static final List<Slot> TIER_1 = build(List.of(),
            new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}},
            new int[][] {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}},
            new int[][] {});
    private static final List<Slot> TIER_2 = build(TIER_1,
            new int[][] {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}},
            new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}},
            new int[][] {});
    private static final List<Slot> TIER_3 = build(TIER_2,
            new int[][] {},
            new int[][] {{4, 2}, {4, -2}, {-4, 2}, {-4, -2}, {2, 4}, {-2, 4}, {2, -4}, {-2, -4}},
            new int[][] {});

    private static List<Slot> build(List<Slot> base, int[][] pedestals, int[][] runes, int[][] crystals) {
        List<Slot> list = new ArrayList<>(base);
        for (int[] p : pedestals) list.add(new Slot(p[0], p[1], Part.PEDESTAL));
        for (int[] p : runes) list.add(new Slot(p[0], p[1], Part.RUNE_STONE));
        for (int[] p : crystals) list.add(new Slot(p[0], p[1], Part.MANA_CRYSTAL));
        return List.copyOf(list);
    }

    /** 1, 2 ou 3 — o tamanho do círculo para o Círculo-alvo (5×5 até o 4º, 7×7 até o 7º, 9×9 depois). */
    public static int tier(int targetCircle) {
        if (targetCircle <= 4) return 1;
        if (targetCircle <= 7) return 2;
        return 3;
    }

    /** Raio do multibloco (2, 3 ou 4). */
    public static int radius(int targetCircle) {
        return tier(targetCircle) + 1;
    }

    /** v1.0: folga da barreira além do multibloco (era 2; +3 blocos de raio para lutar com espaço). */
    public static final int BARRIER_EXTRA = 5;
    /** Altura da parede reta do domo de runas (acima dela, a cúpula). */
    public static final int DOME_WALL = 4;
    /** Quantos blocos abaixo do Núcleo a barreira/proteção alcança (cavar para fugir não adianta). */
    public static final int BARRIER_BELOW = 4;
    /** Maior barreira possível (padrão 9×9 + folga): raio da área protegida dos altares públicos. */
    public static final int MAX_BOUNDARY = 4 + BARRIER_EXTRA;

    /**
     * Raio da BARREIRA da Tribulação (raio do multibloco + {@value #BARRIER_EXTRA}): quem ascende não passa dela
     * (mobs e magias passam). 7, 8 ou 9 blocos.
     */
    public static int boundary(int targetCircle) {
        return radius(targetCircle) + BARRIER_EXTRA;
    }

    /**
     * Altura do domo de runas (acima do Núcleo) a {@code d} blocos do centro, na horizontal: parede reta de
     * {@value #DOME_WALL} blocos e uma cúpula de raio {@code boundary} em cima.
     */
    public static double domeTop(double boundary, double d) {
        double h = boundary * boundary - d * d;
        return DOME_WALL + (h > 0 ? Math.sqrt(h) : 0);
    }

    /** Altura total do domo acima do Núcleo. */
    public static double domeHeight(double boundary) {
        return DOME_WALL + boundary;
    }

    public static List<Slot> layout(int targetCircle) {
        return switch (tier(targetCircle)) {
            case 1 -> TIER_1;
            case 2 -> TIER_2;
            default -> TIER_3;
        };
    }

    /** Posições do padrão que estão faltando (ou com o bloco errado). Vazio = estrutura completa. */
    public static List<Slot> missing(Level level, BlockPos core, int targetCircle) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : layout(targetCircle)) {
            if (!level.getBlockState(s.at(core)).is(s.part().block())) out.add(s);
        }
        return out;
    }

    /** Maior padrão (9×9): toda posição que pode ter uma peça de algum Círculo. */
    public static List<Slot> fullLayout() {
        return TIER_3;
    }

    /** Posições (mundo) dos pedestais do padrão. */
    public static List<BlockPos> pedestals(BlockPos core, int targetCircle) {
        List<BlockPos> out = new ArrayList<>();
        for (Slot s : layout(targetCircle)) if (s.part() == Part.PEDESTAL) out.add(s.at(core));
        return out;
    }

    /** Resumo "faltam: 2× Pedestal, 1× Pedra Rúnica" para o chat. */
    public static MutableComponent describeMissing(List<Slot> missing) {
        Map<Part, Integer> count = new EnumMap<>(Part.class);
        for (Slot s : missing) count.merge(s.part(), 1, Integer::sum);
        MutableComponent text = Component.empty();
        boolean first = true;
        for (Map.Entry<Part, Integer> e : count.entrySet()) {
            if (!first) text.append(Component.literal(", "));
            first = false;
            text.append(Component.translatable("message.isekai_mahou.ascension.count",
                    e.getValue(), e.getKey().block().getName()).withColor(e.getKey().color));
        }
        return text;
    }

    private AscensionPattern() {}
}
