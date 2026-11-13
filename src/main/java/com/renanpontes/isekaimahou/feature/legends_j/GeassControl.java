package com.renanpontes.isekaimahou.feature.legends_j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.GeassEffect;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Criaturas sob o Geass "Obedeça!": por alguns segundos lutam pelo dono — miram o inimigo (monstro) mais próximo e
 * NUNCA miram nem ferem o dono, o grupo dele ou os outros "obedientes" do mesmo dono. Só atacam monstros (nunca
 * jogadores) e ficam quietas com o dono em Modo Paz ou dentro de cidades. Estado só na memória do servidor.
 */
public final class GeassControl {
    private record Order(Mob mob, LivingEntity owner, long until) {}

    private static final Map<UUID, Order> ORDERS = new HashMap<>();
    /** Teto de obedientes ao mesmo tempo no servidor (não deixa virar um exército). */
    private static final int MAX = 64;

    public static void obey(Mob mob, LivingEntity owner, int ticks) {
        if (ORDERS.size() >= MAX && !ORDERS.containsKey(mob.getUUID())) return;
        ORDERS.put(mob.getUUID(), new Order(mob, owner, mob.level().getGameTime() + Math.max(20, ticks)));
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    public static boolean isObeying(Entity e) {
        return ORDERS.containsKey(e.getUUID());
    }

    /** {@code other} é do lado do dono (o próprio dono, alguém do grupo dele ou outro obediente dele)? */
    static boolean friendly(LivingEntity owner, @Nullable Entity other) {
        if (other == null) return false;
        if (other == owner) return true;
        if (owner instanceof Player op && other instanceof Player p && Party.sameParty(op, p)) return true;
        Order o = ORDERS.get(other.getUUID());
        return o != null && o.owner() == owner;
    }

    public static void tick(MinecraftServer server) {
        if (ORDERS.isEmpty() || server.getTickCount() % 10 != 0) return;
        for (Iterator<Order> it = ORDERS.values().iterator(); it.hasNext(); ) {
            Order o = it.next();
            Mob m = o.mob();
            if (m.isRemoved() || !m.isAlive() || !(m.level() instanceof ServerLevel l) || l.getGameTime() > o.until()
                    || o.owner().isRemoved() || o.owner().level() != l) {
                if (m.isAlive()) {
                    m.setTarget(null);
                    if (m.level() instanceof ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, m.getX(), m.getEyeY(), m.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                    }
                }
                it.remove();
                continue;
            }
            if (LjUtil.calm(o.owner())) {
                if (m.getTarget() != null) m.setTarget(null);
                continue;
            }
            LivingEntity cur = m.getTarget();
            if (cur == null || !cur.isAlive() || friendly(o.owner(), cur) || cur instanceof Player) {
                m.setTarget(nearestFoe(m, o.owner()));
            }
            // Brilho vermelho nos olhos de quem obedece
            ElementFx.sendFar(l, ElementFx.glow(GeassEffect.COLOR, 0.4F), m.getX(), m.getEyeY() + 0.1, m.getZ(), 2, 0.12, 0.05, 0.12, 0);
        }
    }

    @Nullable
    private static LivingEntity nearestFoe(Mob m, LivingEntity owner) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, new AABB(m.blockPosition()).inflate(16),
                e -> e != m && e.isAlive() && !(e instanceof Player) && (e instanceof Enemy || Statuses.isBossLike(e))
                        && !friendly(owner, e))) {
            double d = e.distanceToSqr(m);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** O obediente não escolhe o dono (nem o grupo dele) como alvo. */
    static void onChangeTarget(LivingChangeTargetEvent e) {
        Order o = ORDERS.get(e.getEntity().getUUID());
        if (o == null) return;
        LivingEntity t = e.getNewAboutToBeSetTarget();
        if (t != null && (friendly(o.owner(), t) || t instanceof Player)) e.setCanceled(true);
    }

    /** ... nem fere (explosão de creeper, flecha perdida). */
    static void onIncoming(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (src == null) return;
        Order o = ORDERS.get(src.getUUID());
        if (o != null && (friendly(o.owner(), e.getEntity()) || e.getEntity() instanceof Player)) e.setCanceled(true);
    }

    public static void clear() {
        ORDERS.clear();
    }

    private GeassControl() {}
}
