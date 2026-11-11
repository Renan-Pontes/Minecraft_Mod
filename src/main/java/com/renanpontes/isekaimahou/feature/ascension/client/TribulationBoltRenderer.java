package com.renanpontes.isekaimahou.feature.ascension.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.ascension.entity.TribulationBoltEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Raio arcano: mesmo traçado em zigue-zague do raio vanilla (com galhos), mas roxo com o miolo claro,
 * e piscando no fim da vida.
 */
public class TribulationBoltRenderer extends EntityRenderer<TribulationBoltEntity> {
    public TribulationBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(TribulationBoltEntity bolt, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        // Pisca nos últimos ticks (como os "flashes" do raio vanilla).
        if (bolt.tickCount > TribulationBoltEntity.LIFE / 2 && bolt.tickCount % 2 == 1) return;

        float[] xs = new float[8];
        float[] zs = new float[8];
        float x = 0F, z = 0F;
        RandomSource shape = RandomSource.create(bolt.seed);
        for (int i = 7; i >= 0; i--) {
            xs[i] = x;
            zs[i] = z;
            x += shape.nextInt(11) - 5;
            z += shape.nextInt(11) - 5;
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        for (int layer = 0; layer < 4; layer++) {
            RandomSource rnd = RandomSource.create(bolt.seed);
            // Camada 0 (mais fina) quase branca; as de fora roxas.
            float r = layer == 0 ? 0.85F : 0.55F;
            float g = layer == 0 ? 0.75F : 0.30F;
            float b = layer == 0 ? 1.00F : 0.90F;
            for (int branch = 0; branch < 3; branch++) {
                int top = branch > 0 ? 7 - branch : 7;
                int bottom = branch > 0 ? top - 2 : 0;
                float cx = xs[top] - x;
                float cz = zs[top] - z;
                for (int seg = top; seg >= bottom; seg--) {
                    float px = cx, pz = cz;
                    if (branch == 0) {
                        cx += rnd.nextInt(11) - 5;
                        cz += rnd.nextInt(11) - 5;
                    } else {
                        cx += rnd.nextInt(31) - 15;
                        cz += rnd.nextInt(31) - 15;
                    }
                    float w1 = 0.1F + layer * 0.2F;
                    if (branch == 0) w1 *= seg * 0.1F + 1.0F;
                    float w2 = 0.1F + layer * 0.2F;
                    if (branch == 0) w2 *= (seg - 1.0F) * 0.1F + 1.0F;
                    quad(m, vc, cx, cz, seg, px, pz, r, g, b, w1, w2, false, false, true, false);
                    quad(m, vc, cx, cz, seg, px, pz, r, g, b, w1, w2, true, false, true, true);
                    quad(m, vc, cx, cz, seg, px, pz, r, g, b, w1, w2, true, true, false, true);
                    quad(m, vc, cx, cz, seg, px, pz, r, g, b, w1, w2, false, true, false, false);
                }
            }
        }
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float x1, float z1, int index, float x2, float z2,
                             float r, float g, float b, float w1, float w2, boolean a, boolean c, boolean d, boolean e) {
        vc.addVertex(m, x1 + (a ? w2 : -w2), index * 16F, z1 + (c ? w2 : -w2)).setColor(r, g, b, 0.3F);
        vc.addVertex(m, x2 + (a ? w1 : -w1), (index + 1) * 16F, z2 + (c ? w1 : -w1)).setColor(r, g, b, 0.3F);
        vc.addVertex(m, x2 + (d ? w1 : -w1), (index + 1) * 16F, z2 + (e ? w1 : -w1)).setColor(r, g, b, 0.3F);
        vc.addVertex(m, x1 + (d ? w2 : -w2), index * 16F, z1 + (e ? w2 : -w2)).setColor(r, g, b, 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(TribulationBoltEntity bolt) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
