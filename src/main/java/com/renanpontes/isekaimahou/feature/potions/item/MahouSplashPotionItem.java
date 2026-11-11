package com.renanpontes.isekaimahou.feature.potions.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.potions.PotionForm;
import com.renanpontes.isekaimahou.feature.potions.PotionKind;
import com.renanpontes.isekaimahou.feature.potions.PotionLogic;
import com.renanpontes.isekaimahou.feature.potions.PotionTier;
import com.renanpontes.isekaimahou.feature.potions.PotionTooltips;
import com.renanpontes.isekaimahou.feature.potions.entity.ThrownMahouPotion;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Frasco arremessável: respinga em área (aliados na vida; jogadores no recurso). Também sai de dispensers. */
public class MahouSplashPotionItem extends Item implements ProjectileItem {
    private final PotionKind kind;
    private final PotionTier tier;

    public MahouSplashPotionItem(PotionKind kind, PotionTier tier, Properties props) {
        super(props);
        this.kind = kind;
        this.tier = tier;
    }

    public PotionKind kind() { return kind; }
    public PotionTier tier() { return tier; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownMahouPotion thrown = new ThrownMahouPotion(level, player);
            thrown.setItem(stack.copyWithCount(1));
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(thrown);
        }
        PotionLogic.startCooldown(player, tier);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        ThrownMahouPotion thrown = new ThrownMahouPotion(level, pos.x(), pos.y(), pos.z());
        thrown.setItem(stack.copyWithCount(1));
        return thrown;
    }

    @Override
    public DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder()
                .uncertainty(DispenseConfig.DEFAULT.uncertainty() * 0.5F)
                .power(DispenseConfig.DEFAULT.power() * 1.25F)
                .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        PotionTooltips.add(kind, PotionForm.SPLASH, tier, tooltip);
    }
}
