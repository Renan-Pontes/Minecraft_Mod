package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueArmorItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Regras de combate do Ladino (servidor): "de costas", passo de sombra para trás do alvo, janela de Emboscada (o
 * próximo golpe depois de sumir conta como pelas costas) e as marcas de Ponto Vital. Estado estático pequeno e limpo
 * ({@link #clear} ao parar o servidor; marcas e janelas vencidas saem sozinhas em {@link #tick}).
 */
public final class RogueCombat {
    /** Multiplicador do golpe pelas costas da passiva (corpo a corpo e facas): "dano dobrado pelas costas". */
    public static final float PASSIVE_BACK = 2.0F;
    /** Multiplicador do glifo Apunhalar pelas costas. */
    public static final float BACKSTAB_MULT = 2.5F;
    /** Contra jogadores (PvP) e chefes os multiplicadores pelas costas são menores. */
    public static final float BACK_PVP = 1.5F;
    public static final float BACK_BOSS = 1.6F;
    /** Crítico do Ladino: chance base + por rank do Livro-Razão; dano ×1,5 (Ponto Vital: certo e ×2). */
    public static final float CRIT_BASE = 0.15F;
    public static final float CRIT_PER_RANK = 0.02F;
    public static final float CRIT_MULT = 1.5F;
    public static final float VITAL_MULT = 2.0F;
    private static final int MAX_MARKS = 128;

    /** Janela de Emboscada por jogador/entidade: até quando o próximo golpe conta como pelas costas. */
    private static final Map<UUID, Long> AMBUSH = new HashMap<>();
    /** Marcas de Ponto Vital: alvo → (dono, até quando, poder). */
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** Marcas gastas há pouco (o golpe que gastou pode ser o que matou): alvo → (dono, até quando). */
    private static final Map<UUID, Mark> SPENT = new HashMap<>();
    /** Conjuração em andamento que força "pelas costas" (Golpe do Ladrão Fantasma), por conjurador. */
    private static final Map<UUID, Integer> FORCED_BACK = new HashMap<>();

    public record Mark(UUID owner, long until, float power, ServerLevel level) {}

    // =====================================================================================
    //  Classe e rank
    // =====================================================================================
    public static boolean isRogue(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.ROGUE;
    }

    public static int rank(Player p) {
        return MagicApi.get(p).counter(RogueRanks.RANK_COUNTER);
    }

    /** Chance de crítico do Ladino (passiva + rank + traje completo). */
    public static float critChance(ServerPlayer p) {
        float c = CRIT_BASE + CRIT_PER_RANK * Math.min(5, rank(p));
        RogueGear set = RogueArmorItem.fullSet(p);
        if (set != null) c += set.critBonus;
        return Math.min(0.6F, c);
    }

    // =====================================================================================
    //  Costas
    // =====================================================================================

    /**
     * O alvo está "de costas" para {@code attacker}? Vale se: o atacante está invisível, em Emboscada ou numa conjuração
     * que força as costas; o alvo é um mob que NÃO está mirando o atacante (distraído, ou sem ver); ou o atacante está
     * atrás do alvo (cone de ~100° nas costas).
     */
    public static boolean exposedBack(LivingEntity target, LivingEntity attacker) {
        if (attacker.isInvisible() || ambushing(attacker) || FORCED_BACK.containsKey(attacker.getUUID())) return true;
        if (target instanceof Mob mob && !(Statuses.isBossLike(mob)) && mob.getTarget() != attacker) return true;
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1);
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot()).multiply(1, 0, 1);
        if (toAttacker.lengthSqr() < 1.0E-4 || facing.lengthSqr() < 1.0E-4) return false;
        return facing.normalize().dot(toAttacker.normalize()) < -0.2;
    }

    /** Multiplicador pelas costas ajustado para jogadores e chefes. */
    public static float backMultiplier(LivingEntity target, float base) {
        if (target instanceof Player) return Math.min(base, BACK_PVP);
        if (Statuses.isBossLike(target)) return Math.min(base, BACK_BOSS);
        return base;
    }

    public static void forceBack(LivingEntity caster, boolean on) {
        if (on) FORCED_BACK.merge(caster.getUUID(), 1, Integer::sum);
        else FORCED_BACK.computeIfPresent(caster.getUUID(), (k, v) -> v <= 1 ? null : v - 1);
    }

    // =====================================================================================
    //  Emboscada
    // =====================================================================================
    public static void startAmbush(LivingEntity e, int ticks) {
        AMBUSH.put(e.getUUID(), e.level().getGameTime() + ticks);
    }

    public static boolean ambushing(LivingEntity e) {
        Long until = AMBUSH.get(e.getUUID());
        return until != null && e.level().getGameTime() <= until;
    }

    /** Gasta a Emboscada (o golpe aconteceu). */
    public static void consumeAmbush(LivingEntity e) {
        AMBUSH.remove(e.getUUID());
    }

    // =====================================================================================
    //  Passo de sombra
    // =====================================================================================

    /**
     * Teleporta {@code caster} para as costas de {@code target} (ou para a frente dele se as costas estiverem
     * bloqueadas), virado para o alvo. Falha se não houver espaço, se estiver longe demais ou montado.
     */
    public static boolean teleportBehind(ServerLevel level, LivingEntity caster, LivingEntity target, double maxDist) {
        if (caster.level() != level || caster.isPassenger() || caster.distanceToSqr(target) > maxDist * maxDist) return false;
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot()).multiply(1, 0, 1);
        if (facing.lengthSqr() < 1.0E-4) facing = new Vec3(0, 0, 1);
        facing = facing.normalize();
        double off = target.getBbWidth() * 0.5 + caster.getBbWidth() * 0.5 + 0.45;
        Vec3[] tries = {target.position().subtract(facing.scale(off)),
                target.position().subtract(facing.yRot(0.6F).scale(off)),
                target.position().subtract(facing.yRot(-0.6F).scale(off)),
                target.position().add(facing.scale(off))};
        Vec3 dest = null;
        for (Vec3 t : tries) {
            Vec3 delta = t.subtract(caster.position());
            if (level.noCollision(caster, caster.getBoundingBox().move(delta))) {
                dest = t;
                break;
            }
            Vec3 up = t.add(0, 1, 0);
            if (level.noCollision(caster, caster.getBoundingBox().move(up.subtract(caster.position())))) {
                dest = up;
                break;
            }
        }
        if (dest == null) return false;
        Vec3 old = caster.position();
        RogueFx.vanish(level, old);
        RogueFx.line(level, old.add(0, 1, 0), dest.add(0, 1, 0), RogueFx.dust(RogueFx.SHADOW, 1.0F), 0.7);
        Vec3 look = target.position().subtract(dest);
        float yaw = (float) (Math.atan2(-look.x, look.z) * (180F / Math.PI));
        caster.teleportTo(dest.x, dest.y, dest.z);
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.setYBodyRot(yaw);
        if (caster instanceof ServerPlayer sp) sp.connection.teleport(dest.x, dest.y, dest.z, yaw, sp.getXRot());
        caster.fallDistance = 0;
        caster.resetFallDistance();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 18, 0.3, 0.6, 0.3, 0.05);
        RogueFx.sound(level, dest, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.7F);
        return true;
    }

    // =====================================================================================
    //  Ponto Vital
    // =====================================================================================
    public static void mark(LivingEntity target, LivingEntity owner, int ticks, float power) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (MARKS.size() >= MAX_MARKS && !MARKS.containsKey(target.getUUID())) purge(level.getGameTime(), true);
        MARKS.put(target.getUUID(), new Mark(owner.getUUID(), level.getGameTime() + ticks, power, level));
    }

    /** Marca de {@code owner} no alvo (válida), ou null. */
    @Nullable
    public static Mark markOf(LivingEntity target, LivingEntity owner) {
        Mark m = MARKS.get(target.getUUID());
        if (m == null || !m.owner().equals(owner.getUUID()) || target.level().getGameTime() > m.until()) return null;
        return m;
    }

    public static boolean isMarked(LivingEntity target) {
        Mark m = MARKS.get(target.getUUID());
        return m != null && target.level().getGameTime() <= m.until();
    }

    public static void consumeMark(LivingEntity target) {
        Mark m = MARKS.remove(target.getUUID());
        if (m != null) SPENT.put(target.getUUID(), new Mark(m.owner(), target.level().getGameTime() + 20, m.power(), m.level()));
    }

    /** A marca de {@code owner} acabou de ser gasta neste alvo (até 1 s atrás) ou ainda vale? (reembolso no abate) */
    public static boolean wasMarkedBy(LivingEntity target, LivingEntity owner) {
        if (markOf(target, owner) != null) return true;
        Mark m = SPENT.get(target.getUUID());
        return m != null && m.owner().equals(owner.getUUID()) && target.level().getGameTime() <= m.until();
    }

    public static void forgetSpent(LivingEntity target) {
        SPENT.remove(target.getUUID());
        MARKS.remove(target.getUUID());
    }

    /** A cada 10 ticks: brilho das marcas (máx. 32 por volta) e limpeza das vencidas. */
    static void tick(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 10 != 0) return;
        purge(now, false);
        int drawn = 0;
        for (Map.Entry<UUID, Mark> e : MARKS.entrySet()) {
            if (drawn++ >= 32) break;
            ServerLevel level = e.getValue().level();
            var ent = level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;
            Vec3 c = le.position().add(0, le.getBbHeight() + 0.45, 0);
            // mira vermelha girando acima do alvo
            double a = (now / 10.0) * 0.8;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Math.PI / 2;
                level.sendParticles(RogueFx.dust(RogueFx.BLOOD, 0.9F), c.x + Math.cos(ang) * 0.35, c.y, c.z + Math.sin(ang) * 0.35,
                        1, 0, 0, 0, 0);
            }
        }
        if (now % 200 == 0) AMBUSH.entrySet().removeIf(en -> en.getValue() < now - 200);
        if (!SPENT.isEmpty()) SPENT.entrySet().removeIf(en -> en.getValue().until() < en.getValue().level().getGameTime());
    }

    private static void purge(long now, boolean oldest) {
        Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Mark> e = it.next();
            if (e.getValue().until() < e.getValue().level().getGameTime() || oldest) {
                it.remove();
                if (oldest) return;
            }
        }
    }

    public static void forget(UUID id) {
        AMBUSH.remove(id);
        FORCED_BACK.remove(id);
    }

    public static void clear() {
        AMBUSH.clear();
        MARKS.clear();
        SPENT.clear();
        FORCED_BACK.clear();
    }

    private RogueCombat() {}
}
