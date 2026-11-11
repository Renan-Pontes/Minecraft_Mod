package com.renanpontes.isekaimahou.feature.structures_c;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "structures_c": estruturas de RPG do Overworld — Vila da Guilda dos Aventureiros, Vila e Fortaleza
 * Goblin, Acampamento Orc, Covil de Kobolds, Labirinto do Minotauro, Ninho de Harpias, Aldeia dos
 * Homens-Lagarto, Ponte do Troll e Torre do Necromante Renegado.
 * <p>
 * O worldgen é quase todo por dados (gerados por tools/gen/structures_c.py): .nbt em data/isekai_mahou/structure/,
 * structure/structure_set/template_pool/processor_list em data/isekai_mahou/worldgen/, baús em
 * loot_table/chests/ e tags de bioma no merge. O Java cuida do que dados não fazem:
 * <ul>
 *   <li>{@link TrollBridgeStructure}: tipo de estrutura próprio que gira a Ponte do Troll para atravessar o rio;</li>
 *   <li>{@link RpgGlyphScrollFunction}: Pergaminhos de Glifo sorteados entre os glifos que existem;</li>
 *   <li>{@link StructureSpawns}: moradores continuam nascendo (e a Vila da Guilda é zona segura);</li>
 *   <li>{@link StructureDiscovery}: título, som e XP ao descobrir cada estrutura;</li>
 *   <li>{@link LabyrinthWard}: paredes do labirinto protegidas enquanto o Minotauro vive;</li>
 *   <li>{@link StructureMapTrades}: mapas de explorador da Guilda e do Labirinto.</li>
 * </ul>
 */
public class StructuresCModule implements Module {
    @Override
    public String id() {
        return "structures_c";
    }

    @Override
    public void init(IEventBus modBus) {
        ScRegistries.register(modBus);
        StructureSpawns.register();
        StructureDiscovery.register();
        LabyrinthWard.register();
        StructureMapTrades.register();
    }
}
