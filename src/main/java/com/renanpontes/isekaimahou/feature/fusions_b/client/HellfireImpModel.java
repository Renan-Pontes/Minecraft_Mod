package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.HellfireImpEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modelo do Diabrete Infernal: cabeça grande com chifres, corpo pequeno, braços com garras, pernas curtas,
 * asas de morcego e cauda com ponta de lança. Textura 64×64 em textures/entity/hellfire_imp.png
 * (gerada por tools/gen/fusions_b.py com o mesmo mapa UV abaixo).
 */
public class HellfireImpModel extends HierarchicalModel<HellfireImpEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(IsekaiMahou.id("hellfire_imp"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart tail;

    public HellfireImpModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightLeg = this.body.getChild("right_leg");
        this.leftLeg = this.body.getChild("left_leg");
        this.rightWing = this.body.getChild("right_wing");
        this.leftWing = this.body.getChild("left_wing");
        this.tail = this.body.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 0, 0));
        // Corpo: 5×6×3, a base fica a 5 px do chão (ele flutua)
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-2.5F, -6F, -1.5F, 5, 6, 3), PartPose.offset(0, 19, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3F, -6F, -3F, 6, 6, 6), PartPose.offset(0, -6, 0));
        head.addOrReplaceChild("right_horn", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-0.5F, -3F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(-2F, -5.5F, -0.5F, -0.35F, 0, -0.45F));
        head.addOrReplaceChild("left_horn", CubeListBuilder.create()
                .texOffs(44, 0).addBox(-0.5F, -3F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(2F, -5.5F, -0.5F, -0.35F, 0, 0.45F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-2F, -0.5F, -1F, 2, 5, 2, new CubeDeformation(-0.1F)), PartPose.offset(-2.5F, -5.5F, 0));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 0).addBox(0F, -0.5F, -1F, 2, 5, 2, new CubeDeformation(-0.1F)), PartPose.offset(2.5F, -5.5F, 0));
        body.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(24, 8).addBox(-1F, 0F, -1F, 2, 4, 2, new CubeDeformation(-0.15F)), PartPose.offset(-1.2F, 0, 0));
        body.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(32, 8).addBox(-1F, 0F, -1F, 2, 4, 2, new CubeDeformation(-0.15F)), PartPose.offset(1.2F, 0, 0));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create()
                .texOffs(0, 24).addBox(0F, -3F, 0F, 0, 7, 8), PartPose.offset(-1F, -5F, 1.5F));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create()
                .texOffs(0, 24).mirror().addBox(0F, -3F, 0F, 0, 7, 8).mirror(false), PartPose.offset(1F, -5F, 1.5F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(40, 6).addBox(-0.5F, -0.5F, 0F, 1, 1, 5), PartPose.offset(0, -0.5F, 1.5F));
        tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(52, 6).addBox(-1F, -1F, 0F, 2, 2, 2), PartPose.offset(0, 0, 4.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HellfireImpEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // Flutua subindo e descendo
        body.y += Mth.sin(age * 0.18F) * 0.8F;
        body.xRot = 0.15F + limbSwingAmount * 0.5F;
        // Asas batendo rápido
        float flap = Mth.cos(age * 1.3F) * 0.55F;
        rightWing.yRot = -0.9F - flap;
        leftWing.yRot = 0.9F + flap;
        rightWing.xRot = 0.3F;
        leftWing.xRot = 0.3F;
        // Braços balançando, golpe de garra no ataque
        float sway = Mth.cos(age * 0.12F) * 0.12F;
        rightArm.zRot = 0.35F + sway;
        leftArm.zRot = -0.35F - sway;
        rightArm.xRot = -0.25F;
        leftArm.xRot = -0.25F;
        if (attackTime > 0) {
            float s = Mth.sin(attackTime * (float) Math.PI);
            rightArm.xRot = -2.2F * s - 0.25F;
            leftArm.xRot = -1.6F * s - 0.25F;
        }
        // Pernas penduradas
        rightLeg.xRot = 0.35F + Mth.cos(age * 0.1F) * 0.1F;
        leftLeg.xRot = 0.35F - Mth.cos(age * 0.1F) * 0.1F;
        // Cauda serpenteando
        tail.xRot = -0.65F + Mth.sin(age * 0.2F) * 0.15F;
        tail.yRot = Mth.sin(age * 0.13F) * 0.35F;
    }
}
