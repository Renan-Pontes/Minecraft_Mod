package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * SÓ CLIENTE. Clima do Mundo Oco: grãos de areia branca caindo do céu negro e cinza fina levada pelo vento (só ao ar
 * livre — dentro da cúpula de Las Noches e em cavernas não cai nada). No Reino das Fadas, pólen brilhante subindo devagar.
 * Quantidade pelo {@link FxQuality}; nada é enviado pelo servidor.
 */
public final class HmAtmosphere {
    private static BlockParticleOption sandDust;

    static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.isPaused()) return;
        HmRealm realm = HmRealm.of(level.dimension());
        if (realm == null) return;
        BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        if (!level.canSeeSky(head)) return;
        RandomSource r = level.random;
        if (realm == HmRealm.HOLLOW) {
            if (sandDust == null) sandDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, HmRegistries.HOLLOW_SAND.get().defaultBlockState());
            int n = FxQuality.count(4);
            for (int i = 0; i < n; i++) {
                level.addParticle(sandDust, p.getX() + (r.nextDouble() - 0.5) * 32, p.getY() + 6 + r.nextDouble() * 14,
                        p.getZ() + (r.nextDouble() - 0.5) * 32, 0, 0, 0);
            }
            // Rajadas: cinza branca atravessando o deserto (o vento vem do norte, de Las Noches).
            if (p.tickCount % 3 == 0) {
                int m = FxQuality.count(2);
                for (int i = 0; i < m; i++) {
                    level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 20, p.getY() + r.nextDouble() * 4,
                            p.getZ() - 10 + r.nextDouble() * 4, 0.01, 0, 0.06);
                }
            }
            if (FxQuality.atLeast(Graphics.HIGH) && r.nextInt(40) == 0) {
                // Um redemoinho de areia passando ao longe.
                double a = r.nextDouble() * Math.PI * 2, d = 14 + r.nextDouble() * 10;
                double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d, y = p.getY();
                for (int i = 0; i < 18; i++) {
                    double t = i / 18.0, ang = t * Math.PI * 6;
                    level.addParticle(sandDust, x + Math.cos(ang) * (0.4 + t * 1.6), y + t * 5, z + Math.sin(ang) * (0.4 + t * 1.6), 0, 0, 0);
                }
            }
        } else if (p.tickCount % 2 == 0) {
            int n = FxQuality.count(2);
            for (int i = 0; i < n; i++) {
                level.addParticle(ParticleTypes.GLOW, p.getX() + (r.nextDouble() - 0.5) * 24, p.getY() + r.nextDouble() * 3,
                        p.getZ() + (r.nextDouble() - 0.5) * 24, 0, 0.02, 0);
            }
        }
    }

    private HmAtmosphere() {}
}
