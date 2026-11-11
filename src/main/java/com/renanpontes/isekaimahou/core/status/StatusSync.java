package com.renanpontes.isekaimahou.core.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sincroniza a lista de condições de cada entidade com os jogadores que a rastreiam (e com ela mesma, se for jogador).
 * Mudanças são juntadas e enviadas no fim do tick do servidor. Renovar só a duração (chuva a cada segundo) não manda
 * pacote, a não ser que a contagem do cliente esteja para acabar (≤3 s) — o cliente nunca tira um ícone sozinho.
 */
public final class StatusSync {
    private static final int DRIFT_TOLERANCE = 30;
    private static final Set<LivingEntity> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Última lista enviada por entidade (para estimar o que o cliente mostra). */
    private static final WeakHashMap<LivingEntity, Sent> SENT = new WeakHashMap<>();

    private record Sent(long tick, List<StatusPayloads.Entry> entries) {}

    public static void markDirty(LivingEntity e) {
        if (e != null && !e.level().isClientSide) DIRTY.add(e);
    }

    /** Só a duração mudou: manda de novo apenas se o cliente estiver contando muito diferente. */
    static void markRefreshed(LivingEntity e, Kind kind, int newTicks) {
        if (e.level().isClientSide || DIRTY.contains(e)) return;
        Sent s = SENT.get(e);
        if (s == null) {
            DIRTY.add(e);
            return;
        }
        long now = e.level().getGameTime();
        for (StatusPayloads.Entry en : s.entries()) {
            if (en.kind() != kind.ordinal()) continue;
            if (en.ticks() < 0 || newTicks < 0) {
                if ((en.ticks() < 0) != (newTicks < 0)) DIRTY.add(e);
                return;
            }
            long estimate = en.ticks() - (now - s.tick());
            // Renovações frequentes (chuva) só reenviam quando o relógio do cliente está para acabar
            boolean runningOut = estimate <= 2L * DRIFT_TOLERANCE && newTicks > estimate + DRIFT_TOLERANCE;
            if (runningOut || newTicks < estimate - DRIFT_TOLERANCE) DIRTY.add(e);
            return;
        }
        DIRTY.add(e);
    }

    static void flush(ServerTickEvent.Post event) {
        if (DIRTY.isEmpty()) return;
        List<LivingEntity> batch = new ArrayList<>(DIRTY);
        DIRTY.clear();
        for (LivingEntity e : batch) {
            if (e.isRemoved() || e.level().isClientSide) continue;
            List<StatusPayloads.Entry> entries = build(e);
            Sent prev = SENT.get(e);
            if (entries.isEmpty() && prev == null) continue; // nunca teve nada visível no cliente
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new StatusPayloads.StatusList(e.getId(), entries));
            if (entries.isEmpty()) SENT.remove(e);
            else SENT.put(e, new Sent(e.level().getGameTime(), entries));
        }
    }

    /** Lista atual das condições da entidade (só efeitos de verdade; o fogo vanilla o cliente já vê). */
    public static List<StatusPayloads.Entry> build(LivingEntity e) {
        List<StatusPayloads.Entry> list = new ArrayList<>(2);
        for (Kind k : Kind.values()) {
            MobEffectInstance inst = e.getEffect(Statuses.effect(k));
            if (inst == null) continue;
            int stacks = Math.min(k.maxStacks(), inst.getAmplifier() + 1);
            list.add(new StatusPayloads.Entry(k.ordinal(), stacks, inst.isInfiniteDuration() ? -1 : inst.getDuration()));
        }
        return list;
    }

    static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(target instanceof LivingEntity le)) return;
        List<StatusPayloads.Entry> entries = build(le);
        if (!entries.isEmpty()) PacketDistributor.sendToPlayer(sp, new StatusPayloads.StatusList(le.getId(), entries));
    }

    /** O jogador trocou de mundo/renasceu/entrou: o cliente limpou tudo, manda a lista dele de novo. */
    static void resendSelf(PlayerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        List<StatusPayloads.Entry> entries = build(sp);
        PacketDistributor.sendToPlayer(sp, new StatusPayloads.StatusList(sp.getId(), entries));
        if (entries.isEmpty()) SENT.remove(sp);
        else SENT.put(sp, new Sent(sp.level().getGameTime(), entries));
    }

    static void clear() {
        DIRTY.clear();
        SENT.clear();
    }

    private StatusSync() {}
}
