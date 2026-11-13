package com.renanpontes.isekaimahou.feature.automation;

import com.renanpontes.isekaimahou.glyph.ParsedSpell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * v1.5 — gatilho de PEDESTAL: um {@code TriggerGlyph} no começo da receita que também implementa esta interface é
 * conferido pelo Pedestal Rúnico ANTES de conjurar (sem gastar mana). Falso = o pedestal espera ("Aguardando
 * condição") e confere de novo no próximo intervalo.
 * <p>Ex.: "Ao Amadurecer" (há plantação madura na área), "Ao Esvaziar" (há terra arada vazia / camada sem blocos).
 * Deve ser BARATO: no máximo uma varredura da área da magia (veja {@code AutoArea}).
 */
public interface PedestalCondition {
    /**
     * @param level  mundo do pedestal
     * @param pedestal posição do bloco do pedestal
     * @param origin ponto de onde a magia nasce (topo + deslocamento)
     * @param spell  a receita do pergaminho (para ler a área/augments)
     */
    boolean ready(ServerLevel level, BlockPos pedestal, Vec3 origin, ParsedSpell spell);
}
