package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A máscara do Vazio no jogador (terceira pessoa, inventário e outros jogadores):
 * <ul>
 *   <li><b>Máscara do Vazio</b>: a máscara branca de caveira com listras rubras sobre o rosto (por cima do capacete);</li>
 *   <li><b>Vasto Lorde</b>: a máscara completa com dois CHIFRES longos saindo da testa e curvando para a frente, e o
 *   BURACO no peito (na frente e nas costas).</li>
 * </ul>
 * Modelo 64×32 em pixels do jogador (cabeça de −8 a 0 em y, frente em z = −4).
 */
public class HollowMaskLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation MASK = IsekaiMahou.id("textures/entity/shinigami_hollow_mask.png");
    private static final ResourceLocation VASTO = IsekaiMahou.id("textures/entity/shinigami_vasto_mask.png");

    private static ModelPart root;

    public HollowMaskLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static ModelPart model() {
        if (root != null) return root;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // a máscara: uma placa 9×9 na frente do rosto, afastada do capacete
        r.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -8.6F, -5.7F, 9, 9, 1), PartPose.ZERO);
        // chifres do Vasto Lorde: base grossa na testa, para a frente e para cima, ponta fina
        for (int s : new int[]{1, -1}) {
            String side = s > 0 ? "l" : "r";
            PartDefinition horn = r.addOrReplaceChild("horn_" + side, CubeListBuilder.create().texOffs(32, 0)
                    .addBox(-1F, -1F, -5F, 2, 2, 5), PartPose.offsetAndRotation(3.0F * s, -7.2F, -4.4F, -0.35F, -0.22F * s, 0F));
            PartDefinition mid = horn.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(32, 8)
                    .addBox(-0.5F, -0.5F, -4F, 1, 1, 4), PartPose.offsetAndRotation(0F, 0F, -4.6F, -0.3F, 0.06F * s, 0F));
            mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(32, 14)
                    .addBox(-0.5F, -0.5F, -3F, 1, 1, 3), PartPose.offsetAndRotation(0F, 0F, -3.6F, -0.35F, 0.08F * s, 0F));
        }
        // o buraco no peito (frente e costas)
        r.addOrReplaceChild("hole_front", CubeListBuilder.create().texOffs(16, 16).addBox(-2F, 3F, -2.35F, 4, 4, 0), PartPose.ZERO);
        r.addOrReplaceChild("hole_back", CubeListBuilder.create().texOffs(24, 16).addBox(-2F, 3F, 2.35F, 4, 4, 0), PartPose.ZERO);
        root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        return root;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int kind = HollowClient.kind(p);
        if (kind == 0 || p.isInvisible()) return;
        ModelPart m = model();
        boolean vasto = kind == 2;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(vasto ? VASTO : MASK));
        ps.pushPose();
        getParentModel().head.translateAndRotate(ps);
        m.getChild("mask").render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        if (vasto) {
            m.getChild("horn_l").render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            m.getChild("horn_r").render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        }
        ps.popPose();
        if (vasto) {
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            m.getChild("hole_front").render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            m.getChild("hole_back").render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
