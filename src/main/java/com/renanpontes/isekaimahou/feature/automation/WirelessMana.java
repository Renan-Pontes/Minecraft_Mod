package com.renanpontes.isekaimahou.feature.automation;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * v1.5 — reservatório de mana "sem fio" (o Núcleo de Mana do módulo mana_core implementa). Pedestais e Condensadores
 * ligados com o Condutor de Mana guardam a POSIÇÃO do núcleo e o acham por aqui, sem importar o módulo dele.
 * <p>Regras da ligação: mesmo mundo, até {@link #RANGE} blocos, e o chunk do núcleo precisa estar carregado
 * (nunca carregamos chunk). Cada {@link #LOSS_STEP} blocos de distância custam +{@link #LOSS_PERCENT}% de mana.
 */
public interface WirelessMana {
    /** Alcance máximo da ligação (distância reta, em blocos). */
    int RANGE = 48;
    /** A cada tantos blocos de distância... */
    int LOSS_STEP = 16;
    /** ...a transmissão custa mais esta porcentagem. */
    int LOSS_PERCENT = 5;

    int mana();

    int capacity();

    default int space() {
        return capacity() - mana();
    }

    /** Coloca mana; devolve quanto coube. */
    int insert(int amount);

    /** Tira mana; devolve quanto saiu. */
    int extract(int amount);

    /** Avisa o núcleo que {@code user} gastou {@code amount} (estatísticas da tela: torres ativas, consumo/min). */
    void noteUse(BlockPos user, int amount);

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================

    /** O núcleo em {@code pos}, se o chunk estiver carregado e o bloco ainda for um núcleo. */
    @Nullable
    static WirelessMana at(Level level, @Nullable BlockPos pos) {
        if (pos == null) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockEntity be = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        return be instanceof WirelessMana w && !be.isRemoved() ? w : null;
    }

    static boolean inRange(BlockPos a, BlockPos b) {
        return a.distSqr(b) <= (double) RANGE * RANGE;
    }

    /** Perda (em %) da transmissão entre {@code a} e {@code b}: 0, 5, 10 ou 15. */
    static int lossPercent(BlockPos a, BlockPos b) {
        int dist = (int) Math.floor(Math.sqrt(a.distSqr(b)));
        return Mth.clamp(dist / LOSS_STEP, 0, RANGE / LOSS_STEP) * LOSS_PERCENT;
    }

    /** Custo final de {@code cost} pago a partir de um núcleo com a perda dada. */
    static int withLoss(int cost, int lossPercent) {
        return cost <= 0 ? 0 : (int) Math.ceil(cost * (100 + lossPercent) / 100.0);
    }
}
