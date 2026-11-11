package com.renanpontes.isekaimahou.client.status;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Desenho no mundo das condições de status (funciona com qualquer renderizador de entidade, inclusive GeckoLib):
 * bloco de gelo translúcido em volta do Congelado, casca de pedra no Petrificado, chamas no Em Chamas (quando o fogo
 * vanilla não está aparecendo), fileira de ícones com acúmulos sobre a cabeça e os textos das reações.
 */
final class StatusWorldRenderer {
    private static final double SHELL_RANGE = 48.0;
    private static final double ICON_RANGE = 24.0;
    private static final ResourceLocation ICE = ResourceLocation.withDefaultNamespace("block/ice");
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("block/stone");
    private static MultiBufferSource.BufferSource buffers;

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean hasStatuses = !ClientStatuses.trackedIds().isEmpty();
        if (!hasStatuses && ReactionTexts.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(4096));

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        PoseStack ps = new PoseStack();

        if (hasStatuses) {
            boolean icons = !mc.options.hideGui && !ClientStatuses.worldIconsHandledExternally() && iconsEnabled();
            List<Integer> ids = new ArrayList<>(ClientStatuses.trackedIds());
            for (int id : ids) {
                Entity ent = mc.level.getEntity(id);
                if (!(ent instanceof LivingEntity le) || le.isRemoved() || le.isInvisibleTo(mc.player)) continue;
                List<ClientStatuses.Entry> list = ClientStatuses.get(le);
                if (list.isEmpty()) continue;
                Vec3 pos = le.getPosition(pt);
                double dist = pos.distanceTo(camPos);
                if (dist > SHELL_RANGE) continue;
                boolean firstPerson = le == cam.getEntity() && !cam.isDetached();
                if (!firstPerson) shells(ps, le, list, pos, camPos, cam, pt);
                if (icons && dist <= ICON_RANGE && le != mc.player) iconRow(ps, le, list, pos, camPos, cam, dist);
            }
        }
        if (!mc.options.hideGui) ReactionTexts.render(ps, buffers, cam, pt);
        buffers.endBatch();
    }

    private static boolean iconsEnabled() {
        try {
            return MahouConfig.SHOW_STATUS_ICONS.get();
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    // =====================================================================================
    //  Gelo, pedra e chamas
    // =====================================================================================
    private static void shells(PoseStack ps, LivingEntity le, List<ClientStatuses.Entry> list, Vec3 pos, Vec3 camPos, Camera cam, float pt) {
        boolean frozen = false, stone = false, burning = false;
        for (ClientStatuses.Entry en : list) {
            if (en.kind() == Statuses.Kind.FROZEN) frozen = true;
            else if (en.kind() == Statuses.Kind.PETRIFIED && en.stacks() >= 3) stone = true;
            else if (en.kind() == Statuses.Kind.BURNING) burning = true;
        }
        if (!frozen && !stone && !burning) return;
        Minecraft mc = Minecraft.getInstance();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(le, pt);
        ps.pushPose();
        ps.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
        if (stone) box(ps, le, sprite(STONE), 0.92F, 0.10F, light, 1.0F, 1.0F, 1.0F);
        else if (frozen) box(ps, le, sprite(ICE), 0.62F, 0.14F, light, 0.85F, 0.95F, 1.0F);
        if (burning && !le.displayFireAnimation() && !frozen) flames(ps, le, cam);
        ps.popPose();
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /** Caixa texturizada em volta da caixa de colisão da entidade. */
    private static void box(PoseStack ps, LivingEntity le, TextureAtlasSprite s, float alpha, float pad, int light,
                            float r, float g, float b) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = ps.last();
        float hw = le.getBbWidth() / 2F + pad;
        float x0 = -hw, x1 = hw, z0 = -hw, z1 = hw, y0 = -0.02F, y1 = le.getBbHeight() + pad;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int a = (int) (alpha * 255);
        int col = (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
        // +Y / -Y
        face(vc, pose, col, light, 0, 1, 0, x0, y1, z0, u0, v0, x0, y1, z1, u0, v1, x1, y1, z1, u1, v1, x1, y1, z0, u1, v0);
        face(vc, pose, col, light, 0, -1, 0, x0, y0, z1, u0, v0, x0, y0, z0, u0, v1, x1, y0, z0, u1, v1, x1, y0, z1, u1, v0);
        // +Z / -Z
        face(vc, pose, col, light, 0, 0, 1, x0, y1, z1, u0, v0, x0, y0, z1, u0, v1, x1, y0, z1, u1, v1, x1, y1, z1, u1, v0);
        face(vc, pose, col, light, 0, 0, -1, x1, y1, z0, u0, v0, x1, y0, z0, u0, v1, x0, y0, z0, u1, v1, x0, y1, z0, u1, v0);
        // +X / -X
        face(vc, pose, col, light, 1, 0, 0, x1, y1, z1, u0, v0, x1, y0, z1, u0, v1, x1, y0, z0, u1, v1, x1, y1, z0, u1, v0);
        face(vc, pose, col, light, -1, 0, 0, x0, y1, z0, u0, v0, x0, y0, z0, u0, v1, x0, y0, z1, u1, v1, x0, y1, z1, u1, v0);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, int col, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(vc, pose, col, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(vc, pose, col, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(vc, pose, col, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(vc, pose, col, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int col, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** Chamas do vanilla (mesmo desenho do "pegando fogo"), para Em Chamas sem fogo vanilla. */
    private static void flames(PoseStack ps, LivingEntity le, Camera cam) {
        TextureAtlasSprite fire0 = ModelBakery.FIRE_0.sprite();
        TextureAtlasSprite fire1 = ModelBakery.FIRE_1.sprite();
        ps.pushPose();
        float f = le.getBbWidth() * 1.4F;
        ps.scale(f, f, f);
        float half = 0.5F;
        float h = le.getBbHeight() / f;
        float y = 0.0F;
        ps.mulPose(Axis.YP.rotationDegrees(-cam.getYRot()));
        ps.translate(0.0F, 0.0F, 0.3F - (int) h * 0.02F);
        float z = 0.0F;
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = ps.last();
        for (int i = 0; h > 0.0F; i++) {
            TextureAtlasSprite s = i % 2 == 0 ? fire0 : fire1;
            float u0 = s.getU0(), v0 = s.getV0(), u1 = s.getU1(), v1 = s.getV1();
            if (i / 2 % 2 == 0) {
                float t = u1;
                u1 = u0;
                u0 = t;
            }
            fire(vc, pose, -half, -y, z, u1, v1);
            fire(vc, pose, half, -y, z, u0, v1);
            fire(vc, pose, half, 1.4F - y, z, u0, v0);
            fire(vc, pose, -half, 1.4F - y, z, u1, v0);
            h -= 0.45F;
            y -= 0.45F;
            half *= 0.9F;
            z -= 0.03F;
        }
        ps.popPose();
    }

    private static void fire(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setUv1(0, 10).setLight(240).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // =====================================================================================
    //  Ícones sobre a cabeça
    // =====================================================================================
    private static void iconRow(PoseStack ps, LivingEntity le, List<ClientStatuses.Entry> all, Vec3 pos, Vec3 camPos, Camera cam, double dist) {
        // Dentro d'água "Molhado" é óbvio (peixes, lulas, Afogados): não enche o lago de ícones
        List<ClientStatuses.Entry> list = all;
        if (le.isInWater()) list = all.stream().filter(en -> en.kind() != Statuses.Kind.WET).toList();
        if (list.isEmpty()) return;
        int n = Math.min(list.size(), 8);
        float alpha = Mth.clamp((float) (ICON_RANGE + 2 - dist) / 4F, 0F, 1F);
        if (alpha <= 0.02F) return;
        // Acima da cabeça, do nome e da barrinha de vida flutuante
        double h = le.getBbHeight() + 0.78 + (le.hasCustomName() ? 0.3 : 0);
        float scale = 0.025F * (1F + Mth.clamp((float) (dist - 6.0) / 18F, 0F, 1F) * 0.6F);
        final float size = 10F, gap = 2F;
        float total = n * size + (n - 1) * gap;
        ps.pushPose();
        ps.translate(pos.x - camPos.x, pos.y + h - camPos.y, pos.z - camPos.z);
        ps.mulPose(cam.rotation());
        ps.scale(scale, -scale, scale);
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255);
        Font font = Minecraft.getInstance().font;
        float x = -total / 2F;
        for (int i = 0; i < n; i++) {
            ClientStatuses.Entry en = list.get(i);
            ResourceLocation tex = ClientStatuses.icon(en.kind());
            // Piscando nos últimos 2 s
            float blink = en.ticksLeft() >= 0 && en.ticksLeft() < 40 && (en.ticksLeft() / 4) % 2 == 0 ? 0.55F : 1F;
            int ca = (int) (a * blink);
            if (GlyphArt.exists(tex)) {
                VertexConsumer vc = buffers.getBuffer(RenderType.text(tex));
                quadTex(vc, m, x, -size, x + size, 0, (ca << 24) | 0xFFFFFF);
            } else {
                VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
                quadCol(vc, m, x, -size, x + size, 0, 0.02F, (ca << 24) | en.kind().color());
                quadCol(vc, m, x + 1, -size + 1, x + size - 1, -1, 0.01F, (ca << 24) | darken(en.kind().color()));
            }
            if (en.stacks() > 1) {
                String s = Integer.toString(en.stacks());
                ps.pushPose();
                ps.translate(x + size - font.width(s) * 0.6F + 1F, -5.2F, -0.05F);
                ps.scale(0.6F, 0.6F, 0.6F);
                font.drawInBatch8xOutline(net.minecraft.network.chat.Component.literal(s).getVisualOrderText(), 0, 0,
                        (Math.max(8, ca) << 24) | 0xFFFFFF, (Math.max(8, ca) << 24) | 0x101018, ps.last().pose(), buffers,
                        LightTexture.FULL_BRIGHT);
                ps.popPose();
            }
            x += size + gap;
        }
        ps.popPose();
    }

    /** Quad na ordem do texto do vanilla (canto sup. esq., inf. esq., inf. dir., sup. dir.): visível de frente. */
    private static void quadTex(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int light = LightTexture.FULL_BRIGHT;
        vc.addVertex(m, x0, y0, 0).setColor(argb).setUv(0, 0).setLight(light);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setUv(0, 1).setLight(light);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setUv(1, 1).setLight(light);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setUv(1, 0).setLight(light);
    }

    private static void quadCol(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int light = LightTexture.FULL_BRIGHT;
        vc.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
    }

    private static int darken(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r * 6 / 10) << 16) | ((g * 6 / 10) << 8) | (b * 6 / 10);
    }

    private StatusWorldRenderer() {}
}
