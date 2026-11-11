package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Tipo de render das partículas BRILHANTES (mistura aditiva: somam luz, brilham no escuro, sem escrever profundidade).
 * O NeoForge desenha tipos próprios depois dos do vanilla; a função de mistura volta ao padrão no estágio
 * AFTER_PARTICLES ({@link SpellFxClient}).
 */
public final class FxRenderTypes {
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "isekai_mahou:fx_additive";
        }
    };

    /** Luz máxima (partículas que brilham sozinhas). */
    public static final int FULL_BRIGHT = 0xF000F0;

    private FxRenderTypes() {}
}
