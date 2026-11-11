package com.renanpontes.isekaimahou.feature.fusions_c;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Utilidades das Misturas C (dedupe por conjuração, alvos, chão, movimento). */
public final class FcUtil {
    /** Aumentos "vazios" (poder 1) para golpes de entidades cujo dano já vem multiplicado. */
    public static final SpellStats PLAIN = new SpellStats();

    /** (conjuração -> chave -> tick) para uma forma de área que acerta vários alvos não repetir o efeito de área. */
    private static final Map<SpellContext, Map<String, Long>> ONCE = new WeakHashMap<>();

    /** true só na 1ª chamada desta tick para (conjuração, chave). */
    public static boolean once(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Map<String, Long> m = ONCE.computeIfAbsent(ctx, k -> new HashMap<>());
        if (m.size() > 64) m.values().removeIf(t -> t != now);
        Long last = m.get(key);
        if (last != null && last == now) return false;
        m.put(key, now);
        return true;
    }

    /** Já houve um {@link #once} com esta chave nesta tick? (não marca nada) */
    public static boolean usedThisTick(SpellContext ctx, String key) {
        Map<String, Long> m = ONCE.get(ctx);
        if (m == null) return false;
        Long last = m.get(key);
        return last != null && last == ctx.level().getGameTime();
    }

    /**
     * A forma Si Mesmo chama o efeito no conjurador e depois no bloco sob os pés: este acerto de bloco é só esse "eco"
     * (e o efeito já agiu nesta tick com a chave {@code key})?
     */
    public static boolean isSelfFeetEcho(SpellContext ctx, BlockHitResult hit, String key) {
        LivingEntity c = ctx.caster();
        if (c == null) return false;
        boolean nearFeet = Vec3.atCenterOf(hit.getBlockPos()).distanceToSqr(c.position()) < 2.5 * 2.5;
        return nearFeet && usedThisTick(ctx, key);
    }

    /** Ponto em cima do bloco atingido (onde nasce uma zona/estrutura). */
    public static Vec3 onTop(BlockHitResult hit) {
        return Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
    }

    /** Chave por bloco: uma área por ponto, mesmo com vários alvos no mesmo lugar. */
    public static String posKey(String key, Vec3 p) {
        return key + "@" + BlockPos.containing(p).asLong();
    }

    /** (conjuração -> chave -> centros já usados nesta tick) para {@link #onceNear}. */
    private static final Map<SpellContext, Map<String, List<Vec3>>> NEAR = new WeakHashMap<>();
    private static final Map<SpellContext, Long> NEAR_TICK = new WeakHashMap<>();

    /**
     * true se nesta tick ainda não houve (conjuração, chave) a menos de {@code minDist} de {@code p} — e marca o ponto.
     * Formas de ÁREA chamam o efeito em CADA alvo da área: uma onda/erupção grande deve sair uma vez para o grupo, não uma
     * por alvo (10 ondas sobrepostas = dano ×10 e destruição repetida). Projéteis divididos que acertam longe ainda
     * criam a sua própria.
     */
    public static boolean onceNear(SpellContext ctx, String key, Vec3 p, double minDist) {
        long now = ctx.level().getGameTime();
        Long last = NEAR_TICK.get(ctx);
        Map<String, List<Vec3>> m = NEAR.computeIfAbsent(ctx, k -> new HashMap<>());
        if (last == null || last != now) {
            m.clear();
            NEAR_TICK.put(ctx, now);
        }
        List<Vec3> used = m.computeIfAbsent(key, k -> new ArrayList<>());
        double d2 = minDist * minDist;
        for (Vec3 q : used) if (q.distanceToSqr(p) < d2) return false;
        if (used.size() >= 16) return false;
        used.add(p);
        return true;
    }

    /** (entidade -> chave -> tick) para {@link #ready}. */
    private static final Map<Entity, Map<String, Long>> COOLDOWNS = new WeakHashMap<>();

    /**
     * true se a entidade não recebeu {@code key} nos últimos {@code ticks} ticks — e marca agora. Ex.: duas Explosões de
     * Vapor ao mesmo tempo não detonam o mesmo Molhado duas vezes.
     */
    public static boolean ready(Entity e, String key, int ticks) {
        long now = e.level().getGameTime();
        Map<String, Long> m = COOLDOWNS.computeIfAbsent(e, k -> new HashMap<>());
        Long last = m.get(key);
        if (last != null && now - last < ticks && now >= last) return false;
        m.put(key, now);
        return true;
    }

    static void clear() {
        ONCE.clear();
        NEAR.clear();
        NEAR_TICK.clear();
        COOLDOWNS.clear();
    }

    /** Quantas entidades vivas deste tipo pertencem ao conjurador (limites por conjurador). */
    public static <T extends com.renanpontes.isekaimahou.feature.fusions_c.entity.FcSpellEntity> int owned(
            net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.EntityType<T> type, @Nullable java.util.UUID owner) {
        if (owner == null) return 0;
        return level.getEntities(type, e -> e.isAlive() && owner.equals(e.ownerId())).size();
    }

    // =====================================================================================
    //  Poder e alvos
    // =====================================================================================

    /** Aumentos dos golpes das entidades: mantém Crítico/Vampírico com poder 1 (o dano guardado já tem o poder). */
    public static SpellStats hitStats(SpellStats stats) {
        if (!stats.crit && !stats.vampiric) return PLAIN;
        SpellStats s = new SpellStats();
        s.crit = stats.crit;
        s.vampiric = stats.vampiric;
        return s;
    }

    public static boolean isSelf(SpellContext ctx, Entity target) {
        return ctx.caster() != null && target == ctx.caster();
    }

    /** Os {@code max} inimigos mais próximos de um ponto. */
    public static List<LivingEntity> nearestEnemies(SpellContext ctx, Vec3 at, double radius, int max) {
        List<LivingEntity> list = new ArrayList<>(ctx.enemiesAround(at, radius));
        list.removeIf(FcUtil::untouchable);
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** Chefes resistem a controle forte (prender, carregar, adormecer). */
    public static boolean bossLike(Entity e) {
        return e instanceof LivingEntity le && Statuses.isBossLike(le);
    }

    public static boolean undead(Entity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    /** Jogador em criativo/espectador não recebe controle. */
    public static boolean untouchable(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    // =====================================================================================
    //  Mira e chão
    // =====================================================================================

    /** Direção horizontal (normalizada) de um vetor, ou +Z. */
    public static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** Ponto do chão abaixo (até {@code maxDown} blocos); se não achar, o próprio ponto. */
    public static Vec3 ground(Level level, Vec3 pos, int maxDown) {
        BlockPos p = BlockPos.containing(pos);
        // Se o ponto está dentro de um bloco sólido, sobe até achar ar (máx. 4)
        for (int i = 0; i < 4 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) p = p.above();
        for (int i = 0; i <= maxDown; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(pos.x, p.getY(), pos.z);
            }
            p = below;
        }
        return pos;
    }

    /** Onde o olhar a partir de {@code from} bate num bloco (ou o fim do alcance). */
    public static Vec3 aimPoint(Level level, Vec3 from, Vec3 dir, double range, @Nullable Entity ignore) {
        Vec3 to = from.add(dir.normalize().scale(range));
        CollisionContext cc = ignore != null ? CollisionContext.of(ignore) : CollisionContext.empty();
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cc));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
    }

    /** Centro de um efeito de área: no alvo, ou (Si Mesmo) no ponto mirado a até {@code range} blocos. */
    public static Vec3 areaCenter(SpellContext ctx, Entity target, double range) {
        if (isSelf(ctx, target)) {
            LivingEntity c = ctx.caster();
            return ground(ctx.level(), aimPoint(ctx.level(), ctx.eyePos(), ctx.look(), range, c), 8);
        }
        return ground(ctx.level(), target.position(), 8);
    }

    // =====================================================================================
    //  Movimento
    // =====================================================================================

    /** Muda a velocidade e avisa o cliente (jogadores controlam o próprio movimento). */
    public static void setMotion(Entity e, Vec3 motion) {
        e.setDeltaMovement(motion);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    public static void addMotion(Entity e, Vec3 add) {
        setMotion(e, e.getDeltaMovement().add(add));
    }

    /** Arremesso que respeita chefes (bem menos) e criativo (nada). */
    public static void knock(Entity e, Vec3 motion) {
        if (untouchable(e)) return;
        if (bossLike(e)) motion = motion.scale(0.2);
        addMotion(e, motion);
    }

    public static void stopMob(LivingEntity e) {
        if (e instanceof Mob m) m.getNavigation().stop();
    }


    private FcUtil() {}
}
