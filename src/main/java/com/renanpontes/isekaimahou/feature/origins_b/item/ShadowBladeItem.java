package com.renanpontes.isekaimahou.feature.origins_b.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Lâmina Sombria (Arma Sombria): espada conjurada pelo Pacto. Some quando o tempo acaba, quando é largada,
 * guardada fora do inventário ou quando o dono morre. Cada golpe murcha o alvo e devolve um pouco de vida.
 * O tempo fica num componente ({@link ShadowBladeTimer}) ancorado ao relógio do mundo: guardá-la num baú,
 * funil ou moldura não pausa a contagem (ao voltar para um inventário ela já some).
 */
public class ShadowBladeItem extends SwordItem {
    /** Atualiza o componente a cada N ticks (evita sincronizar o item todo tick). */
    private static final int STEP = 10;
    /** Duração de uma lâmina que apareceu sem tempo (comando/criativo). */
    private static final int DEFAULT_TICKS = 900;

    public ShadowBladeItem(Properties props) {
        super(Tiers.NETHERITE, props);
    }

    /** Cria uma lâmina com o tempo e o dano (escala com o poder da magia), já ancorada ao relógio do mundo. */
    public static ItemStack create(int ticks, float power, long gameTime) {
        ItemStack stack = new ItemStack(ObRegistries.SHADOW_BLADE.get());
        stack.set(ObRegistries.SHADOW_BLADE_TIMER.get(), new ShadowBladeTimer(ticks, ticks, gameTime + ticks));
        double damage = Mth.clamp(6.0 + 3.0 * (power - 1.0), 5.0, 16.0);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.2, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build());
        return stack;
    }

    public static boolean isBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ObRegistries.SHADOW_BLADE.get());
    }

    /** Efeito de dissipar no lugar onde a lâmina estava. */
    public static void vanishFx(ServerLevel level, double x, double y, double z) {
        level.sendParticles(Vfx.ink(), x, y, z, 10, 0.2, 0.25, 0.2, 0.03);
        level.sendParticles(ObFx.dust(ObFx.VIOLET, 1.0F), x, y, z, 12, 0.25, 0.3, 0.25, 0);
        level.playSound(null, x, y, z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        long now = level.getGameTime();
        ShadowBladeTimer t = stack.get(ObRegistries.SHADOW_BLADE_TIMER.get());
        if (t == null || t.expires() <= 0) {
            // Lâmina sem tempo (comando/criativo) ou de versão antiga: ancora no relógio do mundo
            int left = t == null ? DEFAULT_TICKS : Math.max(1, t.left());
            int total = t == null ? DEFAULT_TICKS : Math.max(left, t.total());
            stack.set(ObRegistries.SHADOW_BLADE_TIMER.get(), new ShadowBladeTimer(left, total, now + left));
            return;
        }
        // O tempo corre pelo relógio do mundo: guardada num baú/moldura ela continua se desfazendo
        int left = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, t.expires() - now));
        if (left > 0 && now % STEP != 0) return;
        if (left <= 0) {
            stack.setCount(0);
            if (level instanceof ServerLevel sl) vanishFx(sl, entity.getX(), entity.getY() + 1.0, entity.getZ());
            if (entity instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.blade_vanished").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            return;
        }
        stack.set(ObRegistries.SHADOW_BLADE_TIMER.get(), new ShadowBladeTimer(left, t.total(), t.expires()));
        if (selected && level instanceof ServerLevel sl && level.getGameTime() % 20 == 0) {
            // Fumaça escorrendo da lâmina na mão
            sl.sendParticles(Vfx.ink(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0), attacker);
        attacker.heal(1.0F);
        if (attacker.level() instanceof ServerLevel sl) {
            sl.sendParticles(Vfx.ink(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.3, 0.2, 0.04);
            sl.sendParticles(ObFx.dust(ObFx.VIOLET, 1.1F), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0);
        }
        return true;
    }

    /** Conjurada: não gasta durabilidade. */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    /** Some se virar item no chão (largada, baú quebrado, morte com drops). */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel sl) {
            vanishFx(sl, entity.getX(), entity.getY() + 0.3, entity.getZ());
            entity.discard();
        }
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !ItemStack.isSameItem(oldStack, newStack);
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
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    // ------------------------------------------------------------------ barra de tempo
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(ObRegistries.SHADOW_BLADE_TIMER.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        ShadowBladeTimer t = stack.get(ObRegistries.SHADOW_BLADE_TIMER.get());
        return t == null ? 13 : Math.round(13 * t.fraction());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x9A4FE0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.shadow_blade.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        ShadowBladeTimer t = stack.get(ObRegistries.SHADOW_BLADE_TIMER.get());
        if (t != null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.shadow_blade.time", Math.max(1, t.left() / 20)).withStyle(ChatFormatting.GRAY));
        }
    }
}
