package com.renanpontes.isekaimahou.feature.bosses_e.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.DragonPhase;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * v1.3 — O céu da luta contra o Dragão Primordial (só cliente).
 * <ul>
 *   <li>cada FASE pinta o céu e a névoa com a cor do elemento (brasa, gelo, poeira, tempestade, crepúsculo) e traz
 *       o "clima" dela em volta do jogador (cinzas e brasas, neve, poeira caindo, relâmpagos no céu, fagulhas do vazio);</li>
 *   <li>na fase final o céu RACHA: fendas brilhantes se abrem no domo e aumentam conforme a vida dele cai;</li>
 *   <li>o sopro distorce o ar (a câmera ondula como calor sobre asfalto) para quem está perto;</li>
 *   <li>a troca de fase dá um clarão na cor da nova fase.</li>
 * </ul>
 * Tudo passa pelo {@link FxQuality}: no preset Baixo não há domo nem distorção, só névoa e poucas partículas.
 */
public final class DragonSkyFx {
    /** Distância em que o céu da luta aparece. */
    private static final double SKY_RANGE = 140;
    /** Distância em que o sopro distorce a câmera. */
    private static final double HEAT_RANGE = 34;
    private static final int MAX_CRACKS = 22;

    private static WeakReference<PrimordialDragon> dragon = new WeakReference<>(null);
    private static int scanCd;
    private static float sky, skyO;
    private static float cr = 1F, cg = 1F, cb = 1F;
    private static float crack, crackO;
    private static float heat, heatO;
    private static float flash;
    private static int flashRgb = 0xFFFFFF;
    private static int lastPhase = -1;
    private static int ticks;
    private static List<float[][]> cracks;

    private static final RenderType DOME = RenderType.create("isekai_mahou_dragon_sky",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    private static final RenderType GLOW = RenderType.create("isekai_mahou_dragon_cracks",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::tick);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::fogColor);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::fog);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::angles);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::fov);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::render);
        NeoForge.EVENT_BUS.addListener(DragonSkyFx::gui);
    }

    // ------------------------------------------------------------------ tick

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        skyO = sky;
        crackO = crack;
        heatO = heat;
        flash = Math.max(0F, flash - 0.06F);
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            dragon = new WeakReference<>(null);
            sky = crack = heat = 0F;
            lastPhase = -1;
            return;
        }
        ticks++;
        PrimordialDragon d = dragon.get();
        // Busca barata: a cada 10 ticks, e só se não temos um dragão válido por perto.
        if (--scanCd <= 0 && (d == null || d.isRemoved() || d.level() != level || d.distanceToSqr(player) > SKY_RANGE * SKY_RANGE)) {
            scanCd = 10;
            d = null;
            double best = SKY_RANGE * SKY_RANGE;
            for (PrimordialDragon c : level.getEntitiesOfClass(PrimordialDragon.class, player.getBoundingBox().inflate(SKY_RANGE))) {
                double dd = c.distanceToSqr(player);
                if (dd < best) {
                    best = dd;
                    d = c;
                }
            }
            dragon = new WeakReference<>(d);
        }
        boolean live = d != null && !d.isRemoved() && d.level() == level;
        boolean active = live && (d.fighting() || d.state() == PrimordialDragon.STATE_INTRO) && d.distanceToSqr(player) < SKY_RANGE * SKY_RANGE;
        float dying = live && d.isDeadOrDying() ? Math.min(1F, d.deathTime / 60F) : 0F;
        sky = approach(sky, active ? 1F - dying : 0F, active ? 0.02F : 0.03F);
        if (!live) {
            crack = approach(crack, 0F, 0.02F);
            heat = approach(heat, 0F, 0.1F);
            return;
        }
        DragonPhase ph = d.phase();
        if (lastPhase != -1 && lastPhase != ph.number() && active) {
            flash = 1F;
            flashRgb = ph.rgb;
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE,
                    0.6F, 0.5F, false);
        }
        lastPhase = ph.number();
        // Cor do céu desliza para a da fase (não pula).
        float tr = ((ph.rgb >> 16) & 255) / 255F, tg = ((ph.rgb >> 8) & 255) / 255F, tb = (ph.rgb & 255) / 255F;
        if (ph == DragonPhase.TWILIGHT) {
            tr *= 0.55F;
            tg *= 0.35F;
            tb *= 0.75F;
        }
        cr = Mth.lerp(0.04F, cr, tr);
        cg = Mth.lerp(0.04F, cg, tg);
        cb = Mth.lerp(0.04F, cb, tb);
        // Rachaduras: abrem na fase 5 e crescem conforme a vida cai (a fase 5 começa em 20%).
        float wantCrack = 0F;
        if (active && ph == DragonPhase.TWILIGHT) {
            float hp = d.getHealth() / Math.max(1F, d.getMaxHealth());
            wantCrack = 0.3F + 0.7F * Mth.clamp(1F - hp / 0.2F, 0F, 1F);
        }
        crack = approach(crack, wantCrack, 0.01F);
        // Distorção do sopro: forte soprando, leve carregando; cai com a distância.
        float wantHeat = 0F;
        int bs = d.breathState();
        if (bs > 0 && FxQuality.atLeast(Graphics.MEDIUM)) {
            double dist = Math.sqrt(d.distanceToSqr(player));
            float near = (float) Mth.clamp(1.0 - dist / HEAT_RANGE, 0.0, 1.0);
            wantHeat = (bs == 2 ? 1F : 0.35F) * (0.35F + 0.65F * near) * (ph == DragonPhase.FIRE ? 1F : 0.6F);
            if (bs == 2 && dist < HEAT_RANGE) shimmer(level, d, ph);
        }
        heat = approach(heat, wantHeat, 0.08F);
        if (active && sky > 0.3F) weather(level, player, ph);
    }

    /** Ar tremulando na frente da boca (pequenas brasas/vapores subindo rápido). */
    private static void shimmer(ClientLevel level, PrimordialDragon d, DragonPhase ph) {
        RandomSource r = level.random;
        Vec3 m = d.mouthPos();
        Vec3 dir = Vec3.directionFromRotation(0, d.getYRot());
        int n = FxQuality.count(4);
        for (int i = 0; i < n; i++) {
            double along = 2 + r.nextDouble() * 18;
            Vec3 p = m.add(dir.scale(along)).add((r.nextDouble() - 0.5) * along * 0.7, -along * 0.12 + (r.nextDouble() - 0.5) * 2,
                    (r.nextDouble() - 0.5) * along * 0.7);
            ParticleOptions o = switch (ph) {
                case FIRE -> r.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.WHITE_ASH;
                case WATER -> ParticleTypes.SNOWFLAKE;
                case EARTH -> ParticleTypes.WHITE_ASH;
                case STORM -> ParticleTypes.ELECTRIC_SPARK;
                case TWILIGHT -> ParticleTypes.REVERSE_PORTAL;
            };
            level.addParticle(o, p.x, p.y, p.z, 0, 0.08 + r.nextDouble() * 0.06, 0);
        }
    }

    /** O "clima" de cada fase em volta do jogador. */
    private static void weather(ClientLevel level, LocalPlayer p, DragonPhase ph) {
        RandomSource r = level.random;
        int n = FxQuality.count(3);
        for (int i = 0; i < n; i++) {
            double x = p.getX() + (r.nextDouble() - 0.5) * 28;
            double z = p.getZ() + (r.nextDouble() - 0.5) * 28;
            double yTop = p.getY() + 6 + r.nextDouble() * 8;
            double yLow = p.getY() - 1 + r.nextDouble() * 2;
            switch (ph) {
                case FIRE -> {
                    if (r.nextBoolean()) level.addParticle(ParticleTypes.ASH, x, yTop, z, 0, -0.02, 0);
                    else level.addParticle(r.nextInt(4) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, x, yLow, z, 0, 0.06, 0);
                }
                case WATER -> level.addParticle(ParticleTypes.SNOWFLAKE, x, yTop, z, (r.nextDouble() - 0.5) * 0.1, -0.08, 0.05);
                case EARTH -> level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.RED_SAND.defaultBlockState()),
                        x, yTop, z, 0, 0, 0);
                case STORM -> level.addParticle(ParticleTypes.FALLING_WATER, x, yTop, z, 0, 0, 0);
                case TWILIGHT -> level.addParticle(r.nextBoolean() ? ParticleTypes.REVERSE_PORTAL
                        : new DustParticleOptions(new Vector3f(0.85F, 0.4F, 0.75F), 1.2F), x, yLow + r.nextDouble() * 4, z, 0, 0.04, 0);
            }
        }
        // Tempestade: o céu pisca com relâmpagos distantes.
        if (ph == DragonPhase.STORM && r.nextInt(90) == 0) {
            level.setSkyFlashTime(2);
            level.playLocalSound(p.getX(), p.getY() + 30, p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    0.35F, 0.6F + r.nextFloat() * 0.3F, false);
        }
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    // ------------------------------------------------------------------ névoa e câmera

    private static void fogColor(ViewportEvent.ComputeFogColor e) {
        float s = Mth.lerp((float) e.getPartialTick(), skyO, sky);
        if (s <= 0.001F) return;
        float k = s * 0.6F;
        e.setRed(Mth.lerp(k, e.getRed(), cr * 0.75F));
        e.setGreen(Mth.lerp(k, e.getGreen(), cg * 0.75F));
        e.setBlue(Mth.lerp(k, e.getBlue(), cb * 0.75F));
    }

    private static void fog(ViewportEvent.RenderFog e) {
        float s = Mth.lerp((float) e.getPartialTick(), skyO, sky);
        if (s <= 0.001F || e.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
        // Névoa um pouco mais curta: a arena "fecha" em volta da luta (sem esconder o dragão).
        float far = e.getFarPlaneDistance();
        float f = Mth.lerp(s * 0.5F, far, Math.min(far, 110F));
        e.setFarPlaneDistance(f);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), f * 0.25F));
        e.setCanceled(true);
    }

    private static void angles(ViewportEvent.ComputeCameraAngles e) {
        float h = Mth.lerp((float) e.getPartialTick(), heatO, heat);
        if (h <= 0.001F) return;
        double t = ticks + e.getPartialTick();
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 0.83) * 0.9 + Math.sin(t * 2.1) * 0.35) * h);
        e.setPitch(e.getPitch() + (float) Math.sin(t * 1.37) * 0.35F * h);
    }

    private static void fov(ViewportEvent.ComputeFov e) {
        float h = Mth.lerp((float) e.getPartialTick(), heatO, heat);
        if (h <= 0.001F) return;
        double t = ticks + e.getPartialTick();
        e.setFOV(e.getFOV() * (1.0 + 0.018 * h * Math.sin(t * 0.61)));
    }

    // ------------------------------------------------------------------ céu

    private static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float s = Mth.lerp(pt, skyO, sky);
        float c = Mth.lerp(pt, crackO, crack);
        if (s <= 0.01F && c <= 0.01F) return;
        float radius = mc.options.getEffectiveRenderDistance() * 16 * 1.45F;
        if (s > 0.01F && FxQuality.atLeast(Graphics.MEDIUM)) drawDome(radius, s);
        if (c > 0.01F) drawCracks(radius * 0.97F, c, ticks + pt);
    }

    /** Domo translúcido na cor da fase (só aparece onde é céu: o terreno mais perto o esconde). */
    private static void drawDome(float r, float s) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 24;
        float[] lat = {-0.35F, 0F, 0.25F, 0.55F, 0.9F, 1.25F, (float) (Math.PI / 2)};
        for (int i = 0; i < lat.length - 1; i++) {
            float a0 = alphaAt(lat[i], s), a1 = alphaAt(lat[i + 1], s);
            for (int j = 0; j < seg; j++) {
                double y0 = Math.PI * 2 * j / seg, y1 = Math.PI * 2 * (j + 1) / seg;
                vtx(b, r, lat[i], y0, a0);
                vtx(b, r, lat[i], y1, a0);
                vtx(b, r, lat[i + 1], y1, a1);
                vtx(b, r, lat[i + 1], y0, a1);
            }
        }
        draw(b, DOME);
    }

    private static float alphaAt(float lat, float s) {
        // Mais forte no horizonte (como um pôr do sol da cor do elemento), mais leve no alto.
        float horizon = 1F - Mth.clamp(Math.abs(lat - 0.12F) / 1.3F, 0F, 1F);
        return s * (0.16F + 0.24F * horizon);
    }

    private static void vtx(BufferBuilder b, float r, double lat, double yaw, float a) {
        float x = (float) (Math.cos(lat) * Math.cos(yaw) * r);
        float y = (float) (Math.sin(lat) * r);
        float z = (float) (Math.cos(lat) * Math.sin(yaw) * r);
        b.addVertex(x, y, z).setColor(cr, cg, cb, a);
    }

    /** Fendas brilhantes no céu (fase final). Mais fendas e mais largas conforme a vida cai. */
    private static void drawCracks(float r, float c, double t) {
        if (cracks == null) cracks = buildCracks();
        int visible = Math.max(1, Math.round(MAX_CRACKS * c * (FxQuality.atLeast(Graphics.HIGH) ? 1F : 0.6F)));
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.15);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < Math.min(visible, cracks.size()); i++) {
            float[][] line = cracks.get(i);
            float flick = 0.85F + 0.15F * (float) Math.sin(t * 0.4 + i * 1.7);
            // brilho largo (roxo) + núcleo fino (branco-rosado)
            ribbon(b, line, r, r * 0.010F * (0.6F + c), 0.75F, 0.30F, 0.95F, 0.35F * pulse * flick);
            ribbon(b, line, r, r * 0.0032F * (0.7F + c), 1.0F, 0.88F, 1.0F, 0.95F * flick);
        }
        draw(b, GLOW);
    }

    private static void ribbon(BufferBuilder b, float[][] line, float r, float w, float red, float green, float blue, float a) {
        for (int k = 0; k + 1 < line.length; k++) {
            Vec3 p0 = new Vec3(line[k][0], line[k][1], line[k][2]).scale(r);
            Vec3 p1 = new Vec3(line[k + 1][0], line[k + 1][1], line[k + 1][2]).scale(r);
            // afina nas pontas
            float t0 = 1F - Math.abs(2F * k / (line.length - 1) - 1F) * 0.8F;
            float t1 = 1F - Math.abs(2F * (k + 1) / (line.length - 1) - 1F) * 0.8F;
            Vec3 side = p1.subtract(p0).cross(p0).normalize();
            Vec3 s0 = side.scale(w * t0), s1 = side.scale(w * t1);
            b.addVertex((float) (p0.x - s0.x), (float) (p0.y - s0.y), (float) (p0.z - s0.z)).setColor(red, green, blue, a);
            b.addVertex((float) (p0.x + s0.x), (float) (p0.y + s0.y), (float) (p0.z + s0.z)).setColor(red, green, blue, a);
            b.addVertex((float) (p1.x + s1.x), (float) (p1.y + s1.y), (float) (p1.z + s1.z)).setColor(red, green, blue, a);
            b.addVertex((float) (p1.x - s1.x), (float) (p1.y - s1.y), (float) (p1.z - s1.z)).setColor(red, green, blue, a);
        }
    }

    /** Fendas fixas (sempre as mesmas): caminhadas aleatórias no domo, com galhos. */
    private static List<float[][]> buildCracks() {
        Random rnd = new Random(0x5EEDL);
        List<float[][]> out = new ArrayList<>();
        while (out.size() < MAX_CRACKS) {
            double yaw = rnd.nextDouble() * Math.PI * 2;
            double lat = 0.25 + rnd.nextDouble() * 0.95;
            double dir = rnd.nextDouble() * Math.PI * 2;
            int steps = 7 + rnd.nextInt(7);
            List<float[]> pts = new ArrayList<>();
            for (int i = 0; i <= steps; i++) {
                pts.add(unit(lat, yaw));
                dir += (rnd.nextDouble() - 0.5) * 1.1;
                double len = 0.04 + rnd.nextDouble() * 0.05;
                lat = Mth.clamp(lat + Math.sin(dir) * len, 0.12, 1.45);
                yaw += Math.cos(dir) * len / Math.max(0.2, Math.cos(lat));
                if (i == steps / 2 && rnd.nextBoolean() && out.size() + 1 < MAX_CRACKS) {
                    // galho saindo do meio
                    List<float[]> br = new ArrayList<>();
                    double bl = lat, by = yaw, bd = dir + (rnd.nextBoolean() ? 1 : -1) * (0.7 + rnd.nextDouble() * 0.6);
                    for (int k = 0; k < 4 + rnd.nextInt(3); k++) {
                        br.add(unit(bl, by));
                        bd += (rnd.nextDouble() - 0.5) * 0.9;
                        bl = Mth.clamp(bl + Math.sin(bd) * 0.04, 0.12, 1.45);
                        by += Math.cos(bd) * 0.04 / Math.max(0.2, Math.cos(bl));
                    }
                    out.add(br.toArray(new float[0][]));
                }
            }
            out.add(0, pts.toArray(new float[0][]));
        }
        return out;
    }

    private static float[] unit(double lat, double yaw) {
        return new float[] {(float) (Math.cos(lat) * Math.cos(yaw)), (float) Math.sin(lat), (float) (Math.cos(lat) * Math.sin(yaw))};
    }

    private static void draw(BufferBuilder b, RenderType type) {
        MeshData mesh = b.build();
        if (mesh != null) type.draw(mesh);
    }

    // ------------------------------------------------------------------ tela

    /** Clarão na cor da fase nova + bordas quentes quando o sopro passa perto. */
    private static void gui(RenderGuiEvent.Post e) {
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        int w = e.getGuiGraphics().guiWidth(), h = e.getGuiGraphics().guiHeight();
        if (flash > 0.01F) {
            int a = (int) (Math.min(1F, flash) * 150);
            e.getGuiGraphics().fill(0, 0, w, h, (a << 24) | (flashRgb & 0xFFFFFF));
        }
        float ht = Mth.lerp(pt, heatO, heat);
        if (ht > 0.05F && lastPhase == DragonPhase.FIRE.number()) {
            int a = (int) (ht * 90);
            int edge = h / 4;
            e.getGuiGraphics().fillGradient(0, 0, w, edge, (a << 24) | 0xFF5A10, 0x00FF5A10);
            e.getGuiGraphics().fillGradient(0, h - edge, w, h, 0x00FF5A10, (a << 24) | 0xFF5A10);
        }
    }

    private DragonSkyFx() {}
}
