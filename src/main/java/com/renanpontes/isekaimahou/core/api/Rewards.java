package com.renanpontes.isekaimahou.core.api;

import java.util.List;

import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entrega de recompensas com feedback bonito (servidor). Use em vez de mexer no inventário na mão:
 * <ul>
 *   <li>{@link #give}: inventário, ou o chão aos pés do jogador se não couber (nunca some).</li>
 *   <li>{@link #toast}: notificação estilizada no canto superior direito (moldura arcana + ícone).</li>
 *   <li>{@link #banner}: faixa de sistema no topo da tela (o mesmo estilo de "Missão atualizada").</li>
 * </ul>
 */
public final class Rewards {
    /** Cor padrão das notificações de recompensa (dourado). */
    public static final int GOLD = 0xFFD86B;

    /** Coloca o item no inventário; o que não couber cai no chão aos pés do jogador (com dono, sem demora). */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (upgradeInPlace(player, stack)) return;
        ItemStack copy = stack.copy();
        boolean added = player.getInventory().add(copy);
        if (!added || !copy.isEmpty()) {
            var drop = player.drop(copy, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.25F, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    /**
     * Armas de classe com tiers ({@code <família>_t1..t4}, ex.: zanpakuto_t2): se o jogador já carrega uma arma da MESMA
     * família com tier menor (ou a arma do kit, sem "_t"), ela é SUBSTITUÍDA no mesmo slot (encantamentos e nome
     * são mantidos) em vez de entregar uma segunda arma. Devolve true se substituiu.
     */
    private static boolean upgradeInPlace(ServerPlayer player, ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+)_t([1-9])$").matcher(id.getPath());
        if (!m.matches()) return false;
        String family = m.group(1);
        int newTier = Integer.parseInt(m.group(2));
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int bestSlot = -1, bestTier = Integer.MAX_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack cur = inv.getItem(i);
            if (cur.isEmpty()) continue;
            net.minecraft.resources.ResourceLocation cid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cur.getItem());
            if (!cid.getNamespace().equals(id.getNamespace())) continue;
            String path = cid.getPath();
            int tier;
            if (path.equals(family)) tier = 0;   // a arma do kit (sem _t) é substituída já pelo tier 1
            else {
                java.util.regex.Matcher cm = java.util.regex.Pattern.compile("^(.+)_t([1-9])$").matcher(path);
                if (!cm.matches() || !cm.group(1).equals(family)) continue;
                tier = Integer.parseInt(cm.group(2));
            }
            if (tier < newTier && tier < bestTier) { bestTier = tier; bestSlot = i; }
        }
        if (bestSlot < 0) return false;
        ItemStack old = inv.getItem(bestSlot);
        ItemStack upgraded = stack.copyWithCount(1);
        var ench = old.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) upgraded.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, ench);
        var name = old.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) upgraded.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        inv.setItem(bestSlot, upgraded);
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
        return true;
    }

    /** Atalho: entrega vários itens. */
    public static void giveAll(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack s : stacks) give(player, s);
    }

    /** Toast estilizado (moldura arcana dourada) com título, subtítulo e ícone. {@code icon} pode ser vazio. */
    public static void toast(ServerPlayer player, Component title, Component subtitle, ItemStack icon) {
        toast(player, title, subtitle, icon, GOLD);
    }

    /** Toast estilizado com cor de destaque {@code rgb} (borda, título e brilho). */
    public static void toast(ServerPlayer player, Component title, Component subtitle, ItemStack icon, int rgb) {
        PacketDistributor.sendToPlayer(player, new CorePayloads.RewardToast(title, subtitle,
                icon == null ? ItemStack.EMPTY : icon.copyWithCount(Math.max(1, icon.getCount())), rgb & 0xFFFFFF));
    }

    /** Faixa de sistema no topo da tela só para este jogador (Announce STYLE_SYSTEM, sem balão de fala). */
    public static void banner(ServerPlayer player, Component text, int rgb) {
        PacketDistributor.sendToPlayer(player, new CorePayloads.AnnounceText(-1, text, rgb & 0xFFFFFF, Announce.STYLE_SYSTEM));
    }

    private Rewards() {}
}
