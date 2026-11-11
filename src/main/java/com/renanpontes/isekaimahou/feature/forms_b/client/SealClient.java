package com.renanpontes.isekaimahou.feature.forms_b.client;

import java.util.List;

import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.seal.SealData;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Cliente do Selo: linha no tooltip da arma (magia + cargas) e faíscas na mão de quem a segura. */
public final class SealClient {

    public static void tooltip(ItemTooltipEvent event) {
        SealData d = event.getItemStack().get(FormsBRegistry.WEAPON_SEAL.get());
        if (d == null) return;
        List<Component> tip = event.getToolTip();
        Component name = d.recipe().name().isEmpty()
                ? Component.translatable("tooltip.isekai_mahou.forms_b.seal_unnamed")
                : Component.literal(d.recipe().name());
        int at = Math.min(1, tip.size());
        tip.add(at, Component.translatable("tooltip.isekai_mahou.forms_b.seal", name.copy().withColor(d.rgb()), d.element().displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(at + 1, Component.translatable("tooltip.isekai_mahou.forms_b.seal_charges", d.charges(), d.maxCharges())
                .withStyle(ChatFormatting.GRAY));
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        boolean reduced = FormsBUtil.reducedParticles();
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > 32 * 32 || p.isInvisible()) continue;
            ItemStack held = p.getMainHandItem();
            SealData d = held.get(FormsBRegistry.WEAPON_SEAL.get());
            if (d == null || p.getRandom().nextInt(reduced ? 12 : 4) != 0) continue;
            // Mão principal: ao lado do corpo, girando com ele
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            float side = p.getMainArm() == HumanoidArm.RIGHT ? -1F : 1F;
            double hx = p.getX() + Math.cos(yaw) * 0.4 * side - Math.sin(yaw) * 0.25;
            double hz = p.getZ() + Math.sin(yaw) * 0.4 * side + Math.cos(yaw) * 0.25;
            double hy = p.getY() + p.getBbHeight() * 0.45 + p.getRandom().nextDouble() * 0.5;
            mc.level.addParticle(FormsBUtil.dust(d.rgb(), 0.6F), hx, hy, hz, 0, 0.02, 0);
        }
    }

    private SealClient() {}
}
