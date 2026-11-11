package com.renanpontes.isekaimahou.feature.forms_b.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellRuneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Runa: selo no chão quase invisível. O dono vê a runa nítida (pulsando); os outros, só um vulto.
 */
public class RuneRenderer extends EntityRenderer<SpellRuneEntity> {
    private static final ResourceLocation RUNE = QuadFx.tex("spell_rune");

    public RuneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(SpellRuneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        UUID owner = e.ownerId();
        Minecraft mc = Minecraft.getInstance();
        boolean mine = owner != null && mc.player != null && owner.equals(mc.player.getUUID());
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.12F);
        float alpha = (mine ? 0.55F : 0.1F) * pulse * e.fade(pt);
        if (!e.armed()) alpha *= 0.6F;
        ps.pushPose();
        ps.translate(0, 0.02, 0);
        ps.mulPose(Axis.YP.rotationDegrees(t * 0.4F));
        QuadFx.flat(ps, QuadFx.glow(buffers, RUNE), 0.85F, e.color(), alpha);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellRuneEntity e) {
        return RUNE;
    }
}
