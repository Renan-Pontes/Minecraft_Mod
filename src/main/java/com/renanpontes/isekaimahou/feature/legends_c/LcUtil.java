package com.renanpontes.isekaimahou.feature.legends_c;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Utilidades das Técnicas Malditas: barreiras de domínio no caminho, chão, orçamento por tick, Colossal. */
public final class LcUtil {
    /** Aumento Colossal (legends_a), lido só pelo id. */
    public static final ResourceLocation COLOSSAL = IsekaiMahou.id("colossal");

    /** Uma parede de domínio fechado no caminho de um golpe: o domínio, o ponto na parede e a fração do segmento. */
    public record Wall(Domains.Info domain, Vec3 point, double t) {}

    /**
     * A primeira parede de domínio FECHADO (de outro dono, não aliado) que o segmento {@code from → to} atravessa.
     * Regra §11.1: a barreira separa os lados — o golpe para nela (e a ataca) em vez de passar. Null = caminho livre.
     */
    @Nullable
    public static Wall firstWall(ServerLevel level, Vec3 from, Vec3 to, @Nullable LivingEntity caster) {
        Wall best = null;
        Vec3 d = to.subtract(from);
        double a = d.lengthSqr();
        if (a < 1.0E-6) return null;
        for (Domains.Info info : Domains.active(level)) {
            if (!info.closed() || friendly(info, caster)) continue;
            Vec3 f = from.subtract(info.center());
            double r = info.radius();
            double b = 2 * f.dot(d);
            double c = f.lengthSqr() - r * r;
            double disc = b * b - 4 * a * c;
            if (disc < 0) continue;
            double sq = Math.sqrt(disc);
            double t1 = (-b - sq) / (2 * a), t2 = (-b + sq) / (2 * a);
            // De dentro: a parede é a saída (t2); de fora: a entrada (t1)
            double t = c < 0 ? t2 : t1;
            if (t < 0 || t > 1) continue;
            if (best == null || t < best.t) best = new Wall(info, from.add(d.scale(t)), t);
        }
        return best;
    }

    /**
     * O alvo está do OUTRO lado de uma barreira fechada (de outro dono) em relação ao ponto de origem do golpe — ex.:
     * protegido dentro de um domínio menor enquanto a explosão/sucção acontece do lado de fora. Golpes em área usam
     * isto para não atravessar a barreira (regra §11.1: ela separa os lados).
     */
    public static boolean behindWall(ServerLevel level, Vec3 origin, Entity target, @Nullable LivingEntity caster) {
        return firstWall(level, origin, center(target), caster) != null;
    }

    /** O domínio é do conjurador ou de um aliado dele (a própria barreira nunca bloqueia nem é atacada). */
    public static boolean friendly(Domains.Info info, @Nullable LivingEntity caster) {
        if (caster == null) return false;
        LivingEntity owner = info.owner();
        return owner == caster || owner.getUUID().equals(caster.getUUID()) || caster.isAlliedTo(owner);
    }

    /**
     * O golpe bate numa parede: tira integridade pela regra do núcleo (normal só quebra de fora; invertida só de dentro)
     * e mostra um corte/faísca no ponto. Devolve o que foi tirado.
     */
    public static float strikeWall(ServerLevel level, Wall wall, Vec3 attackerPos, @Nullable Entity attacker, float amount) {
        return Domains.hitBarrier(level, wall.point(), attackerPos, attacker, amount);
    }

    /** Chão (topo do primeiro bloco sólido) abaixo de {@code p}, até {@code maxDown} blocos; senão o próprio ponto. */
    public static Vec3 ground(ServerLevel level, Vec3 p, int maxDown) {
        BlockPos.MutableBlockPos m = BlockPos.containing(p).mutable();
        for (int i = 0; i <= maxDown; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && !s.getCollisionShape(level, m).isEmpty()) return new Vec3(p.x, m.getY() + 1.0, p.z);
            m.move(0, -1, 0);
            if (m.getY() < level.getMinBuildHeight()) break;
        }
        return p;
    }

    /** Há chão sólido a até {@code maxDown} blocos abaixo? */
    public static boolean hasGround(ServerLevel level, Vec3 p, int maxDown) {
        BlockPos.MutableBlockPos m = BlockPos.containing(p).mutable();
        for (int i = 0; i <= maxDown; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && !s.getCollisionShape(level, m).isEmpty()) return true;
            m.move(0, -1, 0);
        }
        return false;
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static boolean isBoss(LivingEntity e) {
        return BossKit.isBossLike(e);
    }

    /** Colossal (legends_a) no glifo? */
    public static boolean colossal(SpellStats stats) {
        return stats.custom(COLOSSAL) > 0;
    }

    /** Direção horizontal normalizada (ou +Z se vertical). */
    public static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo) — as técnicas então saem na direção do olhar. */
    public static boolean selfTarget(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    /**
     * O "bloco" atingido é o chão sob os pés do conjurador (a forma Si Mesmo também aplica os efeitos no bloco embaixo
     * dele): as técnicas ignoram, senão o Azul/Vermelho/Desmantelar sairiam no próprio pé.
     */
    public static boolean selfBlock(SpellContext ctx, net.minecraft.world.phys.BlockHitResult hit) {
        LivingEntity c = ctx.caster();
        return c != null && hit.getLocation().distanceToSqr(c.position()) < 2.25;
    }

    // ------------------------------------------------------------------ orçamento por tick

    private record Budget(long tick, int used) {}

    private static final Map<String, Budget> BUDGET = new HashMap<>();

    /**
     * Gasta 1 do orçamento {@code key} do conjurador neste tick (máx. {@code max}). Domínios aplicam os efeitos em
     * dezenas de inimigos no mesmo tick: isto segura o número de cortes/entidades/blocos por tick.
     */
    public static boolean budget(ServerLevel level, @Nullable Entity caster, String key, int max) {
        String k = (caster == null ? "?" : caster.getStringUUID()) + "|" + key;
        long now = level.getGameTime();
        Budget b = BUDGET.get(k);
        if (b == null || b.tick != now) b = new Budget(now, 0);
        if (b.used >= max) return false;
        BUDGET.put(k, new Budget(now, b.used + 1));
        if (BUDGET.size() > 512) BUDGET.values().removeIf(x -> x.tick < now);
        return true;
    }

    public static void clear() {
        BUDGET.clear();
    }

    private LcUtil() {}
}
