package com.renanpontes.isekaimahou.feature.fusions_b.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.Vec3;

/** Lógica de reflexão do Prisma Refletor (usada pelo tick do efeito e pelo evento de impacto de projétil). */
public final class Prism {
    private static final String TAG = "isekai_mahou_prism";

    /** O projétil vai acertar (ou passar raspando) o protegido nos próximos ticks? */
    public static boolean threatens(Projectile p, LivingEntity protectedOne) {
        if (friendly(p.getOwner(), protectedOne)) return false;
        if (recentlyReflected(p)) return false;
        Vec3 v = p.getDeltaMovement();
        double speed2 = v.lengthSqr();
        if (speed2 < 0.01) return false; // parado (flecha cravada no chão)
        Vec3 rel = FbUtil.center(protectedOne).subtract(p.position());
        double t = rel.dot(v) / speed2;
        if (t < 0) return false; // já passou / indo embora
        t = Math.min(t, 3.0);
        double miss = rel.subtract(v.scale(t)).length();
        return miss < Math.max(1.0, protectedOne.getBbWidth()) + 0.7;
    }

    /** Quem atirou é o próprio protegido ou um aliado dele (time, invocação, jogadores com PvP desligado)? */
    public static boolean friendly(Entity owner, LivingEntity protectedOne) {
        if (owner == null) return false;
        if (owner == protectedOne) return true;
        if (owner.isAlliedTo(protectedOne) || protectedOne.isAlliedTo(owner)) return true;
        if (owner instanceof OwnableEntity o && protectedOne.getUUID().equals(o.getOwnerUUID())) return true;
        if (protectedOne instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID())) return true;
        return owner instanceof Player && protectedOne instanceof Player && owner.getServer() != null
                && !owner.getServer().isPvpAllowed();
    }

    /** Foi devolvido há menos de meio segundo? */
    public static boolean recentlyReflected(Projectile p) {
        long at = p.getPersistentData().getLong(TAG);
        return at != 0 && p.level().getGameTime() - at < 10;
    }

    /**
     * Devolve o projétil para quem atirou (ou de volta pelo caminho). Projéteis de magia do próprio mod são
     * ABSORVIDOS pelo cristal: eles guardam o contexto do conjurador original e, devolvidos, poderiam ferir
     * os aliados de quem foi protegido.
     */
    public static void reflect(ServerLevel sl, Projectile p, LivingEntity protectedOne) {
        Vec3 at = p.position();
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(p.getType());
        // Teleguiados (voltariam) e anzóis (prenderiam no dono errado) também são absorvidos
        if (IsekaiMahou.MODID.equals(key.getNamespace()) || p instanceof ShulkerBullet || p instanceof FishingHook) {
            burst(sl, at);
            FbFx.sound(sl, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 1.3F);
            p.discard();
            return;
        }
        Entity owner = p.getOwner();
        Vec3 v = p.getDeltaMovement();
        double speed = Math.max(0.8, v.length());
        Vec3 dir;
        if (owner instanceof LivingEntity le && le.isAlive() && le != protectedOne && le.level() == sl
                && le.distanceToSqr(protectedOne) < 48 * 48) {
            dir = le.getEyePosition().subtract(at).normalize();
        } else {
            dir = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(-1) : protectedOne.getLookAngle();
        }
        p.setDeltaMovement(dir.scale(speed));
        if (p instanceof ThrownTrident) {
            // Tridente fiel precisa continuar sendo de quem jogou (senão voltaria para o protegido)
        } else if (p instanceof AbstractArrow arrow) {
            // setOwner(jogador) libera pegar a flecha do chão: mantém a regra original (sem flechas grátis)
            AbstractArrow.Pickup pickup = arrow.pickup;
            arrow.setOwner(protectedOne);
            arrow.pickup = pickup;
        } else {
            p.setOwner(protectedOne);
        }
        p.hasImpulse = true;
        p.hurtMarked = true;
        p.getPersistentData().putLong(TAG, sl.getGameTime());
        burst(sl, at);
        FbFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
        FbFx.sound(sl, at, SoundEvents.SHIELD_BLOCK, 0.6F, 1.4F);
    }

    /** Dispersão do prisma: as cores do arco-íris saindo do ponto. */
    private static void burst(ServerLevel sl, Vec3 at) {
        for (int c : FbFx.RAINBOW) {
            sl.sendParticles(FbFx.dust(c, 1.1F), at.x, at.y, at.z, 3, 0.25, 0.25, 0.25, 0);
        }
        sl.sendParticles(Vfx.endRod(), at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.08);
        sl.sendParticles(Vfx.waxOn(), at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.05);
    }

    private Prism() {}
}
