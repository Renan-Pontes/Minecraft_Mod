package com.renanpontes.isekaimahou.feature.towers.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.towers.TwRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Lâminas Gêmeas (SAO): Noturna e Celeste. Sozinha é uma boa espada (≈ diamante, um pouco mais rápida). Com uma em cada
 * mão, "Usar" solta a <b>Torrente Estelar</b> ({@link TwinCombo}): 16 golpes seguidos, recarga de 30 s nas duas.
 */
public class TwinBladeItem extends SwordItem {
    public static final int COOLDOWN = 600;
    private final int color;

    public TwinBladeItem(Tier tier, int color, Properties props) {
        super(tier, props);
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** Uma em cada mão, e de cores diferentes. */
    public static boolean isPaired(Player p) {
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        return main.getItem() instanceof TwinBladeItem && off.getItem() instanceof TwinBladeItem && main.getItem() != off.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isPaired(player)) {
            if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.tower_twin_blade.need_pair")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this) || TwinCombo.isActive(player)) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer sp) {
            TwinCombo.start(sp);
            sp.getCooldowns().addCooldown(TwRegistries.TWIN_NOIR.get(), COOLDOWN);
            sp.getCooldowns().addCooldown(TwRegistries.TWIN_AZURE.get(), COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.tower_twin_blade.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.isekai_mahou.tower_twin_blade.use").withColor(color));
    }
}
