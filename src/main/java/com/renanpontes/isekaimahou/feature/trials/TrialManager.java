package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.TrialsApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Gerenciador das Câmaras de Provação (servidor): instâncias abertas, grupo (convites/entrar/sair), viagens
 * (ponto de retorno por jogador), limpeza aos poucos dos blocos, evacuação ao entrar no mundo e limpeza no boot.
 */
public final class TrialManager {
    /**
     * Mantém os chunks de cada instância carregados (o grupo pode estar só na recepção enquanto a arena é montada,
     * e o chefe precisa agir mesmo com todos abatidos). No boot, toda instância antiga é abortada, então os
     * tickets que sobraram são descartados aqui.
     */
    public static final TicketController TICKETS = new TicketController(IsekaiMahou.id("trials"), (level, helper) -> {
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        for (BlockPos owner : List.copyOf(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
    });

    private static final Map<UUID, TrialInstance> INSTANCES = new LinkedHashMap<>();
    private static final List<TrialClearJob> JOBS = new ArrayList<>();
    private static final List<Later> LATER = new ArrayList<>();
    @Nullable private static MinecraftServer server;
    private static long ticks;
    private static boolean stopping;

    private record Later(long at, Runnable run) {}

    static TrialSavedData data() {
        if (server == null) throw new IllegalStateException("Câmaras de Provação usadas sem servidor");
        return TrialSavedData.get(server);
    }

    /** Roda {@code run} daqui a {@code delay} ticks (descartado se o servidor parar). */
    static void later(int delay, Runnable run) {
        LATER.add(new Later(ticks + Math.max(1, delay), run));
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    /** Instância aberta da qual o jogador faz parte, ou null. */
    @Nullable
    public static TrialInstance of(UUID player) {
        for (TrialInstance i : INSTANCES.values()) if (i.open() && i.isMember(player)) return i;
        return null;
    }

    @Nullable
    static TrialInstance byId(UUID id) {
        TrialInstance i = INSTANCES.get(id);
        return i != null && i.open() ? i : null;
    }

    static List<TrialInstance> all() {
        return INSTANCES.values().stream().filter(TrialInstance::open).toList();
    }

    static int activeCount() {
        return all().size();
    }

    // =====================================================================================
    //  TrialsApi.Handler
    // =====================================================================================
    static boolean canStart(ServerPlayer p, Element e, int rank, List<Component> reasons) {
        boolean ok = TrialsApi.meetsRequirements(MagicApi.get(p), e, rank, reasons);
        if (server == null || TrialDimension.level(p.server) == null) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.no_realm"));
            return false;
        }
        if (of(p.getUUID()) != null) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.already_in"));
            ok = false;
        } else if (TrialDimension.is(p.level())) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.in_realm"));
            ok = false;
        }
        if (activeCount() >= MahouConfig.TRIAL_MAX_INSTANCES.get()) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.full", MahouConfig.TRIAL_MAX_INSTANCES.get()));
            ok = false;
        }
        if (p.level().getDifficulty() == Difficulty.PEACEFUL) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.peaceful"));
            ok = false;
        }
        if (!p.isAlive() || p.isSpectator()) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.reason.spectator"));
            ok = false;
        }
        return ok;
    }

    static void start(ServerPlayer p, Element e, int rank) {
        ServerLevel realm = TrialDimension.level(p.server);
        if (realm == null) return;
        TrialSavedData d = data();
        int index = d.freeIndex();
        TrialInstance inst = new TrialInstance(realm, index, e, rank, p);
        for (ChunkPos cp : TrialDimension.chunks(index)) TICKETS.forceChunk(realm, inst.id, cp.x, cp.z, true, true);
        INSTANCES.put(inst.id, inst);
        inst.spawnPanels();
        d.putActive(index, inst.info());
        saveReturn(p);
        inst.addMember(p);
        IsekaiMahou.LOGGER.info("Câmara de Provação {} aberta por {}: {} rank {}", index, p.getGameProfile().getName(), e.id, rank);
    }

    // =====================================================================================
    //  Blocos
    // =====================================================================================
    public static void onAltarUse(ServerPlayer p, BlockPos pos) {
        if (!TrialDimension.is(p.level())) {
            TrialNotifier.list(p);
            return;
        }
        TrialInstance inst = of(p.getUUID());
        if (inst == null) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.not_member").withStyle(ChatFormatting.RED), true);
            return;
        }
        inst.onAltar(p);
    }

    public static void onExitUse(ServerPlayer p) {
        if (!TrialDimension.is(p.level())) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.exit_outside").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        leave(p);
    }

    // =====================================================================================
    //  Grupo
    // =====================================================================================
    static boolean invite(ServerPlayer from, ServerPlayer target) {
        TrialInstance inst = of(from.getUUID());
        if (inst == null) {
            from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.not_in_trial").withStyle(ChatFormatting.RED));
            return false;
        }
        if (target == from) {
            from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invite_self").withStyle(ChatFormatting.RED));
            return false;
        }
        if (inst.isMember(target.getUUID())) {
            from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.already_member", target.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (inst.members.size() >= TrialInstance.MAX_PARTY) {
            from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.party_full", TrialInstance.MAX_PARTY).withStyle(ChatFormatting.RED));
            return false;
        }
        inst.invited.add(target.getUUID());
        MutableComponent title = TrialsApi.title(inst.element, inst.rank);
        Component join = Component.translatable("message.isekai_mahou.trials.invite_join").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mahou trial join " + from.getGameProfile().getName()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.isekai_mahou.trials.invite_join_hover", title))));
        target.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invited", from.getDisplayName(), title)
                .withStyle(ChatFormatting.GOLD).append(" ").append(join));
        if (!TrialsApi.meetsRequirements(MagicApi.get(target), inst.element, inst.rank, new ArrayList<>())) {
            target.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invite_assist").withStyle(ChatFormatting.GRAY));
        }
        TrialFx.ding(target, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 1.2F);
        from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invite_sent", target.getDisplayName()).withStyle(ChatFormatting.GREEN));
        if (inst.phase != TrialInstance.Phase.LOBBY && inst.phase != TrialInstance.Phase.COUNTDOWN) {
            from.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invite_later").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    static boolean join(ServerPlayer p, ServerPlayer target) {
        TrialInstance inst = of(target.getUUID());
        if (inst == null) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.target_not_in_trial", target.getDisplayName()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (of(p.getUUID()) != null) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.reason.already_in").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!inst.invited.contains(p.getUUID()) && !p.hasPermissions(2)) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.not_invited", target.getDisplayName()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (inst.phase != TrialInstance.Phase.LOBBY && inst.phase != TrialInstance.Phase.COUNTDOWN) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.join_running").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (inst.members.size() >= TrialInstance.MAX_PARTY) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.party_full", TrialInstance.MAX_PARTY).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!p.isAlive() || p.isSpectator()) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.reason.spectator").withStyle(ChatFormatting.RED));
            return false;
        }
        saveReturn(p);
        inst.addMember(p);
        return true;
    }

    /** Sai do grupo (voluntário) e volta para o ponto de retorno. */
    static void leave(ServerPlayer p) {
        TrialInstance inst = of(p.getUUID());
        if (inst == null) {
            if (TrialDimension.is(p.level())) sendHome(p, Element.ARCANE);
            else p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.not_in_trial").withStyle(ChatFormatting.RED));
            return;
        }
        Element el = inst.element;
        inst.removeMember(p.getUUID(), TrialInstance.Leave.LEFT);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.left_self", TrialsApi.title(el, inst.rank)).withStyle(ChatFormatting.GRAY));
        if (TrialDimension.is(p.level())) sendHome(p, el);
    }

    // =====================================================================================
    //  Viagens
    // =====================================================================================
    /** Guarda de onde o jogador veio (só se ele estiver fora do Reino das Provações). */
    static void saveReturn(ServerPlayer p) {
        if (TrialDimension.is(p.level())) return;
        data().putReturn(p.getUUID(), new TrialSavedData.ReturnPoint(p.level().dimension(), p.position(), p.getYRot(), -1));
    }

    static void sendToLobby(ServerPlayer p, TrialInstance inst) {
        TrialFx.warp(p.serverLevel(), p.position(), inst.element);
        Vec3 to = inst.layout.spawn();
        p.teleportTo(inst.level, to.x, to.y, to.z, 180F, 0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        TrialFx.warp(inst.level, to, inst.element);
        inst.level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIAL_SPAWNER_AMBIENT, SoundSource.BLOCKS, 1.5F, 0.8F);
    }

    /**
     * Devolve o jogador ao ponto de retorno (ou ao spawn do mundo, se não houver) e restaura o modo de jogo se ele
     * ficou espectador por estar "Abatido".
     */
    static void sendHome(ServerPlayer p, Element fxElement) {
        TrialSavedData.ReturnPoint rp = data().takeReturn(p.getUUID());
        if (rp != null && rp.prevMode() >= 0 && p.isSpectator()) p.setGameMode(GameType.byId(rp.prevMode()));
        ServerLevel dest = rp == null ? null : p.server.getLevel(rp.dim());
        Vec3 pos;
        float yaw;
        if (dest == null || TrialDimension.is(dest)) {
            dest = p.server.overworld();
            BlockPos spawn = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getSharedSpawnPos());
            pos = Vec3.atBottomCenterOf(spawn);
            yaw = dest.getSharedSpawnAngle();
        } else {
            pos = rp.pos();
            yaw = rp.yaw();
        }
        TrialFx.warp(p.serverLevel(), p.position(), fxElement);
        p.teleportTo(dest, pos.x, pos.y, pos.z, yaw, p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        TrialFx.warp(dest, pos, fxElement);
        TrialNetwork.clear(p);
        later(60, () -> TrialEvents.notify(p, null));
    }

    /** Fora de um grupo, mas dentro do Reino das Provações (entrou no mundo, teleporte de outro mod): volta para casa. */
    static void evacuate(ServerPlayer p) {
        if (p.hasDisconnected() || !TrialDimension.is(p.level()) || of(p.getUUID()) != null) return;
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.evacuated").withStyle(ChatFormatting.YELLOW));
        sendHome(p, Element.ARCANE);
    }

    // =====================================================================================
    //  Ciclo de vida
    // =====================================================================================
    static void tick() {
        if (server == null) return;
        ticks++;
        for (TrialInstance inst : List.copyOf(INSTANCES.values())) {
            if (!inst.open()) continue;
            try {
                inst.tick();
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Câmara de Provação {} falhou; fechando", inst.index, ex);
                inst.close("message.isekai_mahou.trials.closed_error");
            }
        }
        INSTANCES.values().removeIf(i -> !i.open());
        runJobs();
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (l.at <= ticks) { due.add(l); it.remove(); }
            }
            for (Later l : due) l.run.run();
        }
    }

    private static void runJobs() {
        if (JOBS.isEmpty()) return;
        ServerLevel realm = TrialDimension.level(server);
        if (realm == null) {
            JOBS.clear();
            return;
        }
        TrialClearJob job = JOBS.get(0);
        if (job.step(realm, 4)) {
            JOBS.remove(0);
            if (job.full) data().cleaned(job.index);
        }
    }

    /** A instância fechou: os blocos dela entram na fila de limpeza (os tickets são soltos no fim da limpeza). */
    static void retire(TrialInstance inst) {
        if (server == null) return;
        data().retire(inst.index);
        if (!stopping) JOBS.add(TrialClearJob.full(inst.index, inst.id, true));
        IsekaiMahou.LOGGER.info("Câmara de Provação {} fechada", inst.index);
    }

    static void onServerStarted(MinecraftServer s) {
        server = s;
        stopping = false;
        ticks = 0;
        TrialSavedData d = data();
        // Instâncias abertas quando o servidor parou/caiu: abortadas (jogadores são evacuados ao entrar).
        for (int idx : List.copyOf(d.active.keySet())) d.retire(idx);
        if (TrialDimension.level(s) == null) {
            IsekaiMahou.LOGGER.warn("Dimensão isekai_mahou:trial_realm não carregada — Câmaras de Provação indisponíveis neste mundo");
            return;
        }
        for (int idx : List.copyOf(d.dirty)) JOBS.add(TrialClearJob.full(idx, UUID.randomUUID(), false));
    }

    /**
     * Servidor parando (os jogadores ainda estão conectados): ninguém fica preso como espectador. Ninguém é
     * teleportado agora — a evacuação acontece quando cada um entrar de novo; os blocos são limpos no próximo boot.
     */
    static void onServerStopping() {
        stopping = true;
        for (TrialInstance inst : List.copyOf(INSTANCES.values())) {
            if (inst.open()) inst.shutdown();
        }
    }

    static void onServerStopped() {
        INSTANCES.clear();
        JOBS.clear();
        LATER.clear();
        server = null;
        stopping = false;
    }

    private TrialManager() {}
}
