package com.renanpontes.isekaimahou.feature.bosses_e;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialChain;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "bosses_e": o DRAGÃO PRIMORDIAL, chefe final que surge da Fenda quando o Arauto do Crepúsculo cai
 * (gancho por id em {@code bosses_b/TwilightHerald.onFinalDeath}). v1.0: feito para 6 jogadores de nível máximo —
 * vida/dano pelo grupo, ultimates que EXIGEM contramágica (Pilha), e sem morte instantânea: estátuas, correntes,
 * drenos, troca de lugar, loadout apagado, Maldição do Esquecimento, Marca do Sacrifício e Ruína ({@code DragonCurses}).
 * 5 fases elementais, arena GRANDE da Fenda (raio ~42, protegida pela {@code BossArena}) que se transforma com
 * terreno temporário, domínio "Fim dos Elementos", Clones de Sombra e a Aniquilação Primordial.
 * Recompensas: Coração do Dragão Primordial, título "Matador de Dragões", pergaminhos de lenda e o contador
 * {@code boss.primordial_dragon} para TODOS que lutaram (libera o nível 150→200).
 * Teste: {@code /mahou spawnboss primordial_dragon [rift]} e {@code /mahou dragon curse <tipo>}.
 */
public class BossesEModule implements Module {
    @Override
    public String id() {
        return "bosses_e";
    }

    @Override
    public void init(IEventBus modBus) {
        BeRegistries.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTabs);
        modBus.addListener(DragonLegends::setup);
        modBus.addListener(this::setup);
        BeCommands.register();
        BeEvents.register();
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(BeRegistries.PRIMORDIAL_DRAGON.get(), PrimordialDragon.createAttributes().build());
        e.put(BeRegistries.PRIMORDIAL_CHAIN.get(), PrimordialChain.createAttributes().build());
    }

    /** A arena da Fenda (estrutura e lugar do hub) fica SEMPRE protegida — é onde o Dragão nasce e luta. */
    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            BossArena.protectStructure(com.renanpontes.isekaimahou.IsekaiMahou.id("twilight_arena"));
            BossArena.protectPlace("hub_twilight_rift");
        });
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(BeRegistries.PRIMORDIAL_DRAGON_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            e.accept(BeRegistries.PRIMORDIAL_CHAIN_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            e.accept(BeRegistries.PRIMORDIAL_DRAGON_HEART.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
