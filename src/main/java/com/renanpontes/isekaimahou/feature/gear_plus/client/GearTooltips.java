package com.renanpontes.isekaimahou.feature.gear_plus.client;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.gear_plus.GemType;
import com.renanpontes.isekaimahou.feature.gear_plus.Gems;
import com.renanpontes.isekaimahou.feature.gear_plus.SetBonuses;
import com.renanpontes.isekaimahou.feature.gear_plus.WeaponBond;
import com.renanpontes.isekaimahou.feature.gear_plus.item.GemItem;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Linhas extras na dica dos itens (curtas, só quando fazem sentido):
 * <ul>
 *   <li>peça de armadura de um conjunto: nome do conjunto, peças vestidas (n/4) e o bônus (verde quando completo);</li>
 *   <li>equipamento com encaixes: as gemas encaixadas (para as classes que encaixam, também os encaixes vazios);</li>
 *   <li>arma de classe que desperta: nível do Vínculo, abates e a habilidade do despertar.</li>
 * </ul>
 */
final class GearTooltips {
    static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        if (stack.isEmpty()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        List<Component> tip = e.getToolTip();
        MagicData md = ClientMagic.data();
        setLines(stack, player, tip);
        gemLines(stack, md, tip);
        bondLines(stack, md, tip, e.getFlags().hasShiftDown());
    }

    private static void setLines(ItemStack stack, Player player, List<Component> tip) {
        String family = SetBonuses.familyOf(stack);
        if (family == null) return;
        int[] worn = SetBonuses.worn(player, family);
        boolean complete = worn[0] >= SetBonuses.PIECES;
        int rank = worn[0] > 0 ? Math.min(worn[1], SetBonuses.rankOf(stack)) : SetBonuses.rankOf(stack);
        tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.set", SetBonuses.familyName(family), worn[0], SetBonuses.PIECES)
                .withStyle(complete ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        for (SetBonuses.Line line : SetBonuses.lines(family)) {
            tip.add(Component.literal(" ").append(line.describe(rank))
                    .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
    }

    private static void gemLines(ItemStack stack, MagicData md, List<Component> tip) {
        int max = Gems.maxSockets(stack);
        if (max == 0) return;
        List<GemItem> gems = Gems.gems(stack);
        boolean crafter = Gems.canUse(md.path());
        if (gems.isEmpty() && !crafter) return;
        int unlocked = Gems.unlocked(md.level(), stack);
        MutableComponent sockets = Component.empty();
        for (int i = 0; i < max; i++) {
            if (i < gems.size()) sockets.append(Component.literal("◆").withColor(gems.get(i).type().color));
            else sockets.append(Component.literal("◇").withStyle(i < unlocked ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.sockets", sockets).withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < gems.size(); i++) {
            GemItem gem = gems.get(i);
            boolean active = crafter && i < unlocked && md.level() >= GemType.GRADE_LEVEL[gem.grade() - 1];
            tip.add(Component.literal(" ").append(gem.type().describe(gem.grade()))
                    .withStyle(active ? ChatFormatting.BLUE : ChatFormatting.DARK_GRAY));
        }
        if (!gems.isEmpty() && !crafter) {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.sockets.dormant").withStyle(ChatFormatting.DARK_GRAY));
        } else if (crafter && gems.size() < max && gems.size() >= unlocked) {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.sockets.next", Gems.levelFor(stack, gems.size()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void bondLines(ItemStack stack, MagicData md, List<Component> tip, boolean shift) {
        MagicPath path = md.path();
        if (!WeaponBond.isBondWeapon(path, stack)) return;
        int kills = ClientGear.kills();
        int level = WeaponBond.level(kills, md.level());
        if (level >= WeaponBond.MAX_LEVEL) {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.bond.awakened",
                    WeaponBond.abilityName(path).withColor(path.rgb())).withStyle(ChatFormatting.GOLD));
            if (shift) tip.add(WeaponBond.abilityDesc(path).withStyle(ChatFormatting.GRAY));
        } else if (kills >= WeaponBond.killsFor(WeaponBond.MAX_LEVEL)) {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.bond.waiting", WeaponBond.AWAKEN_PLAYER_LEVEL)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.bond", level, kills, WeaponBond.killsFor(level + 1))
                    .withStyle(ChatFormatting.GOLD));
        }
        int pct = Math.round(WeaponBond.damageBonus(level) * 1000F);
        if (pct > 0) {
            tip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.bond.damage", pct / 10 + (pct % 10 == 0 ? "" : "." + pct % 10))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private GearTooltips() {}
}
