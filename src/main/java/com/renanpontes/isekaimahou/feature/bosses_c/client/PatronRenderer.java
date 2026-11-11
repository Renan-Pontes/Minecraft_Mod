package com.renanpontes.isekaimahou.feature.bosses_c.client;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.ImprisonedPatron;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Patrono Aprisionado: além do modelo, desenha as correntes de verdade (modelo do bloco de corrente do vanilla)
 * saindo das algemas até as estacas cravadas no chão. Correntes partidas ficam penduradas nas algemas.
 */
public class PatronRenderer extends BossGeoRenderer<ImprisonedPatron> {
    /** Osso de cada corrente (mesma ordem dos bits de {@link ImprisonedPatron#chainMask()}). */
    private static final String[] CUFFS = {"right_cuff", "left_cuff", "right_ankle", "left_ankle"};

    public PatronRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "imprisoned_patron", true, 1.0F, 1.1F);
    }

    @Override
    public void preRender(PoseStack poseStack, ImprisonedPatron animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // Guarda a posição das algemas neste quadro para ligar as correntes nelas.
        for (String cuff : CUFFS) model.getBone(cuff).ifPresent(b -> b.setTrackingMatrices(true));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** As correntes vão longe do corpo: continua desenhando enquanto as estacas estiverem na tela. */
    @Override
    public boolean shouldRender(ImprisonedPatron patron, Frustum frustum, double camX, double camY, double camZ) {
        return super.shouldRender(patron, frustum, camX, camY, camZ) || frustum.isVisible(patron.getBoundingBox().inflate(6.0, 1.0, 6.0));
    }

    @Override
    public void render(ImprisonedPatron patron, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(patron, entityYaw, partialTick, poseStack, buffers, light);
        // Antes de o servidor mandar o ponto de origem, só as algemas aparecem.
        boolean homeKnown = !patron.home().equals(BlockPos.ZERO);
        Vec3 origin = new Vec3(Mth.lerp(partialTick, patron.xo, patron.getX()), Mth.lerp(partialTick, patron.yo, patron.getY()),
                Mth.lerp(partialTick, patron.zo, patron.getZ()));
        float bodyYaw = Mth.rotLerp(partialTick, patron.yBodyRotO, patron.yBodyRot);
        int mask = patron.chainMask();
        for (int i = 0; i < CUFFS.length; i++) {
            Vec3 cuff = cuffPos(i, bodyYaw);
            boolean intact = homeKnown && (mask & (1 << i)) != 0;
            Vec3 end;
            if (intact) {
                end = patron.anchor(i).subtract(origin);
                renderStake(poseStack, buffers, end, lightAt(patron, origin.add(end)));
            } else {
                // Pedaço partido pendurado.
                end = cuff.add(0, -0.9, 0).add(Math.sin(patron.tickCount * 0.1 + i) * 0.08, 0, 0);
            }
            renderChain(poseStack, buffers, cuff, end, lightAt(patron, origin.add(cuff.add(end).scale(0.5))));
        }
    }

    /** Posição da algema (relativa à entidade); se o osso ainda não foi medido, estima pelo giro do corpo. */
    private Vec3 cuffPos(int i, float bodyYaw) {
        GeoBone bone = getGeoModel().getBone(CUFFS[i]).orElse(null);
        if (bone != null && bone.isTrackingMatrices()) {
            Vector3d p = bone.getLocalPosition();
            if (p.lengthSquared() > 1.0E-6) return new Vec3(p.x, p.y, p.z);
        }
        double side = (i % 2 == 0 ? -1 : 1) * (i < 2 ? 0.7 : 0.25);
        double height = i < 2 ? 0.95 : 0.37;
        double rad = Math.toRadians(bodyYaw);
        return new Vec3(-Math.cos(rad) * side, height, -Math.sin(rad) * side);
    }

    private static int lightAt(ImprisonedPatron patron, Vec3 world) {
        return LevelRenderer.getLightColor(patron.level(), BlockPos.containing(world));
    }

    /** Corrente reta de {@code from} até {@code to} feita de pedaços do bloco de corrente, alternando 90° a cada elo. */
    private static void renderChain(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, int light) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.05) return;
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        Vector3f dir = new Vector3f((float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        int whole = (int) Math.floor(len);
        float rest = (float) (len - whole);
        for (int k = 0; k <= whole; k++) {
            float h = k < whole ? 1.0F : rest;
            if (h <= 0.01F) break;
            pose.pushPose();
            pose.translate(0, k, 0);
            if (k % 2 == 1) pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.scale(1.0F, h, 1.0F);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(chain, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Estaca de pedra negra onde a corrente está presa. */
    private static void renderStake(PoseStack pose, MultiBufferSource buffers, Vec3 at, int light) {
        pose.pushPose();
        pose.translate(at.x, at.y - 0.15, at.z);
        pose.scale(0.45F, 0.55F, 0.45F);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), pose, buffers,
                light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
