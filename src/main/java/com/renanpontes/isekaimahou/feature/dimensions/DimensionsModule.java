package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "dimensions": as quatro dimensões do mod e o que leva até elas.
 * <p>v0.9: os portais deixaram de ser "portais do Nether" montados pelo jogador — não há mais receita de moldura,
 * Farol ou Fenda. Cada portal espera ADORMECIDO numa estrutura do Mundo Normal (módulo world_v09) que a rota
 * principal do Diário revela; despertar continua como antes (oferenda + Círculo). Os blocos seguem funcionando em
 * mundos antigos e por comando/criativo; dentro das estruturas as peças do portal são protegidas.
 * <ul>
 *   <li><b>Plano Elemental</b> (Círculo 3) — Portão Elemental: moldura elemental, desperta com Cristal de Mana + 50 de
 *       mana. Arquipélago flutuante em 4 regiões, templos dos 4 Guardiões e o hub com o Altar da Convergência.</li>
 *   <li><b>Abismo</b> (Círculo 6) — Fenda Abissal: moldura sombria no fundo do cânion, desperta com Essência de Sombra.
 *       Cavernas escuras sobre um mar abissal, Trono do Soberano.</li>
 *   <li><b>Santuário Celestial</b> (Círculo 6) — Escadaria Celeste: Farol sobre ouro, aceso ao meio-dia com Essência de
 *       Luz. Ilhas de nuvem e cidades de cristal, Catedral do Serafim.</li>
 *   <li><b>Fenda do Crepúsculo</b> (Círculo 9 + Soberano + Serafim) — Ruptura do Crepúsculo: a Fenda já aberta, que só
 *       deixa passar quem cumpriu os requisitos. Vazio com a arena do Arauto.</li>
 * </ul>
 * Cada hub vira um lugar do {@code Places} ({@code hub_<dimensão>}, tipo {@code dimension_hub}) para a Viagem rápida.
 * O terreno é 100% dados (dimension_type, noise_settings, biomas, features, estruturas); o Java cuida dos
 * portais, da viagem, dos hubs, da Convergência e do céu mágico (cliente).
 */
public class DimensionsModule implements Module {
    @Override
    public String id() {
        return "dimensions";
    }

    @Override
    public void init(IEventBus modBus) {
        DimRegistry.register(modBus);
        modBus.addListener(DimPayloads::register);
        modBus.addListener(this::onCreativeTabs);
        DimEvents.register(NeoForge.EVENT_BUS);
        DimCommands.register();
        // Magias que destroem terreno não desmontam os hubs nem os portais das estruturas.
        Destruction.addProtection(DimSites::destructionProtected);
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            for (Supplier<? extends Item> item : DimRegistry.TAB_ORDER) e.accept(item.get());
        }
    }
}
