package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.feature.settlements.SkyCity;
import com.renanpontes.isekaimahou.feature.settlements.SkyIsles;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Onda 7 (sky_ui) — contas da PRÉVIA da Ilha Celeste (o desenho fica em {@link CityPlacer}): o contorno da ilha que
 * nasceria sobre um ponto, tirado da MESMA conta do gerador ({@link SkyCity#inside}: conta pura, igual no cliente e no
 * servidor), e um palpite da altitude para os instantes em que a resposta do servidor ainda não chegou.
 */
final class SkyPreview {
    /** Pontos do contorno (um a cada 3,75°): suficiente para ver baías e pontas sem pesar. */
    static final int POINTS = 96;
    /** Cor do holograma da ilha (azul-céu). */
    static final int SKY = 0x8FD3FF;

    private static long rimKey = Long.MIN_VALUE;
    private static int rimRadius = -1;
    private static final float[] RIM = new float[POINTS * 2];

    /**
     * Contorno (pares dx, dz a partir do coração) da ilha de um raio fundada na coluna do {@code heart}. Refeito só
     * quando a prévia muda de coluna (a forma depende do x/z do coração).
     */
    static float[] rim(BlockPos heart, int radius) {
        long key = BlockPos.asLong(heart.getX(), 0, heart.getZ());
        if (key == rimKey && radius == rimRadius) return RIM;
        rimKey = key;
        rimRadius = radius;
        outline(heart, radius, RIM);
        return RIM;
    }

    /** Preenche {@code out} (pares dx, dz) com o contorno da ilha: um ponto por ângulo, a última coluna com terra. */
    static void outline(BlockPos heart, int radius, float[] out) {
        int points = out.length / 2;
        // a borda mais estufada fica a no máximo `reach` num eixo; na diagonal, um pouco mais
        int far = (int) (SkyCity.reach(radius) * 1.45F) + 2;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double cos = Math.cos(a), sin = Math.sin(a);
            int d = far;
            // de fora para dentro, de 2 em 2, e depois o bloco exato
            while (d > 0 && !SkyCity.inside(heart, radius, (int) Math.round(cos * d), (int) Math.round(sin * d))) d -= 2;
            if (d > 0 && SkyCity.inside(heart, radius, (int) Math.round(cos * (d + 1)), (int) Math.round(sin * (d + 1)))) d++;
            out[i * 2] = (float) (cos * (d + 0.5));
            out[i * 2 + 1] = (float) (sin * (d + 0.5));
        }
    }

    /** Raio da ilha da fundação (a primeira ilha cobre o território do começo). */
    static int startRadius() {
        return CityLayout.START_RADIUS;
    }

    /**
     * Palpite da altitude do Coração enquanto o servidor não responde: a mesma regra dele, só com a altura do ponto
     * (o servidor ainda olha os morros em volta — a resposta dele substitui este valor).
     */
    static int guessY(BlockPos ground) {
        // onda 9 (isles): a mesma regra do servidor para a ilha nova (bem mais alta)
        int y = SkyIsles.heartY(ground.getY());
        return y > 0 ? y : SkyIsles.MAX_Y;
    }

    // ---- onda 9 (isles): toda Ilha Celeste nova é um arquipélago — a prévia mostra a ilha do MEIO (as outras nascem
    // com as obras) e o poço da Corrente Ascendente
    private static long coreKey = Long.MIN_VALUE;
    private static Direction coreFront = Direction.SOUTH;
    private static final float[] CORE_RIM = new float[POINTS * 2];

    /** Contorno (pares dx, dz a partir do coração) da ilha do meio de um arquipélago novo com esta frente da praça. */
    static float[] coreRim(BlockPos heart, Direction front) {
        long key = BlockPos.asLong(heart.getX(), 0, heart.getZ());
        if (key == coreKey && front == coreFront) return CORE_RIM;
        coreKey = key;
        coreFront = front;
        int points = CORE_RIM.length / 2;
        int far = SkyIsles.coreReach() + 2;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double cos = Math.cos(a), sin = Math.sin(a);
            int d = (int) (far * 1.45F);
            while (d > 0 && !SkyIsles.inCore(heart, front, (int) Math.round(cos * d), (int) Math.round(sin * d))) d--;
            CORE_RIM[i * 2] = (float) (cos * (d + 0.5));
            CORE_RIM[i * 2 + 1] = (float) (sin * (d + 0.5));
        }
        return CORE_RIM;
    }

    private SkyPreview() {}
}
