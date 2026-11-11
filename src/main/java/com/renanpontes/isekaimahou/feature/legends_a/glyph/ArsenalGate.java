package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ArsenalPortalEntity;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ConjuredBladeEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Estado de UM Portão do Arsenal aberto (servidor): os portais o compartilham. Calcula a mira uma vez por tick
 * (olhar do jogador, alvo do mob ou a direção do estágio), escolhe os alvos de cada disparo (espalhando entre os
 * inimigos perto do ponto mirado) e avisa o fim do estágio ({@code ctx.end}) quando o último portal fecha.
 */
public final class ArsenalGate {
    /** Alcance da mira do jogador. */
    public static final double RANGE = 48.0;
    /** Inimigos a até esta distância do ponto mirado viram alvo das armas. */
    public static final double TARGET_RADIUS = 7.0;
    /** Velocidade das armas (blocos/tick). */
    public static final float SPEED = 2.2F;

    private final SpellContext shot;
    private final int slots;
    private final boolean bound;
    private final Vec3 base;
    private final Vec3 forward;
    private final int color;
    private int portalsAlive;
    private boolean ended;
    private int shotCount;

    private long aimTick = Long.MIN_VALUE;
    private Vec3 aim;
    private Vec3 lastAim;
    private List<LivingEntity> foes = List.of();

    /**
     * @param shot   contexto usado pelas armas (já com o poder por arma reduzido)
     * @param bound  true = acompanha o conjurador; false = fixo atrás de {@code base} (estágio com origem)
     */
    ArsenalGate(SpellContext shot, int slots, boolean bound, Vec3 base, Vec3 forward, int color) {
        this.shot = shot;
        this.slots = slots;
        this.bound = bound;
        this.base = base;
        this.forward = forward;
        this.color = color;
        this.portalsAlive = slots;
    }

    public int slots() { return slots; }
    public Vec3 forward() { return forward; }

    /** Posição do portal {@code slot} (servidor; o cliente refaz a mesma conta com a posição interpolada do dono). */
    public Vec3 slotPos(int slot) {
        LivingEntity caster = shot.caster();
        if (bound && caster != null) return ArsenalPortalEntity.slotPos(caster.position(), caster.getYRot(), slot, slots);
        return ArsenalPortalEntity.slotPos(base, ArsenalPortalEntity.yawOf(forward), slot, slots);
    }

    @Nullable
    public Vec3 aimPoint() { return aim; }

    /** Recalcula mira e alvos (uma vez por tick para o portão inteiro). */
    public void refresh(ServerLevel sl) {
        long now = sl.getGameTime();
        if (now == aimTick) return;
        aimTick = now;
        aim = computeAim(sl);
        lastAim = aim;
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : shot.enemiesAround(aim, TARGET_RADIUS)) {
            if (!shot.ignores(e) && !e.isSpectator()) list.add(e);
        }
        LivingEntity caster = shot.caster();
        if (list.isEmpty() && bound && caster != null) {
            // Nada perto da mira: inimigos à frente do conjurador (cone de ~35°)
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getLookAngle();
            for (LivingEntity e : shot.enemiesAround(eye, 24)) {
                Vec3 to = LegendFx.center(e).subtract(eye);
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) > 0.82 && caster.hasLineOfSight(e)) list.add(e);
            }
        }
        Vec3 a = aim;
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(a)));
        foes = list;
    }

    private Vec3 computeAim(ServerLevel sl) {
        LivingEntity caster = shot.caster();
        if (!bound || caster == null) {
            // Estágio com origem: o ponto de impacto que abriu o portão (ou à frente dele)
            Vec3 anchor = shot.anchor();
            return anchor != null ? anchor.add(0, 0.8, 0) : shot.eyePos().add(shot.look().scale(16));
        }
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return LegendFx.center(mob.getTarget());
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(RANGE));
        BlockHitResult block = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(sl, caster, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !shot.isAlly(e));
        return ent != null ? LegendFx.center(ent.getEntity()) : stop;
    }

    /** Um portal dispara a arma que estava saindo dele. */
    public void fire(ServerLevel sl, ArsenalPortalEntity portal) {
        if (aim == null) return;
        Vec3 target;
        if (!foes.isEmpty()) {
            LivingEntity foe = foes.get((portal.slot() + shotCount) % foes.size());
            target = LegendFx.center(foe);
        } else {
            // Sem inimigo: chuva de armas espalhada em volta do ponto mirado
            target = aim.add((sl.random.nextDouble() - 0.5) * 2.4, (sl.random.nextDouble() - 0.5) * 0.6, (sl.random.nextDouble() - 0.5) * 2.4);
        }
        shotCount++;
        Vec3 from = unblocked(sl, portal.position().add(portal.aim().scale(0.5)));
        Vec3 dir = target.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) dir = forward;
        int pierce = 1 + Math.max(0, shot.formStats().pierce);
        float speed = SPEED * Math.max(0.6F, shot.formStats().speedMult());
        ConjuredBladeEntity blade = ConjuredBladeEntity.carrier(shot, from, dir, portal.item(), color, 1.6F, pierce, speed);
        sl.addFreshEntity(blade);
        portal.setItem(LegendFx.treasureWeapon(sl.random));
        sl.sendParticles(LegendFx.dust(color, 1.3F), from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 3, 0.2, 0.2, 0.2, 0.05);
        LegendFx.sound(sl, from, SoundEvents.TRIDENT_THROW, 0.8F, 0.85F + sl.random.nextFloat() * 0.35F);
    }

    /**
     * Ponto de saída da arma do lado de cá de qualquer parede: dentro de casa/caverna o portal pode ficar atrás da
     * parede das costas, e a arma cravaria nela em vez de voar no alvo. Ela passa a surgir logo antes do bloco.
     */
    private Vec3 unblocked(ServerLevel sl, Vec3 from) {
        LivingEntity caster = shot.caster();
        Vec3 hub = bound && caster != null ? caster.getEyePosition() : shot.eyePos();
        BlockHitResult wall = sl.clip(new ClipContext(hub, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (wall.getType() != HitResult.Type.BLOCK) return from;
        Vec3 back = hub.subtract(wall.getLocation());
        return back.lengthSqr() < 0.16 ? hub : wall.getLocation().add(back.normalize().scale(0.4));
    }

    /** Um portal fechou/sumiu. O último avisa o fim do estágio (ligação Ao Terminar). */
    public void portalGone(ServerLevel sl) {
        portalsAlive--;
        if (portalsAlive > 0 || ended) return;
        ended = true;
        Vec3 at = lastAim != null ? lastAim : base;
        LivingEntity caster = shot.caster();
        Vec3 dir = caster != null && bound ? at.subtract(caster.getEyePosition()) : forward;
        shot.end(at, dir);
    }
}
