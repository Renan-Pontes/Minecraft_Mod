package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Uma arma do Arsenal (cajado, varinha, lâmina, foice, espada longa, maça, machado, manoplas, adagas, arco, besta ou
 * alaúde) de um tier 1..4. Os bônus ficam no componente de atributos do item (mão principal), então valem sozinhos e
 * aparecem no tooltip do vanilla ("Na mão principal: +8% Poder Mágico ..."). O bônus de dano dos disparos (que não é
 * atributo) vem em linhas próprias do tooltip e é aplicado em {@code ArmoryArrows}.
 */
public interface ArmoryWeapon {
    ResourceLocation SPELL_POWER_ID = IsekaiMahou.id("armory_weapon_spell_power");
    ResourceLocation MAX_MANA_ID = IsekaiMahou.id("armory_weapon_max_mana");
    ResourceLocation MANA_REGEN_ID = IsekaiMahou.id("armory_weapon_mana_regen");
    ResourceLocation ARMOR_ID = IsekaiMahou.id("armory_weapon_armor");
    ResourceLocation KNOCKBACK_ID = IsekaiMahou.id("armory_weapon_knockback");
    ResourceLocation MOVE_ID = IsekaiMahou.id("armory_weapon_move");
    /** Prefixo das chaves de tooltip do módulo. */
    String TIP = "tooltip.isekai_mahou.armory.";

    WeaponKind kind();

    int tier();

    /** A arma da pilha, ou null se não for do Arsenal. */
    static ArmoryWeapon of(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ArmoryWeapon w ? w : null;
    }

    /** true se a pilha é a arma {@code kind} no tier {@code tier} ou acima. */
    static boolean is(ItemStack stack, WeaponKind kind, int minTier) {
        ArmoryWeapon w = of(stack);
        return w != null && w.kind() == kind && w.tier() >= minTier;
    }

    /** Todos os atributos da arma no tier (mão principal). */
    static ItemAttributeModifiers attributes(WeaponKind kind, int tier) {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        float damage = kind.meleeDamage(tier);
        if (damage > 0) {
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            b.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, kind.attackSpeed(),
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (kind.armor(tier) > 0) {
            b.add(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, kind.armor(tier),
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (kind.knockbackResistance(tier) > 0) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_ID, kind.knockbackResistance(tier),
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (kind.moveSpeed(tier) > 0) {
            b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(MOVE_ID, kind.moveSpeed(tier),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        }
        // ADD_MULTIPLIED_BASE sobre base 1.0 = soma simples, mas o tooltip mostra em % (+8% Poder Mágico)
        b.add(CoreRegistries.SPELL_POWER, new AttributeModifier(SPELL_POWER_ID, kind.spellPower(tier),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        b.add(CoreRegistries.MAX_MANA, new AttributeModifier(MAX_MANA_ID, kind.maxMana(tier),
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (kind.manaRegen(tier) > 0) {
            b.add(CoreRegistries.MANA_REGEN, new AttributeModifier(MANA_REGEN_ID, kind.manaRegen(tier),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        }
        return b.build();
    }

    /** Linhas comuns do tooltip: tier, classe, história, traço da arma, disparo, passiva do tier IV e dica de melhoria. */
    static void appendTooltip(ArmoryWeapon w, String descKey, List<Component> tooltip) {
        WeaponKind k = w.kind();
        int t = w.tier();
        tooltip.add(Component.translatable(TIP + "tier", WeaponKind.roman(t),
                Component.translatable(TIP + "tier_name." + t)).withStyle(ChatFormatting.GOLD));
        tooltip.add((k.isGeneric() ? Component.translatable(TIP + "generic_weapon", k.ownerName())
                : Component.translatable(TIP + "class_weapon", k.ownerName())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(descKey)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (k == WeaponKind.REAPER_SCYTHE) {
            tooltip.add(Component.translatable(TIP + "reaper_scythe.harvest").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (k.category() != com.renanpontes.isekaimahou.core.MagicPath.Category.MAGIC) {
            // Traço de todas as armas avulsas da v0.9 (golpe duplo, quebra-escudos, instrumento...)
            tooltip.add(Component.translatable(TIP + "trait", Component.translatable(TIP + k.id + ".trait"))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(TIP + k.id + ".trait.desc").withStyle(ChatFormatting.GRAY));
        }
        if (k.isRanged()) {
            if (Math.round((k.projectileDamage(t) - 1F) * 100F) != 0) {
                tooltip.add(Component.translatable(TIP + "projectile_damage", pct(k.projectileDamage(t)))
                        .withStyle(ChatFormatting.BLUE));
            }
            if (Math.round((k.projectileSpeed(t) - 1F) * 100F) != 0) {
                tooltip.add(Component.translatable(TIP + "projectile_speed", pct(k.projectileSpeed(t)))
                        .withStyle(ChatFormatting.BLUE));
            }
            if (k.style == WeaponKind.Style.BOW) {
                tooltip.add(Component.translatable(TIP + "draw_time", seconds(k.drawTicks(t) / 20F)).withStyle(ChatFormatting.BLUE));
            } else {
                tooltip.add(Component.translatable(TIP + "charge_time", seconds(k.chargeSeconds(t))).withStyle(ChatFormatting.BLUE));
            }
        }
        if (t >= WeaponKind.MAX_TIER) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(TIP + "passive", Component.translatable(TIP + k.id + ".passive"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(TIP + k.id + ".passive.desc").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(TIP + "upgrade_hint", Component.translatable(TIP + "material.t" + (t + 1)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** "+18%" a partir de um multiplicador 1.18. */
    private static String pct(float mult) {
        return String.format(Locale.ROOT, "+%d%%", Math.round((mult - 1F) * 100F));
    }

    private static String seconds(float s) {
        return String.format(Locale.ROOT, "%.2f", s).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
