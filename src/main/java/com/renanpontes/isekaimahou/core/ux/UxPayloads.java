package com.renanpontes.isekaimahou.core.ux;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do núcleo v1.0 — A3 (UX). Ids {@code isekai_mahou:core_ux_*}. */
public final class UxPayloads {
    private static final int MAX_ID = 128;

    /** Servidor → cliente: TODAS as arenas da dimensão {@code dim} (substitui a lista do cliente). */
    public record ArenaSync(ResourceKey<Level> dim, List<ArenaBounds.Arena> arenas) implements CustomPacketPayload {
        public static final Type<ArenaSync> TYPE = new Type<>(IsekaiMahou.id("core_ux_arena_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeResourceKey(p.dim);
                    int n = Math.min(p.arenas.size(), ArenaBounds.MAX_SYNCED);
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) writeArena(buf, p.arenas.get(i));
                },
                buf -> {
                    ResourceKey<Level> dim = buf.readResourceKey(Registries.DIMENSION);
                    int n = Math.min(buf.readVarInt(), ArenaBounds.MAX_SYNCED);
                    List<ArenaBounds.Arena> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(readArena(buf, dim));
                    return new ArenaSync(dim, List.copyOf(list));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeArena(RegistryFriendlyByteBuf buf, ArenaBounds.Arena a) {
        buf.writeUtf(a.id(), MAX_ID);
        buf.writeByte(a.shape().ordinal());
        buf.writeDouble(a.center().x);
        buf.writeDouble(a.center().y);
        buf.writeDouble(a.center().z);
        buf.writeDouble(a.radiusX());
        buf.writeDouble(a.radiusZ());
        buf.writeDouble(a.minY());
        buf.writeDouble(a.maxY());
        buf.writeInt(a.rgb());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, a.name());
        buf.writeVarInt(a.warnDistance());
        buf.writeBoolean(a.showWall());
    }

    private static ArenaBounds.Arena readArena(RegistryFriendlyByteBuf buf, ResourceKey<Level> dim) {
        String id = buf.readUtf(MAX_ID);
        int shape = buf.readByte();
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double rx = buf.readDouble(), rz = buf.readDouble(), minY = buf.readDouble(), maxY = buf.readDouble();
        int rgb = buf.readInt();
        Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int warn = buf.readVarInt();
        boolean wall = buf.readBoolean();
        ArenaBounds.Shape[] shapes = ArenaBounds.Shape.values();
        return new ArenaBounds.Arena(id, dim, shapes[Math.floorMod(shape, shapes.length)], center, rx, rz, minY, maxY, rgb, name, warn, wall);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ArenaSync.TYPE, ArenaSync.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.ux.UxClientHandlers.arenaSync(p));
    }

    private UxPayloads() {}
}
