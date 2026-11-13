package com.renanpontes.isekaimahou.feature.auto_glyphs;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Efeito de fazenda que sabe dizer, BARATO, se há trabalho na área (usado pelo gatilho "Quando Houver Trabalho").
 * Varre no máximo a área do efeito uma vez e só em chunks carregados.
 */
public interface WorkProbe {
    /**
     * @param center   centro estimado da área (o bloco do pedestal / o chão sob o jogador)
     * @param pedestal posição do pedestal (null = conjuração com a mão)
     * @param stats    aumentos do efeito na receita
     */
    boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, SpellStats stats);
}
