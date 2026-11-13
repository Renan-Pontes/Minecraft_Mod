package com.renanpontes.isekaimahou.feature.world_events.event;

import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Tempestade Arcana</b>: chove forte por alguns minutos e as magias de ÁGUA e VENTO (e as fusões delas) ficam mais
 * fortes ({@link #SPELL_BONUS}). Qualquer tempestade com trovões do jogo já dá um bônus pequeno
 * ({@link #NATURAL_THUNDER_BONUS}).
 * <p>
 * Cidades são sagradas: o evento liga a CHUVA, não os trovões do vanilla (raio de verdade põe fogo em casa de
 * madeira). Os raios que se veem são só visuais ({@code setVisualOnly}): não queimam, não ferem, não caem perto de
 * lugares registrados.
 */
public final class ArcaneStorm {
    public static final float SPELL_BONUS = 1.30F;
    public static final float NATURAL_THUNDER_BONUS = 1.10F;

    private ArcaneStorm() {}

    public static void start(ServerLevel level, WeState s) {
        s.extra.putBoolean("madeRain", !level.isRaining());
        rain(level, s);
    }

    private static void rain(ServerLevel level, WeState s) {
        int left = (int) Math.max(200, s.endsAt - level.getGameTime());
        // mantém os trovões como estavam: se já trovejava de verdade, não mexemos nisso
        level.setWeatherParameters(0, left, true, level.isThundering());
    }

    public static boolean tick(ServerLevel level, WeState s, int age) {
        if (age % 100 == 0 && !level.isRaining()) rain(level, s);
        if (age % 20 != 0) return true;
        RandomSource rnd = level.random;
        for (ServerPlayer p : level.players()) {
            // um relâmpago arcano (só luz e som) a cada ~10 s por jogador
            if (p.isSpectator() || rnd.nextInt(10) != 0) continue;
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 18 + rnd.nextDouble() * 34;
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (WeSites.nearPlace(level, pos, 16)) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        return true;
    }

    public static void end(ServerLevel level, WeState s) {
        // Só limpa o céu se a chuva foi nossa
        if (s.extra.getBoolean("madeRain")) level.setWeatherParameters(6000, 0, false, false);
    }
}
