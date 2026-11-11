package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do Ritual do Druida: a tela da Árvore Coração (S→C) e o pedido de crescer (C→S). */
public final class DruidNetwork {
    /** Gancho de cliente: abre/atualiza a tela da Árvore Coração (o RitualDruidClient substitui). */
    public static Consumer<TreeView> openView = v -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ritual_druid-1");
        r.playToClient(TreeView.TYPE, TreeView.STREAM_CODEC, (p, c) -> openView.accept(p));
        r.playToServer(Grow.TYPE, Grow.STREAM_CODEC, (p, c) -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            BlockPos pos = p.pos();
            if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12 * 12) return;
            if (sp.serverLevel().isLoaded(pos) && sp.serverLevel().getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity core) {
                DruidProgress.tryAdvance(sp, core);
            }
        });
    }

    /** Monta a visão da árvore para quem clicou (o dono vê o progresso da tarefa; os outros, só a Harmonia). */
    public static void sendView(ServerPlayer viewer, HeartTreeCoreBlockEntity core, ServerLevel level) {
        boolean owner = core.isOwner(viewer);
        ServerPlayer ownerPlayer = owner ? viewer
                : core.owner() == null ? null : level.getServer().getPlayerList().getPlayer(core.owner());
        DruidProgress.Status st = DruidProgress.status(level, core, ownerPlayer, true);
        List<OfferLine> offers = new ArrayList<>();
        DruidStages.Stage next = st.next();
        if (next != null) {
            for (int i = 0; i < next.offerings().size(); i++) {
                offers.add(new OfferLine(next.offerings().get(i).stack(), st.delivered()[i]));
            }
        }
        PacketDistributor.sendToPlayer(viewer, new TreeView(core.getBlockPos(), core.ownerName(), owner, core.stage(),
                core.isGrowing(), st.harmony(), next == null ? -1 : next.harmony(), offers,
                next == null ? -1 : next.task().ordinal(), st.taskDone(), next == null ? 0 : next.amount(),
                next == null ? -1 : next.season().ordinal(), next == null ? -1 : next.spirit().ordinal()));
    }

    // =====================================================================================

    /** Uma linha da oferenda: item pedido (com a quantidade) e quantos já foram entregues. */
    public record OfferLine(ItemStack stack, int delivered) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OfferLine> STREAM_CODEC = StreamCodec.of(
                (buf, o) -> {
                    ItemStack.STREAM_CODEC.encode(buf, o.stack());
                    buf.writeVarInt(o.delivered());
                },
                buf -> new OfferLine(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt()));
    }

    /** Tudo que a tela da Árvore Coração mostra. */
    public record TreeView(BlockPos pos, String ownerName, boolean isOwner, int stage, boolean growing, Harmony harmony,
                           int harmonyRequired, List<OfferLine> offerings, int task, int taskDone, int taskRequired,
                           int season, int spirit) implements CustomPacketPayload {
        public static final Type<TreeView> TYPE = new Type<>(IsekaiMahou.id("ritual_druid_tree_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos());
                    buf.writeUtf(v.ownerName(), 64);
                    buf.writeBoolean(v.isOwner());
                    buf.writeVarInt(v.stage());
                    buf.writeBoolean(v.growing());
                    v.harmony().write(buf);
                    buf.writeInt(v.harmonyRequired());
                    buf.writeVarInt(v.offerings().size());
                    for (OfferLine o : v.offerings()) OfferLine.STREAM_CODEC.encode(buf, o);
                    buf.writeInt(v.task());
                    buf.writeInt(v.taskDone());
                    buf.writeVarInt(v.taskRequired());
                    buf.writeInt(v.season());
                    buf.writeInt(v.spirit());
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    String name = buf.readUtf(64);
                    boolean owner = buf.readBoolean();
                    int stage = buf.readVarInt();
                    boolean growing = buf.readBoolean();
                    Harmony h = Harmony.read(buf);
                    int req = buf.readInt();
                    int n = buf.readVarInt();
                    List<OfferLine> offers = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) offers.add(OfferLine.STREAM_CODEC.decode(buf));
                    return new TreeView(pos, name, owner, stage, growing, h, req, offers, buf.readInt(), buf.readInt(),
                            buf.readVarInt(), buf.readInt(), buf.readInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Botão "Crescer" da tela. */
    public record Grow(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Grow> TYPE = new Type<>(IsekaiMahou.id("ritual_druid_grow"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Grow> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Grow::pos, Grow::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private DruidNetwork() {}
}
