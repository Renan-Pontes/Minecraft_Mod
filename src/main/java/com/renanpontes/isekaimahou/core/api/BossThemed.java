package com.renanpontes.isekaimahou.core.api;

/**
 * Opcional para entidades de chefe: qual MÚSICA TEMA tocar agora (lido NO CLIENTE pelo módulo boss_music,
 * então o valor tem que vir de dados sincronizados — ex.: SynchedEntityData com a fase).
 * Sem esta interface, o tema é escolhido pelo id do tipo da entidade (ex.: "ulquiorra").
 * Exemplo: Ulquiorra devolve "ulquiorra" na 1ª fase e "ulquiorra_segunda_etapa" depois de liberar a Segunda Etapa.
 * Devolver null = usar o padrão pelo id.
 */
public interface BossThemed {
    String musicTheme();
}
