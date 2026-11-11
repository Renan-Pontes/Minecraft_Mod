package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.HmBoss;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

/** Quem é do mesmo lado: criaturas e chefes do mesmo reino (Vazios entre si, fadas entre si). */
public final class HmAllies {

    @Nullable
    public static HmRealm sideOf(@Nullable Entity e) {
        if (e instanceof HmMob m) return m.side();
        if (e instanceof HmBoss b) return b.side();
        return null;
    }

    public static boolean isAlly(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        // Bichos e invocações de jogadores nunca são aliados das criaturas, mesmo sendo do mesmo tipo.
        if (b instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        HmRealm sa = sideOf(a), sb = sideOf(b);
        return sa != null && sa == sb;
    }

    /** Criatura "Vazia" (Vazios, Menos, Arrancar, Rei/Imperatriz): o Rasgo de Reishi dos Quincy dá dano dobrado nelas. */
    public static boolean isHollow(@Nullable Entity e) {
        return sideOf(e) == HmRealm.HOLLOW;
    }

    private HmAllies() {}
}
