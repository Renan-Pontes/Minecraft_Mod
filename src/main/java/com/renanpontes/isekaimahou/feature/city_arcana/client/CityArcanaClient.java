package com.renanpontes.isekaimahou.feature.city_arcana.client;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.city_arcana.CityArcanaNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import org.joml.Vector3f;

/**
 * Parte de cliente do módulo "city_arcana": desenha os efeitos dos prédios mágicos que o servidor manda
 * ({@link CityArcanaNet.Fx}). Tudo passa pelo {@link FxQuality} (quantidade, distância e extras).
 */
public class CityArcanaClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        CityArcanaNet.client = CityArcanaClient::play;
    }

    private static void play(CityArcanaNet.Fx fx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 pos = fx.pos();
        Camera cam = mc.gameRenderer.getMainCamera();
        // o domo inteiro é grande: mede até a borda mais perto, não até o centro
        double dist = cam.getPosition().distanceToSqr(pos);
        if (fx.kind() == CityArcanaNet.DOME) dist = Math.max(0, Math.sqrt(dist) - fx.size()) * Math.max(0, Math.sqrt(dist) - fx.size());
        if (!FxQuality.inRange(dist)) return;
        RandomSource r = level.random;
        switch (fx.kind()) {
            case CityArcanaNet.WARD_HIT -> hexPatch(level, r, pos, fx.vec(), fx.size(), fx.rgb());
            case CityArcanaNet.NULLIFY -> nullify(level, r, pos, fx.size());
            case CityArcanaNet.ZAP -> zap(level, r, pos, fx.vec(), fx.rgb());
            case CityArcanaNet.REVEAL -> ring(level, r, pos, fx.size(), fx.rgb());
            case CityArcanaNet.DOME -> dome(level, r, pos, fx.size(), fx.rgb(), cam.getPosition());
            case CityArcanaNet.OBELISK_PULSE -> obeliskPulse(level, r, pos, fx.size(), fx.rgb());
            case CityArcanaNet.MANA -> mana(level, r, pos, fx.rgb());
            default -> { }
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private static void add(ClientLevel level, ParticleOptions p, Vec3 at, double vx, double vy, double vz) {
        level.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
    }

    /** Base ortonormal do plano perpendicular a n. */
    private static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : n.normalize();
        Vec3 ref = Math.abs(nn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = nn.cross(ref).normalize();
        Vec3 v = nn.cross(u).normalize();
        return new Vec3[] {nn, u, v};
    }

    /** Placa de hexágonos do domo acendendo onde a magia bateu (com contorno e faíscas). */
    private static void hexPatch(ClientLevel level, RandomSource r, Vec3 c, Vec3 normal, float radius, int rgb) {
        Vec3[] b = basis(normal);
        double step = 0.42;
        int skip = FxQuality.atLeast(Graphics.HIGH) ? 1 : FxQuality.atLeast(Graphics.MEDIUM) ? 2 : 3;
        DustParticleOptions face = dust(rgb, 0.7F);
        DustParticleOptions edge = dust(0xFFFFFF, 0.9F);
        int i = 0;
        int n = (int) Math.ceil(radius / step) + 1;
        for (int a = -n; a <= n; a++) {
            for (int k = -n; k <= n; k++) {
                double x = (a + k * 0.5) * step, y = k * 0.866 * step;
                double d = Math.sqrt(x * x + y * y);
                if (d > radius || (i++ % skip) != 0) continue;
                // borda dos hexágonos: pontos da rede mais claros perto do contorno
                Vec3 at = c.add(b[1].scale(x)).add(b[2].scale(y));
                double out = 0.01 + 0.02 * (1 - d / radius);
                add(level, d > radius * 0.8 ? edge : face, at, b[0].x * out, b[0].y * out, b[0].z * out);
            }
        }
        int sparks = FxQuality.count(10);
        for (int s = 0; s < sparks; s++) {
            double ang = r.nextDouble() * Math.PI * 2;
            Vec3 at = c.add(b[1].scale(Math.cos(ang) * radius * 0.6)).add(b[2].scale(Math.sin(ang) * radius * 0.6));
            add(level, ParticleTypes.END_ROD, at, b[0].x * 0.08 + (r.nextDouble() - 0.5) * 0.05, b[0].y * 0.08,
                    b[0].z * 0.08 + (r.nextDouble() - 0.5) * 0.05);
        }
        if (FxQuality.atLeast(Graphics.MEDIUM)) {
            for (int s = 0; s < FxQuality.count(6); s++) add(level, ParticleTypes.ENCHANT, c, (r.nextDouble() - 0.5), r.nextDouble() * 0.5, (r.nextDouble() - 0.5));
        }
    }

    /** Runas roxas implodem no conjurador e somem em fumaça. */
    private static void nullify(ClientLevel level, RandomSource r, Vec3 c, float size) {
        DustParticleOptions purple = dust(0x9B5CFF, 1.0F);
        DustParticleOptions dark = dust(0x2A1840, 1.2F);
        int n = FxQuality.count(18);
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            double rad = 1.1 * size;
            Vec3 at = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.5) * 0.8, Math.sin(ang) * rad);
            Vec3 v = c.subtract(at).scale(0.12);
            add(level, i % 2 == 0 ? purple : dark, at, v.x, v.y, v.z);
        }
        for (int i = 0; i < FxQuality.count(6); i++) add(level, ParticleTypes.SMOKE, c, (r.nextDouble() - 0.5) * 0.06, 0.04, (r.nextDouble() - 0.5) * 0.06);
        if (FxQuality.atLeast(Graphics.MEDIUM)) {
            for (int i = 0; i < FxQuality.count(4); i++) add(level, ParticleTypes.SOUL, c, (r.nextDouble() - 0.5) * 0.04, 0.03, (r.nextDouble() - 0.5) * 0.04);
        }
    }

    /** Raio em zigue-zague do cristal da barreira até o alvo. */
    private static void zap(ClientLevel level, RandomSource r, Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        int steps = Math.max(4, (int) (len / (FxQuality.atLeast(Graphics.HIGH) ? 0.35 : 0.7)));
        DustParticleOptions core = dust(rgb, 0.8F);
        Vec3 prev = from;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double jit = i == steps ? 0 : 0.35 * Mth.sin((float) (t * Math.PI));
            Vec3 at = from.add(d.scale(t)).add((r.nextDouble() - 0.5) * jit, (r.nextDouble() - 0.5) * jit, (r.nextDouble() - 0.5) * jit);
            Vec3 mid = prev.add(at).scale(0.5);
            add(level, core, mid, 0, 0, 0);
            add(level, core, at, 0, 0, 0);
            prev = at;
        }
        for (int i = 0; i < FxQuality.count(5); i++) add(level, ParticleTypes.ELECTRIC_SPARK, to, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
    }

    /** Anel de luz se abrindo a partir do olho do farol. */
    private static void ring(ClientLevel level, RandomSource r, Vec3 c, float radius, int rgb) {
        int n = FxQuality.count(36);
        DustParticleOptions gold = dust(rgb, 1.0F);
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            double vx = Math.cos(ang), vz = Math.sin(ang);
            add(level, ParticleTypes.END_ROD, c, vx * radius / 40.0, 0, vz * radius / 40.0);
            if (FxQuality.atLeast(Graphics.MEDIUM)) add(level, gold, c.add(vx * 1.2, 0, vz * 1.2), 0, 0, 0);
        }
    }

    /** Domo da barreira: meridianos e paralelos de pontos perto de quem está olhando. */
    private static void dome(ClientLevel level, RandomSource r, Vec3 c, float half, int rgb, Vec3 viewer) {
        double R = Math.max(8, half);
        DustParticleOptions p = dust(rgb, 1.6F);
        int budget = FxQuality.count(700);
        double near = FxQuality.range();
        int drawn = 0;
        // meridianos a cada 15° e paralelos a cada 10° (só os pedaços dentro do alcance do preset)
        for (int m = 0; m < 24 && drawn < budget; m++) {
            double phi = Math.PI * 2 * m / 24;
            for (int k = 0; k <= 40 && drawn < budget; k++) {
                double th = Math.PI / 2 * k / 40;
                Vec3 at = c.add(Math.cos(phi) * Math.cos(th) * R, Math.sin(th) * R * 0.6, Math.sin(phi) * Math.cos(th) * R);
                if (at.distanceToSqr(viewer) > near * near) continue;
                add(level, p, at, 0, 0, 0);
                drawn++;
            }
        }
        for (int k = 1; k < 9 && drawn < budget; k++) {
            double th = Math.PI / 2 * k / 9;
            int seg = 72;
            for (int m = 0; m < seg && drawn < budget; m++) {
                double phi = Math.PI * 2 * m / seg;
                Vec3 at = c.add(Math.cos(phi) * Math.cos(th) * R, Math.sin(th) * R * 0.6, Math.sin(phi) * Math.cos(th) * R);
                if (at.distanceToSqr(viewer) > near * near) continue;
                add(level, p, at, 0, 0, 0);
                drawn++;
            }
        }
    }

    /** Onda escura descendo pelo obelisco e se espalhando no chão. */
    private static void obeliskPulse(ClientLevel level, RandomSource r, Vec3 top, float height, int rgb) {
        DustParticleOptions p = dust(rgb, 1.3F);
        int n = FxQuality.count(24);
        for (int i = 0; i < n; i++) {
            double y = height * i / n;
            add(level, p, top.add((r.nextDouble() - 0.5) * 0.8, -y, (r.nextDouble() - 0.5) * 0.8), 0, -0.05, 0);
        }
        Vec3 base = top.add(0, -height + 0.3, 0);
        int m = FxQuality.count(32);
        for (int i = 0; i < m; i++) {
            double ang = Math.PI * 2 * i / m;
            add(level, ParticleTypes.SOUL_FIRE_FLAME, base, Math.cos(ang) * 0.25, 0.01, Math.sin(ang) * 0.25);
        }
    }

    /** Fagulhas de mana subindo em volta do jogador (Torre de Mana). */
    private static void mana(ClientLevel level, RandomSource r, Vec3 feet, int rgb) {
        DustParticleOptions p = dust(rgb, 0.6F);
        int n = FxQuality.count(6);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            add(level, p, feet.add(Math.cos(ang) * 0.5, r.nextDouble() * 0.4, Math.sin(ang) * 0.5), 0, 0.06 + r.nextDouble() * 0.04, 0);
        }
    }
}
