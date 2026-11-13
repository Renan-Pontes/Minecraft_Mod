package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * v1.0 — Loot de chefe vai para o Inventário Dimensional quando o inventário está cheio (só para quem conhece o
 * glifo Inventário Dimensional).
 * <ul>
 *   <li>Os itens que um chefe deixa cair ganham a marca {@link #TAG}; ao tocar num deles com o inventário cheio, o
 *       item entra no Item Box (respeitando o tamanho do Círculo e os itens proibidos).</li>
 *   <li>Recompensas entregues direto ao jogador ({@code Rewards.give}) que não couberam caem aos pés dele com dono:
 *       durante {@link #WINDOW_TICKS} depois da morte de um chefe por perto, elas também vão para o Item Box.</li>
 * </ul>
 * Nada some: o que não couber no Item Box continua no chão, como antes.
 */
final class BossLootBox {
    /** Marca nos dados persistentes do item no chão. */
    static final String TAG = "isekai_mahou_boss_loot";
    /** Quem estava a até esta distância do chefe conta para a janela de recompensas. */
    private static final double RANGE = 48.0;
    private static final int WINDOW_TICKS = 300;
    /** Intervalo mínimo entre dois avisos ao mesmo jogador. */
    private static final int NOTICE_GAP = 60;

    /** Jogador → fim da janela (tempo de jogo) em que recompensas com dono vão para o Item Box. */
    private static final Map<UUID, Long> WINDOW = new HashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossLootBox::onDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossLootBox::onDeath);
        NeoForge.EVENT_BUS.addListener(BossLootBox::onPickup);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            WINDOW.clear();
            LAST_NOTICE.clear();
        });
    }

    private static void forget(UUID id) {
        WINDOW.remove(id);
        LAST_NOTICE.remove(id);
    }

    private static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || dead.level().isClientSide || !SpaceGravity.isBoss(dead)) return;
        for (ItemEntity ie : e.getDrops()) ie.getPersistentData().putBoolean(TAG, true);
    }

    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || dead.level().isClientSide || !SpaceGravity.isBoss(dead)) return;
        long until = dead.level().getGameTime() + WINDOW_TICKS;
        for (Player p : dead.level().players()) {
            if (p.distanceToSqr(dead) <= RANGE * RANGE) WINDOW.put(p.getUUID(), until);
        }
    }

    private static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp) || sp.isSpectator()) return;
        ItemEntity ie = e.getItemEntity();
        if (ie.hasPickUpDelay()) return;
        UUID target = ie.getTarget();
        if (target != null && !target.equals(sp.getUUID())) return;
        boolean bossDrop = ie.getPersistentData().getBoolean(TAG);
        if (!bossDrop) {
            // recompensa com dono (não coube no inventário) logo depois de um chefe morrer por perto
            Long until = WINDOW.get(sp.getUUID());
            if (target == null || until == null || sp.level().getGameTime() > until) return;
        }
        ItemStack stack = ie.getItem();
        if (stack.isEmpty() || !ItemBoxMenu.allowed(stack)) return;
        Inventory inv = sp.getInventory();
        // ainda cabe algo no inventário: o vanilla apanha normalmente (o resto vem para cá no próximo toque)
        if (inv.getFreeSlot() >= 0 || inv.getSlotWithRemainingSpace(stack) >= 0) return;
        MagicData d = MagicApi.get(sp);
        if (!d.knowsGlyph(SpaceGlyphs.DIMENSIONAL_INVENTORY)) return;
        // com o Item Box aberto a tela é a dona dos dados: não mexer por fora
        if (sp.containerMenu instanceof ItemBoxMenu) return;

        int moved = store(sp, stack, SpaceData.boxSlots(d.circle()));
        if (moved <= 0) return;
        sp.take(ie, moved);
        if (stack.isEmpty()) {
            ie.discard();
            e.setCanPickup(TriState.FALSE);
        } else {
            ie.setItem(stack);
        }
        long now = sp.level().getGameTime();
        Long last = LAST_NOTICE.get(sp.getUUID());
        if (last == null || now - last >= NOTICE_GAP) {
            LAST_NOTICE.put(sp.getUUID(), now);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.boss_loot_boxed")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** Guarda o que couber nos {@code slots} visíveis do Item Box (junta pilhas, depois espaços vazios). */
    private static int store(ServerPlayer sp, ItemStack incoming, int slots) {
        NonNullList<ItemStack> box = SpaceData.loadBox(sp, sp.registryAccess());
        int limit = Math.min(slots, box.size());
        int start = incoming.getCount();
        for (int i = 0; i < limit && !incoming.isEmpty(); i++) {
            ItemStack s = box.get(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, incoming)) continue;
            int move = Math.min(incoming.getCount(), s.getMaxStackSize() - s.getCount());
            if (move <= 0) continue;
            s.grow(move);
            incoming.shrink(move);
        }
        for (int i = 0; i < limit && !incoming.isEmpty(); i++) {
            if (!box.get(i).isEmpty()) continue;
            box.set(i, incoming.split(incoming.getMaxStackSize()));
        }
        int moved = start - incoming.getCount();
        if (moved > 0) SpaceData.saveBox(sp, box, sp.registryAccess());
        return moved;
    }

    private BossLootBox() {}
}
