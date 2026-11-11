package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiNetwork;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRegistries;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ShinigamiArmorItem;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do módulo Shinigami: renderers (espírito GeckoLib, projéteis de Reiatsu, traje 3D), os modelos da zanpakutō
 * selada/Shikai/Bankai (propriedades {@code isekai_mahou:release} e {@code isekai_mahou:archetype}), o marcador da
 * liberação junto da mira, a tela de escolha do arquétipo, a névoa do Mundo Interior na cor da alma e a aba
 * "Shinigami" do menu B.
 */
public class ClassShinigamiClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        ShinigamiArmorItem.renderFactory = ShinigamiArmorRenderer.Provider::new;
        ShinigamiNetwork.clientOpenArchetypes = () -> Minecraft.getInstance().setScreen(new ArchetypeScreen());
        ShinigamiNetwork.clientMask = HollowClient::accept;
        // efeitos de anime: Senbonzakura, pressão espiritual, Shunpo, Cero, Mugetsu...
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.clientSwarm = ShinigamiFxClient::onSwarm;
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.clientBurst = ShinigamiFxClient::onBurst;
        NeoForge.EVENT_BUS.addListener(ShinigamiFxClient::tick);
        NeoForge.EVENT_BUS.addListener(ShinigamiFxClient::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ShinigamiFxClient.clear());
        modBus.addListener(this::onRenderers);
        modBus.addListener(ClassShinigamiClient::onLayers);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> HollowClient.clear());
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onGuiLayers);
        NeoForge.EVENT_BUS.addListener(ClassShinigamiClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(ClassShinigamiClient::onFog);
        MenuTabs.register("shinigami", 87, Component.translatable("tab.isekai_mahou.shinigami"), ShinigamiTab::new);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ShinigamiRegistries.SPIRIT.get(), ZanpakutoSpiritRenderer::new);
        e.registerEntityRenderer(ShinigamiRegistries.REISHI_WAVE.get(), ReishiWaveRenderer::new);
        e.registerEntityRenderer(ShinigamiRegistries.BLACK_COFFIN.get(), BlackCoffinRenderer::new);
        e.registerEntityRenderer(ShinigamiRegistries.HOLLOW_BEAM.get(), HollowBeamRenderer::new);
    }

    /** A máscara do Vazio / Vasto Lorde nos modelos de jogador (as duas peles). */
    @SuppressWarnings("unchecked")
    private static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r) {
                var pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                pr.addLayer(new HollowMaskLayer(pr));
            }
        }
    }

    private void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            for (Item blade : ShinigamiRegistries.blades()) {
                ItemProperties.register(blade, IsekaiMahou.id("release"), (stack, level, entity, seed) -> ZanpakutoItem.release(stack));
                ItemProperties.register(blade, IsekaiMahou.id("archetype"), (stack, level, entity, seed) -> ZanpakutoItem.archetype(stack));
                // Senbonzakura: enquanto o enxame é controlado, só o cabo fica na mão
                ItemProperties.register(blade, IsekaiMahou.id("petals_out"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                && ZanpakutoItem.archetype(stack) == Archetype.PETALS.counterValue() && ZanpakutoItem.release(stack) > 0 ? 1F : 0F);
            }
        });
    }

    private void onGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("shinigami_release"), ShinigamiHud::render);
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, IsekaiMahou.id("shinigami_fx_overlay"), ShinigamiFxClient::overlay);
    }

    /** Arquétipo do jogador local (paisagem do Mundo Interior): -1 fora dele. */
    private static int innerLandscape() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !InnerWorld.is(mc.level)) return -1;
        MagicData d = ClientMagic.data();
        Archetype a = d == null ? null : Shinigami.archetype(d);
        return a == null ? 0 : a.counterValue();
    }

    private static final int[] FOG = {0xB8BCC8, 0x5A606E, 0xF6C4DA, 0xDCF2FF, 0x6A2414, 0xF4F4F4};

    private static void onFogColor(ViewportEvent.ComputeFogColor e) {
        int land = innerLandscape();
        if (land < 0) return;
        int c = FOG[land];
        e.setRed(((c >> 16) & 0xFF) / 255F);
        e.setGreen(((c >> 8) & 0xFF) / 255F);
        e.setBlue((c & 0xFF) / 255F);
    }

    /** Névoa mais fechada no Mundo sem nome (Mar de Névoa) e no Templo da Chama (fumaça). */
    private static void onFog(ViewportEvent.RenderFog e) {
        int land = innerLandscape();
        if ((land != 0 && land != 4) || e.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
        e.setNearPlaneDistance(land == 0 ? 6F : 14F);
        e.setFarPlaneDistance(land == 0 ? 56F : 90F);
        e.setCanceled(true);
    }
}
