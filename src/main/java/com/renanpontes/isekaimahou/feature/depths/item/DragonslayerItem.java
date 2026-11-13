package com.renanpontes.isekaimahou.feature.depths.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.depths.DepthsEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Matadora de Dragões (Berserk): "grande demais para ser chamada de espada". Lenta, pesada (anda 10% mais devagar
 * com ela na mão), alcance +1 e dano 14. Todo golpe cheio corta em ARCO largo ({@code DepthsEvents}); +50% de dano em
 * chefes e criaturas enormes.
 * <p>Usar (segurar 1 s e soltar): <b>Corte do Espadachim Negro</b> — arco de 200° com 6 blocos de raio, 16 de dano e
 * empurrão. Recarga 10 s.</p>
 */
public class DragonslayerItem extends SwordItem {
    public static final int COOLDOWN = 200;
    public static final int CHARGE_TICKS = 20;
    public static final float HUGE_BONUS = 1.5F;
    private static final double SLASH_RANGE = 6.0;

    public DragonslayerItem(Tier tier, Properties props) {
        super(tier, props);
    }

    public static ItemAttributeModifiers createDragonslayerAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 13.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.3, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(IsekaiMahou.id("depths_dragonslayer_reach"), 1.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED, new AttributeModifier(IsekaiMahou.id("depths_dragonslayer_weight"), -0.1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack, entity) - remaining;
        if (level instanceof ServerLevel sl && used == CHARGE_TICKS) {
            sl.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
            sl.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 2.2, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        int used = getUseDuration(stack, entity) - timeLeft;
        if (used < CHARGE_TICKS) return;
        player.swing(InteractionHand.MAIN_HAND);
        if (level instanceof ServerLevel sl) {
            DepthsEvents.withoutHitBonus(() -> blackSwordsmanSlash(sl, player));
            stack.hurtAndBreak(2, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
    }

    /** Corte do Espadachim Negro: arco de 200° na frente, dano fixo alto + empurrão; mais forte contra gigantes. */
    private static void blackSwordsmanSlash(ServerLevel sl, Player player) {
        Vec3 c = player.position();
        for (Entity e : sl.getEntities(player, player.getBoundingBox().inflate(SLASH_RANGE, 2.0, SLASH_RANGE))) {
            if (!DepthsFoes.isFoe(player, e) || !DepthsFoes.inArc(player, e, 100.0)) continue;
            if (e.distanceTo(player) > SLASH_RANGE + e.getBbWidth() * 0.5) continue;
            LivingEntity le = (LivingEntity) e;
            float dmg = 16.0F * (DepthsFoes.isHuge(le) ? HUGE_BONUS : 1.0F);
            if (le.hurt(player.damageSources().playerAttack(player), dmg)) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) push = push.normalize();
                le.knockback(1.2, -push.x, -push.z);
            }
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i <= 20; i++) {
            double a = base + Math.toRadians(-100 + i * 10);
            for (double r = 2.0; r <= SLASH_RANGE; r += 2.0) {
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * r, c.y + 1.0, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        BossKit.shake(sl, c, 12, 0.7F, 8);
        sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.5F);
        sl.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        DepthsLoreItem.lore(stack, lines);
        lines.add(Component.translatable("item.isekai_mahou.depths_dragonslayer.passive").withStyle(ChatFormatting.YELLOW));
    }
}
