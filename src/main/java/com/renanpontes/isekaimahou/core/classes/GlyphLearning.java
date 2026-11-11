package com.renanpontes.isekaimahou.core.classes;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.Glyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Regras de APRENDER glifos por item (v1.0, docs/V10_DESIGN.md A1.6):
 * <ul>
 *   <li>Pergaminho de Glifo: gasta 1 essência do elemento do glifo (Arcano = essência arcana; fusão = de um dos pais, o
 *       que o jogador tiver mais). No criativo é de graça.</li>
 *   <li>Glifo de ORIGEM de outra classe ({@link Glyph#origin()} ≠ classe do jogador) não sai de pergaminho: só com uma
 *       Lição escrita por um jogador da classe ({@code technique_lesson}).</li>
 * </ul>
 * Usado pelo {@code GlyphScrollItem} e pela Lição; módulos podem usar para itens próprios de aprendizado.
 */
public final class GlyphLearning {

    /** Elementos cuja essência paga o glifo (Arcano → [Arcano]; base → [ele]; fusão → [pai A, pai B]). */
    public static List<Element> essenceOptions(Glyph g) {
        Element e = g == null || g.element() == null ? Element.ARCANE : g.element();
        if (e.isFusion()) return List.of(e.parentA, e.parentB);
        return List.of(e);
    }

    /** Item da essência do elemento (arcano ou base); null para fusões. */
    @Nullable
    public static Item essenceItem(Element e) {
        DeferredItem<Item> holder = CoreRegistries.ESSENCES.get(e);
        return holder == null ? null : holder.get();
    }

    /** Quantas essências do elemento o jogador carrega. */
    public static int count(Player p, Element e) {
        Item item = essenceItem(e);
        return item == null ? 0 : p.getInventory().countItem(item);
    }

    /**
     * Essência que vai pagar {@code amount} unidades: a opção com MAIS unidades no inventário (se alguma tiver o
     * suficiente). Null se nenhuma basta.
     */
    @Nullable
    public static Element pickEssence(Player p, Glyph g, int amount) {
        Element best = null;
        int bestCount = -1;
        for (Element e : essenceOptions(g)) {
            int c = count(p, e);
            if (c >= amount && c > bestCount) {
                best = e;
                bestCount = c;
            }
        }
        return best;
    }

    /** Tira {@code amount} essências do elemento do inventário (o chamador já conferiu que tem). */
    public static void consume(Player p, Element e, int amount) {
        Item item = essenceItem(e);
        if (item == null || amount <= 0) return;
        p.getInventory().clearOrCountMatchingItems(s -> s.is(item), amount, p.inventoryMenu.getCraftSlots());
        p.inventoryMenu.broadcastChanges();
    }

    /** "1× Essência de Fogo" / "2× Essência de Água ou Essência de Vento" (na cor do elemento). */
    public static MutableComponent costText(Glyph g, int amount) {
        MutableComponent out = Component.literal(amount + "× ");
        List<Element> opts = essenceOptions(g);
        for (int i = 0; i < opts.size(); i++) {
            if (i > 0) out.append(Component.translatable("message.isekai_mahou.classes.or"));
            Element e = opts.get(i);
            Item item = essenceItem(e);
            Component name = item == null ? e.displayName() : new ItemStack(item).getHoverName();
            out.append(Component.empty().append(name).withColor(e.color));
        }
        return out;
    }

    /** Glifo de origem de OUTRA classe (só se aprende com Lição)? */
    public static boolean isForeignOrigin(@Nullable Glyph g, MagicPath path) {
        return g != null && g.origin() != null && g.origin() != MagicPath.NONE && g.origin() != path;
    }

    /** Sem custo (criativo). */
    public static boolean free(Player p) {
        return p.getAbilities().instabuild;
    }

    /** Aviso de falha na barra de ação + som de "não". */
    public static void deny(ServerPlayer p, Component msg) {
        p.displayClientMessage(Component.empty().append(msg).withStyle(ChatFormatting.RED), true);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    /** Mensagem "falta essência" com o custo e o que o jogador tem. */
    public static Component missingEssence(Player p, Glyph g, int amount) {
        int have = 0;
        for (Element e : essenceOptions(g)) have = Math.max(have, count(p, e));
        return Component.translatable("message.isekai_mahou.classes.need_essence", costText(g, amount), g.displayName(), have);
    }

    /** O jogador é da classe dona do glifo de origem? (glifo sem origem: sempre "sim"). */
    public static boolean sameClass(Player p, Glyph g) {
        return g.origin() == null || g.origin() == MagicApi.get(p).path();
    }

    /** Ícone padrão (para toasts de aprendizado): a essência do primeiro elemento que paga o glifo. */
    public static ItemStack icon(Glyph g) {
        Item item = essenceItem(essenceOptions(g).get(0));
        return new ItemStack(item == null ? Items.PAPER : item);
    }

    private GlyphLearning() {}
}
