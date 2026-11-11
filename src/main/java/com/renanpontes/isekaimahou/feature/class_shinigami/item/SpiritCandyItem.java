package com.renanpontes.isekaimahou.feature.class_shinigami.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
 * Doce Espiritual (homenagem às "balas de alma"): na hora devolve 30% do recurso (Shinigami; outras classes 15%), cura
 * um pouco e dá Velocidade I por 10 s. Recarga de 30 s. Sai dos ranks do Caminho da Zanpakutō e tem receita.
 */
public class SpiritCandyItem extends Item {
    public static final int COOLDOWN = 600;

    public SpiritCandyItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, true);
        MagicData d = MagicApi.get(sp);
        float frac = Shinigami.isShinigami(sp) ? 0.30F : 0.15F;
        ClassResources.gain(sp, d.maxMana() * frac);
        sp.heal(4.0F * com.renanpontes.isekaimahou.core.Progression.HEAL_SCALE);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        ServerLevel sl = sp.serverLevel();
        ShinigamiFx.sound(sl, sp.position(), SoundEvents.GENERIC_EAT, 0.8F, 1.4F);
        ShinigamiFx.sound(sl, sp.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
        ShinigamiFx.pillar(sl, sp.position(), 2.0, 16, ShinigamiFx.SPIRIT);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.shinigami_spirit_candy").withStyle(ChatFormatting.GRAY));
    }
}
