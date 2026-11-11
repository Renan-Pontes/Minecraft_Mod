package com.renanpontes.isekaimahou.feature.ritual_necro.client;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.block.OssuaryAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Durante o Rito Ossuário, um Filactério transbordando de almas sobe girando sobre o altar
 * (cada vez mais rápido), com brilho total.
 */
public class OssuaryAltarRenderer implements BlockEntityRenderer<OssuaryAltarBlockEntity> {
    private final ItemRenderer items;
    private ItemStack shown;

    public OssuaryAltarRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    private ItemStack glowingPhylactery() {
        if (shown == null) {
            shown = new ItemStack(NecroContent.PHYLACTERY.get());
            shown.set(NecroContent.SOULS.get(), new SoulStore(Map.of(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE), SoulStore.MAX_PER_TYPE)));
        }
        return shown;
    }

    @Override
    public void render(OssuaryAltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isActive() || be.getLevel() == null) return;
        float t = be.progress(partialTick);
        float time = (be.getLevel().getGameTime() % 100000L) + partialTick;
        // Sobe com uma curva suave e gira acelerando; no fim pulsa
        float rise = 1.05F + Mth.sin(t * Mth.HALF_PI) * 0.7F + Mth.sin(time * 0.15F) * 0.04F;
        float spin = time * (3F + t * 18F);
        float scale = 0.55F + (t > 0.85F ? Mth.sin((t - 0.85F) / 0.15F * Mth.PI) * 0.2F : 0F);

        pose.pushPose();
        pose.translate(0.5F, rise, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);
        items.renderStatic(glowingPhylactery(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    /** O Filactério sobe até ~1,9 bloco acima do altar: a caixa de corte cobre essa altura (senão some na borda da tela). */
    @Override
    public AABB getRenderBoundingBox(OssuaryAltarBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 3, p.getZ() + 1);
    }
}
