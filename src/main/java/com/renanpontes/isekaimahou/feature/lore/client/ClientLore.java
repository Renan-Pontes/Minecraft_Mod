package com.renanpontes.isekaimahou.feature.lore.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.lore.LoreContent;
import com.renanpontes.isekaimahou.feature.lore.LoreData;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments;
import com.renanpontes.isekaimahou.feature.lore.LoreNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado de cliente do módulo lore: a cópia dos dados de lore do jogador (formato em {@link LoreData}), o provedor do
 * rastreador de objetivos (seta para o lugar do fragmento seguido) e utilidades das telas.
 */
public final class ClientLore {
    static final String TRACKER_ID = "lore";
    /** Acima da história do Diário (500) e abaixo do Guia de Portais (550): o jogador pediu este rastreio. */
    private static final int TRACKER_PRIORITY = 540;

    private static CompoundTag data = new CompoundTag();
    /** Sobe a cada sincronização (as telas refazem as listas quando muda). */
    static int version;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private ClientLore() {}

    /** Dados de lore do jogador local (leia com os métodos de {@link LoreData}). */
    public static CompoundTag data() {
        return data;
    }

    static void init() {
        LoreNetwork.onSync = p -> {
            data = p.data();
            version++;
            ObjectiveTracker.refresh();
        };
        LoreNetwork.onRead = ClientLore::onRead;
        ObjectiveTracker.register(TRACKER_ID, TRACKER_PRIORITY, ClientLore::lines);
    }

    static void reset() {
        data = new CompoundTag();
        version++;
    }

    static void send(int op, String fragment) {
        PacketDistributor.sendToServer(new LoreNetwork.Action(op, fragment));
    }

    /** O jogador acabou de ler um fragmento: abre a página (sem atropelar outra tela que esteja aberta). */
    private static void onRead(LoreNetwork.Read p) {
        Minecraft mc = Minecraft.getInstance();
        LoreFragments.Fragment f = LoreFragments.get(p.fragment());
        if (f == null || mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof LorePageScreen) && !(mc.screen instanceof ChatScreen)) return;
        mc.setScreen(new LorePageScreen(f, p.fresh(), p.setComplete()));
    }

    // ------------------------------------------------------------------ rastreador
    private static List<ObjectiveLine> lines() {
        LocalPlayer p = Minecraft.getInstance().player;
        String id = LoreData.tracked(data);
        if (p == null || id.isEmpty()) return List.of();
        LoreFragments.Fragment f = LoreFragments.get(id);
        LoreData.Spot spot = LoreData.spot(data, id);
        if (f == null || spot == null) return List.of();
        LoreFragments.LoreSet set = LoreFragments.set(f.set());
        double y = spot.exactY() ? spot.pos().getY() + 0.5 : p.getY();
        return List.of(ObjectiveLine.of(f.placeName(), f.title(), set == null ? 0xD8B45A : set.color(), pageIcon())
                .withTarget(new Vec3(spot.pos().getX() + 0.5, y, spot.pos().getZ() + 0.5), spot.dim()));
    }

    // ------------------------------------------------------------------ utilidades das telas
    static ItemStack pageIcon() {
        return ICONS.computeIfAbsent("#page", k -> new ItemStack(LoreContent.LORE_PAGE.get()));
    }

    /** Item do ícone pelo id em texto (reserva: livro). */
    static ItemStack icon(String itemId) {
        return ICONS.computeIfAbsent(itemId, k -> {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            return id == null ? new ItemStack(Items.BOOK)
                    : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BOOK));
        });
    }

    /** "812 blocos a nordeste" a partir do jogador (null se o lugar é em outra dimensão). */
    @Nullable
    static Component distanceText(LoreData.Spot spot) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.level().dimension() != spot.dim()) return null;
        double dx = spot.pos().getX() + 0.5 - p.getX(), dz = spot.pos().getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.hypot(dx, dz));
        // 0 = norte (-Z), sentido horário
        int idx = Mth.positiveModulo((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return Component.translatable("lore.isekai_mahou.distance", dist, Component.translatable("lore.isekai_mahou.dir." + dirs[idx]));
    }
}
