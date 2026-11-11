package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pilar de luz: coluna vertical que sobe rápido do chão, segura e afina até sumir. Sempre virada para a câmera
 * (gira em volta do eixo Y). Camada aditiva (brilho) + gêmeo translúcido fraco (aparece de dia).
 */
public class PillarParticle extends Particle {
    private final boolean additive;
    private final int rgb;
    private final float radius;
    private final float height;
    private final TextureAtlasSprite sprite;

    PillarParticle(ClientLevel level, double x, double y, double z, FxOptions o, TextureAtlasSprite sprite, boolean additive) {
        super(level, x, y, z);
        this.additive = additive;
        this.rgb = o.rgb();
        this.radius = Mth.clamp(o.scale(), 0.1F, 16F);
        this.height = Mth.clamp(o.data() / 16F, 1F, 64F);
        this.sprite = sprite;
        this.lifetime = Math.max(4, o.life() > 0 ? o.life() : 20);
        this.xd = this.yd = this.zd = 0;
        this.gravity = 0;
        this.hasPhysics = false;
        setSize(0.1F, 0.1F);
    }

    static Particle create(FxOptions o, ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        TextureAtlasSprite s = sprites.get(level.random);
        Minecraft.getInstance().particleEngine.add(new PillarParticle(level, x, y, z, o, s, false));
        return new PillarParticle(level, x, y, z, o, s, true);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) remove();
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float pt) {
        float ageF = age + pt;
        float t = Mth.clamp(ageF / lifetime, 0F, 1F);
        float grow = Mth.clamp(ageF / Math.max(2F, lifetime * 0.2F), 0F, 1F);
        grow = 1F - (1F - grow) * (1F - grow);
        float thin = t < 0.5F ? 1F : 1F - (t - 0.5F) / 0.5F;
        float h = height * grow;
        float w = radius * (0.35F + 0.65F * thin) * (1F + 0.05F * Mth.sin(ageF * 1.3F));
        float a = (additive ? 0.9F : 0.35F) * (t < 0.6F ? 1F : 1F - (t - 0.6F) / 0.4F);
        if (a <= 0.005F || h <= 0.01F) return;
        Vec3 cam = camera.getPosition();
        double bx = x - cam.x, by = y - cam.y, bz = z - cam.z;
        // lado horizontal perpendicular à câmera
        Vec3 toCam = new Vec3(-bx, 0, -bz);
        Vec3 side = toCam.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : new Vec3(-toCam.z, 0, toCam.x).normalize();
        Vec3 up = new Vec3(0, h * 0.5, 0);
        float cx = (float) bx, cy = (float) (by + h * 0.5), cz = (float) bz;
        int outer = additive ? rgb : FxDraw.lighten(rgb, 0.2F);
        FxDraw.quad(vc, cx, cy, cz, side.scale(w), up, sprite, FxDraw.r(outer), FxDraw.g(outer), FxDraw.b(outer), a, FxRenderTypes.FULL_BRIGHT);
        if (additive) {
            int inner = FxDraw.lighten(rgb, 0.7F);
            FxDraw.quad(vc, cx, cy, cz, side.scale(w * 0.35F), up, sprite, FxDraw.r(inner), FxDraw.g(inner), FxDraw.b(inner), a, FxRenderTypes.FULL_BRIGHT);
        }
    }

    @Override
    public AABB getRenderBoundingBox(float pt) {
        return new AABB(x - radius - 1, y - 1, z - radius - 1, x + radius + 1, y + height + 1, z + radius + 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return additive ? FxRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
