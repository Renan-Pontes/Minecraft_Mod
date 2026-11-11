package com.renanpontes.isekaimahou.feature.class_smith.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer da Lâmina Forjada (item com {@code builtin/entity}): desenha as caixas de {@link BladeGeometry} com o atlas
 * de peças (tingido pela cor do material/acabamento; runas e gemas brilham no escuro). Na mão/chão usa as
 * transformações do modelo JSON (a mão fica em y = 8); no inventário e no quadro a arma é ENQUADRADA no espaço
 * (adaga grande, montante e lança reduzidos para caber na diagonal). Geometria em cache por valor das partes.
 */
public class ForgedBladeRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/item/class_smith/forge_parts.png");
    private static final int CACHE_SIZE = 96;
    private static final float ATLAS = 64F;

    private final Map<ForgedBlade, BladeGeometry> cache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ForgedBlade, BladeGeometry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public ForgedBladeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public BladeGeometry geometry(ForgedBlade blade) {
        return cache.computeIfAbsent(blade, BladeGeometry::build);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BladeGeometry g = geometry(ForgedBladeItem.get(stack));
        ps.pushPose();
        ps.translate(0.5F, 0.5F, 0.5F);
        ps.scale(1 / 16F, 1 / 16F, 1 / 16F);
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED) {
            // enquadra a arma inteira na diagonal do espaço do inventário
            float h = g.maxY - g.minY, w = g.maxX - g.minX;
            float s = Math.min(1.3F, 21.5F / Math.max(1F, h + w * 0.6F));
            ps.scale(s, s, s);
            ps.translate(-(g.minX + g.maxX) / 2F, -(g.minY + g.maxY) / 2F, -8F);
        } else {
            ps.translate(-8F, -8F, -8F);
        }
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil());
        draw(ps, vc, g, light, overlay);
        ps.popPose();
    }

    /** Desenha todas as caixas (usado também pela prévia da Bigorna). */
    public static void draw(PoseStack ps, VertexConsumer vc, BladeGeometry g, int light, int overlay) {
        for (BladeGeometry.Box b : g.boxes) {
            int lt = b.glow() ? LightTexture.FULL_BRIGHT : light;
            if (b.rotZ() != 0F) {
                ps.pushPose();
                ps.translate(b.px(), b.py(), b.pz());
                ps.mulPose(Axis.ZP.rotationDegrees(b.rotZ()));
                ps.translate(-b.px(), -b.py(), -b.pz());
                box(ps, vc, b, lt, overlay);
                ps.popPose();
            } else {
                box(ps, vc, b, lt, overlay);
            }
        }
    }

    private static void box(PoseStack ps, VertexConsumer vc, BladeGeometry.Box b, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float x0 = b.x0(), y0 = b.y0(), z0 = b.z0(), x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
        int r = (b.rgb() >> 16) & 255, g = (b.rgb() >> 8) & 255, bl = b.rgb() & 255;
        BladeGeometry.Region reg = b.region();
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        // frente/trás (as faces largas da lâmina)
        quad(vc, pose, reg, dx, dy, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, r, g, bl, light, overlay);
        quad(vc, pose, reg, dx, dy, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, r, g, bl, light, overlay);
        // laterais
        quad(vc, pose, reg, dz, dy, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, r, g, bl, light, overlay);
        quad(vc, pose, reg, dz, dy, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, r, g, bl, light, overlay);
        // topo/base
        quad(vc, pose, reg, dx, dz, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, bl, light, overlay);
        quad(vc, pose, reg, dx, dz, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, r, g, bl, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, BladeGeometry.Region reg, float fw, float fh,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz, int r, int g, int b, int light, int overlay) {
        float u0 = reg.u / ATLAS, v0 = reg.v / ATLAS;
        float u1 = (reg.u + Math.min(reg.w, Math.max(0.5F, fw))) / ATLAS;
        float v1 = (reg.v + Math.min(reg.h, Math.max(0.5F, fh))) / ATLAS;
        vertex(vc, pose, ax, ay, az, u0, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(vc, pose, bx, by, bz, u1, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(vc, pose, cx, cy, cz, u1, v0, nx, ny, nz, r, g, b, light, overlay);
        vertex(vc, pose, dx, dy, dz, u0, v0, nx, ny, nz, r, g, b, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int r, int g, int b, int light, int overlay) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
