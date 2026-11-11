package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Fragmento de Alma de Arrancar: o que sobra de um Arrancar vencido. As classes espirituais (Shinigami e Quincy)
 * ABSORVEM o fragmento — XP e o recurso da classe cheio; para as outras é só material (Chave da Garganta).
 */
public class SoulShardItem extends Item {
    private static final int XP = 300, COOLDOWN = 1200;

    public SoulShardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        MagicData d = MagicApi.get(sp);
        if (!ReishiCrystalItem.isSpiritual(d.path())) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.soul_shard_other")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        MagicApi.giveXp(sp, XP, "hueco_mundo");
        d.fillMana();
        d.markDirty();
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(HmFx.dust(HmFx.CERO_GREEN, 1.3F), sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.5, 0.8, 0.5, 0);
        sl.sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1, sp.getZ(), 12, 0.4, 0.6, 0.4, 0.03);
        sl.playSound(null, sp.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.soul_shard_absorbed")
                .withColor(HmFx.CERO_GREEN), true);
        stack.consume(1, sp);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.arrancar_soul_shard.desc").withStyle(ChatFormatting.GRAY));
    }
}
