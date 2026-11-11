package com.renanpontes.isekaimahou.feature.guild.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.item.FactionBoardItem;
import com.renanpontes.isekaimahou.feature.guild.item.GuildCardInfo;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do modulo "guild": renderers dos NPCs, tela da Guilda, diálogo do aventureiro, aba "Guilda" do menu B,
 * linhas do rastreador de objetivos (missões e companheiros), a cor do cartão e o modelo do Quadro de Facção por facção.
 */
public class GuildClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        modBus.addListener(this::onSetup);
        MenuTabs.register("guild", 60, Component.translatable("screen.isekai_mahou.guild.menu_tab"), GuildTab::new);
        // Missões ativas e a prova de promoção no rastreador de objetivos (tecla J).
        GuildObjectives.register();
        GuildNetwork.openScreen = data -> Minecraft.getInstance().execute(() -> GuildScreen.openOrUpdate(data));
        GuildNetwork.openAdvDialog = data -> Minecraft.getInstance().execute(() -> AdventurerDialogScreen.openOrUpdate(data));
        GuildNetwork.openGuildTab = () -> Minecraft.getInstance().execute(() -> StatusScreen.open("guild"));
        // Ao sair do mundo/servidor, esquece o estado da Guilda do jogador anterior.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            GuildNetwork.clientState = GuildNetwork.State.EMPTY;
            GuildNetwork.clientCompanions = java.util.List.of();
        });
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(GuildContent.RECEPTIONIST.get(), ctx -> new GuildNpcRenderer<>(ctx, "guild_receptionist", 0.45F));
        e.registerEntityRenderer(GuildContent.MASTER.get(), ctx -> new GuildNpcRenderer<>(ctx, "guild_master", 0.6F));
        e.registerEntityRenderer(GuildContent.ADVENTURER.get(), ctx -> new GuildNpcRenderer<>(ctx, "adventurer", 0.45F));
        // v0.9: estandarte das Áreas de Caça.
        e.registerEntityRenderer(GuildContent.HUNT_BANNER.get(), HuntBannerRenderer::new);
    }

    /**
     * Cartão: o modelo troca de cor conforme o rank (0.1 = F ... 0.7 = S).
     * Quadro de Facção: o modelo do item segue a facção do componente block_state (0 = Guilda, 0.1 = Reino ... 0.4 = Submundo).
     */
    private void onSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            ItemProperties.register(GuildContent.ADVENTURER_CARD.get(), IsekaiMahou.id("rank"), (stack, level, entity, seed) -> {
                GuildCardInfo info = stack.get(GuildContent.CARD.get());
                return info == null ? 0.0F : info.rank() / 10.0F;
            });
            ItemProperties.register(GuildContent.FACTION_BOARD_ITEM.get(), IsekaiMahou.id("faction"),
                    (stack, level, entity, seed) -> FactionBoardItem.factionOf(stack).ordinal() / 10.0F);
        });
    }
}
