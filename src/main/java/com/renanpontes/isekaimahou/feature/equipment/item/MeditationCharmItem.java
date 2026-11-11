package com.renanpontes.isekaimahou.feature.equipment.item;

import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Talismã da Meditação (espaço "charm"): enquanto o jogador medita (agachado, parado, no chão — regra do núcleo),
 * a regeneração de mana sobe e a vida volta aos poucos. O bônus é um modificador TEMPORÁRIO do atributo
 * isekai_mahou:mana_regen, posto e tirado conforme o estado de meditação.
 */
public class MeditationCharmItem extends Item implements ICurioItem {
    public static final ResourceLocation REGEN_MODIFIER = IsekaiMahou.id("equipment_meditation_charm");
    /** +75% de regeneração de mana (soma ao multiplicador base 1.0 do atributo). */
    public static final double REGEN_BONUS = 0.75;
    /** Recupera 1 ponto de vida a cada 3 segundos meditando. */
    public static final int HEAL_INTERVAL = 60;
    private static final DustParticleOptions JADE = new DustParticleOptions(new Vector3f(0.47F, 0.80F, 0.61F), 0.9F);

    public MeditationCharmItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.meditation_regen", Math.round(REGEN_BONUS * 100))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.meditation_heal", HEAL_INTERVAL / 20)
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.meditation_hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0F, 1.4F);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity e = slotContext.entity();
        if (!(e instanceof ServerPlayer sp)) return;
        AttributeInstance regen = sp.getAttribute(CoreRegistries.MANA_REGEN);
        if (regen == null) return;
        // Espectador: o núcleo para de atualizar a meditação, então o estado pode ficar "preso" em true
        boolean meditating = MagicApi.get(sp).meditating && !sp.isSpectator() && sp.isAlive();
        if (!meditating) {
            if (regen.hasModifier(REGEN_MODIFIER)) regen.removeModifier(REGEN_MODIFIER);
            return;
        }
        ServerLevel level = sp.serverLevel();
        if (!regen.hasModifier(REGEN_MODIFIER)) {
            regen.addTransientModifier(new AttributeModifier(REGEN_MODIFIER, REGEN_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.3F);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.equipment.meditation_calm")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), true);
        }
        if (sp.tickCount % HEAL_INTERVAL == 0 && sp.getHealth() < sp.getMaxHealth()) {
            sp.heal(1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
        // Dois pontos de jade girando devagar em volta da cintura
        if (sp.tickCount % 3 == 0) {
            double a = sp.tickCount * 0.12;
            for (int i = 0; i < 2; i++) {
                double ang = a + i * Math.PI;
                level.sendParticles(JADE, sp.getX() + Math.cos(ang) * 0.8, sp.getY() + 0.55, sp.getZ() + Math.sin(ang) * 0.8,
                        1, 0.0, 0.03, 0.0, 0.0);
            }
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer sp && !newStack.is(this)) {
            AttributeInstance regen = sp.getAttribute(CoreRegistries.MANA_REGEN);
            if (regen != null) regen.removeModifier(REGEN_MODIFIER);
        }
    }
}
