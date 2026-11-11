package com.renanpontes.isekaimahou.feature.farm.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** As fazendas mágicas do módulo. Todas guardam mana numa reserva interna e enchem pelas Baterias de Mana em volta. */
public enum MachineKind {
    /** Gerador Mágico de Pedra/Minério: pedra sem catalisador; minérios com uma essência no espaço de catalisador. */
    STONE_GENERATOR("arcane_stone_generator", 0xE0703A),
    /** Árvore Acelerada: uma muda (ou semente) que cresce e é colhida sozinha, de novo e de novo. */
    ACCELERATED_TREE("accelerated_tree", 0x6BCB5A),
    /** Criadouro Arcano: guarde um animal (traga na guia) e alimente — ele "produz" o que daria. */
    HATCHERY("arcane_hatchery", 0xF2D06B),
    /** Lago de Peixes mágico: pesca sozinho (uma vara no espaço melhora a sorte e a velocidade). */
    FISH_POND("magic_fish_pond", 0x4FA8E8),
    /** Pedestal Elemental (roubado de um Santuário com Toque Suave): gera Essência do elemento com o tempo e mana. */
    ESSENCE("elemental_pedestal", 0xB896FF);

    private static final String BLOCK_KEY = "block.isekai_mahou.";
    private static final String KEY = "farm.isekai_mahou.machine.";

    public final String id;
    public final int rgb;

    MachineKind(String id, int rgb) {
        this.id = id;
        this.rgb = rgb;
    }

    /** Reserva interna de mana. */
    public int capacity() {
        return this == ESSENCE ? 6000 : 4000;
    }

    public MutableComponent displayName() {
        return Component.translatable(BLOCK_KEY + id);
    }

    public MutableComponent hint() {
        return Component.translatable(KEY + id + ".hint");
    }

    /** Dica do espaço de entrada (o que colocar). */
    public MutableComponent inputHint() {
        return Component.translatable(KEY + id + ".input");
    }

    public BlockBehaviour.Properties properties() {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never);
        return switch (this) {
            case STONE_GENERATOR -> p.mapColor(MapColor.DEEPSLATE).strength(3.5F, 9.0F).sound(SoundType.DEEPSLATE_BRICKS)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(FarmMachineBlock.ACTIVE) ? 9 : 2);
            case ACCELERATED_TREE -> p.mapColor(MapColor.WOOD).strength(2.0F, 4.0F).sound(SoundType.WOOD).noOcclusion()
                    .lightLevel(s -> s.getValue(FarmMachineBlock.ACTIVE) ? 7 : 0);
            case HATCHERY -> p.mapColor(MapColor.COLOR_YELLOW).strength(2.0F, 4.0F).sound(SoundType.WOOD).noOcclusion()
                    .lightLevel(s -> s.getValue(FarmMachineBlock.ACTIVE) ? 6 : 0);
            case FISH_POND -> p.mapColor(MapColor.WATER).strength(3.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> s.getValue(FarmMachineBlock.ACTIVE) ? 8 : 3);
            case ESSENCE -> p.mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 1200.0F).sound(SoundType.DEEPSLATE_BRICKS)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(FarmMachineBlock.ACTIVE) ? 12 : 7);
        };
    }

    public static MachineKind byIndex(int i) {
        MachineKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : STONE_GENERATOR;
    }
}
