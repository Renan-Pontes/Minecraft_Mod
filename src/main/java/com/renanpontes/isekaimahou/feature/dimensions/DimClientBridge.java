package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * Ganchos que a parte de cliente (DimensionsClient) preenche. O código comum só chama estes campos, então
 * nenhuma classe de cliente é carregada no servidor dedicado.
 */
public final class DimClientBridge {
    /** Abre a tela do Altar da Convergência (no servidor dedicado continua vazio). */
    public static Consumer<BlockPos> openAltar = pos -> {};

    private DimClientBridge() {}
}
