package com.renanpontes.isekaimahou.feature.class_steelball.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.BallBreakerEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.InfiniteSpinEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.ScanBallEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpiralHoleEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.TuskNailEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinNailEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinVortexEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitItem;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do Steel Ball: renderers GeckoLib (bola, unha, turbilhão, espiral infinita, Mestre), roupa 3D e a aba
 * "Rotação" do menu B — registrada só quando o jogador local é um Steel Ball (as outras classes não veem a aba).
 */
public class ClassSteelballClient implements ClientModule {
    private static boolean tabRegistered;

    @Override
    public void initClient(IEventBus modBus) {
        SbOutfitItem.renderFactory = SbOutfitRenderer.Provider::new;
        SbFxClient.init();
        modBus.addListener(ClassSteelballClient::renderers);
        NeoForge.EVENT_BUS.addListener(ClassSteelballClient::onClientTick);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(SbRegistry.BALL.get(), ctx -> new SbProjectileRenderer<SteelBallEntity>(ctx, "steelball_ball",
                b -> b.golden() ? "steelball_ball_gold" : "steelball_ball", SteelBallEntity::golden));
        e.registerEntityRenderer(SbRegistry.NAIL.get(), ctx -> new SbProjectileRenderer<SpinNailEntity>(ctx, "steelball_nail",
                n -> n.golden() ? "steelball_nail_gold" : "steelball_nail", n -> true));
        e.registerEntityRenderer(SbRegistry.VORTEX.get(), ctx -> new SbAuraRenderer<SpinVortexEntity>(ctx, "steelball_vortex",
                v -> v.golden() ? "steelball_vortex_gold" : "steelball_vortex", (v, pt) -> v.radius() / 3.5F * v.openness(pt)));
        e.registerEntityRenderer(SbRegistry.INFINITE.get(), ctx -> new SbAuraRenderer<InfiniteSpinEntity>(ctx, "steelball_infinite_spin",
                v -> "steelball_infinite_spin", (v, pt) -> v.size() * v.openness(pt)));
        e.registerEntityRenderer(SbRegistry.MENTOR.get(), MentorRenderer::new);
        // v1.0 — Técnicas do Santo
        e.registerEntityRenderer(SbRegistry.TUSK_NAIL.get(), ctx -> new SbProjectileRenderer<TuskNailEntity>(ctx, "steelball_nail",
                n -> n.golden() ? "steelball_nail_gold" : "steelball_tusk_nail", n -> true));
        e.registerEntityRenderer(SbRegistry.SCAN_BALL.get(), ctx -> new SbProjectileRenderer<ScanBallEntity>(ctx, "steelball_ball",
                b -> "steelball_scan_ball", b -> true));
        e.registerEntityRenderer(SbRegistry.SPIRAL_HOLE.get(), ctx -> new SbAuraRenderer<SpiralHoleEntity>(ctx, "steelball_spiral_hole",
                h -> h.mode() == SpiralHoleEntity.PORTAL ? "steelball_wormhole" : "steelball_spiral_hole",
                (h, pt) -> h.openness(pt) * (h.mode() == SpiralHoleEntity.PORTAL ? 1.3F : 1.0F)));
        e.registerEntityRenderer(SbRegistry.BALL_BREAKER.get(), ctx -> new SbAuraRenderer<BallBreakerEntity>(ctx, "steelball_ball_breaker",
                b -> "steelball_ball_breaker", (b, pt) -> b.size() * b.openness(pt)));
        // LOVE TRAIN: a cortina de luz do Santo
        e.registerEntityRenderer(SbRegistry.LOVE_TRAIN.get(), LoveTrainRenderer::new);
    }

    /** A aba aparece na primeira vez que o jogador local é Steel Ball (e fica até fechar o jogo). */
    private static void onClientTick(ClientTickEvent.Post e) {
        if (tabRegistered || ClientMagic.data().path() != MagicPath.STEELBALL) return;
        tabRegistered = true;
        MenuTabs.register("steelball", 55, Component.translatable("gui.isekai_mahou.steelball.tab"), SteelballTab::new);
    }
}
