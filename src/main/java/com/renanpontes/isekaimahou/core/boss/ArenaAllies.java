package com.renanpontes.isekaimahou.core.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.social.SocialRules;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * v1.0 — "é isso, pokémon": invocações e aliados do jogador LUTANDO contam como luta na arena de chefe.
 * <p>
 * O {@code BossArenaService} (núcleo social) só enxergava jogadores: se o dono saía do raio, morria ou virava alma, a
 * arena era dada como abandonada (abria em 30 s) e o chefe, sem alvo, voltava a se curar/recomeçar a luta — mesmo com as
 * invocações ainda batendo nele. Sem mexer no serviço, uma vez por segundo e só nas arenas seladas:
 * <ul>
 *   <li>procura mobs de jogador (com dono online: invocações, familiares, companheiros, montarias) que estejam em luta
 *       com o chefe (um mira o outro, ou trocaram golpes há menos de 10 s);</li>
 *   <li>havendo algum, renova a luta ({@link BossArena#seal}: a arena não abre; se já estava liberando, sela de novo);</li>
 *   <li>e, se o chefe está sem alvo, ele passa a enfrentar a invocação mais próxima — com alvo, nenhum chefe "desiste"
 *       (as rotinas de cura/recomeço de cada chefe só rodam sem alvo).</li>
 * </ul>
 * Chefes de provação ficam de fora (a provação tem as próprias regras de derrota). Servidor.
 */
public final class ArenaAllies {
    private static final int SCAN_EVERY = 20;
    /** Golpe trocado há menos que isto ainda conta como luta (ticks da entidade). */
    private static final int RECENT_HIT = 200;
    /** {@link #fighting} continua true por este tempo depois da última varredura com aliados (ticks do servidor). */
    private static final int LINGER = 60;

    /** Último tick do servidor em que cada chefe tinha aliados de jogador em luta, e quantos. */
    private static final Map<UUID, int[]> LAST = new HashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ArenaAllies::onTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> LAST.clear());
    }

    /**
     * Invocações/aliados de jogador estão lutando com este chefe agora (visto há ≤ 3 s)? Chefes com rotina própria de
     * "ninguém por perto → curar/recomeçar" devem consultar isto antes.
     */
    public static boolean fighting(@Nullable Entity boss) {
        if (boss == null || boss.getServer() == null) return false;
        int[] last = LAST.get(boss.getUUID());
        return last != null && boss.getServer().getTickCount() - last[0] <= LINGER;
    }

    /** Quantos aliados de jogador lutavam com o chefe na última varredura (0 se nenhum há pouco). */
    public static int count(@Nullable Entity boss) {
        return fighting(boss) ? LAST.get(boss.getUUID())[1] : 0;
    }

    private static void onTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        int now = server.getTickCount();
        if (now % SCAN_EVERY != 3) return;
        List<BossArena.Info> arenas = BossArena.all();
        if (arenas.isEmpty()) {
            if (!LAST.isEmpty()) LAST.clear();
            return;
        }
        Set<UUID> alive = new HashSet<>();
        for (BossArena.Info a : arenas) {
            alive.add(a.boss());
            try {
                scan(server, a, now);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("[boss] varredura de aliados da arena falhou", ex);
            }
        }
        LAST.keySet().retainAll(alive);
    }

    private static void scan(MinecraftServer server, BossArena.Info a, int now) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, a.dimension()));
        if (level == null || !(level.getEntity(a.boss()) instanceof LivingEntity boss) || !boss.isAlive()) return;
        if (BossKit.isTrialBoss(boss)) return;
        List<Mob> allies = fightingAllies(server, level, boss, a);
        if (allies.isEmpty()) return;
        LAST.put(a.boss(), new int[]{now, allies.size()});
        // A luta continua: a arena não libera (e volta a selar se já estava liberando)
        BossArena.seal(boss);
        if (!(boss instanceof Mob mob)) return;
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && target.level() == level) return;
        // Sem alvo: enfrenta a invocação mais próxima (nunca tira o chefe de cima de um jogador)
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Mob m : allies) {
            double d = m.distanceToSqr(boss);
            if (d < best) {
                best = d;
                nearest = m;
            }
        }
        if (nearest != null) mob.setTarget(nearest);
    }

    /** Mobs de jogador dentro da arena (com folga) que estão em luta com o chefe. */
    private static List<Mob> fightingAllies(MinecraftServer server, ServerLevel level, LivingEntity boss, BossArena.Info a) {
        Vec3 c = a.center();
        double r = a.radius() + 8;
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r + 16, c.z + r);
        List<Mob> out = new ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m != boss && m.isAlive() && !boss.isAlliedTo(m))) {
            if (engaged(m, boss) && playerAlly(server, m)) out.add(m);
        }
        return out;
    }

    /** Um mira o outro, ou trocaram golpes há pouco. */
    private static boolean engaged(Mob m, LivingEntity boss) {
        if (m.getTarget() == boss) return true;
        if (boss instanceof Mob bm && bm.getTarget() == m) return true;
        if (boss.getLastHurtByMob() == m && boss.tickCount - boss.getLastHurtByMobTimestamp() < RECENT_HIT) return true;
        return m.getLastHurtByMob() == boss && m.tickCount - m.getLastHurtByMobTimestamp() < RECENT_HIT;
    }

    /** Mob de um jogador online (invocação, familiar, companheiro, montaria domada...). */
    private static boolean playerAlly(MinecraftServer server, Mob m) {
        if (m instanceof OwnableEntity own) {
            UUID owner = own.getOwnerUUID();
            if (owner != null && server.getPlayerList().getPlayer(owner) != null) return true;
        }
        Player behind = SocialRules.playerBehind(m);
        return behind != null;
    }

    private ArenaAllies() {}
}
