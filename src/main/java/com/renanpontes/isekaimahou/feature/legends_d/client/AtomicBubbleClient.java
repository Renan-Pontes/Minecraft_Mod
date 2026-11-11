package com.renanpontes.isekaimahou.feature.legends_d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Bolha protetora do Atômico (cliente): uma esfera azul com favos de energia girando em volta de cada aliado
 * protegido — aparece com um "pulo", brilha na borda, ondula quando algo bate nela e estoura no fim. O próprio jogador
 * em primeira pessoa não vê a esfera por dentro (a HUD mostra a moldura azul, ver {@link AtomicOverlay}).
 */
public final class AtomicBubbleClient {
    private static final ResourceLocation HEX = LdRenderTypes.tex("atomic_hex");
    private static final int BLUE = 0x5AB8FF;
    private static final int PALE = 0xCDEBFF;
    private static final int APPEAR = 7;
    private static final int POP = 8;
    private static final int MAX = 64;

    private static final class Bubble {
        float radius;
        int start;
        int end;
        int popAt = -1;
        Vec3 lastPos = Vec3.ZERO;
    }

    private static final Int2ObjectMap<Bubble> BUBBLES = new Int2ObjectOpenHashMap<>();
    private static int ticks;
    private static ClientLevel level;

    public static void onFx(LdNet.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (level != mc.level) {
            BUBBLES.clear();
            level = mc.level;
        }
        if (p.kind() == LdNet.ATOMIC_BUBBLE) {
            Bubble b = BUBBLES.get(p.entityId());
            if (b == null || b.popAt >= 0) {
                if (b == null && BUBBLES.size() >= MAX) return;
                b = new Bubble();
                b.start = ticks;
                BUBBLES.put(p.entityId(), b);
            }
            b.radius = Math.max(0.6F, p.size());
            b.end = ticks + Math.max(1, p.ticks());
            b.popAt = -1;
            b.lastPos = p.pos();
        } else if (p.kind() == LdNet.ATOMIC_BUBBLE_END) {
            Bubble b = BUBBLES.get(p.entityId());
            if (b != null && b.popAt < 0) b.popAt = ticks;
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) {
            if (!BUBBLES.isEmpty()) BUBBLES.clear();
            level = mc.level;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        if (BUBBLES.isEmpty()) return;
        BUBBLES.int2ObjectEntrySet().removeIf(en -> {
            Bubble b = en.getValue();
            if (b.popAt < 0 && ticks >= b.end) b.popAt = ticks;
            return b.popAt >= 0 && ticks - b.popAt > POP;
        });
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        BUBBLES.clear();
        level = null;
    }

    /** 0..1: o jogador local está numa bolha (moldura azul na HUD). */
    public static float localStrength(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0F;
        Bubble b = BUBBLES.get(mc.player.getId());
        if (b == null) return 0F;
        float now = ticks + pt;
        if (b.popAt >= 0) return Mth.clamp(1F - (now - b.popAt) / POP, 0F, 1F);
        return Mth.clamp((now - b.start) / APPEAR, 0F, 1F);
    }

    /** Segundos restantes da bolha do jogador local (0 = sem bolha). */
    public static float localSeconds(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0F;
        Bubble b = BUBBLES.get(mc.player.getId());
        if (b == null || b.popAt >= 0) return 0F;
        return Math.max(0F, (b.end - ticks - pt) / 20F);
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BUBBLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = ticks + pt;
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Entity viewer = mc.getCameraEntity();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        RenderType swirl = RenderType.energySwirl(HEX, (now * 0.004F) % 1F, (now * 0.007F) % 1F);
        for (Int2ObjectMap.Entry<Bubble> en : BUBBLES.int2ObjectEntrySet()) {
            Bubble b = en.getValue();
            Entity ent = mc.level.getEntity(en.getIntKey());
            if (ent != null) {
                b.lastPos = new Vec3(Mth.lerp(pt, ent.xo, ent.getX()), Mth.lerp(pt, ent.yo, ent.getY()) + ent.getBbHeight() * 0.5,
                        Mth.lerp(pt, ent.zo, ent.getZ()));
            }
            if (ent == viewer && firstPerson) continue;
            Vec3 c = b.lastPos.subtract(cam);
            if (c.lengthSqr() > 160 * 160) continue;
            float scale, alpha;
            if (b.popAt >= 0) {
                float k = Mth.clamp((now - b.popAt) / POP, 0F, 1F);
                scale = 1F + 0.35F * k;
                alpha = 1F - k;
            } else {
                float k = Mth.clamp((now - b.start) / APPEAR, 0F, 1F);
                scale = elastic(k);
                alpha = Mth.clamp(k * 1.5F, 0F, 1F);
                float left = b.end - now;
                if (left < 10) alpha *= 0.6F + 0.4F * Mth.sin(now * 1.4F) * Mth.sin(now * 1.4F);
            }
            if (alpha <= 0.01F) continue;
            float r = b.radius * scale * (1F + 0.025F * Mth.sin(now * 0.25F + en.getIntKey()));
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            Vec3 camLocal = cam.subtract(b.lastPos);
            // Favos de energia girando
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(now * 1.5F + en.getIntKey() * 37F));
            LdDraw.texSphere(buffers.getBuffer(swirl), ps.last(), r, 12, 20, LdDraw.swirl(BLUE, 0.55F * alpha), 2F, 0.02F, now);
            ps.popPose();
            // Borda brilhante + reflexo
            VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
            LdDraw.glowSphere(g, ps.last().pose(), r * 1.01F, 12, 20, BLUE, 0.05F * alpha, 0.75F * alpha, camLocal);
            ps.pushPose();
            ps.translate(-r * 0.35F, r * 0.45F, 0);
            ps.mulPose(camera.rotation());
            LdDraw.halo(g, ps.last().pose(), 0F, r * 0.08F, r * 0.22F, PALE, 0.55F * alpha, 12);
            ps.popPose();
            // Anel no "equador" girando
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-now * 4F));
            LdDraw.ringFlat(g, ps.last().pose(), r * 1.05F, 0.08F, PALE, 0.5F * alpha, 32);
            ps.popPose();
            // Estouro: onda clara se abrindo
            if (b.popAt >= 0) {
                float k = Mth.clamp((now - b.popAt) / POP, 0F, 1F);
                ps.mulPose(camera.rotation());
                LdDraw.halo(g, ps.last().pose(), r * (1F + k * 0.6F), r * (1.1F + k * 0.8F), r * (1.3F + k), PALE, 0.8F * (1F - k), 28);
            }
            ps.popPose();
        }
        buffers.endBatch(swirl);
        buffers.endBatch(LdRenderTypes.GLOW);
    }

    /** Pulo elástico de 0 a 1 (a bolha "infla"). */
    private static float elastic(float t) {
        if (t <= 0F) return 0.05F;
        if (t >= 1F) return 1F;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    private AtomicBubbleClient() {}
}
