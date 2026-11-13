package com.renanpontes.isekaimahou.feature.endless_abyss;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "endless_abyss" — release 1.0: <b>Abismo Infinito</b> e <b>Lutas das Sombras</b> (pós nível 100).
 * <ul>
 *   <li>Entrada pela aba "Abismo" do menu B, liberada no nível {@link AbyssManager#UNLOCK_LEVEL}.</li>
 *   <li>Dimensão própria {@code isekai_mahou:endless_abyss} (vazia): cada descida tem a sua arena ({@link AbyssDimension}).</li>
 *   <li>Andares sem fim com ondas e, a cada 5, um chefe — a Sombra de um chefe já vencido ({@link AbyssRun},
 *       {@link AbyssFloors}, {@link AbyssShadows}); Luta das Sombras avulsa contra o chefe escolhido.</li>
 *   <li>Recorde por jogador salvo e exposto para ranking ({@link AbyssRecords}); recompensas em {@link AbyssRewards}.</li>
 *   <li>Cair/sair/deslogar devolve o jogador inteiro para onde ele estava ({@link AbyssManager}).</li>
 * </ul>
 * Teste: {@code /mahou abyss enter [andar]}, {@code shadow <chefe>}, {@code floor <n>}, {@code skip}, {@code status}.
 */
public class EndlessAbyssModule implements Module {
    @Override
    public String id() {
        return "endless_abyss";
    }

    @Override
    public void init(IEventBus modBus) {
        AbyssContent.ITEMS.register(modBus);
        modBus.addListener(AbyssNetwork::register);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(AbyssManager.TICKETS));
        modBus.addListener(this::onCreativeTabs);
        AbyssManager.register();
        AbyssCommands.register();
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_WORLD.getKey()) return;
        e.accept(AbyssContent.ABYSS_SHARD.get());
        e.accept(AbyssContent.ABYSS_CORE.get());
        e.accept(AbyssContent.SHADOW_FRAGMENT.get());
    }
}
