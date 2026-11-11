package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.wand.ConstructionWandItem;
import com.renanpontes.isekaimahou.feature.farm.wand.WandData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * De onde saem (e para onde voltam) os blocos de uma construção: o inventário do jogador + o baú ligado à varinha,
 * ou os baús em volta de um canteiro/pedestal. Sempre 1 item por vez (as construções vão bloco a bloco).
 */
public abstract class Supply {
    /** Distância máxima até o baú ligado. */
    public static final int LINK_RANGE = 64;

    /** Tira 1 item que combina (prefere {@code prefer}); {@link ItemStack#EMPTY} se não houver. */
    public abstract ItemStack take(Predicate<ItemStack> match, @Nullable Item prefer);

    /** Guarda/devolve um item (o que não couber cai no chão). */
    public abstract void give(ItemStack stack);

    /** Quantos itens que combinam há (para mensagens e telas). */
    public abstract int count(Predicate<ItemStack> match);

    /** Modo criativo: não gasta nada. */
    public boolean free() {
        return false;
    }

    // =====================================================================================
    //  Fábricas
    // =====================================================================================

    /** Jogador: inventário + baú ligado à primeira Varinha de Construção que ele carrega. */
    public static Supply player(ServerPlayer player) {
        return new PlayerSupply(player);
    }

    /** Recipientes (baús, barris, coletores...) nas posições dadas. */
    public static Supply containers(ServerLevel level, List<BlockPos> positions, BlockPos dropAt) {
        return new ContainerSupply(level, positions, dropAt);
    }

    /** Posições com inventário num cubo de raio {@code r} (sem o centro). */
    public static List<BlockPos> findContainers(ServerLevel level, BlockPos center, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (p.equals(center) || !level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) == null) continue;
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, p, null) != null) out.add(p.immutable());
        }
        return out;
    }

    @Nullable
    public static IItemHandler handler(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }

    static ItemStack takeFrom(IItemHandler h, Predicate<ItemStack> match, @Nullable Item prefer) {
        if (prefer != null) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && s.is(prefer) && match.test(s)) {
                    ItemStack got = h.extractItem(i, 1, false);
                    if (!got.isEmpty()) return got;
                }
            }
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && match.test(s)) {
                ItemStack got = h.extractItem(i, 1, false);
                if (!got.isEmpty()) return got;
            }
        }
        return ItemStack.EMPTY;
    }

    static int countIn(IItemHandler h, Predicate<ItemStack> match) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && match.test(s)) n += s.getCount();
        }
        return n;
    }

    // =====================================================================================
    //  Jogador
    // =====================================================================================
    private static final class PlayerSupply extends Supply {
        /** Mão secundária primeiro (é onde fica o material), depois a barra e a mochila; nunca a armadura. */
        private static final int[] SLOTS = slots();
        private final ServerPlayer player;

        private static int[] slots() {
            int[] out = new int[37];
            out[0] = Inventory.SLOT_OFFHAND;
            for (int i = 0; i < 36; i++) out[i + 1] = i;
            return out;
        }

        PlayerSupply(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public boolean free() {
            return player.getAbilities().instabuild;
        }

        @Nullable
        private IItemHandler linked() {
            GlobalPos link = linkOf(player);
            if (link == null || !link.dimension().equals(player.level().dimension())) return null;
            if (link.pos().distSqr(player.blockPosition()) > (double) LINK_RANGE * LINK_RANGE) return null;
            return handler(player.level(), link.pos());
        }

        @Override
        public ItemStack take(Predicate<ItemStack> match, @Nullable Item prefer) {
            if (free()) return ItemStack.EMPTY;
            Inventory inv = player.getInventory();
            // 1) inventário (preferido primeiro), 2) baú ligado
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0 && prefer == null) continue;
                for (int i : SLOTS) {
                    ItemStack s = inv.getItem(i);
                    if (s.isEmpty() || s.getItem() instanceof ConstructionWandItem) continue;
                    if (pass == 0 && !s.is(prefer)) continue;
                    if (!match.test(s)) continue;
                    ItemStack got = s.split(1);
                    inv.setChanged();
                    return got;
                }
            }
            IItemHandler h = linked();
            return h == null ? ItemStack.EMPTY : takeFrom(h, match, prefer);
        }

        @Override
        public void give(ItemStack stack) {
            if (stack.isEmpty() || free()) return;
            if (player.getInventory().add(stack) && stack.isEmpty()) return;
            IItemHandler h = linked();
            ItemStack rest = h == null ? stack : ItemHandlerHelper.insertItemStacked(h, stack, false);
            if (!rest.isEmpty()) player.drop(rest, false);
        }

        @Override
        public int count(Predicate<ItemStack> match) {
            int n = 0;
            Inventory inv = player.getInventory();
            for (int i : SLOTS) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && match.test(s) && !(s.getItem() instanceof ConstructionWandItem)) {
                    n += s.getCount();
                }
            }
            IItemHandler h = linked();
            if (h != null) n += countIn(h, match);
            return n;
        }
    }

    /** Baú ligado: o da varinha na mão principal; senão o da primeira varinha do inventário. */
    @Nullable
    public static GlobalPos linkOf(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.has(FarmRegistries.WAND.get())) {
            WandData d = main.get(FarmRegistries.WAND.get());
            if (d != null && d.link().isPresent()) return d.link().get();
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            WandData d = inv.getItem(i).get(FarmRegistries.WAND.get());
            if (d != null && d.link().isPresent()) return d.link().get();
        }
        return null;
    }

    // =====================================================================================
    //  Recipientes
    // =====================================================================================
    private static final class ContainerSupply extends Supply {
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private final BlockPos dropAt;

        ContainerSupply(ServerLevel level, List<BlockPos> positions, BlockPos dropAt) {
            this.level = level;
            this.positions = positions;
            this.dropAt = dropAt;
        }

        @Override
        public ItemStack take(Predicate<ItemStack> match, @Nullable Item prefer) {
            if (prefer != null) {
                for (BlockPos p : positions) {
                    IItemHandler h = handler(level, p);
                    if (h == null) continue;
                    ItemStack got = takeFrom(h, s -> s.is(prefer) && match.test(s), null);
                    if (!got.isEmpty()) return got;
                }
            }
            for (BlockPos p : positions) {
                IItemHandler h = handler(level, p);
                if (h == null) continue;
                ItemStack got = takeFrom(h, match, null);
                if (!got.isEmpty()) return got;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void give(ItemStack stack) {
            ItemStack rest = stack;
            for (BlockPos p : positions) {
                if (rest.isEmpty()) return;
                IItemHandler h = handler(level, p);
                if (h != null) rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
            }
            if (!rest.isEmpty()) Containers.dropItemStack(level, dropAt.getX() + 0.5, dropAt.getY() + 1.0, dropAt.getZ() + 0.5, rest);
        }

        @Override
        public int count(Predicate<ItemStack> match) {
            int n = 0;
            for (BlockPos p : positions) {
                IItemHandler h = handler(level, p);
                if (h != null) n += countIn(h, match);
            }
            return n;
        }
    }
}
