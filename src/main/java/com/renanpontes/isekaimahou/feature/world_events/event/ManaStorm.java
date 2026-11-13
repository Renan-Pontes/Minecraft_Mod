package com.renanpontes.isekaimahou.feature.world_events.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * <b>Tempestade de Mana</b>: por alguns minutos TODA magia (de jogadores e de monstros) fica mais forte
 * ({@link #SPELL_BONUS}), a mana volta um pouco mais rápido e Cristais de Mana caem do céu perto de quem está ao ar
 * livre — poucos: um a cada meio minuto por jogador, mais ou menos.
 * <p>
 * Os cristais só caem fora das zonas seguras e longe de lugares registrados (nada de lixo no chão da cidade).
 */
public final class ManaStorm {
    public static final float SPELL_BONUS = 1.20F;
    /** Mana devolvida por segundo, em fração da mana máxima. */
    private static final float REGEN_FRACTION = 0.015F;
    private static final int DROP_MIN = 20 * 25;
    private static final int DROP_SPREAD = 20 * 15;

    /** Próxima queda de cristal de cada jogador (gameTime). Some com o servidor: não precisa salvar. */
    private static final Map<UUID, Long> NEXT_DROP = new ConcurrentHashMap<>();

    private ManaStorm() {}

    public static void start(ServerLevel level, WeState s) {
        NEXT_DROP.clear();
    }

    public static void reset() {
        NEXT_DROP.clear();
    }

    public static boolean tick(ServerLevel level, WeState s, int age) {
        if (age % 20 != 0) return true;
        long now = level.getGameTime();
        RandomSource rnd = level.random;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            MagicData d = MagicApi.get(p);
            if (d.mana() < d.maxMana()) d.addMana(Math.max(1F, d.maxMana() * REGEN_FRACTION));

            Long next = NEXT_DROP.get(p.getUUID());
            if (next == null) {
                NEXT_DROP.put(p.getUUID(), now + 100 + rnd.nextInt(DROP_SPREAD));
                continue;
            }
            if (now < next) continue;
            NEXT_DROP.put(p.getUUID(), now + DROP_MIN + rnd.nextInt(DROP_SPREAD));
            dropCrystal(level, p, rnd);
        }
        return true;
    }

    private static void dropCrystal(ServerLevel level, ServerPlayer p, RandomSource rnd) {
        // só para quem está ao ar livre
        if (!level.canSeeSky(p.blockPosition().above())) return;
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = 4 + rnd.nextDouble() * 7;
        int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (Places.isSafe(level, ground) || WeSites.nearPlace(level, ground, 16)) return;
        if (!level.getFluidState(ground.below()).isEmpty()) return;

        ItemStack stack = rnd.nextInt(20) == 0
                ? new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get())
                : new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
        double top = ground.getY() + 22;
        ItemEntity item = new ItemEntity(level, x + 0.5, top, z + 0.5, stack, 0, -0.6, 0);
        item.setDefaultPickUpDelay();
        item.setGlowingTag(true);
        level.addFreshEntity(item);
        // risco de luz do céu até o chão + sininho: dá para ver onde caiu sem nada na tela
        for (int i = 0; i < 22; i += 2) {
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5, ground.getY() + i + 0.5, z + 0.5, 2, 0.05, 0.4, 0.05, 0.01);
        }
        level.sendParticles(ParticleTypes.ENCHANT, x + 0.5, ground.getY() + 1.0, z + 0.5, 20, 0.5, 0.5, 0.5, 0.5);
        level.playSound(null, ground, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.2F, 0.8F + rnd.nextFloat() * 0.5F);
    }
}
