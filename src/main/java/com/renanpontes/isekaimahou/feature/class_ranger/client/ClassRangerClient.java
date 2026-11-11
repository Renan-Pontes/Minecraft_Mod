package com.renanpontes.isekaimahou.feature.class_ranger.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerRegistries;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerWolfEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.SpiritEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerArmorItem;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerBowItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Parte de cliente do módulo "class_ranger": renderers GeckoLib dos companheiros (textura pelo estágio), da Águia
 * Espiritual, o Traje Selvagem 3D, a puxada dos Arcos do Ranger e a aba "Trilha do Ranger" do menu B.
 */
public class ClassRangerClient implements ClientModule {
    public static final String TAB_ID = "ranger_trail";
    private static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");
    private static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");

    @Override
    public void initClient(IEventBus modBus) {
        RangerArmorItem.renderFactory = RangerArmorRenderer.Provider::new;
        modBus.addListener(ClassRangerClient::renderers);
        modBus.addListener(ClassRangerClient::setup);
        MenuTabs.register(TAB_ID, 86, Component.translatable("screen.isekai_mahou.class_ranger.tab"), RangerTrailTab::new);
        MenuTabs.setIcon(TAB_ID, IsekaiMahou.id("textures/gui/tab/ranger_trail.png"));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(RangerRegistries.WOLF.get(), ctx -> new RangerGeoRenderer<>(ctx,
                new RangerStageModel<RangerWolfEntity>(RangerWolfEntity.ID, true, RangerWolfEntity::textureStage, 4), 0.6F));
        e.registerEntityRenderer(RangerRegistries.EAGLE.get(), ctx -> new RangerGeoRenderer<>(ctx,
                new RangerStageModel<RangerEagleEntity>(RangerEagleEntity.ID, true, RangerEagleEntity::stage), 0.35F));
        e.registerEntityRenderer(RangerRegistries.STEED.get(), ctx -> new RangerGeoRenderer<>(ctx,
                new RangerStageModel<RangerSteedEntity>(RangerSteedEntity.ID, true, RangerSteedEntity::stage), 0.9F));
        e.registerEntityRenderer(RangerRegistries.SPIRIT_EAGLE.get(), ctx -> new RangerGeoRenderer<SpiritEagleEntity>(ctx,
                new DefaultedEntityGeoModel<SpiritEagleEntity>(IsekaiMahou.id(RangerEagleEntity.ID))
                        .withAltTexture(IsekaiMahou.id(SpiritEagleEntity.ID)), 0F).spirit(0.8F, 3.2F));
        // pacote de técnicas de anime: a corrente conjurada e a linha de bala
        e.registerEntityRenderer(RangerRegistries.CHAIN_TETHER.get(), ChainTetherRenderer::new);
        e.registerEntityRenderer(RangerRegistries.BULLET_LINE.get(), BulletLineRenderer::new);
    }

    /** Puxada dos arcos (o vanilla só registra para o arco comum), com a puxada mais curta de cada grau. */
    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            bow(RangerRegistries.BOW);
            RangerRegistries.BOW_TIERS.forEach(ClassRangerClient::bow);
        });
    }

    private static void bow(DeferredItem<RangerBowItem> holder) {
        RangerBowItem bow = holder.get();
        ItemProperties.register(bow, PULL, (stack, level, e, seed) -> e == null || e.getUseItem() != stack ? 0F
                : (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) bow.drawTicks());
        ItemProperties.register(bow, PULLING, (stack, level, e, seed) -> e != null && e.isUsingItem() && e.getUseItem() == stack ? 1F : 0F);
    }
}
