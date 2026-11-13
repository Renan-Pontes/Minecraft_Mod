package com.renanpontes.isekaimahou.feature.auto_glyphs;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.block.ArcaneCollectorBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Para onde vão os itens que os glifos de fazenda produzem (igual ao Recolher):
 * <ul>
 *   <li>Pedestal: Coletor Arcano mais perto (até 16 blocos) → baús encostados no pedestal → em cima do pedestal.</li>
 *   <li>Jogador: direto no inventário (cheio = cai aos pés dele).</li>
 *   <li>Outros: caem no lugar do trabalho.</li>
 * </ul>
 */
public final class Delivery {
    private final ServerLevel level;
    @Nullable private final RunicPedestalBlockEntity pedestal;
    @Nullable private final ArcaneCollectorBlockEntity collector;
    @Nullable private final ServerPlayer player;
    private final Vec3 fallback;
    private int delivered;

    private Delivery(ServerLevel level, @Nullable RunicPedestalBlockEntity pedestal, @Nullable ArcaneCollectorBlockEntity collector,
                     @Nullable ServerPlayer player, Vec3 fallback) {
        this.level = level;
        this.pedestal = pedestal;
        this.collector = collector;
        this.player = player;
        this.fallback = fallback;
    }

    public static Delivery open(SpellContext ctx, @Nullable RunicPedestalBlockEntity pedestal, Vec3 at) {
        ServerLevel level = ctx.level();
        if (pedestal != null) {
            return new Delivery(level, pedestal, ManaNet.nearestCollector(level, pedestal.getBlockPos()), null, pedestal.castPoint());
        }
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null && !(sp instanceof FakePlayer)) return new Delivery(level, null, null, sp, sp.position());
        return new Delivery(level, null, null, null, at);
    }

    /** Ponto para onde os itens "voam" (efeito visual). */
    public Vec3 destination() {
        if (collector != null) return Vec3.atCenterOf(collector.getBlockPos()).add(0, 0.6, 0);
        if (player != null) return player.position().add(0, 0.8, 0);
        return fallback;
    }

    public void give(List<ItemStack> stacks) {
        for (ItemStack s : stacks) give(s);
    }

    public void give(ItemStack stack) {
        if (stack.isEmpty()) return;
        delivered += stack.getCount();
        if (player != null) {
            ItemHandlerHelper.giveItemToPlayer(player, stack.copy());
            return;
        }
        ItemStack rest = stack.copy();
        if (collector != null && !collector.isRemoved()) rest = collector.insert(rest);
        if (!rest.isEmpty() && pedestal != null) rest = insertAdjacent(level, pedestal.getBlockPos(), rest);
        if (!rest.isEmpty()) {
            ItemEntity ie = new ItemEntity(level, fallback.x, fallback.y, fallback.z, rest);
            ie.setDeltaMovement(Vec3.ZERO);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
    }

    /** XP (minérios, pesca) aparece no destino. */
    public void xp(int amount) {
        if (amount <= 0) return;
        if (player != null) player.giveExperiencePoints(amount);
        else ExperienceOrb.award(level, pedestal != null ? pedestal.castPoint() : fallback, amount);
    }

    /** Quantos itens já foram entregues. */
    public int delivered() {
        return delivered;
    }

    /** Linha de partículas do trabalho até o destino (no máximo uma por chamada). */
    public void trail(SpellContext ctx, Vec3 from) {
        if (delivered <= 0) return;
        AutoFx.line(level, from, destination(), ElementFx.dust(ctx.element(), 0.6F), 0.6);
    }

    private static ItemStack insertAdjacent(ServerLevel level, BlockPos pedestal, ItemStack stack) {
        ItemStack rest = stack;
        for (Direction d : Direction.values()) {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pedestal.relative(d), d.getOpposite());
            if (h == null) continue;
            rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
            if (rest.isEmpty()) break;
        }
        return rest;
    }
}
