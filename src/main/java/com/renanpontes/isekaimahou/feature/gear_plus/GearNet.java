package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo. Servidor → cliente: {@link Sync} (abates do Vínculo da classe atual e espera do kit).
 * Cliente → servidor: {@link Recover} (botão "Recuperar kit" do menu B). O cliente liga o gancho {@link #onSync}.
 */
public final class GearNet {
    public static Consumer<CompoundTag> onSync = t -> { };

    /**
     * {@code kills} (Vínculo da classe atual), {@code kit_wait} (ticks até a recuperação grátis) e o kit que a
     * recuperação devolveria a este jogador: {@code kit_path} (id da classe) + {@code kit} (as peças, já no melhor grau
     * que ele carregou — a aba mostra a peça que volta de verdade).
     */
    public record Sync(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(IsekaiMahou.id("gear_plus_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Sync::new, Sync::tag);
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** {@code pay}: aceita gastar os cristais para pular a recarga. */
    public record Recover(boolean pay) implements CustomPacketPayload {
        public static final Type<Recover> TYPE = new Type<>(IsekaiMahou.id("gear_plus_recover"));
        public static final StreamCodec<ByteBuf, Recover> STREAM_CODEC = ByteBufCodecs.BOOL.map(Recover::new, Recover::pay);
        @Override public Type<Recover> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("gear_plus-1");
            r.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> onSync.accept(p.tag()));
            r.playToServer(Recover.TYPE, Recover.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp) {
                    KitRecovery.recover(sp, p.pay(), false);
                    sync(sp); // mesmo em falha: o cliente acerta o relógio da recarga
                }
            });
        });
    }

    /** Classe mandada na última sincronização de cada jogador (troca de classe pede outra). */
    private static final java.util.Map<java.util.UUID, MagicPath> LAST_PATH = new java.util.concurrent.ConcurrentHashMap<>();

    /** Chamado de vez em quando: sincroniza de novo se a classe mudou (Altar da Reencarnação, comandos). */
    static void syncIfClassChanged(ServerPlayer sp) {
        if (LAST_PATH.get(sp.getUUID()) != MagicApi.get(sp).path()) sync(sp);
    }

    static void forget(java.util.UUID player) {
        LAST_PATH.remove(player);
    }

    public static void sync(ServerPlayer sp) {
        if (sp.connection == null) return;
        MagicPath path = MagicApi.get(sp).path();
        LAST_PATH.put(sp.getUUID(), path);
        CompoundTag tag = new CompoundTag();
        tag.putInt("kills", WeaponBond.hasBond(path) ? GearData.bondKills(sp, path) : 0);
        tag.putLong("kit_wait", KitRecovery.waitTicks(sp));
        ListTag kit = new ListTag();
        for (ItemStack st : KitRecovery.restorable(sp, path)) kit.add(st.save(sp.registryAccess()));
        tag.putString("kit_path", path.id);
        tag.put("kit", kit);
        PacketDistributor.sendToPlayer(sp, new Sync(tag));
    }

    private GearNet() {}
}
