package com.renanpontes.isekaimahou.core;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Efeitos visuais e sonoros padrão de cada elemento. Formas e efeitos devem usar isto
 * para manter o visual consistente. (Lado servidor: sendParticles; lado cliente: clientTrail.)
 * <p>
 * v1.1 (spellfx): a paleta usa partículas próprias {@code isekai_mahou:fx_*} (brasa, faísca, gelo, gota, folha,
 * pedra, vento, brilho, sombra, runa...). A API antiga ({@link #dust}, {@link #signature}, {@link #burst},
 * {@link #ring}, {@link #line}, {@link #channeling}, {@link #clientTrail}) continua igual — só ficou mais bonita.
 * Novos: {@link #impact}, {@link #shockwave}, {@link #runeCircle}, {@link #pillar}, {@link #flash},
 * {@link #style}, {@link #glow}, {@link #sendFar}. "tier" = 0 (fraca) .. 4 (Círculo alto) / 5 (ultimate),
 * ver {@link SpellVisuals#tier}.
 */
public final class ElementFx {
    /** Alcance dos efeitos grandes (clarão, onda, círculo, pilar). */
    public static final double FAR = 96.0;

    // =====================================================================================
    //  Opções de partícula
    // =====================================================================================

    /** Partícula de "pó colorido" do elemento (v1.1: brilho suave colorido). */
    public static ParticleOptions dust(Element e, float size) {
        return glow(e.color, size);
    }

    /** Brilho suave com qualquer cor RGB ({@code size} igual ao do pó do vanilla: 1 = normal). */
    public static ParticleOptions glow(int rgb, float size) {
        return fx(SpellFxParticles.GLOW, rgb, size, 0, -1, 0);
    }

    /** Partícula "característica" do elemento (fogo = brasa, água = gota, gelo = floco...). */
    public static ParticleOptions signature(Element e) {
        return style(e, 1.0F);
    }

    /** Partícula característica do elemento com escala. */
    public static ParticleOptions style(Element e, float size) {
        return style(e, e.color, size);
    }

    /** Partícula característica do elemento, tingida com {@code rgb} (ex.: cor escolhida na magia). */
    public static ParticleOptions style(Element e, int rgb, float size) {
        return fx(styleType(e), rgb, size, 0, -1, 0);
    }

    /** Tipo da partícula característica de cada elemento. */
    public static DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> styleType(Element e) {
        return switch (e) {
            case FIRE, SUN, MAGMA, HELLFIRE -> SpellFxParticles.EMBER;
            case WATER, PURITY, ABYSS -> SpellFxParticles.DROPLET;
            case STEAM -> SpellFxParticles.SMOKE;
            case EARTH, SAND -> SpellFxParticles.STONE;
            case WIND -> SpellFxParticles.WIND;
            case LIGHT, ASTRAL, TWILIGHT -> SpellFxParticles.MOTE;
            case SHADOW, VOID, GRAVITY -> SpellFxParticles.WISP;
            case LIGHTNING -> SpellFxParticles.SPARK;
            case ICE, CRYSTAL -> SpellFxParticles.FROST;
            case NATURE -> SpellFxParticles.LEAF;
            default -> SpellFxParticles.RUNE;
        };
    }

    /** Partícula secundária (fusões mostram um pouco do 2º pai; base: faísca/brilho). */
    private static ParticleOptions accent(Element e, int rgb, float size) {
        if (e.isFusion() && e.parentB != null) {
            DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> t = styleType(e.parentB);
            if (t != styleType(e)) return fx(t, rgb, size, 0, -1, 0);
            return fx(styleType(e.parentA), rgb, size, 0, -1, 0);
        }
        return switch (e) {
            case FIRE, LIGHT, LIGHTNING -> fx(SpellFxParticles.SPARK, rgb, size, 0, -1, 0);
            case EARTH -> fx(SpellFxParticles.SMOKE, 0xA08A70, size, 0, -1, 0);
            case WATER -> fx(SpellFxParticles.MOTE, lighten(rgb, 0.5F), size * 0.7F, 0, -1, 0);
            case SHADOW -> fx(SpellFxParticles.MOTE, 0xC090FF, size * 0.6F, 0, -1, 0);
            case WIND -> fx(SpellFxParticles.LEAF, 0xBFEFD0, size * 0.8F, 0, -1, 0);
            default -> fx(SpellFxParticles.MOTE, lighten(rgb, 0.4F), size * 0.7F, 0, -1, 0);
        };
    }

    /** Monta opções de partícula fx (com reserva para o pó do vanilla se o registro ainda não existe). */
    public static ParticleOptions fx(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale,
                                     int life, int data, int flags) {
        if (!type.isBound()) return vanillaDust(rgb, scale);
        return new FxOptions(type.get(), rgb & 0xFFFFFF, scale, life, data, flags);
    }

    private static DustParticleOptions vanillaDust(int c, float size) {
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F),
                Mth.clamp(size, 0.05F, 4.0F));
    }

    // =====================================================================================
    //  Efeitos clássicos (mesma assinatura de sempre)
    // =====================================================================================

    /** Explosão de partículas num ponto. */
    public static void burst(ServerLevel level, Vec3 pos, Element e, int count, double spread) {
        level.sendParticles(dust(e, 1.4F), pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
        level.sendParticles(signature(e), pos.x, pos.y, pos.z, Math.max(1, count / 2), spread, spread, spread, 0.06);
        if (count >= 8) {
            level.sendParticles(accent(e, e.color, 1.0F), pos.x, pos.y, pos.z, Math.max(1, count / 5), spread, spread, spread, 0.12);
        }
        // Explosões grandes ganham um clarão e uma onda (são quase sempre eventos únicos: morte, invocação...)
        if (count >= 24) flash(level, pos, e.color, (float) Math.min(3.0, 0.8 + spread * 1.2));
        if (count >= 40) shockwave(level, pos, e.color, (float) Math.min(6.0, 1.2 + spread * 2.0), 12, null);
    }

    /** Anel horizontal de partículas (raio em blocos). */
    public static void ring(ServerLevel level, Vec3 center, Element e, double radius, int points) {
        ParticleOptions g = dust(e, 1.2F);
        ParticleOptions sig = signature(e);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            level.sendParticles(g, x, center.y + 0.1, z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 3 == 0) level.sendParticles(sig, x, center.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Linha de partículas entre dois pontos (feixes, encadear). Raio desenha um arco elétrico em zigue-zague. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, Element e) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int steps = Math.max(2, (int) (len * 4));
        boolean jagged = e == Element.LIGHTNING && len > 0.2;
        ParticleOptions g = dust(e, 1.0F);
        ParticleOptions sig = signature(e);
        RandomSource r = level.random;
        // Desvio lateral do zigue-zague (só no raio): pontos de controle a cada ~1.2 bloco
        Vec3 side = jagged ? perpendicular(d) : Vec3.ZERO;
        Vec3 up = jagged ? d.cross(side).normalize() : Vec3.ZERO;
        double offA = 0, offB = 0, nextA = 0, nextB = 0;
        int seg = Math.max(1, (int) (steps / Math.max(1.0, len / 1.2)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            if (jagged) {
                if (i % seg == 0) {
                    offA = nextA;
                    offB = nextB;
                    boolean last = i + seg > steps;
                    nextA = last ? 0 : (r.nextDouble() - 0.5) * 0.7;
                    nextB = last ? 0 : (r.nextDouble() - 0.5) * 0.7;
                }
                double f = (i % seg) / (double) seg;
                p = p.add(side.scale(Mth.lerp(f, offA, nextA))).add(up.scale(Mth.lerp(f, offB, nextB)));
                level.sendParticles(fx(SpellFxParticles.SPARK, lighten(e.color, 0.4F), 0.8F, 4, -1, 0), p.x, p.y, p.z, 0,
                        d.x / len, d.y / len, d.z / len, 0.05);
            }
            level.sendParticles(g, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 4 == 0) level.sendParticles(sig, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        if (jagged) flash(level, to, e.color, 0.7F);
    }

    /**
     * Partículas de conjuração em volta de quem está segurando a tecla. v1.1: também mantém o CÍRCULO MÁGICO aos pés
     * (e na frente da mão, nas magias fortes) enquanto a conjuração durar.
     */
    public static void channeling(ServerLevel level, Entity caster, Element e) {
        double a = (caster.tickCount * 0.35) % (Math.PI * 2);
        ParticleOptions g = dust(e, 1.0F);
        for (int i = 0; i < 3; i++) {
            double ang = a + i * Math.PI * 2 / 3;
            level.sendParticles(g, caster.getX() + Math.cos(ang) * 0.9, caster.getY() + 1.0,
                    caster.getZ() + Math.sin(ang) * 0.9, 1, 0, 0.1, 0, 0);
        }
        // Runas subindo em espiral + partícula do elemento
        level.sendParticles(fx(SpellFxParticles.RUNE, lighten(e.color, 0.3F), 0.9F, 0, -1, 0),
                caster.getX(), caster.getY() + 0.2, caster.getZ(), 1, 0.6, 0.1, 0.6, 0.01);
        level.sendParticles(signature(e), caster.getX(), caster.getY() + 1.4, caster.getZ(), 1, 0.45, 0.35, 0.45, 0.02);
        SpellVisuals.channelCircle(level, caster, e);
    }

    /** Rastro no cliente (projéteis). Chamar só no lado cliente. */
    public static void clientTrail(Level level, Vec3 pos, Vec3 motion, Element e, float size) {
        clientTrail(level, pos, motion, e, e.color, size, 1);
    }

    /**
     * Rastro no cliente com a cor da magia e o tier visual (mais partículas e maiores nos tiers altos).
     * Respeita a opção "reduzir partículas" (o vanilla já respeita a opção de partículas do jogo).
     */
    public static void clientTrail(Level level, Vec3 pos, Vec3 motion, Element e, int rgb, float size, int tier) {
        boolean reduced = reducedParticles();
        int n = Math.max(1, (int) ((2 + tier) * size * (reduced ? 0.4F : 1.0F)));
        RandomSource r = level.random;
        ParticleOptions g = glow(rgb, (0.9F + 0.15F * tier) * size);
        ParticleOptions sig = style(e, e.color, 0.8F + 0.12F * tier);
        for (int i = 0; i < n; i++) {
            double f = i / (double) n;
            double x = pos.x - motion.x * f;
            double y = pos.y - motion.y * f;
            double z = pos.z - motion.z * f;
            level.addParticle(g, x, y, z, (r.nextDouble() - 0.5) * 0.02, (r.nextDouble() - 0.5) * 0.02, (r.nextDouble() - 0.5) * 0.02);
            if (r.nextInt(reduced ? 5 : 2) == 0) {
                level.addParticle(sig, x + (r.nextDouble() - 0.5) * 0.2, y + (r.nextDouble() - 0.5) * 0.2, z + (r.nextDouble() - 0.5) * 0.2,
                        -motion.x * 0.08 + (r.nextDouble() - 0.5) * 0.04, -motion.y * 0.08 + (r.nextDouble() - 0.5) * 0.04,
                        -motion.z * 0.08 + (r.nextDouble() - 0.5) * 0.04);
            }
        }
        if (tier >= 3 && !reduced && r.nextInt(3) == 0) {
            level.addParticle(fx(SpellFxParticles.MOTE, lighten(rgb, 0.5F), 0.8F, 0, -1, 0), pos.x, pos.y, pos.z,
                    (r.nextDouble() - 0.5) * 0.1, (r.nextDouble() - 0.5) * 0.1, (r.nextDouble() - 0.5) * 0.1);
        }
    }

    // =====================================================================================
    //  v1.1 — efeitos novos (servidor)
    // =====================================================================================

    /**
     * Impacto de magia: clarão + onda de choque + estilhaços do elemento + faíscas. Escala com o {@code tier}
     * (0 discreto .. 5 ultimate). {@code normal} = face atingida (null = para cima).
     */
    public static void impact(ServerLevel level, Vec3 pos, Element e, int tier, @Nullable Vec3 normal) {
        impact(level, pos, e, e.color, tier, normal);
    }

    /** Impacto com a cor da magia ({@code rgb}); a partícula característica continua sendo a do elemento. */
    public static void impact(ServerLevel level, Vec3 pos, Element e, int rgb, int tier, @Nullable Vec3 normal) {
        tier = Mth.clamp(tier, 0, 5);
        Vec3 n = normal == null || normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 c = pos.add(n.scale(0.1));
        flash(level, c, rgb, 0.6F + 0.35F * tier);
        if (tier >= 1) shockwave(level, c, rgb, 0.8F + 0.6F * tier, 8 + 2 * tier, n);
        int count = 6 + 4 * tier;
        double spread = 0.15 + 0.08 * tier;
        level.sendParticles(glow(rgb, 1.2F + 0.15F * tier), c.x, c.y, c.z, count, spread, spread, spread, 0.04 + 0.01 * tier);
        level.sendParticles(style(e, e.color, 1.0F + 0.1F * tier), c.x, c.y, c.z, count, spread, spread, spread, 0.12 + 0.04 * tier);
        level.sendParticles(accent(e, e.color, 1.0F), c.x, c.y, c.z, Math.max(2, count / 3), spread, spread, spread, 0.15);
        // Faíscas voando para fora da superfície
        RandomSource r = level.random;
        int sparks = 3 + 3 * tier;
        ParticleOptions sp = fx(SpellFxParticles.SPARK, lighten(rgb, 0.5F), 0.8F + 0.1F * tier, 0, -1, 0);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().add(n.scale(0.9)).normalize();
            level.sendParticles(sp, c.x, c.y, c.z, 0, v.x, v.y, v.z, 0.25 + 0.06 * tier + r.nextDouble() * 0.15);
        }
        if (tier >= 3) {
            // Runa no chão embaixo do impacto e um pilar curto nos tiers mais altos
            if (n.y > 0.5) runeCircle(level, pos.add(0, 0.05, 0), rgb, 1.0F + 0.5F * tier, 24 + 4 * tier, tier);
            if (tier >= 4) pillar(level, pos, rgb, 0.5F + 0.15F * tier, 3.0F + tier, 14);
        }
    }

    /** Clarão (raio em blocos). */
    public static void flash(ServerLevel level, Vec3 pos, int rgb, float radius) {
        sendFar(level, fx(SpellFxParticles.FLASH, rgb, radius, 0, -1, 0), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }

    /** Onda de choque: anel que cresce até {@code radius} em {@code life} ticks, no plano de {@code normal} (null = chão). */
    public static void shockwave(ServerLevel level, Vec3 pos, int rgb, float radius, int life, @Nullable Vec3 normal) {
        int mode = normal == null || Math.abs(normal.y) > 0.92 ? SpellFxParticles.MODE_GROUND : SpellFxParticles.MODE_NORMAL;
        ParticleOptions o = fx(SpellFxParticles.SHOCKWAVE, rgb, radius, life, -1, SpellFxParticles.flags(0, mode, 0));
        if (mode == SpellFxParticles.MODE_NORMAL) {
            Vec3 n = normal.normalize();
            sendFar(level, o, pos.x, pos.y, pos.z, 0, n.x, n.y, n.z, 1.0);
        } else {
            sendFar(level, o, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Círculo rúnico desenhado no chão (decalque girando, some sozinho). Áreas, zonas, impactos grandes.
     * {@code tier} escolhe o desenho (0-1 simples, 2-4 principal, 5 camada de ultimate + selo interno).
     */
    public static void runeCircle(ServerLevel level, Vec3 pos, int rgb, float radius, int life, int tier) {
        int layer = tier <= 1 ? 3 : 0;
        sendFar(level, fx(SpellFxParticles.CIRCLE, rgb, radius, life, -1, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, layer)),
                pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        if (tier >= 3) {
            sendFar(level, fx(SpellFxParticles.CIRCLE, lighten(rgb, 0.3F), radius * 0.55F, life, -1,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 1) | SpellFxParticles.FLAG_REVERSE),
                    pos.x, pos.y + 0.01, pos.z, 1, 0, 0, 0, 0);
        }
        if (tier >= 5) {
            sendFar(level, fx(SpellFxParticles.CIRCLE, rgb, radius * 1.35F, life, -1,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 2)), pos.x, pos.y - 0.01, pos.z, 1, 0, 0, 0, 0);
        }
    }

    /** Pilar de luz vertical saindo de {@code base}. */
    public static void pillar(ServerLevel level, Vec3 base, int rgb, float radius, float height, int life) {
        int h16 = Mth.clamp((int) (height * 16), 16, 16 * 64);
        sendFar(level, fx(SpellFxParticles.PILLAR, rgb, radius, life, h16, 0), base.x, base.y, base.z, 1, 0, 0, 0, 0);
    }

    /** Círculo mágico preso a uma entidade (pés/frente/céu). Ver {@link SpellVisuals} para o uso nas conjurações. */
    public static void castCircle(ServerLevel level, Entity caster, int rgb, float radius, int life, int flags) {
        sendFar(level, fx(SpellFxParticles.CIRCLE, rgb, radius, life, caster.getId(), flags),
                caster.getX(), caster.getY(), caster.getZ(), 1, 0, 0, 0, 0);
    }

    /**
     * Envia partículas para todo jogador até {@link #FAR} blocos. Perto: caminho normal (respeita a opção de
     * partículas); longe: "longa distância" para não perder o efeito.
     */
    public static void sendFar(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            double d2 = sp.distanceToSqr(x, y, z);
            if (d2 > FAR * FAR) continue;
            level.sendParticles(sp, p, d2 > 32 * 32, x, y, z, count, dx, dy, dz, speed);
        }
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    /** Som de conjuração do elemento. */
    public static SoundEvent castSoundOf(Element e) {
        return switch (e) {
            case FIRE, MAGMA, SUN, HELLFIRE -> SoundEvents.BLAZE_SHOOT;
            case WATER, ABYSS, PURITY, STEAM -> SoundEvents.PLAYER_SPLASH;
            case EARTH, SAND, CRYSTAL, GRAVITY -> SoundEvents.STONE_BREAK;
            case WIND, ASTRAL -> SoundEvents.PHANTOM_FLAP;
            case LIGHT -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case SHADOW, VOID -> SoundEvents.ENDERMAN_AMBIENT;
            case LIGHTNING -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case ICE -> SoundEvents.GLASS_BREAK;
            case NATURE -> SoundEvents.AZALEA_LEAVES_PLACE;
            case TWILIGHT -> SoundEvents.BEACON_POWER_SELECT;
            default -> SoundEvents.EVOKER_CAST_SPELL;
        };
    }

    public static void castSound(ServerLevel level, Entity caster, Element e) {
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), castSoundOf(e), SoundSource.PLAYERS, 0.7F,
                0.9F + level.random.nextFloat() * 0.3F);
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================

    /** Clareia uma cor RGB (0 = igual, 1 = branco). */
    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Opção de cliente "reduzir partículas" (falso se a config não carregou). */
    public static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Vec3 perpendicular(Vec3 d) {
        Vec3 s = d.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0E-6) s = d.cross(new Vec3(1, 0, 0));
        return s.normalize();
    }

    private ElementFx() {}
}
