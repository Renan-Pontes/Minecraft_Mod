package com.renanpontes.isekaimahou.feature.talents;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo. Servidor → cliente: {@link Sync} (dados do jogador; a árvore da classe em JSON vai junto só quando
 * muda). Cliente → servidor: {@link Spend} (1 ponto num nó) e {@link Pick} (escolher a especialização). O cliente liga o
 * gancho {@link #onSync} no init dele.
 */
public final class TalentsNetwork {
    public static Consumer<CompoundTag> onSync = t -> {};

    /** {@code class} (id), {@code data} (NBT do {@link TalentData}) e, quando mudou, {@code tree} (JSON da árvore). */
    public record Sync(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(IsekaiMahou.id("talents_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Sync::new, Sync::tag);
        @Override public Type<Sync> type() { return TYPE; }
    }

    public record Spend(String node) implements CustomPacketPayload {
        public static final Type<Spend> TYPE = new Type<>(IsekaiMahou.id("talents_spend"));
        public static final StreamCodec<ByteBuf, Spend> STREAM_CODEC = ByteBufCodecs.stringUtf8(64).map(Spend::new, Spend::node);
        @Override public Type<Spend> type() { return TYPE; }
    }

    public record Pick(String subclass) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(IsekaiMahou.id("talents_pick"));
        public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = ByteBufCodecs.stringUtf8(64).map(Pick::new, Pick::subclass);
        @Override public Type<Pick> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("talents-1");
            r.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> onSync.accept(p.tag()));
            r.playToServer(Spend.TYPE, Spend.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp) Talents.spend(sp, p.node());
            });
            r.playToServer(Pick.TYPE, Pick.STREAM_CODEC, (p, c) -> {
                if (!(c.player() instanceof ServerPlayer sp)) return;
                if (Talents.chooseSubclass(sp, p.subclass()) == Talents.Result.OK) announceSubclass(sp, p.subclass());
            });
        });
    }

    /** Uma linha no chat e um som: escolha importante, mas sem estourar a tela. */
    static void announceSubclass(ServerPlayer sp, String id) {
        TalentTree tree = TalentTrees.get(com.renanpontes.isekaimahou.core.MagicApi.get(sp).path().id);
        TalentTree.Subclass sub = tree == null ? null : tree.subclass(id);
        int color = sub == null ? 0xFFD86B : sub.color();
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.talents.subclass_chosen",
                TalentText.subclassName(id).withColor(color)));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    private TalentsNetwork() {}
}
