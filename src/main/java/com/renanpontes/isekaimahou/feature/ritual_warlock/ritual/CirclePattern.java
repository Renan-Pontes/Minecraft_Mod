package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.RitualChalkBlock;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Os três desenhos de círculo aceitos em volta do Altar de Oferendas (mesma altura do altar):
 * <ul>
 *   <li>Pequeno (pactos 1–3): anel de giz 3×3 + 4 velas acesas nas pontas (cruz ou cantos).</li>
 *   <li>Médio (4–6): anel 5×5 + 6 velas em hexágono (deitado ou em pé).</li>
 *   <li>Grande (7–10): anel 7×7 + 8 velas em octógono (ou estrela).</li>
 * </ul>
 * Um círculo maior também serve para pactos menores.
 */
public enum CirclePattern {
    SMALL(1, new int[][][]{
            {{0, -2}, {0, 2}, {-2, 0}, {2, 0}},
            {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}}),
    MEDIUM(2, new int[][][]{
            {{3, 0}, {-3, 0}, {2, 3}, {-2, 3}, {2, -3}, {-2, -3}},
            {{0, 3}, {0, -3}, {3, 2}, {3, -2}, {-3, 2}, {-3, -2}}}),
    LARGE(3, new int[][][]{
            {{2, 4}, {-2, 4}, {2, -4}, {-2, -4}, {4, 2}, {4, -2}, {-4, 2}, {-4, -2}},
            {{0, 4}, {0, -4}, {4, 0}, {-4, 0}, {4, 4}, {4, -4}, {-4, 4}, {-4, -4}}});

    private static final Vector3f CHALK_GUIDE = new Vector3f(0.95F, 0.95F, 0.92F);

    /** "Raio" do anel de giz: 1 = 3×3, 2 = 5×5, 3 = 7×7. */
    public final int radius;
    private final int[][][] candleVariants;

    CirclePattern(int radius, int[][][] candleVariants) {
        this.radius = radius;
        this.candleVariants = candleVariants;
    }

    public int candleCount() {
        return candleVariants[0].length;
    }

    public MutableComponent displayName() {
        return switch (this) {
            case SMALL -> Component.translatable("warlock.isekai_mahou.circle.small");
            case MEDIUM -> Component.translatable("warlock.isekai_mahou.circle.medium");
            case LARGE -> Component.translatable("warlock.isekai_mahou.circle.large");
        };
    }

    /** Círculo exigido pelo pacto de número {@code n} (1..10; acima de 10 continua o Grande). */
    public static CirclePattern forPact(int n) {
        if (n <= 3) return SMALL;
        if (n <= 6) return MEDIUM;
        return LARGE;
    }

    public static CirclePattern byOrdinal(int i) {
        CirclePattern[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    // ---------------------------------------------------------------- posições
    public List<BlockPos> chalkPositions(BlockPos altar) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == radius) out.add(altar.offset(x, 0, z));
            }
        }
        return out;
    }

    public List<BlockPos> candlePositions(BlockPos altar, int variant) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] o : candleVariants[Math.floorMod(variant, candleVariants.length)]) out.add(altar.offset(o[0], 0, o[1]));
        return out;
    }

    // ---------------------------------------------------------------- validação
    public static boolean isChalk(BlockState s) {
        return s.is(WarlockContent.RITUAL_CHALK.get());
    }

    public static boolean isLitCandle(BlockState s) {
        return s.is(BlockTags.CANDLES) && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    public int missingChalk(Level level, BlockPos altar) {
        int n = 0;
        for (BlockPos p : chalkPositions(altar)) if (!isChalk(level.getBlockState(p))) n++;
        return n;
    }

    /** Variante de velas com menos velas faltando (para a mensagem/guia). */
    public int bestCandleVariant(Level level, BlockPos altar) {
        int best = 0, bestMissing = Integer.MAX_VALUE;
        for (int v = 0; v < candleVariants.length; v++) {
            int m = missingCandles(level, altar, v);
            if (m < bestMissing) { bestMissing = m; best = v; }
        }
        return best;
    }

    public int missingCandles(Level level, BlockPos altar, int variant) {
        int n = 0;
        for (BlockPos p : candlePositions(altar, variant)) if (!isLitCandle(level.getBlockState(p))) n++;
        return n;
    }

    public boolean isComplete(Level level, BlockPos altar) {
        if (!level.getBlockState(altar).is(WarlockContent.OFFERING_ALTAR.get())) return false;
        if (missingChalk(level, altar) > 0) return false;
        return missingCandles(level, altar, bestCandleVariant(level, altar)) == 0;
    }

    /** Maior círculo completo em volta do altar, ou null. */
    public static CirclePattern largestComplete(Level level, BlockPos altar) {
        CirclePattern[] v = values();
        for (int i = v.length - 1; i >= 0; i--) if (v[i].isComplete(level, altar)) return v[i];
        return null;
    }

    // ---------------------------------------------------------------- efeitos no mundo
    /** Pinta o giz de sangue (ativo) ou volta ao branco. */
    public void setBlood(Level level, BlockPos altar, boolean blood) {
        for (BlockPos p : chalkPositions(altar)) {
            BlockState s = level.getBlockState(p);
            if (isChalk(s) && s.getValue(RitualChalkBlock.BLOOD) != blood) {
                level.setBlock(p, s.setValue(RitualChalkBlock.BLOOD, blood), 3);
            }
        }
    }

    /** Apaga as velas do círculo (o poder do ritual se esgotou). */
    public void extinguishCandles(Level level, BlockPos altar) {
        for (int v = 0; v < candleVariants.length; v++) {
            for (BlockPos p : candlePositions(altar, v)) {
                BlockState s = level.getBlockState(p);
                if (isLitCandle(s)) AbstractCandleBlock.extinguish(null, s, level, p);
            }
        }
    }

    /** Fim do ritual (selado ou rompido): giz volta ao branco e as velas se apagam. */
    public void release(Level level, BlockPos altar) {
        setBlood(level, altar, false);
        extinguishCandles(level, altar);
    }

    /** Velas acesas atualmente no círculo (para desenhar as "correntes" até o Patrono). */
    public List<BlockPos> litCandles(Level level, BlockPos altar) {
        List<BlockPos> out = new ArrayList<>();
        for (int v = 0; v < candleVariants.length; v++) {
            for (BlockPos p : candlePositions(altar, v)) if (isLitCandle(level.getBlockState(p))) out.add(p);
        }
        return out;
    }

    /**
     * Mostra SÓ para o jogador onde falta giz (pó branco) e velas (chamas), por alguns segundos.
     * @return quantos blocos faltam no total
     */
    public int showGuide(ServerPlayer player, ServerLevel level, BlockPos altar) {
        int variant = bestCandleVariant(level, altar);
        List<BlockPos> chalk = new ArrayList<>();
        for (BlockPos p : chalkPositions(altar)) if (!isChalk(level.getBlockState(p))) chalk.add(p);
        List<BlockPos> candles = new ArrayList<>();
        for (BlockPos p : candlePositions(altar, variant)) if (!isLitCandle(level.getBlockState(p))) candles.add(p);
        DustParticleOptions dust = new DustParticleOptions(CHALK_GUIDE, 1.2F);
        for (int i = 0; i < 8; i++) {
            SpellScheduler.schedule(i * 12, () -> {
                if (player.isRemoved() || player.level() != level) return;
                for (BlockPos p : chalk) {
                    level.sendParticles(player, dust, true, p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5, 3, 0.25, 0.02, 0.25, 0);
                }
                for (BlockPos p : candles) {
                    level.sendParticles(player, ParticleTypes.SMALL_FLAME, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.05, 0.15, 0.05, 0.005);
                }
            });
        }
        return chalk.size() + candles.size();
    }
}
