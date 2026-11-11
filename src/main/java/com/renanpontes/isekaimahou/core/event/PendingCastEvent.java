package com.renanpontes.isekaimahou.core.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.SpellStack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Uma conjuração entrou na Pilha ({@link SpellStack}). Postado em NeoForge.EVENT_BUS (servidor):
 * <ul>
 *   <li>{@link SpellStack#push} e conjurações de jogador: na hora;</li>
 *   <li>golpes telegrafados ({@code Telegraph.delayed*} com dono): no fim do tick em que o grupo se formou, já com
 *       todas as formas somadas (raio, tempo e tier finais).</li>
 * </ul>
 * Use para reagir "Em Resposta" (ex.: gatilho que conjura uma contramágica). Pode chamar {@link SpellStack#counter}
 * aqui mesmo, mas prefira agendar a sua conjuração para o próximo tick.
 */
public class PendingCastEvent extends Event {
    private final ServerLevel level;
    private final SpellStack.Pending pending;

    public PendingCastEvent(ServerLevel level, SpellStack.Pending pending) {
        this.level = level;
        this.pending = pending;
    }

    public ServerLevel getLevel() { return level; }
    public SpellStack.Pending getPending() { return pending; }

    /** Quem conjura (null se já sumiu). */
    @Nullable
    public LivingEntity getCaster() { return pending.caster(); }

    /** A pendente é hostil a {@code e}? (atalho de {@link SpellStack.Pending#hostileTo}) */
    public boolean isHostileTo(@Nullable LivingEntity e) { return pending.hostileTo(e); }
}
