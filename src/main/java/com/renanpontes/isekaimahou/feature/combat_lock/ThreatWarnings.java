package com.renanpontes.isekaimahou.feature.combat_lock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.boss.BossKit;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aviso "!!!" (servidor decide, cliente desenha): um monstro COMUM que está caçando o jogador e
 * <ul>
 *   <li>chega à distância de golpe com um ataque forte (dano alto para a vida do jogador), ou um creeper acendendo;</li>
 *   <li>chega à distância de golpe pelas COSTAS do jogador, ou puxa o arco/besta pelas costas.</li>
 * </ul>
 * Chefes ficam de fora (têm os próprios avisos). Barato: a cada 4 ticks por jogador, uma busca pequena só pelos
 * monstros que têm o jogador como alvo; um aviso de cada vez e cada monstro só avisa de novo depois de alguns segundos.
 */
final class ThreatWarnings {
    private static final int EVERY = 4;
    private static final double SCAN = 12.0, SCAN_Y = 4.0;
    /** Ticks entre dois avisos para o mesmo jogador (um de cada vez, sem metralhar). */
    private static final int GAP = 14;
    /** Ticks até o MESMO monstro poder avisar de novo. */
    private static final int HEAVY_REARM = 70, BEHIND_REARM = 110;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private static final class State {
        long nextAny = NEVER;
        final Int2LongOpenHashMap next = new Int2LongOpenHashMap();

        State() {
            next.defaultReturnValue(NEVER);
        }
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        bus.addListener(PlayerTickEvent.Post.class, ThreatWarnings::onTick);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> STATES.remove(e.getEntity().getUUID()));
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> STATES.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> STATES.clear());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if ((now + p.getId()) % EVERY != 0) return;
        if (p.isSpectator() || p.isCreative() || p.isDeadOrDying() || !LockConfig.on(LockConfig.WARNINGS_ENABLED)) return;
        State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        if (now < st.nextAny) return;

        Vec3 look = Vec3.directionFromRotation(0F, p.getYHeadRot());
        AABB box = p.getBoundingBox().inflate(SCAN, SCAN_Y, SCAN);
        Mob best = null;
        byte bestKind = 0;
        double bestDist = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getTarget() == p)) {
            if (now < st.next.get(m.getId()) || BossKit.isBossLike(m)) continue;
            double dx = m.getX() - p.getX(), dz = m.getZ() - p.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            byte kind = kindOf(m, p, dist, dist > 0.01 && (dx * look.x + dz * look.z) / dist < -0.3);
            if (kind < 0 || dist >= bestDist) continue;
            best = m;
            bestKind = kind;
            bestDist = dist;
        }
        if (best == null) return;
        st.nextAny = now + GAP;
        st.next.put(best.getId(), now + (bestKind == LockPayloads.WARN_HEAVY ? HEAVY_REARM : BEHIND_REARM));
        if (st.next.size() > 48) st.next.int2LongEntrySet().removeIf(en -> en.getLongValue() < now);
        PacketDistributor.sendToPlayer(p, new LockPayloads.Warn(best.getId(), bestKind));
    }

    /** Tipo do aviso para este monstro agora, ou -1 se não é o caso. */
    private static byte kindOf(Mob m, ServerPlayer p, double dist, boolean behind) {
        if (m instanceof Creeper creeper) {
            return creeper.getSwellDir() > 0 && dist < 6.0 ? LockPayloads.WARN_HEAVY : -1;
        }
        if (m instanceof RangedAttackMob) {
            // Arco/besta sendo puxado pelas costas
            return behind && m.isUsingItem() ? LockPayloads.WARN_BEHIND : -1;
        }
        // Alcance do corpo a corpo + uma folga para o aviso chegar ANTES do golpe
        double reach = m.getBbWidth() * 0.5 + p.getBbWidth() * 0.5 + 3.0;
        if (dist > reach || Math.abs(m.getY() - p.getY()) > 3.0) return -1;
        if (heavy(m, p)) return LockPayloads.WARN_HEAVY;
        return behind ? LockPayloads.WARN_BEHIND : -1;
    }

    /** Golpe forte: 8+ de dano, ou 30% da vida máxima do jogador de uma vez. */
    private static boolean heavy(Mob m, ServerPlayer p) {
        AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return false;
        double dmg = attack.getValue();
        return dmg >= 8.0 || dmg >= p.getMaxHealth() * 0.3;
    }

    private ThreatWarnings() {}
}
