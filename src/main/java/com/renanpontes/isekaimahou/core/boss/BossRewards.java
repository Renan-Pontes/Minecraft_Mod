package com.renanpontes.isekaimahou.core.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Recompensa de grupo dos chefes: XP mágico + contador {@code boss.<id>} para TODOS que lutaram — quem está no
 * raio (inclusive espectadores, ex.: jogadores "Abatidos" na provação) e quem causou dano nos últimos 30 s.
 */
public final class BossRewards {
    /** Janela (ticks) em que dano causado ainda conta como participação. */
    public static final int ASSIST_WINDOW = 600;
    /** persistentData: o chefe já pagou a recompensa (evita pagar duas vezes). */
    public static final String REWARDED_KEY = "isekai_mahou.boss_rewarded";
    /** Só rastreia dano em alvos com pelo menos esta vida máx. (ou chefes por tag), para não pesar no servidor. */
    private static final float TRACK_MIN_HEALTH = 60F;

    /** Por chefe: jogador → gameTime do último dano. */
    private static final Map<LivingEntity, Map<UUID, Long>> DAMAGERS = new WeakHashMap<>();

    /**
     * Dá {@code xp} de XP mágico ({@link MagicApi#giveXp}, fonte "boss") e +1 no contador {@code boss.<path do id>}
     * uma vez para cada jogador participante. Só paga UMA vez por chefe (chamadas repetidas devolvem lista vazia).
     * Use no {@code die()} do chefe NO LUGAR do contador manual. Devolve os jogadores recompensados.
     */
    public static List<ServerPlayer> grant(LivingEntity boss, int xp, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(boss.level() instanceof ServerLevel level)) return out;
        if (boss.getPersistentData().getBoolean(REWARDED_KEY)) return out;
        boss.getPersistentData().putBoolean(REWARDED_KEY, true);

        Map<UUID, ServerPlayer> players = new LinkedHashMap<>();
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(boss) <= r2) players.put(p.getUUID(), p);
        }
        Map<UUID, Long> hits = DAMAGERS.remove(boss);
        if (hits != null) {
            long now = level.getGameTime();
            hits.forEach((id, when) -> {
                if (now - when > ASSIST_WINDOW || players.containsKey(id)) return;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) players.put(id, p);
            });
        }

        String key = "boss." + BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath();
        for (ServerPlayer p : players.values()) {
            if (xp > 0) MagicApi.giveXp(p, xp, "boss");
            MagicApi.addCounter(p, key, 1);
            if (xp > 0) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.boss_kit.reward", boss.getDisplayName(), xp)
                        .withStyle(ChatFormatting.GOLD), true);
            }
            out.add(p);
        }
        return out;
    }

    /** Já pagou a recompensa deste chefe? */
    public static boolean wasRewarded(LivingEntity boss) {
        return boss.getPersistentData().getBoolean(REWARDED_KEY);
    }

    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getNewDamage() <= 0) return;
        if (!isTracked(victim)) return;
        ServerPlayer attacker = playerOf(event.getSource().getEntity());
        if (attacker == null) return;
        DAMAGERS.computeIfAbsent(victim, v -> new HashMap<>()).put(attacker.getUUID(), victim.level().getGameTime());
    }

    private static boolean isTracked(LivingEntity e) {
        return e.getMaxHealth() >= TRACK_MIN_HEALTH || BossKit.isTrialBoss(e) || e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Jogador responsável: o próprio, ou o dono de uma invocação/pet. Projéteis já chegam como o atirador. */
    private static ServerPlayer playerOf(Entity e) {
        if (e instanceof ServerPlayer sp) return sp;
        if (e instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    private BossRewards() {}
}
