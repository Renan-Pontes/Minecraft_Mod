package com.renanpontes.isekaimahou.feature.journal;

import com.renanpontes.isekaimahou.feature.journal.story.StoryCommands;
import com.renanpontes.isekaimahou.feature.journal.story.StoryConfig;
import com.renanpontes.isekaimahou.feature.journal.story.StoryService;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "journal": Diário do Isekai (história principal), Espírito Guia e Bestiário.
 * <ul>
 *   <li>Rota principal data-driven em {@code data/<ns>/journal/*.json} (condições sobre o MagicData e o inventário), avaliada
 *       no servidor: marcadores {@code journal.<capitulo>.<passo>} / {@code journal.<capitulo>}, avisos "Missão atualizada",
 *       recompensas por passo e por capítulo (toasts) e o final épico.</li>
 *   <li>Rastreador de objetivos (núcleo {@code ObjectiveTracker}): capítulo + passo atual com progresso e dica curta;
 *       passos com {@code locate} ganham seta e distância ({@link JournalLocator} procura a estrutura no servidor).</li>
 *   <li>Rota-guia (v0.9): de chefe em chefe e de portal em portal até o Dragão Primordial, com as missões "A Ascensão"
 *       (o tributo de cada Círculo pronto, item do módulo ascension), cidades e Pedras de Teleporte.</li>
 *   <li>Entrada no jogo (v0.9): o capítulo 1 abre no {@code OnboardingEvent.ClassCongratsDone}; o Espírito Guia conta a
 *       história e ensina J, B, Grimório e Z pela caixa de diálogo do núcleo ({@link JournalTutorial}); silêncio no chat
 *       enquanto {@code Onboarding.quiet}.</li>
 *   <li>Espírito Guia ({@code guide_spirit}, GeckoLib): aparece só para o jogador quando um capítulo começa; chamado, voa na
 *       direção do objetivo deixando um rastro de luz; clicado, dá a dica — sempre pela caixa de diálogo.</li>
 *   <li>Contadores de magia {@code spell.linked|leap|multi_element|legend} ({@link JournalSpellCounters}) e de blocos colocados.</li>
 *   <li>Abas do menu B: "Diário" (50) e "Bestiário" (70) — ficam no cliente ({@code client/JournalClient}).</li>
 *   <li>Comandos {@code /mahou journal status|complete|reset|guide|locate|tutorial}.</li>
 *   <li>v1.0 release: a CAMPANHA ({@code story/}: Atos, falas do Eco e cutscenes de marco, sempre puláveis), a seção
 *       "Onde Conseguir" do Guia de Viagem ({@code guide/}) e o Bestiário completo. Comandos {@code /mahou story ...}.</li>
 * </ul>
 */
public class JournalModule implements Module {
    @Override
    public String id() {
        return "journal";
    }

    @Override
    public void init(IEventBus modBus) {
        JournalContent.register(modBus);
        modBus.addListener(JournalNetwork::register);
        JournalEvents.register();
        JournalCommands.register();
        // Campanha (v1.0 release): opções no menu do Minecraft, gatilhos nos marcos e comandos de teste.
        StoryConfig.register();
        StoryService.register();
        StoryCommands.register();
    }
}
