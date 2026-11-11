package com.renanpontes.isekaimahou.feature.farm.blueprint;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As Plantas de Casa: cada uma é um molde ({@link Template}) que o Canteiro de Obras levanta sozinho, bloco a bloco,
 * com os blocos dos baús em volta. Os moldes são feitos por código (fáceis de ler e de ajustar).
 */
public enum BlueprintKind {
    SMALL_HOUSE("small_house", 0xE8B26A, BlueprintKind::smallHouse),
    TOWER("tower", 0xB8C4D8, BlueprintKind::tower),
    BARN("barn", 0xD9634A, BlueprintKind::barn),
    BRIDGE("bridge", 0x9C7A4E, BlueprintKind::bridge),
    FARM_PLOT("farm_plot", 0x8FD46A, BlueprintKind::farmPlot);

    private static final String KEY = "farm.isekai_mahou.blueprint.";

    public final String id;
    public final int rgb;
    private final Supplier<Template> template;

    BlueprintKind(String id, int rgb, Supplier<Template> factory) {
        this.id = id;
        this.rgb = rgb;
        this.template = Suppliers.memoize(factory::get);
    }

    public String itemId() {
        return "blueprint_" + id;
    }

    public Template template() {
        return template.get();
    }

    public MutableComponent displayName() {
        return Component.translatable(KEY + id);
    }

    public MutableComponent description() {
        return Component.translatable(KEY + id + ".desc");
    }

    public static BlueprintKind byIndex(int i) {
        BlueprintKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : SMALL_HOUSE;
    }

    // =====================================================================================
    //  Moldes (fachada em z máximo; porta virada para dentro = NORTH local)
    // =====================================================================================

    /** Casa pequena 7×7: chão de pedregulho, toras nos cantos, telhado de duas águas, baú, bancada e fornalha. */
    private static Template smallHouse() {
        Template.Builder b = new Template.Builder(7, 7);
        b.box(0, 0, 0, 6, 0, 6, Mat.COBBLE).box(1, 0, 1, 5, 0, 5, Mat.PLANKS);
        b.ring(0, 1, 0, 6, 3, 6, Mat.PLANKS);
        for (int y = 1; y <= 3; y++) {
            b.log(0, y, 0, Axis.Y).log(6, y, 0, Axis.Y).log(0, y, 6, Axis.Y).log(6, y, 6, Axis.Y);
        }
        for (int z = 0; z <= 6; z++) b.log(0, 4, z, Axis.Z).log(6, 4, z, Axis.Z);
        b.gableRoof(-1, 7, 4, -1, 7, Mat.PLANKS);
        // Janelas
        b.put(0, 2, 2, Mat.GLASS_PANE).put(0, 2, 4, Mat.GLASS_PANE).put(6, 2, 2, Mat.GLASS_PANE).put(6, 2, 4, Mat.GLASS_PANE);
        b.put(3, 2, 0, Mat.GLASS_PANE).put(1, 2, 6, Mat.GLASS_PANE).put(5, 2, 6, Mat.GLASS_PANE);
        b.put(3, 5, 6, Mat.GLASS_PANE).put(3, 5, 0, Mat.GLASS_PANE);
        // Porta (a metade de cima é posta junto com a de baixo)
        b.put(3, 1, 6, Mat.DOOR, Direction.NORTH).none(3, 2, 6);
        b.put(3, 0, 7, Mat.COBBLE);
        b.clear(1, 1, 1, 5, 3, 5);
        // Móveis e luz
        b.put(1, 1, 1, Mat.CHEST, Direction.SOUTH).put(2, 1, 1, Mat.CRAFTING).put(5, 1, 1, Mat.FURNACE, Direction.SOUTH);
        b.put(1, 3, 3, Mat.TORCH, Direction.EAST).put(5, 3, 3, Mat.TORCH, Direction.WEST);
        b.put(2, 2, 7, Mat.TORCH, Direction.SOUTH).put(4, 2, 7, Mat.TORCH, Direction.SOUTH);
        return b.build();
    }

    /** Torre de vigia 5×5 com 16 de altura: escada de mão, dois andares, ameias e tochas no topo. */
    private static Template tower() {
        Template.Builder b = new Template.Builder(5, 5);
        b.box(0, 0, 0, 4, 0, 4, Mat.COBBLE);
        b.ring(0, 1, 0, 4, 13, 4, Mat.STONE_BRICKS);
        for (int y = 1; y <= 13; y++) {
            b.put(0, y, 0, Mat.COBBLE).put(4, y, 0, Mat.COBBLE).put(0, y, 4, Mat.COBBLE).put(4, y, 4, Mat.COBBLE);
        }
        for (int y : new int[]{5, 9, 13}) b.box(1, y, 1, 3, y, 3, Mat.PLANKS);
        for (int y = 1; y <= 13; y++) b.put(2, y, 1, Mat.LADDER, Direction.SOUTH);
        // Seteiras
        for (int y : new int[]{3, 7, 11}) b.put(0, y, 2, Mat.AIR).put(4, y, 2, Mat.AIR);
        b.put(2, 7, 4, Mat.AIR).put(2, 11, 4, Mat.AIR);
        // Ameias
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
            boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
            if (edge && (x + z) % 2 == 0) b.put(x, 14, z, Mat.STONE_BRICKS);
        }
        b.put(0, 15, 0, Mat.TORCH).put(4, 15, 0, Mat.TORCH).put(0, 15, 4, Mat.TORCH).put(4, 15, 4, Mat.TORCH);
        b.put(2, 1, 4, Mat.DOOR, Direction.NORTH).none(2, 2, 4);
        b.put(2, 0, 5, Mat.COBBLE);
        b.clear(1, 1, 1, 3, 12, 3);
        b.put(1, 3, 3, Mat.TORCH, Direction.EAST).put(3, 7, 3, Mat.TORCH, Direction.WEST).put(1, 11, 3, Mat.TORCH, Direction.EAST);
        return b.build();
    }

    /** Celeiro 9×11: estrutura de toras, portão triplo, baias de cerca, feno, lanternas penduradas na viga central. */
    private static Template barn() {
        Template.Builder b = new Template.Builder(9, 11);
        b.box(0, 0, 0, 8, 0, 10, Mat.COBBLE).box(1, 0, 1, 7, 0, 9, Mat.PLANKS);
        b.ring(0, 1, 0, 8, 4, 10, Mat.PLANKS);
        for (int y = 1; y <= 4; y++) {
            for (int z : new int[]{0, 5, 10}) b.log(0, y, z, Axis.Y).log(8, y, z, Axis.Y);
            b.log(4, y, 0, Axis.Y);
        }
        for (int z = 0; z <= 10; z++) b.log(0, 5, z, Axis.Z).log(8, 5, z, Axis.Z);
        for (int z = 1; z <= 9; z++) b.log(4, 5, z, Axis.Z);
        b.gableRoof(-1, 9, 5, -1, 11, Mat.PLANKS);
        // Portão triplo
        for (int x = 3; x <= 5; x++) {
            b.put(x, 1, 10, Mat.GATE, Direction.NORTH).put(x, 2, 10, Mat.AIR).put(x, 3, 10, Mat.AIR);
            b.put(x, 0, 11, Mat.COBBLE);
        }
        for (int z : new int[]{2, 3, 7, 8}) b.put(0, 3, z, Mat.GLASS_PANE).put(8, 3, z, Mat.GLASS_PANE);
        b.put(4, 7, 10, Mat.GLASS_PANE).put(4, 7, 0, Mat.GLASS_PANE);
        b.clear(1, 1, 1, 7, 4, 9);
        // Baias e feno
        for (int z : new int[]{3, 6}) {
            b.put(1, 1, z, Mat.FENCE).put(2, 1, z, Mat.FENCE).put(6, 1, z, Mat.FENCE).put(7, 1, z, Mat.FENCE);
        }
        b.put(1, 1, 1, Mat.HAY).put(2, 1, 1, Mat.HAY).put(1, 2, 1, Mat.HAY).put(7, 1, 1, Mat.HAY).put(6, 1, 1, Mat.HAY).put(7, 1, 2, Mat.HAY);
        b.put(4, 4, 3, Mat.LANTERN, null, null, 1).put(4, 4, 7, Mat.LANTERN, null, null, 1);
        b.put(2, 2, 11, Mat.TORCH, Direction.SOUTH).put(6, 2, 11, Mat.TORCH, Direction.SOUTH);
        return b.build();
    }

    /** Ponte 3×15 para a frente: vigas de tora, guarda-corpo de cerca, lanternas e pilares a cada 4 blocos. */
    private static Template bridge() {
        Template.Builder b = new Template.Builder(3, 15);
        for (int z = 0; z <= 14; z++) {
            b.log(0, 0, z, Axis.Z).put(1, 0, z, Mat.PLANKS).log(2, 0, z, Axis.Z);
            b.put(0, 1, z, Mat.FENCE).put(2, 1, z, Mat.FENCE).put(1, 1, z, Mat.AIR).put(1, 2, z, Mat.AIR);
        }
        for (int z : new int[]{0, 7, 14}) b.put(0, 2, z, Mat.LANTERN).put(2, 2, z, Mat.LANTERN);
        for (int z : new int[]{3, 7, 11}) {
            for (int y = -4; y <= -1; y++) b.log(0, y, z, Axis.Y).log(2, y, z, Axis.Y);
        }
        return b.build();
    }

    /** Horta 9×9: moldura de toras, terra arada com água no meio, cerca com portão e tochas nos cantos. */
    private static Template farmPlot() {
        Template.Builder b = new Template.Builder(9, 9);
        for (int i = 0; i <= 8; i++) {
            b.log(i, 0, 0, Axis.X).log(i, 0, 8, Axis.X).log(0, 0, i, Axis.Z).log(8, 0, i, Axis.Z);
        }
        b.box(1, 0, 1, 7, 0, 7, Mat.FARMLAND).put(4, 0, 4, Mat.WATER);
        b.ring(0, 1, 0, 8, 1, 8, Mat.FENCE).put(4, 1, 8, Mat.GATE, Direction.NORTH);
        b.clear(1, 1, 1, 7, 2, 7);
        b.put(0, 2, 0, Mat.TORCH).put(8, 2, 0, Mat.TORCH).put(0, 2, 8, Mat.TORCH).put(8, 2, 8, Mat.TORCH);
        return b.build();
    }
}
