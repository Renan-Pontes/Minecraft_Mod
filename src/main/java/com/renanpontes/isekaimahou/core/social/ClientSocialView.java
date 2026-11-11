package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.PartySync;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Req;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.State;

/**
 * Cópia no CLIENTE do estado social do jogador local (grupo, cidade, guerras, duelo, pedidos, arena). Só dados — sem
 * classes de cliente, para a API comum ({@code Party}, {@code Allegiance}) poder ler nos dois lados.
 */
public final class ClientSocialView {
    private static volatile PartySync party = PartySync.empty();
    private static volatile Set<UUID> memberIds = Set.of();
    private static volatile State state = State.empty();
    private static volatile Set<UUID> enemies = Set.of();
    /** Sobe a cada pacote de grupo/estado (telas que guardam cache sabem quando refazer). */
    private static volatile int version;

    static void setParty(PartySync p) {
        party = p;
        Set<UUID> ids = new HashSet<>();
        for (Member m : p.members()) ids.add(m.id());
        memberIds = Collections.unmodifiableSet(ids);
        version++;
    }

    static void setState(State s) {
        state = s;
        enemies = Collections.unmodifiableSet(new HashSet<>(s.enemies()));
        version++;
    }

    public static void reset() {
        party = PartySync.empty();
        memberIds = Set.of();
        state = State.empty();
        enemies = Set.of();
        version++;
    }

    public static int version() { return version; }

    // ---------------------------------------------------------------- grupo
    public static boolean inParty() { return !party.members().isEmpty(); }

    public static boolean isMember(UUID id) { return memberIds.contains(id); }

    public static List<Member> partyMembers() { return party.members(); }

    public static List<UUID> members() {
        List<UUID> out = new ArrayList<>();
        for (Member m : party.members()) out.add(m.id());
        return out;
    }

    @Nullable
    public static UUID leader() { return inParty() ? party.leader() : null; }

    // ---------------------------------------------------------------- estado
    public static State state() { return state; }

    /** Jogador online com quem o local está em guerra (PvP liberado)? */
    public static boolean isEnemy(UUID id) { return enemies.contains(id); }

    @Nullable
    public static UUID duelOpponent() { return state.duelWith().orElse(null); }

    /** Pedido pendente deste jogador para o local (tipo = ordinal de {@link Requests.Kind}), ou null. */
    @Nullable
    public static Req requestFrom(UUID from, int kind) {
        for (Req r : state.requests()) if (r.from().equals(from) && r.kind() == kind) return r;
        return null;
    }

    private ClientSocialView() {}
}
