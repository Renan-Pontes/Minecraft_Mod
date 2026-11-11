package com.renanpontes.isekaimahou.client.spellfx;

import com.renanpontes.isekaimahou.glyph.fx.FxOptions;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Partícula solta da paleta dos elementos (uma textura virada para a câmera). O comportamento vem do {@link Kind}:
 * brilho, brasa (anima e sobe), gelo (gira e cai devagar), gota (cai), folha (flutua), pedra (quica), cintilar,
 * sombra (cresce e sobe), runa (sobe), fumaça (cresce) e clarão (rápido e grande).
 * A cor vem de {@link FxOptions#rgb()} (as texturas são brancas).
 */
public class FxParticle extends TextureSheetParticle {
    public enum Kind { GLOW, EMBER, FROST, DROPLET, LEAF, STONE, MOTE, WISP, RUNE, SMOKE, FLASH }

    private final Kind kind;
    private final SpriteSet sprites;
    private final int rgb;
    private final float baseSize;
    private final float baseAlpha;
    private final float phase;
    private float spin;

    protected FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                         FxOptions o, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        this.rgb = o.rgb();
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        float s = Mth.clamp(o.scale(), 0.05F, 16.0F);
        float rnd = 0.8F + random.nextFloat() * 0.4F;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 0.92F;
        int life;
        float size;
        float alpha = 1.0F;
        int color = rgb;
        switch (kind) {
            case GLOW -> {
                life = 8 + random.nextInt(10);
                size = 0.11F * s * rnd;
                friction = 0.9F;
                alpha = 0.85F;
                color = FxDraw.lighten(rgb, 0.15F);
            }
            case EMBER -> {
                life = 10 + random.nextInt(10);
                size = 0.12F * s * rnd;
                gravity = -0.4F;
                color = FxDraw.lighten(rgb, 0.55F);
            }
            case FROST -> {
                life = 20 + random.nextInt(15);
                size = 0.09F * s * rnd;
                gravity = 0.15F;
                friction = 0.95F;
                spin = (random.nextFloat() - 0.5F) * 0.3F;
                color = FxDraw.lighten(rgb, 0.35F);
            }
            case DROPLET -> {
                life = 14 + random.nextInt(10);
                size = 0.07F * s * rnd;
                gravity = 1.2F;
                friction = 0.98F;
                hasPhysics = true;
                alpha = 0.9F;
                color = FxDraw.lighten(rgb, 0.25F);
            }
            case LEAF -> {
                life = 30 + random.nextInt(20);
                size = 0.1F * s * rnd;
                gravity = 0.12F;
                friction = 0.96F;
                hasPhysics = true;
                spin = (random.nextFloat() - 0.5F) * 0.25F;
            }
            case STONE -> {
                life = 18 + random.nextInt(14);
                size = 0.075F * s * (0.7F + random.nextFloat() * 0.6F);
                gravity = 2.0F;
                friction = 0.98F;
                hasPhysics = true;
                spin = (random.nextFloat() - 0.5F) * 0.4F;
                color = FxDraw.lighten(rgb, 0.1F);
            }
            case MOTE -> {
                life = 14 + random.nextInt(16);
                size = 0.1F * s * rnd;
                gravity = -0.05F;
                friction = 0.9F;
                color = FxDraw.lighten(rgb, 0.45F);
            }
            case WISP -> {
                life = 18 + random.nextInt(14);
                size = 0.16F * s * rnd;
                gravity = -0.15F;
                friction = 0.93F;
                spin = (random.nextFloat() - 0.5F) * 0.12F;
                alpha = 0.75F;
                color = FxDraw.darken(rgb, 0.45F);
            }
            case RUNE -> {
                life = 18 + random.nextInt(12);
                size = 0.12F * s * rnd;
                gravity = -0.25F;
                friction = 0.9F;
                color = FxDraw.lighten(rgb, 0.3F);
            }
            case SMOKE -> {
                life = 20 + random.nextInt(15);
                size = 0.18F * s * rnd;
                gravity = -0.1F;
                friction = 0.92F;
                spin = (random.nextFloat() - 0.5F) * 0.06F;
                alpha = 0.55F;
                color = FxDraw.lighten(rgb, 0.5F);
            }
            default -> { // FLASH: scale = raio em blocos
                life = 5;
                size = Mth.clamp(o.scale(), 0.1F, 16.0F);
                xd = yd = zd = 0;
                color = FxDraw.lighten(rgb, 0.6F);
                roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            }
        }
        if (o.life() > 0) life = o.life();
        this.lifetime = Math.max(2, life);
        this.baseSize = size;
        this.quadSize = size;
        this.baseAlpha = alpha;
        this.alpha = kind == Kind.RUNE ? 0 : alpha;
        setColor(FxDraw.r(color), FxDraw.g(color), FxDraw.b(color));
        if (kind == Kind.EMBER || kind == Kind.WISP) setSpriteFromAge(sprites);
        else pickSprite(sprites);
        setSize(0.1F, 0.1F);
    }

    @Override
    public void tick() {
        oRoll = roll;
        super.tick();
        if (removed) return;
        roll += spin;
        float t = age / (float) lifetime;
        switch (kind) {
            case GLOW -> alpha = baseAlpha * (1F - t * t);
            case EMBER -> {
                setSpriteFromAge(sprites);
                // claro → cor do elemento → escuro
                int c = t < 0.5F ? mix(FxDraw.lighten(rgb, 0.55F), rgb, t * 2F) : mix(rgb, FxDraw.darken(rgb, 0.5F), (t - 0.5F) * 2F);
                setColor(FxDraw.r(c), FxDraw.g(c), FxDraw.b(c));
                alpha = t > 0.7F ? baseAlpha * (1F - (t - 0.7F) / 0.3F) : baseAlpha;
                xd += Mth.sin(age * 0.6F + phase) * 0.004;
                zd += Mth.cos(age * 0.6F + phase) * 0.004;
            }
            case FROST, STONE -> alpha = t > 0.75F ? baseAlpha * (1F - (t - 0.75F) / 0.25F) : baseAlpha;
            case DROPLET -> {
                if (onGround) lifetime = Math.min(lifetime, age + 2);
                alpha = t > 0.8F ? baseAlpha * (1F - (t - 0.8F) / 0.2F) : baseAlpha;
            }
            case LEAF -> {
                xd += Mth.sin(age * 0.25F + phase) * 0.006;
                zd += Mth.cos(age * 0.21F + phase) * 0.006;
                if (onGround) spin *= 0.5F;
                alpha = t > 0.8F ? 1F - (t - 0.8F) / 0.2F : 1F;
            }
            case MOTE -> alpha = baseAlpha * (0.55F + 0.45F * Mth.sin(age * 0.9F + phase)) * (1F - t * t);
            case WISP -> {
                setSpriteFromAge(sprites);
                xd += Mth.sin(age * 0.3F + phase) * 0.003;
                zd += Mth.cos(age * 0.3F + phase) * 0.003;
                alpha = baseAlpha * (t < 0.2F ? t / 0.2F : 1F - (t - 0.2F) / 0.8F);
            }
            case RUNE -> alpha = baseAlpha * (t < 0.2F ? t / 0.2F : t > 0.65F ? 1F - (t - 0.65F) / 0.35F : 1F);
            case SMOKE -> alpha = baseAlpha * (t < 0.15F ? t / 0.15F : 1F - (t - 0.15F) / 0.85F);
            default -> alpha = baseAlpha * (1F - t) * (1F - t);
        }
    }

    @Override
    public float getQuadSize(float pt) {
        float t = Mth.clamp((age + pt) / lifetime, 0F, 1F);
        return switch (kind) {
            case GLOW -> baseSize * (1F - 0.6F * t);
            case EMBER -> baseSize * (1F - 0.5F * t);
            case MOTE -> baseSize * (0.8F + 0.2F * Mth.sin((age + pt) * 0.9F + phase));
            case WISP -> baseSize * (0.7F + 0.8F * t);
            case SMOKE -> baseSize * (0.6F + 1.4F * t);
            case FLASH -> baseSize * (0.75F + 0.5F * t);
            default -> baseSize;
        };
    }

    @Override
    protected int getLightColor(float pt) {
        return switch (kind) {
            case LEAF, STONE -> super.getLightColor(pt);
            default -> FxRenderTypes.FULL_BRIGHT;
        };
    }

    @Override
    public ParticleRenderType getRenderType() {
        return switch (kind) {
            case EMBER, MOTE, RUNE, FLASH -> FxRenderTypes.ADDITIVE;
            default -> ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        };
    }

    private static int mix(int a, int b, float f) {
        f = Mth.clamp(f, 0F, 1F);
        int r = (int) Mth.lerp(f, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(f, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(f, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }
}
