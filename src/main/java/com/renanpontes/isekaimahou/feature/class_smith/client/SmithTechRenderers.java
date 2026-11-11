package com.renanpontes.isekaimahou.feature.class_smith.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_smith.entity.BladeWorldEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.RhoAiasEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithBladeEntity;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.ProjectionItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Renderers das técnicas do Ferreiro: a arma projetada (modelo do item apontado para onde voa, com espiral de broca
 * ou giro de disco), o escudo Rho Aias (sete pétalas de luz desenhadas uma a uma) e a Projeção na mão (o modelo da
 * arma copiada, com brilho).
 */
public final class SmithTechRenderers {

    // =====================================================================================
    //  Arma projetada
    // =====================================================================================
    public static class Blade extends EntityRenderer<SmithBladeEntity> {
        private final ItemRenderer items;

        public Blade(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.items = ctx.getItemRenderer();
            this.shadowRadius = 0F;
        }

        @Override
        public void render(SmithBladeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            ItemStack stack = e.item();
            if (stack.isEmpty()) stack = new ItemStack(Items.IRON_SWORD);
            byte mode = e.mode();
            float age = e.tickCount + pt;
            float s = e.scale();
            float alpha = 1F;
            if (mode == SmithBladeEntity.FADE) {
                alpha = Math.max(0F, 1F - (age - e.clientModeStart) / SmithBladeEntity.FADE_TICKS);
                s *= 0.4F + 0.6F * alpha;
            }
            if (s <= 0.02F) return;
            Vec3 d = e.dir();
            ps.pushPose();
            ps.translate(0, e.getBbHeight() * 0.5, 0);
            // aponta o +Y do modelo (ponta da arma) para a direção da lâmina
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) d.x, (float) d.y, (float) d.z));
            ps.mulPose(q);
            if (e.style() == SmithBladeEntity.STYLE_SPIN && mode == SmithBladeEntity.BOOMERANG) {
                ps.mulPose(Axis.XP.rotationDegrees(90F));
                ps.mulPose(Axis.ZP.rotationDegrees(age * 48F));
            } else if (e.style() == SmithBladeEntity.STYLE_SPIRAL) {
                ps.mulPose(Axis.YP.rotationDegrees(age * 40F));
            }
            boolean forged = ForgedBladeItem.is(stack);
            if (mode == SmithBladeEntity.PLANTED || mode == SmithBladeEntity.STUCK) {
                // cravada: um pouco enterrada
                ps.translate(0, 0.25F * s, 0);
            }
            ps.pushPose();
            ps.scale(s, s, s);
            if (!forged) ps.mulPose(Axis.ZP.rotationDegrees(45F)); // ícone diagonal: ponta para +Y
            else ps.translate(0, -0.35F, 0);
            items.renderStatic(stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers,
                    e.level(), e.getId());
            ps.popPose();
            if (e.style() == SmithBladeEntity.STYLE_SPIRAL && mode != SmithBladeEntity.FADE) {
                spiral(ps, buffers.getBuffer(RenderType.lightning()), e.color(), s, age, alpha);
            }
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        /** Dupla hélice de luz em volta da espada-flecha (a "broca" do Caladbolg). */
        private static void spiral(PoseStack ps, VertexConsumer vc, int rgb, float s, float age, float alpha) {
            PoseStack.Pose pose = ps.last();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            int a = (int) (150 * alpha);
            int seg = 28;
            for (int h = 0; h < 2; h++) {
                for (int i = 0; i < seg; i++) {
                    float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
                    float y0 = (-0.9F + t0 * 2.6F) * s, y1 = (-0.9F + t1 * 2.6F) * s;
                    float rad0 = (0.55F - t0 * 0.4F) * s, rad1 = (0.55F - t1 * 0.4F) * s;
                    float ang0 = -age * 0.6F + t0 * 14F + h * Mth.PI, ang1 = -age * 0.6F + t1 * 14F + h * Mth.PI;
                    float x0 = Mth.cos(ang0) * rad0, z0 = Mth.sin(ang0) * rad0;
                    float x1 = Mth.cos(ang1) * rad1, z1 = Mth.sin(ang1) * rad1;
                    float w = 0.045F * s;
                    quad(vc, pose, x0, y0 - w, z0, x1, y1 - w, z1, x1, y1 + w, z1, x0, y0 + w, z0, r, g, b, a);
                }
            }
        }

        @Override
        public ResourceLocation getTextureLocation(SmithBladeEntity e) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    // =====================================================================================
    //  Rho Aias
    // =====================================================================================
    public static class RhoAias extends EntityRenderer<RhoAiasEntity> {
        public RhoAias(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0F;
        }

        @Override
        public void render(RhoAiasEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            int petals = e.petals();
            if (petals <= 0) return;
            float age = e.tickCount + pt;
            float rise = Math.min(1F, age / 6F);
            int rgb = e.color();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            ps.pushPose();
            ps.translate(0, e.getBbHeight() * 0.5, 0);
            float ry = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            float rx = Mth.lerp(pt, e.xRotO, e.getXRot());
            ps.mulPose(Axis.YP.rotationDegrees(-ry));
            ps.mulPose(Axis.XP.rotationDegrees(rx));
            // o disco fica no plano XY (normal = +Z, para onde o dono olha)
            ps.mulPose(Axis.ZP.rotationDegrees(age * 0.8F));
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            PoseStack.Pose pose = ps.last();
            float len = (float) RhoAiasEntity.DISK * rise;
            float crack = e.crack();
            for (int i = 0; i < petals; i++) {
                float a0 = Mth.TWO_PI * i / RhoAiasEntity.PETALS;
                boolean outer = i == petals - 1;
                int alpha = outer ? (int) (120 * (1F - crack * 0.6F)) : 120;
                float z = 0.02F * i;
                petal(vc, pose, a0, len, 0.95F * rise, z, r, g, b, alpha);
                // contorno claro
                petal(vc, pose, a0, len * 1.03F, 0.18F * rise, z + 0.005F, 255, 240, 250, Math.min(255, alpha + 60));
            }
            // miolo
            ring(vc, pose, 0.35F * rise, 0.08F, 0.05F, 255, 255, 255, 180);
            ring(vc, pose, len * 0.98F, 0.05F, 0.04F, r, g, b, 140);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        /** Uma pétala: losango arredondado do centro até {@code len}, largura máxima {@code w}. */
        private static void petal(VertexConsumer vc, PoseStack.Pose pose, float ang, float len, float w, float z,
                                  int r, int g, int b, int a) {
            float cx = Mth.cos(ang), cy = Mth.sin(ang);
            float px = -cy, py = cx;
            int seg = 8;
            for (int k = 0; k < seg; k++) {
                float t0 = k / (float) seg, t1 = (k + 1) / (float) seg;
                float w0 = Mth.sin(t0 * Mth.PI) * w * 0.5F, w1 = Mth.sin(t1 * Mth.PI) * w * 0.5F;
                float d0 = 0.15F + t0 * (len - 0.15F), d1 = 0.15F + t1 * (len - 0.15F);
                float ax = cx * d0 - px * w0, ay = cy * d0 - py * w0;
                float bx = cx * d1 - px * w1, by = cy * d1 - py * w1;
                float qx = cx * d1 + px * w1, qy = cy * d1 + py * w1;
                float dx = cx * d0 + px * w0, dy = cy * d0 + py * w0;
                quad(vc, pose, ax, ay, z, bx, by, z, qx, qy, z, dx, dy, z, r, g, b, a);
            }
        }

        private static void ring(VertexConsumer vc, PoseStack.Pose pose, float rad, float w, float z, int r, int g, int b, int a) {
            int seg = 28;
            for (int k = 0; k < seg; k++) {
                float a0 = Mth.TWO_PI * k / seg, a1 = Mth.TWO_PI * (k + 1) / seg;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                quad(vc, pose, c0 * (rad - w), s0 * (rad - w), z, c1 * (rad - w), s1 * (rad - w), z,
                        c1 * (rad + w), s1 * (rad + w), z, c0 * (rad + w), s0 * (rad + w), z, r, g, b, a);
            }
        }

        @Override
        public ResourceLocation getTextureLocation(RhoAiasEntity e) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    // =====================================================================================
    //  Mundo das Lâminas (o visual são as espadas e as partículas)
    // =====================================================================================
    public static class BladeWorld extends EntityRenderer<BladeWorldEntity> {
        public BladeWorld(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0F;
        }

        @Override
        public boolean shouldRender(BladeWorldEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
            return false;
        }

        @Override
        public ResourceLocation getTextureLocation(BladeWorldEntity e) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    // =====================================================================================
    //  Projeção na mão/inventário: o modelo da arma copiada, com brilho
    // =====================================================================================
    public static class Projection extends BlockEntityWithoutLevelRenderer {
        public Projection() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
            ItemStack src = ProjectionItem.source(stack).copy();
            if (src.getItem() instanceof ProjectionItem) src = new ItemStack(Items.IRON_SWORD);
            src.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            ps.pushPose();
            ps.translate(0.5F, 0.5F, 0.5F);
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(src, ctx, light, overlay, ps, buffers, mc.level, 0);
            ps.popPose();
        }
    }

    /** Quad nos dois sentidos (o tipo "lightning" corta a face de trás). */
    static void quad(VertexConsumer vc, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a) {
        vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
        vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
    }

    private SmithTechRenderers() {}
}
