package com.renanpontes.isekaimahou.feature.class_knight.client;

import java.util.HashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Vento Invisível no cliente: a lâmina (espada) de quem está com o efeito NÃO é desenhada — nem na primeira pessoa (a mão
 * principal some), nem na terceira (o item é tirado da mão só durante o desenho daquele jogador e devolvido logo
 * depois). O ar distorcido em volta vem das partículas do servidor.
 */
public final class InvisibleAirClient {
    /** Itens escondidos durante o desenho de cada jogador (id da entidade → item). */
    private static final Map<Integer, ItemStack> HIDDEN = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InvisibleAirClient::onHand);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InvisibleAirClient::onPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InvisibleAirClient::onPost);
    }

    /** O efeito está ativo de verdade? (efeitos de outros jogadores no cliente ficam com duração 0 ao vencer). */
    public static boolean wrapped(LivingEntity e) {
        MobEffectInstance inst = e.getEffect(KnightRegistries.INVISIBLE_AIR);
        return inst != null && (inst.isInfiniteDuration() || inst.getDuration() > 0);
    }

    private static void onHand(RenderHandEvent event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND || !KnightSwords.isSword(event.getItemStack())) return;
        Player p = Minecraft.getInstance().player;
        if (p != null && wrapped(p)) event.setCanceled(true);
    }

    private static void onPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled()) return;
        Player p = event.getEntity();
        if (!wrapped(p)) return;
        Inventory inv = p.getInventory();
        if (inv.selected < 0 || inv.selected >= inv.items.size()) return;
        ItemStack held = inv.items.get(inv.selected);
        if (!KnightSwords.isSword(held)) return;
        HIDDEN.put(p.getId(), held);
        inv.items.set(inv.selected, ItemStack.EMPTY);
    }

    private static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        ItemStack held = HIDDEN.remove(p.getId());
        if (held == null) return;
        Inventory inv = p.getInventory();
        if (inv.selected >= 0 && inv.selected < inv.items.size() && inv.items.get(inv.selected).isEmpty()) {
            inv.items.set(inv.selected, held);
        }
    }

    private InvisibleAirClient() {}
}
