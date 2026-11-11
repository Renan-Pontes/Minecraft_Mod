package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgeService;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do Ferreiro: abrir a tela da Bigorna Arcana (S→C), editar a lâmina na bigorna (C→S) e subir de rank nas Provas
 * da Forja (C→S). O servidor confere TUDO de novo (bigorna perto, classe, custos). A parte de cliente preenche
 * {@link #clientOpen}; no servidor dedicado ele nunca é chamado.
 */
public final class SmithNet {
    public static final double ANVIL_REACH = 8.0;

    // operações de ForgeEdit
    public static final int OP_PART = 0;      // arg = parte*2 + (1 = aumentar, 0 = diminuir)
    public static final int OP_FAMILY = 1;    // arg = família
    public static final int OP_GRIP = 2;      // arg = empunhadura
    public static final int OP_FINISH = 3;    // arg = acabamento
    public static final int OP_RUNE = 4;      // arg = elemento (−1 = apagar)
    public static final int OP_MATERIAL = 5;  // arg = material
    public static final int OP_TEMPER = 6;
    public static final int OP_FORGE_NEW = 7; // arg = material
    public static final int OP_CONVERT = 8;   // só converte a arma vanilla da mão

    public record OpenAnvil(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenAnvil> TYPE = new Type<>(IsekaiMahou.id("class_smith_open_anvil"));
        public static final StreamCodec<FriendlyByteBuf, OpenAnvil> STREAM_CODEC =
                StreamCodec.of((buf, p) -> buf.writeBlockPos(p.pos), buf -> new OpenAnvil(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ForgeEdit(BlockPos pos, int op, int arg) implements CustomPacketPayload {
        public static final Type<ForgeEdit> TYPE = new Type<>(IsekaiMahou.id("class_smith_forge_edit"));
        public static final StreamCodec<FriendlyByteBuf, ForgeEdit> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.op);
                    buf.writeVarInt(p.arg + 1);
                },
                buf -> new ForgeEdit(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt() - 1));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RankUp(int rank) implements CustomPacketPayload {
        public static final Type<RankUp> TYPE = new Type<>(IsekaiMahou.id("class_smith_rank_up"));
        public static final StreamCodec<FriendlyByteBuf, RankUp> STREAM_CODEC =
                StreamCodec.of((buf, p) -> buf.writeVarInt(p.rank), buf -> new RankUp(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Gancho do cliente (abre a tela). */
    public static volatile Consumer<BlockPos> clientOpen = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_smith-1");
        r.playToClient(OpenAnvil.TYPE, OpenAnvil.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientOpen.accept(p.pos())));
        r.playToServer(ForgeEdit.TYPE, ForgeEdit.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) handleEdit(sp, p);
        }));
        r.playToServer(RankUp.TYPE, RankUp.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) handleRankUp(sp, p);
        }));
    }

    public static void openAnvil(ServerPlayer sp, BlockPos pos) {
        PacketDistributor.sendToPlayer(sp, new OpenAnvil(pos));
    }

    private static boolean nearAnvil(ServerPlayer sp, BlockPos pos) {
        return sp.level().getBlockState(pos).is(SmithRegistries.ARCANE_ANVIL.get())
                && sp.distanceToSqr(Vec3.atCenterOf(pos)) <= ANVIL_REACH * ANVIL_REACH;
    }

    /** Custo em recurso (Mana/Vigor/Foco) de mexer uma parte na bigorna: 3% do máximo (Ferreiro 1,5%). */
    public static float stepCost(MagicData d) {
        return d.maxMana() * (d.path() == MagicPath.SMITH ? 0.015F : 0.03F);
    }

    private static void handleEdit(ServerPlayer sp, ForgeEdit p) {
        if (!nearAnvil(sp, p.pos())) return;
        if (p.op() == OP_FORGE_NEW) {
            BladeMaterial m = BladeMaterial.VALUES[Mth.clamp(p.arg(), 0, BladeMaterial.VALUES.length - 1)];
            report(sp, ForgeService.forgeNew(sp, m));
            return;
        }
        ForgeService.Target t = ForgeService.target(sp, true);
        if (t == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.no_blade").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.op() == OP_CONVERT) return; // a conversão já aconteceu ao achar o alvo
        boolean paysResource = p.op() == OP_PART || p.op() == OP_FAMILY || p.op() == OP_GRIP || p.op() == OP_FINISH;
        MagicData d = MagicApi.get(sp);
        float cost = paysResource && !sp.isCreative() ? stepCost(d) * (p.op() == OP_FAMILY ? 2F : 1F) : 0F;
        if (cost > 0 && d.mana() < cost) {
            sp.displayClientMessage(MagicApi.noResourceMessage(sp), true);
            return;
        }
        ForgeService.Result r = switch (p.op()) {
            case OP_PART -> {
                int idx = Mth.clamp(p.arg() / 2, 0, ForgedBlade.Part.VALUES.length - 1);
                yield ForgeService.stepPart(sp, t, ForgedBlade.Part.VALUES[idx], (p.arg() & 1) == 1 ? 1 : -1);
            }
            case OP_FAMILY -> ForgeService.setFamily(sp, t, BladeFamily.VALUES[Mth.clamp(p.arg(), 0, BladeFamily.VALUES.length - 1)]);
            case OP_GRIP -> ForgeService.setGrip(sp, t, ForgedBlade.Grip.VALUES[Mth.clamp(p.arg(), 0, ForgedBlade.Grip.VALUES.length - 1)]);
            case OP_FINISH -> ForgeService.setFinish(sp, t, ForgedBlade.Finish.VALUES[Mth.clamp(p.arg(), 0, ForgedBlade.Finish.VALUES.length - 1)]);
            case OP_RUNE -> ForgeService.setRune(sp, t, element(p.arg()));
            case OP_MATERIAL -> ForgeService.setMaterial(sp, t, BladeMaterial.VALUES[Mth.clamp(p.arg(), 0, BladeMaterial.VALUES.length - 1)]);
            case OP_TEMPER -> ForgeService.temper(sp, t);
            default -> null;
        };
        if (r == null) return;
        if (r.ok() && cost > 0) {
            d.consumeMana(cost);
            d.markDirty();
        }
        report(sp, r);
    }

    @Nullable
    private static Element element(int ordinal) {
        Element[] all = Element.values();
        return ordinal < 0 || ordinal >= all.length ? null : all[ordinal];
    }

    private static void report(ServerPlayer sp, ForgeService.Result r) {
        if (!r.ok()) sp.displayClientMessage(r.message(), true);
    }

    private static void handleRankUp(ServerPlayer sp, RankUp p) {
        BlockPos anvil = findAnvil(sp);
        if (anvil == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.rank_need_anvil")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.rank() != SmithProgress.rank(MagicApi.get(sp)) + 1) return;
        MutableComponent err = SmithRanks.tryAdvance(sp, Vec3.atCenterOf(anvil));
        if (err != null) sp.displayClientMessage(err, true);
    }

    /** Bigorna Arcana a até {@link SmithRanks#ANVIL_RANGE} blocos (a mais perto), ou null. */
    @Nullable
    public static BlockPos findAnvil(ServerPlayer sp) {
        int r = (int) Math.ceil(SmithRanks.ANVIL_RANGE);
        BlockPos center = sp.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (!sp.level().getBlockState(q).is(SmithRegistries.ARCANE_ANVIL.get())) continue;
            double dd = q.distToCenterSqr(sp.position());
            if (dd <= SmithRanks.ANVIL_RANGE * SmithRanks.ANVIL_RANGE && dd < bestD) {
                bestD = dd;
                best = q.immutable();
            }
        }
        return best;
    }

    private SmithNet() {}
}
