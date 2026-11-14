package com.renanpontes.isekaimahou.feature.dark_self;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.dark_self.entity.DarkSelfEntity;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * O ciclo da <b>Sombra</b> (Dark Self) — "no nível 100 a sua sombra some e você luta contra você mesmo para passar do 100".
 * <ol>
 *   <li><b>Trava</b>: {@link Progression#darkSelfGate} prende o nível no 100 (o XP vai para o banco). Saves antigos já
 *       acima do 100 ganham a flag na hora (não travam).</li>
 *   <li><b>Gatilho</b> (fácil de acontecer): andando, com a mana CHEIA, longe de cidades/lugares, fora do Modo Paz e de
 *       arenas — a cada {@link #CHECK_EVERY} ticks há {@link #CHANCE} de chance, e garantido depois de
 *       {@link #GUARANTEE_CHECKS} checagens válidas (~2 min).</li>
 *   <li><b>A sombra some</b>: os clientes param de desenhar o círculo de sombra do jogador, a tela escurece de leve e
 *       toca um eco. Em PRIMEIRA PESSOA o dono vê o presságio ({@code client/DarkSelfOmen}): a silhueta dele no chão,
 *       esticada pelo sol/lua, que depois de {@link #ATTACH_MIN}–{@link #ATTACH_MAX} ticks se SOLTA dos pés, desliza até
 *       o ponto do surgimento ({@link #omenSpot}), ondula e afunda; {@link #DETACH_TO_RISE} ticks depois a Sombra sai do
 *       chão ali.</li>
 *   <li><b>Duelo</b> ({@link DarkSelfEntity}). Vitória: sombra volta, trava some, banco de XP aplicado, cutscene curta e
 *       título. Derrota/fuga: ela se desfaz e pode voltar depois de {@link #FADE_COOLDOWN} ticks.</li>
 * </ol>
 * Estado em memória (por sessão do servidor); a única coisa salva é a flag {@link Progression#DARK_SELF_FLAG}.
 */
public final class DarkSelfService {
    /** Checagem do gatilho (ticks do jogador). */
    static final int CHECK_EVERY = 40;
    /** Chance por checagem válida (≈ 1 a cada 50 s andando com tudo certo). */
    static final float CHANCE = 0.04F;
    /** Checagens válidas até ser garantido (60 × 2 s = 2 min). */
    static final int GUARANTEE_CHECKS = 60;
    /** Tempo em que a silhueta fica presa aos pés (depois de a sombra sumir) antes de se soltar. */
    static final int ATTACH_MIN = 60, ATTACH_MAX = 130;
    /** Da silhueta se soltar até a Sombra surgir (deslizar ~25 ticks + afundar ~30 no cliente). */
    static final int DETACH_TO_RISE = 60;
    /** Espera depois de a Sombra se desfazer (morte, fuga, Modo Paz). */
    static final int FADE_COOLDOWN = 3 * 60 * 20;
    /** Espera depois de o "sumiço" ser cancelado (entrou numa cidade antes de ela surgir). */
    static final int CANCEL_COOLDOWN = 30 * 20;
    /** Distância mínima até a borda de qualquer lugar nomeado (cidade, vila, covil...). */
    static final int PLACE_MARGIN = 48;
    /** Título dado na vitória (lista de títulos da Reputação/Renome). */
    public static final String TITLE_KEY = "title.isekai_mahou.dark_self";
    /** Contador de feito: vezes que venceu a própria Sombra. */
    public static final String COUNTER = "feat.dark_self";

    public enum Phase { IDLE, VANISHED, DUEL }

    public enum Reason { DIED, FLED, LEFT, PEACE, RESET }

    static final class State {
        Phase phase = Phase.IDLE;
        long riseAt;
        long detachAt;
        @Nullable Vec3 spot;
        long cooldownUntil;
        int checks;
        boolean forced;
        @Nullable UUID entity;
        double lastX = Double.NaN, lastZ = Double.NaN;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Onde há duelo agora (id da Sombra → dimensão + posição): o terreno ali não é destruído por magias. */
    private record Spot(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, double x, double z) {}
    private static final Map<UUID, Spot> SPOTS = new ConcurrentHashMap<>();
    /** Raio sem destruição de blocos em volta da Sombra. */
    static final int NO_GRIEF_RADIUS = 40;

    private DarkSelfService() {}

    static void init(IEventBus bus) {
        bus.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 10 == 0) tick(sp);
        });
        bus.addListener(SpellCastEvent.Post.class, DarkSelfService::onCast);
        bus.addListener(LivingIncomingDamageEvent.class, DarkSelfService::onDamage);
        // LOWEST e sem os cancelados: se outro módulo salvou o jogador da morte, o duelo continua.
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ownerGone(sp, Reason.DIED);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ownerGone(sp, Reason.LEFT);
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ownerGone(sp, Reason.FLED);
        });
        bus.addListener(PlayerEvent.StartTracking.class, e -> {
            if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target && hidden(target)) {
                DarkSelfNetwork.sendTo(viewer, target.getUUID(), true);
            }
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            STATES.clear();
            SPOTS.clear();
        });
        // "Não quebra blocos": nenhuma magia (dela ou do dono) abre crateras na área do duelo.
        Destruction.addProtection((level, pos) -> {
            if (SPOTS.isEmpty()) return false;
            for (Spot s : SPOTS.values()) {
                if (s.dim() != level.dimension()) continue;
                double dx = pos.getX() + 0.5 - s.x(), dz = pos.getZ() + 0.5 - s.z();
                if (dx * dx + dz * dz < NO_GRIEF_RADIUS * NO_GRIEF_RADIUS) return true;
            }
            return false;
        });
    }

    /** A Sombra avisa onde está (a cada meio segundo) — ou que sumiu ({@code alive=false}). */
    public static void markSpot(Entity shadow, boolean alive) {
        if (alive) SPOTS.put(shadow.getUUID(), new Spot(shadow.level().dimension(), shadow.getX(), shadow.getZ()));
        else SPOTS.remove(shadow.getUUID());
    }

    // =====================================================================================
    //  Consulta
    // =====================================================================================
    static State state(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), u -> new State());
    }

    /** A sombra do jogador está sumida (esperando a Sombra ou em duelo)? */
    public static boolean hidden(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s != null && s.phase != Phase.IDLE;
    }

    /** Esta entidade é a Sombra do duelo atual do jogador? */
    public static boolean isDuelEntity(ServerPlayer owner, UUID entity) {
        State s = STATES.get(owner.getUUID());
        return s != null && s.phase == Phase.DUEL && entity.equals(s.entity);
    }

    public static boolean passed(MagicData d) {
        return d.hasFlag(Progression.DARK_SELF_FLAG);
    }

    private static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    // =====================================================================================
    //  Tick do jogador
    // =====================================================================================
    private static void tick(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        // Saves antigos já acima do 100: não travam (marca para sempre).
        if (!passed(d) && d.level() > Progression.CIRCLE_TEN_LEVEL) d.setFlag(Progression.DARK_SELF_FLAG, true);
        State s = state(sp);
        long now = now(sp.server);
        switch (s.phase) {
            case IDLE -> {
                if (sp.tickCount % CHECK_EVERY != 0) return;
                boolean moved = moved(sp, s);
                if (passed(d) || d.level() < Progression.CIRCLE_TEN_LEVEL) {
                    s.checks = 0;
                    return;
                }
                Hints.show(sp, "dark_self.cap");
                if (now < s.cooldownUntil || !eligible(sp, d, moved)) return;
                s.checks++;
                if (s.checks >= GUARANTEE_CHECKS || sp.getRandom().nextFloat() < CHANCE) vanish(sp, s, false);
            }
            case VANISHED -> {
                if (!s.forced && !stillOk(sp)) {
                    cancel(sp, s);
                    return;
                }
                if (s.spot == null) {
                    if (now >= s.detachAt) detach(sp, s);
                } else if (now >= s.riseAt) {
                    rise(sp, s);
                }
            }
            case DUEL -> {
                // A Sombra cuida de si mesma; aqui só se ela sumiu sem avisar (chunk descarregado, /kill de outro jeito).
                if (s.entity == null || findEntity(sp.server, s.entity) == null) {
                    if (++s.checks > 10) endDuel(sp, s, Reason.LEFT);
                } else {
                    s.checks = 0;
                }
            }
        }
    }

    /** Andou pelo menos 1,5 bloco desde a última checagem? */
    private static boolean moved(ServerPlayer sp, State s) {
        double dx = sp.getX() - s.lastX, dz = sp.getZ() - s.lastZ;
        s.lastX = sp.getX();
        s.lastZ = sp.getZ();
        return !Double.isNaN(dx) && dx * dx + dz * dz >= 1.5 * 1.5;
    }

    /** As condições do gatilho natural. */
    static boolean eligible(ServerPlayer sp, MagicData d, boolean moved) {
        return moved && survival(sp) && sp.onGround() && !sp.isPassenger() && !sp.isFallFlying() && !sp.isInWater()
                && sp.getLastHurtByMob() == null && sp.hurtTime == 0
                && d.mana() >= d.maxMana() - 0.5F
                && stillOk(sp);
    }

    /** Condições que valem até a Sombra surgir: fora do Modo Paz, longe de lugares nomeados e de arenas. */
    static boolean stillOk(ServerPlayer sp) {
        return survival(sp) && sp.isAlive() && !PeaceMode.isOn(sp) && farFromPlaces(sp)
                && ArenaBounds.at(sp.level(), sp.position()).isEmpty();
    }

    private static boolean survival(ServerPlayer sp) {
        return !sp.isSpectator() && !sp.isCreative();
    }

    /** Longe ({@link #PLACE_MARGIN} blocos) de qualquer cidade/vila/covil registrado em {@link Places}. */
    static boolean farFromPlaces(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos p = sp.blockPosition();
        for (Places.Place place : Places.all(level)) {
            if (place.dim() != level.dimension()) continue;
            BoundingBox b = place.box();
            int dx = Math.max(0, Math.max(b.minX() - p.getX(), p.getX() - b.maxX()));
            int dz = Math.max(0, Math.max(b.minZ() - p.getZ(), p.getZ() - b.maxZ()));
            if (dx * dx + dz * dz < PLACE_MARGIN * PLACE_MARGIN) return false;
        }
        return true;
    }

    // =====================================================================================
    //  Fases
    // =====================================================================================
    /** A sombra some (eco + escurecer) e a Sombra é agendada. {@code forced}: comando (ignora as condições). */
    static void vanish(ServerPlayer sp, State s, boolean forced) {
        long now = now(sp.server);
        s.phase = Phase.VANISHED;
        s.forced = forced;
        s.checks = 0;
        s.spot = null;
        s.detachAt = now + (forced ? 30 : ATTACH_MIN + sp.getRandom().nextInt(ATTACH_MAX - ATTACH_MIN + 1));
        s.riseAt = Long.MAX_VALUE;
        DarkSelfNetwork.broadcast(sp, true);
        DarkSelfNetwork.sendOmen(sp, false, Vec3.ZERO);
        sp.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.9F, 0.55F);
        Hints.show(sp, "dark_self.vanish");
    }

    /** A silhueta se solta dos pés: escolhe o ponto onde ela afunda (e a Sombra surge) e avisa o cliente do dono. */
    static void detach(ServerPlayer sp, State s) {
        s.spot = omenSpot(sp);
        s.riseAt = now(sp.server) + DETACH_TO_RISE;
        DarkSelfNetwork.sendOmen(sp, true, s.spot);
        sp.playNotifySound(SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.AMBIENT, 0.8F, 0.6F);
    }

    /** A Sombra sai do chão onde a silhueta afundou. */
    static void rise(ServerPlayer sp, State s) {
        ServerLevel level = sp.serverLevel();
        DarkSelfEntity e = DarkSelfRegistry.DARK_SELF.get().create(level);
        if (e == null) {
            cancel(sp, s);
            return;
        }
        // O ponto do presságio; se o jogador já correu para longe dele, um novo atrás do jogador.
        Vec3 pos = s.spot != null && s.spot.distanceToSqr(sp.position()) < 20 * 20 ? s.spot : risePos(sp);
        s.spot = null;
        float yaw = (float) Math.toDegrees(Math.atan2(-(sp.getX() - pos.x), sp.getZ() - pos.z));
        e.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.bind(sp);
        level.addFreshEntity(e);
        s.phase = Phase.DUEL;
        s.forced = false;
        s.checks = 0;
        s.entity = e.getUUID();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.7F, 1.35F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.dark_self.rise").withColor(0xB58CFF), true);
        Hints.show(sp, "dark_self.rise");
    }

    /** Uns 3 blocos atrás do jogador, no chão (ou no próprio jogador, se não achar lugar). */
    private static Vec3 risePos(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        return groundSpot(sp, look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : look.normalize().scale(-1), 3.0);
    }

    /**
     * Para onde a silhueta desliza: na direção da sombra (pelo sol/lua) se ela estiver mais ou menos à frente do
     * jogador — assim ele VÊ a sombra ir embora em primeira pessoa; senão (luz a pino ou sombra para trás), à frente.
     */
    static Vec3 omenSpot(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 shadow = shadowVector(sp.level(), 1F);
        Vec3 dir = shadow.lengthSqr() > 0.3 * 0.3 && shadow.normalize().dot(look) > -0.2 ? shadow.normalize() : look;
        return groundSpot(sp, dir, 4.0);
    }

    /**
     * Sombra de quem está de pé, pela luz do céu: vetor horizontal (só X — o sol anda de leste a oeste) apontando para
     * LONGE da luz, com comprimento = tangente do ângulo da luz em relação ao zênite (até 3). De noite usa a lua; luz
     * no horizonte, dimensão sem ciclo (Nether/End) ou sol/lua a pino → {@link Vec3#ZERO} (sombra reto para baixo).
     * Usado pelo servidor (ponto do presságio) e pelo cliente (desenho da silhueta).
     */
    public static Vec3 shadowVector(net.minecraft.world.level.Level level, float partialTick) {
        if (level.dimensionType().hasFixedTime()) return Vec3.ZERO;
        double a = level.getSunAngle(partialTick);
        double lx = -Math.sin(a), ly = Math.cos(a); // direção do sol no céu (leste = +X)
        if (ly < 0) { // noite: a lua fica do lado oposto
            lx = -lx;
            ly = -ly;
        }
        if (ly < 0.08) return Vec3.ZERO;
        double len = Math.min(3.0, Math.abs(lx) / ly);
        return new Vec3(-Math.signum(lx) * len, 0, 0);
    }

    /** Chão firme com 2 blocos livres em cima, {@code maxDist}→1 blocos na direção {@code dir} (ou os pés do jogador). */
    private static Vec3 groundSpot(ServerPlayer sp, Vec3 dir, double maxDist) {
        ServerLevel level = sp.serverLevel();
        for (double dist = maxDist; dist >= 1.0; dist -= 1.0) {
            BlockPos base = BlockPos.containing(sp.getX() + dir.x * dist, sp.getY() + 2, sp.getZ() + dir.z * dist);
            for (int dy = 0; dy < 6; dy++) {
                BlockPos feet = base.below(dy);
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return sp.position();
    }

    /** O "sumiço" foi cancelado antes da Sombra surgir (entrou numa cidade, ligou o Modo Paz...). */
    static void cancel(ServerPlayer sp, State s) {
        s.phase = Phase.IDLE;
        s.spot = null;
        s.forced = false;
        s.entity = null;
        s.cooldownUntil = now(sp.server) + CANCEL_COOLDOWN;
        DarkSelfNetwork.broadcast(sp, false);
    }

    /** Fim do duelo sem vitória: sombra volta, a Sombra pode voltar depois. */
    private static void endDuel(ServerPlayer sp, State s, Reason reason) {
        s.phase = Phase.IDLE;
        s.forced = false;
        s.entity = null;
        s.checks = 0;
        s.cooldownUntil = now(sp.server) + (reason == Reason.RESET ? 0 : FADE_COOLDOWN);
        DarkSelfNetwork.broadcast(sp, false);
        String key = switch (reason) {
            case DIED -> "message.isekai_mahou.dark_self.fade_died";
            case PEACE -> "message.isekai_mahou.dark_self.fade_peace";
            case RESET -> "message.isekai_mahou.dark_self.reset";
            default -> "message.isekai_mahou.dark_self.fade";
        };
        // Morte: no chat (a barra de ação some na tela de morte); o resto: barra de ação, discreto.
        sp.displayClientMessage(Component.translatable(key).withColor(0x9A86B8), reason != Reason.DIED);
    }

    /** O dono morreu, saiu ou mudou de dimensão. */
    private static void ownerGone(ServerPlayer sp, Reason reason) {
        State s = STATES.get(sp.getUUID());
        if (s == null || s.phase == Phase.IDLE) return;
        if (s.phase == Phase.VANISHED) {
            cancel(sp, s);
            return;
        }
        Entity e = s.entity == null ? null : findEntity(sp.server, s.entity);
        if (e instanceof DarkSelfEntity ds) ds.vanishQuietly();
        endDuel(sp, s, reason);
    }

    /** Chamado pela Sombra quando ela se desfaz (longe demais, Modo Paz...). */
    public static void onFade(MinecraftServer server, UUID owner, UUID entity, Reason reason) {
        State s = STATES.get(owner);
        if (s == null || s.phase != Phase.DUEL || !entity.equals(s.entity)) return;
        ServerPlayer sp = server.getPlayerList().getPlayer(owner);
        if (sp == null) {
            s.phase = Phase.IDLE;
            s.entity = null;
            s.cooldownUntil = now(server) + FADE_COOLDOWN;
            return;
        }
        endDuel(sp, s, reason);
    }

    /** Vitória: sombra volta, a trava do 100 some, o XP guardado é aplicado, cutscene curta e título. */
    public static void onVictory(ServerPlayer sp, UUID entity) {
        State s = state(sp);
        if (s.phase != Phase.DUEL || !entity.equals(s.entity)) return;
        s.phase = Phase.IDLE;
        s.entity = null;
        s.checks = 0;
        DarkSelfNetwork.broadcast(sp, false);
        grantPass(sp, true);
    }

    /** Libera o nível (vitória ou comando). {@code ceremony}: cutscene, título e mensagens. */
    static void grantPass(ServerPlayer sp, boolean ceremony) {
        MagicData d = MagicApi.get(sp);
        boolean first = !passed(d);
        d.setFlag(Progression.DARK_SELF_FLAG, true);
        if (ceremony) {
            MagicApi.addCounter(sp, COUNTER, 1);
            Cutscenes.play(sp, "ascension", sp, Component.translatable("message.isekai_mahou.dark_self.victory_title"),
                    Component.translatable("message.isekai_mahou.dark_self.victory_sub"), 0x8A5CFF, 60, false);
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.dark_self.victory")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
            if (first) Reputation.grantTitle(sp, TITLE_KEY);
        }
        // No Círculo 9 o limite ainda é o do Círculo: o banco fica para a Ascensão.
        if (first && d.circle() >= Progression.MAX_CIRCLE) MagicApi.releaseXpBank(sp);
    }

    // =====================================================================================
    //  Eventos do duelo
    // =====================================================================================
    /** O dono conjurou: a Sombra guarda a magia (para devolvê-la) e às vezes esquiva. */
    private static void onCast(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer sp)) return;
        State s = STATES.get(sp.getUUID());
        if (s == null || s.phase != Phase.DUEL || s.entity == null) return;
        if (sp.serverLevel().getEntity(s.entity) instanceof DarkSelfEntity ds) {
            ds.onOwnerCast(sp, e.getContext().recipe(), e.getContext().element(), e.getContext().spell());
        }
    }

    /** As magias da Sombra só ferem o dono dela (ninguém mais entra no duelo). */
    private static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof DarkSelfEntity ds && !ds.isOwner(e.getEntity())) e.setCanceled(true);
    }

    @Nullable
    static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    // =====================================================================================
    //  Comandos
    // =====================================================================================
    /** Comando "spawn": a sombra some agora, o presságio se solta em 1,5 s e a Sombra surge ~5 s depois (ignora as condições). */
    static boolean forceSpawn(ServerPlayer sp) {
        State s = state(sp);
        if (s.phase == Phase.DUEL) return false;
        vanish(sp, s, true);
        return true;
    }

    /** Comando "reset": tira a flag e acaba com qualquer Sombra/sumiço em andamento. */
    static void reset(ServerPlayer sp) {
        State s = state(sp);
        if (s.phase == Phase.DUEL && s.entity != null && findEntity(sp.server, s.entity) instanceof DarkSelfEntity ds) {
            ds.vanishQuietly();
        }
        STATES.remove(sp.getUUID());
        DarkSelfNetwork.broadcast(sp, false);
        MagicApi.get(sp).setFlag(Progression.DARK_SELF_FLAG, false);
    }

    /** Comando "pass": libera o nível sem lutar (sem cutscene). */
    static void pass(ServerPlayer sp) {
        State s = state(sp);
        if (s.phase == Phase.DUEL && s.entity != null && findEntity(sp.server, s.entity) instanceof DarkSelfEntity ds) {
            ds.vanishQuietly();
        }
        s.phase = Phase.IDLE;
        s.entity = null;
        DarkSelfNetwork.broadcast(sp, false);
        grantPass(sp, false);
    }

    /** Texto do comando "status". */
    static Component status(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        State s = state(sp);
        long now = now(sp.server);
        long cd = Math.max(0, s.cooldownUntil - now) / 20;
        String yes = "§a✔", no = "§c✘";
        return Component.translatable("command.isekai_mahou.dark_self.status", sp.getName(), d.level(), d.levelCap(),
                passed(d) ? yes : no, s.phase.name(), s.checks, GUARANTEE_CHECKS, cd, d.xpBank(),
                d.mana() >= d.maxMana() - 0.5F ? yes : no, farFromPlaces(sp) ? yes : no, PeaceMode.isOn(sp) ? no : yes,
                ArenaBounds.at(sp.level(), sp.position()).isEmpty() ? yes : no, survival(sp) ? yes : no);
    }
}
