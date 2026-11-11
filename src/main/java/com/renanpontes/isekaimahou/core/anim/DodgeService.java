package com.renanpontes.isekaimahou.core.anim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Esquiva no servidor: o cliente já moveu o jogador (o movimento do próprio jogador é do cliente) e tocou a animação;
 * aqui o servidor confere a recarga, cobra o cansaço, dá alguns ticks de invulnerabilidade e mostra a animação
 * para quem está vendo. Se uma esquiva "comer" um golpe, toca um tilintar (esquiva perfeita).
 */
public final class DodgeService {
    /** Próximo tick em que cada jogador pode esquivar de novo. */
    private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();
    /** Até que tick (inclusive) cada jogador está invulnerável pela esquiva. */
    private static final Map<UUID, Long> IFRAMES = new ConcurrentHashMap<>();
    /** Último tick em que tocou o som de "esquiva perfeita" (não repetir a cada golpe do mesmo tick). */
    private static final Map<UUID, Long> PERFECT = new ConcurrentHashMap<>();

    static void dodge(ServerPlayer player, byte dir) {
        if (!AnimConfig.DODGE_ENABLED.get()) return;
        if (player.isSpectator() || player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isDeadOrDying()) return;
        if (dir < MahouAnims.DIR_FORWARD || dir > MahouAnims.DIR_RIGHT) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long next = NEXT.get(player.getUUID());
        // 2 ticks de folga para o atraso da rede (o cliente usa a mesma recarga)
        if (next != null && now < next - 2) return;
        boolean creative = player.getAbilities().instabuild;
        if (!creative && player.getFoodData().getFoodLevel() <= 6) return;

        NEXT.put(player.getUUID(), now + AnimConfig.DODGE_COOLDOWN.get());
        int iframes = AnimConfig.DODGE_IFRAMES.get();
        if (iframes > 0) IFRAMES.put(player.getUUID(), now + iframes);
        if (!creative) player.causeFoodExhaustion(AnimConfig.DODGE_EXHAUSTION.get().floatValue());

        String anim = switch (dir) {
            case MahouAnims.DIR_BACK -> MahouAnims.DODGE_B;
            case MahouAnims.DIR_LEFT -> MahouAnims.DODGE_L;
            case MahouAnims.DIR_RIGHT -> MahouAnims.DODGE_R;
            default -> MahouAnims.DODGE_F;
        };
        MahouAnims.playOthers(player, anim);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.35F, 1.7F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.25, 0.02, 0.25, 0.01);
    }

    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long until = IFRAMES.get(player.getUUID());
        if (until == null) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now > until) {
            IFRAMES.remove(player.getUUID());
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        Long last = PERFECT.get(player.getUUID());
        if (last == null || last != now) {
            PERFECT.put(player.getUUID(), now);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    1.0F, 1.6F);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
        }
    }

    /** O jogador está nos ticks de invulnerabilidade da esquiva (para outros módulos, ex.: contra-ataque). */
    public static boolean isDodging(ServerPlayer player) {
        Long until = IFRAMES.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() <= until;
    }

    static void forget(UUID id) {
        NEXT.remove(id);
        IFRAMES.remove(id);
        PERFECT.remove(id);
    }

    static void clear() {
        NEXT.clear();
        IFRAMES.clear();
        PERFECT.clear();
    }

    private DodgeService() {}
}
