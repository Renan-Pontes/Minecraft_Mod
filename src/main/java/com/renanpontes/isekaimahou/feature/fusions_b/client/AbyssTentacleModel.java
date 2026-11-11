package com.renanpontes.isekaimahou.feature.fusions_b.client;

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
import net.minecraft.util.Mth;

/**
 * Um tentáculo do Abismo: 6 segmentos encadeados que afinam até a ponta. Cada segmento gira em relação ao
 * anterior, então pequenas rotações viram uma curva suave (enrolando no alvo). Ventosas no lado da frente
 * (-Z), que o renderer vira para a vítima. Textura 64×32: textures/entity/abyss_tentacle.png.
 */
public class AbyssTentacleModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(IsekaiMahou.id("abyss_tentacle"), "main");
    public static final int SEGMENTS = 6;

    private final ModelPart root;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];

    public AbyssTentacleModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        ModelPart p = root;
        for (int i = 0; i < SEGMENTS; i++) {
            p = p.getChild("seg" + i);
            segments[i] = p;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        int[][] tex = {{0, 0}, {16, 0}, {32, 0}, {44, 0}, {0, 12}, {8, 12}};
        int[] size = {4, 4, 3, 3, 2, 2};
        int[] len = {6, 6, 6, 5, 5, 4};
        float[] shrink = {0F, -0.3F, 0F, -0.25F, 0F, -0.2F};
        float y = 24;
        for (int i = 0; i < SEGMENTS; i++) {
            float h = size[i] / 2F;
            p = p.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(tex[i][0], tex[i][1])
                    .addBox(-h, -len[i], -h, size[i], len[i], size[i], new CubeDeformation(shrink[i])),
                    PartPose.offset(0, i == 0 ? y : -(len[i - 1] - 0.3F), 0));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /**
     * Pose do tentáculo: {@code curl} é quanto ele se inclina para a frente (para a vítima), {@code age} anima a
     * ondulação e {@code seed} desencontra os tentáculos.
     */
    public void pose(float age, float curl, float seed) {
        for (int i = 0; i < SEGMENTS; i++) {
            float k = (i + 1) / (float) SEGMENTS;
            segments[i].xRot = curl * (0.12F + 0.32F * k) + Mth.sin(age * 0.22F + i * 0.8F + seed) * 0.12F * k;
            segments[i].zRot = Mth.cos(age * 0.17F + i * 0.6F + seed * 1.7F) * 0.14F * k;
            segments[i].yRot = 0;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }
}
