package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Armas de golpe do Arsenal: Lâmina do Pacto e Foice (Mágicas) e Espada Longa, Maça Sagrada, Machado Grande,
 * Manoplas de Ki, Lâmina do Cavaleiro e Adagas Gêmeas (Guerreiras/Ladino). São espadas de verdade (encantamentos de
 * espada, conserto com Cristal de Mana na bigorna); só as lâminas e a foice varrem. O Machado Grande quebra escudos.
 * Dano, velocidade e bônus vêm de {@link WeaponKind}; as passivas, de {@code ArmoryPassives}/{@code ClassWeaponPassives}.
 */
public class ArmoryMeleeItem extends SwordItem implements ArmoryWeapon {
    private final WeaponKind kind;
    private final int tier;

    public ArmoryMeleeItem(WeaponKind kind, int tier, Properties props) {
        super(tierOf(tier), props.attributes(ArmoryWeapon.attributes(kind, tier)));
        this.kind = kind;
        this.tier = tier;
    }

    /** Material por tier: durabilidade crescente, minera como ferro/diamante/netherite (só teia, como espada). */
    private static Tier tierOf(int tier) {
        TagKey<Block> incorrect = switch (tier) {
            case 1 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 2, 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
        return new SimpleTier(incorrect, WeaponKind.DURABILITY[tier - 1], 6.0F + tier, 0.0F, 14 + tier * 2,
                () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()));
    }

    @Override
    public WeaponKind kind() {
        return kind;
    }

    @Override
    public int tier() {
        return tier;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        // Maça, machado, manoplas e adagas não fazem a varrida de espada
        if (ability == ItemAbilities.SWORD_SWEEP && !kind.sweeps()) return false;
        return super.canPerformAction(stack, ability);
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        // Machado Grande: como um machado, um golpe derruba a guarda do escudo por 5 s
        return kind.style == WeaponKind.Style.AXE || super.canDisableShield(stack, shield, entity, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ArmoryWeapon.appendTooltip(this, getDescriptionId() + ".desc", tooltip);
    }
}
