package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Party;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Regra de aliado igual à do {@code SpellContext.isAlly} (dono, grupo/facção, time, PvP desligado). */
public final class GcaAllies {

    public static boolean isAlly(@Nullable LivingEntity owner, @Nullable Entity e) {
        if (owner == null || e == null) return false;
        if (e == owner) return true;
        UUID id = owner.getUUID();
        if (e instanceof OwnableEntity own && id.equals(own.getOwnerUUID())) return true;
        Boolean social = Party.allyOverride(owner, e);
        if (social != null) return social;
        if (owner.isAlliedTo(e)) return true;
        return owner instanceof Player && e instanceof Player && owner.getServer() != null && !owner.getServer().isPvpAllowed();
    }

    /** Criatura (lacaio, invocação, pet) que pertence a {@code owner}? */
    public static boolean ownedBy(Entity e, UUID owner) {
        return e instanceof OwnableEntity own && owner.equals(own.getOwnerUUID());
    }

    private GcaAllies() {}
}
