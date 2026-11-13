package com.renanpontes.isekaimahou.feature.flat_lands;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "flat_lands" (v1.6, docs/V16_DESIGN.md): biomas PLANOS e raros no Overworld, bons para cidades, e o
 * Santuário Torii com o Omikuji.
 * <ul>
 *   <li>Biomas {@code sakura_fields} (Campos de Sakura: cerejeiras esparsas e pétalas no ar),
 *       {@code flat_lands_golden_steppe} (Estepe Dourada: capim alto dourado) e
 *       {@code flat_lands_kingdom_meadows} (Prados do Reino: planície florida). Entram no gerador do vanilla pelo
 *       mixin {@code OverworldBiomeBuilderFlatMixin} (regra em {@link FlatLandsBiomes}); só em chunks NOVOS.</li>
 *   <li>Tag {@code #isekai_mahou:flat_lands}, incluída nas tags de bioma das cidades e capitais
 *       ({@code has_structure/guild_city}, {@code capital_*}).</li>
 *   <li>Estrutura {@code sakura_torii_shrine} (Santuário Torii) com a Caixa do Omikuji: sorte do dia
 *       (bênção ou má sorte leve por 1 dia do jogo). Moldes/texturas/dados: {@code tools/gen/flat_lands.py}.</li>
 * </ul>
 */
public class FlatLandsModule implements Module {
    @Override
    public String id() {
        return "flat_lands";
    }

    @Override
    public void init(IEventBus modBus) {
        FlatLandsContent.register(modBus);
        modBus.addListener(FlatLandsModule::creativeTabs);
        FlatLandsCommands.init();
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(FlatLandsContent.OMIKUJI_BOX_ITEM.get());
            e.accept(FlatLandsContent.FORTUNE_SLIP.get());
        }
    }
}
