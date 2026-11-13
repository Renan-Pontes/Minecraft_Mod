package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.network.SkyPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Onda 7 (sky_ui) — <b>névoa de nuvem</b> das Ilhas Celestes, toda no cliente e leve: fiapos de nuvem escorrendo pela
 * borda da ilha e um borrifo no pé das cachoeiras (flocos de neve nas de gelo). O servidor só avisa, de 5 em 5 s, que há
 * uma ilha por perto ({@link SkyPackets.Near}); o contorno sai da mesma conta do gerador ({@link SkyPreview#outline}).
 * Quantidade e distância pelo preset gráfico ({@link FxQuality}); nada aparece com o jogo pausado.
 */
final class SkyFx {
    /** Ilha conhecida: some sozinha se o servidor parar de avisar (o jogador se afastou). */
    private static final class Isle {
        String dim = "";
        BlockPos heart = BlockPos.ZERO;
        int half;
        int[] falls = new int[0];
        float[] rim = new float[0];
        long seen;
    }

    private static final Map<Long, Isle> ISLES = new HashMap<>();
    private static final int KEEP_TICKS = 300;
    private static int age;

    static void init() {
        SkyPackets.onNear = SkyFx::accept;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ISLES.clear());
    }

    private static void accept(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos heart = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
        int radius = Mth.clamp(t.getInt("R"), 1, CityLayout.MAX_RADIUS);
        Isle isle = ISLES.computeIfAbsent(heart.asLong(), k -> new Isle());
        int half = CityLayout.half(radius);
        if (isle.half != half || !isle.heart.equals(heart)) {
            // contorno novo só quando a ilha muda de forma: um ponto a cada ~7 blocos de borda
            int points = Mth.clamp(half * 8 / 7, 96, 384);
            isle.rim = new float[points * 2];
            SkyPreview.outline(heart, radius, isle.rim);
            isle.half = half;
        }
        isle.heart = heart;
        isle.dim = t.getString("Dim");
        isle.falls = t.getIntArray("Falls");
        isle.seen = mc.level.getGameTime();
    }

    private static void tick() {
        if (ISLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) {
            ISLES.clear();
            return;
        }
        if (mc.isPaused()) return;
        age++;
        long now = level.getGameTime();
        String dim = level.dimension().location().toString();
        // a névoa é grande e clara: vale um pouco além da distância dos efeitos comuns
        double range = FxQuality.range() + 24;
        RandomSource rnd = level.random;
        for (Iterator<Isle> it = ISLES.values().iterator(); it.hasNext();) {
            Isle isle = it.next();
            if (now - isle.seen > KEEP_TICKS || now < isle.seen) {
                it.remove();
                continue;
            }
            if (!dim.equals(isle.dim)) continue;
            if ((age & 1) == 0) rimMist(level, p, isle, rnd, range);
            if (age % 4 == 1) fallMist(level, p, isle, rnd);
        }
    }

    /** Fiapos de nuvem na beirada: nascem um pouco para fora e abaixo do topo e escorrem devagar para longe da ilha. */
    private static void rimMist(ClientLevel level, LocalPlayer p, Isle isle, RandomSource rnd, double range) {
        int points = isle.rim.length / 2;
        int top = isle.heart.getY() - 1;
        if (points == 0 || Math.abs(p.getY() - top) > range + 16) return;
        double cx = isle.heart.getX() + 0.5, cz = isle.heart.getZ() + 0.5;
        double px = p.getX() - cx, pz = p.getZ() - cz;
        int want = FxQuality.count(2), made = 0;
        for (int tries = want * 5; tries > 0 && made < want; tries--) {
            int i = rnd.nextInt(points);
            double rx = isle.rim[i * 2], rz = isle.rim[i * 2 + 1];
            double dx = rx - px, dz = rz - pz;
            if (dx * dx + dz * dz > range * range) continue;
            double len = Math.max(1.0, Math.sqrt(rx * rx + rz * rz));
            double ox = rx / len, oz = rz / len, out = 0.5 + rnd.nextDouble() * 2.5;
            level.addParticle(ParticleTypes.CLOUD, cx + rx + ox * out + (rnd.nextDouble() - 0.5) * 3, top + 0.8 - rnd.nextDouble() * 3.5,
                    cz + rz + oz * out + (rnd.nextDouble() - 0.5) * 3, ox * 0.012, -0.004, oz * 0.012);
            made++;
        }
    }

    /** Pé das cachoeiras: borrifo de água e um fiapo de névoa; nas de gelo, flocos descendo pela coluna. */
    private static void fallMist(ClientLevel level, LocalPlayer p, Isle isle, RandomSource rnd) {
        int[] f = isle.falls;
        for (int i = 0; i + 4 < f.length; i += 5) {
            double x = f[i] + 0.5, z = f[i + 1] + 0.5;
            int top = f[i + 2], y0 = f[i + 3];
            if (!FxQuality.inRange(p.distanceToSqr(x, Mth.clamp(p.getY(), y0, top), z))) continue;
            if (f[i + 4] == 1) {
                level.addParticle(ParticleTypes.SNOWFLAKE, x + (rnd.nextDouble() - 0.5) * 1.6, y0 + 2 + rnd.nextDouble() * (top - y0 - 2),
                        z + (rnd.nextDouble() - 0.5) * 1.6, 0, -0.02, 0);
                continue;
            }
            int n = FxQuality.count(2);
            for (int k = 0; k < n; k++) {
                level.addParticle(ParticleTypes.SPLASH, x + (rnd.nextDouble() - 0.5) * 1.4, y0 + 2.1, z + (rnd.nextDouble() - 0.5) * 1.4, 0, 0.05, 0);
            }
            if (rnd.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CLOUD, x + (rnd.nextDouble() - 0.5) * 2, y0 + 2.3, z + (rnd.nextDouble() - 0.5) * 2, 0, 0.01, 0);
            }
        }
    }

    private SkyFx() {}
}
