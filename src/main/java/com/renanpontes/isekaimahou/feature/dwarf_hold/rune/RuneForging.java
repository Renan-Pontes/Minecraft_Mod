package com.renanpontes.isekaimahou.feature.dwarf_hold.rune;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Gravação de runas na Forja Rúnica (lado do servidor). */
public final class RuneForging {
    public static final int INGOTS = 2;
    public static final int LEVELS = 3;
    public static final String FLAG = "dwarf_hold.rune";
    private static final String MSG = "message.isekai_mahou.dwarf_hold.forge.";

    public static void tryEngrave(Player player, BlockPos pos) {
        ItemStack gear = player.getMainHandItem();
        ItemStack essence = player.getOffhandItem();
        Runes.Target target = Runes.target(gear);
        if (target == null) {
            showHelp(player);
            return;
        }
        Rune rune = Rune.byEssence(essence);
        if (rune == null) {
            fail(player, Component.translatable(MSG + "need_essence"));
            return;
        }
        if (rune == Runes.of(gear)) {
            fail(player, Component.translatable(MSG + "same", rune.displayName()));
            return;
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && countIngots(player) < INGOTS) {
            fail(player, Component.translatable(MSG + "need_mithril", INGOTS));
            return;
        }
        if (!free && player.experienceLevel < LEVELS) {
            fail(player, Component.translatable(MSG + "need_xp", LEVELS));
            return;
        }
        if (!free) {
            takeIngots(player, INGOTS);
            essence.shrink(1);
            player.giveExperienceLevels(-LEVELS);
        }
        boolean replaced = Runes.of(gear) != null;
        gear.set(DwarfRegistry.RUNE.get(), rune.id);
        if (player.level() instanceof ServerLevel sl) {
            double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
            sl.sendParticles(ElementFx.dust(rune.element, 1.2F), x, y + 0.2, z, 30, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ParticleTypes.LAVA, x, y, z, 6, 0.2, 0.1, 0.2, 0);
            sl.sendParticles(ParticleTypes.ENCHANT, x, y + 0.6, z, 40, 0.4, 0.4, 0.4, 0.6);
            sl.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9F, 0.8F);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.9F);
            sl.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
        }
        Component name = rune.displayName().withStyle(s -> s.withColor(rune.color));
        player.displayClientMessage(Component.translatable(MSG + (replaced ? "replaced" : "done"), name, gear.getHoverName())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal("  ").append(rune.effectText(target.kind()))
                .append(Runes.isMithril(gear) ? Component.translatable(MSG + "mithril_bonus") : Component.empty())
                .withStyle(ChatFormatting.GRAY), false);
        if (player instanceof ServerPlayer sp) {
            MagicApi.get(sp).setFlag(FLAG, true);
            MagicApi.addCounter(sp, "dwarf_hold.runes", 1);
        }
    }

    public static void showHelp(Player player) {
        player.displayClientMessage(Component.translatable(MSG + "help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable(MSG + "help", INGOTS, LEVELS).withStyle(ChatFormatting.GRAY), false);
        for (Rune r : Rune.values()) {
            player.displayClientMessage(Component.literal(" ◆ ").withStyle(s -> s.withColor(r.color))
                    .append(r.displayName().withStyle(s -> s.withColor(r.color)))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(MSG + "help.line", r.element.displayName(),
                            r.effectText(Rune.Kind.WEAPON), r.effectText(Rune.Kind.ARMOR), r.effectText(Rune.Kind.TOOL))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        player.displayClientMessage(Component.translatable(MSG + "help.mithril").withStyle(ChatFormatting.AQUA), false);
    }

    private static void fail(Player player, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    private static int countIngots(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(DwarfRegistry.MITHRIL_INGOT.get())) n += s.getCount();
        return n;
    }

    private static void takeIngots(Player player, int amount) {
        for (ItemStack s : player.getInventory().items) {
            if (amount <= 0) return;
            if (s.is(DwarfRegistry.MITHRIL_INGOT.get())) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
    }

    private RuneForging() {}
}
