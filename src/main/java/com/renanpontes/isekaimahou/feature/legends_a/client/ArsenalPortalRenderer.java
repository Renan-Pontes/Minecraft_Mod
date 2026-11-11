package com.renanpontes.isekaimahou.feature.legends_a.client;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ArsenalPortalEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Portal dourado: ondulação girando, aro de runas girando ao contrário, brilho aditivo e a arma saindo dele aos
 * poucos (cresce e avança até ser disparada). Preso ao conjurador, é desenhado na posição INTERPOLADA dele (sem atraso).
 */
public class ArsenalPortalRenderer extends EntityRenderer<ArsenalPortalEntity> {
    private final ItemRenderer items;

    public ArsenalPortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(ArsenalPortalEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return e.shouldRender(camX, camY, camZ) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(ArsenalPortalEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = e.openness(pt);
        if (open <= 0.01F) return;
        float age = e.tickCount + pt;
        int rgb = e.color();
        float size = e.size() * easeOut(open);

        ps.pushPose();
        Entity owner = e.owner();
        if (e.bound() && owner != null) {
            // Recalcula a posição a partir do dono interpolado: o arco acompanha o jogador sem tremer
            Vec3 base = owner.getPosition(pt);
            float oy = Mth.rotLerp(pt, owner.yRotO, owner.getYRot());
            Vec3 want = ArsenalPortalEntity.slotPos(base, oy, e.slot(), e.slots());
            Vec3 at = e.getPosition(pt);
            ps.translate(want.x - at.x, want.y - at.y, want.z - at.z);
        }
        Vec3 aim = e.aim();
        ps.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) aim.x, (float) aim.y, (float) aim.z));

        // Brilho atrás
        LegendQuads.disc(ps, LegendQuads.additive(buffers, LegendQuads.GLOW), size * 1.9F, 0, rgb, 0.45F * open, true);
        // Ondulação dourada
        ps.pushPose();
        ps.translate(0, 0, 0.01F);
        float pulse = 1.0F + 0.05F * Mth.sin(age * 0.5F);
        LegendQuads.disc(ps, LegendQuads.glow(buffers, LegendQuads.PORTAL), size * pulse, age * 2.5F, LegendQuads.lighten(rgb, 0.25F),
                0.95F * open, false);
        ps.popPose();
        // Aro de runas
        ps.pushPose();
        ps.translate(0, 0, 0.02F);
        LegendQuads.disc(ps, LegendQuads.glow(buffers, LegendQuads.PORTAL_RIM), size * 1.3F, -age * 4.0F, rgb, open, false);
        ps.popPose();

        // A próxima arma saindo do portal
        ItemStack stack = e.item();
        float charge = e.chargeProgress(pt);
        if (!stack.isEmpty() && charge > 0.05F && open > 0.9F) {
            float t = (charge - 0.05F) / 0.95F;
            float s = 1.6F * (0.35F + 0.65F * t);
            ps.pushPose();
            ps.translate(0, 0, 0.15F + 0.55F * t);
            // Ponta (+Y do sprite girado) para +Z = para onde o portal mira
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.mulPose(Axis.YP.rotationDegrees(age * 6));
            ps.mulPose(Axis.ZP.rotationDegrees(-45));
            ps.scale(s, s, s);
            items.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers,
                    e.level(), e.getId());
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    @Override
    public ResourceLocation getTextureLocation(ArsenalPortalEntity entity) {
        return LegendQuads.PORTAL;
    }
}
