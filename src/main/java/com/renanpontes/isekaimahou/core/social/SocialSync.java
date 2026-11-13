package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.social.SocialData.PartyRec;
import com.renanpontes.isekaimahou.core.social.SocialData.WarRec;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.ArenaInfo;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.MemberPos;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.PartyPos;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.PartySync;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Req;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.State;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Envia ao cliente o HUD de grupo (a cada 0,5 s, só quando algo mudou) e o estado social (a cada 1 s ou na hora quando
 * marcado). Guarda o último pacote de cada jogador para não reenviar igual.
 */
final class SocialSync {
    private static final Set<UUID> PARTY_DIRTY = new HashSet<>();
    private static final Set<UUID> STATE_DIRTY = new HashSet<>();
    private static boolean allStatesDirty;
    private static final Map<UUID, PartySync> LAST_PARTY = new HashMap<>();
    private static final Map<UUID, State> LAST_STATE = new HashMap<>();
    /** Última lista de posições mandada a cada jogador (marcador dos amigos). */
    private static final Map<UUID, PartyPos> LAST_POS = new HashMap<>();

    static void markParty(UUID partyId) {
        PARTY_DIRTY.add(partyId);
    }

    static void markState(UUID player) {
        STATE_DIRTY.add(player);
    }

    static void markAllStates() {
        allStatesDirty = true;
    }

    /** O jogador saiu do grupo: HUD vazio na hora. */
    static void clearParty(MinecraftServer server, UUID member) {
        LAST_PARTY.remove(member);
        ServerPlayer sp = server.getPlayerList().getPlayer(member);
        if (sp != null) PacketDistributor.sendToPlayer(sp, PartySync.empty());
        if (LAST_POS.remove(member) != null && sp != null) PacketDistributor.sendToPlayer(sp, PartyPos.empty());
        STATE_DIRTY.add(member);
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        boolean periodicParty = now % 10 == 0;
        if (periodicParty || !PARTY_DIRTY.isEmpty()) {
            SocialData d = SocialData.get(server);
            for (PartyRec party : d.parties()) {
                if (!periodicParty && !PARTY_DIRTY.contains(party.id)) continue;
                List<ServerPlayer> online = PartyService.online(server, party);
                if (online.isEmpty()) continue;
                PartySync packet = snapshot(server, party);
                for (ServerPlayer p : online) {
                    if (!packet.equals(LAST_PARTY.get(p.getUUID()))) {
                        LAST_PARTY.put(p.getUUID(), packet);
                        PacketDistributor.sendToPlayer(p, packet);
                    }
                }
                if (periodicParty) syncPositions(online);
            }
            PARTY_DIRTY.clear();
        }
        boolean periodicState = now % 20 == 0;
        if (periodicState || allStatesDirty || !STATE_DIRTY.isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!periodicState && !allStatesDirty && !STATE_DIRTY.contains(p.getUUID())) continue;
                State s = state(server, p);
                if (!s.equals(LAST_STATE.get(p.getUUID()))) {
                    LAST_STATE.put(p.getUUID(), s);
                    PacketDistributor.sendToPlayer(p, s);
                }
            }
            STATE_DIRTY.clear();
            allStatesDirty = false;
        }
    }

    /**
     * Marcador dos amigos: a cada 0,5 s cada membro recebe o bloco onde estão os OUTROS membros online da mesma
     * dimensão (só quando mudou). Grupo de até 8: custo desprezível.
     */
    private static void syncPositions(List<ServerPlayer> online) {
        for (ServerPlayer p : online) {
            List<MemberPos> list = new ArrayList<>();
            for (ServerPlayer q : online) {
                if (q == p || q.level() != p.level() || q.isSpectator()) continue;
                list.add(new MemberPos(q.getUUID(), q.getBlockX(), q.getBlockY(), q.getBlockZ()));
            }
            PartyPos packet = new PartyPos(list);
            PartyPos last = LAST_POS.get(p.getUUID());
            if (last == null ? list.isEmpty() : packet.equals(last)) continue;
            LAST_POS.put(p.getUUID(), packet);
            PacketDistributor.sendToPlayer(p, packet);
        }
    }

    private static PartySync snapshot(MinecraftServer server, PartyRec party) {
        List<Member> list = new ArrayList<>(party.members.size());
        for (UUID id : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) {
                list.add(new Member(id, party.name(id), false, 0, 0, 0, 0, 0xFF7A5CFF, "", 0, ""));
                continue;
            }
            MagicData md = MagicApi.get(sp);
            list.add(new Member(id, sp.getGameProfile().getName(), true, round(sp.getHealth()), round(sp.getMaxHealth()),
                    round(md.mana()), md.maxMana(), MagicApi.resourceColor(sp), md.path().id, md.level(), PartyService.dimId(sp)));
        }
        return new PartySync(party.leader, list);
    }

    private static float round(float v) {
        return Math.round(v * 10F) / 10F;
    }

    private static State state(MinecraftServer server, ServerPlayer p) {
        UUID id = p.getUUID();
        String city = AllegianceService.of(server, id);
        Optional<Component> cityName = Optional.ofNullable(city == null ? null : AllegianceService.cityName(server, city));
        List<Component> wars = new ArrayList<>();
        Set<String> sides = AllegianceService.sides(server, id);
        if (!sides.isEmpty()) {
            for (WarRec w : AllegianceService.wars(server)) {
                if (sides.contains(w.a()) || sides.contains(w.b())) wars.add(AllegianceService.warLine(server, w));
            }
        }
        List<UUID> enemies = new ArrayList<>();
        if (!wars.isEmpty()) {
            for (ServerPlayer q : server.getPlayerList().getPlayers()) {
                if (q != p && AllegianceService.atWar(server, id, q.getUUID())) enemies.add(q.getUUID());
            }
        }
        DuelService.Duel duel = DuelService.current(id);
        int now = server.getTickCount();
        Optional<UUID> duelWith = duel == null ? Optional.empty() : Optional.of(duel.other(id));
        String duelName = duel == null ? "" : duel.nameOf(duel.other(id));
        int phase = duel == null ? DuelService.PHASE_NONE : duel.phase(now);
        int duelTicks = duel == null ? 0 : duel.ticksLeft(now) / 20 * 20;
        List<Req> reqs = new ArrayList<>();
        for (Requests.Request r : Requests.pendingFor(server, id)) {
            reqs.add(new Req(r.from(), r.fromName(), r.kind().ordinal(), r.ticksLeft(now) / 20 * 20));
        }
        BossArenaService.Arena a = BossArenaService.at(p.level(), p.position());
        Optional<ArenaInfo> arena = a == null ? Optional.empty() : Optional.of(new ArenaInfo(a.center.x, a.center.y, a.center.z,
                (float) a.radius, a.releasing(), a.ticksLeft(now) / 20 * 20, a.bossName));
        return new State(city == null ? "" : city, cityName, wars, enemies, duelWith, duelName, phase, duelTicks, reqs, arena,
                PartyService.partyChat(id));
    }

    static void forget(UUID player) {
        LAST_PARTY.remove(player);
        LAST_STATE.remove(player);
        LAST_POS.remove(player);
        STATE_DIRTY.remove(player);
    }

    static void clear() {
        PARTY_DIRTY.clear();
        STATE_DIRTY.clear();
        LAST_PARTY.clear();
        LAST_STATE.clear();
        LAST_POS.clear();
        allStatesDirty = false;
    }

    private SocialSync() {}
}
