package com.renanpontes.isekaimahou.feature.depths.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.depths.DepthsRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Faca da Deusa (DanMachi): adaga leve (dano 4, rápida) que CRESCE com quem a empunha — "Falna": +1 de dano a cada
 * 12 níveis e +0,2 por Círculo (até ~+10 no fim do jogo). Só tem esse poder na mão do dono do progresso: o bônus é
 * calculado na hora do golpe ({@code DepthsEvents}).
 * <p>Usar (segurar e soltar): <b>Argonauta</b> — carrega até 3 s (um sino por segundo); o PRÓXIMO golpe em 10 s
 * causa +50% de dano por segundo carregado. Recarga 12 s.</p>
 */
public class GoddessKnifeItem extends SwordItem {
    public static final int COOLDOWN = 240;
    public static final String ARGONAUT_KEY = "isekai_mahou.depths_argonaut";
    public static final String ARGONAUT_UNTIL_KEY = "isekai_mahou.depths_argonaut_until";
    private static final int ARGONAUT_WINDOW = 200;

    public GoddessKnifeItem(Tier tier, Properties props) {
        super(tier, props);
    }

    /** Bônus de dano da Falna para este jogador. */
    public static float falnaBonus(Player player) {
        MagicData d = MagicApi.get(player);
        return d.level() / 12.0F + d.circle() * 0.2F;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        int v = Math.round(falnaBonus(sp) * 10.0F);
        Integer cur = stack.get(DepthsRegistries.FALNA.get());
        if (cur == null || cur != v) stack.set(DepthsRegistries.FALNA.get(), v);
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
        if (!(level instanceof ServerLevel sl) || used <= 0 || used > 60 || used % 20 != 0) return;
        float pitch = 0.8F + used / 60.0F;
        sl.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, pitch);
        sl.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 1.2, entity.getZ(), 6 + used / 5, 0.4, 0.5, 0.4, 0.02);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer sp)) return;
        int charge = Math.min(3, (getUseDuration(stack, entity) - timeLeft) / 20);
        if (charge <= 0) return;
        CompoundTag data = sp.getPersistentData();
        data.putInt(ARGONAUT_KEY, charge);
        data.putLong(ARGONAUT_UNTIL_KEY, sp.level().getGameTime() + ARGONAUT_WINDOW);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        sp.displayClientMessage(Component.translatable("item.isekai_mahou.depths_goddess_knife.charged", charge * 50)
                .withStyle(ChatFormatting.WHITE), true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /** Consome o Argonauta carregado (se ainda valer) e devolve o multiplicador do golpe. */
    public static float consumeArgonaut(ServerPlayer sp) {
        CompoundTag data = sp.getPersistentData();
        int charge = data.getInt(ARGONAUT_KEY);
        if (charge <= 0) return 1.0F;
        data.remove(ARGONAUT_KEY);
        if (sp.level().getGameTime() > data.getLong(ARGONAUT_UNTIL_KEY)) return 1.0F;
        return 1.0F + 0.5F * charge;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        DepthsLoreItem.lore(stack, lines);
        Integer falna = stack.get(DepthsRegistries.FALNA.get());
        String shown = falna == null ? "?" : String.format("%.1f", falna / 10.0F);
        lines.add(Component.translatable("item.isekai_mahou.depths_goddess_knife.falna", shown).withStyle(ChatFormatting.AQUA));
    }
}
