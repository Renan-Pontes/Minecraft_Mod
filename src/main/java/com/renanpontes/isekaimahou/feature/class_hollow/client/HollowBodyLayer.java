package com.renanpontes.isekaimahou.feature.class_hollow.client;

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
import net.minecraft.util.Mth;

/**
 * O corpo do Hollow no modelo do jogador (código, sem GeckoLib), por cima de pele e armadura:
 * <ul>
 *   <li><b>Máscara</b> branca de listras rubras pelo rank — Hollow: caveira com dentes; Gillian: bico longo; Adjuchas:
 *   focinho de fera e chifres curtos; Vasto Lorde: chifres longos curvados; Arrancar: só o FRAGMENTO no lado da
 *   mandíbula;</li>
 *   <li>o <b>buraco</b> no peito (frente e costas), em todos os ranks;</li>
 *   <li><b>ossos</b>: ombreiras (Adjuchas e Vasto Lorde) e costelas (Vasto Lorde);</li>
 *   <li><b>Resurrección</b>: Pantera (crista e cauda), Murciélago (asas negras e chifres), Serpiente (capuz e cauda longa).</li>
 * </ul>
 * Textura 64×64 gerada por {@code tools/gen/class_hollow.py}.
 */
public class HollowBodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/hollowfied_body.png");
    private static ModelPart root;

    public HollowBodyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static ModelPart model() {
        if (root != null) return root;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // ---- máscara (presa à cabeça; frente em z = -4, a máscara afastada para não brigar com o capacete)
        r.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -8.6F, -5.7F, 9, 9, 1), PartPose.ZERO);
        r.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 10).addBox(-3.5F, -2.3F, -6.2F, 7, 3, 1), PartPose.ZERO);
        r.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -5F, -8.7F, 2, 2, 3),
                PartPose.ZERO);
        r.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(20, 6).addBox(-2F, -4.2F, -8.4F, 4, 3, 3), PartPose.ZERO);
        for (int s : new int[]{1, -1}) {
            String side = s > 0 ? "l" : "r";
            r.addOrReplaceChild("stub_" + side, CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -0.5F, -3F, 1, 1, 3),
                    PartPose.offsetAndRotation(2.6F * s, -8.2F, -4.6F, 0.9F, -0.3F * s, 0F));
            // chifres do Vasto Lorde: base grossa na testa, para a frente e para cima
            PartDefinition horn = r.addOrReplaceChild("horn_" + side, CubeListBuilder.create().texOffs(44, 0)
                    .addBox(-1F, -1F, -5F, 2, 2, 5), PartPose.offsetAndRotation(3.0F * s, -7.4F, -4.6F, -0.4F, -0.25F * s, 0F));
            PartDefinition mid = horn.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(44, 7)
                    .addBox(-0.5F, -0.5F, -4F, 1, 1, 4), PartPose.offsetAndRotation(0F, 0F, -4.6F, -0.35F, 0.06F * s, 0F));
            mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(54, 7)
                    .addBox(-0.5F, -0.5F, -3F, 1, 1, 3), PartPose.offsetAndRotation(0F, 0F, -3.6F, -0.4F, 0.08F * s, 0F));
            // chifres do Murciélago (para cima, como orelhas de morcego)
            r.addOrReplaceChild("bat_horn_" + side, CubeListBuilder.create().texOffs(24, 34).addBox(-0.5F, -4F, -0.5F, 1, 4, 1),
                    PartPose.offsetAndRotation(3.2F * s, -8F, -1F, -0.2F, 0F, 0.35F * s));
        }
        // fragmento do Arrancar: pedaço de mandíbula no lado direito do rosto
        r.addOrReplaceChild("fragment", CubeListBuilder.create().texOffs(0, 16).addBox(-4.8F, -3.5F, -5.2F, 1, 3, 5)
                .texOffs(12, 16).addBox(-4.6F, -1.5F, -5.4F, 3, 2, 1), PartPose.ZERO);
        // crista da Pantera
        r.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(24, 24).addBox(-1F, -10F, -4F, 2, 2, 6), PartPose.ZERO);
        // capuz da Serpiente (atrás da cabeça)
        r.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(26, 42).addBox(-5F, -9.5F, 4.4F, 10, 8, 1), PartPose.ZERO);
        // ---- peito
        r.addOrReplaceChild("hole_front", CubeListBuilder.create().texOffs(24, 16).addBox(-2F, 3F, -2.4F, 4, 4, 0), PartPose.ZERO);
        r.addOrReplaceChild("hole_back", CubeListBuilder.create().texOffs(32, 16).addBox(-2F, 3F, 2.4F, 4, 4, 0), PartPose.ZERO);
        r.addOrReplaceChild("ribs", CubeListBuilder.create().texOffs(0, 34).addBox(-4F, 1F, -2.9F, 8, 6, 1), PartPose.ZERO);
        // ombreiras (presas aos braços)
        r.addOrReplaceChild("pauldron_r", CubeListBuilder.create().texOffs(0, 24).addBox(-4F, -3F, -3F, 5, 3, 6), PartPose.ZERO);
        r.addOrReplaceChild("pauldron_l", CubeListBuilder.create().texOffs(0, 24).mirror().addBox(-1F, -3F, -3F, 5, 3, 6), PartPose.ZERO);
        // asas do Murciélago (nas costas)
        r.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 42).addBox(0F, -2F, 0F, 12, 10, 0),
                PartPose.offsetAndRotation(1F, 1F, 2.6F, 0F, -0.45F, -0.25F));
        r.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 42).mirror().addBox(-12F, -2F, 0F, 12, 10, 0),
                PartPose.offsetAndRotation(-1F, 1F, 2.6F, 0F, 0.45F, 0.25F));
        // caudas (nas costas, descendo)
        PartDefinition tail = r.addOrReplaceChild("tail_p", CubeListBuilder.create().texOffs(40, 24).addBox(-0.5F, -0.5F, 0F, 1, 1, 6),
                PartPose.offsetAndRotation(0F, 11F, 2F, -0.6F, 0F, 0F));
        tail.addOrReplaceChild("tail_p2", CubeListBuilder.create().texOffs(40, 24).addBox(-0.5F, -0.5F, 0F, 1, 1, 6),
                PartPose.offsetAndRotation(0F, 0F, 5.6F, 0.5F, 0F, 0F));
        PartDefinition st = r.addOrReplaceChild("tail_s", CubeListBuilder.create().texOffs(40, 33).addBox(-1.5F, -1.5F, 0F, 3, 3, 6),
                PartPose.offsetAndRotation(0F, 11F, 2F, -0.9F, 0F, 0F));
        PartDefinition st2 = st.addOrReplaceChild("tail_s2", CubeListBuilder.create().texOffs(48, 52).addBox(-1F, -1F, 0F, 2, 2, 6),
                PartPose.offsetAndRotation(0F, 0F, 5.6F, 0.6F, 0F, 0F));
        st2.addOrReplaceChild("tail_s3", CubeListBuilder.create().texOffs(24, 52).addBox(-0.5F, -0.5F, 0F, 1, 1, 5),
                PartPose.offsetAndRotation(0F, 0F, 5.6F, 0.4F, 0F, 0F));
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return root;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        HollowLooks.Look look = HollowLooks.of(p);
        if (look == null || p.isInvisible()) return;
        ModelPart m = model();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        int ov = OverlayTexture.NO_OVERLAY;
        int rank = look.rank();
        boolean released = look.released();
        int rel = look.release();
        PlayerModel<AbstractClientPlayer> pm = getParentModel();

        // ---- cabeça
        ps.pushPose();
        pm.head.translateAndRotate(ps);
        if (rank < 4) {
            m.getChild("mask").render(ps, vc, light, ov);
            m.getChild("jaw").render(ps, vc, light, ov);
            if (rank == 1) m.getChild("beak").render(ps, vc, light, ov);
            if (rank == 2) {
                m.getChild("snout").render(ps, vc, light, ov);
                m.getChild("stub_l").render(ps, vc, light, ov);
                m.getChild("stub_r").render(ps, vc, light, ov);
            }
            if (rank == 3) {
                m.getChild("horn_l").render(ps, vc, light, ov);
                m.getChild("horn_r").render(ps, vc, light, ov);
            }
        } else {
            m.getChild("fragment").render(ps, vc, light, ov);
        }
        if (released) {
            switch (rel) {
                case 1 -> m.getChild("crest").render(ps, vc, light, ov);
                case 2 -> {
                    m.getChild("bat_horn_l").render(ps, vc, light, ov);
                    m.getChild("bat_horn_r").render(ps, vc, light, ov);
                }
                case 3 -> m.getChild("hood").render(ps, vc, light, ov);
                default -> { }
            }
        }
        ps.popPose();

        // ---- tronco
        ps.pushPose();
        pm.body.translateAndRotate(ps);
        m.getChild("hole_front").render(ps, vc, light, ov);
        m.getChild("hole_back").render(ps, vc, light, ov);
        if (rank == 3) m.getChild("ribs").render(ps, vc, light, ov);
        if (released && rel == 2) {
            // as asas batem devagar (rápido voando)
            float speed = p.getAbilities().flying ? 0.6F : 0.15F;
            float flap = Mth.sin(ageInTicks * speed) * (p.getAbilities().flying ? 0.5F : 0.12F);
            ModelPart wl = m.getChild("wing_l"), wr = m.getChild("wing_r");
            wl.yRot = -0.45F - flap;
            wr.yRot = 0.45F + flap;
            wl.render(ps, vc, light, ov);
            wr.render(ps, vc, light, ov);
        }
        if (released && (rel == 1 || rel == 3)) {
            ModelPart tail = m.getChild(rel == 1 ? "tail_p" : "tail_s");
            float sway = Mth.sin(ageInTicks * 0.12F + limbSwing * 0.4F) * 0.25F;
            tail.yRot = sway;
            tail.render(ps, vc, light, ov);
        }
        ps.popPose();

        // ---- ombreiras de osso (Adjuchas e Vasto Lorde)
        if (rank == 2 || rank == 3) {
            ps.pushPose();
            pm.rightArm.translateAndRotate(ps);
            m.getChild("pauldron_r").render(ps, vc, light, ov);
            ps.popPose();
            ps.pushPose();
            pm.leftArm.translateAndRotate(ps);
            m.getChild("pauldron_l").render(ps, vc, light, ov);
            ps.popPose();
        }
    }
}
