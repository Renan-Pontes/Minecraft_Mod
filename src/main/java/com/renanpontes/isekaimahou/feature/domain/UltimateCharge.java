package com.renanpontes.isekaimahou.feature.domain;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * O dano de magia recarrega a Carga de Ultimate (núcleo: MagicApi.onSpellDamage). Para que um Cataclismo ou os
 * pulsos de um Domínio NÃO recarreguem a própria ultimate (o que permitiria emendar domínios sem parar),
 * tiramos uma "foto" das cargas antes de aplicar os efeitos e devolvemos depois.
 */
public final class UltimateCharge {

    /** Cargas atuais do jogador (null para mobs: eles não usam carga). */
    public static float[] snapshot(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer sp)) return null;
        MagicData d = MagicApi.get(sp);
        Element[] all = Element.values();
        float[] out = new float[all.length];
        for (int i = 0; i < all.length; i++) out[i] = d.charge(all[i]);
        return out;
    }

    /** Desfaz o que foi ganho desde a foto (nunca tira carga que já existia). */
    public static void restore(LivingEntity owner, float[] snap) {
        if (snap == null || !(owner instanceof ServerPlayer sp)) return;
        MagicData d = MagicApi.get(sp);
        Element[] all = Element.values();
        for (int i = 0; i < all.length; i++) {
            if (d.charge(all[i]) > snap[i]) d.setCharge(all[i], snap[i]);
        }
    }

    private UltimateCharge() {}
}
