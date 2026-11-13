package com.renanpontes.isekaimahou.feature.mana_core.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;
import com.renanpontes.isekaimahou.feature.mana_core.Overcharge;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Parte de cliente do módulo "mana_core": tela do Núcleo, carga no item e o tom roxo na tela da Intoxicação por Mana. */
public class ManaCoreClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ManaCoreRegistries.MANA_CORE_MENU.get(), ManaCoreScreen::new));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                ItemProperties.register(ManaCoreRegistries.MANA_CORE_ITEM.get(), IsekaiMahou.id("core_fill"),
                        (stack, level, entity, seed) -> ManaCoreItem.stored(stack) / (float) ManaCoreBlockEntity.CAPACITY)));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                IsekaiMahou.id("mana_intoxication_tint"), ManaCoreClient::renderTint));
    }

    /**
     * Intoxicação por Mana: vinheta magenta pulsando nas bordas da tela (mais forte a cada nível) e um véu bem leve
     * no meio. No preset gráfico baixo, sem pulso e mais fraca.
     */
    private static void renderTint(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        MobEffectInstance inst = mc.player.getEffect(ManaCoreRegistries.MANA_INTOXICATION);
        if (inst == null) return;
        int amp = Math.min(Overcharge.MAX_LEVEL - 1, inst.getAmplifier());
        boolean fancy = FxQuality.atLeast(MahouConfig.Graphics.MEDIUM);
        float pulse = fancy ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / (420.0 - amp * 25.0)) : 0.5F;
        float strength = Math.min(1F, 0.35F + 0.07F * amp) * (0.7F + 0.3F * pulse) * (fancy ? 1F : 0.75F);
        int rgb = Overcharge.RGB & 0xFFFFFF;
        int w = g.guiWidth(), h = g.guiHeight();
        int edge = Math.max(8, Math.round(Math.min(w, h) * (0.10F + 0.015F * amp)));
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float t = 1F - i / (float) steps;
            int a = Math.round(255 * strength * 0.5F * t * t);
            if (a <= 0) continue;
            int c = (a << 24) | rgb;
            int o = edge * i / steps, o2 = edge * (i + 1) / steps;
            g.fill(0, o, w, o2, c);
            g.fill(0, h - o2, w, h - o, c);
            g.fill(o, o2, o2, h - o2, c);
            g.fill(w - o2, o2, w - o, h - o2, c);
        }
        int veil = Math.round(255 * (0.03F + 0.012F * amp) * (0.6F + 0.4F * pulse));
        g.fill(0, 0, w, h, (veil << 24) | rgb);
    }
}
