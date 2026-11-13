package com.renanpontes.isekaimahou.feature.espada.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Asa de Murciélago (troféu de Ulquiorra): usar faz um <b>Sonído</b> — some e reaparece até 9 blocos à frente (para
 * antes de paredes), sem dano de queda no próximo pouso. Recarga de 4 s.
 */
public class MurcielagoWingItem extends Item {
    private static final double RANGE = 9.0;

    public MurcielagoWingItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, Math.max(-0.2, Math.min(0.35, look.y)), look.z).normalize();
        Vec3 from = player.position();
        Vec3 eye = player.getEyePosition();
        var hit = level.clip(new ClipContext(eye, eye.add(flat.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double dist = hit.getType() == HitResult.Type.MISS ? RANGE : Math.max(0, hit.getLocation().distanceTo(eye) - 0.8);
        Vec3 to = from.add(flat.scale(dist));
        if (dist < 1.0 || !level.noCollision(player, player.getBoundingBox().move(to.subtract(from)))) {
            return InteractionResultHolder.fail(stack);
        }
        player.teleportTo(to.x, to.y, to.z);
        player.fallDistance = 0;
        EspadaFx.sonido(sl, player, from, to, EspadaFx.ULQ_GREEN);
        EspadaSounds.play(player, EspadaSounds.SONIDO, 0.9F, 1.1F);
        player.getCooldowns().addCooldown(this, 80);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.murcielago_wing.desc").withStyle(ChatFormatting.GREEN));
    }
}
