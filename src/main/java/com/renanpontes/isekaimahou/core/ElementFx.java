package com.renanpontes.isekaimahou.core;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.fx.FxBatch;
import com.renanpontes.isekaimahou.glyph.fx.FxBurst;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
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
 * <p>
 * v1.3 (efeitos compostos): {@link #impact} agora manda UM pacote e o cliente monta tudo pelo preset gráfico.
 * Novos atalhos para a linguagem visual por elemento: {@link #gather} (antecipação), {@link #nova} (explosão de
 * ultimate), {@link #area}, {@link #aura}, {@link #beam}, {@link #portal}, {@link #domainSphere}, {@link #skyStrike},
 * {@link #slash}, {@link #reaction} e {@link #shake} (tremor de câmera).
 * <p>
 * Release 1.0 (perf): os efeitos clássicos ({@link #burst}, {@link #ring}, {@link #line}, {@link #channeling},
 * {@link #sendFar} e tudo que usa eles) entram no LOTE do tick ({@link FxBatch}): um pacote por jogador por tick, em vez
 * de um por partícula. Anéis e linhas vão só como parâmetros e o cliente desenha pelo Preset Gráfico.
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
        FxBatch.emit(level, dust(e, 1.4F), pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02, false);
        FxBatch.emit(level, signature(e), pos.x, pos.y, pos.z, Math.max(1, count / 2), spread, spread, spread, 0.06, false);
        if (count >= 8) {
            FxBatch.emit(level, accent(e, e.color, 1.0F), pos.x, pos.y, pos.z, Math.max(1, count / 5), spread, spread, spread, 0.12, false);
        }
        // Explosões grandes ganham um clarão e uma onda (são quase sempre eventos únicos: morte, invocação...)
        if (count >= 24) flash(level, pos, e.color, (float) Math.min(3.0, 0.8 + spread * 1.2));
        if (count >= 40) shockwave(level, pos, e.color, (float) Math.min(6.0, 1.2 + spread * 2.0), 12, null);
    }

    /** Anel horizontal de partículas (raio em blocos). Um item no lote: o cliente desenha os pontos. */
    public static void ring(ServerLevel level, Vec3 center, Element e, double radius, int points) {
        FxBatch.ring(level, e, e.color, center, radius, points, 3, 1.2F, false);
    }

    /**
     * Linha de partículas entre dois pontos (feixes, encadear). Raio desenha um arco elétrico em zigue-zague.
     * Um item no lote: o cliente desenha os pontos.
     */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, Element e) {
        FxBatch.line(level, e, from, to, false);
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
            FxBatch.emit(level, g, caster.getX() + Math.cos(ang) * 0.9, caster.getY() + 1.0,
                    caster.getZ() + Math.sin(ang) * 0.9, 1, 0, 0.1, 0, 0, false);
        }
        // Runas subindo em espiral + partícula do elemento
        FxBatch.emit(level, fx(SpellFxParticles.RUNE, lighten(e.color, 0.3F), 0.9F, 0, -1, 0),
                caster.getX(), caster.getY() + 0.2, caster.getZ(), 1, 0.6, 0.1, 0.6, 0.01, false);
        FxBatch.emit(level, signature(e), caster.getX(), caster.getY() + 1.4, caster.getZ(), 1, 0.45, 0.35, 0.45, 0.02, false);
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
        boolean reduced = !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        int n = Math.max(1, (int) ((2 + tier) * size * FxQuality.particles()));
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
        impactFx(level, pos, e, rgb, tier, normal, 1.0F);
    }

    /**
     * Impacto montado no cliente (clarão + onda + núcleo do elemento + faíscas + detritos + assinatura da família +
     * rescaldo; runa no chão no tier 3+, pilar no 4+). {@code size} multiplica o tamanho (1 = normal). Tier 4+ treme a
     * câmera de quem está perto.
     */
    public static void impactFx(ServerLevel level, Vec3 pos, Element e, int rgb, int tier, @Nullable Vec3 normal, float size) {
        tier = Mth.clamp(tier, 0, 5);
        Vec3 n = normal == null || normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal.normalize();
        FxBurst.send(level, FxBurst.IMPACT, e, rgb, pos, n, size, tier, -1, 0);
        if (tier >= 4) shake(level, pos, 10 + 4 * tier * size, 0.35F * (tier - 3) * Math.min(2F, size), 10 + 2 * tier);
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
     * Envia partículas para todo jogador até {@link #FAR} blocos (entra no lote do tick). Perto: caminho normal
     * (respeita a opção de partículas); longe: "longa distância" para não perder o efeito.
     */
    public static void sendFar(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed) {
        FxBatch.emit(level, p, x, y, z, count, dx, dy, dz, speed, true);
    }

    // =====================================================================================
    //  v1.3 — efeitos compostos (montados no cliente pelo preset gráfico)
    // =====================================================================================

    /**
     * Antecipação: partículas do elemento convergindo para a frente da mão de {@code caster} por {@code ticks}
     * (carregar golpe forte). {@code radius} = de onde vêm (1,5–4 blocos). Termina num clarãozinho.
     */
    public static void gather(ServerLevel level, Entity caster, Element e, int rgb, float radius, int tier, int ticks) {
        FxBurst.send(level, FxBurst.GATHER, e, rgb, caster.getEyePosition(), Vec3.ZERO, radius, tier, caster.getId(), ticks);
    }

    /** Antecipação num ponto fixo (céu, núcleo de uma invocação, centro de um ritual). */
    public static void gatherAt(ServerLevel level, Vec3 pos, Element e, int rgb, float radius, int tier, int ticks) {
        FxBurst.send(level, FxBurst.GATHER, e, rgb, pos, Vec3.ZERO, radius, tier, -1, ticks);
    }

    /**
     * Explosão de ultimate: clarões em camadas, 3 ondas, anel vertical, pilar, detritos rente ao chão, assinatura do
     * elemento, runa gigante e 2 s de rescaldo. Treme a câmera.
     */
    public static void nova(ServerLevel level, Vec3 pos, Element e, int rgb, float radius, int tier) {
        nova(level, pos, e, rgb, radius, tier, true);
    }

    /** Nova com ou sem tremor (quem já treme a câmera por conta própria passa {@code false}). */
    public static void nova(ServerLevel level, Vec3 pos, Element e, int rgb, float radius, int tier, boolean shake) {
        FxBurst.send(level, FxBurst.NOVA, e, rgb, pos, Vec3.ZERO, radius, Math.max(4, tier), -1, 0);
        if (shake) shake(level, pos, Math.max(24, radius * 4), Math.min(2.5F, 0.6F + radius * 0.12F), 24);
    }

    /** Zona no chão: decalque girando + partículas do elemento (brasas sobem, chuva cai, neve, folhas...) por {@code ticks}. */
    public static void area(ServerLevel level, Vec3 pos, Element e, int rgb, float radius, int tier, int ticks) {
        FxBurst.send(level, FxBurst.AREA, e, rgb, pos, Vec3.ZERO, radius, tier, -1, ticks);
    }

    /** Aura (casca de partículas) em volta de {@code target} por {@code ticks}. Segue a entidade sozinha no cliente. */
    public static void aura(ServerLevel level, Entity target, Element e, int rgb, float size, int tier, int ticks) {
        FxBurst.send(level, FxBurst.AURA, e, rgb, target.position(), Vec3.ZERO, size, tier, target.getId(), ticks);
    }

    /** Feixe de {@code from} até {@code to}: selo na boca, núcleo brilhante, anéis viajando, impacto no fim. */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, Element e, int rgb, float width, int tier) {
        FxBurst.send(level, FxBurst.BEAM, e, rgb, from, to.subtract(from), width, tier, -1, 0);
        if (tier >= 4) shake(level, from, 16 + 4 * tier, 0.3F * (tier - 3), 10);
    }

    /** Portal / invocação: círculo girando no plano de {@code normal} com redemoinho por {@code ticks}. */
    public static void portal(ServerLevel level, Vec3 pos, @Nullable Vec3 normal, Element e, int rgb, float radius, int tier, int ticks) {
        Vec3 n = normal == null ? new Vec3(0, 1, 0) : normal;
        FxBurst.send(level, FxBurst.PORTAL, e, rgb, pos, n, radius, tier, -1, ticks);
    }

    /**
     * Expansão de domínio: a cúpula sobe do chão e fecha no topo em {@code ticks}; ao fechar, clarão de "troca de
     * paisagem", ondas e pilar. Treme a câmera quando fecha.
     */
    public static void domainSphere(ServerLevel level, Vec3 center, Element e, int rgb, float radius, int ticks) {
        int t = Mth.clamp(ticks, 8, 120);
        FxBurst.send(level, FxBurst.DOMAIN, e, rgb, center, Vec3.ZERO, radius, 5, -1, t);
        shake(level, center, radius * 3, 0.4F, t);
        BossKit.schedule(level, t, () -> shake(level, center, radius * 3, 1.6F, 20));
    }

    /**
     * Golpe do céu: círculo no céu + aviso no chão, energia juntando por {@code delay} ticks, depois pilar + impacto
     * (+ nova no tier 4+). Só visual — o dano continua com quem chamou.
     */
    public static void skyStrike(ServerLevel level, Vec3 ground, Element e, int rgb, float radius, int tier, int delay) {
        FxBurst.send(level, FxBurst.SKY_STRIKE, e, rgb, ground, Vec3.ZERO, radius, tier, -1, delay);
        float k = tier >= 4 ? 1.4F : 0.6F;
        if (delay <= 0) shake(level, ground, 20 + radius * 3, k, 16);
        else BossKit.schedule(level, delay, () -> shake(level, ground, 20 + radius * 3, k, 16));
    }

    /** Corte em meia-lua na frente de {@code pos} na direção {@code dir}. */
    public static void slash(ServerLevel level, Vec3 pos, Vec3 dir, Element e, int rgb, float length, int tier) {
        FxBurst.send(level, FxBurst.SLASH, e, rgb, pos, dir, length, tier, -1, 0);
    }

    /** Explosão própria de cada reação elemental (Eletrocutar, Congelar, Vapor...). */
    public static void reaction(ServerLevel level, Entity target, ElementReactions.Reaction r) {
        reaction(level, target, r, false);
    }

    /**
     * Explosão da reação, montada no cliente pelo preset. {@code lite} = versão enxuta (muitas reações no mesmo tick:
     * só clarão, onda e poucas partículas).
     */
    public static void reaction(ServerLevel level, Entity target, ElementReactions.Reaction r, boolean lite) {
        double up = target.getBbHeight() * 0.55;
        reactionAt(level, target.position().add(0, up, 0), up, Math.max(0.6F, Math.min(3F, target.getBbHeight() * 0.6F)), r, lite);
    }

    /**
     * Explosão de reação num ponto qualquer (vitrines, armadilhas). {@code toGround} = quanto o chão fica abaixo de
     * {@code center} (o decalque e as ondas rasteiras nascem lá).
     */
    public static void reactionAt(ServerLevel level, Vec3 center, double toGround, float size, ElementReactions.Reaction r, boolean lite) {
        FxBurst.send(level, FxBurst.REACTION, Element.ARCANE, r.color(), center, new Vec3(0, -Math.max(0, toGround), 0),
                size, 2, r.ordinal(), lite ? 1 : 0);
    }

    /**
     * Tremor de câmera (mesmo sistema dos chefes): {@code intensity} ≈ graus perto do centro (0,5 leve · 1,5 forte ·
     * 3 máx.). Some com a distância até {@code radius}.
     */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks) {
        // No máx. 2 tremores por tick (uma magia de área que acerta 10 alvos não vira terremoto)
        long now = level.getGameTime();
        if (now != shakeTick) {
            shakeTick = now;
            shakesThisTick = 0;
        }
        if (++shakesThisTick > 2) return;
        BossKit.shake(level, pos, radius, intensity, ticks);
    }

    private static long shakeTick = Long.MIN_VALUE;
    private static int shakesThisTick;

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
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), castSoundOf(e), SoundSource.PLAYERS, 0.3F, /* v1.0: baixo, os sons próprios (ambience) tocam por cima */
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

    /**
     * "Poucas partículas"? Agora é o Preset Gráfico que manda: LOW e MEDIUM contam como reduzido (a opção antiga
     * "reduzir partículas" limita o preset a MEDIUM). Para quantidades, prefira {@link FxQuality#count}.
     */
    public static boolean reducedParticles() {
        return !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
    }

    private ElementFx() {}
}
