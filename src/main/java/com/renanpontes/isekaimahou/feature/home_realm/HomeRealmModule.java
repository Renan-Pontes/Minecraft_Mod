package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.travel.guide.PortalGuide;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "home_realm" — release 1.0: <b>Dimensão Casa</b> — "ter uma 'dimensão casa', se comprar libera pra entrar a
 * qualquer hora que não em combate por 2 min".
 * <ul>
 *   <li>Compra: item {@code isekai_mahou:home_key} (Chave do Lar), vendido bem caro pelo estalajadeiro; usar consome a
 *       chave e cria o terreno do jogador ({@link HomeKeyItem}, {@link HomeManager#unlock}).</li>
 *   <li>Dimensão própria {@code isekai_mahou:home_realm} (vazia): cada jogador tem uma ilha flutuante com uma casinha
 *       pronta, horta, lago e 129×129 blocos para construir ({@link HomeDimension}).</li>
 *   <li>Entrar/sair pela tecla (padrão Home) ou pela seção "Lar" do Guia de Viagem no menu B; só entra quem está há 2 min
 *       fora de combate, e ao sair volta para onde estava ({@link HomeManager}).</li>
 *   <li>Convidar amigos (visitam quando quiserem; só mexem se o dono deixar). Sem monstros, sem PvP, sem dano; baús e
 *       camas funcionam (a cama vale como ponto de renascimento e passa a noite).</li>
 * </ul>
 * Teste: {@code /mahou home key | unlock | go | goto <jogador> | combat [clear] | status} (op) e
 * {@code /mahou home enter | leave | visit | invite | uninvite} (todos).
 */
public class HomeRealmModule implements Module {
    @Override
    public String id() {
        return "home_realm";
    }

    @Override
    public void init(IEventBus modBus) {
        HomeContent.ITEMS.register(modBus);
        modBus.addListener(HomeNetwork::register);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(HomeManager.TICKETS));
        modBus.addListener(this::onCreativeTabs);
        HomeManager.register();
        HomeCommands.register();
        // Guia de Portais (aba Viagem): a Dimensão Casa não tem portal físico — entra-se pela tecla ou pelo menu
        PortalGuide.register(new PortalGuide.Entry("home_realm", 0x8FD8FF, List.of(), Level.OVERWORLD, 0, List.of(),
                "isekai_mahou:home_key", false));
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) e.accept(HomeContent.HOME_KEY.get());
    }
}
