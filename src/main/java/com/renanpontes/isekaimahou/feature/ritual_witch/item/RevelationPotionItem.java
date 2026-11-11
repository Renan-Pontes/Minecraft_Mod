package com.renanpontes.isekaimahou.feature.ritual_witch.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchProgress;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Poção de Revelação (tiers 1..5): o "up" do ritual da Bruxa. Só pode ser bebida se o tier for exatamente
 * o rank atual + 1. Dá muito XP, sobe o rank, ensina os glifos de Maldição do tier e desperta Água (I) e Sombra (III).
 */
public class RevelationPotionItem extends Item {
    public static final int MAX_TIER = 5;
    private final int tier;

    public RevelationPotionItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** Dados do jogador no lado certo (cliente usa a cópia sincronizada, para prever igual ao servidor). */
    private static MagicData dataOf(Player player) {
        return player.level().isClientSide ? ClientMagic.data() : MagicApi.get(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int rank = WitchProgress.rank(dataOf(player));
        if (tier != rank + 1) {
            if (!level.isClientSide) {
                MutableComponent msg = tier <= rank
                        ? Component.translatable("message.isekai_mahou.witch.revelation_absorbed", WitchProgress.roman(tier))
                        : Component.translatable("message.isekai_mahou.witch.revelation_too_strong", WitchProgress.roman(rank + 1));
                player.displayClientMessage(msg.withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 48;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        if (player instanceof ServerPlayer sp) {
            // Confere de novo no servidor (o rank pode ter mudado durante o gole)
            if (WitchProgress.rank(MagicApi.get(sp)) + 1 != tier) return stack;
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            WitchProgress.reveal(sp, tier);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        entity.gameEvent(GameEvent.DRINK);
        if (!player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.tier", WitchProgress.roman(tier)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.glyphs", WitchProgress.roman(tier)).withStyle(ChatFormatting.GRAY));
        if (tier == 1) tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.awaken_water").withStyle(ChatFormatting.BLUE));
        if (tier == 3) tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.awaken_shadow").withStyle(ChatFormatting.DARK_PURPLE));
        int rank = WitchProgress.rank(ClientMagic.data());
        if (tier <= rank) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.absorbed").withStyle(ChatFormatting.DARK_GRAY));
        } else if (tier == rank + 1) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.ready").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.revelation.locked", WitchProgress.roman(tier - 1)).withStyle(ChatFormatting.RED));
        }
    }
}
