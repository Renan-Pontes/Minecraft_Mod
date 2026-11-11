package com.renanpontes.isekaimahou.feature.class_smith.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.class_smith.SmithNet;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Parte de cliente do Ferreiro: renderer 3D da Lâmina Forjada (montada peça a peça), roupa GeckoLib, martelo da Forja
 * Estelar, fagulhas, tela da Bigorna Arcana e a aba "Forja" do menu B.
 */
public class ClassSmithClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(ClassSmithClient::renderers);
        modBus.addListener(ClassSmithClient::extensions);
        SmithOutfitItem.renderFactory = SmithOutfitRenderer.Provider::new;
        SmithNet.clientOpen = pos -> Minecraft.getInstance().setScreen(new ArcaneAnvilScreen(pos));
        MenuTabs.register("smith_forge", 45, Component.translatable("tab.isekai_mahou.smith_forge"), SmithForgeTab::new);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(SmithRegistries.STAR_HAMMER.get(), SmithEntityRenderers.StarHammer::new);
        e.registerEntityRenderer(SmithRegistries.SPARK.get(), SmithEntityRenderers.Spark::new);
        e.registerEntityRenderer(SmithRegistries.FLYING_BLADE.get(), SmithTechRenderers.Blade::new);
        e.registerEntityRenderer(SmithRegistries.RHO_AIAS.get(), SmithTechRenderers.RhoAias::new);
        e.registerEntityRenderer(SmithRegistries.BLADE_WORLD.get(), SmithTechRenderers.BladeWorld::new);
    }

    private static void extensions(RegisterClientExtensionsEvent e) {
        e.registerItem(new IClientItemExtensions() {
            private ForgedBladeRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ForgedBladeRenderer();
                return renderer;
            }
        }, SmithRegistries.FORGED_BLADE.get());
        e.registerItem(new IClientItemExtensions() {
            private SmithTechRenderers.Projection renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new SmithTechRenderers.Projection();
                return renderer;
            }
        }, SmithRegistries.PROJECTION.get());
    }
}
