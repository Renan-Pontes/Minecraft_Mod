package com.renanpontes.isekaimahou.feature.travel.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideNet;
import com.renanpontes.isekaimahou.feature.travel.guide.PortalGuide;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado de cliente do Guia de Portais: o portal que o jogador está rastreando (seta no rastreador de objetivos), a
 * última resposta do servidor (para a tela mostrar "nenhum por perto" etc.) e a chegada (a seta some sozinha).
 */
final class GuideClient {
    static final String TRACKER_ID = "portal_guide";
    /** Acima da história do Diário (500): o jogador pediu este rastreio agora. */
    private static final int TRACKER_PRIORITY = 550;
    private static final double ARRIVE = 20, REFINE = 160;

    record Target(String id, ResourceKey<Level> dim, BlockPos pos, boolean exact) {}

    @Nullable
    private static Target target;
    /** Última resposta: status (-1 = nenhuma), de qual dimensão, alcance da busca e quando. */
    static int lastStatus = -1;
    static String lastId = "";
    static int lastReach;
    private static boolean pending;
    private static long pendingSince;
    /** Já pediu a posição exata deste alvo (ao chegar perto de um ponto "do mapa"). */
    private static boolean refined;
    private static int timer;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    static void init() {
        GuideNet.onTarget = GuideClient::onTarget;
        ObjectiveTracker.register(TRACKER_ID, TRACKER_PRIORITY, GuideClient::lines);
    }

    @Nullable
    static Target target() {
        return target;
    }

    static boolean tracking(String id) {
        return target != null && target.id().equals(id);
    }

    static boolean pending() {
        return pending && Util.getMillis() - pendingSince < 8000;
    }

    /** Pede ao servidor o portal mais próximo ("" = parar). */
    static void request(String id) {
        pending = !id.isEmpty();
        pendingSince = Util.getMillis();
        if (id.isEmpty()) {
            target = null;
            lastStatus = -1;
        } else {
            lastId = id;
        }
        PacketDistributor.sendToServer(new GuideNet.TrackPortal(id));
    }

    private static void onTarget(GuideNet.PortalTarget p) {
        // Resposta a um clique de agora (e não o alvo antigo devolvido ao entrar no jogo)?
        boolean asked = pending;
        pending = false;
        if (p.status() == GuideNet.CLEARED) {
            target = null;
            return;
        }
        lastStatus = p.status();
        lastId = p.id();
        lastReach = p.reach();
        if (p.status() != GuideNet.FOUND) return;
        boolean sameAsBefore = target != null && target.id().equals(p.id());
        target = new Target(p.id(), ResourceKey.create(Registries.DIMENSION, p.dim()), p.pos(), p.exact());
        if (!sameAsBefore) refined = false;
        // O jogador pediu a seta agora: o rastreador aparece mesmo que estivesse fechado
        if (asked && !ObjectiveTracker.isEnabled()) ObjectiveTracker.setVisible(true);
        ObjectiveTracker.refresh();
    }

    static void tick() {
        if (target == null || ++timer % 10 != 0) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.level().dimension().equals(target.dim())) return;
        double dist = Math.hypot(target.pos().getX() + 0.5 - p.getX(), target.pos().getZ() + 0.5 - p.getZ());
        if (dist <= ARRIVE) {
            PortalGuide.Entry e = PortalGuide.get(target.id());
            if (e != null) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.translatable("guide.isekai_mahou.portals.arrived", e.site()).withStyle(ChatFormatting.GOLD), false);
            }
            request("");
            ObjectiveTracker.refresh();
        } else if (!target.exact() && !refined && dist <= REFINE) {
            // Perto de um ponto "do mapa": a estrutura já carregou, o servidor agora sabe a posição certa
            // (pedido silencioso: não mexe no "procurando..." da tela nem reabre o rastreador)
            refined = true;
            PacketDistributor.sendToServer(new GuideNet.TrackPortal(target.id()));
        }
    }

    static void reset() {
        target = null;
        lastStatus = -1;
        lastId = "";
        pending = false;
        refined = false;
    }

    private static List<ObjectiveLine> lines() {
        Target t = target;
        LocalPlayer p = Minecraft.getInstance().player;
        if (t == null || p == null) return List.of();
        PortalGuide.Entry e = PortalGuide.get(t.id());
        if (e == null) return List.of();
        MutableComponent title = Component.translatable("guide.isekai_mahou.portals.tracker", e.site());
        Component detail = t.exact() ? e.name() : Component.translatable("guide.isekai_mahou.portals.tracker_approx", e.name());
        double y = t.exact() ? t.pos().getY() + 0.5 : p.getY();
        return List.of(ObjectiveLine.of(title, detail, e.color(), icon(e))
                .withTarget(new Vec3(t.pos().getX() + 0.5, y, t.pos().getZ() + 0.5), t.dim()));
    }

    /** Ícone da dimensão (item pelo id em texto; reserva: Olho do Fim). */
    static ItemStack icon(PortalGuide.Entry e) {
        return ICONS.computeIfAbsent(e.id(), k -> {
            ResourceLocation id = ResourceLocation.tryParse(e.icon());
            return id == null ? new ItemStack(Items.ENDER_EYE)
                    : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.ENDER_EYE));
        });
    }

    /** "812 blocos a nordeste" a partir do jogador (só na mesma dimensão do alvo). */
    @Nullable
    static Component distanceText(Target t) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.level().dimension().equals(t.dim())) return null;
        double dx = t.pos().getX() + 0.5 - p.getX(), dz = t.pos().getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.hypot(dx, dz));
        // 0 = norte (-Z), sentido horário
        int idx = Mth.positiveModulo((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return Component.translatable("guide.isekai_mahou.portals.distance", dist,
                Component.translatable("guide.isekai_mahou.portals.dir." + dirs[idx]));
    }

    private GuideClient() {}
}
