package com.renanpontes.isekaimahou.feature.gear_plus.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.gear_plus.GemType;
import com.renanpontes.isekaimahou.feature.gear_plus.Gems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Gema de encaixe. No inventário, com a gema no cursor, clique com o botão DIREITO num equipamento para encaixar
 * (igual a guardar itens num saco). Só Ferreiro, Cavaleiro Rúnico e Encantador conseguem.
 */
public class GemItem extends Item {
    private final GemType type;
    private final int grade;

    public GemItem(GemType type, int grade, Properties properties) {
        super(properties);
        this.type = type;
        this.grade = grade;
    }

    public GemType type() {
        return type;
    }

    /** Lapidação: 1 lascada, 2 lapidada, 3 perfeita. */
    public int grade() {
        return grade;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack gem, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        // Só "rouba" o clique quando o alvo é equipamento: clicar em espaço vazio/pilha continua normal
        if (target.isEmpty() || Gems.maxSockets(target) == 0 || !slot.allowModification(player)) return false;
        Gems.Result r = Gems.trySocket(player, target, this);
        if (r.code() == Gems.Code.OK) {
            gem.shrink(1);
            slot.setChanged();
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.3F);
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.gear_plus.gem.socketed",
                        getDescription(), target.getHoverName()).withStyle(ChatFormatting.GREEN), true);
            }
        } else if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(failure(r).withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    private static net.minecraft.network.chat.MutableComponent failure(Gems.Result r) {
        String base = "message.isekai_mahou.gear_plus.gem.";
        return switch (r.code()) {
            case WRONG_CLASS -> Component.translatable(base + "wrong_class");
            case FULL -> Component.translatable(base + "full");
            case NEED_LEVEL -> Component.translatable(base + "need_level", r.level());
            case GRADE_LEVEL -> Component.translatable(base + "grade_level", r.level());
            default -> Component.translatable(base + "not_socketable");
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(type.describe(grade).withStyle(ChatFormatting.BLUE));
        int need = GemType.GRADE_LEVEL[grade - 1];
        if (need > 1) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.gem.level", need).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.gem.classes").withStyle(ChatFormatting.GRAY));
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.gem.how").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.gem.sockets", Gems.WEAPON_LEVELS[0],
                    Gems.WEAPON_LEVELS[1], Gems.WEAPON_LEVELS[2], Gems.ARMOR_LEVEL).withStyle(ChatFormatting.DARK_GRAY));
            if (grade < 3) {
                tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.gem.combine").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
