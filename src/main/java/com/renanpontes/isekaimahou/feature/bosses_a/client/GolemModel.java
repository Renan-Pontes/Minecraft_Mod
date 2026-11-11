package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.PrimordialGolemEntity;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo do Golem Primordial: a casca de pedra (Escudo) só aparece com o escudo ativo e vai perdendo
 * pedaços conforme racha (topo abaixo de 2/3, frente abaixo de 1/3).
 */
public class GolemModel extends DefaultedEntityGeoModel<PrimordialGolemEntity> {
    public GolemModel() {
        super(IsekaiMahou.id("primordial_golem"));
    }

    @Override
    public void setCustomAnimations(PrimordialGolemEntity golem, long instanceId, AnimationState<PrimordialGolemEntity> state) {
        super.setCustomAnimations(golem, instanceId, state);
        boolean shield = golem.isShielded();
        float f = golem.shieldFraction();
        getBone("shell").ifPresent(b -> b.setHidden(!shield));
        for (String side : new String[] {"shell_back", "shell_left", "shell_right"}) {
            getBone(side).ifPresent(b -> b.setHidden(!shield));
        }
        getBone("shell_top").ifPresent(b -> b.setHidden(!shield || f < 0.67F));
        getBone("shell_front").ifPresent(b -> b.setHidden(!shield || f < 0.34F));
    }
}
