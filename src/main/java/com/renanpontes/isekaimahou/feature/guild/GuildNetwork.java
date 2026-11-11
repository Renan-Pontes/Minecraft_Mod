package com.renanpontes.isekaimahou.feature.guild;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes da Guilda. O servidor monta VISÕES prontas (textos já como Component, ícones, progresso) e o cliente
 * só desenha — o cliente não precisa conhecer as missões nem os contadores.
 */
public final class GuildNetwork {
    // ---------------- Ações (cliente → servidor) ----------------
    public static final int ACT_REGISTER = 0, ACT_ACCEPT = 1, ACT_TURN_IN = 2, ACT_ABANDON = 3, ACT_BUY = 4,
            ACT_TRIAL_ACCEPT = 5, ACT_TRIAL_REPORT = 6, ACT_TRIAL_ABANDON = 7, ACT_REISSUE_CARD = 8;

    // ---------------- Estados de uma missão na visão ----------------
    public static final int Q_AVAILABLE = 0, Q_ACTIVE = 1, Q_TAKEN = 2, Q_LOCKED = 3, Q_READY = 4, Q_TRIAL_OFFER = 5;

    // ---------------- Ações do diálogo do aventureiro ----------------
    public static final int ADV_TALK = 0, ADV_TRADE = 1, ADV_HIRE = 2, ADV_RENEW = 3, ADV_DISMISS = 4, ADV_FOLLOW = 5, ADV_BYE = 6;
    /** Situação do aventureiro no diálogo: livre, contratado por você, contratado por outro jogador. */
    public static final int HIRE_FREE = 0, HIRE_MINE = 1, HIRE_OTHER = 2;

    // ---------------- Lado do cliente (ganchos e cópia do estado; sem imports de cliente) ----------------
    /** Abre/atualiza a tela da Guilda (o GuildClient substitui). */
    public static Consumer<ScreenData> openScreen = p -> {};
    /** Abre a aba "Guilda" do menu B (o GuildClient substitui). */
    public static Runnable openGuildTab = () -> {};
    /** Abre/atualiza o diálogo de um aventureiro (o GuildClient substitui). */
    public static Consumer<AdvDialog> openAdvDialog = p -> {};
    /** Último estado recebido do servidor. */
    public static volatile State clientState = State.EMPTY;
    /** Companheiros contratados (último envio do servidor). */
    public static volatile List<CompanionView> clientCompanions = List.of();

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("guild-1");
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> {
            clientState = p;
        });
        r.playToClient(ScreenData.TYPE, ScreenData.STREAM_CODEC, (p, c) -> openScreen.accept(p));
        r.playToClient(Companions.TYPE, Companions.STREAM_CODEC, (p, c) -> clientCompanions = List.copyOf(p.list()));
        r.playToClient(AdvDialog.TYPE, AdvDialog.STREAM_CODEC, (p, c) -> openAdvDialog.accept(p));
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) GuildService.handleAction(sp, p);
        });
        r.playToServer(AdvAction.TYPE, AdvAction.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) CompanionService.handleDialog(sp, p);
        });
    }

    // =====================================================================================
    //  Visões
    // =====================================================================================
    /** Um objetivo com o progresso atual. */
    public record ObjectiveView(ItemStack icon, Component label, int progress, int target) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ObjectiveView> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, ObjectiveView::icon,
                ComponentSerialization.STREAM_CODEC, ObjectiveView::label,
                ByteBufCodecs.VAR_INT, ObjectiveView::progress,
                ByteBufCodecs.VAR_INT, ObjectiveView::target,
                ObjectiveView::new);

        public boolean done() {
            return progress >= target;
        }
    }

    private static final StreamCodec<FriendlyByteBuf, Map<String, Integer>> REP_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(32), ByteBufCodecs.INT, 8);

    /**
     * v0.9: a Área de Caça de uma missão. {@code phase}: {@link #HUNT_OFFER} (no quadro: ganha área ao aceitar),
     * {@link #HUNT_TRAVEL} (vá até lá), {@link #HUNT_FIGHT} (ondas em andamento). {@code pos}: centro (vazio na oferta);
     * {@code wave}/{@code waves}: onda atual e ondas previstas pelo rank; {@code name}: "Área de Caça: Goblin"; {@code rgb}:
     * cor do elemento da área.
     */
    public record HuntView(Optional<GlobalPos> pos, int phase, int wave, int waves, Component name, int rgb) {
        public static final StreamCodec<RegistryFriendlyByteBuf, HuntView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), HuntView::pos,
                ByteBufCodecs.VAR_INT, HuntView::phase,
                ByteBufCodecs.VAR_INT, HuntView::wave,
                ByteBufCodecs.VAR_INT, HuntView::waves,
                ComponentSerialization.STREAM_CODEC, HuntView::name,
                ByteBufCodecs.INT, HuntView::rgb,
                HuntView::new);
    }

    public static final int HUNT_OFFER = -1, HUNT_TRAVEL = 0, HUNT_FIGHT = 1;
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<HuntView>> HUNT_CODEC = ByteBufCodecs.optional(HuntView.STREAM_CODEC);

    /**
     * Uma missão como aparece na tela. {@code faction}: quadro da missão ("guild", "kingdom"...); {@code standing}: patamar
     * exigido (id) nas missões de facção ("" nas da Guilda); {@code reputation}: reputação que a entrega dá, por facção;
     * {@code hunt} (v0.9): Área de Caça da missão.
     */
    public record QuestView(ResourceLocation id, int rank, String category, Component title, Component description,
                            List<ObjectiveView> objectives, int coins, int xp, int points, List<ItemStack> items, int state,
                            String faction, String standing, Map<String, Integer> reputation, Optional<HuntView> hunt) {
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestView> STREAM_CODEC = StreamCodec.of(
                (buf, q) -> {
                    ResourceLocation.STREAM_CODEC.encode(buf, q.id);
                    buf.writeVarInt(q.rank);
                    buf.writeUtf(q.category, 32);
                    ComponentSerialization.STREAM_CODEC.encode(buf, q.title);
                    ComponentSerialization.STREAM_CODEC.encode(buf, q.description);
                    ObjectiveView.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, q.objectives);
                    buf.writeVarInt(q.coins);
                    buf.writeVarInt(q.xp);
                    buf.writeVarInt(q.points);
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(16)).encode(buf, q.items);
                    buf.writeVarInt(q.state);
                    buf.writeUtf(q.faction, 32);
                    buf.writeUtf(q.standing, 32);
                    REP_CODEC.encode(buf, q.reputation);
                    HUNT_CODEC.encode(buf, q.hunt);
                },
                buf -> new QuestView(ResourceLocation.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(32),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ObjectiveView.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(16)).decode(buf), buf.readVarInt(),
                        buf.readUtf(32), buf.readUtf(32), REP_CODEC.decode(buf), HUNT_CODEC.decode(buf)));

        public GuildRank guildRank() {
            GuildRank r = GuildRank.byLevel(rank);
            return r == null ? GuildRank.F : r;
        }

        public GuildFaction guildFaction() {
            return GuildFaction.of(faction);
        }

        public boolean complete() {
            for (ObjectiveView o : objectives) if (!o.done()) return false;
            return true;
        }
    }

    /** Uma oferta da loja. {@code standing}: patamar exigido com a Guilda ("" = nenhum). */
    public record OfferView(int index, ItemStack stack, int price, int rank, boolean locked, String standing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OfferView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OfferView::index,
                ItemStack.OPTIONAL_STREAM_CODEC, OfferView::stack,
                ByteBufCodecs.VAR_INT, OfferView::price,
                ByteBufCodecs.VAR_INT, OfferView::rank,
                ByteBufCodecs.BOOL, OfferView::locked,
                ByteBufCodecs.stringUtf8(32), OfferView::standing,
                OfferView::new);
    }

    /**
     * Um companheiro contratado (rastreador e aba). {@code entityId} -1 = não está carregado perto do jogador
     * (vida desconhecida); {@code pos} = onde foi visto por último.
     */
    public record CompanionView(UUID id, int entityId, Component name, int variant, int rank, float health, float maxHealth,
                                long ticksLeft, boolean following, Optional<GlobalPos> pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CompanionView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, v.id);
                    buf.writeVarInt(v.entityId);
                    ComponentSerialization.STREAM_CODEC.encode(buf, v.name);
                    buf.writeVarInt(v.variant);
                    buf.writeVarInt(v.rank);
                    buf.writeFloat(v.health);
                    buf.writeFloat(v.maxHealth);
                    buf.writeVarLong(v.ticksLeft);
                    buf.writeBoolean(v.following);
                    ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).encode(buf, v.pos);
                },
                buf -> new CompanionView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarLong(), buf.readBoolean(),
                        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).decode(buf)));

        public boolean loaded() {
            return entityId >= 0;
        }

        public GuildRank guildRank() {
            GuildRank r = GuildRank.byLevel(rank);
            return r == null ? GuildRank.F : r;
        }
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    /**
     * Estado do jogador na Guilda (aba do menu, rastreador de objetivos). {@code hall}/{@code master}: onde o jogador
     * usou o quadro/recepção e onde falou com o Mestre pela última vez (setas do rastreador); {@code boards}: último
     * Quadro de cada facção não-Guilda.
     */
    public record State(int rank, int points, int completed, List<QuestView> active, Optional<QuestView> trial, boolean trialReady,
                        Optional<GlobalPos> hall, Optional<GlobalPos> master, Map<String, GlobalPos> boards) implements CustomPacketPayload {
        public static final State EMPTY = new State(0, 0, 0, List.of(), Optional.empty(), false, Optional.empty(), Optional.empty(), Map.of());
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("guild_state"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<QuestView>> ACTIVE_CODEC = QuestView.STREAM_CODEC.apply(ByteBufCodecs.list(16));
        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<QuestView>> TRIAL_CODEC = ByteBufCodecs.optional(QuestView.STREAM_CODEC);
        private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<GlobalPos>> POS_CODEC = ByteBufCodecs.optional(GlobalPos.STREAM_CODEC);
        private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, GlobalPos>> BOARDS_CODEC =
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(32), GlobalPos.STREAM_CODEC, 16);
        // 9 campos: StreamCodec.composite só vai até 6.
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.rank);
                    buf.writeVarInt(s.points);
                    buf.writeVarInt(s.completed);
                    ACTIVE_CODEC.encode(buf, s.active);
                    TRIAL_CODEC.encode(buf, s.trial);
                    buf.writeBoolean(s.trialReady);
                    POS_CODEC.encode(buf, s.hall);
                    POS_CODEC.encode(buf, s.master);
                    BOARDS_CODEC.encode(buf, s.boards);
                },
                buf -> new State(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), ACTIVE_CODEC.decode(buf), TRIAL_CODEC.decode(buf),
                        buf.readBoolean(), POS_CODEC.decode(buf), POS_CODEC.decode(buf), BOARDS_CODEC.decode(buf)));

        public GuildRank guildRank() {
            return GuildRank.byLevel(rank);
        }

        /** Onde entregar uma missão desta facção (Guilda: quadro/recepção). */
        public Optional<GlobalPos> deskFor(String faction) {
            return GuildFaction.of(faction).isGuild() ? hall : Optional.ofNullable(boards.get(faction));
        }

        public int activeCount(boolean guild) {
            int n = 0;
            for (QuestView q : active) if (q.guildFaction().isGuild() == guild) n++;
            return n;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Abre (ou atualiza, se já aberta) a tela da Guilda. Modo nos bits baixos (0 recepção, 1 quadro, 2 mestre);
     * com {@link #REFRESH} é só uma atualização depois de uma ação (não abre a tela se ela já foi fechada);
     * {@link #DAILY}: a prova oferecida pelo Mestre é sorteada entre várias e muda a cada dia.
     * {@code faction}: facção do quadro ("guild" na recepção e no Mestre).
     */
    public record ScreenData(int mode, int npcId, List<QuestView> board, List<OfferView> shop, Optional<QuestView> trialOffer,
                             boolean hasCard, String faction) implements CustomPacketPayload {
        public static final int REFRESH = 8, DAILY = 16;
        public static final Type<ScreenData> TYPE = new Type<>(IsekaiMahou.id("guild_screen"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<QuestView>> BOARD_CODEC = QuestView.STREAM_CODEC.apply(ByteBufCodecs.list(32));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<OfferView>> SHOP_CODEC = OfferView.STREAM_CODEC.apply(ByteBufCodecs.list(128));
        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<QuestView>> TRIAL_CODEC = ByteBufCodecs.optional(QuestView.STREAM_CODEC);
        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenData> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.mode);
                    buf.writeVarInt(d.npcId);
                    BOARD_CODEC.encode(buf, d.board);
                    SHOP_CODEC.encode(buf, d.shop);
                    TRIAL_CODEC.encode(buf, d.trialOffer);
                    buf.writeBoolean(d.hasCard);
                    buf.writeUtf(d.faction, 32);
                },
                buf -> new ScreenData(buf.readVarInt(), buf.readVarInt(), BOARD_CODEC.decode(buf), SHOP_CODEC.decode(buf),
                        TRIAL_CODEC.decode(buf), buf.readBoolean(), buf.readUtf(32)));

        public int screenMode() {
            return mode & 7;
        }

        public boolean isRefresh() {
            return (mode & REFRESH) != 0;
        }

        public boolean dailyTrial() {
            return (mode & DAILY) != 0;
        }

        public GuildFaction guildFaction() {
            return GuildFaction.of(faction);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Ação do jogador numa tela da Guilda. */
    public record Action(int action, ResourceLocation id, int index, int amount) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("guild_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action,
                ResourceLocation.STREAM_CODEC, Action::id,
                ByteBufCodecs.VAR_INT, Action::index,
                ByteBufCodecs.VAR_INT, Action::amount,
                Action::new);

        public static Action of(int action, ResourceLocation id) {
            return new Action(action, id, 0, 1);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Lista de companheiros do jogador (mandada a cada segundo quando muda). */
    public record Companions(List<CompanionView> list) implements CustomPacketPayload {
        public static final Type<Companions> TYPE = new Type<>(IsekaiMahou.id("guild_companions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Companions> STREAM_CODEC = StreamCodec.composite(
                CompanionView.STREAM_CODEC.apply(ByteBufCodecs.list(8)), Companions::list, Companions::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Diálogo de um aventureiro (servidor → cliente). Tudo pronto para desenhar: fala, preço do contrato (já com a
     * reputação), situação do contrato e limites.
     */
    public record AdvDialog(int entityId, Component name, int variant, int rank, int xp, int xpNext, int element, int price,
                            int days, int hire, boolean following, long ticksLeft, Component line, int companions, int maxCompanions,
                            boolean canTrade, boolean refused, int kills, boolean refresh) implements CustomPacketPayload {
        public static final Type<AdvDialog> TYPE = new Type<>(IsekaiMahou.id("guild_adv_dialog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AdvDialog> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.entityId);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.name);
                    buf.writeVarInt(d.variant);
                    buf.writeVarInt(d.rank);
                    buf.writeVarInt(d.xp);
                    buf.writeVarInt(d.xpNext);
                    buf.writeVarInt(d.element);
                    buf.writeVarInt(d.price);
                    buf.writeVarInt(d.days);
                    buf.writeVarInt(d.hire);
                    buf.writeBoolean(d.following);
                    buf.writeVarLong(d.ticksLeft);
                    ComponentSerialization.STREAM_CODEC.encode(buf, d.line);
                    buf.writeVarInt(d.companions);
                    buf.writeVarInt(d.maxCompanions);
                    buf.writeBoolean(d.canTrade);
                    buf.writeBoolean(d.refused);
                    buf.writeVarInt(d.kills);
                    buf.writeBoolean(d.refresh);
                },
                buf -> new AdvDialog(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readVarLong(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(),
                        buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean()));

        public GuildRank guildRank() {
            GuildRank r = GuildRank.byLevel(rank);
            return r == null ? GuildRank.F : r;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Botão do diálogo do aventureiro (cliente → servidor). */
    public record AdvAction(int entityId, int action) implements CustomPacketPayload {
        public static final Type<AdvAction> TYPE = new Type<>(IsekaiMahou.id("guild_adv_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AdvAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AdvAction::entityId,
                ByteBufCodecs.VAR_INT, AdvAction::action,
                AdvAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private GuildNetwork() {}
}
