package com.renanpontes.isekaimahou.feature.bosses_g.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * "Canto do Esquecimento" no cliente: por alguns segundos o HUD inteiro some (barra de itens, vida, rastreador,
 * barras de chefe, painéis de outros mods que desenham no HUD) e no lugar fica uma névoa lilás com frases soltas de
 * quem esqueceu o que estava fazendo. Cancela o {@link RenderGuiEvent.Pre} — nada mais do HUD é desenhado.
 */
public final class MemoryFog {
    private static final int LINES = 4;
    private static int ticks;
    private static int total;
    private static int seed;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, MemoryFog::onRenderGui);
        NeoForge.EVENT_BUS.addListener(MemoryFog::onClientTick);
    }

    public static void start(int duration) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            ticks = Math.max(ticks, duration);
            total = Math.max(total, ticks);
            seed = mc.player.getRandom().nextInt(LINES);
            mc.player.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 1.6F);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ticks = total = 0;
            return;
        }
        if (ticks > 0 && --ticks == 0) total = 0;
    }

    private static void onRenderGui(RenderGuiEvent.Pre event) {
        if (ticks <= 0 || total <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float left = ticks - pt;
        float elapsed = total - left;
        float in = Mth.clamp(elapsed / 8F, 0F, 1F);
        float out = Mth.clamp(left / 20F, 0F, 1F);
        float k = Math.min(in, out);
        int w = g.guiWidth();
        int h = g.guiHeight();
        // Névoa lilás que pulsa devagar, mais forte nas bordas.
        float pulse = 0.85F + 0.15F * Mth.sin((mc.player.tickCount + pt) * 0.15F);
        int a = (int) (110 * k * pulse);
        int edge = (int) (190 * k);
        g.fill(0, 0, w, h, (a << 24) | 0x3A2C55);
        g.fillGradient(0, 0, w, h / 4, (edge << 24) | 0x1C1430, 0x001C1430);
        g.fillGradient(0, h - h / 4, w, h, 0x001C1430, (edge << 24) | 0x1C1430);
        // Frase solta, tremendo, letra por letra.
        Font font = mc.font;
        int textA = (int) (230 * k);
        if (textA > 8) {
            String key = "message.isekai_mahou.false_hydra.wipe_" + seed;
            Component line = Component.translatable(key);
            String raw = line.getString();
            int shown = Mth.clamp((int) (elapsed * 1.6F), 0, raw.length());
            String part = raw.substring(0, shown);
            int jitter = (mc.player.tickCount / 2) % 3 - 1;
            g.drawCenteredString(font, part, w / 2 + jitter, h / 2 - 20, (textA << 24) | 0xD9CCFF);
            if (elapsed > 30) {
                Component hint = Component.translatable("message.isekai_mahou.false_hydra.wipe_hint");
                g.drawCenteredString(font, hint, w / 2, h / 2 + 2, ((int) (textA * 0.7F) << 24) | 0xA898C8);
            }
        }
        event.setCanceled(true);
    }

    private MemoryFog() {}
}
