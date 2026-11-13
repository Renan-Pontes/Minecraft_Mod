package com.renanpontes.isekaimahou.feature.endless_abyss.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssDimension;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssShadows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.event.GeoRenderEvent;

/**
 * Visual escuro das Sombras do Abismo, sem tocar nos renderizadores dos chefes (que são de outros módulos): a entidade
 * que está no time {@link AbyssShadows#TEAM} é desenhada com a cor do shader escurecida/arroxeada. Como as entidades são
 * desenhadas em lote, o lote é fechado antes (para não escurecer o que veio antes) e logo depois da Sombra (com a cor
 * escura aplicada). Só roda dentro da dimensão do Abismo e só para quem está no time — custo zero no resto do jogo.
 */
final class ShadowTint {
    private static final float R = 0.26F, G = 0.20F, B = 0.38F;

    static void register() {
        // Chefes GeckoLib (quase todos) e, por garantia, mobs de modelo vanilla (Campeão do Abismo)
        NeoForge.EVENT_BUS.addListener((GeoRenderEvent.Entity.Pre e) -> before(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((GeoRenderEvent.Entity.Post e) -> after(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class, e -> before(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Post.class, e -> after(e.getEntity()));
    }

    private static boolean isShadow(Entity e) {
        if (e == null || e.level().dimension() != AbyssDimension.KEY) return false;
        Team team = e.getTeam();
        return team != null && AbyssShadows.TEAM.equals(team.getName());
    }

    private static void before(Entity e) {
        if (!isShadow(e)) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();
    }

    private static void after(Entity e) {
        if (!isShadow(e)) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.setShaderColor(R, G, B, 1F);
        buffers.endBatch();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private ShadowTint() {}
}
