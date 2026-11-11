package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "fusions_b": renderers das entidades de magia e invocações, camadas de modelo
 * e o controle de voo da Gravidade Zero (pular sobe, agachar desce).
 */
public class FusionsBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FusionsBClient::renderers);
        modBus.addListener(FusionsBClient::layers);
        NeoForge.EVENT_BUS.addListener(FusionsBClient::tick);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FbRegistries.CRYSTAL_PRISON.get(), CrystalPrisonRenderer::new);
        e.registerEntityRenderer(FbRegistries.CRYSTAL_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.75F, true));
        e.registerEntityRenderer(FbRegistries.FALLING_STAR.get(), ctx -> new ThrownItemRenderer<>(ctx, 2.6F, true));
        e.registerEntityRenderer(FbRegistries.INFERNAL_CHAIN.get(), InfernalChainRenderer::new);
        e.registerEntityRenderer(FbRegistries.HELLFIRE_IMP.get(), HellfireImpRenderer::new);
        e.registerEntityRenderer(FbRegistries.ABYSS_TENTACLE.get(), AbyssTentacleRenderer::new);
        e.registerEntityRenderer(FbRegistries.GRAVITY_WELL.get(), GravityWellRenderer::new);
        e.registerEntityRenderer(FbRegistries.ORBITING_DEBRIS.get(), OrbitingDebrisRenderer::new);
        e.registerEntityRenderer(FbRegistries.VOID_RIFT.get(), VoidRiftRenderer::new);
        e.registerEntityRenderer(FbRegistries.TWILIGHT_CLONE.get(), TwilightCloneRenderer::new);
        e.registerEntityRenderer(FbRegistries.ECLIPSE_FIELD.get(), EclipseFieldRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(HellfireImpModel.LAYER, HellfireImpModel::createBodyLayer);
        e.registerLayerDefinition(AbyssTentacleModel.LAYER, AbyssTentacleModel::createBodyLayer);
    }

    /**
     * Gravidade Zero no próprio jogador: o movimento do jogador é calculado no cliente, então o controle
     * vertical (subir/descer/pairar) fica aqui. O servidor só zera o dano de queda.
     */
    private static void tick(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isSpectator() || p.getAbilities().flying) return;
        MobEffectInstance zg = p.getEffect(FbRegistries.ZERO_GRAVITY);
        if (zg == null) return;
        Vec3 v = p.getDeltaMovement();
        double vy;
        if (zg.getAmplifier() > 0) {
            // Lançada por um inimigo: à deriva, subindo devagar, sem controle
            vy = v.y + (0.012 - v.y) * 0.1;
        } else if (p.input.jumping) {
            vy = Math.min(v.y + 0.045, 0.35);
        } else if (p.input.shiftKeyDown) {
            vy = Math.max(v.y - 0.045, -0.35);
        } else {
            vy = v.y * 0.82;
        }
        p.setDeltaMovement(v.x, vy, v.z);
    }
}
