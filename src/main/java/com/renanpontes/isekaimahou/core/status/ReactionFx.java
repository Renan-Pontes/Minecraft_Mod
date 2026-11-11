package com.renanpontes.isekaimahou.core.status;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementReactions.Reaction;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Partículas, som e texto flutuante de cada reação elemental (servidor → jogadores a ≤32 blocos). */
public final class ReactionFx {
    public static final double TEXT_RANGE = 32.0;
    /**
     * A MESMA reação no MESMO alvo é anunciada (efeitos visuais, texto, evento, contadores) no máx. 1 vez a cada 10
     * ticks — feixes que acertam a cada 2–4 ticks em reações que não consomem a condição (Incêndio, Supercondutor,
     * Lacerar) não viram spam nem "fazenda" de contador. A mecânica da reação continua valendo em todo golpe.
     */
    public static final int REPEAT_TICKS = 10;
    private static final Map<LivingEntity, long[]> LAST = new WeakHashMap<>();

    /** true se a reação pode ser anunciada agora neste alvo (e marca o momento). Só no servidor. */
    public static boolean claim(LivingEntity t, Reaction r) {
        long now = t.level().getGameTime();
        long[] last = LAST.computeIfAbsent(t, k -> {
            long[] a = new long[Reaction.values().length];
            Arrays.fill(a, Long.MIN_VALUE);
            return a;
        });
        int i = r.ordinal();
        if (last[i] != Long.MIN_VALUE && now >= last[i] && now - last[i] < REPEAT_TICKS) return false;
        last[i] = now;
        return true;
    }

    static void clear() {
        LAST.clear();
    }

    public static void play(ServerLevel level, LivingEntity t, Reaction r) {
        double x = t.getX(), y = t.getY() + t.getBbHeight() * 0.55, z = t.getZ();
        double w = Math.max(0.3, t.getBbWidth() * 0.5), h = Math.max(0.3, t.getBbHeight() * 0.35);
        switch (r) {
            case ELECTROCUTE -> {
                burst(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, w, h, 0.4);
                burst(level, ParticleTypes.FALLING_WATER, x, y, z, 12, w, h, 0);
                sound(level, t, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.5F);
                sound(level, t, SoundEvents.BEE_STING, 0.6F, 0.6F);
            }
            case FREEZE -> {
                burst(level, ParticleTypes.SNOWFLAKE, x, y, z, 36, w, h, 0.05);
                burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), x, y, z, 20, w, h, 0.1);
                sound(level, t, SoundEvents.GLASS_PLACE, 1.0F, 0.5F);
                sound(level, t, SoundEvents.POWDER_SNOW_PLACE, 1.0F, 0.7F);
            }
            case VAPOR -> {
                burst(level, ParticleTypes.CLOUD, x, y, z, 40, 1.4, 0.8, 0.04);
                burst(level, ParticleTypes.POOF, x, y, z, 12, w, h, 0.05);
                sound(level, t, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
            }
            case WILDFIRE -> {
                burst(level, ParticleTypes.FLAME, x, y, z, 40, 1.6, 0.6, 0.08);
                burst(level, ParticleTypes.LARGE_SMOKE, x, y, z, 8, w, h, 0.02);
                sound(level, t, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
            }
            case MELT -> {
                burst(level, ParticleTypes.FALLING_WATER, x, y, z, 24, w, h, 0);
                burst(level, ParticleTypes.CLOUD, x, y, z, 16, w, h, 0.03);
                burst(level, dust(0xFFB060, 1.4F), x, y, z, 16, w, h, 0);
                sound(level, t, SoundEvents.LAVA_EXTINGUISH, 0.9F, 1.2F);
            }
            case OVERLOAD -> {
                burst(level, ParticleTypes.FLAME, x, y, z, 30, 1.2, 0.8, 0.15);
                burst(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 1.2, 0.8, 0.3);
                burst(level, dust(0xFF5FA0, 1.8F), x, y, z, 20, 1.0, 0.6, 0);
                sound(level, t, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.4F);
            }
            case SUPERCONDUCT -> {
                burst(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, 24, w, h, 0.2);
                burst(level, ParticleTypes.SNOWFLAKE, x, y, z, 16, w, h, 0.03);
                burst(level, dust(0xB4A8FF, 1.4F), x, y, z, 16, w, h, 0);
                sound(level, t, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
            }
            case SHATTER -> {
                burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), x, y, z, 40, w, h, 0.25);
                burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), x, y, z, 16, w, h, 0.2);
                sound(level, t, SoundEvents.GLASS_BREAK, 1.2F, 0.7F);
                sound(level, t, SoundEvents.ANVIL_LAND, 0.4F, 1.6F);
            }
            case LACERATE -> {
                burst(level, dust(0xB0101C, 1.3F), x, y, z, 30, w, h, 0);
                burst(level, ParticleTypes.SWEEP_ATTACK, x, y, z, 3, w * 0.5, h * 0.5, 0);
                sound(level, t, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
            }
            case RUPTURE -> {
                burst(level, ParticleTypes.END_ROD, x, y, z, 18, w, h, 0.08);
                burst(level, ParticleTypes.SQUID_INK, x, y, z, 18, w, h, 0.05);
                sound(level, t, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5F, 1.6F);
            }
            case SAND_EYES -> {
                burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), x, y, z, 30, w, h, 0.15);
                burst(level, ParticleTypes.WHITE_ASH, x, y, z, 24, w, h, 0.02);
                sound(level, t, SoundEvents.SAND_BREAK, 1.0F, 0.8F);
            }
        }
        PacketDistributor.sendToPlayersNear(level, null, t.getX(), t.getY(), t.getZ(), TEXT_RANGE,
                new StatusPayloads.ReactionText(t.getId(), r.ordinal(), x, t.getY() + t.getBbHeight(), z));
    }

    private static void burst(ServerLevel level, ParticleOptions p, double x, double y, double z, int n, double dxz, double dy, double speed) {
        level.sendParticles(p, x, y, z, n, dxz, dy, dxz, speed);
    }

    private static void sound(ServerLevel level, LivingEntity t, SoundEvent s, float vol, float pitch) {
        level.playSound(null, t.getX(), t.getY(), t.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private ReactionFx() {}
}
