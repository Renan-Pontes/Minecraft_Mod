package com.renanpontes.isekaimahou.feature.espada.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Statuses;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Garra da Pantera (troféu de Grimmjow): usar solta um <b>Desgarrón</b> pequeno — cinco garras azuis à frente
 * (8 blocos), dano e sangramento. Recarga de 8 s.
 */
public class PanteraClawItem extends Item {
    private static final double RANGE = 8.0;

    public PanteraClawItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 base = player.position().add(0, 1.0, 0);
        for (int i = 0; i < 5; i++) {
            Vec3 from = base.add(side.scale((i - 2) * 0.7));
            EspadaFx.claws(sl, player, from, dir.scale(RANGE), (float) RANGE, i, EspadaFx.GRIMM_BLUE);
        }
        EspadaSounds.play(player, EspadaSounds.CLAW, 1.0F, 1.1F);
        final Vec3 fdir = dir;
        AABB box = player.getBoundingBox().expandTowards(dir.scale(RANGE)).inflate(2.0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3 d = e.position().subtract(player.position());
            double along = d.dot(fdir);
            double lateral = Math.abs(d.dot(side));
            if (along < 0 || along > RANGE || lateral > 2.2) continue;
            if (e instanceof Player other && !player.canHarmPlayer(other)) continue;
            if (e.hurt(sl.damageSources().playerAttack(player), 8F)) Statuses.apply(e, Statuses.Kind.BLEEDING, 2, 100, player, 1F);
        }
        player.getCooldowns().addCooldown(this, 160);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.pantera_claw.desc").withStyle(ChatFormatting.AQUA));
    }
}
