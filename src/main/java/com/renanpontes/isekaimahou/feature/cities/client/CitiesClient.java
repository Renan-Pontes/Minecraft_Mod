package com.renanpontes.isekaimahou.feature.cities.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.cities.CitiesContent;
import com.renanpontes.isekaimahou.feature.cities.CityRealm;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente das Cidades: renderers dos NPCs e do coração, tela de diálogo e as linhas do rastreador de
 * objetivos (rumor ativo com seta; "Procurado — pague a fiança" com seta para o guarda mais perto).
 */
public class CitiesClient implements ClientModule {
    /** Rumor ativo (do servidor). */
    @Nullable private static CitiesNetwork.Rumor rumor;
    /** Reinos (facções) que procuram o jogador (do servidor). */
    private static List<String> wantedIn = List.of();
    /** Abordagem da guarda em andamento (v1.0; null = nenhuma). */
    @Nullable private static CitiesNetwork.Confront confront;
    /** Tick do cliente em que a abordagem chegou (para a contagem andar entre os pacotes). */
    private static long confrontAt;
    /** Pedido (sidequest) ativo (release 1.0; null = nenhum). */
    @Nullable private static CitiesNetwork.QuestView quest;

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        CitiesNetwork.openDialogue = d -> Minecraft.getInstance().execute(() -> CityDialogueScreen.openOrUpdate(d));
        CitiesNetwork.rumor = r -> Minecraft.getInstance().execute(() -> rumor = r.active() ? r : null);
        CitiesNetwork.wanted = w -> Minecraft.getInstance().execute(() -> wantedIn = List.copyOf(w.factions()));
        CitiesNetwork.confront = c -> Minecraft.getInstance().execute(() -> {
            confront = c.guardId() < 0 ? null : c;
            confrontAt = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        });
        CitiesNetwork.questView = q -> Minecraft.getInstance().execute(() -> quest = q.active() ? q : null);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            quest = null;
            rumor = null;
            wantedIn = List.of();
            confront = null;
        });
        ObjectiveTracker.register("cities_confront", 950, CitiesClient::confrontLines);
        ObjectiveTracker.register("cities_wanted", 900, CitiesClient::wantedLines);
        ObjectiveTracker.register("cities_quest", 300, CitiesClient::questLines);
        ObjectiveTracker.register("cities_rumor", 250, CitiesClient::rumorLines);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CitiesContent.CITY_GUARD.get(), CityNpcRenderer::new);
        e.registerEntityRenderer(CitiesContent.CITIZEN.get(), CityNpcRenderer::new);
        e.registerEntityRenderer(CitiesContent.CITY_MERCHANT.get(), CityNpcRenderer::new);
        e.registerEntityRenderer(CitiesContent.CITY_LORD.get(), CityNpcRenderer::new);
        e.registerBlockEntityRenderer(CitiesContent.CITY_HEART_BE.get(), CityHeartRenderer::new);
    }

    /** O pedido ativo: título, o passo atual, barra de progresso (caça/coleta) ou seta (lugar/volta). */
    private static List<ObjectiveLine> questLines() {
        CitiesNetwork.QuestView q = quest;
        if (q == null) return List.of();
        Component title = Component.translatable("objective.isekai_mahou.cities.quest", q.title());
        ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);
        ObjectiveLine line = q.max() > 0 ? ObjectiveLine.progress(title, q.detail(), q.cur(), q.max(), 0xFFE9A8, icon)
                : ObjectiveLine.of(title, q.detail(), 0xFFE9A8, icon);
        return List.of(q.arrow() ? line.withTarget(q.pos(), ResourceKey.create(Registries.DIMENSION, q.dim())) : line);
    }

    private static List<ObjectiveLine> rumorLines() {
        CitiesNetwork.Rumor r = rumor;
        if (r == null) return List.of();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, r.dim());
        if (r.guide()) {
            return List.of(ObjectiveLine.of(Component.translatable("objective.isekai_mahou.cities.guide", r.name()),
                    Component.translatable("objective.isekai_mahou.cities.guide.detail"), 0xB98CFF, new ItemStack(Items.COMPASS))
                    .withTarget(r.pos(), dim));
        }
        return List.of(ObjectiveLine.of(Component.translatable("objective.isekai_mahou.cities.rumor", r.name()),
                Component.translatable("objective.isekai_mahou.cities.rumor.detail"), 0xE0C070, new ItemStack(Items.MAP))
                .withTarget(r.pos(), dim));
    }

    /** "Abordado pelo guarda X" com a seta para ele e o tempo que falta (esperar / responder). */
    private static List<ObjectiveLine> confrontLines() {
        CitiesNetwork.Confront c = confront;
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc.level == null || mc.player == null) return List.of();
        int secs = Math.max(0, c.seconds() - (int) ((mc.level.getGameTime() - confrontAt) / 20));
        Component title = Component.translatable("objective.isekai_mahou.cities.confront", c.guardName());
        Component detail = Component.translatable(c.talking() ? "objective.isekai_mahou.cities.confront.talk"
                : "objective.isekai_mahou.cities.confront.wait", secs);
        ObjectiveLine line = ObjectiveLine.of(title, detail, 0xF07A3A, new ItemStack(Items.BELL));
        net.minecraft.world.entity.Entity g = mc.level.getEntity(c.guardId());
        return List.of(g != null ? line.withTarget(new Vec3(g.getX(), g.getY(), g.getZ()), mc.level.dimension()) : line);
    }

    private static List<ObjectiveLine> wantedLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        MobEffectInstance w = mc.player.getEffect(CitiesContent.WANTED);
        if (w == null) return List.of();
        int secs = w.getDuration() / 20;
        Component detail = Component.translatable("objective.isekai_mahou.cities.wanted.detail", secs / 60, String.format("%02d", secs % 60));
        List<String> where = wantedIn;
        Component title = Component.translatable("objective.isekai_mahou.cities.wanted");
        if (!where.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < where.size(); i++) {
                CityKingdom k = CityRealm.byFaction(where.get(i));
                if (i > 0) names.append(", ");
                names.append(k != null ? CityRealm.shortName(k).withColor(CityRealm.color(k)) : Component.literal(where.get(i)));
            }
            title = Component.translatable("objective.isekai_mahou.cities.wanted.where", names);
        }
        ObjectiveLine line = ObjectiveLine.of(title, detail, 0xE04A3A, new ItemStack(Items.IRON_BARS));
        // Seta para o guarda mais perto de um reino que procura o jogador (quem aceita a fiança dele).
        CityGuard best = null;
        double bestD = 64 * 64;
        for (CityGuard g : mc.level.getEntitiesOfClass(CityGuard.class, mc.player.getBoundingBox().inflate(64),
                g -> where.isEmpty() || where.contains(g.kingdomFaction()))) {
            double d = g.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        List<ObjectiveLine> out = new ArrayList<>();
        out.add(best != null ? line.withTarget(new Vec3(best.getX(), best.getY(), best.getZ()), mc.level.dimension()) : line);
        return out;
    }
}
