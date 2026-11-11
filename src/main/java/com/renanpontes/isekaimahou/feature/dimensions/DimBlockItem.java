package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeaconBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * Item de bloco com dicas de uso. Os portais, o farol, a fenda e o altar têm regras que o jogador não adivinha
 * (item-chave, Círculo, meio-dia...): a dica explica onde cada um é encontrado (v0.9: não há mais receita — os
 * portais esperam adormecidos em estruturas do Mundo Normal, que a rota principal revela) e como ativar.
 * As linhas são montadas na hora (os nomes dos itens/chefes vêm dos registros, que só existem depois do boot).
 */
public class DimBlockItem extends BlockItem {
    private final Supplier<List<Component>> lines;

    public DimBlockItem(Block block, Properties properties, Supplier<List<Component>> lines) {
        super(block, properties);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Cinza por padrão; linhas que já têm cor própria (a dica da rota) ficam como estão.
        for (Component line : lines.get()) {
            tooltip.add(line.getStyle().getColor() == null ? line.copy().withStyle(ChatFormatting.GRAY) : line);
        }
    }

    // ------------------------------------------------------------------ Textos de cada bloco
    private static Component leadsTo(DimKind dim) {
        return Component.translatable("tooltip.isekai_mahou.dimensions.leads_to", dim.displayName());
    }

    /** "Encontrado em: Portão Elemental" + a dica de que a rota principal revela onde fica. */
    private static List<Component> foundIn(DimKind dim) {
        return List.of(Component.translatable("tooltip.isekai_mahou.dimensions.found_in", dim.siteName().withColor(dim.color)),
                Component.translatable("tooltip.isekai_mahou.dimensions.route_hint").withStyle(ChatFormatting.DARK_GRAY,
                        ChatFormatting.ITALIC));
    }

    static List<Component> frame(PortalKind kind) {
        Component activate = kind.manaCost > 0
                ? Component.translatable("tooltip.isekai_mahou.dimensions.frame_mana", kind.key().getDescription(), kind.minCircle, kind.manaCost)
                : Component.translatable("tooltip.isekai_mahou.dimensions.frame_offer", kind.key().getDescription(), kind.minCircle);
        List<Component> out = new ArrayList<>();
        out.add(leadsTo(kind.dim));
        out.addAll(foundIn(kind.dim));
        out.add(activate);
        out.add(Component.translatable("tooltip.isekai_mahou.dimensions.frame_shape"));
        return out;
    }

    static List<Component> beacon() {
        List<Component> out = new ArrayList<>();
        out.add(leadsTo(DimKind.CELESTIAL));
        out.addAll(foundIn(DimKind.CELESTIAL));
        out.add(Component.translatable("tooltip.isekai_mahou.dimensions.beacon_noon",
                Convergence.essence(Element.LIGHT).getDescription(), CelestialBeaconBlock.MIN_CIRCLE));
        out.add(Component.translatable("tooltip.isekai_mahou.dimensions.beacon_place"));
        return out;
    }

    static List<Component> rift() {
        List<Component> out = new ArrayList<>();
        out.add(leadsTo(DimKind.TWILIGHT));
        out.addAll(foundIn(DimKind.TWILIGHT));
        out.addAll(List.of(
                Component.translatable("tooltip.isekai_mahou.dimensions.rift_req", DimTravel.TWILIGHT_MIN_CIRCLE),
                Component.translatable("tooltip.isekai_mahou.dimensions.bullet", DimTravel.bossName(DimTravel.BOSS_ABYSS)),
                Component.translatable("tooltip.isekai_mahou.dimensions.bullet", DimTravel.bossName(DimTravel.BOSS_SERAPH))));
        return out;
    }

    static List<Component> altar() {
        return List.of(Component.translatable("tooltip.isekai_mahou.dimensions.altar_use"),
                Component.translatable("tooltip.isekai_mahou.dimensions.altar_place", DimKind.ELEMENTAL.displayName()));
    }
}
