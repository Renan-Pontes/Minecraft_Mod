package com.renanpontes.isekaimahou.feature.depths.item;

import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Quem os golpes em área das armas do módulo atingem: só inimigos (nunca jogadores, bichos de estimação ou aldeões). */
public final class DepthsFoes {

    public static boolean isFoe(Player player, Entity e) {
        if (!(e instanceof LivingEntity le) || e == player || !le.isAlive() || e instanceof Player || e instanceof ArmorStand) return false;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        if (e.isAlliedTo(player)) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == player);
    }

    /** Criatura "grande" para a Matadora de Dragões: chefes ou corpos enormes. */
    public static boolean isHuge(LivingEntity e) {
        return BossKit.isBossLike(e) || e.getBbWidth() >= 1.3F || e.getBbHeight() >= 2.5F;
    }

    /** Dentro do arco à frente do jogador (meio-ângulo em graus). */
    public static boolean inArc(Player player, Entity e, double halfAngleDeg) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 0.25) return true;
        if (look.lengthSqr() < 1.0E-4) return true;
        double cos = look.normalize().dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(halfAngleDeg));
    }

    private DepthsFoes() {}
}
