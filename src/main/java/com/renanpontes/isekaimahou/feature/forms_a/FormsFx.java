package com.renanpontes.isekaimahou.feature.forms_a;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellBeamEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Visual/som das formas do módulo. Igual ao {@link ElementFx}, mas usa a COR escolhida na magia
 * ({@link SpellContext#color()}) e envia partículas também para quem está longe (Queda/Chuva miram a 48 blocos,
 * e o envio padrão do vanilla só alcança 32).
 */
public final class FormsFx {
    /** Até onde os jogadores recebem as partículas destas formas. */
    private static final double FAR = 96.0;
    private static final double NEAR = 32.0;

    /** Pó colorido com a cor ARGB/RGB informada. */
    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.max(0.05F, Math.min(4.0F, size)));
    }

    /** Clareia uma cor RGB (0 = igual, 1 = branco). Usado no "núcleo" brilhante dos feixes. */
    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Envia partículas para todos os jogadores até {@link #FAR} blocos. Quem está perto recebe pelo caminho normal
     * (respeita a opção de partículas); quem está longe recebe como "longa distância" para não ficar sem ver o impacto.
     */
    public static void send(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                            double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            double d2 = sp.distanceToSqr(x, y, z);
            if (d2 > FAR * FAR) continue;
            level.sendParticles(sp, p, d2 > NEAR * NEAR, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void send(ServerLevel level, ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        send(level, p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** Partícula com velocidade (modo "count = 0" do vanilla: dx/dy/dz viram a direção). */
    public static void moving(ServerLevel level, ParticleOptions p, Vec3 pos, Vec3 velocity, double speed) {
        send(level, p, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, speed);
    }

    /**
     * Partícula do elemento para jatos com velocidade. Encantamento/portal "voam de volta" para o ponto de origem
     * no vanilla (ficariam ao contrário), então são trocadas por equivalentes que seguem a velocidade.
     */
    public static ParticleOptions jet(Element e) {
        ParticleOptions sig = ElementFx.signature(e);
        if (sig == ParticleTypes.ENCHANT) return ParticleTypes.END_ROD;
        if (sig == ParticleTypes.PORTAL) return ParticleTypes.DRAGON_BREATH;
        return sig;
    }

    /** Explosão de partículas com a cor da magia + partícula característica do elemento. */
    public static void burst(SpellContext ctx, Vec3 pos, int count, double spread) {
        ServerLevel level = ctx.level();
        send(level, ElementFx.glow(ctx.color(), 1.5F), pos, count, spread, 0.02);
        send(level, ElementFx.signature(ctx.element()), pos, Math.max(1, count / 2), spread, 0.06);
        if (count >= 24) ElementFx.flash(level, pos, ctx.color(), (float) Math.min(3.0, 0.8 + spread * 1.2));
    }

    /**
     * Raio de luz de verdade (renderer de feixe brilhante, ver {@link SpellBeamEntity}) de {@code from} até {@code to},
     * na cor da magia. {@code width} = largura do núcleo em blocos; {@code life} = ticks até sumir.
     */
    public static void beam(SpellContext ctx, Vec3 from, Vec3 to, float width, int life) {
        if (from.distanceToSqr(to) < 1.0E-4) return;
        ctx.level().addFreshEntity(SpellBeamEntity.create(ctx.level(), from, to, ctx.color(), width, life));
    }

    /**
     * Faíscas do elemento ao longo de um raio (fogo solta chamas, gelo flocos...), uma a cada {@code spacing} blocos.
     * Complementa o {@link #beam}: o raio mostra a cor; as faíscas, o elemento.
     */
    public static void sparks(SpellContext ctx, Vec3 from, Vec3 to, double spacing) {
        ServerLevel level = ctx.level();
        ParticleOptions sig = jet(ctx.element());
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1.0 / len);
        int n = Math.max(1, (int) (len / Math.max(0.3, spacing)));
        for (int i = 0; i < n; i++) {
            Vec3 p = from.add(d.scale((i + ctx.random().nextDouble()) / n));
            moving(level, sig, p, dir.add((ctx.random().nextDouble() - 0.5) * 0.6, (ctx.random().nextDouble() - 0.5) * 0.6,
                    (ctx.random().nextDouble() - 0.5) * 0.6), 0.04);
        }
    }

    /** Rastro de "avanço" (Saltar em formas de curto alcance): a magia corta o ar do alvo anterior até o próximo. */
    public static void dash(SpellContext ctx, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 0.25) return;
        beam(ctx, from, to, 0.08F, 5);
        sparks(ctx, from, to, 1.2);
        send(ctx.level(), ParticleTypes.CLOUD, to, 4, 0.2, 0.02);
        sound(ctx, to, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6F, 1.5F);
    }

    /** Anel horizontal com a cor da magia. {@code density} = pontos por bloco de circunferência. */
    public static void ring(SpellContext ctx, Vec3 center, double radius, double density) {
        ServerLevel level = ctx.level();
        ParticleOptions d = ElementFx.glow(ctx.color(), 1.3F);
        ParticleOptions sig = ElementFx.signature(ctx.element());
        int points = Math.max(8, (int) (Math.PI * 2 * radius * density));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            send(level, d, x, center.y + 0.1, z, 1, 0.03, 0.02, 0.03, 0);
            if (i % 4 == 0) send(level, sig, x, center.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Opção de cliente "reduzir partículas" (falso se a config ainda não carregou). Só faz sentido no cliente. */
    public static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Som na posição; magias com Silencioso tocam bem mais baixo. */
    public static void sound(SpellContext ctx, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        float v = ctx.formStats().silent ? volume * 0.25F : volume;
        ctx.level().playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, v,
                pitch * (0.92F + ctx.random().nextFloat() * 0.16F));
    }

    public static void sound(SpellContext ctx, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        float v = ctx.formStats().silent ? volume * 0.25F : volume;
        ctx.level().playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, v,
                pitch * (0.92F + ctx.random().nextFloat() * 0.16F));
    }

    private FormsFx() {}
}
