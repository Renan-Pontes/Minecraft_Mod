package com.renanpontes.isekaimahou.core.status;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementReactions.Reaction;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Som e texto flutuante de cada reação elemental (servidor → jogadores a ≤32 blocos).
 * <p>
 * Release 1.0 (perf): as rajadas de partículas do vanilla (2–3 pacotes e ~100 partículas por reação, que não respeitavam
 * o Preset Gráfico) saíram daqui. O visual inteiro da reação é a explosão montada no cliente
 * ({@code ElementFx.reaction} → {@code FxBurstClient.reaction}), que ganhou as lascas de gelo/pedra/areia que ficavam aqui.
 */
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
        double x = t.getX(), z = t.getZ();
        switch (r) {
            case ELECTROCUTE -> {
                sound(level, t, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.5F);
                sound(level, t, SoundEvents.BEE_STING, 0.6F, 0.6F);
            }
            case FREEZE -> {
                sound(level, t, SoundEvents.GLASS_PLACE, 1.0F, 0.5F);
                sound(level, t, SoundEvents.POWDER_SNOW_PLACE, 1.0F, 0.7F);
            }
            case VAPOR -> {
                sound(level, t, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
            }
            case WILDFIRE -> {
                sound(level, t, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
            }
            case MELT -> {
                sound(level, t, SoundEvents.LAVA_EXTINGUISH, 0.9F, 1.2F);
            }
            case OVERLOAD -> {
                sound(level, t, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.4F);
            }
            case SUPERCONDUCT -> {
                sound(level, t, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
            }
            case SHATTER -> {
                sound(level, t, SoundEvents.GLASS_BREAK, 1.2F, 0.7F);
                sound(level, t, SoundEvents.ANVIL_LAND, 0.4F, 1.6F);
            }
            case LACERATE -> {
                sound(level, t, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
            }
            case RUPTURE -> {
                sound(level, t, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5F, 1.6F);
            }
            case SAND_EYES -> {
                sound(level, t, SoundEvents.SAND_BREAK, 1.0F, 0.8F);
            }
        }
        PacketDistributor.sendToPlayersNear(level, null, t.getX(), t.getY(), t.getZ(), TEXT_RANGE,
                new StatusPayloads.ReactionText(t.getId(), r.ordinal(), x, t.getY() + t.getBbHeight(), z));
    }

    private static void sound(ServerLevel level, LivingEntity t, SoundEvent s, float vol, float pitch) {
        level.playSound(null, t.getX(), t.getY(), t.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private ReactionFx() {}
}
