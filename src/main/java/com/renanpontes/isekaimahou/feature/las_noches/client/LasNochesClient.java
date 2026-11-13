package com.renanpontes.isekaimahou.feature.las_noches.client;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicApi;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.las_noches.LnLayout;
import com.renanpontes.isekaimahou.feature.las_noches.LnNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "las_noches": música ambiente "las_noches" enquanto o jogador está no complexo
 * ({@link BossMusicApi}), poeira de luz sob o céu falso da cúpula e o redemoinho dos selos da torre. Tudo pelo
 * {@link FxQuality} (quantidade e alcance).
 */
public class LasNochesClient implements ClientModule {
    private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.72F), 0.7F);
    private static final DustParticleOptions SEAL = new DustParticleOptions(new Vector3f(0.72F, 0.78F, 0.92F), 0.9F);
    @Nullable
    private static volatile BlockPos center;

    @Override
    public void initClient(IEventBus modBus) {
        LnNet.clientInfo = info -> center = info.built() ? info.center() : null;
        NeoForge.EVENT_BUS.addListener(LasNochesClient::onTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> center = null);
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        BlockPos c = center;
        if (p == null || level == null || c == null || mc.isPaused() || !level.dimension().equals(HmRealm.HOLLOW.key)) return;
        double rx = p.getX() - c.getX(), ry = p.getY() - c.getY(), rz = p.getZ() - c.getZ();
        boolean inComplex = rx >= LnLayout.MIN_X && rx <= LnLayout.MAX_X + 1 && rz >= LnLayout.MIN_Z && rz <= LnLayout.MAX_Z + 1
                && ry >= -6 && ry <= LnLayout.MAX_Y;
        if (!inComplex) return;
        if (p.tickCount % 10 == 0) BossMusicApi.requestAmbient("las_noches");
        RandomSource r = level.random;
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        boolean underSky = ry >= 0 && ry < LnLayout.CAP && d < LnLayout.R - 2;
        if (underSky && p.tickCount % 2 == 0) {
            // Poeira de luz flutuando sob o céu falso (raios de sol de mentira).
            int n = FxQuality.count(3);
            for (int i = 0; i < n; i++) {
                level.addParticle(MOTE, p.getX() + (r.nextDouble() - 0.5) * 24, p.getY() + r.nextDouble() * 10 - 1,
                        p.getZ() + (r.nextDouble() - 0.5) * 24, 0, 0, 0);
            }
            if (FxQuality.atLeast(Graphics.HIGH) && r.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5) * 18, p.getY() + 4 + r.nextDouble() * 6,
                        p.getZ() + (r.nextDouble() - 0.5) * 18, 0, -0.01, 0);
            }
        }
        sealSwirl(level, c, rx, ry, rz, p.tickCount);
    }

    /** Redemoinho lento sobre os selos negros da torre (mostra onde agachar). */
    private static void sealSwirl(ClientLevel level, BlockPos c, double rx, double ry, double rz, int tick) {
        if (tick % 3 != 0) return;
        for (BlockPos pad : new BlockPos[] {LnLayout.PAD_LOW, LnLayout.PAD_HIGH}) {
            double dx = rx - pad.getX(), dy = ry - pad.getY(), dz = rz - pad.getZ();
            if (dx * dx + dy * dy + dz * dz > 14 * 14) continue;
            int n = FxQuality.count(4);
            for (int i = 0; i < n; i++) {
                double a = tick * 0.15 + i * (Math.PI * 2 / n);
                double x = c.getX() + pad.getX() + 0.5 + Math.cos(a) * 1.3, z = c.getZ() + pad.getZ() + 0.5 + Math.sin(a) * 1.3;
                level.addParticle(SEAL, x, c.getY() + pad.getY() + 0.1 + (i % 3) * 0.4, z, 0, 0.02, 0);
            }
            if (FxQuality.atLeast(Graphics.MEDIUM)) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, c.getX() + pad.getX() + 0.5, c.getY() + pad.getY() + 0.2,
                        c.getZ() + pad.getZ() + 0.5, 0, 0.05, 0);
            }
        }
    }
}
