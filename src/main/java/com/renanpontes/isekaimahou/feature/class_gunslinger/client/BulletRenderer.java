package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bala de mana: um traçante de luz (fita voltada para a câmera, com halo colorido e núcleo claro afinando no rastro)
 * e uma estrela no bico. Desenhado com {@link RenderType#lightning()} (aditivo, sem textura): brilha no escuro.
 * As faíscas do rastro saem do tick da própria bala (cliente).
 */
public class BulletRenderer extends EntityRenderer<ManaBulletEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/mana_bullet.png");

    public BulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(ManaBulletEntity bullet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 v = bullet.getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-3) return;
        Vec3 dir = v.scale(1.0 / speed);
        float size = bullet.size();
        byte style = bullet.style();
        int rgb = bullet.color();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        // rastro mais longo quanto mais rápida; a bala do Tiro Carregado é mais grossa
        float len = (float) Math.min(3.2, speed * 0.75) * (style == ManaBulletEntity.STYLE_PELLET ? 0.6F : 1.0F);
        float w = 0.045F * size;

        pose.pushPose();
        pose.translate(0, bullet.getBbHeight() * 0.5, 0);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 here = bullet.getPosition(partialTick).add(0, bullet.getBbHeight() * 0.5, 0);
        Vec3 toCam = cam.subtract(here);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 tail = dir.scale(-len);

        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // halo (cor da magia) e núcleo (quase branco)
        ribbon(vc, m, Vec3.ZERO, tail, side, w * 2.6F, r, g, b, 0.45F);
        ribbon(vc, m, Vec3.ZERO, tail.scale(0.7), side, w, 0.5F + r * 0.5F, 0.5F + g * 0.5F, 0.5F + b * 0.5F, 0.95F);
        // estrela no bico (duas fitas cruzadas curtas)
        float s = w * (style == ManaBulletEntity.STYLE_HEAVY ? 5F : 3.2F);
        Vec3 up = side.cross(dir).normalize();
        ribbon(vc, m, dir.scale(s), dir.scale(-s), side, s * 0.35F, r, g, b, 0.7F);
        ribbon(vc, m, up.scale(s), up.scale(-s), dir, s * 0.35F, r, g, b, 0.6F);
        if (style == ManaBulletEntity.STYLE_SPIRIT) {
            // Dedo Espiritual: esfera de luz — raios girando em volta do núcleo, halo largo e miolo branco
            float orb = 0.32F * size / 3.2F;
            float spin = (bullet.tickCount + partialTick) * 0.6F;
            for (int i = 0; i < 6; i++) {
                double a = spin + i * Math.PI / 3;
                Vec3 ray = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                ribbon(vc, m, ray.scale(orb * 1.6F), ray.scale(-orb * 1.6F), dir, orb * 0.5F, r, g, b, 0.55F);
            }
            ribbon(vc, m, side.scale(orb), side.scale(-orb), up, orb, 0.9F, 0.97F, 1F, 0.9F);
            ribbon(vc, m, up.scale(orb), up.scale(-orb), side, orb, 0.9F, 0.97F, 1F, 0.9F);
        }
        pose.popPose();

        super.render(bullet, yaw, partialTick, pose, buffers, light);
    }

    /** Fita de {@code a} (cheia) até {@code b} (some), com largura {@code w} no eixo {@code side}. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float w, float r, float g, float bl, float alpha) {
        Vec3 o = side.scale(w);
        vertex(vc, m, a.add(o), r, g, bl, alpha);
        vertex(vc, m, a.subtract(o), r, g, bl, alpha);
        vertex(vc, m, b.subtract(o.scale(0.2)), r, g, bl, 0F);
        vertex(vc, m, b.add(o.scale(0.2)), r, g, bl, 0F);
        // verso (a fita é vista dos dois lados)
        vertex(vc, m, b.add(o.scale(0.2)), r, g, bl, 0F);
        vertex(vc, m, b.subtract(o.scale(0.2)), r, g, bl, 0F);
        vertex(vc, m, a.subtract(o), r, g, bl, alpha);
        vertex(vc, m, a.add(o), r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(ManaBulletEntity entity) {
        return TEXTURE;
    }
}
