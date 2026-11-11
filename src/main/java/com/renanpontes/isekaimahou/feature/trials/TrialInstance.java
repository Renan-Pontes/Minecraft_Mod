package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Uma Câmara de Provação aberta (servidor). Ciclo:
 * <pre>RECEPÇÃO → contagem (5 s) → INTRO → ONDAS (intervalos de 5 s) → CHEFE → VITÓRIA (45 s) → volta</pre>
 * Morrer na arena vira "Abatido" (espectador sobre a arena) e o jogador volta na onda seguinte com 50% da vida; todos
 * abatidos = derrota → todos voltam para a recepção e podem tentar de novo. Nada de item perdido.
 */
public final class TrialInstance {
    public enum Phase {
        LOBBY, COUNTDOWN, INTRO, WAVE, BREAK, BOSS_INTRO, BOSS, VICTORY, DEFEAT, CLOSED;

        /** Fases em que morrer vira "Abatido" (e não volta para a recepção). */
        public boolean combat() {
            return this == INTRO || this == WAVE || this == BREAK || this == BOSS_INTRO || this == BOSS;
        }

        /** Fases em que os jogadores estão (ou deveriam estar) na arena. */
        public boolean arena() {
            return combat() || this == VICTORY || this == DEFEAT;
        }

        /** Nome da fase para comandos (/mahou trial info|list). */
        public Component displayName() {
            return switch (this) {
                case LOBBY -> Component.translatable("command.isekai_mahou.trials.phase.lobby");
                case COUNTDOWN -> Component.translatable("command.isekai_mahou.trials.phase.countdown");
                case INTRO -> Component.translatable("command.isekai_mahou.trials.phase.intro");
                case WAVE -> Component.translatable("command.isekai_mahou.trials.phase.wave");
                case BREAK -> Component.translatable("command.isekai_mahou.trials.phase.break");
                case BOSS_INTRO -> Component.translatable("command.isekai_mahou.trials.phase.boss_intro");
                case BOSS -> Component.translatable("command.isekai_mahou.trials.phase.boss");
                case VICTORY -> Component.translatable("command.isekai_mahou.trials.phase.victory");
                case DEFEAT -> Component.translatable("command.isekai_mahou.trials.phase.defeat");
                case CLOSED -> Component.translatable("command.isekai_mahou.trials.phase.closed");
            };
        }
    }

    /** persistentData: UUID da instância dona da entidade (mobs, chefes, painéis). */
    public static final String TAG = "isekai_mahou.trial_instance";
    public static final String ROLE = "isekai_mahou.trial_role";
    public static final int ROLE_MOB = 0, ROLE_ELITE = 1, ROLE_BOSS = 2, ROLE_ADD = 3, ROLE_DECOR = 4;
    public static final int MAX_PARTY = 6;

    private static final int COUNTDOWN_TICKS = 100;
    private static final int INTRO_TICKS = 60;
    private static final int BREAK_TICKS = 100;
    private static final int BOSS_INTRO_TICKS = 70;
    private static final int VICTORY_TICKS = 900;
    private static final int DEFEAT_TICKS = 80;
    private static final int SUMMON_TICKS = 30;
    private static final int MAX_MOBS = 20;
    /** A quantos blocos da borda o aviso "Limite da arena" e a parede de luz aparecem (a arena tem raio 18-24). */
    private static final int BOUNDS_WARN = 6;
    /**
     * Quanto além da borda (face interna da parede) alguém pode estar antes de ser puxado de volta. Passar disso só é
     * possível atravessando a parede/barreira (teleporte de magia, bloco quebrado...), então o limite real é a parede.
     */
    private static final double ESCAPE_SLACK = 1.0;
    /** Mesma folga para os chefes (corpos grandes encostam mais fundo na parede). */
    private static final double BOSS_ESCAPE_SLACK = 2.5;

    /** Um jogador do grupo. */
    static final class Member {
        final UUID id;
        String name;
        boolean eligible;
        boolean clearedBefore;
        boolean downed;
        GameType prevMode = GameType.SURVIVAL;

        Member(ServerPlayer p) {
            this.id = p.getUUID();
            this.name = p.getGameProfile().getName();
        }
    }

    private record Pending(EntityType<?> type, Vec3 pos, int at, int role) {}

    private record Task(int at, int epoch, Runnable run) {}

    // =====================================================================================
    //  Estado
    // =====================================================================================
    public final UUID id = UUID.randomUUID();
    public final ServerLevel level;
    public final int index;
    public final Element element;
    public final int rank;
    public final BlockPos origin;
    public final BlockPos arenaCenter;
    public final int radius;
    public final int waves;
    final TrialBuilder.LobbyLayout layout;
    /** Limite da arena mostrado aos jogadores (core ArenaBounds) — o mesmo usado para puxar de volta. */
    final ArenaBounds.Arena bounds;
    private boolean boundsShown;

    final Map<UUID, Member> members = new LinkedHashMap<>();
    final Set<UUID> invited = new HashSet<>();
    UUID host;
    Phase phase = Phase.LOBBY;
    int epoch;
    private int timer;
    private int age;
    private int wave = -1;
    private boolean arenaReady;
    private final Deque<BooleanSupplier> build = new ArrayDeque<>();
    private final List<UUID> mobs = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private int waveTotal;
    private final List<UUID> bosses = new ArrayList<>();
    private final Map<UUID, Integer> bossMissing = new LinkedHashMap<>();
    /** Vida máxima de cada chefe invocado (mortos continuam contando: a barra não "volta" quando um cai). */
    private final Map<UUID, Float> bossMaxHp = new LinkedHashMap<>();
    /** Quantos limiares de vida do chefe (66% / 33%) já trouxeram os abatidos de volta. */
    private int bossReviveStage;
    private Component bossTitle = Component.empty();
    private final List<Task> tasks = new ArrayList<>();
    private int hazardTimer;
    private int hazardCount;
    private final ServerBossEvent bar;
    private UUID panelId;
    private UUID exitLabelId;
    private UUID victoryLabelId;
    @Nullable private BlockPos victoryExit;
    private boolean hudDirty = true;

    TrialInstance(ServerLevel level, int index, Element element, int rank, ServerPlayer host) {
        this.level = level;
        this.index = index;
        this.element = element;
        this.rank = rank;
        this.origin = TrialDimension.origin(index);
        this.arenaCenter = TrialDimension.arenaCenter(index);
        this.radius = TrialDimension.arenaRadius(rank);
        this.waves = rank <= 2 ? 3 : rank <= 4 ? 4 : 5;
        this.host = host.getUUID();
        this.layout = TrialBuilder.lobby(level, index, element);
        this.bounds = ArenaBounds.Arena.circle("trial_" + id, level.dimension(), centerVec(), TrialDimension.arenaEdge(rank))
                .withColor(element.color)
                .withName(TrialsApi.title(element, rank))
                .withWarnDistance(BOUNDS_WARN)
                .withVertical(floorTop(), floorTop() + TrialDimension.ARENA_ROOF - 1);
        for (Runnable step : TrialBuilder.arenaSteps(level, index, element, rank)) build.add(() -> { step.run(); return true; });
        this.bar = new ServerBossEvent(TrialsApi.title(element, rank), TrialTheme.barColor(element), BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setProgress(0F);
    }

    /**
     * Painéis de texto da recepção. Chamado pelo gerenciador DEPOIS de registrar a instância — entidades com a
     * marca de uma instância desconhecida são barradas ao entrar no mundo.
     */
    void spawnPanels() {
        panelId = TrialPanel.spawn(level, layout.panel(), panelText(), id, 230);
        exitLabelId = TrialPanel.spawn(level, layout.exitLabel(),
                Component.translatable("panel.isekai_mahou.trials.exit_label").withStyle(ChatFormatting.AQUA), id, 160);
    }

    /** Dados mínimos para a limpeza no boot (SavedData). */
    CompoundTag info() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("element", element.id);
        t.putInt("rank", rank);
        t.putUUID("host", host);
        return t;
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    public Phase phase() { return phase; }

    public boolean isMember(UUID player) { return members.containsKey(player); }

    public boolean isHost(UUID player) { return host.equals(player); }

    /** O instance está realmente aberto (não fechado). */
    public boolean open() { return phase != Phase.CLOSED; }

    /** Desafio = o anfitrião já tinha concluído este rank (XP/espólio 50%). */
    public boolean challenge() {
        Member m = members.get(host);
        return m != null && m.clearedBefore;
    }

    Vec3 centerVec() {
        return new Vec3(arenaCenter.getX() + 0.5, floorTop(), arenaCenter.getZ() + 0.5);
    }

    double floorTop() {
        return arenaCenter.getY() + 1.0;
    }

    @Nullable
    ServerPlayer player(UUID uuid) {
        return level.getServer().getPlayerList().getPlayer(uuid);
    }

    List<ServerPlayer> onlineMembers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : members.keySet()) {
            ServerPlayer p = player(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Jogadores de pé na arena (alvos dos perigos; quem conta para "todos abatidos"). */
    List<ServerPlayer> standingPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (m.downed) continue;
            ServerPlayer p = player(m.id);
            if (p != null && p.level() == level && p.isAlive() && !p.isSpectator() && !p.isCreative()) out.add(p);
        }
        return out;
    }

    boolean isStanding(ServerPlayer p) {
        Member m = members.get(p.getUUID());
        return m != null && !m.downed && !p.isSpectator() && !p.isCreative() && p.isAlive();
    }

    /** Os perigos agendados ainda valem? (mesma "época": nada de golpe atrasado depois de vitória/derrota). */
    boolean hazardActive(int ep) {
        return ep == epoch && (phase == Phase.WAVE || phase == Phase.BREAK || phase == Phase.BOSS || phase == Phase.BOSS_INTRO);
    }

    /** Mantém o ponto dentro da arena (até {@code margin} da parede), na altura do piso. */
    Vec3 clampToArena(Vec3 p, double margin) {
        Vec3 c = centerVec();
        double dx = p.x - c.x, dz = p.z - c.z, d = Math.sqrt(dx * dx + dz * dz), max = radius - margin;
        if (d > max && d > 1.0E-3) {
            dx *= max / d;
            dz *= max / d;
        }
        return new Vec3(c.x + dx, floorTop(), c.z + dz);
    }

    /** O ponto está dentro da área atual do grupo (arena ou recepção)? Usado para pérolas/coro. */
    boolean allows(Vec3 target) {
        if (phase.arena()) {
            return bounds.edgeDistance(target) >= 0 && target.y >= floorTop() - 1
                    && target.y <= floorTop() + TrialDimension.ARENA_ROOF - 1;
        }
        double h = TrialDimension.LOBBY_HALF - 0.5;
        return Math.abs(target.x - (origin.getX() + 0.5)) <= h && Math.abs(target.z - (origin.getZ() + 0.5)) <= h
                && target.y >= origin.getY() && target.y <= origin.getY() + TrialDimension.LOBBY_ROOF - 1;
    }

    void schedule(int delay, Runnable run) {
        tasks.add(new Task(age + Math.max(0, delay), epoch, run));
    }

    /** Marca a entidade como desta instância (para limpeza e para nada sobrar depois de um reinício). */
    static void tag(Entity e, UUID instance, int role) {
        CompoundTag pd = e.getPersistentData();
        pd.putUUID(TAG, instance);
        pd.putInt(ROLE, role);
    }

    // =====================================================================================
    //  Grupo
    // =====================================================================================
    /** Coloca o jogador no grupo e o leva para a recepção (as checagens já foram feitas pelo gerenciador). */
    void addMember(ServerPlayer p) {
        Member m = new Member(p);
        List<Component> missing = new ArrayList<>();
        m.eligible = TrialsApi.meetsRequirements(MagicApi.get(p), element, rank, missing);
        m.clearedBefore = TrialsApi.isCleared(MagicApi.get(p), element, rank);
        boolean first = members.isEmpty();
        members.put(p.getUUID(), m);
        invited.remove(p.getUUID());
        TrialManager.sendToLobby(p, this);

        p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.welcome", TrialsApi.title(element, rank))
                .withStyle(ChatFormatting.GOLD));
        if (p.getUUID().equals(host)) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.host_hint").withStyle(ChatFormatting.YELLOW));
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.invite_hint")
                    .withStyle(s -> s.withColor(ChatFormatting.GRAY)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mahou trial invite "))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("message.isekai_mahou.trials.invite_hover")))));
        } else {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.member_hint", hostName()).withStyle(ChatFormatting.YELLOW));
        }
        if (m.clearedBefore && m.eligible) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.challenge_note").withStyle(ChatFormatting.AQUA));
        }
        if (!m.eligible) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.assist_note").withStyle(ChatFormatting.GRAY));
            for (Component c : missing) {
                p.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY).append(c.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
        if (!first) {
            broadcast(Component.translatable("message.isekai_mahou.trials.joined", p.getDisplayName(), members.size(), MAX_PARTY)
                    .withStyle(ChatFormatting.GREEN), p.getUUID());
            level.playSound(null, layout.altar(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.3F);
        }
        refreshPanel();
        hudDirty = true;
    }

    /** Motivos de saída do grupo. */
    enum Leave { LEFT, LOGOUT, DIMENSION }

    /** Tira o jogador do grupo (sem teleportar: quem chama decide). Se o anfitrião sair, o próximo assume. */
    void removeMember(UUID uuid, Leave reason) {
        if (phase == Phase.CLOSED) return;
        // O último a sair pelo portal depois da vitória leva o espólio que ainda estava no chão.
        if (phase == Phase.VICTORY && reason == Leave.LEFT && members.size() == 1 && members.containsKey(uuid)) collectLoot(true);
        Member m = members.remove(uuid);
        if (m == null) return;
        ServerPlayer p = player(uuid);
        if (p != null) {
            if (m.downed) restoreMode(p, m);
            bar.removePlayer(p);
            TrialNetwork.clear(p);
        }
        String key = switch (reason) {
            case LOGOUT -> "message.isekai_mahou.trials.left_logout";
            case DIMENSION -> "message.isekai_mahou.trials.left_dimension";
            default -> "message.isekai_mahou.trials.left";
        };
        broadcast(Component.translatable(key, m.name).withStyle(ChatFormatting.YELLOW), null);
        if (members.isEmpty()) {
            close("message.isekai_mahou.trials.closed_empty");
            return;
        }
        if (host.equals(uuid)) {
            host = members.keySet().iterator().next();
            Member h = members.get(host);
            broadcast(Component.translatable("message.isekai_mahou.trials.new_host", h.name).withStyle(ChatFormatting.GOLD), null);
            if (phase == Phase.COUNTDOWN) cancelCountdown();
        }
        if (phase.combat()) checkAllDowned();
        refreshPanel();
        hudDirty = true;
    }

    String hostName() {
        Member h = members.get(host);
        return h == null ? "?" : h.name;
    }

    void broadcast(Component msg, @Nullable UUID except) {
        for (ServerPlayer p : onlineMembers()) if (!p.getUUID().equals(except)) p.sendSystemMessage(msg);
    }

    private void titleAll(Component title, Component subtitle, int stay) {
        for (ServerPlayer p : onlineMembers()) TrialFx.title(p, title, subtitle, 6, stay, 10);
    }

    private void actionAll(Component text) {
        for (ServerPlayer p : onlineMembers()) TrialFx.actionBar(p, text);
    }

    private void soundAll(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : onlineMembers()) TrialFx.ding(p, sound, volume, pitch);
    }

    // =====================================================================================
    //  Altar / saída
    // =====================================================================================
    void onAltar(ServerPlayer p) {
        Member m = members.get(p.getUUID());
        if (m == null) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.not_member").withStyle(ChatFormatting.RED), true);
            return;
        }
        switch (phase) {
            case LOBBY -> {
                if (!isHost(p.getUUID())) {
                    p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.waiting_host", hostName())
                            .withColor(element.color), true);
                    TrialFx.ding(p, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F);
                    return;
                }
                phase = Phase.COUNTDOWN;
                timer = COUNTDOWN_TICKS;
                broadcast(Component.translatable("message.isekai_mahou.trials.countdown_start", p.getDisplayName())
                        .withStyle(ChatFormatting.GOLD), null);
                level.playSound(null, layout.altar(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.9F);
                level.playSound(null, layout.altar(), SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.BLOCKS, 1.5F, 0.8F);
                ElementFx.burst(level, Vec3.atCenterOf(layout.altar()).add(0, 1, 0), element, 40, 0.6);
                refreshPanel();
                hudDirty = true;
            }
            case COUNTDOWN -> {
                if (isHost(p.getUUID())) cancelCountdown();
                else p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.starting").withColor(element.color), true);
            }
            default -> p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.in_progress").withColor(element.color), true);
        }
    }

    private void cancelCountdown() {
        if (phase != Phase.COUNTDOWN) return;
        phase = Phase.LOBBY;
        broadcast(Component.translatable("message.isekai_mahou.trials.countdown_cancel").withStyle(ChatFormatting.YELLOW), null);
        titleAll(Component.empty(), Component.empty(), 1);
        level.playSound(null, layout.altar(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        refreshPanel();
        hudDirty = true;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    void tick() {
        if (phase == Phase.CLOSED) return;
        age++;
        runBuild();
        runTasks();
        checkMembers();
        if (phase == Phase.CLOSED) return;
        if (phase.combat() && level.getDifficulty() == Difficulty.PEACEFUL) {
            close("message.isekai_mahou.trials.closed_peaceful");
            return;
        }
        switch (phase) {
            case LOBBY -> tickLobby();
            case COUNTDOWN -> tickCountdown();
            case INTRO -> tickIntro();
            case WAVE -> tickWave();
            case BREAK -> tickBreak();
            case BOSS_INTRO -> tickBossIntro();
            case BOSS -> tickBoss();
            case VICTORY -> tickVictory();
            case DEFEAT -> tickDefeat();
            default -> { }
        }
        if (phase == Phase.CLOSED) return;
        if (hudDirty || age % 10 == 0) syncHud();
        if (age % 20 == 0) refreshBar();
    }

    private void runBuild() {
        BooleanSupplier step = build.peek();
        if (step == null) {
            arenaReady = true;
            return;
        }
        if (step.getAsBoolean()) build.poll();
        if (build.isEmpty()) arenaReady = true;
    }

    private void runTasks() {
        if (tasks.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.epoch != epoch) { it.remove(); continue; }
            if (t.at <= age) { due.add(t); it.remove(); }
        }
        for (Task t : due) t.run.run();
    }

    /** Membros fora do lugar: desconectados/fora da dimensão saem; quem escapou dos limites volta. */
    private void checkMembers() {
        for (UUID u : List.copyOf(members.keySet())) {
            Member m = members.get(u);
            if (m == null) continue;
            ServerPlayer p = player(u);
            if (p == null) { removeMember(u, Leave.LOGOUT); continue; }
            if (p.level() != level) { removeMember(u, Leave.DIMENSION); continue; }
            if (phase == Phase.CLOSED) return;
            if (m.downed && !p.isSpectator()) {
                // alguém mudou o modo de jogo na mão: considera de pé (e não restaura nada depois)
                m.downed = false;
                TrialManager.data().setPrevMode(u, -1);
                hudDirty = true;
            }
            if (!phase.arena()) {
                Vec3 o = Vec3.atBottomCenterOf(origin);
                double h = TrialDimension.LOBBY_HALF + 0.5;
                if (Math.abs(p.getX() - o.x) > h || Math.abs(p.getZ() - o.z) > h || p.getY() < origin.getY() - 1
                        || p.getY() > origin.getY() + TrialDimension.LOBBY_ROOF + 1) {
                    teleport(p, layout.spawn(), 180F);
                }
            } else {
                double edge = bounds.edgeDistance(p.position());
                if (m.downed) {
                    if (edge < -10 || p.getY() < floorTop() + 2 || p.getY() > floorTop() + 40) {
                        teleport(p, centerVec().add(0, 14, 0), p.getYRot());
                    }
                } else if (edge < -ESCAPE_SLACK || p.getY() < floorTop() - 3 || p.getY() > floorTop() + TrialDimension.ARENA_ROOF + 1) {
                    pullBack(p);
                }
            }
        }
    }

    /**
     * Quem passou da borda (só atravessando a parede: magia de teleporte, bloco quebrado...) volta para o ponto da
     * arena mais perto de onde saiu, no piso, a 2,5 blocos da parede — e não para o meio da luta.
     */
    private void pullBack(ServerPlayer p) {
        Vec3 from = p.position();
        Vec3 in = bounds.clampInside(from, 2.5);
        Vec3 to = new Vec3(in.x, floorTop(), in.z);
        if (!level.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) to = arrival(0, 1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 24, 0.3, 0.6, 0.3, 0.05);
        teleport(p, to, p.getYRot());
        ElementFx.burst(level, to.add(0, 1, 0), element, 16, 0.4);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.3F);
        TrialFx.actionBar(p, Component.translatable("message.isekai_mahou.trials.pulled_back").withColor(element.color));
    }

    /** Mostra a borda da arena (parede de luz + "Limite da arena: N blocos") para quem está no Reino das Provações. */
    private void showBounds() {
        ArenaBounds.register(level.getServer(), bounds);
        boundsShown = true;
    }

    /** Some com a borda (volta à recepção, fim da instância). */
    private void hideBounds() {
        if (!boundsShown) return;
        boundsShown = false;
        ArenaBounds.unregister(level.getServer(), bounds.id());
    }

    private void tickLobby() {
        if (age % 30 == 0) {
            Vec3 a = Vec3.atCenterOf(layout.altar());
            ElementFx.ring(level, a.add(0, -0.4, 0), element, 1.6, 18);
        }
        if (age % 40 == 0) {
            // Magias que trocam blocos direto (sem evento) não podem sumir com o Altar/Portal: o grupo ficaria preso.
            if (!level.getBlockState(layout.altar()).is(TrialsRegistry.ALTAR.get())) {
                level.setBlock(layout.altar(), TrialsRegistry.ALTAR.get().defaultBlockState(), 3);
            }
            if (!level.getBlockState(layout.exit()).is(TrialsRegistry.EXIT.get())) {
                level.setBlock(layout.exit(), TrialsRegistry.EXIT.get().defaultBlockState(), 3);
            }
        }
        if (age % 100 == 0 && !arenaReady) {
            for (ServerPlayer p : onlineMembers()) {
                if (isHost(p.getUUID())) TrialFx.actionBar(p, Component.translatable("message.isekai_mahou.trials.arena_preparing").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private void tickCountdown() {
        if (timer > 0 && timer % 20 == 0) {
            int s = timer / 20;
            titleAll(Component.literal(String.valueOf(s)).withStyle(ChatFormatting.BOLD).withColor(element.color),
                    Component.translatable("title.isekai_mahou.trials.countdown", TrialsApi.title(element, rank)), 22);
            soundAll(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.6F + (5 - s) * 0.15F);
            hudDirty = true;
        }
        if (timer > 0) timer--;
        if (timer > 0) return;
        if (!arenaReady) {
            if (age % 20 == 0) actionAll(Component.translatable("message.isekai_mahou.trials.arena_preparing").withStyle(ChatFormatting.GRAY));
            return;
        }
        enterArena();
    }

    // =====================================================================================
    //  Arena
    // =====================================================================================
    /** Posição de chegada na arena para o jogador i de n (anel em volta do medalhão central). */
    private Vec3 arrival(int i, int n) {
        Vec3 c = centerVec();
        double a = Math.PI * 2 * i / Math.max(1, n) + Math.PI / 2;
        return new Vec3(c.x + Math.cos(a) * 2.2, floorTop(), c.z + Math.sin(a) * 2.2);
    }

    private void enterArena() {
        phase = Phase.INTRO;
        timer = INTRO_TICKS;
        List<ServerPlayer> online = onlineMembers();
        for (int i = 0; i < online.size(); i++) {
            ServerPlayer p = online.get(i);
            Vec3 from = p.position();
            TrialFx.warp(level, from, element);
            teleport(p, arrival(i, online.size()), 180F);
            p.setHealth(p.getMaxHealth());
            p.clearFire();
            TrialFx.title(p, TrialsApi.title(element, rank).withStyle(ChatFormatting.BOLD),
                    Component.translatable(challenge() ? "title.isekai_mahou.trials.enter_challenge" : "title.isekai_mahou.trials.enter"), 8, 50, 12);
            bar.addPlayer(p);
        }
        showBounds();
        Component limit = Component.translatable("message.isekai_mahou.trials.bounds_hint", radius).withColor(element.color);
        for (ServerPlayer p : online) p.sendSystemMessage(limit);
        Vec3 c = centerVec();
        TrialFx.warp(level, c, element);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.0F);
        level.playSound(null, c.x, c.y, c.z, TrialTheme.ambience(element), SoundSource.HOSTILE, 2.0F, 0.7F);
        refreshPanel();
        hudDirty = true;
    }

    private void tickIntro() {
        Vec3 c = centerVec();
        if (age % 4 == 0) {
            double f = 1.0 - timer / (double) INTRO_TICKS;
            ElementFx.ring(level, c.add(0, 0.1, 0), element, 1.0 + f * (radius - 1), 48);
        }
        if (--timer <= 0) beginWave(0);
    }

    // ------------------------------------------------------------------------------------- ondas
    private void beginWave(int i) {
        phase = Phase.WAVE;
        wave = i;
        mobs.clear();
        pending.clear();
        reviveAll(0.5F);
        RandomSource r = level.random;
        int players = Math.max(1, members.size());
        int n = Mth.clamp(3 + rank + 2 * (players - 1), 3, MAX_MOBS);
        float progress = waves > 1 ? i / (float) (waves - 1) : 1F;
        int t3 = rank >= 5 ? Math.round(n * (0.15F + 0.1F * (rank - 5) + 0.15F * progress)) : (rank == 4 && i == waves - 1 ? 1 : 0);
        int t2 = rank >= 3 ? Math.round((n - t3) * (0.35F + 0.1F * (rank - 3) + 0.2F * progress)) : (rank == 2 ? Math.round(n * 0.3F * progress) : 0);
        int delay = 0;
        for (int k = 0; k < n; k++) {
            int tier = k < t3 ? 3 : k < t3 + t2 ? 2 : 1;
            queue(TrialTheme.pick(element, tier, r), ROLE_MOB, delay);
            delay += 4 + r.nextInt(4);
        }
        if (i >= 1) {
            int eliteTier = rank <= 2 ? 2 : rank <= 4 ? (i == waves - 1 ? 3 : 2) : 3;
            queue(TrialTheme.pick(element, eliteTier, r), ROLE_ELITE, delay + 10);
        }
        waveTotal = pending.size();
        hazardTimer = hazardInterval() / 2 + 20;

        boolean last = i == waves - 1;
        Component title = last ? Component.translatable("title.isekai_mahou.trials.wave_final", i + 1, waves)
                : Component.translatable("title.isekai_mahou.trials.wave", i + 1, waves);
        titleAll(title.copy().withStyle(ChatFormatting.BOLD).withColor(element.color),
                Component.translatable("title.isekai_mahou.trials.wave_sub", waveTotal), 30);
        Vec3 c = centerVec();
        if (last) level.playSound(null, c.x, c.y, c.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 1.0F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.HOSTILE, 2.0F, 0.9F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.9F);
        hudDirty = true;
    }

    private int hazardInterval() {
        return Math.max(60, 140 - 10 * rank);
    }

    private void queue(EntityType<?> type, int role, int delay) {
        Vec3 pos = findSpot(type);
        int warm = SUMMON_TICKS + delay;
        pending.add(new Pending(type, pos, age + warm, role));
        Vec3 ground = new Vec3(pos.x, floorTop(), pos.z);
        Telegraph.Shape circle = Telegraph.Shape.circle(ground, role == ROLE_ELITE ? 2.2F : 1.4F).warmup(warm).color(element.color).flat().noFlash();
        if (role != ROLE_ELITE) circle.noRunes();
        Telegraph.show(level, circle);
        schedule(delay, () -> level.playSound(null, pos.x, pos.y, pos.z, TrialTheme.summon(element), SoundSource.HOSTILE, 0.6F,
                0.8F + level.random.nextFloat() * 0.3F));
    }

    /** Ponto livre no piso (longe do centro e das colunas). Voadores nascem 2 blocos acima. */
    private Vec3 findSpot(EntityType<?> type) {
        RandomSource r = level.random;
        Vec3 c = centerVec();
        boolean flyer = TrialTheme.isFlyer(type);
        List<Vec3> pillars = TrialBuilder.pillarCenters(index, rank);
        for (int attempt = 0; attempt < 30; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = radius * 0.35 + r.nextDouble() * (radius * 0.6 - 2);
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            boolean blocked = false;
            for (Vec3 pc : pillars) if (Math.hypot(pc.x - x, pc.z - z) < 2.6) { blocked = true; break; }
            if (blocked) continue;
            double y = floorTop() + (flyer ? 2 : 0);
            AABB box = type.getSpawnAABB(x, y, z);
            if (!level.noCollision(box) || level.containsAnyLiquid(box)) continue;
            return new Vec3(x, y, z);
        }
        return c.add(0, flyer ? 2 : 0, radius * 0.5);
    }

    private void tickWave() {
        tickPending();
        mobs.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        if (age % 20 == 0) herd();
        tickHazards();
        if (pending.isEmpty() && mobs.isEmpty()) waveCleared();
    }

    private void tickPending() {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (age >= p.at()) {
                it.remove();
                Vec3 pos = level.noCollision(p.type().getSpawnAABB(p.pos().x, p.pos().y, p.pos().z)) ? p.pos() : findSpot(p.type());
                Mob mob = TrialSpawner.spawnMob(this, p.type(), pos, p.role(), members.size());
                if (mob != null) {
                    mobs.add(mob.getUUID());
                    ServerPlayer target = nearestStanding(pos);
                    if (target != null) mob.setTarget(target);
                    // cavaleiros (aranha + esqueleto...) também contam na onda
                    for (Entity rider : mob.getIndirectPassengers()) {
                        if (rider instanceof Mob riderMob && !mobs.contains(rider.getUUID())) {
                            mobs.add(rider.getUUID());
                            waveTotal++;
                            if (target != null) riderMob.setTarget(target);
                        }
                    }
                } else {
                    waveTotal = Math.max(0, waveTotal - 1);
                }
            } else if (age % 3 == 0) {
                Vec3 at = new Vec3(p.pos().x, floorTop(), p.pos().z);
                level.sendParticles(ElementFx.signature(element), at.x, at.y + 0.3, at.z, 2, 0.25, 0.3, 0.25, 0.02);
                level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.2, at.z, 3, 0.4, 0.4, 0.4, 0.4);
            }
        }
    }

    private void waveCleared() {
        Vec3 c = centerVec();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.3F);
        ElementFx.burst(level, c.add(0, 1.5, 0), element, 40, 0.8);
        actionAll(Component.translatable(wave >= waves - 1 ? "message.isekai_mahou.trials.waves_done" : "message.isekai_mahou.trials.wave_cleared",
                wave + 1, waves).withStyle(ChatFormatting.GREEN));
        phase = Phase.BREAK;
        timer = BREAK_TICKS;
        hudDirty = true;
    }

    private void tickBreak() {
        if (timer % 20 == 0 && timer > 0) {
            int s = timer / 20;
            boolean boss = wave >= waves - 1;
            actionAll(Component.translatable(boss ? "message.isekai_mahou.trials.boss_in" : "message.isekai_mahou.trials.next_wave_in", s)
                    .withColor(element.color));
            hudDirty = true;
        }
        tickHazards();
        if (--timer > 0) return;
        if (wave < waves - 1) beginWave(wave + 1);
        else beginBossIntro();
    }

    /** Puxa de volta quem fugiu, mantém o alvo num jogador de pé e faz as últimas criaturas brilharem. */
    private void herd() {
        boolean reveal = pending.isEmpty() && mobs.size() <= 3;
        for (UUID u : mobs) {
            if (!(level.getEntity(u) instanceof Mob mob)) continue;
            LivingEntity target = mob.getTarget();
            if (!(target instanceof ServerPlayer tp) || !isStanding(tp)) {
                ServerPlayer nearest = nearestStanding(mob.position());
                if (nearest != null) mob.setTarget(nearest);
            }
            if (bounds.edgeDistance(mob.position()) < -ESCAPE_SLACK || mob.getY() < floorTop() - 3
                    || mob.getY() > floorTop() + TrialDimension.ARENA_ROOF) {
                Vec3 back = findSpot(mob.getType());
                level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
                mob.teleportTo(back.x, back.y, back.z);
                mob.setDeltaMovement(Vec3.ZERO);
                ElementFx.burst(level, back.add(0, 0.5, 0), element, 12, 0.4);
            }
            if (reveal) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
        }
    }

    @Nullable
    private ServerPlayer nearestStanding(Vec3 from) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : standingPlayers()) {
            double d = p.distanceToSqr(from);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private void tickHazards() {
        boolean active = (phase == Phase.WAVE && wave >= 1) || phase == Phase.BOSS;
        if (!active) return;
        if (--hazardTimer > 0) return;
        int interval = hazardInterval();
        if (phase == Phase.BOSS) interval = Math.round(interval * 1.4F);
        hazardTimer = interval + level.random.nextInt(Math.max(1, interval / 4));
        List<Element> bases = TrialTheme.bases(element);
        Element base = bases.get(hazardCount++ % bases.size());
        TrialHazards.fire(this, base);
    }

    // ------------------------------------------------------------------------------------- chefe
    private List<TrialSpawner.BossSpec> lineup() {
        return TrialSpawner.lineup(element, rank, centerVec());
    }

    private void beginBossIntro() {
        phase = Phase.BOSS_INTRO;
        timer = BOSS_INTRO_TICKS;
        reviveAll(0.5F);
        List<TrialSpawner.BossSpec> specs = lineup();
        MutableComponent names = Component.empty();
        for (int i = 0; i < specs.size(); i++) {
            TrialSpawner.BossSpec s = specs.get(i);
            if (!s.main()) continue;
            if (!names.getSiblings().isEmpty()) names.append(Component.literal(" + "));
            names.append(TrialSpawner.bossName(s.id()));
        }
        if (rank == 6) names.append(Component.translatable("title.isekai_mahou.trials.with_sentinels"));
        bossTitle = names;
        for (TrialSpawner.BossSpec s : specs) {
            Telegraph.show(level, Telegraph.Shape.circle(new Vec3(s.pos().x, floorTop(), s.pos().z), s.main() ? 4.0F : 2.8F)
                    .warmup(BOSS_INTRO_TICKS - 10).color(element.color).flat());
        }
        titleAll(Component.translatable("title.isekai_mahou.trials.boss").withStyle(ChatFormatting.BOLD).withColor(element.color),
                names.copy().withStyle(ChatFormatting.GOLD), 50);
        Vec3 c = centerVec();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 0.7F);
        BossKit.shake(level, c, radius + 16, 1.2F, 40);
        bar.setPlayBossMusic(true);
        hudDirty = true;
    }

    private void tickBossIntro() {
        List<TrialSpawner.BossSpec> specs = lineup();
        if (age % 3 == 0) {
            for (TrialSpawner.BossSpec s : specs) {
                Vec3 p = s.pos();
                double f = 1.0 - timer / (double) BOSS_INTRO_TICKS;
                for (int k = 0; k < 4; k++) {
                    double a = age * 0.3 + k * Math.PI / 2;
                    level.sendParticles(ElementFx.dust(element, 2.0F), p.x + Math.cos(a) * 3 * (1 - f), floorTop() + 0.2 + f * 2.5,
                            p.z + Math.sin(a) * 3 * (1 - f), 1, 0, 0, 0, 0);
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, floorTop() + 0.1, p.z, 3, 0.6, 0.05, 0.6, 0.02);
            }
        }
        if (timer == 10) {
            // Antes de o chefe aparecer, empurra quem está em cima do círculo (nada de nascer colado no jogador).
            for (TrialSpawner.BossSpec s : specs) {
                for (ServerPlayer p : standingPlayers()) {
                    Vec3 d = p.position().subtract(s.pos()).multiply(1, 0, 1);
                    if (d.length() < 4.5) {
                        Vec3 push = d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
                        p.setDeltaMovement(push.x * 1.1, 0.45, push.z * 1.1);
                        p.hurtMarked = true;
                    }
                }
            }
            spawnBosses(specs);
        }
        if (--timer > 0) return;
        phase = Phase.BOSS;
        hazardTimer = hazardInterval();
        hudDirty = true;
    }

    private void spawnBosses(List<TrialSpawner.BossSpec> specs) {
        bosses.clear();
        bossMissing.clear();
        bossMaxHp.clear();
        bossReviveStage = 0;
        for (TrialSpawner.BossSpec s : specs) {
            Mob boss = TrialSpawner.spawnBoss(this, s);
            if (boss == null) continue;
            bosses.add(boss.getUUID());
            bossMaxHp.put(boss.getUUID(), boss.getMaxHealth());
            ServerPlayer target = nearestStanding(boss.position());
            if (target != null) boss.setTarget(target);
        }
        if (bosses.isEmpty()) victory();   // nada pôde ser invocado (sem chefes nem criaturas): não prende o grupo
    }

    private void tickBoss() {
        Vec3 c = centerVec();
        float hp = 0, max = 0;
        for (UUID u : List.copyOf(bosses)) {
            Entity e = level.getEntity(u);
            if (e == null) {
                // sumiu sem morrer (comando, outro mod): depois de 5 s conta como vencido para não travar a provação
                int miss = bossMissing.merge(u, 1, Integer::sum);
                if (miss > 100) { bosses.remove(u); bossMissing.remove(u); }
                continue;
            }
            bossMissing.remove(u);
            if (!e.isAlive()) { bosses.remove(u); continue; }
            if (e instanceof LivingEntity le) {
                hp += le.getHealth();
                bossMaxHp.merge(u, le.getMaxHealth(), Math::max);
            }
            if (age % 20 == 0 && e instanceof Mob mob) {
                if (bounds.edgeDistance(mob.position()) < -BOSS_ESCAPE_SLACK || mob.getY() < floorTop() - 4
                        || mob.getY() > floorTop() + TrialDimension.ARENA_ROOF + 2) {
                    level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.5, 1, 0.5, 0.3);
                    mob.teleportTo(c.x, floorTop() + 0.5, c.z - 6);
                    mob.setDeltaMovement(Vec3.ZERO);
                }
                LivingEntity target = mob.getTarget();
                if (!(target instanceof ServerPlayer tp) || !isStanding(tp)) {
                    ServerPlayer nearest = nearestStanding(mob.position());
                    if (nearest != null) mob.setTarget(nearest);
                }
            }
        }
        mobs.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        if (age % 20 == 0) herd();
        for (float m : bossMaxHp.values()) max += m;
        if (max > 0) {
            float frac = Mth.clamp(hp / max, 0F, 1F);
            bar.setProgress(frac);
            // Não há "próxima onda" na luta contra o chefe: os abatidos voltam quando ele perde 1/3 e 2/3 da vida.
            boolean crossed = false;
            while (bossReviveStage < 2 && frac <= (bossReviveStage == 0 ? 0.66F : 0.33F)) {
                bossReviveStage++;
                crossed = true;
            }
            if (crossed && !bosses.isEmpty() && members.values().stream().anyMatch(m -> m.downed)) {
                broadcast(Component.translatable("message.isekai_mahou.trials.boss_revive").withColor(element.color), null);
                reviveAll(0.5F);
            }
        }
        tickHazards();
        if (bosses.isEmpty()) victory();
    }

    /** Uma criatura desta instância morreu (evento): tira das listas na hora (o HUD atualiza sem esperar). */
    void onTrackedDeath(Entity e) {
        if (mobs.remove(e.getUUID()) || bosses.remove(e.getUUID())) hudDirty = true;
    }

    /** Criaturas invocadas por chefes/xamãs entram na lista (contam na onda e somem no fim). */
    void adopt(Mob mob) {
        tag(mob, id, ROLE_ADD);
        if (phase == Phase.WAVE || phase == Phase.BOSS || phase == Phase.BOSS_INTRO) {
            if (!mobs.contains(mob.getUUID())) {
                mobs.add(mob.getUUID());
                if (phase == Phase.WAVE) waveTotal++;
            }
        }
    }

    // =====================================================================================
    //  Abatidos
    // =====================================================================================
    /** O jogador "morreu" aqui: devolve true se a morte deve ser cancelada (sempre, nas Câmaras). */
    boolean onFatal(ServerPlayer p) {
        Member m = members.get(p.getUUID());
        if (m == null) return false;
        p.setHealth(1F);
        p.clearFire();
        p.resetFallDistance();
        if (!phase.combat()) {
            // recepção/vitória/derrota: ninguém morre de verdade — volta inteiro para o ponto seguro
            p.setHealth(p.getMaxHealth());
            if (phase.arena()) teleport(p, arrival(0, 1), p.getYRot());
            else teleport(p, layout.spawn(), 180F);
            return true;
        }
        if (m.downed) return true;
        m.downed = true;
        m.prevMode = p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR ? GameType.SURVIVAL : p.gameMode.getGameModeForPlayer();
        TrialManager.data().setPrevMode(p.getUUID(), m.prevMode.getId());
        Vec3 at = p.position();
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 30, 0.4, 0.6, 0.4, 0.04);
        ElementFx.burst(level, at.add(0, 1, 0), element, 20, 0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.6F);
        clearHarmful(p);   // poções boas que o jogador tomou continuam valendo quando ele voltar
        p.setGameMode(GameType.SPECTATOR);
        // espectador flutuando sobre a arena, olhando para baixo (vê a luta inteira)
        Vec3 above = centerVec().add(0, 14, 0);
        p.teleportTo(level, above.x, above.y, above.z, p.getYRot(), 60F);
        TrialFx.title(p, Component.translatable("title.isekai_mahou.trials.downed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("title.isekai_mahou.trials.downed_sub"), 6, 50, 14);
        int standing = standingPlayers().size();
        broadcast(Component.translatable("message.isekai_mahou.trials.member_downed", p.getDisplayName(), standing, members.size())
                .withStyle(ChatFormatting.RED), null);
        hudDirty = true;
        checkAllDowned();
        return true;
    }

    /** Todos os membros abatidos = derrota (quem está no criativo nunca é abatido, então segura a luta). */
    private void checkAllDowned() {
        if (!phase.combat() || members.isEmpty()) return;
        for (Member m : members.values()) if (!m.downed) return;
        defeat();
    }

    /** Volta todos os abatidos para a luta com {@code frac} da vida (começo de onda / chefe / vitória). */
    private void reviveAll(float frac) {
        List<Member> down = members.values().stream().filter(m -> m.downed).toList();
        int i = 0;
        for (Member m : down) {
            ServerPlayer p = player(m.id);
            if (p == null) continue;
            restoreMode(p, m);
            teleport(p, arrival(i++, Math.max(1, down.size())), 180F);
            p.setHealth(Math.max(1F, p.getMaxHealth() * frac));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            TrialFx.title(p, Component.translatable("title.isekai_mahou.trials.revived").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    Component.empty(), 4, 30, 10);
            TrialFx.warp(level, p.position(), element);
        }
        if (!down.isEmpty()) hudDirty = true;
    }

    private void restoreMode(ServerPlayer p, Member m) {
        m.downed = false;
        if (p.isSpectator()) p.setGameMode(m.prevMode);
        TrialManager.data().setPrevMode(p.getUUID(), -1);
    }

    // =====================================================================================
    //  Derrota
    // =====================================================================================
    private void defeat() {
        phase = Phase.DEFEAT;
        timer = DEFEAT_TICKS;
        epoch++;
        pending.clear();
        purgeMonsters();
        titleAll(Component.translatable("title.isekai_mahou.trials.defeat").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.translatable("title.isekai_mahou.trials.defeat_sub"), 60);
        Vec3 c = centerVec();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.5F, 1.4F);
        bar.setPlayBossMusic(false);
        hudDirty = true;
    }

    private void tickDefeat() {
        if (--timer > 0) return;
        resetToLobby();
    }

    /** Todos de volta à recepção, inteiros; a arena é refeita para a próxima tentativa. */
    private void resetToLobby() {
        phase = Phase.LOBBY;
        wave = -1;
        epoch++;
        mobs.clear();
        bosses.clear();
        bar.removeAllPlayers();
        bar.setProgress(0F);
        hideBounds();
        for (Member m : members.values()) {
            ServerPlayer p = player(m.id);
            if (p == null) continue;
            if (m.downed) restoreMode(p, m);
            teleport(p, layout.spawn(), 180F);
            p.setHealth(p.getMaxHealth());
            p.clearFire();
            clearHarmful(p);
            p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), 14));
        }
        broadcast(Component.translatable("message.isekai_mahou.trials.retry", hostName()).withStyle(ChatFormatting.YELLOW), null);
        rebuildArena();
        refreshPanel();
        hudDirty = true;
    }

    /** Apaga a arena (feitiços podem ter mudado blocos) e constrói de novo, em fatias. */
    private void rebuildArena() {
        arenaReady = false;
        build.clear();
        TrialClearJob clear = TrialClearJob.arenaOnly(index);
        build.add(() -> clear.step(level, 6));
        for (Runnable step : TrialBuilder.arenaSteps(level, index, element, rank)) build.add(() -> { step.run(); return true; });
    }

    // =====================================================================================
    //  Vitória
    // =====================================================================================
    void victory() {
        if (phase == Phase.VICTORY || phase == Phase.CLOSED) return;
        phase = Phase.VICTORY;
        timer = VICTORY_TICKS;
        epoch++;
        pending.clear();
        purgeMonsters();
        reviveAll(1F);
        bar.setPlayBossMusic(false);
        bar.setProgress(1F);
        Vec3 c = centerVec();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.5, c.z, 120, 1.5, 1.5, 1.5, 0.6);
        ElementFx.burst(level, c.add(0, 2, 0), element, 120, 2.0);

        // Portal de saída no medalhão central, com placa.
        victoryExit = arenaCenter.above();
        level.setBlock(victoryExit, TrialsRegistry.EXIT.get().defaultBlockState(), 3);
        victoryLabelId = TrialPanel.spawn(level, Vec3.atBottomCenterOf(victoryExit).add(0, 1.4, 0),
                Component.translatable("panel.isekai_mahou.trials.victory_exit").withStyle(ChatFormatting.AQUA), id, 180);

        for (ServerPlayer p : onlineMembers()) {
            Member m = members.get(p.getUUID());
            p.setHealth(p.getMaxHealth());
            p.clearFire();
            clearHarmful(p);
            TrialFx.title(p, Component.translatable("title.isekai_mahou.trials.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    TrialsApi.title(element, rank), 10, 70, 20);
            Cutscenes.play(p, "portal", p, Component.translatable("title.isekai_mahou.trials.victory"), TrialsApi.title(element, rank),
                    element.color, 50, false);
            if (m != null) TrialRewards.grant(this, p, m);
        }
        for (int k = 0; k < 10; k++) {
            int idx = k;
            schedule(k * 7, () -> launchFirework(idx));
        }
        refreshPanel();
        hudDirty = true;
    }

    private void launchFirework(int k) {
        double a = Math.PI * 2 * k / 10.0;
        Vec3 c = centerVec();
        double d = radius * 0.7;
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        int main = element.color;
        int alt = TrialTheme.bases(element).size() > 1 ? TrialTheme.bases(element).get(1).color : 0xFFD86B;
        FireworkExplosion.Shape shape = k % 3 == 0 ? FireworkExplosion.Shape.STAR : k % 3 == 1 ? FireworkExplosion.Shape.LARGE_BALL
                : FireworkExplosion.Shape.BURST;
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + k % 2, List.of(
                new FireworkExplosion(shape, IntList.of(main, alt), IntList.of(0xFFFFFF), true, true))));
        FireworkRocketEntity fw = new FireworkRocketEntity(level, c.x + Math.cos(a) * d, floorTop() + 0.5, c.z + Math.sin(a) * d, rocket);
        tag(fw, id, ROLE_DECOR);
        level.addFreshEntity(fw);
    }

    private void tickVictory() {
        if (timer % 20 == 0) {
            int s = timer / 20;
            actionAll(Component.translatable("message.isekai_mahou.trials.return_in", s).withStyle(ChatFormatting.GOLD));
            hudDirty = true;
        }
        if (victoryExit != null && age % 4 == 0) {
            Vec3 e = Vec3.atCenterOf(victoryExit);
            for (int i = 0; i < 2; i++) {
                double a = age * 0.2 + i * Math.PI;
                level.sendParticles(ElementFx.dust(element, 1.5F), e.x + Math.cos(a) * 1.2, e.y + (age % 40) / 15.0, e.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
            }
        }
        if (timer == VICTORY_TICKS - 200) {
            for (ServerPlayer p : onlineMembers()) {
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.exit_hint").withStyle(ChatFormatting.AQUA));
            }
        }
        if (age % 20 == 0) collectLoot(false);
        if (--timer > 0) return;
        close("message.isekai_mahou.trials.closed_victory");
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    /**
     * Remove criaturas/chefes VIVOS com um "puf" (vitória, derrota, fim). Quem já está morrendo fica: a animação de
     * morte do chefe e o espólio dele terminam normalmente.
     */
    private void purgeMonsters() {
        List<UUID> all = new ArrayList<>(mobs);
        all.addAll(bosses);
        for (UUID u : all) {
            Entity e = level.getEntity(u);
            if (e == null || !e.isAlive()) continue;
            level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
            ElementFx.burst(level, e.position().add(0, 0.5, 0), element, 10, 0.4);
            e.discard();
        }
        mobs.clear();
        bosses.clear();
        // criaturas "adotadas" ou passageiros que ficaram fora das listas
        AABB box = TrialDimension.aabb(TrialDimension.arenaBox(index));
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof Mob && e.isAlive() && e.getPersistentData().hasUUID(TAG))) {
            if (e.getPersistentData().getInt(ROLE) != ROLE_DECOR) e.discard();
        }
    }

    /**
     * Fecha a instância: manda todos de volta, tira a barra/HUD e entrega a limpeza dos blocos ao gerenciador.
     * {@code reasonKey} = mensagem para quem ainda estava no grupo (null = sem mensagem).
     */
    void close(@Nullable String reasonKey) {
        if (phase == Phase.CLOSED) return;
        if (phase == Phase.VICTORY) collectLoot(true);   // o que ainda estava no chão vai junto para casa
        phase = Phase.CLOSED;
        epoch++;
        tasks.clear();
        pending.clear();
        purgeMonsters();
        List<UUID> ids = new ArrayList<>(members.keySet());
        Map<UUID, Member> old = new LinkedHashMap<>(members);
        members.clear();
        bar.removeAllPlayers();
        hideBounds();
        for (UUID u : ids) {
            ServerPlayer p = player(u);
            if (p == null) continue;
            Member m = old.get(u);
            if (m != null && m.downed) restoreMode(p, m);
            TrialNetwork.clear(p);
            if (reasonKey != null && !reasonKey.equals("message.isekai_mahou.trials.closed_victory")) {
                p.sendSystemMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.YELLOW));
            }
            if (p.level() == level) TrialManager.sendHome(p, element);
        }
        TrialPanel.remove(level, panelId);
        TrialPanel.remove(level, exitLabelId);
        TrialPanel.remove(level, victoryLabelId);
        TrialManager.retire(this);
    }

    /** Servidor desligando: só devolve o modo de jogo de quem estava abatido e solta a barra (sem teleportes). */
    void shutdown() {
        if (phase == Phase.CLOSED) return;
        phase = Phase.CLOSED;
        epoch++;
        tasks.clear();
        for (Member m : members.values()) {
            ServerPlayer p = player(m.id);
            if (p != null && m.downed) restoreMode(p, m);
        }
        members.clear();
        bar.removeAllPlayers();
        hideBounds();
        TrialManager.retire(this);
    }

    // =====================================================================================
    //  Painel, barra e HUD
    // =====================================================================================
    private Component panelText() {
        MutableComponent t = Component.empty();
        t.append(Component.literal("✦ ").withColor(element.color))
                .append(TrialsApi.title(element, rank).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" ✦").withColor(element.color));
        if (challenge()) t.append("\n").append(Component.translatable("panel.isekai_mahou.trials.challenge").withStyle(ChatFormatting.AQUA));
        t.append("\n").append(Component.translatable("panel.isekai_mahou.trials.waves", waves, bossLine()).withStyle(ChatFormatting.GRAY));
        t.append("\n\n").append(Component.translatable("panel.isekai_mahou.trials.party", members.size(), MAX_PARTY).withStyle(ChatFormatting.GOLD));
        for (Member m : members.values()) {
            t.append("\n");
            MutableComponent line = Component.literal((isHost(m.id) ? "★ " : "• ") + m.name)
                    .withStyle(isHost(m.id) ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            if (!m.eligible) line.append(Component.literal(" ")).append(Component.translatable("panel.isekai_mahou.trials.assist").withStyle(ChatFormatting.GRAY));
            else if (m.clearedBefore) line.append(Component.literal(" ")).append(Component.translatable("panel.isekai_mahou.trials.repeat").withStyle(ChatFormatting.AQUA));
            t.append(line);
        }
        t.append("\n\n");
        switch (phase) {
            case LOBBY -> t.append(Component.translatable("panel.isekai_mahou.trials.howto_start", hostName()).withStyle(ChatFormatting.GREEN))
                    .append("\n").append(Component.translatable("panel.isekai_mahou.trials.howto_invite").withStyle(ChatFormatting.GRAY))
                    .append("\n").append(Component.translatable("panel.isekai_mahou.trials.howto_leave").withStyle(ChatFormatting.GRAY));
            case COUNTDOWN -> t.append(Component.translatable("panel.isekai_mahou.trials.starting").withStyle(ChatFormatting.GOLD));
            default -> t.append(Component.translatable("panel.isekai_mahou.trials.running").withStyle(ChatFormatting.RED));
        }
        return t;
    }

    private Component bossLine() {
        if (rank <= 4) return Component.translatable("panel.isekai_mahou.trials.boss_avatar");
        if (rank == 6) return Component.translatable("panel.isekai_mahou.trials.boss_sentinels");
        String g = TrialTheme.guardian(element);
        Component gName = g != null && TrialTheme.type(g).isPresent() ? TrialSpawner.bossName(g) : Component.translatable("panel.isekai_mahou.trials.boss_avatar");
        if (rank == 5) return element.isFusion() ? Component.translatable("panel.isekai_mahou.trials.boss_avatar") : gName;
        return Component.translatable("panel.isekai_mahou.trials.boss_pair", gName);
    }

    void refreshPanel() {
        TrialPanel.update(level, panelId, panelText());
    }

    private void refreshBar() {
        Component name = TrialsApi.title(element, rank);
        Component text = switch (phase) {
            case INTRO -> Component.translatable("bossbar.isekai_mahou.trials.intro", name);
            case WAVE -> Component.translatable("bossbar.isekai_mahou.trials.wave", name, wave + 1, waves, mobs.size() + pending.size());
            case BREAK -> wave >= waves - 1
                    ? Component.translatable("bossbar.isekai_mahou.trials.boss_soon", name, Math.max(1, (timer + 19) / 20))
                    : Component.translatable("bossbar.isekai_mahou.trials.break", name, wave + 2, Math.max(1, (timer + 19) / 20));
            case BOSS_INTRO, BOSS -> Component.translatable("bossbar.isekai_mahou.trials.boss", name, bossTitle);
            case VICTORY -> Component.translatable("bossbar.isekai_mahou.trials.victory", name);
            case DEFEAT -> Component.translatable("bossbar.isekai_mahou.trials.defeat", name);
            default -> name;
        };
        bar.setName(text);
        if (phase == Phase.WAVE || phase == Phase.BREAK) {
            float frac = phase == Phase.BREAK || waveTotal == 0 ? 1F : (waveTotal - mobs.size() - pending.size()) / (float) waveTotal;
            bar.setProgress(Mth.clamp((wave + frac) / (waves + 1F), 0F, 1F));
        }
        for (ServerPlayer p : onlineMembers()) {
            if (phase.arena() && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.isRemoved() || p.level() != level || !members.containsKey(p.getUUID())) bar.removePlayer(p);
        }
    }

    private void syncHud() {
        hudDirty = false;
        int downed = (int) members.values().stream().filter(m -> m.downed).count();
        int enemies = mobs.size() + pending.size() + bosses.size();
        int seconds = switch (phase) {
            case COUNTDOWN, BREAK, VICTORY, DEFEAT -> Math.max(0, (timer + 19) / 20);
            default -> 0;
        };
        BlockPos target = switch (phase) {
            case LOBBY, COUNTDOWN -> layout.altar();
            case VICTORY -> victoryExit;
            default -> null;
        };
        int total = phase == Phase.WAVE ? waveTotal : phase == Phase.BOSS || phase == Phase.BOSS_INTRO ? Math.max(1, bosses.size()) : 0;
        for (ServerPlayer p : onlineMembers()) {
            Member m = members.get(p.getUUID());
            TrialNetwork.send(p, new TrialNetwork.TrialHud(true, element.id, rank, phase.ordinal(), Math.max(0, wave + 1), waves, enemies,
                    total, downed, members.size(), seconds, challenge(), m != null && m.downed, target != null,
                    target == null ? BlockPos.ZERO : target));
        }
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    void teleport(ServerPlayer p, Vec3 to, float yaw) {
        p.teleportTo(level, to.x, to.y, to.z, yaw, p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
    }

    /** Tira só os efeitos ruins (queimadura, veneno, escuridão dos perigos...); poções boas ficam. */
    static void clearHarmful(ServerPlayer p) {
        for (MobEffectInstance eff : List.copyOf(p.getActiveEffects())) {
            if (eff.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(eff.getEffect());
        }
    }

    /**
     * Espólio sem dono caído na arena (drops do chefe e das criaturas, orbes de XP) vai direto para o membro mais
     * perto — ninguém perde o núcleo do Guardião por sair pelo portal antes de catar tudo. Itens largados por
     * jogadores ficam com o dono. {@code all=false}: só o que já está no chão há 3 s (dá para ver cair).
     */
    private void collectLoot(boolean all) {
        List<ServerPlayer> here = new ArrayList<>();
        for (ServerPlayer p : onlineMembers()) if (p.level() == level && !p.hasDisconnected()) here.add(p);
        if (here.isEmpty()) return;
        AABB box = TrialDimension.aabb(TrialDimension.arenaBox(index));
        boolean any = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, i -> i.isAlive() && !i.getItem().isEmpty())) {
            if ((!all && item.getAge() < 60) || TrialClearJob.ownerId(item) != null) continue;
            ServerPlayer to = nearest(here, item.position());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.3, item.getZ(), 8, 0.15, 0.2, 0.15, 0.03);
            Rewards.give(to, item.getItem());
            item.discard();
            any = true;
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) {
            if (!all && orb.tickCount < 60) continue;
            CompoundTag t = new CompoundTag();
            orb.addAdditionalSaveData(t);
            nearest(here, orb.position()).giveExperiencePoints(orb.getValue() * Math.max(1, t.getInt("Count")));
            orb.discard();
            any = true;
        }
        if (any) soundAll(SoundEvents.ITEM_PICKUP, 0.6F, 1.4F);
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, Vec3 at) {
        ServerPlayer best = players.get(0);
        for (ServerPlayer p : players) if (p.distanceToSqr(at) < best.distanceToSqr(at)) best = p;
        return best;
    }
}
