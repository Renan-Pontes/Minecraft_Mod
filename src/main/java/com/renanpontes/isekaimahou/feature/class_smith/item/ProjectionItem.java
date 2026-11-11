package com.renanpontes.isekaimahou.feature.class_smith.item;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Projeção (Traçar: Ligar): a cópia TEMPORÁRIA de uma arma que o Ferreiro viu. Bate como a original (mesmos
 * modificadores de ataque e encantamentos) e é desenhada com o modelo dela, com brilho de mana. Some sozinha quando
 * o tempo acaba, assim que sai do inventário (jogada no chão) e nunca serve de material — o metal é só mana.
 */
public class ProjectionItem extends Item {
    /** Projeções ao mesmo tempo por jogador (a mais antiga some). */
    public static final int MAX_HELD = 3;

    public ProjectionItem(Properties props) {
        super(props);
    }

    /** Cria a projeção de {@code source} que dura até o tick {@code until} do mundo. */
    public static ItemStack create(ItemStack source, long until) {
        ItemStack src = source.isEmpty() ? new ItemStack(Items.IRON_SWORD) : source.copyWithCount(1);
        if (src.getItem() instanceof ProjectionItem) {
            ItemStack inner = src.get(SmithRegistries.PROJECTION_SOURCE.get());
            src = inner == null || inner.isEmpty() ? new ItemStack(Items.IRON_SWORD) : inner.copyWithCount(1);
        }
        ItemStack out = new ItemStack(SmithRegistries.PROJECTION.get());
        out.set(SmithRegistries.PROJECTION_SOURCE.get(), src);
        out.set(SmithRegistries.PROJECTION_UNTIL.get(), until);
        ItemAttributeModifiers mods = src.getAttributeModifiers();
        if (mods != null && !mods.modifiers().isEmpty()) out.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
        var ench = src.get(DataComponents.ENCHANTMENTS);
        if (ench != null) out.set(DataComponents.ENCHANTMENTS, ench);
        return out;
    }

    /** A arma copiada (para o desenho e o nome). */
    public static ItemStack source(ItemStack projection) {
        ItemStack s = projection.get(SmithRegistries.PROJECTION_SOURCE.get());
        return s == null || s.isEmpty() ? new ItemStack(Items.IRON_SWORD) : s;
    }

    public static boolean expired(ItemStack stack, Level level) {
        Long until = stack.get(SmithRegistries.PROJECTION_UNTIL.get());
        return until == null || level.getGameTime() > until;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.isekai_mahou.smith_projection.of", source(stack).getHoverName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(level instanceof ServerLevel sl)) return;
        if (expired(stack, level)) {
            fade(sl, entity.position().add(0, entity.getBbHeight() * 0.6, 0));
            stack.setCount(0);
        } else if (selected && entity.tickCount % 6 == 0 && entity instanceof LivingEntity le) {
            Vec3 hand = le.getEyePosition().add(le.getLookAngle().scale(0.6)).subtract(0, 0.5, 0);
            sl.sendParticles(SmithFx.dust(0x9FD8FF, 0.6F), hand.x, hand.y, hand.z, 1, 0.15, 0.15, 0.15, 0);
        }
    }

    /** Largada no chão: a projeção se desfaz na hora. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel sl) {
            fade(sl, entity.position());
            entity.discard();
        }
        return true;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.isekai_mahou.class_smith.projection").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.isekai_mahou.class_smith.projection.fade").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void fade(ServerLevel sl, @Nullable Vec3 at) {
        if (at == null) return;
        sl.sendParticles(SmithFx.dust(0x9FD8FF, 1.0F), at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
        SmithFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 1.6F);
    }
}
