package com.renanpontes.isekaimahou.feature.potions.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.potions.PotionForm;
import com.renanpontes.isekaimahou.feature.potions.PotionKind;
import com.renanpontes.isekaimahou.feature.potions.PotionLogic;
import com.renanpontes.isekaimahou.feature.potions.PotionTier;
import com.renanpontes.isekaimahou.feature.potions.PotionTooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
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

/** Frasco de beber (normal ou prolongado). Devolve o frasco vazio, como as poções do vanilla. */
public class MahouPotionItem extends Item {
    private final PotionKind kind;
    private final PotionForm form;
    private final PotionTier tier;

    public MahouPotionItem(PotionKind kind, PotionForm form, PotionTier tier, Properties props) {
        super(props);
        this.kind = kind;
        this.form = form;
        this.tier = tier;
    }

    public PotionKind kind() { return kind; }
    public PotionForm form() { return form; }
    public PotionTier tier() { return tier; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Frasco normal com tudo cheio = desperdício: avisa e não bebe (o prolongado ainda vale pelo fluxo longo).
        if (form == PotionForm.DRINK && PotionLogic.wasted(player, kind, tier)) {
            if (!level.isClientSide) {
                player.displayClientMessage(kind == PotionKind.HEALTH
                        ? Component.translatable("message.isekai_mahou.potions.full_health").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("message.isekai_mahou.potions.full_resource").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (player instanceof ServerPlayer sp) CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        if (!level.isClientSide) PotionLogic.apply(entity, kind, tier, form, 1.0);
        if (player != null) {
            PotionLogic.startCooldown(player, tier);
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return form.useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        PotionTooltips.add(kind, form, tier, tooltip);
    }
}
