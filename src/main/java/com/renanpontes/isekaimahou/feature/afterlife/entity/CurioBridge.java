package com.renanpontes.isekaimahou.feature.afterlife.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Tira/devolve os acessórios do Curios para o Corpo (À Beira da Morte). A chave guarda o lugar exato
 * ({@code "<slot>#<i>"} ou {@code "<slot>#c<i>"} para o cosmético) para devolver ao mesmo espaço.
 */
final class CurioBridge {
    record Taken(String key, ItemStack stack) {}

    /** Esvazia os espaços do Curios e devolve o que havia (cada item sai uma única vez). */
    static List<Taken> takeAll(ServerPlayer player) {
        List<Taken> out = new ArrayList<>();
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
                for (Map.Entry<String, ICurioStacksHandler> e : inv.getCurios().entrySet()) {
                    take(e.getKey() + "#", e.getValue().getStacks(), out);
                    take(e.getKey() + "#c", e.getValue().getCosmeticStacks(), out);
                }
            });
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.warn("afterlife: não consegui ler os acessórios do Curios de {}", player.getGameProfile().getName(), ex);
        }
        return out;
    }

    private static void take(String prefix, IDynamicStackHandler stacks, List<Taken> out) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack s = stacks.getStackInSlot(i);
            if (s.isEmpty()) continue;
            out.add(new Taken(prefix + i, s.copy()));
            stacks.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Põe o item de volta no espaço original se ele existir e estiver vazio. false = não coube (quem chamou entrega de outro jeito). */
    static boolean put(ServerPlayer player, String key, ItemStack stack) {
        int hash = key.lastIndexOf('#');
        if (hash <= 0) return false;
        String slot = key.substring(0, hash);
        String rest = key.substring(hash + 1);
        boolean cosmetic = rest.startsWith("c");
        int index;
        try {
            index = Integer.parseInt(cosmetic ? rest.substring(1) : rest);
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            var inv = CuriosApi.getCuriosInventory(player);
            if (inv.isEmpty()) return false;
            var handler = inv.get().getStacksHandler(slot);
            if (handler.isEmpty()) return false;
            IDynamicStackHandler stacks = cosmetic ? handler.get().getCosmeticStacks() : handler.get().getStacks();
            if (index < 0 || index >= stacks.getSlots() || !stacks.getStackInSlot(index).isEmpty()) return false;
            stacks.setStackInSlot(index, stack);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CurioBridge() {}
}
