package com.renanpontes.isekaimahou.feature.forms_a;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mira e geometria compartilhadas pelas formas do módulo (raycasts, cones, chão). Só servidor. */
public final class FormAim {

    /** Entidade atingida por um raio, com o ponto e a distância do começo do raio. */
    public record Hit(Entity entity, Vec3 pos, double dist) {}

    /** Ponto mirado no mundo (Queda, Chuva) e a entidade, se a mira caiu numa. */
    public record Point(Vec3 pos, Entity entity) {}

    /** Base ortonormal (frente, direita, cima) para abrir leques/arcos em volta de uma direção. */
    public record Frame(Vec3 forward, Vec3 right, Vec3 up) {
        public static Frame of(Vec3 dir) {
            Vec3 f = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            r = r.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : r.normalize();
            return new Frame(f, r, r.cross(f).normalize());
        }

        /** Direção girada {@code yaw} (para a direita) e {@code pitch} (para cima), em radianos. */
        public Vec3 dir(double yaw, double pitch) {
            Vec3 h = forward.scale(Math.cos(yaw)).add(right.scale(Math.sin(yaw)));
            return h.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch))).normalize();
        }
    }

    /**
     * Direção de mira: o olhar do conjurador; se for um mob com alvo (cultistas, chefes conjurando),
     * mira direto no centro do alvo para a magia não sair torta. Num estágio de magia programável (ou castFrom),
     * a direção do contexto manda: o mob não "corrige" a mira para o alvo dele.
     */
    public static Vec3 aimDir(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        if (!ctx.hasOrigin() && c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            LivingEntity t = mob.getTarget();
            Vec3 d = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(c.getEyePosition());
            if (d.lengthSqr() > 1.0E-4) return d.normalize();
        }
        return ctx.look();
    }

    /** Direção horizontal da mira (ondas). Olhando reto para baixo/cima usa o giro do corpo. */
    public static Vec3 horizontalAim(SpellContext ctx) {
        Vec3 d = aimDir(ctx);
        Vec3 h = new Vec3(d.x, 0, d.z);
        if (h.lengthSqr() < 0.01 && ctx.caster() != null) h = Vec3.directionFromRotation(0, ctx.caster().getYRot());
        return h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    /**
     * A forma pode sair? Sem origem própria ela sai do conjurador: formas atrasadas (Atraso/Repetir) não saem de quem
     * já saiu do mundo, renasceu (a entidade antiga é removida) ou trocou de dimensão (o contexto continua preso ao
     * mundo antigo). Um estágio de magia programável (ou castFrom) sai de um ponto do mundo e sempre pode sair —
     * como um projétil que continua voando depois que o conjurador caiu.
     */
    public static boolean casterReady(SpellContext ctx) {
        if (ctx.hasOrigin()) return true;
        LivingEntity c = ctx.caster();
        return c != null && !c.isRemoved() && c.level() == ctx.level();
    }

    /**
     * Criaturas vivas num raio que a magia AFETA (inimigos numa magia de dano, aliados numa de cura...).
     * Usado pelo Teleguiado e pelo Dividir das formas, para não "mirar" em quem a magia ignora.
     */
    public static List<LivingEntity> targetsAround(SpellContext ctx, Vec3 center, double radius) {
        Entity caster = ctx.caster();
        return ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e != caster && e.distanceToSqr(center) <= radius * radius
                        && affects(ctx, e));
    }

    /** ClipContext que aceita fonte nula (contexto recarregado sem conjurador): o construtor do vanilla daria NPE. */
    public static ClipContext clipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity source) {
        return new ClipContext(from, to, block, fluid, source == null ? CollisionContext.empty() : CollisionContext.of(source));
    }

    /** Algum efeito da magia vale para este alvo? (feixes atravessam aliados de magias de dano, por exemplo). */
    public static boolean affects(SpellContext ctx, Entity target) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (ctx.isValidTarget(e.effect(), e.stats(), target)) return true;
        }
        return false;
    }

    /** Raycast de blocos (formato de colisão, sem fluidos). */
    public static BlockHitResult clipBlocks(SpellContext ctx, Vec3 from, Vec3 to, Entity source) {
        return ctx.level().clip(clipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
    }

    /** Há caminho livre (sem blocos sólidos) entre os dois pontos? */
    public static boolean clear(SpellContext ctx, Vec3 from, Vec3 to, Entity source) {
        return clipBlocks(ctx, from, to, source).getType() == HitResult.Type.MISS;
    }

    /**
     * Todas as entidades "acertáveis" que um raio de largura {@code width} cruza entre {@code from} e {@code to},
     * da mais perto para a mais longe.
     */
    public static List<Hit> entitiesAlong(SpellContext ctx, Vec3 from, Vec3 to, double width, Predicate<Entity> filter) {
        List<Hit> hits = new ArrayList<>();
        AABB area = new AABB(from, to).inflate(width + 1.0);
        Entity caster = ctx.caster();
        for (Entity e : ctx.level().getEntities(caster, area,
                e -> e.isAlive() && !e.isSpectator() && e.isPickable() && !riding(caster, e) && filter.test(e))) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + width);
            Optional<Vec3> p = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            p.ifPresent(v -> hits.add(new Hit(e, v, v.distanceTo(from))));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        return hits;
    }

    /** A montaria do conjurador (ou quem está montado nele) nunca é atingida pelos raios dele. */
    private static boolean riding(Entity caster, Entity e) {
        return caster != null && (e == caster.getVehicle() || e.getVehicle() == caster);
    }

    /** Ponto mais perto de {@code p} dentro da caixa da entidade. */
    public static Vec3 closestPoint(Entity e, Vec3 p) {
        AABB b = e.getBoundingBox();
        return new Vec3(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY, p.y)),
                Math.max(b.minZ, Math.min(b.maxZ, p.z)));
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Desce de {@code p} até o primeiro bloco sólido ou superfície de líquido (até {@code depth} blocos). */
    public static Vec3 groundBelow(SpellContext ctx, Vec3 p, double depth) {
        BlockHitResult r = ctx.level().clip(clipContext(p, p.subtract(0, depth, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, ctx.caster()));
        return r.getType() == HitResult.Type.MISS ? p : r.getLocation();
    }

    /**
     * Ponto mirado até {@code range} blocos: a entidade que estiver na mira, senão o chão onde o olhar bate
     * (se mirar uma parede, o pé dela; se mirar o céu, o chão embaixo do fim do alcance). Mira a partir de
     * {@code ctx.eyePos()}/{@code ctx.look()}: num estágio sai do ponto de origem, e o mob não corrige a mira.
     */
    public static Point aimPoint(SpellContext ctx, double range) {
        LivingEntity c = ctx.caster();
        if (!ctx.hasOrigin() && c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= range) {
            return new Point(mob.getTarget().position(), mob.getTarget());
        }
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(aimDir(ctx).scale(range));
        BlockHitResult bh = ctx.level().clip(clipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, c));
        Vec3 limit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        // Só "trava" a mira em quem a magia afeta: o lobo domado na frente não vira o alvo do meteoro
        List<Hit> ents = entitiesAlong(ctx, eye, limit, 0.35,
                e -> e instanceof LivingEntity && e != c && !ctx.ignores(e) && affects(ctx, e));
        if (!ents.isEmpty()) return new Point(ents.get(0).entity().position(), ents.get(0).entity());
        if (bh.getType() == HitResult.Type.MISS) return new Point(groundBelow(ctx, end, 64), null);
        if (bh.getDirection() == Direction.UP) return new Point(bh.getLocation(), null);
        Vec3 out = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
        return new Point(groundBelow(ctx, out, 24), null);
    }

    /**
     * Ponto de uma forma de ÁREA (Queda, Chuva): num estágio nascido de um impacto, a âncora (descida até o chão se
     * ele estiver a até {@code depth} blocos; senão o próprio ponto, no ar) e a criatura que está nela
     * ({@link #entityAt}); sem âncora, a mira de sempre ({@link #aimPoint}).
     */
    public static Point areaPoint(SpellContext ctx, double range, double depth) {
        Vec3 a = ctx.anchor();
        if (a == null) return aimPoint(ctx, range);
        Vec3 g = findGround(ctx, anchorProbe(ctx, a), depth);
        return new Point(g != null ? g : a, entityAt(ctx, a, 1.0));
    }

    /**
     * De onde procurar o chão sob a âncora. Num impacto em BLOCO a âncora fica exatamente na face do bloco (descer
     * por ela "acha" a própria parede); o núcleo põe a origem do estágio 0.3 para fora, então usa a origem quando as
     * duas estão juntas. Nos outros casos (entidade, fim, Saltar) a própria âncora.
     */
    public static Vec3 anchorProbe(SpellContext ctx, Vec3 anchor) {
        Vec3 o = ctx.eyePos();
        return ctx.hasOrigin() && o.distanceToSqr(anchor) < 0.25 ? o : anchor;
    }

    /**
     * A criatura que está NO ponto {@code p} (caixa a até {@code radius} blocos dele; a mais perto), que a magia afeta.
     * Diferente de {@link #nearestAffected}, INCLUI o alvo que o estágio anterior acabou de atingir: formas de área e a
     * Mira nascidas no ponto de impacto devem cair nele (Feixe → Ao Acertar → Queda cai em quem o feixe atingiu).
     * Num empate (inimigos amontoados) prefere outro: com Saltar a âncora são os pés do PRÓXIMO inimigo e o alvo
     * anterior é de onde a magia saltou.
     */
    public static LivingEntity entityAt(SpellContext ctx, Vec3 p, double radius) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Entity caster = ctx.caster();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(radius + 1.0),
                e -> e.isAlive() && !e.isSpectator() && e != caster)) {
            double d = closestPoint(e, p).distanceTo(p);
            if (d > radius || !affects(ctx, e)) continue;
            if (ctx.ignores(e)) d += 0.1;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Primeiro chão (bloco ou líquido) até {@code depth} blocos abaixo de {@code p}, ou null se não houver. */
    public static Vec3 findGround(SpellContext ctx, Vec3 p, double depth) {
        Vec3 from = p.add(0, 0.05, 0);
        BlockHitResult r = ctx.level().clip(clipContext(from, from.subtract(0, depth, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, null));
        return r.getType() == HitResult.Type.MISS ? null : r.getLocation();
    }

    /**
     * Criatura que a magia afeta mais perto de {@code p} (pés a até {@code radius} blocos, o mesmo critério de
     * {@code resolveArea}), fora o alvo que o estágio anterior acabou de atingir. Null se não houver.
     */
    public static LivingEntity nearestAffected(SpellContext ctx, Vec3 p, double radius) {
        LivingEntity best = null;
        double bestD = radius * radius;
        Entity caster = ctx.caster();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(radius + 1.0),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !ctx.ignores(e))) {
            double d = e.distanceToSqr(p);
            if (d <= bestD && affects(ctx, e)) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Direção horizontal de {@code d}: o próximo estágio de algo que caiu do céu (Queda, gota da Chuva) segue
     * "para a frente", não para dentro do chão. Quase vertical: usa a mira horizontal do estágio.
     */
    public static Vec3 flatDir(SpellContext ctx, Vec3 d) {
        Vec3 h = new Vec3(d.x, 0, d.z);
        return h.lengthSqr() > 0.0025 ? h.normalize() : horizontalAim(ctx);
    }

    /**
     * Formas de curto alcance (Cone, Corte) num estágio cuja âncora ficou longe da origem — Saltar: a origem é o
     * alvo anterior e a âncora o próximo inimigo. A forma "avança" pela direção até ficar a meio alcance da âncora
     * (sem atravessar paredes). Fora desse caso devolve a própria origem.
     */
    public static Vec3 closeIn(SpellContext ctx, Vec3 origin, Vec3 dir, double reach) {
        Vec3 a = ctx.anchor();
        if (!ctx.hasOrigin() || a == null) return origin;
        double along = a.subtract(origin).dot(dir);
        if (along <= reach * 0.75) return origin;
        Vec3 to = origin.add(dir.scale(along - reach * 0.5));
        BlockHitResult bh = clipBlocks(ctx, origin, to, null);
        if (bh.getType() == HitResult.Type.BLOCK) to = bh.getLocation().subtract(dir.scale(0.3));
        return to;
    }

    /**
     * Altura do "chão" numa coluna para algo que desliza (Onda): procura de {@code y + stepUp} até {@code y - depth}
     * o topo do primeiro bloco com colisão ou a superfície de um líquido. Retorna NaN se não houver chão.
     */
    public static double groundHeight(BlockGetter level, double x, double y, double z, double stepUp, double depth) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(y + stepUp);
        int bottom = (int) Math.floor(y - depth);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = top; by >= bottom; by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return by + shape.max(Direction.Axis.Y);
            // Superfície de líquido conta como chão (a onda corre por cima d'água); submersa, segue até o fundo.
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty() && level.getFluidState(pos.above()).isEmpty()) return by + fluid.getHeight(level, pos);
        }
        return Double.NaN;
    }

    private FormAim() {}
}
