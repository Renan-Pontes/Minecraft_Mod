package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Party.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Duelos 1×1: pedido/aceite, contagem de 3 s, luta de até 3 min num círculo de {@link #RADIUS} blocos. Ninguém morre:
 * o golpe que mataria deixa o perdedor com 25% da vida. Sair do círculo, deslogar ou morrer de outra forma = derrota.
 * Durante o duelo o PvP vale entre os dois mesmo com o PvP do servidor desligado (e mesmo sendo do mesmo grupo).
 */
public final class DuelService {
    public static final int COUNTDOWN_TICKS = 60;
    public static final int FIGHT_TICKS = 3600;
    public static final double RADIUS = 32.0;
    static final int PHASE_NONE = 0, PHASE_COUNTDOWN = 1, PHASE_FIGHT = 2;

    static final class Duel {
        final UUID a, b;
        final String nameA, nameB;
        final ResourceKey<Level> dim;
        final Vec3 center;
        final int start;
        boolean over;

        Duel(ServerPlayer a, ServerPlayer b, int now) {
            this.a = a.getUUID();
            this.b = b.getUUID();
            this.nameA = a.getGameProfile().getName();
            this.nameB = b.getGameProfile().getName();
            this.dim = a.level().dimension();
            this.center = a.position().add(b.position()).scale(0.5);
            this.start = now;
        }

        boolean involves(UUID id) { return a.equals(id) || b.equals(id); }

        UUID other(UUID id) { return a.equals(id) ? b : a; }

        String nameOf(UUID id) { return a.equals(id) ? nameA : nameB; }

        int phase(int now) { return now - start < COUNTDOWN_TICKS ? PHASE_COUNTDOWN : PHASE_FIGHT; }

        /** Ticks restantes da fase atual. */
        int ticksLeft(int now) {
            int t = now - start;
            return t < COUNTDOWN_TICKS ? COUNTDOWN_TICKS - t : Math.max(0, COUNTDOWN_TICKS + FIGHT_TICKS - t);
        }
    }

    private static final List<Duel> DUELS = new ArrayList<>();

    // =====================================================================================
    //  Consulta
    // =====================================================================================
    @Nullable
    static Duel duelOf(UUID player) {
        for (Duel d : DUELS) if (!d.over && d.involves(player)) return d;
        return null;
    }

    public static boolean inDuel(UUID player) {
        return duelOf(player) != null;
    }

    /** Duelando entre si E já na fase de luta. */
    public static boolean dueling(UUID a, UUID b) {
        Duel d = duelOf(a);
        return d != null && d.other(a).equals(b) && d.phase(serverTicks()) == PHASE_FIGHT;
    }

    /** Duelando entre si ainda na contagem (ninguém pode bater). */
    static boolean countingDown(UUID a, UUID b) {
        Duel d = duelOf(a);
        return d != null && d.other(a).equals(b) && d.phase(serverTicks()) == PHASE_COUNTDOWN;
    }

    private static MinecraftServer server;

    private static int serverTicks() {
        return server == null ? 0 : server.getTickCount();
    }

    // =====================================================================================
    //  Pedido / aceite
    // =====================================================================================
    public static Result request(ServerPlayer from, ServerPlayer to) {
        server = from.server;
        if (from == to) return Result.SELF;
        if (!from.isAlive() || !to.isAlive()) return Result.DEAD;
        if (from.level() != to.level() || from.distanceTo(to) > Party.INTERACT_RANGE * 2) return Result.TOO_FAR;
        if (inDuel(from.getUUID()) || TradeService.isTrading(from.getUUID())) return Result.BUSY;
        if (inDuel(to.getUUID())) return Result.TARGET_BUSY;
        if (Requests.has(from.server, from.getUUID(), to.getUUID(), Requests.Kind.DUEL)) return accept(from, to.getUUID());
        Requests.send(from, to, Requests.Kind.DUEL);
        return Result.OK;
    }

    public static Result accept(ServerPlayer player, @Nullable UUID from) {
        server = player.server;
        Requests.Request req;
        if (from != null) {
            req = Requests.take(player.server, player.getUUID(), from, Requests.Kind.DUEL);
        } else {
            Requests.Request latest = Requests.latest(player.server, player.getUUID(), Requests.Kind.DUEL);
            req = latest == null ? null : Requests.take(player.server, player.getUUID(), latest.from(), Requests.Kind.DUEL);
        }
        if (req == null) return Result.NO_REQUEST;
        ServerPlayer other = player.server.getPlayerList().getPlayer(req.from());
        if (other == null) return Result.OFFLINE;
        if (!other.isAlive() || !player.isAlive()) return Result.DEAD;
        if (other.level() != player.level() || other.distanceTo(player) > Party.INTERACT_RANGE * 2) return Result.TOO_FAR;
        if (inDuel(player.getUUID())) return Result.BUSY;
        if (inDuel(other.getUUID())) return Result.TARGET_BUSY;
        start(other, player);
        return Result.OK;
    }

    public static Result decline(ServerPlayer player, @Nullable UUID from) {
        Requests.Request req;
        if (from != null) {
            req = Requests.take(player.server, player.getUUID(), from, Requests.Kind.DUEL);
        } else {
            Requests.Request latest = Requests.latest(player.server, player.getUUID(), Requests.Kind.DUEL);
            req = latest == null ? null : Requests.take(player.server, player.getUUID(), latest.from(), Requests.Kind.DUEL);
        }
        if (req == null) return Result.NO_REQUEST;
        ServerPlayer other = player.server.getPlayerList().getPlayer(req.from());
        if (other != null) {
            other.displayClientMessage(Component.translatable("social.isekai_mahou.duel.declined", player.getGameProfile().getName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return Result.OK;
    }

    private static void start(ServerPlayer a, ServerPlayer b) {
        Duel d = new Duel(a, b, a.server.getTickCount());
        DUELS.add(d);
        Requests.forget(a.getUUID(), Requests.Kind.DUEL);
        Requests.forget(b.getUUID(), Requests.Kind.DUEL);
        Component vs = Component.translatable("social.isekai_mahou.duel.start", d.nameA, d.nameB).withColor(0xFF9A6B);
        for (ServerPlayer p : List.of(a, b)) {
            p.sendSystemMessage(vs);
            p.sendSystemMessage(Component.translatable("social.isekai_mahou.duel.rules", (int) RADIUS).withStyle(ChatFormatting.GRAY));
        }
        SocialSync.markState(a.getUUID());
        SocialSync.markState(b.getUUID());
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(MinecraftServer srv) {
        server = srv;
        if (DUELS.isEmpty()) return;
        int now = srv.getTickCount();
        for (Duel d : new ArrayList<>(DUELS)) {
            if (d.over) continue;
            ServerPlayer a = srv.getPlayerList().getPlayer(d.a);
            ServerPlayer b = srv.getPlayerList().getPlayer(d.b);
            if (a == null || b == null) {
                // Saiu do servidor: quem ficou vence
                end(srv, d, a != null ? d.a : d.b, false);
                continue;
            }
            int t = now - d.start;
            if (t < COUNTDOWN_TICKS) {
                if (t % 20 == 0) countdown(a, b, 3 - t / 20);
            } else if (t == COUNTDOWN_TICKS) {
                fight(a, b);
            }
            boolean outA = outside(d, a), outB = outside(d, b);
            if (outA || outB) {
                UUID winner = outA && outB ? (a.position().distanceToSqr(d.center) <= b.position().distanceToSqr(d.center) ? d.a : d.b)
                        : outA ? d.b : d.a;
                ServerPlayer leaver = winner.equals(d.a) ? b : a;
                leaver.sendSystemMessage(Component.translatable("social.isekai_mahou.duel.left_area").withStyle(ChatFormatting.RED));
                end(srv, d, winner, false);
                continue;
            }
            if (t >= COUNTDOWN_TICKS + FIGHT_TICKS) {
                end(srv, d, d.a, true);
                continue;
            }
            if (t % 20 == 0 && a.level() instanceof ServerLevel level) ring(level, d.center);
        }
        DUELS.removeIf(d -> d.over);
    }

    private static boolean outside(Duel d, ServerPlayer p) {
        if (!p.level().dimension().equals(d.dim)) return true;
        double dx = p.getX() - d.center.x, dz = p.getZ() - d.center.z;
        return dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(p.getY() - d.center.y) > RADIUS;
    }

    private static void countdown(ServerPlayer a, ServerPlayer b, int n) {
        for (ServerPlayer p : List.of(a, b)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 4));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("social.isekai_mahou.duel.vs",
                    p == a ? b.getGameProfile().getName() : a.getGameProfile().getName()).withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.valueOf(n)).withColor(0xFFD86B)));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.0F, 0.8F + 0.2F * (3 - n));
        }
    }

    private static void fight(ServerPlayer a, ServerPlayer b) {
        for (ServerPlayer p : List.of(a, b)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 24, 8));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("social.isekai_mahou.duel.fight")
                    .withStyle(ChatFormatting.BOLD).withColor(0xFF5A4A)));
            p.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.4F);
            SocialSync.markState(p.getUUID());
        }
    }

    private static void ring(ServerLevel level, Vec3 c) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.25F), 1.3F);
        int n = (int) Math.ceil(Math.PI * 2 * RADIUS / 2.2);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            level.sendParticles(dust, c.x + Math.cos(ang) * RADIUS, c.y + 0.3, c.z + Math.sin(ang) * RADIUS, 1, 0, 0.1, 0, 0);
        }
    }

    /** Termina o duelo. {@code draw}: tempo esgotado (ninguém ganha). */
    private static void end(MinecraftServer srv, Duel d, UUID winnerId, boolean draw) {
        if (d.over) return;
        d.over = true;
        UUID loserId = d.other(winnerId);
        ServerPlayer winner = srv.getPlayerList().getPlayer(winnerId);
        ServerPlayer loser = srv.getPlayerList().getPlayer(loserId);
        if (draw) {
            Component msg = Component.translatable("social.isekai_mahou.duel.draw", d.nameA, d.nameB).withStyle(ChatFormatting.YELLOW);
            if (winner != null) winner.sendSystemMessage(msg);
            if (loser != null) loser.sendSystemMessage(msg);
        } else {
            Component msg = Component.translatable("social.isekai_mahou.duel.won", d.nameOf(winnerId), d.nameOf(loserId))
                    .withColor(0xFFD86B);
            if (winner != null) {
                MagicApi.addCounter(winner, "duel.win", 1);
                announce(winner, msg);
                winner.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 10));
                winner.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                winner.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("social.isekai_mahou.duel.victory")
                        .withStyle(ChatFormatting.BOLD).withColor(0xFFD86B)));
                winner.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
                winner.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, winner.getX(), winner.getY() + 1, winner.getZ(),
                        40, 0.5, 0.8, 0.5, 0.3);
            }
            if (loser != null) {
                MagicApi.addCounter(loser, "duel.loss", 1);
                loser.sendSystemMessage(msg);
                loser.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 0.9F);
            }
        }
        if (winner != null && loser != null) NeoForge.EVENT_BUS.post(new SocialEvent.DuelEnded(winner, loser, draw));
        SocialSync.markState(winnerId);
        SocialSync.markState(loserId);
    }

    private static void announce(ServerPlayer winner, Component msg) {
        // Quem está perto também vê o resultado
        for (ServerPlayer p : winner.serverLevel().players()) {
            if (p.distanceToSqr(winner) <= 48 * 48) p.sendSystemMessage(msg);
        }
    }

    // =====================================================================================
    //  Morte e saída
    // =====================================================================================
    /** O golpe que mataria um duelista em luta vira derrota (fica com 25% da vida). Mortes que ignoram tudo (vazio, /kill) passam. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        Duel d = duelOf(victim.getUUID());
        if (d == null) return;
        server = victim.server;
        UUID winner = d.other(victim.getUUID());
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            end(victim.server, d, winner, false);
            return;
        }
        if (d.phase(victim.server.getTickCount()) != PHASE_FIGHT) {
            end(victim.server, d, winner, true);
            return;
        }
        e.setCanceled(true);
        victim.setHealth(Math.max(1F, victim.getMaxHealth() * 0.25F));
        victim.clearFire();
        victim.removeAllEffects();
        victim.invulnerableTime = 40;
        victim.serverLevel().sendParticles(ParticleTypes.POOF, victim.getX(), victim.getY() + 1, victim.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        end(victim.server, d, winner, false);
    }

    static void onLogout(ServerPlayer player) {
        Duel d = duelOf(player.getUUID());
        if (d != null) end(player.server, d, d.other(player.getUUID()), false);
    }

    /** Para o HUD: fase (0 nenhum, 1 contagem, 2 luta), ticks restantes e oponente. */
    @Nullable
    static Duel current(UUID player) {
        return duelOf(player);
    }

    static void clear() {
        DUELS.clear();
        server = null;
    }

    private DuelService() {}
}
