package com.renanpontes.isekaimahou.feature.class_ranger.client;

import java.util.function.ToIntFunction;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib dos companheiros: mesmo .geo e animações, textura trocada pelo ESTÁGIO de evolução
 * ({@code textures/entity/<id>_<0|1|2>.png} = Filhote, Adulto, Alfa — cada uma com a sua _glowmask).
 */
public class RangerStageModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
    private final ResourceLocation[] textures;
    private final ToIntFunction<T> stage;

    public RangerStageModel(String id, boolean turnsHead, ToIntFunction<T> stage) {
        this(id, turnsHead, stage, 3);
    }

    /** {@code stages} texturas ({@code _0.._N-1}); o Lobo tem 4: a 4ª é a Deusa Loba. */
    public RangerStageModel(String id, boolean turnsHead, ToIntFunction<T> stage, int stages) {
        super(IsekaiMahou.id(id), turnsHead);
        this.stage = stage;
        this.textures = new ResourceLocation[Math.max(1, stages)];
        for (int i = 0; i < textures.length; i++) textures[i] = IsekaiMahou.id("textures/entity/" + id + "_" + i + ".png");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        int s = Math.max(0, Math.min(textures.length - 1, stage.applyAsInt(animatable)));
        return textures[s];
    }
}
