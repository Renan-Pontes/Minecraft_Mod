package com.renanpontes.isekaimahou.feature.structures_b.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Vaga-lume das Flores-Vaga-lume: pontinho de luz que vagueia devagar e pisca (acende/apaga) até sumir.
 * Sempre em brilho máximo, translúcido.
 */
public class FireflyParticle extends TextureSheetParticle {
    private final float phase;
    private final float blinkSpeed;
    private final float baseSize;

    protected FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.lifetime = 90 + random.nextInt(90);
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.blinkSpeed = 0.12F + random.nextFloat() * 0.12F;
        this.baseSize = 0.045F + random.nextFloat() * 0.03F;
        this.quadSize = baseSize;
        // Amarelo-esverdeado com leve variação (alguns puxam para o dourado).
        float warm = random.nextFloat();
        this.setColor(0.78F + warm * 0.22F, 1.0F, 0.35F + (1 - warm) * 0.25F);
        this.xd = (random.nextDouble() - 0.5) * 0.02;
        this.yd = random.nextDouble() * 0.01;
        this.zd = (random.nextDouble() - 0.5) * 0.02;
        this.alpha = 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        // Voo errante: pequenos empurrões aleatórios, com tendência leve a subir no começo da vida.
        xd += (random.nextDouble() - 0.5) * 0.006;
        yd += (random.nextDouble() - 0.5) * 0.004 + (age < lifetime / 3 ? 0.0004 : -0.0002);
        zd += (random.nextDouble() - 0.5) * 0.006;
        double max = 0.035;
        xd = Mth.clamp(xd, -max, max);
        yd = Mth.clamp(yd, -max * 0.6, max * 0.6);
        zd = Mth.clamp(zd, -max, max);

        float blink = 0.5F + 0.5F * Mth.sin(age * blinkSpeed + phase);
        float fadeIn = Math.min(1.0F, age / 12.0F);
        float fadeOut = Math.min(1.0F, (lifetime - age) / 20.0F);
        alpha = Mth.clamp((0.15F + 0.85F * blink * blink) * fadeIn * fadeOut, 0.0F, 1.0F);
        quadSize = baseSize * (0.75F + 0.35F * blink);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FireflyParticle(level, x, y, z, sprites);
        }
    }
}
