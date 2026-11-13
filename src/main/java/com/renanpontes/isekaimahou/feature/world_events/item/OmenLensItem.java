package com.renanpontes.isekaimahou.feature.world_events.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.world_events.WeManager;
import com.renanpontes.isekaimahou.feature.world_events.WorldEventType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Lente de Presságios: olhe o céu por ela (clique direito) e ela diz, em uma linha, qual evento do mundo está
 * acontecendo, quanto falta e — se for um meteoro ou terremoto — para que lado e a que distância fica.
 * Não dá poder nenhum: é só informação.
 */
public class OmenLensItem extends Item {
    private static final String[] DIRS = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};

    public OmenLensItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.getCooldowns().addCooldown(this, 40);
        level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1F, 1.2F);
        WorldEventType type = WeManager.active(sl);
        if (type == null) {
            sp.displayClientMessage(Component.translatable("world_event.isekai_mahou.lens.calm").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(stack);
        }
        int minutes = Math.max(1, Math.round(WeManager.ticksLeft(sl) / 1200F));
        BlockPos site = WeManager.site(sl);
        Component msg;
        if (site != null) {
            double dx = site.getX() - sp.getX(), dz = site.getZ() - sp.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            // ângulo estilo Minecraft: 0 = sul, 90 = oeste
            float yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            int idx = Math.floorMod(Math.round(yaw / 45F), 8);
            msg = Component.translatable("world_event.isekai_mahou.lens.where", type.displayName(), dist,
                    Component.translatable("world_event.isekai_mahou.lens.dir." + DIRS[idx]));
        } else {
            msg = Component.translatable("world_event.isekai_mahou.lens.active", type.displayName(), minutes);
        }
        sp.displayClientMessage(msg.copy().withColor(type.color), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.world_events_omen_lens.desc").withStyle(ChatFormatting.GRAY));
    }
}
