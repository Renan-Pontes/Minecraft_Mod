package com.renanpontes.isekaimahou.feature.mana_core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlockEntity;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "mana_core" — v1.5: Núcleo de Mana (banco de mana enorme, depositar/retirar), Condutor de Mana (liga
 * pedestais e condensadores ao núcleo sem fio), SOBRECARGA (mana acima do máximo, ver {@link Overcharge}) e o efeito
 * Intoxicação por Mana.
 * <p>Comandos de teste (operador): {@code /mahou mana_core give | fill <n> | overcharge <n>}.
 */
public class ManaCoreModule implements Module {
    @Override
    public String id() {
        return "mana_core";
    }

    @Override
    public void init(IEventBus modBus) {
        ManaCoreRegistries.register(modBus);
        ManaCoreNet.register(modBus);
        modBus.addListener(this::onCreativeTab);
        NeoForge.EVENT_BUS.addListener(Overcharge::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Overcharge::onLogin);
        NeoForge.EVENT_BUS.addListener(Overcharge::onRespawn);
        NeoForge.EVENT_BUS.addListener(Overcharge::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(Overcharge::onLogout);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ManaIntoxicationEffect::clampDamage);
        registerCommands();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        e.accept(ManaCoreRegistries.MANA_CORE_ITEM.get());
        e.accept(ManaCoreRegistries.MANA_CORE_ITEM.get().full());
        e.accept(ManaCoreRegistries.MANA_CONDUCTOR.get());
    }

    /** /mahou mana_core give | fill [n] | overcharge <n> */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("mana_core")
                .then(MahouCommands.op("give").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    give(p, new ItemStack(ManaCoreRegistries.MANA_CORE_ITEM.get()));
                    give(p, new ItemStack(ManaCoreRegistries.MANA_CONDUCTOR.get()));
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mana_core.given")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))
                .then(MahouCommands.op("fill")
                        .executes(c -> fill(c.getSource().getPlayerOrException(), ManaCoreBlockEntity.CAPACITY))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, ManaCoreBlockEntity.CAPACITY))
                                .executes(c -> fill(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "amount")))))
                .then(MahouCommands.op("overcharge")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int n = IntegerArgumentType.getInteger(c, "amount");
                            Overcharge.set(p, n);
                            Overcharge.sync(p, true);
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mana_core.overcharge", n,
                                    Overcharge.level(n)).withColor(Overcharge.RGB), false);
                            return 1;
                        })))));
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    /** Enche o Núcleo que o operador está olhando (até 8 blocos). */
    private static int fill(ServerPlayer p, int amount) {
        HitResult hit = p.pick(8.0, 0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                && p.level().getBlockEntity(bhr.getBlockPos()) instanceof ManaCoreBlockEntity core) {
            core.setManaDirect(amount);
            p.sendSystemMessage(Component.translatable("command.isekai_mahou.mana_core.filled", core.mana(), ManaCoreBlockEntity.CAPACITY)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return 1;
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.mana_core.look_at_core").withStyle(ChatFormatting.RED));
        return 0;
    }
}
