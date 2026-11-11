package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

/** Quem não deve ser atingido pelos golpes de um chefe do módulo (ele mesmo, as cabeças da Hidra, os mímicos do Rei). */
public final class BgAllies {

    public static boolean isAlly(@Nullable Entity source, Entity target) {
        if (source == null) return false;
        if (target == source) return true;
        if (!isBossSide(source)) return false;
        // Invocações e bichos de jogadores são inimigos, mesmo que sejam do mesmo tipo.
        if (target instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        return isBossSide(target);
    }

    /** Chefes do módulo e tudo o que eles criam. */
    public static boolean isBossSide(Entity e) {
        return e instanceof GBossEntity || e instanceof FalseHydraHead || e instanceof VaultMimic
                || e.getTags().contains(GBossEntity.MINION_TAG);
    }

    private BgAllies() {}
}
