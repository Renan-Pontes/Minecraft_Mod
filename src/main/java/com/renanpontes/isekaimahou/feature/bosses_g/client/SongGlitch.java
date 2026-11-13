package com.renanpontes.isekaimahou.feature.bosses_g.client;

import java.lang.ref.WeakReference;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.FalseHydra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * v1.3 — Canto do Esquecimento AO VIVO (só cliente): enquanto a Falsa Hidra canta e você está na área, a memória
 * começa a falhar ANTES do apagão — o HUD treme e pula de lugar, a barra de itens e os corações piscam, faixas de
 * "estática" lilás cortam a tela e frases soltas aparecem. Quanto mais o canto dura, pior fica: é o relógio visual de
 * "machuque o corpo AGORA". (O apagão de verdade continua no {@link MemoryFog}.)
 */
public final class SongGlitch {
    private static final double RANGE = 30;

    private static WeakReference<FalseHydra> hydra = new WeakReference<>(null);
    private static int scanCd;
    private static int ticks;
    private static float glitch, glitchO;
    private static int jx, jy;
    private static boolean shifted;
    private static long seed;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SongGlitch::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SongGlitch::layerPre);
        NeoForge.EVENT_BUS.addListener(SongGlitch::layerPost);
        NeoForge.EVENT_BUS.addListener(SongGlitch::gui);
        NeoForge.EVENT_BUS.addListener(SongGlitch::fov);
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        glitchO = glitch;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            glitch = 0F;
            hydra = new WeakReference<>(null);
            return;
        }
        ticks++;
        if (--scanCd <= 0) {
            scanCd = 10;
            FalseHydra found = null;
            for (FalseHydra h : level.getEntitiesOfClass(FalseHydra.class, player.getBoundingBox().inflate(RANGE))) {
                if (h.isAlive()) found = h;
            }
            hydra = new WeakReference<>(found);
        }
        FalseHydra h = hydra.get();
        boolean singing = h != null && !h.isRemoved() && h.isAlive() && h.isSinging() && h.level() == level
                && h.distanceToSqr(player) < RANGE * RANGE;
        glitch = singing ? Math.min(1F, glitch + 0.011F) : Math.max(0F, glitch - 0.06F);
        RandomSource r = level.random;
        // Saltos do HUD: raros no começo, frequentes no fim.
        if (glitch > 0.05F && r.nextFloat() < 0.08F + glitch * 0.35F) {
            int amp = 1 + (int) (glitch * 7);
            jx = r.nextInt(amp * 2 + 1) - amp;
            jy = r.nextInt(amp + 1) - amp / 2;
            seed = r.nextLong();
        } else if (r.nextInt(3) == 0) {
            jx = jy = 0;
        }
        // Notas desafinadas do canto, cada vez mais perto
        if (singing && ticks % Math.max(6, 22 - (int) (glitch * 14)) == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE,
                    0.5F + glitch * 0.5F, 0.5F + r.nextFloat() * 0.3F, false);
        }
    }

    /** Desloca as camadas do HUD (pulos) e às vezes some com a barra/corações por um quadro. */
    private static void layerPre(RenderGuiLayerEvent.Pre e) {
        if (glitch <= 0.05F || e.isCanceled()) return;
        boolean flick = e.getName().equals(VanillaGuiLayers.HOTBAR) || e.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
                || e.getName().equals(VanillaGuiLayers.FOOD_LEVEL) || e.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR);
        if (!flick) return;
        long h = seed ^ e.getName().hashCode();
        if (glitch > 0.45F && ((h >>> 7) & 15) < (int) (glitch * 5)) {
            e.setCanceled(true);
            return;
        }
        if (jx == 0 && jy == 0) return;
        e.getGuiGraphics().pose().pushPose();
        e.getGuiGraphics().pose().translate(jx, jy, 0);
        shifted = true;
    }

    private static void layerPost(RenderGuiLayerEvent.Post e) {
        if (!shifted) return;
        e.getGuiGraphics().pose().popPose();
        shifted = false;
    }

    /** Estática lilás/ciano, faixas pretas e frases soltas por cima de tudo. */
    private static void gui(RenderGuiEvent.Post e) {
        GuiGraphics g = e.getGuiGraphics();
        if (shifted) {
            g.pose().popPose();
            shifted = false;
        }
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = Mth.lerp(pt, glitchO, glitch);
        if (k <= 0.02F) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        RandomSource r = RandomSource.create(seed + ticks / 2);
        int bars = Math.round((2 + k * 10) * (FxQuality.atLeast(Graphics.MEDIUM) ? 1F : 0.5F));
        for (int i = 0; i < bars; i++) {
            int y = r.nextInt(h);
            int bh = 1 + r.nextInt(2 + (int) (k * 5));
            int x0 = r.nextInt(w / 2);
            int x1 = x0 + w / 3 + r.nextInt(w / 2);
            int a = (int) ((40 + r.nextInt(80)) * k);
            int c = switch (r.nextInt(4)) {
                case 0 -> 0xA88BFF;
                case 1 -> 0x6FE6FF;
                case 2 -> 0x000000;
                default -> 0xFF6FD8;
            };
            g.fill(x0, y, x1, y + bh, (a << 24) | c);
            // "aberração cromática": a mesma faixa deslocada em outra cor
            if (k > 0.4F) g.fill(x0 + 3, y + 1, x1 + 3, y + bh + 1, ((a / 2) << 24) | 0x6FE6FF);
        }
        // Vinheta lilás que fecha devagar
        int edge = (int) (h * (0.1F + 0.15F * k));
        int va = (int) (120 * k);
        g.fillGradient(0, 0, w, edge, (va << 24) | 0x2A1C44, 0x002A1C44);
        g.fillGradient(0, h - edge, w, h, 0x002A1C44, (va << 24) | 0x2A1C44);
        // Frases soltas de quem está esquecendo
        if (k > 0.3F && ((ticks / 10) % 3 != 0)) {
            int line = (int) ((seed >>> 3) & 3);
            Component txt = Component.translatable("message.isekai_mahou.false_hydra.wipe_" + line);
            String raw = txt.getString();
            int cut = Mth.clamp((int) (raw.length() * (0.3F + 0.7F * k)), 1, raw.length());
            int x = w / 2 + (int) ((seed >>> 11) % 80) - 40;
            int y = h / 3 + (int) ((seed >>> 17) % 60);
            int a = (int) (200 * k);
            g.drawCenteredString(mc.font, raw.substring(0, cut), x + jx, y + jy, (a << 24) | 0xD9CCFF);
        }
        if (k > 0.15F) {
            Component warn = Component.translatable("hud.isekai_mahou.false_hydra.song");
            float pulse = 0.6F + 0.4F * (float) Math.sin((ticks + pt) * 0.5);
            int a = Math.max(8, (int) (255 * Math.min(1F, k * 2F) * pulse));
            g.drawCenteredString(mc.font, warn, w / 2, 18, (a << 24) | 0xC8A0FF);
        }
    }

    /** Leve "respiração" da visão (tontura do canto). */
    private static void fov(ViewportEvent.ComputeFov e) {
        float k = Mth.lerp((float) e.getPartialTick(), glitchO, glitch);
        if (k <= 0.02F) return;
        e.setFOV(e.getFOV() * (1.0 + 0.025 * k * Math.sin((ticks + e.getPartialTick()) * 0.2)));
    }

    private SongGlitch() {}
}
