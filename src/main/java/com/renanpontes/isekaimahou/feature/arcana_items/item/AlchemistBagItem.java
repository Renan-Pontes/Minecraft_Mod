package com.renanpontes.isekaimahou.feature.arcana_items.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaContent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * <b>Bolsa do Alquimista</b> — "só pra algumas classes como bruxas e mágicas". 27 espaços só para essências, cristais e
 * reagentes (tag {@code isekai_mahou:arcana_items_reagents}); só as classes Mágicas conseguem abri-la. Com a coleta
 * ligada (agachado + botão direito troca), os reagentes que você apanha do chão vão direto para a bolsa.
 * O conteúdo fica no próprio item (componente de contêiner do vanilla), então a bolsa pode ser guardada num baú cheia.
 */
public class AlchemistBagItem extends Item {
    public static final int SLOTS = 27;
    /** O que cabe na bolsa (editável por datapack). */
    public static final TagKey<Item> REAGENTS = TagKey.create(Registries.ITEM, IsekaiMahou.id("arcana_items_reagents"));

    public AlchemistBagItem(Properties props) {
        super(props);
    }

    /** Só as classes Mágicas (Mago, Bruxa, Bruxo, Druida, Necromante) usam a bolsa. */
    public static boolean canUse(MagicPath path) {
        return path != MagicPath.NONE && path.category() == MagicPath.Category.MAGIC;
    }

    /** O item cabe na bolsa? Essências de qualquer módulo entram pelo nome, o resto pela tag. */
    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        if (stack.is(REAGENTS)) return true;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return IsekaiMahou.MODID.equals(id.getNamespace()) && id.getPath().startsWith("essence_");
    }

    public static boolean collecting(ItemStack bag) {
        return !Boolean.FALSE.equals(bag.get(ArcanaContent.BAG_COLLECT.get()));
    }

    public static NonNullList<ItemStack> load(ItemStack bag) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ItemContainerContents c = bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        // copyInto exige uma lista do mesmo tamanho ou maior: uma bolsa "estrangeira" maior é cortada com segurança
        if (c.getSlots() <= SLOTS) c.copyInto(items);
        else for (int i = 0; i < SLOTS; i++) items.set(i, c.getStackInSlot(i).copy());
        return items;
    }

    public static void save(ItemStack bag, List<ItemStack> items) {
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    public static int used(ItemStack bag) {
        int n = 0;
        for (ItemStack s : bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) n++;
        return n;
    }

    /**
     * Guarda o máximo possível de {@code incoming} na bolsa (junta em pilhas iguais, depois ocupa espaços vazios).
     * {@code incoming} encolhe; devolve quantos itens entraram.
     */
    public static int insert(ItemStack bag, ItemStack incoming) {
        if (!accepts(incoming)) return 0;
        NonNullList<ItemStack> items = load(bag);
        int start = incoming.getCount();
        for (int i = 0; i < SLOTS && !incoming.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, incoming)) continue;
            int move = Math.min(incoming.getCount(), s.getMaxStackSize() - s.getCount());
            if (move <= 0) continue;
            s.grow(move);
            incoming.shrink(move);
        }
        for (int i = 0; i < SLOTS && !incoming.isEmpty(); i++) {
            if (!items.get(i).isEmpty()) continue;
            items.set(i, incoming.split(incoming.getMaxStackSize()));
        }
        int moved = start - incoming.getCount();
        if (moved > 0) save(bag, items);
        return moved;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (!canUse(MagicApi.get(sp).path())) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.bag_class").withStyle(ChatFormatting.RED), true);
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
            return InteractionResultHolder.fail(stack);
        }
        if (sp.isShiftKeyDown()) {
            boolean on = !collecting(stack);
            stack.set(ArcanaContent.BAG_COLLECT.get(), on);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.bag_collect_" + (on ? "on" : "off"))
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
            sp.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.7F, on ? 1.3F : 0.8F);
            return InteractionResultHolder.consume(stack);
        }
        int slot = hand == InteractionHand.OFF_HAND ? 40 : sp.getInventory().selected;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new AlchemistBagMenu(id, inv, sp, slot), stack.getHoverName()));
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.7F, 1.1F);
        Hints.show(sp, "arcana_items.alchemist_bag");
        return InteractionResultHolder.consume(stack);
    }

    /** Nada de bolsa dentro de Shulker/sacola (evita "inventário dentro de inventário"). */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.bag.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.bag.used", used(stack), SLOTS).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.bag.collect_" + (collecting(stack) ? "on" : "off"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
