package com.renanpontes.isekaimahou.feature.mana_core.block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.WirelessMana;
import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;
import com.renanpontes.isekaimahou.feature.mana_core.Overcharge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Núcleo de Mana: um "banco" de mana enorme ({@link #CAPACITY}, 10 Baterias). Você deposita o seu mana; pedestais e
 * condensadores ligados pelo Condutor de Mana usam/enchem sem fio; e você pode RETIRAR — inclusive acima do seu
 * máximo (sobrecarga → Intoxicação por Mana).
 * <p>O nível 0–6 vira estado do bloco (textura/luz) e o comparador lê 0–15. Guarda estatísticas para a tela:
 * quantas torres puxaram mana no último minuto e quanto.
 */
public class ManaCoreBlockEntity extends BlockEntity implements WirelessMana, MenuProvider {
    /** 600 000 (10 Baterias de Mana). */
    public static final int CAPACITY = Math.round(200_000 * ResourceTraining.MANA_SCALE);

    // Dados da tela
    public static final int D_MANA_LO = 0, D_MANA_HI = 1, D_TOWERS = 2, D_USED_LO = 3, D_USED_HI = 4, D_CAN_WITHDRAW = 5, D_COUNT = 6;
    // Botões
    public static final int B_DEPOSIT_SMALL = 0, B_DEPOSIT_BIG = 1, B_DEPOSIT_ALL = 2, B_WITHDRAW_SMALL = 3, B_WITHDRAW_BIG = 4,
            B_WITHDRAW_FILL = 5, B_WITHDRAW_OVER = 6;
    public static final int SMALL = 100, BIG = 1000;

    private int mana;
    @Nullable private UUID owner;
    private String ownerName = "";

    // Estatísticas (não salvas): janela de 1 minuto
    private int ticks;
    private int usedNow, usedLast;
    private final Set<Long> usersNow = new HashSet<>();
    private int towersLast;

    public ManaCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ManaCoreRegistries.MANA_CORE_BE.get(), pos, state);
    }

    // =====================================================================================
    //  WirelessMana
    // =====================================================================================
    @Override public int mana() { return mana; }
    @Override public int capacity() { return CAPACITY; }

    @Override
    public int insert(int amount) {
        int add = Mth.clamp(amount, 0, space());
        if (add > 0) setMana(mana + add);
        return add;
    }

    @Override
    public int extract(int amount) {
        int take = Mth.clamp(amount, 0, mana);
        if (take > 0) setMana(mana - take);
        return take;
    }

    @Override
    public void noteUse(BlockPos user, int amount) {
        usedNow += Math.max(0, amount);
        usersNow.add(user.asLong());
    }

    /** Comando de teste / item cheio. */
    public void setManaDirect(int value) {
        setMana(value);
    }

    private void setMana(int value) {
        int oldSignal = comparatorSignal(mana);
        mana = Mth.clamp(value, 0, CAPACITY);
        setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        int fill = fillLevel(mana);
        if (state.hasProperty(ManaCoreBlock.FILL) && state.getValue(ManaCoreBlock.FILL) != fill) {
            level.setBlock(worldPosition, state.setValue(ManaCoreBlock.FILL, fill), Block.UPDATE_ALL);
        }
        if (comparatorSignal(mana) != oldSignal) level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }

    /** Nível visual 0–6 (0 só vazio, 6 só cheio). */
    public static int fillLevel(int mana) {
        if (mana <= 0) return 0;
        if (mana >= CAPACITY) return 6;
        return Mth.clamp(1 + (int) ((long) mana * 5 / CAPACITY), 1, 5);
    }

    public static int comparatorSignal(int mana) {
        return mana <= 0 ? 0 : Math.min(15, 1 + (int) ((long) mana * 14 / CAPACITY));
    }

    // =====================================================================================
    //  Dono
    // =====================================================================================
    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    @Nullable public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }

    /** Retirar: só o dono (ou criativo/operador; ou qualquer um se o núcleo não tem dono). Depositar: qualquer um. */
    public boolean mayWithdraw(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.getAbilities().instabuild || player.hasPermissions(2);
    }

    // =====================================================================================
    //  Tick (servidor): só a janela de estatísticas
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaCoreBlockEntity be) {
        if (++be.ticks >= 1200) {
            be.ticks = 0;
            be.usedLast = be.usedNow;
            be.towersLast = be.usersNow.size();
            be.usedNow = 0;
            be.usersNow.clear();
        }
    }

    /** Torres que puxaram mana no último minuto (o minuto atual conta se já passou do anterior). */
    public int towers() { return Math.max(towersLast, usersNow.size()); }
    public int usedPerMinute() { return Math.max(usedLast, usedNow); }

    // =====================================================================================
    //  Depositar / retirar
    // =====================================================================================
    public void handleButton(ServerPlayer sp, int id) {
        switch (id) {
            case B_DEPOSIT_SMALL -> deposit(sp, SMALL);
            case B_DEPOSIT_BIG -> deposit(sp, BIG);
            case B_DEPOSIT_ALL -> deposit(sp, Integer.MAX_VALUE);
            case B_WITHDRAW_SMALL -> withdraw(sp, SMALL);
            case B_WITHDRAW_BIG -> withdraw(sp, BIG);
            case B_WITHDRAW_FILL -> {
                MagicData d = MagicApi.get(sp);
                int room = (int) Math.floor(d.maxMana() - d.mana());
                if (room <= 0) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.already_full").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    withdraw(sp, room);
                }
            }
            case B_WITHDRAW_OVER -> {
                MagicData d = MagicApi.get(sp);
                int room = (int) Math.max(0, Math.floor(d.maxMana() - d.mana()));
                withdraw(sp, room + Overcharge.STEP);
            }
            default -> {}
        }
    }

    /** Põe mana do jogador no núcleo: a SOBRECARGA sai primeiro (é o jeito seguro de se livrar dela), depois o mana. */
    public void deposit(ServerPlayer sp, int requested) {
        MagicData d = MagicApi.get(sp);
        if (space() <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.core_full").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        float over = Overcharge.get(sp);
        int available = (int) Math.floor(over + d.mana());
        int amount = Math.min(requested, Math.min(available, space()));
        if (amount <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.no_player_mana").withStyle(ChatFormatting.RED), true);
            return;
        }
        float fromOver = Math.min(over, amount);
        if (fromOver > 0) Overcharge.set(sp, over - fromOver);
        float fromMana = amount - fromOver;
        if (fromMana > 0) d.setMana(d.mana() - fromMana);
        insert(amount);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.deposited", amount, mana, CAPACITY)
                .withColor(AutoFx.MANA_RGB), true);
        flowFx(sp, true);
    }

    /**
     * Tira mana do núcleo para o jogador. O que passar do máximo dele vira SOBRECARGA (até {@link Overcharge#cap}) —
     * os botões 100/1000 deixam passar (é a escolha do jogador, com aviso); "Encher" pede exatamente o que falta e
     * "+300" pede o que falta + 300.
     */
    public void withdraw(ServerPlayer sp, int requested) {
        if (!mayWithdraw(sp)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.not_owner", ownerName).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (mana <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.core_empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        MagicData d = MagicApi.get(sp);
        float over = Overcharge.get(sp);
        int room = (int) Math.max(0, Math.floor(d.maxMana() - d.mana()));
        int overRoom = (int) Math.max(0, Math.floor(Overcharge.cap(d) - over));
        int take = Math.min(requested, Math.min(mana, room + overRoom));
        if (take <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.over_cap", Overcharge.cap(d))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int toMana = Math.min(take, room);
        int toOver = take - toMana;
        extract(take);
        if (toMana > 0) d.addMana(toMana);
        if (toOver > 0) {
            float total = over + toOver;
            Overcharge.set(sp, total);
            int lvl = Overcharge.level(total);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.withdrew_over", take, toOver,
                    (int) total, lvl).withColor(Overcharge.RGB), true);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WITCH_DRINK, sp.getSoundSource(), 0.8F, 0.7F);
        } else {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.withdrew", take, mana, CAPACITY)
                    .withColor(AutoFx.MANA_RGB), true);
        }
        flowFx(sp, false);
    }

    /** Linha de mana entre o jogador e o núcleo (para dentro ao depositar, para fora ao retirar). */
    private void flowFx(ServerPlayer sp, boolean in) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Vec3 p = sp.position().add(0, 1.1, 0);
        AutoFx.line(server, in ? p : c, in ? c : p, AutoFx.dust(AutoFx.MANA_RGB, 1.0F), 0.3);
        server.sendParticles(in ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, c.x, c.y + 0.5, c.z, 16, 0.4, 0.4, 0.4, in ? 0.6 : 0.05);
        AutoFx.sound(server, c, in ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BEACON_POWER_SELECT, 0.9F, in ? 1.1F : 1.5F);
    }

    // =====================================================================================
    //  Menu
    // =====================================================================================
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.isekai_mahou.mana_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ManaCoreMenu(id, inventory, this, player);
    }

    // =====================================================================================
    //  NBT e componentes do item
    // =====================================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mana", mana);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mana = Mth.clamp(tag.getInt("Mana"), 0, CAPACITY);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        mana = Mth.clamp(input.getOrDefault(ManaCoreRegistries.CORE_MANA.get(), 0), 0, CAPACITY);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (mana > 0) components.set(ManaCoreRegistries.CORE_MANA.get(), mana);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Mana");
    }
}
