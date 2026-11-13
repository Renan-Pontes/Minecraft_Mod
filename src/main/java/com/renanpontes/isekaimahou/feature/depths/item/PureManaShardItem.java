package com.renanpontes.isekaimahou.feature.depths.item;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fragmento de Mana Pura (geodos, masmorra, Verme-Rei): quebrado na mão devolve 40% da mana máxima.
 * Recarga de 30 s — um "elixir" de emergência, nunca obrigatório.
 */
public class PureManaShardItem extends DepthsLoreItem {
    public static final int COOLDOWN = 600;
    private static final float RESTORE = 0.4F;

    public PureManaShardItem(Properties props) {
        super(props, true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.consume(stack);
        MagicData data = MagicApi.get(sp);
        if (data.mana() >= data.maxMana()) {
            sp.displayClientMessage(Component.translatable("item.isekai_mahou.depths_pure_mana_shard.full").withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.fail(stack);
        }
        data.addMana(data.maxMana() * RESTORE);
        data.markDirty();
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
        sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        stack.consume(1, sp);
        return InteractionResultHolder.success(stack);
    }
}
