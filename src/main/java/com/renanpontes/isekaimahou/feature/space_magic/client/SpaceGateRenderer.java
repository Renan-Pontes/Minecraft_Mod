package com.renanpontes.isekaimahou.feature.space_magic.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.space_magic.entity.SpaceGateEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Portal Espacial: um anel oval vertical com o céu estrelado do vazio dentro ({@link RenderType#endGateway()}), borda
 * brilhante na cor da magia, um halo que pulsa e runas que giram em volta. Abre crescendo e fecha encolhendo.
 */
public class SpaceGateRenderer extends EntityRenderer<SpaceGateEntity> {
    private static final int SEG = 40;

    public SpaceGateRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SpaceGateEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = e.openness(pt);
        if (open <= 0F) return;
        float t = e.tickCount + pt;
        Vec3 side = e.side();
        Vec3 up = new Vec3(0, 1, 0);
        double rx = SpaceGateEntity.HALF_WIDTH * open, ry = SpaceGateEntity.HEIGHT * 0.5 * (0.35 + 0.65 * open);
        Vec3 c = new Vec3(0, SpaceGateEntity.HEIGHT * 0.5, 0);
        int rgb = e.color();
        Matrix4f m = ps.last().pose();

        // Interior: o vazio estrelado
        VertexConsumer space = buffers.getBuffer(RenderType.endGateway());
        for (int i = 0; i < SEG; i++) {
            Vec3 a = rim(c, side, up, rx, ry, i, t, 0.97);
            Vec3 b = rim(c, side, up, rx, ry, i + 1, t, 0.97);
            spaceQuad(space, m, c, a, b, c);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.25F);
        int edge = argb(lighten(rgb, 0.55F), 0.95F * open), core = argb(rgb, 0.8F * open), none = argb(rgb, 0F);
        for (int i = 0; i < SEG; i++) {
            // Borda brilhante (fina, por dentro e por fora) e halo largo que some para fora
            Vec3 in0 = rim(c, side, up, rx, ry, i, t, 0.86), in1 = rim(c, side, up, rx, ry, i + 1, t, 0.86);
            Vec3 r0 = rim(c, side, up, rx, ry, i, t, 1.0), r1 = rim(c, side, up, rx, ry, i + 1, t, 1.0);
            Vec3 out0 = rim(c, side, up, rx, ry, i, t, 1.08), out1 = rim(c, side, up, rx, ry, i + 1, t, 1.08);
            Vec3 far0 = rim(c, side, up, rx, ry, i, t, 1.0 + 0.35 * pulse), far1 = rim(c, side, up, rx, ry, i + 1, t, 1.0 + 0.35 * pulse);
            quad(glow, m, in0, none, r0, edge, r1, edge, in1, none);
            quad(glow, m, r0, edge, out0, core, out1, core, r1, edge);
            quad(glow, m, out0, argb(rgb, 0.35F * open * pulse), far0, none, far1, none, out1, argb(rgb, 0.35F * open * pulse));
        }
        // Espiral que gira para dentro (o "redemoinho" do espaço)
        int arms = 3;
        for (int a = 0; a < arms; a++) {
            Vec3 prev = null;
            for (int k = 0; k <= 16; k++) {
                double f = 1.0 - k / 16.0;
                double ang = t * 0.12 + a * Math.PI * 2 / arms + k * 0.32;
                Vec3 p = c.add(side.scale(Math.cos(ang) * rx * f * 0.92)).add(up.scale(Math.sin(ang) * ry * f * 0.92));
                if (prev != null) ribbon(glow, m, prev, p, side, up, 0.035 * f + 0.01, argb(lighten(rgb, 0.4F), 0.55F * open * (float) f));
                prev = p;
            }
        }
        // Runas: tracinhos que giram em volta da borda
        int runes = 12;
        for (int i = 0; i < runes; i++) {
            double ang = -t * 0.05 + i * Math.PI * 2 / runes;
            Vec3 dir = side.scale(Math.cos(ang) * rx).add(up.scale(Math.sin(ang) * ry));
            Vec3 p0 = c.add(dir.scale(1.14)), p1 = c.add(dir.scale(1.24));
            ribbon(glow, m, p0, p1, side, up, 0.05, argb(0xFFF2C8, 0.8F * open));
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** Ponto na borda (índice i de SEG) com leve ondulação. */
    private static Vec3 rim(Vec3 c, Vec3 side, Vec3 up, double rx, double ry, int i, float t, double f) {
        double ang = Math.PI * 2 * i / SEG;
        double wob = 1.0 + 0.025 * Math.sin(ang * 5 + t * 0.3);
        return c.add(side.scale(Math.cos(ang) * rx * f * wob)).add(up.scale(Math.sin(ang) * ry * f * wob));
    }

    /** Fita entre dois pontos no plano do portal (largura perpendicular ao segmento, dentro do plano). */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, Vec3 up, double hw, int col) {
        Vec3 d = b.subtract(a);
        Vec3 n = side.cross(up);
        Vec3 w = d.cross(n);
        if (w.lengthSqr() < 1.0E-8) return;
        w = w.normalize().scale(hw);
        int fade = col & 0x00FFFFFF;
        quad(vc, m, a.subtract(w), fade, a, col, b, col, b.subtract(w), fade);
        quad(vc, m, a, col, a.add(w), fade, b.add(w), fade, b, col);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int c) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255);
    }

    /** Quad colorido dos dois lados. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc, Vec3 d, int cd) {
        v(vc, m, a, ca);
        v(vc, m, b, cb);
        v(vc, m, c, cc);
        v(vc, m, d, cd);
        v(vc, m, d, cd);
        v(vc, m, c, cc);
        v(vc, m, b, cb);
        v(vc, m, a, ca);
    }

    private static void spaceQuad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        Vec3[] q = {a, b, c, d, d, c, b, a};
        for (Vec3 p : q) vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z);
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean shouldRender(SpaceGateEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(1.5));
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceGateEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
