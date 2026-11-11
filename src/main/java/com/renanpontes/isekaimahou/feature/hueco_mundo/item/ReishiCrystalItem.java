package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Cristal de Reishi: partículas espirituais condensadas no Mundo Oco. Restaura 30% do recurso da classe (mana, vigor ou
 * foco); para as classes espirituais (Shinigami e Quincy, que vivem de reishi) restaura 50% e dá Força I por 20 s.
 */
public class ReishiCrystalItem extends Item {
    private static final int COOLDOWN = 600;

    public ReishiCrystalItem(Properties props) {
        super(props);
    }

    public static boolean isSpiritual(MagicPath p) {
        return p == MagicPath.SHINIGAMI || p == MagicPath.QUINCY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        MagicData d = MagicApi.get(sp);
        boolean spiritual = isSpiritual(d.path());
        float frac = spiritual ? 0.5F : 0.3F;
        d.addMana(d.maxMana() * frac);
        d.markDirty();
        if (spiritual) sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(HmFx.dust(HmFx.REISHI, 1.2F), sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.5, 0.8, 0.5, 0);
        sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        sp.displayClientMessage(Component.translatable(spiritual ? "message.isekai_mahou.hueco_mundo.reishi_spiritual"
                : "message.isekai_mahou.hueco_mundo.reishi_other").withColor(HmFx.REISHI), true);
        stack.consume(1, sp);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.reishi_crystal.desc").withStyle(ChatFormatting.GRAY));
    }
}
