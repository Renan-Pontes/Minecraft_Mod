package com.renanpontes.isekaimahou.feature.farm.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineBlock;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

/**
 * Desenho das fazendas mágicas:
 * <ul>
 *   <li>Gerador: o último produto girando em cima;</li>
 *   <li>Árvore Acelerada: a muda/planta CRESCENDO no vaso conforme o ciclo (plantações passam pelas idades);</li>
 *   <li>Criadouro: o animal guardado, em miniatura, andando em círculos dentro da gaiola;</li>
 *   <li>Lago: um peixe pulando de vez em quando;</li>
 *   <li>Pedestal Elemental: a essência flutuando e um círculo de runas na cor do elemento.</li>
 * </ul>
 */
public class FarmMachineRenderer implements BlockEntityRenderer<FarmMachineBlockEntity> {
    private static final ResourceLocation RING = IsekaiMahou.id("textures/block/farm_essence_ring.png");
    private static final ItemStack[] FISH = {new ItemStack(Items.COD), new ItemStack(Items.SALMON), new ItemStack(Items.TROPICAL_FISH)};
    /** Uma criatura "de vitrine" por tipo (só no cliente, nunca entra no mundo). */
    private static final Map<EntityType<?>, Entity> MINIS = new HashMap<>();

    private final ItemRenderer items;
    private final BlockRenderDispatcher blocks;

    public FarmMachineRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    /** Solta as miniaturas (troca de mundo). */
    public static void clearCache() {
        MINIS.clear();
    }

    @Override
    public void render(FarmMachineBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float t = level.getGameTime() + partialTick;
        boolean active = be.getBlockState().hasProperty(FarmMachineBlock.ACTIVE) && be.getBlockState().getValue(FarmMachineBlock.ACTIVE);
        int seed = (int) be.getBlockPos().asLong();
        int lightAbove = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        switch (be.kind()) {
            case STONE_GENERATOR -> floating(be.display(), ps, buffers, level, t, seed, 1.18, 0.45F, active ? 2.5F : 0.6F, lightAbove);
            case ACCELERATED_TREE -> plant(be, ps, buffers, partialTick, lightAbove);
            case HATCHERY -> mini(be, ps, buffers, t, partialTick, light);
            case FISH_POND -> fish(ps, buffers, level, t, seed, lightAbove, active);
            case ESSENCE -> essence(be, ps, buffers, level, t, seed, active);
        }
    }

    private void floating(ItemStack stack, PoseStack ps, MultiBufferSource buffers, Level level, float t, int seed, double y, float scale,
                          float spin, int light) {
        if (stack.isEmpty()) return;
        ps.pushPose();
        ps.translate(0.5, y + Mth.sin(t / 10F + (seed & 15)) * 0.05, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees((t * spin) % 360F));
        ps.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, level, seed);
        ps.popPose();
    }

    // ---------------------------------------------------------------- Árvore Acelerada
    private void plant(FarmMachineBlockEntity be, PoseStack ps, MultiBufferSource buffers, float partialTick, int light) {
        ItemStack in = be.input();
        if (!(in.getItem() instanceof BlockItem bi)) return;
        float p = be.clientProgress(partialTick);
        BlockState state = bi.getBlock().defaultBlockState();
        IntegerProperty age = ageOf(state);
        float scale;
        if (age != null) {
            int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            state = state.setValue(age, Math.round(p * max));
            scale = 0.8F;
        } else {
            scale = 0.3F + 0.6F * p;
        }
        ps.pushPose();
        ps.translate(0.5, 9.0 / 16.0, 0.5);
        // balança de leve, como ao vento
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin((be.getLevel().getGameTime() + partialTick) / 18F) * 2F));
        ps.scale(scale, scale, scale);
        ps.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static IntegerProperty ageOf(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntegerProperty ip && prop.getName().equals("age")) return ip;
        }
        return null;
    }

    // ---------------------------------------------------------------- Criadouro
    private void mini(FarmMachineBlockEntity be, PoseStack ps, MultiBufferSource buffers, float t, float partialTick, int light) {
        ResourceLocation id = be.animal();
        if (id == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return;
        Entity e = MINIS.computeIfAbsent(type, ty -> ty.create(be.getLevel()));
        if (e == null) return;
        float size = Math.max(e.getBbWidth(), e.getBbHeight());
        float scale = Math.min(0.45F, 0.6F / Math.max(0.1F, size));
        float angle = (t * 1.2F) % 360F;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        ps.pushPose();
        ps.translate(0.5, 5.0 / 16.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-angle));
        ps.translate(0.18, 0, 0);
        ps.scale(scale, scale, scale);
        e.setYRot(0F);
        e.setYHeadRot(0F);
        if (e instanceof net.minecraft.world.entity.LivingEntity le) {
            le.yBodyRot = 0F;
            le.yBodyRotO = 0F;
            le.yHeadRotO = 0F;
            le.walkAnimation.setSpeed(0.6F);
            le.walkAnimation.update(0.6F, 0.4F);
        }
        dispatcher.setRenderShadow(false);
        dispatcher.render(e, 0, 0, 0, 0F, partialTick, ps, buffers, light);
        dispatcher.setRenderShadow(true);
        ps.popPose();
    }

    // ---------------------------------------------------------------- Lago
    private void fish(PoseStack ps, MultiBufferSource buffers, Level level, float t, int seed, int light, boolean active) {
        int period = active ? 50 : 110;
        float phase = (t + (seed & 63)) % period;
        if (phase > 16) return;
        float k = phase / 16F; // 0..1
        int which = (int) (((t + (seed & 63)) / period)) % FISH.length;
        ps.pushPose();
        ps.translate(0.5 + (k - 0.5) * 0.5, 0.82 + Math.sin(k * Math.PI) * 0.55, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees((seed & 3) * 90F));
        ps.mulPose(Axis.ZP.rotationDegrees(-60F + k * 120F));
        ps.scale(0.45F, 0.45F, 0.45F);
        items.renderStatic(FISH[Math.floorMod(which, FISH.length)], ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, level, seed);
        ps.popPose();
    }

    // ---------------------------------------------------------------- Pedestal Elemental
    private void essence(FarmMachineBlockEntity be, PoseStack ps, MultiBufferSource buffers, Level level, float t, int seed, boolean active) {
        Element e = be.element();
        if (e == null) return;
        var essence = CoreRegistries.ESSENCES.get(e);
        if (essence != null) {
            floating(new ItemStack(essence.get()), ps, buffers, level, t, seed, 1.15, 0.6F, active ? 3F : 1F, LightTexture.FULL_BRIGHT);
        }
        float alpha = active ? 0.8F : 0.3F;
        ps.pushPose();
        ps.translate(0.5, 13.15 / 16.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees((t * (active ? 1.8F : 0.4F)) % 360F));
        quad(buffers.getBuffer(RenderType.entityTranslucent(RING)), ps.last(), 0.62F, e.color, alpha);
        ps.popPose();
        if (active) {
            float p = be.clientProgress(0F);
            ps.pushPose();
            ps.translate(0.5, 13.2 / 16.0, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((-t * 2.6F) % 360F));
            quad(buffers.getBuffer(RenderType.entityTranslucent(RING)), ps.last(), 0.25F + 0.45F * p, e.color, 0.5F + 0.4F * p);
            ps.popPose();
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float half, int rgb, float alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(vc, pose, -half, -half, 0, 0, r, g, b, a);
        vertex(vc, pose, -half, half, 0, 1, r, g, b, a);
        vertex(vc, pose, half, half, 1, 1, r, g, b, a);
        vertex(vc, pose, half, -half, 1, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose, x, 0F, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0F, 1F, 0F);
    }

    @Override
    public AABB getRenderBoundingBox(FarmMachineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, 1.5, 0);
    }
}
