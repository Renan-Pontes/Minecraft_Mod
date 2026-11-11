package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import javax.annotation.Nullable;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Witch;

/** Quem não deve ser atingido pelos ataques de um chefe (ele mesmo, outros chefes, ilusões e as próprias invocações). */
public final class BossAllies {

    public static boolean isAlly(@Nullable Entity source, Entity target) {
        if (source == null) return false;
        if (target == source) return true;
        if (!isBossSide(source)) return false;
        if (isBossSide(target)) return true;
        // Invocações de jogadores (servos mortos-vivos do Necromante, familiares...) são inimigas, mesmo sendo do mesmo tipo.
        if (target instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        if (source instanceof Lich && target.getType().is(EntityTypeTags.UNDEAD)) return true;
        return source instanceof CorruptedWitch && target instanceof Witch;
    }

    /** Chefes deste módulo e tudo o que eles invocam. */
    private static boolean isBossSide(Entity e) {
        return e instanceof ClassBossEntity || e instanceof ArchmageIllusion || e instanceof LichPhylactery || e instanceof HexCat
                || e.getTags().contains(ClassBossEntity.MINION_TAG);
    }

    private BossAllies() {}
}
