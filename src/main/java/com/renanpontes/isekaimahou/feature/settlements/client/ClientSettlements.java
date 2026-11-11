package com.renanpontes.isekaimahou.feature.settlements.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cópia no cliente do estado da cidade aberta na tela e das linhas do rastreador. */
public final class ClientSettlements {
    private static CompoundTag state = new CompoundTag();
    private static CompoundTag tracker = new CompoundTag();
    private static int version;

    public static CompoundTag state() {
        return state;
    }

    public static CompoundTag tracker() {
        return tracker;
    }

    public static int version() {
        return version;
    }

    static void setState(CompoundTag tag) {
        state = tag;
        version++;
    }

    static void setTracker(CompoundTag tag) {
        tracker = tag;
    }

    public static void clear() {
        state = new CompoundTag();
        tracker = new CompoundTag();
        version++;
    }

    /** Lê um texto (JSON de Component) mandado pelo servidor. */
    public static Component text(CompoundTag t, String key) {
        String json = t.getString(key);
        if (json.isEmpty()) return Component.empty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Component.literal(json);
        try {
            Component c = Component.Serializer.fromJson(json, mc.level.registryAccess());
            return c == null ? Component.empty() : c;
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    public static void action(String city, String action, String arg) {
        PacketDistributor.sendToServer(new SettlementsNetwork.Action(city, action, arg));
    }

    public static void request(@Nullable String city) {
        PacketDistributor.sendToServer(new SettlementsNetwork.Request(city == null ? "" : city));
    }

    private ClientSettlements() {}
}
