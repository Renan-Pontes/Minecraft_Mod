package com.renanpontes.isekaimahou.glyph.fx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * v1.3 — substitutos "com cara de magia" das partículas comuns do vanilla, para as magias usarem a mesma paleta
 * {@code fx_*} (brilho aditivo, brasas que mudam de cor, gelo que gira, fiapos de sombra...). Mesmo papel do vanilla
 * (chama → brasa, fumaça → fumaça fx, END_ROD → cintilar, portal → fiapo roxo...), mesmas regras de velocidade do
 * {@code sendParticles}. Vantagem extra: partículas fx respeitam o preset gráfico no cliente (LOW corta 70%).
 * <p>
 * Sempre chame na hora de usar (não guarde em campo {@code static final}: os tipos só existem depois do registro).
 */
public final class Vfx {
    private static ParticleOptions fx(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale) {
        return fx(type, rgb, scale, 0);
    }

    private static ParticleOptions fx(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale, int motion) {
        return com.renanpontes.isekaimahou.core.ElementFx.fx(type, rgb, scale, 0, -1, motion);
    }

    // ---------------- Fogo ----------------
    public static ParticleOptions flame() { return fx(SpellFxParticles.EMBER, 0xFF8A3A, 1.0F); }
    public static ParticleOptions smallFlame() { return fx(SpellFxParticles.EMBER, 0xFF9A40, 0.6F); }
    public static ParticleOptions soulFlame() { return fx(SpellFxParticles.EMBER, 0x50D8F0, 1.0F); }
    public static ParticleOptions lavaDrip() { return fx(SpellFxParticles.EMBER, 0xFF5A10, 0.8F); }

    // ---------------- Fumaça / nuvem ----------------
    public static ParticleOptions smoke() { return fx(SpellFxParticles.SMOKE, 0x5A5550, 0.8F); }
    public static ParticleOptions largeSmoke() { return fx(SpellFxParticles.SMOKE, 0x3E3A38, 1.6F); }
    public static ParticleOptions cloud() { return fx(SpellFxParticles.SMOKE, 0xF2F4F8, 1.5F); }
    public static ParticleOptions poof() { return fx(SpellFxParticles.SMOKE, 0xEEEEEE, 1.0F); }
    public static ParticleOptions whiteSmoke() { return fx(SpellFxParticles.SMOKE, 0xF4F4F4, 1.2F); }
    public static ParticleOptions whiteAsh() { return fx(SpellFxParticles.SMOKE, 0xDDDDDD, 0.5F); }
    public static ParticleOptions ash() { return fx(SpellFxParticles.SMOKE, 0x444444, 0.5F); }

    // ---------------- Água / gelo ----------------
    public static ParticleOptions splash() { return fx(SpellFxParticles.DROPLET, 0x6FC0FF, 1.0F); }
    public static ParticleOptions water() { return fx(SpellFxParticles.DROPLET, 0x3FA9FF, 0.9F); }
    public static ParticleOptions snowflake() { return fx(SpellFxParticles.FROST, 0xE6F8FF, 1.0F); }

    // ---------------- Luz / brilho ----------------
    public static ParticleOptions endRod() { return fx(SpellFxParticles.MOTE, 0xFFF4C8, 1.0F); }
    public static ParticleOptions totem() { return fx(SpellFxParticles.MOTE, 0xE8FF70, 1.0F); }
    public static ParticleOptions waxOn() { return fx(SpellFxParticles.MOTE, 0xFFC040, 0.8F); }
    public static ParticleOptions waxOff() { return fx(SpellFxParticles.MOTE, 0xFFFFFF, 0.8F); }
    public static ParticleOptions happy() { return fx(SpellFxParticles.GLOW, 0x7FFF6A, 1.0F, SpellFxParticles.MOTION_SLOW); }
    public static ParticleOptions witch() { return fx(SpellFxParticles.GLOW, 0xC060F0, 1.0F, SpellFxParticles.MOTION_SLOW); }
    public static ParticleOptions sporeAir() { return fx(SpellFxParticles.GLOW, 0x9AE070, 0.7F); }
    public static ParticleOptions crimsonSpore() { return fx(SpellFxParticles.GLOW, 0xC02030, 0.7F); }

    // ---------------- Faíscas ----------------
    public static ParticleOptions crit() { return fx(SpellFxParticles.SPARK, 0xFFF0D0, 0.7F, SpellFxParticles.MOTION_SLOWER); }
    public static ParticleOptions magicCrit() { return fx(SpellFxParticles.SPARK, 0xA0D8FF, 0.7F, SpellFxParticles.MOTION_SLOWER); }
    public static ParticleOptions electric() { return fx(SpellFxParticles.SPARK, 0x9FE8FF, 0.8F, SpellFxParticles.MOTION_SLOW); }
    public static ParticleOptions firework() { return fx(SpellFxParticles.SPARK, 0xFFFFFF, 0.9F); }

    // ---------------- Sombra / alma / portal ----------------
    public static ParticleOptions portal() { return fx(SpellFxParticles.WISP, 0x9A40E0, 0.7F, SpellFxParticles.MOTION_INWARD); }
    public static ParticleOptions reversePortal() { return fx(SpellFxParticles.WISP, 0xB060FF, 0.7F, SpellFxParticles.MOTION_SLOWER); }
    public static ParticleOptions ink() { return fx(SpellFxParticles.WISP, 0x120A18, 1.2F); }
    public static ParticleOptions glowInk() { return fx(SpellFxParticles.WISP, 0x40F0E0, 1.0F); }
    public static ParticleOptions soul() { return fx(SpellFxParticles.WISP, 0x7FE8F0, 0.9F); }
    public static ParticleOptions sculkSoul() { return fx(SpellFxParticles.WISP, 0x2FC8C8, 1.0F); }
    public static ParticleOptions dragonBreath() { return fx(SpellFxParticles.WISP, 0xD050C0, 1.0F); }

    // ---------------- Natureza / runas ----------------
    public static ParticleOptions enchant() { return fx(SpellFxParticles.RUNE, 0xB98CFF, 0.7F, SpellFxParticles.MOTION_INWARD); }
    public static ParticleOptions cherry() { return fx(SpellFxParticles.LEAF, 0xFFB7D5, 1.0F); }
    public static ParticleOptions sporeFall() { return fx(SpellFxParticles.LEAF, 0xC0F0A0, 0.8F); }
    public static ParticleOptions composter() { return fx(SpellFxParticles.LEAF, 0x8AD04A, 0.7F); }

    private Vfx() {}
}
