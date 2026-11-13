package com.renanpontes.isekaimahou.feature.character.client;

import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.Race;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * SÓ CLIENTE (release 1.0). A <b>partícula de assinatura</b> de cada raça ({@code isekai_mahou:race_mote_<raça>}, um
 * desenho 16×16 por raça em {@code textures/particle/}) com o jeito de se mexer de cada uma:
 * estrela do Humano (sobe cintilando), folha-lua do Elfo (cai balançando), runa em brasa do Anão (salta e cai), presa de
 * sangue do Meio-Orc (voa e pesa), chama negra do Demônio (sobe tremendo), escama do Dracônico (sobe em espiral, na cor
 * da ancestralidade), garra do Bestial (um risco que some rápido), fio de teia do Aracnídeo (paira girando devagar), alma
 * do Fantasma (sobe ondulando, aparece e some), pena do Anjo (cai devagar girando) e bolha do Slime (sobe e estoura).
 * Sempre acesas (brilham no escuro). Use {@link #spawn} — ele respeita a cor pedida (a escama é branca e é tingida).
 */
final class RaceMoteParticle extends TextureSheetParticle {
    /** Cor da próxima partícula criada (a criação é síncrona no {@code addParticle}); −1 = branca (cor do desenho). */
    private static int nextTint = -1;

    private final Race race;
    private final float baseSize;
    private final float spin;
    private final float phase;

    private RaceMoteParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Race race) {
        super(level, x, y, z);
        this.race = race;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        float size = 0.14F + random.nextFloat() * 0.06F;
        float spinSpeed = 0F;
        int life = 24;
        switch (race) {
            case HUMAN -> { life = 22 + random.nextInt(10); friction = 0.94F; }
            case ELF -> { life = 44 + random.nextInt(20); gravity = 0.02F; friction = 0.96F; spinSpeed = 0.05F; size *= 1.1F; }
            case DWARF -> { life = 20 + random.nextInt(10); gravity = 0.9F; friction = 0.98F; spinSpeed = 0.2F; }
            case HALF_ORC -> { life = 16 + random.nextInt(8); gravity = 1.2F; friction = 0.98F; spinSpeed = 0.1F; }
            case DEMON -> { life = 20 + random.nextInt(10); gravity = -0.05F; friction = 0.92F; size *= 1.25F; }
            case DRAGONBORN -> { life = 30 + random.nextInt(10); friction = 0.96F; spinSpeed = 0.08F; }
            case BEASTFOLK -> { life = 8 + random.nextInt(4); friction = 0.7F; size *= 2.2F; roll = (random.nextFloat() - 0.5F) * 1.2F; }
            case ARACHNID -> { life = 50 + random.nextInt(20); friction = 0.9F; spinSpeed = 0.015F; size *= 1.5F; }
            case PHANTOM -> { life = 36 + random.nextInt(14); gravity = -0.02F; friction = 0.95F; size *= 1.3F; }
            case ANGEL -> { life = 50 + random.nextInt(24); gravity = 0.03F; friction = 0.95F; spinSpeed = 0.06F; size *= 1.2F; }
            case SLIME -> { life = 26 + random.nextInt(12); gravity = -0.03F; friction = 0.93F; }
        }
        this.lifetime = life;
        this.baseSize = size;
        this.quadSize = size;
        this.spin = spinSpeed * (random.nextBoolean() ? 1F : -1F);
        if (spinSpeed > 0F) this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        if (nextTint >= 0) setColor(((nextTint >> 16) & 255) / 255F, ((nextTint >> 8) & 255) / 255F, (nextTint & 255) / 255F);
        nextTint = -1;
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        float t = age / (float) lifetime;
        float wave = Mth.sin(age * 0.3F + phase);
        switch (race) {
            case HUMAN -> {
                // Cintila
                quadSize = baseSize * (0.7F + 0.5F * Math.abs(wave)) * (1F - t * 0.5F);
                alpha = 1F - t * t;
            }
            case ELF, ANGEL -> {
                // Balança ao cair (como folha/pena)
                xd += Mth.cos(age * 0.18F + phase) * 0.004;
                zd += Mth.sin(age * 0.18F + phase) * 0.004;
                alpha = t > 0.75F ? (1F - t) * 4F : 1F;
            }
            case DWARF, HALF_ORC -> {
                quadSize = baseSize * (1F - t * 0.6F);
                alpha = 1F - t * t * t;
            }
            case DEMON -> {
                xd += wave * 0.003;
                quadSize = baseSize * (1F - t * 0.8F);
                alpha = 1F - t;
            }
            case DRAGONBORN -> {
                // Espiral
                xd += Mth.cos(age * 0.35F + phase) * 0.006;
                zd += Mth.sin(age * 0.35F + phase) * 0.006;
                alpha = t > 0.7F ? (1F - t) / 0.3F : 1F;
            }
            case BEASTFOLK -> {
                quadSize = baseSize * (0.6F + 0.6F * Math.min(1F, t * 3F));
                alpha = 1F - t;
            }
            case ARACHNID -> alpha = (t < 0.15F ? t / 0.15F : t > 0.7F ? (1F - t) / 0.3F : 1F) * 0.9F;
            case PHANTOM -> {
                xd += wave * 0.002;
                zd += Mth.cos(age * 0.3F + phase) * 0.002;
                alpha = Mth.sin(t * Mth.PI) * 0.85F;
            }
            case SLIME -> {
                // Balança e estoura no fim
                quadSize = baseSize * (t > 0.85F ? 1F + (t - 0.85F) * 6F : 0.9F + 0.15F * wave);
                alpha = t > 0.85F ? (1F - t) / 0.15F : 0.9F;
            }
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // =====================================================================================
    //  Uso / registro
    // =====================================================================================
    /** Solta uma partícula de assinatura da raça ({@code rgb} −1 = cor do desenho; a escama dracônica é tingida). */
    static void spawn(ClientLevel level, Race race, int rgb, double x, double y, double z, double vx, double vy, double vz) {
        nextTint = rgb;
        level.addParticle(CharacterContent.mote(race), x, y, z, vx, vy, vz);
        nextTint = -1;
    }

    /** Cor do desenho (sem tingir), para todas as raças menos o Dracônico (escama branca, tingida pela ancestralidade). */
    static int tintFor(Race race, int rgb) {
        return race == Race.DRAGONBORN ? rgb : -1;
    }

    static void register(RegisterParticleProvidersEvent e) {
        for (Race r : Race.ALL) e.registerSpriteSet(CharacterContent.mote(r), sprites -> new Provider(sprites, r));
    }

    private record Provider(SpriteSet sprites, Race race) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy,
                                       double vz) {
            RaceMoteParticle p = new RaceMoteParticle(level, x, y, z, vx, vy, vz, race);
            p.pickSprite(sprites);
            return p;
        }
    }
}
