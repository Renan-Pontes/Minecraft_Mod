package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Entity;

/**
 * Marca do Caçador: o alvo brilha através das paredes e leva MAIS dano do caçador que marcou e dos companheiros
 * dele. Uma marca por alvo (a mais recente vence). Estado só em memória (marcas duram segundos), limpo ao parar o
 * servidor; a mira gira sobre a cabeça a cada 5 ticks, com teto de marcas desenhadas.
 */
public final class RangerMarks {
    /** Marca: quem marcou, até quando, cor, bônus de dano (0.25 = +25%) e o alvo. */
    public record Mark(UUID owner, long until, int rgb, float bonus, LivingEntity target) {}

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static final int MAX_MARKS = 256;
    private static final int MAX_DRAWN = 40;

    /** Marca {@code target} por {@code ticks}. Uma marca mais forte não é trocada por uma mais fraca do mesmo dono. */
    public static void mark(LivingEntity target, LivingEntity owner, int ticks, int rgb, float bonus) {
        if (!(target.level() instanceof ServerLevel sl) || target == owner) return;
        long now = sl.getGameTime();
        Mark old = MARKS.get(target.getUUID());
        if (old != null && old.owner().equals(owner.getUUID()) && old.until() > now && old.bonus() > bonus) {
            bonus = old.bonus();
            ticks = Math.max(ticks, (int) (old.until() - now));
        }
        if (old == null && MARKS.size() >= MAX_MARKS) return;
        MARKS.put(target.getUUID(), new Mark(owner.getUUID(), now + ticks, rgb, bonus, target));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), owner);
    }

    @Nullable
    public static Mark markOf(LivingEntity target) {
        Mark m = MARKS.get(target.getUUID());
        if (m == null) return null;
        if (m.until() < target.level().getGameTime() || !target.isAlive()) return null;
        return m;
    }

    public static boolean isMarkedBy(LivingEntity target, UUID owner) {
        Mark m = markOf(target);
        return m != null && m.owner().equals(owner);
    }

    /**
     * Bônus de dano quando {@code attacker} acerta {@code target}: vale para quem marcou e para os bichos/companheiros
     * dele. 0 se não se aplica.
     */
    public static float bonusFor(LivingEntity target, @Nullable Entity attacker) {
        if (attacker == null) return 0F;
        Mark m = markOf(target);
        if (m == null) return 0F;
        if (attacker.getUUID().equals(m.owner())) return m.bonus();
        if (attacker instanceof OwnableEntity own && m.owner().equals(own.getOwnerUUID())) return m.bonus();
        return 0F;
    }

    /** Inimigo marcado pelo {@code owner} mais próximo dele (até {@code range}), ou null. */
    @Nullable
    public static LivingEntity nearestMarked(LivingEntity owner, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        long now = owner.level().getGameTime();
        for (Mark m : MARKS.values()) {
            if (!m.owner().equals(owner.getUUID()) || m.until() < now) continue;
            LivingEntity t = m.target();
            if (!t.isAlive() || t.level() != owner.level()) continue;
            double d = t.distanceToSqr(owner);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    /** Quem marcou o alvo que acabou de morrer (para o contador de abates marcados). */
    @Nullable
    public static UUID ownerOf(LivingEntity target) {
        Mark m = MARKS.get(target.getUUID());
        return m == null ? null : m.owner();
    }

    /** Tira as marcas de um alvo (morte). */
    public static void clear(LivingEntity target) {
        MARKS.remove(target.getUUID());
    }

    /** A cada 5 ticks: remove marcas vencidas e desenha a mira (no máx. {@link #MAX_DRAWN}). */
    public static void tick(MinecraftServer server) {
        if (MARKS.isEmpty() || server.getTickCount() % 5 != 0) return;
        int drawn = 0;
        Iterator<Mark> it = MARKS.values().iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            LivingEntity t = m.target();
            if (t.isRemoved() || !t.isAlive() || !(t.level() instanceof ServerLevel sl) || m.until() < sl.getGameTime()) {
                it.remove();
                continue;
            }
            if (drawn < MAX_DRAWN && !sl.players().isEmpty()
                    && sl.getNearestPlayer(t.getX(), t.getY(), t.getZ(), 48, false) != null) {
                RangerFx.crosshair(sl, t, m.rgb(), sl.getGameTime());
                drawn++;
            }
        }
    }

    /** Limpa tudo (servidor parando). */
    public static void reset() {
        MARKS.clear();
    }

    private RangerMarks() {}
}
