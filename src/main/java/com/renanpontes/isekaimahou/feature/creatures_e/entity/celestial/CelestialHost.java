package com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * "Hoste celestial": seres do Santuario se protegem. Quem fere um celestial vira alvo dos guardas proximos,
 * e o Querubim cura qualquer ser da tag {@code isekai_mahou:celestial_beings} (inclui chefes de outros modulos).
 */
public final class CelestialHost {
    public static final TagKey<EntityType<?>> CELESTIAL_BEINGS = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("celestial_beings"));

    public static boolean isCelestial(Entity e) {
        return e != null && (e.getType().is(CELESTIAL_BEINGS)
                || (e instanceof DimensionalCreature d && d.faction() == DimensionalCreature.Faction.CELESTIAL));
    }

    /** Chama os guardas (nao o Querubim) num raio de 20 blocos para cacar quem feriu um celestial. */
    public static void alert(LivingEntity victim, LivingEntity attacker) {
        if (attacker == null || isCelestial(attacker)) return;
        if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (Mob guard : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(20),
                m -> m != victim && m.isAlive() && isCelestial(m) && !(m instanceof Cherub))) {
            if (guard.getTarget() == null || !guard.getTarget().isAlive()) guard.setTarget(attacker);
        }
    }

    private CelestialHost() {}
}
