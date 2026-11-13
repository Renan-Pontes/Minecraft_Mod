package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Partícula ESTICADA na direção do movimento: faísca (aditiva, cai, rápida) e risco de vento (translúcido, longo).
 * Sem velocidade recebida, escolhe uma direção aleatória (faísca) ou horizontal (vento).
 */
public class StreakParticle extends TextureSheetParticle {
    private final boolean spark;
    private final float width;
    private final float lengthMul;
    private final float baseAlpha;
    private final int startRgb;
    private final int endRgb;

    protected StreakParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             FxOptions o, SpriteSet sprites, boolean spark) {
        super(level, x, y, z);
        this.spark = spark;
        float s = Mth.clamp(o.scale(), 0.05F, 8.0F);
        int motion = com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles.motionOf(o.flags());
        if (motion == 1) { vx *= 0.3; vy *= 0.3; vz *= 0.3; }
        else if (motion >= 2) { vx *= 0.1; vy *= 0.1; vz *= 0.1; }
        double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (sp < 0.02) {
            if (spark) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() + 0.6, random.nextGaussian()).normalize().scale(0.15 + random.nextDouble() * 0.1);
                vx = d.x;
                vy = d.y;
                vz = d.z;
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                vx = Math.cos(a) * 0.18;
                vy = (random.nextDouble() - 0.5) * 0.03;
                vz = Math.sin(a) * 0.18;
            }
        }
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        if (spark) {
            this.lifetime = 6 + random.nextInt(7);
            this.gravity = 0.9F;
            this.friction = 0.9F;
            this.width = 0.05F * s;
            this.lengthMul = 2.6F;
            this.baseAlpha = 1.0F;
            this.startRgb = FxDraw.lighten(o.rgb(), 0.6F);
            this.endRgb = o.rgb();
        } else {
            this.lifetime = 10 + random.nextInt(8);
            this.gravity = 0;
            this.friction = 0.97F;
            this.width = 0.2F * s;
            this.lengthMul = 5.0F * s;
            this.baseAlpha = 0.5F;
            this.startRgb = FxDraw.lighten(o.rgb(), 0.6F);
            this.endRgb = FxDraw.lighten(o.rgb(), 0.3F);
        }
        if (o.life() > 0) this.lifetime = o.life();
        this.alpha = spark ? 1F : 0F;
        setColor(FxDraw.r(startRgb), FxDraw.g(startRgb), FxDraw.b(startRgb));
        pickSprite(sprites);
        setSize(0.05F, 0.05F);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float t = age / (float) lifetime;
        int c = mix(startRgb, endRgb, t);
        setColor(FxDraw.r(c), FxDraw.g(c), FxDraw.b(c));
        alpha = spark ? baseAlpha * (1F - t * t) : baseAlpha * Mth.sin(t * Mth.PI);
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        double px = Mth.lerp(pt, xo, x) - cam.x;
        double py = Mth.lerp(pt, yo, y) - cam.y;
        double pz = Mth.lerp(pt, zo, z) - cam.z;
        Vec3 v = new Vec3(xd, yd, zd);
        double sp = v.length();
        if (sp < 1.0E-4) return;
        double len = spark ? Math.max(0.08, sp * lengthMul) : Math.max(0.5, sp * lengthMul);
        Vec3 head = new Vec3(px, py, pz);
        Vec3 tail = head.subtract(v.scale(len / sp));
        FxDraw.streak(vc, tail, head, width, sprite, rCol, gCol, bCol, alpha, FxRenderTypes.FULL_BRIGHT);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return spark ? FxRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static int mix(int a, int b, float f) {
        f = Mth.clamp(f, 0F, 1F);
        int r = (int) Mth.lerp(f, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(f, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(f, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }
}
