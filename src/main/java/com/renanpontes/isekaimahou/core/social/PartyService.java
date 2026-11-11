package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Party.Result;
import com.renanpontes.isekaimahou.core.social.SocialData.PartyRec;
import com.renanpontes.isekaimahou.core.social.SocialEvent.PartyChange;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Regras dos grupos (servidor). API pública: {@link Party}. */
public final class PartyService {
    /** Membros do grupo a até esta distância (mesma dimensão) também ganham o XP de abate (o núcleo já dá a quem está a 24). */
    public static final double PARTY_XP_RADIUS = 64.0;
    public static final int PARTY_COLOR = 0x7CFF8A;

    /** Jogadores com o chat de grupo ligado (o chat normal vai só para o grupo). */
    private static final Set<UUID> PARTY_CHAT = new HashSet<>();

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    @Nullable
    public static PartyRec partyOf(Player p) {
        MinecraftServer server = p.getServer();
        return server == null ? null : SocialData.get(server).partyOf(p.getUUID());
    }

    public static boolean sameParty(@Nullable MinecraftServer server, UUID a, UUID b) {
        if (server == null) return false;
        if (a.equals(b)) return true;
        SocialData d = SocialData.get(server);
        UUID pa = d.partyOf.get(a);
        return pa != null && pa.equals(d.partyOf.get(b));
    }

    public static List<UUID> members(Player p) {
        PartyRec rec = partyOf(p);
        return rec == null ? List.of() : List.copyOf(rec.members);
    }

    @Nullable
    public static UUID leader(Player p) {
        PartyRec rec = partyOf(p);
        return rec == null ? null : rec.leader;
    }

    public static List<ServerPlayer> onlineMembers(ServerPlayer p) {
        PartyRec rec = partyOf(p);
        if (rec == null) return List.of(p);
        return online(p.server, rec);
    }

    static List<ServerPlayer> online(MinecraftServer server, PartyRec rec) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID m : rec.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m);
            if (sp != null) out.add(sp);
        }
        return out;
    }

    public static boolean partyChat(UUID player) {
        return PARTY_CHAT.contains(player);
    }

    // =====================================================================================
    //  Convite / aceitar / recusar
    // =====================================================================================
    public static Result invite(ServerPlayer from, ServerPlayer to) {
        if (from == to) return Result.SELF;
        SocialData d = SocialData.get(from.server);
        PartyRec mine = d.partyOf(from.getUUID());
        PartyRec theirs = d.partyOf(to.getUUID());
        if (theirs != null) return mine == theirs ? Result.ALREADY_MEMBER : Result.TARGET_IN_PARTY;
        if (mine != null && !mine.leader.equals(from.getUUID())) return Result.NOT_LEADER;
        if (mine != null && mine.members.size() >= Party.MAX_MEMBERS) return Result.PARTY_FULL;
        // Os dois se convidaram: aceita direto
        if (Requests.has(from.server, from.getUUID(), to.getUUID(), Requests.Kind.PARTY)) return accept(from, to.getUUID());
        Requests.send(from, to, Requests.Kind.PARTY);
        return Result.OK;
    }

    public static Result accept(ServerPlayer player, @Nullable UUID from) {
        MinecraftServer server = player.server;
        Requests.Request req = from != null ? Requests.take(server, player.getUUID(), from, Requests.Kind.PARTY)
                : takeLatest(server, player.getUUID());
        if (req == null) return Result.NO_REQUEST;
        SocialData d = SocialData.get(server);
        if (d.partyOf(player.getUUID()) != null) return Result.ALREADY_IN_PARTY;
        ServerPlayer inviter = server.getPlayerList().getPlayer(req.from());
        PartyRec party = d.partyOf(req.from());
        // Quem convidou entrou em outro grupo (ou perdeu a liderança) depois do convite: o convite não vale mais
        if (party != null && !party.leader.equals(req.from())) return Result.NO_REQUEST;
        if (party == null) {
            if (inviter == null) return Result.OFFLINE;
            party = d.create(inviter.getUUID(), inviter.getGameProfile().getName());
            NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, inviter.getUUID(), PartyChange.CREATED));
        }
        if (party.members.size() >= Party.MAX_MEMBERS) return Result.PARTY_FULL;
        d.addMember(party, player.getUUID(), player.getGameProfile().getName());
        Requests.forget(player.getUUID(), Requests.Kind.PARTY);
        broadcast(server, party, Component.translatable("social.isekai_mahou.party.joined",
                Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.BOLD)).withColor(PARTY_COLOR),
                SoundEvents.PLAYER_LEVELUP, 0.5F, 1.6F);
        player.displayClientMessage(Component.translatable("social.isekai_mahou.party.welcome").withColor(PARTY_COLOR), true);
        NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, player.getUUID(), PartyChange.JOINED));
        changed(server, party);
        return Result.OK;
    }

    @Nullable
    private static Requests.Request takeLatest(MinecraftServer server, UUID to) {
        Requests.Request latest = Requests.latest(server, to, Requests.Kind.PARTY);
        return latest == null ? null : Requests.take(server, to, latest.from(), Requests.Kind.PARTY);
    }

    public static Result decline(ServerPlayer player, @Nullable UUID from) {
        MinecraftServer server = player.server;
        Requests.Request req = from != null ? Requests.take(server, player.getUUID(), from, Requests.Kind.PARTY)
                : takeLatest(server, player.getUUID());
        if (req == null) return Result.NO_REQUEST;
        ServerPlayer inviter = server.getPlayerList().getPlayer(req.from());
        if (inviter != null) {
            inviter.displayClientMessage(Component.translatable("social.isekai_mahou.party.declined", player.getGameProfile().getName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return Result.OK;
    }

    // =====================================================================================
    //  Sair / expulsar / promover / desfazer
    // =====================================================================================
    public static Result leave(ServerPlayer player) {
        SocialData d = SocialData.get(player.server);
        PartyRec party = d.partyOf(player.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        removeFrom(player.server, d, party, player.getUUID(), PartyChange.LEFT);
        player.displayClientMessage(Component.translatable("social.isekai_mahou.party.you_left").withStyle(ChatFormatting.GRAY), true);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.8F);
        return Result.OK;
    }

    public static Result kick(ServerPlayer leader, UUID target) {
        SocialData d = SocialData.get(leader.server);
        PartyRec party = d.partyOf(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(leader.getUUID())) return Result.NOT_LEADER;
        if (target.equals(leader.getUUID())) return Result.SELF;
        if (!party.members.contains(target)) return Result.NOT_MEMBER;
        ServerPlayer victim = leader.server.getPlayerList().getPlayer(target);
        removeFrom(leader.server, d, party, target, PartyChange.KICKED);
        if (victim != null) {
            victim.sendSystemMessage(Component.translatable("social.isekai_mahou.party.you_were_kicked", leader.getGameProfile().getName())
                    .withStyle(ChatFormatting.RED));
            victim.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1F);
        }
        return Result.OK;
    }

    public static Result promote(ServerPlayer leader, UUID target) {
        SocialData d = SocialData.get(leader.server);
        PartyRec party = d.partyOf(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(leader.getUUID())) return Result.NOT_LEADER;
        if (target.equals(leader.getUUID())) return Result.SELF;
        if (!party.members.contains(target)) return Result.NOT_MEMBER;
        party.leader = target;
        d.setDirty();
        broadcast(leader.server, party, Component.translatable("social.isekai_mahou.party.promoted",
                Component.literal(party.name(target)).withStyle(ChatFormatting.BOLD)).withColor(0xFFD86B),
                SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.3F);
        NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, target, PartyChange.PROMOTED));
        changed(leader.server, party);
        return Result.OK;
    }

    public static Result disband(ServerPlayer leader) {
        SocialData d = SocialData.get(leader.server);
        PartyRec party = d.partyOf(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(leader.getUUID())) return Result.NOT_LEADER;
        disbandParty(leader.server, d, party);
        return Result.OK;
    }

    /** Tira um membro; o líder que sai passa a liderança (online primeiro); grupo de 1 se desfaz. */
    private static void removeFrom(MinecraftServer server, SocialData d, PartyRec party, UUID member, PartyChange why) {
        String name = party.name(member);
        d.removeMember(party, member);
        PARTY_CHAT.remove(member);
        SocialSync.clearParty(server, member);
        NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, member, why));
        if (party.members.size() <= 1) {
            disbandParty(server, d, party);
            return;
        }
        if (party.leader.equals(member)) {
            UUID next = null;
            for (UUID m : party.members) {
                if (server.getPlayerList().getPlayer(m) != null) {
                    next = m;
                    break;
                }
            }
            party.leader = next != null ? next : party.members.iterator().next();
            d.setDirty();
            broadcast(server, party, Component.translatable("social.isekai_mahou.party.new_leader",
                    Component.literal(party.name(party.leader)).withStyle(ChatFormatting.BOLD)).withColor(0xFFD86B), null, 0, 0);
            NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, party.leader, PartyChange.PROMOTED));
        }
        String key = why == PartyChange.KICKED ? "social.isekai_mahou.party.kicked" : "social.isekai_mahou.party.left";
        broadcast(server, party, Component.translatable(key, Component.literal(name).withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GRAY), SoundEvents.BOOK_PAGE_TURN, 0.6F, 0.8F);
        changed(server, party);
    }

    private static void disbandParty(MinecraftServer server, SocialData d, PartyRec party) {
        List<UUID> members = new ArrayList<>(party.members);
        for (ServerPlayer p : online(server, party)) {
            p.displayClientMessage(Component.translatable("social.isekai_mahou.party.disbanded").withStyle(ChatFormatting.GRAY), true);
        }
        d.disband(party);
        for (UUID m : members) {
            PARTY_CHAT.remove(m);
            SocialSync.clearParty(server, m);
        }
        NeoForge.EVENT_BUS.post(new SocialEvent.Party(party.id, party.leader, PartyChange.DISBANDED));
    }

    // =====================================================================================
    //  Chat de grupo
    // =====================================================================================
    public static Result chat(ServerPlayer from, String message) {
        PartyRec party = partyOf(from);
        if (party == null) return Result.NOT_IN_PARTY;
        String text = message.strip();
        if (text.isEmpty()) return Result.OK;
        if (text.length() > 256) text = text.substring(0, 256);
        MutableComponent line = Component.translatable("social.isekai_mahou.party.chat",
                Component.literal(from.getGameProfile().getName()).withStyle(party.leader.equals(from.getUUID())
                        ? ChatFormatting.GOLD : ChatFormatting.GREEN), Component.literal(text).withStyle(ChatFormatting.WHITE))
                .withColor(PARTY_COLOR);
        for (ServerPlayer p : online(from.server, party)) p.sendSystemMessage(line);
        return Result.OK;
    }

    /** Liga/desliga o chat de grupo (o chat normal do jogador vai só para o grupo). Devolve o estado novo. */
    public static boolean toggleChat(ServerPlayer player) {
        if (partyOf(player) == null) {
            PARTY_CHAT.remove(player.getUUID());
            return false;
        }
        boolean on = !PARTY_CHAT.contains(player.getUUID());
        if (on) PARTY_CHAT.add(player.getUUID());
        else PARTY_CHAT.remove(player.getUUID());
        SocialSync.markState(player.getUUID());
        return on;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /**
     * XP de abate com o grupo: o núcleo já dá o XP cheio a todo jogador a até 24 blocos; membros do grupo do autor do
     * golpe final que estão mais longe (até 64, mesma dimensão) também recebem.
     */
    static void onDeath(LivingDeathEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer killer) || killer instanceof FakePlayer) return;
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel level)) return;
        PartyRec party = partyOf(killer);
        if (party == null) return;
        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);
        int xp = Progression.killXp(victim.getMaxHealth(), spell);
        if (xp <= 0) return;
        double near = Progression.KILL_SHARE_RADIUS * Progression.KILL_SHARE_RADIUS;
        double far = PARTY_XP_RADIUS * PARTY_XP_RADIUS;
        for (ServerPlayer p : online(level.getServer(), party)) {
            if (p == killer || p.level() != level || p.isSpectator() || !p.isAlive()) continue;
            double d2 = p.distanceToSqr(victim);
            if (d2 > near && d2 <= far) MagicApi.giveXp(p, xp, "party");
        }
    }

    /** Atualiza o nome guardado (troca de nick) e manda o HUD na entrada. */
    static void onLogin(ServerPlayer player) {
        SocialData d = SocialData.get(player.server);
        PartyRec party = d.partyOf(player.getUUID());
        if (party == null) return;
        String name = player.getGameProfile().getName();
        if (!name.equals(party.names.get(player.getUUID()))) {
            party.names.put(player.getUUID(), name);
            d.setDirty();
        }
        for (ServerPlayer p : online(player.server, party)) {
            if (p != player) {
                p.displayClientMessage(Component.translatable("social.isekai_mahou.party.online", name).withColor(PARTY_COLOR), true);
            }
        }
        changed(player.server, party);
    }

    static void onLogout(ServerPlayer player) {
        PARTY_CHAT.remove(player.getUUID());
        PartyRec party = partyOf(player);
        if (party != null) changed(player.server, party);
    }

    static void clear() {
        PARTY_CHAT.clear();
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    static void broadcast(MinecraftServer server, PartyRec party, Component msg, @Nullable SoundEvent sound, float vol, float pitch) {
        for (ServerPlayer p : online(server, party)) {
            p.sendSystemMessage(msg);
            if (sound != null) p.playNotifySound(sound, SoundSource.PLAYERS, vol, pitch);
        }
    }

    private static void changed(MinecraftServer server, PartyRec party) {
        SocialSync.markParty(party.id);
        for (UUID m : party.members) SocialSync.markState(m);
    }

    /** Registro da dimensão (para o HUD de grupo marcar quem está em outro mundo). */
    static String dimId(ServerPlayer p) {
        return p.level().dimension().location().toString();
    }

    private PartyService() {}
}
