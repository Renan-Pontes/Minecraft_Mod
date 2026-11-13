package com.renanpontes.isekaimahou.feature.mana_core.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu do Núcleo de Mana (sem espaços de item): só números sincronizados e os botões de depositar/retirar.
 * O "pode retirar" é calculado para CADA jogador que abre (dono/criativo/operador).
 */
public class ManaCoreMenu extends AbstractContainerMenu {
    private final ContainerData data;
    @Nullable private final ManaCoreBlockEntity core;
    private final BlockPos pos;

    /** Cliente. */
    public ManaCoreMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, new SimpleContainerData(ManaCoreBlockEntity.D_COUNT), null, buf.readBlockPos());
    }

    /** Servidor. */
    public ManaCoreMenu(int id, Inventory inventory, ManaCoreBlockEntity be, Player player) {
        this(id, serverData(be, player), be, be.getBlockPos());
    }

    private ManaCoreMenu(int id, ContainerData data, @Nullable ManaCoreBlockEntity core, BlockPos pos) {
        super(ManaCoreRegistries.MANA_CORE_MENU.get(), id);
        this.data = data;
        this.core = core;
        this.pos = pos;
        checkContainerDataCount(data, ManaCoreBlockEntity.D_COUNT);
        addDataSlots(data);
    }

    private static ContainerData serverData(ManaCoreBlockEntity be, Player player) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case ManaCoreBlockEntity.D_MANA_LO -> be.mana() & 0xFFFF;
                    case ManaCoreBlockEntity.D_MANA_HI -> (be.mana() >>> 16) & 0xFFFF;
                    case ManaCoreBlockEntity.D_TOWERS -> be.towers();
                    case ManaCoreBlockEntity.D_USED_LO -> be.usedPerMinute() & 0xFFFF;
                    case ManaCoreBlockEntity.D_USED_HI -> (be.usedPerMinute() >>> 16) & 0xFFFF;
                    case ManaCoreBlockEntity.D_CAN_WITHDRAW -> be.mayWithdraw(player) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() {
                return ManaCoreBlockEntity.D_COUNT;
            }
        };
    }

    public BlockPos pos() { return pos; }
    public int mana() { return join(ManaCoreBlockEntity.D_MANA_LO, ManaCoreBlockEntity.D_MANA_HI); }
    public int usedPerMinute() { return join(ManaCoreBlockEntity.D_USED_LO, ManaCoreBlockEntity.D_USED_HI); }
    public int towers() { return data.get(ManaCoreBlockEntity.D_TOWERS); }
    public boolean canWithdraw() { return data.get(ManaCoreBlockEntity.D_CAN_WITHDRAW) != 0; }

    /** Os dados vão em pedaços de 16 bits: junta as duas metades. */
    private int join(int lo, int hi) {
        return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null || core.isRemoved() || !(player instanceof ServerPlayer sp)) return false;
        core.handleButton(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (core != null && core.isRemoved()) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
