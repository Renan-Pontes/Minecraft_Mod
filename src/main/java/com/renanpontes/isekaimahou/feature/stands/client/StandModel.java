package com.renanpontes.isekaimahou.feature.stands.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.stands.Stand;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * SÓ CLIENTE. O corpo do stand: o humanoide padrão com peças extras na cabeça/peito que cada stand liga ou desliga
 * ({@link Stand#has}): orelhas de gato (Killer Queen), orelhas-chifres compridas (D4C), crista/rosto na testa (King
 * Crimson, Requiem), cabelo espetado (Star Platinum), elmo (Ball Breaker, Tusk Ato 4) e a estrela do Tusk.
 */
public class StandModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(IsekaiMahou.id("stand"), "main");

    private final ModelPart ears, horns, crest, hair, helmet, star;

    public StandModel(ModelPart root) {
        super(root);
        this.ears = head.getChild("ears");
        this.horns = head.getChild("horns");
        this.crest = head.getChild("crest");
        this.hair = head.getChild("hair");
        this.helmet = head.getChild("helmet");
        this.star = body.getChild("star");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(40, 32)
                .addBox(-4F, -11F, -1F, 2F, 3F, 1F).addBox(2F, -11F, -1F, 2F, 3F, 1F), PartPose.ZERO);
        head.addOrReplaceChild("horns", CubeListBuilder.create().texOffs(48, 32)
                .addBox(-4.5F, -15F, -0.5F, 1F, 7F, 1F).addBox(3.5F, -15F, -0.5F, 1F, 7F, 1F), PartPose.ZERO);
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(40, 36)
                .addBox(-1.5F, -9.5F, -4.6F, 3F, 3F, 1F).addBox(-0.5F, -10.5F, -3F, 1F, 2F, 6F), PartPose.ZERO);
        head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.5F, -10F, -2F, 9F, 2F, 7F).addBox(-3F, -11.5F, 0F, 6F, 2F, 5F).addBox(-1.5F, -6F, 4F, 3F, 6F, 2F),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(0, 41)
                .addBox(-4.5F, -8.6F, -4.5F, 9F, 4F, 9F, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("star", CubeListBuilder.create().texOffs(56, 32)
                .addBox(-1.5F, 2F, -2.6F, 3F, 3F, 1F), PartPose.rotation(0F, 0F, Mth.PI / 4F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Liga as peças do stand e zera a pose. */
    public void setup(Stand stand) {
        ears.visible = stand.has(Stand.EARS);
        horns.visible = stand.has(Stand.HORNS);
        crest.visible = stand.has(Stand.CREST);
        hair.visible = stand.has(Stand.HAIR);
        helmet.visible = stand.has(Stand.HELMET);
        star.visible = stand.has(Stand.TUSK);
        hat.visible = false;
        young = false;
        crouching = false;
        riding = false;
        for (ModelPart p : new ModelPart[] {head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            p.xRot = 0F;
            p.yRot = 0F;
            p.zRot = 0F;
        }
    }
}
