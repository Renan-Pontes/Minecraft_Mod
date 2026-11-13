package com.renanpontes.isekaimahou.feature.legends_e;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Utilitários de servidor das Magias de Nível Supremo: mira, chão, quem é inimigo, execução ("morre na hora"),
 * dano em % da vida que fura armadura e o controle de "uma por área" (a mesma magia acertando vários alvos ao mesmo
 * tempo cria UM relógio/pilar/vento por região, não um por alvo).
 */
public final class LeUtil {

    // =====================================================================================
    //  Mira e geometria
    // =====================================================================================

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, ClipContext.Fluid fluid, @Nullable Entity source) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid,
                source == null ? CollisionContext.empty() : CollisionContext.of(source)));
    }

    /** Desce de {@code p} até o primeiro bloco sólido ou superfície de líquido (até {@code depth} blocos). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p, double depth) {
        BlockHitResult r = clip(level, p, p.subtract(0, depth, 0), ClipContext.Fluid.ANY, null);
        return r.getType() == HitResult.Type.MISS ? p : r.getLocation();
    }

    /** Algum efeito da magia vale para este alvo? */
    public static boolean affects(SpellContext ctx, Entity target) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (ctx.isValidTarget(e.effect(), e.stats(), target)) return true;
        }
        return false;
    }

    /**
     * Ponto mirado no chão até {@code range}: âncora do estágio, alvo do mob, a criatura na mira ou o chão onde o
     * olhar bate (mirando o céu: o chão embaixo do fim do alcance).
     */
    public static Vec3 aimGround(SpellContext ctx, double range) {
        ServerLevel level = ctx.level();
        if (ctx.anchor() != null) return groundBelow(level, ctx.anchor().add(0, 0.5, 0), 24);
        LivingEntity c = ctx.caster();
        if (!ctx.hasOrigin() && c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget().position();
        }
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(ctx.look().scale(range));
        BlockHitResult bh = clip(level, eye, end, ClipContext.Fluid.ANY, c);
        Vec3 limit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        // Criatura na linha da mira
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities(c, new AABB(eye, limit).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && affects(ctx, e))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.4).clip(eye, limit);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestD) {
                bestD = hit.get().distanceToSqr(eye);
                best = e;
            }
        }
        if (best != null) return best.position();
        if (bh.getType() == HitResult.Type.MISS) return groundBelow(level, end, 96);
        if (bh.getDirection() == Direction.UP) return bh.getLocation();
        Vec3 out = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
        return groundBelow(level, out, 32);
    }

    // =====================================================================================
    //  Alvos
    // =====================================================================================

    /**
     * Inimigo de verdade para as magias de área enormes (não fere bichos pacíficos à toa): monstros, chefes, jogadores
     * não aliados (PvP) e qualquer criatura que esteja atacando você ou um aliado seu. Aldeões e NPCs só se atacarem.
     */
    public static boolean isFoe(SpellContext ctx, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
        LivingEntity c = ctx.caster();
        if (e == c || (ctx.casterId() != null && e.getUUID().equals(ctx.casterId())) || ctx.isAlly(e)) return false;
        if (e instanceof Player p) return !p.isCreative();
        boolean attacking = e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()
                && (m.getTarget() == c || ctx.isAlly(m.getTarget()));
        if (e instanceof Npc) return attacking;
        return attacking || e instanceof Enemy || Statuses.isBossLike(le);
    }

    /** Chefe (tag c:bosses, provação, vida ≥150) ou jogador: essas magias não matam na hora, tiram uma fração da vida. */
    public static boolean resistsExecution(LivingEntity e) {
        return e instanceof Player || Statuses.isBossLike(e);
    }

    /** Inimigos a até {@code radius} de {@code center} (na horizontal; de {@code below} abaixo a {@code above} acima), mais perto primeiro. */
    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 center, double radius, double below, double above, int max) {
        ServerLevel level = ctx.level();
        AABB box = new AABB(center.x - radius, center.y - below, center.z - radius, center.x + radius, center.y + above, center.z + radius);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(ctx, e))) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            if (dx * dx + dz * dz <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5)) list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================

    /** Poder efetivo da magia neste glifo (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /**
     * "Morre na hora": dano mágico enorme pelo caminho normal ({@code ctx.damage}) — conta o abate para quem conjurou,
     * respeita eventos de dano (totens e anulações continuam valendo).
     */
    public static boolean execute(SpellContext ctx, LivingEntity target, SpellStats stats, @Nullable Entity direct) {
        float need = target.getHealth() + target.getAbsorptionAmount() + 50F;
        float mult = Math.max(0.01F, power(ctx, stats));
        ctx.damage(target, Math.max(need, 1000F) * 20F / mult, stats, direct);
        return !target.isAlive() || target.isDeadOrDying();
    }

    /**
     * Tira {@code fraction} da vida MÁXIMA do alvo (furando a armadura), pelo caminho normal do dano de magia.
     * O poder da magia não muda a fração (é um efeito de "regra", não de força).
     */
    public static void percent(SpellContext ctx, LivingEntity target, float fraction, SpellStats stats, @Nullable Entity direct) {
        float wanted = target.getMaxHealth() * fraction;
        float raw = armorPierce(target, wanted);
        float mult = Math.max(0.01F, power(ctx, stats));
        ctx.damage(target, raw / mult, stats, direct);
    }

    /** Dano "bruto" para que, depois da armadura do alvo, sobrem {@code wanted} pontos. */
    public static float armorPierce(LivingEntity target, float wanted) {
        if (wanted <= 0) return wanted;
        float armor = target.getArmorValue();
        if (armor <= 0) return wanted;
        float tough = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float lo = wanted, hi = wanted * 6.0F;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5F;
            if (afterArmor(mid, armor, tough) < wanted) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    private static float afterArmor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float r = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F) / 25.0F;
        return damage * (1.0F - r);
    }

    /** Dano base que, depois dos multiplicadores da magia, vira {@code wanted} DEPOIS da armadura (fura armadura). */
    public static float piercingBase(SpellContext ctx, SpellStats stats, LivingEntity target, float wantedBase) {
        float mult = Math.max(0.01F, power(ctx, stats));
        return armorPierce(target, wantedBase * mult) / mult;
    }

    // =====================================================================================
    //  "Uma por área"
    // =====================================================================================

    private record Mark(String kind, ResourceKey<Level> dim, @Nullable UUID caster, Vec3 pos, double radius, long time) {}

    private static final List<Mark> MARKS = new ArrayList<>();

    /**
     * Reserva uma região para um efeito grande. Falso se o mesmo conjurador já criou um efeito deste tipo cobrindo
     * {@code pos} nos últimos {@code window} ticks (aí o efeito existente já cuida deste alvo).
     */
    public static boolean claim(String kind, ServerLevel level, @Nullable UUID caster, Vec3 pos, double radius, int window) {
        long now = level.getGameTime();
        MARKS.removeIf(m -> now - m.time > 600 || now < m.time);
        for (Mark m : MARKS) {
            if (m.kind.equals(kind) && m.dim == level.dimension() && Objects.equals(m.caster, caster) && now - m.time <= window
                    && m.pos.distanceToSqr(pos) <= m.radius * m.radius) return false;
        }
        if (MARKS.size() >= 256) MARKS.remove(0);
        MARKS.add(new Mark(kind, level.dimension(), caster, pos, radius, now));
        return true;
    }

    static void clear() {
        MARKS.clear();
    }

    /** Config de cliente "partículas reduzidas" (só chame no cliente; no servidor devolve false). */
    public static boolean reducedParticles() {
        return !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
    }

    private LeUtil() {}
}
