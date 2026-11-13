package com.renanpontes.isekaimahou.feature.espada.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fragmento da Arrogante (troféu de Barragan): um sopro curto de <b>Respira</b> num cone de 7 blocos — criaturas ficam
 * Envelhecidas (chefes resistem) e projéteis no caminho viram pó. Recarga de 15 s.
 */
public class ArroganteShardItem extends Item {
    public ArroganteShardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        Vec3 dir = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        // Sopro curto (instantâneo): 6 bolos rápidos que somem em 1 s.
        EspadaFx.respiraCloud(sl, player, eye.subtract(0, 0.3, 0), dir, 50F, 0.3, 22, 6, false);
        EspadaSounds.play(player, EspadaSounds.RESPIRA, 0.9F, 1.2F);
        AABB box = player.getBoundingBox().inflate(7.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && inCone(eye, dir, e.position().add(0, e.getBbHeight() * 0.5, 0)))) {
            if (e instanceof Player other && !player.canHarmPlayer(other)) continue;
            Aging.apply(e, BossKit.isBossLike(e) ? 1 : 3, player);
            e.hurt(sl.damageSources().indirectMagic(player, player), 3F);
        }
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != player && inCone(eye, dir, p.position()))) {
            EspadaFx.crumble(sl, p.position());
            p.discard();
        }
        player.getCooldowns().addCooldown(this, 300);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }

    private static boolean inCone(Vec3 eye, Vec3 dir, Vec3 p) {
        Vec3 d = p.subtract(eye);
        double len = d.length();
        return len < 7.5 && len > 0.1 && d.normalize().dot(dir) > Math.cos(Math.toRadians(28));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.arrogante_shard.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
