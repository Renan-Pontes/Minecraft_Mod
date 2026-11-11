package com.renanpontes.isekaimahou.feature.class_smith.item;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeStats;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * "Lâmina Forjada": UMA arma que vira adaga, espada, espada longa, montante, machado, lança, martelo ou foice conforme as
 * partes do componente {@link ForgedBlade}. Atributos, durabilidade, raridade e nome vêm das partes ({@link #set});
 * o modelo 3D é montado peça a peça pelo renderer do cliente (comprimento, largura, curva, ponta, serrilha, acabamento,
 * runa e empunhadura mudam o desenho de verdade). Efeitos nos golpes: {@code SmithEvents}.
 */
public class ForgedBladeItem extends Item {

    public ForgedBladeItem(Properties props) {
        super(props.stacksTo(1).durability(ForgedBlade.DEFAULT.material().durability)
                .attributes(BladeStats.of(ForgedBlade.DEFAULT).modifiers()));
    }

    /** As partes da lâmina (a adaga de ferro padrão se o item não tiver o componente). */
    public static ForgedBlade get(ItemStack stack) {
        ForgedBlade b = stack.get(SmithRegistries.BLADE.get());
        return b == null ? ForgedBlade.DEFAULT : b;
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ForgedBladeItem;
    }

    /** Lâmina nova com as partes dadas. */
    public static ItemStack create(ForgedBlade blade) {
        ItemStack stack = new ItemStack(SmithRegistries.FORGED_BLADE.get());
        set(stack, blade);
        return stack;
    }

    /**
     * Grava as partes e recalcula tudo que depende delas: atributos, durabilidade máxima (o desgaste proporcional é
     * mantido), raridade, resistência ao fogo e brilho de encantamento (só no acabamento Rúnico).
     */
    public static void set(ItemStack stack, ForgedBlade blade) {
        ForgedBlade b = blade.clamped();
        BladeStats stats = BladeStats.of(b);
        int oldMax = stack.getMaxDamage();
        float wear = oldMax > 0 ? stack.getDamageValue() / (float) oldMax : 0F;
        stack.set(SmithRegistries.BLADE.get(), b);
        stack.set(DataComponents.MAX_DAMAGE, stats.durability());
        stack.set(DataComponents.DAMAGE, Math.min(stats.durability() - 1, Math.round(wear * stats.durability())));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stats.modifiers());
        stack.set(DataComponents.RARITY, switch (b.quality()) {
            case 1, 2 -> Rarity.COMMON;
            case 3 -> Rarity.UNCOMMON;
            case 4 -> Rarity.RARE;
            default -> Rarity.EPIC;
        });
        if (b.material().fireResistant()) stack.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        else stack.remove(DataComponents.FIRE_RESISTANT);
        if (b.finish() == ForgedBlade.Finish.RUNIC) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        else stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    @Override
    public Component getName(ItemStack stack) {
        ForgedBlade b = get(stack);
        MutableComponent name = Component.translatable("blade.isekai_mahou.name", b.typeName(), b.material().displayName());
        if (b.masterwork()) name = Component.translatable("blade.isekai_mahou.name_masterwork", name);
        return name;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    /** Ferreiro: suas armas gastam metade (bônus de classe "o equipamento dura muito mais"). */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        if (entity instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.SMITH && amount > 0) {
            int kept = 0;
            for (int i = 0; i < amount; i++) if (sp.getRandom().nextBoolean()) kept++;
            return amount - kept;
        }
        return amount;
    }

    /** Machados e martelos quebram a guarda de escudos, como o machado vanilla. */
    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        BladeFamily f = get(stack).family();
        return f == BladeFamily.AXE || f == BladeFamily.HAMMER;
    }

    /** Corte em área (varrida): lâminas a partir da espada, foices e qualquer arma com curva. */
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (ability == ItemAbilities.SWORD_SWEEP) {
            ForgedBlade b = get(stack);
            return (b.family() == BladeFamily.BLADE && b.length() >= 1) || b.family() == BladeFamily.SCYTHE || b.curve() > 0;
        }
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(Blocks.COBWEB) ? 15.0F : 1.0F;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return get(stack).material().enchantability;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(get(stack).material().repairItem().get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ForgedBlade b = get(stack);
        BladeStats s = BladeStats.of(b);
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.quality", stars(b.quality()), b.quality())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.points", b.points(), b.capacity())
                .withStyle(b.points() >= b.capacity() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.parts",
                b.length(), b.width(), b.curve(), b.tip(), b.serration()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.style", b.grip().displayName(),
                b.finish().displayName(), Component.translatable(SmithKeys.BLADE_WEIGHT + s.weightClass()))
                .withStyle(ChatFormatting.GRAY));
        Element rune = b.runeElement();
        if (rune != null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.rune", rune.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (b.tip() > 0) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.tip",
                    Math.round(b.tip() * BladeStats.PIERCE_PER_TIP * 100), Math.round(b.tip() * BladeStats.CRIT_PER_TIP * 100))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (b.serration() > 0) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.serration", b.serration())
                    .withStyle(ChatFormatting.DARK_RED));
        }
        tooltip.add(Component.translatable(SmithKeys.TIP_FAMILY + b.family().id)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(SmithKeys.TIP_FINISH + b.finish().id)
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!b.smith().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.smith", b.smith())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (!flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.more").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.blade.how").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    public static String stars(int quality) {
        return "★".repeat(Math.max(0, quality)) + "☆".repeat(Math.max(0, ForgedBlade.MAX_QUALITY - quality));
    }
}
