package com.renanpontes.isekaimahou.feature.class_rogue.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueData;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueNet;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueStolenBook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Grimório Roubado: o livro onde ficam as magias que o Ladino copiou (glifo Roubar Magia). Usar = conjura a página
 * escolhida (metade do mana original, 1 carga); agachado + usar = próxima página. A dica mostra as páginas e cargas.
 * As páginas ficam com o JOGADOR (não com o livro): perder o livro não perde as magias, e a forma Página Roubada
 * conjura sem ele.
 */
public class StolenGrimoireItem extends Item {
    public StolenGrimoireItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            List<RogueData.Page> pages = RogueData.pages(sp);
            if (pages.isEmpty()) {
                RogueFx.actionbar(sp, Component.translatable("message.isekai_mahou.class_rogue.book.empty"), 0xA89EC0);
                return InteractionResultHolder.fail(stack);
            }
            int next = (RogueData.selected(sp) + 1) % pages.size();
            RogueData.select(sp, next);
            RogueNet.sync(sp);
            RogueData.Page pg = pages.get(next);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.book.page", next + 1, pages.size(),
                    pg.displayName(), pg.charges()).withColor(RogueFx.PURPLE), true);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
            return InteractionResultHolder.success(stack);
        }
        if (RogueStolenBook.castSelected(sp, 1.0F)) {
            sp.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.rogue_stolen_grimoire.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        RogueNet.Sync view = RogueNet.clientView;
        if (view.pages().isEmpty()) {
            tooltip.add(Component.translatable("item.isekai_mahou.rogue_stolen_grimoire.no_pages").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (int i = 0; i < view.pages().size(); i++) {
                RogueNet.PageView p = view.pages().get(i);
                Element e = Element.byId(p.element());
                Component line = Component.translatable("item.isekai_mahou.rogue_stolen_grimoire.page", p.name(), p.charges(),
                        e == null ? Element.ARCANE.displayName() : e.displayName(), p.from()).withColor(p.color());
                tooltip.add(i == view.selected() ? Component.literal("▶ ").withStyle(ChatFormatting.GOLD).append(line)
                        : Component.literal("  ").append(line));
            }
        }
        tooltip.add(Component.translatable("item.isekai_mahou.rogue_stolen_grimoire.use").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !RogueNet.clientView.pages().isEmpty();
    }
}
