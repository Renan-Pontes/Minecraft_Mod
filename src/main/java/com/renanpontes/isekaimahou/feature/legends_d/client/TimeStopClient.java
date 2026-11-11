package com.renanpontes.isekaimahou.feature.legends_d.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * PARAR O TEMPO (cliente).
 * <ul>
 *   <li>As entidades paradas também não "tickam" aqui (projéteis/itens não seguem voando na simulação local, membros
 *       e cabeças ficam imóveis).</li>
 *   <li>Onda de NEGATIVO: uma esfera que inverte as cores de tudo que está dentro dela se expande do conjurador até o
 *       raio e volta a se fechar (e de novo, rápida, quando o tempo volta).</li>
 *   <li>Tela CINZA para quem está no raio (efeito de pós-processamento próprio, carregado no GameRenderer; se não der,
 *       um véu cinza na HUD), relógio na tela com o tempo que falta e um relógio dourado no chão do conjurador.</li>
 * </ul>
 */
public final class TimeStopClient {
    private static final ResourceLocation POST = IsekaiMahou.id("shaders/post/legends_d_time_stop.json");
    private static final ResourceLocation CLOCK = LdRenderTypes.tex("clock_face");
    private static final int WAVE_IN = 12;
    private static final int WAVE_OUT = 10;
    private static final int GOLD = 0xFFE3A0;

    private static final class Zone {
        Vec3 center = Vec3.ZERO;
        float radius;
        int start;
        int end;
        int casterId = -1;
        IntOpenHashSet exempt = new IntOpenHashSet();
    }

    private record Ripple(Vec3 center, float radius, int start, int grow, int shrink) {
        float radiusAt(float now) {
            float t = now - start;
            if (t < 0) return 0F;
            if (t < grow) {
                float k = t / grow;
                return radius * (1F - (1F - k) * (1F - k) * (1F - k));
            }
            float k = Mth.clamp((t - grow) / shrink, 0F, 1F);
            return radius * (1F - k * k);
        }

        boolean done(int now) { return now - start > grow + shrink; }
    }

    private static final Map<Integer, Zone> ZONES = new HashMap<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static int ticks;
    private static ClientLevel level;
    private static float sat = 1F;
    private static boolean postOurs;
    private static boolean postFailed;

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    public static void onSync(LdNet.TimeStopSync p) {
        ensureLevel();
        Zone z = ZONES.get(p.zone());
        if (z == null) {
            z = new Zone();
            ZONES.put(p.zone(), z);
            if (p.elapsed() < WAVE_IN + WAVE_OUT) {
                RIPPLES.add(new Ripple(p.center(), p.radius(), ticks - p.elapsed(), WAVE_IN, WAVE_OUT));
            }
        }
        z.center = p.center();
        z.radius = p.radius();
        z.start = ticks - p.elapsed();
        z.end = ticks + p.remaining();
        z.casterId = p.casterId();
        z.exempt = new IntOpenHashSet(p.exempt());
    }

    public static void onEnd(LdNet.TimeStopEnd p) {
        ensureLevel();
        Zone z = ZONES.remove(p.zone());
        if (z != null) RIPPLES.add(new Ripple(z.center, z.radius * 0.7F, ticks, 6, 7));
    }

    private static void ensureLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ZONES.clear();
            RIPPLES.clear();
            level = mc.level;
        }
    }

    // =====================================================================================
    //  Entidades paradas (simulação local)
    // =====================================================================================
    public static void onEntityTick(EntityTickEvent.Pre e) {
        if (ZONES.isEmpty()) return;
        Entity en = e.getEntity();
        if (!en.level().isClientSide || en instanceof Player || !frozen(en)) return;
        e.setCanceled(true);
        en.tickCount--;
        if (en instanceof LivingEntity le) {
            // Pose congelada: sem balanço de pernas, cabeça/corpo sem tremer entre quadros
            le.walkAnimation.update(0F, 1F);
            le.yBodyRotO = le.yBodyRot;
            le.yHeadRotO = le.yHeadRot;
            le.oAttackAnim = le.attackAnim;
        }
    }

    private static boolean frozen(Entity en) {
        for (Zone z : ZONES.values()) {
            if (!z.exempt.contains(en.getId()) && en.position().distanceToSqr(z.center) <= (double) z.radius * z.radius) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Tick: tela cinza
    // =====================================================================================
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ZONES.clear();
            RIPPLES.clear();
            level = mc.level;
        }
        if (mc.level == null) {
            if (postOurs) shutdownPost(mc);
            sat = 1F;
            return;
        }
        if (!mc.isPaused()) ticks++;
        ZONES.values().removeIf(z -> ticks > z.end + 60);
        RIPPLES.removeIf(r -> r.done(ticks));
        boolean inside = viewerZone(mc) != null;
        float target = inside ? 0.06F : 1F;
        sat += (target - sat) * (inside ? 0.3F : 0.22F);
        if (Math.abs(sat - target) < 0.01F) sat = target;
        updatePost(mc, sat < 0.995F);
    }

    /** A parada em que a câmera está (depois que a onda passou por ela). */
    private static Zone viewerZone(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;
        for (Zone z : ZONES.values()) {
            double d = cam.position().distanceTo(z.center);
            float k = Mth.clamp((ticks - z.start) / (float) WAVE_IN, 0F, 1F);
            float wave = z.radius * (1F - (1F - k) * (1F - k) * (1F - k));
            if (d <= z.radius && d <= wave + 0.5) return z;
        }
        return null;
    }

    private static void updatePost(Minecraft mc, boolean need) {
        GameRenderer gr = mc.gameRenderer;
        PostChain cur = gr.currentEffect();
        boolean ours = cur != null && POST.toString().equals(cur.getName());
        if (need) {
            // Só carrega se não houver outro efeito (ex.: visão de espectador de um creeper)
            if (!ours && cur == null && !postFailed) {
                loadPost(gr);
                cur = gr.currentEffect();
                ours = cur != null && POST.toString().equals(cur.getName());
                if (!ours) {
                    postFailed = true;
                    IsekaiMahou.LOGGER.warn("legends_d: efeito de tela cinza indisponível, usando o véu da HUD");
                }
            }
            if (ours) cur.setUniform("Saturation", sat);
            postOurs = ours;
        } else if (ours) {
            shutdownPost(mc);
        } else {
            postOurs = false;
        }
    }

    /** Carrega o pós-processamento (método público com o AT do NeoForge; reflexão como reserva). */
    private static void loadPost(GameRenderer gr) {
        try {
            gr.loadEffect(POST);
        } catch (Throwable t) {
            try {
                Method m = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "loadEffect", ResourceLocation.class);
                m.invoke(gr, POST);
            } catch (Throwable t2) {
                postFailed = true;
            }
        }
    }

    private static void shutdownPost(Minecraft mc) {
        PostChain cur = mc.gameRenderer.currentEffect();
        if (cur != null && POST.toString().equals(cur.getName())) mc.gameRenderer.shutdownEffect();
        postOurs = false;
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ZONES.clear();
        RIPPLES.clear();
        sat = 1F;
        shutdownPost(Minecraft.getInstance());
        postFailed = false;
    }

    // =====================================================================================
    //  Mundo: ondas de negativo, borda da parada, relógio no chão
    // =====================================================================================
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (RIPPLES.isEmpty() && ZONES.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = ticks + pt;
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // Negativo: dentro da esfera as cores ficam invertidas (duas camadas se anulam do lado de fora)
        if (!RIPPLES.isEmpty()) {
            VertexConsumer inv = buffers.getBuffer(LdRenderTypes.INVERT);
            boolean screen = false;
            for (Ripple r : RIPPLES) {
                float rad = r.radiusAt(now);
                if (rad < 0.05F) continue;
                Vec3 c = r.center.subtract(cam);
                ps.pushPose();
                ps.translate(c.x, c.y, c.z);
                LdDraw.flatSphere(inv, ps.last().pose(), rad, 20, 36, 0xFFFFFFFF);
                ps.popPose();
                if (c.lengthSqr() < (double) rad * rad) screen = !screen;
            }
            buffers.endBatch(LdRenderTypes.INVERT);
            if (screen) {
                LdDraw.screenQuad(buffers.getBuffer(LdRenderTypes.INVERT_SCREEN), ps.last().pose(), camera, 0xFFFFFFFF);
                buffers.endBatch(LdRenderTypes.INVERT_SCREEN);
            }
        }

        VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
        for (Ripple r : RIPPLES) {
            float rad = r.radiusAt(now);
            if (rad < 0.05F) continue;
            Vec3 c = r.center.subtract(cam);
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            LdDraw.glowSphere(g, ps.last().pose(), rad, 16, 28, 0xF0E8FF, 0F, 0.7F, cam.subtract(r.center));
            ps.popPose();
        }
        for (Zone z : ZONES.values()) {
            if (now - z.start < WAVE_IN) continue;
            Vec3 c = z.center.subtract(cam);
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            LdDraw.glowSphere(g, ps.last().pose(), z.radius, 16, 28, 0xC8C8D8, 0F, 0.16F, cam.subtract(z.center));
            ps.popPose();
        }
        buffers.endBatch(LdRenderTypes.GLOW);

        // Relógio dourado girando devagar aos pés do conjurador
        VertexConsumer tex = buffers.getBuffer(LdRenderTypes.additiveTexture(CLOCK));
        for (Zone z : ZONES.values()) {
            Entity caster = mc.level.getEntity(z.casterId);
            if (caster == null) continue;
            Vec3 feet = new Vec3(Mth.lerp(pt, caster.xo, caster.getX()), Mth.lerp(pt, caster.yo, caster.getY()),
                    Mth.lerp(pt, caster.zo, caster.getZ())).subtract(cam);
            float appear = Mth.clamp((now - z.start) / 10F, 0F, 1F);
            ps.pushPose();
            ps.translate(feet.x, feet.y + 0.06, feet.z);
            ps.mulPose(Axis.YP.rotationDegrees(-(now - z.start) * 0.6F));
            LdDraw.texFlat(tex, ps.last().pose(), 4.5F * appear, LdDraw.argb(GOLD, 0.6F * appear));
            ps.popPose();
        }
        buffers.endBatch(LdRenderTypes.additiveTexture(CLOCK));
    }

    // =====================================================================================
    //  HUD: relógio e véu cinza de reserva
    // =====================================================================================
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        // Véu cinza de reserva (sem o pós-processamento): vale mesmo com a HUD escondida (F1)
        if (sat < 0.995F && !postOurs) {
            int a = (int) ((1F - sat) * 0.45F * 255);
            g.fill(0, 0, w, h, (a << 24) | 0x7A7A84);
        }
        if (mc.options.hideGui) return;
        Zone z = viewerZone(mc);
        if (z == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        float left = Math.max(0F, (z.end - ticks - pt) / 20F);
        float total = Math.max(1F, (z.end - z.start) / 20F);
        int cx = w / 2, cy = 44;
        RenderSystem.enableBlend();
        g.setColor(1F, 0.92F, 0.7F, 0.9F);
        g.blit(CLOCK, cx - 20, cy - 20, 40, 40, 0, 0, 128, 128, 128, 128);
        g.setColor(1F, 1F, 1F, 1F);
        // Ponteiro dos segundos travado no 12 (treme); o das horas marca o tempo que falta
        float shiver = Mth.sin((ticks + pt) * 1.7F) * 2.5F;
        hand(g, cx, cy, shiver, 15, 0xFFFFF2D0);
        hand(g, cx, cy, -360F * (left / total), 10, 0xFFE8B84A);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        RenderSystem.disableBlend();
        Component title = Component.translatable("hud.isekai_mahou.legends_d.time_stopped").withStyle(ChatFormatting.BOLD);
        Component time = Component.translatable("hud.isekai_mahou.legends_d.seconds", String.format("%.1f", left));
        g.drawCenteredString(mc.font, title, cx, cy + 24, 0xFFE8E0D0);
        g.drawCenteredString(mc.font, time, cx, cy + 35, 0xFFE8B84A);
    }

    private static void hand(GuiGraphics g, int cx, int cy, float deg, int len, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
        g.fill(-1, -len, 1, 1, argb);
        g.pose().popPose();
    }

    private TimeStopClient() {}
}
