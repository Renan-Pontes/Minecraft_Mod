package com.renanpontes.isekaimahou.core.onboard;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.OnboardingEvent;
import com.renanpontes.isekaimahou.core.ux.EntryFlow;
import com.renanpontes.isekaimahou.network.CorePayloads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sequência de entrada no jogo (servidor): escolha da classe → etapas de criação (v1.0: raça) → letreiro "Parabéns!" →
 * {@code ClassCongratsDone} → título + tutorial (Diário/Espírito Guia) → {@code TutorialDone}. Sem atributos. Estado nas flags de {@link MagicData} (sobrevive a reinícios).
 */
public final class OnboardService {
    /** Duração do letreiro de parabéns (o evento sai quando ele termina). */
    public static final int CONGRATS_TICKS = 80;
    /** Atraso depois de entrar no mundo para retomar a sequência (o cliente termina de carregar). */
    private static final int RESUME_DELAY = 60;

    /** Interna: o silêncio acabou por tempo (5 min) sem o tutorial terminar. */
    static final String FLAG_QUIET_OVER = "onboard.quiet_over";
    /** Interna: o rastreador já começou fechado uma vez para este personagem. */
    static final String FLAG_TRACKER = "onboard.tracker";
    /** Segundo do jogo (tempo do Overworld / 20, +1) em que a classe foi escolhida. */
    static final String COUNTER_CLASS_AT = "onboard.class_at";

    /** Flag de etapa de criação concluída: {@code onboard.step.<id>}. */
    static final String STEP_PREFIX = "onboard.step.";
    /** Pausa entre uma etapa e a próxima (a tela anterior termina de fechar). */
    private static final int STEP_DELAY = 10;

    private static final String TASK_CONGRATS = "congrats";
    private static final String TASK_RESUME = "resume";
    private static final String TASK_STEP = "step";

    /** Etapas de criação registradas pelos módulos (ordem de registro; dado de registro, nunca limpo). */
    private static final List<String> STEPS = new CopyOnWriteArrayList<>();

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    /** Personagem de versão antiga: classe escolhida antes da v0.9 (sem a flag da tela nova). */
    private static boolean legacy(MagicData d) {
        return d.path() != MagicPath.NONE && !d.hasFlag(Onboarding.FLAG_CLASS);
    }

    public static boolean quiet(Player p) {
        if (!(p instanceof ServerPlayer sp) || sp instanceof FakePlayer) return false;
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE) return true;
        if (legacy(d) || d.hasFlag(Onboarding.FLAG_TUTORIAL) || d.hasFlag(FLAG_QUIET_OVER)) return false;
        if (creating(d)) return true; // montando o personagem: o relógio dos 5 min ainda não começou
        return elapsedSinceChoice(sp, d) < Onboarding.QUIET_TICKS / 20;
    }

    // =====================================================================================
    //  Etapas de criação de personagem (§3.2b)
    // =====================================================================================
    public static void addCreationStep(String id) {
        if (id == null || id.isBlank() || STEPS.contains(id)) return;
        STEPS.add(id);
    }

    /** Etapas na ordem em que abrem (v1.0: a raça sempre logo depois da classe — {@link EntryFlow#STEP_ORDER}). */
    public static List<String> creationSteps() {
        return Collections.unmodifiableList(EntryFlow.ordered(STEPS));
    }

    private static List<String> pending(MagicData d) {
        if (STEPS.isEmpty() || d.hasFlag(Onboarding.FLAG_CONGRATS) || legacy(d)) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : EntryFlow.ordered(STEPS)) if (!d.hasFlag(STEP_PREFIX + s)) out.add(s);
        return out;
    }

    /** Classe escolhida, letreiro ainda não mostrado e etapas de criação por fazer. */
    private static boolean creating(MagicData d) {
        return d.path() != MagicPath.NONE && !pending(d).isEmpty();
    }

    public static List<String> pendingCreationSteps(Player p) {
        return p == null ? List.of() : pending(MagicApi.get(p));
    }

    public static void completeCreationStep(ServerPlayer sp, String id) {
        if (sp == null || sp instanceof FakePlayer || id == null || !STEPS.contains(id)) return;
        MagicData d = MagicApi.get(sp);
        String flag = STEP_PREFIX + id;
        if (d.hasFlag(flag)) return;
        boolean wasPending = pending(d).contains(id);
        d.setFlag(flag, true);
        if (!wasPending || d.path() == MagicPath.NONE) return; // fora da sequência (sem classe ou depois do letreiro)
        OnboardTasks.later(sp, STEP_DELAY, TASK_STEP, OnboardService::continueCreation);
    }

    /** Abre a próxima etapa pendente ou, sem nenhuma, começa o "Parabéns!". */
    private static void continueCreation(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE || d.hasFlag(Onboarding.FLAG_CONGRATS)) return;
        List<String> left = pending(d);
        if (!left.isEmpty()) {
            NeoForge.EVENT_BUS.post(new OnboardingEvent.CreationStep(sp, left.get(0)));
            return;
        }
        startCongrats(sp, d.path());
    }

    public static boolean classChosen(Player p) {
        return MagicApi.get(p).hasFlag(Onboarding.FLAG_CLASS);
    }

    public static boolean congratsDone(Player p) {
        MagicData d = MagicApi.get(p);
        return d.hasFlag(Onboarding.FLAG_CONGRATS) || legacy(d);
    }

    public static boolean tutorialDone(Player p) {
        MagicData d = MagicApi.get(p);
        return d.hasFlag(Onboarding.FLAG_TUTORIAL) || legacy(d);
    }

    private static int nowSeconds(MinecraftServer server) {
        return (int) Math.min(Integer.MAX_VALUE - 1, server.overworld().getGameTime() / 20L) + 1;
    }

    /** Segundos desde a escolha (sem registro de hora = muito tempo). */
    private static int elapsedSinceChoice(ServerPlayer sp, MagicData d) {
        int at = d.counter(COUNTER_CLASS_AT);
        if (at <= 0 || sp.getServer() == null) return Integer.MAX_VALUE;
        return Math.max(0, nowSeconds(sp.getServer()) - at);
    }

    // =====================================================================================
    //  Escolha da classe (chamado por CoreGameplay.choosePath ANTES de postar PathChosen)
    // =====================================================================================
    /**
     * Começa a sequência: grava as flags (os módulos que ouvem {@code PathChosen} já veem {@code quiet = true}) e abre
     * as etapas de criação pendentes ({@code CreationStep}) ou, sem nenhuma, mostra o letreiro "Parabéns!", toca a
     * fanfarra e agenda {@code ClassCongratsDone}.
     */
    public static void onClassChosen(ServerPlayer sp, MagicPath path) {
        if (sp instanceof FakePlayer || path == null || path == MagicPath.NONE) return;
        MagicData d = MagicApi.get(sp);
        d.setFlag(Onboarding.FLAG_CLASS, true);
        d.setFlag(Onboarding.FLAG_CONGRATS, false);
        d.setFlag(Onboarding.FLAG_TUTORIAL, false);
        d.setFlag(FLAG_QUIET_OVER, false);
        setClassTime(sp, d);
        List<String> left = pending(d);
        if (!left.isEmpty()) {
            // Montar o personagem primeiro (o módulo da etapa abre a tela dele); o "Parabéns!" vem no fim. Um instante
            // depois: o PathChosen termina e a tela de classe fecha antes da tela da etapa abrir
            sendState(sp);
            OnboardTasks.later(sp, STEP_DELAY, TASK_STEP, OnboardService::continueCreation);
            return;
        }
        startCongrats(sp, path);
    }

    /** Letreiro + agenda do {@code ClassCongratsDone}; o relógio dos 5 min de silêncio começa aqui. */
    private static void startCongrats(ServerPlayer sp, MagicPath path) {
        setClassTime(sp, MagicApi.get(sp));
        celebrate(sp, path);
        sendState(sp);
        OnboardTasks.later(sp, CONGRATS_TICKS, TASK_CONGRATS, OnboardService::congratsDone);
    }

    private static void setClassTime(ServerPlayer sp, MagicData d) {
        if (sp.getServer() == null) return;
        int now = nowSeconds(sp.getServer());
        MagicApi.addCounter(sp, COUNTER_CLASS_AT, now - d.counter(COUNTER_CLASS_AT));
    }

    /** Letreiro no cliente + partículas e som para quem estiver perto. */
    private static void celebrate(ServerPlayer sp, MagicPath path) {
        PacketDistributor.sendToPlayer(sp, congratsPayload(path, EntryFlow.congratsLines(sp)));
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(), 60, 0.6, 0.9, 0.6, 0.45);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 0.2, sp.getZ(), 24, 0.8, 0.1, 0.8, 0.05);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    /**
     * Dados do letreiro: nome da classe na cor, categoria, recurso (com a cor), estilo, ícone e as linhas extras
     * (raça etc. — {@link EntryFlow#addCongratsLine}).
     */
    static OnboardPayloads.Congrats congratsPayload(MagicPath path, List<Component> extras) {
        MagicPath.Category category = path.category();
        MagicPath.Resource resource = path.resource();
        ItemStack icon = ClassKits.icon(path);
        return new OnboardPayloads.Congrats(path.displayName(), path.argb() & 0xFFFFFF, category.displayName(),
                resource.displayName(), resource.argb() & 0xFFFFFF,
                path.styleName(), icon == null ? ItemStack.EMPTY : icon, extras == null ? List.of() : List.copyOf(extras));
    }

    private static void congratsDone(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE || d.hasFlag(Onboarding.FLAG_CONGRATS)) return;
        d.setFlag(Onboarding.FLAG_CONGRATS, true);
        NeoForge.EVENT_BUS.post(new OnboardingEvent.ClassCongratsDone(sp));
    }

    // =====================================================================================
    //  Fim do tutorial
    // =====================================================================================
    public static void finishTutorial(ServerPlayer sp, boolean skipped) {
        if (sp == null || sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(Onboarding.FLAG_TUTORIAL) || d.path() == MagicPath.NONE) return;
        boolean congratsPending = !d.hasFlag(Onboarding.FLAG_CONGRATS);
        d.setFlag(Onboarding.FLAG_CLASS, true);
        d.setFlag(Onboarding.FLAG_TUTORIAL, true);
        OnboardTasks.cancel(sp.getUUID(), TASK_RESUME);
        if (congratsPending) {
            // Pulou antes do letreiro acabar (ou no meio das etapas de criação): a história ainda começa (o Diário vê
            // tutorialDone = true e não ensina); etapas não feitas ficam com os valores padrão dos módulos
            OnboardTasks.cancel(sp.getUUID(), TASK_CONGRATS);
            OnboardTasks.cancel(sp.getUUID(), TASK_STEP);
            d.setFlag(Onboarding.FLAG_CONGRATS, true);
            NeoForge.EVENT_BUS.post(new OnboardingEvent.ClassCongratsDone(sp));
        }
        sendState(sp);
        NeoForge.EVENT_BUS.post(new OnboardingEvent.TutorialDone(sp, skipped));
    }

    /**
     * Recomeça a entrada: sem classe → tela de classe; com classe → letreiro de novo e o tutorial outra vez (sem kit).
     */
    public static void reset(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        OnboardTasks.cancel(sp.getUUID(), TASK_CONGRATS);
        OnboardTasks.cancel(sp.getUUID(), TASK_RESUME);
        OnboardTasks.cancel(sp.getUUID(), TASK_STEP);
        for (String f : new String[]{Onboarding.FLAG_CLASS, Onboarding.FLAG_CONGRATS, Onboarding.FLAG_TUTORIAL, FLAG_QUIET_OVER}) {
            d.setFlag(f, false);
        }
        // As etapas de criação também recomeçam
        for (String f : List.copyOf(d.flags())) {
            if (f.startsWith(STEP_PREFIX)) d.setFlag(f, false);
        }
        d.setFlag(FLAG_TRACKER, true);
        if (d.path() == MagicPath.NONE) {
            PacketDistributor.sendToPlayer(sp, new OnboardPayloads.OnboardState(true, false, true));
            PacketDistributor.sendToPlayer(sp, CorePayloads.OpenPathScreen.INSTANCE);
            return;
        }
        onClassChosen(sp, d.path());
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.OnboardState(true, false, true));
    }

    // =====================================================================================
    //  Entrada no mundo e relógio dos 5 min
    // =====================================================================================
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        boolean hideTracker = false;
        if (d.path() == MagicPath.NONE) {
            // Personagem novo: o rastreador começa fechado (o tutorial ensina o J) — uma vez por personagem
            if (!d.hasFlag(FLAG_TRACKER)) {
                d.setFlag(FLAG_TRACKER, true);
                hideTracker = true;
            }
        } else if (legacy(d)) {
            // Personagem de versão antiga: já sabe jogar
            d.setFlag(Onboarding.FLAG_CLASS, true);
            d.setFlag(Onboarding.FLAG_CONGRATS, true);
            d.setFlag(Onboarding.FLAG_TUTORIAL, true);
            d.setFlag(FLAG_TRACKER, true);
        } else if (!pending(d).isEmpty()) {
            // Saiu no meio da montagem do personagem: reabre a etapa pendente
            OnboardTasks.later(sp, RESUME_DELAY, TASK_STEP, OnboardService::continueCreation);
        } else if (!d.hasFlag(Onboarding.FLAG_CONGRATS)) {
            OnboardTasks.later(sp, RESUME_DELAY, TASK_CONGRATS, OnboardService::congratsDone);
        } else if (!d.hasFlag(Onboarding.FLAG_TUTORIAL)) {
            OnboardTasks.later(sp, RESUME_DELAY, TASK_RESUME, p -> {
                if (!tutorialDone(p)) NeoForge.EVENT_BUS.post(new OnboardingEvent.TutorialResume(p));
            });
        }
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.OnboardState(quiet(sp), tutorialDone(sp), hideTracker));
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OnboardTasks.cancel(event.getEntity().getUUID(), TASK_CONGRATS);
        OnboardTasks.cancel(event.getEntity().getUUID(), TASK_RESUME);
        OnboardTasks.cancel(event.getEntity().getUUID(), TASK_STEP);
    }

    /** A cada segundo: o silêncio acaba 5 min depois da escolha mesmo sem o tutorial. */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 11 || sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (!d.hasFlag(Onboarding.FLAG_CLASS) || d.hasFlag(Onboarding.FLAG_TUTORIAL) || d.hasFlag(FLAG_QUIET_OVER)) return;
        if (creating(d)) return;
        if (elapsedSinceChoice(sp, d) < Onboarding.QUIET_TICKS / 20) return;
        d.setFlag(FLAG_QUIET_OVER, true);
        sendState(sp);
    }

    static void sendState(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.OnboardState(quiet(sp), tutorialDone(sp), false));
    }

    private OnboardService() {}
}
