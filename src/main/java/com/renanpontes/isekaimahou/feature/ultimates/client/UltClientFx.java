package com.renanpontes.isekaimahou.feature.ultimates.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.ultimates.UltNet;
import com.renanpontes.isekaimahou.feature.ultimates.entity.SpectralColossusEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Visual de cliente do módulo:
 * <ul>
 *   <li><b>Presságio</b> da Chuva de Meteoros: céu escurece e avermelha (névoa/céu) perto da área, vinheta vermelha na
 *       tela e um círculo rúnico gigante girando no céu sobre o centro;</li>
 *   <li><b>Transcendência</b>: auréola girando sobre a cabeça e duas asas de luz batendo nas costas (de quem estiver
 *       transcendido, visto por todos), vinheta dourada para o próprio jogador;</li>
 *   <li><b>Efeitos curtos</b>: corte em arco do Colosso, anéis de choque no chão, clarões.</li>
 * </ul>
 */
public final class UltClientFx {
    private static final int MAX_FX = 256;
    private static final double DRAW_RANGE = 192;

    private static final class Omen {
        final Vec3 center;
        final float radius;
        final float height;
        final long start;
        long end;
        final int rgb;

        Omen(UltNet.SkyOmen p, long now) {
            this.center = p.center();
            this.radius = p.radius();
            this.height = p.height();
            this.start = now;
            this.end = now + p.ticks();
            this.rgb = p.rgb();
        }
    }

    private record Trans(int rgb, long start, long end) {}

    private static final class Fx {
        final UltNet.Fx p;
        int age;

        Fx(UltNet.Fx p) {
            this.p = p;
        }
    }

    private static final Map<Integer, Omen> OMENS = new HashMap<>();
    private static final Map<Integer, Trans> TRANS = new HashMap<>();
    private static final List<Fx> FX = new ArrayList<>();
    private static ClientLevel level;

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    static void onOmen(UltNet.SkyOmen p) {
        ClientLevel l = sync();
        if (l == null) return;
        long now = l.getGameTime();
        Omen old = OMENS.get(p.id());
        if (p.ticks() <= 0) {
            if (old != null) old.end = Math.min(old.end, now + 30);
            return;
        }
        // Reenvio periódico do servidor: só atualiza o fim (não reabre o círculo do céu)
        if (old != null) old.end = now + p.ticks();
        else OMENS.put(p.id(), new Omen(p, now));
    }

    static void onTranscend(UltNet.TranscendSync p) {
        ClientLevel l = sync();
        if (l == null) return;
        long now = l.getGameTime();
        if (p.active()) TRANS.put(p.entityId(), new Trans(p.rgb(), now, now + p.ticks()));
        else TRANS.remove(p.entityId());
    }

    static void onFx(UltNet.Fx p) {
        if (sync() == null) return;
        if (FX.size() >= MAX_FX) FX.remove(0);
        FX.add(new Fx(p));
    }

    /** Nível atual (limpa tudo ao trocar de mundo/dimensão). */
    private static ClientLevel sync() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != level) {
            clear();
            level = l;
        }
        return l;
    }

    static void clear() {
        OMENS.clear();
        TRANS.clear();
        FX.clear();
        level = null;
    }

    static void tick(ClientTickEvent.Post e) {
        ClientLevel l = sync();
        if (l == null) return;
        if (Minecraft.getInstance().isPaused()) return;
        long now = l.getGameTime();
        OMENS.values().removeIf(o -> now > o.end + 45);
        TRANS.entrySet().removeIf(en -> now > en.getValue().end() + 5 || l.getEntity(en.getKey()) == null && now > en.getValue().start() + 40);
        Iterator<Fx> it = FX.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            if (++fx.age >= fx.p.life()) it.remove();
        }
    }

    // =====================================================================================
    //  Céu (presságio)
    // =====================================================================================
    /** Força do presságio para quem está em {@code at} (0..1). */
    private static float omenFactor(Vec3 at, float pt) {
        if (OMENS.isEmpty() || level == null) return 0F;
        float now = level.getGameTime() + pt;
        float best = 0F;
        for (Omen o : OMENS.values()) {
            float time = Math.min(Mth.clamp((now - o.start) / 40F, 0F, 1F), Mth.clamp((o.end - now) / 40F, 0F, 1F));
            double dx = at.x - o.center.x, dz = at.z - o.center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            float dist = d <= o.radius + 24 ? 1F : (float) Mth.clamp(1.0 - (d - o.radius - 24) / 56.0, 0.0, 1.0);
            best = Math.max(best, time * dist);
        }
        return best;
    }

    private static int omenColor() {
        int rgb = 0xC02010;
        for (Omen o : OMENS.values()) {
            rgb = o.rgb;
            break;
        }
        // Vermelho sangue puxado para a cor da magia
        int r = (int) (0x9A * 0.75F + ((rgb >> 16) & 255) * 0.25F);
        int g = (int) (0x1A * 0.75F + ((rgb >> 8) & 255) * 0.25F);
        int b = (int) (0x10 * 0.75F + (rgb & 255) * 0.25F);
        return (r << 16) | (g << 8) | b;
    }

    static void onFogColor(ViewportEvent.ComputeFogColor e) {
        float f = omenFactor(e.getCamera().getPosition(), (float) e.getPartialTick());
        if (f <= 0.001F) return;
        int c = omenColor();
        float m = f * 0.85F;
        e.setRed(Mth.lerp(m, e.getRed(), ((c >> 16) & 255) / 255F * 0.55F));
        e.setGreen(Mth.lerp(m, e.getGreen(), ((c >> 8) & 255) / 255F * 0.55F));
        e.setBlue(Mth.lerp(m, e.getBlue(), (c & 255) / 255F * 0.55F));
    }

    static void onRenderFog(ViewportEvent.RenderFog e) {
        if (e.getType() != FogType.NONE) return;
        float f = omenFactor(e.getCamera().getPosition(), (float) e.getPartialTick());
        if (f <= 0.001F) return;
        if (e.getMode() == FogRenderer.FogMode.FOG_SKY) {
            // O céu inteiro toma a cor do presságio
            e.setNearPlaneDistance(Mth.lerp(f, e.getNearPlaneDistance(), 0F));
            e.setFarPlaneDistance(Mth.lerp(f * 0.9F, e.getFarPlaneDistance(), 2F));
        } else {
            // Ar pesado de fumaça: o horizonte fica mais perto, mas a área da chuva continua visível
            float far = Math.max(56F, e.getFarPlaneDistance() * 0.6F);
            e.setFarPlaneDistance(Mth.lerp(f * 0.7F, e.getFarPlaneDistance(), far));
            e.setNearPlaneDistance(Mth.lerp(f * 0.7F, e.getNearPlaneDistance(), far * 0.2F));
        }
        e.setCanceled(true);
    }

    // =====================================================================================
    //  Mundo
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        if (OMENS.isEmpty() && TRANS.isEmpty() && FX.isEmpty()) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L) + pt;

        if (!OMENS.isEmpty()) {
            VertexConsumer sigil = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.SKY_SIGIL));
            for (Omen o : OMENS.values()) drawSigil(sigil, ps, o, cam, time, pt);
            buffers.endBatch(UltRenderTypes.additive(UltRenderTypes.SKY_SIGIL));
            VertexConsumer glow = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.GLOW_TEX));
            for (Omen o : OMENS.values()) drawSigilGlow(glow, ps, o, cam, time, pt);
            buffers.endBatch(UltRenderTypes.additive(UltRenderTypes.GLOW_TEX));
        }

        if (!FX.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(UltRenderTypes.GLOW);
            for (Fx fx : FX) {
                if (fx.p.pos().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
                switch (fx.p.kind()) {
                    case UltNet.FX_RING -> drawRing(vc, ps, fx, cam, pt);
                    case UltNet.FX_SLASH -> drawSlash(vc, ps, fx, cam, pt);
                    default -> {}
                }
            }
            buffers.endBatch(UltRenderTypes.GLOW);
            VertexConsumer flash = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.GLOW_TEX));
            for (Fx fx : FX) {
                if (fx.p.kind() == UltNet.FX_FLASH && fx.p.pos().distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE) {
                    drawFlash(flash, ps, fx, camera, pt);
                }
            }
            buffers.endBatch(UltRenderTypes.additive(UltRenderTypes.GLOW_TEX));
        }

        if (!TRANS.isEmpty()) {
            Entity camEntity = mc.getCameraEntity();
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            VertexConsumer halo = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.HALO));
            for (Map.Entry<Integer, Trans> en : TRANS.entrySet()) {
                Entity ent = mc.level.getEntity(en.getKey());
                if (!(ent instanceof LivingEntity le) || (firstPerson && ent == camEntity) || ent.isInvisible()) continue;
                drawHalo(halo, ps, le, en.getValue(), cam, time, pt);
            }
            buffers.endBatch(UltRenderTypes.additive(UltRenderTypes.HALO));
            VertexConsumer wing = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.WING));
            for (Map.Entry<Integer, Trans> en : TRANS.entrySet()) {
                Entity ent = mc.level.getEntity(en.getKey());
                if (!(ent instanceof LivingEntity le) || (firstPerson && ent == camEntity) || ent.isInvisible()) continue;
                drawWings(wing, ps, le, en.getValue(), cam, time, pt);
            }
            buffers.endBatch(UltRenderTypes.additive(UltRenderTypes.WING));
        }
    }

    // ---------------- Círculo do céu ----------------
    private static void drawSigil(VertexConsumer vc, PoseStack ps, Omen o, Vec3 cam, float time, float pt) {
        float now = level.getGameTime() + pt;
        float open = Mth.clamp((now - o.start) / 30F, 0F, 1F);
        float fade = Mth.clamp((o.end - now) / 40F, 0F, 1F);
        float a = Math.min(open, fade);
        if (a <= 0.01F) return;
        Vec3 c = o.center.add(0, o.height, 0);
        int red = UltRenderTypes.lighten(omenColor(), 0.25F);
        float r = o.radius * 0.95F * (0.4F + 0.6F * easeOut(open));
        hQuad(vc, ps, c.subtract(cam), r, time * 0.012F, UltRenderTypes.argb(red, 0.95F * a));
        hQuad(vc, ps, c.subtract(cam).add(0, -0.6, 0), r * 0.55F, -time * 0.03F, UltRenderTypes.argb(0xFFB060, 0.85F * a));
        hQuad(vc, ps, c.subtract(cam).add(0, 0.6, 0), r * 1.25F, -time * 0.006F, UltRenderTypes.argb(red, 0.35F * a));
    }

    private static void drawSigilGlow(VertexConsumer vc, PoseStack ps, Omen o, Vec3 cam, float time, float pt) {
        float now = level.getGameTime() + pt;
        float a = Math.min(Mth.clamp((now - o.start) / 30F, 0F, 1F), Mth.clamp((o.end - now) / 40F, 0F, 1F));
        if (a <= 0.01F) return;
        Vec3 c = o.center.add(0, o.height - 0.4, 0);
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.15F);
        hQuad(vc, ps, c.subtract(cam), o.radius * 0.5F * pulse, 0F, UltRenderTypes.argb(0xFF6A2A, 0.8F * a));
    }

    /** Quad horizontal (textura inteira) de meia-largura {@code r}, girado {@code rot} radianos. */
    private static void hQuad(VertexConsumer vc, PoseStack ps, Vec3 rel, float r, float rot, int argb) {
        Matrix4f m = ps.last().pose();
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float x = (float) rel.x, y = (float) rel.y, z = (float) rel.z;
        vc.addVertex(m, x + (-cs + sn), y, z + (-sn - cs)).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + (cs + sn), y, z + (sn - cs)).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + (cs - sn), y, z + (sn + cs)).setUv(1, 1).setColor(argb);
        vc.addVertex(m, x + (-cs - sn), y, z + (-sn + cs)).setUv(0, 1).setColor(argb);
    }

    // ---------------- Anel de choque ----------------
    private static void drawRing(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.p.life(), 0F, 1F);
        float r = Math.max(0.3F, fx.p.radius() * easeOut(t));
        float w = 0.5F + fx.p.radius() * 0.08F;
        float a = (1F - t) * 0.9F;
        Vec3 c = fx.p.pos().subtract(cam);
        Matrix4f m = ps.last().pose();
        int rgb = UltRenderTypes.lighten(fx.p.rgb(), 0.2F);
        int seg = 48;
        float h = (1.4F + fx.p.radius() * 0.05F) * (1F - t);
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float ri = Math.max(0F, r - w), ro = r;
            float x = (float) c.x, y = (float) c.y, z = (float) c.z;
            // faixa no chão (miolo transparente, borda forte)
            vc.addVertex(m, x + c0 * ri, y, z + s0 * ri).setColor(UltRenderTypes.argb(rgb, 0F));
            vc.addVertex(m, x + c0 * ro, y, z + s0 * ro).setColor(UltRenderTypes.argb(rgb, a));
            vc.addVertex(m, x + c1 * ro, y, z + s1 * ro).setColor(UltRenderTypes.argb(rgb, a));
            vc.addVertex(m, x + c1 * ri, y, z + s1 * ri).setColor(UltRenderTypes.argb(rgb, 0F));
            // parede da onda (some para cima)
            vc.addVertex(m, x + c0 * ro, y, z + s0 * ro).setColor(UltRenderTypes.argb(rgb, a * 0.7F));
            vc.addVertex(m, x + c0 * ro, y + h, z + s0 * ro).setColor(UltRenderTypes.argb(rgb, 0F));
            vc.addVertex(m, x + c1 * ro, y + h, z + s1 * ro).setColor(UltRenderTypes.argb(rgb, 0F));
            vc.addVertex(m, x + c1 * ro, y, z + s1 * ro).setColor(UltRenderTypes.argb(rgb, a * 0.7F));
        }
    }

    // ---------------- Corte em arco do Colosso ----------------
    private static void drawSlash(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.p.life(), 0F, 1F);
        float reveal = Mth.clamp(t / 0.3F, 0F, 1F);
        float fade = 1F - Mth.clamp((t - 0.3F) / 0.7F, 0F, 1F);
        float half = SpectralColossusEntity.SLASH_ANGLE / 2F;
        float ro = fx.p.radius(), ri = ro * 0.45F;
        Vec3 c = fx.p.pos().subtract(cam);
        Matrix4f m = ps.last().pose();
        int rgb = UltRenderTypes.lighten(fx.p.rgb(), 0.35F);
        int seg = 24;
        int shown = Math.max(1, (int) (seg * reveal));
        for (int i = 0; i < shown; i++) {
            float f0 = i / (float) seg, f1 = (i + 1) / (float) seg;
            // o corte desce na diagonal: da direita-alto para a esquerda-baixo
            float y0 = 2.2F - 3.0F * f0, y1 = 2.2F - 3.0F * f1;
            Vec3 d0 = SpectralColossusEntity.forward(fx.p.yaw() + half - 2 * half * f0);
            Vec3 d1 = SpectralColossusEntity.forward(fx.p.yaw() + half - 2 * half * f1);
            // a ponta do corte (mais nova) brilha mais
            float head = 1F - Math.abs((i + 1) / (float) shown - 1F);
            float a0 = fade * (0.35F + 0.65F * head);
            float a1 = fade * (0.35F + 0.65F * Math.min(1F, head + 1F / shown));
            float x = (float) c.x, y = (float) c.y, z = (float) c.z;
            vc.addVertex(m, x + (float) d0.x * ri, y + y0 * 0.6F, z + (float) d0.z * ri).setColor(UltRenderTypes.argb(rgb, 0F));
            vc.addVertex(m, x + (float) d0.x * ro, y + y0, z + (float) d0.z * ro).setColor(UltRenderTypes.argb(rgb, a0));
            vc.addVertex(m, x + (float) d1.x * ro, y + y1, z + (float) d1.z * ro).setColor(UltRenderTypes.argb(rgb, a1));
            vc.addVertex(m, x + (float) d1.x * ri, y + y1 * 0.6F, z + (float) d1.z * ri).setColor(UltRenderTypes.argb(rgb, 0F));
            // fio branco na borda externa
            float e = 0.35F;
            vc.addVertex(m, x + (float) d0.x * (ro - e), y + y0, z + (float) d0.z * (ro - e)).setColor(UltRenderTypes.argb(0xFFFFFF, a0 * 0.8F));
            vc.addVertex(m, x + (float) d0.x * (ro + e), y + y0, z + (float) d0.z * (ro + e)).setColor(UltRenderTypes.argb(0xFFFFFF, 0F));
            vc.addVertex(m, x + (float) d1.x * (ro + e), y + y1, z + (float) d1.z * (ro + e)).setColor(UltRenderTypes.argb(0xFFFFFF, 0F));
            vc.addVertex(m, x + (float) d1.x * (ro - e), y + y1, z + (float) d1.z * (ro - e)).setColor(UltRenderTypes.argb(0xFFFFFF, a1 * 0.8F));
        }
    }

    // ---------------- Clarão ----------------
    private static void drawFlash(VertexConsumer vc, PoseStack ps, Fx fx, Camera camera, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.p.life(), 0F, 1F);
        float r = fx.p.radius() * (0.6F + 0.8F * easeOut(t));
        float a = (1F - t) * (1F - t);
        Vec3 c = fx.p.pos().subtract(camera.getPosition());
        Matrix4f m = ps.last().pose();
        billboard(vc, m, camera, c, r, UltRenderTypes.argb(UltRenderTypes.lighten(fx.p.rgb(), 0.3F), a));
        billboard(vc, m, camera, c, r * 0.5F, UltRenderTypes.argb(0xFFFFFF, a));
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Camera cam, Vec3 c, float r, int argb) {
        Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        float lx = l.x() * r, ly = l.y() * r, lz = l.z() * r, ux = u.x() * r, uy = u.y() * r, uz = u.z() * r;
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setUv(0, 1).setColor(argb);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setUv(1, 1).setColor(argb);
    }

    // ---------------- Transcendência ----------------
    private static float transAlpha(Trans tr, float pt) {
        float now = level.getGameTime() + pt;
        float in = Mth.clamp((now - tr.start()) / 10F, 0F, 1F);
        float left = tr.end() - now;
        float out = left < 60 ? 0.55F + 0.45F * Mth.cos(now * 0.6F) : 1F;   // pisca quando está acabando
        return in * Mth.clamp(left / 10F, 0F, 1F) * out;
    }

    private static Vec3 lerpPos(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static void drawHalo(VertexConsumer vc, PoseStack ps, LivingEntity e, Trans tr, Vec3 cam, float time, float pt) {
        float a = transAlpha(tr, pt);
        if (a <= 0.01F) return;
        Vec3 c = lerpPos(e, pt).add(0, e.getBbHeight() + 0.32 + 0.04 * Mth.sin(time * 0.1F), 0).subtract(cam);
        int col = UltRenderTypes.lighten(tr.rgb(), 0.55F);
        hQuad(vc, ps, c, 0.42F, time * 0.05F, UltRenderTypes.argb(col, 0.95F * a));
        hQuad(vc, ps, c.add(0, 0.01, 0), 0.6F, -time * 0.03F, UltRenderTypes.argb(tr.rgb(), 0.35F * a));
    }

    private static void drawWings(VertexConsumer vc, PoseStack ps, LivingEntity e, Trans tr, Vec3 cam, float time, float pt) {
        float a = transAlpha(tr, pt);
        if (a <= 0.01F) return;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        Vec3 fwd = SpectralColossusEntity.forward(bodyYaw);
        Vec3 back = fwd.scale(-1);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 up = new Vec3(0, 1, 0);
        float scale = e.getBbHeight() / 1.8F;
        Vec3 anchor = lerpPos(e, pt).add(0, e.getBbHeight() * 0.74, 0).add(back.scale(0.22 * scale)).subtract(cam);
        float flap = (float) Math.toRadians(28 + 14 * Mth.sin(time * (e.onGround() ? 0.08F : 0.22F)));
        float w = 1.9F * scale, h = 1.9F * scale;
        int col = UltRenderTypes.lighten(tr.rgb(), 0.35F);
        Matrix4f m = ps.last().pose();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 dir = right.scale(s * Mth.cos(flap)).add(back.scale(Mth.sin(flap))).normalize();
            Vec3 root = anchor.add(right.scale(s * 0.12 * scale));
            // a raiz da asa na textura fica em (u 0,03, v 0,22)
            Vec3 tl = root.subtract(dir.scale(0.03 * w)).add(up.scale(0.22 * h));
            Vec3 tr2 = tl.add(dir.scale(w));
            Vec3 br = tr2.subtract(up.scale(h));
            Vec3 bl = tl.subtract(up.scale(h));
            int c = UltRenderTypes.argb(col, 0.85F * a);
            vc.addVertex(m, (float) tl.x, (float) tl.y, (float) tl.z).setUv(0, 0).setColor(c);
            vc.addVertex(m, (float) tr2.x, (float) tr2.y, (float) tr2.z).setUv(1, 0).setColor(c);
            vc.addVertex(m, (float) br.x, (float) br.y, (float) br.z).setUv(1, 1).setColor(c);
            vc.addVertex(m, (float) bl.x, (float) bl.y, (float) bl.z).setUv(0, 1).setColor(c);
        }
    }

    // =====================================================================================
    //  Tela
    // =====================================================================================
    static void overlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level != level || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float f = omenFactor(mc.gameRenderer.getMainCamera().getPosition(), pt);
        if (f > 0.01F) {
            int red = omenColor();
            int top = ((int) (110 * f) << 24) | red;
            int mid = ((int) (28 * f) << 24) | red;
            g.fillGradient(0, 0, w, h / 2, top, mid);
            g.fillGradient(0, h / 2, w, h, mid, ((int) (70 * f) << 24) | red);
        }
        Trans tr = TRANS.get(mc.player.getId());
        if (tr != null) {
            float a = transAlpha(tr, pt);
            float pulse = 0.7F + 0.3F * Mth.sin((mc.level.getGameTime() + pt) * 0.12F);
            int col = UltRenderTypes.lighten(tr.rgb(), 0.4F);
            int edge = ((int) (60 * a * pulse) << 24) | col;
            g.fillGradient(0, 0, w, h / 5, edge, col & 0xFFFFFF);
            g.fillGradient(0, h - h / 5, w, h, col & 0xFFFFFF, edge);
        }
    }

    private static float easeOut(float t) {
        float k = 1F - t;
        return 1F - k * k * k;
    }

    private UltClientFx() {}
}
