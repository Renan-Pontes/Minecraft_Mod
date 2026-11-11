package com.renanpontes.isekaimahou.feature.guild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.ObjectiveView;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.OfferView;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.QuestView;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildMasterEntity;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildNpcEntity;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildReceptionistEntity;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntRules;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntState;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntingGrounds;
import com.renanpontes.isekaimahou.feature.guild.item.AdventurerCardItem;
import com.renanpontes.isekaimahou.feature.guild.item.GuildCardInfo;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuest;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuests;
import com.renanpontes.isekaimahou.feature.guild.quest.QuestObjective;
import com.renanpontes.isekaimahou.feature.guild.quest.QuestRewards;
import com.renanpontes.isekaimahou.feature.guild.quest.RewardItem;
import com.renanpontes.isekaimahou.feature.guild.quest.ShopOffer;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras da Guilda no SERVIDOR: registro, quadro, aceitar/entregar/abandonar missões, provas de promoção,
 * loja, recompensas e a sincronização da visão com o cliente.
 * <p>
 * v0.8: Quadros de Facção (Reino, Academia, Igreja, Submundo) mostram as missões daquela facção, liberadas pelo patamar de
 * reputação; entregas dão reputação ({@link GuildQuest#reputationRewards()}); preços da loja seguem a reputação com a Guilda.
 */
public final class GuildService {
    public static final int MODE_RECEPTION = 0, MODE_BOARD = 1, MODE_MASTER = 2;
    /** Preço (moedas) para emitir um novo cartão. */
    public static final int REISSUE_PRICE = 3;
    /** Distância máxima até o quadro/NPC para as ações valerem. */
    private static final double REACH = 12.0;
    /** Limites das listas enviadas ao cliente (os codecs dos pacotes recusam listas maiores). */
    private static final int MAX_VIEW_ITEMS = 12, MAX_SHOP_OFFERS = 128;
    /** Cor dos avisos de "missão pronta" (verde da Guilda). */
    private static final int READY_RGB = 0x6BD66B;

    /** De onde o jogador abriu a tela (valida as ações recebidas) e a facção do quadro. */
    private record Session(int mode, ResourceKey<Level> dim, Vec3 at, int npcId, GuildFaction faction) {}
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    /**
     * Prova mostrada na sala do Mestre: o "Aceitar" aceita EXATAMENTE ela, mesmo se o dia virar (e o sorteio mudar)
     * com a tela aberta. Zera ao abrir a sala de novo, para o sorteio do dia valer.
     */
    private static final Map<UUID, ResourceLocation> OFFERED = new ConcurrentHashMap<>();

    public static GuildData data(Player player) {
        return player.getData(GuildContent.GUILD);
    }

    // =====================================================================================
    //  Abrir telas
    // =====================================================================================
    public static void openReception(ServerPlayer sp, GuildReceptionistEntity npc) {
        GuildData d = data(sp);
        SESSIONS.put(sp.getUUID(), new Session(MODE_RECEPTION, sp.level().dimension(), npc.position(), npc.getId(), GuildFaction.GUILD));
        d.setHall(GlobalPos.of(sp.level().dimension(), npc.blockPosition()));
        // Folheia o livro de registros ao atender.
        npc.playSound(SoundEvents.VILLAGER_WORK_LIBRARIAN, 0.7F, 1.1F);
        if (!d.registered()) npc.say("dialogue.isekai_mahou.receptionist.welcome");
        else if (hasReadyQuest(sp, d)) npc.say("dialogue.isekai_mahou.receptionist.ready", sp.getGameProfile().getName());
        else npc.say("dialogue.isekai_mahou.receptionist.welcome_back", sp.getGameProfile().getName());
        sendScreen(sp, MODE_RECEPTION, npc.getId(), false);
    }

    /** Quadro de Missões (Guilda) ou Quadro de Facção: mesma tela, filtrada pela facção. */
    public static void openBoard(ServerPlayer sp, net.minecraft.core.BlockPos pos, GuildFaction faction) {
        SESSIONS.put(sp.getUUID(), new Session(MODE_BOARD, sp.level().dimension(), pos.getCenter(), -1, faction));
        GlobalPos gp = GlobalPos.of(sp.level().dimension(), pos);
        if (faction.isGuild()) data(sp).setHall(gp);
        else data(sp).setBoard(faction.id(), gp);
        sp.serverLevel().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 0.9F);
        if (faction.isGuild() && !data(sp).registered()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.not_registered").withStyle(ChatFormatting.GOLD), true);
        } else if (!Reputation.canTrade(sp, faction.id())) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.faction_refuses", faction.displayName())
                    .withStyle(ChatFormatting.RED), true);
        }
        sendScreen(sp, MODE_BOARD, -1, false);
    }

    public static void openMaster(ServerPlayer sp, GuildMasterEntity npc) {
        GuildData d = data(sp);
        SESSIONS.put(sp.getUUID(), new Session(MODE_MASTER, sp.level().dimension(), npc.position(), npc.getId(), GuildFaction.GUILD));
        OFFERED.remove(sp.getUUID());
        d.setMasterPos(GlobalPos.of(sp.level().dimension(), npc.blockPosition()));
        // A cota de malha do veterano faz barulho quando ele se vira para o jogador.
        npc.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8F, 0.8F);
        GuildRank next = d.rank() == null ? null : d.rank().next();
        if (!d.registered()) npc.say("dialogue.isekai_mahou.master.unregistered");
        else if (next == null) npc.say("dialogue.isekai_mahou.master.max_rank", sp.getGameProfile().getName());
        else if (d.trial() != null) npc.say("dialogue.isekai_mahou.master.trial_going");
        else if (d.points() >= next.pointsRequired) npc.say("dialogue.isekai_mahou.master.ready", "#" + GuildRank.LETTER_KEY + next.id());
        else npc.say("dialogue.isekai_mahou.master.not_ready");
        sendScreen(sp, MODE_MASTER, npc.getId(), false);
    }

    /** Monta e envia a tela. {@code refresh}: só atualiza se a mesma tela ainda estiver aberta no cliente. */
    private static void sendScreen(ServerPlayer sp, int mode, int npcId, boolean refresh) {
        sync(sp, true);
        GuildData d = data(sp);
        GuildRank rank = d.registered() ? d.rank() : GuildRank.F;
        Session session = SESSIONS.get(sp.getUUID());
        GuildFaction faction = session == null ? GuildFaction.GUILD : session.faction();
        List<QuestView> board = new ArrayList<>();
        List<OfferView> shop = new ArrayList<>();
        Optional<QuestView> trialOffer = Optional.empty();
        if (mode == MODE_BOARD && !faction.isGuild()) {
            board.addAll(factionBoardViews(sp, d, faction));
        } else if (mode == MODE_RECEPTION || mode == MODE_BOARD) {
            GuildQuests.Board b = GuildQuests.board(sp.server, rank);
            Set<ResourceLocation> taken = d.takenToday(GuildQuests.day(sp.server));
            MagicData md = MagicApi.get(sp);
            for (GuildQuest q : b.open()) {
                ActiveQuest a = d.find(q.id());
                int state;
                if (a != null) state = isComplete(progress(sp, q, a), q) ? GuildNetwork.Q_READY : GuildNetwork.Q_ACTIVE;
                else if (!d.registered()) state = GuildNetwork.Q_LOCKED;
                else if (taken.contains(q.id())) state = GuildNetwork.Q_TAKEN;
                else if (!q.repeatable() && md.counter(questCounter(q.id())) > 0) continue; // única e já feita: some do quadro
                else state = GuildNetwork.Q_AVAILABLE;
                board.add(view(sp, q, a, state));
            }
            for (GuildQuest q : b.locked()) board.add(view(sp, q, null, GuildNetwork.Q_LOCKED));
        }
        if (mode == MODE_RECEPTION) {
            List<ShopOffer> offers = GuildQuests.shop();
            for (int i = 0; i < offers.size() && shop.size() < MAX_SHOP_OFFERS; i++) {
                ShopOffer o = offers.get(i);
                ItemStack display = offerDisplay(o);
                if (display.isEmpty()) continue;
                boolean locked = !d.registered() || o.rank().ordinal() > rank.ordinal() || !o.standingOk(sp);
                shop.add(new OfferView(i, display, o.priceFor(sp), o.rank().level(), locked, o.standing().map(st -> st.id).orElse("")));
            }
        }
        boolean daily = false;
        if (mode == MODE_MASTER && d.registered() && d.trial() == null && rank.next() != null) {
            GuildQuest t = offeredTrial(sp, rank.next());
            if (t != null) {
                trialOffer = Optional.of(view(sp, t, null, GuildNetwork.Q_TRIAL_OFFER));
                // Só avisa "amanhã pode ser outra" quando há mais de uma prova possível para o rank.
                daily = GuildQuests.trialOptions(sp.server, rank.next()) > 1;
            }
        }
        int flags = refresh ? mode | GuildNetwork.ScreenData.REFRESH : mode;
        if (daily) flags |= GuildNetwork.ScreenData.DAILY;
        PacketDistributor.sendToPlayer(sp, new GuildNetwork.ScreenData(flags, npcId, board, shop, trialOffer, hasOwnCard(sp), faction.id()));
    }

    /** Missões do quadro de uma facção (não-Guilda) para o patamar do jogador, mais as do patamar seguinte (trancadas). */
    private static List<QuestView> factionBoardViews(ServerPlayer sp, GuildData d, GuildFaction faction) {
        List<QuestView> out = new ArrayList<>();
        boolean trusted = Reputation.canTrade(sp, faction.id());
        Reputation.Standing standing = Reputation.standing(sp, faction.id());
        // Quem a facção recusa vê o quadro do patamar Desconfiado, tudo trancado (para saber o que perde).
        GuildQuests.Board b = GuildQuests.factionBoard(sp.server, faction, trusted ? standing : Reputation.Standing.UNFRIENDLY);
        Set<ResourceLocation> taken = d.takenToday(GuildQuests.day(sp.server));
        MagicData md = MagicApi.get(sp);
        for (GuildQuest q : b.open()) {
            ActiveQuest a = d.find(q.id());
            int state;
            if (a != null) state = isComplete(progress(sp, q, a), q) ? GuildNetwork.Q_READY : GuildNetwork.Q_ACTIVE;
            else if (!trusted) state = GuildNetwork.Q_LOCKED;
            else if (taken.contains(q.id())) state = GuildNetwork.Q_TAKEN;
            else if (!q.repeatable() && md.counter(questCounter(q.id())) > 0) continue;
            else state = GuildNetwork.Q_AVAILABLE;
            out.add(view(sp, q, a, state));
        }
        for (GuildQuest q : b.locked()) out.add(view(sp, q, null, GuildNetwork.Q_LOCKED));
        return out;
    }

    private static boolean sessionValid(ServerPlayer sp, int... modes) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.dim != sp.level().dimension()) return false;
        boolean modeOk = false;
        for (int m : modes) modeOk |= m == s.mode;
        if (!modeOk) return false;
        Vec3 at = s.at;
        if (s.npcId >= 0) {
            Entity npc = sp.level().getEntity(s.npcId);
            if (!(npc instanceof GuildNpcEntity) || !npc.isAlive()) return false;
            at = npc.position();
        }
        return sp.position().distanceToSqr(at) <= REACH * REACH;
    }

    public static void endSession(ServerPlayer sp) {
        SESSIONS.remove(sp.getUUID());
        OFFERED.remove(sp.getUUID());
    }

    /** Servidor parou: esquece sessões (estado estático). */
    public static void clearAll() {
        SESSIONS.clear();
        OFFERED.clear();
    }

    /** Facção do balcão/quadro aberto agora (null = nenhum válido). */
    @Nullable
    private static GuildFaction deskFaction(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || !sessionValid(sp, MODE_RECEPTION, MODE_BOARD)) return null;
        return s.faction();
    }

    /**
     * A prova que o Mestre oferece agora: a que já está na tela (se ainda vale para este rank), senão o sorteio do dia
     * ({@link GuildQuests#trialFor}).
     */
    @Nullable
    private static GuildQuest offeredTrial(ServerPlayer sp, GuildRank target) {
        GuildQuest shown = GuildQuests.get(OFFERED.get(sp.getUUID()));
        if (shown != null && shown.trial() && shown.rank() == target && shown.isAvailable(sp.server)) return shown;
        GuildQuest t = GuildQuests.trialFor(sp.server, target, sp.getUUID());
        if (t != null) OFFERED.put(sp.getUUID(), t.id());
        else OFFERED.remove(sp.getUUID());
        return t;
    }

    // =====================================================================================
    //  Ações recebidas da tela
    // =====================================================================================
    public static void handleAction(ServerPlayer sp, GuildNetwork.Action a) {
        GuildFaction desk = deskFaction(sp);
        switch (a.action()) {
            case GuildNetwork.ACT_REGISTER -> { if (sessionValid(sp, MODE_RECEPTION)) register(sp); }
            case GuildNetwork.ACT_ACCEPT -> { if (desk != null) accept(sp, a.id(), desk); }
            case GuildNetwork.ACT_TURN_IN -> { if (desk != null) turnIn(sp, a.id(), desk); }
            case GuildNetwork.ACT_ABANDON -> abandon(sp, a.id());
            case GuildNetwork.ACT_BUY -> { if (sessionValid(sp, MODE_RECEPTION)) buy(sp, a.index(), a.amount()); }
            case GuildNetwork.ACT_TRIAL_ACCEPT -> { if (sessionValid(sp, MODE_MASTER)) trialAccept(sp); }
            case GuildNetwork.ACT_TRIAL_REPORT -> { if (sessionValid(sp, MODE_MASTER)) trialReport(sp); }
            case GuildNetwork.ACT_TRIAL_ABANDON -> trialAbandon(sp);
            case GuildNetwork.ACT_REISSUE_CARD -> { if (sessionValid(sp, MODE_RECEPTION)) reissueCard(sp); }
            default -> { }
        }
        // Atualiza a tela aberta (se o jogador ainda está no balcão/quadro).
        Session s = SESSIONS.get(sp.getUUID());
        if (s != null && sessionValid(sp, s.mode)) sendScreen(sp, s.mode, s.npcId, true);
        else sync(sp, true);
    }

    // =====================================================================================
    //  Registro e cartão
    // =====================================================================================
    public static void register(ServerPlayer sp) {
        GuildData d = data(sp);
        if (d.registered()) return;
        d.setRank(GuildRank.F);
        syncRankCounter(sp, d);
        give(sp, AdventurerCardItem.issue(sp, d));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.registered", GuildRank.F.letter().withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.registered_hint").withStyle(ChatFormatting.GRAY));
        celebrate(sp, false);
        npcSay(sp, "dialogue.isekai_mahou.receptionist.registered", sp.getGameProfile().getName());
    }

    private static void reissueCard(ServerPlayer sp) {
        GuildData d = data(sp);
        if (!d.registered()) return;
        if (countCoins(sp) < REISSUE_PRICE) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.no_coins", REISSUE_PRICE));
            return;
        }
        removeCoins(sp, REISSUE_PRICE);
        give(sp, AdventurerCardItem.issue(sp, d));
        sp.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.card_reissued").withStyle(ChatFormatting.GOLD), true);
    }

    static boolean hasOwnCard(ServerPlayer sp) {
        for (ItemStack s : allStacks(sp.getInventory())) {
            GuildCardInfo info = s.get(GuildContent.CARD.get());
            if (info != null && info.owner().equals(sp.getUUID())) return true;
        }
        return false;
    }

    /** Atualiza os cartões do jogador que estão no inventário dele. */
    static void refreshCards(ServerPlayer sp, GuildData d) {
        if (!d.registered()) return;
        GuildCardInfo now = AdventurerCardItem.infoOf(sp, d);
        for (ItemStack s : allStacks(sp.getInventory())) {
            GuildCardInfo info = s.get(GuildContent.CARD.get());
            if (info != null && info.owner().equals(sp.getUUID()) && !info.equals(now)) s.set(GuildContent.CARD.get(), now);
        }
    }

    /** Espelha o rank no contador "guild_rank" do MagicData (o HUD lê de lá). */
    static void syncRankCounter(ServerPlayer sp, GuildData d) {
        int cur = MagicApi.get(sp).counter("guild_rank");
        if (cur != d.rankLevel()) MagicApi.addCounter(sp, "guild_rank", d.rankLevel() - cur);
    }

    // =====================================================================================
    //  Missões
    // =====================================================================================
    private static void accept(ServerPlayer sp, ResourceLocation id, GuildFaction desk) {
        GuildData d = data(sp);
        GuildQuest q = GuildQuests.get(id);
        if (q == null || q.trial() || q.faction() != desk) return;
        if (q.isGuild() && !d.registered()) return;
        if (!Reputation.canTrade(sp, q.faction().id())) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.faction_refuses", q.faction().displayName()));
            return;
        }
        if (!q.isGuild() && !Reputation.standing(sp, q.faction().id()).atLeast(q.standing())) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.standing_required", q.standing().displayName(),
                    q.faction().displayName()));
            return;
        }
        GuildQuests.Board board = q.isGuild() ? GuildQuests.board(sp.server, d.rank())
                : GuildQuests.factionBoard(sp.server, q.faction(), Reputation.standing(sp, q.faction().id()));
        if (!board.open().contains(q)) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.not_on_board"));
            return;
        }
        Set<ResourceLocation> taken = d.takenToday(GuildQuests.day(sp.server));
        MagicData md = MagicApi.get(sp);
        if (d.find(id) != null || taken.contains(id)) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.already_taken"));
            return;
        }
        if (!q.repeatable() && md.counter(questCounter(id)) > 0) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.already_done"));
            return;
        }
        int limit = q.isGuild() ? GuildData.MAX_ACTIVE : GuildData.MAX_FACTION_ACTIVE;
        if (activeCount(d, q.isGuild()) >= limit) {
            fail(sp, Component.translatable(q.isGuild() ? "message.isekai_mahou.guild.too_many" : "message.isekai_mahou.guild.too_many_faction",
                    limit));
            return;
        }
        ActiveQuest aq = new ActiveQuest(id, baselineFor(md, q), 0, GuildQuests.day(sp.server), false);
        d.active().add(aq);
        taken.add(id);
        d.markDirty();
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.accepted", q.titleComponent().copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.YELLOW));
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
        // v0.9: missões de caça ganham uma Área de Caça (lugar marcado, estandarte, ondas das criaturas).
        HuntingGrounds.create(sp, aq, q, true);
    }

    private static Map<String, Integer> baselineFor(MagicData md, GuildQuest q) {
        Map<String, Integer> base = new HashMap<>();
        for (QuestObjective o : q.objectives()) base.putAll(o.snapshot(md));
        return base;
    }

    private static void turnIn(ServerPlayer sp, ResourceLocation id, GuildFaction desk) {
        GuildData d = data(sp);
        ActiveQuest a = d.find(id);
        GuildQuest q = GuildQuests.get(id);
        if (a == null || q == null) return;
        if (q.faction() != desk) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.wrong_board", q.faction().displayName()));
            return;
        }
        if (!isComplete(progress(sp, q, a), q)) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.not_complete"));
            return;
        }
        consumeGatherItems(sp, q);
        HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.TURNED_IN);
        d.active().remove(a);
        int before = d.points();
        // Pontos de rank só para quem é da Guilda (missões de facção podem ser feitas sem cartão).
        int points = d.registered() ? q.rewards().points() : 0;
        d.addPoints(points);
        if (q.isGuild()) d.addCompleted();
        else d.markDirty();
        MagicApi.addCounter(sp, questCounter(id), 1);
        if (q.isGuild()) {
            MagicApi.addCounter(sp, "guild_quests", 1);
        } else {
            MagicApi.addCounter(sp, "guild.faction_quests", 1);
            MagicApi.addCounter(sp, "guild.faction_quests." + q.faction().id(), 1);
        }
        grantRewards(sp, q.rewards(), q.rank().questGlyphTier);
        q.reputationRewards().forEach((faction, n) -> Reputation.add(sp, faction, n, q.titleComponent()));
        if (q.isGuild()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.completed",
                    q.titleComponent().copy().withStyle(ChatFormatting.WHITE), points).withStyle(ChatFormatting.GOLD));
        } else {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.completed_faction",
                    q.titleComponent().copy().withStyle(ChatFormatting.WHITE), q.faction().displayName().withColor(q.faction().rgb()))
                    .withStyle(ChatFormatting.GOLD));
        }
        celebrate(sp, false);
        Rewards.toast(sp, q.isGuild() ? Component.translatable("toast.isekai_mahou.guild.quest_done", points)
                        : Component.translatable("toast.isekai_mahou.guild.faction_quest_done", q.faction().displayName()), q.titleComponent(),
                new ItemStack(GuildContent.GUILD_COIN.get(), Math.max(1, Math.min(64, q.rewards().coins()))),
                q.isGuild() ? q.rank().rgb() : q.faction().rgb());
        npcSay(sp, "dialogue.isekai_mahou.receptionist.turn_in", sp.getGameProfile().getName());
        GuildRank next = d.rank() == null ? null : d.rank().next();
        if (next != null && before < next.pointsRequired && d.points() >= next.pointsRequired) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.trial_unlocked", next.letter().withStyle(ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static void abandon(ServerPlayer sp, ResourceLocation id) {
        GuildData d = data(sp);
        ActiveQuest a = d.find(id);
        if (a == null) return;
        HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.ABANDONED);
        d.active().remove(a);
        d.markDirty();
        GuildQuest q = GuildQuests.get(id);
        Component title = q != null ? q.titleComponent() : Component.literal(id.toString());
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.abandoned", title).withStyle(ChatFormatting.GRAY));
        sp.playNotifySound(SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // =====================================================================================
    //  Provas de promoção
    // =====================================================================================
    private static void trialAccept(ServerPlayer sp) {
        GuildData d = data(sp);
        if (!d.registered() || d.trial() != null) return;
        GuildRank next = d.rank().next();
        if (next == null) return;
        if (d.points() < next.pointsRequired) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.trial_points", next.pointsRequired, next.letter()));
            return;
        }
        GuildQuest t = offeredTrial(sp, next);
        if (t == null) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.trial_none"));
            return;
        }
        OFFERED.remove(sp.getUUID());
        ActiveQuest tq = new ActiveQuest(t.id(), baselineFor(MagicApi.get(sp), t), 0, GuildQuests.day(sp.server), false);
        d.setTrial(tq);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.trial_accepted", next.letter().withStyle(ChatFormatting.BOLD),
                t.titleComponent().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.LIGHT_PURPLE));
        sp.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
        npcSay(sp, "dialogue.isekai_mahou.master.trial_given");
        npcAnim(sp, "slam");
        HuntingGrounds.create(sp, tq, t, true);
    }

    private static void trialReport(ServerPlayer sp) {
        GuildData d = data(sp);
        ActiveQuest a = d.trial();
        if (a == null) return;
        GuildQuest q = GuildQuests.get(a.id);
        if (q == null) {
            d.setTrial(null);
            return;
        }
        if (!isComplete(progress(sp, q, a), q)) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.not_complete"));
            npcSay(sp, "dialogue.isekai_mahou.master.not_yet");
            return;
        }
        consumeGatherItems(sp, q);
        HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.TURNED_IN);
        MagicApi.addCounter(sp, questCounter(q.id()), 1);
        promote(sp, d, q);
    }

    private static void trialAbandon(ServerPlayer sp) {
        GuildData d = data(sp);
        if (d.trial() == null) return;
        HuntingGrounds.finish(sp, d, d.trial(), HuntingGrounds.Reason.ABANDONED);
        d.setTrial(null);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.trial_abandoned").withStyle(ChatFormatting.GRAY));
        sp.playNotifySound(SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** Sobe um rank: prêmios da prova + pergaminho de promoção + cerimônia (título na tela, anúncio no servidor). */
    private static void promote(ServerPlayer sp, GuildData d, GuildQuest trial) {
        GuildRank next = d.rank().next();
        if (next == null) return;
        d.setRank(next);
        d.setTrial(null);
        syncRankCounter(sp, d);
        // O presente é escolhido ANTES de entregar os outros prêmios (ver PromotionGear: o que o jogador já tem é pulado).
        ItemStack gear = PromotionGear.pick(sp, next);
        grantRewards(sp, trial.rewards(), next.promotionGlyphTier);
        ResourceLocation bonus = randomGlyph(next.promotionGlyphTier, sp, sp.getRandom());
        if (bonus != null) give(sp, GlyphScrollItem.of(bonus));
        else give(sp, new ItemStack(GuildContent.GUILD_COIN.get(), 10 * next.promotionGlyphTier));
        d.addPoints(trial.rewards().points());
        // Promoção também rende reputação com a Guilda (E +20 … S +70).
        Reputation.add(sp, GuildFaction.GUILD.id(), 10 * next.level(), trial.titleComponent());

        MutableComponent letter = next.letter().withStyle(s -> s.withColor(next.rgb()).withBold(true));
        if (!gear.isEmpty()) {
            give(sp, gear.copy());
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.promotion_gift", gear.getDisplayName())
                    .withStyle(ChatFormatting.GOLD));
        }
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.guild.promoted", letter),
                gear.isEmpty() ? Component.translatable("toast.isekai_mahou.guild.promoted_sub")
                        : Component.translatable("toast.isekai_mahou.guild.gift", gear.getHoverName()),
                gear.isEmpty() ? AdventurerCardItem.issue(sp, d) : gear, next.rgb());
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.isekai_mahou.guild.promoted", letter)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.isekai_mahou.guild.promoted_sub")
                .withStyle(ChatFormatting.GOLD)));
        sp.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.isekai_mahou.guild.promoted_broadcast",
                sp.getDisplayName(), letter).withStyle(ChatFormatting.GOLD), false);
        celebrate(sp, true);
        npcSay(sp, "dialogue.isekai_mahou.master.promoted", "#" + GuildRank.LETTER_KEY + next.id());
        npcAnim(sp, "slam");
    }

    // =====================================================================================
    //  Loja
    // =====================================================================================
    private static ItemStack offerDisplay(ShopOffer o) {
        if (o.legendScroll()) return legendScrollDisplay();
        if (o.item().isPresent()) return o.item().get().toStack();
        if (o.glyph().isPresent()) {
            return GlyphRegistry.get(o.glyph().get()) == null ? ItemStack.EMPTY : GlyphScrollItem.of(o.glyph().get());
        }
        return randomScrollDisplay(o.glyphTier(), 1);
    }

    /** Pergaminho "?" para mostrar uma recompensa/oferta de glifo aleatório. */
    static ItemStack randomScrollDisplay(int tier, int count) {
        ItemStack s = new ItemStack(CoreRegistries.GLYPH_SCROLL.get(), count);
        s.set(DataComponents.ITEM_NAME, Component.translatable("guild.isekai_mahou.random_scroll", tier));
        return s;
    }

    /** Pergaminho "?" dourado para a oferta de Magia Lendária aleatória (vazio se nenhum módulo registrou lendas). */
    static ItemStack legendScrollDisplay() {
        if (Legends.all().isEmpty()) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(CoreRegistries.SPELL_SCROLL.get());
        s.set(DataComponents.ITEM_NAME, Component.translatable("guild.isekai_mahou.random_legend"));
        s.set(DataComponents.RARITY, Rarity.EPIC);
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private static void buy(ServerPlayer sp, int index, int amount) {
        GuildData d = data(sp);
        List<ShopOffer> offers = GuildQuests.shop();
        if (!d.registered() || index < 0 || index >= offers.size()) return;
        ShopOffer o = offers.get(index);
        if (o.rank().ordinal() > d.rank().ordinal()) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.rank_required", o.rank().letter()));
            return;
        }
        if (!o.standingOk(sp)) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.standing_required", o.standing().get().displayName(),
                    GuildFaction.GUILD.displayName()));
            return;
        }
        if (!Reputation.canTrade(sp, GuildFaction.GUILD.id())) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.faction_refuses", GuildFaction.GUILD.displayName()));
            return;
        }
        int price = o.priceFor(sp);
        int times = o.item().isPresent() ? Math.max(1, Math.min(16, amount)) : 1;
        int bought = 0;
        ItemStack last = ItemStack.EMPTY;
        for (int k = 0; k < times; k++) {
            if (countCoins(sp) < price) break;
            ItemStack stack;
            if (o.legendScroll()) {
                // Prefere lendas que o Círculo do jogador já permite conjurar.
                stack = Legends.randomScroll(sp.getRandom(), MagicApi.get(sp).circle());
                if (stack.isEmpty()) {
                    fail(sp, Component.translatable("message.isekai_mahou.guild.legend_none"));
                    return;
                }
                Rewards.toast(sp, Component.translatable("toast.isekai_mahou.guild.legend_bought"), stack.getHoverName(), stack, 0xFFC94F);
            } else if (o.item().isPresent()) {
                stack = o.item().get().toStack();
            } else if (o.glyph().isPresent()) {
                ResourceLocation g = o.glyph().get();
                if (GlyphRegistry.get(g) == null) break;
                if (MagicApi.get(sp).knowsGlyph(g)) {
                    fail(sp, Component.translatable("message.isekai_mahou.guild.glyph_known"));
                    return;
                }
                stack = GlyphScrollItem.of(g);
            } else {
                ResourceLocation g = randomGlyph(o.glyphTier(), sp, sp.getRandom());
                if (g == null) {
                    fail(sp, Component.translatable("message.isekai_mahou.guild.tier_known", o.glyphTier()));
                    return;
                }
                stack = GlyphScrollItem.of(g);
            }
            if (stack.isEmpty()) break;
            removeCoins(sp, price);
            last = stack.copy();
            give(sp, stack);
            bought++;
        }
        if (bought == 0) {
            fail(sp, Component.translatable("message.isekai_mahou.guild.no_coins", price));
            return;
        }
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.bought", bought, last.getHoverName(), bought * price)
                .withStyle(ChatFormatting.GREEN), true);
        sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
        npcSay(sp, "dialogue.isekai_mahou.receptionist.bought");
    }

    // =====================================================================================
    //  Progresso
    // =====================================================================================
    /** Progresso de cada objetivo (já limitado ao alvo). */
    public static int[] progress(ServerPlayer sp, GuildQuest q, ActiveQuest a) {
        MagicData md = MagicApi.get(sp);
        int[] out = new int[q.objectives().size()];
        for (int i = 0; i < out.length; i++) {
            QuestObjective o = q.objectives().get(i);
            int v = switch (o.kind()) {
                case KILL -> {
                    int sum = 0;
                    // A família toda conta (goblin com lobo, arqueiro... para "goblins"; slime de fogo... para "slimes")
                    for (ResourceLocation e : com.renanpontes.isekaimahou.core.api.KillFamilies.expand(o.entities())) {
                        int kill = delta(md, a, QuestObjective.killKey(e));
                        int boss = Math.max(delta(md, a, "boss." + e.getPath()), delta(md, a, "boss." + e.getNamespace() + "." + e.getPath()));
                        sum += Math.max(kill, boss);
                    }
                    yield sum;
                }
                case COUNTER -> o.absolute() ? md.counter(o.key().get()) : delta(md, a, o.key().get());
                case FLAG -> md.hasFlag(o.key().get()) ? 1 : 0;
                case GATHER -> countItems(sp, o);
                case EXPLORE -> a.explored(i) ? 1 : 0;
                case TRIAL_CLEAR -> trialProgress(md, a, o);
            };
            out[i] = Math.max(0, Math.min(o.target(), v));
        }
        return out;
    }

    /**
     * Provações: conclusões de rank &gt;= N desde o aceite (ou no total, com "absolute"); com "distinct", quantos
     * ELEMENTOS diferentes tiveram uma conclusão dessas.
     */
    private static int trialProgress(MagicData md, ActiveQuest a, QuestObjective o) {
        if (o.absolute()) {
            return o.distinct() ? QuestObjective.trialElementsCleared(md, o.rank()) : QuestObjective.trialValue(md, o.rank(), o.trialElement());
        }
        if (!o.distinct()) {
            Element e = o.trialElement();
            return deltaOf(a, QuestObjective.trialKey(o.rank(), e), QuestObjective.trialValue(md, o.rank(), e));
        }
        int n = 0;
        for (Element e : QuestObjective.trialElements(null)) {
            if (deltaOf(a, QuestObjective.trialKey(o.rank(), e), QuestObjective.trialValue(md, o.rank(), e)) > 0) n++;
        }
        return n;
    }

    private static int delta(MagicData md, ActiveQuest a, String key) {
        return deltaOf(a, key, md.counter(key));
    }

    /** Quanto {@code current} cresceu desde o aceite (a linha de base guardada na missão). */
    private static int deltaOf(ActiveQuest a, String key, int current) {
        Integer base = a.baseline.get(key);
        if (base == null) {
            // Missão editada depois do aceite: começa a contar a partir de agora.
            a.baseline.put(key, current);
            return 0;
        }
        return current - base;
    }

    public static boolean isComplete(int[] progress, GuildQuest q) {
        for (int i = 0; i < progress.length; i++) if (progress[i] < q.objectives().get(i).target()) return false;
        return true;
    }

    private static boolean hasReadyQuest(ServerPlayer sp, GuildData d) {
        for (ActiveQuest a : d.active()) {
            GuildQuest q = GuildQuests.get(a.id);
            if (q != null && q.isGuild() && isComplete(progress(sp, q, a), q)) return true;
        }
        return false;
    }

    /** Missões ativas da Guilda ({@code guild} true) ou de facção. Missões que sumiram contam como da Guilda. */
    static int activeCount(GuildData d, boolean guild) {
        int n = 0;
        for (ActiveQuest a : d.active()) {
            GuildQuest q = GuildQuests.get(a.id);
            boolean isGuild = q == null || q.isGuild();
            if (isGuild == guild) n++;
        }
        return n;
    }

    // =====================================================================================
    //  Tick do jogador (1x por segundo): exploração, avisos e sincronização
    // =====================================================================================
    static void tickPlayer(ServerPlayer sp) {
        GuildData d = data(sp);
        CompanionService.tickOwner(sp, d);
        // Sem cartão e sem missões de facção: nada a checar.
        if (!d.registered() && d.active().isEmpty()) {
            if (d.dirty) sync(sp, false);
            return;
        }
        // Missões que sumiram (datapack mudou)
        for (Iterator<ActiveQuest> it = d.active().iterator(); it.hasNext(); ) {
            ActiveQuest a = it.next();
            if (GuildQuests.get(a.id) == null) {
                HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.GONE);
                it.remove();
                d.markDirty();
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.quest_gone", a.id.toString()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (d.trial() != null && GuildQuests.get(d.trial().id) == null) {
            HuntingGrounds.finish(sp, d, d.trial(), HuntingGrounds.Reason.GONE);
            d.setTrial(null);
        }

        for (ActiveQuest a : d.active()) checkQuest(sp, d, a, false);
        if (d.trial() != null) checkQuest(sp, d, d.trial(), true);
        sync(sp, false);
    }

    /** Detecta lugares explorados e avisa quando a missão fica pronta para entregar. */
    private static void checkQuest(ServerPlayer sp, GuildData d, ActiveQuest a, boolean trial) {
        GuildQuest q = GuildQuests.get(a.id);
        if (q == null) return;
        if (q.hasExplore()) {
            ServerLevel level = sp.serverLevel();
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestObjective o = q.objectives().get(i);
                if (o.kind() != QuestObjective.Kind.EXPLORE || a.explored(i)) continue;
                if (o.isAt(level, sp.blockPosition())) {
                    a.markExplored(i);
                    d.markDirty();
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.explored", o.subject()).withStyle(ChatFormatting.AQUA), true);
                    sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.3F);
                }
            }
        }
        boolean done = isComplete(progress(sp, q, a), q);
        // v0.9: Área de Caça (chegada, ondas, fim) — 1x por segundo, junto com a checagem da missão.
        if (a.hunt != null) HuntingGrounds.tick(sp, d, a, q, done);
        if (done && !a.notified) {
            a.notified = true;
            d.markDirty();
            if (!trial && !q.isGuild()) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.ready_faction",
                        q.titleComponent().copy().withStyle(ChatFormatting.WHITE), q.faction().displayName().withColor(q.faction().rgb()))
                        .withStyle(ChatFormatting.GREEN));
            } else {
                sp.sendSystemMessage(Component.translatable(trial ? "message.isekai_mahou.guild.trial_ready" : "message.isekai_mahou.guild.ready",
                        q.titleComponent().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));
            }
            sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
            Rewards.toast(sp, Component.translatable(trial ? "toast.isekai_mahou.guild.trial_ready" : "toast.isekai_mahou.guild.quest_ready"),
                    q.titleComponent(), q.objectives().get(0).icon(), q.isGuild() ? READY_RGB : q.faction().rgb());
        } else if (!done && a.notified) {
            a.notified = false;
        }
    }

    /** Envia a visão ao cliente se mudou (ou sempre, com {@code force}); atualiza cartões e o contador de rank. */
    public static void sync(ServerPlayer sp, boolean force) {
        GuildData d = data(sp);
        List<QuestView> active = new ArrayList<>();
        for (ActiveQuest a : d.active()) {
            GuildQuest q = GuildQuests.get(a.id);
            if (q == null) continue;
            int[] p = progress(sp, q, a);
            active.add(view(sp, q, a, p, isComplete(p, q) ? GuildNetwork.Q_READY : GuildNetwork.Q_ACTIVE));
        }
        Optional<QuestView> trial = Optional.empty();
        if (d.trial() != null) {
            GuildQuest q = GuildQuests.get(d.trial().id);
            if (q != null) {
                int[] p = progress(sp, q, d.trial());
                trial = Optional.of(view(sp, q, d.trial(), p, isComplete(p, q) ? GuildNetwork.Q_READY : GuildNetwork.Q_ACTIVE));
            }
        }
        GuildRank rank = d.rank();
        GuildRank next = rank == null ? null : rank.next();
        boolean trialReady = next != null && d.trial() == null && d.points() >= next.pointsRequired;
        GuildNetwork.State state = new GuildNetwork.State(d.rankLevel(), d.points(), d.completed(), active, trial, trialReady,
                d.hall(), d.masterPos(), Map.copyOf(d.boards()));
        int hash = signature(state);
        if (force || d.dirty || hash != d.lastSentHash) {
            if (d.dirty) {
                refreshCards(sp, d);
                syncRankCounter(sp, d);
            }
            d.dirty = false;
            d.lastSentHash = hash;
            PacketDistributor.sendToPlayer(sp, state);
        }
    }

    /** Assinatura barata do que muda com frequência (progresso, rank, pontos). */
    private static int signature(GuildNetwork.State s) {
        int h = s.rank() * 31 + s.points() * 17 + s.completed() * 7 + (s.trialReady() ? 1 : 0);
        h = h * 31 + s.hall().hashCode();
        h = h * 31 + s.master().hashCode();
        h = h * 31 + s.boards().hashCode();
        List<QuestView> all = new ArrayList<>(s.active());
        s.trial().ifPresent(all::add);
        for (QuestView q : all) {
            h = h * 31 + q.id().hashCode() + q.state();
            for (ObjectiveView o : q.objectives()) h = h * 31 + o.progress();
            // v0.9: Área de Caça (fase, onda, centro medido) — o rastreador acompanha.
            if (q.hunt().isPresent()) {
                GuildNetwork.HuntView hv = q.hunt().get();
                h = h * 31 + hv.phase() * 7 + hv.wave() * 13 + hv.pos().hashCode();
            }
        }
        // A prévia do presente da promoção muda com o inventário (PromotionGear pula o que o jogador já tem).
        if (s.trial().isPresent()) {
            for (ItemStack it : s.trial().get().items()) h = h * 31 + Item.getId(it.getItem()) * 67 + it.getCount();
        }
        return h;
    }

    // =====================================================================================
    //  Visões
    // =====================================================================================
    private static QuestView view(ServerPlayer sp, GuildQuest q, @Nullable ActiveQuest a, int state) {
        return view(sp, q, a, a == null ? new int[q.objectives().size()] : progress(sp, q, a), state);
    }

    private static QuestView view(ServerPlayer sp, GuildQuest q, @Nullable ActiveQuest a, int[] prog, int state) {
        List<ObjectiveView> objs = new ArrayList<>();
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            // Itens a entregar mostram o que o jogador já tem mesmo antes de aceitar.
            int p = a == null && o.kind() == QuestObjective.Kind.GATHER ? Math.min(o.target(), countItems(sp, o)) : prog[i];
            objs.add(new ObjectiveView(o.icon(), o.describe(), p, o.target()));
        }
        QuestRewards r = q.rewards();
        List<ItemStack> items = new ArrayList<>();
        int tier = q.trial() ? q.rank().promotionGlyphTier : q.rank().questGlyphTier;
        if (r.glyph().isPresent()) {
            if (r.randomGlyph()) items.add(randomScrollDisplay(tier, r.glyphCount()));
            else {
                ResourceLocation g = ResourceLocation.tryParse(r.glyph().get());
                if (g != null && GlyphRegistry.get(g) != null) items.add(GlyphScrollItem.of(g).copyWithCount(r.glyphCount()));
            }
        }
        if (q.trial()) {
            items.add(randomScrollDisplay(q.rank().promotionGlyphTier, 1));
            ItemStack gear = PromotionGear.pick(sp, q.rank());
            if (!gear.isEmpty()) items.add(gear);
        }
        for (RewardItem ri : r.items()) {
            ItemStack s = ri.toStack();
            if (!s.isEmpty() && items.size() < MAX_VIEW_ITEMS) items.add(s);
        }
        return new QuestView(q.id(), q.rank().level(), q.category(), q.titleComponent(), q.descriptionComponent(), objs,
                r.coins(), r.xp(), q.isGuild() || data(sp).registered() ? r.points() : 0, items, state, q.faction().id(),
                q.isGuild() ? "" : q.standing().id, q.reputationRewards(), huntView(sp, q, a, state));
    }

    /** v0.9: Área de Caça da missão (ativa), ou o aviso "ganha Área de Caça ao aceitar" no quadro/Mestre. */
    private static Optional<GuildNetwork.HuntView> huntView(ServerPlayer sp, GuildQuest q, @Nullable ActiveQuest a, int state) {
        int waves = HuntingGrounds.wavesFor(q.rank());
        if (a != null) {
            HuntState h = a.hunt;
            if (h == null || !h.active()) return Optional.empty();
            int phase = h.phase == HuntState.FIGHT ? GuildNetwork.HUNT_FIGHT : GuildNetwork.HUNT_TRAVEL;
            return Optional.of(new GuildNetwork.HuntView(Optional.of(h.globalPos()), phase, h.wave, waves, HuntingGrounds.placeName(h),
                    HuntingGrounds.colorOf(h)));
        }
        if ((state == GuildNetwork.Q_AVAILABLE || state == GuildNetwork.Q_TRIAL_OFFER || state == GuildNetwork.Q_LOCKED)
                && HuntRules.eligible(sp.serverLevel(), q)) {
            return Optional.of(new GuildNetwork.HuntView(Optional.empty(), GuildNetwork.HUNT_OFFER, 0, waves, Component.empty(), 0x6BD66B));
        }
        return Optional.empty();
    }

    // =====================================================================================
    //  Recompensas e itens
    // =====================================================================================
    private static void grantRewards(ServerPlayer sp, QuestRewards r, int glyphTier) {
        if (r.coins() > 0) give(sp, new ItemStack(GuildContent.GUILD_COIN.get(), r.coins()));
        if (r.xp() > 0) MagicApi.giveXp(sp, r.xp(), "quest");
        if (r.glyph().isPresent()) {
            MagicData md = MagicApi.get(sp);
            for (int i = 0; i < r.glyphCount(); i++) {
                ResourceLocation g = r.randomGlyph() ? randomGlyph(glyphTier, sp, sp.getRandom()) : ResourceLocation.tryParse(r.glyph().get());
                // Glifo específico que o jogador já conhece (missão repetida): vira moedas em vez de um pergaminho inútil.
                boolean useless = g != null && !r.randomGlyph() && md.knowsGlyph(g);
                if (g != null && GlyphRegistry.get(g) != null && !useless) give(sp, GlyphScrollItem.of(g));
                else give(sp, new ItemStack(GuildContent.GUILD_COIN.get(), 4 * Math.max(1, glyphTier)));
            }
        }
        for (RewardItem ri : r.items()) give(sp, ri.toStack());
    }

    /** Entrega uma pilha (dividida no tamanho máximo); o que não couber cai no chão. */
    public static void give(ServerPlayer sp, ItemStack stack) {
        int left = stack.getCount();
        while (left > 0 && !stack.isEmpty()) {
            int n = Math.min(left, stack.getMaxStackSize());
            ItemHandlerHelper.giveItemToPlayer(sp, stack.copyWithCount(n));
            left -= n;
        }
    }

    /**
     * Glifo aleatório de um tier, sem os de Magia de Origem. Com jogador: só glifos que ele ainda não conhece,
     * preferindo os neutros e os dos elementos que ele já despertou. Retorna null se não houver nenhum.
     */
    @Nullable
    public static ResourceLocation randomGlyph(int tier, @Nullable ServerPlayer player, RandomSource random) {
        List<Glyph> any = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() != null || g.tier() != tier) continue;
            if (g.isLegendary() && tier < 5) continue;
            any.add(g);
        }
        if (player == null) return any.isEmpty() ? null : any.get(random.nextInt(any.size())).id();
        MagicData md = MagicApi.get(player);
        List<Glyph> unknown = new ArrayList<>(), usable = new ArrayList<>();
        for (Glyph g : any) {
            if (md.knowsGlyph(g.id())) continue;
            unknown.add(g);
            if (g.element() == Element.ARCANE || md.isAwakened(g.element())) usable.add(g);
        }
        List<Glyph> pool = !usable.isEmpty() ? usable : unknown;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size())).id();
    }

    static List<ItemStack> allStacks(Inventory inv) {
        List<ItemStack> l = new ArrayList<>(inv.items);
        l.addAll(inv.offhand);
        return l;
    }

    private static int countItems(ServerPlayer sp, QuestObjective o) {
        int n = 0;
        for (ItemStack s : allStacks(sp.getInventory())) if (o.matches(s)) n += s.getCount();
        return n;
    }

    private static void consumeGatherItems(ServerPlayer sp, GuildQuest q) {
        for (QuestObjective o : q.objectives()) {
            if (o.kind() != QuestObjective.Kind.GATHER) continue;
            int left = o.target();
            for (NonNullList<ItemStack> list : List.of(sp.getInventory().items, sp.getInventory().offhand)) {
                for (ItemStack s : list) {
                    if (left <= 0) break;
                    if (!o.matches(s)) continue;
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                }
            }
        }
        sp.getInventory().setChanged();
    }

    public static int countCoins(Player p) {
        int n = 0;
        for (ItemStack s : allStacks(p.getInventory())) if (s.is(GuildContent.GUILD_COIN.get())) n += s.getCount();
        return n;
    }

    static void removeCoins(ServerPlayer sp, int amount) {
        int left = amount;
        for (NonNullList<ItemStack> list : List.of(sp.getInventory().items, sp.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (!s.is(GuildContent.GUILD_COIN.get())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        sp.getInventory().setChanged();
    }

    public static String questCounter(ResourceLocation id) {
        return id.getNamespace().equals(IsekaiMahou.MODID) ? "quest." + id.getPath() : "quest." + id.getNamespace() + "." + id.getPath();
    }

    // =====================================================================================
    //  Retorno visual/sonoro
    // =====================================================================================
    static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.4F, 1.3F);
    }

    private static void celebrate(ServerPlayer sp, boolean big) {
        ServerLevel level = sp.serverLevel();
        SoundEvent sound = big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP;
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), sound, SoundSource.PLAYERS, big ? 1.0F : 0.7F, big ? 1.0F : 1.3F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), big ? 30 : 12, 0.6, 0.8, 0.6, 0.0);
        if (big) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(), 60, 0.4, 0.8, 0.4, 0.5);
            level.sendParticles(ParticleTypes.FIREWORK, sp.getX(), sp.getY() + 2.2, sp.getZ(), 40, 0.8, 0.4, 0.8, 0.12);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** O NPC da sessão (recepcionista/mestre) fala com o jogador. */
    private static void npcSay(ServerPlayer sp, String key, String arg) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.npcId < 0) return;
        if (sp.level().getEntity(s.npcId) instanceof GuildNpcEntity npc) npc.say(key, arg);
    }

    private static void npcSay(ServerPlayer sp, String key) {
        npcSay(sp, key, null);
    }

    private static void npcAnim(ServerPlayer sp, String anim) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.npcId < 0) return;
        if (sp.level().getEntity(s.npcId) instanceof GuildNpcEntity npc) npc.triggerAnim("action", anim);
    }

    // =====================================================================================
    //  Comandos (testes)
    // =====================================================================================
    /** Define o rank diretamente (registra se preciso). */
    public static void setRank(ServerPlayer sp, GuildRank rank) {
        GuildData d = data(sp);
        boolean wasRegistered = d.registered();
        d.setRank(rank);
        d.setTrial(null);
        syncRankCounter(sp, d);
        if (!wasRegistered && !hasOwnCard(sp)) give(sp, AdventurerCardItem.issue(sp, d));
        sync(sp, true);
    }

    /** Completa os objetivos de contador/exploração das missões ativas (itens a entregar continuam necessários). */
    public static void forceComplete(ServerPlayer sp) {
        GuildData d = data(sp);
        MagicData md = MagicApi.get(sp);
        List<ActiveQuest> all = new ArrayList<>(d.active());
        if (d.trial() != null) all.add(d.trial());
        for (ActiveQuest a : all) {
            GuildQuest q = GuildQuests.get(a.id);
            if (q == null) continue;
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestObjective o = q.objectives().get(i);
                switch (o.kind()) {
                    case KILL -> {
                        ResourceLocation e = o.entities().get(0);
                        a.baseline.put(QuestObjective.killKey(e), md.counter(QuestObjective.killKey(e)) - o.target());
                    }
                    case COUNTER -> { if (!o.absolute()) a.baseline.put(o.key().get(), md.counter(o.key().get()) - o.target()); }
                    case EXPLORE -> a.markExplored(i);
                    case TRIAL_CLEAR -> {
                        if (o.absolute()) break;
                        if (o.distinct()) {
                            // N elementos "com uma conclusão a mais" desde o aceite
                            List<Element> els = QuestObjective.trialElements(null);
                            for (int k = 0; k < Math.min(o.target(), els.size()); k++) {
                                Element e = els.get(k);
                                a.baseline.put(QuestObjective.trialKey(o.rank(), e), QuestObjective.trialValue(md, o.rank(), e) - 1);
                            }
                        } else {
                            Element e = o.trialElement();
                            a.baseline.put(QuestObjective.trialKey(o.rank(), e), QuestObjective.trialValue(md, o.rank(), e) - o.target());
                        }
                    }
                    default -> { }
                }
            }
        }
        d.markDirty();
        sync(sp, true);
    }

    public static void reset(ServerPlayer sp) {
        GuildData old = data(sp);
        for (ActiveQuest a : new ArrayList<>(old.active())) HuntingGrounds.finish(sp, old, a, HuntingGrounds.Reason.ABANDONED);
        if (old.trial() != null) HuntingGrounds.finish(sp, old, old.trial(), HuntingGrounds.Reason.ABANDONED);
        CompanionService.releaseAll(sp, data(sp));
        sp.setData(GuildContent.GUILD, new GuildData());
        syncRankCounter(sp, data(sp));
        sync(sp, true);
    }

    private GuildService() {}
}
