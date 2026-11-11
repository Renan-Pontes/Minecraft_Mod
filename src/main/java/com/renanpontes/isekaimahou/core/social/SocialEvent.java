package com.renanpontes.isekaimahou.core.social;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/**
 * Eventos sociais (postados no {@code NeoForge.EVENT_BUS}, sempre no servidor, depois que a mudança aconteceu).
 * Ver docs/v10/API_social.md.
 */
public abstract class SocialEvent extends Event {

    /** O que mudou num grupo. */
    public enum PartyChange { CREATED, JOINED, LEFT, KICKED, PROMOTED, DISBANDED }

    /** Grupo mudou. {@code player} = quem entrou/saiu/foi promovido (UUID: pode estar offline). */
    public static final class Party extends SocialEvent {
        private final UUID partyId;
        private final UUID player;
        private final PartyChange change;

        public Party(UUID partyId, UUID player, PartyChange change) {
            this.partyId = partyId;
            this.player = player;
            this.change = change;
        }

        public UUID getPartyId() { return partyId; }
        public UUID getPlayer() { return player; }
        public PartyChange getChange() { return change; }
    }

    /** O jogador mudou de cidade (lealdade). Ids de lugar ({@code Places}); null = nenhuma. */
    public static final class AllegianceChanged extends SocialEvent {
        private final ServerPlayer player;
        @Nullable private final String oldCity, newCity;

        public AllegianceChanged(ServerPlayer player, @Nullable String oldCity, @Nullable String newCity) {
            this.player = player;
            this.oldCity = oldCity;
            this.newCity = newCity;
        }

        public ServerPlayer getPlayer() { return player; }
        @Nullable public String getOldCity() { return oldCity; }
        @Nullable public String getNewCity() { return newCity; }
    }

    /** Guerra declarada ({@code city} declarou {@code target} inimiga). {@code by} pode ser null (comando/sistema). */
    public static final class WarDeclared extends SocialEvent {
        private final String city, target;
        @Nullable private final ServerPlayer by;

        public WarDeclared(String city, String target, @Nullable ServerPlayer by) {
            this.city = city;
            this.target = target;
            this.by = by;
        }

        public String getCity() { return city; }
        public String getTarget() { return target; }
        @Nullable public ServerPlayer getBy() { return by; }
    }

    /** Paz feita entre duas cidades. */
    public static final class PeaceMade extends SocialEvent {
        private final String cityA, cityB;

        public PeaceMade(String cityA, String cityB) {
            this.cityA = cityA;
            this.cityB = cityB;
        }

        public String getCityA() { return cityA; }
        public String getCityB() { return cityB; }
    }

    /** Duelo terminou. Empate: {@code draw} true (vencedor/perdedor em qualquer ordem). */
    public static final class DuelEnded extends SocialEvent {
        private final ServerPlayer winner, loser;
        private final boolean draw;

        public DuelEnded(ServerPlayer winner, ServerPlayer loser, boolean draw) {
            this.winner = winner;
            this.loser = loser;
            this.draw = draw;
        }

        public ServerPlayer getWinner() { return winner; }
        public ServerPlayer getLoser() { return loser; }
        public boolean isDraw() { return draw; }
    }

    /** Troca segura concluída entre dois jogadores. */
    public static final class TradeCompleted extends SocialEvent {
        private final ServerPlayer a, b;

        public TradeCompleted(ServerPlayer a, ServerPlayer b) {
            this.a = a;
            this.b = b;
        }

        public ServerPlayer getA() { return a; }
        public ServerPlayer getB() { return b; }
    }

    /** Arena de chefe selada (luta começou). */
    public static final class ArenaSealed extends SocialEvent {
        private final LivingEntity boss;
        private final Vec3 center;
        private final double radius;

        public ArenaSealed(LivingEntity boss, Vec3 center, double radius) {
            this.boss = boss;
            this.center = center;
            this.radius = radius;
        }

        public LivingEntity getBoss() { return boss; }
        public Vec3 getCenter() { return center; }
        public double getRadius() { return radius; }
    }

    /** Arena de chefe liberada (30 s depois do fim da luta). O chefe pode já não existir. */
    public static final class ArenaReleased extends SocialEvent {
        private final UUID boss;
        private final Vec3 center;

        public ArenaReleased(UUID boss, Vec3 center) {
            this.boss = boss;
            this.center = center;
        }

        public UUID getBoss() { return boss; }
        public Vec3 getCenter() { return center; }
    }
}
