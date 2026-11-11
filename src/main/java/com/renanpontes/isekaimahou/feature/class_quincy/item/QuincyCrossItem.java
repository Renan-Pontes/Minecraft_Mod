package com.renanpontes.isekaimahou.feature.class_quincy.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Cruz Quincy (arma do kit, graus I–IV): o pingente vira um Arco Espiritual ao puxar. As flechas são de Reishi — não
 * gastam munição nem recurso; o dano depende do grau, do quanto foi puxado e do rank do Quincy (outras classes atiram
 * com 60%). Agachado e com ≥ 5 de Reishi guardado, o disparo carregado vira uma rajada de 5 flechas (gasta 5).
 * Segurar a Cruz junta Reishi do ambiente (ver QuincyEvents) e conta como "mirando" para o Foco.
 */
public class QuincyCrossItem extends BowItem {
    public static final int BURST_COST = 5;
    private final int tier;

    public QuincyCrossItem(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** Dano base da flecha com o arco totalmente puxado (antes da inflação e do rank). */
    public float baseDamage() {
        return switch (tier) {
            case 2 -> 5.0F;
            case 3 -> 6.5F;
            case 4 -> 8.0F;
            default -> 4.0F;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // sem munição: o arco é de energia
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(user instanceof Player player)) return;
        float charge = getPowerForTime(getUseDuration(stack, user) - timeLeft);
        if (charge < 0.1F) return;
        if (!(level instanceof ServerLevel sl)) return;
        MagicData d = MagicApi.get(player);
        boolean quincy = Quincy.isQuincy(player);
        float dmg = baseDamage() * charge * (quincy ? Quincy.rankPower(d) : 0.6F);
        boolean burst = quincy && charge >= 1.0F && player.isShiftKeyDown() && Quincy.reishi(d) >= BURST_COST
                && player instanceof ServerPlayer;
        int arrows = burst ? 5 : 1;
        if (burst) {
            ServerPlayer sp = (ServerPlayer) player;
            com.renanpontes.isekaimahou.core.MagicApi.addCounter(sp, Quincy.REISHI, -BURST_COST);
            sp.displayClientMessage(Quincy.msg("burst").withColor(Quincy.REISHI_RGB), true);
        }
        for (int i = 0; i < arrows; i++) {
            SpiritArrowEntity a = SpiritArrowEntity.plain(sl, player, dmg);
            float yawOff = arrows == 1 ? 0 : (i - (arrows - 1) / 2.0F) * 7.0F;
            a.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOff, 0.0F, 3.2F * charge, 0.6F);
            if (charge >= 1.0F && quincy && Quincy.rank(d) >= 2) a.pierce(1);
            sl.addFreshEntity(a);
        }
        QuincyFx.bowFlash(sl, player);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, player.getSoundSource(), 0.8F,
                1.5F + charge * 0.3F);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 0.6F,
                1.4F + level.random.nextFloat() * 0.3F);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_cross").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_cross.tier", Quincy.roman(tier),
                com.renanpontes.isekaimahou.feature.class_quincy.QuincyArmor.fmt(baseDamage() * com.renanpontes.isekaimahou.glyph.SpellCost.damageScale()))
                .withColor(Quincy.RGB));
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_cross.burst", BURST_COST).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_cross.gather").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_cross.auswahlen").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_outfit.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
