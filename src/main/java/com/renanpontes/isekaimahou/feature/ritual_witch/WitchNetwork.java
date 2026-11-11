package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do Ritual da Bruxa (todos servidor → cliente). */
public final class WitchNetwork {

    /** Gancho de cliente: abre a tela do Livro de Receitas (o RitualWitchClient substitui). */
    public static Consumer<BookData> openBook = p -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ritual_witch-1");
        r.playToClient(SyncIngredients.TYPE, SyncIngredients.STREAM_CODEC, (p, c) -> {
            Set<Item> set = new HashSet<>();
            for (ResourceLocation id : p.items()) BuiltInRegistries.ITEM.getOptional(id).ifPresent(set::add);
            WitchRecipes.clientIngredients = Set.copyOf(set);
        });
        r.playToClient(BookData.TYPE, BookData.STREAM_CODEC, (p, c) -> openBook.accept(p));
    }

    public static void syncIngredients(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncIngredients(WitchRecipes.ingredientIds()));
    }

    /** Monta e envia as receitas que o jogador conhece (abre o livro no cliente). */
    public static void sendBook(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        List<BookEntry> entries = new ArrayList<>();
        for (WitchRecipe r : WitchRecipes.all()) {
            if (!d.hasFlag(WitchRecipe.knowledgeFlag(r.id()))) continue;
            List<ResourceLocation> ing = new ArrayList<>();
            for (Item i : r.ingredients()) ing.add(BuiltInRegistries.ITEM.getKey(i));
            entries.add(new BookEntry(r.id(), ing, r.stirs(), r.heat().ordinal(), r.moonMask(), r.night(), r.result().copy()));
        }
        // Revelações primeiro (por tier), depois o resto em ordem alfabética
        entries.sort((a, b) -> {
            int ta = tierOf(a.result()), tb = tierOf(b.result());
            if ((ta > 0) != (tb > 0)) return ta > 0 ? -1 : 1;
            if (ta != tb) return Integer.compare(ta, tb);
            return a.id().compareTo(b.id());
        });
        PacketDistributor.sendToPlayer(player, new BookData(entries, WitchRecipes.all().size()));
    }

    private static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof com.renanpontes.isekaimahou.feature.ritual_witch.item.RevelationPotionItem r ? r.tier() : 0;
    }

    // =====================================================================================

    /** Itens que o caldeirão aceita (para o cliente prever o clique). */
    public record SyncIngredients(List<ResourceLocation> items) implements CustomPacketPayload {
        public static final Type<SyncIngredients> TYPE = new Type<>(IsekaiMahou.id("ritual_witch_ingredients"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncIngredients> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncIngredients::items, SyncIngredients::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Uma receita conhecida, como aparece no livro. */
    public record BookEntry(ResourceLocation id, List<ResourceLocation> ingredients, int stirs, int heat, int moonMask, boolean night,
                            ItemStack result) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BookEntry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    ResourceLocation.STREAM_CODEC.encode(buf, e.id());
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.ingredients());
                    buf.writeVarInt(e.stirs());
                    buf.writeVarInt(e.heat());
                    buf.writeByte(e.moonMask());
                    buf.writeBoolean(e.night());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.result());
                },
                buf -> new BookEntry(ResourceLocation.STREAM_CODEC.decode(buf),
                        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(), buf.readBoolean(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

        public boolean moonAllowed(int phase) {
            return (moonMask & (1 << (phase & 7))) != 0;
        }
    }

    /** Conteúdo do Livro de Receitas (receitas conhecidas + total existente). */
    public record BookData(List<BookEntry> entries, int total) implements CustomPacketPayload {
        public static final Type<BookData> TYPE = new Type<>(IsekaiMahou.id("ritual_witch_book"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BookData> STREAM_CODEC = StreamCodec.composite(
                BookEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), BookData::entries,
                ByteBufCodecs.VAR_INT, BookData::total, BookData::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private WitchNetwork() {}
}
