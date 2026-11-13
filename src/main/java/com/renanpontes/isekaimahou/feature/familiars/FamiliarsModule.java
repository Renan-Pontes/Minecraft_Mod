package com.renanpontes.isekaimahou.feature.familiars;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Módulo "familiars" — v1.0: "Familiares são legais, ainda mais se for tipo patrono que dependem de como você é e forem
 * mudando com você".
 * <ul>
 *   <li>Um familiar por jogador ({@link FamiliarData}), despertado com o Incenso do Vínculo ({@link BondIncenseItem})
 *       perto de uma fogueira.</li>
 *   <li>A forma nasce do jeito de jogar — classe, raça, elemento mais usado e feitos ({@link FamiliarShaping}) — entre
 *       10 formas base ({@link FamiliarForm}), evolui em 3 estágios com o nível do dono e muda de forma se o dono mudar
 *       ({@link FamiliarService}).</li>
 *   <li>Ajuda pequena e temática, sem lutar ({@link FamiliarHelp}): achar minério/tesouro, avisar perigo, cura leve,
 *       buscar itens e uma bolsa para carregar coisas.</li>
 * </ul>
 * Comandos de op: {@code /mahou familiars ...} ({@link FamiliarCommands}). Aba "Familiar" no menu B (só depois de
 * despertar).
 */
public class FamiliarsModule implements Module {
    @Override
    public String id() {
        return "familiars";
    }

    @Override
    public void init(IEventBus modBus) {
        FamiliarContent.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, FamiliarNetwork::register);
        FamiliarService.init();
        FamiliarCommands.register();
    }
}
