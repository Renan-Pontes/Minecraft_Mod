package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Rede das receitas de transformação: a aba Votos escolhe a receita de um espaço; o servidor devolve a lista. */
public final class ForgeNetwork {
    /** Cliente → servidor: usar {@code recipe} no espaço {@code slot} (vazio = tirar). */
    public record SetRecipe(String slot, Optional<SpellRecipe> recipe) implements CustomPacketPayload {
        public static final Type<SetRecipe> TYPE = new Type<>(IsekaiMahou.id("tforge_set_recipe"));
        public static final StreamCodec<ByteBuf, SetRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), SetRecipe::slot, ByteBufCodecs.optional(SpellRecipe.STREAM_CODEC), SetRecipe::recipe,
                SetRecipe::new);

        @Override
        public Type<SetRecipe> type() { return TYPE; }
    }

    /** Servidor → cliente: todas as receitas escolhidas (espaço → receita). */
    public record SyncRecipes(Map<String, SpellRecipe> recipes) implements CustomPacketPayload {
        public static final Type<SyncRecipes> TYPE = new Type<>(IsekaiMahou.id("tforge_sync"));
        public static final StreamCodec<ByteBuf, SyncRecipes> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(64), SpellRecipe.STREAM_CODEC, ForgeData.MAX_SLOTS),
                SyncRecipes::recipes, SyncRecipes::new);

        @Override
        public Type<SyncRecipes> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("1");
            r.playToServer(SetRecipe.TYPE, SetRecipe.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp) {
                    c.enqueueWork(() -> ForgeSessions.chooseRecipe(sp, p.slot(), p.recipe().orElse(SpellRecipe.EMPTY)));
                }
            });
            // Só guarda um mapa estático (sem classes de cliente): seguro em qualquer lado
            r.playToClient(SyncRecipes.TYPE, SyncRecipes.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> ForgeData.setClient(p.recipes())));
        });
    }

    private ForgeNetwork() {}
}
