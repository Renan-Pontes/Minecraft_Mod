package com.renanpontes.isekaimahou.core.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.item.SpellScrollItem;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

/**
 * Registro de MAGIAS LENDÁRIAS pré-montadas (homenagens de anime: "Explosão!", "Vazio Roxo"...).
 * Lado comum: os módulos registram no {@code init} (roda no cliente e no servidor), então a galeria do
 * Grimório e o servidor enxergam a mesma lista. Ordem = ordem de registro.
 *
 * Nome mostrado: a tradução {@code legend.isekai_mahou.<id>} se existir; senão o nome da receita.
 */
public final class Legends {
    /** Uma lenda: id único (a-z0-9_), receita pronta, elemento primário (slot) e Círculo mínimo para usar. */
    public record Legend(String id, SpellRecipe recipe, Element element, int minCircle) {
        /** Nome traduzível ({@code legend.isekai_mahou.<id>}), com o nome da receita como reserva. */
        public MutableComponent title() {
            String fallback = recipe.name().isEmpty() ? id : recipe.name();
            return Component.translatableWithFallback("legend.isekai_mahou." + id, fallback);
        }

        /** Cor RGB da lenda (a da receita, ou a do elemento). */
        public int color() {
            return recipe.color() >= 0 ? recipe.color() : element.color;
        }
    }

    private static final Map<String, Legend> LEGENDS = new LinkedHashMap<>();

    /**
     * Registra (ou substitui, se o id repetir) uma Magia Lendária. Chame no init do módulo.
     * @return a lenda registrada
     */
    public static synchronized Legend register(String id, SpellRecipe recipe, Element element, int minCircle) {
        // erro cedo e claro no init do módulo, em vez de NPE no comando/galeria
        if (id == null || id.isEmpty() || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Id de lenda inválido: " + id);
        if (recipe == null || recipe.isEmpty()) throw new IllegalArgumentException("Lenda '" + id + "' sem receita");
        Legend legend = new Legend(id, recipe, element == null ? Element.ARCANE : element, Math.max(1, minCircle));
        if (LEGENDS.put(id, legend) != null) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' registrada de novo (substituída)", id);
        return legend;
    }

    /** Todas as lendas, na ordem de registro (cópia). */
    public static synchronized List<Legend> all() {
        return new ArrayList<>(LEGENDS.values());
    }

    @Nullable
    public static synchronized Legend get(String id) {
        return LEGENDS.get(id);
    }

    /** Pergaminho de Magia com a receita da lenda (nome dourado, raridade épica, descrição). Vazio se o id não existe. */
    public static ItemStack scroll(String id) {
        Legend legend = get(id);
        return legend == null ? ItemStack.EMPTY : scroll(legend);
    }

    public static ItemStack scroll(Legend legend) {
        ItemStack stack = SpellScrollItem.of(legend.recipe());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.isekai_mahou.spell_scroll.legend", legend.title())
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.isekai_mahou.legend.header").withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)),
                Component.translatable("tooltip.isekai_mahou.legend.element", legend.element().displayName())
                        .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
                Component.translatable("tooltip.isekai_mahou.legend.circle", legend.minCircle())
                        .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))));
        return stack;
    }

    /** Pergaminho de uma lenda sorteada (vazio se não há nenhuma registrada). */
    public static ItemStack randomScroll(RandomSource random) {
        List<Legend> list = all();
        return list.isEmpty() ? ItemStack.EMPTY : scroll(list.get(random.nextInt(list.size())));
    }

    /** Sorteia só entre lendas com {@code minCircle <= maxCircle} (cai para qualquer uma se nenhuma servir). */
    public static ItemStack randomScroll(RandomSource random, int maxCircle) {
        List<Legend> fit = all().stream().filter(l -> l.minCircle() <= maxCircle).toList();
        return fit.isEmpty() ? randomScroll(random) : scroll(fit.get(random.nextInt(fit.size())));
    }

    private Legends() {}
}
