package com.renanpontes.isekaimahou.feature.armory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Quem é aliado / inimigo para as passivas em área do Arsenal (curas, auras, ondas de choque, explosões). */
public final class ArmoryAllies {
    /** Marca de dono dos servos do Necromante (módulo ritual_necro, por texto). */
    private static final String NECRO_OWNER_TAG = "isekai_mahou.necro_owner";

    /** Aliado: outros jogadores (mesmo time, ou sem times) e criaturas domadas/invocadas do dono. */
    public static boolean isFriend(LivingEntity owner, LivingEntity e) {
        if (e == owner) return true;
        if (e instanceof Player p) {
            if (p.isSpectator()) return false;
            if (!(owner instanceof Player)) return false;
            return owner.getTeam() == null || p.getTeam() == null || owner.isAlliedTo(p);
        }
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return true;
        CompoundTag tag = e.getPersistentData();
        return tag.hasUUID(NECRO_OWNER_TAG) && owner.getUUID().equals(tag.getUUID(NECRO_OWNER_TAG));
    }

    /**
     * Inimigo para dano em área: monstros e qualquer criatura que esteja mirando o dono. Animais, aldeões, suportes de
     * armadura e outros jogadores ficam de fora (o dano em área não deve virar desastre na vila).
     */
    public static boolean isEnemy(LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e instanceof ArmorStand || e instanceof Player || isFriend(owner, e)) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == owner);
    }

    private ArmoryAllies() {}
}
