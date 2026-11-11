package com.renanpontes.isekaimahou.feature.afterlife.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.afterlife.entity.ReaperEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Ceifador: capuz fundo, manto rasgado flutuando (sem pernas), braços estendidos para a alma e uma foice na mão
 * direita. UV em 64×64 (ver tools/gen/afterlife.py).
 */
public class ReaperModel extends HierarchicalModel<ReaperEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(IsekaiMahou.id("afterlife_reaper"), "main");

    private final ModelPart root;
    private final ModelPart hood;
    private final ModelPart skirt;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public ReaperModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.hood = root.getChild("hood");
        this.skirt = root.getChild("skirt");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -9F, -4.5F, 9, 9, 9), PartPose.offset(0F, 1F, 0F));
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 18).addBox(-4.5F, 0F, -2.5F, 9, 12, 5), PartPose.offset(0F, 1F, 0F));
        r.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 35).addBox(-5.5F, 0F, -3.5F, 11, 10, 7), PartPose.offset(0F, 12F, 0F));
        PartDefinition right = r.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 18).addBox(-1.5F, -1.5F, -1.5F, 3, 12, 3),
                PartPose.offsetAndRotation(-6F, 3F, 0F, -1.1F, 0F, 0.1F));
        r.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 18).mirror().addBox(-1.5F, -1.5F, -1.5F, 3, 12, 3),
                PartPose.offsetAndRotation(6F, 3F, 0F, -1.3F, 0F, -0.1F));
        // foice: cabo preso na mão, "em pé" apesar do braço esticado
        PartDefinition scythe = right.addOrReplaceChild("scythe", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -16F, -0.5F, 1, 26, 1),
                PartPose.offsetAndRotation(0F, 10F, 0F, 1.1F, 0F, 0F));
        scythe.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 54).addBox(0F, -1F, -0.5F, 13, 2, 1)
                .texOffs(30, 54).addBox(11F, 1F, -0.5F, 3, 2, 1), PartPose.offsetAndRotation(0F, -15F, 0F, 0F, 0F, 0.18F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(ReaperEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float sway = Mth.sin(age * 0.12F);
        hood.xRot = 0.18F + Mth.sin(age * 0.05F) * 0.06F;
        skirt.xRot = 0.12F + sway * 0.08F;
        skirt.zRot = Mth.cos(age * 0.09F) * 0.05F;
        rightArm.xRot = -1.1F + sway * 0.12F;
        leftArm.xRot = -1.3F - sway * 0.15F;
        leftArm.zRot = -0.1F - Mth.cos(age * 0.15F) * 0.08F;
    }
}
