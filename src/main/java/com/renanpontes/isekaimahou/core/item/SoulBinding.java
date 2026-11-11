package com.renanpontes.isekaimahou.core.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Itens de CLASSE são intransferíveis: qualquer jogador pode pegar um, mas na primeira vez ele fica VINCULADO a quem pegou
 * (nome e UUID guardados no item). Depois disso:
 * <ul><li>só o dono consegue pegar o item do chão;</li>
 * <li>se o item for parar no inventário de outro jogador (baú, corpo...), ele some de lá e VOLTA para o dono
 * (na hora, se ele estiver online; senão, no próximo login);</li>
 * <li>a tela de troca não aceita itens de classe.</li></ul>
 * Item de classe = item do Isekai Mahou cujo id é a arma do kit de uma classe, uma arma de tier dela
 * ({@code <prefixo>_t1..t4}) ou começa com o id da classe ({@code shinigami_}, {@code smith_}...).
 */
public final class SoulBinding {
    private static final String KEY = "isekai_mahou_bound";

    public static boolean isClassItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return false;
        String path = id.getPath();
        for (MagicPath p : MagicPath.values()) {
            if (p == MagicPath.NONE) continue;
            ResourceLocation kit = p.kitWeapon();
            if (kit != null && kit.getPath().equals(path)) return true;
            String prefix = p.weaponPrefix();
            if (prefix != null && !prefix.isEmpty() && path.startsWith(prefix + "_t") && path.length() == prefix.length() + 3
                    && Character.isDigit(path.charAt(path.length() - 1))) return true;
            if (path.startsWith(p.id + "_")) return true;
        }
        return false;
    }

    @Nullable
    public static UUID owner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) return null;
        CompoundTag b = tag.getCompound(KEY);
        return b.hasUUID("id") ? b.getUUID("id") : null;
    }

    public static String ownerName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getCompound(KEY).getString("name");
    }

    public static void bind(ItemStack stack, ServerPlayer player) {
        CompoundTag b = new CompoundTag();
        b.putUUID("id", player.getUUID());
        b.putString("name", player.getGameProfile().getName());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put(KEY, b));
    }

    public static boolean isBoundToOther(ItemStack stack, @Nullable UUID player) {
        UUID o = owner(stack);
        return o != null && !o.equals(player);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SoulBinding::onPickup);
        NeoForge.EVENT_BUS.addListener(SoulBinding::onTick);
        NeoForge.EVENT_BUS.addListener(SoulBinding::onLogin);
    }

    private static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        ItemStack st = e.getItemEntity().getItem();
        if (!isClassItem(st) || !isBoundToOther(st, sp.getUUID())) return;
        e.setCanPickup(TriState.FALSE);
        if (sp.tickCount % 40 == 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.bound.not_yours", ownerName(st))
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 7) return;
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!isClassItem(st)) continue;
            UUID o = owner(st);
            if (o == null) {
                bind(st, sp);
            } else if (!o.equals(sp.getUUID())) {
                String name = ownerName(st);
                inv.setItem(i, ItemStack.EMPTY);
                returnToOwner(sp.server, o, st);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.bound.returned", name)
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static void returnToOwner(MinecraftServer server, UUID owner, ItemStack stack) {
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            Component name = stack.getHoverName();
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.bound.came_back", name)
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            Pending.of(server).add(owner, stack, server.registryAccess());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        for (ItemStack st : Pending.of(sp.server).take(sp.getUUID(), sp.server.registryAccess())) {
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
    }

    /** Itens vinculados esperando o dono entrar no mundo. */
    private static final class Pending extends SavedData {
        private final Map<UUID, ListTag> items = new HashMap<>();

        static Pending of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Pending::new, Pending::load, null), "isekai_mahou_bound_items");
        }

        static Pending load(CompoundTag tag, HolderLookup.Provider reg) {
            Pending p = new Pending();
            for (String k : tag.getAllKeys()) {
                try {
                    p.items.put(UUID.fromString(k), tag.getList(k, Tag.TAG_COMPOUND));
                } catch (IllegalArgumentException ignored) {
                    // chave inválida: ignora
                }
            }
            return p;
        }

        void add(UUID owner, ItemStack stack, HolderLookup.Provider reg) {
            items.computeIfAbsent(owner, k -> new ListTag()).add(stack.save(reg));
            setDirty();
        }

        List<ItemStack> take(UUID owner, HolderLookup.Provider reg) {
            ListTag list = items.remove(owner);
            List<ItemStack> out = new ArrayList<>();
            if (list == null) return out;
            for (int i = 0; i < list.size(); i++) ItemStack.parse(reg, list.getCompound(i)).ifPresent(out::add);
            setDirty();
            return out;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            items.forEach((k, v) -> tag.put(k.toString(), v));
            return tag;
        }
    }

    private SoulBinding() {}
}
