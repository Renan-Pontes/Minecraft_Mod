package com.renanpontes.isekaimahou.core.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do núcleo A1 (todos servidor → cliente). Os handlers chamam código de cliente SÓ dentro do lambda: o servidor
 * dedicado nunca carrega classes de {@code client/}.
 */
public final class StatusPayloads {

    /** Uma condição numa entidade: tipo (ordinal de Statuses.Kind), acúmulos e ticks restantes (-1 = infinita). */
    public record Entry(int kind, int stacks, int ticks) {}

    /** Lista COMPLETA das condições de uma entidade (vazia = não tem mais nenhuma). */
    public record StatusList(int entityId, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<StatusList> TYPE = new Type<>(IsekaiMahou.id("core_status_list"));
        public static final StreamCodec<FriendlyByteBuf, StatusList> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeVarInt(p.entries.size());
                    for (Entry e : p.entries) {
                        buf.writeByte(e.kind);
                        buf.writeByte(e.stacks);
                        buf.writeVarInt(e.ticks + 1);
                    }
                },
                buf -> {
                    int id = buf.readVarInt();
                    int n = Math.min(64, buf.readVarInt());
                    List<Entry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(new Entry(buf.readByte(), buf.readByte(), buf.readVarInt() - 1));
                    return new StatusList(id, list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Texto flutuante de reação sobre o alvo ({@code reaction} = ordinal de ElementReactions.Reaction). */
    public record ReactionText(int entityId, int reaction, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<ReactionText> TYPE = new Type<>(IsekaiMahou.id("core_status_reaction"));
        public static final StreamCodec<FriendlyByteBuf, ReactionText> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeByte(p.reaction);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                },
                buf -> new ReactionText(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O jogador entrou num lugar e/ou saiu de outro. */
    public record PlaceChange(Optional<Places.Place> entered, Optional<Places.Place> left, boolean firstVisit)
            implements CustomPacketPayload {
        public static final Type<PlaceChange> TYPE = new Type<>(IsekaiMahou.id("core_status_place"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlaceChange> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeOptional(buf, p.entered);
                    writeOptional(buf, p.left);
                    buf.writeBoolean(p.firstVisit);
                },
                buf -> new PlaceChange(readOptional(buf), readOptional(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeOptional(RegistryFriendlyByteBuf buf, Optional<Places.Place> place) {
        buf.writeBoolean(place.isPresent());
        place.ifPresent(p -> writePlace(buf, p));
    }

    private static Optional<Places.Place> readOptional(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(readPlace(buf)) : Optional.empty();
    }

    static void writePlace(RegistryFriendlyByteBuf buf, Places.Place p) {
        buf.writeUtf(p.id(), 128);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.name());
        buf.writeUtf(p.type(), 64);
        buf.writeUtf(p.faction(), 64);
        buf.writeResourceKey(p.dim());
        BoundingBox b = p.box();
        buf.writeInt(b.minX());
        buf.writeInt(b.minY());
        buf.writeInt(b.minZ());
        buf.writeInt(b.maxX());
        buf.writeInt(b.maxY());
        buf.writeInt(b.maxZ());
        buf.writeBlockPos(p.center());
        buf.writeBoolean(p.safeZone());
        buf.writeInt(p.color());
    }

    static Places.Place readPlace(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf(128);
        var name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        String type = buf.readUtf(64);
        String faction = buf.readUtf(64);
        ResourceKey<net.minecraft.world.level.Level> dim = buf.readResourceKey(Registries.DIMENSION);
        BoundingBox box = new BoundingBox(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        BlockPos center = buf.readBlockPos();
        boolean safe = buf.readBoolean();
        int color = buf.readInt();
        return new Places.Place(id, name, type, faction, dim, box, center, safe, color);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(StatusList.TYPE, StatusList.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.status.StatusClientHandlers.statusList(p));
        r.playToClient(ReactionText.TYPE, ReactionText.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.status.StatusClientHandlers.reaction(p));
        r.playToClient(PlaceChange.TYPE, PlaceChange.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.status.StatusClientHandlers.place(p));
    }

    private StatusPayloads() {}
}
