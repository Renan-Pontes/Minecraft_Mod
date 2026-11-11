package com.renanpontes.isekaimahou.feature.class_rogue.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueKeys;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

/**
 * Adagas do Ladino: leves e rápidas (ataque mais veloz que uma espada, dano menor). Somam com as passivas da classe
 * (crítico e dano pelas costas, ver {@code RogueCombat}). Tier III+: 25% de abrir Sangramento; tier IV ("Lâmina
 * Fantasma"): quem derruba um inimigo com ela some por 2 s.
 * <ul>
 *   <li>{@code rogue_dagger} — Adaga Furtiva (kit inicial);</li>
 *   <li>{@code rogue_dagger_t1..t4} — Punhal do Batedor, Presa da Sombra, Ferrão do Mestre-Ladrão, Lâmina Fantasma
 *   (entregues pela Guilda e pelas Provações; os ranks do Livro-Razão também dão a II e a IV).</li>
 * </ul>
 */
public class RogueDaggerItem extends SwordItem {
    private final int tierIndex;

    public RogueDaggerItem(int tierIndex, Tier tier, int damage, float speed, Properties props) {
        super(tier, props.attributes(SwordItem.createAttributes(tier, damage, speed)));
        this.tierIndex = tierIndex;
    }

    /** 0 = adaga do kit; 1..4 = tiers. */
    public int tierIndex() {
        return tierIndex;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean r = super.hurtEnemy(stack, target, attacker);
        if (!(attacker.level() instanceof ServerLevel level)) return r;
        if (tierIndex >= 3 && attacker.getRandom().nextFloat() < 0.25F) {
            Statuses.applyUpTo(target, Statuses.Kind.BLEEDING, 1, 100, attacker, 1.0F, 4);
        }
        if (tierIndex >= 4 && !target.isAlive()) {
            attacker.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 14, 0.3, 0.6, 0.3, 0.02);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                    SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        return r;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(RogueKeys.k("item.isekai_mahou.rogue_dagger.flavor.", tierIndex)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.dagger.passive").withStyle(ChatFormatting.DARK_PURPLE));
        if (tierIndex >= 3) tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.dagger.bleed").withStyle(ChatFormatting.RED));
        if (tierIndex >= 4) tooltip.add(Component.translatable("tooltip.isekai_mahou.class_rogue.dagger.vanish").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
