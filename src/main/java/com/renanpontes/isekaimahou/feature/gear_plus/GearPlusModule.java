package com.renanpontes.isekaimahou.feature.gear_plus;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "gear_plus" — v1.0 RELEASE, trabalho "gear":
 * <ul>
 *   <li>{@link SetBonuses} + {@link GearBonuses}: bônus de conjunto (4 peças do mesmo set);</li>
 *   <li>{@link KitRecovery} + {@link KitBest}: recuperar o kit de classe pelo menu B no nível em que o jogador chegou (a
 *       melhor peça que ele já carregou em cada vaga), sem duplicar (peças seladas);</li>
 *   <li>{@link Gems} + {@link GemDrops}: gemas com encaixe, só para Ferreiro, Cavaleiro Rúnico e Encantador;</li>
 *   <li>{@link WeaponBond}: armas de classe que sobem com abates e despertam uma habilidade (8 classes físicas/destreza).</li>
 * </ul>
 * Tudo aparece numa aba só do menu B ("Equipamento", em Personagem) e nas dicas dos itens — nada novo no HUD.
 */
public class GearPlusModule implements Module {
    @Override
    public String id() {
        return "gear_plus";
    }

    @Override
    public void init(IEventBus modBus) {
        GearRegistry.register(modBus);
        GearNet.register(modBus);
        GearBonuses.register();
        KitRecovery.register();
        GemDrops.register();
        WeaponBond.register();
        GearCommands.register();
    }
}
