package com.renanpontes.isekaimahou.feature.nether_citadel;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Quem é "demônio" (tag {@code isekai_mahou:citadel_demons}) e quem é aliado de quem nos golpes dos tesouros. */
public final class NcDemons {
    public static final TagKey<EntityType<?>> DEMONS = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("citadel_demons"));

    /** Demônios da tag + mortos-vivos (alvos da Katana do Sol). */
    public static boolean isUnholy(Entity e) {
        return e.getType().is(DEMONS) || e.getType().is(EntityTypeTags.UNDEAD);
    }

    /** Guarnição da Cidadela (não se atacam nem atacam o Lorde). */
    public static boolean isGarrison(Entity e) {
        return e.getType() == NcRegistries.DEMON_LORD.get() || e.getType() == NcRegistries.DEMON_KNIGHT.get()
                || e.getType() == NcRegistries.DEMON_SUCCUBUS.get();
    }

    /** Alvo válido para um golpe de jogador (não ele mesmo, nem bicho/invocação dele, nem time). */
    public static boolean isEnemyOf(Player player, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == player) return false;
        if (e instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())) return false;
        if (player.isAlliedTo(e)) return false;
        if (e instanceof Player other) return !other.isCreative() && !other.isSpectator() && player.canHarmPlayer(other);
        // v1.0 (correção): a Investida da Katana e o Corte Antimagia acertavam aldeões, moradores e animais no caminho.
        // Agora só quem é hostil ou está atacando o jogador (cidades são sagradas).
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == player);
    }

    private NcDemons() {}
}
