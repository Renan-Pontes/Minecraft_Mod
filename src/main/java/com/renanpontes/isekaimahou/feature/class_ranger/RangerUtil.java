package com.renanpontes.isekaimahou.feature.class_ranger;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Utilidades do módulo: aliados e mira no chão para as formas de área. */
public final class RangerUtil {
    /** {@code e} é aliado de {@code owner} (ele mesmo, bicho/companheiro dele, time, jogadores sem PvP)? */
    public static boolean isFriend(@Nullable LivingEntity owner, Entity e) {
        if (owner == null) return false;
        if (e == owner) return true;
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return true;
        if (owner.isAlliedTo(e)) return true;
        Boolean social = com.renanpontes.isekaimahou.core.api.Party.allyOverride(owner, e);
        if (social != null) return social;
        return owner instanceof Player && e instanceof Player && owner.level().getServer() != null
                && !owner.level().getServer().isPvpAllowed();
    }

    /**
     * Ponto no chão para formas de área: a âncora do estágio, o alvo do mob ou onde a mira toca um bloco/criatura
     * até {@code range} blocos (sem nada, o chão embaixo do ponto final).
     */
    public static Vec3 aimGround(SpellContext ctx, double range) {
        ServerLevel level = ctx.level();
        if (ctx.anchor() != null) return groundBelow(level, ctx.anchor());
        LivingEntity caster = ctx.caster();
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) {
            return groundBelow(level, mob.getTarget().position().add(0, 0.5, 0));
        }
        Vec3 from = ctx.eyePos();
        Vec3 to = from.add(ctx.look().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                caster != null ? CollisionContext.of(caster) : CollisionContext.empty()));
        Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities(caster, new AABB(from, end).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !ctx.isAlly(e))) {
            var clip = e.getBoundingBox().inflate(0.4).clip(from, end);
            if (clip.isEmpty()) continue;
            double d = from.distanceToSqr(clip.get());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) return groundBelow(level, best.position().add(0, 0.5, 0));
        return groundBelow(level, end.add(0, 0.2, 0));
    }

    /** Desce de {@code p} até achar chão sólido (no máx. 24 blocos); sem chão, o próprio ponto. */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        for (int i = 0; i < 24 && pos.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = pos.below();
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) return new Vec3(p.x, below.getY() + shape.max(Direction.Axis.Y), p.z);
            pos = below;
        }
        return p;
    }

    /** Altura de onde as flechas caem: {@code wanted} acima do chão, ou logo abaixo do teto (cavernas/casas). */
    public static double skyY(ServerLevel level, Vec3 ground, double wanted) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ground.x), (int) Math.floor(ground.z));
        if (top > ground.y + 2) {
            BlockPos p = BlockPos.containing(ground).above();
            for (int i = 0; i < (int) wanted; i++) {
                if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return p.getY() + 0.2;
                p = p.above();
            }
        }
        return ground.y + wanted;
    }

    private RangerUtil() {}
}
