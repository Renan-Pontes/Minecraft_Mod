package com.renanpontes.isekaimahou.client.boss;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.core.boss.BossPayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. A tela das maldições assinatura dos chefes ({@link BossCurses}) e dos clarões ({@link BossPayloads.Flash}):
 * <ul>
 *   <li>Calor Escaldante: bordas alaranjadas pulsando + o ar "tremula" (balanço e FOV ondulando de leve);</li>
 *   <li>Sussurros do Abismo: bordas roxo-escuras, vozes em volta, frases sussurradas e (gráficos Alto+) a visão
 *       piscando INVERTIDA por um instante;</li>
 *   <li>Tempo Lento: tela sépia, tique-taque de relógio, câmera pesada;</li>
 *   <li>Afogamento (azul), Alma Drenada (ciano frio), Sapo (verde), Mente Invertida (roxo + controles trocados).</li>
 * </ul>
 * Tudo é vinheta/tingimento barato (uma textura); os extras respeitam o {@link FxQuality}.
 */
public final class BossScreenFx {
    private static final ResourceLocation VIGNETTE = IsekaiMahou.id("textures/misc/boss_vignette.png");
    private static final ResourceLocation INVERT = ResourceLocation.withDefaultNamespace("shaders/post/invert.json");
    private static final int MAX_FLASHES = 6;

    private record Flash(int rgb, float alpha, int ticks, int kind, long start) {}

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static long ticks;
    /** Ticks restantes do "piscar invertido" dos Sussurros (0 = sem). */
    private static int invertLeft;
    private static boolean invertOurs;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                IsekaiMahou.id("boss_screen_fx"), BossScreenFx::render));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, BossScreenFx::onCamera);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, BossScreenFx::onFov);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BossScreenFx::onInput);
    }

    public static void flash(BossPayloads.Flash p) {
        if (FLASHES.size() >= MAX_FLASHES) FLASHES.remove(0);
        FLASHES.add(new Flash(p.rgb(), p.alpha(), Math.max(1, p.ticks()), p.kind(), ticks));
    }

    static void clear() {
        FLASHES.clear();
        stopInvert();
    }

    private static boolean has(LocalPlayer p, Holder<MobEffect> e) {
        return p.hasEffect(e);
    }

    // =====================================================================================
    //  Tick: sons, frases e o piscar invertido
    // =====================================================================================
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ticks++;
        if (!FLASHES.isEmpty()) FLASHES.removeIf(f -> ticks - f.start > f.ticks);
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            stopInvert();
            return;
        }
        if (invertLeft > 0 && --invertLeft == 0) stopInvert();

        if (has(p, BossCurses.WHISPERS)) {
            var rnd = p.getRandom();
            // Vozes vindo de trás/dos lados (posicionais: dá vontade de virar)
            if (ticks % 37 == 0 && rnd.nextInt(3) != 0) {
                double a = Math.toRadians(p.getYRot() + 90 + rnd.nextInt(180));
                mc.level.playLocalSound(p.getX() + Math.cos(a) * 3, p.getEyeY(), p.getZ() + Math.sin(a) * 3,
                        rnd.nextBoolean() ? SoundEvents.AMBIENT_CAVE.value() : SoundEvents.SCULK_SHRIEKER_SHRIEK,
                        SoundSource.HOSTILE, 0.35F, 0.45F + rnd.nextFloat() * 0.2F, false);
            }
            if (ticks % 90 == 0) {
                mc.gui.setOverlayMessage(Component.translatable("boss.isekai_mahou.whisper." + rnd.nextInt(8))
                        .withColor(0x9A6ACF), false);
            }
            if (ticks % 140 == 70 && FxQuality.atLeast(Graphics.HIGH)) startInvert(mc, 5);
        }
        if (has(p, BossCurses.TIME_SLOW) && ticks % 10 == 0) {
            mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE,
                    0.45F, ticks % 20 == 0 ? 0.5F : 0.6F, false);
        }
        if (has(p, BossCurses.SCORCHED) && ticks % 8 == 0 && FxQuality.atLeast(Graphics.MEDIUM)) {
            // Ar quente subindo na frente dos olhos
            var look = p.getLookAngle();
            for (int i = 0; i < FxQuality.count(2); i++) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                        p.getX() + look.x * 1.5 + (p.getRandom().nextDouble() - 0.5) * 2, p.getEyeY() - 0.6,
                        p.getZ() + look.z * 1.5 + (p.getRandom().nextDouble() - 0.5) * 2, 0, 0.05, 0);
            }
        }
    }

    private static void startInvert(Minecraft mc, int t) {
        try {
            PostChain cur = mc.gameRenderer.currentEffect();
            if (cur != null && !invertOurs) return; // outro efeito de tela ativo (espectador, outro mod): não mexe
            if (!invertOurs) mc.gameRenderer.loadEffect(INVERT);
            invertOurs = true;
            invertLeft = t;
        } catch (RuntimeException ex) {
            invertOurs = false;
            invertLeft = 0;
        }
    }

    private static void stopInvert() {
        if (!invertOurs) return;
        invertOurs = false;
        invertLeft = 0;
        try {
            Minecraft mc = Minecraft.getInstance();
            PostChain cur = mc.gameRenderer.currentEffect();
            if (cur != null && INVERT.toString().equals(cur.getName())) mc.gameRenderer.shutdownEffect();
        } catch (RuntimeException ignored) {
            // Sem efeito para desligar
        }
    }

    // =====================================================================================
    //  Câmera: calor tremula, tempo pesa
    // =====================================================================================
    private static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double t = ticks + e.getPartialTick();
        if (has(p, BossCurses.SCORCHED)) {
            float k = 0.35F + 0.15F * amp(p, BossCurses.SCORCHED);
            e.setRoll(e.getRoll() + (float) (Math.sin(t * 0.21) * k + Math.sin(t * 0.53 + 1.1) * k * 0.5));
        }
        if (has(p, BossCurses.TIME_SLOW)) {
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.05) * 1.2F);
        }
        if (has(p, BossCurses.INVERTED)) {
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.09) * 2.5F);
        }
    }

    private static void onFov(ViewportEvent.ComputeFov e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !e.usedConfiguredFov()) return;
        double t = ticks + e.getPartialTick();
        if (has(p, BossCurses.SCORCHED)) e.setFOV(e.getFOV() + Math.sin(t * 0.33) * 1.2);
        if (has(p, BossCurses.TIME_SLOW)) e.setFOV(e.getFOV() * 0.94);
        if (has(p, BossCurses.WHISPERS)) e.setFOV(e.getFOV() + Math.sin(t * 0.07) * 2.0);
    }

    private static int amp(LocalPlayer p, Holder<MobEffect> e) {
        MobEffectInstance i = p.getEffect(e);
        return i == null ? 0 : i.getAmplifier();
    }

    // =====================================================================================
    //  Controles trocados (Mente Invertida)
    // =====================================================================================
    private static void onInput(MovementInputUpdateEvent e) {
        if (!e.getEntity().hasEffect(BossCurses.INVERTED)) return;
        Input in = e.getInput();
        in.forwardImpulse = -in.forwardImpulse;
        in.leftImpulse = -in.leftImpulse;
        boolean up = in.up;
        in.up = in.down;
        in.down = up;
        boolean left = in.left;
        in.left = in.right;
        in.right = left;
    }

    // =====================================================================================
    //  Desenho (camada de HUD logo acima das sobreposições da câmera)
    // =====================================================================================
    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        double t = ticks + delta.getGameTimeDeltaPartialTick(false);

        if (has(p, BossCurses.SCORCHED)) {
            float pulse = 0.35F + 0.15F * (float) Math.sin(t * 0.25) + 0.08F * amp(p, BossCurses.SCORCHED);
            vignette(g, w, h, 0xFF5A10, pulse);
        }
        if (has(p, BossCurses.WHISPERS)) {
            vignette(g, w, h, 0x1A0630, 0.75F + 0.15F * (float) Math.sin(t * 0.11));
            vignette(g, w, h, 0x6A2AA0, 0.25F);
        }
        if (has(p, BossCurses.TIME_SLOW)) {
            g.fill(0, 0, w, h, argb(0x6B5A3A, 0.22F));
            vignette(g, w, h, 0x3A2A14, 0.55F);
        }
        if (has(p, BossCurses.DROWNING)) vignette(g, w, h, 0x0E3A9A, 0.5F + 0.15F * (float) Math.sin(t * 0.2));
        if (has(p, BossCurses.SOUL_DRAIN)) vignette(g, w, h, 0x5FD8C8, 0.18F + 0.06F * amp(p, BossCurses.SOUL_DRAIN));
        if (has(p, BossCurses.FROG)) vignette(g, w, h, 0x3E9A22, 0.4F);
        if (has(p, BossCurses.INVERTED)) vignette(g, w, h, 0xA040E0, 0.35F + 0.15F * (float) Math.sin(t * 0.3));
        if (has(p, BossCurses.CHAINED)) vignette(g, w, h, 0x5A0A0A, 0.3F);

        for (Flash f : FLASHES) {
            double age = t - f.start;
            if (age < 0 || age > f.ticks) continue;
            float k = (float) (1 - age / f.ticks);
            k = k * k;
            if (f.kind == 1) vignette(g, w, h, f.rgb, f.alpha * k);
            else g.fill(0, 0, w, h, argb(f.rgb, f.alpha * k));
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        alpha = Mth.clamp(alpha, 0F, 1F);
        if (alpha <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
        g.blit(VIGNETTE, 0, 0, 0, 0, w, h, w, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    private static int argb(int rgb, float alpha) {
        return ((int) (Mth.clamp(alpha, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private BossScreenFx() {}
}
