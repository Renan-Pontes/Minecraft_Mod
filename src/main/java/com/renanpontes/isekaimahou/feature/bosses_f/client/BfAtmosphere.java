package com.renanpontes.isekaimahou.feature.bosses_f.client;

import java.lang.ref.WeakReference;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.DesertBasilisk;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.WyvernKing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * v1.3 — Clima e avisos dos chefes do Overworld (só cliente).
 * <ul>
 *   <li><b>Tempestade do Wyvern Rei</b>: enquanto ele luta, um teto de nuvens escuras se fecha sobre o pico, a névoa
 *       fica cinza-azulada, cai chuva e o céu pisca com relâmpagos (as nuvens acendem junto);</li>
 *   <li><b>Olhar do Basilisco</b>: enquanto ele carrega o olhar, quem está OLHANDO para os olhos dele vê a pedra subindo
 *       pelas bordas da tela, ouve o coração e lê "DESVIE O OLHAR!" — virar a câmera apaga o aviso na hora.
 *       (Mesmo teste do servidor: ângulo {@link DesertBasilisk#LOOK_DOT} e linha de visão.)</li>
 * </ul>
 */
public final class BfAtmosphere {
    private static final double STORM_RANGE = 72;
    private static final double GAZE_RANGE = 26;

    private static WeakReference<WyvernKing> wyvern = new WeakReference<>(null);
    private static WeakReference<DesertBasilisk> basilisk = new WeakReference<>(null);
    private static int scanCd;
    private static int ticks;
    private static float storm, stormO;
    private static double cloudY;
    private static float bolt;
    private static float stone, stoneO;
    private static boolean gazing;
    private static boolean looking;

    private static final RenderType CLOUDS = RenderType.create("isekai_mahou_wyvern_storm",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 32768, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BfAtmosphere::tick);
        NeoForge.EVENT_BUS.addListener(BfAtmosphere::fogColor);
        NeoForge.EVENT_BUS.addListener(BfAtmosphere::fog);
        NeoForge.EVENT_BUS.addListener(BfAtmosphere::render);
        NeoForge.EVENT_BUS.addListener(BfAtmosphere::gui);
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        stormO = storm;
        stoneO = stone;
        bolt = Math.max(0F, bolt - 0.2F);
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            storm = stone = 0F;
            gazing = looking = false;
            wyvern = new WeakReference<>(null);
            basilisk = new WeakReference<>(null);
            return;
        }
        ticks++;
        // Busca barata a cada 10 ticks (as referências valem entre uma busca e outra).
        if (--scanCd <= 0) {
            scanCd = 10;
            WyvernKing w = null;
            for (WyvernKing c : level.getEntitiesOfClass(WyvernKing.class, player.getBoundingBox().inflate(STORM_RANGE))) {
                if (c.isAlive() && c.isAwake()) w = c;
            }
            wyvern = new WeakReference<>(w);
            DesertBasilisk b = null;
            for (DesertBasilisk c : level.getEntitiesOfClass(DesertBasilisk.class, player.getBoundingBox().inflate(GAZE_RANGE))) {
                if (c.isAlive()) b = c;
            }
            basilisk = new WeakReference<>(b);
        }
        tickStorm(level, player);
        tickGaze(level, player);
    }

    // ------------------------------------------------------------------ tempestade

    private static void tickStorm(ClientLevel level, LocalPlayer player) {
        WyvernKing w = wyvern.get();
        boolean on = w != null && !w.isRemoved() && w.isAlive() && w.isAwake() && w.level() == level
                && w.distanceToSqr(player) < STORM_RANGE * STORM_RANGE;
        storm = on ? Math.min(1F, storm + 0.015F) : Math.max(0F, storm - 0.02F);
        if (on) {
            // O teto fica acima do jogador e do Wyvern (que voa alto), e desliza devagar.
            double want = Math.max(player.getY() + 22, w.getY() + 10);
            cloudY = storm <= 0.02F ? want : Mth.lerp(0.03, cloudY, want);
        }
        if (storm < 0.25F) return;
        RandomSource r = level.random;
        // Chuva em volta do jogador
        int n = FxQuality.count(5);
        for (int i = 0; i < n; i++) {
            double x = player.getX() + (r.nextDouble() - 0.5) * 24, z = player.getZ() + (r.nextDouble() - 0.5) * 24;
            level.addParticle(ParticleTypes.FALLING_WATER, x, player.getY() + 8 + r.nextDouble() * 6, z, 0, 0, 0);
        }
        // Fiapos de nuvem descendo do teto
        if (FxQuality.atLeast(Graphics.MEDIUM) && r.nextInt(3) == 0) {
            double x = player.getX() + (r.nextDouble() - 0.5) * 40, z = player.getZ() + (r.nextDouble() - 0.5) * 40;
            level.addParticle(ParticleTypes.CLOUD, x, cloudY - 1 - r.nextDouble() * 2, z, (r.nextDouble() - 0.5) * 0.05, -0.02, 0);
        }
        // Relâmpagos no céu (as nuvens acendem)
        if (r.nextInt(70) == 0) {
            bolt = 1F;
            level.setSkyFlashTime(2);
            level.playLocalSound(player.getX(), player.getY() + 20, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    0.4F, 0.7F + r.nextFloat() * 0.4F, false);
        }
    }

    private static void fogColor(ViewportEvent.ComputeFogColor e) {
        float s = Mth.lerp((float) e.getPartialTick(), stormO, storm);
        if (s <= 0.001F) return;
        float k = s * 0.65F;
        float glow = bolt * 0.35F;
        e.setRed(Mth.lerp(k, e.getRed(), 0.20F + glow));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.24F + glow));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.32F + glow));
    }

    private static void fog(ViewportEvent.RenderFog e) {
        float s = Mth.lerp((float) e.getPartialTick(), stormO, storm);
        if (s <= 0.001F || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = e.getFarPlaneDistance();
        float f = Mth.lerp(s * 0.6F, far, Math.min(far, 96F));
        e.setFarPlaneDistance(f);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), f * 0.2F));
        e.setCanceled(true);
    }

    /** Teto de nuvens: discos escuros em camadas, mais densos no centro, ondulando devagar. */
    private static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float s = Mth.lerp(pt, stormO, storm);
        if (s <= 0.02F || !FxQuality.atLeast(Graphics.MEDIUM)) return;
        Vec3 cam = e.getCamera().getPosition();
        double t = ticks + pt;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int layers = FxQuality.atLeast(Graphics.HIGH) ? 3 : 2;
        for (int l = 0; l < layers; l++) {
            float y = (float) (cloudY + l * 2.5 - cam.y);
            disc(b, y, 70F + l * 12F, s * (0.55F - l * 0.12F), t * (0.0015 + l * 0.001) * (l % 2 == 0 ? 1 : -1), l);
        }
        MeshData mesh = b.build();
        if (mesh != null) CLOUDS.draw(mesh);
    }

    private static void disc(BufferBuilder b, float y, float radius, float alpha, double spin, int layer) {
        int rings = 6, seg = 28;
        float lit = bolt * (layer == 0 ? 0.55F : 0.35F);
        float cr = 0.16F + lit, cg = 0.18F + lit, cb = 0.24F + lit * 1.2F;
        for (int i = 0; i < rings; i++) {
            float r0 = radius * i / rings, r1 = radius * (i + 1) / rings;
            for (int j = 0; j < seg; j++) {
                double a0 = spin + Math.PI * 2 * j / seg, a1 = spin + Math.PI * 2 * (j + 1) / seg;
                float a00 = puff(alpha, i, j, layer, rings), a01 = puff(alpha, i, j + 1, layer, rings);
                float a10 = puff(alpha, i + 1, j, layer, rings), a11 = puff(alpha, i + 1, j + 1, layer, rings);
                b.addVertex((float) (Math.cos(a0) * r0), y, (float) (Math.sin(a0) * r0)).setColor(cr, cg, cb, a00);
                b.addVertex((float) (Math.cos(a1) * r0), y, (float) (Math.sin(a1) * r0)).setColor(cr, cg, cb, a01);
                b.addVertex((float) (Math.cos(a1) * r1), y, (float) (Math.sin(a1) * r1)).setColor(cr, cg, cb, a11);
                b.addVertex((float) (Math.cos(a0) * r1), y, (float) (Math.sin(a0) * r1)).setColor(cr, cg, cb, a10);
            }
        }
    }

    /** Alfa de um vértice do disco: cai para 0 na borda e varia em "tufos" fixos (parece nuvem, não um disco liso). */
    private static float puff(float alpha, int ring, int seg, int layer, int rings) {
        float edge = 1F - (float) ring / rings;
        int h = (ring * 73856093) ^ ((seg % 28) * 19349663) ^ (layer * 83492791);
        float noise = 0.65F + 0.35F * ((h >>> 8) & 255) / 255F;
        return Mth.clamp(alpha * edge * noise, 0F, 1F);
    }

    // ------------------------------------------------------------------ olhar do basilisco

    private static void tickGaze(ClientLevel level, LocalPlayer player) {
        DesertBasilisk b = basilisk.get();
        gazing = b != null && !b.isRemoved() && b.isAlive() && b.isGazing() && b.level() == level
                && b.distanceToSqr(player) < GAZE_RANGE * GAZE_RANGE;
        looking = false;
        if (gazing && !player.isSpectator()) {
            Vec3 eye = b.eyes();
            Vec3 from = player.getEyePosition();
            Vec3 to = eye.subtract(from).normalize();
            if (player.getViewVector(1.0F).dot(to) > DesertBasilisk.LOOK_DOT) {
                looking = level.clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                        .getType() == HitResult.Type.MISS;
            }
            if (looking && ticks % 12 == 0) {
                level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
                        1.0F, 1.3F, false);
            }
            if (looking && FxQuality.atLeast(Graphics.MEDIUM) && ticks % 2 == 0) {
                // brilho dos olhos ofuscando
                level.addParticle(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, (level.random.nextDouble() - 0.5) * 0.1, 0.02,
                        (level.random.nextDouble() - 0.5) * 0.1);
            }
        }
        stone = looking ? Math.min(1F, stone + 0.08F) : Math.max(0F, stone - 0.15F);
    }

    private static void gui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float s = Mth.lerp(pt, stoneO, stone);
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (s > 0.01F) {
            // A pedra sobe pelas bordas da tela
            int a = (int) (s * 200);
            int edgeV = (int) (h * (0.18F + 0.17F * s)), edgeH = (int) (w * (0.12F + 0.12F * s));
            int stoneC = 0x8C8678;
            g.fillGradient(0, 0, w, edgeV, (a << 24) | stoneC, stoneC);
            g.fillGradient(0, h - edgeV, w, h, stoneC, (a << 24) | stoneC);
            sideGradient(g, 0, edgeH, h, (a << 24) | stoneC, true);
            sideGradient(g, w - edgeH, w, h, (a << 24) | stoneC, false);
        }
        if (!gazing) return;
        Component msg = looking ? Component.translatable("hud.isekai_mahou.desert_basilisk.look_away")
                : Component.translatable("hud.isekai_mahou.desert_basilisk.eyes_closed");
        float pulse = 0.5F + 0.5F * (float) Math.sin((ticks + pt) * 0.6);
        int color = looking ? (0xFF000000 | (255 << 16) | ((int) (80 + 100 * pulse) << 8) | 60) : 0xFF7CFF8A;
        PoseStack ps = g.pose();
        ps.pushPose();
        float scale = looking ? 2.0F : 1.0F;
        ps.translate(w / 2F, h / 2F + (looking ? -40 : 22), 0);
        ps.scale(scale, scale, 1F);
        g.drawCenteredString(mc.font, msg, 0, 0, color);
        ps.popPose();
    }

    /** Gradiente horizontal (o GuiGraphics só faz vertical): faixas finas com alfa decrescente para dentro. */
    private static void sideGradient(GuiGraphics g, int x0, int x1, int h, int argb, boolean fromLeft) {
        int width = x1 - x0;
        int steps = Math.max(1, Math.min(24, width / 2));
        int a0 = (argb >>> 24) & 255;
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            int a = (int) (a0 * (1F - f));
            int xa = fromLeft ? x0 + (int) (width * f) : x1 - (int) (width * (f + 1F / steps));
            int xb = fromLeft ? x0 + (int) (width * (f + 1F / steps)) : x1 - (int) (width * f);
            g.fill(xa, 0, xb, h, (a << 24) | (argb & 0xFFFFFF));
        }
    }

    private BfAtmosphere() {}
}
