package com.renanpontes.isekaimahou.feature.creatures_b.client;

import java.util.function.Function;

import com.renanpontes.isekaimahou.feature.creatures_b.entity.CbBoss;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.Color;

/** Renderer dos chefes do módulo: igual ao dos humanoides, mas sem o "tombar de lado" vanilla (há animação de morte). */
public class CbBossRenderer<T extends CbBoss> extends CbRenderer<T> {
    public CbBossRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float shadow, Function<T, Color> tint) {
        super(ctx, model, true, shadow, tint);
    }

    @Override
    protected float getDeathMaxRotation(T boss) {
        return 0.0F;
    }

    /** Vermelho só no golpe final: a vanilla pinta a morte inteira (aqui são 3 s de animação). */
    @Override
    public int getPackedOverlay(T boss, float u, float partialTick) {
        boolean red = boss.deathTime > 0 ? boss.deathTime < 8 : boss.hurtTime > 0;
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(red));
    }
}
