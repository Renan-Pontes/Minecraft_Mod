package com.renanpontes.isekaimahou.client.spellfx;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.fx.FxBatch;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * SÓ CLIENTE. Desenha o lote de partículas de magia do tick ({@link FxBatch}): um pacote traz tudo que aconteceu perto
 * do jogador. Cada item é desenhado como o pacote de partículas do vanilla faria, mas passando pelo Preset Gráfico:
 * <ul>
 *   <li>itens pequenos além de {@link FxQuality#range()} são ignorados (LOW = 24 blocos); os "de longe" (formas que
 *   miram a 48 blocos) valem até a distância do preset, nunca menos que 32; peças grandes (clarão, onda, círculo,
 *   pilar) aparecem até 96;</li>
 *   <li>partículas {@code fx_*} já são cortadas pelos providers ({@code SpellFxClient}); as do vanilla (lascas de
 *   bloco, fumaça...) são contadas aqui com {@link FxQuality#count};</li>
 *   <li>anéis e linhas chegam só como parâmetros e os pontos são criados aqui, na densidade do preset.</li>
 * </ul>
 */
public final class FxBatchClient {
    public static void receive(FxBatch.Payload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = FxQuality.range();
        float q = FxQuality.particles();
        RandomSource rnd = level.random;
        // Formas: os pontos já saem contados pelo preset, então os providers não cortam de novo
        FxBatch.Sink shape = (o, x, y, z, n, dx, dy, dz, s) -> spawn(level, rnd, o, false, x, y, z, n, dx, dy, dz, s, 1F);
        // Formas "de longe" além de 32 blocos: forçadas, senão o vanilla nem cria a partícula
        FxBatch.Sink shapeFar = (o, x, y, z, n, dx, dy, dz, s) -> spawn(level, rnd, o, true, x, y, z, n, dx, dy, dz, s, 1F);
        double farLoose = Math.max(range, FxBatch.NEAR);
        for (FxBatch.Entry e : p.entries()) {
            double d2 = cam.distanceToSqr(e.x(), e.y(), e.z());
            try {
                if (e instanceof FxBatch.Emit m) {
                    // Peças grandes (clarão, onda, círculo, pilar) aparecem até 96 blocos; partículas soltas "de longe"
                    // seguem a distância do preset (nunca menos que os 32 do vanilla); as normais, a do preset
                    double lim = !m.far() ? range : big(m.particle()) ? FxBatch.FAR : farLoose;
                    if (d2 > lim * lim) continue;
                    spawn(level, rnd, m.particle(), m.far() && d2 > FxBatch.NEAR * FxBatch.NEAR, m.x(), m.y(), m.z(), m.count(),
                            m.dx(), m.dy(), m.dz(), m.speed(), q);
                } else if (e instanceof FxBatch.Ring r) {
                    double edge = Math.max(0, Math.sqrt(d2) - r.radius());
                    if (edge > (r.far() ? farLoose : range)) continue;
                    SpellFxClient.LOCAL = true;
                    FxBatch.expand(r, q, r.far() && edge > FxBatch.NEAR ? shapeFar : shape);
                } else if (e instanceof FxBatch.Line l) {
                    // o servidor já filtrou pela distância até o segmento; aqui só o corte do preset pelo meio da linha
                    double mx = e.x() + l.vx() * 0.5, my = e.y() + l.vy() * 0.5, mz = e.z() + l.vz() * 0.5;
                    double half = Math.sqrt(l.vx() * l.vx() + l.vy() * l.vy() + l.vz() * l.vz()) * 0.5;
                    double near = Math.max(0, Math.sqrt(cam.distanceToSqr(mx, my, mz)) - half);
                    if (near > (l.far() ? farLoose : range)) continue;
                    SpellFxClient.LOCAL = true;
                    FxBatch.expand(l, rnd, q, l.far() && near > FxBatch.NEAR ? shapeFar : shape);
                }
            } catch (RuntimeException ex) {
                // partícula que este cliente não consegue criar: pula o item, o resto do lote continua
            } finally {
                SpellFxClient.LOCAL = false;
            }
        }
    }

    /** Peça única e grande (um quadrado na tela), que vale a pena ver de longe. */
    private static boolean big(ParticleOptions o) {
        if (o instanceof FxOptions f) {
            ParticleType<?> t = f.type();
            return t == SpellFxParticles.FLASH.get() || t == SpellFxParticles.SHOCKWAVE.get() || t == SpellFxParticles.CIRCLE.get()
                    || t == SpellFxParticles.PILLAR.get();
        }
        return o.getType().getOverrideLimiter() || o.getType() == ParticleTypes.FLASH;
    }

    /** Igual ao tratamento do pacote de partículas do vanilla ({@code count} 0 = velocidade dirigida). */
    private static void spawn(ClientLevel level, RandomSource rnd, ParticleOptions o, boolean force, double x, double y, double z,
                              int count, double dx, double dy, double dz, double speed, float quality) {
        if (count == 0) {
            level.addParticle(o, force, x, y, z, speed * dx, speed * dy, speed * dz);
            return;
        }
        int n = count;
        // Partículas do vanilla não conhecem o preset: conta aqui (as fx_* são cortadas no provider)
        if (n > 1 && quality < 1F && !(o instanceof FxOptions)) n = Math.max(1, Math.round(n * quality));
        for (int i = 0; i < n; i++) {
            double ox = rnd.nextGaussian() * dx, oy = rnd.nextGaussian() * dy, oz = rnd.nextGaussian() * dz;
            double vx = rnd.nextGaussian() * speed, vy = rnd.nextGaussian() * speed, vz = rnd.nextGaussian() * speed;
            level.addParticle(o, force, x + ox, y + oy, z + oz, vx, vy, vz);
        }
    }

    private FxBatchClient() {}
}
