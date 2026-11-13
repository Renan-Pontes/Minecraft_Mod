package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.settlements.Conquest;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "settlements": renderers (NPCs, Campeão, letreiro das obras, cristal do coração), telas
 * (governo/Juramento e Carta de Fundação), aba "Cidade" no menu B e as linhas do rastreador (Juramento e obras).
 */
public class SettlementsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        SettlementsNetwork.onState = tag -> {
            ClientSettlements.setState(tag);
            Minecraft mc = Minecraft.getInstance();
            if (tag.getBoolean("Open") && !(mc.screen instanceof SettlementScreen)) mc.setScreen(new SettlementScreen());
        };
        SettlementsNetwork.onFound = pos -> Minecraft.getInstance().setScreen(new FoundingScreen(pos, CityPlacer.foundRot()));
        // v1.0 (city_plan): prévias no mundo — holograma da fundação e prédio em posição livre
        SettlementsNetwork.onProbe = CityPlacer::onProbe;
        // onda 7 (sky_ui): névoa de nuvem na borda das Ilhas Celestes e no pé das cachoeiras
        SkyFx.init();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> CityPlacer.tick());
        NeoForge.EVENT_BUS.addListener(CityPlacer::onRender);
        NeoForge.EVENT_BUS.addListener(CityPlacer::onInteract);
        NeoForge.EVENT_BUS.addListener(CityPlacer::onKey);
        NeoForge.EVENT_BUS.addListener(CityPlacer::onScroll);
        SettlementsNetwork.onTracker = ClientSettlements::setTracker;
        MenuTabs.register("settlement", 62, Component.translatable("settlements.isekai_mahou.ui.tab"), SettlementTab::new);
        MenuTabs.setIcon("settlement", IsekaiMahou.id("textures/gui/settlements/tab.png"));
        ObjectiveTracker.register("settlements_oath", 320, SettlementsClient::oathLines);
        ObjectiveTracker.register("settlements_build", 180, SettlementsClient::buildLines);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientSettlements.clear();
            CityPlacer.reset();
        });
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(SettlementsContent.BUILDER.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.RESIDENT.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.GUARD.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.MERCHANT.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.MILITIA.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.TRADER.get(), SettlementNpcRenderer::new);
        e.registerEntityRenderer(SettlementsContent.CHAMPION.get(), CityChampionRenderer::new);
        e.registerEntityRenderer(SettlementsContent.SITE_SIGN.get(), SiteSignRenderer::new);
        e.registerBlockEntityRenderer(SettlementsContent.HEART_BE.get(), SettlementHeartRenderer::new);
    }

    /** Juramento em andamento: as três provas e, com o Selo, o desafio no coração. */
    private static List<ObjectiveLine> oathLines() {
        CompoundTag t = ClientSettlements.tracker();
        if (!t.contains("OathCity")) return List.of();
        Component city = ClientSettlements.text(t, "OathCity");
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("OathDim"));
        ResourceKey<Level> dim = dimId == null ? null : ResourceKey.create(Registries.DIMENSION, dimId);
        var pos = NbtUtils.readBlockPos(t, "OathPos").orElse(null);
        List<ObjectiveLine> out = new ArrayList<>();
        if (t.getBoolean("OathSeal")) {
            ObjectiveLine l = ObjectiveLine.of(Component.translatable("settlements.isekai_mahou.track.challenge", city),
                    Component.translatable("settlements.isekai_mahou.track.challenge.detail"), 0xFFD86B,
                    new ItemStack(SettlementsContent.OATH_SEAL.get()));
            out.add(pos != null ? l.withTarget(pos, dim) : l);
            return out;
        }
        int done = (t.getInt("OathKills") >= Conquest.KILLS_NEEDED ? 1 : 0) + (t.getInt("OathTribute") >= Conquest.TRIBUTE_NEEDED ? 1 : 0)
                + (t.getInt("OathVigil") >= Conquest.VIGIL_NEEDED ? 1 : 0);
        Component detail = Component.translatable("settlements.isekai_mahou.track.oath.detail",
                Math.min(t.getInt("OathKills"), Conquest.KILLS_NEEDED), Conquest.KILLS_NEEDED,
                Math.min(t.getInt("OathTribute"), Conquest.TRIBUTE_NEEDED), Conquest.TRIBUTE_NEEDED,
                Math.min(t.getInt("OathVigil"), Conquest.VIGIL_NEEDED), Conquest.VIGIL_NEEDED);
        ObjectiveLine l = ObjectiveLine.progress(Component.translatable("settlements.isekai_mahou.track.oath", city), detail, done, 3,
                0xFFD86B, new ItemStack(Items.WRITABLE_BOOK));
        out.add(pos != null ? l.withTarget(pos, dim) : l);
        return out;
    }

    /** Obra da cidade própria (a primeira da fila). */
    private static List<ObjectiveLine> buildLines() {
        CompoundTag t = ClientSettlements.tracker();
        if (!t.contains("BuildKey") || t.getString("BuildKey").isEmpty()) return List.of();
        Component what = t.contains("BuildName") ? ClientSettlements.text(t, "BuildName") : Component.translatable(t.getString("BuildKey"));
        Component title = Component.translatable("settlements.isekai_mahou.track.build", what);
        Component detail = Component.translatable("settlements.isekai_mahou.track.build.detail", t.getString("BuildCity"), t.getInt("BuildQueue"));
        return List.of(ObjectiveLine.progress(title, detail, t.getInt("BuildPct"), 100, 0xC9A27A, new ItemStack(Items.BRICKS)));
    }
}
