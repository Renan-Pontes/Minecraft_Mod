package com.renanpontes.isekaimahou.feature.legends_f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estados visuais do lado do servidor ({@link LfNet.State}): guarda até quando cada entidade está em cada estado, avisa
 * quem acompanha a entidade e reenvia para quem começar a acompanhá-la depois (entrou no alcance, logou). Só visual:
 * as regras de jogo de cada técnica ficam nos efeitos/gerenciadores dela. Em memória (some ao fechar o servidor).
 */
public final class LfStates {
    private static final class Entry {
        long end;
        int value;
    }

    private static final Map<Long, Entry> STATES = new HashMap<>();

    private static long key(int entityId, byte kind) {
        return ((long) entityId << 8) | (kind & 0xFF);
    }

    /** Liga (ou renova) o estado por {@code ticks} ticks. */
    public static void set(Entity e, byte kind, int ticks, int value) {
        if (!(e.level() instanceof ServerLevel level) || ticks <= 0) return;
        Entry en = STATES.computeIfAbsent(key(e.getId(), kind), k -> new Entry());
        en.end = level.getGameTime() + ticks;
        en.value = value;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new LfNet.State(kind, e.getId(), ticks, value));
    }

    /** Atualiza só o valor (ex.: progresso do contrato), mantendo o fim. */
    public static void value(Entity e, byte kind, int value) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Entry en = STATES.get(key(e.getId(), kind));
        if (en == null || en.value == value) return;
        en.value = value;
        int left = (int) Math.max(1, en.end - level.getGameTime());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new LfNet.State(kind, e.getId(), left, value));
    }

    public static void clear(Entity e, byte kind) {
        if (STATES.remove(key(e.getId(), kind)) != null && e.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new LfNet.State(kind, e.getId(), 0, 0));
        }
    }

    public static boolean has(Entity e, byte kind) {
        if (!(e.level() instanceof ServerLevel level)) return false;
        Entry en = STATES.get(key(e.getId(), kind));
        return en != null && en.end > level.getGameTime();
    }

    /** Quem começou a acompanhar uma entidade recebe os estados ativos dela. */
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        long now = level.getGameTime();
        for (byte k = 0; k < LfNet.STATE_KINDS; k++) {
            Entry en = STATES.get(key(target.getId(), k));
            if (en != null && en.end > now) {
                PacketDistributor.sendToPlayer(sp, new LfNet.State(k, target.getId(), (int) (en.end - now), en.value));
            }
        }
    }

    /** O próprio jogador (relogou/trocou de dimensão) recebe os estados dele. */
    static void resendSelf(ServerPlayer sp) {
        long now = sp.serverLevel().getGameTime();
        for (byte k = 0; k < LfNet.STATE_KINDS; k++) {
            Entry en = STATES.get(key(sp.getId(), k));
            if (en != null && en.end > now) {
                PacketDistributor.sendToPlayer(sp, new LfNet.State(k, sp.getId(), (int) (en.end - now), en.value));
            }
        }
    }

    /** Limpeza dos vencidos (o tempo de jogo é do overworld; estados curtos não importam a diferença). */
    static void prune(long now) {
        Iterator<Map.Entry<Long, Entry>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().end + 200 < now) it.remove();
        }
        if (STATES.size() > 4096) STATES.clear();
    }

    static void clearAll() {
        STATES.clear();
    }

    private LfStates() {}
}
