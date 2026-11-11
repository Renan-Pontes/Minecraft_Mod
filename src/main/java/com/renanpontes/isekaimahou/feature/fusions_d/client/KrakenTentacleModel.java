package com.renanpontes.isekaimahou.feature.fusions_d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * Tentáculo do Kraken: 10 segmentos encadeados que afinam de 12 a 3 pixels (cerca de 5 blocos antes da escala). Cada
 * um gira em relação ao anterior, então rotações pequenas viram curvas suaves. Ventosas pintadas na frente (-Z).
 * Textura 128x64 gerada por tools/gen/fusions_d.py (mesmo layout de UV que {@link #TEX}).
 */
public class KrakenTentacleModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(IsekaiMahou.id("fd_kraken_tentacle"), "main");
    public static final int SEGMENTS = 10;
    public static final int LEN = 8;
    public static final int[] SIZE = {12, 11, 10, 9, 8, 7, 6, 5, 4, 3};
    public static final int[][] TEX = {{0, 0}, {48, 0}, {0, 20}, {40, 20}, {76, 20}, {0, 40}, {28, 40}, {108, 20}, {104, 0}, {92, 0}};

    private final ModelPart root;
    private final ModelPart[] seg = new ModelPart[SEGMENTS];

    public KrakenTentacleModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        ModelPart p = root;
        for (int i = 0; i < SEGMENTS; i++) {
            p = p.getChild("seg" + i);
            seg[i] = p;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        for (int i = 0; i < SEGMENTS; i++) {
            float h = SIZE[i] / 2F;
            p = p.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(TEX[i][0], TEX[i][1])
                    .addBox(-h, -LEN, -h, SIZE[i], LEN, SIZE[i], new CubeDeformation(0.05F)),
                    PartPose.offset(0, i == 0 ? 24 : -(LEN - 0.6F), 0));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Define a curvatura: {@code bend[i]} = rotação X (para a frente) e {@code twist[i]} = rotação Z de cada segmento. */
    public void pose(float[] bend, float[] twist) {
        for (int i = 0; i < SEGMENTS; i++) {
            seg[i].xRot = bend[i];
            seg[i].zRot = twist[i];
            seg[i].yRot = 0;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }
}
