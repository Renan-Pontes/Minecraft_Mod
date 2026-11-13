package com.renanpontes.isekaimahou.feature.cities.network;

import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.CityDialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes das Cidades. O servidor monta a fala, os botões e os serviços prontos; o cliente só desenha a tela de
 * diálogo e manda de volta o botão clicado. Ganchos de cliente são trocados pelo CitiesClient (sem imports de cliente).
 */
public final class CitiesNetwork {
    public static final int BTN_TALK = 1, BTN_TRADE = 2, BTN_TRADE_LOCKED = 4, BTN_SERVICES = 8, BTN_RUMORS = 16;
    /**
     * v1.0: abordagem da guarda (o guarda viu o saque). A tela troca os botões normais pelas opções que vêm em
     * {@code services} (Persuadir, Pagar propina, Devolver, Fugir, Brigar) e mostra {@code note} (a acusação).
     */
    public static final int BTN_CONFRONT = 32;
    /** v1.0: fecha a tela deste NPC já (a abordagem virou briga/fuga). */
    public static final int BTN_CLOSE_NOW = 64;
    /** v1.0: cidade em guerra com o jogador — só a fala hostil e "Despedir-se". */
    public static final int BTN_ENEMY = 128;
    /** Release 1.0: o morador tem um pedido (sidequest) para o jogador, ou faz parte do pedido ativo dele. */
    public static final int BTN_QUEST = 256;
    /**
     * Release 1.0: escolha de um pedido (aceitar/recusar, os dois desfechos, desistir). Como na abordagem, as opções vêm
     * em {@code services} e {@code note} é a linha de destaque; o clique volta como ação {@code quest} + id da opção.
     */
    public static final int BTN_CHOICE = 512;
    /** Release 1.0: loja fechada de madrugada (o botão "Trocar" aparece, com o aviso de que fechou). */
    public static final int BTN_TRADE_CLOSED = 1024;

    /** Abre/atualiza a tela de diálogo (o CitiesClient substitui). */
    public static Consumer<Dialogue> openDialogue = d -> {};
    /** Rumor ativo do jogador (o CitiesClient substitui). */
    public static Consumer<Rumor> rumor = r -> {};
    /** Reinos que procuram o jogador (o CitiesClient substitui). */
    public static Consumer<Wanted> wanted = w -> {};
    /** Abordagem da guarda em andamento (o CitiesClient substitui). */
    public static Consumer<Confront> confront = c -> {};
    /** Pedido (sidequest) ativo do jogador (o CitiesClient substitui). */
    public static Consumer<QuestView> questView = q -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("cities-4");
        r.playToClient(Dialogue.TYPE, Dialogue.STREAM_CODEC, (p, c) -> openDialogue.accept(p));
        r.playToClient(Rumor.TYPE, Rumor.STREAM_CODEC, (p, c) -> rumor.accept(p));
        r.playToClient(Wanted.TYPE, Wanted.STREAM_CODEC, (p, c) -> wanted.accept(p));
        r.playToClient(Confront.TYPE, Confront.STREAM_CODEC, (p, c) -> confront.accept(p));
        r.playToClient(QuestView.TYPE, QuestView.STREAM_CODEC, (p, c) -> questView.accept(p));
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) CityDialogue.handle(sp, p);
        });
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    // =====================================================================================
    //  Visões
    // =====================================================================================
    /** Um serviço do NPC (cura, quarto, fiança...). */
    public record Service(String id, int cost, boolean available, Component label, Component description) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Service> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Service::id,
                ByteBufCodecs.VAR_INT, Service::cost,
                ByteBufCodecs.BOOL, Service::available,
                ComponentSerialization.STREAM_CODEC, Service::label,
                ComponentSerialization.STREAM_CODEC, Service::description,
                Service::new);
    }

    /**
     * Tela de diálogo. {@code standingRgb} = cor do patamar; {@code standing} já vem traduzido.
     * {@code services} vazio = sem botão "Serviços". {@code note}: texto extra sob a fala (a acusação da guarda; vazio
     * = nada).
     */
    public record Dialogue(int entityId, String kind, Component name, Component role, Component faction, Component standing,
                           int factionRgb, Component line, int buttons, List<Service> services, int coins, Component note)
            implements CustomPacketPayload {
        public static final Type<Dialogue> TYPE = new Type<>(IsekaiMahou.id("cities_dialogue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Dialogue> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.entityId);
                    buf.writeUtf(d.kind, 32);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.name);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.role);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.faction);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.standing);
                    buf.writeInt(d.factionRgb);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.line);
                    buf.writeVarInt(d.buttons);
                    Service.STREAM_CODEC.apply(ByteBufCodecs.list(12)).encode(buf, d.services);
                    buf.writeVarInt(d.coins);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.note);
                },
                buf -> new Dialogue(buf.readVarInt(), buf.readUtf(32), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), buf.readInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                        buf.readVarInt(), Service.STREAM_CODEC.apply(ByteBufCodecs.list(12)).decode(buf), buf.readVarInt(),
                        ComponentSerialization.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public boolean has(int button) {
            return (buttons & button) != 0;
        }
    }

    /**
     * Botão clicado: talk, trade, rumor, service (arg = id do serviço), confront (arg = opção da abordagem), quest
     * (arg = "" abre o pedido; senão a opção escolhida), close.
     */
    public record Action(int entityId, String action, String arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("cities_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::entityId,
                ByteBufCodecs.stringUtf8(16), Action::action,
                ByteBufCodecs.stringUtf8(32), Action::arg,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Rumor ativo (seta no rastreador de objetivos). {@code active=false} apaga. {@code guide=true}: não é rumor, é um
     * morador mostrando o caminho (Altar de Ascensão, Pedra de Teleporte) dentro da cidade.
     */
    public record Rumor(boolean active, BlockPos pos, ResourceLocation dim, Component name, boolean guide) implements CustomPacketPayload {
        public static final Type<Rumor> TYPE = new Type<>(IsekaiMahou.id("cities_rumor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rumor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Rumor::active,
                BlockPos.STREAM_CODEC, Rumor::pos,
                ResourceLocation.STREAM_CODEC, Rumor::dim,
                ComponentSerialization.STREAM_CODEC, Rumor::name,
                ByteBufCodecs.BOOL, Rumor::guide,
                Rumor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Reinos (ids de facção) que procuram o jogador agora; vazio = nenhum ou crime antigo (todos). */
    public record Wanted(List<String> factions) implements CustomPacketPayload {
        public static final Type<Wanted> TYPE = new Type<>(IsekaiMahou.id("cities_wanted"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Wanted> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(8)), Wanted::factions,
                Wanted::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Abordagem da guarda em andamento: {@code guardId} = entidade do guarda (−1 = nenhuma, apaga a linha do
     * rastreador); {@code seconds} = tempo que falta antes de o guarda considerar fuga; {@code talking} = o guarda já
     * chegou e espera a resposta.
     */
    public record Confront(int guardId, Component guardName, int seconds, boolean talking) implements CustomPacketPayload {
        public static final Type<Confront> TYPE = new Type<>(IsekaiMahou.id("cities_confront"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Confront> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Confront::guardId,
                ComponentSerialization.STREAM_CODEC, Confront::guardName,
                ByteBufCodecs.VAR_INT, Confront::seconds,
                ByteBufCodecs.BOOL, Confront::talking,
                Confront::new);

        public static Confront none() {
            return new Confront(-1, Component.empty(), 0, false);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Pedido (sidequest) ativo, para o rastreador de objetivos: título, o passo atual, progresso {@code cur/max}
     * (0/0 = sem barra) e a seta ({@code arrow}) para {@code pos} em {@code dim}. {@code active=false} apaga.
     */
    public record QuestView(boolean active, Component title, Component detail, int cur, int max, boolean arrow, BlockPos pos,
                            ResourceLocation dim) implements CustomPacketPayload {
        public static final Type<QuestView> TYPE = new Type<>(IsekaiMahou.id("cities_quest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestView> STREAM_CODEC = StreamCodec.of(
                (buf, q) -> {
                    buf.writeBoolean(q.active);
                    ComponentSerialization.STREAM_CODEC.encode(buf, q.title);
                    ComponentSerialization.STREAM_CODEC.encode(buf, q.detail);
                    buf.writeVarInt(q.cur);
                    buf.writeVarInt(q.max);
                    buf.writeBoolean(q.arrow);
                    buf.writeBlockPos(q.pos);
                    buf.writeResourceLocation(q.dim);
                },
                buf -> new QuestView(buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBlockPos(), buf.readResourceLocation()));

        public static QuestView none() {
            return new QuestView(false, Component.empty(), Component.empty(), 0, 0, false, BlockPos.ZERO,
                    ResourceLocation.withDefaultNamespace("overworld"));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CitiesNetwork() {}
}
