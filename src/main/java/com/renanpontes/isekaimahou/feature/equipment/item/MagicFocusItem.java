package com.renanpontes.isekaimahou.feature.equipment.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.equipment.FocusOrb;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Foco Mágico (espaço "magic_focus" do Curios): aumenta o poder das magias de um elemento.
 * Fusões contam os dois pais (um Foco de Fogo também fortalece Magma, Vapor, Raio, Sol e Chama Infernal).
 * O Foco Arcano ({@code element == null}) dá um bônus menor para qualquer elemento.
 * O bônus é aplicado em {@code FocusBonuses} (SpellCastEvent.Pre).
 */
public class MagicFocusItem extends Item implements ICurioItem {
    private final Element element;
    private final float bonus;
    private final boolean greater;

    public MagicFocusItem(Element element, float bonus, boolean greater, Properties props) {
        super(props);
        this.element = element;
        this.bonus = bonus;
        this.greater = greater;
    }

    /** Elemento do foco, ou null no Foco Arcano (vale para todos). */
    public Element element() { return element; }
    public boolean isGreater() { return greater; }

    /** Elemento usado no visual (partículas, brilho). */
    public Element fxElement() { return element == null ? Element.ARCANE : element; }

    /** Bônus (0.15 = +15%) que este foco dá a uma magia do elemento informado. */
    public float bonusFor(Element spellElement) {
        if (spellElement == null) return 0F;
        if (element == null) return bonus;
        return spellElement.matches(element) ? bonus : 0F;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return greater || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String pct = String.valueOf(Math.round(bonus * 100));
        if (element == null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.arcane_focus_bonus", pct)
                    .withColor(Element.ARCANE.color));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.focus_bonus", pct, element.displayName())
                    .withColor(element.color));
            MutableComponent fusions = Component.empty();
            boolean first = true;
            for (Element f : Element.FUSIONS) {
                if (!f.matches(element)) continue;
                if (!first) fusions.append(", ");
                fusions.append(f.displayName());
                first = false;
            }
            tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.focus_fusions", fusions)
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.equipment.focus_hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ Curios

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, greater ? 0.75F : 1.2F);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        LivingEntity e = slotContext.entity();
        // tickCount > 20: não solta partículas quando o inventário é carregado ao entrar no mundo
        if (e.level() instanceof ServerLevel level && e.tickCount > 20 && !ItemStack.isSameItem(prevStack, stack)) {
            ElementFx.burst(level, FocusOrb.position(e, 1.0F), fxElement(), greater ? 16 : 9, 0.25);
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity e = slotContext.entity();
        // Brilho ambiente em volta do orbe flutuante (só no cliente; o orbe é desenhado pelo renderer do módulo)
        if (!e.level().isClientSide || e.isInvisible() || slotContext.cosmetic()) return;
        if (e.tickCount % (greater ? 5 : 9) != 0) return;
        Vec3 p = FocusOrb.position(e, 1.0F);
        double ox = (e.getRandom().nextDouble() - 0.5) * 0.25;
        double oz = (e.getRandom().nextDouble() - 0.5) * 0.25;
        e.level().addParticle(ElementFx.dust(fxElement(), greater ? 0.7F : 0.5F), p.x + ox, p.y - 0.05, p.z + oz,
                0, 0.02, 0);
        if (greater && e.tickCount % 20 == 0) {
            e.level().addParticle(ElementFx.signature(fxElement()), p.x, p.y + 0.1, p.z, 0, 0.01, 0);
        }
    }
}
