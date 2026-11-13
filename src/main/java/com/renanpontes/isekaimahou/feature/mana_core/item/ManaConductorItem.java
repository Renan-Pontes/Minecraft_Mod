package com.renanpontes.isekaimahou.feature.mana_core.item;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.WirelessMana;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Condutor de Mana: o "fio sem fio" entre um Núcleo de Mana e as máquinas.
 * <ul>
 *   <li>Clique num <b>Núcleo</b>: sintoniza o Condutor nele.</li>
 *   <li>Clique num <b>Pedestal Rúnico</b> ou <b>Condensador de Mana</b>: liga ao núcleo sintonizado (de novo = desliga).</li>
 *   <li>Agachado + clique num <b>Pedestal</b>: LIGA/DESLIGA o pedestal.</li>
 *   <li>Clique no ar: mostra as ligações do núcleo. Agachado no ar: limpa a sintonia.</li>
 *   <li>Segurando: a cada 1,5 s mostra (só para você) os fios de mana até as máquinas ligadas.</li>
 * </ul>
 * Alcance {@link WirelessMana#RANGE} blocos, mesmo mundo; cada 16 blocos custam +5% de mana na transmissão.
 */
public class ManaConductorItem extends Item {
    public ManaConductorItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static GlobalPos link(ItemStack stack) {
        return stack.get(ManaCoreRegistries.CONDUCTOR_LINK.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return link(stack) != null;
    }

    // =====================================================================================
    //  Clique em blocos (antes do próprio bloco: o Condutor não abre a tela do pedestal/núcleo)
    // =====================================================================================
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        boolean known = be instanceof ManaCoreBlockEntity || be instanceof RunicPedestalBlockEntity || be instanceof ManaCondenserBlockEntity;
        if (!known) return InteractionResult.PASS;
        if (level.isClientSide || !(ctx.getPlayer() instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (be instanceof ManaCoreBlockEntity core) {
            stack.set(ManaCoreRegistries.CONDUCTOR_LINK.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_bound", pos.getX(), pos.getY(), pos.getZ(),
                    core.mana(), ManaCoreBlockEntity.CAPACITY).withColor(AutoFx.MANA_RGB));
            server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.8F);
            showLinks(server, sp, pos, true);
            return InteractionResult.SUCCESS;
        }
        if (be instanceof RunicPedestalBlockEntity pedestal) {
            if (!pedestal.mayConfigure(sp)) {
                say(sp, Component.translatable("message.isekai_mahou.automation.pedestal_not_owner", pedestal.ownerName())
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (sp.isSecondaryUseActive()) {
                pedestal.setEnabled(!pedestal.enabled());
                say(sp, Component.translatable(pedestal.enabled() ? "message.isekai_mahou.mana_core.pedestal_on"
                        : "message.isekai_mahou.mana_core.pedestal_off").withColor(pedestal.enabled() ? 0x6BE38A : 0x9A93AC));
                server.playSound(null, pos, pedestal.enabled() ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.6F, 1.4F);
                return InteractionResult.SUCCESS;
            }
            BlockPos target = linkTarget(stack, sp, server, pos);
            if (target == null) return InteractionResult.SUCCESS;
            if (target.equals(pedestal.corePos())) {
                pedestal.setCorePos(null);
                unlinked(sp, server, pos);
            } else {
                pedestal.setCorePos(target);
                linked(sp, server, target, pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (be instanceof ManaCondenserBlockEntity condenser) {
            BlockPos target = linkTarget(stack, sp, server, pos);
            if (target == null) return InteractionResult.SUCCESS;
            if (target.equals(condenser.corePos())) {
                condenser.setCorePos(null);
                unlinked(sp, server, pos);
            } else {
                condenser.setCorePos(target);
                linked(sp, server, target, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Núcleo sintonizado que serve para {@code machine} (mesmo mundo, alcance, ainda existe) — ou null com o aviso. */
    @Nullable
    private static BlockPos linkTarget(ItemStack stack, ServerPlayer sp, ServerLevel level, BlockPos machine) {
        GlobalPos link = link(stack);
        if (link == null) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_unbound").withStyle(ChatFormatting.YELLOW));
            return null;
        }
        if (!link.dimension().equals(level.dimension())) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_other_world").withStyle(ChatFormatting.RED));
            return null;
        }
        BlockPos core = link.pos();
        if (!WirelessMana.inRange(core, machine)) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_too_far",
                    (int) Math.sqrt(core.distSqr(machine)), WirelessMana.RANGE).withStyle(ChatFormatting.RED));
            return null;
        }
        if (WirelessMana.at(level, core) == null && level.getChunkSource().getChunkNow(core.getX() >> 4, core.getZ() >> 4) != null) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_core_gone").withStyle(ChatFormatting.RED));
            return null;
        }
        return core;
    }

    private static void linked(ServerPlayer sp, ServerLevel level, BlockPos core, BlockPos machine) {
        int dist = (int) Math.sqrt(core.distSqr(machine));
        say(sp, Component.translatable("message.isekai_mahou.mana_core.linked", dist, WirelessMana.lossPercent(core, machine))
                .withColor(AutoFx.MANA_RGB));
        AutoFx.line(level, top(core), top(machine), AutoFx.dust(AutoFx.MANA_RGB, 1.1F), 0.6);
        level.playSound(null, machine, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.3F);
    }

    private static void unlinked(ServerPlayer sp, ServerLevel level, BlockPos machine) {
        say(sp, Component.translatable("message.isekai_mahou.mana_core.unlinked").withStyle(ChatFormatting.GRAY));
        level.playSound(null, machine, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    // =====================================================================================
    //  Clique no ar e enquanto segura
    // =====================================================================================
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        GlobalPos link = link(stack);
        if (sp.isSecondaryUseActive()) {
            if (link != null) {
                stack.remove(ManaCoreRegistries.CONDUCTOR_LINK.get());
                say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_cleared").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResultHolder.success(stack);
        }
        if (link == null) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_unbound").withStyle(ChatFormatting.YELLOW));
        } else if (!link.dimension().equals(level.dimension())) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_other_world").withStyle(ChatFormatting.RED));
        } else {
            showLinks(server, sp, link.pos(), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer sp) || sp.isFakePlayer()) return;
        if (sp.tickCount % 30 != 0) return;
        if (!selected && sp.getOffhandItem() != stack) return;
        GlobalPos link = link(stack);
        if (link == null || !link.dimension().equals(level.dimension())) return;
        if (link.pos().distSqr(sp.blockPosition()) > 96 * 96) return;
        showLinks(server, sp, link.pos(), false);
    }

    /**
     * Fios de mana (só para este jogador) do núcleo até cada pedestal/condensador ligado a ele. Varre só chunks
     * carregados num raio de {@link WirelessMana#RANGE}. {@code report}: também diz na barra de ação o resumo.
     */
    private static void showLinks(ServerLevel level, ServerPlayer sp, BlockPos core, boolean report) {
        WirelessMana w = WirelessMana.at(level, core);
        if (w == null) {
            if (report) say(sp, Component.translatable("message.isekai_mahou.mana_core.conductor_core_unloaded").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Vec3 from = top(core);
        int pedestals = 0, condensers = 0, off = 0;
        for (RunicPedestalBlockEntity p : ManaNet.scan(level, core, WirelessMana.RANGE, RunicPedestalBlockEntity.class)) {
            if (!core.equals(p.corePos())) continue;
            pedestals++;
            if (!p.enabled()) off++;
            line(level, sp, from, top(p.getBlockPos()), AutoFx.dust(p.enabled() ? AutoFx.MANA_RGB : 0x6A6478, 0.9F));
        }
        for (ManaCondenserBlockEntity c : ManaNet.scan(level, core, WirelessMana.RANGE, ManaCondenserBlockEntity.class)) {
            if (!core.equals(c.corePos())) continue;
            condensers++;
            line(level, sp, top(c.getBlockPos()), from, AutoFx.dust(0x6BE3C8, 0.9F));
        }
        level.sendParticles(sp, AutoFx.dust(AutoFx.MANA_RGB, 1.4F), true, from.x, from.y + 0.3, from.z, 8, 0.25, 0.25, 0.25, 0);
        if (report) {
            say(sp, Component.translatable("message.isekai_mahou.mana_core.links", w.mana(), w.capacity(), pedestals, off, condensers)
                    .withColor(AutoFx.MANA_RGB));
        }
    }

    private static void line(ServerLevel level, ServerPlayer sp, Vec3 from, Vec3 to, ParticleOptions particle) {
        Vec3 d = to.subtract(from);
        int n = Math.max(2, (int) Math.min(48, d.length() / 0.75));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(sp, particle, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static Vec3 top(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 1.05, 0);
    }

    private static void say(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg, true);
    }

    // =====================================================================================
    //  Tooltip
    // =====================================================================================
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = link(stack);
        if (link != null) {
            BlockPos p = link.pos();
            tooltip.add(Component.translatable("item.isekai_mahou.mana_conductor.bound", p.getX(), p.getY(), p.getZ(),
                    link.dimension().location().getPath()).withColor(AutoFx.MANA_RGB));
        } else {
            tooltip.add(Component.translatable("item.isekai_mahou.mana_conductor.unbound").withStyle(ChatFormatting.YELLOW));
        }
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.isekai_mahou.mana_conductor.tooltip" + i).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.isekai_mahou.mana_conductor.range", WirelessMana.RANGE, WirelessMana.LOSS_STEP,
                WirelessMana.LOSS_PERCENT).withStyle(ChatFormatting.DARK_GRAY));
    }
}
