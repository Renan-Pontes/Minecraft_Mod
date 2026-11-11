package com.renanpontes.isekaimahou.feature.class_shinigami;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.SpiritTrials;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Shinigami: a tela de escolha do arquétipo (S→C, aberta pelo espírito no rank II), a escolha (C→S) e o
 * botão "Meditar" da aba (C→S). O servidor confere tudo de novo.
 */
public final class ShinigamiNetwork {
    /** Abre a tela de arquétipos no cliente (preenchido pelo módulo de cliente; nulo no servidor dedicado). */
    public static volatile Runnable clientOpenArchetypes;
    /** Recebe a máscara do Vazio de uma entidade (preenchido pelo módulo de cliente; nulo no servidor dedicado). */
    public static volatile java.util.function.Consumer<HollowMask> clientMask;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_shinigami-1");
        r.playToClient(OpenArchetypes.TYPE, OpenArchetypes.STREAM_CODEC, (p, c) -> {
            Runnable open = clientOpenArchetypes;
            if (open != null) open.run();
        });
        r.playToServer(ChooseArchetype.TYPE, ChooseArchetype.STREAM_CODEC, (p, c) -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            Archetype a = Archetype.byIndex(p.index());
            if (a != null && InnerWorld.inOwn(sp)) SpiritTrials.choose(sp, a);
        });
        r.playToClient(HollowMask.TYPE, HollowMask.STREAM_CODEC, (p, c) -> {
            java.util.function.Consumer<HollowMask> h = clientMask;
            if (h != null) h.accept(p);
        });
        r.playToServer(Meditate.TYPE, Meditate.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && Shinigami.isShinigami(sp) && !Meditation.active(sp)) Meditation.begin(sp);
        });
    }

    /** Servidor → cliente: o espírito pede o nome (abre a escolha do arquétipo). */
    public record OpenArchetypes() implements CustomPacketPayload {
        public static final Type<OpenArchetypes> TYPE = new Type<>(IsekaiMahou.id("class_shinigami_open_archetypes"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenArchetypes> STREAM_CODEC = StreamCodec.unit(new OpenArchetypes());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Escolha do arquétipo (índice de {@link Archetype}). */
    public record ChooseArchetype(int index) implements CustomPacketPayload {
        public static final Type<ChooseArchetype> TYPE = new Type<>(IsekaiMahou.id("class_shinigami_choose_archetype"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseArchetype> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ChooseArchetype::index, ChooseArchetype::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Servidor → cliente: a máscara do Vazio no rosto de uma entidade ({@code kind}: 0 = sem, 1 = Máscara do Vazio,
     * 2 = Vasto Lorde) por {@code ticks}. Reenviado a cada 2 s enquanto dura (para quem chegar perto depois).
     */
    public record HollowMask(int entityId, int kind, int ticks) implements CustomPacketPayload {
        public static final Type<HollowMask> TYPE = new Type<>(IsekaiMahou.id("class_shinigami_hollow_mask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HollowMask> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HollowMask::entityId, ByteBufCodecs.VAR_INT, HollowMask::kind, ByteBufCodecs.VAR_INT, HollowMask::ticks,
                HollowMask::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Botão "Meditar (Jinzen)" da aba. */
    public record Meditate() implements CustomPacketPayload {
        public static final Type<Meditate> TYPE = new Type<>(IsekaiMahou.id("class_shinigami_meditate"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Meditate> STREAM_CODEC = StreamCodec.unit(new Meditate());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ShinigamiNetwork() {}
}
