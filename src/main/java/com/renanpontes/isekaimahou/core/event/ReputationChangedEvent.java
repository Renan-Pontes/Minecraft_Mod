package com.renanpontes.isekaimahou.core.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Disparado (NeoForge.EVENT_BUS, servidor) DEPOIS que a reputação de um jogador com uma facção mudou de verdade
 * (inclui respingos em rivais/aliados e o comando set). Não é cancelável.
 */
public class ReputationChangedEvent extends Event {
    /** Por que mudou. */
    public enum Cause {
        /** {@code Reputation.add} (missões, abates, serviços...). */
        DIRECT,
        /** Respingo de uma mudança em facção rival/aliada. */
        SPILLOVER,
        /** {@code Reputation.set} ou comando. */
        SET
    }

    private final ServerPlayer player;
    private final String faction;
    private final int oldValue;
    private final int newValue;
    private final Cause cause;
    @Nullable private final Component reason;

    public ReputationChangedEvent(ServerPlayer player, String faction, int oldValue, int newValue, Cause cause, @Nullable Component reason) {
        this.player = player;
        this.faction = faction;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.cause = cause;
        this.reason = reason;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getFaction() { return faction; }
    public int getOldValue() { return oldValue; }
    public int getNewValue() { return newValue; }
    public int getDelta() { return newValue - oldValue; }
    public Cause getCause() { return cause; }
    @Nullable public Component getReason() { return reason; }
    public Reputation.Standing getOldStanding() { return Reputation.Standing.of(oldValue); }
    public Reputation.Standing getNewStanding() { return Reputation.Standing.of(newValue); }
    /** Mudou de patamar (para cima ou para baixo)? */
    public boolean standingChanged() { return getOldStanding() != getNewStanding(); }
}
