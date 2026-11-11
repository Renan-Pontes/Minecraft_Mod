package com.renanpontes.isekaimahou.feature.ritual_necro.item;

import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroRitual;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRite;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRites;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Filactério: guarda almas por tipo de mob (componente {@code isekai_mahou:necro_souls}).
 * Brilha mais quanto mais almas tem (modelo com 4 estágios; brilho encantado quando transborda).
 * Botão direito (na mão secundária, agachado): mostra no chat o que o próximo Rito Ossuário pede.
 */
public class PhylacteryItem extends Item {
    private static final int TOOLTIP_LINES = 8;

    public PhylacteryItem(Properties properties) {
        super(properties);
    }

    /**
     * Filactério do jogador: mão secundária, depois a principal e, se {@code anywhere}, o do inventário
     * com mais almas. Vazio se não tiver nenhum.
     */
    public static ItemStack find(Player player, boolean anywhere) {
        ItemStack off = player.getOffhandItem();
        if (off.is(NecroContent.PHYLACTERY.get())) return off;
        ItemStack main = player.getMainHandItem();
        if (main.is(NecroContent.PHYLACTERY.get())) return main;
        if (!anywhere) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        int bestTotal = -1;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(NecroContent.PHYLACTERY.get())) continue;
            int t = SoulStore.of(s).total();
            if (t > bestTotal) {
                best = s;
                bestTotal = t;
            }
        }
        return best;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Na mão secundária (onde ele colhe almas) só responde agachado: senão qualquer clique no ar lotaria o chat
        if (hand == InteractionHand.OFF_HAND && !player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer sp) {
            SoulStore souls = SoulStore.of(stack);
            int next = NecroRitual.rank(MagicApi.get(sp)) + 1;
            NecroRite rite = NecroRites.get(next);
            sp.sendSystemMessage(NecroRitual.msg("phylactery_report", souls.total()).withStyle(ChatFormatting.DARK_AQUA));
            if (rite == null) {
                sp.sendSystemMessage(NecroRitual.msg(NecroRites.isEmpty() ? "no_rites" : "max_rank").withStyle(ChatFormatting.GRAY));
            } else {
                sp.sendSystemMessage(NecroRitual.msg("next_rite", NecroRitual.roman(next)).withStyle(ChatFormatting.GRAY));
                for (Map.Entry<ResourceLocation, Integer> e : rite.souls().entrySet()) {
                    int have = souls.count(e.getKey());
                    sp.sendSystemMessage(Component.literal("   ").append(NecroRitual.msg("soul_line", NecroRitual.soulName(e.getKey()),
                            Math.min(have, e.getValue()), e.getValue())
                            .withStyle(have >= e.getValue() ? ChatFormatting.GREEN : ChatFormatting.RED)));
                }
                if (rite.requiredSkulls() > 0) {
                    sp.sendSystemMessage(NecroRitual.msg("next_rite_skulls", rite.requiredSkulls()).withStyle(ChatFormatting.DARK_GRAY));
                }
                if (rite.minCircle() > 1) {
                    sp.sendSystemMessage(NecroRitual.msg("next_rite_circle", rite.minCircle()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return SoulStore.of(stack).stage() >= 3 || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SoulStore souls = SoulStore.of(stack);
        if (souls.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.empty").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.total", souls.total()).withStyle(ChatFormatting.DARK_AQUA));
            List<Map.Entry<ResourceLocation, Integer>> list = souls.sorted();
            for (int i = 0; i < Math.min(TOOLTIP_LINES, list.size()); i++) {
                Map.Entry<ResourceLocation, Integer> e = list.get(i);
                tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.soul_line", NecroRitual.soulName(e.getKey()), e.getValue())
                        .withStyle(ChatFormatting.AQUA));
            }
            if (list.size() > TOOLTIP_LINES) {
                tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.more", list.size() - TOOLTIP_LINES).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.hint2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.phylactery.hint3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
