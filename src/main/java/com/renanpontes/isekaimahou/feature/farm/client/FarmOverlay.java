package com.renanpontes.isekaimahou.feature.farm.client;

import javax.annotation.Nullable;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.feature.farm.blueprint.BlueprintItem;
import com.renanpontes.isekaimahou.feature.farm.wand.Clipboard;
import com.renanpontes.isekaimahou.feature.farm.wand.ConstructionWandItem;
import com.renanpontes.isekaimahou.feature.farm.wand.WandData;
import com.renanpontes.isekaimahou.feature.farm.wand.WandMode;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Prévias no mundo (visíveis através das paredes) e o mini-HUD da varinha:
 * <ul>
 *   <li>Varinha: a caixa marcada (com só o 1º canto, a caixa acompanha o bloco olhado), a prévia da colagem e o baú ligado;</li>
 *   <li>Planta de Casa: o contorno de onde a obra vai ficar, antes de montar o canteiro.</li>
 * </ul>
 */
public final class FarmOverlay {
    private record Box(AABB box, int rgb, float alpha) {}

    // =====================================================================================
    //  Mundo
    // =====================================================================================
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ItemStack main = player.getMainHandItem();
        java.util.List<Box> boxes = new java.util.ArrayList<>(3);
        float pulse = 0.65F + 0.25F * (float) Math.sin((mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.2);
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK ? bh : null;

        if (main.getItem() instanceof ConstructionWandItem) {
            WandData d = ConstructionWandItem.data(main);
            WandMode mode = d.wandMode();
            if (mode == WandMode.PASTE) {
                if (d.hasClip() && hit != null) {
                    BlockPos origin = hit.getBlockPos().relative(hit.getDirection());
                    boxes.add(new Box(Clipboard.pasteBox(origin, d.clip(), d.clipDirection(), player.getDirection()), mode.rgb, pulse));
                }
            } else if (mode != WandMode.UNDO && d.a().isPresent()) {
                BlockPos a = d.a().get();
                BlockPos b = d.b().orElse(hit != null ? hit.getBlockPos() : a);
                boxes.add(new Box(new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1),
                        mode.rgb, d.b().isPresent() ? 0.9F : pulse));
                boxes.add(new Box(new AABB(a).deflate(0.1), 0xFFFFFF, 0.8F));
            }
            d.link().ifPresent(l -> {
                if (l.dimension().equals(mc.level.dimension()) && l.pos().closerThan(player.blockPosition(), 64)) {
                    boxes.add(new Box(new AABB(l.pos()).inflate(0.03), 0x9BE07A, 0.6F));
                }
            });
        } else if (main.getItem() instanceof BlueprintItem bp && hit != null) {
            BlockPos site = hit.getDirection() == Direction.UP ? hit.getBlockPos().above() : hit.getBlockPos().relative(hit.getDirection());
            boxes.add(new Box(bp.kind.template().bounds(site, player.getDirection()), bp.kind.rgb, pulse));
            boxes.add(new Box(new AABB(site).deflate(0.2), 0xFFFFFF, 0.9F));
        }
        if (boxes.isEmpty()) return;
        draw(event, boxes);
    }

    private static void draw(RenderLevelStageEvent event, java.util.List<Box> boxes) {
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2.5F);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        PoseStack pose = new PoseStack();
        for (Box b : boxes) {
            AABB box = b.box.move(-cam.x, -cam.y, -cam.z).inflate(0.004);
            int c = b.rgb;
            LevelRenderer.renderLineBox(pose, buf, box, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, b.alpha);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // =====================================================================================
    //  Mini-HUD da varinha (à direita da mira)
    // =====================================================================================
    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        ItemStack main = mc.player.getMainHandItem();
        if (!(main.getItem() instanceof ConstructionWandItem)) return;
        WandData d = ConstructionWandItem.data(main);
        WandMode mode = d.wandMode();
        int x = g.guiWidth() / 2 + 12, y = g.guiHeight() / 2 - 8;
        g.drawString(mc.font, Component.translatable("farm.isekai_mahou.hud.mode", mode.displayName()), x, y, 0xFFFFFFFF, true);
        Component line = info(d, mode);
        if (line != null) g.drawString(mc.font, line, x, y + 10, 0xFFB8C4D8, true);
        Component mat = material(d);
        if (mat != null && mode.usesMaterial) g.drawString(mc.font, mat, x, y + 20, 0xFF7FD4FF, true);
    }

    @Nullable
    private static Component info(WandData d, WandMode mode) {
        if (mode == WandMode.PASTE) {
            return d.hasClip() ? Component.translatable("farm.isekai_mahou.wand.clip", d.clip().getX(), d.clip().getY(), d.clip().getZ())
                    : Component.translatable("farm.isekai_mahou.hud.no_clip");
        }
        if (mode == WandMode.UNDO) return Component.translatable("farm.isekai_mahou.hud.undo");
        if (!d.hasSelection()) {
            return Component.translatable(d.a().isPresent() ? "farm.isekai_mahou.hud.pick2" : "farm.isekai_mahou.hud.pick1");
        }
        BlockPos s = d.size();
        long vol = switch (mode) {
            case FLOOR -> (long) s.getX() * s.getZ();
            case WALL -> (long) s.getY() * Math.max(1, 2 * (s.getX() + s.getZ()) - 4);
            default -> (long) s.getX() * s.getY() * s.getZ();
        };
        return Component.translatable("farm.isekai_mahou.hud.size", s.getX(), s.getY(), s.getZ(), vol);
    }

    @Nullable
    private static Component material(WandData d) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof net.minecraft.world.item.BlockItem) {
            return Component.translatable("farm.isekai_mahou.hud.material", off.getHoverName());
        }
        return d.material().map(m -> (Component) Component.translatable("farm.isekai_mahou.hud.material",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(m).getName())).orElse(null);
    }

    private FarmOverlay() {}
}
